package com.chinacreator.gzcm.engine.ontology.controller;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.ontology.dto.OntologyEntityDependenciesVO;
import com.chinacreator.gzcm.engine.ontology.dto.OntologyEntityDetailVO;
import com.chinacreator.gzcm.engine.ontology.dto.OntologyEntitySaveDTO;
import com.chinacreator.gzcm.engine.ontology.dto.OntologyEntityVO;
import com.chinacreator.gzcm.engine.ontology.dto.OntologyPropertySaveDTO;
import com.chinacreator.gzcm.engine.ontology.dto.OntologyPropertyVO;
import com.chinacreator.gzcm.engine.ontology.dto.OntologyRelationshipSaveDTO;
import com.chinacreator.gzcm.engine.ontology.dto.OntologyRelationshipVO;
import com.chinacreator.gzcm.engine.ontology.dto.OntologySaveDTO;
import com.chinacreator.gzcm.engine.ontology.dto.OntologyVO;
import com.chinacreator.gzcm.engine.ontology.service.OntologyService;

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
 *
 * <p>T16-1 (2026-09-12)：方法入/出参由 {@code Map<String,Object>} 改为强类型 DTO/VO
 * （{@code OntologySaveDTO / OntologyEntitySaveDTO / OntologyPropertySaveDTO /
 * OntologyRelationshipSaveDTO / Ontology*VO / Ontology*DetailVO / Ontology*DependenciesVO}），
 * 返回体保持 {@code ApiResponse<T>} 统一框架不变。
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

    /** 列出全部本体 —— 强类型版本。 */
    @GetMapping
    public ApiResponse<List<OntologyVO>> listOntologies() {
        return ApiResponse.success(ontologyService.listOntologiesVO());
    }

    /** 创建本体 —— 接收 {@code OntologySaveDTO}（code/name/description 必填）。 */
    @PostMapping
    public ApiResponse<OntologyVO> createOntology(@RequestBody OntologySaveDTO dto) {
        OntologyVO ont = ontologyService.createOntology(dto);
        log.info("Ontology created: {} [{}]", ont.getId(), ont.getCode());
        return ApiResponse.success(ont);
    }

    /** 更新本体 —— 接收 {@code OntologySaveDTO}（name/description/status）。 */
    @PutMapping("/{id}")
    public ApiResponse<OntologyVO> updateOntology(
            @PathVariable String id,
            @RequestBody OntologySaveDTO dto) {
        return ontologyService.updateOntology(id, dto)
            .map(ApiResponse::success)
            .orElseGet(() -> ApiResponse.notFound("本体 " + id + " 不存在"));
    }

    /** 删除本体（逻辑删除，Wave B-3 T17）。 */
    @DeleteMapping("/{id}")
    public ApiResponse<String> deleteOntology(@PathVariable String id) {
        if (ontologyService.deleteOntology(id)) {
            return ApiResponse.success("本体 " + id + " 已删除");
        }
        return ApiResponse.notFound("本体 " + id + " 不存在");
    }

    /** 列出指定本体的关系（保留旧语义 — 走 Map 路径,未来 T16-2 强类型化）。 */
    @GetMapping("/{ontologyId}/relationships")
    public ApiResponse<List<java.util.Map<String, Object>>> listRelationshipsByOntology(
            @PathVariable String ontologyId) {
        return ApiResponse.success(ontologyService.listRelationshipsByOntology(ontologyId));
    }

    // ═══════════════ 实体 CRUD ═══════════════════

    /** 列出本体的实体 — 强类型版本。 */
    @GetMapping("/{ontologyId}/entities")
    public ApiResponse<List<OntologyEntityVO>> listEntities(@PathVariable String ontologyId) {
        return ApiResponse.success(ontologyService.listEntitiesVO(ontologyId));
    }

    /** 创建实体（在本体下） — 接收 {@code OntologyEntitySaveDTO}。 */
    @PostMapping("/{ontologyId}/entities")
    public ApiResponse<OntologyEntityVO> createEntity(
            @PathVariable String ontologyId,
            @RequestBody OntologyEntitySaveDTO dto) {
        OntologyEntityVO ent = ontologyService.createEntity(ontologyId, dto);
        log.info("Ontology entity created via DB: {} [{}]", ent.getId(), ent.getCode());
        return ApiResponse.success(ent);
    }

    /** 更新实体 — 接收 {@code OntologyEntitySaveDTO}；(不存在 → 404)。 */
    @PutMapping("/{ontologyId}/entities/{entityId}")
    public ApiResponse<OntologyEntityVO> updateEntity(
            @PathVariable String ontologyId,
            @PathVariable String entityId,
            @RequestBody OntologyEntitySaveDTO dto) {
        return ontologyService.updateEntity(entityId, dto)
            .map(ApiResponse::success)
            .orElseGet(() -> ApiResponse.notFound("实体 " + entityId + " 不存在"));
    }

    /** 删除实体（级联清理属性/关系/动作 — 逻辑删除 Wave B-3 T17）。 */
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

    /** 列出实体的属性 — 强类型版本。 */
    @GetMapping("/entities/{entityId}/properties")
    public ApiResponse<List<OntologyPropertyVO>> listProperties(@PathVariable String entityId) {
        return ApiResponse.success(ontologyService.listPropertiesVO(entityId));
    }

    /** 创建属性 — 接收 {@code OntologyPropertySaveDTO}。 */
    @PostMapping("/entities/{entityId}/properties")
    public ApiResponse<OntologyPropertyVO> createProperty(
            @PathVariable String entityId,
            @RequestBody OntologyPropertySaveDTO dto) {
        OntologyPropertyVO prop = ontologyService.createProperty(entityId, dto);
        log.info("Property created via DB: {} for entity {}", prop.getId(), entityId);
        return ApiResponse.success(prop);
    }

    /** 更新属性 — 接收 {@code OntologyPropertySaveDTO}。 */
    @PutMapping("/entities/{entityId}/properties/{propId}")
    public ApiResponse<OntologyPropertyVO> updateProperty(
            @PathVariable String entityId,
            @PathVariable String propId,
            @RequestBody OntologyPropertySaveDTO dto) {
        return ontologyService.updateProperty(propId, dto)
            .map(ApiResponse::success)
            .orElseGet(() -> ApiResponse.notFound("属性 " + propId + " 不存在"));
    }

    /** 删除属性（逻辑删除，Wave B-3 T17）。 */
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

    /** 列出实体的关系（作为 source 或 target） — 强类型版本。 */
    @GetMapping("/entities/{entityId}/relationships")
    public ApiResponse<List<OntologyRelationshipVO>> listEntityRelationships(@PathVariable String entityId) {
        return ApiResponse.success(ontologyService.listEntityRelationshipsVO(entityId));
    }

    /** 列出全部关系 — 强类型版本。 */
    @GetMapping("/relationships")
    public ApiResponse<List<OntologyRelationshipVO>> listAllRelationships() {
        return ApiResponse.success(ontologyService.listAllRelationshipsVO());
    }

    /** 创建关系 — 接收 {@code OntologyRelationshipSaveDTO}（source 由 path 传入）。 */
    @PostMapping("/entities/{entityId}/relationships")
    public ApiResponse<OntologyRelationshipVO> createRelationship(
            @PathVariable String entityId,
            @RequestBody OntologyRelationshipSaveDTO dto) {
        OntologyRelationshipVO rel = ontologyService.createRelationship(entityId, dto);
        log.info("Relationship created via DB: {} {}→{}", rel.getId(), entityId, rel.getTargetEntityId());
        return ApiResponse.success(rel);
    }

    /** 删除关系（逻辑删除，Wave B-3 T17）。 */
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

    /** 实体详情（主实体 + 嵌套属性/关系） — 强类型版本。 */
    @GetMapping("/entities/{entityId}")
    public ApiResponse<OntologyEntityDetailVO> getEntityDetail(@PathVariable String entityId) {
        OntologyEntityDetailVO detail = ontologyService.getEntityDetailVO(entityId);
        if (detail == null) {
            return ApiResponse.notFound("ONT-001: Entity '" + entityId + "' not found");
        }
        return ApiResponse.success(detail);
    }

    /** 实体依赖分析（关联实体 + 级联删除影响） — 强类型版本。 */
    @GetMapping("/entities/{entityId}/dependencies")
    public ApiResponse<OntologyEntityDependenciesVO> getEntityDependencies(@PathVariable String entityId) {
        return ApiResponse.success(ontologyService.getEntityDependenciesVO(entityId));
    }

    // ═══════════════ Domain-scoped Entity ═══════════════════

    /** 列出指定 domain 下的实体（path 用 domainCode；回退到 listEntities 语义）。 */
    @GetMapping("/domains/{domainCode}/entities")
    public ApiResponse<List<OntologyEntityVO>> listEntitiesByDomain(@PathVariable String domainCode) {
        return ApiResponse.success(ontologyService.listEntitiesVO(domainCode));
    }

    /** 在指定 domain 下创建实体。 */
    @PostMapping("/domains/{domainCode}/entities")
    public ApiResponse<OntologyEntityVO> createEntityInDomain(
            @PathVariable String domainCode,
            @RequestBody OntologyEntitySaveDTO dto) {
        OntologyEntityVO ent = ontologyService.createEntity(domainCode, dto);
        log.info("Entity created in domain {}: [{}]", domainCode, ent.getCode());
        return ApiResponse.success(ent);
    }
}
