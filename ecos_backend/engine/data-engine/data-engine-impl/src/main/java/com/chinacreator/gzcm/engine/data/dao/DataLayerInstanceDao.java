package com.chinacreator.gzcm.engine.data.dao;

import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

/**
 * DataLayerInstanceService 的 JDBC 数据访问层。
 * <p>DW 层表列元数据解析与实例行查询走本 DAO（表/列/SQL 均由 service 侧白名单校验后组装传入）。</p>
 */
@Repository
public class DataLayerInstanceDao extends JdbcTemplateDaoSupport {

    /** 列定义（名称 + PG 目录 data_type）。 */
    public record ColumnDef(String name, String dataType) {
    }

    /** 从 PG 目录读取表列定义（列名/类型来自 DB，非用户输入）。 */
    public List<ColumnDef> loadColumns(String schema, String table) {
        return jdbcTemplate.query(
                "SELECT column_name, data_type FROM information_schema.columns "
                        + "WHERE table_schema = ? AND table_name = ? ORDER BY ordinal_position",
                (rs, rowNum) -> new ColumnDef(rs.getString("column_name"), rs.getString("data_type")),
                schema, table);
    }

    /** 查询表主键列名（无主键返回 null）。 */
    public String findPrimaryKeyColumn(String schema, String table) {
        List<String> keys = jdbcTemplate.queryForList(
                "SELECT a.attname FROM pg_index i "
                        + "JOIN pg_class c ON c.oid = i.indrelid "
                        + "JOIN pg_namespace n ON n.oid = c.relnamespace "
                        + "JOIN pg_attribute a ON a.attrelid = c.oid AND a.attnum = i.indkey[0] "
                        + "WHERE n.nspname = ? AND c.relname = ? AND i.indisprimary "
                        + "ORDER BY i.indexrelid LIMIT 1",
                String.class, schema, table);
        return keys.isEmpty() ? null : keys.get(0);
    }

    /** 执行查询返回原始行（列名→值，保持 SELECT 列序）。 */
    public List<Map<String, Object>> queryRows(String sql, Object[] params) {
        return jdbcTemplate.queryForList(sql, params);
    }
}
