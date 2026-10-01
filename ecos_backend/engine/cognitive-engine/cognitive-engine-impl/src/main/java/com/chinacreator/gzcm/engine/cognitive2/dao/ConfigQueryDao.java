package com.chinacreator.gzcm.engine.cognitive2.dao;

import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * B2-b 数据访问收口（架构铁律 §2.5 #1 / IR06）：{@code CognitiveConfigQueryService}
 * 内联的 {@code sys_config} 读 / 写 SQL 落点（config_group='cognitive'）。
 *
 * <p>只搬不改：SQL 文本 / 表名 / 字段 / 参数序（含 INSERT 的 {@code gen_random_uuid()}
 * 服务端生成）与原 Service 逐字节一致；label 推导留在 Service 层。</p>
 */
@Repository
public class ConfigQueryDao extends JdbcTemplateDaoSupport {

    /** 拉取指定组的全部配置行（active），按 sort_order、config_key 排序。 */
    public List<Map<String, Object>> listActiveConfigRows(String group) {
        return jdbcTemplate.queryForList(
                "SELECT config_key, config_value, description, config_type, config_label " +
                "FROM sys_config WHERE config_group = ? AND status = 'active' " +
                "ORDER BY sort_order, config_key",
                group);
    }

    /** 尝试 UPDATE 配置值（命中返回行数；0 表示不存在需 INSERT）。 */
    public int updateConfigValue(String key, String value, LocalDateTime now) {
        return jdbcTemplate.update(
                "UPDATE sys_config SET config_value = ?, updated_at = ? " +
                "WHERE config_key = ? AND status = 'active'",
                value, now, key);
    }

    /** 新建配置行（id 由 PG gen_random_uuid() 生成）。 */
    public void insertConfigValue(String key, String value, String group, String label, LocalDateTime now) {
        jdbcTemplate.update(
                "INSERT INTO sys_config (id, config_key, config_value, config_group, config_type, " +
                "config_label, description, sort_order, status, edition, created_at, updated_at) " +
                "VALUES (gen_random_uuid(), ?, ?, ?, 'string', ?, '', 100, 'active', 'all', ?, ?)",
                key, value, group, label, now, now);
    }
}
