package com.chinacreator.gzcm.engine.kb.dao;

import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public class EntityLinkerDao extends JdbcTemplateDaoSupport {

    /** 本体类型候选（kb_ontology_snapshot.entity_codes + ILIKE domain 过滤）。 */
    public List<Map<String, Object>> queryOntologyTypes(String domain) {
        return jdbcTemplate.queryForList(
                "SELECT s.ontology_id AS ontology_id, e AS entity_code " +
                "FROM ecos_knowledge.kb_ontology_snapshot s, " +
                "     jsonb_array_elements_text(s.entity_codes) AS e " +
                "WHERE s.is_deleted = 0 AND e ILIKE ? " +
                "ORDER BY s.ontology_id LIMIT 50",
                "%" + domain + "%");
    }
}
