package com.chinacreator.gzcm.engine.kb.dao;

import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

/**
 * <p>V200/R3-B1: schema 由 `${kb.control-schema:ecos_knowledge}` 注入 (MC06 全面落地)。</p>
 */
@Repository
public class KnowledgeEvalDao extends JdbcTemplateDaoSupport {

    private final String schema;

    public KnowledgeEvalDao(
            @Value("${kb.control-schema:ecos_knowledge}") String schema) {
        this.schema = schema;
    }

    public List<Map<String, Object>> sampleEmbeddings(int maxQueries) {
        return jdbcTemplate.queryForList(
                "SELECT id, content FROM " + schema + ".knowledge_embedding " +
                "WHERE embedding_vec IS NOT NULL AND content IS NOT NULL AND content <> '' " +
                "ORDER BY created_at DESC LIMIT ?", maxQueries);
    }
}
