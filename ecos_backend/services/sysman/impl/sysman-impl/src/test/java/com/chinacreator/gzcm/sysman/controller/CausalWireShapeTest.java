package com.chinacreator.gzcm.sysman.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 因果图 wire 键面门（DI-2 #147DE；2026-11-13 D48 裁决后更新）。
 *
 * <p>图 4 键 / 节点 4 键 / 边 5 键 / 路径 5 键 / 对比 3 键（场景 4、节点 5 含 impactFactor、
 * 边 6 含 projectedWeight、差异 4）。</p>
 *
 * <p><b>D48 裁决（2026-11-13）</b>：本族原有一条「DB ping 得通就读 {@code ecos_causal_node}/
 * {@code ecos_causal_edge}」的分支，而两表全仓无 DDL、库中零命中 ⇒ {@code /graph} 恒 500。
 * 现已删除该分支与 {@code CausalQueryDao}，整族统一读内存种子，因此本测试不再需要
 * mock 一个总会抛错的 Service——构造器已无参。</p>
 */
class CausalWireShapeTest {

    private CausalController controller;

    @BeforeEach
    void setUp() {
        controller = new CausalController();
    }

    private static List<String> keys(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    private static JsonNode json(Object payload) {
        return new ObjectMapper().valueToTree(payload);
    }

    @Test
    @DisplayName("图：4 键（nodes/edges/nodeCount/edgeCount），节点 4 键、边 5 键")
    void graphShape() {
        JsonNode data = json(controller.getGraph().getData());

        assertEquals(List.of("nodes", "edges", "nodeCount", "edgeCount"), keys(data));
        assertEquals(8, data.path("nodeCount").asInt());
        assertEquals(10, data.path("edgeCount").asInt());
        assertEquals(List.of("id", "name", "type", "group"), keys(data.path("nodes").get(0)));
        assertEquals(List.of("id", "source", "target", "weight", "type"),
                keys(data.path("edges").get(0)));
    }

    @Test
    @DisplayName("路径：2 键（paths/total），路径 5 键且长度/权重为数值")
    void pathsShape() {
        JsonNode data = json(controller.getPaths("n1", "n3", 5).getData());

        assertEquals(List.of("paths", "total"), keys(data));
        assertTrue(data.path("total").asInt() >= 1, "n1→n2→n3 至少一条路径");
        assertEquals(List.of("nodes", "length", "edges", "totalWeight", "avgWeight"),
                keys(data.path("paths").get(0)));
        assertEquals(2, data.path("paths").get(0).path("length").asInt());
        assertEquals(0.85 + 0.72, data.path("paths").get(0).path("totalWeight").asDouble(), 1e-9);
    }

    @Test
    @DisplayName("对比：3 键；场景 4 键、投影节点 5 键、投影边 6 键、差异 4 键")
    void compareShape() {
        JsonNode data = json(controller.compare(Map.of("scenarioIds", List.of("s1", "s2"))).getData());

        assertEquals(List.of("scenarios", "differences", "comparedCount"), keys(data));
        assertEquals(2, data.path("comparedCount").asInt());

        JsonNode scenario = data.path("scenarios").get(0);
        assertEquals(List.of("id", "name", "nodes", "edges"), keys(scenario));
        assertEquals(List.of("id", "name", "type", "group", "impactFactor"),
                keys(scenario.path("nodes").get(0)));
        assertEquals(List.of("id", "source", "target", "weight", "type", "projectedWeight"),
                keys(scenario.path("edges").get(0)));

        assertEquals(List.of("scenarioA", "scenarioB", "nodeCountDiff", "edgeCountDiff"),
                keys(data.path("differences").get(0)));
    }

    @Test
    @DisplayName("对比：场景不足两个 ⇒ 400，不合成空对比")
    void compareRequiresTwoScenarios() {
        assertEquals(400, controller.compare(Map.of("scenarioIds", List.of("s1"))).getCode());
    }

    /**
     * D48 关账腿（2026-11-13 裁决后）：{@code /graph} 必须恒 200 且返回种子图。
     *
     * <p><b>裁决依据</b>：本族是演示形态（{@code /compare} 用 {@code Math.random()} 造投影值、
     * {@code /paths} 走内存 BFS），前端生产代码不消费本前缀；真图在
     * {@code GET /api/v1/ecos/world-model-graph/causal-graph}（实测 200 且含真实目标行）。
     * 原实现多出一条读 {@code ecos_causal_node}/{@code ecos_causal_edge} 的分支，
     * 两表全仓无 DDL、库中零命中 ⇒ 恒 500。裁决：删分支，整族统一内存。</p>
     *
     * <p><b>这条腿防什么</b>：防有人「修好 500」时把幽灵表再引回来——只要 {@code /graph}
     * 重新连库，本断言在无表环境下立刻红（不再是 200）。同时钉住种子规模
     * （8 节点 / 10 边），确保是<b>已知的演示数据</b>而不是随库变化的未知值。</p>
     */
    @Test
    @DisplayName("D48：/graph 恒 200 且返回内存种子（8 节点/10 边），不再连幽灵表")
    void graphAlwaysServesSeedWithoutTouchingMissingTables() {
        var resp = controller.getGraph();

        assertEquals(0, resp.getCode(), "D48 裁决：/graph 不得再因缺表失败: " + resp.getMessage());
        assertEquals(8, resp.getData().nodeCount(), "种子固定 8 节点（变则说明换了数据源，须同步裁决）");
        assertEquals(10, resp.getData().edgeCount(), "种子固定 10 边");
    }

    @Test
    @DisplayName("申请 D48 裁决完整性：整族三面（graph/paths/compare）同源且互不干扰")
    void allThreeFacesServeFromSameInMemorySeed() {
        int nodes = controller.getGraph().getData().nodeCount();
        var paths = controller.getPaths("n1", "n3", 5).getData();
        var compare = controller.compare(Map.of("scenarioIds", List.of("s1", "s2"))).getData();

        assertTrue(paths.total() >= 1, "同一份种子上必须有 n1→n2→n3 路径（图与路径同源）");
        assertEquals(2, compare.comparedCount(), "对比面同样取内存种子");
        assertEquals(nodes, compare.scenarios().get(0).nodes().size(),
                "对比场景的节点数必须等于图节点数（三面同源，不是各造各的）");
    }
}
