package com.chinacreator.gzcm.sysman.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.sysman.dict.entity.DictColumn;
import com.chinacreator.gzcm.sysman.dict.entity.DictTable;
import com.chinacreator.gzcm.sysman.dict.service.IDictTableService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping({"/api/dict", "/api/v1/dict"})
public class DictTableController {

    private static final Logger log = LoggerFactory.getLogger(DictTableController.class);

    @Autowired
    private IDictTableService dictTableService;

    @GetMapping("/tables")
    public ApiResponse<List<DictTable>> listTables(
            @RequestParam(required = false) String schema,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search) {
        try {
            List<DictTable> tables = dictTableService.listTables(schema, status, search);
            return ApiResponse.success(tables);
        } catch (Exception e) {
            log.error("查询数据字典表失败", e);
            return ApiResponse.internalError("查询失败: " + e.getMessage());
        }
    }

    @GetMapping("/tables/{id}")
    public ApiResponse<Map<String, Object>> getTable(@PathVariable String id) {
        try {
            DictTable table = dictTableService.getTable(id);
            if (table == null) {
                return ApiResponse.notFound("数据表不存在: " + id);
            }
            List<DictColumn> columns = dictTableService.listColumns(id);

            Map<String, Object> result = tableToMap(table);
            result.put("columns", columns.stream().map(this::columnToMap).toList());
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("查询数据字典表详情失败: id={}", id, e);
            return ApiResponse.internalError("查询失败: " + e.getMessage());
        }
    }

    @PostMapping("/tables")
    public ApiResponse<Map<String, Object>> createTable(@RequestBody Map<String, Object> body) {
        try {
            DictTable table = new DictTable();
            table.setName((String) body.get("name"));
            table.setNameZh((String) body.get("nameZh"));
            table.setSchema((String) body.get("schema"));
            table.setDescription((String) body.get("description"));
            table.setSource((String) body.get("source"));
            table.setCode((String) body.get("code"));
            table.setOwner((String) body.get("owner"));
            if (body.get("tags") instanceof List<?> tagList) {
                table.setTags(String.join(",", tagList.stream().map(Object::toString).toList()));
            }

            if (table.getName() == null || table.getName().isEmpty()) {
                return ApiResponse.badRequest("name 不能为空");
            }

            DictTable created = dictTableService.createTable(table);
            return ApiResponse.success(tableToMap(created));
        } catch (Exception e) {
            log.error("创建数据字典表失败", e);
            return ApiResponse.internalError("创建失败: " + e.getMessage());
        }
    }

    @PutMapping("/tables/{id}")
    public ApiResponse<Map<String, Object>> updateTable(
            @PathVariable String id, @RequestBody Map<String, Object> body) {
        try {
            DictTable existing = dictTableService.getTable(id);
            if (existing == null) {
                return ApiResponse.notFound("数据表不存在: " + id);
            }

            DictTable table = new DictTable();
            table.setName((String) body.get("name"));
            table.setNameZh((String) body.get("nameZh"));
            table.setDescription((String) body.get("description"));
            table.setStatus((String) body.get("status"));
            table.setSource((String) body.get("source"));
            table.setCode((String) body.get("code"));
            table.setOwner((String) body.get("owner"));
            if (body.get("tags") instanceof List<?> tagList) {
                table.setTags(String.join(",", tagList.stream().map(Object::toString).toList()));
            }

            DictTable updated = dictTableService.updateTable(id, table);
            return ApiResponse.success(tableToMap(updated));
        } catch (Exception e) {
            log.error("更新数据字典表失败: id={}", id, e);
            return ApiResponse.internalError("更新失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/tables/{id}")
    public ApiResponse<Map<String, Object>> deleteTable(@PathVariable String id) {
        try {
            DictTable existing = dictTableService.getTable(id);
            if (existing == null) {
                return ApiResponse.notFound("数据表不存在: " + id);
            }
            dictTableService.deleteTable(id);
            return ApiResponse.success(Map.of("success", true, "id", id));
        } catch (Exception e) {
            log.error("删除数据字典表失败: id={}", id, e);
            return ApiResponse.internalError("删除失败: " + e.getMessage());
        }
    }

    @PostMapping("/tables/{tableId}/columns")
    public ApiResponse<Map<String, Object>> createColumn(
            @PathVariable String tableId, @RequestBody Map<String, Object> body) {
        try {
            DictTable table = dictTableService.getTable(tableId);
            if (table == null) {
                return ApiResponse.notFound("数据表不存在: " + tableId);
            }

            DictColumn column = new DictColumn();
            column.setName((String) body.get("name"));
            column.setType((String) body.get("type"));
            if (body.get("length") instanceof Number n) column.setLength(n.intValue());
            if (body.get("precision") instanceof Number n) column.setPrecision(n.intValue());
            if (body.get("scale") instanceof Number n) column.setScale(n.intValue());
            if (body.get("nullable") instanceof Boolean b) column.setNullable(b);
            if (body.get("primaryKey") instanceof Boolean b) column.setPrimaryKey(b);
            column.setDefaultValue((String) body.get("defaultValue"));
            column.setDescription((String) body.get("description"));
            if (body.get("sortOrder") instanceof Number n) column.setSortOrder(n.intValue());

            if (column.getName() == null || column.getName().isEmpty()) {
                return ApiResponse.badRequest("name 不能为空");
            }
            if (column.getType() == null || column.getType().isEmpty()) {
                return ApiResponse.badRequest("type 不能为空");
            }

            DictColumn created = dictTableService.createColumn(tableId, column);
            return ApiResponse.success(columnToMap(created));
        } catch (Exception e) {
            log.error("创建字段失败: tableId={}", tableId, e);
            return ApiResponse.internalError("创建失败: " + e.getMessage());
        }
    }

    @PutMapping("/tables/{tableId}/columns/{columnId}")
    public ApiResponse<Map<String, Object>> updateColumn(
            @PathVariable String tableId, @PathVariable String columnId,
            @RequestBody Map<String, Object> body) {
        try {
            DictColumn column = new DictColumn();
            column.setName((String) body.get("name"));
            column.setType((String) body.get("type"));
            if (body.get("length") instanceof Number n) column.setLength(n.intValue());
            if (body.get("precision") instanceof Number n) column.setPrecision(n.intValue());
            if (body.get("scale") instanceof Number n) column.setScale(n.intValue());
            if (body.get("nullable") instanceof Boolean b) column.setNullable(b);
            if (body.get("primaryKey") instanceof Boolean b) column.setPrimaryKey(b);
            column.setDefaultValue((String) body.get("defaultValue"));
            column.setDescription((String) body.get("description"));
            if (body.get("sortOrder") instanceof Number n) column.setSortOrder(n.intValue());

            DictColumn updated = dictTableService.updateColumn(tableId, columnId, column);
            if (updated == null) {
                return ApiResponse.notFound("字段不存在: " + columnId);
            }
            return ApiResponse.success(columnToMap(updated));
        } catch (Exception e) {
            log.error("更新字段失败: tableId={}, columnId={}", tableId, columnId, e);
            return ApiResponse.internalError("更新失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/tables/{tableId}/columns/{columnId}")
    public ApiResponse<Map<String, Object>> deleteColumn(
            @PathVariable String tableId, @PathVariable String columnId) {
        try {
            dictTableService.deleteColumn(tableId, columnId);
            return ApiResponse.success(Map.of("success", true, "columnId", columnId));
        } catch (Exception e) {
            log.error("删除字段失败: tableId={}, columnId={}", tableId, columnId, e);
            return ApiResponse.internalError("删除失败: " + e.getMessage());
        }
    }

    @PutMapping("/tables/{tableId}/columns/reorder")
    public ApiResponse<Map<String, Object>> reorderColumns(
            @PathVariable String tableId, @RequestBody Map<String, Object> body) {
        try {
            @SuppressWarnings("unchecked")
            List<String> columnIds = (List<String>) body.get("columnIds");
            if (columnIds == null || columnIds.isEmpty()) {
                return ApiResponse.badRequest("columnIds 不能为空");
            }
            dictTableService.reorderColumns(tableId, columnIds);
            return ApiResponse.success(Map.of("success", true, "reordered", columnIds.size()));
        } catch (Exception e) {
            log.error("重排字段失败: tableId={}", tableId, e);
            return ApiResponse.internalError("重排失败: " + e.getMessage());
        }
    }

    private Map<String, Object> tableToMap(DictTable t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", t.getId());
        m.put("code", t.getCode());
        m.put("name", t.getName());
        m.put("nameZh", t.getNameZh());
        m.put("schema", t.getSchema());
        m.put("description", t.getDescription());
        m.put("status", t.getStatus());
        m.put("source", t.getSource());
        m.put("rowCount", t.getRowCount());
        m.put("storageSize", t.getStorageSize());
        m.put("owner", t.getOwner());
        m.put("tags", t.getTags() != null ? Arrays.asList(t.getTags().split(",")) : Collections.emptyList());
        m.put("createdBy", t.getCreatedBy());
        m.put("createdAt", t.getCreatedAt() != null ? t.getCreatedAt().toString() : null);
        m.put("updatedAt", t.getUpdatedAt() != null ? t.getUpdatedAt().toString() : null);
        return m;
    }

    private Map<String, Object> columnToMap(DictColumn c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId());
        m.put("tableId", c.getTableId());
        m.put("name", c.getName());
        m.put("type", c.getType());
        m.put("length", c.getLength());
        m.put("precision", c.getPrecision());
        m.put("scale", c.getScale());
        m.put("nullable", c.isNullable());
        m.put("primaryKey", c.isPrimaryKey());
        m.put("defaultValue", c.getDefaultValue());
        m.put("description", c.getDescription());
        m.put("sortOrder", c.getSortOrder());
        m.put("createdAt", c.getCreatedAt() != null ? c.getCreatedAt().toString() : null);
        m.put("updatedAt", c.getUpdatedAt() != null ? c.getUpdatedAt().toString() : null);
        return m;
    }
}
