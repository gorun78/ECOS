package com.chinacreator.gzcm.engine.ontology.dao;

import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.chinacreator.gzcm.engine.ontology.model.OntologyAction;
import com.chinacreator.gzcm.engine.ontology.model.OntologyEntity;
import com.chinacreator.gzcm.engine.ontology.model.OntologyProperty;
import com.chinacreator.gzcm.engine.ontology.model.OntologyRelationship;
import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * B3c 数据访问收口（R2/B3c）：本体持久化 CRUD（实体/属性/关系/动作 + ontology 主表 + 关系边）。
 *
 * <p>SQL / RowMapper 全量搬运自 {@code OntologyRepository}（含 nextId 的
 * {@code regexp_replace ... ::bigint} 白名单表反注入）。风格保留源文件的
 * {@code SELECT *}（存量非 proposal 链路，见各 wave 裁决）。</p>
 */
@Repository
public class OntologyDao extends JdbcTemplateDaoSupport {

    private static final java.util.Set<String> ALLOWED_ID_TABLES = java.util.Set.of(
        "ecos_ontology_entity", "ecos_ontology_property", "ecos_ontology_relationship");

    private final RowMapper<OntologyEntity> ENTITY_MAPPER = (rs, rn) -> {
        OntologyEntity e = new OntologyEntity();
        e.setId(rs.getString("id"));
        e.setOntologyId(rs.getString("ontology_id"));
        e.setCode(rs.getString("code"));
        e.setName(rs.getString("name"));
        e.setDescription(rs.getString("description"));
        e.setEntityType(rs.getString("entity_type"));
        e.setDomainId(rs.getString("domain_id"));
        e.setSortOrder(rs.getInt("sort_order"));
        e.setCreatedAt(ts(rs.getTimestamp("created_at")));
        e.setUpdatedAt(ts(rs.getTimestamp("updated_at")));
        return e;
    };

    private final RowMapper<OntologyProperty> PROP_MAPPER = (rs, rn) -> {
        // V200: ecos_ontology_property 已无 description / unique_flag 列 —— 二次分辨率列不读，
        // 守行只带 V200 实存列（否则 SELECT * + 死列读抛 "栏位名称不存在"）。
        OntologyProperty p = new OntologyProperty();
        p.setId(rs.getString("id"));
        p.setEntityId(rs.getString("entity_id"));
        p.setCode(rs.getString("code"));
        p.setName(rs.getString("name"));
        p.setPropertyType(rs.getString("property_type"));
        p.setRequiredFlag(rs.getInt("required_flag"));
        p.setSearchableFlag(rs.getInt("searchable_flag"));
        p.setSortOrder(rs.getInt("sort_order"));
        p.setFunctionType(rs.getString("function_type"));
        p.setFunctionExpression(rs.getString("function_expression"));
        p.setCreatedAt(ts(rs.getTimestamp("created_at")));
        return p;
    };

    private final RowMapper<OntologyRelationship> REL_MAPPER = (rs, rn) -> {
        OntologyRelationship r = new OntologyRelationship();
        r.setId(rs.getString("id"));
        r.setSourceEntityId(rs.getString("source_entity_id"));
        r.setTargetEntityId(rs.getString("target_entity_id"));
        r.setCode(rs.getString("code"));
        r.setName(rs.getString("name"));
        r.setRelationshipType(rs.getString("relationship_type"));
        r.setCreatedAt(ts(rs.getTimestamp("created_at")));
        return r;
    };

    private final RowMapper<OntologyAction> ACTION_MAPPER = (rs, rn) -> {
        OntologyAction a = new OntologyAction();
        a.setId(rs.getString("id"));
        a.setEntityId(rs.getString("entity_id"));
        a.setCode(rs.getString("code"));
        a.setName(rs.getString("name"));
        a.setActionType(rs.getString("action_type"));
        a.setDescription(rs.getString("description"));
        a.setPreconditions(rs.getString("preconditions"));
        a.setEffects(rs.getString("effects"));
        a.setRuleJson(rs.getString("rule_json"));
        a.setStrategy(rs.getString("strategy"));
        a.setStatus(rs.getString("status"));
        a.setCreatedAt(ts(rs.getTimestamp("created_at")));
        a.setUpdatedAt(ts(rs.getTimestamp("updated_at")));
        return a;
    };

    private static java.time.LocalDateTime ts(Timestamp t) {
        return t != null ? t.toLocalDateTime() : null;
    }

    public String nextId(String prefix, String table) {
        if (!ALLOWED_ID_TABLES.contains(table)) {
            throw new IllegalArgumentException("ONT-000: table not allowed for id generation: " + table);
        }
        Long max = jdbcTemplate.queryForObject(
            "SELECT COALESCE(MAX(NULLIF(regexp_replace(id, '\\D', '', 'g'), '')::bigint), 500) "
                + "FROM " + table + " WHERE id LIKE ?", Long.class, prefix + "%");
        return prefix + ((max == null ? 500L : max) + 1L);
    }

    public List<OntologyEntity> findEntitiesByOntology(String ontologyId) {
        return jdbcTemplate.query(
            "SELECT * FROM ecos_ontology_entity WHERE ontology_id = ? ORDER BY sort_order",
            ENTITY_MAPPER, ontologyId);
    }

    public Optional<OntologyEntity> findEntityById(String id) {
        List<OntologyEntity> list = jdbcTemplate.query(
            "SELECT * FROM ecos_ontology_entity WHERE id = ?", ENTITY_MAPPER, id);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public int insertEntity(OntologyEntity entity) {
        return jdbcTemplate.update("""
            INSERT INTO ecos_ontology_entity (id, ontology_id, code, name, description, entity_type, sort_order, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
            """, entity.getId(), entity.getOntologyId(), entity.getCode(),
            entity.getName(), entity.getDescription(), entity.getEntityType(), entity.getSortOrder());
    }

    public int updateEntity(String id, String code, String name, String description, String entityType) {
        return jdbcTemplate.update("""
            UPDATE ecos_ontology_entity SET
                code = COALESCE(?, code),
                name = COALESCE(?, name),
                description = COALESCE(?, description),
                entity_type = COALESCE(?, entity_type),
                updated_at = NOW()
            WHERE id = ?
            """, code, name, description, entityType, id);
    }

    public int deleteEntity(String id) {
        return jdbcTemplate.update(
            "UPDATE ecos_ontology_entity SET is_deleted = 1, status = 'ARCHIVED', " +
            "updated_at = now(), update_by = 'system' WHERE id = ? AND is_deleted = 0", id);
    }

    public List<OntologyProperty> findPropertiesByEntity(String entityId) {
        return jdbcTemplate.query(
            "SELECT * FROM ecos_ontology_property WHERE entity_id = ? AND is_deleted = 0 ORDER BY sort_order",
            PROP_MAPPER, entityId);
    }

    public Optional<OntologyProperty> findPropertyById(String id) {
        List<OntologyProperty> list = jdbcTemplate.query(
            "SELECT * FROM ecos_ontology_property WHERE id = ?", PROP_MAPPER, id);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public int insertProperty(OntologyProperty prop) {
        // V200: 无 description / unique_flag 列，仅落实存列
        return jdbcTemplate.update("""
            INSERT INTO ecos_ontology_property (id, entity_id, code, name, property_type, required_flag, searchable_flag, sort_order, function_type, function_expression, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())
            """, prop.getId(), prop.getEntityId(), prop.getCode(), prop.getName(),
            prop.getPropertyType(), prop.getRequiredFlag(), prop.getSearchableFlag(),
            prop.getSortOrder(),
            prop.getFunctionType(), prop.getFunctionExpression());
    }

    public int updateProperty(String id, String code, String name, String description, String propertyType,
                               Integer requiredFlag, Integer searchableFlag, Integer uniqueFlag,
                               String functionType, String functionExpression) {
        // V200: description / unique_flag 不落列；description/uniqueFlag 参数保留以兼容签名
        return jdbcTemplate.update("""
            UPDATE ecos_ontology_property SET
                code = COALESCE(?, code),
                name = COALESCE(?, name),
                property_type = COALESCE(?, property_type),
                required_flag = COALESCE(?, required_flag),
                searchable_flag = COALESCE(?, searchable_flag),
                function_type = COALESCE(?, function_type),
                function_expression = COALESCE(?, function_expression)
            WHERE id = ? AND is_deleted = 0
            """, code, name, propertyType, requiredFlag, searchableFlag,
            functionType, functionExpression, id);
    }

    public int deleteProperty(String id) {
        return jdbcTemplate.update(
            "UPDATE ecos_ontology_property SET is_deleted = 1, status = 'ARCHIVED', " +
            "update_time = now(), update_by = 'system' WHERE id = ? AND is_deleted = 0", id);
    }

    public int deletePropertiesByEntity(String entityId) {
        return jdbcTemplate.update(
            "UPDATE ecos_ontology_property SET is_deleted = 1, status = 'ARCHIVED', " +
            "update_time = now(), update_by = 'system' WHERE entity_id = ? AND is_deleted = 0", entityId);
    }

    public List<OntologyRelationship> findRelationshipsByEntity(String entityId) {
        return jdbcTemplate.query(
            "SELECT * FROM ecos_ontology_relationship WHERE source_entity_id = ? OR target_entity_id = ?",
            REL_MAPPER, entityId, entityId);
    }

    public List<OntologyRelationship> findAllRelationships() {
        return jdbcTemplate.query("SELECT * FROM ecos_ontology_relationship", REL_MAPPER);
    }

    public Optional<OntologyRelationship> findRelationshipById(String id) {
        List<OntologyRelationship> list = jdbcTemplate.query(
            "SELECT * FROM ecos_ontology_relationship WHERE id = ?", REL_MAPPER, id);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public int insertRelationship(OntologyRelationship rel) {
        return jdbcTemplate.update("""
            INSERT INTO ecos_ontology_relationship (id, source_entity_id, target_entity_id, code, name, relationship_type, created_at)
            VALUES (?, ?, ?, ?, ?, ?, NOW())
            """, rel.getId(), rel.getSourceEntityId(), rel.getTargetEntityId(),
            rel.getCode(), rel.getName(), rel.getRelationshipType());
    }

    public int deleteRelationship(String id) {
        return jdbcTemplate.update(
            "UPDATE ecos_ontology_relationship SET is_deleted = 1, status = 'ARCHIVED', " +
            "updated_at = now(), update_by = 'system' WHERE id = ? AND is_deleted = 0", id);
    }

    public int deleteRelationshipsByEntity(String entityId) {
        return jdbcTemplate.update(
            "UPDATE ecos_ontology_relationship SET is_deleted = 1, status = 'ARCHIVED', " +
            "updated_at = now(), update_by = 'system' " +
            "WHERE (source_entity_id = ? OR target_entity_id = ?) AND is_deleted = 0",
            entityId, entityId);
    }

    // ═══════════════ OntologyAction CRUD (ecos_ontology_action 表 —— V200 已 DROP) ═══════════════════
    // V200：实体级动作表 ecos_ontology_action 已删除（幸存的动作形态见 action_definition，
    // 已由 ActionTypeDao 承接）。此处保签名 no-op：读返回空 / Optional.empty，写返回 0，
    // 避免 relation-does-not-exist 500（对象详情「动作」区会读本表）。

    public List<OntologyAction> findActionsByEntity(String entityId) {
        return java.util.Collections.emptyList();
    }

    public List<OntologyAction> findAllActions() {
        return java.util.Collections.emptyList();
    }

    public Optional<OntologyAction> findActionById(String id) {
        return Optional.empty();
    }

    public int insertAction(OntologyAction action) {
        return 0;
    }

    public int updateAction(String id, String code, String name, String actionType,
                            String description, String preconditions, String effects,
                            String ruleJson, String strategy, String status) {
        return 0;
    }

    public int deleteAction(String id) {
        return 0;
    }

    public int deleteActionsByEntity(String entityId) {
        return 0;
    }

    public List<OntologyEntity> findEntitiesByDomain(String domainCode) {
        return jdbcTemplate.query(
            "SELECT e.* FROM ecos_ontology_entity e " +
            "INNER JOIN ecos_ontology_domain d ON e.domain_id = d.id " +
            "WHERE d.code = ? ORDER BY e.sort_order",
            ENTITY_MAPPER, domainCode);
    }

    public List<OntologyEntity> findAllEntities() {
        return jdbcTemplate.query("SELECT * FROM ecos_ontology_entity ORDER BY sort_order", ENTITY_MAPPER);
    }

    public int updateEntityDomain(String entityId, String domainId) {
        return jdbcTemplate.update(
            "UPDATE ecos_ontology_entity SET domain_id = ?, updated_at = NOW() WHERE id = ?",
            domainId, entityId);
    }

    public List<Map<String, Object>> findAllRelationshipEdges() {
        var mapper = (RowMapper<Map<String, Object>>)(rs, rn) -> {
            var m = new LinkedHashMap<String, Object>();
            m.put("source", rs.getString("source_entity_id"));
            m.put("target", rs.getString("target_entity_id"));
            m.put("code", rs.getString("code"));
            return m;
        };
        return jdbcTemplate.query("SELECT source_entity_id, target_entity_id, code FROM ecos_ontology_relationship", mapper);
    }

    // ═══════════════ Ontology CRUD (ecos_ontology 表 —— V200 已 DROP) ═══════════════════
    // V200 治理把旧的本体主表 ecos_ontology 并入 ecos_ontology_domain（域表）。
    // 前端 (OntologyWorkbenchLayout / useOntologyData) 在 T8 已把 /ecos/ontologies
    // 降级为「域兜底源」，权威域源是 /api/v1/ontology/domains。此主表 CRUD 无实存读源，
    // 读路径返回空以保页面健康（不抛 relation-does-not-exist 500），写路径 no-op。
    // 组世代：如后续需单体业务能力，应改读 ecos_ontology_domain 或回 = E fetchOntologies 路径。

    public List<Map<String, Object>> findAllOntologies() {
        // V200: 表已 DROP，无实存源 —— 返回空列表（前端仅作域兜底，权威域源见 /ontology/domains）
        return java.util.Collections.emptyList();
    }

    public Map<String, Object> findOntologyById(String id) {
        // V200: 表已 DROP —— 无实存源
        return null;
    }

    public int insertOntology(String id, String code, String name, String description) {
        // V200: 表已 DROP —— no-op（主表概念并入域表，本路径无写源）
        return 0;
    }

    public int updateOntology(String id, String name, String description, String status) {
        // V200: 表已 DROP —— no-op
        return 0;
    }

    public int deleteOntology(String id) {
        // V200: 表已 DROP —— no-op
        return 0;
    }

    public List<Map<String, Object>> findRelationshipsByOntology(String ontologyId) {
        String sql = "SELECT r.id, r.source_entity_id, r.target_entity_id, r.code, r.name, r.relationship_type, r.created_at " +
                     "FROM ecos_ontology_relationship r " +
                     "JOIN ecos_ontology_entity se ON r.source_entity_id = se.id " +
                     "WHERE se.ontology_id = ? " +
                     "ORDER BY r.created_at DESC";
        return jdbcTemplate.queryForList(sql, ontologyId);
    }
}
