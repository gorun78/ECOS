package com.chinacreator.gzcm.gateway.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * DqDashboardService — DqDashboard 查询服务（PMO-E2: 从 DqDashboardController 下沉的 JdbcTemplate 访问层）。
 */
@Service
public class DqDashboardService {

    private final JdbcTemplate jdbc;

    public DqDashboardService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
        return jdbc.query(sql, rowMapper, args);
    }
    public <T> List<T> query(String sql, RowMapper<T> rowMapper) {
        return jdbc.query(sql, rowMapper);
    }
    public <T> T queryForObject(String sql, Class<T> type, Object... args) {
        return jdbc.queryForObject(sql, type, args);
    }
    public int update(String sql, Object... args) {
        return jdbc.update(sql, args);
    }
}
