package com.chinacreator.gzcm.engine.data.quality.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.data.quality.service.DqService;

/**
 * F02-08 规格 point 5（doc 行 154 + D.4 兼容性表行 2「/api/v1/ecos/dq/**（legacy，实测 200）」）
 * "写端点停用" 验收护栏。
 *
 * <p>对照 {@code PipelineTaskReadonlyTest}（F02-03 的 6 写端点 409 收口）——本测试锁 legacy DQ
 * 控制器 {@code DqController} 的 <b>10 个写端点全部 410 Gone 且不下沉服务</b>（不写旧表
 * {@code ecos_dq_rule_v2} / {@code ecos_dq_issue}），以及 <b>5 个读端点保留只读兼容</b>
 * （委托 {@code DqService} 读旧表）。
 *
 * <p>只判契约：不触库 / 不触 Spring 上下文。写端点的离线可验面 = "返回体 410 + {@code data==null}
 * + 引导语指向新端点 {@code /api/v1/dq}" 且 "对 {@code DqService} 零交互"（短路不下沉）；
 * 响应体 {@code deprecated=true} 字段半需 §14.4 活环境 HTTP 探针（与 D.4 行 3 PMO45 别名
 * {@code deprecated=true} 同族，见校订二十六；本条为校订二十七）。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("F02-08-2 legacy DQ 写端点 410 停机 + 读端点保留只读（离线契约）")
class DqLegacyWriteStopContractTest {

    private static final int HTTP_GONE = 410;
    private static final String REDIRECT_HINT = "/api/v1/dq";

    @Mock
    private DqService dqService;

    private DqController controller;

    @BeforeEach
    void setUp() {
        controller = new DqController(dqService);
    }

    private void assertGone(ApiResponse<Map<String, Object>> resp) {
        assertEquals(HTTP_GONE, resp.getCode(), "写端点应统一 410 Gone");
        assertTrue(!resp.isSuccess(), "410 非成功码");
        assertNull(resp.getData(), "写端点拒绝不下发任何负载");
        assertNotNull(resp.getMessage());
        assertTrue(resp.getMessage().contains(REDIRECT_HINT),
                "拒绝引导语必须指向新端点 /api/v1/dq：" + resp.getMessage());
    }

    // ────────────────────────── 10 写端点 → 410 Gone ──────────────────────────

    @Test
    @DisplayName("10 个写端点（含旧兼容路径）全部 410 Gone 且引导到新端点")
    void allTenWriteEndpoints_returnGoneWithRedirect() {
        Map<String, Object> body = Map.of("name", "x");

        assertGone(controller.createRule(body));
        assertGone(controller.updateRule(99L, body));
        assertGone(controller.deleteRule(99L));
        assertGone(controller.createIssue(body));
        assertGone(controller.updateIssue(99L, body));
        assertGone(controller.resolveIssue(99L, body));
        assertGone(controller.deleteIssue(99L));
        assertGone(controller.runCheck());
        assertGone(controller.createLegacy("rules", body));
        assertGone(controller.deleteLegacy("rules", 99L));
    }

    @Test
    @DisplayName("写端点短路：对 DqService 零交互（不下沉，不写旧表）")
    void writeEndpoints_neverReachService() {
        Map<String, Object> body = Map.of("name", "x");

        controller.createRule(body);
        controller.updateRule(99L, body);
        controller.deleteRule(99L);
        controller.createIssue(body);
        controller.updateIssue(99L, body);
        controller.resolveIssue(99L, body);
        controller.deleteIssue(99L);
        controller.runCheck();
        controller.createLegacy("rules", body);
        controller.deleteLegacy("rules", 99L);

        verifyNoInteractions(dqService);
    }

    // ────────────────────────── 5 读端点 → 保留只读 ──────────────────────────

    @Test
    @DisplayName("读端点保留只读兼容：仍委托 DqService（读旧表），无 410 拦截")
    void readEndpoints_stillDelegateToService() {
        Map<String, Object> aRule = Map.of("name", "r");

        when(dqService.listRules()).thenReturn(List.of(aRule));
        when(dqService.totalRules()).thenReturn(1L);

        ApiResponse<Map<String, Object>> resp = controller.listRules();

        assertEquals(ApiResponse.CODE_SUCCESS, resp.getCode(), "读端点应成功，而非 410");
        assertTrue(resp.isSuccess(), "读端点保留只读");
        assertNotNull(resp.getData());
        assertEquals(1L, resp.getData().get("total"));
        verify(dqService).listRules();
        verify(dqService).totalRules();
    }
}
