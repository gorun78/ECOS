package com.chinacreator.gzcm.engine.security;

import com.chinacreator.gzcm.common.event.KafkaTopics;
import com.chinacreator.gzcm.engine.security.dao.AuditDao;
import com.chinacreator.gzcm.engine.security.service.AuditEventProducer;
import com.chinacreator.gzcm.engine.security.service.AuditKafkaConsumer;
import com.chinacreator.gzcm.runtime.eventbus.EventBusService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataAccessException;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 详细设计-01 F01-06 / W29·W34 — 审计链（生产→Kafka→消费落库）关键性质测试。
 *
 * <p>B3b-security 数据访问收口后：mock 收敛到 {@link AuditDao} 一个点。</p>
 *
 * <p>断言：</p>
 * <ul>
 *   <li>消费者初始化即订阅 {@code ecos.audit}（{@link KafkaTopics#AUDIT}）；</li>
 *   <li>payload 为 Map 或 JSON 字符串均经 DAO 幂等插入 {@code ecos_audit_log}（PG ON CONFLICT）；</li>
 *   <li>无效 payload（null / 空串 / 缺 id）不触 DAO（防脏数据旁路）；</li>
 *   <li>PRODUCER 侧：{@code eventBus=null}（Kafka 不可用）→ 经 DAO 写
 *       {@code ecos_runtime_audit_retry}（重试表兜底，不阻塞业务）。</li>
 * </ul>
 */
class AuditChainConsumerTest {

    private AuditDao auditDao;
    private EventBusService eventBus;
    private final ObjectProvider<EventBusService> provider =
            mock(ObjectProvider.class);
    private AuditKafkaConsumer consumer;
    private AuditEventProducer producer;

    @BeforeEach
    void setUp() {
        auditDao = mock(AuditDao.class);
        eventBus = mock(EventBusService.class);
        reset(provider);
        when(provider.getIfAvailable()).thenReturn(eventBus);
        consumer = new AuditKafkaConsumer(auditDao, provider);
        producer = new AuditEventProducer(auditDao);
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
    @DisplayName("onAuditEvent(Map) — 经 DAO 幂等插入 ecos_audit_log")
    void mapPayloadInsertsIntoAuditLog() {
        consumer.onAuditEvent(evt("a-1"));

        verify(auditDao).insertAuditLogIdempotent(
                eq("a-1"), eq("u1"), eq("admin"), eq("login.success"),
                eq("/api/v1/auth/login"), eq("SUCCESS"),
                eq("{\"ip\":\"10.0.0.1\"}"), eq("10.0.0.1"));
    }

    @Test
    @DisplayName("onAuditEvent(JSON String) — 字符串 payload 反序列化后同样经 DAO 落库")
    void jsonStringPayloadInserts() throws Exception {
        String payload = new com.fasterxml.jackson.databind.ObjectMapper()
                .writeValueAsString(evt("b-1"));
        consumer.onAuditEvent(payload);
        verify(auditDao, times(1)).insertAuditLogIdempotent(
                eq("b-1"), eq("u1"), eq("admin"), eq("login.success"),
                anyString(), anyString(), anyString(), eq("10.0.0.1"));
    }

    @Test
    @DisplayName("onAuditEvent(null / 空串) — 不触 DAO（脏数据防线）")
    void nullOrNullPayloadSkipped() {
        consumer.onAuditEvent(null);
        consumer.onAuditEvent("");
        consumer.onAuditEvent("   ");
        verifyNoInteractions(auditDao);
    }

    @Test
    @DisplayName("onAuditEvent(Map 缺 id) — 防脏数据 drop（不触 DAO）")
    void missingIdDrops() {
        Map<String, Object> bad = new LinkedHashMap<>(evt(null));
        bad.remove("id");
        consumer.onAuditEvent(bad);
        verifyNoInteractions(auditDao);
    }

    @Test
    @DisplayName("幂等插入失败 — 退化到 COUNT + 条件 INSERT（幂等等价）")
    void fallbackOnDataAccessException() {
        when(auditDao.insertAuditLogIdempotent(anyString(), any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new DataAccessException("unsupported") {});
        when(auditDao.countAuditLog("c-1")).thenReturn(0);

        consumer.onAuditEvent(evt("c-1"));

        // 幂等插入抛 → 退化 COUNT=0 → 普通插入（DAO 层共 3 次交互）
        verify(auditDao).insertAuditLogIdempotent(anyString(), any(), any(), any(),
                anyString(), any(), any(), anyString());
        verify(auditDao).countAuditLog("c-1");
        verify(auditDao).insertAuditLog(eq("c-1"), eq("u1"), eq("admin"),
                eq("login.success"), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("幂等插入失败且 COUNT>0 — 重放跳过，不再插入")
    void fallbackSkipsExistingRow() {
        when(auditDao.insertAuditLogIdempotent(anyString(), any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new DataAccessException("unsupported") {});
        when(auditDao.countAuditLog("c-1")).thenReturn(1);

        consumer.onAuditEvent(evt("c-1"));

        verify(auditDao, never()).insertAuditLog(anyString(), any(), any(), any(),
                any(), any(), any(), any());
    }

    @Test
    @DisplayName("AuditEventProducer.publish — EventBus 不可用 → 经 DAO 走重试表 ecos_runtime_audit_retry")
    void producerFallbackToRetryTable() throws Exception {
        // 清空 eventBus 模拟 Kafka 不可用
        var f = AuditEventProducer.class.getDeclaredField("eventBus");
        f.setAccessible(true);
        f.set(producer, null);

        producer.publish(AuditEventProducer.AuditEvent.of(
                "u1", "admin", "login.fail", "/api/v1/auth/login", "DENIED", "nope", "10.0.0.1"));

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(auditDao).insertAuditRetry(anyString(), payloadCaptor.capture());
        String payloadJson = payloadCaptor.getValue();
        assertTrue(payloadJson.contains("\"action\":\"login.fail\""),
                "重试表 payload 必须保留原始事件: " + payloadJson);
    }

    @Test
    @DisplayName("AuditEventProducer.publish — EventBus 正常 → 发布到 ecos.audit，不写重试表")
    void producerHappyPathPublishesOnly() {
        producer.publish(AuditEventProducer.AuditEvent.of(
                "u1", "admin", "login.ok", "/api/v1/auth/login", "SUCCESS", "ok", "10.0.0.1"));

        verify(eventBus).publish(eq(KafkaTopics.AUDIT), anyMap());
        verify(auditDao, never()).insertAuditRetry(anyString(), anyString());
    }
}
