package com.chinacreator.gzcm.engine.kb.dao;

import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.stereotype.Repository;

@Repository
public class KbEngineHealthDao extends JdbcTemplateDaoSupport {

    public void pingDb() {
        jdbcTemplate.queryForObject("SELECT 1", Integer.class);
    }

    public long countNodes() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ecos_knowledge.graph_node", Long.class);
    }

    public long countEdges() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ecos_knowledge.graph_edge", Long.class);
    }

    public long countArticles() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ecos_knowledge.knowledge_article", Long.class);
    }

    public long countRules() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ecos_knowledge.expert_rule", Long.class);
    }

    public long countEmbeddings() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ecos_knowledge.knowledge_embedding", Long.class);
    }
}
