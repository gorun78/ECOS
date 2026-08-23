package com.chinacreator.gzcm.gateway.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 系统配置服务 — 从 SysConfigController 下沉的 JdbcTemplate 访问层。
 * SQL 语义与原 Controller 保持一致。
 */
@Service
public class GatewaySysConfigService {

    private final JdbcTemplate jdbc;

    public GatewaySysConfigService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 查询全部配置（带 group / edition 过滤） */
    public List<Map<String, Object>> queryConfigs(String sql, Object[] params) {
        return jdbc.queryForList(sql, params);
    }

    /** 配置审计查询 */
    public List<Map<String, Object>> queryConfigAudit() {
        String sql = "SELECT config_key, config_label, config_value, " +
                     "is_consumed, consumed_by, consumed_at " +
                     "FROM sys_config ORDER BY config_group, sort_order";
        return jdbc.queryForList(sql);
    }

    /** 更新单个配置值，返回受影响行数 */
    public int updateConfigValue(String val, String configKey) {
        return jdbc.update(
                "UPDATE sys_config SET config_value=?, updated_at=NOW() WHERE config_key=?",
                val, configKey);
    }

    // ── PMO-E2 通用委托方法 ──
    public List<Map<String, Object>> queryForList(String sql) {
        return jdbc.queryForList(sql);
    }
    public List<Map<String, Object>> queryForList(String sql, Object[] params) {
        return jdbc.queryForList(sql, params);
    }
    public int update(String sql, Object... args) {
        return jdbc.update(sql, args);
    }
}
