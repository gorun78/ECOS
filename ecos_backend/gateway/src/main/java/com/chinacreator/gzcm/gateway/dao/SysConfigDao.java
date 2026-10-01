package com.chinacreator.gzcm.gateway.dao;

import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

/**
 * B6b 数据访问收口（架构铁律 §2.5 / IR06）：{@code GatewaySysConfigService} 内联的
 * {@code sys_config} 裸 SQL 落点。
 *
 * <p>只搬不改：SQL 文本 / 表名 / 字段 / 参数序与 Service 层逐字节一致。
 * {@code queryConfigs(sql, params)} 的 PMO-E2 通用委托形态（调用方直传 SQL）保持签名不变，
 * 本 DAO 以同名方法承载，语义等价。</p>
 */
@Repository
public class SysConfigDao extends JdbcTemplateDaoSupport {

    /** 查询全部配置（带 group / edition 过滤，SQL 由调用方给定）。 */
    public List<Map<String, Object>> queryConfigs(String sql, Object[] params) {
        return jdbcTemplate.queryForList(sql, params);
    }

    /** 配置审计查询 */
    public List<Map<String, Object>> queryConfigAudit() {
        String sql = "SELECT config_key, config_label, config_value, " +
                     "is_consumed, consumed_by, consumed_at " +
                     "FROM sys_config ORDER BY config_group, sort_order";
        return jdbcTemplate.queryForList(sql);
    }

    /** 更新单个配置值，返回受影响行数 */
    public int updateConfigValue(String val, String configKey) {
        return jdbcTemplate.update(
                "UPDATE sys_config SET config_value=?, updated_at=NOW() WHERE config_key=?",
                val, configKey);
    }

    // ── PMO-E2 通用委托方法 ──
    public List<Map<String, Object>> queryForList(String sql) {
        return jdbcTemplate.queryForList(sql);
    }
    public List<Map<String, Object>> queryForList(String sql, Object[] params) {
        return jdbcTemplate.queryForList(sql, params);
    }
    public int update(String sql, Object... args) {
        return jdbcTemplate.update(sql, args);
    }
}
