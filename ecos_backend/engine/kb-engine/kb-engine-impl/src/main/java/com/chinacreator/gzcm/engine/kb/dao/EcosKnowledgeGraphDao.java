package com.chinacreator.gzcm.engine.kb.dao;

import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * <p>V200/R3-B1: schema 由 `${kb.control-schema:ecos_knowledge}` 注入 (MC06 全面落地)。</p>
 */
@Repository
public class EcosKnowledgeGraphDao extends JdbcTemplateDaoSupport {

    private final String schema;

    public EcosKnowledgeGraphDao(
            @Value("${kb.control-schema:ecos_knowledge}") String schema) {
        this.schema = schema;
    }

    public List<Map<String, Object>> loadNodes() {
        return jdbcTemplate.queryForList(
                    "SELECT id, label, node_type, domain, properties FROM " + schema + ".graph_node ORDER BY label");
    }

    public List<Map<String, Object>> loadEdges() {
        return jdbcTemplate.queryForList(
                    "SELECT e.id, e.source_id, e.target_id, e.type AS \"relationship\", " +
                    "       n1.label AS \"sourceName\", n2.label AS \"targetName\" " +
                    "FROM " + schema + ".graph_edge e " +
                    "LEFT JOIN " + schema + ".graph_node n1 ON n1.id = e.source_id " +
                    "LEFT JOIN " + schema + ".graph_node n2 ON n2.id = e.target_id " +
                    "ORDER BY n1.label, n2.label");
    }

    public Integer countNodes() {
        return jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM " + schema + ".graph_node", Integer.class);
    }

    public Integer countEdges() {
        return jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM " + schema + ".graph_edge", Integer.class);
    }

    public List<String> distinctDomains() {
        return jdbcTemplate.queryForList(
                    "SELECT DISTINCT domain FROM " + schema + ".graph_node " +
                    "WHERE domain IS NOT NULL ORDER BY domain",
                    String.class);
    }

    /**
     * 投影装载面的节点列（附录 A 第 15 条投影腿）：只取投影所需四列，不搬 {@code properties}
     * （大 JSONB 无投影语义）。{@code node_type} 空值在此保持 {@code null}，由服务侧归入
     * {@code UNKNOWN} 桶参与对账（禁静默丢弃无类型节点）。
     */
    public List<Map<String, Object>> loadProjectionNodes() {
        return jdbcTemplate.queryForList(
                    "SELECT id, label, node_type AS \"nodeType\", domain " +
                    "FROM " + schema + ".graph_node ORDER BY id");
    }

    /** 投影装载面的边列（端点/类型；属性不参与投影对账）。 */
    public List<Map<String, Object>> loadProjectionEdges() {
        return jdbcTemplate.queryForList(
                    "SELECT id, source_id AS \"sourceId\", target_id AS \"targetId\", type " +
                    "FROM " + schema + ".graph_edge ORDER BY id");
    }

    /**
     * 按 {@code node_type} 的 PG 权威计数（§7.2.3 对账行：按 node_type 比对）。
     * {@code node_type} 空值归 {@code UNKNOWN} 桶（与投影侧 {@code coalesce(n.nodeType,'UNKNOWN')} 同口径）。
     */
    public List<Map<String, Object>> countNodesByType() {
        return jdbcTemplate.queryForList(
                    "SELECT COALESCE(node_type, 'UNKNOWN') AS \"nodeType\", COUNT(*) AS \"c\" " +
                    "FROM " + schema + ".graph_node GROUP BY COALESCE(node_type, 'UNKNOWN') ORDER BY 1");
    }

    public void insertNeo4jSyncLog(String jobId) {
        jdbcTemplate.update(
                    // #146h D-7（2026-10-27）：kg_sync_log（V115，dev 库面不存在）→ V179 合规新表 ecos_kg_sync_log；
                    // id 应用侧 UUID（MC01）、op 列名 sync_op、create_time 显式补（V179 finish_deny_in-place 语义）。
                    "INSERT INTO " + schema + ".ecos_kg_sync_log " +
                    "(id, object_type, sync_op, job_id, status, progress, create_time) " +
                    "VALUES (?, ?, ?, ?, 'RUNNING', 0, NOW())",
                    UUID.randomUUID().toString(), "ALL", "NEO4J_SYNC", jobId);
    }
}
