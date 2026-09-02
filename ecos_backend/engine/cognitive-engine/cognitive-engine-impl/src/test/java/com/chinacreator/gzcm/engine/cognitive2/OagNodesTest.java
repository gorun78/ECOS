package com.chinacreator.gzcm.engine.cognitive2;

import com.chinacreator.gzcm.engine.cognitive2.model.*;
import com.chinacreator.gzcm.engine.cognitive2.service.EngineCapabilityRegistryImpl;
import com.chinacreator.gzcm.engine.cognitive2.service.OagIntakeService;
import com.chinacreator.gzcm.engine.cognitive2.service.OagPlannerService;
import com.chinacreator.gzcm.engine.cognitive2.service.StrategyGeneratorService;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Wave-3.2 T3 — OAG 8 步 3 节点路由测试。
 *
 * <p>覆盖：OAG_INTAKE / OAG_PLAN / OAG_STRATEGY 节点执行；
 * NodeType 8 值（G1）。
 */
class OagNodesTest {

    // ── NodeType 8 值检查 ──
    @Test
    void nodeTypeHasEightValues() {
        assertEquals(8, NodeType.values().length, "G1: NodeType.values().length == 8");
        assertNotNull(NodeType.fromString("OAG_INTAKE"));
        assertNotNull(NodeType.fromString("OAG_PLAN"));
        assertNotNull(NodeType.fromString("OAG_STRATEGY"));
    }

    // ── OAG_INTAKE ──
    @Test
    void intakeExtractsDeviationAndMetric() {
        OagIntakeService svc = new OagIntakeService();
        Map<String, Object> cfg = Map.of("raw_request", "首创 2025-05 销售同比 -12%", "domain", "finance");
        Map<String, Object> out = svc.handle("首创 2025-05 销售同比 -12%", "finance", cfg);

        assertNotNull(out.get("intent_id"));
        @SuppressWarnings("unchecked")
        Map<String, Object> slots = (Map<String, Object>) out.get("slot_map");
        assertNotNull(slots);
        assertEquals("finance", slots.get("domain"));
        // -12% 被抽到
        assertTrue(slots.get("deviation") instanceof Number);
        assertEquals(-12.0, ((Number) slots.get("deviation")).doubleValue());
    }

    // ── OAG_PLAN ──
    @Test
    void plannerEmitsFiveSubTasksInOrder() {
        OagPlannerService svc = new OagPlannerService();
        Map<String, Object> slots = Map.of("domain", "finance", "metric", "销售同比", "deviation", -12.0);
        Map<String, Object> out = svc.handle("int-123", slots, Map.of());

        assertEquals("plan-", String.valueOf(out.get("plan_id")).substring(0, 5));
        assertEquals("int-123", out.get("intent_id"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> sub = (List<Map<String, Object>>) out.get("sub_tasks");
        assertEquals(5, sub.size());
        // 线性 DAG 检查：每个 task 依赖前面一个
        for (int i = 0; i < sub.size(); i++) {
            @SuppressWarnings("unchecked")
            List<String> deps = (List<String>) sub.get(i).get("depends_on");
            if (i == 0) {
                assertTrue(deps.isEmpty());
            } else {
                assertEquals(1, deps.size());
                assertEquals(sub.get(i - 1).get("sub_task_id"), deps.get(0));
            }
        }
    }

    // ── OAG_STRATEGY ──
    @Test
    void strategyEnrichesActionsAndCostsRisk() {
        StrategyGeneratorService svc = new StrategyGeneratorService();
        Map<String, Object> reasonerResult = Map.of(
                "answer", "原因 A 引发 B",
                "confidence", 0.92
        );
        Map<String, Object> out = svc.handle(reasonerResult, null, null, 5);

        @SuppressWarnings("unchecked")
        Map<String, Object> strategy = (Map<String, Object>) out.get("strategy");
        assertNotNull(strategy);
        @SuppressWarnings("unchecked")
        List<String> actions = (List<String>) strategy.get("actions");
        assertEquals(1, actions.size());
        // conf=0.92 → 低成本/低风险
        assertEquals("低成本", strategy.get("cost"));
        assertEquals("低风险", strategy.get("risk"));
        // precedent_refs 为空列表
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> prefs = (List<Map<String, Object>>) out.get("precedent_refs");
        assertNotNull(prefs);
    }

    @Test
    void strategyInjectsPrecedentIds() {
        StrategyGeneratorService svc = new StrategyGeneratorService();
        Map<String, Object> out = svc.handle(
                Map.of("answer", "x", "confidence", 0.4),
                List.of("pid-1", "pid-2"),
                null, 4);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> prefs = (List<Map<String, Object>>) out.get("precedent_refs");
        assertEquals(2, prefs.size());
        assertEquals("pid-1", prefs.get(0).get("decision_id"));
        assertEquals("pid-2", prefs.get(1).get("decision_id"));
    }

    // ── Registry 路由（仅构造合法性） ──
    @Test
    void engineCapabilityRegistryRoutesToThreeOagCallers() throws Exception {
        // mock：构造不能是个全 mock（会引入 Mockito 依赖问题），改用 new Oag... 桩
        var decision = new DecisionServiceImplStub();
        var intake = new OagIntakeService();
        var plan = new OagPlannerService();
        var strategy = new StrategyGeneratorService();
        EngineCapabilityRegistryImpl reg = new EngineCapabilityRegistryImpl(decision, intake, plan, strategy);
        assertNotNull(reg);

        // 执行 OAG_INTAKE 节点
        Map<String, Object> config = Map.of("raw_request", "测试 -5%");
        Map<String, Object> out = reg.executeNode(
                new CognitivePipelineNode("s1_intake", NodeType.OAG_INTAKE,
                        "{\"raw_request\":\"测试 -5%\"}", null),
                new HashMap<>());
        assertNotNull(out.get("intent_id"));
        assertEquals("intake_completed", out.get("status"));
    }

    // stub 决策服务：满足 EngineCapabilityRegistryImpl 构造器
    static class DecisionServiceImplStub implements com.chinacreator.gzcm.engine.cognitive2.DecisionService {
        @Override public String recordDecision(String category, String scenario, String reasoning,
                                                String outcome, double confidence, String decisionMaker) { return "stub"; }
        @Override public void addCausalRelationship(String s, String t, String rel) {}
        @Override public List<Decision> findSimilarDecisions(String q, int k) { return List.of(); }
        @Override public List<Decision> traceDecisionChain(String id) { return List.of(); }
        @Override public Map<String, Object> analyzeDecisionImpact(String id) { return Map.of(); }
        @Override public Map<String, Object> checkDecisionRules(String id) { return Map.of(); }
        @Override public String recordProvenance(String et, String id, String st, String sr, String a, String ac) { return "stub"; }
        @Override public List<ProvenanceEntry> queryProvenance(String et, String id) { return List.of(); }
    }
}
