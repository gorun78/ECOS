package com.chinacreator.gzcm.engine.data.pipeline;

import com.chinacreator.gzcm.engine.data.DataSourceService;
import com.chinacreator.gzcm.engine.data.UdfService;
import com.chinacreator.gzcm.engine.data.datasource.entity.DataSourceEntity;
import com.chinacreator.gzcm.engine.data.service.DataLakeResourceService;
import com.chinacreator.gzcm.runtime.access.connector.ConnectorFactory;
import com.chinacreator.gzcm.runtime.access.connector.CsvConnector;
import com.chinacreator.gzcm.runtime.access.storage.MinioStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * C.8 降级与失败语义矩阵护栏（详细设计-02 C.8 / D.1 "05x"）—— 管道依赖不可用时 fail-loud、
 * 错误码真实落位、管道整体 FAILED 而非"成功"。只锁三条<b>禁成功</b>的管道面错误码：
 * <ul>
 *   <li>{@code ECOS-DATA-051} runtime-access(JDBC Driver) 不可用 → SOURCE_JDBC 直接失败，禁</li>
 *   <li>{@code ECOS-DATA-052} MinIO(RAW 近源层) 不可用 → SINK_MINIO 失败、不写半对象，禁</li>
 *   <li>{@code ECOS-DATA-057} DuckDB(parquet) 不可用 → 拒绝 parquet 维持 csv，可但须明确提示</li>
 * </ul>
 * 不触库、不 Spring 容器、不联网（仅打桩 MinIO / ConnectorFactory）。
 */
class C8DegradationFailLoudTest {

    private PipelineRepository repository;
    private ConnectorFactory connectorFactory;
    private MinioStorageService minioStorageService;
    private DataLakeResourceService dataLakeResourceService;
    private DataSourceService dataSourceService;

    private PipelineExecutionService service() {
        return new PipelineExecutionService(repository, connectorFactory,
                mock(JdbcTemplate.class), dataSourceService, mock(UdfService.class),
                minioStorageService, dataLakeResourceService);
    }

    @BeforeEach
    void setUp() {
        this.repository = mock(PipelineRepository.class);
        this.connectorFactory = mock(ConnectorFactory.class);
        this.minioStorageService = mock(MinioStorageService.class);
        this.dataLakeResourceService = mock(DataLakeResourceService.class);
        this.dataSourceService = mock(DataSourceService.class);
    }

    @Test
    @DisplayName("ECOS-DATA-051 — JDBC Driver 不可用（非 JdbcConnector）→ SOURCE_JDBC 管道失败，禁")
    void jdbcDriverUnavailable_failsLoud() {
        DataSourceEntity ds = new DataSourceEntity();
        ds.setConnectionConfig("jdbc:postgresql://x/db");
        when(dataSourceService.getById("ds1")).thenReturn(ds);
        // 模拟 runtime-access JDBC 底座缺失：工厂返回非 JdbcConnector（C.8 行"runtime-access(JDBC Driver)"）
        CsvConnector notJdbc = mock(CsvConnector.class);
        when(connectorFactory.getConnector("JDBC")).thenReturn(notJdbc);
        stubSingle("p-access", "SOURCE_JDBC", "{\"sql\":\"SELECT 1\",\"datasourceId\":\"ds1\"}");

        PipelineExecution exec = service().executePipeline("p-access");

        assertEquals("FAILED", exec.getStatus(), "JDBC 底座不可用时管道必须失败，禁成功");
        assertTrue(exec.getErrorMessage().contains("ECOS-DATA-051"),
                "错误信息须落 C.8 错误码 ECOS-DATA-051，实际: " + exec.getErrorMessage());
    }

    @Test
    @DisplayName("ECOS-DATA-052 — MinIO 上传失败 → 管道失败、错误码落位、不写近源层登记，禁")
    void minioUploadFailure_failsLoud() {
        when(minioStorageService.putObject(anyString(), any(byte[].class), anyString()))
                .thenReturn(Map.of("status", "failed", "message", "minio 连接超时"));
        stubSingle("p-minio", "SINK_MINIO",
                "{\"table\":\"orders\",\"format\":\"csv\",\"columns\":[\"id\"],\"inlineData\":[{\"id\":1}]}");

        PipelineExecution exec = service().executePipeline("p-minio");

        assertEquals("FAILED", exec.getStatus(), "MinIO 不可用时应管道整体 FAILED，不留半成功");
        assertTrue(exec.getErrorMessage().contains("ECOS-DATA-052"),
                "错误信息须落 C.8 错误码 ECOS-DATA-052，实际: " + exec.getErrorMessage());
        verify(dataLakeResourceService, never()).markNearSourceStructured(
                anyString(), anyString(), anyString(), anyInt(), any(Long.class));
    }

    @Test
    @DisplayName("ECOS-DATA-057 — parquet 写入器不可用 → 明确拒绝（错误码落位）、不调 MinIO，可但须提示")
    void parquetWriterUnavailable_rejectedWithCode() {
        stubSingle("p-parquet", "SINK_MINIO",
                "{\"table\":\"orders\",\"format\":\"parquet\",\"inlineData\":[{\"id\":1}]}");

        PipelineExecution exec = service().executePipeline("p-parquet");

        assertEquals("FAILED", exec.getStatus(), "parquet 写入器未就绪时应失败，不得静默写 csv");
        assertTrue(exec.getErrorMessage().contains("ECOS-DATA-057"),
                "错误信息须落 C.8 错误码 ECOS-DATA-057，实际: " + exec.getErrorMessage());
    }

    @Test
    @DisplayName("白线 — 三条 C.8 错误码常量在 PipelineExecutionService 内真实定义（防空文件静默绿）")
    void c8CodesDefinedInService() {
        assertEquals("ECOS-DATA-051", PipelineExecutionService.CODE_RUNTIME_ACCESS);
        assertEquals("ECOS-DATA-052", PipelineExecutionService.CODE_MINIO_UNAVAILABLE);
        assertEquals("ECOS-DATA-057", PipelineExecutionService.CODE_DUCKDB_UNAVAILABLE);
    }

    // ── helpers ──
    private void stubSingle(String definitionId, String type, String configJson) {
        PipelineNode node = new PipelineNode();
        node.setId("n-" + definitionId);
        node.setNodeId("n-" + definitionId);
        node.setDefinitionId(definitionId);
        node.setType(type);
        node.setConfig(configJson);
        node.setDependsOn("[]");

        PipelineDefinition def = new PipelineDefinition();
        def.setId(definitionId);
        def.setName("c8-" + definitionId);
        def.setStatus("ACTIVE");
        when(repository.findDefinitionById(definitionId)).thenReturn(def);
        when(repository.findNodesByDefinitionId(definitionId)).thenReturn(List.of(node));

        PipelineExecution rec = new PipelineExecution();
        rec.setId("exec-" + definitionId);
        rec.setDefinitionId(definitionId);
        rec.setStatus("PENDING");
        when(repository.insertExecution(any(PipelineExecution.class))).thenReturn(rec);
    }
}
