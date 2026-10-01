package com.chinacreator.gzcm.engine.ontology.dao;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.chinacreator.gzcm.engine.ontology.model.OntologyRule;
import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * B3c 数据访问收口（R2/B3c）：规则 CRUD（{@code ecos_ontology_rule} 表）。
 *
 * <p>SQL / RowMapper 全量搬运自 {@code OntologyRuleRepository}。</p>
 */
@Repository
public class OntologyRuleDao extends JdbcTemplateDaoSupport {

    private final RowMapper<OntologyRule> ROW_MAPPER = (rs, rn) -> {
        OntologyRule r = new OntologyRule();
        r.setId(rs.getString("id"));
        r.setEntityId(rs.getString("entity_id"));
        r.setCode(rs.getString("code"));
        r.setName(rs.getString("name"));
        r.setRuleType(rs.getString("rule_type"));
        r.setExpression(rs.getString("expression"));
        r.setAction(rs.getString("action"));
        r.setPriority(rs.getInt("priority"));
        r.setEnabled(rs.getInt("enabled"));
        r.setDescription(rs.getString("description"));
        r.setCreatedAt(ts(rs.getTimestamp("created_at")));
        r.setUpdatedAt(ts(rs.getTimestamp("updated_at")));
        return r;
    };

    private static LocalDateTime ts(Timestamp t) {
        return t != null ? t.toLocalDateTime() : null;
    }

    public List<OntologyRule> findByEntity(String entityId) {
        return jdbcTemplate.query(
            "SELECT * FROM ecos_ontology_rule WHERE entity_id = ? ORDER BY priority, created_at DESC",
            ROW_MAPPER, entityId);
    }

    public List<OntologyRule> findAll() {
        return jdbcTemplate.query("SELECT * FROM ecos_ontology_rule ORDER BY priority, created_at DESC", ROW_MAPPER);
    }

    public Optional<OntologyRule> findById(String id) {
        List<OntologyRule> list = jdbcTemplate.query("SELECT * FROM ecos_ontology_rule WHERE id = ?", ROW_MAPPER, id);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public int insert(OntologyRule rule) {
        return jdbcTemplate.update("""
            INSERT INTO ecos_ontology_rule (id, entity_id, code, name, rule_type, expression, action, priority, enabled, description, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
            """, rule.getId(), rule.getEntityId(), rule.getCode(), rule.getName(),
            rule.getRuleType(), rule.getExpression(), rule.getAction(),
            rule.getPriority(), rule.getEnabled(), rule.getDescription());
    }

    public int update(String id, String code, String name, String ruleType,
                       String expression, String action, Integer priority,
                       Integer enabled, String description) {
        return jdbcTemplate.update("""
            UPDATE ecos_ontology_rule SET
                code = COALESCE(?, code),
                name = COALESCE(?, name),
                rule_type = COALESCE(?, rule_type),
                expression = COALESCE(?, expression),
                action = COALESCE(?, action),
                priority = COALESCE(?, priority),
                enabled = COALESCE(?, enabled),
                description = COALESCE(?, description),
                updated_at = NOW()
            WHERE id = ?
            """, code, name, ruleType, expression, action, priority, enabled, description, id);
    }

    public int delete(String id) {
        return jdbcTemplate.update(
            "UPDATE ecos_ontology_rule SET is_deleted = 1, status = 'ARCHIVED', " +
            "update_time = now(), update_by = 'system' WHERE id = ? AND is_deleted = 0", id);
    }

    public int deleteByEntity(String entityId) {
        return jdbcTemplate.update(
            "UPDATE ecos_ontology_rule SET is_deleted = 1, status = 'ARCHIVED', " +
            "update_time = now(), update_by = 'system' WHERE entity_id = ? AND is_deleted = 0", entityId);
    }
}
