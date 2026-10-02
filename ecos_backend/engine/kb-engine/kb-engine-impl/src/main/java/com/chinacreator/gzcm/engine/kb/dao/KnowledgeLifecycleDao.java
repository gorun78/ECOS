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
public class KnowledgeLifecycleDao extends JdbcTemplateDaoSupport {

    private final String schema;

    public KnowledgeLifecycleDao(
            @Value("${kb.control-schema:ecos_knowledge}") String schema) {
        this.schema = schema;
    }

    public List<Map<String, Object>> listAssets(int limit, int offset) {
        return jdbcTemplate.queryForList(
                    "SELECT id, title, status, updated_at FROM " + schema + ".knowledge_article "
                            + "ORDER BY updated_at DESC LIMIT ? OFFSET ?",
                    limit, offset);
    }

    public List<Map<String, Object>> listAssetsByStatus(String placeholders, Object[] args) {
        return jdbcTemplate.queryForList(
                    "SELECT id, title, status, updated_at FROM " + schema + ".knowledge_article "
                            + "WHERE lower(status) IN (" + placeholders + ") "
                            + "ORDER BY updated_at DESC LIMIT ? OFFSET ?",
                    args);
    }

    public List<Map<String, Object>> listAudit(int limit, int offset) {
        return jdbcTemplate.queryForList(
                "SELECT id, object_type, op, status, created_at FROM " + schema + ".kg_sync_log "
                        + "ORDER BY created_at DESC LIMIT ? OFFSET ?",
                limit, offset);
    }
}
