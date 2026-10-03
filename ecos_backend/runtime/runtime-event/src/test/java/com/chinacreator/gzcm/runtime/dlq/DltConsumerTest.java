package com.chinacreator.gzcm.runtime.dlq;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Constructor;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

import com.chinacreator.gzcm.common.event.KafkaTopics;
import com.chinacreator.gzcm.runtime.core.alert.IAlertService;
import com.chinacreator.gzcm.runtime.dlq.entity.DltEventRow;
import com.chinacreator.gzcm.runtime.dlq.mapper.DltEventMapper;
import com.chinacreator.gzcm.runtime.eventbus.KafkaBusConsumerConfig;

/**
 * C.5.2 DLQ 消费主链单测（W13 / 详细设计-00）。
 * <p>
 * 全 mock，不起 broker、不连 DB：
 * <ol>
 *   <li>topic 展开 = 11 个 KafkaTopics 常量的 {@code <topic>.DLT}（反射同源口径）；</li>
 *   <li>单条 DLT 消息 → 落库（payload/error/first_seen_at/status=pending + UUID）
 *       → 触发规则 kafka.dlt.lag（provider=runtime-event, severity=warn）；</li>
 *   <li>落库失败不阻断告警（双保险：落库失败也告警一次）。</li>
 * </ol>
 *
 * <p>运行：{@code mvn -pl runtime/runtime-event test -Dtest=DltConsumerTest}
 */
@DisplayName("DltConsumer — C.5.2 DLQ 消费主链")
class DltConsumerTest {

    private KafkaBusConsumerConfig.ConsumerBaseProps baseProps() throws Exception {
        Constructor<KafkaBusConsumerConfig.ConsumerBaseProps> c =
                KafkaBusConsumerConfig.ConsumerBaseProps.class.getDeclaredConstructor(Map.class);
        c.setAccessible(true);
        java.util.Map<String, Object> props = new java.util.HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:1");
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "ecos-dlt-consumer");
        return c.newInstance(props);
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<IAlertService> alertProvider(IAlertService svc) {
        return mock(ObjectProvider.class, i -> svc); // getIfAvailable → svc
    }

    /** 构造被测消费者（不 start() — 避免建真实容器） */
    @SuppressWarnings("unchecked")
    private DltConsumer newConsumer(DltEventMapper mapper, IAlertService alertService) throws Exception {
        ObjectProvider<IAlertService> p = mock(ObjectProvider.class);
        // getIfAvailable → alertService（缺席场景传 null）
        when(p.getIfAvailable()).thenReturn(alertService);
        return new DltConsumer(baseProps(), mapper, p, "latest");
    }

    @Test
    @DisplayName("11 个业务 topic × .DLT（反射 KafkaTopics 同源，避免硬编码漂移）")
    void dltTopicsCoverAllCodeTopics() {
        List<String> topics = DltConsumer.resolveBusinessTopics();
        assertEquals(11, topics.size(), "KafkaTopics 常量数应为 11，实测 " + topics);
        Set<String> dlt = new HashSet<>();
        for (String t : topics) {
            dlt.add(t + ".DLT");
        }
        assertTrue(dlt.contains(KafkaTopics.IDENTITY + ".DLT"));
        assertTrue(dlt.contains(KafkaTopics.AUDIT + ".DLT"));
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("消息 → 落库（pending/UUID/payload/error）→ kafka.dlt.lag 告警（provider=runtime-event, warn）")
    void onDeadLetterPersistsThenAlerts() throws Exception {
        DltEventMapper mapper = mock(DltEventMapper.class);
        IAlertService alert = mock(IAlertService.class);
        when(mapper.insert(any(DltEventRow.class))).thenReturn(1);

        DltConsumer consumer = newConsumer(mapper, alert);
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                KafkaTopics.OBJECT + ".DLT", 0, 1L, null,
                "{\"objectId\":\"obj-1\"}");
        record.headers().add(new RecordHeader("dtl-error",
                "com.foo.UnhandledException: boom".getBytes(java.nio.charset.StandardCharsets.UTF_8)));

        consumer.onDeadLetter(KafkaTopics.OBJECT, KafkaTopics.OBJECT + ".DLT", record);

        // 1) 落库行断言
        ArgumentCaptor<DltEventRow> rowCap = ArgumentCaptor.forClass(DltEventRow.class);
        verify(mapper).insert(rowCap.capture());
        DltEventRow row = rowCap.getValue();
        assertNotNull(row.getId());
        assertEquals(36, row.getId().length(), "MC01 应用侧 UUID");
        assertEquals(KafkaTopics.OBJECT, row.getTopic());
        assertEquals(KafkaTopics.OBJECT + ".DLT", row.getDltTopic());
        assertEquals("{\"objectId\":\"obj-1\"}", row.getPayload());
        assertEquals("com.foo.UnhandledException: boom", row.getErrorMessage());
        assertEquals("pending", row.getStatus());
        assertNotNull(row.getFirstSeenAt());

        // 2) 告警断言（同规则 kafka.dlt.lag，severity=warn，provider=runtime-event）
        verify(alert).triggerAlertByRuleCode(
                org.mockito.ArgumentMatchers.eq(DltConsumer.RULE_DLT_LAG),
                org.mockito.ArgumentMatchers.eq("runtime-event"),
                org.mockito.ArgumentMatchers.eq(DltConsumer.RULE_DLT_LAG),
                org.mockito.ArgumentMatchers.eq("warn"),
                org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.eq("DLT_MESSAGE"),
                org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.contains("topic=" + KafkaTopics.OBJECT));
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("落库失败 → 仍触发告警（可用性优先双保险）")
    void onDeadLetterAlertEvenWhenPersistFails() throws Exception {
        DltEventMapper mapper = mock(DltEventMapper.class);
        IAlertService alert = mock(IAlertService.class);
        when(mapper.insert(any(DltEventRow.class)))
                .thenThrow(new RuntimeException("relation does not exist"));

        DltConsumer consumer = newConsumer(mapper, alert);
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                KafkaTopics.WORKFLOW + ".DLT", 0, 2L, null, "{}");

        consumer.onDeadLetter(KafkaTopics.WORKFLOW, KafkaTopics.WORKFLOW + ".DLT", record);

        verify(mapper).insert(any(DltEventRow.class));        // 尝试过落库
        verify(alert).triggerAlertByRuleCode(org.mockito.ArgumentMatchers.eq(DltConsumer.RULE_DLT_LAG),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq("warn"), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.contains("persisted=false"));
    }
}
