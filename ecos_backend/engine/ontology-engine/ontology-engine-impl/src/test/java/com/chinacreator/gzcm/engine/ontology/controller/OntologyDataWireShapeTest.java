package com.chinacreator.gzcm.engine.ontology.controller;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.ontology.service.OntologyDataService;
import com.chinacreator.gzcm.engine.ontology.service.OntologyService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 本体数据实例端点 wire 保真（DI-2 #147DM）。
 *
 * <p>三腿共用一个记录的三种落键形态：</p>
 * <ol>
 *   <li>读面：{@code recordKey/status} 在前、{@code properties} 居中、时间在后，缺值条件键省略；</li>
 *   <li>创建回执：{@code status} 为尾键（无 recordKey/updatedAt）；</li>
 *   <li>更新回执：{@code updatedAt} 为尾键（无 recordKey/status）。</li>
 * </ol>
 * <p>{@code properties} 属对象类型自定义属性（EAV），按原样透传不冻结。</p>
 */
class OntologyDataWireShapeTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private OntologyDataService dataService;
    private OntologyDataController controller;

    @BeforeEach
    void setUp() {
        dataService = mock(OntologyDataService.class);
        controller = new OntologyDataController(mock(OntologyService.class), dataService);
    }

    @Test
    @DisplayName("列表：分页 5 键 + 记录 9 键；缺 recordKey/payload 的条件键省略")
    void listKeepsPageAndRecordKeys() throws Exception {
        when(dataService.countRecords(isNull())).thenReturn(3);
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(row("d-1", "ot-1", "Order", "K-1", "ACTIVE",
                "{\"properties\":{\"amount\":10},\"createdBy\":\"u1\",\"createdAt\":\"2026-11-01T00:00:00\"}",
                Timestamp.valueOf("2026-11-01 00:00:00"), Timestamp.valueOf("2026-11-02 00:00:00")));
        rows.add(row("d-2", "ot-1", "Order", null, "ACTIVE", null, null, null));
        when(dataService.listRecords(isNull(), anyInt(), anyInt())).thenReturn(rows);

        JsonNode data = dataOf(controller.listData(null, null, 2, 2));

        assertEquals(List.of("data", "total", "page", "size", "totalPages"), fieldNames(data));
        assertEquals(3, data.get("total").asInt());
        assertEquals(2, data.get("page").asInt());
        assertEquals(2, data.get("size").asInt());
        assertEquals(2, data.get("totalPages").asInt());

        JsonNode first = data.get("data").get(0);
        assertEquals(List.of("id", "objectTypeId", "objectTypeName", "recordKey", "status",
                "properties", "createdBy", "createdAt", "updatedAt"), fieldNames(first));
        assertEquals(10, first.get("properties").get("amount").asInt());

        JsonNode second = data.get("data").get(1);
        assertEquals(List.of("id", "objectTypeId", "objectTypeName", "status", "properties"),
                fieldNames(second));
        assertFalse(second.has("recordKey"), "无 recordKey 时条件键省略");
        assertFalse(second.has("createdBy"));
        assertEquals(0, second.get("properties").size());
    }

    @Test
    @DisplayName("创建回执：status 尾键、objectTypeName 回退 objectTypeId、id 为全量 UUID")
    void createKeepsReceiptKeys() throws Exception {
        when(dataService.insertRecord(anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString())).thenReturn(1);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("objectTypeId", "ot-1");
        body.put("properties", Map.of("amount", 10));

        JsonNode data = dataOf(controller.createData(body));

        assertEquals(List.of("id", "objectTypeId", "objectTypeName", "properties", "createdBy",
                "createdAt", "status"), fieldNames(data));
        assertEquals("ot-1", data.get("objectTypeName").asText(), "缺省回退 objectTypeId");
        assertEquals("ACTIVE", data.get("status").asText());
        assertTrue(data.get("id").asText().matches(
                "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"),
                "MC01 全量 UUID");
    }

    @Test
    @DisplayName("更新回执：updatedAt 尾键（无 recordKey/status），properties 覆盖生效")
    void updateKeepsReceiptKeys() throws Exception {
        Map<String, Object> existing = row("d-1", "ot-1", "Order", "K-1", "ACTIVE",
                "{\"properties\":{\"amount\":10},\"createdBy\":\"u1\",\"createdAt\":\"2026-11-01T00:00:00\"}",
                null, null);
        when(dataService.findRecordById("d-1")).thenReturn(List.of(existing));
        when(dataService.updateRecord(anyString(), anyString(), anyString(), anyString())).thenReturn(1);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("properties", Map.of("amount", 12));

        JsonNode data = dataOf(controller.updateData("d-1", body));

        assertEquals(List.of("id", "objectTypeId", "objectTypeName", "properties", "createdBy",
                "createdAt", "updatedAt"), fieldNames(data));
        assertEquals(12, data.get("properties").get("amount").asInt());
        assertEquals("u1", data.get("createdBy").asText());
    }

    // ── 辅助 ─────────────────────────────────────────────

    private static Map<String, Object> row(String id, String ontologyId, String objectType,
                                           String recordKey, String status, String payload,
                                           Timestamp createTime, Timestamp updateTime) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("ontology_id", ontologyId);
        row.put("object_type", objectType);
        row.put("record_key", recordKey);
        row.put("status", status);
        row.put("payload", payload);
        row.put("create_time", createTime);
        row.put("update_time", updateTime);
        return row;
    }

    private static JsonNode dataOf(ApiResponse<?> resp) throws Exception {
        assertEquals(0, resp.getCode(), "夹具必须走成功信封: " + resp.getMessage());
        return MAPPER.valueToTree(resp).get("data");
    }

    private static List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }
}
