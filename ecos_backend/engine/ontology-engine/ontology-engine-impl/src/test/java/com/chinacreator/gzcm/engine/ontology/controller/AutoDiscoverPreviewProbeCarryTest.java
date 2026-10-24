package com.chinacreator.gzcm.engine.ontology.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.exception.DataBridgeException;
import com.chinacreator.gzcm.common.exception.NotFoundException;
import com.chinacreator.gzcm.engine.ontology.service.AutoDiscoverPreviewQueryService;
import com.chinacreator.gzcm.engine.ontology.service.AutoDiscoverService;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 数据源存在性探测通道的不可用承载面（差距 <b>D-B4-88</b> 首枚收口件，#142/B4-3e-58）。
 *
 * <p><b>缺陷形态</b>：{@code AutoDiscoverPreviewController.datasourceExists} 的
 * {@code catch (Exception)} 顶层 {@code return true}，注释自证「宽松降级：允许预览继续」——
 * 判定通道（{@code countDatasourceById}）故障时把「查不了」直接洗成「数据源在」，
 * 于是预览面照常拉 DW 资源清单并渲染成功信封，违详设 §4.7.2「不可用 ≠ 无权限 ≠ 空结果」
 * （同族已收口：D-B4-82／D-B4-84 的「注释自证宽容」形态）。</p>
 *
 * <p><b>本类钉三件事</b>：</p>
 * <ol>
 *   <li>探测通道抛异常 ⇒ 端点<b>上抛</b>携带 503 ＋ {@code ECOS-SYS-503} 的
 *       {@link DataBridgeException}，而不是被方法自身的 {@code catch (Exception)} 折成
 *       {@code internalError}（反向折叠＝状态行照旧丢，见 {@link #carrierIsNotRefoldedAtTheEndpoint}）；</li>
 *   <li>探测结果为 0 行（真实「不存在」）⇒ 不得产出 503 载体，「不存在」与「查不了」必须分态
 *       —— 本用例同时钉住保护腿的必然副产：{@code NotFoundException extends DataBridgeException}，
 *       故收口后它按携带的 404 出口渲染，不再被兜底折成 {@code internalError(code=-1)}；</li>
 *   <li>探测为真 ⇒ 主流程照常（收口不许把只读预览一起打死，成功码取 {@code CODE_SUCCESS} 单源）。</li>
 * </ol>
 */
class AutoDiscoverPreviewProbeCarryTest {

    private AutoDiscoverPreviewQueryService previewQueryService;
    private AutoDiscoverService autoDiscoverService;
    private AutoDiscoverPreviewController controller;

    @BeforeEach
    void setUp() {
        previewQueryService = mock(AutoDiscoverPreviewQueryService.class);
        autoDiscoverService = mock(AutoDiscoverService.class);
        controller = new AutoDiscoverPreviewController(previewQueryService, autoDiscoverService);
    }

    private static Map<String, Object> body(String datasourceId, List<String> resourceNames) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("datasourceId", datasourceId);
        b.put("resourceNames", resourceNames);
        return b;
    }

    @Test
    @DisplayName("D-B4-88①：存在性探测通道故障 → 端点上抛 503 载体（禁折成「允许预览继续」）")
    void probeChannelFailureCarriesExplicitUnavailable() {
        when(previewQueryService.countDatasourceById("ds-1"))
                .thenThrow(new IllegalStateException("datasource existence query failed"));

        DataBridgeException thrown = assertThrows(DataBridgeException.class,
                () -> controller.preview("DOM", body("ds-1", List.of("t_order"))),
                "判定通道故障必须显式承载，返回值/信封都不是承载面");
        assertEquals(503, thrown.getHttpStatus(), "不可用态的状态行必须是 503");
        assertTrue(thrown.getMessage().contains("ECOS-SYS-503"),
                "机读码必须随载体一起上抛，实读=" + thrown.getMessage());

        // 关键反证：探测失败后不得再去拉 DW 资源清单（宽松降级的可观测后果＝预览照常成功）
        verify(autoDiscoverService, never()).loadCuratedResources();
    }

    @Test
    @DisplayName("D-B4-88①保护腿：503 载体不被端点兜底 catch (Exception) 反向折叠成 internalError")
    void carrierIsNotRefoldedAtTheEndpoint() {
        when(previewQueryService.countDatasourceById("ds-2"))
                .thenThrow(new IllegalStateException("boom"));
        try {
            ApiResponse<Map<String, Object>> r = controller.preview("DOM",
                    body("ds-2", List.of("t_order")));
            // 走到这里＝载体被折叠成了信封；internalError 的 code=-1 面正是 D-B4-88 要消灭的
            throw new AssertionError("载体被端点兜底折叠，实得 code=" + r.getCode()
                    + " errorCode=" + r.getErrorCode());
        } catch (DataBridgeException expected) {
            assertNotNull(expected.getMessage(), "保护腿缺失时本用例即红");
        }
    }

    @Test
    @DisplayName("D-B4-88①分态：探测到 0 行（真实不存在）出 404 承载，不得冒充 503 不可用")
    void genuinelyAbsentDatasourceIsNotReportedAsUnavailable() {
        when(previewQueryService.countDatasourceById("ds-3")).thenReturn(0);

        // 实跑发现（本用例的价值所在）：NotFoundException 是 DataBridgeException 的子类，
        // 因此新增的保护腿把它一并从兜底 catch (Exception) → internalError(200 + code=-1)
        // 里解放出来 —— 「不存在」自此按携带的 404 出口渲染。这是承载型保护腿的必然副产，
        // 与 §4.7.2「不可用 ≠ 无权限 ≠ 不存在」的三分态口径同向，台账已如实登记。
        NotFoundException thrown = assertThrows(NotFoundException.class,
                () -> controller.preview("DOM", body("ds-3", List.of("t_order"))),
                "真实不存在必须走 NotFoundException，而不是被折成无码信封");
        assertEquals(404, thrown.getHttpStatus(), "「不存在」的状态行是 404，不得与 503 混一");
        assertFalse(String.valueOf(thrown.getMessage()).contains("ECOS-SYS-503"),
                "不存在态不得携带不可用机读码，实读=" + thrown.getMessage());
        verify(autoDiscoverService, never()).loadCuratedResources();
    }

    @Test
    @DisplayName("D-B4-88①不过头：探测为真 → 预览主流程照常出成功信封")
    void probeSuccessStillServesPreview() {
        when(previewQueryService.countDatasourceById("ds-4")).thenReturn(1);
        when(autoDiscoverService.loadCuratedResources()).thenReturn(List.of());
        when(autoDiscoverService.resolveResourceFields(anyList(), anyMap(), eq("ds-4"), anyString()))
                .thenReturn(List.of());

        ApiResponse<Map<String, Object>> r = controller.preview("DOM",
                body("ds-4", List.of("t_order")));
        // 本机信封成功码 = ApiResponse.CODE_SUCCESS（0），不是 HTTP 状态行 200
        assertEquals(ApiResponse.CODE_SUCCESS, r.getCode(),
                "收口只改不可用态，合法读面不得一起打死");
        assertTrue(r.isSuccess(), "合法预览仍须成功，实得 code=" + r.getCode());
        assertNotNull(r.getData());
        verify(previewQueryService).countDatasourceById("ds-4");
    }

    @Test
    @DisplayName("入参校验腿不受收口影响：缺 datasourceId/resourceNames 仍出 400")
    void requestValidationLegsRemain() {
        assertEquals(400, controller.preview("DOM", body(null, List.of("t"))).getCode());
        assertEquals(400, controller.preview("DOM", body("ds-5", List.of())).getCode());
        verify(previewQueryService, never()).countDatasourceById(any());
    }
}
