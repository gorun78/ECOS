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
    @DisplayName("onAuditEvent(Map) — 经 DAO 幂等插入 ecos_audit_log（数值 id 原样下传）")
    void mapPayloadInsertsIntoAuditLog() {
        consumer.onAuditEvent(evt("1001"));

        verify(auditDao).insertAuditLogIdempotent(
                eq("1001"), eq("u1"), eq("admin"), eq("login.success"),
                eq("/api/v1/auth/login"), eq("SUCCESS"),
                eq("{\"ip\":\"10.0.0.1\"}"), eq("10.0.0.1"));
    }

    @Test
    @DisplayName("onAuditEvent(JSON String) — 字符串 payload 反序列化后同样经 DAO 落库")
    void jsonStringPayloadInserts() throws Exception {
        String payload = new com.fasterxml.jackson.databind.ObjectMapper()
                .writeValueAsString(evt("1002"));
        consumer.onAuditEvent(payload);
        verify(auditDao, times(1)).insertAuditLogIdempotent(
                eq("1002"), eq("u1"), eq("admin"), eq("login.success"),
                anyString(), anyString(), anyString(), eq("10.0.0.1"));
    }

    @Test
    @DisplayName("驼峰主体键（operation/entityType/entityId）→ 经 DAO 回填主体列")
    void camelCaseSubjectKeysFillSubjectColumns() {
        Map<String, Object> payload = evt("1003");
        payload.put("operation", "CREATE");
        payload.put("entityType", "AgentConfig");
        payload.put("entityId", "agent-101");

        consumer.onAuditEvent(payload);

        verify(auditDao).fillSubjectColumns("1003", "CREATE", "AgentConfig", "agent-101");
    }

    @Test
    @DisplayName("无主体键 — 不回填主体列（不打无谓 UPDATE）")
    void absentSubjectKeysSkipFill() {
        consumer.onAuditEvent(evt("1004"));

        verify(auditDao, never()).fillSubjectColumns(any(), any(), any(), any());
    }

    @Test
    @DisplayName("退化路径（幂等插入抛异常）同样回填主体列")
    void fallbackPathAlsoFillsSubjectColumns() {
        when(auditDao.insertAuditLogIdempotent(anyString(), any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new DataAccessException("unsupported") {});
        when(auditDao.countAuditLog("1005")).thenReturn(0);

        Map<String, Object> payload = evt("1005");
        payload.put("operation", "CREATE");
        payload.put("entityType", "AgentConfig");
        payload.put("entityId", "agent-303");
        consumer.onAuditEvent(payload);

        verify(auditDao).fillSubjectColumns("1005", "CREATE", "AgentConfig", "agent-303");
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
    @DisplayName("非数值 id（32 位 UUID 十六进制）— 改写为 bigint 可绑的确定性主键，不原样下传")
    void hexUuidIdRewrittenToNumeric() {
        // D142 实证原值：impersonate / SecurityAuditController / CryptoKeyAdminController 发的 32 位 hex
        String rawId = "24edbdb69ca442f2aae2759307b49b3c";
        consumer.onAuditEvent(evt(rawId));

        ArgumentCaptor<String> idCaptor = ArgumentCaptor.forClass(String.class);
        verify(auditDao).insertAuditLogIdempotent(idCaptor.capture(), eq("u1"), eq("admin"),
                eq("login.success"), anyString(), anyString(), anyString(), eq("10.0.0.1"));
        String bound = idCaptor.getValue();
        assertNotEquals(rawId, bound, "非数值 id 不得原样绑到 bigint 主键");
        assertTrue(AuditKafkaConsumer.isNumericId(bound), "改写后必须是合法 bigint 字面量: " + bound);
        assertEquals(AuditKafkaConsumer.deriveNumericId(rawId), bound, "改写须确定性（重放幂等）");

        // 语义列不得因改写而丢失
        verify(auditDao, never()).countAuditLog(anyString());
        verify(auditDao, never()).insertAuditLog(anyString(), any(), any(), any(),
                any(), any(), any(), any());
    }

    @Test
    @DisplayName("非数值 id 重放 — 同一原值恒得同一主键（幂等 ON CONFLICT 仍生效）")
    void hexUuidIdRewriteIsDeterministic() {
        String rawId = "24edbdb69ca442f2aae2759307b49b3c";
        consumer.onAuditEvent(evt(rawId));
        consumer.onAuditEvent(evt(rawId));

        ArgumentCaptor<String> idCaptor = ArgumentCaptor.forClass(String.class);
        verify(auditDao, times(2)).insertAuditLogIdempotent(idCaptor.capture(), any(), any(),
                any(), any(), any(), any(), any());
        assertEquals(idCaptor.getAllValues().get(0), idCaptor.getAllValues().get(1),
                "同 rawId 两次投递必须绑同一个 bigint 主键，否则重放会产生重复行");
    }

    @Test
    @DisplayName("非数值 id + EventBus 不可用退化路径 — countAuditLog/insertAuditLog 也用改写后主键")
    void hexUuidIdRewriteAlsoCoversFallback() {
        String rawId = "24edbdb69ca442f2aae2759307b49b3c";
        String expected = AuditKafkaConsumer.deriveNumericId(rawId);
        when(auditDao.insertAuditLogIdempotent(anyString(), any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new DataAccessException("unsupported") {});
        when(auditDao.countAuditLog(expected)).thenReturn(0);

        consumer.onAuditEvent(evt(rawId));

        verify(auditDao).countAuditLog(expected);
        verify(auditDao).insertAuditLog(eq(expected), eq("u1"), eq("admin"),
                eq("login.success"), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("数值 id 不触发改写（kb/ontology/workspace 发布端口径不变）")
    void numericIdLeftUntouched() {
        String numeric = "1730000000000123";
        consumer.onAuditEvent(evt(numeric));

        verify(auditDao).insertAuditLogIdempotent(eq(numeric), any(), any(), any(),
                any(), any(), any(), any());
        assertTrue(AuditKafkaConsumer.isNumericId(numeric));
        assertFalse(AuditKafkaConsumer.isNumericId("24edbdb69ca442f2aae2759307b49b3c"));
        // 19 位但越界：正则放得过的形态，parseLong 必须判非数值（否则绑参报 bigint 越界）
        assertFalse(AuditKafkaConsumer.isNumericId("99999999999999999999"));
    }

    @Test
    @DisplayName("幂等插入失败 — 退化到 COUNT + 条件 INSERT（幂等等价）")
    void fallbackOnDataAccessException() {
        when(auditDao.insertAuditLogIdempotent(anyString(), any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new DataAccessException("unsupported") {});
        when(auditDao.countAuditLog("2001")).thenReturn(0);

        consumer.onAuditEvent(evt("2001"));

        // 幂等插入抛 → 退化 COUNT=0 → 普通插入（DAO 层共 3 次交互）
        verify(auditDao).insertAuditLogIdempotent(anyString(), any(), any(), any(),
                anyString(), any(), any(), anyString());
        verify(auditDao).countAuditLog("2001");
        verify(auditDao).insertAuditLog(eq("2001"), eq("u1"), eq("admin"),
                eq("login.success"), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("幂等插入失败且 COUNT>0 — 重放跳过，不再插入")
    void fallbackSkipsExistingRow() {
        when(auditDao.insertAuditLogIdempotent(anyString(), any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new DataAccessException("unsupported") {});
        when(auditDao.countAuditLog("2002")).thenReturn(1);

        consumer.onAuditEvent(evt("2002"));

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
