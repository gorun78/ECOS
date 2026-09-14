package com.chinacreator.gzcm.services.datanet.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * GitQueryService — Git 查询服务（P3-A 从 gateway 迁至 datanet。PMO-E2 下沉的 JdbcTemplate 访问层）。
 *
 * @author ecos-factory
 * @since PMO-49 P3-A
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
