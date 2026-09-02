package com.chinacreator.gzcm.engine.cognitive2;

import com.chinacreator.gzcm.engine.cognitive2.model.*;
import com.chinacreator.gzcm.engine.cognitive2.service.ReasoningPathFromCausalBuilder;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Wave-3.2 T1 — ReasoningPathFromCausalBuilder 行为测试。
 *
 * <p>G4 验收：因果链 → step 展开（depth/stepIndex/sourceType/ruleRef 等）。
 * 04 文档 §4.1 算法 + §四 拆解目标（CausalChainNode → ReasoningStep）。
 */
class ReasoningPathFromCausalBuilderTest {

    private final ReasoningPathFromCausalBuilder builder = new ReasoningPathFromCausalBuilder();

    private static RuleRef ruleOf(String id, String priority) {
        RuleRef r = new RuleRef(id, "rule-" + id, "#x>100", "WARN");
        r.setSourceRank(Integer.parseInt(priority));
        return r;
    }

    @Test
    void threeLayerCausalChainEmitsThreeSteps() {
        List<CausalChainNode> nodes = List.of(
                new CausalChainNode(1, "毛利率", 0.9, "KG", "finance"),
                new CausalChainNode(2, "原材料价格上涨", 0.75, "KG", "finance"),
                new CausalChainNode(3, "大宗商品波动", 0.55, "LLM", "finance")
        );
        ReasoningPathFromCausalBuilder.Context ctx =
                new ReasoningPathFromCausalBuilder.Context(new HashMap<>(), new HashMap<>());

        ReasoningPath path = builder.buildPath(nodes, "毛利率受影响", ctx, 8);

        assertNotNull(path);
        assertEquals(3, path.getSteps().size());
        // 第 1 步
        assertEquals(1, path.getSteps().get(0).getStepIndex());
        assertEquals("depth-1", path.getSteps().get(0).getStepId());
        assertEquals("KG", path.getSteps().get(0).getSourceType());
        assertEquals("毛利率", path.getSteps().get(0).getOutputFact());
        // 第 3 步
        assertEquals("LLM", path.getSteps().get(2).getSourceType());
        // 没 ruleRef（KG/LLM 不带）
        assertNull(path.getSteps().get(0).getRuleRef());
        // ruleRefs 聚合
        assertTrue(path.getRuleRefs().isEmpty());
        // 必须有结构化 clauses（G3）
        assertNotNull(path.getClauses());
        assertEquals(3, path.getClauses().size());
        assertEquals(JustificationClause.TYPE_FACT_ACCRUAL, path.getClauses().get(0).getClauseType());
    }

    @Test
    void ruleNodeInjectsRuleRefFromContext() {
        CausalChainNode n = new CausalChainNode(2, "应收账款规则校验", 0.8, "RULE", "finance");
        n.setRuleId("R5");
        n.setRuleName("5-day-payment-window");

        Map<String, RuleRef> ruleRefs = new HashMap<>();
        ruleRefs.put("R5", ruleOf("R5", "2"));

        ReasoningPathFromCausalBuilder.Context ctx =
                new ReasoningPathFromCausalBuilder.Context(ruleRefs, new HashMap<>());
        ReasoningPath path = builder.buildPath(List.of(n), "root", ctx, 5);
        ReasoningStep step = path.getSteps().get(0);

        assertNotNull(step.getRuleRef());
        assertEquals("R5", step.getRuleRef().getRuleId());
        assertEquals(2, step.getRuleRef().getSourceRank());
        assertEquals("RULE", step.getSourceType());
        // 聚合到 path
        assertEquals(1, path.getRuleRefs().size());
        assertEquals("R5", path.getRuleRefs().get(0).getRuleId());
    }

    @Test
    void maxDepthTrimsLongerChain() {
        List<CausalChainNode> nodes = new ArrayList<>();
        for (int d = 1; d <= 7; d++) {
            nodes.add(new CausalChainNode(d, "node-" + d, 0.5 + d * 0.05, "KG", "finance"));
        }
        ReasoningPathFromCausalBuilder.Context ctx =
                new ReasoningPathFromCausalBuilder.Context(new HashMap<>(), new HashMap<>());
        ReasoningPath path = builder.buildPath(nodes, "root", ctx, 4);

        // 原 7 层裁剪到 4 层
        assertEquals(4, path.getSteps().size());
        // clauses 受 8 上限约束，但 ≤ steps 数
        assertEquals(4, path.getClauses().size());
    }

    @Test
    void emptyInputProducesEmptyPath() {
        ReasoningPathFromCausalBuilder.Context ctx =
                new ReasoningPathFromCausalBuilder.Context(new HashMap<>(), new HashMap<>());
        ReasoningPath path = builder.buildPath(null, "root", ctx, 3);
        assertNotNull(path);
        assertTrue(path.getSteps().isEmpty());
        assertTrue(path.getClauses().isEmpty());
    }

    @Test
    void precedentNodeInheritsPrecedentRef() {
        // 用 description 携带 "precedent:xxx ..." 表达先例节点
        CausalChainNode n = new CausalChainNode(2, "precedent:p-099 历史销售下滑处理方案", 0.8, "KG", "finance");
        PrecedentRef ref = new PrecedentRef("p-099", "d-99", "销售下滑处置", "approved", 0.9);

        Map<String, PrecedentRef> preIndex = new HashMap<>();
        preIndex.put("p-099", ref);
        ReasoningPathFromCausalBuilder.Context ctx =
                new ReasoningPathFromCausalBuilder.Context(new HashMap<>(), preIndex);

        ReasoningPath path = builder.buildPath(List.of(n), "root", ctx, 5);
        ReasoningStep step = path.getSteps().get(0);
        assertNotNull(step.getPrecedentRef());
        assertEquals("p-099", step.getPrecedentRef().getPrecedentId());
        assertEquals("PRECEDENT", step.getSourceType());
        // 聚合到 path
        assertEquals(1, path.getPrecedentRefs().size());
        assertEquals(JustificationClause.TYPE_PRECEDENT_RECALL, path.getClauses().get(0).getClauseType());
    }
}
