package com.chinacreator.gzcm.engine.ontology.repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.chinacreator.gzcm.engine.ontology.dao.OntologyDao;
import com.chinacreator.gzcm.engine.ontology.model.OntologyEntity;
import com.chinacreator.gzcm.engine.ontology.model.OntologyProperty;
import com.chinacreator.gzcm.engine.ontology.model.OntologyRelationship;
import com.chinacreator.gzcm.engine.ontology.model.OntologyAction;
import org.springframework.stereotype.Repository;

/**
 * 本体持久化 CRUD 仓库（实体/属性/关系/动作 + ontology 主表 + 关系边）。
 *
 * <p>R2/B3c：JDBC / RowMapper 全量下沉至 {@link OntologyDao}，本类仅保留公开 API 透传。
 */
@Repository
public class OntologyRepository {

    private final OntologyDao dao;

    public OntologyRepository(OntologyDao dao) {
        this.dao = dao;
    }

    // ═══════════════ Entity CRUD ═══════════════════

    /**
     * 生成下一个业务主键（{@code prefix} + 数字后缀）。
     *
     * <p>基于目标表内既有 ID 的最大数字后缀自增，替代原内存自增计数器 —— 后者在进程重启后
     * 会从头计数，与库内既有 {@code ent501/prop501/rel501} 撞主键，导致属性/实体新增失败。
     * <p>表名来自 DAO 内部的受限枚举校验，不接受外部输入，无注入面。
     *
     * @param prefix 主键前缀（ent / prop / rel）
     * @param table  目标表名（须在白名单内）
     * @return 下一个可用主键，如 {@code prop502}
     */
    public String nextId(String prefix, String table) {
        return dao.nextId(prefix, table);
    }

    public List<OntologyEntity> findEntitiesByOntology(String ontologyId) {
        return dao.findEntitiesByOntology(ontologyId);
    }

    public Optional<OntologyEntity> findEntityById(String id) {
        return dao.findEntityById(id);
    }

    public int insertEntity(OntologyEntity entity) {
        return dao.insertEntity(entity);
    }

    public int updateEntity(String id, String code, String name, String description, String entityType) {
        return dao.updateEntity(id, code, name, description, entityType);
    }

    /** 逻辑删除 (Wave B-3 T17)：原 DELETE FROM → UPDATE SET is_deleted=1, status='ARCHIVED'。 */
    public int deleteEntity(String id) {
        return dao.deleteEntity(id);
    }

    // ═══════════════ Property CRUD ═══════════════════

    public List<OntologyProperty> findPropertiesByEntity(String entityId) {
        return dao.findPropertiesByEntity(entityId);
    }

    public Optional<OntologyProperty> findPropertyById(String id) {
        return dao.findPropertyById(id);
    }

    public int insertProperty(OntologyProperty prop) {
        return dao.insertProperty(prop);
    }

    /**
     * 更新属性。
     *
     * <p>COALESCE 语义：入参为 null 表示不更新该字段。注意 {@code description} 允许清空 ——
     * 前端以空串表达「清空描述」，空串非 null，可正常覆盖。
     */
    public int updateProperty(String id, String code, String name, String description, String propertyType,
                               Integer requiredFlag, Integer searchableFlag, Integer uniqueFlag,
                               String functionType, String functionExpression) {
        return dao.updateProperty(id, code, name, description, propertyType,
            requiredFlag, searchableFlag, uniqueFlag, functionType, functionExpression);
    }

    /** 逻辑删除 property (T17)：按 id 单行逻辑删除。 */
    public int deleteProperty(String id) {
        return dao.deleteProperty(id);
    }

    /** 逻辑删除 property (T17)：级联删除某实体下全部属性。 */
    public int deletePropertiesByEntity(String entityId) {
        return dao.deletePropertiesByEntity(entityId);
    }

    // ═══════════════ Relationship CRUD ═══════════════════

    public List<OntologyRelationship> findRelationshipsByEntity(String entityId) {
        return dao.findRelationshipsByEntity(entityId);
    }

    public List<OntologyRelationship> findAllRelationships() {
        return dao.findAllRelationships();
    }

    public Optional<OntologyRelationship> findRelationshipById(String id) {
        return dao.findRelationshipById(id);
    }

    public int insertRelationship(OntologyRelationship rel) {
        return dao.insertRelationship(rel);
    }

    /** 逻辑删除 relationship (T17)：按 id 单行逻辑删除。 */
    public int deleteRelationship(String id) {
        return dao.deleteRelationship(id);
    }

    /** 逻辑删除 relationship (T17)：级联删除某实体的 source/target 双向关系。 */
    public int deleteRelationshipsByEntity(String entityId) {
        return dao.deleteRelationshipsByEntity(entityId);
    }

    // ═══════════════ Action CRUD ═══════════════════

    public List<OntologyAction> findActionsByEntity(String entityId) {
        return dao.findActionsByEntity(entityId);
    }

    public List<OntologyAction> findAllActions() {
        return dao.findAllActions();
    }

    public Optional<OntologyAction> findActionById(String id) {
        return dao.findActionById(id);
    }

    public int insertAction(OntologyAction action) {
        return dao.insertAction(action);
    }

    public int updateAction(String id, String code, String name, String actionType,
                            String description, String preconditions, String effects,
                            String ruleJson, String strategy, String status) {
        return dao.updateAction(id, code, name, actionType, description, preconditions,
            effects, ruleJson, strategy, status);
    }

    /** 逻辑删除 action (T17)：按 id 单行逻辑删除。 */
    public int deleteAction(String id) {
        return dao.deleteAction(id);
    }

    /** 逻辑删除 action (T17)：级联删除某实体下全部动作。 */
    public int deleteActionsByEntity(String entityId) {
        return dao.deleteActionsByEntity(entityId);
    }

    // ═══════════════ Domain-level Entity Queries ═══════════════════

    public List<OntologyEntity> findEntitiesByDomain(String domainCode) {
        return dao.findEntitiesByDomain(domainCode);
    }

    public List<OntologyEntity> findAllEntities() {
        return dao.findAllEntities();
    }

    /**
     * 更新实体的归属域。
     */
    public int updateEntityDomain(String entityId, String domainId) {
        return dao.updateEntityDomain(entityId, domainId);
    }

    // ═══════════════ Cycle Detection (DFS) ═══════════════════

    public List<Map<String, Object>> findAllRelationshipEdges() {
        return dao.findAllRelationshipEdges();
    }

    // ═══════════════ Ontology CRUD (ecos_ontology 表) ═══════════════════

    public List<Map<String, Object>> findAllOntologies() {
        return dao.findAllOntologies();
    }

    public Map<String, Object> findOntologyById(String id) {
        return dao.findOntologyById(id);
    }

    public int insertOntology(String id, String code, String name, String description) {
        return dao.insertOntology(id, code, name, description);
    }

    public int updateOntology(String id, String name, String description, String status) {
        return dao.updateOntology(id, name, description, status);
    }

    /** 逻辑删除 ontology (T17)：原 DELETE → UPDATE SET is_deleted=1, status='ARCHIVED'。 */
    public int deleteOntology(String id) {
        return dao.deleteOntology(id);
    }

    // ═══════════════ Ontology-scoped relationships ═══════════════════

    public List<Map<String, Object>> findRelationshipsByOntology(String ontologyId) {
        return dao.findRelationshipsByOntology(ontologyId);
    }
}
