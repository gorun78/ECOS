package com.chinacreator.gzcm.engine.data.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * F02-11 (W64) —— 血缘自动派生断言：跑一条
 * SOURCE_JDBC → TRANSFORM_SQL → SINK 的三节点源码，
 * 断言生成 3 节点 2 边。
 *
 * <p><b>为何用 YAML 侧驱动</b>：{@link DataLineageService#parseYamlLineage(String)}
 * 是纯函数（依赖零 Spring 上下文、零数据库）。F02-11
 * 关键验收是一次"派生"能算清一条 source→transform→sink 管道，
 * 而真实引擎中管道形态有两种载体（{@code ecs_pipeline_definition.definition JSON}
 * 与 {@code ecos_pipeline_task.yaml_content}），派生规则同源（节点 +
 * {@code dependsOn: [..]} 依赖边）。把该纯函数作为"派生规则"的
 * 代表实现来跑，等价于验证 3 节点→2 边 的拓扑正确性，且不
 * 需打重依赖（DB、JSqlParser 版本、Portal 表）。
 * 持久化承载已由 {@code NoYamlLineageScanArchTest}
 * 与 {@code DataLineageService.getLineage} 参数化读血缘表路径共同覆盖。
 *
 * <p>真正的集成级（DB 表读边）路径由 {@link
 * com.chinacreator.gzcm.engine.data.NoYamlLineageScanArchTest}（禁回归）
 * 与后续 E2E（{@code pipeline execute →
 * lineageService.buildTopology → 落库 →
 * getLineage 参数化读}）共同收口。
 */
@DisplayName("F02-11 血缘派生：一条 SOURCE→TRANSFORM→SINK 得 3 节点 2 边")
@ExtendWith(org.mockito.junit.jupiter.MockitoExtension.class)
class LineageDerivationTest {

    /** 一条最典型 SOURCE_JDBC→TRANSFORM_SQL→SINK 三节点 YAML（circled-oid 与字段）。 */
    private static final String THREE_NODE_YAML = String.join("\n",
            "pipeline:",
            "  nodes:",
            "    - id: source-orders",
            "      type: SOURCE_JDBC",
            "      table: \"orders.src_orders\"",
            "      columns:",
            "        - field: order_id",
            "        - field: amount",
            "    - id: transform-enrich",
            "      type: TRANSFORM_SQL",
            "      table: \"ods.orders_enriched\"",
            "      dependsOn: [source-orders]",
            "      sql: |",
            "        SELECT o.order_id, o.amount, e.region ",
            "        FROM orders.src_orders o JOIN dim.entity e ON o.entity_id = e.id",
            "    - id: sink-dws",
            "      type: SINK",
            "      table: \"dws.orders_wide\"",
            "      dependsOn: [transform-enrich]",
            "      columns:",
            "        - field: order_id",
            "        - field: amount");

    @Test
    @DisplayName("parseYamlLineage 对 3 节点源码产出 3 节点 + 2 边（source→transform, transform→sink）")
    void threeNodeYamlYieldsThreeNodesAndTwoEdges() {
        DataLineageService svc = new DataLineageService(org.mockito.Mockito.mock(
                org.springframework.jdbc.core.JdbcTemplate.class));

        Map<String, Object> got = svc.parseYamlLineage(THREE_NODE_YAML);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) got.get("nodes");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> edges = (List<Map<String, Object>>) got.get("edges");

        assertNotNull(nodes, "nodes 不应为空");
        assertNotNull(edges, "edges 不应为空");
        assertEquals(3, nodes.size(),
                "三节点源码应派生 3 节点（SOURCE_JDBC/TRANSFORM_SQL/SINK），实际 " + nodes.size());
        assertEquals(2, edges.size(),
                "两 dependsOn 关系应派生 2 边（source→transform, transform→sink），实际 " + edges.size());

        // 边方向：source=上游, target=当前节点（源码写 dependsOn: [..]）
        // 即 edge(source=source-orders, target=transform-enrich)
        //    与 edge(source=transform-enrich, target=sink-dws)
        boolean edge1 = false;
        boolean edge2 = false;
        for (Map<String, Object> e : edges) {
            Object s = e.get("source");
            Object t = e.get("target");
            if ("source-orders".equals(s) && "transform-enrich".equals(t)) edge1 = true;
            if ("transform-enrich".equals(s) && "sink-dws".equals(t)) edge2 = true;
        }
        assertTrue(edge1, "missing edge source-orders → transform-enrich; edges=" + edges);
        assertTrue(edge2, "missing edge transform-enrich → sink-dws; edges=" + edges);

        // 三节点的类型 tag
        java.util.Set<String> types = new java.util.HashSet<>();
        for (Map<String, Object> n : nodes) {
            Object t = n.get("type");
            if (t != null) types.add(t.toString());
        }
        assertTrue(types.contains("SOURCE_JDBC"), "应含 SOURCE_JDBC 节点；实际 types=" + types);
        assertTrue(types.contains("TRANSFORM_SQL"), "应含 TRANSFORM_SQL 节点；实际 types=" + types);
        assertTrue(types.contains("SINK"), "应含 SINK 节点；实际 types=" + types);
    }

    @Test
    @DisplayName("SUMMARY：result 中 total_nodes/total_edges 结构完整")
    void summaryFieldsArePopulated() {
        DataLineageService svc = new DataLineageService(org.mockito.Mockito.mock(
                org.springframework.jdbc.core.JdbcTemplate.class));

        Map<String, Object> got = svc.parseYamlLineage(THREE_NODE_YAML);
        Object tn = got.get("total_nodes");
        Object te = got.get("total_edges");
        assertEquals(3, tn, "total_nodes 应 = 3；实际 " + tn);
        assertEquals(2, te, "total_edges 应 = 2；实际 " + te);
    }
}
