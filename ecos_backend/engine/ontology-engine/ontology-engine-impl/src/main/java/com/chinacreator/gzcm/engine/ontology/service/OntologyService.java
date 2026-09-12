package com.chinacreator.gzcm.engine.ontology.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import com.chinacreator.gzcm.engine.ontology.dto.OntologyEntityDependenciesVO;
import com.chinacreator.gzcm.engine.ontology.dto.OntologyEntityDetailVO;
import com.chinacreator.gzcm.engine.ontology.dto.OntologyEntitySaveDTO;
import com.chinacreator.gzcm.engine.ontology.dto.OntologyEntityVO;
import com.chinacreator.gzcm.engine.ontology.dto.OntologyPropertySaveDTO;
import com.chinacreator.gzcm.engine.ontology.dto.OntologyPropertyVO;
import com.chinacreator.gzcm.engine.ontology.dto.OntologyRelationshipGraphVO;
import com.chinacreator.gzcm.engine.ontology.dto.OntologyRelationshipSaveDTO;
import com.chinacreator.gzcm.engine.ontology.dto.OntologyRelationshipValidateVO;
import com.chinacreator.gzcm.engine.ontology.dto.OntologyRelationshipVO;
import com.chinacreator.gzcm.engine.ontology.dto.OntologySaveDTO;
import com.chinacreator.gzcm.engine.ontology.dto.OntologyVO;
import com.chinacreator.gzcm.engine.ontology.model.OntologyEntity;
import com.chinacreator.gzcm.engine.ontology.model.OntologyProperty;
import com.chinacreator.gzcm.engine.ontology.model.OntologyRelationship;
import com.chinacreator.gzcm.engine.ontology.model.OntologyRule;
import com.chinacreator.gzcm.engine.ontology.model.OntologyAction;
import com.chinacreator.gzcm.engine.ontology.model.OntologyDomain;
import com.chinacreator.gzcm.engine.ontology.model.OntologyVersion;
import com.chinacreator.gzcm.engine.ontology.repository.OntologyRepository;
import com.chinacreator.gzcm.engine.ontology.repository.OntologyRuleRepository;
import com.chinacreator.gzcm.engine.ontology.repository.OntologyDomainRepository;
import com.chinacreator.gzcm.engine.ontology.repository.OntologyVersionRepository;
import com.chinacreator.gzcm.engine.ontology.repository.OntologyMappingStore;

/**
 * 本体业务服务 — 将 Entity/Property/Relationship 转为 Map 返回给 Controller
 */
@Service
public class OntologyService {

    private static final Logger log = LoggerFactory.getLogger(OntologyService.class);
    private static final AtomicInteger ID_SEQ = new AtomicInteger(500);

    private final OntologyRepository repository;
    private final OntologyMappingStore mappingStore;

    public OntologyService(OntologyRepository repository, OntologyMappingStore mappingStore) {
        this.repository = repository;
        this.mappingStore = mappingStore;
    }

    private String nextId() { return String.valueOf(ID_SEQ.incrementAndGet()); }

    // ═══════════════ Strong-Typed Facades (T16-1, 2026-09-12) ═══════════════════
    // 旧 Map 签名方法保留（Wave31 / OntologyProposalController / OntologyDomainApiController 调用方）；
    // 新增 DTO/VO 签名作 Controller 入参出参唯一消费路径。字段与语义与旧 method 完全一致。

    // ═══════════════ Entity (强类型路径) ═══════════════════

    public List<OntologyEntityVO> listEntitiesVO(String ontologyId) {
        return repository.findEntitiesByOntology(ontologyId).stream()
            .map(this::entityToVO)
            .collect(Collectors.toList());
    }

    public OntologyEntityVO createEntity(String ontologyId, OntologyEntitySaveDTO dto) {
        OntologyEntity entity = new OntologyEntity();
        String id = "ent" + nextId();
        entity.setId(id);
        entity.setOntologyId(ontologyId);
        entity.setCode(dto.getCode() != null ? dto.getCode() : "");
        entity.setName(dto.getName() != null ? dto.getName() : "");
        entity.setDescription(dto.getDescription() != null ? dto.getDescription() : "");
        entity.setEntityType(dto.getEntityType() != null ? dto.getEntityType() : "MASTER");
        entity.setSortOrder(1);
        try {
            repository.insertEntity(entity);
        } catch (DuplicateKeyException e) {
            log.warn("Entity unique constraint violation: {} in ontology {}", entity.getCode(), ontologyId);
            throw e;
        }
        log.info("Ontology entity created: {} [{}]", id, entity.getCode());
        return entityToVO(entity);
    }

    public Optional<OntologyEntityVO> updateEntity(String entityId, OntologyEntitySaveDTO dto) {
        return repository.findEntityById(entityId).map(existing -> {
            repository.updateEntity(entityId, dto.getCode(), dto.getName(),
                dto.getDescription(), dto.getEntityType());
            return repository.findEntityById(entityId).map(this::entityToVO).orElse(null);
        });
    }

    public OntologyEntityDetailVO getEntityDetailVO(String entityId) {
        return repository.findEntityById(entityId).map(entity -> {
            OntologyEntityDetailVO vo = new OntologyEntityDetailVO();
            vo.setId(entity.getId());
            vo.setOntologyId(entity.getOntologyId());
            vo.setCode(entity.getCode());
            vo.setName(entity.getName());
            vo.setDescription(entity.getDescription());
            vo.setEntityType(entity.getEntityType());
            vo.setDomainId(entity.getDomainId());
            vo.setSortOrder(entity.getSortOrder());
            vo.setMapping(mappingStore.store.get(entity.getId()));
            vo.setCreatedAt(entity.getCreatedAt() != null ? entity.getCreatedAt().toString() : null);
            vo.setUpdatedAt(entity.getUpdatedAt() != null ? entity.getUpdatedAt().toString() : null);
            vo.setProperties(listProperties(entityId));
            vo.setRelationships(listEntityRelationships(entityId));
            vo.setRules(new java.util.ArrayList<>());
            vo.setActions(new java.util.ArrayList<>());
            return vo;
        }).orElse(null);
    }

    public OntologyEntityDependenciesVO getEntityDependenciesVO(String entityId) {
        OntologyEntityDependenciesVO vo = new OntologyEntityDependenciesVO();
        vo.setEntityId(entityId);
        List<OntologyRelationship> rels = repository.findRelationshipsByEntity(entityId);
        java.util.Set<String> related = new java.util.HashSet<>();
        for (OntologyRelationship r : rels) {
            if (!entityId.equals(r.getSourceEntityId())) related.add(r.getSourceEntityId());
            if (!entityId.equals(r.getTargetEntityId())) related.add(r.getTargetEntityId());
        }
        vo.setRelatedEntities(new java.util.ArrayList<>(related));
        vo.setPropertyCount(listProperties(entityId).size());
        vo.setRelationshipCount(rels.size());
        vo.setCascadingDeletes(List.of("properties", "relationships", "actions"));
        return vo;
    }

    // ═══════════════ Property (强类型路径) ═══════════════════

    public List<OntologyPropertyVO> listPropertiesVO(String entityId) {
        return repository.findPropertiesByEntity(entityId).stream()
            .map(this::propToVO)
            .collect(Collectors.toList());
    }

    public OntologyPropertyVO createProperty(String entityId, OntologyPropertySaveDTO dto) {
        OntologyProperty prop = new OntologyProperty();
        String id = "prop" + nextId();
        prop.setId(id);
        prop.setEntityId(entityId);
        prop.setCode(dto.getCode() != null ? dto.getCode() : "");
        prop.setName(dto.getName() != null ? dto.getName() : "");
        prop.setPropertyType(dto.getPropertyType() != null ? dto.getPropertyType() : "STRING");
        prop.setRequiredFlag(dto.getRequiredFlag() != null ? dto.getRequiredFlag() : 0);
        prop.setSearchableFlag(dto.getSearchableFlag() != null ? dto.getSearchableFlag() : 0);
        prop.setUniqueFlag(dto.getUniqueFlag() != null ? dto.getUniqueFlag() : 0);
        prop.setSortOrder(1);
        prop.setEnumValues(dto.getEnumValues() != null ? dto.getEnumValues() : "");
        prop.setDefaultValue(dto.getDefaultValue() != null ? dto.getDefaultValue() : "");
        prop.setValidationRule(dto.getValidationRule() != null ? dto.getValidationRule() : "");
        prop.setRefEntityCode(dto.getRefEntityCode() != null ? dto.getRefEntityCode() : "");
        prop.setMaxLength(dto.getMaxLength());
        prop.setMinValue(dto.getMinValue());
        prop.setMaxValue(dto.getMaxValue());
        prop.setFunctionType(dto.getFunctionType() != null ? dto.getFunctionType() : "");
        prop.setFunctionExpression(dto.getFunctionExpression() != null ? dto.getFunctionExpression() : "");
        try {
            repository.insertProperty(prop);
        } catch (DuplicateKeyException e) {
            log.warn("Property unique constraint violation: {} on entity {}", prop.getCode(), entityId);
            throw e;
        }
        log.info("Property created: {} [{}] for entity {}", id, prop.getCode(), entityId);
        return propToVO(prop);
    }

    public Optional<OntologyPropertyVO> updateProperty(String propId, OntologyPropertySaveDTO dto) {
        return repository.findPropertyById(propId).map(existing -> {
            repository.updateProperty(propId, dto.getCode(), dto.getName(), dto.getPropertyType(),
                dto.getRequiredFlag(), dto.getSearchableFlag(),
                dto.getFunctionType(), dto.getFunctionExpression());
            return repository.findPropertyById(propId).map(this::propToVO).orElse(null);
        });
    }

    // ═══════════════ Relationship (强类型路径) ═══════════════════

    public List<OntologyRelationshipVO> listEntityRelationshipsVO(String entityId) {
        return repository.findRelationshipsByEntity(entityId).stream()
            .map(this::relToVO)
            .collect(Collectors.toList());
    }

    public List<OntologyRelationshipVO> listAllRelationshipsVO() {
        return repository.findAllRelationships().stream()
            .map(this::relToVO)
            .collect(Collectors.toList());
    }

    public OntologyRelationshipVO createRelationship(String sourceEntityId, OntologyRelationshipSaveDTO dto) {
        OntologyRelationship rel = new OntologyRelationship();
        String id = "rel" + nextId();
        rel.setId(id);
        rel.setSourceEntityId(sourceEntityId);
        rel.setTargetEntityId(dto.getTargetEntityId() != null ? dto.getTargetEntityId() : "");
        rel.setCode(dto.getCode() != null ? dto.getCode() : "");
        rel.setName(dto.getName() != null ? dto.getName() : "");
        rel.setRelationshipType(dto.getRelationshipType() != null ? dto.getRelationshipType() : "ONE_TO_MANY");
        try {
            repository.insertRelationship(rel);
        } catch (DuplicateKeyException e) {
            log.warn("Relationship unique constraint violation: {} {}→{}",
                    rel.getCode(), sourceEntityId, rel.getTargetEntityId());
            throw e;
        }
        log.info("Relationship created: {} [{}] {}→{}", id, rel.getCode(), sourceEntityId, rel.getTargetEntityId());
        return relToVO(rel);
    }

    /** 强类型版本的关系校验（仅 source/target 两个 String，无 Map 中间结构）。 */
    public OntologyRelationshipValidateVO validateRelationshipVO(String sourceEntityId, String targetEntityId) {
        boolean hasCycle = validateAsBoolean(sourceEntityId, targetEntityId);
        OntologyRelationshipValidateVO vo = new OntologyRelationshipValidateVO();
        vo.setHasCycle(hasCycle);
        vo.setMessage(hasCycle ? "ONT-003: Relationship would introduce a cycle" : "Relationship is valid");
        return vo;
    }

    /** 关系图谱（强类型版）。 */
    public OntologyRelationshipGraphVO getRelationshipGraphVO() {
        List<Map<String, Object>> edges = repository.findAllRelationshipEdges();
        java.util.Set<String> nodeIds = new java.util.LinkedHashSet<>();
        for (Map<String, Object> e : edges) {
            nodeIds.add(String.valueOf(e.get("source")));
            nodeIds.add(String.valueOf(e.get("target")));
        }
        OntologyRelationshipGraphVO vo = new OntologyRelationshipGraphVO();
        List<OntologyRelationshipGraphVO.GraphNode> nodes = nodeIds.stream().map(id -> {
            OntologyRelationshipGraphVO.GraphNode n = new OntologyRelationshipGraphVO.GraphNode();
            n.setId(id);
            return n;
        }).collect(Collectors.toList());
        List<OntologyRelationshipGraphVO.GraphEdge> edgeVos = edges.stream().map(e -> {
            OntologyRelationshipGraphVO.GraphEdge edgeVo = new OntologyRelationshipGraphVO.GraphEdge();
            edgeVo.setSource(String.valueOf(e.get("source")));
            edgeVo.setTarget(String.valueOf(e.get("target")));
            edgeVo.setCode(String.valueOf(e.get("code")));
            return edgeVo;
        }).collect(Collectors.toList());
        vo.setNodes(nodes);
        vo.setEdges(edgeVos);
        return vo;
    }

    // ═══════════════ Ontology (强类型路径) ═══════════════════

    public List<OntologyVO> listOntologiesVO() {
        return repository.findAllOntologies().stream()
            .map(this::ontologyToVO)
            .collect(Collectors.toList());
    }

    public OntologyVO createOntology(OntologySaveDTO dto) {
        String id = java.util.UUID.randomUUID().toString().substring(0, 8);
        String code = dto.getCode() != null ? dto.getCode() : "";
        String name = dto.getName() != null ? dto.getName() : "";
        String description = dto.getDescription() != null ? dto.getDescription() : "";
        repository.insertOntology(id, code, name, description);
        log.info("Ontology created: {} [{}]", id, code);
        return ontologyToVO(repository.findOntologyById(id));
    }

    public Optional<OntologyVO> updateOntology(String id, OntologySaveDTO dto) {
        Map<String, Object> existing = repository.findOntologyById(id);
        if (existing == null) return Optional.empty();
        repository.updateOntology(id, dto.getName(), dto.getDescription(), dto.getStatus());
        return Optional.ofNullable(ontologyToVO(repository.findOntologyById(id)));
    }

    // ═══════════════ Strong-Typed Converters (T16-1) ═══════════════════

    private OntologyEntityVO entityToVO(OntologyEntity e) {
        OntologyEntityVO vo = new OntologyEntityVO();
        vo.setId(e.getId());
        vo.setOntologyId(e.getOntologyId());
        vo.setCode(e.getCode());
        vo.setName(e.getName());
        vo.setDescription(e.getDescription());
        vo.setEntityType(e.getEntityType());
        vo.setDomainId(e.getDomainId());
        vo.setSortOrder(e.getSortOrder());
        vo.setMapping(mappingStore.store.get(e.getId()));
        vo.setCreatedAt(e.getCreatedAt() != null ? e.getCreatedAt().toString() : null);
        vo.setUpdatedAt(e.getUpdatedAt() != null ? e.getUpdatedAt().toString() : null);
        return vo;
    }

    private OntologyPropertyVO propToVO(OntologyProperty p) {
        OntologyPropertyVO vo = new OntologyPropertyVO();
        vo.setId(p.getId());
        vo.setEntityId(p.getEntityId());
        vo.setCode(p.getCode());
        vo.setName(p.getName());
        vo.setPropertyType(p.getPropertyType());
        vo.setRequiredFlag(p.getRequiredFlag());
        vo.setSearchableFlag(p.getSearchableFlag());
        vo.setUniqueFlag(p.getUniqueFlag());
        vo.setSortOrder(p.getSortOrder());
        vo.setEnumValues(p.getEnumValues());
        vo.setDefaultValue(p.getDefaultValue());
        vo.setValidationRule(p.getValidationRule());
        vo.setRefEntityCode(p.getRefEntityCode());
        vo.setMaxLength(p.getMaxLength());
        vo.setMinValue(p.getMinValue());
        vo.setMaxValue(p.getMaxValue());
        vo.setFunctionType(p.getFunctionType());
        vo.setFunctionExpression(p.getFunctionExpression());
        vo.setCreatedAt(p.getCreatedAt() != null ? p.getCreatedAt().toString() : null);
        vo.setUpdatedAt(p.getUpdatedAt() != null ? p.getUpdatedAt().toString() : null);
        return vo;
    }

    private OntologyRelationshipVO relToVO(OntologyRelationship r) {
        OntologyRelationshipVO vo = new OntologyRelationshipVO();
        vo.setId(r.getId());
        vo.setSourceEntityId(r.getSourceEntityId());
        vo.setTargetEntityId(r.getTargetEntityId());
        vo.setCode(r.getCode());
        vo.setName(r.getName());
        vo.setRelationshipType(r.getRelationshipType());
        vo.setCreatedAt(r.getCreatedAt() != null ? r.getCreatedAt().toString() : null);
        return vo;
    }

    private OntologyVO ontologyToVO(Map<String, Object> raw) {
        if (raw == null) return null;
        OntologyVO vo = new OntologyVO();
        vo.setId(asString(raw.get("id")));
        vo.setCode(asString(raw.get("code")));
        vo.setName(asString(raw.get("name")));
        vo.setVersion(asString(raw.get("version")));
        vo.setDescription(asString(raw.get("description")));
        vo.setStatus(asString(raw.get("status")));
        vo.setCreatedAt(asString(raw.get("created_at")));
        vo.setUpdatedAt(asString(raw.get("updated_at")));
        return vo;
    }

    private static String asString(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    /** 内部辅助：跑一次 DFS 闭环检测，返回 boolean（与旧 validateRelationship 行为一致）。 */
    private boolean validateAsBoolean(String sourceEntityId, String targetEntityId) {
        List<Map<String, Object>> edges = repository.findAllRelationshipEdges();
        Map<String, Object> pending = new LinkedHashMap<>();
        pending.put("source", sourceEntityId);
        pending.put("target", targetEntityId);
        pending.put("code", "PENDING");
        edges.add(pending);
        java.util.Set<String> visited = new java.util.HashSet<>();
        java.util.Set<String> inStack = new java.util.HashSet<>();
        return hasCycleDFS(sourceEntityId, edges, visited, inStack);
    }

    // ═══════════════ Entity ═══════════════════

    public List<Map<String, Object>> listEntities(String ontologyId) {
        return repository.findEntitiesByOntology(ontologyId).stream()
            .map(this::entityToMap)
            .collect(Collectors.toList());
    }

    /**
     * 列出所有对象类型（ObjectType）— 返回全部实体，含 mapping + domainId 字段。
     * 供 /api/v1/ontology/objects 端点使用。
     */
    public List<Map<String, Object>> listAllObjects() {
        return repository.findAllEntities().stream()
            .map(this::entityToMap)
            .collect(Collectors.toList());
    }

    public Map<String, Object> createEntity(String ontologyId, Map<String, Object> body) {
        OntologyEntity entity = new OntologyEntity();
        String id = "ent" + nextId();
        entity.setId(id);
        entity.setOntologyId(ontologyId);
        entity.setCode(String.valueOf(body.getOrDefault("code", "")));
        entity.setName(String.valueOf(body.getOrDefault("name", "")));
        entity.setDescription(String.valueOf(body.getOrDefault("description", "")));
        entity.setEntityType(String.valueOf(body.getOrDefault("entityType", "MASTER")));
        entity.setSortOrder(1);
        try {
            repository.insertEntity(entity);
        } catch (DuplicateKeyException e) {
            // Wave-6 T-25: 唯一约束冲突（如 (ontology_id, code)）→ 409
            log.warn("Entity unique constraint violation: {} in ontology {}", entity.getCode(), ontologyId);
            throw e;
        }
        log.info("Ontology entity created: {} [{}]", id, entity.getCode());
        return entityToMap(entity);
    }

    public Optional<Map<String, Object>> updateEntity(String entityId, Map<String, Object> body) {
        return repository.findEntityById(entityId).map(existing -> {
            String code = body.containsKey("code") ? String.valueOf(body.get("code")) : null;
            String name = body.containsKey("name") ? String.valueOf(body.get("name")) : null;
            String description = body.containsKey("description") ? String.valueOf(body.get("description")) : null;
            String entityType = body.containsKey("entityType") ? String.valueOf(body.get("entityType")) : null;
            repository.updateEntity(entityId, code, name, description, entityType);
            return repository.findEntityById(entityId).map(this::entityToMap).orElse(null);
        });
    }

    /**
     * 删除实体 (B1 决策)：逻辑删除 — 物理 DELETE 已改 Repository 层 UPDATE (Wave B-3 T17)。
     * Service 方法签名与 4 次级联调用形态不变；仅 Repository 层 SQL 从 DELETE 改为
     * UPDATE SET is_deleted=1, status='ARCHIVED'。Wave31OntologyConvergenceTest C1
     * verify(ontRepo).deletePropertiesByEntity / deleteRelationshipsByEntity /
     * deleteActionsByEntity / deleteEntity 4 次调用零改动仍 PASS。
     */
    public boolean deleteEntity(String entityId) {
        Optional<OntologyEntity> existing = repository.findEntityById(entityId);
        if (existing.isEmpty()) return false;
        repository.deletePropertiesByEntity(entityId);
        repository.deleteRelationshipsByEntity(entityId);
        repository.deleteActionsByEntity(entityId);
        repository.deleteEntity(entityId);
        return true;
    }

    // ═══════════════ Property ═══════════════════

    public List<Map<String, Object>> listProperties(String entityId) {
        return repository.findPropertiesByEntity(entityId).stream()
            .map(this::propToMap)
            .collect(Collectors.toList());
    }

    public Map<String, Object> createProperty(String entityId, Map<String, Object> body) {
        OntologyProperty prop = new OntologyProperty();
        String id = "prop" + nextId();
        prop.setId(id);
        prop.setEntityId(entityId);
        prop.setCode(String.valueOf(body.getOrDefault("code", "")));
        prop.setName(String.valueOf(body.getOrDefault("name", "")));
        prop.setPropertyType(String.valueOf(body.getOrDefault("propertyType", "STRING")));
        prop.setRequiredFlag(toInt(body.getOrDefault("requiredFlag", 0)));
        prop.setSearchableFlag(toInt(body.getOrDefault("searchableFlag", 0)));
        prop.setUniqueFlag(toInt(body.getOrDefault("uniqueFlag", 0)));
        prop.setSortOrder(1);
        prop.setEnumValues(String.valueOf(body.getOrDefault("enumValues", "")));
        prop.setDefaultValue(String.valueOf(body.getOrDefault("defaultValue", "")));
        prop.setValidationRule(String.valueOf(body.getOrDefault("validationRule", "")));
        prop.setRefEntityCode(String.valueOf(body.getOrDefault("refEntityCode", "")));
        prop.setMaxLength(body.containsKey("maxLength") ? toInteger(body.get("maxLength")) : null);
        prop.setMinValue(body.containsKey("minValue") ? toDouble(body.get("minValue")) : null);
        prop.setMaxValue(body.containsKey("maxValue") ? toDouble(body.get("maxValue")) : null);
        prop.setFunctionType(String.valueOf(body.getOrDefault("functionType", "")));
        prop.setFunctionExpression(String.valueOf(body.getOrDefault("functionExpression", "")));
        try {
            repository.insertProperty(prop);
        } catch (DuplicateKeyException e) {
            // Wave-6 T-25: 唯一约束冲突（如 (entity_id, code)）→ 409
            log.warn("Property unique constraint violation: {} on entity {}", prop.getCode(), entityId);
            throw e;
        }
        log.info("Property created: {} [{}] for entity {}", id, prop.getCode(), entityId);
        return propToMap(prop);
    }

    public Optional<Map<String, Object>> updateProperty(String propId, Map<String, Object> body) {
        return repository.findPropertyById(propId).map(existing -> {
            String code = body.containsKey("code") ? String.valueOf(body.get("code")) : null;
            String name = body.containsKey("name") ? String.valueOf(body.get("name")) : null;
            String propertyType = body.containsKey("propertyType") ? String.valueOf(body.get("propertyType")) : null;
            Integer requiredFlag = body.containsKey("requiredFlag") ? toInt(body.get("requiredFlag")) : null;
            Integer searchableFlag = body.containsKey("searchableFlag") ? toInt(body.get("searchableFlag")) : null;
            Integer uniqueFlag = body.containsKey("uniqueFlag") ? toInt(body.get("uniqueFlag")) : null;
            String enumValues = body.containsKey("enumValues") ? String.valueOf(body.get("enumValues")) : null;
            String defaultValue = body.containsKey("defaultValue") ? String.valueOf(body.get("defaultValue")) : null;
            String validationRule = body.containsKey("validationRule") ? String.valueOf(body.get("validationRule")) : null;
            String refEntityCode = body.containsKey("refEntityCode") ? String.valueOf(body.get("refEntityCode")) : null;
            Integer maxLength = body.containsKey("maxLength") ? toInteger(body.get("maxLength")) : null;
            Double minValue = body.containsKey("minValue") ? toDouble(body.get("minValue")) : null;
            Double maxValue = body.containsKey("maxValue") ? toDouble(body.get("maxValue")) : null;
            String functionType = body.containsKey("functionType") ? String.valueOf(body.get("functionType")) : null;
            String functionExpression = body.containsKey("functionExpression") ? String.valueOf(body.get("functionExpression")) : null;
            repository.updateProperty(propId, code, name, propertyType, requiredFlag, searchableFlag,
                functionType, functionExpression);
            return repository.findPropertyById(propId).map(this::propToMap).orElse(null);
        });
    }

    public boolean deleteProperty(String propId) {
        return repository.deleteProperty(propId) > 0;
    }

    // ═══════════════ Relationship ═══════════════════

    public List<Map<String, Object>> listEntityRelationships(String entityId) {
        return repository.findRelationshipsByEntity(entityId).stream()
            .map(this::relToMap)
            .collect(Collectors.toList());
    }

    public List<Map<String, Object>> listAllRelationships() {
        return repository.findAllRelationships().stream()
            .map(this::relToMap)
            .collect(Collectors.toList());
    }

    public Map<String, Object> createRelationship(String sourceEntityId, Map<String, Object> body) {
        OntologyRelationship rel = new OntologyRelationship();
        String id = "rel" + nextId();
        rel.setId(id);
        rel.setSourceEntityId(sourceEntityId);
        rel.setTargetEntityId(String.valueOf(body.getOrDefault("targetEntityId", "")));
        rel.setCode(String.valueOf(body.getOrDefault("code", "")));
        rel.setName(String.valueOf(body.getOrDefault("name", "")));
        rel.setRelationshipType(String.valueOf(body.getOrDefault("relationshipType", "ONE_TO_MANY")));
        try {
            repository.insertRelationship(rel);
        } catch (DuplicateKeyException e) {
            // Wave-6 T-25: 唯一约束冲突（如 (sourceEntityId, targetEntityId, code)）→ 409
            log.warn("Relationship unique constraint violation: {} {}→{}",
                    rel.getCode(), sourceEntityId, rel.getTargetEntityId());
            throw e;
        }
        log.info("Relationship created: {} [{}] {}→{}", id, rel.getCode(), sourceEntityId, rel.getTargetEntityId());
        return relToMap(rel);
    }

    public boolean deleteRelationship(String relId) {
        return repository.deleteRelationship(relId) > 0;
    }

    // ═══════════════ Map Converters ═══════════════════

    private Map<String, Object> entityToMap(OntologyEntity e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getId());
        m.put("ontologyId", e.getOntologyId());
        m.put("code", e.getCode());
        m.put("name", e.getName());
        m.put("description", e.getDescription());
        m.put("entityType", e.getEntityType());
        m.put("domainId", e.getDomainId());
        m.put("mapping", mappingStore.store.get(e.getId())); // 关联映射配置
        m.put("sortOrder", e.getSortOrder());
        m.put("createdAt", e.getCreatedAt() != null ? e.getCreatedAt().toString() : null);
        m.put("updatedAt", e.getUpdatedAt() != null ? e.getUpdatedAt().toString() : null);
        return m;
    }

    private Map<String, Object> propToMap(OntologyProperty p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.getId());
        m.put("entityId", p.getEntityId());
        m.put("code", p.getCode());
        m.put("name", p.getName());
        m.put("propertyType", p.getPropertyType());
        m.put("requiredFlag", p.getRequiredFlag());
        m.put("searchableFlag", p.getSearchableFlag());
        m.put("uniqueFlag", p.getUniqueFlag());
        m.put("sortOrder", p.getSortOrder());
        m.put("enumValues", p.getEnumValues());
        m.put("defaultValue", p.getDefaultValue());
        m.put("validationRule", p.getValidationRule());
        m.put("refEntityCode", p.getRefEntityCode());
        m.put("maxLength", p.getMaxLength());
        m.put("minValue", p.getMinValue());
        m.put("maxValue", p.getMaxValue());
        m.put("functionType", p.getFunctionType());
        m.put("functionExpression", p.getFunctionExpression());
        m.put("createdAt", p.getCreatedAt() != null ? p.getCreatedAt().toString() : null);
        m.put("updatedAt", p.getUpdatedAt() != null ? p.getUpdatedAt().toString() : null);
        return m;
    }

    private Map<String, Object> relToMap(OntologyRelationship r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("sourceEntityId", r.getSourceEntityId());
        m.put("targetEntityId", r.getTargetEntityId());
        m.put("code", r.getCode());
        m.put("name", r.getName());
        m.put("relationshipType", r.getRelationshipType());
        m.put("createdAt", r.getCreatedAt() != null ? r.getCreatedAt().toString() : null);
        return m;
    }

    private static int toInt(Object val) {
        if (val instanceof Number) return ((Number) val).intValue();
        try { return Integer.parseInt(String.valueOf(val)); } catch (Exception e) { return 0; }
    }

    private static Integer toInteger(Object val) {
        if (val == null) return null;
        if (val instanceof Number) return ((Number) val).intValue();
        try { return Integer.parseInt(String.valueOf(val)); } catch (Exception e) { return null; }
    }

    private static Double toDouble(Object val) {
        if (val == null) return null;
        if (val instanceof Number) return ((Number) val).doubleValue();
        try { return Double.parseDouble(String.valueOf(val)); } catch (Exception e) { return null; }
    }

    // ═══════════════ Entity Detail / Dependencies ═══════════════════

    public Map<String, Object> getEntityDetail(String entityId) {
        return repository.findEntityById(entityId).map(entity -> {
            Map<String, Object> m = entityToMap(entity);
            m.put("properties", listProperties(entityId));
            m.put("relationships", listEntityRelationships(entityId));
            m.put("rules", new java.util.ArrayList<>()); // loaded externally
            m.put("actions", new java.util.ArrayList<>()); // loaded externally
            return m;
        }).orElse(null);
    }

    public Map<String, Object> getEntityDependencies(String entityId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("entityId", entityId);
        List<OntologyRelationship> rels = repository.findRelationshipsByEntity(entityId);
        // 直接关联的实体
        java.util.Set<String> related = new java.util.HashSet<>();
        for (OntologyRelationship r : rels) {
            if (!entityId.equals(r.getSourceEntityId())) related.add(r.getSourceEntityId());
            if (!entityId.equals(r.getTargetEntityId())) related.add(r.getTargetEntityId());
        }
        result.put("relatedEntities", new java.util.ArrayList<>(related));
        // 级联删除影响
        int propCount = listProperties(entityId).size();
        int relCount = rels.size();
        result.put("propertyCount", propCount);
        result.put("relationshipCount", relCount);
        result.put("cascadingDeletes", List.of("properties", "relationships", "actions"));
        return result;
    }

    // ═══════════════ Cycle Detection (DFS) ═══════════════════

    public Map<String, Object> validateRelationship(String sourceEntityId, String targetEntityId) {
        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> edges = repository.findAllRelationshipEdges();
        // 添加待检边
        Map<String, Object> pending = new LinkedHashMap<>();
        pending.put("source", sourceEntityId);
        pending.put("target", targetEntityId);
        pending.put("code", "PENDING");
        edges.add(pending);
        // DFS 闭环检测
        java.util.Set<String> visited = new java.util.HashSet<>();
        java.util.Set<String> inStack = new java.util.HashSet<>();
        boolean hasCycle = hasCycleDFS(sourceEntityId, edges, visited, inStack);
        result.put("hasCycle", hasCycle);
        result.put("message", hasCycle ? "ONT-003: Relationship would introduce a cycle" : "Relationship is valid");
        return result;
    }

    private boolean hasCycleDFS(String node, List<Map<String, Object>> edges,
                                 java.util.Set<String> visited, java.util.Set<String> inStack) {
        if (inStack.contains(node)) return true; // back edge → cycle
        if (visited.contains(node)) return false;
        visited.add(node);
        inStack.add(node);
        for (Map<String, Object> edge : edges) {
            String src = String.valueOf(edge.getOrDefault("source", ""));
            String tgt = String.valueOf(edge.getOrDefault("target", ""));
            if (node.equals(src)) {
                if (hasCycleDFS(tgt, edges, visited, inStack)) return true;
            }
        }
        inStack.remove(node);
        return false;
    }

    public List<Map<String, Object>> getRelationshipGraph() {
        List<Map<String, Object>> edges = repository.findAllRelationshipEdges();
        // 包装为 nodes + edges 格式供前端图谱渲染
        java.util.Set<String> nodeIds = new java.util.LinkedHashSet<>();
        for (Map<String, Object> e : edges) {
            nodeIds.add(String.valueOf(e.get("source")));
            nodeIds.add(String.valueOf(e.get("target")));
        }
        List<Map<String, Object>> nodes = nodeIds.stream().map(id -> {
            Map<String, Object> n = new LinkedHashMap<>();
            n.put("id", id);
            return n;
        }).collect(java.util.stream.Collectors.toList());
        List<Map<String, Object>> result = new java.util.ArrayList<>();
        Map<String, Object> graph = new LinkedHashMap<>();
        graph.put("nodes", nodes);
        graph.put("edges", edges);
        result.add(graph);
        return result;
    }

    // ═══════════════ Ontology CRUD ═══════════════════

    public List<Map<String, Object>> listOntologies() {
        return repository.findAllOntologies();
    }

    public Map<String, Object> createOntology(Map<String, Object> body) {
        String id = java.util.UUID.randomUUID().toString().substring(0, 8);
        String code = String.valueOf(body.getOrDefault("code", ""));
        String name = String.valueOf(body.getOrDefault("name", ""));
        String description = String.valueOf(body.getOrDefault("description", ""));
        repository.insertOntology(id, code, name, description);
        log.info("Ontology created: {} [{}]", id, code);
        return repository.findOntologyById(id);
    }

    public Optional<Map<String, Object>> updateOntology(String id, Map<String, Object> body) {
        Map<String, Object> existing = repository.findOntologyById(id);
        if (existing == null) return Optional.empty();
        String name = body.containsKey("name") ? String.valueOf(body.get("name")) : null;
        String description = body.containsKey("description") ? String.valueOf(body.get("description")) : null;
        String status = body.containsKey("status") ? String.valueOf(body.get("status")) : null;
        repository.updateOntology(id, name, description, status);
        return Optional.ofNullable(repository.findOntologyById(id));
    }

    public boolean deleteOntology(String id) {
        return repository.deleteOntology(id) > 0;
    }

    // ═══════════════ Ontology-scoped relationships ═══════════════════

    public List<Map<String, Object>> listRelationshipsByOntology(String ontologyId) {
        return repository.findRelationshipsByOntology(ontologyId);
    }
}
