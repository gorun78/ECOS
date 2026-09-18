package com.chinacreator.gzcm.engine.data.pipeline;

import com.chinacreator.gzcm.engine.data.DataSourceService;
import com.chinacreator.gzcm.engine.data.UdfService;
import com.chinacreator.gzcm.engine.data.datasource.entity.DataSourceEntity;
import com.chinacreator.gzcm.runtime.access.connector.ConnectorFactory;
import com.chinacreator.gzcm.runtime.access.connector.JdbcConnector;
import com.chinacreator.gzcm.runtime.access.storage.MinioStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SINK 节点批量 INSERT 单测 — batchSize 分片 + executeBatch 调用验证（不连外部 PG）。
 *
 * <p>验证 P1#4 修复：
 * <ul>
 *   <li>30 行 / batchSize=10 → JdbcConnector.executeBatch 被调 1 次（内部循环 3 批），
 *       且传入 values.size()==30, batchSize=10</li>
 *   <li>SQL 参数化（占位符 ? 数量 = 列数）</li>
 *   <li>overwrite 模式 → 先 executeSql(DELETE FROM) 再 executeBatch</li>
 * </ul>
 */
class PipelineSinkBatchInsertTest {

    private PipelineRepository repository;
    private JdbcConnector jdbcConnector;
    private DataSourceService dataSourceService;
    private PipelineExecutionService service;

    @BeforeEach
    void setUp() {
        this.repository = mock(PipelineRepository.class);
        this.jdbcConnector = mock(JdbcConnector.class);
        this.dataSourceService = mock(DataSourceService.class);
        ConnectorFactory cf = mock(ConnectorFactory.class);
        when(cf.getConnector("JDBC")).thenReturn(jdbcConnector);
        this.service = new PipelineExecutionService(repository, cf,
                mock(JdbcTemplate.class), dataSourceService, mock(UdfService.class),
                mock(MinioStorageService.class),
                mock(com.chinacreator.gzcm.engine.data.service.DataLakeResourceService.class));
    }

    @Test
    @DisplayName("SINK 30行 batchSize=10 → executeBatch 收到 30 行 values + batchSize=10, 返回 30")
    void batchInsert30RowsBatchSize10() {
        // 构造 30 行 inlineData JSON
        StringBuilder inlineJson = new StringBuilder("[");
        for (int i = 0; i < 30; i++) {
            if (i > 0) {
                inlineJson.append(",");
            }
            inlineJson.append("{\"id\":").append(i).append(",\"name\":\"r").append(i).append("\"}");
        }
        inlineJson.append("]");

        String configJson = "{\"table\":\"t_target\",\"mode\":\"append\","
                + "\"batchSize\":10,\"datasourceId\":\"ds1\","
                + "\"columns\":[\"id\",\"name\"],"
                + "\"inlineData\":" + inlineJson + "}";
        PipelineNode sink = new PipelineNode();
        sink.setId("sink-1");
        sink.setNodeId("sink-1");
        sink.setDefinitionId("p-batch");
        sink.setType("SINK");
        sink.setConfig(configJson);
        sink.setDependsOn("[]");

        // mock repository
        PipelineDefinition def = new PipelineDefinition();
        def.setId("p-batch");
        def.setName("batch-test");
        def.setStatus("ACTIVE");
        when(repository.findDefinitionById("p-batch")).thenReturn(def);
        when(repository.findNodesByDefinitionId(anyString())).thenReturn(List.of(sink));
        PipelineExecution execRec = new PipelineExecution();
        execRec.setId("e-batch");
        execRec.setDefinitionId("p-batch");
        execRec.setStatus("PENDING");
        when(repository.insertExecution(any(PipelineExecution.class))).thenReturn(execRec);

        // mock datasource
        DataSourceEntity ds = new DataSourceEntity();
        ds.setDatasourceId("ds1");
        ds.setConnectionConfig("{\"jdbcUrl\":\"jdbc:postgresql://h:5432/db\",\"username\":\"u\",\"password\":\"p\"}");
        when(dataSourceService.getById("ds1")).thenReturn(ds);

        // mock executeBatch → 30 行成功
        when(jdbcConnector.executeBatch(anyString(), anyString(), any(List.class), anyInt())).thenReturn(30);

        PipelineExecution result = service.executePipeline("p-batch");

        assertEquals("COMPLETED", result.getStatus(),
                "Pipeline 应成功完成，实际: " + result.getStatus() + " err=" + result.getErrorMessage());

        // 验证 executeBatch 被调 1 次（内部按 batchSize=10 循环 3 批）
        ArgumentCaptor<List<Object[]>> valuesCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Integer> batchSizeCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(jdbcConnector, times(1)).executeBatch(
                anyString(), sqlCaptor.capture(), valuesCaptor.capture(), batchSizeCaptor.capture());

        List<Object[]> capturedValues = valuesCaptor.getValue();
        assertEquals(30, capturedValues.size(), "executeBatch 应收到 30 行数据");
        assertEquals(2, capturedValues.get(0).length, "每行应有 2 个参数 (id, name)");
        assertEquals(10, batchSizeCaptor.getValue().intValue(), "batchSize 应为 10");

        String capturedSql = sqlCaptor.getValue();
        assertEquals("INSERT INTO t_target (id, name) VALUES (?, ?)", capturedSql,
                "SQL 应为参数化 INSERT，实际: " + capturedSql);
    }

    @Test
    @DisplayName("SINK overwrite 模式 → 先 DELETE 再 executeBatch")
    void overwriteModeDeletesThenBatchInserts() {
        String configJson = "{\"table\":\"t_target\",\"mode\":\"overwrite\","
                + "\"batchSize\":10,\"datasourceId\":\"ds1\","
                + "\"columns\":[\"id\",\"name\"],"
                + "\"inlineData\":[{\"id\":1,\"name\":\"a\"}]}";
        PipelineNode sink = new PipelineNode();
        sink.setId("sink-2");
        sink.setNodeId("sink-2");
        sink.setDefinitionId("p-ovw");
        sink.setType("SINK");
        sink.setConfig(configJson);
        sink.setDependsOn("[]");

        PipelineDefinition def = new PipelineDefinition();
        def.setId("p-ovw");
        def.setName("overwrite-test");
        def.setStatus("ACTIVE");
        when(repository.findDefinitionById("p-ovw")).thenReturn(def);
        when(repository.findNodesByDefinitionId(anyString())).thenReturn(List.of(sink));
        PipelineExecution execRec = new PipelineExecution();
        execRec.setId("e-ovw");
        execRec.setDefinitionId("p-ovw");
        execRec.setStatus("PENDING");
        when(repository.insertExecution(any(PipelineExecution.class))).thenReturn(execRec);

        DataSourceEntity ds = new DataSourceEntity();
        ds.setDatasourceId("ds1");
        ds.setConnectionConfig("{\"jdbcUrl\":\"jdbc:postgresql://h:5432/db\",\"username\":\"u\",\"password\":\"p\"}");
        when(dataSourceService.getById("ds1")).thenReturn(ds);

        when(jdbcConnector.executeBatch(anyString(), anyString(), any(List.class), anyInt())).thenReturn(1);
        jdbcConnector.executeSql(anyString(), anyString(), anyInt());

        PipelineExecution result = service.executePipeline("p-ovw");

        assertEquals("COMPLETED", result.getStatus(),
                "Pipeline 应成功完成，实际: " + result.getStatus() + " err=" + result.getErrorMessage());

        // overwrite → 先 DELETE
        verify(jdbcConnector, times(1)).executeSql(anyString(),
                org.mockito.ArgumentMatchers.contains("DELETE FROM t_target"), anyInt());
        // 再 batch insert
        verify(jdbcConnector, times(1)).executeBatch(
                anyString(), org.mockito.ArgumentMatchers.contains("INSERT INTO t_target"),
                any(List.class), anyInt());
    }
}
