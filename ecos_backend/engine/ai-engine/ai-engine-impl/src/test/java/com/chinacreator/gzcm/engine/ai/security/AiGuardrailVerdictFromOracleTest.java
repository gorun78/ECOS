package com.chinacreator.gzcm.engine.ai.security;

import com.chinacreator.gzcm.common.security.guardrail.GuardrailDecisionContract;
import com.chinacreator.gzcm.runtime.eventbus.EventBusService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * AI 通道对护栏裁决的<b>消费面</b>用例：同一份录制 oracle，断
 * {@code AiSecurityEngineClient#verdictOf} 的三态映射与单源策略名
 * （可执行标识 {@code AiGuardrailVerdictFromOracleTest}，#142/B4-3c）。
 *
 <p><b>与 security 侧用例的分工</b>（避免第二套真值）：</p>
 * <ul>
 *   <li>security 侧 {@code OpaDocumentShapeTest#everyRecordedCaseReproducesThroughProductionParser}
 *       证明「OPA 应答原文 → {@code data} 段（allow/policyState/obligations）」由<b>生产解析</b>给出；</li>
 *   <li>本类证明「{@code data} 段 → 三态 Verdict」这一段：ALLOW→ALLOW、真策略 DENY→DENY、
 *       {@code POLICY_UNDEFINED}/{@code OPA_UNAVAILABLE}/不可达 → FAIL_CLOSED。</li>
 * </ul>
 * 两侧的期望值都取自<b>同一份</b>
 * {@code security-engine-impl/src/test/resources/opa/data_access_oracles.json}
 * —— 本类按仓根相对路径读原文件，<b>禁复制</b>该文件到 ai 侧。</p>
 *
 * <p>矩阵期望（§4.7.2 AI 列）在 {@link #MATRIX_EXPECTED_VERDICT} 里显式写出，并与
 * oracle 录制态交叉核对：策略若被改成与矩阵不符，本类即刻变红（而不是跟着 oracle 一起绿）。</p>
 */
class AiGuardrailVerdictFromOracleTest {

    /** oracle 单源（security 侧 test resource，跨模块按仓根相对路径读同一文件）。 */
    private static final String ORACLE_REPO_PATH =
            "ecos_backend/engine/security-engine/security-engine-impl/src/test/resources/opa/data_access_oracles.json";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 详设-01 §4.7.2 AI 列的矩阵期望（出处是设计文本，不是 oracle 的回读）。 */
    private static final Map<String, String> MATRIX_EXPECTED_VERDICT = Map.of(
            "fin_ai", AiSecurityEngineClient.V_ALLOW,
            "proj_ai_p1", AiSecurityEngineClient.V_ALLOW,
            "proj_ai_p2", AiSecurityEngineClient.V_DENY,
            "proj_ai_no_scope", AiSecurityEngineClient.V_DENY,
            "dept_ai", AiSecurityEngineClient.V_ALLOW,
            "norole_ai", AiSecurityEngineClient.V_DENY,
            "ai_channel_inferred", AiSecurityEngineClient.V_ALLOW,
            "ai_channel_no_department", AiSecurityEngineClient.V_DENY,
            // #142/B4-3d-11：生产 AI 工具载荷形态（ToolAdjudicationRequest 只投影 projectIds）。
            // DENY 是本批登记的现状而非认可的终态 —— 域内可达性由 D-B4-30/D-B4-36 的服务端真值解析关掉。
            "ai_tool_no_department_declared", AiSecurityEngineClient.V_DENY);

    @Test
    @DisplayName("AI 列三态 — 矩阵期望与 oracle 录制态交叉核对后逐例判定")
    void verdictMappingMatchesMatrixAndRecordedOracle() throws IOException {
        List<Map<String, Object>> cases = loadCases();
        Set<String> covered = new TreeSet<>();
        for (Map<String, Object> c : cases) {
            String name = String.valueOf(c.get("name"));
            if (isAiChannelShape(c)) {
                covered.add(name);
                Map<String, Object> data = dataSegmentOf(c);
                String expected = MATRIX_EXPECTED_VERDICT.get(name);
                assertEquals(expected, AiSecurityEngineClient.verdictOf(data),
                        name + " 的 AI 通道判定与 §4.7.2 矩阵期望不符");
            }
        }
        assertEquals(MATRIX_EXPECTED_VERDICT.keySet(), covered,
                "oracle 里的 AI 列用例集合必须与矩阵期望集合上一一配对（新增/删除格须先改矩阵）");
    }

    @Test
    @DisplayName("拒绝态可回查 — DENY 时义务里的 deniedReason（违规名）必须随 data 段可得")
    void deniedReasonSurvivesOnAiSide() throws IOException {
        Map<String, Object> p2 = caseByName("proj_ai_p2");
        Map<String, Object> data = dataSegmentOf(p2);

        assertEquals(AiSecurityEngineClient.V_DENY, AiSecurityEngineClient.verdictOf(data));
        assertEquals("AI_SCOPE_OUT_OF_SUBJECT_DEPARTMENT", firstDeniedReason(data),
                "横向越权的违规名必须能在 AI 侧被记录（审计/告警据此定位 rego 里的 violation）");
    }

    @Test
    @DisplayName("理由码诚实化 — 生产工具形态落 AI_DEPARTMENT_NOT_DECLARED，禁冒充跨域（D-B4-29）")
    void undeclaredDepartmentHasItsOwnReasonCode() throws IOException {
        Map<String, Object> data = dataSegmentOf(caseByName("ai_tool_no_department_declared"));

        assertEquals(AiSecurityEngineClient.V_DENY, AiSecurityEngineClient.verdictOf(data));
        assertEquals("AI_DEPARTMENT_NOT_DECLARED", firstDeniedReason(data),
                "「载荷从未声明域」与「声明的域不是自己的域」是两件事：折叠成一个码会让运维对着"
                        + "一个从未声明域的工具调用去查这个人的跨域行为，方向从根上就错"
                        + "（与 §4.5.4「不可用 ≠ 无权限」、D-B4-26 同一条纪律的第三种形态）");
    }

    @Test
    @DisplayName("分态不折叠 — POLICY_UNDEFINED / OPA_UNAVAILABLE 落 FAIL_CLOSED 而非 DENY")
    void unevaluatedStatesAreFailClosedNotDeny() {
        for (String state : List.of(GuardrailDecisionContract.STATE_POLICY_UNDEFINED,
                GuardrailDecisionContract.STATE_UNAVAILABLE)) {
            Map<String, Object> data = Map.of(
                    GuardrailDecisionContract.KEY_ALLOW, false,
                    GuardrailDecisionContract.KEY_POLICY_STATE, state);
            assertEquals(AiSecurityEngineClient.V_FAIL_CLOSED, AiSecurityEngineClient.verdictOf(data),
                    state + " 的含义是「护栏没说话」，不得报成策略拒绝（§4.2/F06-14 同源口径）");
        }
        // 对照：真策略拒绝才是 DENY（两态若被写成同一个串，上面一条会一起失效）
        assertEquals(AiSecurityEngineClient.V_DENY, AiSecurityEngineClient.verdictOf(Map.of(
                GuardrailDecisionContract.KEY_ALLOW, false,
                GuardrailDecisionContract.KEY_POLICY_STATE, GuardrailDecisionContract.STATE_DENY)));
        // 兼容面：source=fallback / opaError 同样 FAIL_CLOSED（security 自己兜底而非策略裁决）
        assertEquals(AiSecurityEngineClient.V_FAIL_CLOSED, AiSecurityEngineClient.verdictOf(Map.of(
                GuardrailDecisionContract.KEY_ALLOW, false, "source", "fallback")));
        assertEquals(AiSecurityEngineClient.V_FAIL_CLOSED, AiSecurityEngineClient.verdictOf(Map.of(
                GuardrailDecisionContract.KEY_ALLOW, true, "opaError", "timeout")));
        assertEquals(AiSecurityEngineClient.V_FAIL_CLOSED, AiSecurityEngineClient.verdictOf(Map.of()),
                "空 data 段不得默认放行");
    }

    @Test
    @DisplayName("单源策略名 — 裁决体 policy 取底座常量，禁内联 rbac/含点号名")
    void adjudicationBodyCarriesSingleSourcePolicyName() {
        Map<String, Object> body = unreachableClient().evaluateBodyForAdjudication(Map.of("purpose", "ai"));

        assertEquals(GuardrailDecisionContract.POLICY_DATA_ACCESS, body.get("policy"),
                "AI 通道与页面/导出通道必须查同一份策略，否则「三通道同源」只在入口形态成立（D-B4-22）");
        assertFalse(String.valueOf(body.get("policy")).contains("."),
                "策略名必须过 OpaPolicyService 的白名单（不含点号）");
    }

    @Test
    @DisplayName("传输失败如实抛出 — evaluateData 不吞异常，由闸侧转 FAIL_CLOSED")
    void unreachableSecurityPropagatesInsteadOfSilentDeny() {
        assertThrows(Exception.class,
                () -> unreachableClient().evaluateData(Map.of(
                        "policy", GuardrailDecisionContract.POLICY_DATA_ACCESS,
                        "input", Map.of("purpose", "ai"))),
                "连不上 security 必须抛（不可用态），禁在此折叠成 DENY 或空放行");
    }

    /** 对端不可达的客户端（只用于线路面用例；三态判定面走静态 {@code verdictOf}）。 */
    @SuppressWarnings("unchecked")
    private static AiSecurityEngineClient unreachableClient() {
        return new AiSecurityEngineClient("http://127.0.0.1:1",
                AiSecurityEngineClient.POLICY_TIMEOUT_MS, mock(ObjectProvider.class));
    }

    // ── oracle 读取与 data 段构造 ────────────────────────────────────────────

    private static List<Map<String, Object>> loadCases() throws IOException {
        Map<String, Object> root = MAPPER.readValue(repoFile(ORACLE_REPO_PATH).toFile(),
                new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
        Object cases = root.get("cases");
        if (!(cases instanceof List<?> list) || list.isEmpty()) {
            throw new AssertionError("oracle cases 段缺失/为空 —— 禁当成「无 oracle 所以放行」");
        }
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> out = (List<Map<String, Object>>) list;
        return out;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> caseByName(String name) throws IOException {
        return loadCases().stream().filter(c -> name.equals(String.valueOf(c.get("name")))).findFirst()
                .orElseThrow(() -> new AssertionError("oracle 里没有用例 " + name));
    }

    /** AI 列形态：显式 {@code purpose=ai}，或无 purpose 但带 {@code environment.channel=AGENT}。 */
    @SuppressWarnings("unchecked")
    private static boolean isAiChannelShape(Map<String, Object> c) {
        Map<String, Object> input = (Map<String, Object>) c.get("input");
        if ("ai".equals(input.get("purpose"))) return true;
        Object env = input.get("environment");
        return env instanceof Map<?, ?> m && "AGENT".equals(m.get("channel"));
    }

    /**
     * 按生产 {@code OpaPolicyService#parseEvalResponse} 的输出形态取 {@code data} 段
     * —— 该形态由 security 侧 {@code OpaDocumentShapeTest} 逐例证明；本类不重算裁决，
     * 只把录制态摆成消费方实际收到的键名（全部取 {@link GuardrailDecisionContract}，禁内联）。
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> dataSegmentOf(Map<String, Object> oracleCase) {
        boolean allow = Boolean.TRUE.equals(oracleCase.get("allow"));
        Object obligations = oracleCase.get("obligations");
        Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put(GuardrailDecisionContract.KEY_ALLOW, allow);
        data.put(GuardrailDecisionContract.KEY_POLICY_STATE,
                allow ? GuardrailDecisionContract.STATE_ALLOW : GuardrailDecisionContract.STATE_DENY);
        data.put(GuardrailDecisionContract.KEY_OBLIGATIONS,
                obligations instanceof List<?> l ? l : List.of());
        data.put(GuardrailDecisionContract.KEY_POLICY_REF,
                "ecos." + GuardrailDecisionContract.POLICY_DATA_ACCESS);
        data.put("source", "opa");
        return data;
    }

    @SuppressWarnings("unchecked")
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

    private static Path repoFile(String repoRelative) throws IOException {
        Path cur = Paths.get("").toAbsolutePath();
        for (int i = 0; i < 12 && cur != null; i++) {
            Path candidate = cur.resolve(repoRelative);
            if (Files.isRegularFile(candidate)) return candidate;
            cur = cur.getParent();
        }
        throw new IOException("未找到 oracle 单源: " + repoRelative
                + "（本类按仓根相对路径读 security 侧同一文件，禁复制）");
    }
}
