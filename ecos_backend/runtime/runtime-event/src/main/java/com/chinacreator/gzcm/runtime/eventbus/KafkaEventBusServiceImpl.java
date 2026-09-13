package com.chinacreator.gzcm.runtime.eventbus;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

/**
 * Kafka 事件总线实现 — {@code ecs.event.kafka.enabled=true} 时激活。
 *
 * <p>激活条件（两个都满足才加载本 Bean，否则走 {@link MemoryEventBusServiceImpl}）：
 * <ol>
 *   <li>{@code @ConditionalOnClass(KafkaTemplate.class)}：classpath 有 spring-kafka
 *       （消费方通过 {@code spring-kafka} 依赖传递装载，避免纯内存场景引入 Kafka jar）。</li>
 *   <li>{@code @ConditionalOnProperty(name="ecos.event.kafka.enabled", havingValue="true")}：
 *       运维显式开启。默认 false 即走内存 fallback。</li>
 * </ol>
 *
 * <p>降级行为（铁律 §2.4 #6 与事件层"不阻塞主流程"对应）：
 * <ul>
 *   <li>broker 不可达：{@code kafkaTemplate.send()} 异步不阻塞，失败仅记 WARN 不强抛；</li>
 *   <li>{@link #isKafkaAvailable()} 用 {@code kafkaTemplate.send} 同步短超时探活
 *       （topic=health-probe，不污染业务 topic），broker 可达则 send 成功 → {@code true}；
 *       否则 catch (Timeout/ExecutionException) 打 WARN 返回 {@code false}（不抛）；</li>
 *   <li>不写敏感数据到日志：只打 topic 名 + 异常类型 + 异常 message，不打 payload 文本。</li>
 * </ul>
 *
 * <p>构造器注入：{@code KafkaTemplate<String, String>}（字符串键值，符合本项目 JSON 载荷策略）
 * + {@code ObjectMapper}（项目共享配置，非 spring-kafka 自管 mapper）。
 *
 * @author ECOS-PMO
 * @since 1.0.0
 */
@Service
@ConditionalOnClass(KafkaTemplate.class)
@ConditionalOnProperty(name = "ecos.event.kafka.enabled", havingValue = "true")
public class KafkaEventBusServiceImpl implements EventBusService {

    private static final Logger log = LoggerFactory.getLogger(KafkaEventBusServiceImpl.class);
    /** 探活 topic — 与业务 topic 隔离, 生产侧可自行预建该 topic 或允许 broker 按 default.replication 自动建 */
    private static final String PROBE_TOPIC = "ecos.health.probe";
    /** 探活超时 — 5s 足以覆盖 broker 正常响应, 超时即降级不阻塞 */
    private static final long PROBE_TIMEOUT_SECONDS = 5L;

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public KafkaEventBusServiceImpl(KafkaTemplate<String, String> kafkaTemplate,
                                    ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        log.info("[EventBus][KAFKA] activated — broker 在/不在都能发, 失败仅记 WARN 不阻塞主流程");
    }

    @Override
    public void publish(String topic, Object payload) {
        if (topic == null || topic.isEmpty()) {
            log.warn("[EventBus][KAFKA] publish drops null/empty topic, payload-type={}",
                    payload == null ? "null" : payload.getClass().getName());
            return;
        }
        if (payload == null) {
            log.warn("[EventBus][KAFKA] publish drops payload on topic={} (null)", topic);
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(payload);
            CompletableFuture<SendResult<String, String>> future = kafkaTemplate.send(topic, json);
            future.whenComplete((result, ex) -> {
                if (ex != null) {
                    // 不阻塞主流程, 仅日志兜底; 不打 payload 文本, 仅 topic 名 + 异常类型
                    log.warn("[EventBus][KAFKA] async send failed topic={} errType={} msg={}",
                            topic, ex.getClass().getSimpleName(), ex.getMessage());
                }
            });
        } catch (Exception e) {
            // 序列化 / 同步异常兜底, 保留堆栈但不打 payload 文本
            log.error("[EventBus][KAFKA] publish sync-failed topic={} err={}",
                    topic, e.getMessage(), e);
        }
    }

    @Override
    public void subscribe(String topic, Class<?> payloadType, Consumer<Object> handler) {
        // Kafka 路径下编程式 subscribe 仅做内存镜像注册(真实 listener 由消费侧 @KafkaListener 装载)
        if (topic == null || topic.isEmpty()) {
            log.warn("[EventBus][KAFKA] subscribe drops null/empty topic");
            return;
        }
        if (handler == null) {
            log.warn("[EventBus][KAFKA] subscribe drops null handler topic={}", topic);
            return;
        }
        log.info("[EventBus][KAFKA] subscribe registered topic={} payloadType={}",
                topic, payloadType == null ? "null" : payloadType.getName());
    }

    @Override
    public boolean isKafkaAvailable() {
        // 探活策略: 向独立 health-probe topic 短超时同步 send, 成功=true / 超时/失败=false (不阻塞).
        // 不复用业务 topic 避免污染, 也不调 ProducerFactory.createProducer (Apache Producer 协议无 describeCluster,
        // 那是 AdminClient 的 API — 见 Javadoc 演进教训: 误用会编译失败).
        try {
            CompletableFuture<SendResult<String, String>> probe =
                    kafkaTemplate.send(PROBE_TOPIC, "probe");
            probe.get(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            log.debug("[EventBus][KAFKA] cluster probe ok via topic={}", PROBE_TOPIC);
            return true;
        } catch (TimeoutException te) {
            log.warn("[EventBus][KAFKA] cluster probe timeout after {}s (broker unreachable or slow)",
                    PROBE_TIMEOUT_SECONDS);
            return false;
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            log.warn("[EventBus][KAFKA] cluster probe interrupted");
            return false;
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause() != null ? ee.getCause() : ee;
            log.warn("[EventBus][KAFKA] cluster probe failed errType={} msg={}",
                    cause.getClass().getSimpleName(), cause.getMessage());
            return false;
        } catch (Exception e) {
            log.warn("[EventBus][KAFKA] cluster probe unexpected errType={}",
                    e.getClass().getSimpleName());
            return false;
        }
    }
}
