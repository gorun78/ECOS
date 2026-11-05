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

/**
 * 数据字典「表/字段」REST API（{@code /api/v1/dict}）。
 *
 * <p><b>#147BY（DoD DI-2 第四批）</b>：出入参强类型化——请求体由弱类型 Map 改
 * {@link DictTableRequest}/{@link DictColumnRequest}/{@link ReorderRequest}（record），
 * 响应由 {@code tableToMap}/{@code columnToMap} 改 {@link DictTableVO}/{@link DictColumnVO}。
 * <b>wire 键面逐字不变</b>（含键序）：列表/详情＝表 15 键 + {@code columns} 追加在尾；
 * 建表/改表＝表 15 键（旧 Map 本来就<b>没有</b> columns 键，故另立 {@link DictTableBaseVO}，
 * 禁用一个带可空 columns 的 VO 造出多余键）；字段 14 键；{@code createdAt/updatedAt} 仍是
 * ISO 字符串（旧 Map 用 {@code LocalDateTime#toString()}），{@code tags} 仍是逗号串拆成的数组。
 * 绑定面等价：缺省字段保持「未设值」而非被强转成空串。</p>
 */
@RestController
@RequestMapping("/api/v1/dict")
public class DictTableController {

    private static final Logger log = LoggerFactory.getLogger(DictTableController.class);

    @Autowired
    private IDictTableService dictTableService;

    @GetMapping("/tables")
    public ApiResponse<List<DictTableVO>> listTables(
            @RequestParam(required = false) String schema,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search) {
        try {
            List<DictTableVO> result = new ArrayList<>();
            for (DictTable t : dictTableService.listTables(schema, status, search)) {
                result.add(toTableVO(t, dictTableService.listColumns(t.getId()).stream()
                        .map(DictTableController::toColumnVO).toList()));
            }
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("查询数据字典表失败", e);
            return ApiResponse.internalError("查询失败");
        }
    }

    @GetMapping("/tables/{id}")
    public ApiResponse<DictTableVO> getTable(@PathVariable String id) {
        try {
            DictTable table = dictTableService.getTable(id);
            if (table == null) {
                return ApiResponse.notFound("数据表不存在: " + id);
            }
            return ApiResponse.success(toTableVO(table, dictTableService.listColumns(id).stream()
                    .map(DictTableController::toColumnVO).toList()));
        } catch (Exception e) {
            log.error("查询数据字典表详情失败: id={}", id, e);
            return ApiResponse.internalError("查询失败");
        }
    }

    @PostMapping("/tables")
    public ApiResponse<DictTableBaseVO> createTable(@RequestBody DictTableRequest body) {
        try {
            DictTable table = new DictTable();
            table.setName(body.name());
            table.setNameZh(body.nameZh());
            table.setSchema(body.schema());
            table.setDescription(body.description());
            table.setSource(body.source());
            table.setCode(body.code());
            table.setOwner(body.owner());
            if (body.tags() != null) {
                table.setTags(String.join(",", body.tags()));
            }

            if (table.getName() == null || table.getName().isEmpty()) {
                return ApiResponse.badRequest("name 不能为空");
            }

            return ApiResponse.success(toTableBaseVO(dictTableService.createTable(table)));
        } catch (Exception e) {
            log.error("创建数据字典表失败", e);
            return ApiResponse.internalError("创建失败");
        }
    }

    @PutMapping("/tables/{id}")
    public ApiResponse<DictTableBaseVO> updateTable(
            @PathVariable String id, @RequestBody DictTableRequest body) {
        try {
            if (dictTableService.getTable(id) == null) {
                return ApiResponse.notFound("数据表不存在: " + id);
            }

            DictTable table = new DictTable();
            table.setName(body.name());
            table.setNameZh(body.nameZh());
            table.setDescription(body.description());
            table.setStatus(body.status());
            table.setSource(body.source());
            table.setCode(body.code());
            table.setOwner(body.owner());
            if (body.tags() != null) {
                table.setTags(String.join(",", body.tags()));
            }

            return ApiResponse.success(toTableBaseVO(dictTableService.updateTable(id, table)));
        } catch (Exception e) {
            log.error("更新数据字典表失败: id={}", id, e);
            return ApiResponse.internalError("更新失败");
        }
    }

    @DeleteMapping("/tables/{id}")
    public ApiResponse<DeleteTableResultVO> deleteTable(@PathVariable String id) {
        try {
            if (dictTableService.getTable(id) == null) {
                return ApiResponse.notFound("数据表不存在: " + id);
            }
            dictTableService.deleteTable(id);
            return ApiResponse.success(new DeleteTableResultVO(true, id));
        } catch (Exception e) {
            log.error("删除数据字典表失败: id={}", id, e);
            return ApiResponse.internalError("删除失败");
        }
    }

    @PostMapping("/tables/{tableId}/columns")
    public ApiResponse<DictColumnVO> createColumn(
            @PathVariable String tableId, @RequestBody DictColumnRequest body) {
        try {
            if (dictTableService.getTable(tableId) == null) {
                return ApiResponse.notFound("数据表不存在: " + tableId);
            }

            DictColumn column = toColumn(body);
            if (column.getName() == null || column.getName().isEmpty()) {
                return ApiResponse.badRequest("name 不能为空");
            }
            if (column.getType() == null || column.getType().isEmpty()) {
                return ApiResponse.badRequest("type 不能为空");
            }

            return ApiResponse.success(toColumnVO(dictTableService.createColumn(tableId, column)));
        } catch (Exception e) {
            log.error("创建字段失败: tableId={}", tableId, e);
            return ApiResponse.internalError("创建失败");
        }
    }

    @PutMapping("/tables/{tableId}/columns/{columnId}")
    public ApiResponse<DictColumnVO> updateColumn(
            @PathVariable String tableId, @PathVariable String columnId,
            @RequestBody DictColumnRequest body) {
        try {
            DictColumn updated = dictTableService.updateColumn(tableId, columnId, toColumn(body));
            if (updated == null) {
                return ApiResponse.notFound("字段不存在: " + columnId);
            }
            return ApiResponse.success(toColumnVO(updated));
        } catch (Exception e) {
            log.error("更新字段失败: tableId={}, columnId={}", tableId, columnId, e);
            return ApiResponse.internalError("更新失败");
        }
    }

    @DeleteMapping("/tables/{tableId}/columns/{columnId}")
    public ApiResponse<DeleteColumnResultVO> deleteColumn(
            @PathVariable String tableId, @PathVariable String columnId) {
        try {
            dictTableService.deleteColumn(tableId, columnId);
            return ApiResponse.success(new DeleteColumnResultVO(true, columnId));
        } catch (Exception e) {
            log.error("删除字段失败: tableId={}, columnId={}", tableId, columnId, e);
            return ApiResponse.internalError("删除失败");
        }
    }

    @PutMapping("/tables/{tableId}/columns/reorder")
    public ApiResponse<ReorderResultVO> reorderColumns(
            @PathVariable String tableId, @RequestBody ReorderRequest body) {
        try {
            List<String> columnIds = body.columnIds();
            if (columnIds == null || columnIds.isEmpty()) {
                return ApiResponse.badRequest("columnIds 不能为空");
            }
            dictTableService.reorderColumns(tableId, columnIds);
            return ApiResponse.success(new ReorderResultVO(true, columnIds.size()));
        } catch (Exception e) {
            log.error("重排字段失败: tableId={}", tableId, e);
            return ApiResponse.internalError("重排失败");
        }
    }

    /** 字段请求 → 实体（#147BY：与旧 Map 读取逐字段等价，缺省字段不落值）。 */
    private static DictColumn toColumn(DictColumnRequest body) {
        DictColumn column = new DictColumn();
        column.setName(body.name());
        column.setType(body.type());
        if (body.length() != null) column.setLength(body.length());
        if (body.precision() != null) column.setPrecision(body.precision());
        if (body.scale() != null) column.setScale(body.scale());
        if (body.nullable() != null) column.setNullable(body.nullable());
        if (body.primaryKey() != null) column.setPrimaryKey(body.primaryKey());
        column.setDefaultValue(body.defaultValue());
        column.setDescription(body.description());
        if (body.sortOrder() != null) column.setSortOrder(body.sortOrder());
        return column;
    }

    static DictTableBaseVO toTableBaseVO(DictTable t) {
        return new DictTableBaseVO(
                t.getId(), t.getCode(), t.getName(), t.getNameZh(), t.getSchema(), t.getDescription(),
                t.getStatus(), t.getSource(), t.getRowCount(), t.getStorageSize(), t.getOwner(),
                tagsOf(t), t.getCreatedBy(), isoOf(t.getCreatedAt()), isoOf(t.getUpdatedAt()));
    }

    static DictTableVO toTableVO(DictTable t, List<DictColumnVO> columns) {
        DictTableBaseVO base = toTableBaseVO(t);
        return new DictTableVO(base.id(), base.code(), base.name(), base.nameZh(), base.schema(),
                base.description(), base.status(), base.source(), base.rowCount(), base.storageSize(),
                base.owner(), base.tags(), base.createdBy(), base.createdAt(), base.updatedAt(), columns);
    }

    static DictColumnVO toColumnVO(DictColumn c) {
        return new DictColumnVO(
                c.getId(), c.getTableId(), c.getName(), c.getType(), c.getLength(), c.getPrecision(),
                c.getScale(), c.isNullable(), c.isPrimaryKey(), c.getDefaultValue(), c.getDescription(),
                c.getSortOrder(), isoOf(c.getCreatedAt()), isoOf(c.getUpdatedAt()));
    }

    private static List<String> tagsOf(DictTable t) {
        return t.getTags() != null && !t.getTags().isEmpty()
                ? Arrays.stream(t.getTags().split(",")).filter(s -> !s.isEmpty()).toList()
                : Collections.emptyList();
    }

    private static String isoOf(java.time.LocalDateTime value) {
        return value != null ? value.toString() : null;
    }

    // ── 请求 DTO（#147BY，record：Jackson 按组件名绑定） ─────────

    /** 建表/改表请求（create 读 schema、update 读 status，与旧 Map 读取口径一致）。 */
    public record DictTableRequest(String name, String nameZh, String schema, String description,
                                   String status, String source, String code, String owner,
                                   List<String> tags) {
    }

    /** 字段建/改请求（数值与布尔字段可缺省，缺省＝不覆盖实体默认值）。 */
    public record DictColumnRequest(String name, String type, Integer length, Integer precision,
                                    Integer scale, Boolean nullable, Boolean primaryKey,
                                    String defaultValue, String description, Integer sortOrder) {
    }

    /** 字段重排请求。 */
    public record ReorderRequest(List<String> columnIds) {
    }

    // ── 响应 VO（#147BY：组件顺序＝旧 Map 的键序） ─────────────

    /** 建表/改表回执：15 键、无 {@code columns}（旧 Map 即无此键）。 */
    public record DictTableBaseVO(String id, String code, String name, String nameZh, String schema,
                                  String description, String status, String source, Long rowCount,
                                  String storageSize, String owner, List<String> tags, String createdBy,
                                  String createdAt, String updatedAt) {
    }

    /** 列表/详情：15 键 + {@code columns} 追加在尾。 */
    public record DictTableVO(String id, String code, String name, String nameZh, String schema,
                              String description, String status, String source, Long rowCount,
                              String storageSize, String owner, List<String> tags, String createdBy,
                              String createdAt, String updatedAt, List<DictColumnVO> columns) {
    }

    /** 数据字典字段（14 键）。 */
    public record DictColumnVO(String id, String tableId, String name, String type, Integer length,
                               Integer precision, Integer scale, boolean nullable, boolean primaryKey,
                               String defaultValue, String description, int sortOrder,
                               String createdAt, String updatedAt) {
    }

    /** 删表回执。 */
    public record DeleteTableResultVO(boolean success, String id) {
    }

    /** 删字段回执。 */
    public record DeleteColumnResultVO(boolean success, String columnId) {
    }

    /** 重排回执。 */
    public record ReorderResultVO(boolean success, int reordered) {
    }
}
