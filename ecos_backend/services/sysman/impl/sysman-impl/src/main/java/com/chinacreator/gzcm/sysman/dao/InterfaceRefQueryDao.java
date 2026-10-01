package com.chinacreator.gzcm.sysman.dao;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 接口引用查询 DAO — 承接 InterfaceRefQueryService 中的 JdbcTemplate 操作（铁律 §2.5）。
 */
@Repository
public class InterfaceRefQueryDao extends com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport {

    private static final String TABLE = "ecos_interface_ref";

    private static final String COLUMNS = "id, name, interface_type, endpoint, method, timeout_ms, "
            + "create_time, update_time, create_by";

    /** 行映射：列名 → 前端驼峰键（顺序与下沉前一致）。 */
    private final RowMapper<Map<String, Object>> ROW_MAPPER = (rs, rowNum) -> {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", rs.getString("id"));
        row.put("name", rs.getString("name"));
        row.put("interfaceType", rs.getString("interface_type"));
        row.put("endpoint", rs.getString("endpoint"));
        row.put("method", rs.getString("method"));
        row.put("timeoutMs", rs.getInt("timeout_ms"));
        Timestamp ct = rs.getTimestamp("create_time");
        row.put("createTime", ct != null ? ct.getTime() : null);
        Timestamp ut = rs.getTimestamp("update_time");
        row.put("updateTime", ut != null ? ut.getTime() : null);
        row.put("createBy", rs.getString("create_by"));
        return row;
    };

    public <T> List<T> query(String sql, RowMapper<T> rowMapper) {
        return jdbcTemplate.query(sql, rowMapper);
    }

    public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
        return jdbcTemplate.query(sql, rowMapper, args);
    }

    public <T> T queryForObject(String sql, Class<T> type, Object... args) {
        return jdbcTemplate.queryForObject(sql, type, args);
    }

    public int update(String sql, Object... args) {
        return jdbcTemplate.update(sql, args);
    }

    public List<Map<String, Object>> listAll() {
        String sql = "SELECT " + COLUMNS + " FROM " + TABLE
                + " WHERE is_deleted = 0 ORDER BY id";
        return jdbcTemplate.query(sql, ROW_MAPPER);
    }

    public Map<String, Object> findById(String id) {
        String sql = "SELECT " + COLUMNS + " FROM " + TABLE + " WHERE id = ? AND is_deleted = 0";
        List<Map<String, Object>> rows = jdbcTemplate.query(sql, ROW_MAPPER, id);
        return rows.isEmpty() ? null : rows.get(0);
    }
}
