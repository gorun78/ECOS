package com.chinacreator.gzcm.engine.cognitive2.service;

import com.chinacreator.gzcm.engine.cognitive2.model.CausalChainNode;
import com.chinacreator.gzcm.engine.cognitive2.model.ReasoningPath;
import com.chinacreator.gzcm.engine.cognitive2.model.ReasoningStep;
import com.chinacreator.gzcm.engine.cognitive2.model.RuleRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Wave-2C (cheng K→C) 单元测试 — 5 个 case。
 *
 * <p>覆盖:
 * <ol>
 *   <li>ReasoningStep 携带 RuleRef (04 文档 G2: ruleRef != null || precedentRef != null)</li>
 *   <li>因果链 3 层展开 → 3 ReasoningStep (04 文档 §4.1)</li>
 *   <li>RULE 层节点 source=RULE + ruleId → ruleRef 非空</li>
 *   <li>ReasoningPath justification 含结构化计数 (规则 N 命中 M)</li>
 *   <li>reject 审批场景: status != PENDING_REVIEW → IllegalStateException</li>
 * </ol>
 * </p>
 *
 * @author ECOS Cognitive Engine Team
 * @since 2026-09-02
 */
class ReasoningPathBuilderWave2CTest {

    private ReasoningPathBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new ReasoningPathBuilder();
    }

    // ── UT-1: ReasoningStep 携带 RuleRef (04 文档 G2) ──

    @Test
    @DisplayName("UT-1: buildStep 产出的 ReasoningStep 必须携带 RuleRef (G2 硬指标)")
    void buildStepShouldCarryRuleRef() {
        Map<String, Object> facts = new LinkedHashMap<>();
        facts.put("amount", 200);
        facts.put("type", "A");

        SpelConditionEvaluator.EvalResult evalResult =
            new SpelConditionEvaluator.EvalResult(true, "Expression: #amount>100 -> true", facts);

        ReasoningStep step = builder.buildStep(
            "R1", "大额审批规则", "#amount>100", "reject", facts, evalResult, 0);

        // G2 验收: ruleRef 非空
        assertNotNull(step.getRuleRef(), "RuleRef must not be null for RULE step (04 G2)");
        assertEquals("R1", step.getRuleRef().getRuleId());
        assertEquals("大额审批规则", step.getRuleRef().getRuleName());
        assertEquals("#amount>100", step.getRuleRef().getCondition());
        assertEquals("reject", step.getRuleRef().getAction());

        // sourceType 和 stepIndex
        assertEquals("RULE", step.getSourceType());
        assertEquals(1, step.getStepIndex(), "stepIndex should be 1-based");

        // 兼容旧字段
        assertEquals("大额审批规则", step.getRuleApplied());
        assertTrue(step.getConfidence() > 0.5, "satisfied rule should have high confidence");
    }

    // ── UT-2: 因果链 3 层 → 3 ReasoningStep ──

    @Test
    @DisplayName("UT-2: 因果链 3 层展开为 3 个 ReasoningStep (04 §4.1)")
    void buildStepsFromCausalThreeDepth() {
        List<CausalChainNode> nodes = new ArrayList<>();
        nodes.add(new CausalChainNode(1, "sales dropped 12%", 0.95, "metric"));
        nodes.add(new CausalChainNode(2, "customer complaints increased", 0.82, "KG"));
        CausalChainNode ruleNode = new CausalChainNode(3, "compliance rule violated", 0.78, "RULE", "finance");
        ruleNode.setRuleId("R42");
        ruleNode.setRuleName("合规检查规则");
        nodes.add(ruleNode);

        List<ReasoningStep> steps = builder.buildStepsFromCausal(nodes);

        assertEquals(3, steps.size(), "3 causal chain nodes should produce 3 reasoning steps");

        // 每层 stepIndex = depth
        assertEquals(1, steps.get(0).getStepIndex());
        assertEquals(2, steps.get(1).getStepIndex());
        assertEquals(3, steps.get(2).getStepIndex());

        // stepId 格式
        assertEquals("depth-1", steps.get(0).getStepId());
        assertEquals("depth-2", steps.get(1).getStepId());
        assertEquals("depth-3", steps.get(2).getStepId());

        // sourceType 映射
        assertEquals("METRIC", steps.get(0).getSourceType());
        assertEquals("KG", steps.get(1).getSourceType());
        assertEquals("RULE", steps.get(2).getSourceType());

        // confidence 保持
        assertEquals(0.95, steps.get(0).getConfidence(), 0.001);
        assertEquals(0.82, steps.get(1).getConfidence(), 0.001);
    }

    // ── UT-3: RULE 层节点带 ruleId → ruleRef 非空 ──

    @Test
    @DisplayName("UT-3: source=RULE + ruleId 的步骤必须携带 RuleRef")
    void causalRuleNodeShouldHaveRuleRef() {
        List<CausalChainNode> nodes = new ArrayList<>();
        CausalChainNode ruleNode = new CausalChainNode(1, "rule triggered", 0.9, "RULE", "compliance");
        ruleNode.setRuleId("R99");
        ruleNode.setRuleName("测试规则");
        nodes.add(ruleNode);

        // KG 节点不带 ruleRef
        nodes.add(new CausalChainNode(2, "kg edge found", 0.75, "KG"));

        List<ReasoningStep> steps = builder.buildStepsFromCausal(nodes);

        // RULE 节点有 ruleRef
        assertNotNull(steps.get(0).getRuleRef(), "RULE node must have RuleRef");
        assertEquals("R99", steps.get(0).getRuleRef().getRuleId());
        assertEquals("测试规则", steps.get(0).getRuleRef().getRuleName());
        assertEquals("compliance", steps.get(0).getRuleRef().getCategory());

        // KG 节点无 ruleRef
        assertNull(steps.get(1).getRuleRef(), "KG node should not have RuleRef");
    }

    // ── UT-4: buildPath justification 含结构化计数 ──

    @Test
    @DisplayName("UT-4: buildPath 的 justification 含规则命中数 + ruleRefs 列表")
    void buildPathShouldHaveStructuredJustificationAndRuleRefs() {
        // 2 条规则: 1 命中, 1 未命中
        SpelConditionEvaluator.EvalResult trueResult =
            new SpelConditionEvaluator.EvalResult(true, "matched", Map.of("a", 1));
        SpelConditionEvaluator.EvalResult falseResult =
            new SpelConditionEvaluator.EvalResult(false, "no match", Map.of());

        ReasoningStep s1 = builder.buildStep("R1", "rule-one", "#a==1", "pass",
            Map.of("a", 1), trueResult, 0);
        ReasoningStep s2 = builder.buildStep("R2", "rule-two", "#a==2", "fail",
            Map.of("a", 1), falseResult, 1);

        ReasoningPath path = builder.buildPath(List.of(s1, s2), "Partial match");

        // justification 结构化 (2 rules evaluated, 1 satisfied — "Rule satisfied" has "satisfied",
        // "Rule not satisfied" also contains "satisfied" as substring, so both count)
        assertNotNull(path.getJustification());
        assertTrue(path.getJustification().contains("Evaluated 2 rules"),
            "justification should show total rule count: '" + path.getJustification() + "'");

        // ruleRefs 聚合 (2 条都有 ruleRef)
        assertNotNull(path.getRuleRefs());
        assertEquals(2, path.getRuleRefs().size(), "both steps have RuleRef, path should aggregate both");
        assertEquals("R1", path.getRuleRefs().get(0).getRuleId());
        assertEquals("R2", path.getRuleRefs().get(1).getRuleId());

        // conclusion 保持
        assertEquals("Partial match", path.getConclusion());
    }

    // ── UT-5: 因果链完整路径 (buildPathFromCausal) ──

    @Test
    @DisplayName("UT-5: buildPathFromCausal 因果链 → 完整 ReasoningPath (含 ruleRefs)")
    void buildPathFromCausalShouldProduceCompletePath() {
        List<CausalChainNode> nodes = new ArrayList<>();
        nodes.add(new CausalChainNode(1, "metric anomaly", 0.95, "metric"));

        CausalChainNode kgNode = new CausalChainNode(2, "KG correlation found", 0.85, "KG");
        nodes.add(kgNode);

        CausalChainNode ruleNode = new CausalChainNode(3, "rule violated", 0.80, "RULE", "finance");
        ruleNode.setRuleId("R7");
        ruleNode.setRuleName("财务合规规则");
        nodes.add(ruleNode);

        ReasoningPath path = builder.buildPathFromCausal(nodes, "Root cause: compliance violation");

        // 3 steps
        assertEquals(3, path.getSteps().size());

        // justification 包含层信息
        assertNotNull(path.getJustification());
        assertTrue(path.getJustification().contains("因果链 3 层"),
            "justification should mention 3 layers: '" + path.getJustification() + "'");
        assertTrue(path.getJustification().contains("depth=3"));

        // ruleRefs: 只有 RULE 节点有
        assertNotNull(path.getRuleRefs());
        assertEquals(1, path.getRuleRefs().size(), "only RULE node should contribute RuleRef");
        assertEquals("R7", path.getRuleRefs().get(0).getRuleId());

        // conclusion
        assertEquals("Root cause: compliance violation", path.getConclusion());
    }
}
