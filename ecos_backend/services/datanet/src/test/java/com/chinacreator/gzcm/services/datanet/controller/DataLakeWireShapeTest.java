package com.chinacreator.gzcm.services.datanet.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.chinacreator.gzcm.services.datanet.controller.DataLakeController.DataLakeHealthVO;
import com.chinacreator.gzcm.services.datanet.controller.DataLakeController.DataLakeQueryVO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * DataLake 端点 wire 保真（DI-2 #147EN）。
 *
 * <p>查询回执 3 键（{@code row_count} 保持下划线）；健康检查恒定三键 +
 * 探针触发时出现 {@code minio_http}。</p>
 */
class DataLakeWireShapeTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    @DisplayName("查询回执：sql/row_count/data 三键，行数据原样")
    void queryKeepsKeys() throws Exception {
        JsonNode node = MAPPER.valueToTree(new DataLakeQueryVO("select 1",
                1, List.of(Map.of("c", 1))));
        assertEquals(List.of("sql", "row_count", "data"), fieldNames(node));
        assertEquals(1, node.get("row_count").asInt());
        assertEquals(1, node.get("data").get(0).get("c").asInt());
    }

    @Test
    @DisplayName("健康检查：探针未触发省略 minio_http；触发时插在 version 之前")
    void healthKeepsConditionalKey() throws Exception {
        JsonNode noProbe = MAPPER.valueToTree(new DataLakeHealthVO(
                Map.of("status", "DOWN"), Map.of("status", "UP"), null, "P2-15"));
        assertEquals(List.of("duckdb", "minio", "datalake_version"), fieldNames(noProbe));
        assertFalse(noProbe.has("minio_http"));

        JsonNode probed = MAPPER.valueToTree(new DataLakeHealthVO(
                Map.of("status", "DOWN"), Map.of("status", "DOWN"),
                Map.of("status", "UP", "code", 200), "P2-15"));
        assertEquals(List.of("duckdb", "minio", "minio_http", "datalake_version"),
                fieldNames(probed));
        assertEquals(200, probed.get("minio_http").get("code").asInt());
    }

    private static List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }
}
