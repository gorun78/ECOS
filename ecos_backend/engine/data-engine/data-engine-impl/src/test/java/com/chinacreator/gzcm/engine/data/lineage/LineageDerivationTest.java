package com.chinacreator.gzcm.engine.data.lineage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.chinacreator.gzcm.engine.data.lineage.dao.EcosDataLineageMapper;
import com.chinacreator.gzcm.engine.data.pipeline.PipelineNode;
import com.chinacreator.gzcm.engine.data.pipeline.PipelineRepository;

/**
 * F02-11 / W64 规格验收（LineageDerivationTest，纯单测无 DB）：
 * 血缘从 pipeline <b>definition（DAG）</b>派生，跑一条
 * SOURCE_JDBC → TRANSFORM_SQL → SINK 管道，断言派生 <b>3 节点 2 边</b>，
 * 且节点/边行经 {@link EcosDataLineageMapper} 全参化落库（主路径 insertNodes/insertEdges）。
 *
 * <p>派生入口 {@link LineageDerivationService#deriveFromDefinition} 读取 DAG 节点
 * （{@link PipelineRepository#findNodesByDefinitionId}）→ 归一 node_type
 * （SOURCE/TRANSFORM/TARGET）→ dependsOn 前驱 → edge（前驱 → 本节点）→ 幂等落库。</p>
 */
@DisplayName("F02-11 血缘从 definition 派生")
class LineageDerivationTest {

    @Mock
    private PipelineRepository repository;
    @Mock
    private EcosDataLineageMapper mapper;

    private LineageDerivationService svc;

    private void setUp() {
        var auto = MockitoAnnotations.openMocks(this);
        svc = new LineageDerivationService(repository, mapper);
    }

    private PipelineNode node(String nodeId, String type, String config, String dependsOn) {
        PipelineNode n = new PipelineNode();
        n.setNodeId(nodeId);
        n.setType(type);
        n.setConfig(config);
        n.setDependsOn(dependsOn);
        return n;
    }

    @Test
    @DisplayName("SOURCE→TRANSFORM→SINK 三节点派生 3 节点 2 边并批量落库")
    void deriveThreeNodePipelineYieldsThreeNodesTwoEdges() {
        setUp();
        List<PipelineNode> defs = List.of(
                node("n_src", "SOURCE_JDBC", "{\"datasourceId\":\"ds1\",\"table\":\"crm.orders\"}", "[]"),
                node("n_txf", "TRANSFORM_SQL", "{\"sql\":\"SELECT id, amt FROM crm.orders\"}", "[\"n_src\"]"),
                node("n_sink", "SINK", "{\"sinkTable\":\"dwd.orders_wide\",\"table\":\"dwd.orders_wide\"}", "[\"n_txf\"]"));
        when(repository.findNodesByDefinitionId("def-1")).thenReturn(defs);

        LineageDerivationService.DerivationSummary summary = svc.deriveFromDefinition("def-1");

        assertNotNull(summary);
        assertEquals(3, summary.nodeCount(), "definition 3 DAG 节点应派生 3 个 lineage node");
        assertEquals(2, summary.edgeCount(), "dependsOn 链 n_src→n_txf→n_sink 应派生 2 条边");

        // 验证走了全参化批量落库（insertNodes/insertEdges 各被调用一次）
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Map<String, Object>>> nodeCap = ArgumentCaptor.forClass(List.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Map<String, Object>>> edgeCap = ArgumentCaptor.forClass(List.class);
        verify(mapper).insertNodes(nodeCap.capture());
        verify(mapper).insertEdges(edgeCap.capture());

        assertEquals(3, nodeCap.getValue().size());
        assertEquals(2, edgeCap.getValue().size());

        // 节点类型归一：SOURCE / TRANSFORM / TARGET
        List<String> nodeTypes = nodeCap.getValue().stream().map(r -> (String) r.get("nodeType")).toList();
        assertEquals("SOURCE", nodeTypes.get(0));
        assertEquals("TRANSFORM", nodeTypes.get(1));
        assertEquals("TARGET", nodeTypes.get(2));

        // SOURCE 节点表名取 config.table（限定名），SINK 节点同
        assertEquals("crm.orders", nodeCap.getValue().get(0).get("tableName"));
        assertEquals("dwd.orders_wide", nodeCap.getValue().get(2).get("tableName"));
    }

    @Test
    @DisplayName("无节点 definition 派生 0 节点 0 边（不抛）")
    void emptyDefinitionYieldsZero() {
        setUp();
        when(repository.findNodesByDefinitionId("def-empty")).thenReturn(List.of());
        LineageDerivationService.DerivationSummary s = svc.deriveFromDefinition("def-empty");
        assertEquals(0, s.nodeCount());
        assertEquals(0, s.edgeCount());
    }

    @Test
    @DisplayName("definitionId 必填（F02-11 入参校验）")
    void blankDefinitionIdRejected() {
        setUp();
        assertThrows(IllegalArgumentException.class, () -> svc.deriveFromDefinition(" "));
    }

    @Test
    @DisplayName("派生先清旧行再写入（幂等），且 pipeline_task_id 复用 definitionId 单源")
    void idempotentClearThenInsert() {
        setUp();
        when(repository.findNodesByDefinitionId("def-2")).thenReturn(List.of(
                node("a", "SOURCE_JDBC", "{\"table\":\"t1\"}", "[]"),
                node("b", "SINK", "{\"table\":\"t2\"}", "[\"a\"]")));
        svc.deriveFromDefinition("def-2");
        verify(mapper).deleteNodesByPipelineId("def-2");
        verify(mapper).deleteEdgesByPipelineId("def-2");
        // 全部行落在该 definition（pipeline_task_id 单源）下
        verify(mapper).insertNodes(org.mockito.ArgumentMatchers.anyList());
    }
}
