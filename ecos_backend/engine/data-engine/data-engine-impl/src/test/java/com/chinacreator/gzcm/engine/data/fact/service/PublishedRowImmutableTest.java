package com.chinacreator.gzcm.engine.data.fact.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.chinacreator.gzcm.common.data.DataDomainErrorCode;
import com.chinacreator.gzcm.common.exception.DataBridgeException;
import com.chinacreator.gzcm.engine.data.fact.FactType;
import com.chinacreator.gzcm.engine.data.fact.dao.AttributionFactMapper;
import com.chinacreator.gzcm.engine.data.fact.dao.CostFactMapper;
import com.chinacreator.gzcm.engine.data.fact.dao.ForecastSnapshotMapper;
import com.chinacreator.gzcm.engine.data.fact.dao.ResourceFactMapper;
import com.chinacreator.gzcm.engine.data.fact.dao.StageFactMapper;

/**
 * PublishedRowImmutableTest — F02-06-4：{@code dq_status=PUBLISHED} 后业务字段任何
 * UPDATE 路径 → 409 {@code ECOS-DATA-021}。
 *
 * <p>本撮唯一更新通道 = writeActionOutcome（touchUpdate）。PUBLISHED 行任何触达即
 * {@link FactImportService#guardPublished} 抛 {@link DataBridgeException}(409)，
 * 不触 update_time/update_by。PENDING/PASSED 行放行（可 touch）。</p>
 *
 * <p>与既有 {@code PublishedRowImmutabilityTest} 的差异：本类守卫的是
 * 「PUBLISHED 不可变 = 409 合同」在多事实类型下的通用性（ATTRIBUTION 路由），
 * 并额外断言 <b>httpStatus 精确为 409</b>（合同契约位），及 guardPublished 对
 * PENDING/PASSED 的放行语义（状态机前置状态集 6 元素）。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("F02-06-4 PUBLISHED 行不可变：409 ECOS-DATA-021（多事实类型路由）")
class PublishedRowImmutableTest {

    @Mock private AttributionFactMapper attributionMapper;
    @Mock private StageFactMapper stageMapper;
    @Mock private ResourceFactMapper resourceMapper;
    @Mock private CostFactMapper costMapper;
    @Mock private ForecastSnapshotMapper forecastMapper;

    private FactImportService service() {
        return new FactImportService(attributionMapper, stageMapper, resourceMapper,
                costMapper, forecastMapper,
                (t, rows, ctx) -> java.util.List.of(),   // DqRowGate 无拒
                p -> false,                                // PeriodLock 全开
                Optional.empty(), 0.9);
    }

    @Test
    @DisplayName("ATTRIBUTION：PUBLISHED 行写 outcome → 409 ECOS-DATA-021，httpStatus 精确 409，不触 touchUpdate")
    void publishedAttributionRowRejected409() {
        when(attributionMapper.getDqStatusById("attr-pub")).thenReturn("PUBLISHED");
        DataBridgeException ex = assertThrows(DataBridgeException.class,
                () -> service().writeActionOutcome(req("ATTRIBUTION", "attr-pub", "ADOPTED")));
        // 错误消息首段 = ECOS-DATA-021
        assertTrue(ex.getMessage().startsWith(DataDomainErrorCode.ECOS_DATA_021),
                "消息须以 ECOS-DATA-021 开头，实际: " + ex.getMessage());
        // 精确 409
        assertTrue(ex.getHttpStatus() == 409, "httpStatus 必须精确 409 Conflict，实际 " + ex.getHttpStatus());
        // 守卫先行 → 绝不触 update
        verify(attributionMapper, never()).touchUpdate(anyString(), anyString());
    }

    @Test
    @DisplayName("COST：PUBLISHED 行写 outcome → 409 ECOS-DATA-021，且不路由到其它表 mapper")
    void publishedCostRowRejected409() {
        when(costMapper.getDqStatusById("cost-pub")).thenReturn("PUBLISHED");
        DataBridgeException ex = assertThrows(DataBridgeException.class,
                () -> service().writeActionOutcome(req("COST", "cost-pub", "ADOPTED")));
        assertTrue(ex.getMessage().contains(DataDomainErrorCode.ECOS_DATA_021));
        assertTrue(ex.getHttpStatus() == 409);
        // 路由隔离：COST 走 costMapper，不触 stage/attribution
        verify(costMapper, never()).touchUpdate(anyString(), anyString());
        verify(stageMapper, never()).touchUpdate(anyString(), anyString());
        verify(attributionMapper, never()).touchUpdate(anyString(), anyString());
    }

    @Test
    @DisplayName("PENDING / PASSED / REJECTED 前置状态 → guardPublished 放行（不抛 409）")
    void nonPublishedPreStatesAllowed() {
        FactImportService svc = service();
        for (String st : new String[]{"PENDING", "PASSED", "REJECTED"}) {
            // guardPublished 直调：非 PUBLISHED 不得抛
            assertDoesNotThrow(() -> svc.guardPublished(FactType.STAGE, "id", st),
                    "状态 " + st + " 不应被 guardPublished 拒");
        }
        // PUBLISHED 必抛
        DataBridgeException ex = assertThrows(DataBridgeException.class,
                () -> svc.guardPublished(FactType.ATTRIBUTION, "id", "PUBLISHED"));
        assertTrue(ex.getHttpStatus() == 409);
    }

    @Test
    @DisplayName("PENDING 行写 outcome → 放行触达 touchUpdate 1 次（正向对照，证明守卫仅拦 PUBLISHED）")
    void pendingRowOutcomeTouches() {
        when(resourceMapper.getDqStatusById("res-pending")).thenReturn("PENDING");
        when(resourceMapper.touchUpdate(anyString(), anyString())).thenReturn(1);
        assertDoesNotThrow(() -> service().writeActionOutcome(req("RESOURCE", "res-pending", "DONE")));
        verify(resourceMapper).touchUpdate(anyString(), any());
    }

    @Test
    @DisplayName("PUBLISHED 行存在 → getDqStatus 命中后立即拒，不触任何 mapper touch（守卫前置）")
    void guardRunsBeforeTouchOnAllTypes() {
        assertTypeRejectedThenNoTouch("STAGE", "pub");
        assertTypeRejectedThenNoTouch("RESOURCE", "pub");
        assertTypeRejectedThenNoTouch("FORECAST_SNAPSHOT", "pub");

        verify(stageMapper, never()).touchUpdate(anyString(), anyString());
        verify(resourceMapper, never()).touchUpdate(anyString(), anyString());
        verify(forecastMapper, never()).touchUpdate(anyString(), anyString());
    }

    private void assertTypeRejectedThenNoTouch(String type, String factId) {
        switch (type) {
            case "STAGE" -> when(stageMapper.getDqStatusById(factId)).thenReturn("PUBLISHED");
            case "RESOURCE" -> when(resourceMapper.getDqStatusById(factId)).thenReturn("PUBLISHED");
            default -> when(forecastMapper.getDqStatusById(factId)).thenReturn("PUBLISHED");
        }
        DataBridgeException ex = assertThrows(DataBridgeException.class,
                () -> service().writeActionOutcome(req(type, factId, "ADOPTED")));
        assertTrue(ex.getHttpStatus() == 409, type + " PUBLISHED 应 409，实际 " + ex.getHttpStatus());
    }

    private static com.chinacreator.gzcm.engine.data.fact.model.ActionOutcomeRequest req(
            String type, String factId, String outcome) {
        com.chinacreator.gzcm.engine.data.fact.model.ActionOutcomeRequest r =
                new com.chinacreator.gzcm.engine.data.fact.model.ActionOutcomeRequest();
        r.setFactType(type);
        r.setFactId(factId);
        r.setOutcome(outcome);
        return r;
    }
}
