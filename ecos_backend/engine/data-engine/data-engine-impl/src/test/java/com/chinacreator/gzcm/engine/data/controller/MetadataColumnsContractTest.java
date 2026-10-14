package com.chinacreator.gzcm.engine.data.controller;

import com.chinacreator.gzcm.engine.data.MetadataService;
import com.chinacreator.gzcm.engine.data.model.MetadataColumnsVO;
import com.chinacreator.gzcm.engine.data.service.DataLakeResourceService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * P0-3 线上传输契约锁定测试（差距补齐）。
 *
 * <p>本体引擎 {@code DatNetLineageGuard.RestLineageColumnFetcher} 的解析路径硬编码为
 * {@code body.get("data")} → {@code data.get("columns")}，且要求其为数组；
 * 一旦本端点出参字段名漂移（例如改成 {@code fields} 或把列提到顶层），
 * 调用侧会得到 {@code null} 并把提案 execute 打回 503 fail-closed——
 * 这是最难查的一类「两端各自都测试通过」的断裂。本测试把序列化后的真实 JSON 结构与
 * 调用侧解析代码逐字段对账，字段名一改即红。</p>
 */
class MetadataColumnsContractTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private MetadataController controllerWith(com.chinacreator.gzcm.engine.data.metadata.ColumnCatalogQueryService query) {
        return new MetadataController(
                mock(com.chinacreator.gzcm.engine.data.service.MetadataCollectionService.class),
                mock(com.chinacreator.gzcm.engine.data.metadata.MetadataTaskService.class),
                mock(com.chinacreator.gzcm.engine.data.metadata.AutoCollectScheduler.class),
                mock(com.chinacreator.gzcm.engine.data.service.MetadataRowCountService.class),
                mock(com.chinacreator.gzcm.engine.data.DataSourceService.class),
                mock(com.chinacreator.gzcm.engine.data.metadata.MetadataAsyncTrigger.class),
                mock(com.chinacreator.gzcm.engine.data.metadata.MetadataCollectGitArchive.class),
                mock(MetadataService.class),
                query,
                mock(DataLakeResourceService.class));
    }

    @Test
    @DisplayName("序列化后的 JSON 必须满足调用侧解析路径 $.code=0 且 $.data.columns 为数组")
    void responseBodyMatchesFetcherParsingPath() throws Exception {
        com.chinacreator.gzcm.engine.data.metadata.ColumnCatalogQueryService query =
                mock(com.chinacreator.gzcm.engine.data.metadata.ColumnCatalogQueryService.class);
        when(query.query("ecos_dw.stage_fact"))
                .thenReturn(new MetadataColumnsVO("ecos_dw.stage_fact", "ecos_dw.stage_fact", true,
                        List.of("fact_id", "amount")));

        JsonNode root = MAPPER.readTree(MAPPER.writeValueAsString(
                controllerWith(query).listColumnsByTableRef("ecos_dw.stage_fact")));

        assertEquals(0, root.path("code").asInt(), "调用侧要求 code=0");
        assertTrue(root.path("data").isObject(), "$.data 必须是对象，实际: " + root.path("data"));
        assertTrue(root.path("data").path("columns").isArray(), "$.data.columns 必须是数组");
        assertEquals("fact_id", root.path("data").path("columns").get(0).asText());
        assertEquals("amount", root.path("data").path("columns").get(1).asText());
        assertEquals("ecos_dw.stage_fact", root.path("data").path("resolvedTable").asText());
        assertTrue(root.path("data").path("exists").asBoolean());
    }

    @Test
    @DisplayName("表不存在：code 仍为 0（不得伪装成依赖故障）且 columns 为空数组")
    void missingTableKeepsCodeZeroWithEmptyColumns() throws Exception {
        com.chinacreator.gzcm.engine.data.metadata.ColumnCatalogQueryService query =
                mock(com.chinacreator.gzcm.engine.data.metadata.ColumnCatalogQueryService.class);
        when(query.query(anyString())).thenReturn(new MetadataColumnsVO("ecos_dw.ghost", null, false, List.of()));

        JsonNode root = MAPPER.readTree(MAPPER.writeValueAsString(
                controllerWith(query).listColumnsByTableRef("ecos_dw.ghost")));

        assertEquals(0, root.path("code").asInt());
        assertFalse(root.path("data").path("exists").asBoolean());
        assertEquals(0, root.path("data").path("columns").size());
    }

    @Test
    @DisplayName("非法 tableRef：code=400 且无 $.data.columns（调用侧按依赖不可用 fail-closed）")
    void invalidTableRefReturnsNonZeroCodeWithoutColumns() throws Exception {
        com.chinacreator.gzcm.engine.data.metadata.ColumnCatalogQueryService query =
                new com.chinacreator.gzcm.engine.data.metadata.ColumnCatalogQueryService(
                        mock(com.chinacreator.gzcm.engine.data.dao.ColumnCatalogDao.class));

        JsonNode root = MAPPER.readTree(MAPPER.writeValueAsString(
                controllerWith(query).listColumnsByTableRef("pg_catalog.pg_user")));

        assertEquals(400, root.path("code").asInt());
        assertTrue(root.path("data").isMissingNode() || root.path("data").isNull(),
                "入参被拒时不应带 data.columns，实际: " + root.path("data"));
    }
}
