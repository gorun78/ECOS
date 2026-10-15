package com.chinacreator.gzcm.engine.security.rolechannel;

import com.chinacreator.gzcm.common.security.guardrail.GuardrailDecisionContract;
import com.chinacreator.gzcm.engine.security.decision.AssetRef;
import com.chinacreator.gzcm.engine.security.decision.DecisionBundle;
import com.chinacreator.gzcm.engine.security.service.OpaPolicyService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 护栏<b>应答形态</b>与<b>策略源形态</b>的门禁（可执行标识 {@code OpaDocumentShapeTest}，#142/B4-3c）。
 *
 <p>三段职责：</p>
 * <ol>
 *   <li><b>全量回放一致性</b>：oracle 里 14 组输入逐一过生产
 *       {@code OpaPolicyService#evaluate}（真 HTTP + 真解析），断 {@code allow} /
 *       {@code policyState} / {@code obligations} / {@code policyRef} 与录制态逐字相等 ——
 *       这是「应答原文 → 裁决」这一段的保真证明，也顺带证明 oracle 未被手编；</li>
 *   <li><b>四态判定</b>：策略未加载（无 {@code result} 段 / {@code result} 无 {@code allow}）
 *       必须落 {@code POLICY_UNDEFINED}，非 2xx 与不可解析必须落 {@code OPA_UNAVAILABLE}，
 *       真拒绝才落 {@code EVALUATED_DENY}。旧形态只看 {@code body.contains("\"result\":true")}，
 *       于是网关 5xx 被伪装成策略拒绝（D-B4-21）、策略未加载被伪装成"无权限"（D-B4-14）。</li>
 *   <li><b>策略源形态静态门</b>：{@code default allow = false}（默认不放行）、
 *       四类义务集各自恰有一条 {@code default X = []}（拒绝态义务段不得整段消失）、
 *       {@code obligations} 只定义一次且不被 default 覆盖、
 *       <b>不引用 {@code input.action}</b> —— 最后一项是回放有效性的前提：生产请求体比录制态
 *       多一个 {@code action} 字段，只有策略不读它时二者才等价
 *       （{@link OpaOracleReplaySupport#assertRequestMatches} 每次回放都会复核该前提）。</li>
 * </ol>
 */
class OpaDocumentShapeTest {

    @Test
    @DisplayName("全量回放 — 14 组录制输入的裁决结果逐字复现（禁挑例自证）")
    void everyRecordedCaseReproducesThroughProductionParser() throws IOException {
        OpaOracleReplaySupport.assertPolicyNotDrifted();
        List<OpaOracleReplaySupport.OracleCase> cases = OpaOracleReplaySupport.cases();
        assertEquals(14, cases.size(), "oracle 用例数与 §4.7.2 矩阵护栏格登记值不符（改动须同步矩阵）");

        try (OpaOracleReplaySupport server = OpaOracleReplaySupport.start()) {
            PageChannelHarness harness = PageChannelHarness.create(server.baseUrl());
            for (OpaOracleReplaySupport.OracleCase c : cases) {
                server.setCase(c.name());
                Map<String, Object> data = harness.evaluateGuardrail(c.input());

                assertEquals(OpaOracleReplaySupport.writeJson(c.input()),
                        OpaOracleReplaySupport.writeJson(server.lastSentInput()),
                        c.name() + " 发出的请求体与录制输入不逐字一致 ⇒ 回放不成立");
                assertEquals(c.allow(), Boolean.TRUE.equals(data.get(GuardrailDecisionContract.KEY_ALLOW)),
                        c.name() + " 的 allow 与录制态不一致");
                assertEquals(c.allow() ? GuardrailDecisionContract.STATE_ALLOW
                                : GuardrailDecisionContract.STATE_DENY,
                        data.get(GuardrailDecisionContract.KEY_POLICY_STATE),
                        c.name() + " 的护栏态必须与 allow 同向（真引擎应答不得落未评估态）");
                assertEquals(OpaOracleReplaySupport.writeJson(c.obligations()),
                        OpaOracleReplaySupport.writeJson(
                                data.get(GuardrailDecisionContract.KEY_OBLIGATIONS)),
                        c.name() + " 的义务集必须逐字透传");
                assertEquals("ecos." + OpaPolicyService.POLICY_DATA_ACCESS,
                        data.get("policyRef"), c.name());
                assertEquals("opa", data.get("source"),
                        c.name() + " 的应答来源必须是 opa；fallback 意味着护栏没被咨询");
            }
        }
    }

    @Test
    @DisplayName("应答无 result 段 — POLICY_UNDEFINED（策略未加载＝部署缺陷态，禁冒充策略拒绝）")
    void missingResultSegmentIsPolicyUndefined() throws IOException {
        Map<String, Object> data = raw(200, "{\"foo\":1}");

        assertEquals(Boolean.FALSE, data.get(GuardrailDecisionContract.KEY_ALLOW));
        assertEquals(GuardrailDecisionContract.STATE_POLICY_UNDEFINED,
                data.get(GuardrailDecisionContract.KEY_POLICY_STATE));
        assertTrue(GuardrailDecisionContract.isUnevaluated(
                        String.valueOf(data.get(GuardrailDecisionContract.KEY_POLICY_STATE))),
                "未加载态必须落在「未评估」一侧，消费方据此报 503 而非 403");
        assertEquals("OPA_POLICY_UNDEFINED", firstDeniedReason(data));
    }

    @Test
    @DisplayName("result 有但缺 allow 键 — 同落 POLICY_UNDEFINED（禁把键缺失当 false 拒绝）")
    void resultWithoutAllowKeyIsPolicyUndefined() throws IOException {
        Map<String, Object> data = raw(200, "{\"result\":{\"other\":1}}");

        assertEquals(GuardrailDecisionContract.STATE_POLICY_UNDEFINED,
                data.get(GuardrailDecisionContract.KEY_POLICY_STATE));
        assertEquals("OPA_POLICY_UNDEFINED", firstDeniedReason(data));
    }

    @Test
    @DisplayName("非 2xx — OPA_UNAVAILABLE 且不得冒充策略拒绝（D-B4-21 的根因钉）")
    void nonSuccessStatusIsUnavailableNotDeny() throws IOException {
        // 故意给出 allow=true 的 body：旧实现按 body 子串判定会把它当放行/拒绝混淆，
        // 新实现必须在状态码层面先 fail-close 并如实标不可用。
        Map<String, Object> data = raw(503, "{\"result\":{\"allow\":true,\"obligations\":[]}}");

        assertEquals(Boolean.FALSE, data.get(GuardrailDecisionContract.KEY_ALLOW));
        assertEquals(GuardrailDecisionContract.STATE_UNAVAILABLE,
                data.get(GuardrailDecisionContract.KEY_POLICY_STATE));
        assertFalse(GuardrailDecisionContract.isEvaluated(
                String.valueOf(data.get(GuardrailDecisionContract.KEY_POLICY_STATE))));
        assertEquals("fallback", data.get("source"), "非 2xx 不得标 source=opa");
        assertEquals("OPA_HTTP_503", firstDeniedReason(data));
    }

    @Test
    @DisplayName("2xx 但应答不可解析 — OPA_UNAVAILABLE（禁因解析失败而放行，也禁冒充策略裁决）")
    void unparsableBodyIsUnavailable() throws IOException {
        Map<String, Object> data = raw(200, "<html>gateway error</html>");

        assertEquals(Boolean.FALSE, data.get(GuardrailDecisionContract.KEY_ALLOW));
        assertEquals(GuardrailDecisionContract.STATE_UNAVAILABLE,
                data.get(GuardrailDecisionContract.KEY_POLICY_STATE));
        assertEquals("OPA_RESPONSE_UNPARSABLE", firstDeniedReason(data));
    }

    @Test
    @DisplayName("legacy 裸布尔应答 — 仍判放行（兼容旧 .../allow 查询，不因此判成未加载）")
    void legacyBareBooleanResponseIsStillParsed() throws IOException {
        Map<String, Object> allow = raw(200, "{\"result\":true}");
        assertEquals(Boolean.TRUE, allow.get(GuardrailDecisionContract.KEY_ALLOW));
        assertEquals(GuardrailDecisionContract.STATE_ALLOW,
                allow.get(GuardrailDecisionContract.KEY_POLICY_STATE));

        Map<String, Object> deny = raw(200, "{\"result\":false}");
        assertEquals(Boolean.FALSE, deny.get(GuardrailDecisionContract.KEY_ALLOW));
        assertEquals(GuardrailDecisionContract.STATE_DENY,
                deny.get(GuardrailDecisionContract.KEY_POLICY_STATE));
        assertNull(firstDeniedReason(deny),
                "策略给出拒绝但未带义务时必须取到 null，禁消费方凭 allow=false 拼装拒绝原因");
    }

    // ── 策略源形态静态门 ──────────────────────────────────────────────────────

    @Test
    @DisplayName("rego 形态 — 默认不放行 + 包名与单源策略名同源")
    void regoDeniesByDefaultAndMatchesSingleSourceName() throws IOException {
        String body = codeLinesOnly(OpaOracleReplaySupport.regoText());

        assertTrue(Pattern.compile("(?m)^\\s*default\\s+allow\\s*=\\s*false\\s*$").matcher(body).find(),
                "策略必须 default allow=false（缺上下文一律不放行）");
        assertTrue(body.contains("package ecos." + OpaPolicyService.POLICY_DATA_ACCESS),
                "包名必须等于单源策略名 ecos.data_access，否则线上查询路径与策略对不上");
        assertTrue(body.contains("\"NO_DATA_ACCESS_ROLE\""), "无权角色违规名必须在策略里");
        assertTrue(body.contains("\"AI_SCOPE_OUT_OF_SUBJECT_DEPARTMENT\""),
                "横向越权违规名必须在策略里（§4.7.2 项目负责人行 AI 列 DENY 的判据）");
    }

    @Test
    @DisplayName("rego 形态 — 四类义务集各自恰一条 default=[] 且有定义分支（拒绝态义务不得整段消失）")
    void everyObligationSetIsDefaultedExactlyOnce() throws IOException {
        String body = codeLinesOnly(OpaOracleReplaySupport.regoText());
        for (String set : List.of("deny_obligations", "mask_obligations",
                "watermark_obligations", "rate_obligations")) {
            assertEquals(1, count(body, "(?m)^\\s*default\\s+" + set + "\\s*=\\s*\\[\\]\\s*$"),
                set + " 必须恰有一条 default = [] 兜底（录制 oracle 时实测：写成命中才定义会让"
                        + "分支一真一假时规则整体未定义，obligations 段连同 DENIED 理由一起消失）");
            assertTrue(Pattern.compile("(?m)^\\s*" + set + "\\s*:=").matcher(body).find(),
                    set + " 还必须有定义分支，只有 default 等于义务永不可得");
        }
        assertEquals(1, count(body, "(?m)^\\s*obligations\\s*:="), "汇总规则 obligations 只能定义一次");
        assertEquals(0, count(body, "(?m)^\\s*default\\s+obligations\\b"),
                "obligations 不得被 default 覆盖（否则 default 分支会顶掉 concat 结果）");
    }

    @Test
    @DisplayName("rego 形态 — 不引用 input.action（回放等价性前提：生产请求体比录制态多该字段）")
    void regoNeverReadsInputAction() throws IOException {
        String body = codeLinesOnly(OpaOracleReplaySupport.regoText());
        assertEquals(0, count(body, "input\\.action"),
                "策略一旦读 input.action，oracle 的录制输入（无该字段）就不再等价，必须重录");
        assertEquals(0, count(body, "\"action\""),
                "同上：object.get(input, \"action\") 形态的引用也会让回放失真");
    }

    @Test
    @DisplayName("用例集自身有效性对照 — 护栏态若被折叠则分态断言必红（禁写出永不成立的门禁）")
    void vocabularyKeepsFourStatesDistinct() {
        List<String> states = List.of(GuardrailDecisionContract.STATE_ALLOW,
                GuardrailDecisionContract.STATE_DENY,
                GuardrailDecisionContract.STATE_POLICY_UNDEFINED,
                GuardrailDecisionContract.STATE_UNAVAILABLE);
        assertEquals(4, states.stream().distinct().count(), "四态串必须互不相同");
        // ALLOW/DENY ＝「护栏说了话」→ isUnevaluated=false；POLICY_UNDEFINED/OPA_UNAVAILABLE ＝「没说话」→ true
        assertEquals(List.of(false, false, true, true), List.of(
                GuardrailDecisionContract.isUnevaluated(GuardrailDecisionContract.STATE_ALLOW),
                GuardrailDecisionContract.isUnevaluated(GuardrailDecisionContract.STATE_DENY),
                GuardrailDecisionContract.isUnevaluated(GuardrailDecisionContract.STATE_POLICY_UNDEFINED),
                GuardrailDecisionContract.isUnevaluated(GuardrailDecisionContract.STATE_UNAVAILABLE)),
                "「未评估」必须恰好是 {POLICY_UNDEFINED, OPA_UNAVAILABLE} 两态");
    }

    // ── helpers ────────────────────────────────────────────────────────────

    /** 让回放服务按给定状态码/原文应答，取生产 evaluate 的返回段（应答形态用例专用）。 */
    private static Map<String, Object> raw(int status, String body) throws IOException {
        try (OpaOracleReplaySupport server = OpaOracleReplaySupport.start()) {
            server.respondRaw(status, body);
            PageChannelHarness harness = PageChannelHarness.create(server.baseUrl());
            Map<String, Object> data = harness.evaluateGuardrail(Map.of("purpose", "export"));
            assertEquals(1, server.requestedPaths().size(), "本用例只应发出一次护栏请求");
            return data;
        }
    }

    /** 剥掉 # 行注释后的策略正文（形态门只对真规则负责）。 */
    private static String codeLinesOnly(String rego) {
        StringBuilder sb = new StringBuilder();
        for (String line : rego.split("\r?\n")) {
            if (line.trim().startsWith("#")) continue;
            sb.append(line).append('\n');
        }
        return sb.toString();
    }

    private static int count(String text, String regex) {
        Matcher m = Pattern.compile(regex).matcher(text);
        int n = 0;
        while (m.find()) n++;
        return n;
    }

    private static String firstDeniedReason(Map<String, Object> data) {
        Object raw = data.get(GuardrailDecisionContract.KEY_OBLIGATIONS);
        if (!(raw instanceof List<?> list)) return null;
        for (Object o : list) {
            if (o instanceof Map<?, ?> m && m.get(GuardrailDecisionContract.KEY_DENIED_REASON) != null) {
                return String.valueOf(m.get(GuardrailDecisionContract.KEY_DENIED_REASON));
            }
        }
        return null;
    }

    /** 与本批硬门联动：/decision 入口在 POLICY_UNDEFINED 下必须可被判为未评估（供咽喉选 503 分支）。 */
    @Test
    @DisplayName("bundle 侧联动 — POLICY_UNDEFINED 态在裁决包上判为「护栏未给出裁决」")
    void policyUndefinedIsUnevaluatedOnBundle() throws IOException {
        OpaOracleReplaySupport.OracleCase c = OpaOracleReplaySupport.byName("norole_page");
        try (OpaOracleReplaySupport server = OpaOracleReplaySupport.start()) {
            server.respondRaw(200, "{\"foo\":1}");
            PageChannelHarness harness = PageChannelHarness.create(server.baseUrl());
            DecisionBundle b = harness.decide(
                    new AssetRef(PageChannelHarness.SCHEMA, (String) c.input().get("asset"), List.of()),
                    harness.explicitContext(OpaOracleReplaySupport.str(c, "userId"),
                            OpaOracleReplaySupport.str(c, "tenantId"),
                            OpaOracleReplaySupport.str(c, "department"),
                            OpaOracleReplaySupport.rolesOf(c)),
                    DecisionBundle.Purpose.PAGE);
            assertTrue(b.guardrailUnevaluated());
            assertEquals(GuardrailDecisionContract.STATE_POLICY_UNDEFINED, b.opaPolicyState());
            assertFalse(b.guardrailEvaluated());
        }
    }
}
