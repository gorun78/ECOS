package com.chinacreator.gzcm.engine.cognitive2.seam;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.cognitive.api.CognitiveApiPaths;
import com.chinacreator.gzcm.engine.cognitive.api.CognitiveErrorCode;
import com.chinacreator.gzcm.engine.cognitive2.controller.DecisionBasisController;
import com.chinacreator.gzcm.engine.cognitive2.controller.scenario.CognitiveErrorAdvice;
import com.chinacreator.gzcm.engine.cognitive2.dao.DecisionDao;
import com.chinacreator.gzcm.engine.cognitive2.service.decision.DecisionBasisService;
import com.chinacreator.gzcm.engine.cognitive2.service.scenario.CognitiveGuardException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Decision Basis 只读解析端点验收（差距分析附录A 第 19 条 = 详设接口总表第 5416 行
 * {@code getDecisionBasis}；语义单源 = 补充设计 §10.2.9）。
 *
 * <p>本类钉五条不变量：</p>
 * <ol>
 *   <li><b>只出真行</b>：载荷字段逐字来自 DAO 行集，底座 {@code metadata} jsonb 载体列不出面
 *       （把未解析 JSON 塞进依据段＝伪结构化）；</li>
 *   <li><b>三段诚实口径</b>：属主内载体空集记 {@code EMPTY}、跨域/无列面记 {@code UNRESOLVED}
 *       且必须带 {@code owner} + {@code reason} 并<b>禁出 payload 键</b>（禁合成占位值）；</li>
 *   <li><b>时间锚定不静默升级</b>：带 {@code version} / {@code hash} 的 pinned 引用在认知底座
 *       无锚定载体 ⇒ 404 {@code ECOS-COG-404D}，并取证"未做任何依据段读取"；</li>
 *   <li><b>入参 fail-loud</b>：空白/超宽 decisionId、出现但全空白的引用段 ⇒ 400
 *       {@code ECOS-COG-400P}，不静默当"未提供"；</li>
 *   <li><b>表面只读 + 常量驱动</b>：类前缀单值 {@code COGNITIVE}、唯一 handler 是 GET、
 *       路径取 {@code DECISION_BASIS_REL}；错误矩阵经 {@code CognitiveErrorAdvice} 落真实 404。</li>
 * </ol>
 */
@DisplayName("附录A 第 19 条 Decision Basis 只读解析端点")
class DecisionBasisEndpointTest {

    private static final String DECISION_ID = "d-0a1b2c3d";

    private final DecisionDao dao = mock(DecisionDao.class);
    private final DecisionBasisService service = new DecisionBasisService(dao);
    private final DecisionBasisController controller = new DecisionBasisController(service);

    /** §10.2.9 八段的冻结顺序（设计原文编号 ①~⑧；顺序漂移即 diff 可见）。 */
    private static final List<String> FROZEN_SEGMENT_ORDER = List.of(
            "fact_snapshot_quality",
            "ontology_metric_caliber_version",
            "knowledge_profile_hypothesis_evidence",
            "forecast_run",
            "diagnosis_counterfactual",
            "evaluation_risk_open_gaps",
            "options_and_comparison",
            "authorization_approval_and_expiry");

    // ── 夹具 ────────────────────────────────────────────────────────

    private static Map<String, Object> decisionRow() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", DECISION_ID);
        row.put("category", "vendor_selection");
        row.put("scenario", "供应商续约评估");
        row.put("reasoning", "交付准时率连续两季低于阈值");
        row.put("outcome", "RENEW_WITH_CLAUSE");
        row.put("confidence", new java.math.BigDecimal("0.6400"));
        row.put("decision_maker", "agent-w-1");
        row.put("valid_from", Timestamp.valueOf("2026-10-01 09:00:00"));
        row.put("valid_until", Timestamp.valueOf("2026-12-31 23:59:59"));
        row.put("created_at", Timestamp.valueOf("2026-10-01 09:00:00"));
        row.put("updated_at", Timestamp.valueOf("2026-10-02 08:00:00"));
        // 驱动侧 jsonb 载体列以字符串形态出现即可（本夹具不依赖 org.postgresql.util.PGobject），
        // 判定点是「本面不得把未解析 JSON 冒充结构化依据」，与载体具体类型无关。
        row.put("metadata", "{\"carrier\":\"jsonb\"}");
        return row;
    }

    private static Map<String, Object> approvalRow() {
        Map<String, Object> row = new HashMap<>();
        row.put("id", "ap-1");
        row.put("approver", "u-77");
        row.put("level", 2);
        row.put("status", "approved");
        row.put("comment", "续约附交付条款");
        row.put("created_at", Timestamp.valueOf("2026-10-01 10:00:00"));
        return row;
    }

    /** 只 stub 底座行；依据段各载体按需再 stub。 */
    private void givenDecisionRowExists() {
        when(dao.findDecisionById(DECISION_ID)).thenReturn(List.of(decisionRow()));
    }

    // ── ① 真行只读解析 ──────────────────────────────────────────────

    @Test
    @DisplayName("GET /decision-basis/{decisionId}：载荷逐字来自 DAO 行集，metadata 载体列不出面")
    void resolvesCognitiveBaseRowsVerbatim() {
        givenDecisionRowExists();
        when(dao.findApprovalsByDecision(DECISION_ID)).thenReturn(List.of(approvalRow()));
        when(dao.findPendingExceptions(DECISION_ID)).thenReturn(List.of());
        when(dao.findPrecedentsByDecision(DECISION_ID)).thenReturn(List.of());
        when(dao.queryProvenance("decision", DECISION_ID)).thenReturn(List.of());
        when(dao.findParentLinks(DECISION_ID)).thenReturn(List.of());
        when(dao.findDownstreamLinks(DECISION_ID)).thenReturn(List.of());
        when(dao.findActivePoliciesForBasis("vendor_selection")).thenReturn(List.of());

        ApiResponse<Map<String, Object>> resp = controller.getDecisionBasis(DECISION_ID, null, null);
        assertTrue(resp.isSuccess(), "只读解析成功态必须是 success 信封（契约原文：只读解析结果，无写回）");
        Map<String, Object> data = resp.getData();

        assertEquals(Boolean.TRUE, data.get("readOnly"), "响应必须自证只读面");
        assertEquals(DECISION_ID, data.get("decisionId"));

        @SuppressWarnings("unchecked")
        Map<String, Object> decision = (Map<String, Object>) data.get("decision");
        assertEquals("vendor_selection", decision.get("category"), "列名口径必须与库侧一致（禁改名漂移）");
        assertEquals("RENEW_WITH_CLAUSE", decision.get("outcome"));
        assertEquals(new java.math.BigDecimal("0.6400"), decision.get("confidence"),
                "置信度原样透出，禁四舍五入或换算");
        assertEquals("2026-10-01T09:00", decision.get("valid_from"),
                "时间列必须归一成 ISO-8601 字符串（禁驱动相关形态）");
        assertFalse(decision.containsKey("metadata"),
                "jsonb 载体列不得出现在依据面（未解析 JSON 冒充结构化依据＝伪实现）");

        Map<String, Object> citation = asMap(data.get("citation"));
        assertEquals(Boolean.FALSE, citation.get("anchored"),
                "未带 pinned 引用时必须显式声明未锚定，禁让调用方误读成当时版本");
        assertEquals("UNPINNED_CURRENT_READ", citation.get("anchorStatus"));
        assertNull(citation.get("version"), "未请求版本 ⇒ 引用段回 null，禁回填猜测值");
        assertNull(citation.get("hash"), "未请求 hash ⇒ 引用段回 null，禁生成占位摘要");
    }

    @Test
    @DisplayName("八段顺序冻结；属主内可解析段出 payload，跨域段只出 owner+reason")
    void segmentsFollowDesignOrderAndNeverFabricate() {
        givenDecisionRowExists();
        when(dao.findApprovalsByDecision(DECISION_ID)).thenReturn(List.of(approvalRow()));

        Map<String, Object> data = controller.getDecisionBasis(DECISION_ID, null, null).getData();
        List<Map<String, Object>> segments = asList(data.get("segments"));
        assertEquals(FROZEN_SEGMENT_ORDER.size(), segments.size(), "依据段数量必须等于 §10.2.9 的 8 段");

        List<String> order = new ArrayList<>();
        for (Map<String, Object> seg : segments) {
            order.add((String) seg.get("segment"));
            assertNotNull(seg.get("owner"), seg.get("segment") + "：每段必须标属主");
            String status = (String) seg.get("status");
            assertTrue(List.of("RESOLVED", "EMPTY", "UNRESOLVED").contains(status),
                    seg.get("segment") + "：状态必须落在三段口径内，实际=" + status);
            if ("UNRESOLVED".equals(status)) {
                assertFalse(seg.containsKey("payload"),
                        seg.get("segment") + "：不可解析段禁出 payload（禁合成占位值）");
                assertNotNull(seg.get("reason"), seg.get("segment") + "：不可解析段必须给原因");
                assertFalse(String.valueOf(seg.get("reason")).isBlank(),
                        seg.get("segment") + "：原因不得为空散文");
            }
        }
        assertEquals(FROZEN_SEGMENT_ORDER, order, "依据段顺序漂移（§10.2.9 ①~⑧）");

        Map<String, Object> authorization = segments.get(7);
        assertEquals("RESOLVED", authorization.get("status"), "有审批行 ⇒ 授权段必须 RESOLVED");
        String carrier = (String) authorization.get("carrier");
        assertTrue(carrier.startsWith("ecos_cognitive."), "载体清单必须 schema 限定（§四附则1）：" + carrier);
        Map<String, Object> payload = asMap(authorization.get("payload"));
        assertEquals(List.of(approvalRow()), payload.get("approvals"), "载荷必须逐字来自 DAO 行集");
    }

    @Test
    @DisplayName("载体在位但查无行 ⇒ EMPTY（与「没实现」的 UNRESOLVED 严格区分）")
    void emptyCarriersAreEmptyNotUnresolved() {
        givenDecisionRowExists(); // 其余 DAO 默认返回空集（Mockito 空 List 默认值）

        Map<String, Object> data = controller.getDecisionBasis(DECISION_ID, null, null).getData();
        List<Map<String, Object>> segments = asList(data.get("segments"));
        Map<String, Object> authorization = segments.get(7);
        assertEquals("EMPTY", authorization.get("status"),
                "审批链查询真实执行且结果为空 ⇒ 必须是 EMPTY，禁冒充 UNRESOLVED 也禁冒充 RESOLVED");
        Map<String, Object> payload = asMap(authorization.get("payload"));
        assertEquals(List.of(), payload.get("approvals"));
        assertEquals(List.of(), payload.get("active_policies"), "策略段按 category 精确匹配，空集如实为空");
    }

    // ── ② 不存在与 pinned 引用 ───────────────────────────────────────

    @Test
    @DisplayName("决策不存在 ⇒ 404 ECOS-COG-404D + data.missing 指向认知底座表与 id")
    void unknownDecisionFailsLoudWith404() {
        when(dao.findDecisionById("missing-id")).thenReturn(List.of());

        CognitiveGuardException e = assertThrows(CognitiveGuardException.class,
                () -> controller.getDecisionBasis("missing-id", null, null));
        assertEquals(CognitiveErrorCode.E404_DECISION_BASIS, e.errorCode);
        assertEquals(404, e.httpStatus, "新登记码必须被 httpOf 认账（否则 default→500 静默降级）");
        assertEquals(CognitiveErrorCode.MSG_DECISION_BASIS_NOT_FOUND, e.getMessage(),
                "响应 message 只能是冻结字面量（F05-07 #4 禁泄漏）");
        assertEquals("ecos_cognitive.ecos_decision:missing-id", e.missing);
    }

    @Test
    @DisplayName("带 version/hash 的 pinned 引用不可锚定 ⇒ 404，且取证「一次依据段读取都没做」")
    void pinnedCitationNeverSilentlyUpgradesToCurrentRow() {
        givenDecisionRowExists();

        CognitiveGuardException versioned = assertThrows(CognitiveGuardException.class,
                () -> controller.getDecisionBasis(DECISION_ID, "3", null));
        assertEquals(CognitiveErrorCode.E404_DECISION_BASIS, versioned.errorCode);
        assertEquals(404, versioned.httpStatus);
        assertTrue(versioned.missing.contains("pinned citation carrier"),
                "missing 必须取证锚定载体缺口，实际=" + versioned.missing);

        CognitiveGuardException hashed = assertThrows(CognitiveGuardException.class,
                () -> controller.getDecisionBasis(DECISION_ID, null, "sha256:abcd"));
        assertEquals(CognitiveErrorCode.E404_DECISION_BASIS, hashed.errorCode);

        // 反向补强：拒绝发生在依据段装配之前 ⇒ 不存在"当前行 + 空 hash"的伪锚定 200 路径
        verify(dao, never()).findApprovalsByDecision(anyString());
        verify(dao, never()).findPrecedentsByDecision(anyString());
        verify(dao, never()).findActivePoliciesForBasis(anyString());
        verify(dao, never()).queryProvenance(anyString(), anyString());
    }

    // ── ③ 入参 fail-loud ────────────────────────────────────────────

    @Test
    @DisplayName("空白/超宽 decisionId 与「出现但全空白」的引用段 ⇒ 400 ECOS-COG-400P，不静默降级")
    void invalidParametersAreRejectedNotIgnored() {
        for (String bad : List.of("", "   ")) {
            CognitiveGuardException e = assertThrows(CognitiveGuardException.class,
                    () -> controller.getDecisionBasis(bad, null, null), bad + " 必须 fail-loud");
            assertEquals(CognitiveErrorCode.E400_INVALID_PARAM, e.errorCode);
            assertEquals(400, e.httpStatus);
        }
        assertThrows(CognitiveGuardException.class, () -> controller.getDecisionBasis(null, null, null));

        CognitiveGuardException tooLong = assertThrows(CognitiveGuardException.class,
                () -> controller.getDecisionBasis("x".repeat(65), null, null));
        assertEquals(CognitiveErrorCode.E400_INVALID_PARAM, tooLong.errorCode,
                "超 V103 列宽（64）属结构非法，禁进 SQL 后再判空");

        CognitiveGuardException blankVersion = assertThrows(CognitiveGuardException.class,
                () -> controller.getDecisionBasis(DECISION_ID, " ", null));
        assertEquals(CognitiveErrorCode.E400_INVALID_PARAM, blankVersion.errorCode,
                "引用段出现但全空白不得当作「未提供」降级成当前态解析");
        assertTrue(blankVersion.missing.startsWith(CognitiveApiPaths.PARAM_VERSION + " "),
                "missing 必须定位到具体参数名，实际=" + blankVersion.missing);

        CognitiveGuardException blankHash = assertThrows(CognitiveGuardException.class,
                () -> controller.getDecisionBasis(DECISION_ID, null, "\t"));
        assertTrue(blankHash.missing.startsWith(CognitiveApiPaths.PARAM_HASH + " "),
                "missing 必须定位到具体参数名，实际=" + blankHash.missing);
    }

    // ── ④ 表面形态 + 传输层状态行 ────────────────────────────────────

    @Test
    @DisplayName("端点表面：单值 COGNITIVE 前缀 + 唯一 GET handler + 路径取 DECISION_BASIS_REL 常量")
    void surfaceIsReadOnlyAndConstantDriven() {
        RequestMapping root = DecisionBasisController.class.getAnnotation(RequestMapping.class);
        assertEquals(1, root.value().length, "类级前缀必须单值（禁 /api/ 旧前缀双写）");
        assertEquals(CognitiveApiPaths.COGNITIVE, root.value()[0],
                "复用既有前缀 ⇒ VersionPrefixRewriteFilter 不新增条目（该族 KEEP v1）");

        int getHandlers = 0;
        for (Method m : DecisionBasisController.class.getDeclaredMethods()) {
            assertTrue(m.getAnnotation(PostMapping.class) == null
                            && m.getAnnotation(PutMapping.class) == null
                            && m.getAnnotation(DeleteMapping.class) == null
                            && m.getAnnotation(PatchMapping.class) == null,
                    m.getName() + "：Decision Basis 是只读解析面，出现写映射即违 §10.2.9「无写回」");
            GetMapping get = m.getAnnotation(GetMapping.class);
            if (get != null) {
                getHandlers++;
                assertEquals(1, get.value().length, m.getName() + "：路径必须取 CognitiveApiPaths 常量");
                assertEquals(CognitiveApiPaths.DECISION_BASIS_REL, get.value()[0],
                        m.getName() + "：方法级只能用相对段（绝对常量会拼成双前缀）");
            }
        }
        assertEquals(1, getHandlers, "本契约只有 getDecisionBasis 一枚端点");

        // 契约绝对形态（附录A 第 19 条原文路径）由常量拼接保证
        assertEquals("/api/v1/cognitive/decision-basis/{decisionId}",
                CognitiveApiPaths.DECISION_BASIS);
    }

    @Test
    @DisplayName("传输层保真：advice 把 404D 落成真实 HTTP 404 + code/errorCode/missing 同面")
    void errorAdviceCarriesRealStatusLine() {
        when(dao.findDecisionById("nope")).thenReturn(List.of());
        CognitiveGuardException e = assertThrows(CognitiveGuardException.class,
                () -> controller.getDecisionBasis("nope", null, null));

        ResponseEntity<ApiResponse<Map<String, Object>>> re = new CognitiveErrorAdvice().onGuard(e);
        assertEquals(HttpStatus.NOT_FOUND, re.getStatusCode(), "404D 必须落真实 404 状态行（禁 200 伪数据）");
        ApiResponse<Map<String, Object>> body = re.getBody();
        assertNotNull(body);
        assertEquals(404, body.getCode(), "信封 code 与 HTTP 状态行同号");
        assertEquals(CognitiveErrorCode.E404_DECISION_BASIS, body.getErrorCode());
        assertEquals(CognitiveErrorCode.MSG_DECISION_BASIS_NOT_FOUND, body.getMessage());
        Map<String, Object> data = asMap(body.getData());
        assertEquals("ecos_cognitive.ecos_decision:nope", data.get("missing"));
        assertFalse(data.containsKey("stub"), "404D 是真实不存在，不是未实现桩，禁带 stub 标记");
    }

    // ── 工具 ────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        assertNotNull(value, "载荷缺键（应为 Map）");
        assertTrue(value instanceof Map, "载荷形态必须是 Map，实际=" + value.getClass());
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> asList(Object value) {
        assertNotNull(value, "载荷缺键（应为 List）");
        assertTrue(value instanceof List, "载荷形态必须是 List，实际=" + value.getClass());
        return (List<Map<String, Object>>) value;
    }
}
