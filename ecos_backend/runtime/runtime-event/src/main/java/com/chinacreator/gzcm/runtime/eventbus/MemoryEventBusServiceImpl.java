package com.chinacreator.gzcm.runtime.eventbus;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * 内存事件总线 fallback — Kafka 未启用时自动加载（本地/开发/降级态）。
 *
 * <p>激活条件（见 {@link MemoryEventBusFallbackCondition}）：
 * 容器里不存在 {@code KafkaEventBusServiceImpl} Bean（即 {@code ecs.event.kafka.enabled} != true
 * 或 spring-kafka 类未装载）。
 *
 * <p>实现策略：
 * <ul>
 *   <li>{@link #publish} 走 Spring {@code ApplicationEventPublisher} 同步 in-process 分发
 *       （同 JVM {@code @EventListener} 接收，覆盖 dbus {@code @EventListener} 当前路径）；
 *       同时把"编程式 subscribe"注册的 {@code Map<topic, handler>} 表里的 handler 同步触发；</li>
 *   <li>{@link #subscribe} 仅 {@code put} 到内存表，真实触发由 {@link #publish} 与
 *       {@link #onEnvelope(MemoryEnvelopeEvent)} 两条路径共同覆盖；</li>
 *   <li>{@link #isKafkaAvailable()} 永远 false（无 broker）。</li>
 * </ul>
 *
 * <p>限制（就地文档化，避免误用）：
 * <ul>
 *   <li>仅同 JVM 内进程同步分发；buszhi→dbus 跨 JVM 需启用=true 走 Kafka；</li>
 *   <li>无人监听的事件打 DEBUG（避免本地噪音）；</li>
 *   <li>编程式 handler 抛异常不阻塞 publish 主流程，catch 后记 WARN。</li>
 * </ul>
 *
 * @author ECOS-PMO
 * @since 1.0.0
 */
@Service
@Conditional(MemoryEventBusFallbackCondition.class)
public class MemoryEventBusServiceImpl implements EventBusService {

    private static final Logger log = LoggerFactory.getLogger(MemoryEventBusServiceImpl.class);
    private static final String FALLBACK_NOTICE =
            "[EventBus][MEMORY] fallback mode — Kafka 未启用 (ecs.event.kafka.enabled != true), 仅同 JVM 内存分发";

    private final ApplicationEventPublisher publisher;
    private final ConcurrentHashMap<String, Consumer<Object>> topicHandlers = new ConcurrentHashMap<>();

    public MemoryEventBusServiceImpl(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
        log.warn(FALLBACK_NOTICE);
    }

    @Override
    public void publish(String topic, Object payload) {
        if (topic == null || topic.isEmpty()) {
            log.debug("[EventBus][MEMORY] publish drops null/empty topic, payload-type={}",
                    payload == null ? "null" : payload.getClass().getName());
            return;
        }
        // 1. Spring 事件路径 — 同 JVM @EventListener 接收 (覆盖 dbus @EventListener 当前路径)
        try {
            publisher.publishEvent(new MemoryEnvelopeEvent(this, topic, payload));
        } catch (Exception e) {
            log.error("[EventBus][MEMORY] spring-event publish failed topic={} err={}",
                    topic, e.getMessage(), e);
        }
        // 2. 编程式 subscribe 路径 — Map<topic, handler> 同步触发 (显式 subscribe 的条目)
        Consumer<Object> handler = topicHandlers.get(topic);
        if (handler != null) {
            try {
                handler.accept(payload);
            } catch (Exception e) {
                log.warn("[EventBus][MEMORY] programmatic handler failed topic={} err={}",
                        topic, e.getMessage());
            }
        }
    }

    @Override
    public void subscribe(String topic, Class<?> payloadType, Consumer<Object> handler) {
        if (topic == null || topic.isEmpty()) {
            log.warn("[EventBus][MEMORY] subscribe drops null/empty topic");
            return;
        }
        if (handler == null) {
            log.warn("[EventBus][MEMORY] subscribe drops null handler topic={}", topic);
            return;
        }
        topicHandlers.put(topic, handler);
        log.debug("[EventBus][MEMORY] subscribe registered topic={} payloadType={}",
                topic, payloadType == null ? "null" : payloadType.getName());
    }

    @Override
    public boolean isKafkaAvailable() {
        return false;
    }

    /** 收到 Spring 容器分发的 {@link MemoryEnvelopeEvent} 时的兜底转发。
     *  spring 路径只为本 Bean 自身提供 (避免把 handler 也强绑到循环依赖);
     *  显式 subscribe 的 handler 由 #publish 路径 2 直接触发, 此处只记 DEBUG 即可。 */
    @EventListener
    public void onEnvelope(MemoryEnvelopeEvent env) {
        if (env == null || env.getTopic() == null) {
            return;
        }
        Consumer<Object> h = topicHandlers.get(env.getTopic());
        if (h == null) {
            log.debug("[EventBus][MEMORY] spring-event received but no programmatic handler topic={}",
                    env.getTopic());
            return;
        }
        // 同一事件可能由 publish 路径 2 与 onEnvelope 两条路径双重触发 — 去重交给调用方 (幂等语义)
        log.debug("[EventBus][MEMORY] spring-event double-fired with programmatic handler topic={}",
                env.getTopic());
    }

    /** 内存事件包装 — 让 Spring 容器携带 topic 信息; 继承 {@link ApplicationEvent} 即注册为容器事件。 */
    static final class MemoryEnvelopeEvent extends ApplicationEvent {
        private static final long serialVersionUID = 1L;
        private final String topic;
        private final Object payload;

        MemoryEnvelopeEvent(Object source, String topic, Object payload) {
            super(source);
            this.topic = topic;
            this.payload = payload;
        }

        public String getTopic() { return topic; }
        public Object getPayload() { return payload; }
    }
}
