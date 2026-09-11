package com.chinacreator.gzcm.sysman.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 系统配置查询服务 — 从 SysConfigController 下沉的 JdbcTemplate 访问。
 * 读写 sys_config 表。SQL 与原 Controller 保持一致。
 */
@Service
public class SysConfigQueryService {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final JdbcTemplate jdbcTemplate;

    public SysConfigQueryService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 数据源是否可用（Controller 依赖此判断以返回兜底空结果） */
    public boolean isAvailable() {
        return jdbcTemplate != null;
    }

    /**
     * 按分组/edition 列出配置项；返回的每行已将 config_options JSONB 解析为 List。
     *
     * @param group   配置分组，可为空
     * @param edition 版本过滤，可为空
     * @param currentEdition 当前版本标识（来自 Maven profile）
     */
    public List<Map<String, Object>> listByGroup(String group, String edition, String currentEdition) {
        // 构建 SQL: 包含 config_options, impact_scope, edition 字段
        StringBuilder sqlBuilder = new StringBuilder(
            "SELECT id, config_group, config_key, config_value, config_type, " +
            "config_label, config_label_en, description, sort_order, " +
            "config_options, impact_scope, edition " +
            "FROM sys_config WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (group != null && !group.isBlank()) {
            sqlBuilder.append(" AND config_group=?");
            params.add(group);
        }

        // edition 过滤: 如果指定了 edition，返回 edition='all' 或 edition=<指定的>
        String effectiveEdition = !edition.isBlank() ? edition : currentEdition;
        if (effectiveEdition != null && !effectiveEdition.isBlank() && !"all".equals(effectiveEdition)) {
            sqlBuilder.append(" AND (edition='all' OR edition=?)");
            params.add(effectiveEdition);
        }

        if (group != null && !group.isBlank()) {
            sqlBuilder.append(" ORDER BY sort_order");
        } else {
            sqlBuilder.append(" ORDER BY config_group, sort_order");
        }

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                sqlBuilder.toString(), params.toArray());

        // Post-process: parse config_options JSONB → List
        for (Map<String, Object> row : rows) {
            Object opts = row.get("config_options");
            if (opts instanceof String && !((String) opts).isBlank()) {
                try {
                    List<String> parsed = objectMapper.readValue(
                            (String) opts, new TypeReference<List<String>>() {});
                    row.put("config_options", parsed);
                } catch (Exception e) {
                    row.put("config_options", List.of());
                }
            } else if (opts == null) {
                row.put("config_options", List.of());
            }
        }

        return rows;
    }

    /** 更新单个配置值；返回受影响行数 */
    public int updateConfigValue(String configKey, String val) {
        return jdbcTemplate.update(
                "UPDATE sys_config SET config_value=?, updated_at=NOW() WHERE config_key=?",
                val, configKey);
    }

    /** 查询配置元数据（带定义、影响范围） */
    public List<Map<String, Object>> queryMetadata() {
        String sql = "SELECT id, config_group, config_key, config_value, config_type, " +
                     "config_label, config_label_en, description, impact_scope, edition " +
                     "FROM sys_config ORDER BY config_group, sort_order";
        return jdbcTemplate.queryForList(sql);
    }

    /** 查询用于消费审计的配置项基本信息 */
    public List<Map<String, Object>> queryForAudit() {
        String sql = "SELECT config_key, config_group, config_label, description FROM sys_config ORDER BY config_group, sort_order";
        return jdbcTemplate.queryForList(sql);
    }

    // ── PMO-E2 通用委托方法（供 Controller 调用）──
    public List<Map<String, Object>> queryForList(String sql, Object... args) {
        return jdbcTemplate.queryForList(sql, args);
    }
    public int update(String sql, Object... args) {
        return jdbcTemplate.update(sql, args);
    }
}
