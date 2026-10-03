package com.chinacreator.gzcm.runtime.dlq;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.kafka.listener.MessageListener;

import com.chinacreator.gzcm.common.context.TraceContext;
import com.chinacreator.gzcm.common.event.KafkaTopics;
import com.chinacreator.gzcm.runtime.core.alert.IAlertService;
import com.chinacreator.gzcm.runtime.dlq.entity.DltEventRow;
import com.chinacreator.gzcm.runtime.dlq.mapper.DltEventMapper;
import com.chinacreator.gzcm.runtime.eventbus.KafkaBusConsumerConfig;

/**
 * DLQ 统一消费者（W13 / C.5.2 事件流含 DLQ）。
 * <p>
 * 装配条件与 {@link KafkaBusConsumerConfig} 同口径：
 * {@code ecos.event.kafka.enabled=true} + spring-kafka 在 classpath。
 * <ul>
 *   <li>全 11 个代码 topic 各一个 listener，topic = {@code <topic>.DLT}，
 *       groupId 统一 {@code ecos-dlt-consumer}（多 topic 共享组 = 竞争消费死信，
 *       语义正确：一条死信只落库一次）；</li>
 *   <li>消息落 {@code public.ecos_runtime_event_dlq}（payload/error/first_seen_at/
 *       status=pending，MC01 应用侧 UUID）；</li>
 *   <li>落库后触发规则 {@code kafka.dlt.lag} 告警（rule 不存在时由
 *       {@link IAlertService#triggerAlertByRuleCode} 自动创建，
 *       provider=runtime-event，severity=warn）；</li>
 *   <li>DB 全链 try/catch 降级：表缺失只 WARN，消费位点照常推进（可用性优先）。
 *       落库失败时尝试进程内告警（内存模式不影响）。</li>
 * </ul>
 *
 * <p>容器经 {@link #start()}（SmartLifecycle）程序式启动，不占用宿主
 * {@code kafkaListenerContainerFactory} Bean 类型（避免与事件总线容器工厂混配）。
 */
@Configuration
@ConditionalOnClass(ConsumerConfig.class)
@ConditionalOnProperty(name = "ecos.event.kafka.enabled", havingValue = "true")
public class DltConsumer implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(DltConsumer.class);
    public static final String GROUP_ID = "ecos-dlt-consumer";
    /** V241 规则约定编码（设计 C.5.2：kafka.dlt.lag = DLQ 死信/lag 统一告警规则） */
    public static final String RULE_DLT_LAG = "kafka.dlt.lag";
    private static final String VERSION_NO = "1";
    private static final String DOMAIN = "default";

    private final KafkaBusConsumerConfig.ConsumerBaseProps consumerBaseProps;
    private final DltEventMapper dltEventMapper;
    private final ObjectProvider<IAlertService> alertServiceProvider;
    private final List<String> businessTopics;
    private final List<KafkaMessageListenerContainer<String, String>> containers = new ArrayList<>();
    private volatile boolean running = false;

    public DltConsumer(KafkaBusConsumerConfig.ConsumerBaseProps consumerBaseProps,
                       DltEventMapper dltEventMapper,
                       ObjectProvider<IAlertService> alertServiceProvider,
                       @Value("${ecos.event.kafka.auto-offset-reset:latest}") String autoOffsetReset) {
        this.consumerBaseProps = consumerBaseProps;
        this.dltEventMapper = dltEventMapper;
        this.alertServiceProvider = alertServiceProvider;
        this.businessTopics = resolveBusinessTopics();
        log.info("[DltConsumer] 装配 DLQ 消费容器 {} 个 (groupId={}, autoOffsetReset={})",
                businessTopics.size(), GROUP_ID, autoOffsetReset);
    }

    /** 反射枚举 {@link KafkaTopics} 常量 — 与 C.5.3 topic 单源同源，避免硬编码漂移 */
    static List<String> resolveBusinessTopics() {
        List<String> topics = new ArrayList<>();
        for (Field f : KafkaTopics.class.getFields()) {
            if (Modifier.isStatic(f.getModifiers()) && f.getType() == String.class) {
                try {
                    Object v = f.get(null);
                    if (v instanceof String s && !s.isBlank()) {
                        topics.add(s);
                    }
                } catch (IllegalAccessException ignore) {
                    // 常量读不到直接跳过
                }
            }
        }
        Collections.sort(topics);
        return topics;
    }

    // ── SmartLifecycle ────────────────────────────────────────────

    @Override
    public void start() {
        if (running) {
            return;
        }
        for (String topic : businessTopics) {
            String dltTopic = topic + ".DLT";
            try {
                Map<String, Object> props = new HashMap<>(consumerBaseProps.asMap());
                props.put(ConsumerConfig.GROUP_ID_CONFIG, GROUP_ID);
                ConsumerFactory<String, String> factory =
                        new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), new StringDeserializer());
                ContainerProperties cp = new ContainerProperties(dltTopic);
                cp.setMessageListener((MessageListener<String, String>)
                        record -> onDeadLetter(topic, dltTopic, record));
                cp.setAckMode(ContainerProperties.AckMode.RECORD);
                KafkaMessageListenerContainer<String, String> container =
                        new KafkaMessageListenerContainer<>(factory, cp);
                container.setBeanName("ecos-dlt-consumer-" + dltTopic);
                container.start();
                containers.add(container);
            } catch (Exception e) {
                // 单容器 start 失败不阻塞宿主（与 KafkaEventBusServiceImpl.subscribe 同口径）
                log.warn("[DltConsumer] DLQ 容器启动失败 topic={} errType={} msg={}",
                        dltTopic, e.getClass().getSimpleName(), e.getMessage());
            }
        }
        running = true;
    }

    @Override
    public void stop() {
        for (KafkaMessageListenerContainer<String, String> c : containers) {
            try {
                c.stop();
            } catch (Exception e) {
                log.warn("[DltConsumer] 容器停止失败: {}", e.getMessage());
            }
        }
        containers.clear();
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public boolean isAutoStartup() {
        return true;
    }

    // ── 消费主链 ──────────────────────────────────────────────────

    /** 单消息处理 — 包级可见（单测直投） */
    void onDeadLetter(String businessTopic, String dltTopic, ConsumerRecord<String, String> record) {
        String json = record == null ? null : record.value();
        if (json == null || json.isEmpty()) {
            log.warn("[DltConsumer] null/empty DLT record, skipped topic={}", dltTopic);
            return;
        }
        String errorMessage = extractError(record);
        boolean persisted = false;
        try {
            DltEventRow row = new DltEventRow();
            row.setId(UUID.randomUUID().toString());
            row.setTopic(businessTopic);
            row.setDltTopic(dltTopic);
            row.setPayload(json);
            row.setErrorMessage(errorMessage);
            row.setFirstSeenAt(Timestamp.from(Instant.now()));
            row.setStatus("pending");
            row.setTraceId(extractTrace(record));
            row.setVersionNo(VERSION_NO);
            row.setIsDeleted((short) 0);
            row.setDomain(DOMAIN);
            dltEventMapper.insert(row);
            persisted = true;
        } catch (Exception e) {
            log.warn("[DltConsumer] DLQ 落库失败 topic={} errType={} msg={}",
                    dltTopic, e.getClass().getSimpleName(), e.getMessage());
        }
        // 落库成功才触发告警（告警 record 含 trace 溯源；落库失败时也告警一次 — 双保险）
        triggerDltAlert(businessTopic, json.length(), persisted);
    }

    /** 触发 kafka.dlt.lag 规则告警（rule 不存在时自动创建，provider=runtime-event，severity=warn） */
    private void triggerDltAlert(String businessTopic, int payloadBytes, boolean persisted) {
        IAlertService alertService = alertServiceProvider.getIfAvailable();
        if (alertService == null) {
            if (log.isDebugEnabled()) {
                log.debug("[DltConsumer] IAlertService 缺席，死信告警降级跳过 topic={}", businessTopic);
            }
            return;
        }
        try {
            alertService.triggerAlertByRuleCode(
                    RULE_DLT_LAG, "runtime-event", RULE_DLT_LAG, "warn", null, null,
                    "DLT_MESSAGE", null, null,
                    "topic=" + businessTopic + " 收到死信 payload=" + payloadBytes + "B persisted=" + persisted);
        } catch (Exception e) {
            log.warn("[DltConsumer] 死信告警触发失败 topic={} errType={}",
                    businessTopic, e.getClass().getSimpleName());
        }
    }

    private static String extractError(ConsumerRecord<String, String> record) {
        Header h = record.headers().lastHeader("dtl-error");
        if (h != null && h.value() != null) {
            String s = new String(h.value(), java.nio.charset.StandardCharsets.UTF_8);
            return s.length() > 1000 ? s.substring(0, 1000) : s;
        }
        Header reason = record.headers().lastHeader("dtl-reason");
        return reason != null && reason.value() != null
                ? new String(reason.value(), java.nio.charset.StandardCharsets.UTF_8) : null;
    }

    private static String extractTrace(ConsumerRecord<String, String> record) {
        Header h = record.headers().lastHeader(TraceContext.HEADER_NAME);
        if (h != null && h.value() != null) {
            return new String(h.value(), java.nio.charset.StandardCharsets.UTF_8);
        }
        h = record.headers().lastHeader("ecos-dlt-trace");
        return h != null && h.value() != null
                ? new String(h.value(), java.nio.charset.StandardCharsets.UTF_8) : null;
    }
}
