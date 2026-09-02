package com.chinacreator.gzcm.engine.cognitive2;

import com.chinacreator.gzcm.engine.cognitive2.model.*;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Wave-3.2 模型契约单测（G1: 5 类 Contract 全齐 + 增量字段）。
 *
 * <p>覆盖：
 * <ol>
 *   <li>RuleRef / PrecedentRef POJO 完整性（G1）</li>
 *   <li>ReasoningStep 增量字段 setter/getter（先例/规则并存）</li>
 *   <li>ReasoningPath 增量字段（precedentRefs + clauses）</li>
 *   <li>CausalChainResult.reasoningPath 字段（G4 流转）</li>
 * </ol>
 */
class ContractModelsWave32Test {

    @Test
    void ruleRefAndPrecedentRefShouldCoexist() {
        RuleRef ref = new RuleRef("R1", "rule-A", "#amount>100", "REJECT");
        ref.setCategory("compliance");
        ref.setVersion("2");
        ref.setSourceRank(0);

        PrecedentRef pre = new PrecedentRef("P1", "d-001", "案例摘要", "approved", 0.85);
        pre.getSimilarityEvidence().put("vector_score", -1.0);

        assertEquals("R1", ref.getRuleId());
        assertEquals("rule-A", ref.getRuleName());
        assertEquals("#amount>100", ref.getCondition());
        assertEquals("REJECT", ref.getAction());
        assertEquals("compliance", ref.getCategory());
        assertEquals("2", ref.getVersion());
        assertEquals(0, ref.getSourceRank());

        assertEquals("P1", pre.getPrecedentId());
        assertEquals("d-001", pre.getDecisionId());
        assertEquals("案例摘要", pre.getSummary());
        assertEquals("approved", pre.getOutcome());
        assertEquals(0.85, pre.getSimilarity());
        assertNotNull(pre.getSimilarityEvidence());
        assertEquals(-1.0, pre.getSimilarityEvidence().get("vector_score"));
    }

    @Test
    void precedentRefFromDecision() {
        Decision d = new Decision();
        d.setId("d-100");
        d.setScenario("月度销售波动");
        d.setReasoning("缺在收款周期延长");
        d.setOutcome("approved");
        d.setConfidence(0.88);
        PrecedentRef ref = PrecedentRef.fromDecision(d, 0.72);
        assertNotNull(ref);
        assertEquals("d-100", ref.getPrecedentId());
        assertEquals("d-100", ref.getDecisionId());
        assertEquals("approved", ref.getOutcome());
        assertEquals(0.72, ref.getSimilarity());
        // 摘要包含 scenario 与 reasoning
        assertTrue(ref.getSummary().contains("月度销售波动"));
        assertTrue(ref.getSummary().contains("缺在收款周期延长"));
    }

    @Test
    void justificationClauseStructure() {
        JustificationClause c = new JustificationClause(
                "clause-1", JustificationClause.TYPE_RULE_TRIGGER,
                "step-1", "Rule R1 命中", 0.9);
        c.getFactRefs().add("amount");
        c.getFactRefs().add("type");

        assertEquals("clause-1", c.getClauseId());
        assertEquals("RULE_TRIGGER", c.getClauseType());
        assertEquals("step-1", c.getStepRef());
        assertEquals("Rule R1 命中", c.getText());
        assertEquals(0.9, c.getWeight());
        assertEquals(2, c.getFactRefs().size());
        // 常量对齐 04 文档 §三
        assertEquals("FACT_ACCRUAL", JustificationClause.TYPE_FACT_ACCRUAL);
        assertEquals("PRECEDENT_RECALL", JustificationClause.TYPE_PRECEDENT_RECALL);
    }

    @Test
    void reasoningStepCarriesRuleAndPrecedent() {
        ReasoningStep step = new ReasoningStep();
        step.setStepId("s1");
        step.setStepIndex(1);
        step.setConfidence(0.85);
        step.setSourceType("RULE");
        RuleRef rule = new RuleRef("R1", "rule-A", null, null);
        step.setRuleRef(rule);
        PrecedentRef pre = new PrecedentRef("P1", "d-1", "摘要", "approved", 0.8);
        step.setPrecedentRef(pre);

        assertNotNull(step.getRuleRef());
        assertNotNull(step.getPrecedentRef());
        assertEquals("R1", step.getRuleRef().getRuleId());
        assertEquals("P1", step.getPrecedentRef().getPrecedentId());
        assertEquals(1, step.getStepIndex());
    }

    @Test
    void reasoningPathAggregatesRuleAndPrecedentRefs() {
        List<ReasoningStep> steps = new ArrayList<>();
        ReasoningStep s1 = new ReasoningStep();
        s1.setStepId("s1");
        s1.setRuleRef(new RuleRef("R1", "rule-A", null, null));
        steps.add(s1);

        ReasoningStep s2 = new ReasoningStep();
        s2.setStepId("s2");
        s2.setPrecedentRef(new PrecedentRef("P1", "d-1", "x", "approved", 0.8));
        steps.add(s2);

        ReasoningPath path = new ReasoningPath();
        path.setSteps(steps);
        path.setConclusion("done");
        path.setJustification("j");
        path.setRuleRefs(List.of(new RuleRef("R1", "rule-A", null, null)));
        path.setPrecedentRefs(List.of(new PrecedentRef("P1", "d-1", "x", "approved", 0.8)));
        path.setClauses(List.of(new JustificationClause("c1", "RULE_TRIGGER", "s1", "t", 0.7)));

        assertEquals(1, path.getRuleRefs().size());
        assertEquals(1, path.getPrecedentRefs().size());
        assertEquals(1, path.getClauses().size());
        assertEquals("RULE_TRIGGER", path.getClauses().get(0).getClauseType());
    }

    @Test
    void causalChainResultCarriesReasoningPath() {
        // G4: CausalChainResult.reasoningPath 字段流转
        CausalChainResult r = new CausalChainResult();
        ReasoningPath path = new ReasoningPath();
        path.setConclusion("root");
        path.setJustification("j");
        r.setReasoningPath(path);
        assertNotNull(r.getReasoningPath());
        assertEquals("root", r.getReasoningPath().getConclusion());

        // 既有 fields 仍然可用
        r.setRootCause("gross-marginal-up");
        r.getCausalChain().add(new CausalChainNode(1, "毛利率", 0.9, "KG", "finance"));
        assertEquals(1, r.getCausalChain().size());
    }
}
