package com.chinacreator.gzcm.dccheng.ontology;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * JdbcTemplate 仓库 — 本体持久化 CRUD（实体/属性/关系/动作）
 */
@Repository
public class OntologyRepository {

    private final JdbcTemplate jdbc;

    public OntologyRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ═══════════════ Entity RowMapper ═══════════════════

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

    private static java.time.LocalDateTime ts(Timestamp t) {
        return t != null ? t.toLocalDateTime() : null;
    }

    // ═══════════════ Entity CRUD ═══════════════════

    public List<OntologyEntity> findEntitiesByOntology(String ontologyId) {
        return jdbc.query(
            "SELECT * FROM ecos_ontology_entity WHERE ontology_id = ? ORDER BY sort_order",
            ENTITY_MAPPER, ontologyId);
    }

    public Optional<OntologyEntity> findEntityById(String id) {
        List<OntologyEntity> list = jdbc.query(
            "SELECT * FROM ecos_ontology_entity WHERE id = ?", ENTITY_MAPPER, id);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public int insertEntity(OntologyEntity entity) {
        return jdbc.update("""
            INSERT INTO ecos_ontology_entity (id, ontology_id, code, name, description, entity_type, sort_order, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
            """, entity.getId(), entity.getOntologyId(), entity.getCode(),
            entity.getName(), entity.getDescription(), entity.getEntityType(), entity.getSortOrder());
    }

    public int updateEntity(String id, String code, String name, String description, String entityType) {
        return jdbc.update("""
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
        return jdbc.update("DELETE FROM ecos_ontology_entity WHERE id = ?", id);
    }

    // ═══════════════ Property CRUD ═══════════════════

    public List<OntologyProperty> findPropertiesByEntity(String entityId) {
        return jdbc.query(
            "SELECT * FROM ecos_ontology_property WHERE entity_id = ? ORDER BY sort_order",
            PROP_MAPPER, entityId);
    }

    public Optional<OntologyProperty> findPropertyById(String id) {
        List<OntologyProperty> list = jdbc.query(
            "SELECT * FROM ecos_ontology_property WHERE id = ?", PROP_MAPPER, id);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public int insertProperty(OntologyProperty prop) {
        return jdbc.update("""
            INSERT INTO ecos_ontology_property (id, entity_id, code, name, property_type, required_flag, searchable_flag, sort_order, function_type, function_expression, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())
            """, prop.getId(), prop.getEntityId(), prop.getCode(), prop.getName(),
            prop.getPropertyType(), prop.getRequiredFlag(), prop.getSearchableFlag(),
            prop.getSortOrder(),
            prop.getFunctionType(), prop.getFunctionExpression());
    }

    public int updateProperty(String id, String code, String name, String propertyType,
                               Integer requiredFlag, Integer searchableFlag,
                               String functionType, String functionExpression) {
        return jdbc.update("""
            UPDATE ecos_ontology_property SET
                code = COALESCE(?, code),
                name = COALESCE(?, name),
                property_type = COALESCE(?, property_type),
                required_flag = COALESCE(?, required_flag),
                searchable_flag = COALESCE(?, searchable_flag),
                function_type = COALESCE(?, function_type),
                function_expression = COALESCE(?, function_expression)
            WHERE id = ?
            """, code, name, propertyType, requiredFlag, searchableFlag,
            functionType, functionExpression, id);
    }

    public int deleteProperty(String id) {
        return jdbc.update("DELETE FROM ecos_ontology_property WHERE id = ?", id);
    }

    public int deletePropertiesByEntity(String entityId) {
        return jdbc.update("DELETE FROM ecos_ontology_property WHERE entity_id = ?", entityId);
    }

    // ═══════════════ Relationship CRUD ═══════════════════

    public List<OntologyRelationship> findRelationshipsByEntity(String entityId) {
        return jdbc.query(
            "SELECT * FROM ecos_ontology_relationship WHERE source_entity_id = ? OR target_entity_id = ?",
            REL_MAPPER, entityId, entityId);
    }

    public List<OntologyRelationship> findAllRelationships() {
        return jdbc.query("SELECT * FROM ecos_ontology_relationship", REL_MAPPER);
    }

    public Optional<OntologyRelationship> findRelationshipById(String id) {
        List<OntologyRelationship> list = jdbc.query(
            "SELECT * FROM ecos_ontology_relationship WHERE id = ?", REL_MAPPER, id);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public int insertRelationship(OntologyRelationship rel) {
        return jdbc.update("""
            INSERT INTO ecos_ontology_relationship (id, source_entity_id, target_entity_id, code, name, relationship_type, created_at)
            VALUES (?, ?, ?, ?, ?, ?, NOW())
            """, rel.getId(), rel.getSourceEntityId(), rel.getTargetEntityId(),
            rel.getCode(), rel.getName(), rel.getRelationshipType());
    }

    public int deleteRelationship(String id) {
        return jdbc.update("DELETE FROM ecos_ontology_relationship WHERE id = ?", id);
    }

    public int deleteRelationshipsByEntity(String entityId) {
        return jdbc.update(
            "DELETE FROM ecos_ontology_relationship WHERE source_entity_id = ? OR target_entity_id = ?",
            entityId, entityId);
    }

    // ═══════════════ Action CRUD ═══════════════════

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

    public List<OntologyAction> findActionsByEntity(String entityId) {
        return jdbc.query(
            "SELECT * FROM ecos_ontology_action WHERE entity_id = ? ORDER BY created_at",
            ACTION_MAPPER, entityId);
    }

    public List<OntologyAction> findAllActions() {
        return jdbc.query("SELECT * FROM ecos_ontology_action ORDER BY created_at", ACTION_MAPPER);
    }

    public Optional<OntologyAction> findActionById(String id) {
        List<OntologyAction> list = jdbc.query(
            "SELECT * FROM ecos_ontology_action WHERE id = ?", ACTION_MAPPER, id);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public int insertAction(OntologyAction action) {
        return jdbc.update("""
            INSERT INTO ecos_ontology_action (id, entity_id, code, name, action_type, description, preconditions, effects, rule_json, strategy, status, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
            """, action.getId(), action.getEntityId(), action.getCode(), action.getName(),
            action.getActionType(), action.getDescription(), action.getPreconditions(), action.getEffects(),
            action.getRuleJson(), action.getStrategy(), action.getStatus());
    }

    public int updateAction(String id, String code, String name, String actionType,
                            String description, String preconditions, String effects,
                            String ruleJson, String strategy, String status) {
        return jdbc.update("""
            UPDATE ecos_ontology_action SET
                code = COALESCE(?, code),
                name = COALESCE(?, name),
                action_type = COALESCE(?, action_type),
                description = COALESCE(?, description),
                preconditions = COALESCE(?, preconditions),
                effects = COALESCE(?, effects),
                rule_json = COALESCE(?, rule_json),
                strategy = COALESCE(?, strategy),
                status = COALESCE(?, status),
                updated_at = NOW()
            WHERE id = ?
            """, code, name, actionType, description, preconditions, effects,
            ruleJson, strategy, status, id);
    }

    public int deleteAction(String id) {
        return jdbc.update("DELETE FROM ecos_ontology_action WHERE id = ?", id);
    }

    public int deleteActionsByEntity(String entityId) {
        return jdbc.update("DELETE FROM ecos_ontology_action WHERE entity_id = ?", entityId);
    }

    // ═══════════════ Domain-level Entity Queries ═══════════════════

    public List<OntologyEntity> findEntitiesByDomain(String domainCode) {
        return jdbc.query(
            "SELECT e.* FROM ecos_ontology_entity e " +
            "INNER JOIN ecos_domain d ON e.domain_id = d.id " +
            "WHERE d.code = ? ORDER BY e.sort_order",
            ENTITY_MAPPER, domainCode);
    }

    public List<OntologyEntity> findAllEntities() {
        return jdbc.query("SELECT * FROM ecos_ontology_entity ORDER BY sort_order", ENTITY_MAPPER);
    }

    /**
     * 更新实体的归属域。
     */
    public int updateEntityDomain(String entityId, String domainId) {
        return jdbc.update(
            "UPDATE ecos_ontology_entity SET domain_id = ?, updated_at = NOW() WHERE id = ?",
            domainId, entityId);
    }

    // ═══════════════ Cycle Detection (DFS) ═══════════════════

    public List<Map<String, Object>> findAllRelationshipEdges() {
        var mapper = (RowMapper<Map<String, Object>>)(rs, rn) -> {
            var m = new java.util.LinkedHashMap<String, Object>();
            m.put("source", rs.getString("source_entity_id"));
            m.put("target", rs.getString("target_entity_id"));
            m.put("code", rs.getString("code"));
            return m;
        };
        return jdbc.query("SELECT source_entity_id, target_entity_id, code FROM ecos_ontology_relationship", mapper);
    }

    // ═══════════════ Helper methods ═══════════════════

    // ═══════════════ Ontology CRUD (ecos_ontology 表) ═══════════════════

    public List<Map<String, Object>> findAllOntologies() {
        return jdbc.queryForList("SELECT id, code, name, version, status, description, created_at, updated_at FROM ecos_ontology ORDER BY created_at DESC");
    }

    public Map<String, Object> findOntologyById(String id) {
        List<Map<String, Object>> list = jdbc.queryForList("SELECT id, code, name, version, status, description, created_at, updated_at FROM ecos_ontology WHERE id = ?", id);
        return list.isEmpty() ? null : list.get(0);
    }

    public int insertOntology(String id, String code, String name, String description) {
        return jdbc.update("INSERT INTO ecos_ontology (id, code, name, version, status, description, created_at, updated_at) VALUES (?,?,?,?,?,?,now(),now())",
            id, code, name, "1.0", "DRAFT", description);
    }

    public int updateOntology(String id, String name, String description, String status) {
        return jdbc.update("UPDATE ecos_ontology SET name = COALESCE(?, name), description = COALESCE(?, description), status = COALESCE(?, status), updated_at = now() WHERE id = ?",
            name, description, status, id);
    }

    public int deleteOntology(String id) {
        return jdbc.update("DELETE FROM ecos_ontology WHERE id = ?", id);
    }

    // ═══════════════ Ontology-scoped relationships ═══════════════════

    public List<Map<String, Object>> findRelationshipsByOntology(String ontologyId) {
        String sql = "SELECT r.id, r.source_entity_id, r.target_entity_id, r.code, r.name, r.relationship_type, r.created_at " +
                     "FROM ecos_ontology_relationship r " +
                     "JOIN ecos_ontology_entity se ON r.source_entity_id = se.id " +
                     "WHERE se.ontology_id = ? " +
                     "ORDER BY r.created_at DESC";
        return jdbc.queryForList(sql, ontologyId);
    }

    private static int getIntSafe(java.sql.ResultSet rs, String col) throws java.sql.SQLException {
        int val = rs.getInt(col);
        return rs.wasNull() ? 0 : val;
    }

    private static Integer getIntegerSafe(java.sql.ResultSet rs, String col) throws java.sql.SQLException {
        int val = rs.getInt(col);
        return rs.wasNull() ? null : val;
    }

    private static Double getDoubleSafe(java.sql.ResultSet rs, String col) throws java.sql.SQLException {
        double val = rs.getDouble(col);
        return rs.wasNull() ? null : val;
    }
}
