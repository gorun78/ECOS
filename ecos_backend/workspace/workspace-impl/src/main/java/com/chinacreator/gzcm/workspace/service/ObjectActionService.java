package com.chinacreator.gzcm.workspace.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 对象 Action Service — 从 ObjectActionController 下沉的 JdbcTemplate 访问层。
 *
 * <p>职责：Action 定义查询、对象存在性校验、可用 Action 列表查询。
 * 保持与原 Controller 完全一致的 SQL 语义。</p>
 */
@Service
public class ObjectActionService {

    private static final Logger log = LoggerFactory.getLogger(ObjectActionService.class);

    private final JdbcTemplate jdbc;

    public ObjectActionService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 验证对象是否存在（按表名 + id 查询）。
     * 返回查询结果行（非空即存在），异常时调用方按 debug 忽略。
     */
    public List<Map<String, Object>> findObjectById(String table, String id) {
        return jdbc.queryForList(
            "SELECT id FROM " + table + " WHERE id = ?", id);
    }

    /**
     * 按 entity code 查询可用 Action 列表（status = 'ACTIVE'）。
     */
    public List<Map<String, Object>> listAvailableActions(String entityCode) {
        return jdbc.queryForList(
            """
            SELECT a.id, a.entity_id, a.name, a.action_type, a.strategy, a.status, a.created_at
            FROM ecos_ontology_action a
            JOIN ecos_ontology_entity e ON a.entity_id = e.id
            WHERE e.code = ? AND a.status = 'ACTIVE'
            ORDER BY a.created_at
            """, entityCode);
    }

    /**
     * 按 action code 查找 Action 定义（name 或 id 匹配，LIMIT 1）。
     * 异常时记录日志并返回空列表。
     */
    public List<Map<String, Object>> findActionByCode(String actionCode) {
        try {
            return jdbc.queryForList(
                "SELECT * FROM ecos_ontology_action WHERE name = ? OR id = ? LIMIT 1",
                actionCode, actionCode);
        } catch (Exception e) {
            log.warn("Failed to find action {}: {}", actionCode, e.getMessage());
            return List.of();
        }
    }

    // ── PMO-E2 通用委托方法 ──
    public List<Map<String, Object>> queryForList(String sql, Object... args) {
        return jdbc.queryForList(sql, args);
    }
    public <T> T queryForObject(String sql, Class<T> type, Object... args) {
        return jdbc.queryForObject(sql, type, args);
    }
    public Map<String, Object> queryForMap(String sql, Object... args) {
        return jdbc.queryForMap(sql, args);
    }
    public int update(String sql, Object... args) {
        return jdbc.update(sql, args);
    }
}
