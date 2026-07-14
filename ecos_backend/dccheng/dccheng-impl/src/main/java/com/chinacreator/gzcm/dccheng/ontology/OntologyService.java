package com.chinacreator.gzcm.dccheng.ontology;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

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
        repository.insertEntity(entity);
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
        repository.insertProperty(prop);
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
        repository.insertRelationship(rel);
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
