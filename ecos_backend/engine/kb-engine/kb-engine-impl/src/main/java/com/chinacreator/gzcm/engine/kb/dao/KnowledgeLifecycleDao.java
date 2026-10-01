package com.chinacreator.gzcm.engine.kb.dao;

import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public class KnowledgeLifecycleDao extends JdbcTemplateDaoSupport {

    public List<Map<String, Object>> listAssets(int limit, int offset) {
        return jdbcTemplate.queryForList(
                    "SELECT id, title, status, updated_at FROM ecos_knowledge.knowledge_article "
                            + "ORDER BY updated_at DESC LIMIT ? OFFSET ?",
                    limit, offset);
    }

    public List<Map<String, Object>> listAssetsByStatus(String placeholders, Object[] args) {
        return jdbcTemplate.queryForList(
                    "SELECT id, title, status, updated_at FROM ecos_knowledge.knowledge_article "
                            + "WHERE lower(status) IN (" + placeholders + ") "
                            + "ORDER BY updated_at DESC LIMIT ? OFFSET ?",
                    args);
    }

    public List<Map<String, Object>> listAudit(int limit, int offset) {
        return jdbcTemplate.queryForList(
                "SELECT id, object_type, op, status, created_at FROM ecos_knowledge.kg_sync_log "
                        + "ORDER BY created_at DESC LIMIT ? OFFSET ?",
                limit, offset);
    }
}
