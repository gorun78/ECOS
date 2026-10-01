package com.chinacreator.gzcm.engine.kb.dao;

import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public class KnowledgeEvalDao extends JdbcTemplateDaoSupport {

    public List<Map<String, Object>> sampleEmbeddings(int maxQueries) {
        return jdbcTemplate.queryForList(
                "SELECT id, content FROM ecos_knowledge.knowledge_embedding " +
                "WHERE embedding_vec IS NOT NULL AND content IS NOT NULL AND content <> '' " +
                "ORDER BY created_at DESC LIMIT ?", maxQueries);
    }
}
