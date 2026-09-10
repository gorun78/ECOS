package com.chinacreator.gzcm.engine.data.pipeline;

import com.chinacreator.gzcm.engine.data.DataSourceService;
import com.chinacreator.gzcm.engine.data.UdfService;
import com.chinacreator.gzcm.runtime.access.connector.ConnectorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PipelineTransformSqlRoutingTest — Wave 5b N4 回归：
 * TRANSFORM_SQL 节点 SQL 首 token 分流（SELECT/SHOW/DESCRIBE/WITH → queryForList，
 * INSERT/UPDATE/DELETE/CREATE 等 DDL/DML → update）。
 *
 * <p>N2/N3 实锤：QA 终验报告 P2-02 §2.2 实证 step2 p1-tr (TRANSFORM_SQL "SELECT 1 AS
 * transform_marker") 因 jdbc.update(SELECT *) 抛 "PreparedStatementCallback ... 传回预期之外
 * 的结果"。PipelineExecutionService#executeTransformSql Wave 5 已按首 token 白名单分流；
 * 本测试补强断言防止回归。
 *
 * <p>同时补强 PipelineDebugService#execTransformSqlCapture：QA 报告中 debug 链路 step2 抛错
 * 的根因在该处（原 execTransformSqlCapture 无分流，Wave 5b 修复后应走 queryForList）。
 * <p>不涉及 PG / 网络：repository / connectorFactory / dataSourceService 全部 mock。
 */
class PipelineTransformSqlRoutingTest {

    private PipelineRepository repository;
    private ConnectorFactory connectorFactory;
    private JdbcTemplate jdbc;
    private DataSourceService dataSourceService;
    private UdfService udfService;
    private PipelineExecutionService service;

    @BeforeEach
    void setUp() {
        this.repository = mock(PipelineRepository.class);
        this.connectorFactory = mock(ConnectorFactory.class);
        this.jdbc = mock(JdbcTemplate.class);
        this.dataSourceService = mock(DataSourceService.class);
        this.udfService = mock(UdfService.class);
        this.service = new PipelineExecutionService(
                repository, connectorFactory, jdbc, dataSourceService, udfService);
    }

    // ─── A. SELECT 走 queryForList ────────────────────────────

    @Test
    @DisplayName("TRANSFORM_SQL 'SELECT 1' — executePipeline 成功且 queryForList 1 次（update 0 次）")
    void transformSqlSelectRoutesToQueryForList() {
        whenDef("p-select");
        PipelineNode n = node("n1", "TRANSFORM_SQL", "{\"sql\":\"SELECT 1 AS transform_marker\"}");
        whenNodes(List.of(n));
        when(jdbc.queryForList(anyString())).thenReturn(List.of(Map.of("transform_marker", 1L)));

        PipelineExecution exec = service.executePipeline("p-select");

        assertEquals("COMPLETED", exec.getStatus(),
                "SELECT 型 TRANSFORM_SQL 走 queryForList 应成功，实际 status=" + exec.getStatus()
                        + " / error=" + exec.getErrorMessage());
        verify(jdbc, times(1)).queryForList("SELECT 1 AS transform_marker");
        // SELECT 路径不应调 update
        verify(jdbc, never()).update(anyString());
    }

    @Test
    @DisplayName("TRANSFORM_SQL 'WITH c AS ...' — executePipeline 成功且走 queryForList（CTE 视为 SELECT）")
    void transformSqlWithCteRoutesToQueryForList() {
        String cteSql = "WITH c AS (SELECT id FROM t) SELECT * FROM c";
        whenDef("p-cte");
        PipelineNode n = node("n1", "TRANSFORM_SQL", "{\"sql\":\"" + cteSql + "\"}");
        whenNodes(List.of(n));
        when(jdbc.queryForList(anyString())).thenReturn(List.of(Map.of("id", 1L)));

        PipelineExecution exec = service.executePipeline("p-cte");

        assertEquals("COMPLETED", exec.getStatus());
        verify(jdbc).queryForList(cteSql);
    }

    // ─── B. DML 仍走 update（行为保持） ─────────────────────────

    @Test
    @DisplayName("TRANSFORM_SQL 'UPDATE t SET ...' — executePipeline 成功且 jdbc.update 1 次")
    void transformSqlUpdateRoutesToUpdate() {
        String updSql = "UPDATE t SET a=1 WHERE b=2";
        whenDef("p-upd");
        PipelineNode n = node("n1", "TRANSFORM_SQL", "{\"sql\":\"" + updSql + "\"}");
        whenNodes(List.of(n));
        when(jdbc.update(anyString())).thenReturn(3);

        PipelineExecution exec = service.executePipeline("p-upd");

        assertEquals("COMPLETED", exec.getStatus());
        verify(jdbc).update(updSql);
    }

    // ─── C. PipelineDebugService — 修复前 p1-tr 抛 "PreparedStatementCallback" 的场景 ─────────

    @Test
    @DisplayName("PipelineDebugService#execTransformSqlCapture — SELECT 走 queryForList（不调 update）")
    void debugServiceTransformSqlSelectRoutesToQueryForList() throws Exception {
        PipelineDebugService dbg = new PipelineDebugService(
                repository, connectorFactory, jdbc, dataSourceService);

        PipelineDebugStartDTO dto = new PipelineDebugStartDTO();
        PipelineDebugStartDTO.PipelineDebugDefinitionDTO def =
                new PipelineDebugStartDTO.PipelineDebugDefinitionDTO();
        def.setName("adhoc-select");
        PipelineDebugStartDTO.PipelineDebugNodeDTO nn = new PipelineDebugStartDTO.PipelineDebugNodeDTO();
        nn.setNodeId("t1");
        nn.setType("TRANSFORM_SQL");
        nn.setConfig(Map.of("sql", "SELECT 1 AS transform_marker"));
        def.setNodes(List.of(nn));
        dto.setDefinition(def);

        PipelineDebugSessionVO created = dbg.createSession(dto);
        assertTrue(created.getSessionId() != null && !created.getSessionId().isBlank());

        // 预落库桩：persistRun INSERT → repository.insertExecution 返回有 id 的记录
        PipelineExecution rec = new PipelineExecution();
        rec.setId("exec-1");
        rec.setDefinitionId("adhoc-x");
        when(repository.insertExecution(org.mockito.ArgumentMatchers.any(PipelineExecution.class)))
                .thenReturn(rec);
        when(jdbc.queryForList(anyString())).thenReturn(List.of(Map.of("transform_marker", 1L)));

        dbg.step(created.getSessionId());

        // 关键回归点：SELECT 必走 queryForList（修复前用 jdbc.update 会抛 SQLException）
        verify(jdbc).queryForList(argThat((String s) -> s.startsWith("SELECT 1")));
        // update 不应被调（仅 SELECT 路径下）
        verify(jdbc, never()).update(anyString());
    }

    // ─── helpers ────────────────────────────────────────────────

    private void whenDef(String defId) {
        PipelineDefinition d = new PipelineDefinition();
        d.setId(defId);
        d.setName("def-" + defId);
        d.setStatus("ACTIVE");
        when(repository.findDefinitionById(defId)).thenReturn(d);
    }

    private void whenNodes(List<PipelineNode> nodes) {
        when(repository.findNodesByDefinitionId(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(nodes);
        PipelineExecution rec = new PipelineExecution();
        rec.setId("e-" + System.nanoTime());
        rec.setStatus("PENDING");
        org.mockito.Mockito.doReturn(rec).when(repository)
                .insertExecution(org.mockito.ArgumentMatchers.any(PipelineExecution.class));
    }

    private PipelineNode node(String id, String type, String configJson) {
        PipelineNode n = new PipelineNode();
        n.setId(id);
        n.setNodeId(id);
        n.setDefinitionId("p-x-" + id);
        n.setType(type);
        n.setConfig(configJson);
        n.setDependsOn("[]");
        return n;
    }
}
