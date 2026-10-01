package com.chinacreator.gzcm.sysman.dao;

import com.chinacreator.gzcm.sysman.dict.entity.DictColumn;
import com.chinacreator.gzcm.sysman.dict.entity.DictTable;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

/**
 * 字典元数据（dict_table / dict_column）数据访问 DAO — 承接 DictTableService 中的 JdbcTemplate 操作。
 */
@Repository
public class DictTableDao extends com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport {

    private static final RowMapper<DictTable> TABLE_MAPPER = (rs, rowNum) -> {
        DictTable t = new DictTable();
        t.setId(rs.getString("id"));
        t.setCode(rs.getString("code"));
        t.setName(rs.getString("name"));
        t.setNameZh(rs.getString("name_zh"));
        t.setSchema(rs.getString("schema_name"));
        t.setDescription(rs.getString("description"));
        t.setStatus(rs.getString("status"));
        t.setSource(rs.getString("source"));
        long rc = rs.getLong("row_count");
        t.setRowCount(rs.wasNull() ? null : rc);
        t.setStorageSize(rs.getString("storage_size"));
        t.setOwner(rs.getString("owner"));
        t.setTags(rs.getString("tags"));
        t.setCreatedBy(rs.getString("created_by"));
        Timestamp ca = rs.getTimestamp("created_at");
        if (ca != null) t.setCreatedAt(ca.toLocalDateTime());
        Timestamp ua = rs.getTimestamp("updated_at");
        if (ua != null) t.setUpdatedAt(ua.toLocalDateTime());
        return t;
    };

    private static final RowMapper<DictColumn> COL_MAPPER = (rs, rowNum) -> {
        DictColumn c = new DictColumn();
        c.setId(rs.getString("id"));
        c.setTableId(rs.getString("table_id"));
        c.setName(rs.getString("name"));
        c.setType(rs.getString("type"));
        int len = rs.getInt("length");
        c.setLength(rs.wasNull() ? null : len);
        int prec = rs.getInt("precision_val");
        c.setPrecision(rs.wasNull() ? null : prec);
        int sc = rs.getInt("scale");
        c.setScale(rs.wasNull() ? null : sc);
        c.setNullable(rs.getBoolean("nullable"));
        c.setPrimaryKey(rs.getBoolean("primary_key"));
        c.setDefaultValue(rs.getString("default_value"));
        c.setDescription(rs.getString("description"));
        c.setSortOrder(rs.getInt("sort_order"));
        Timestamp ca = rs.getTimestamp("created_at");
        if (ca != null) c.setCreatedAt(ca.toLocalDateTime());
        Timestamp ua = rs.getTimestamp("updated_at");
        if (ua != null) c.setUpdatedAt(ua.toLocalDateTime());
        return c;
    };

    // ── dict_table ──────────────────────────────────

    public List<DictTable> listTables(String schema, String status, String search) {
        StringBuilder sql = new StringBuilder(
            "SELECT id, code, name, name_zh, schema_name, description, status, source, " +
            "row_count, storage_size, owner, tags, created_by, created_at, updated_at " +
            "FROM dict_table WHERE 1=1");
        java.util.List<Object> params = new java.util.ArrayList<>();
        if (schema != null && !schema.isEmpty()) {
            sql.append(" AND schema_name = ?");
            params.add(schema);
        }
        if (status != null && !status.isEmpty()) {
            sql.append(" AND status = ?");
            params.add(status);
        }
        if (search != null && !search.isEmpty()) {
            sql.append(" AND (name ILIKE ? OR name_zh ILIKE ? OR code ILIKE ?)");
            String pattern = "%" + search + "%";
            params.add(pattern);
            params.add(pattern);
            params.add(pattern);
        }
        sql.append(" ORDER BY name");
        return jdbcTemplate.query(sql.toString(), TABLE_MAPPER, params.toArray());
    }

    public DictTable getTable(String id) {
        List<DictTable> tables = jdbcTemplate.query(
            "SELECT id, code, name, name_zh, schema_name, description, status, source, " +
            "row_count, storage_size, owner, tags, created_by, created_at, updated_at " +
            "FROM dict_table WHERE id = ?",
            TABLE_MAPPER, id);
        if (tables.isEmpty()) return null;
        return tables.get(0);
    }

    public void insertTable(String id, DictTable table) {
        jdbcTemplate.update(
            "INSERT INTO dict_table (id, code, name, name_zh, schema_name, description, status, source, tags, created_by, created_at, updated_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())",
            id, table.getCode(), table.getName(), table.getNameZh(),
            table.getSchema(), table.getDescription(), table.getStatus(),
            table.getSource(), table.getTags(), table.getCreatedBy());
    }

    public void updateTable(String id, java.util.List<String> sets, java.util.List<Object> params) {
        jdbcTemplate.update("UPDATE dict_table SET " + String.join(", ", sets) + " WHERE id = ?", params.toArray());
    }

    public void deleteTable(String id) {
        jdbcTemplate.update("DELETE FROM dict_column WHERE table_id = ?", id);
        jdbcTemplate.update("DELETE FROM dict_table WHERE id = ?", id);
    }

    // ── dict_column ─────────────────────────────────

    public List<DictColumn> listColumns(String tableId) {
        return jdbcTemplate.query(
            "SELECT id, table_id, name, type, length, precision_val, scale, nullable, primary_key, " +
            "default_value, description, sort_order, created_at, updated_at " +
            "FROM dict_column WHERE table_id = ? ORDER BY sort_order, name",
            COL_MAPPER, tableId);
    }

    public Integer maxColumnSortOrder(String tableId) {
        return jdbcTemplate.queryForObject(
            "SELECT COALESCE(MAX(sort_order), 0) FROM dict_column WHERE table_id = ?",
            Integer.class, tableId);
    }

    public void insertColumn(DictColumn column) {
        jdbcTemplate.update(
            "INSERT INTO dict_column (id, table_id, name, type, length, precision_val, scale, nullable, primary_key, default_value, description, sort_order, created_at, updated_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())",
            column.getId(), column.getTableId(), column.getName(), column.getType(),
            column.getLength(), column.getPrecision(), column.getScale(),
            column.isNullable(), column.isPrimaryKey(),
            column.getDefaultValue(), column.getDescription(), column.getSortOrder());
    }

    public DictColumn getColumnById(String id) {
        return jdbcTemplate.queryForObject(
            "SELECT id, table_id, name, type, length, precision_val, scale, nullable, primary_key, " +
            "default_value, description, sort_order, created_at, updated_at " +
            "FROM dict_column WHERE id = ?",
            COL_MAPPER, id);
    }

    public void updateColumn(java.util.List<String> sets, java.util.List<Object> params) {
        jdbcTemplate.update("UPDATE dict_column SET " + String.join(", ", sets) + " WHERE id = ? AND table_id = ?", params.toArray());
    }

    public DictColumn getColumnByIdAndTable(String columnId, String tableId) {
        List<DictColumn> result = jdbcTemplate.query(
            "SELECT id, table_id, name, type, length, precision_val, scale, nullable, primary_key, " +
            "default_value, description, sort_order, created_at, updated_at " +
            "FROM dict_column WHERE id = ? AND table_id = ?",
            COL_MAPPER, columnId, tableId);
        return result.isEmpty() ? null : result.get(0);
    }

    public void deleteColumn(String tableId, String columnId) {
        jdbcTemplate.update("DELETE FROM dict_column WHERE id = ? AND table_id = ?", columnId, tableId);
    }

    public void setColumnOrder(int sortOrder, String columnId, String tableId) {
        jdbcTemplate.update("UPDATE dict_column SET sort_order = ?, updated_at = NOW() WHERE id = ? AND table_id = ?",
            sortOrder, columnId, tableId);
    }
}
