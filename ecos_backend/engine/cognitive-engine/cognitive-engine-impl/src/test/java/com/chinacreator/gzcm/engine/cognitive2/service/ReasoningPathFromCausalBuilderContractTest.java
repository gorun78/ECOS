package com.chinacreator.gzcm.engine.cognitive2.service;

import com.chinacreator.gzcm.engine.cognitive2.model.CausalChainNode;
import com.chinacreator.gzcm.engine.cognitive2.model.JustificationClause;
import com.chinacreator.gzcm.engine.cognitive2.model.PrecedentRef;
import com.chinacreator.gzcm.engine.cognitive2.model.ReasoningPath;
import com.chinacreator.gzcm.engine.cognitive2.model.ReasoningStep;
import com.chinacreator.gzcm.engine.cognitive2.model.RuleRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Wave-5.1 T-08 — ReasoningPathFromCausalBuilder 行为测试。
 *
 * <p>对应任务 3: 走 contract 5 字段 (clauseId/clauseType/stepRef/text/weight) + contract location + 0 RuleRef = 空 stats.paid
 *
 * <p>覆盖:
 * <ol>
 *   <li>buildSteps: KG 节点 → ReasoningStep sourceType=KG, 5 字段必填</li>
 *   <li>buildSteps: RULE 节点 + ruleId → ruleRef 注入 (G2)</li>
 *   <li>buildSteps: precedent 节点 → precedentRef 注入</li>
 *   <li>buildPath: maxDepth 截断 — 3 层截到 2 层 (多余层丢弃)</li>
 *   <li>buildPath: 0 ruleRef → 空规则计数 (stats.paid 防御)</li>
 *   <li>buildClauses: 每 node → 1 clause, 类型 RULE_TRIGGER / FACT_ACCRUAL 正确切换</li>
 * </ol>
 *
 * @author ECOS Cognitive Engine Team
 * @since 2026-09-02 (Wave-5.1)
 */
class ReasoningPathFromCausalBuilderContractTest {

    private ReasoningPathFromCausalBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new ReasoningPathFromCausalBuilder();
    }

    // ── buildSteps: 5 字段 contract ──

    @Test
    @DisplayName("T-08-3-1: buildSteps 5 字段 contract (stepId/stepIndex/outputFact/confidence/sourceType)")
    void buildStepsFillsFiveFields() {
        List<CausalChainNode> nodes = new ArrayList<>();
        nodes.add(new CausalChainNode(1, "metric-1", 0.95, "metric"));
        nodes.add(new CausalChainNode(2, "kg-2", 0.8, "KG"));
        nodes.add(new CausalChainNode(3, "kg-3", 0.7, "KG"));
        ReasoningPathFromCausalBuilder.Context ctx =
                new ReasoningPathFromCausalBuilder.Context(Collections.emptyMap(), Collections.emptyMap());

        List<ReasoningStep> steps = builder.buildSteps(nodes, ctx);

        assertEquals(3, steps.size());
        for (ReasoningStep s : steps) {
            assertNotNull(s.getStepId(), "stepId 不能 null");
            assertTrue(s.getStepIndex() >= 1);
            assertNotNull(s.getDescription(), "description 不能 null");
            assertNotNull(s.getInputFacts(), "inputFacts 不能 null");
            assertNotNull(s.getOutputFact(), "outputFact 不能 null");
            assertNotNull(s.getSourceType(), "sourceType 不能 null");
            assertTrue(s.getConfidence() > 0 && s.getConfidence() <= 1);
        }
        // 字段语义
        assertEquals("depth-1", steps.get(0).getStepId());
        assertEquals(1, steps.get(0).getStepIndex());
        assertEquals("metric-1", steps.get(0).getOutputFact());
        assertEquals("METRIC", steps.get(0).getSourceType(), "source metric → uppercase METRIC (源码 setSourceType toUpperCase)");
    }

    // ── RULE 节点带 ruleRef ──

    @Test
    @DisplayName("T-08-3-2: RULE 节点 + ruleId → ruleRef 必注入 (G2)")
    void ruleNodeCarriesRuleRef() {
        CausalChainNode ruleNode = new CausalChainNode(1, "rule-1", 0.9, "RULE", "finance");
        ruleNode.setRuleId("R-99");
        ruleNode.setRuleName("大额审批");

        RuleRef inCtx = new RuleRef("R-99", "大额审批", "#a>100", "REJECT");
        inCtx.setSourceRank(5);
        Map<String, RuleRef> ruleRefs = new LinkedHashMap<>();
        ruleRefs.put("R-99", inCtx);
        ReasoningPathFromCausalBuilder.Context ctx =
                new ReasoningPathFromCausalBuilder.Context(ruleRefs, Collections.emptyMap());

        List<ReasoningStep> steps = builder.buildSteps(List.of(ruleNode), ctx);

        assertEquals(1, steps.size());
        ReasoningStep s = steps.get(0);
        assertEquals("R-99", s.getRuleRef().getRuleId(), "RULE+ruleId 必须有 ruleRef");
        assertEquals("大额审批", s.getRuleRef().getRuleName());
        assertEquals(5, s.getRuleRef().getSourceRank(), "ctx 注入的 sourceRank 透传");
    }

    @Test
    @DisplayName("T-08-3-3: RULE 节点 ruleId 缺失 → ruleRef null (G2 兜底: 不应注入空 ref)")
    void ruleNodeWithoutRuleIdHasNullRuleRef() {
        CausalChainNode ru = new CausalChainNode(1, "rule-x", 0.9, "RULE", null); // 无 ruleId
        ReasoningPathFromCausalBuilder.Context ctx =
                new ReasoningPathFromCausalBuilder.Context(Collections.emptyMap(), Collections.emptyMap());

        List<ReasoningStep> steps = builder.buildSteps(List.of(ru), ctx);

        assertNull(steps.get(0).getRuleRef(), "ruleId 缺失 → ruleRef 必须 null");
    }

    // ── PRECEDENT 节点 ──

    @Test
    @DisplayName("T-08-3-4: 'precedent:xxx' 前缀 → precedentRef 注入 + sourceType=PRECEDENT")
    void precedentNodeInjectsPrecedentRef() {
        PrecedentRef pre = new PrecedentRef("P-1", "D-1", "历史案例", "approved", 0.85);
        Map<String, PrecedentRef> preRefs = new LinkedHashMap<>();
        preRefs.put("P-1", pre);
        ReasoningPathFromCausalBuilder.Context ctx =
                new ReasoningPathFromCausalBuilder.Context(Collections.emptyMap(), preRefs);

        CausalChainNode n = new CausalChainNode(2, "precedent:P-1 历史案例 summary", 0.8, "KG");
        List<ReasoningStep> steps = builder.buildSteps(List.of(n), ctx);

        assertNotNull(steps.get(0).getPrecedentRef(), "precedent: 前缀必须注入 precedentRef");
        assertEquals("PRECEDENT", steps.get(0).getSourceType());
    }

    // ── buildPath: maxDepth 截断 ──

    @Test
    @DisplayName("T-08-3-5: buildPath maxDepth=2 → 4 层链裁到 2 层")
    void buildPathTruncatesToMaxDepth() {
        List<CausalChainNode> nodes = new ArrayList<>();
        nodes.add(new CausalChainNode(1, "m1", 1.0, "metric"));
        nodes.add(new CausalChainNode(2, "n2", 0.8, "KG"));
        nodes.add(new CausalChainNode(3, "n3", 0.7, "KG"));
        nodes.add(new CausalChainNode(4, "n4", 0.6, "KG"));

        ReasoningPathFromCausalBuilder.Context ctx =
                new ReasoningPathFromCausalBuilder.Context(Collections.emptyMap(), Collections.emptyMap());
        ReasoningPath path = builder.buildPath(nodes, "root cause", ctx, 2);

        assertEquals(2, path.getSteps().size(), "maxDepth=2 必须裁到 2 层, 实际=" + path.getSteps().size());
        assertNotNull(path.getConclusion());
        assertNotNull(path.getJustification());
        assertEquals("root cause", path.getConclusion());
    }

    @Test
    @DisplayName("T-08-3-6: buildPath maxDepth<=0 → 不截断")
    void buildPathNoTruncationWhenMaxDepthZero() {
        List<CausalChainNode> nodes = new ArrayList<>();
        nodes.add(new CausalChainNode(1, "m", 1.0, "metric"));
        nodes.add(new CausalChainNode(2, "n2", 0.8, "KG"));
        nodes.add(new CausalChainNode(3, "n3", 0.7, "KG"));

        ReasoningPathFromCausalBuilder.Context ctx =
                new ReasoningPathFromCausalBuilder.Context(Collections.emptyMap(), Collections.emptyMap());
        ReasoningPath path = builder.buildPath(nodes, "x", ctx, 0);

        assertEquals(3, path.getSteps().size(), "maxDepth=0 不截断");
    }

    // ── 0 RuleRef → 空 stats (任务 3 contract) ──

    @Test
    @DisplayName("T-08-3-7: 0 RuleRef 链 → ruleRefs 空 list (stats.paid 防御)")
    void emptyRuleRefsWhenNoRuleNodes() {
        List<CausalChainNode> nodes = List.of(
                new CausalChainNode(1, "m", 1.0, "metric"),
                new CausalChainNode(2, "kg-1", 0.8, "KG")
        );
        ReasoningPathFromCausalBuilder.Context ctx =
                new ReasoningPathFromCausalBuilder.Context(Collections.emptyMap(), Collections.emptyMap());
        ReasoningPath path = builder.buildPath(nodes, "c", ctx, 10);

        assertNotNull(path.getRuleRefs());
        assertTrue(path.getRuleRefs().isEmpty(), "无 RULE 节点时 ruleRefs 必须 empty");
        // clause 全部 FACT_ACCRUAL
        assertEquals(2, path.getClauses().size());
        for (JustificationClause c : path.getClauses()) {
            assertEquals(JustificationClause.TYPE_FACT_ACCRUAL, c.getClauseType());
        }
    }

    // ── buildSteps null/empty 防御 ──

    @Test
    @DisplayName("T-08-3-8: buildSteps null/empty → emptyList")
    void buildStepsNullAndEmptyDefensively() {
        ReasoningPathFromCausalBuilder.Context ctx =
                new ReasoningPathFromCausalBuilder.Context(null, null);
        assertTrue(builder.buildSteps(null, ctx).isEmpty());
        assertTrue(builder.buildSteps(Collections.emptyList(), ctx).isEmpty());
    }

    // ── buildClauses 上限 8 ──

    @Test
    @DisplayName("T-08-3-9: 10 层链 clauses 上限 8 (不超)")
    void buildClausesCappedAtEight() {
        List<CausalChainNode> nodes = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            nodes.add(new CausalChainNode(i, "n"+i, 0.5, "KG"));
        }
        ReasoningPathFromCausalBuilder.Context ctx =
                new ReasoningPathFromCausalBuilder.Context(Collections.emptyMap(), Collections.emptyMap());
        ReasoningPath path = builder.buildPath(nodes, "c", ctx, 10);

        assertEquals(8, path.getClauses().size(), "clauses 必须裁到 8 (上限)");
        // 8 个 step 仍然全保留 (steps 不在 buildClauses 限制内)
        assertEquals(10, path.getSteps().size());
    }
}
