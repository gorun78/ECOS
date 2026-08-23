package com.chinacreator.gzcm.sysman.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * SecurityProfileQueryService — SecurityProfile 查询服务（PMO-E2: 从 SecurityProfileController 下沉的 JdbcTemplate 访问层）。
 */
@Service
public class SecurityProfileQueryService {

    private final JdbcTemplate jdbc;

    public SecurityProfileQueryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
        return jdbc.query(sql, rowMapper, args);
    }
    public <T> List<T> query(String sql, RowMapper<T> rowMapper) {
        return jdbc.query(sql, rowMapper);
    }
    public int update(String sql, Object... args) {
        return jdbc.update(sql, args);
    }
}
