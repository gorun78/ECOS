package com.chinacreator.gzcm.engine.kb.dao;

import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

/**
 * <p>V200/R3-B1: schema 由 `${kb.control-schema:ecos_knowledge}` 注入 (MC06 全面落地)。</p>
 */
@Repository
public class KbEngineHealthDao extends JdbcTemplateDaoSupport {

    private final String schema;

    public KbEngineHealthDao(
            @Value("${kb.control-schema:ecos_knowledge}") String schema) {
        this.schema = schema;
    }

    public void pingDb() {
        jdbcTemplate.queryForObject("SELECT 1", Integer.class);
    }

    public long countNodes() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + schema + ".graph_node", Long.class);
    }

    public long countEdges() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + schema + ".graph_edge", Long.class);
    }

    public long countArticles() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + schema + ".knowledge_article", Long.class);
    }

    public long countRules() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + schema + ".expert_rule", Long.class);
    }

    public long countEmbeddings() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + schema + ".knowledge_embedding", Long.class);
    }
}
