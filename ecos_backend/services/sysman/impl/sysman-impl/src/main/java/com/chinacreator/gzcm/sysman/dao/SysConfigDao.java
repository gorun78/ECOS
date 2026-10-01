package com.chinacreator.gzcm.sysman.dao;

import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

/**
 * 系统配置（sys_config）数据访问 DAO — 承接 SysConfigService / SysConfigQueryService 中的 JdbcTemplate 操作。
 */
@Repository
public class SysConfigDao extends com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport {

    // ── SysConfigService 用 ────────────────────────────────

    /** 启动时初始化默认配置（Ensure DDL-free: 表由 db/migration 建好）。 */
    public void ensureDefaultConfigs(String[][] defaults) {
        for (String[] row : defaults) {
            String label = row[0].contains(".") ? row[0].substring(row[0].lastIndexOf('.') + 1) : row[0];
            jdbcTemplate.update(
                "INSERT INTO sys_config (id, config_key, config_value, config_group, config_type, config_label, description, sort_order, status, edition) " +
                "VALUES (gen_random_uuid(), ?, ?, ?, ?, ?, ?, 100, 'active', 'all') " +
                "ON CONFLICT (config_key) DO UPDATE SET description = EXCLUDED.description",
                row[0], row[1], row[2], row[3], label, row[4]
            );
        }
    }

    /** 全量加载 active 配置（refreshCache 用）。 */
    public List<Map<String, Object>> listConfigs() {
        return jdbcTemplate.queryForList(
            "SELECT config_key, config_value FROM sys_config WHERE status = 'active'"
        );
    }

    /** 按分组列出 active 配置，未组按 config_group 排序。 */
    public List<Map<String, Object>> listByGroup(String group) {
        String sql;
        Object[] args;
        if (group != null && !group.isEmpty()) {
            sql = "SELECT * FROM sys_config WHERE config_group = ? AND status = 'active' ORDER BY sort_order";
            args = new Object[]{group};
        } else {
            sql = "SELECT * FROM sys_config WHERE status = 'active' ORDER BY config_group, sort_order";
            args = new Object[]{};
        }
        return jdbcTemplate.queryForList(sql, args);
    }

    /** 按 key 查单个配置行，未命中返回 null。 */
    public Map<String, Object> getByKey(String key) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT * FROM sys_config WHERE config_key = ? AND status = 'active'", key
        );
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** 更新配置值；返回受影响行数。 */
    public int updateValue(String key, String value) {
        return jdbcTemplate.update(
            "UPDATE sys_config SET config_value = ?, updated_at = NOW() WHERE config_key = ?",
            value, key
        );
    }

    /** 统计指定 key 的行数。 */
    public int countByKey(String key) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM sys_config WHERE config_key = ?", Integer.class, key);
    }

    /** 插入新配置行（key 已存在则需调用方先判断）。 */
    public int insertConfig(String key, String value, String group, String type, String label, String description) {
        return jdbcTemplate.update(
            "INSERT INTO sys_config (id, config_key, config_value, config_group, config_type, config_label, description, sort_order, status, edition) " +
            "VALUES (gen_random_uuid(), ?, ?, ?, ?, ?, ?, 100, 'active', 'all')",
            key, value, group, type, label, description
        );
    }

    // ── 通用委托方法（供 SysConfigQueryService / Controller 调用）──
    public List<Map<String, Object>> queryForList(String sql, Object... args) {
        return jdbcTemplate.queryForList(sql, args);
    }
    public List<Map<String, Object>> queryForList(String sql) {
        return jdbcTemplate.queryForList(sql);
    }
    public int update(String sql, Object... args) {
        return jdbcTemplate.update(sql, args);
    }
}
