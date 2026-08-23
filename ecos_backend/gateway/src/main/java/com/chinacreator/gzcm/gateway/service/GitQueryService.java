package com.chinacreator.gzcm.gateway.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * GitQueryService — Git 查询服务（PMO-E2: 从 GitController 下沉的 JdbcTemplate 访问层）。
 */
@Service
public class GitQueryService {

    private final JdbcTemplate jdbc;

    public GitQueryService(JdbcTemplate jdbc) {
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
}
