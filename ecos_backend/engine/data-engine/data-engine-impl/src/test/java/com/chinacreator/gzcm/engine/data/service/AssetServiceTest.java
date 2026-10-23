package com.chinacreator.gzcm.engine.data.service;

import com.chinacreator.gzcm.common.event.DataAssetSecurityTaggedEvent;
import com.chinacreator.gzcm.common.event.KafkaTopics;
import com.chinacreator.gzcm.common.exception.DataBridgeException;
import com.chinacreator.gzcm.common.exception.ForbiddenException;
import com.chinacreator.gzcm.common.security.rls.RlsExecutionContract;
import com.chinacreator.gzcm.engine.data.dao.AssetDao;
import com.chinacreator.gzcm.engine.data.dto.*;
import com.chinacreator.gzcm.runtime.eventbus.EventBusService;
import com.chinacreator.gzcm.sysman.abac.model.AbacContext;
import com.chinacreator.gzcm.sysman.abac.service.IAbacPermissionChecker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * AssetServiceTest — PMO-data10 资产 CRUD / 分级 CRUD / 字段打标 / LLM推荐→人工确认 / ABAC 前置。
 *
 * <p>数据访问收口至 {@link AssetDao}（架构铁律 §2.5 / IR06）后，单测改为对 <b>mock 的
 * AssetDao</b> 打桩：Service 层再无 JdbcTemplate 依赖，responsibility 稳定。</p>
 *
 * <p>覆盖用户验收 §5 必需：</p>
 * <ul>
 *   <li>createAsset 校验 td_data_resource 存在 (ASSET-001)</li>
 *   <li>updateAsset COALESCE 语义 (缺失原值) — Service 层行前查 + DAO 更新</li>
 *   <li>deleteAsset 逻辑删除（DAO 落 is_deleted）</li>
 *   <li>tagAssetFields confirmed=true → 发 Kafka topic DATA_SECURITY_TAGGED</li>
 *   <li>tagAssetFields confirmed=false → 仅写 recommend_*，不发事件</li>
 *   <li>ABAC 三态（#142/B4-3e-55／差距 D-B4-85）：DENY → 403 {@code ForbiddenException}；
 *       Bean 未装配／策略评估抛异常 → 503 {@code ECOS-SEC-501}。三态<b>都</b>不放行、不落库、不发事件，
 *       但对外状态行与机读码必须互不相同（§4.7.2「不可用 ≠ 无权限 ≠ 空结果」）</li>
 * </ul>
 */
class AssetServiceTest {

    private AssetDao assetDao;
    private EventBusService eventBus;
    private IAbacPermissionChecker abac;
    private AssetService service;

    @BeforeEach
    void setUp() {
        assetDao = mock(AssetDao.class);
        eventBus = mock(EventBusService.class);
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
        when(busProvider.getIfAvailable()).thenReturn(eventBus);
        this.service = new AssetService(assetDao, busProvider, abacProvider);
        // ABAC 默认 PERMIT（fail-safe baseline）
        try {
            when(abac.check(any(AbacContext.class))).thenReturn(IAbacPermissionChecker.Decision.PERMIT);
        } catch (IAbacPermissionChecker.PolicyEvaluationException e) {
            throw new IllegalStateException("mock Abac 默认 PERMIT 不应抛", e);
        }
    }

    /** 构造既有的资产 VO（含 assetId / resourceId），供 getAsset 走 mock 回包。 */
    private DataAssetVO existingAsset(String assetId, String resourceId, String level) {
        DataAssetVO vo = new DataAssetVO();
        vo.setAssetId(assetId);
        vo.setResourceId(resourceId);
        vo.setResourceName("mock");
        vo.setSensitivityLevel(level);
        vo.setDomain("default");
        return vo;
    }

    /** 模拟物理表 td_data_resource 的一行（AssetService.fromPhysicalResourceRow 消费）。 */
    private Map<String, Object> physicalRow(String resourceId) {
        return Map.of(
            "resource_id", resourceId,
            "resource_name", "mock_table",
            "resource_type", "TABLE",
            "datasource_id", "ds-1",
            "description", "d",
            "field_count", 3,
            "record_count", 100L,
            "layer", "CURATED"
        );
    }

    @Test
    @DisplayName("createAsset — td_data_resource 不存在时抛 ASSET-001")
    void createAssetRejectsUnknownResource() {
        DataAssetSaveDTO dto = new DataAssetSaveDTO();
        dto.setResourceId("ghost");
        dto.setAssetName("Ghost");
        when(assetDao.countResourceById("ghost")).thenReturn(0);

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> service.createAsset(dto));
        assertTrue(e.getMessage().contains("ASSET-001"));
    }

    @Test
    @DisplayName("createAsset — resource 存在时 DAO 落行 + Kafka 审计")
    void createAssetInsertsAndAudits() {
        DataAssetSaveDTO dto = new DataAssetSaveDTO();
        dto.setResourceId("res-1");
        dto.setAssetName("RealAsset");
        dto.setDomain("default");
        dto.setOperator("tester");

        when(assetDao.countResourceById("res-1")).thenReturn(1);
        when(assetDao.countAssetByResourceAndDomain("res-1", "default")).thenReturn(0);
        when(assetDao.physicalResourceRow("res-1")).thenReturn(List.of(physicalRow("res-1")));
        // insertAsset 是 void：Mockito 默认为 no-op

        DataAssetVO vo = service.createAsset(dto);
        assertNotNull(vo);
        assertEquals("res-1", vo.getResourceId());
        assertEquals("L1", vo.getSensitivityLevel());
        verify(assetDao).insertAsset(anyString(), eq(dto), eq("L1"), eq("default"), eq("tester"), any(Timestamp.class));
        verify(eventBus, atLeastOnce()).publish(eq(KafkaTopics.AUDIT), anyMap());
    }

    @Test
    @DisplayName("updateAsset — 依赖既有资产存在；否则 ASSET-010")
    void updateAssetCoalesce() {
        when(assetDao.findAssetRows("asset-1")).thenReturn(List.of(existingAsset("asset-1", "res-1", "L4")));

        DataAssetSaveDTO dto = new DataAssetSaveDTO();
        dto.setAssetName("Renamed");
        dto.setOperator("tester");
        service.updateAsset("asset-1", dto);

        verify(assetDao).updateAsset(eq("asset-1"), eq(dto), eq("L4"), eq("tester"));
    }

    @Test
    @DisplayName("updateAsset — 资产不存在 → ASSET-010")
    void updateAssetRejectsUnknownAsset() {
        when(assetDao.findAssetRows("ghost")).thenReturn(List.of());

        DataAssetSaveDTO dto = new DataAssetSaveDTO();
        dto.setAssetName("X");

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> service.updateAsset("ghost", dto));
        assertTrue(e.getMessage().contains("ASSET-010"));
    }

    @Test
    @DisplayName("deleteAsset — 走 DAO 逻辑删除 (softDeleteAsset)")
    void deleteAssetSoftDelete() {
        when(assetDao.softDeleteAsset("asset-1", "tester")).thenReturn(1);

        boolean ok = service.deleteAsset("asset-1", "tester");
        assertTrue(ok);
        // 逻辑删除收口到 DAO；本用例确认 Service 层不再走 raw JdbcTemplate
        verify(assetDao).softDeleteAsset("asset-1", "tester");
    }

    @Test
    @DisplayName("deleteAsset — DAO 命中 0 行 → 返回 false")
    void deleteAssetReturnsFalseWhenNoRow() {
        when(assetDao.softDeleteAsset("gone", "tester")).thenReturn(0);
        assertFalse(service.deleteAsset("gone", "tester"));
    }

    @Test
    @DisplayName("tagAssetFields confirmed=true 且 L3 命中 → 发 DATA_SECURITY_TAGGED 事件")
    void tagAssetFieldsL3ConfirmedPublishes() {
        when(assetDao.findAssetRows("asset-1")).thenReturn(List.of(existingAsset("asset-1", "res-1", "L1")));
        when(assetDao.countFieldDup(eq("asset-1"), eq("id_card"))).thenReturn(0);
        when(assetDao.maxConfirmedLevel("asset-1")).thenReturn("L3");

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

        verify(assetDao).insertField(eq("asset-1"), eq(it), anyString(),
            eq("ID_CARD"), eq("L3"), eq("middle4"), eq(true), eq("tester"), any(Timestamp.class));
        verify(assetDao).updateAssetAfterConfirmedTag("asset-1", "L3", "tester");
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
    @DisplayName("tagAssetFields confirmed=false → 仅落 recommend_* 不发事件 (红线)")
    void tagAssetFieldsL1RecommendedNoPublish() {
        when(assetDao.findAssetRows("asset-1")).thenReturn(List.of(existingAsset("asset-1", "res-1", "L1")));
        when(assetDao.countFieldDup(eq("asset-1"), eq("phone"))).thenReturn(0);

        DataAssetFieldTagDTO dto = new DataAssetFieldTagDTO();
        dto.setConfirmed(false);
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

        verify(assetDao, never()).updateAssetAfterConfirmedTag(anyString(), anyString(), anyString());
        verify(assetDao).updateAssetAfterPendingTag("asset-1", "llm-bot");
        verify(eventBus, never()).publish(eq(KafkaTopics.DATA_SECURITY_TAGGED), any(Object.class));
    }

    @Test
    @DisplayName("tagAssetFields — ABAC DENY → 抛 ForbiddenException 403 (Fail-Closed, #142/B4-3e-55 三态分离)")
    void tagAssetFieldsAbacDenySecurityException() throws Exception {
        when(abac.check(any(AbacContext.class))).thenReturn(IAbacPermissionChecker.Decision.DENY);
        when(assetDao.findAssetRows("asset-1")).thenReturn(List.of(existingAsset("asset-1", "res-1", "L1")));

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

        // #142/B4-3e-55（D-B4-85）：拒绝态由 SecurityException（HTTP 面塌成 500）改指自带 403 的具名承载。
        ForbiddenException ex = assertThrows(ForbiddenException.class,
            () -> service.tagAssetFields("asset-1", dto));
        assertEquals(403, ForbiddenException.DEFAULT_HTTP_STATUS, "拒绝态承载自带 403");
        assertEquals(403, DataBridgeException.resolveHttpStatus(ex.getHttpStatus()), "状态行跟随携带值");
        assertTrue(ex.getMessage().contains("ASSET-040"), "拒绝腿保留 ASSET-040 标签供日志检索");
        assertInstanceOf(RuntimeException.class, ex, "RuntimeException ⇒ @Transactional 回滚语义不变");
        verify(eventBus, never()).publish(eq(KafkaTopics.DATA_SECURITY_TAGGED), any());
        verify(assetDao, never()).insertField(anyString(), any(), anyString(), anyString(), anyString(), anyString(), anyBoolean(), anyString(), any(Timestamp.class));
        verify(assetDao, never()).updateField(anyString(), any(), anyString(), anyString(), anyString(), anyBoolean(), anyString());
    }

    @Test
    @DisplayName("tagAssetFields — ABAC 不可用（Bean 未装配）→ 503 未获裁决 (Fail-Closed, 2026-10-12 订正 + #142/B4-3e-55 分态)")
    void tagAssetFieldsAbacUnavailableFailsClosed() {
        // 复盘前实现：checker==null 仅 log.warn 放行（fail-open，违反铁律 #3 默认 DENY）；
        // 2026-10-12 订正：抛 SecurityException(ASSET-042) 不落库、不发事件——但三态在 HTTP 面塌成同一枚 500；
        // #142/B4-3e-55（D-B4-85）：不可用腿改抛 503＋ECOS-SEC-501（与 PeriodCloseService 同源），
        // 「不放行」侧（抛异常＝打标失败）一条不减。
        AssetService svc = serviceWith(null);
        when(assetDao.findAssetRows("asset-1")).thenReturn(List.of(existingAsset("asset-1", "res-1", "L1")));

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

        DataBridgeException ex = assertThrows(DataBridgeException.class,
            () -> svc.tagAssetFields("asset-1", dto));
        assertEquals(RlsExecutionContract.SEC_UNAVAILABLE_HTTP, ex.getHttpStatus(),
            "不可用态＝503，禁折成 403（无权限）或 500（内部错误）");
        assertEquals(503, DataBridgeException.resolveHttpStatus(ex.getHttpStatus()), "状态行跟随携带值");
        assertTrue(ex.getMessage().startsWith(RlsExecutionContract.SEC_UNAVAILABLE_CODE),
            "机读码单源＝ECOS-SEC-501，禁本域另立码面");
        assertTrue(ex.getMessage().contains("ASSET-042"), "未装配腿保留 ASSET-042 标签供日志检索");
        assertInstanceOf(RuntimeException.class, ex, "RuntimeException ⇒ @Transactional 回滚语义不变");
        verify(assetDao, never()).insertField(anyString(), any(), anyString(), anyString(), anyString(), anyString(), anyBoolean(), anyString(), any(Timestamp.class));
        verify(eventBus, never()).publish(eq(KafkaTopics.DATA_SECURITY_TAGGED), any());
    }

    @Test
    @DisplayName("tagAssetFields — ABAC 策略评估抛异常 → 503 未获裁决，禁折成「操作被拒绝」(#142/B4-3e-55)")
    void tagAssetFieldsAbacEvaluationFailureCarriesUnavailable() throws Exception {
        // 收口前：评估异常腿抛 SecurityException("ASSET-041: … 操作被拒绝")，
        // 既在 HTTP 面塌成 500，又用「被拒绝」措辞把「裁决没答」说成「用户没权限」（§4.7.2 反向折叠）。
        when(abac.check(any(AbacContext.class)))
            .thenThrow(new IAbacPermissionChecker.PolicyEvaluationException("OPA 连接中断"));
        when(assetDao.findAssetRows("asset-1")).thenReturn(List.of(existingAsset("asset-1", "res-1", "L1")));

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

        DataBridgeException ex = assertThrows(DataBridgeException.class,
            () -> service.tagAssetFields("asset-1", dto));
        assertEquals(RlsExecutionContract.SEC_UNAVAILABLE_HTTP, ex.getHttpStatus(),
            "评估失败＝裁决不可得＝503，禁与 DENY 的 403 或内部错误的 500 共用一枚载体");
        assertEquals(503, DataBridgeException.resolveHttpStatus(ex.getHttpStatus()), "状态行跟随携带值");
        assertTrue(ex.getMessage().startsWith(RlsExecutionContract.SEC_UNAVAILABLE_CODE),
            "机读码单源＝ECOS-SEC-501");
        assertTrue(ex.getMessage().contains("ASSET-041"), "评估失败腿保留 ASSET-041 标签供日志检索");
        assertFalse(ex.getMessage().contains("操作被拒绝"),
            "禁把「裁决没答」写成「操作被拒绝」（D-B4-85 的反向折叠侧）");
        assertInstanceOf(IAbacPermissionChecker.PolicyEvaluationException.class, ex.getCause(),
            "原异常作为 cause 保留，堆栈不因改承载而丢失");
        verify(assetDao, never()).insertField(anyString(), any(), anyString(), anyString(), anyString(), anyString(), anyBoolean(), anyString(), any(Timestamp.class));
        verify(eventBus, never()).publish(eq(KafkaTopics.DATA_SECURITY_TAGGED), any());
    }

    /** 以指定 ABAC checker（null = Bean 未装配）重建 AssetService。 */
    private AssetService serviceWith(IAbacPermissionChecker checker) {
        @SuppressWarnings("unchecked")
        org.springframework.beans.factory.ObjectProvider<IAbacPermissionChecker> provider =
            (org.springframework.beans.factory.ObjectProvider<IAbacPermissionChecker>)
                mock(org.springframework.beans.factory.ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(checker);
        @SuppressWarnings("unchecked")
        org.springframework.beans.factory.ObjectProvider<EventBusService> busProvider =
            (org.springframework.beans.factory.ObjectProvider<EventBusService>)
                mock(org.springframework.beans.factory.ObjectProvider.class);
        when(busProvider.getIfAvailable()).thenReturn(eventBus);
        return new AssetService(assetDao, busProvider, provider);
    }
}
