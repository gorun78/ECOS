package com.chinacreator.gzcm.engine.data.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Schema 变更检测 REST 端点。
 * <p>
 * 查询 schema_changes 表，支持按 acknowledged 过滤和确认操作。
 * </p>
 */
@RestController
@RequestMapping("/api/v1/engine/data/schema")
public class SchemaChangeController {

    private final JdbcTemplate jdbc;

    public SchemaChangeController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * GET /api/v1/engine/data/schema/changes?acknowledged=false
     * <p>
     * 查询 schema 变更列表。默认只返回未确认的变更。
     * </p>
     *
     * @param acknowledged 是否已确认（默认 false）
     */
    @GetMapping("/changes")
    public ApiResponse<List<Map<String, Object>>> listChanges(
            @RequestParam(defaultValue = "false") boolean acknowledged) {

        String sql = """
            SELECT id, datasource_id, table_name, change_type,
                   detail_json, detected_at, acknowledged
            FROM schema_changes
            WHERE acknowledged = ?
            ORDER BY detected_at DESC
            """;

        List<Map<String, Object>> rows = jdbc.queryForList(sql, acknowledged);
        return ApiResponse.success(rows);
    }

    /**
     * POST /api/v1/engine/data/schema/changes/{id}/acknowledge
     * <p>
     * 确认一条 schema 变更记录。
     * </p>
     *
     * @param id 变更记录 ID
     */
    @PostMapping("/changes/{id}/acknowledge")
    public ApiResponse<Map<String, Object>> acknowledge(@PathVariable Long id) {
        int updated = jdbc.update(
            "UPDATE schema_changes SET acknowledged = TRUE WHERE id = ? AND acknowledged = FALSE",
            id
        );

        if (updated == 0) {
            return ApiResponse.notFound("变更记录不存在或已确认: id=" + id);
        }

        return ApiResponse.success(Map.of(
            "id", id,
            "acknowledged", true,
            "acknowledgedAt", LocalDateTime.now().toString()
        ));
    }
}
