package com.chinacreator.gzcm.engine.ontology.controller;

import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.dccheng.ontology.OntologyService;

/**
 * 本体设计器 REST API — 实体/属性/关系 CRUD（PostgreSQL 持久化）。
 *
 * <h3>端点：</h3>
 * <ul>
 *   <li>GET    /api/v1/ecos/ontologies/{ontologyId}/entities       — 实体列表</li>
 *   <li>POST   /api/v1/ecos/ontologies/{ontologyId}/entities       — 创建实体</li>
 *   <li>PUT    /api/v1/ecos/ontologies/{ontologyId}/entities/{id}  — 更新实体</li>
 *   <li>DELETE /api/v1/ecos/ontologies/{ontologyId}/entities/{id}  — 删除实体</li>
 *   <li>GET    /api/v1/ecos/entities/{entityId}/properties         — 属性列表</li>
 *   <li>POST   /api/v1/ecos/entities/{entityId}/properties         — 创建属性</li>
 *   <li>PUT    /api/v1/ecos/entities/{entityId}/properties/{id}    — 更新属性</li>
 *   <li>DELETE /api/v1/ecos/entities/{entityId}/properties/{id}    — 删除属性</li>
 *   <li>GET    /api/v1/ecos/entities/{entityId}/relationships      — 实体关系</li>
 *   <li>POST   /api/v1/ecos/entities/{entityId}/relationships      — 创建关系</li>
 *   <li>DELETE /api/v1/ecos/entities/{entityId}/relationships/{id} — 删除关系</li>
 *   <li>GET    /api/v1/ecos/relationships                          — 全部关系</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/ecos/ontologies")
public class OntologyController {

    private static final Logger log = LoggerFactory.getLogger(OntologyController.class);

    private final OntologyService ontologyService;

    public OntologyController(OntologyService ontologyService) {
        this.ontologyService = ontologyService;
    }

    // ═══════════════ Ontology 本体 CRUD ═══════════════════

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> listOntologies() {
        return ApiResponse.success(ontologyService.listOntologies());
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> createOntology(@RequestBody Map<String, Object> body) {
        Map<String, Object> ont = ontologyService.createOntology(body);
        log.info("Ontology created: {} [{}]", ont.get("id"), ont.get("code"));
        return ApiResponse.success(ont);
    }

    @PutMapping("/{id}")
    public ApiResponse<Map<String, Object>> updateOntology(
            @PathVariable String id,
            @RequestBody Map<String, Object> body) {
        return ontologyService.updateOntology(id, body)
            .map(ApiResponse::success)
            .orElseGet(() -> ApiResponse.notFound("本体 " + id + " 不存在"));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<String> deleteOntology(@PathVariable String id) {
        if (ontologyService.deleteOntology(id)) {
            return ApiResponse.success("本体 " + id + " 已删除");
        }
        return ApiResponse.notFound("本体 " + id + " 不存在");
    }

    @GetMapping("/{ontologyId}/relationships")
    public ApiResponse<List<Map<String, Object>>> listRelationshipsByOntology(@PathVariable String ontologyId) {
        return ApiResponse.success(ontologyService.listRelationshipsByOntology(ontologyId));
    }

    // ═══════════════ 实体 CRUD ═══════════════════

    @GetMapping("/{ontologyId}/entities")
    public ApiResponse<List<Map<String, Object>>> listEntities(@PathVariable String ontologyId) {
        return ApiResponse.success(ontologyService.listEntities(ontologyId));
    }

    @PostMapping("/{ontologyId}/entities")
    public ApiResponse<Map<String, Object>> createEntity(
            @PathVariable String ontologyId,
            @RequestBody Map<String, Object> body) {
        Map<String, Object> ent = ontologyService.createEntity(ontologyId, body);
        log.info("Ontology entity created via DB: {} [{}]", ent.get("id"), ent.get("code"));
        return ApiResponse.success(ent);
    }

    @PutMapping("/{ontologyId}/entities/{entityId}")
    public ApiResponse<Map<String, Object>> updateEntity(
            @PathVariable String ontologyId,
            @PathVariable String entityId,
            @RequestBody Map<String, Object> body) {
        return ontologyService.updateEntity(entityId, body)
            .map(ApiResponse::success)
            .orElseGet(() -> ApiResponse.notFound("实体 " + entityId + " 不存在"));
    }

    @DeleteMapping("/{ontologyId}/entities/{entityId}")
    public ApiResponse<String> deleteEntity(
            @PathVariable String ontologyId,
            @PathVariable String entityId) {
        if (ontologyService.deleteEntity(entityId)) {
            return ApiResponse.success("实体 " + entityId + " 已删除");
        }
        return ApiResponse.notFound("实体 " + entityId + " 不存在");
    }

    // ═══════════════ 属性 CRUD ═══════════════════

    @GetMapping("/entities/{entityId}/properties")
    public ApiResponse<List<Map<String, Object>>> listProperties(@PathVariable String entityId) {
        return ApiResponse.success(ontologyService.listProperties(entityId));
    }

    @PostMapping("/entities/{entityId}/properties")
    public ApiResponse<Map<String, Object>> createProperty(
            @PathVariable String entityId,
            @RequestBody Map<String, Object> body) {
        Map<String, Object> prop = ontologyService.createProperty(entityId, body);
        log.info("Property created via DB: {} for entity {}", prop.get("id"), entityId);
        return ApiResponse.success(prop);
    }

    @PutMapping("/entities/{entityId}/properties/{propId}")
    public ApiResponse<Map<String, Object>> updateProperty(
            @PathVariable String entityId,
            @PathVariable String propId,
            @RequestBody Map<String, Object> body) {
        return ontologyService.updateProperty(propId, body)
            .map(ApiResponse::success)
            .orElseGet(() -> ApiResponse.notFound("属性 " + propId + " 不存在"));
    }

    @DeleteMapping("/entities/{entityId}/properties/{propId}")
    public ApiResponse<String> deleteProperty(
            @PathVariable String entityId,
            @PathVariable String propId) {
        if (ontologyService.deleteProperty(propId)) {
            return ApiResponse.success("属性 " + propId + " 已删除");
        }
        return ApiResponse.notFound("属性 " + propId + " 不存在");
    }

    // ═══════════════ 关系 CRUD ═══════════════════

    @GetMapping("/entities/{entityId}/relationships")
    public ApiResponse<List<Map<String, Object>>> listEntityRelationships(@PathVariable String entityId) {
        return ApiResponse.success(ontologyService.listEntityRelationships(entityId));
    }

    @GetMapping("/relationships")
    public ApiResponse<List<Map<String, Object>>> listAllRelationships() {
        return ApiResponse.success(ontologyService.listAllRelationships());
    }

    @PostMapping("/entities/{entityId}/relationships")
    public ApiResponse<Map<String, Object>> createRelationship(
            @PathVariable String entityId,
            @RequestBody Map<String, Object> body) {
        Map<String, Object> rel = ontologyService.createRelationship(entityId, body);
        log.info("Relationship created via DB: {} {}→{}", rel.get("id"), entityId, rel.get("targetEntityId"));
        return ApiResponse.success(rel);
    }

    @DeleteMapping("/entities/{entityId}/relationships/{relId}")
    public ApiResponse<String> deleteRelationship(
            @PathVariable String entityId,
            @PathVariable String relId) {
        if (ontologyService.deleteRelationship(relId)) {
            return ApiResponse.success("关系 " + relId + " 已删除");
        }
        return ApiResponse.notFound("关系 " + relId + " 不存在");
    }

    // ═══════════════ Entity 详情 & 依赖分析 ═══════════════════

    @GetMapping("/entities/{entityId}")
    public ApiResponse<Map<String, Object>> getEntityDetail(@PathVariable String entityId) {
        Map<String, Object> detail = ontologyService.getEntityDetail(entityId);
        if (detail == null) return ApiResponse.notFound("ONT-001: Entity '" + entityId + "' not found");
        return ApiResponse.success(detail);
    }

    @GetMapping("/entities/{entityId}/dependencies")
    public ApiResponse<Map<String, Object>> getEntityDependencies(@PathVariable String entityId) {
        return ApiResponse.success(ontologyService.getEntityDependencies(entityId));
    }

    // ═══════════════ Domain-scoped Entity ═══════════════════

    @GetMapping("/domains/{domainCode}/entities")
    public ApiResponse<List<Map<String, Object>>> listEntitiesByDomain(@PathVariable String domainCode) {
        return ApiResponse.success(ontologyService.listEntities(domainCode));
    }

    @PostMapping("/domains/{domainCode}/entities")
    public ApiResponse<Map<String, Object>> createEntityInDomain(
            @PathVariable String domainCode,
            @RequestBody Map<String, Object> body) {
        Map<String, Object> ent = ontologyService.createEntity(domainCode, body);
        log.info("Entity created in domain {}: [{}]", domainCode, ent.get("code"));
        return ApiResponse.success(ent);
    }
}
