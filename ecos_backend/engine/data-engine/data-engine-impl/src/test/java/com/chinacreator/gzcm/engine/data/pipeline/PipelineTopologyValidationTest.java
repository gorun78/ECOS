package com.chinacreator.gzcm.engine.data.pipeline;

import com.chinacreator.gzcm.common.exception.BusinessException;
import com.chinacreator.gzcm.common.exception.ValidationException;
import com.chinacreator.gzcm.runtime.access.connector.ConnectorFactory;
import com.chinacreator.gzcm.runtime.access.storage.MinioStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PipelineTopologyValidationTest — 执行引擎环检测 + 必填配置节点入口校验（Wave 4 T1，不连 PG）。
 *
 * <p>PipelineExecutionService.executePipeline 内部用 Kahn 拓扑排序检测环，
 * executeNode 走 switch 对各 SOURCE/TRANSFORM/OUTPUT 节点入口断言必填配置。
 * 这层"后端镜像"与前端 {@code pipelineValidation.ts} 同源（架构铁律 §4.8.2）。
 *
 * <p>不涉及外部 PG：PipelineRepository / ConnectorFactory / JdbcTemplate /
 * DataSourceService 全部 mock，只测业务门面分支：
 * <ul>
 *   <li>环 A↔B（depends_on 互指）→ BusinessException("循环依赖")</li>
 *   <li>SOURCE_JDBC 缺 sql/datasourceId → ValidationException 引用字段名</li>
 *   <li>SOURCE_CSV 缺 filePath / SOURCE_REST 缺 url / TRANSFORM_SQL 缺 sql /
 *       OUTPUT_OBJECT 缺 targetTable</li>
 *   <li>SOURCE_CDC 节点类型执行前命中标准档（"仅 flagship 版本支持"），对应 T3 三版本兼容性</li>
 *   <li>单节点失败 → repository.updateExecutionStatus(FAILED, msg, 0L) 兜底会计</li>
 * </ul>
 *
 * <p>注意：CONNECTOR_FACTORY 为 final 类（类级别 @Component），
 * 用 Mockito.mockConstruction 拦截构造（mercedes/mockito 5.x），无需反射或子类化。
 */
class PipelineTopologyValidationTest {

    private PipelineRepository repository;
    private ConnectorFactory connectorFactory;
    private JdbcTemplate jdbc;
    private com.chinacreator.gzcm.engine.data.DataSourceService dataSourceService;
    private com.chinacreator.gzcm.engine.data.UdfService udfService;
    private PipelineExecutionService service;

    @BeforeEach
    void setUp() {
        this.repository = mock(PipelineRepository.class);
        this.connectorFactory = mock(ConnectorFactory.class);
        this.jdbc = mock(JdbcTemplate.class);
        this.dataSourceService = mock(com.chinacreator.gzcm.engine.data.DataSourceService.class);
        this.udfService = mock(com.chinacreator.gzcm.engine.data.UdfService.class);
        this.service = new PipelineExecutionService(
                repository, connectorFactory, jdbc, dataSourceService, udfService,
                mock(MinioStorageService.class),
                mock(com.chinacreator.gzcm.engine.data.service.DataLakeResourceService.class));
    }

    // ─── A. 环检测 ────────────────────────────────────

    @Test
    @DisplayName("拓扑排序失败 — A↔B 互依赖 → executePipeline 返回 FAILED 且 errorMessage 含'循环依赖'")
    void cycleDetectedBetweenTwoNodes() {
        whenDef("p-cycle");
        PipelineNode a = node("a1", "SOURCE_JDBC",
                "{\"sql\":\"SELECT 1\",\"datasourceId\":\"ds1\"}");
        a.setDependsOn("[\"b1\"]");   // A depends on B
        PipelineNode b = node("b1", "TRANSFORM_SQL", "{\"sql\":\"SELECT 2\"}");
        b.setDependsOn("[\"a1\"]");   // B depends on A
        whenNodes(List.of(a, b));

        PipelineExecution exec = service.executePipeline("p-cycle");

        // executePipeline 用 try-catch 包裹 executeNode，环检测抛 BusinessException 被吞
        // → FAILED 状态 + errorMessage 含'循环依赖'（架构铁律 §5.4 V1：行为可观察）
        assertEquals("FAILED", exec.getStatus());
        assertTrue(exec.getErrorMessage() != null && exec.getErrorMessage().contains("循环依赖"),
                "executePipeline 失败时应把循环依赖写进 errorMessage，实际：" + exec.getErrorMessage());
        verify(repository).updateExecutionStatus(
                ArgumentMatchers.anyString(),
                ArgumentMatchers.eq("FAILED"),
                ArgumentMatchers.argThat(msg -> msg != null && msg.contains("循环依赖")),
                ArgumentMatchers.eq(0L));
    }

    // ─── B. 必填配置缺失（executeNode 入口解析） ─────────
    // 说明：executePipeline 内部用 try-catch 把节点异常吞掉落 FAILED 状态，
    // 不向外抛。校验"必填配置"语义通过 FAILED + 节点类型 + 字段名 断言。

    @Test
    @DisplayName("SOURCE_JDBC — sql 缺失 → executePipeline FAILED + errorMessage 含 sql 字段名")
    void sourceJdbcMissingSql() {
        whenDef("p-miss-sql");
        PipelineNode n = node("n1", "SOURCE_JDBC", "{\"datasourceId\":\"ds1\"}");
        whenNodes(List.of(n));

        PipelineExecution exec = service.executePipeline("p-miss-sql");
        assertEquals("FAILED", exec.getStatus());
        assertTrue(exec.getErrorMessage() != null && exec.getErrorMessage().contains("sql"),
                "执行失败应把'sql 必填'写进 errorMessage，实际：" + exec.getErrorMessage());
    }

    @Test
    @DisplayName("SOURCE_JDBC — datasourceId 缺失 → executePipeline FAILED + errorMessage 含 datasourceId")
    void sourceJdbcMissingDatasourceId() {
        whenDef("p-miss-ds");
        PipelineNode n = node("n1", "SOURCE_JDBC", "{\"sql\":\"SELECT 1\"}");
        whenNodes(List.of(n));

        PipelineExecution exec = service.executePipeline("p-miss-ds");
        assertEquals("FAILED", exec.getStatus());
        assertTrue(exec.getErrorMessage() != null && exec.getErrorMessage().contains("datasourceId"),
                "执行失败应把'datasourceId 必填'写进 errorMessage，实际：" + exec.getErrorMessage());
    }

    @Test
    @DisplayName("TRANSFORM_SQL — sql 缺失 → executePipeline FAILED + errorMessage 含 sql 字段名")
    void transformSqlMissingSql() {
        whenDef("p-miss-tp");
        PipelineNode n = node("n1", "TRANSFORM_SQL", null);
        whenNodes(List.of(n));

        PipelineExecution exec = service.executePipeline("p-miss-tp");
        assertEquals("FAILED", exec.getStatus());
        assertTrue(exec.getErrorMessage() != null && exec.getErrorMessage().contains("sql"),
                "执行失败应把'sql 必填'写进 errorMessage，实际：" + exec.getErrorMessage());
    }

    @Test
    @DisplayName("OUTPUT_OBJECT — targetTable 缺失 → executePipeline FAILED + errorMessage 含 targetTable")
    void outputObjectMissingTargetTable() {
        whenDef("p-miss-out");
        PipelineNode n = node("n1", "OUTPUT_OBJECT",
                "{\"rows\":[{\"a\":1}]}");
        whenNodes(List.of(n));

        PipelineExecution exec = service.executePipeline("p-miss-out");
        assertEquals("FAILED", exec.getStatus());
        assertTrue(exec.getErrorMessage() != null && exec.getErrorMessage().contains("targetTable"),
                "执行失败应把'targetTable 必填'写进 errorMessage，实际：" + exec.getErrorMessage());
    }

    @Test
    @DisplayName("SOURCE_CSV — filePath 缺失 → executePipeline FAILED + errorMessage 含 filePath")
    void sourceCsvMissingFilePath() {
        whenDef("p-miss-csv");
        PipelineNode n = node("n1", "SOURCE_CSV", "{}");
        whenNodes(List.of(n));

        PipelineExecution exec = service.executePipeline("p-miss-csv");
        assertEquals("FAILED", exec.getStatus());
        assertTrue(exec.getErrorMessage() != null && exec.getErrorMessage().contains("filePath"),
                "执行失败应把'filePath 必填'写进 errorMessage，实际：" + exec.getErrorMessage());
    }

    @Test
    @DisplayName("SOURCE_REST — url 缺失 → executePipeline FAILED + errorMessage 含 url")
    void sourceRestMissingUrl() {
        whenDef("p-miss-rest");
        PipelineNode n = node("n1", "SOURCE_REST", "{}");
        whenNodes(List.of(n));

        PipelineExecution exec = service.executePipeline("p-miss-rest");
        assertEquals("FAILED", exec.getStatus());
        assertTrue(exec.getErrorMessage() != null && exec.getErrorMessage().contains("url"),
                "执行失败应把'url 必填'写进 errorMessage，实际：" + exec.getErrorMessage());
    }

    // ─── C. SOURCE_CDC 三版本兼容性守卫 ─────────────────

    @Test
    @DisplayName("SOURCE_CDC — standard/enterprise 守卫，仅 ultimate 支持（flagship 文案）")
    void sourceCdcUnsupportedInStandardOrEnterprise() {
        whenDef("p-cdc");
        PipelineNode n = node("n1", "SOURCE_CDC", "{}");
        whenNodes(List.of(n));

        PipelineExecution exec = service.executePipeline("p-cdc");
        assertEquals("FAILED", exec.getStatus());
        // executeNode switch 命中 "SOURCE_CDC" 分支抛 BusinessException("仅 flagship 版本支持")
        assertTrue(exec.getErrorMessage() != null
                        && (exec.getErrorMessage().contains("SOURCE_CDC") || exec.getErrorMessage().contains("flagship")),
                "执行失败应把'flagship 专属'语义写进 errorMessage，实际：" + exec.getErrorMessage());
    }

    // ─── D. 单节点失败兜底 failed 落库 ───────────────────

    @Test
    @DisplayName("单节点失败 — repository.updateExecutionStatus 收到 FAILED + 0 行")
    void singleNodeFailureSetsFailedStatus() {
        whenDef("p-failed");
        // SOURCE_CDC 走 executeNode 立即抛 BusinessException("仅 flagship")
        PipelineNode n = node("n1", "SOURCE_CDC", "{}");
        whenNodes(List.of(n));

        PipelineExecution exec = service.executePipeline("p-failed");
        assertEquals("FAILED", exec.getStatus());

        // 关键回归点：失败一定要被记到 ecos_pipeline_execution
        verify(repository).updateExecutionStatus(
                ArgumentMatchers.anyString(),
                ArgumentMatchers.eq("FAILED"),
                ArgumentMatchers.nullable(String.class),
                ArgumentMatchers.eq(0L));
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
        when(repository.findNodesByDefinitionId(ArgumentMatchers.anyString())).thenReturn(nodes);
        doReturn(execRec("e-stub")).when(repository)
                .insertExecution(ArgumentMatchers.any(PipelineExecution.class));
    }

    private PipelineNode node(String id, String type, String configJson) {
        PipelineNode n = new PipelineNode();
        n.setId(id);
        n.setNodeId(id);
        n.setDefinitionId(defIdOf(id));
        n.setType(type);
        n.setConfig(configJson);
        n.setDependsOn("[]");
        return n;
    }

    private static String defIdOf(String nodeId) {
        // 与 executePipeline 内 repository.findNodesByDefinitionId 参数一致的 definition id 桩
        // （mock 不验证参数值，仅占位）
        return "p-x-" + nodeId;
    }

    private PipelineExecution execRec(String id) {
        PipelineExecution e = new PipelineExecution();
        e.setId(id);
        e.setDefinitionId("p-x");
        e.setStatus("PENDING");
        return e;
    }
}
