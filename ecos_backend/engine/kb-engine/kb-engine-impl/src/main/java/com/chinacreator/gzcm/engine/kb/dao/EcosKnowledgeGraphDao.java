package com.chinacreator.gzcm.engine.kb.dao;

import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public class EcosKnowledgeGraphDao extends JdbcTemplateDaoSupport {

    public List<Map<String, Object>> loadNodes() {
        return jdbcTemplate.queryForList(
                    "SELECT id, label, node_type, domain, properties FROM ecos_knowledge.graph_node ORDER BY label");
    }

    public List<Map<String, Object>> loadEdges() {
        return jdbcTemplate.queryForList(
                    "SELECT e.id, e.source_id, e.target_id, e.type AS \"relationship\", " +
                    "       n1.label AS \"sourceName\", n2.label AS \"targetName\" " +
                    "FROM ecos_knowledge.graph_edge e " +
                    "LEFT JOIN ecos_knowledge.graph_node n1 ON n1.id = e.source_id " +
                    "LEFT JOIN ecos_knowledge.graph_node n2 ON n2.id = e.target_id " +
                    "ORDER BY n1.label, n2.label");
    }

    public Integer countNodes() {
        return jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM ecos_knowledge.graph_node", Integer.class);
    }

    public Integer countEdges() {
        return jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM ecos_knowledge.graph_edge", Integer.class);
    }

    public List<String> distinctDomains() {
        return jdbcTemplate.queryForList(
                    "SELECT DISTINCT domain FROM ecos_knowledge.graph_node " +
                    "WHERE domain IS NOT NULL ORDER BY domain",
                    String.class);
    }

    public void insertNeo4jSyncLog(String jobId) {
        jdbcTemplate.update(
                    "INSERT INTO ecos_knowledge.kg_sync_log " +
                    "(object_type, op, job_id, status, progress) VALUES (?, ?, ?, 'RUNNING', 0)",
                    "ALL", "NEO4J_SYNC", jobId);
    }
}
