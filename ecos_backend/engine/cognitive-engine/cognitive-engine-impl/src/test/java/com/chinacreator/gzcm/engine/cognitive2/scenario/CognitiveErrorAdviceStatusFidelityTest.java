package com.chinacreator.gzcm.engine.cognitive2.scenario;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.exception.BusinessException;
import com.chinacreator.gzcm.engine.cognitive2.controller.scenario.CognitiveErrorAdvice;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * D37 回归常驻（2026-11-13 验证批）：认知域 advice 的 catch-all 曾把携带 400 的
 * {@link BusinessException} 折叠成 500 {@code E504_INTERNAL}。
 *
 * <p>实测现场：{@code POST /api/v1/cognitive/diagnose|counterfactual|forecast|models|beliefs|hypotheses|evidence}
 * 七个写面传空体全部 HTTP 500——调用方传空体是 4xx，折叠成 500 会把排障方向指向服务端故障。
 * 本用例逐条钉住「入参型失败必须留在 4xx 且错误码不被改写」，同时反向钉住「未分类异常仍为 500」
 * （避免修复把真正的服务端故障也降级成 4xx）。</p>
 */
class CognitiveErrorAdviceStatusFidelityTest {

    private final CognitiveErrorAdvice advice = new CognitiveErrorAdvice();

    @Test
    @DisplayName("D37：入参型 BusinessException 携带 400 ⇒ 状态行 400 且 code 不被折叠成 500")
    void businessExceptionKeepsItsOwnStatus() {
        BusinessException e = new BusinessException("COG-400: metric 必填");
        ResponseEntity<ApiResponse<Map<String, Object>>> resp = advice.onDataBridge(e);

        assertEquals(400, resp.getStatusCode().value(),
                "BusinessException 默认携带 400，状态行必须跟随携带值（禁折叠成 500）");
        assertNotNull(resp.getBody(), "错误响应体不得为空");
        assertEquals(400, resp.getBody().getCode(),
                "body.code 必须与状态行同号（§3.2.7）");
        assertFalse(resp.getBody().isSuccess(), "失败响应 success 必须为 false");
    }

    @Test
    @DisplayName("D37：显式携带 409 的业务异常同样跟随（不被 advice 二次裁决）")
    void businessExceptionExplicitStatusFollowed() {
        BusinessException e = new BusinessException(409, 409, "COG-409: 已终态");
        ResponseEntity<ApiResponse<Map<String, Object>>> resp = advice.onDataBridge(e);

        assertEquals(409, resp.getStatusCode().value(), "携带 409 必须写进状态行");
        assertEquals(409, resp.getBody().getCode(), "body.code 同号");
    }

    @Test
    @DisplayName("D37 反向腿：未分类异常仍走 500（修复不得把真故障降级成 4xx）")
    void unclassifiedStaysServerError() {
        ResponseEntity<ApiResponse<Map<String, Object>>> resp =
                advice.onOther(new IllegalStateException("boom"));

        assertEquals(500, resp.getStatusCode().value(), "未分类异常必须仍是 500");
        assertEquals(500, resp.getBody().getCode(), "body.code 与状态行同号");
    }
}
