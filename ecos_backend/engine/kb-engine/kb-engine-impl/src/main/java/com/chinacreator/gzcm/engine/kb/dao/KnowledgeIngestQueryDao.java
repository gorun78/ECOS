package com.chinacreator.gzcm.engine.kb.dao;

import com.chinacreator.gzcm.engine.kb.model.KnowledgeNode;
import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

/**
 * <p>V200/R3-B1: schema 由 `${kb.control-schema:ecos_knowledge}` 注入 (MC06 全面落地)。</p>
 */
@Repository
public class KnowledgeIngestQueryDao extends JdbcTemplateDaoSupport {

    private final String schema;

    public KnowledgeIngestQueryDao(
            @Value("${kb.control-schema:ecos_knowledge}") String schema) {
        this.schema = schema;
    }

    public int updateGraphNode(KnowledgeNode node) {
        return jdbcTemplate.update(
                "UPDATE " + schema + ".graph_node SET label = ?, node_type = ?, description = ?, " +
                "properties = ?, domain = ?, updated_at = ? WHERE id = ?",
                node.getLabel(), node.getNodeType(), node.getDescription(),
                node.getPropertiesJson(), node.getDomain(),
                node.getUpdatedAt(), node.getId());
    }
}
