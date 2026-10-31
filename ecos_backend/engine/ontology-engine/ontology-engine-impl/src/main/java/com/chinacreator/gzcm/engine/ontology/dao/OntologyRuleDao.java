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
 *
 * <p>#147B（2026-10-31）两笔账实订正 + 双维承载：
 * ① 时间戳列名断裂修复——原读写 {@code created_at/updated_at} 在 live 表不存在
 *   （实列 {@code create_time/update_time}，42703 复证），建行即读失败；
 * ② 逻辑删读面收口——delete 腿写 {@code is_deleted=1} 但三枚读腿从未过滤，
 *   已删规则恒可见；现 {@code findByEntity/findAll/findById} 一律
 *   {@code AND is_deleted = 0}；
 * ③ 双维路由 + 发布就绪件套承载——{@code rule_carrier/rule_layer/scope/
 *   version_no/git_ref}（V300 落列）与 {@code status/domain} 进 RowMapper/
 *   INSERT/UPDATE（COALESCE null=不更新）。</p>
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
        r.setRuleCarrier(rs.getString("rule_carrier"));
        r.setRuleLayer(rs.getString("rule_layer"));
        r.setScope(rs.getString("scope"));
        r.setVersionNo(rs.getString("version_no"));
        r.setGitRef(rs.getString("git_ref"));
        r.setStatus(rs.getString("status"));
        r.setDomain(rs.getString("domain"));
        r.setCreatedAt(ts(rs.getTimestamp("create_time")));
        r.setUpdatedAt(ts(rs.getTimestamp("update_time")));
        return r;
    };

    private static LocalDateTime ts(Timestamp t) {
        return t != null ? t.toLocalDateTime() : null;
    }

    public List<OntologyRule> findByEntity(String entityId) {
        return jdbcTemplate.query(
            "SELECT * FROM ecos_ontology_rule WHERE entity_id = ? AND is_deleted = 0 ORDER BY priority, create_time DESC",
            ROW_MAPPER, entityId);
    }

    public List<OntologyRule> findAll() {
        return jdbcTemplate.query(
            "SELECT * FROM ecos_ontology_rule WHERE is_deleted = 0 ORDER BY priority, create_time DESC", ROW_MAPPER);
    }

    public Optional<OntologyRule> findById(String id) {
        List<OntologyRule> list = jdbcTemplate.query(
            "SELECT * FROM ecos_ontology_rule WHERE id = ? AND is_deleted = 0", ROW_MAPPER, id);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public int insert(OntologyRule rule) {
        return jdbcTemplate.update("""
            INSERT INTO ecos_ontology_rule (id, entity_id, code, name, rule_type, expression, action, priority, enabled, description,
                rule_carrier, rule_layer, scope, version_no, git_ref, status, domain, create_time, update_time)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
            """, rule.getId(), rule.getEntityId(), rule.getCode(), rule.getName(),
            rule.getRuleType(), rule.getExpression(), rule.getAction(),
            rule.getPriority(), rule.getEnabled(), rule.getDescription(),
            rule.getRuleCarrier(), rule.getRuleLayer(), rule.getScope(),
            rule.getVersionNo(), rule.getGitRef(), rule.getStatus(), rule.getDomain());
    }

    public int update(String id, String code, String name, String ruleType,
                       String expression, String action, Integer priority,
                       Integer enabled, String description,
                       String ruleCarrier, String ruleLayer, String scope,
                       String versionNo, String gitRef, String status) {
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
                rule_carrier = COALESCE(?, rule_carrier),
                rule_layer = COALESCE(?, rule_layer),
                scope = COALESCE(?, scope),
                version_no = COALESCE(?, version_no),
                git_ref = COALESCE(?, git_ref),
                status = COALESCE(?, status),
                update_time = NOW()
            WHERE id = ?
            """, code, name, ruleType, expression, action, priority, enabled, description,
            ruleCarrier, ruleLayer, scope, versionNo, gitRef, status, id);
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
