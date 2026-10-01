package com.chinacreator.gzcm.engine.security.event;

import com.chinacreator.gzcm.common.event.DataAssetSecurityTaggedEvent;
import com.chinacreator.gzcm.common.event.KafkaTopics;
import com.chinacreator.gzcm.engine.security.dao.DataSecurityLevelDao;
import com.chinacreator.gzcm.runtime.eventbus.EventBusService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * DataSecurityLevelEventListenerTest (PMO-data10)
 *
 * <p>事件 → CLS（L3+ 字段进 blocked_cols）+ RLS（L4 资产 clearance_level >= 4）写入。
 * 集成语义：V154 加的 resource_id 列兼顾 table_name 兜底（R9 只加不删）。
 * R2-B3b 数据访问收口后改以 {@link DataSecurityLevelDao} 为边界验证存在/新建分支。</p>
 */
class DataSecurityLevelEventListenerTest {

    private DataSecurityLevelDao dao;
    private ObjectProvider<EventBusService> eventBusProvider;
    private EventBusService eventBus;
    private DataSecurityLevelEventListener listener;

    @BeforeEach
    void setUp() {
        dao = mock(DataSecurityLevelDao.class);
        eventBus = mock(EventBusService.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<EventBusService> provider = (ObjectProvider<EventBusService>) mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(eventBus);
        eventBusProvider = provider;
        listener = new DataSecurityLevelEventListener(dao, eventBusProvider);
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

    /** 让 listener 进入 "INSERT 新策略" 分支（既有策略查询返回 null）。*/
    private void mockNoExistingPolicy() {
        when(dao.findExistingPolicy(anyString(), anyString(), anyString())).thenReturn(null);
    }

    @Test
    @DisplayName("onKafka — 内存 payload 对象直接分发")
    void onKafkaDirectObject() {
        mockNoExistingPolicy();
        listener.onKafka(baseEvent("L3"));
        // INSERT ecos_cls_policy —— DAO 层应触发 CLS 新建
        verify(dao, atLeastOnce()).insertClsPolicy(anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString());
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
        verifyNoInteractions(dao);
    }

    @Test
    @DisplayName("applyPolicies — L4 资产应 INSERT RLS 策略带 clearance_level>=4")
    void applyPoliciesL4GeneratesRls() {
        mockNoExistingPolicy();
        listener.applyPolicies(baseEvent("L4"));
        // RLS INSERT 命中 filter_expr='clearance_level >= 4'
        verify(dao, atLeastOnce()).insertRlsPolicy(anyString(), anyString(), anyString(), anyString(),
                eq("clearance_level >= 4"), anyString(), anyString());
    }

    @Test
    @DisplayName("applyPolicies — 既有 CLS 策略走 UPDATE 分支 (R9 资源 id 双轨覆盖)")
    void applyPoliciesUpdatesExistingCls() {
        when(dao.findExistingPolicy(anyString(), anyString(), anyString()))
            .thenReturn("EXISTING-CLS-ID");
        // L3 只有 CLS；L3 写入用 UPDATE（因为 existingId != null）
        listener.applyPolicies(baseEvent("L3"));
        verify(dao, atLeastOnce()).updateClsPolicy(anyString(), anyString(), anyString(), eq("EXISTING-CLS-ID"));
        verify(dao, never()).insertClsPolicy(anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString());
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
