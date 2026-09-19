package com.chinacreator.gzcm.engine.data.pipeline;

import com.chinacreator.gzcm.engine.data.DataSourceService;
import com.chinacreator.gzcm.engine.data.UdfService;
import com.chinacreator.gzcm.engine.data.service.DataLakeResourceService;
import com.chinacreator.gzcm.engine.data.service.LakeObjectKeys;
import com.chinacreator.gzcm.runtime.access.connector.ConnectorFactory;
import com.chinacreator.gzcm.runtime.access.connector.CsvConnector;
import com.chinacreator.gzcm.runtime.access.storage.MinioStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * B6-1 数据湖节点（SOURCE_MINIO / SINK_MINIO）单测 —— 不连 PG / MinIO。
 *
 * <p>锁定：
 * <ol>
 *   <li>对象 key 由 {@link LakeObjectKeys} 按分层规范 §三 组装（{@code raw/structured/{source}/{table}/{partition}=YYYY-MM-DD/...}）</li>
 *   <li>分区字段 {@code dw.lake.partition_by} 非法值回退 {@code dt}（防注入）</li>
 *   <li>SINK_MINIO format=parquet（无写入器）→ 明确业务异常，禁止静默写成 csv</li>
 *   <li>SOURCE_MINIO zone/table 缺失 → 明确校验异常（不裸 500）</li>
 *   <li>SOURCE_MINIO 结构化读取按前缀取最新分区对象</li>
 * </ol>
 */
class PipelineLakeNodeTest {

    private PipelineRepository repository;
    private ConnectorFactory connectorFactory;
    private MinioStorageService minioStorageService;
    private DataLakeResourceService dataLakeResourceService;
    private PipelineExecutionService service;

    @BeforeEach
    void setUp() {
        this.repository = mock(PipelineRepository.class);
        this.connectorFactory = mock(ConnectorFactory.class);
        this.minioStorageService = mock(MinioStorageService.class);
        this.dataLakeResourceService = mock(DataLakeResourceService.class);
        this.service = new PipelineExecutionService(repository, connectorFactory,
                mock(JdbcTemplate.class), mock(DataSourceService.class), mock(UdfService.class),
                minioStorageService, dataLakeResourceService);
    }

    // ── LakeObjectKeys 单测（无 Spring 依赖） ──

    @Test
    @DisplayName("structuredObjectKey — 按分层规范 §三 组装（含 source/table/分区日/时间戳）")
    void structuredObjectKeyFollowsSpec() {
        String key = LakeObjectKeys.structuredObjectKey("ds1", "orders", "dt", "csv",
                LocalDateTime.of(2026, 9, 19, 10, 30, 0));
        assertEquals("raw/structured/ds1/orders/dt=2026-09-19/orders_20260919103000.csv", key);
    }

    @Test
    @DisplayName("resolvePartitionField — 合法别名保留，非法/空值回退 dt（防注入）")
    void partitionFieldSanitized() {
        assertEquals("pt_day", LakeObjectKeys.resolvePartitionField("pt_day"));
        assertEquals("dt", LakeObjectKeys.resolvePartitionField("DROP TABLE x"));
        assertEquals("dt", LakeObjectKeys.resolvePartitionField(" "));
        assertEquals("dt", LakeObjectKeys.resolvePartitionField(null));
    }

    @Test
    @DisplayName("unstructuredObjectKey — raw/unstructured/{source}/{docId}/{fileName}（去文件名路径段）")
    void unstructuredObjectKeyFollowsSpec() {
        assertEquals("raw/unstructured/kb/doc-1/manual.pdf",
                LakeObjectKeys.unstructuredObjectKey("kb", "doc-1", "C:\\tmp\\manual.pdf"));
    }

    // ── SINK_MINIO ──

    @Test
    @DisplayName("SINK_MINIO format=parquet → 明确业务异常（写入器未就绪），不静默写 csv、不调用 MinIO")
    void sinkMinioParquetDegradesExplicitly() {
        stubSingleNode("p-parquet", "SINK_MINIO",
                "{\"table\":\"orders\",\"format\":\"parquet\",\"inlineData\":[{\"id\":1}]}");

        PipelineExecution exec = service.executePipeline("p-parquet");

        assertEquals("FAILED", exec.getStatus(), "parquet 无写入器时应执行失败");
        assertTrue(exec.getErrorMessage().contains("写入器未就绪"),
                "错误信息应明确说明写入器未就绪，实际: " + exec.getErrorMessage());
        verify(minioStorageService, never()).putObject(anyString(), any(byte[].class), anyString());
    }

    @Test
    @DisplayName("SINK_MINIO 未指定 format → 保持既有 csv 能力（全局 dw.lake.storage_format 期望值不阻断管道）")
    void sinkMinioWithoutFormatKeepsCsvCompatibility() {
        stubSingleNode("p-noformat", "SINK_MINIO",
                "{\"table\":\"orders\",\"columns\":[\"id\"],\"inlineData\":[{\"id\":1}]}");
        when(minioStorageService.putObject(anyString(), any(byte[].class), anyString()))
                .thenReturn(Map.of("status", "success", "bucket", "ecos-datalake"));

        PipelineExecution exec = service.executePipeline("p-noformat");

        assertEquals("COMPLETED", exec.getStatus(),
                "未显式指定 format 时不应因全局 storage_format 期望值而失败，err=" + exec.getErrorMessage());
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(minioStorageService).putObject(keyCaptor.capture(), any(byte[].class), anyString());
        assertTrue(keyCaptor.getValue().endsWith(".csv"),
                "未显式指定 format 时对象后缀应为 csv，实际: " + keyCaptor.getValue());
    }

    @Test
    @DisplayName("SINK_MINIO format=csv + 无 objectName → 组装 raw/structured/{source}/{table}/dt=.../ 并登记近源层")
    void sinkMinioCsvAssemblesSpecKey() {
        stubSingleNode("p-csv", "SINK_MINIO",
                "{\"table\":\"orders\",\"format\":\"csv\",\"columns\":[\"id\"],"
                        + "\"inlineData\":[{\"id\":1}]}");
        when(minioStorageService.putObject(anyString(), any(byte[].class), anyString()))
                .thenReturn(Map.of("status", "success", "bucket", "ecos-datalake"));

        PipelineExecution exec = service.executePipeline("p-csv");

        assertEquals("COMPLETED", exec.getStatus(), "csv 写入应成功，err=" + exec.getErrorMessage());
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(minioStorageService).putObject(keyCaptor.capture(), any(byte[].class), anyString());
        String objectName = keyCaptor.getValue();
        assertTrue(objectName.startsWith("raw/structured/default/orders/dt="),
                "对象 key 应为规范前缀，实际: " + objectName);
        verify(dataLakeResourceService).markNearSourceStructured(
                eq("orders"), eq(objectName), anyString(), anyInt(), anyLong());
    }

    // ── SOURCE_MINIO ──

    @Test
    @DisplayName("SOURCE_MINIO zone=ODS（非法）→ 明确校验异常（不裸 500）")
    void sourceMinioInvalidZoneRejected() {
        stubSingleNode("p-zone", "SOURCE_MINIO", "{\"zone\":\"ODS\",\"table\":\"orders\"}");

        PipelineExecution exec = service.executePipeline("p-zone");

        assertEquals("FAILED", exec.getStatus());
        assertTrue(exec.getErrorMessage().contains("zone 仅支持 STRUCTURED / UNSTRUCTURED"),
                "错误信息应明确 zone 非法，实际: " + exec.getErrorMessage());
    }

    @Test
    @DisplayName("SOURCE_MINIO 结构化且无 table/objectName → 明确拒绝（table 必填）")
    void sourceMinioStructuredRequiresTable() {
        stubSingleNode("p-table", "SOURCE_MINIO", "{\"zone\":\"STRUCTURED\"}");

        PipelineExecution exec = service.executePipeline("p-table");

        assertEquals("FAILED", exec.getStatus());
        assertTrue(exec.getErrorMessage().contains("table 必填"),
                "错误信息应明确 table 必填，实际: " + exec.getErrorMessage());
    }

    @Test
    @DisplayName("SOURCE_MINIO 结构化无 objectName → 按前缀取最新分区对象并读取（csv 解析复用 CsvConnector）")
    void sourceMinioReadsLatestPartitionObject() {
        stubSingleNode("p-src", "SOURCE_MINIO",
                "{\"table\":\"orders\",\"source\":\"ds1\",\"format\":\"csv\"}");
        when(minioStorageService.listObjects("raw/structured/ds1/orders/")).thenReturn(List.of(
                Map.of("name", "raw/structured/ds1/orders/dt=2026-09-18/orders_20260918090000.csv",
                        "size", 10L, "lastModified", "2026-09-18T09:00:00Z"),
                Map.of("name", "raw/structured/ds1/orders/dt=2026-09-19/orders_20260919103000.csv",
                        "size", 20L, "lastModified", "2026-09-19T10:30:00Z")));
        when(minioStorageService.getObject("raw/structured/ds1/orders/dt=2026-09-19/orders_20260919103000.csv"))
                .thenReturn("id,name\n1,a\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        CsvConnector csvConnector = mock(CsvConnector.class);
        when(connectorFactory.getConnector("SOURCE_CSV")).thenReturn(csvConnector);
        when(csvConnector.readRows(anyString(), anyInt())).thenReturn(List.of(Map.of("id", "1", "name", "a")));

        PipelineExecution exec = service.executePipeline("p-src");

        assertEquals("COMPLETED", exec.getStatus(), "近源层读取应成功，err=" + exec.getErrorMessage());
        verify(minioStorageService).getObject(
                "raw/structured/ds1/orders/dt=2026-09-19/orders_20260919103000.csv");
    }

    // ── helpers ──

    /** 构造单节点管道定义并 mock repository（definitionId → 1 node，执行记录 INSERT 返回带 id 记录）。 */
    private void stubSingleNode(String definitionId, String type, String configJson) {
        PipelineNode node = new PipelineNode();
        node.setId("n-" + definitionId);
        node.setNodeId("n-" + definitionId);
        node.setDefinitionId(definitionId);
        node.setType(type);
        node.setConfig(configJson);
        node.setDependsOn("[]");

        PipelineDefinition def = new PipelineDefinition();
        def.setId(definitionId);
        def.setName("lake-test-" + definitionId);
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
