package com.chinacreator.gzcm.engine.data.dao;

import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

/**
 * SchemaChangeService 的 JDBC 数据访问层。
 * <p>schema_changes 表的查询与确认写操作走本 DAO。</p>
 */
@Repository
public class SchemaChangeDao extends JdbcTemplateDaoSupport {

    public List<Map<String, Object>> listChanges(boolean acknowledged) {
        String sql = """
            SELECT id, datasource_id, table_name, change_type,
                   detail_json, detected_at, acknowledged
            FROM schema_changes
            WHERE acknowledged = ?
            ORDER BY detected_at DESC
            """;
        return jdbcTemplate.queryForList(sql, acknowledged);
    }

    public int acknowledge(Long id) {
        return jdbcTemplate.update(
            "UPDATE schema_changes SET acknowledged = TRUE WHERE id = ? AND acknowledged = FALSE",
            id
        );
    }
}
