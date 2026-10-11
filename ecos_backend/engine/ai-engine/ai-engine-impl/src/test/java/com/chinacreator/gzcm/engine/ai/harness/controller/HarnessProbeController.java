package com.chinacreator.gzcm.engine.ai.harness.controller;

import com.chinacreator.gzcm.engine.ai.harness.MockSecurityEngineStub;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.MDC;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 详细设计-06 F06-22 测试底座探针端点（P-1 契约）。
 *
 * <p>本端点<b>不在</b> API 契约范围内（路径 {@code /test/harness/**}）；
 * 用途是让 {@code AiTestHarnessSmokeTest} / {@code GatewayAbacModeTest}
 * 在 <b>同一</b> 上下文中把三态 stub 的真实返回值"透传"到 HTTP 出口，
 * 从而断言网关态与 aiming 态的 <b>ABAC 拒绝不静默跳过</b>（F06-01 四段链中
 * policyEvaluate 失败必须上浮，不是 catch→200 空体）。
 *
 * <p>陈旧用例最常见的假阳性（X-26）：本地对 {@code AgentToolPolicyGate} 的
 * Mockito 桩永远返回 ALLOW，导致"整条链过"通过；本探针不接受 Mockito，
 * 只认 {@link MockSecurityEngineStub} 的真实 JSON —— 与
 * {@code runtime/llm-gateway SecurityEngineBridgeAbacTest} 同型。</p>
 */
@RestController
@RequestMapping("/test/harness")
public class HarnessProbeController {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final MockSecurityEngineStub stub;

    public HarnessProbeController(MockSecurityEngineStub stub) {
        this.stub = stub;
    }

    /** 健康自检 —— 用来给两类断言明确断言上下文起来了（防止语境错配：忘 include 本 config）。 */
    @GetMapping("/ping")
    public Map<String, Object> ping() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", true);
        m.put("traceId", currentTraceId());
        m.put("securityStub", stub.currentState());
        return m;
    }

    /** 模拟 {@code ToolExecutorService.execute} 咽喉里 policyEvaluate 段的行为。
     *
     *  契约（与 F06-01 对齐、与 §3.2 状态矩阵严格分态）：
     *  - 桩 ALLOW  → 返回 {@code status=ALLOW}
     *  - 桩 DENY   → 返回 {@code status=DENY, errorClass=GUARDRAIL_DENIED}
     *  - 桩 FAIL_CLOSED（任何 UNSAFE_*）→ 返回 {@code status=FAIL_CLOSED,
     *    errorClass=GUARDRAIL_FAIL_CLOSED}
     *
     *  <b>禁止</b>把 DENY / FAIL_CLOSED 折叠成 200 且空 body（§3.2 状态矩阵
     *  + A3 用户裁定）。同时 <b>禁止</b>把"200 但 body 不是合法引擎裁决 JSON"
     *  读作 DENY —— 分条错配的 UI 后果（F06-14 错误模板）不可修复 —— 直接
     *  FAIL_CLOSED 兜底。</p>
     */
    @PostMapping("/tool-adjudication")
    public Map<String, Object> adjudicate(
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId,
            @RequestBody Map<String, Object> body) {
        MockSecurityEngineStub.StubResponse r = stub.evaluate((String) body.getOrDefault("toolName", "probe"));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("traceId", traceId != null ? traceId : currentTraceId());
        out.put("state", stub.currentState().name());

        /* 连接拒绝 / 空响应 —— 底座未连上 */
        if (r.status == 0) {
            out.put("status", "FAIL_CLOSED");
            out.put("errorClass", "GUARDRAIL_FAIL_CLOSED");
            out.put("detail", r.note);
            return out;
        }
        /* 429 限流 —— 底座活着但拒判（fail-closed，与 DENY 严格区分） */
        if (r.status == 429) {
            out.put("status", "FAIL_CLOSED");
            out.put("errorClass", "GUARDRAIL_FAIL_CLOSED");
            out.put("detail", "security-engine rate-limited (429)");
            return out;
        }
        /* 其他 5xx —— 底座故障 */
        if (r.status >= 500) {
            out.put("status", "FAIL_CLOSED");
            out.put("errorClass", "GUARDRAIL_FAIL_CLOSED");
            out.put("detail", "security-engine http " + r.status);
            return out;
        }

        try {
            String s = new String(r.body, StandardCharsets.UTF_8);
            JsonNode root = MAPPER.readTree(s);
            JsonNode data = root.path("data");
            /* 引擎正常返回但结构不对（缺 data.allow）—— 不可信，走 FAIL_CLOSED */
            if (!data.isObject()) {
                out.put("status", "FAIL_CLOSED");
                out.put("errorClass", "GUARDRAIL_FAIL_CLOSED");
                out.put("detail", "engine response missing data object: " + shorten(s));
                return out;
            }
            boolean allow = data.path("allow").asBoolean(false)
                    && "engine".equals(data.path("source").asText(""));
            if (allow) {
                out.put("status", "ALLOW");
            } else {
                out.put("status", "DENY");
                out.put("errorClass", "GUARDRAIL_DENIED");
            }
        } catch (Exception e) {
            /* 200 但 body 不是合法 JSON —— 严格分态（不许读作 DENY，防误伤 UI） */
            out.put("status", "FAIL_CLOSED");
            out.put("errorClass", "GUARDRAIL_FAIL_CLOSED");
            out.put("detail", "unparseable engine response: " + e.getMessage());
        }
        return out;
    }

    private static String shorten(String s) {
        if (s == null) {
            return "";
        }
        return s.length() <= 80 ? s : s.substring(0, 80) + "...";
    }

    private static String currentTraceId() {
        String t = MDC.get("traceId");
        return t == null ? "" : t;
    }
}

