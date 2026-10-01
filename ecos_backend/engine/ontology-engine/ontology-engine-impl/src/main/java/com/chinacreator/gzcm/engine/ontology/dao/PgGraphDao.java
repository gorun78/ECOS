package com.chinacreator.gzcm.engine.ontology.dao;

import java.util.List;
import java.util.Map;

import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.stereotype.Repository;

/**
 * B3-a 数据访问收口（R2-B3 / 架构铁律 §2.5 #1 / IR06）：{@code PgGraphService}
 * （standard 档 PG 图实例化：ecos_objects / ecos_object_links）的 JDBC 落点。
 *
 * <p>SQL 原样搬运（含 ON CONFLICT upsert 与子图双查询）；Cypher→SQL 翻译
 * 等业务逻辑保留在 Service 层。</p>
 */
@Repository
public class PgGraphDao extends JdbcTemplateDaoSupport {

    /** Cypher 翻译后的 SELECT 执行（参数数组由 Service 侧按占位符序提取）。 */
    public List<Map<String, Object>> executeTranslatedQuery(String sql, Object[] params) {
        return jdbcTemplate.queryForList(sql, params);
    }

    /**
     * 节点落库（ON CONFLICT (id) upsert）。
     */
    public void createNode(String id, String label, String type, String category, String name) {
        jdbcTemplate.update(
            "INSERT INTO ecos_objects (id, label, type, category, name, properties, created_at) " +
            "VALUES (?, ?, ?, ?, ?, '{}'::jsonb, NOW()) " +
            "ON CONFLICT (id) DO UPDATE SET label=EXCLUDED.label, type=EXCLUDED.type, " +
            "category=EXCLUDED.category, name=EXCLUDED.name",
            id, label, type, category, name
        );
    }

    /**
     * 关系落库。
     */
    public void createRelationship(String id, String fromId, String toId, String relType) {
        jdbcTemplate.update(
            "INSERT INTO ecos_object_links (id, source_id, target_id, rel_type, strength, properties, created_at) " +
            "VALUES (?, ?, ?, ?, 0.5, '{}'::jsonb, NOW())",
            id, fromId, toId, relType
        );
    }

    /**
     * 子图节点查询（中心节点 + 一跳关联节点）。
     */
    public List<Map<String, Object>> findSubgraphNodes(String entityId) {
        return jdbcTemplate.queryForList(
            "SELECT o1.*, o2.id   AS linked_id, o2.label AS linked_label, " +
            "       o2.type AS linked_type, o2.category AS linked_category, o2.name AS linked_name " +
            "FROM ecos_objects o1 " +
            "JOIN ecos_object_links l ON o1.id = l.source_id " +
            "JOIN ecos_objects o2 ON l.target_id = o2.id " +
            "WHERE o1.id = ?",
            entityId
        );
    }

    /**
     * 子图边查询（与中心节点相关的边）。
     */
    public List<Map<String, Object>> findSubgraphEdges(String entityId) {
        return jdbcTemplate.queryForList(
            "SELECT l.id, l.source_id, l.target_id, l.rel_type, l.strength " +
            "FROM ecos_object_links l " +
            "WHERE l.source_id = ? OR l.target_id = ?",
            entityId, entityId
        );
    }
}
