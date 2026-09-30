package com.chinacreator.gzcm.engine.security;

import com.chinacreator.gzcm.common.event.KafkaTopics;
import com.chinacreator.gzcm.engine.security.service.AuditEventProducer;
import com.chinacreator.gzcm.engine.security.service.AuditKafkaConsumer;
import com.chinacreator.gzcm.runtime.eventbus.EventBusService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 详细设计-01 F01-06 / W29·W34 — 审计链（生产→Kafka→消费落库）关键性质测试。
 *
 * <p>断言：</p>
 * <ul>
 *   <li>消费者初始化即订阅 {@code ecos.audit}（{@link KafkaTopics#AUDIT}）；</li>
 *   <li>payload 为 Map 或 JSON 字符串均入 {@code ecos_audit_log}（PG ON CONFLICT 幂等）；</li>
 *   <li>无效 payload（null / 空串 / 缺 id）不触库（防脏数据旁路）；</li>
 *   <li>PRODUCER 侧：{@code eventBus=null}（Kafka 不可用）→ 事件写入
 *       {@code ecos_runtime_audit_retry}（重试表兜底，不阻塞业务）。</li>
 * </ul>
 */
class AuditChainConsumerTest {

    private JdbcTemplate jdbc;
    private EventBusService eventBus;
    @SuppressWarnings("unchecked")
    private final ObjectProvider<EventBusService> provider =
            (ObjectProvider<EventBusService>) mock(ObjectProvider.class);
    private AuditKafkaConsumer consumer;
    private AuditEventProducer producer;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        eventBus = mock(EventBusService.class);
        reset(provider);
        when(provider.getIfAvailable()).thenReturn(eventBus);
        consumer = new AuditKafkaConsumer(jdbc, provider);
        producer = new AuditEventProducer(jdbc);
        // producer.eventBus 通过 @Autowired(required=false) 注入；测试里手工 set 以覆盖两条分支
        try {
            var f = AuditEventProducer.class.getDeclaredField("eventBus");
            f.setAccessible(true);
            f.set(producer, eventBus);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static Map<String, Object> evt(String id) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("userId", "u1");
        m.put("username", "admin");
        m.put("action", "login.success");
        m.put("resource", "/api/v1/auth/login");
        m.put("result", "SUCCESS");
        m.put("detail", "{\"ip\":\"10.0.0.1\"}");
        m.put("ipAddress", "10.0.0.1");
        m.put("timestamp", 1700000000000L);
        return m;
    }

    @Test
    @DisplayName("init() — 挂载到 ecos.audit topic")
    void initSubscribesAuditTopic() {
        consumer.init();
        verify(eventBus).subscribe(eq(KafkaTopics.AUDIT), eq(Map.class), any());
    }

    @Test
    @DisplayName("onAuditEvent(Map) — 幂等 INSERT ON CONFLICT 落 ecos_audit_log")
    void mapPayloadInsertsIntoAuditLog() {
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        consumer.onAuditEvent(evt("a-1"));

        var mc = ArgumentCaptor.forClass(String.class);
        var vc = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).update(mc.capture(), vc.capture());
        String sql = mc.getValue();
        assertTrue(sql.contains("ecos_audit_log"), "必须落 ecos_audit_log: " + sql);
        assertTrue(sql.contains("ON CONFLICT (id) DO NOTHING"), "必须幂等 (PG): " + sql);
        Object[] args = vc.getValue();
        assertEquals("a-1", args[0]);
        assertEquals("admin", args[2]);
    }

    @Test
    @DisplayName("onAuditEvent(JSON String) — 字符串 payload 反序列化后同样落库")
    void jsonStringPayloadInserts() throws Exception {
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        String payload = new com.fasterxml.jackson.databind.ObjectMapper()
                .writeValueAsString(evt("b-1"));
        consumer.onAuditEvent(payload);
        verify(jdbc).update(anyString(), any(Object[].class));
    }

    @Test
    @DisplayName("onAuditEvent(null / 空串) — 不触库（脏数据防线）")
    void nullOrNullPayloadSkipped() {
        consumer.onAuditEvent(null);
        consumer.onAuditEvent("");
        consumer.onAuditEvent("   ");
        verifyNoInteractions(jdbc);
    }

    @Test
    @DisplayName("onAuditEvent(Map 缺 id) — 防脏数据 drop（不触库）")
    void missingIdDrops() {
        Map<String, Object> bad = new LinkedHashMap<>(evt(null));
        bad.remove("id");
        consumer.onAuditEvent(bad);
        verifyNoInteractions(jdbc);
    }

    @Test
    @DisplayName("PG ON CONFLICT 失败 — 退化到 SELECT COUNT + 条件 INSERT（幂等等价）")
    void fallbackOnDataAccessException() {
        when(jdbc.update(anyString(), any(Object[].class)))
                .thenThrow(new DataAccessException("unsupported") {
                    @Override public Throwable getMostSpecificCause() { return null; }
                });
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(0);

        consumer.onAuditEvent(evt("c-1"));

        // 第一次 update 抛 → 走 SELECT + 条件 update；共 2 次 update 尝试（首发 + 退化）
        verify(jdbc, atLeast(2)).update(anyString(), any(Object[].class));
        verify(jdbc).queryForObject(anyString(), eq(Integer.class), any(Object[].class));
    }

    @Test
    @DisplayName("AuditEventProducer.publish — EventBus 不可用 → 走重试表 ecos_runtime_audit_retry")
    void producerFallbackToRetryTable() throws Exception {
        // 清空 eventBus 模拟 Kafka 不可用
        var f = AuditEventProducer.class.getDeclaredField("eventBus");
        f.setAccessible(true);
        f.set(producer, null);

        producer.publish(AuditEventProducer.AuditEvent.of(
                "u1", "admin", "login.fail", "/api/v1/auth/login", "DENIED", "nope", "10.0.0.1"));

        var mc = ArgumentCaptor.forClass(String.class);
        var vc = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).update(mc.capture(), vc.capture());
        String sql = mc.getValue();
        assertTrue(sql.contains("ecos_runtime_audit_retry"), "Kafka 不可用走重试表兜底: " + sql);
        assertTrue(sql.contains("'security_audit'"), "重试表 event_type 必须是 security_audit");
    }

    @Test
    @DisplayName("AuditEventProducer.publish — EventBus 正常 → 发布到 ecos.audit，不写重试表")
    void producerHappyPathPublishesOnly() {
        producer.publish(AuditEventProducer.AuditEvent.of(
                "u1", "admin", "login.ok", "/api/v1/auth/login", "SUCCESS", "ok", "10.0.0.1"));

        verify(eventBus).publish(eq(KafkaTopics.AUDIT), anyMap());
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }
}
