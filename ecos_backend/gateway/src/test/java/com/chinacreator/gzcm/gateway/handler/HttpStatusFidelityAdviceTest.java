package com.chinacreator.gzcm.gateway.handler;

import com.chinacreator.gzcm.common.base.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * D45（2026-11-13 验证批）常驻回归：\`CODE_INTERNAL_ERROR\` 必须映射到 500。
 *
 * <p><b>根因</b>：\`ApiResponse.internalError(String)\` 单参重载（全仓约 317 处调用共用）返回
 * \`code=-1\`，而状态行保真层原先用 \`if (code <= 0) return body;\` 把负数一律放过 ⇒
 * 服务端故障对外表现为 <b>HTTP 200 + code=-1</b>。实测触发：
 * \`GET /api/v1/system/config/params/{id}\`（D45 台账 §46）。</p>
 *
 * <p><b>本测钉的四条</b>：
 * <ol>
 *   <li>\`code=-1\` → 状态行 500（修复点）</li>
 *   <li>\`code=0\`（成功）→ 保持 200（不得被误伤）</li>
 *   <li>其余负数（如 -2，无对外语义约定）→ 保持 200（不擅自升格）</li>
 *   <li>调用方已显式设非 200（如 405/404）→ 不覆盖（既有保护不得破坏）</li>
 * </ol>
 * 第 2~4 条是反向腿：只钉「-1 变 500」会漏掉「顺手把别的改坏」这种回归。</p>
 */
class HttpStatusFidelityAdviceTest {

    private final HttpStatusFidelityAdvice advice = new HttpStatusFidelityAdvice();

    private int statusAfter(ApiResponse<?> body, int presetStatus) {
        MockHttpServletResponse servletResp = new MockHttpServletResponse();
        servletResp.setStatus(presetStatus);
        ServletServerHttpResponse response = new ServletServerHttpResponse(servletResp);
        MockHttpServletRequest servletReq = new MockHttpServletRequest("GET", "/test");
        ServletServerHttpRequest request = new ServletServerHttpRequest(servletReq);
        advice.beforeBodyWrite(body, null, MediaType.APPLICATION_JSON, null, request, response);
        return servletResp.getStatus();
    }

    @Test
    void internalErrorCodeMinusOneMustBecome500() {
        ApiResponse<Void> body = ApiResponse.internalError("查询失败");
        assertEquals(ApiResponse.CODE_INTERNAL_ERROR, body.getCode(), "前置：工厂确实产出 -1");
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), statusAfter(body, 200),
                "code=-1 必须映射为 500，禁留在 200（D45）");
    }

    @Test
    void successStays200() {
        ApiResponse<String> body = ApiResponse.success("ok");
        assertEquals(200, statusAfter(body, 200), "code=0 成功必须保持 200");
    }

    @Test
    void otherNegativeCodesStayUntouched() {
        ApiResponse<Void> body = ApiResponse.error(-2, "legacy negative", "无对外语义约定");
        assertEquals(200, statusAfter(body, 200),
                "其余负数码尚无对外语义约定，不得擅自升格（只特判 CODE_INTERNAL_ERROR）");
    }

    @Test
    void explicitNon200MustNotBeOverridden() {
        ApiResponse<Void> body = ApiResponse.internalError("查询失败");
        assertEquals(503, statusAfter(body, 503),
                "调用方已显式设置非 200（如 503 未装配）时不得改写");
    }
}
