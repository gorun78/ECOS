package com.chinacreator.gzcm.engine.kb.dao;

import com.chinacreator.gzcm.engine.kb.model.KnowledgeNode;
import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.stereotype.Repository;

@Repository
public class KnowledgeIngestQueryDao extends JdbcTemplateDaoSupport {

    public int updateGraphNode(KnowledgeNode node) {
        return jdbcTemplate.update(
                "UPDATE ecos_knowledge.graph_node SET label = ?, node_type = ?, description = ?, " +
                "properties = ?, domain = ?, updated_at = ? WHERE id = ?",
                node.getLabel(), node.getNodeType(), node.getDescription(),
                node.getPropertiesJson(), node.getDomain(),
                node.getUpdatedAt(), node.getId());
    }
}
