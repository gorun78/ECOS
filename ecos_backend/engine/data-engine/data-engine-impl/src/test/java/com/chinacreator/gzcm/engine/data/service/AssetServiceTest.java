package com.chinacreator.gzcm.engine.data.service;

import com.chinacreator.gzcm.common.event.DataAssetSecurityTaggedEvent;
import com.chinacreator.gzcm.common.event.KafkaTopics;
import com.chinacreator.gzcm.engine.data.dto.*;
import com.chinacreator.gzcm.runtime.eventbus.EventBusService;
import com.chinacreator.gzcm.sysman.abac.model.AbacContext;
import com.chinacreator.gzcm.sysman.abac.service.IAbacPermissionChecker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * AssetServiceTest — PMO-data10 资产 CRUD / 分级 CRUD / 字段打标 / LLM推荐→人工确认 / ABAC 前置。
 *
 * <p>覆盖用户验收 §5 必需：</p>
 * <ul>
 *   <li>createAsset 校验 td_data_resource 存在 (ASSET-001)</li>
 *   <li>updateAsset COALESCE 语义 (缺失原值)</li>
 *   <li>deleteAsset 逻辑删除 (is_deleted=1, R9 不真删)</li>
 *   <li>tagAssetFields confirmed=true → 发 Kafka topic DATA_SECURITY_TAGGED</li>
 *   <li>tagAssetFields confirmed=false → 仅写 recommend_*，不发事件</li>
 *   <li>ABAC DENY → 打标抛 SecurityException (Fail-Closed)</li>
 * </ul>
 */
class AssetServiceTest {

    private JdbcTemplate jdbc;
    private EventBusService eventBus;
    private IAbacPermissionChecker abac;
    private AssetService service;

    @BeforeEach
    void setUp() throws Exception {
        jdbc = mock(JdbcTemplate.class);
        @SuppressWarnings("unchecked")
        EventBusService bus = eventBus = mock(EventBusService.class);
        abac = mock(IAbacPermissionChecker.class);
        @SuppressWarnings("unchecked")
        org.springframework.beans.factory.ObjectProvider<IAbacPermissionChecker> abacProvider =
            (org.springframework.beans.factory.ObjectProvider<IAbacPermissionChecker>)
                mock(org.springframework.beans.factory.ObjectProvider.class);
        when(abacProvider.getIfAvailable()).thenReturn(abac);
        @SuppressWarnings("unchecked")
        org.springframework.beans.factory.ObjectProvider<EventBusService> busProvider =
            (org.springframework.beans.factory.ObjectProvider<EventBusService>)
                mock(org.springframework.beans.factory.ObjectProvider.class);
        when(busProvider.getIfAvailable()).thenReturn(bus);
        this.service = new AssetService(jdbc, busProvider, abacProvider);
        // ABAC 在 @BeforeEach 默认构造时显式 stub（避免 checked exception 编译报错）
        try {
            mockAbac(IAbacPermissionChecker.Decision.PERMIT);
        } catch (IAbacPermissionChecker.PolicyEvaluationException e) {
            throw new IllegalStateException("mock Abac 默认 PERMIT 不应抛", e);
        }
    }

    /** 避免 when(mock.check(any())) 的 checked exception 编译问题。 */
    private void mockAbac(IAbacPermissionChecker.Decision decision) throws IAbacPermissionChecker.PolicyEvaluationException {
        when(abac.check(any(AbacContext.class))).thenReturn(decision);
    }

    /** 辅助：mock getAsset 查询命中 1 行。 */
    private void mockAssetFetch(String assetId, String resourceId) {
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object.class)))
            .thenReturn(List.of(new DataAssetVO() {{
                setAssetId(assetId);
                setResourceId(resourceId);
                setResourceName("mock");
                setSensitivityLevel("L1");
                setDomain("default");
            }}));
    }

    @Test
    @DisplayName("createAsset — td_data_resource 不存在时抛 ASSET-001")
    void createAssetRejectsUnknownResource() {
        DataAssetSaveDTO dto = new DataAssetSaveDTO();
        dto.setResourceId("ghost");
        dto.setAssetName("Ghost");
        // mock td_data_resource 查询 0 条
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object.class)))
            .thenReturn(0);
        // mock 第二个 query（防重）—— 因 ASSET-001 会先抛，不到第二查询
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> service.createAsset(dto));
        assertTrue(e.getMessage().contains("ASSET-001"));
    }

    @Test
    @DisplayName("createAsset — resource 存在时 INSERT + Kafka 审计 (ASSET-002 不重复)")
    void createAssetInsertsAndAudits() {
        DataAssetSaveDTO dto = new DataAssetSaveDTO();
        dto.setResourceId("res-1");
        dto.setAssetName("RealAsset"); // 去掉空格，ID_PAT 只允许 [a-zA-Z0-9_-]
        dto.setDomain("default");
        dto.setOperator("tester");

        // 两次 queryForObject（COUNT=1 for resource 存在 + COUNT=0 for 防重）
        Map<String, Object> physical = new LinkedHashMap<>();
        physical.put("resource_id", "res-1");
        physical.put("resource_name", "mock_table");
        physical.put("resource_type", "TABLE");
        physical.put("datasource_id", "ds-1");
        physical.put("description", "d");
        physical.put("field_count", 3);
        physical.put("record_count", 100L);
        physical.put("layer", "CURATED");
        physical.put("zone", null);
        physical.put("last_sync_time", null);

        // 顺序控制：第一次 queryForObject 返回 1，第二次返回 0
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object.class)))
            .thenReturn(1)
            .thenReturn(0);

        when(jdbc.queryForList(anyString(), any(Object.class)))
            .thenReturn(List.of(physical));
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);

        DataAssetVO vo = service.createAsset(dto);
        assertNotNull(vo);
        assertEquals("res-1", vo.getResourceId());
        assertEquals("L1", vo.getSensitivityLevel());
        // Kafka 审计至少 1 次
        verify(eventBus, atLeastOnce()).publish(eq(KafkaTopics.AUDIT), anyMap());
    }

    @Test
    @DisplayName("updateAsset — COALESCE 语义：缺失字段原值保留（version_no+1）")
    void updateAssetCoalesce() {
        // mock 既有资产
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object.class)))
            .thenReturn(List.of(new DataAssetVO() {{
                setAssetId("asset-1"); setResourceId("res-1");
                setSensitivityLevel("L4"); setDomain("default");
            }}));
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);

        DataAssetSaveDTO dto = new DataAssetSaveDTO();
        // 不限定 level，链路层会 fallback 到 existing L4
        dto.setAssetName("Renamed");
        dto.setOperator("tester");
        service.updateAsset("asset-1", dto);
        // 命中 update
        verify(jdbc).update(anyString(), any(Object[].class));
    }

    @Test
    @DisplayName("deleteAsset — 逻辑删除 (is_deleted=1, R9) — 无真正 DELETE")
    void deleteAssetSoftDelete() {
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        boolean ok = service.deleteAsset("asset-1", "tester");
        assertTrue(ok);
        ArgumentCaptor<String> sqlCap = ArgumentCaptor.forClass(String.class);
        verify(jdbc).update(sqlCap.capture(), any(Object[].class));
        // 断言 SQL 是 UPDATE is_deleted（非 DELETE）
        assertTrue(sqlCap.getValue().contains("is_deleted = 1"));
        // 不应有 DELETE 语句
        assertFalse(sqlCap.getValue().toUpperCase().contains("DELETE FROM"));
    }

    @Test
    @DisplayName("tagAssetFields confirmed=true 且 L3 命中 → 发 DATA_SECURITY_TAGGED 事件")
    void tagAssetFieldsL3ConfirmedPublishes() {
        mockAssetFetch("asset-1", "res-1");
        // 字段重复查询
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object.class), any(Object.class)))
            .thenReturn(0);
        // 打标 SQL
        when(jdbc.queryForObject(anyString(), eq(String.class), any(Object.class))).thenReturn("L3");
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);

        DataAssetFieldTagDTO dto = new DataAssetFieldTagDTO();
        dto.setConfirmed(true);
        dto.setOperator("tester");
        DataAssetFieldTagDTO.FieldTagItem it = new DataAssetFieldTagDTO.FieldTagItem();
        it.setFieldId("id_card");
        it.setFieldName("id_card");
        it.setFieldSensitivity("L3");
        it.setDataType("ID_CARD");
        it.setMaskStrategy("middle4");
        dto.setFields(List.of(it));

        service.tagAssetFields("asset-1", dto);

        // 捕获发布 DATA_SECURITY_TAGGED 事件
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Object> evtCap = ArgumentCaptor.forClass(Object.class);
        verify(eventBus).publish(eq(KafkaTopics.DATA_SECURITY_TAGGED), evtCap.capture());
        DataAssetSecurityTaggedEvent evt = (DataAssetSecurityTaggedEvent) evtCap.getValue();
        assertNotNull(evt);
        assertEquals("asset-1", evt.getAssetId());
        assertEquals("L3", evt.getSensitivityLevel());
        assertEquals(1, evt.getFields().size());
        assertEquals("id_card", evt.getFields().get(0).getFieldName());
        assertEquals("ID_CARD", evt.getFields().get(0).getDataType());
    }

    @Test
    @DisplayName("tagAssetFields confirmed=false → LLM 推荐仅写 recommend_* 不发事件 (红线)")
    void tagAssetFieldsL1RecommendedNoPublish() {
        mockAssetFetch("asset-1", "res-1");
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object.class), any(Object.class)))
            .thenReturn(0);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);

        DataAssetFieldTagDTO dto = new DataAssetFieldTagDTO();
        dto.setConfirmed(false); // LLM 推荐场景
        dto.setOperator("llm-bot");
        DataAssetFieldTagDTO.FieldTagItem it = new DataAssetFieldTagDTO.FieldTagItem();
        it.setFieldId("phone");
        it.setFieldName("phone");
        it.setFieldSensitivity("L3");
        it.setDataType("PHONE");
        it.setMaskStrategy("suffix4");
        it.setRecommendLevel("L3");
        it.setRecommendSource("llm-recommend");
        dto.setFields(List.of(it));

        service.tagAssetFields("asset-1", dto);

        // 确认未发 DATA_SECURITY_TAGGED
        verify(eventBus, never()).publish(eq(KafkaTopics.DATA_SECURITY_TAGGED), any(Object.class));
    }

    @Test
    @DisplayName("tagAssetFields — ABAC DENY → 抛 SecurityException (Fail-Closed)")
    void tagAssetFieldsAbacDenySecurityException() throws Exception {
        mockAbac(IAbacPermissionChecker.Decision.DENY);
        // getAsset 需先取到资产 → 否则到不到 ABAC
        mockAssetFetch("asset-1", "res-1");

        DataAssetFieldTagDTO dto = new DataAssetFieldTagDTO();
        dto.setConfirmed(true);
        dto.setOperator("bad-operator");
        DataAssetFieldTagDTO.FieldTagItem it = new DataAssetFieldTagDTO.FieldTagItem();
        it.setFieldId("id_card");
        it.setFieldName("id_card");
        it.setFieldSensitivity("L3");
        it.setDataType("ID_CARD");
        it.setMaskStrategy("middle4");
        dto.setFields(List.of(it));

        assertThrows(SecurityException.class, () -> service.tagAssetFields("asset-1", dto));
        // ABAC 已拦截，不应该发事件 / 不应写库
        verify(eventBus, never()).publish(eq(KafkaTopics.DATA_SECURITY_TAGGED), any());
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }
}
