package com.chinacreator.gzcm.engine.data.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Schema 变更检测 Service 层。
 * <p>
 * 查询 schema_changes 表，支持按 acknowledged 过滤和确认操作。
 * </p>
 */
@Service
public class SchemaChangeService {

    private final JdbcTemplate jdbc;

    public SchemaChangeService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 查询 schema 变更列表。默认只返回未确认的变更。
     *
     * @param acknowledged 是否已确认
     */
    public List<Map<String, Object>> listChanges(boolean acknowledged) {
        String sql = """
            SELECT id, datasource_id, table_name, change_type,
                   detail_json, detected_at, acknowledged
            FROM schema_changes
            WHERE acknowledged = ?
            ORDER BY detected_at DESC
            """;

        return jdbc.queryForList(sql, acknowledged);
    }

    /**
     * 确认一条 schema 变更记录。
     *
     * @param id 变更记录 ID
     * @return 受影响行数
     */
    public int acknowledge(Long id) {
        return jdbc.update(
            "UPDATE schema_changes SET acknowledged = TRUE WHERE id = ? AND acknowledged = FALSE",
            id
        );
    }
}
