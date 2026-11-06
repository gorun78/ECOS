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
public class EntityLinkerDao extends JdbcTemplateDaoSupport {

    private final String schema;

    public EntityLinkerDao(
            @Value("${kb.control-schema:ecos_knowledge}") String schema) {
        this.schema = schema;
    }

    /** 本体类型候选（V179 {@code ecos_kb_ontology_snapshot.entity_codes_json} + ILIKE domain 过滤）。 */
    public List<Map<String, Object>> queryOntologyTypes(String domain) {
        return jdbcTemplate.queryForList(
                "SELECT s.ontology_id AS ontology_id, e AS entity_code " +
                "FROM " + schema + ".ecos_kb_ontology_snapshot s, " +
                "     jsonb_array_elements_text(CAST(s.entity_codes_json AS jsonb)) AS e " +
                "WHERE s.is_deleted = 0 AND e ILIKE ? " +
                "ORDER BY s.ontology_id LIMIT 50",
                "%" + domain + "%");
    }
}
