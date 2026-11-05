package com.chinacreator.gzcm.sysman.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.sysman.dict.entity.DictColumn;
import com.chinacreator.gzcm.sysman.dict.entity.DictTable;
import com.chinacreator.gzcm.sysman.dict.service.IDictTableService;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 数据字典表/字段 wire 键面保真门（#147BY · DoD DI-2 第四批）。
 *
 * <p><b>钉的是什么</b>：本批把 {@code tableToMap}/{@code columnToMap} 与请求体 Map 全部强类型化。
 * 三类响应的键面**各不相同**，必须分别钉死（含键序）：
 * <ul>
 *   <li>列表/详情＝表 15 键 + {@code columns} 在尾（16）；</li>
 *   <li>建表/改表＝表 15 键，<b>没有</b> {@code columns}（旧 Map 即无此键 —— 用一个带可空
 *       columns 的 VO 会凭空多一个 {@code "columns": null}，本门专门防这个假新增）；</li>
 *   <li>字段 14 键；删除/重排回执各 2 键。</li>
 * </ul>
 * 另钉 {@code createdAt/updatedAt} 仍是 ISO 字符串、{@code tags} 仍是数组、请求体缺省字段
 * 「不落值」（不得被强转成 0/false 覆盖实体默认）。</p>
 *
 * <p><b>变异自证</b>：给 {@link DictTableController.DictColumnVO#name()} 加
 * {@code @JsonProperty("column_name")} ⇒ 键集断言红；把建表响应改成带 columns 的 VO ⇒
 * {@code createTableHasNoColumnsKey} 红。</p>
 */
class DictTableWireShapeTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final List<String> TABLE_KEYS = List.of("id", "code", "name", "nameZh", "schema",
            "description", "status", "source", "rowCount", "storageSize", "owner", "tags",
            "createdBy", "createdAt", "updatedAt");
    private static final List<String> COLUMN_KEYS = List.of("id", "tableId", "name", "type", "length",
            "precision", "scale", "nullable", "primaryKey", "defaultValue", "description", "sortOrder",
            "createdAt", "updatedAt");

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return MAPPER.convertValue(value, Map.class);
    }

    private static List<String> keysOf(Object value) {
        return new ArrayList<>(asMap(value).keySet());
    }

    private static DictTable table() {
        DictTable t = new DictTable();
        t.setId("t-1");
        t.setCode("ds_order");
        t.setName("order");
        t.setNameZh("订单");
        t.setSchema("public");
        t.setDescription("desc");
        t.setStatus("active");
        t.setSource("manual");
        t.setRowCount(42L);
        t.setStorageSize("1 MB");
        t.setOwner("alice");
        t.setTags("a,b");
        t.setCreatedBy("alice");
        t.setCreatedAt(LocalDateTime.of(2026, 1, 2, 3, 4, 5));
        t.setUpdatedAt(LocalDateTime.of(2026, 1, 2, 3, 4, 6));
        return t;
    }

    private static DictColumn column() {
        DictColumn c = new DictColumn();
        c.setId("c-1");
        c.setTableId("t-1");
        c.setName("id");
        c.setType("bigint");
        c.setLength(20);
        c.setPrimaryKey(true);
        c.setSortOrder(1);
        c.setCreatedAt(LocalDateTime.of(2026, 1, 2, 3, 4, 5));
        c.setUpdatedAt(LocalDateTime.of(2026, 1, 2, 3, 4, 6));
        return c;
    }

    private static DictTableController controller(IDictTableService service) {
        DictTableController controller = new DictTableController();
        try {
            java.lang.reflect.Field f = DictTableController.class.getDeclaredField("dictTableService");
            f.setAccessible(true);
            f.set(controller, service);
            return controller;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    @DisplayName("列表/详情键面 = 表 15 键 + columns（键序钉死）；字段 14 键")
    void listAndDetailKeepAllKeysInOrder() {
        IDictTableService service = mock(IDictTableService.class);
        when(service.listTables(any(), any(), any())).thenReturn(List.of(table()));
        when(service.listColumns("t-1")).thenReturn(List.of(column()));
        when(service.getTable("t-1")).thenReturn(table());
        DictTableController controller = controller(service);

        Map<String, Object> row = asMap(controller.listTables(null, null, null).getData().get(0));
        List<String> expected = new ArrayList<>(TABLE_KEYS);
        expected.add("columns");
        assertEquals(expected, new ArrayList<>(row.keySet()), "键集与键序都必须与旧 Map 一致");
        List<?> cols = (List<?>) row.get("columns");
        assertEquals(COLUMN_KEYS, keysOf(cols.get(0)));
        assertEquals(List.of("a", "b"), row.get("tags"), "tags 仍是逗号串拆出的数组");
        assertEquals("2026-01-02T03:04:05", row.get("createdAt"), "createdAt 仍是 ISO 字符串");

        assertEquals(expected, keysOf(controller.getTable("t-1").getData()));
    }

    @Test
    @DisplayName("建表/改表回执 = 表 15 键，绝不出现 columns 键")
    void createTableHasNoColumnsKey() {
        IDictTableService service = mock(IDictTableService.class);
        when(service.createTable(any())).thenReturn(table());
        when(service.getTable("t-1")).thenReturn(table());
        when(service.updateTable(anyString(), any())).thenReturn(table());
        DictTableController controller = controller(service);

        List<String> createdKeys = keysOf(controller.createTable(
                new DictTableController.DictTableRequest("order", "订单", "public", "desc",
                        null, "manual", "ds_order", "alice", List.of("a", "b"))).getData());
        assertEquals(TABLE_KEYS, createdKeys, "建表回执不得多出 columns 键");

        assertEquals(TABLE_KEYS, keysOf(controller.updateTable("t-1",
                new DictTableController.DictTableRequest("order", "订单", "IGNORED", "desc",
                        "active", "manual", "ds_order", "alice", null)).getData()));

        ArgumentCaptor<DictTable> captor = ArgumentCaptor.forClass(DictTable.class);
        verify(service).updateTable(anyString(), captor.capture());
        assertNull(captor.getValue().getSchema(), "改表腿不读 schema（与旧 Map 读取口径一致）");
        assertEquals("active", captor.getValue().getStatus());
    }

    @Test
    @DisplayName("字段建/改：键面 14 键；缺省数值/布尔不落值（不覆盖实体默认）")
    void columnEndpointsKeepKeysAndDefaults() {
        IDictTableService service = mock(IDictTableService.class);
        when(service.getTable("t-1")).thenReturn(table());
        when(service.createColumn(anyString(), any())).thenReturn(column());
        when(service.updateColumn(anyString(), anyString(), any())).thenReturn(column());
        DictTableController controller = controller(service);

        DictTableController.DictColumnRequest request = new DictTableController.DictColumnRequest(
                "id", "bigint", null, null, null, null, null, null, null, null);
        assertEquals(COLUMN_KEYS, keysOf(controller.createColumn("t-1", request).getData()));
        assertEquals(COLUMN_KEYS, keysOf(controller.updateColumn("t-1", "c-1", request).getData()));

        ArgumentCaptor<DictColumn> captor = ArgumentCaptor.forClass(DictColumn.class);
        verify(service).createColumn(anyString(), captor.capture());
        assertNull(captor.getValue().getLength(), "缺省 length 不得被写成 0");
        assertTrue(!captor.getValue().isPrimaryKey(), "缺省 primaryKey 保持实体默认 false");
    }

    @Test
    @DisplayName("删除/重排回执键面各自独立（不得互相带出多余键）")
    void receiptKeySetsAreDistinct() {
        IDictTableService service = mock(IDictTableService.class);
        when(service.getTable("t-1")).thenReturn(table());
        DictTableController controller = controller(service);

        assertEquals(List.of("success", "id"), keysOf(controller.deleteTable("t-1").getData()));
        assertEquals(List.of("success", "columnId"),
                keysOf(controller.deleteColumn("t-1", "c-1").getData()));
        assertEquals(List.of("success", "reordered"),
                keysOf(controller.reorderColumns("t-1",
                        new DictTableController.ReorderRequest(List.of("c-1", "c-2"))).getData()));
    }

    @Test
    @DisplayName("请求体 JSON 键与旧 Map 读取逐一对齐（record 绑定）")
    void requestBindingAcceptsLegacyJsonKeys() throws Exception {
        DictTableController.DictTableRequest tableReq = MAPPER.readValue(
                "{\"name\":\"order\",\"nameZh\":\"订单\",\"schema\":\"public\",\"description\":\"d\","
                        + "\"status\":\"active\",\"source\":\"manual\",\"code\":\"ds_order\","
                        + "\"owner\":\"alice\",\"tags\":[\"a\",\"b\"]}",
                DictTableController.DictTableRequest.class);
        assertEquals("order", tableReq.name());
        assertEquals("public", tableReq.schema());
        assertEquals(List.of("a", "b"), tableReq.tags());

        DictTableController.DictColumnRequest colReq = MAPPER.readValue(
                "{\"name\":\"id\",\"type\":\"bigint\",\"length\":20,\"precision\":10,\"scale\":2,"
                        + "\"nullable\":true,\"primaryKey\":true,\"defaultValue\":\"0\","
                        + "\"description\":\"pk\",\"sortOrder\":1}",
                DictTableController.DictColumnRequest.class);
        assertEquals(20, colReq.length());
        assertEquals(Boolean.TRUE, colReq.primaryKey());
        assertEquals(1, colReq.sortOrder());

        DictTableController.ReorderRequest reorder = MAPPER.readValue(
                "{\"columnIds\":[\"c-1\",\"c-2\"]}", DictTableController.ReorderRequest.class);
        assertEquals(List.of("c-1", "c-2"), reorder.columnIds());
    }

    @Test
    @DisplayName("空名/空类型仍按旧口径 400（不得因 DTO 化丢校验）")
    void validationPreserved() {
        IDictTableService service = mock(IDictTableService.class);
        when(service.getTable("t-1")).thenReturn(table());
        DictTableController controller = controller(service);

        ApiResponse<?> noName = controller.createTable(new DictTableController.DictTableRequest(
                null, null, null, null, null, null, null, null, null));
        assertEquals(400, noName.getCode());

        ApiResponse<?> noType = controller.createColumn("t-1", new DictTableController.DictColumnRequest(
                "id", null, null, null, null, null, null, null, null, null));
        assertEquals(400, noType.getCode());
    }
}
