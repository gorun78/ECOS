package com.chinacreator.gzcm.engine.ontology.controller;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.ontology.dto.*;
import com.chinacreator.gzcm.engine.ontology.service.OntologyDomainService;
import com.chinacreator.gzcm.engine.ontology.service.OntologyService;

/**
 * Ontology Domain API Controller — /api/v1/ontology 路径下的域管理端点。
 *
 * <p>轻量别名控制器，复用 {@link OntologyDomainService} 的现有业务逻辑，
 * 为 c2eos 前端提供 /api/v1/ontology/domains 路径访问。
 * 不修改现有 {@code OntologyDomainController} 的任何签名（API 只增不改）。
 *
 * <h3>端点：</h3>
 * <ul>
 *   <li>GET    /api/v1/ontology/domains             — 域列表</li>
 *   <li>POST   /api/v1/ontology/domains             — 创建域</li>
 *   <li>PUT    /api/v1/ontology/domains/{id}        — 更新域（id 即 domainCode）</li>
 *   <li>DELETE /api/v1/ontology/domains/{id}        — 删除域（id 即 domainCode）</li>
 *   <li>GET    /api/v1/ontology/objects             — 对象类型列表（含 mapping + domainId）</li>
 *   <li>PUT    /api/v1/ontology/objects/{id}/domain — 修改对象归属域</li>
 *   <li>POST   /api/v1/ontology/objects             — 创建对象类型</li>
 *   <li>PUT    /api/v1/ontology/objects/{id}        — 更新对象属性</li>
 *   <li>DELETE /api/v1/ontology/objects/{id}        — 删除对象</li>
 *   <li>GET    /api/v1/ontology/links               — 链接类型列表</li>
 *   <li>POST   /api/v1/ontology/links               — 创建链接</li>
 *   <li>DELETE /api/v1/ontology/links/{id}          — 删除链接</li>
 * </ul>
 *
 * <p>T16-2 (2026-09-12)：方法入/出参由 {@code Map<String,Object>} 改为强类型
 * （{@code OntologyDomainVO} / {@code OntologyDomainSaveDTO} / {@code OntologyDomainReassignDTO} /
 * {@code OntologyObjectSaveDTO} / {@code OntologyLinkSaveDTO} / {@code OntologyEntityVO} /
 * {@code OntologyRelationshipVO} / {@code DeleteResultVO} 复用 T16-1 VO）。
 *
 * <p><b>buszhi 红线说明</b>：本控制器 Object CRUD / Link CRUD 段调用的
 * {@code OntologyService.createEntity(Map) / updateEntity(Map) / createRelationship(Map)}
 * 旧 Map 签名在 T16-1 已废弃（保留为 C1 mock 兼容）。Object updater 复用
 * T16-1 已加的 VO 重载：{@code createEntity(String, OntologyEntitySaveDTO)} /
 * {@code updateEntity(String, OntologyEntitySaveDTO)} /
 * {@code createRelationship(String, OntologyRelationshipSaveDTO)}。
 * 既保留 VO 强类型，又不引入新的 service 改动。
 */
@RestController
@RequestMapping("/api/v1/ontology")
public class OntologyDomainApiController {

    private static final Logger log = LoggerFactory.getLogger(OntologyDomainApiController.class);

    private final OntologyDomainService domainService;
    private final OntologyService ontologyService;

    public OntologyDomainApiController(OntologyDomainService domainService,
                                       OntologyService ontologyService) {
        this.domainService = domainService;
        this.ontologyService = ontologyService;
    }

    // ═══════════════ Domain CRUD ═══════════════════

    /** 域列表（强类型 VO）。 */
    @GetMapping("/domains")
    public ApiResponse<List<OntologyDomainVO>> listDomains() {
        return ApiResponse.success(domainService.listDomainsVO());
    }

    /**
     * GET /api/v1/ontology/domains/search?q=&limit=20
     * 模糊匹配 name/code/description 的域列表。PMO E8 端点。
     */
    @GetMapping("/domains/search")
    public ApiResponse<List<OntologyDomainVO>> searchDomains(
            @RequestParam(required = false) String q,
            @RequestParam(required = false, defaultValue = "20") Integer limit) {
        return ApiResponse.success(domainService.searchDomainsVO(q, limit));
    }

    /** 创建域（强类型 DTO）。 */
    @PostMapping("/domains")
    public ApiResponse<OntologyDomainVO> createDomain(@RequestBody OntologyDomainSaveDTO dto) {
        try {
            OntologyDomainVO dom = domainService.createDomain(dto);
            log.info("Domain created via ontology API: {} [{}]", dom.getId(), dom.getCode());
            return ApiResponse.success(dom);
        } catch (IllegalArgumentException e) {
            return ApiResponse.badRequest(e.getMessage());
        }
    }

    /** 更新域（强类型 DTO；调 service {@code updateDomainVO} 返回 VO）。 */
    @PutMapping("/domains/{id}")
    public ApiResponse<OntologyDomainVO> updateDomain(
            @PathVariable String id,
            @RequestBody OntologyDomainSaveDTO dto) {
        return domainService.updateDomainVO(id, dto)
            .map(ApiResponse::success)
            .orElseGet(() -> ApiResponse.notFound("ONT-008: Domain '" + id + "' not found"));
    }

    /** 删除域（含实体时拒绝）。 */
    @DeleteMapping("/domains/{id}")
    public ApiResponse<String> deleteDomain(@PathVariable String id) {
        try {
            if (domainService.deleteDomain(id)) {
                return ApiResponse.success("Domain '" + id + "' deleted");
            }
            return ApiResponse.notFound("ONT-008: Domain '" + id + "' not found");
        } catch (IllegalStateException e) {
            return ApiResponse.badRequest(e.getMessage());
        }
    }

    // ═══════════════ Object → Domain 归属变更 ═══════════════════

    /**
     * GET /api/v1/ontology/objects — 对象类型列表，含 mapping + domainId 字段。
     * <p>改用 T16-1 既有 {@code listEntitiesVO("")}（ontologyId 为空时返回全量）。
     */
    @GetMapping("/objects")
    public ApiResponse<List<OntologyEntityVO>> listObjects() {
        return ApiResponse.success(ontologyService.listEntitiesVO(""));
    }

    /**
     * PUT /api/v1/ontology/objects/{id}/domain — 修改对象归属域。
     * <p>强类型入参 {@link OntologyDomainReassignDTO}（兼容 {@code domainCode} / {@code domainId}
     * 双字段，与既有 {@code body.getOrDefault("domainCode", body.getOrDefault("domainId", ""))}
     * 兼容行为一致）。
     *
     * <p>返回值仍为 {@code Map<String,Object>}：因 service 的
     * {@code reassignEntityDomainVO} 将 DTO 解析后委托 Map 版（保持 C1 mock 行为），
     * 这里在 Controller 内按既有契约字段重映射为强类型
     * {@code {entityId, domainId, domainCode, domainName}} 四字段，
     * 与 {@link OntologyDomainVO} schema 解耦后的语义对齐，
     * 避免 VO 类型在跨 service 时承担过多责任。
     */
    @PutMapping("/objects/{id}/domain")
    public ApiResponse<OntologyDomainReassignVO> reassignObjectDomain(
            @PathVariable String id,
            @RequestBody OntologyDomainReassignDTO dto) {
        try {
            String domainCode = (dto.getDomainCode() != null && !dto.getDomainCode().isBlank())
                ? dto.getDomainCode()
                : (dto.getDomainId() != null ? dto.getDomainId() : "");
            if (domainCode.isEmpty()) {
                return ApiResponse.badRequest("domainCode is required");
            }
            Map<String, Object> result = domainService.reassignEntityDomain(id, domainCode);
            log.info("Entity {} reassigned to domain {}", id, domainCode);
            OntologyDomainReassignVO vo = new OntologyDomainReassignVO();
            vo.setEntityId(id);
            vo.setDomainId(String.valueOf(result.get("domainId")));
            vo.setDomainCode(String.valueOf(result.get("domainCode")));
            vo.setDomainName(String.valueOf(result.get("domainName")));
            return ApiResponse.success(vo);
        } catch (IllegalArgumentException e) {
            return ApiResponse.badRequest(e.getMessage());
        }
    }

    // ═══════════════ Object CRUD ═══════════════════

    /**
     * POST /api/v1/ontology/objects — 创建对象类型。
     * <p>Body 必填: code, name; 可选: entityType, description, ontologyId(默认 ont001), domainId。
     * T16-2 改用 T16-1 既有 {@code OntologyService.createEntity(ontologyId, OntologyEntitySaveDTO)}
     * VO 重载，避免复用 C1 mock 依赖的旧 Map 签名。
     */
    @PostMapping("/objects")
    public ApiResponse<OntologyEntityVO> createObject(@RequestBody OntologyObjectSaveDTO dto) {
        try {
            String ontologyId = dto.getOntologyId() != null && !dto.getOntologyId().isBlank()
                ? dto.getOntologyId() : "ont001";
            if (dto.getCode() == null || dto.getCode().isBlank()) {
                return ApiResponse.badRequest("code is required");
            }
            if (dto.getName() == null || dto.getName().isBlank()) {
                return ApiResponse.badRequest("name is required");
            }
            OntologyEntitySaveDTO entityDto = toEntitySaveDTO(dto);
            OntologyEntityVO result = ontologyService.createEntity(ontologyId, entityDto);
            log.info("Object created: {} in ontology {}", result.getId(), ontologyId);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("Create object failed", e);
            return ApiResponse.badRequest(e.getMessage());
        }
    }

    /**
     * PUT /api/v1/ontology/objects/{id} — 更新对象属性。
     * <p>T16-2 改用 T16-1 既有 {@code OntologyService.updateEntity(entityId, OntologyEntitySaveDTO)} VO 重载。
     */
    @PutMapping("/objects/{id}")
    public ApiResponse<OntologyEntityVO> updateObject(
            @PathVariable String id,
            @RequestBody OntologyObjectSaveDTO dto) {
        try {
            java.util.Optional<OntologyEntityVO> result = ontologyService.updateEntity(id, toEntitySaveDTO(dto));
            if (result.isPresent()) {
                log.info("Object {} updated", id);
                return ApiResponse.success(result.get());
            }
            return ApiResponse.notFound("Object not found: " + id);
        } catch (Exception e) {
            log.error("Update object {} failed", id, e);
            return ApiResponse.badRequest(e.getMessage());
        }
    }

    /**
     * DELETE /api/v1/ontology/objects/{id} — 删除对象。
     * <p>T16-2 返回强类型 {@link DeleteResultVO}（替代原来的
     * {@code Map.of("deleted", true, "id", id)}）。
     */
    @DeleteMapping("/objects/{id}")
    public ApiResponse<DeleteResultVO> deleteObject(@PathVariable String id) {
        try {
            boolean deleted = ontologyService.deleteEntity(id);
            if (deleted) {
                log.info("Object {} deleted", id);
                DeleteResultVO vo = new DeleteResultVO();
                vo.setDeleted(true);
                vo.setId(id);
                return ApiResponse.success(vo);
            }
            return ApiResponse.notFound("Object not found: " + id);
        } catch (Exception e) {
            log.error("Delete object {} failed", id, e);
            return ApiResponse.badRequest(e.getMessage());
        }
    }

    // ═══════════════ 内部辅助方法 ═══════════════════

    /**
     * 强类型入参 {@link OntologyObjectSaveDTO} → T16-1 既有 {@link OntologyEntitySaveDTO}
     * 透传（字段一一对齐，上层 service 直接消费）。
     */
    private OntologyEntitySaveDTO toEntitySaveDTO(OntologyObjectSaveDTO dto) {
        OntologyEntitySaveDTO e = new OntologyEntitySaveDTO();
        e.setCode(dto.getCode());
        e.setName(dto.getName());
        e.setDescription(dto.getDescription());
        e.setEntityType(dto.getEntityType());
        return e;
    }

    // ═══════════════ Link CRUD ═══════════════════

    /**
     * GET /api/v1/ontology/links — 链接类型列表。
     * <p>T16-2 改用 T16-1 既有 {@code listAllRelationshipsVO()}。
     */
    @GetMapping("/links")
    public ApiResponse<List<OntologyRelationshipVO>> listLinks() {
        return ApiResponse.success(ontologyService.listAllRelationshipsVO());
    }

    /**
     * POST /api/v1/ontology/links — 创建链接。
     * <p>Body 必填: sourceEntityId, targetEntityId; 可选: name, linkType, description。
     *
     * <p>T16-2 强类型 {@link OntologyLinkSaveDTO}：
     * 兼容旧字段 {@code sourceObjectId → sourceEntityId} /
     * {@code linkType → relationshipType}。
     */
    @PostMapping("/links")
    public ApiResponse<OntologyRelationshipVO> createLink(
            @RequestBody OntologyLinkSaveDTO dto) {
        try {
            // PMO 指令字段兼容: sourceObjectId → sourceEntityId
            String sourceEntityId = (dto.getSourceEntityId() != null && !dto.getSourceEntityId().isBlank())
                ? dto.getSourceEntityId().trim()
                : (dto.getSourceObjectId() != null ? dto.getSourceObjectId().trim() : "");
            if (sourceEntityId.isEmpty()) {
                return ApiResponse.badRequest("sourceEntityId is required");
            }
            // PMO 指令字段兼容: linkType → relationshipType
            // 规整到 T16-1 既有 OntologyRelationshipSaveDTO
            com.chinacreator.gzcm.engine.ontology.dto.OntologyRelationshipSaveDTO relDto =
                new com.chinacreator.gzcm.engine.ontology.dto.OntologyRelationshipSaveDTO();
            relDto.setSourceEntityId(sourceEntityId);
            relDto.setTargetEntityId(dto.getTargetEntityId());
            String relType = (dto.getRelationshipType() != null && !dto.getRelationshipType().isBlank())
                ? dto.getRelationshipType()
                : (dto.getLinkType() != null ? dto.getLinkType() : "");
            if (relType != null && !relType.isEmpty()) {
                relDto.setRelationshipType(relType);
            }
            if (dto.getDescription() != null) {
                // 描述在 Rel DTO 中没有字段承载，仅 trace log 保留信息
                log.debug("Link description (info only, not persisted): {}", dto.getDescription());
            }
            OntologyRelationshipVO result = ontologyService.createRelationship(sourceEntityId, relDto);
            log.info("Link created: {} → {}", sourceEntityId, dto.getTargetEntityId());
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("Create link failed", e);
            return ApiResponse.badRequest(e.getMessage());
        }
    }

    /**
     * DELETE /api/v1/ontology/links/{id} — 删除链接。
     * <p>T16-2 返回强类型 {@link DeleteResultVO}。
     */
    @DeleteMapping("/links/{id}")
    public ApiResponse<DeleteResultVO> deleteLink(@PathVariable String id) {
        try {
            boolean deleted = ontologyService.deleteRelationship(id);
            if (deleted) {
                log.info("Link {} deleted", id);
                DeleteResultVO vo = new DeleteResultVO();
                vo.setDeleted(true);
                vo.setId(id);
                return ApiResponse.success(vo);
            }
            return ApiResponse.notFound("Link not found: " + id);
        } catch (Exception e) {
            log.error("Delete link {} failed", id, e);
            return ApiResponse.badRequest(e.getMessage());
        }
    }
}
