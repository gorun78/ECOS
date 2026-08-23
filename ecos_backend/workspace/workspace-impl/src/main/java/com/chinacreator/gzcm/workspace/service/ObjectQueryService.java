package com.chinacreator.gzcm.workspace.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * ObjectQueryService — Object 查询服务（PMO-E2: 从 ObjectController 下沉的 JdbcTemplate 访问层）。
 */
@Service
public class ObjectQueryService {

    private final JdbcTemplate jdbc;

    public ObjectQueryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Map<String, Object>> queryForList(String sql, Object... args) {
        return jdbc.queryForList(sql, args);
    }
    public List<Map<String, Object>> queryForList(String sql) {
        return jdbc.queryForList(sql);
    }
    public <T> T queryForObject(String sql, Class<T> type, Object... args) {
        return jdbc.queryForObject(sql, type, args);
    }
    public int update(String sql, Object... args) {
        return jdbc.update(sql, args);
    }
}
