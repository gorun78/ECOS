package com.chinacreator.gzcm.engine.ai.template;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.chinacreator.gzcm.engine.ai.wagent.WAgentEnums.PlanSource;
import com.chinacreator.gzcm.engine.ai.wagent.orchestrator.WAgentPlan;
import com.chinacreator.gzcm.engine.ai.wagent.orchestrator.WAgentPlan.Plan;
import com.chinacreator.gzcm.engine.ai.wagent.orchestrator.WAgentPlan.Step;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Playbook → 逻辑编排画布转换器（模板融合批 2）。
 *
 * <p>判据三组：①六类 StepType 的映射表与 unmapped 如实上报；②确定性（同输入两次转换
 * 输出等价、坐标固定步长无随机）；③非法计划（环）fail-loud 而非静默产空画布。</p>
 */
class PlaybookToGraphConverterTest {

    private final PlaybookToGraphConverter converter = new PlaybookToGraphConverter();

    private static Step step(String key, String type, String toolName, List<String> deps) {
        return new Step(key, type, toolName, null, 1, deps, Map.of("arg", "ref"), false, false, null);
    }

    @Test
    @DisplayName("六类 StepType：可映射五类进画布且节点类型正确，sub_run 进 unmapped 不静默丢")
    void mapsAllStepTypesAndReportsUnmapped() {
        Plan plan = new Plan(PlanSource.PLAYBOOK, "repo/ref", "v1", List.of(
                step("s1", "tool", "ont.query", List.of()),
                step("s2", "llm", null, List.of("s1")),
                step("s3", "human_gate", null, List.of("s2")),
                step("s4", "aggregate", null, List.of("s3")),
                step("s5", "wait_task", null, List.of("s4")),
                step("s6", "sub_run", null, List.of("s5"))));

        PlaybookToGraphConverter.ConvertedGraph graph = converter.convert(plan);

        assertEquals(5, graph.nodes().size(), "五类可映射 step 应全部进画布");
        assertEquals(List.of("tool", "llm", "approval", "condition", "trigger"),
                graph.nodes().stream().map(n -> String.valueOf(n.get("type"))).toList());
        assertEquals(List.of("s6:sub_run"), graph.unmapped(), "sub_run 必须如实上报，禁静默丢弃");
        assertEquals(4, graph.edges().size(), "被剔除的 s6 不得留悬空边");
    }

    @Test
    @DisplayName("确定性：同计划两次转换 JSON 逐字符相同；坐标按层×220、层内×120 固定步长")
    void conversionIsDeterministic() throws Exception {
        Plan plan = new Plan(PlanSource.PLAYBOOK, "ref", "v1", List.of(
                step("a", "tool", "t1", List.of()),
                step("b", "tool", "t2", List.of()),
                step("c", "llm", null, List.of("a", "b"))));

        PlaybookToGraphConverter.ConvertedGraph first = converter.convert(plan);
        PlaybookToGraphConverter.ConvertedGraph second = converter.convert(plan);

        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        assertEquals(mapper.writeValueAsString(first), mapper.writeValueAsString(second),
                "同输入两次转换必须逐字符相同（禁随机/时钟）");

        Map<String, Map<String, Object>> byId = new LinkedHashMap<>();
        for (Map<String, Object> n : first.nodes()) {
            @SuppressWarnings("unchecked")
            Map<String, Object> position = (Map<String, Object>) n.get("position");
            byId.put(String.valueOf(n.get("id")), position);
        }
        assertEquals(100, byId.get("a").get("x"), "第 0 层 x 基准 100");
        assertEquals(320, byId.get("c").get("x"), "第 1 层 x = 100 + 220");
        assertEquals(80, byId.get("a").get("y"), "同层首行 y 基准 80");
        assertEquals(200, byId.get("b").get("y"), "同层第二行 y = 80 + 120");
    }

    @Test
    @DisplayName("非法计划（依赖成环）拒绝转换，不产空画布")
    void rejectsCyclicPlan() {
        Plan cyclic = new Plan(PlanSource.PLAYBOOK, "ref", "v1", List.of(
                step("x", "tool", "t", List.of("y")),
                step("y", "tool", "t", List.of("x"))));

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> converter.convert(cyclic));
        assertTrue(e.getMessage().contains("结构非法"));
    }

    @Test
    @DisplayName("空计划转换结果为空（不抛：空=合法空集，由调用方决定拒绝）")
    void emptyPlanYieldsEmptyGraph() {
        PlaybookToGraphConverter.ConvertedGraph graph =
                converter.convert(new Plan(PlanSource.EXPLORED, null, null, List.of()));
        assertEquals(0, graph.nodes().size());
        assertEquals(0, graph.edges().size());
        assertTrue(graph.unmapped().isEmpty());
    }

    @Test
    @DisplayName("``WAgentPlan.PlanCodec`` 往返：序列化计划 JSON 能被转换器消费（集成腿）")
    void consumesPlanCodecJson() {
        Plan plan = new Plan(PlanSource.PLAYBOOK, "ref", "v1", List.of(
                step("n1", "tool", "kb.rag", List.of()),
                step("n2", "llm", null, List.of("n1"))));
        String json = WAgentPlan.PlanCodec.toJson(plan);
        Plan reparsed = WAgentPlan.PlanCodec.fromJson(json);

        PlaybookToGraphConverter.ConvertedGraph graph = converter.convert(reparsed);
        assertEquals(2, graph.nodes().size());
        assertEquals(1, graph.edges().size());
        assertEquals("n1", graph.edges().get(0).get("source"));
    }
}
