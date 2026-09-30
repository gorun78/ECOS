package com.chinacreator.gzcm.engine.cognitive2.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 认知引擎配置数据访问服务 — sys_config（config_group='cognitive'）的读 / 写查询。
 *
 * <p>供 {@code CognitiveConfigController} 调用：sys_config 的 SELECT / UPDATE / INSERT
 * 全部收敛在 Service 层（架构铁律 §3.6：Controller 不得直接访问数据库）。
 * 数据源为 sys_config 表，与前端 CognitiveConfigTab 默认 {@code cognitive.*} 前缀的
 * key 对齐；不跨模块 import sysman 包（架构铁律：引擎层不依赖服务层）。</p>
 *
 * <p>异常语义：查询 / 写入抛出的 {@code DataAccessException} 原样上浮，由调用方
 * Controller 捕获并降级返回，与既有 HTTP 行为一致。</p>
 */
@Service
public class CognitiveConfigQueryService {

    private final JdbcTemplate jdbcTemplate;

    public CognitiveConfigQueryService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 拉取指定组的全部配置行（active），按 sort_order、config_key 排序。
     *
     * @param group 配置组（如 {@code cognitive}）
     * @return 原始行列表（config_key / config_value / description / config_type / config_label）
     */
    public List<Map<String, Object>> listActiveConfigRows(String group) {
        return jdbcTemplate.queryForList(
                "SELECT config_key, config_value, description, config_type, config_label " +
                "FROM sys_config WHERE config_group = ? AND status = 'active' " +
                "ORDER BY sort_order, config_key",
                group);
    }

    /**
     * 单条 upsert：先尝试 UPDATE，命中 0 行则 INSERT。
     *
     * @param key   配置 key
     * @param value 配置值（非 null，调用方保证空值传 ""）
     * @param group 配置组
     * @return 实际 UPDATE 命中行数（命中即视为已存在；insert 命中返回 0）
     */
    public int upsertConfigValue(String key, String value, String group) {
        LocalDateTime now = LocalDateTime.now();
        // 1) 尝试 update（命中 → 视为已存在）
        int rows = jdbcTemplate.update(
                "UPDATE sys_config SET config_value = ?, updated_at = ? " +
                "WHERE config_key = ? AND status = 'active'",
                value, now, key);
        if (rows > 0) {
            return rows;
        }
        // 2) 不存在 → insert（保持与 Group 一致）
        jdbcTemplate.update(
                "INSERT INTO sys_config (id, config_key, config_value, config_group, config_type, " +
                "config_label, description, sort_order, status, edition, created_at, updated_at) " +
                "VALUES (gen_random_uuid(), ?, ?, ?, 'string', ?, '', 100, 'active', 'all', ?, ?)",
                key, value, group, labelFor(key), now, now);
        return 0;
    }

    /** 从 key 末段推 label：cognitive.model.default → default。 */
    private static String labelFor(String key) {
        int dot = key.lastIndexOf('.');
        return (dot < 0 || dot == key.length() - 1) ? key : key.substring(dot + 1);
    }
}
