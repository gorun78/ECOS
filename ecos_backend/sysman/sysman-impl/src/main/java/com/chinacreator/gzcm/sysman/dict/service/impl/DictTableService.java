package com.chinacreator.gzcm.sysman.dict.service.impl;

import com.chinacreator.gzcm.sysman.dict.entity.DictColumn;
import com.chinacreator.gzcm.sysman.dict.entity.DictTable;
import com.chinacreator.gzcm.sysman.dict.service.IDictTableService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class DictTableService implements IDictTableService {

    private static final Logger log = LoggerFactory.getLogger(DictTableService.class);

    private final JdbcTemplate jdbc;

    public DictTableService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        ensureSchema();
    }

    private void ensureSchema() {
        try {
            jdbc.execute(
                "CREATE TABLE IF NOT EXISTS dict_table (" +
                "  id VARCHAR(36) PRIMARY KEY," +
                "  code VARCHAR(200)," +
                "  name VARCHAR(200) NOT NULL," +
                "  name_zh VARCHAR(200)," +
                "  schema_name VARCHAR(200)," +
                "  description TEXT," +
                "  status VARCHAR(32) DEFAULT 'DRAFT'," +
                "  source VARCHAR(100)," +
                "  row_count BIGINT," +
                "  storage_size VARCHAR(50)," +
                "  owner VARCHAR(100)," +
                "  tags TEXT," +
                "  created_by VARCHAR(100)," +
                "  created_at TIMESTAMP DEFAULT NOW()," +
                "  updated_at TIMESTAMP DEFAULT NOW())");

            jdbc.execute(
                "CREATE TABLE IF NOT EXISTS dict_column (" +
                "  id VARCHAR(36) PRIMARY KEY," +
                "  table_id VARCHAR(36) NOT NULL REFERENCES dict_table(id) ON DELETE CASCADE," +
                "  name VARCHAR(200) NOT NULL," +
                "  type VARCHAR(100) NOT NULL," +
                "  length INT," +
                "  precision_val INT," +
                "  scale INT," +
                "  nullable BOOLEAN DEFAULT true," +
                "  primary_key BOOLEAN DEFAULT false," +
                "  default_value VARCHAR(500)," +
                "  description TEXT," +
                "  sort_order INT DEFAULT 0," +
                "  created_at TIMESTAMP DEFAULT NOW()," +
                "  updated_at TIMESTAMP DEFAULT NOW())");

            jdbc.execute("CREATE INDEX IF NOT EXISTS idx_dict_column_table_id ON dict_column(table_id)");
            jdbc.execute("CREATE INDEX IF NOT EXISTS idx_dict_table_status ON dict_table(status)");
        } catch (Exception e) {
            log.error("创建 dict_table/dict_column 表失败: {}", e.getMessage());
        }
    }

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

    @Override
    public List<DictTable> listTables(String schema, String status, String search) {
        StringBuilder sql = new StringBuilder(
            "SELECT id, code, name, name_zh, schema_name, description, status, source, " +
            "row_count, storage_size, owner, tags, created_by, created_at, updated_at " +
            "FROM dict_table WHERE 1=1");
        List<Object> params = new ArrayList<>();
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
        return jdbc.query(sql.toString(), TABLE_MAPPER, params.toArray());
    }

    @Override
    public DictTable getTable(String id) {
        List<DictTable> tables = jdbc.query(
            "SELECT id, code, name, name_zh, schema_name, description, status, source, " +
            "row_count, storage_size, owner, tags, created_by, created_at, updated_at " +
            "FROM dict_table WHERE id = ?",
            TABLE_MAPPER, id);
        if (tables.isEmpty()) return null;
        DictTable table = tables.get(0);

        List<DictColumn> columns = jdbc.query(
            "SELECT id, table_id, name, type, length, precision_val, scale, nullable, primary_key, " +
            "default_value, description, sort_order, created_at, updated_at " +
            "FROM dict_column WHERE table_id = ? ORDER BY sort_order, name",
            COL_MAPPER, id);
        return table;
    }

    public DictTable getTableWithColumns(String id) {
        DictTable table = getTable(id);
        if (table == null) return null;
        List<DictColumn> columns = listColumns(id);
        return table;
    }

    @Override
    public List<DictColumn> listColumns(String tableId) {
        return jdbc.query(
            "SELECT id, table_id, name, type, length, precision_val, scale, nullable, primary_key, " +
            "default_value, description, sort_order, created_at, updated_at " +
            "FROM dict_column WHERE table_id = ? ORDER BY sort_order, name",
            COL_MAPPER, tableId);
    }

    @Override
    public DictTable createTable(DictTable table) {
        String id = UUID.randomUUID().toString().replace("-", "");
        table.setId(id);
        if (table.getStatus() == null || table.getStatus().isEmpty()) {
            table.setStatus("DRAFT");
        }

        jdbc.update(
            "INSERT INTO dict_table (id, code, name, name_zh, schema_name, description, status, source, tags, created_by, created_at, updated_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())",
            id, table.getCode(), table.getName(), table.getNameZh(),
            table.getSchema(), table.getDescription(), table.getStatus(),
            table.getSource(), table.getTags(), table.getCreatedBy());

        return getTable(id);
    }

    @Override
    public DictTable updateTable(String id, DictTable table) {
        List<String> sets = new ArrayList<>();
        List<Object> params = new ArrayList<>();

        if (table.getName() != null) { sets.add("name = ?"); params.add(table.getName()); }
        if (table.getNameZh() != null) { sets.add("name_zh = ?"); params.add(table.getNameZh()); }
        if (table.getDescription() != null) { sets.add("description = ?"); params.add(table.getDescription()); }
        if (table.getStatus() != null) { sets.add("status = ?"); params.add(table.getStatus()); }
        if (table.getSource() != null) { sets.add("source = ?"); params.add(table.getSource()); }
        if (table.getTags() != null) { sets.add("tags = ?"); params.add(table.getTags()); }
        if (table.getCode() != null) { sets.add("code = ?"); params.add(table.getCode()); }
        if (table.getSchema() != null) { sets.add("schema_name = ?"); params.add(table.getSchema()); }
        if (table.getOwner() != null) { sets.add("owner = ?"); params.add(table.getOwner()); }

        if (!sets.isEmpty()) {
            sets.add("updated_at = NOW()");
            params.add(id);
            jdbc.update("UPDATE dict_table SET " + String.join(", ", sets) + " WHERE id = ?", params.toArray());
        }
        return getTable(id);
    }

    @Override
    public void deleteTable(String id) {
        jdbc.update("DELETE FROM dict_column WHERE table_id = ?", id);
        jdbc.update("DELETE FROM dict_table WHERE id = ?", id);
    }

    @Override
    public DictColumn createColumn(String tableId, DictColumn column) {
        String id = UUID.randomUUID().toString().replace("-", "");
        column.setId(id);
        column.setTableId(tableId);

        Integer maxOrder = jdbc.queryForObject(
            "SELECT COALESCE(MAX(sort_order), 0) FROM dict_column WHERE table_id = ?",
            Integer.class, tableId);
        if (column.getSortOrder() == 0 && maxOrder != null) {
            column.setSortOrder(maxOrder + 1);
        }

        jdbc.update(
            "INSERT INTO dict_column (id, table_id, name, type, length, precision_val, scale, nullable, primary_key, default_value, description, sort_order, created_at, updated_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())",
            id, tableId, column.getName(), column.getType(),
            column.getLength(), column.getPrecision(), column.getScale(),
            column.isNullable(), column.isPrimaryKey(),
            column.getDefaultValue(), column.getDescription(), column.getSortOrder());

        return jdbc.queryForObject(
            "SELECT id, table_id, name, type, length, precision_val, scale, nullable, primary_key, " +
            "default_value, description, sort_order, created_at, updated_at " +
            "FROM dict_column WHERE id = ?",
            COL_MAPPER, id);
    }

    @Override
    public DictColumn updateColumn(String tableId, String columnId, DictColumn column) {
        List<String> sets = new ArrayList<>();
        List<Object> params = new ArrayList<>();

        if (column.getName() != null) { sets.add("name = ?"); params.add(column.getName()); }
        if (column.getType() != null) { sets.add("type = ?"); params.add(column.getType()); }
        if (column.getLength() != null) { sets.add("length = ?"); params.add(column.getLength()); }
        if (column.getPrecision() != null) { sets.add("precision_val = ?"); params.add(column.getPrecision()); }
        if (column.getScale() != null) { sets.add("scale = ?"); params.add(column.getScale()); }
        sets.add("nullable = ?");
        params.add(column.isNullable());
        sets.add("primary_key = ?");
        params.add(column.isPrimaryKey());
        if (column.getDefaultValue() != null) { sets.add("default_value = ?"); params.add(column.getDefaultValue()); }
        if (column.getDescription() != null) { sets.add("description = ?"); params.add(column.getDescription()); }
        if (column.getSortOrder() > 0) { sets.add("sort_order = ?"); params.add(column.getSortOrder()); }

        if (!sets.isEmpty()) {
            sets.add("updated_at = NOW()");
            params.add(columnId);
            params.add(tableId);
            jdbc.update("UPDATE dict_column SET " + String.join(", ", sets) + " WHERE id = ? AND table_id = ?", params.toArray());
        }

        List<DictColumn> result = jdbc.query(
            "SELECT id, table_id, name, type, length, precision_val, scale, nullable, primary_key, " +
            "default_value, description, sort_order, created_at, updated_at " +
            "FROM dict_column WHERE id = ? AND table_id = ?",
            COL_MAPPER, columnId, tableId);
        return result.isEmpty() ? null : result.get(0);
    }

    @Override
    public void deleteColumn(String tableId, String columnId) {
        jdbc.update("DELETE FROM dict_column WHERE id = ? AND table_id = ?", columnId, tableId);
    }

    @Override
    public void reorderColumns(String tableId, List<String> columnIds) {
        for (int i = 0; i < columnIds.size(); i++) {
            jdbc.update("UPDATE dict_column SET sort_order = ?, updated_at = NOW() WHERE id = ? AND table_id = ?",
                i + 1, columnIds.get(i), tableId);
        }
    }
}
