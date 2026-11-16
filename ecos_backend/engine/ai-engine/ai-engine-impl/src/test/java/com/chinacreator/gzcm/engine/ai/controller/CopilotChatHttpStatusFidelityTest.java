package com.chinacreator.gzcm.engine.ai.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.error.AiErrorClass;
import com.chinacreator.gzcm.engine.ai.copilot.CopilotChatService;
import com.chinacreator.gzcm.engine.ai.copilot.CopilotAgentCatalog;
import com.chinacreator.gzcm.engine.ai.copilot.CopilotToolCatalog;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Copilot /chat 的 HTTP 状态行保真门禁（D63①，2026-11-14 验证批）。
 *
 * <p><b>缺陷形态（活体实测）</b>：agent-loop 被护栏 DENY 时，后端返回
 * {@code HTTP 200 + body{"success":false,"errorCode":"ECOS-AI-403"}} ——
 * 拒绝语义被压成成功状态行。浏览器里 Copilot 显示「智能体执行失败」，
 * 而任何基于 HTTP 状态的调用方（监控、重试、网关熔断）都读成成功。</p>
 *
 * <p><b>修法边界（状态行保真族既定口径）</b>：信封逐字段不动、路径/入参零改动，
 * 只补状态行；状态值必须从单源推出 —— 本端点取 {@link AiErrorClass#httpStatus()}，
 * 未登记码位一律 500（宁可报服务端错误，也不把失败压成 200）。</p>
 *
 * <p><b>反假绿</b>：{@link #probeDetectsLyingStatusLine()} 用同一判据跑一遍
 * 「旧行为（恒 200）」的合成信封，证明判据确实能识别假 200。</p>
 */
@DisplayName("D63 — Copilot /chat 失败态必须抬 HTTP 状态行（403/503 不得压成 200）")
class CopilotChatHttpStatusFidelityTest {

    private static CopilotController controllerWith(CopilotChatService chatService) throws Exception {
        CopilotController c = new CopilotController();
        setField(c, "copilotChatService", chatService);
        return c;
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field f = CopilotController.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    private static Map<String, Object> failedResult(String errorCode) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("success", false);
        r.put("answer", "");
        r.put("turns", 0);
        r.put("errorCode", errorCode);
        return r;
    }

    @Test
    @DisplayName("护栏 DENY（ECOS-AI-403）→ HTTP 403，信封 code 仍是 0（信封不动）")
    void guardrailDenyBecomes403() throws Exception {
        CopilotChatService svc = mock(CopilotChatService.class);
        when(svc.supports(anyString())).thenReturn(true);
        when(svc.chat(anyString(), anyString(), anyString())).thenReturn(failedResult("ECOS-AI-403"));

        ResponseEntity<ApiResponse<Map<String, Object>>> resp =
                controllerWith(svc).chat(Map.of("agentId", "agent-data", "message", "hi"));

        assertEquals(403, resp.getStatusCode().value(),
                "护栏拒绝必须是 HTTP 403（原实现恒 200，见 D63①）");
        assertNotNull(resp.getBody());
        assertEquals(ApiResponse.CODE_SUCCESS, resp.getBody().getCode(),
                "信封 code 不得改动（状态行保真族口径：只补状态行）");
    }

    @Test
    @DisplayName("护栏不可用（ECOS-AI-503）→ HTTP 503")
    void guardrailUnavailableBecomes503() throws Exception {
        CopilotChatService svc = mock(CopilotChatService.class);
        when(svc.supports(anyString())).thenReturn(true);
        when(svc.chat(anyString(), anyString(), anyString())).thenReturn(failedResult("ECOS-AI-503"));

        ResponseEntity<ApiResponse<Map<String, Object>>> resp =
                controllerWith(svc).chat(Map.of("agentId", "agent-data", "message", "hi"));

        assertEquals(503, resp.getStatusCode().value(), "护栏不可用是 503，不是 200");
    }

    @Test
    @DisplayName("成功应答仍是 200（补状态行不得误伤成功腿）")
    void successStays200() throws Exception {
        CopilotChatService svc = mock(CopilotChatService.class);
        when(svc.supports(anyString())).thenReturn(true);
        Map<String, Object> ok = new LinkedHashMap<>();
        ok.put("success", true);
        ok.put("answer", "real answer");
        when(svc.chat(anyString(), anyString(), anyString())).thenReturn(ok);

        ResponseEntity<ApiResponse<Map<String, Object>>> resp =
                controllerWith(svc).chat(Map.of("agentId", "agent-data", "message", "hi"));

        assertEquals(200, resp.getStatusCode().value());
        assertEquals(ApiResponse.CODE_SUCCESS, resp.getBody().getCode());
    }

    @Test
    @DisplayName("未登记族码（如 ECOS-AI-500）→ 500，不退化成 200")
    void unregisteredCodeBecomes500() throws Exception {
        CopilotChatService svc = mock(CopilotChatService.class);
        when(svc.supports(anyString())).thenReturn(true);
        when(svc.chat(anyString(), anyString(), anyString())).thenReturn(failedResult("ECOS-AI-500"));

        ResponseEntity<ApiResponse<Map<String, Object>>> resp =
                controllerWith(svc).chat(Map.of("agentId", "agent-data", "message", "hi"));

        assertEquals(500, resp.getStatusCode().value(),
                "查不到族码时应报 500；返回 200 就是把未知失败洗白成功");
    }

    @Test
    @DisplayName("空 message → 400（且不再是 200）")
    void blankMessageBecomes400() throws Exception {
        CopilotChatService svc = mock(CopilotChatService.class);
        ResponseEntity<ApiResponse<Map<String, Object>>> resp =
                controllerWith(svc).chat(Map.of("message", "   "));
        assertEquals(400, resp.getStatusCode().value());
    }

    @Test
    @DisplayName("状态映射单源：族码 → AiErrorClass.httpStatus()，不另建第二套表")
    void statusMappingIsSingleSourced() throws Exception {
        Method m = CopilotController.class.getDeclaredMethod("httpStatusOf", String.class);
        m.setAccessible(true);
        for (AiErrorClass c : AiErrorClass.values()) {
            assertEquals(c.httpStatus(), (int) (Integer) m.invoke(null, c.errorCode()),
                    "族码 " + c.errorCode() + " 必须取自 AiErrorClass.httpStatus()");
        }
    }

    @Test
    @DisplayName("探针自检：判据能识别「失败但状态行 200」的旧行为")
    void probeDetectsLyingStatusLine() {
        // 旧行为＝恒 ResponseEntity.ok(信封{success:false,errorCode:ECOS-AI-403})
        ResponseEntity<ApiResponse<Map<String, Object>>> legacy =
                ResponseEntity.ok(ApiResponse.success(failedResult("ECOS-AI-403")));

        boolean liesToCaller = legacy.getStatusCode().value() == 200
                && Boolean.FALSE.equals(legacy.getBody().getData().get("success"));
        assertTrue(liesToCaller,
                "旧写法（失败 + 200）必须被本门禁判据识别，否则门禁恒绿");
        // 预期正确形态
        assertEquals(403, AiErrorClass.GUARDRAIL_DENIED.httpStatus());
    }

    @SuppressWarnings("unused")
    private static final Class<?>[] CTOR_TYPES = {
            CopilotAgentCatalog.class, CopilotToolCatalog.class, Constructor.class
    };
}
