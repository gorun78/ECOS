package com.chinacreator.gzcm.runtime.eventbus;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.MessageListener;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
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
 * <p>T-C 双投递（PMO-74 H2-T7 裁决 2026-09-29）：{@link #publish} 先做同 JVM 本地分发再发 Kafka，
 * {@link #subscribe} 既登记本地 handler 也起消费容器；本实例自己发出的记录按
 * {@code ecos-event-origin} 头识别并跳过（{@link #isSelfOrigin}），因此每条事件在
 * 「同 JVM」与「跨 JVM」两个方向上各只投递一次。收益：broker 不可用时同 JVM 订阅不再退化为全丢。
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
    /** 投递来源头 — 值为本 JVM 实例 id；消费侧据此跳过自身回环，避免同进程重复投递 */
    private static final String ORIGIN_HEADER = "ecos-event-origin";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    /** 消费者基础属性（bootstrap/反序列化/自动提交），由 {@link KafkaBusConsumerConfig} 提供 */
    private final KafkaBusConsumerConfig.ConsumerBaseProps consumerBaseProps;
    /** 空=每个订阅独占 group（fan-out 广播语义）；非空=多实例共享同一 group 前缀做竞争消费 */
    private final String sharedGroupPrefix;
    private final List<ConcurrentMessageListenerContainer<String, String>> containers = new CopyOnWriteArrayList<>();
    private final AtomicInteger subscriptionSeq = new AtomicInteger();
    /** 本 JVM 事件来源标识 — 写入 Kafka 记录头，用于回环排除 */
    private final String originId = UUID.randomUUID().toString();
    /** topic → 本地订阅者列表；T-C 的本地分发通道，不依赖 broker */
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<Consumer<Object>>> localHandlers =
            new ConcurrentHashMap<>();

    public KafkaEventBusServiceImpl(KafkaTemplate<String, String> kafkaTemplate,
                                    ObjectMapper objectMapper,
                                    KafkaBusConsumerConfig.ConsumerBaseProps consumerBaseProps,
                                    @Value("${ecos.event.kafka.group-prefix:}") String sharedGroupPrefix) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.consumerBaseProps = consumerBaseProps;
        this.sharedGroupPrefix = sharedGroupPrefix;
        log.info("[EventBus][KAFKA] activated — broker 在/不在都能发, 失败仅记 WARN 不阻塞主流程; groupPrefix={}",
                sharedGroupPrefix == null || sharedGroupPrefix.isEmpty() ? "<per-subscription fan-out>" : sharedGroupPrefix);
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
            // T-C 本地侧先发：broker 不可用或序列化失败时，同 JVM 订阅者仍收到（不退化为全丢）
            dispatchLocal(topic, payload);
            String json = objectMapper.writeValueAsString(payload);
            CompletableFuture<SendResult<String, String>> future =
                    kafkaTemplate.send(new ProducerRecord<>(topic, null, null, null, json, originHeaders()));
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
        if (topic == null || topic.isEmpty()) {
            log.warn("[EventBus][KAFKA] subscribe drops null/empty topic");
            return;
        }
        if (handler == null) {
            log.warn("[EventBus][KAFKA] subscribe drops null handler topic={}", topic);
            return;
        }
        // T-C 本地侧：登记进本地分发表，同 JVM 的 publish 直接命中，不依赖 broker
        localHandlers.computeIfAbsent(topic, k -> new CopyOnWriteArrayList<>()).add(handler);
        // PMO-74.2.1：真实消费容器桥接（T-C 远端侧）。每个订阅一个独立 group → fan-out（与内存总线广播语义一致）
        MessageListener<String, String> listener = record -> dispatch(topic, payloadType, handler, record);
        ContainerProperties props = new ContainerProperties(topic);
        props.setMessageListener(listener);
        props.setAckMode(ContainerProperties.AckMode.RECORD);

        Map<String, Object> factoryProps = new HashMap<>(consumerBaseProps.asMap());
        factoryProps.put(ConsumerConfig.GROUP_ID_CONFIG, groupId(topic));

        ConcurrentMessageListenerContainer<String, String> container =
                new ConcurrentMessageListenerContainer<>(
                        new DefaultKafkaConsumerFactory<>(factoryProps), props);
        container.setBeanName("runtimeEventBus-" + topic + "-" + subscriptionSeq.incrementAndGet());
        try {
            container.start();
            containers.add(container);
            log.info("[EventBus][KAFKA] subscribe bridged topic={} group={} payloadType={}",
                    topic, factoryProps.get(ConsumerConfig.GROUP_ID_CONFIG),
                    payloadType == null ? "null" : payloadType.getName());
        } catch (Exception e) {
            // 订阅装载失败不阻塞宿主启动，但必须显式暴露：事件通道静默断裂比启动失败更难查
            log.error("[EventBus][KAFKA] subscribe container start FAILED topic={} errType={} msg={}",
                    topic, e.getClass().getSimpleName(), e.getMessage(), e);
        }
    }

    /** 包级可见仅为 T-C 用例（{@code KafkaEventBusServiceImplTest}）能直接喂 {@link ConsumerRecord} 验回环排除 */
    void dispatch(String topic, Class<?> payloadType, Consumer<Object> handler,
                  ConsumerRecord<String, String> record) {
        String json = record == null ? null : record.value();
        if (json == null) {
            log.warn("[EventBus][KAFKA] null record value, skipped topic={}", topic);
            return;
        }
        if (isSelfOrigin(record)) {
            // 回环：本实例 publish 时已本地分发，容器再投一次就是重复投递
            log.debug("[EventBus][KAFKA] self-origin record skipped topic={} origin={}", topic, originId);
            return;
        }
        Object payload;
        if (payloadType == null || String.class.equals(payloadType)) {
            payload = json;
        } else {
            try {
                payload = objectMapper.readValue(json, payloadType);
            } catch (Exception e) {
                log.warn("[EventBus][KAFKA] deserialize failed, skipped topic={} targetType={} errType={} msg={}",
                        topic, payloadType.getName(), e.getClass().getSimpleName(), e.getMessage());
                return;
            }
        }
        try {
            handler.accept(payload);
        } catch (Exception e) {
            // 与内存总线一致：业务 handler 异常不影响消费位点推进，不重投、不阻塞主流程
            log.warn("[EventBus][KAFKA] handler failed topic={} errType={} msg={}",
                    topic, e.getClass().getSimpleName(), e.getMessage());
        }
    }

    /** T-C 本地分发：与 {@link MemoryEventBusServiceImpl} 的编程式 handler 语义一致（异常只记 WARN 不外抛） */
    private void dispatchLocal(String topic, Object payload) {
        CopyOnWriteArrayList<Consumer<Object>> handlers = localHandlers.get(topic);
        if (handlers == null) {
            return;
        }
        for (Consumer<Object> h : handlers) {
            try {
                h.accept(payload);
            } catch (Exception e) {
                log.warn("[EventBus][KAFKA] local handler failed topic={} errType={} msg={}",
                        topic, e.getClass().getSimpleName(), e.getMessage());
            }
        }
    }

    private List<Header> originHeaders() {
        List<Header> headers = new ArrayList<>(1);
        headers.add(new RecordHeader(ORIGIN_HEADER, originId.getBytes(StandardCharsets.UTF_8)));
        return headers;
    }

    /** 回环判定：记录头里的 origin 与本实例 id 相同即为自己发出的（无头 = 外部老消息，照常投递） */
    private boolean isSelfOrigin(ConsumerRecord<String, String> record) {
        Header header = record.headers().lastHeader(ORIGIN_HEADER);
        if (header == null || header.value() == null) {
            return false;
        }
        return originId.equals(new String(header.value(), StandardCharsets.UTF_8));
    }

    /** group 名可配（多实例共享消费组做竞争），默认按 topic 独占以保证事件总线 fan-out 语义 */
    private String groupId(String topic) {
        if (sharedGroupPrefix != null && !sharedGroupPrefix.isEmpty()) {
            return sharedGroupPrefix + "-" + topic;
        }
        return "ecos-event-" + topic + "-" + UUID.randomUUID();
    }

    @PreDestroy
    public void shutdownContainers() {
        for (ConcurrentMessageListenerContainer<String, String> c : containers) {
            try {
                c.stop();
            } catch (Exception e) {
                log.warn("[EventBus][KAFKA] container stop failed: {}", e.getMessage());
            }
        }
        containers.clear();
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
