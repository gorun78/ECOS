package com.chinacreator.gzcm.engine.data.pipeline;

import com.chinacreator.gzcm.engine.data.DataSourceService;
import com.chinacreator.gzcm.engine.data.UdfService;
import com.chinacreator.gzcm.runtime.access.connector.ConnectorFactory;
import com.chinacreator.gzcm.runtime.access.connector.JdbcConnector;
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
                repository, connectorFactory, jdbc, dataSourceService, udfService);

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

    // ─── D. TRANSFORM_UDF 节点路由（P1#1） ─────────────────────────

    @Test
    @DisplayName("PipelineDebugService TRANSFORM_UDF — 执行器 switch 已补 TRANSFORM_UDF 分支")
    void debugServiceUdfTransformRoutes() throws Exception {
        PipelineDebugService dbg = new PipelineDebugService(
                repository, connectorFactory, jdbc, dataSourceService, udfService);
        // mock UDF 数据
        when(udfService.getById("udf-1")).thenReturn(Map.of(
                "name", "doubling_udf",
                "language", "python",
                "source_code", "def transform(rows, params):\n    return rows"));
        PipelineDebugStartDTO dto = new PipelineDebugStartDTO();
        PipelineDebugStartDTO.PipelineDebugDefinitionDTO def =
                new PipelineDebugStartDTO.PipelineDebugDefinitionDTO();
        def.setName("adhoc-udf");
        PipelineDebugStartDTO.PipelineDebugNodeDTO nn = new PipelineDebugStartDTO.PipelineDebugNodeDTO();
        nn.setNodeId("u1");
        nn.setType("TRANSFORM_UDF");
        nn.setConfig(Map.of("udfId", "udf-1"));
        def.setNodes(List.of(nn));
        dto.setDefinition(def);
        PipelineDebugSessionVO created = dbg.createSession(dto);
        PipelineExecution rec = new PipelineExecution();
        rec.setId("exec-udf-1");
        rec.setDefinitionId("adhoc-udf-x");
        when(repository.insertExecution(org.mockito.ArgumentMatchers.any(PipelineExecution.class)))
                .thenReturn(rec);
        dbg.step(created.getSessionId());
        // UDF python 沙箱执行成功 → 会话状态 COMPLETED（单节点执行完毕 auto-complete）
        PipelineDebugSessionVO after = dbg.getSession(created.getSessionId());
        assertEquals("completed", after.getState(),
                "TRANSFORM_UDF 单节点执行后应 completed，实际 state=" + after.getState()
                        + " / error=" + after.getError());
        // 验证 UdfService 被调用了
        verify(udfService).getById("udf-1");
    }

    // ─── E. JOIN 节点路由（P1#1） ───────────────────────────────

    @Test
    @DisplayName("PipelineDebugService JOIN — 执行器 switch 已补 JOIN 分支")
    void debugServiceJoinRoutes() throws Exception {
        PipelineDebugService dbg = new PipelineDebugService(
                repository, connectorFactory, jdbc, dataSourceService, udfService);
        PipelineDebugStartDTO dto = new PipelineDebugStartDTO();
        PipelineDebugStartDTO.PipelineDebugDefinitionDTO def =
                new PipelineDebugStartDTO.PipelineDebugDefinitionDTO();
        def.setName("adhoc-join");
        // 单节点 JOIN，使用 inlineData 内联右表（无需真实上游产出）
        PipelineDebugStartDTO.PipelineDebugNodeDTO nn = new PipelineDebugStartDTO.PipelineDebugNodeDTO();
        nn.setNodeId("j1");
        nn.setType("JOIN");
        nn.setConfig(Map.of(
                "joinKeys", List.of("id"),
                "joinType", "inner",
                "inlineData", List.of(
                        Map.of("id", 1L, "name", "Alice")
                )));
        def.setNodes(List.of(nn));
        dto.setDefinition(def);
        PipelineDebugSessionVO created = dbg.createSession(dto);
        PipelineExecution rec = new PipelineExecution();
        rec.setId("exec-join-1");
        rec.setDefinitionId("adhoc-join-x");
        when(repository.insertExecution(org.mockito.ArgumentMatchers.any(PipelineExecution.class)))
                .thenReturn(rec);
        dbg.step(created.getSessionId());
        PipelineDebugSessionVO after = dbg.getSession(created.getSessionId());
        assertEquals("completed", after.getState(),
                "JOIN 单节点执行后应 completed，实际 state=" + after.getState()
                        + " / error=" + after.getError());
    }

    // ─── F. SINK 节点路由（P1#1） ───────────────────────────────

    @Test
    @DisplayName("PipelineDebugService SINK — 执行器 switch 已补 SINK 分支")
    void debugServiceSinkRoutes() throws Exception {
        PipelineDebugService dbg = new PipelineDebugService(
                repository, connectorFactory, jdbc, dataSourceService, udfService);
        // mock 数据源
        DataSourceEntity ds = new DataSourceEntity();
        ds.setId("ds-1");
        ds.setConnectionConfig("{\"jdbcUrl\":\"jdbc:postgresql://x:5432/y\",\"username\":\"u\",\"password\":\"p\"}");
        when(dataSourceService.getById("ds-1")).thenReturn(ds);
        // mock ConnectorFactory → JdbcConnector
        JdbcConnector mockJdbcConn = mock(JdbcConnector.class);
        when(connectorFactory.getConnector("JDBC")).thenReturn(mockJdbcConn);
        // mock JdbcConnector.executeSql 返回值（INSERT 无结果集）
        when(mockJdbcConn.executeSql(anyString(), anyString(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(List.of());
        PipelineDebugStartDTO dto = new PipelineDebugStartDTO();
        PipelineDebugStartDTO.PipelineDebugDefinitionDTO def =
                new PipelineDebugStartDTO.PipelineDebugDefinitionDTO();
        def.setName("adhoc-sink");
        PipelineDebugStartDTO.PipelineDebugNodeDTO nn = new PipelineDebugStartDTO.PipelineDebugNodeDTO();
        nn.setNodeId("s1");
        nn.setType("SINK");
        nn.setConfig(Map.of(
                "table", "target_tbl",
                "datasourceId", "ds-1",
                "mode", "append",
                "inlineData", List.of(Map.of("id", 1L, "val", "x"))));
        def.setNodes(List.of(nn));
        dto.setDefinition(def);
        PipelineDebugSessionVO created = dbg.createSession(dto);
        PipelineExecution rec = new PipelineExecution();
        rec.setId("exec-sink-1");
        rec.setDefinitionId("adhoc-sink-x");
        when(repository.insertExecution(org.mockito.ArgumentMatchers.any(PipelineExecution.class)))
                .thenReturn(rec);
        dbg.step(created.getSessionId());
        PipelineDebugSessionVO after = dbg.getSession(created.getSessionId());
        assertEquals("completed", after.getState(),
                "SINK 单节点执行后应 completed，实际 state=" + after.getState()
                        + " / error=" + after.getError());
        // 验证 JdbcConnector 被调用了（INSERT 写入）
        verify(mockJdbcConn, org.mockito.Mockito.atLeast(1)).executeSql(anyString(), anyString(), org.mockito.ArgumentMatchers.anyInt());
    }

    // ─── G. topologicalSort 边序回归（P1#2） ─────────────────────

    @Test
    @DisplayName("topologicalSort — 输入节点数组 e,t,s 但 DAG 是 s→t→e 时应输出 s,t,e")
    void topologicalSortReverseOrder() {
        // 3 节点 DAG: s → t → e
        // 输入顺序故意打乱: e(依赖t), t(依赖s), s(无依赖)
        PipelineDebugService dbg = new PipelineDebugService(
                repository, connectorFactory, jdbc, dataSourceService, udfService);
        // 通过 createSession 触发 topologicalSort
        PipelineDebugStartDTO dto = new PipelineDebugStartDTO();
        PipelineDebugStartDTO.PipelineDebugDefinitionDTO def =
                new PipelineDebugStartDTO.PipelineDebugDefinitionDTO();
        def.setName("topo-test");
        List<PipelineDebugStartDTO.PipelineDebugNodeDTO> dtos = new java.util.ArrayList<>();
        dtos.add(debugNodeDto("e", "TRANSFORM_SQL", Map.of(), List.of("t")));
        dtos.add(debugNodeDto("t", "TRANSFORM_SQL", Map.of(), List.of("s")));
        dtos.add(debugNodeDto("s", "SOURCE_CSV", Map.of(), List.of()));
        def.setNodes(dtos);
        dto.setDefinition(def);
        PipelineDebugSessionVO created = dbg.createSession(dto);
        List<NodeStepVO> steps = created.getSteps();
        // 断言输出顺序 s, t, e
        assertEquals(3, steps.size(), "应有 3 个排序节点");
        assertEquals("s", steps.get(0).getNodeId(), "第 1 个应是 s（无依赖源）");
        assertEquals("t", steps.get(1).getNodeId(), "第 2 个应是 t（依赖 s）");
        assertEquals("e", steps.get(2).getNodeId(), "第 3 个应是 e（依赖 t）");
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

    private PipelineDebugStartDTO.PipelineDebugNodeDTO debugNodeDto(
            String nodeId, String type, Map<String, Object> config, List<String> dependsOn) {
        PipelineDebugStartDTO.PipelineDebugNodeDTO dto = new PipelineDebugStartDTO.PipelineDebugNodeDTO();
        dto.setNodeId(nodeId);
        dto.setType(type);
        dto.setConfig(config);
        dto.setDependsOn(dependsOn);
        return dto;
    }
}
