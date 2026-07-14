package com.chinacreator.gzcm.dccheng.ontology;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * JdbcTemplate 仓库 — 规则 CRUD（ecos_ontology_rule 表）
 */
@Repository
public class OntologyRuleRepository {

    private final JdbcTemplate jdbc;

    public OntologyRuleRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

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
        return jdbc.query(
            "SELECT * FROM ecos_ontology_rule WHERE entity_id = ? ORDER BY priority, created_at DESC",
            ROW_MAPPER, entityId);
    }

    public List<OntologyRule> findAll() {
        return jdbc.query("SELECT * FROM ecos_ontology_rule ORDER BY priority, created_at DESC", ROW_MAPPER);
    }

    public Optional<OntologyRule> findById(String id) {
        List<OntologyRule> list = jdbc.query("SELECT * FROM ecos_ontology_rule WHERE id = ?", ROW_MAPPER, id);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public int insert(OntologyRule rule) {
        return jdbc.update("""
            INSERT INTO ecos_ontology_rule (id, entity_id, code, name, rule_type, expression, action, priority, enabled, description, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
            """, rule.getId(), rule.getEntityId(), rule.getCode(), rule.getName(),
            rule.getRuleType(), rule.getExpression(), rule.getAction(),
            rule.getPriority(), rule.getEnabled(), rule.getDescription());
    }

    public int update(String id, String code, String name, String ruleType,
                       String expression, String action, Integer priority,
                       Integer enabled, String description) {
        return jdbc.update("""
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
        return jdbc.update("DELETE FROM ecos_ontology_rule WHERE id = ?", id);
    }

    public int deleteByEntity(String entityId) {
        return jdbc.update("DELETE FROM ecos_ontology_rule WHERE entity_id = ?", entityId);
    }
}
