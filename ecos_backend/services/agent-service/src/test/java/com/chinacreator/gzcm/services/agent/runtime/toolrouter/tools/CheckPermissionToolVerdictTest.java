package com.chinacreator.gzcm.services.agent.runtime.toolrouter.tools;

import com.chinacreator.gzcm.runtime.access.endpoint.ServiceEndpointResolver;
import com.chinacreator.gzcm.services.agent.runtime.toolrouter.AgentToolEndpointResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link CheckPermissionTool} 收口 D-B4-43 的行为不变式（#142/B4-3e-8）。
 *
 * <p>钉两件事，对应差距的解除条件两半：</p>
 * <ol>
 *   <li><b>基址归单源</b>：请求 URL 由 {@link AgentToolEndpointResolver} 下发，
 *       禁再内联 {@code localhost:8080}（{@link #requestUrlComesFromResolverNotHardcodedHost()}）；</li>
 *   <li><b>应答三态归一后只回载荷</b>：透传原始 envelope 会把义务段/策略引用/兜底标记
 *       一并喂给模型，且 {@code UNEVALUATED}（护栏没说话）在原文里没有对应字面量，
 *       只会被读成"应答正常"。本类断言 ALLOW/DENY/UNEVALUATED 三态各自落位、
 *       DENY 与 UNEVALUATED 永不折叠、且载荷不含任何原始字段。</li>
 * </ol>
 *
 * <p>不用 mockito（agent-service 测试面仅有 junit + archunit），改用手写桩子类：
 * {@link RecordingRestTemplate} 记录 URL 并按脚本返回/抛出，{@link StubResolver} 固定基址解析结果。
 * 每条折叠相关用例都配"同输入 DENY 与 UNEVALUATED 结局必异"的对照，防恒真。</p>
 */
class CheckPermissionToolVerdictTest {

    private static final String RESOLVED_BASE = "http://svc-owner:18081";

    // ── 桩 ─────────────────────────────────────────────

    /** 记录出向 URL、按脚本返回应答或抛传输异常的 RestTemplate 桩。 */
    private static final class RecordingRestTemplate extends RestTemplate {
        private final Object scriptedResponse;
        private final RuntimeException scriptedFailure;
        private String lastUrl;

        RecordingRestTemplate(Object scriptedResponse, RuntimeException scriptedFailure) {
            this.scriptedResponse = scriptedResponse;
            this.scriptedFailure = scriptedFailure;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T postForObject(String url, Object request, Class<T> responseType, Object... uriVariables) {
            this.lastUrl = url;
            if (scriptedFailure != null) {
                throw scriptedFailure;
            }
            return (T) scriptedResponse;
        }
    }

    /** 固定 {@link AgentToolEndpointResolver#securityBaseUrl()} 结果的解析器桩。 */
    private static final class StubResolver extends AgentToolEndpointResolver {
        private final Optional<String> result;

        StubResolver(Optional<String> result) {
            super((ObjectProvider<ServiceEndpointResolver>) null, "");
            this.result = result;
        }

        @Override
        public Optional<String> securityBaseUrl() {
            return result;
        }
    }

    private static CheckPermissionTool tool(Optional<String> base, Object resp, RuntimeException failure) {
        return new CheckPermissionTool(new RecordingRestTemplate(resp, failure), new StubResolver(base));
    }

    /** 组装 {@code {data: {...}}} envelope（与生产 ApiResponse 承载裁决的形态一致）。 */
    private static Map<String, Object> envelope(Map<String, Object> data) {
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("code", "0");
        e.put("data", data);
        return e;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> run(CheckPermissionTool target) {
        Object out = target.execute("CheckPermission", Map.of("resource", "x"));
        assertTrue(out instanceof Map, "工具应返回结构化载荷而非原始对象透传");
        return (Map<String, Object>) out;
    }

    // ── 基址腿 ─────────────────────────────────────────

    @Test
    @DisplayName("请求 URL 取自解析器基址，且不含内联 localhost:8080")
    void requestUrlComesFromResolverNotHardcodedHost() {
        RecordingRestTemplate rest = new RecordingRestTemplate(envelope(Map.of("allow", true)), null);
        new CheckPermissionTool(rest, new StubResolver(Optional.of(RESOLVED_BASE)))
                .execute("CheckPermission", Map.of());

        assertEquals(RESOLVED_BASE + CheckPermissionTool.PATH_POLICY_EVALUATE, rest.lastUrl);
        assertFalse(rest.lastUrl.contains("localhost:8080"),
                "基址不得再内联 monolith 端口（D-B4-43 的基址半句）: " + rest.lastUrl);
    }

    @Test
    @DisplayName("基址解析不到 ⇒ UNEVALUATED（护栏不可用），不发请求、不冒充拒绝")
    void unresolvedBaseYieldsUnevaluatedWithoutCall() {
        RecordingRestTemplate rest = new RecordingRestTemplate(null, null);
        Map<String, Object> payload = run(new CheckPermissionTool(rest, new StubResolver(Optional.empty())));

        assertEquals("UNEVALUATED", payload.get(CheckPermissionTool.KEY_ADJUDICATION));
        assertTrue(((String) payload.get(CheckPermissionTool.KEY_MESSAGE)).length() > 0,
                "不可用结论必须带如实理由");
        // 基址解析不到时根本不该发起出向调用
        org.junit.jupiter.api.Assertions.assertNull(rest.lastUrl, "基址缺失时不得发起请求");
    }

    @Test
    @DisplayName("传输失败 ⇒ UNEVALUATED，绝不折成 DENY")
    void transportFailureYieldsUnevaluated() {
        Map<String, Object> payload = run(tool(Optional.of(RESOLVED_BASE), null,
                new ResourceAccessException("security-engine unreachable")));
        assertEquals("UNEVALUATED", payload.get(CheckPermissionTool.KEY_ADJUDICATION));
    }

    // ── 裁决三态腿 ─────────────────────────────────────

    @Test
    @DisplayName("allow=true ⇒ ALLOW")
    void explicitAllowReadsAllow() {
        Map<String, Object> payload = run(tool(Optional.of(RESOLVED_BASE),
                envelope(Map.of("allow", true)), null));
        assertEquals("ALLOW", payload.get(CheckPermissionTool.KEY_ADJUDICATION));
    }

    @Test
    @DisplayName("allow=false ⇒ DENY（真实策略拒绝）")
    void explicitDenyReadsDeny() {
        Map<String, Object> payload = run(tool(Optional.of(RESOLVED_BASE),
                envelope(Map.of("allow", false)), null));
        assertEquals("DENY", payload.get(CheckPermissionTool.KEY_ADJUDICATION));
    }

    @Test
    @DisplayName("应答无可识别裁决 ⇒ UNEVALUATED，与 DENY 分态不折叠")
    void absentVerdictReadsUnevaluatedNotDeny() {
        Map<String, Object> silent = run(tool(Optional.of(RESOLVED_BASE), envelope(Map.of()), null));
        Map<String, Object> denied = run(tool(Optional.of(RESOLVED_BASE),
                envelope(Map.of("allow", false)), null));

        assertEquals("UNEVALUATED", silent.get(CheckPermissionTool.KEY_ADJUDICATION));
        assertEquals("DENY", denied.get(CheckPermissionTool.KEY_ADJUDICATION));
        // 折叠前提：两者都"没放行"；不变式：三态串必异（护栏没说话 ≠ 策略判了拒绝）
        assertFalse(silent.get(CheckPermissionTool.KEY_ADJUDICATION)
                .equals(denied.get(CheckPermissionTool.KEY_ADJUDICATION)),
                "DENY 与 UNEVALUATED 不得折叠成同一结论（§4.7.2 不可用 ≠ 无权限）");
    }

    @Test
    @DisplayName("载荷只含三态与理由，原始 envelope 字段绝不透传给模型")
    void rawEnvelopeFieldsAreNeverLeaked() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("allow", true);
        data.put("policyRef", "data_access.rego");
        data.put("obligations", java.util.List.of(Map.of("obligation", "watermark", "value", "WM")));
        data.put("source", "opa");

        Map<String, Object> payload = run(tool(Optional.of(RESOLVED_BASE), envelope(data), null));

        assertEquals("ALLOW", payload.get(CheckPermissionTool.KEY_ADJUDICATION));
        assertFalse(payload.containsKey("obligations"), "义务段不得透传");
        assertFalse(payload.containsKey("policyRef"), "策略引用不得透传");
        assertFalse(payload.containsKey("source"), "来源标记不得透传");
        // 只允许 adjudication / message 两段
        assertTrue(payload.keySet().size() <= 2 && payload.keySet().containsAll(
                        java.util.Set.of(CheckPermissionTool.KEY_ADJUDICATION)),
                "载荷段面必须收敛为三态(+可选理由): " + payload.keySet());
    }
}
