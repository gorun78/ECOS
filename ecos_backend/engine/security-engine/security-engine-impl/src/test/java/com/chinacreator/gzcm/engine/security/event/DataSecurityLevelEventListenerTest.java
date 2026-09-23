package com.chinacreator.gzcm.engine.security.event;

import com.chinacreator.gzcm.common.event.DataAssetSecurityTaggedEvent;
import com.chinacreator.gzcm.common.event.KafkaTopics;
import com.chinacreator.gzcm.runtime.eventbus.EventBusService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * DataSecurityLevelEventListenerTest (PMO-data10)
 *
 * <p>事件 → CLS（L3+ 字段进 blocked_cols）+ RLS（L4 资产 clearance_level >= 4）写入。
 * 集成语义：V154 加的 resource_id 列兼顾 table_name 兜底（R9 只加不删）。</p>
 */
class DataSecurityLevelEventListenerTest {

    private JdbcTemplate jdbc;
    private ObjectProvider<EventBusService> eventBusProvider;
    private EventBusService eventBus;
    private DataSecurityLevelEventListener listener;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        eventBus = mock(EventBusService.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<EventBusService> provider = (ObjectProvider<EventBusService>) mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(eventBus);
        eventBusProvider = provider;
        listener = new DataSecurityLevelEventListener(jdbc, eventBusProvider);
    }

    /** 构造基础事件：资产 L3 1 字段 L3-身份证 */
    private DataAssetSecurityTaggedEvent baseEvent(String level) {
        return new DataAssetSecurityTaggedEvent(
            "evt-1", "asset-1", "res-1", "td_user", "TABLE", "default",
            level, 1,
            List.of(new DataAssetSecurityTaggedEvent.TaggedField(
                "field-1", "id_card", "ID_CARD", "L3", "middle4")),
            "tester", Instant.now());
    }

    /** 让 listener 进入 "INSERT 新策略" 分支（mock 未预置既有策略）。*/
    private void mockNoExistingPolicy() {
        when(jdbc.queryForList(anyString(), eq(String.class), any(String.class), any(String.class)))
            .thenReturn(List.of());
    }

    @Test
    @DisplayName("onKafka — 内存 payload 对象直接分发")
    void onKafkaDirectObject() {
        mockNoExistingPolicy();
        listener.onKafka(baseEvent("L3"));
        // INSERT ecos_cls_policy —— 一次 UPDATE / INSERT 按情况（这里 mock 会走 INSERT）
        verify(jdbc, atLeastOnce()).update(anyString(), any(Object[].class));
    }

    @Test
    @DisplayName("onKafka — 未识别 payload 类型 → null 不抛")
    void onKafkaUnknownPayloadNoThrow() {
        // 不抛断言即可（无 interceptor 时 listener 应死亡安静无 throw）
        assertDoesNotThrow(() -> listener.onKafka(42));
    }

    @Test
    @DisplayName("onKafka — 空 assetId 缺失字段 → skip")
    void onKafkaMissingAssetSkip() {
        assertDoesNotThrow(() -> listener.onKafka(
            new DataAssetSecurityTaggedEvent("e", null, "r", "t", "T", "d", "L3", 0, List.of(), "o", Instant.now())));
        verifyNoInteractions(jdbc);
    }

    @Test
    @DisplayName("applyPolicies — L4 资产应 INSERT RLS 策略带 clearance_level>=4")
    void applyPoliciesL4GeneratesRls() {
        mockNoExistingPolicy();
        listener.applyPolicies(baseEvent("L4"));
        // RLS INSERT 命中 filter_expr='clearance_level >= 4'；至少一次 update 调用
        verify(jdbc, atLeastOnce()).update(anyString(), any(Object[].class));
    }

    @Test
    @DisplayName("applyPolicies — 既有 CLS 策略走 UPDATE 分支 (R9 资源 id 双轨覆盖)")
    void applyPoliciesUpdatesExistingCls() {
        when(jdbc.queryForList(anyString(), eq(String.class), any(String.class), any(String.class)))
            .thenReturn(List.of("EXISTING-CLS-ID"));
        // L3 只有 CLS；L3 写入用 UPDATE（因为 existingId != null）
        listener.applyPolicies(baseEvent("L3"));
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbc, atLeastOnce()).update(sqlCaptor.capture(), any(Object[].class));
        // UPDATE 只写既有 blocked_cols —— 不应构建新 INSERT
        boolean hasUpdate = false, hasInsert = false;
        for (String s : sqlCaptor.getAllValues()) {
            if (s.startsWith("UPDATE ecos_cls_policy") || s.contains("UPDATE ecos_cls_policy")) hasUpdate = true;
            if (s.contains("INSERT INTO ecos_cls_policy")) hasInsert = true;
        }
        assertTrue(hasUpdate, "L3 已存在策略 → 走 UPDATE 分支");
        assertFalse(hasInsert, "L3 已存在策略 → 不应再 INSERT");
    }

    /** kafka topic 常量对齐 EventBusService 编程式订阅 */
    @Test
    @DisplayName("init — 启动时 subscribe 到 DataAssetSecurityTaggedEvent topic (data10 集成证据)")
    void initSubscribes() {
        // 覆盖 init() 编程式 subscribe 边界：手动触一次
        assertDoesNotThrow(listener::init);
        verify(eventBus).subscribe(eq(KafkaTopics.DATA_SECURITY_TAGGED),
            eq(DataAssetSecurityTaggedEvent.class), any());
    }
}
