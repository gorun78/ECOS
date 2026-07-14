package com.chinacreator.gzcm.engine.ontology.controller;

import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.dccheng.ontology.OntologyDomainService;
import com.chinacreator.gzcm.dccheng.ontology.OntologyService;

/**
 * Ontology Domain API Controller — /api/v1/ontology 路径下的域管理端点。
 *
 * <p>轻量别名控制器，复用 {@link OntologyDomainService} 的现有业务逻辑，
 * 为 c2eos 前端提供 /api/v1/ontology/domains 路径访问。
 * 不修改现有 OntologyDomainController 的任何签名。</p>
 *
 * <h3>端点：</h3>
 * <ul>
 *   <li>GET    /api/v1/ontology/domains             — 域列表</li>
 *   <li>POST   /api/v1/ontology/domains             — 创建域</li>
 *   <li>PUT    /api/v1/ontology/domains/{id}        — 更新域（id 即 domainCode）</li>
 *   <li>DELETE /api/v1/ontology/domains/{id}        — 删除域（id 即 domainCode）</li>
 *   <li>GET    /api/v1/ontology/objects             — 对象类型列表（含 mapping + domainId）</li>
 *   <li>PUT    /api/v1/ontology/objects/{id}/domain — 修改对象归属域</li>
 * </ul>
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

    @GetMapping("/domains")
    public ApiResponse<List<Map<String, Object>>> listDomains() {
        return ApiResponse.success(domainService.listDomains());
    }

    @PostMapping("/domains")
    public ApiResponse<Map<String, Object>> createDomain(@RequestBody Map<String, Object> body) {
        try {
            Map<String, Object> dom = domainService.createDomain(body);
            log.info("Domain created via ontology API: {} [{}]", dom.get("id"), dom.get("code"));
            return ApiResponse.success(dom);
        } catch (IllegalArgumentException e) {
            return ApiResponse.badRequest(e.getMessage());
        }
    }

    @PutMapping("/domains/{id}")
    public ApiResponse<Map<String, Object>> updateDomain(
            @PathVariable String id,
            @RequestBody Map<String, Object> body) {
        return domainService.updateDomain(id, body)
            .map(ApiResponse::success)
            .orElseGet(() -> ApiResponse.notFound("ONT-008: Domain '" + id + "' not found"));
    }

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
     */
    @GetMapping("/objects")
    public ApiResponse<List<Map<String, Object>>> listObjects() {
        return ApiResponse.success(ontologyService.listAllObjects());
    }

    @PutMapping("/objects/{id}/domain")
    public ApiResponse<Map<String, Object>> reassignObjectDomain(
            @PathVariable String id,
            @RequestBody Map<String, Object> body) {
        try {
            String domainCode = String.valueOf(body.getOrDefault("domainCode",
                body.getOrDefault("domainId", "")));
            Map<String, Object> result = domainService.reassignEntityDomain(id, domainCode);
            log.info("Entity {} reassigned to domain {}", id, domainCode);
            return ApiResponse.success(result);
        } catch (IllegalArgumentException e) {
            return ApiResponse.badRequest(e.getMessage());
        }
    }

    // ═══════════════ Object CRUD ═══════════════════

    /**
     * POST /api/v1/ontology/objects — 创建对象类型。
     * Body 必填: code, name; 可选: entityType, description, ontologyId(默认ont001), domainId
     */
    @PostMapping("/objects")
    public ApiResponse<Map<String, Object>> createObject(@RequestBody Map<String, Object> body) {
        try {
            String ontologyId = String.valueOf(body.getOrDefault("ontologyId", "ont001"));
            Map<String, Object> result = ontologyService.createEntity(ontologyId, body);
            log.info("Object created: {} in ontology {}", result.get("id"), ontologyId);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("Create object failed", e);
            return ApiResponse.badRequest(e.getMessage());
        }
    }

    /**
     * PUT /api/v1/ontology/objects/{id} — 更新对象属性。
     */
    @PutMapping("/objects/{id}")
    public ApiResponse<Map<String, Object>> updateObject(
            @PathVariable String id,
            @RequestBody Map<String, Object> body) {
        try {
            Optional<Map<String, Object>> result = ontologyService.updateEntity(id, body);
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
     */
    @DeleteMapping("/objects/{id}")
    public ApiResponse<Map<String, Object>> deleteObject(@PathVariable String id) {
        try {
            boolean deleted = ontologyService.deleteEntity(id);
            if (deleted) {
                log.info("Object {} deleted", id);
                return ApiResponse.success(Map.of("deleted", true, "id", id));
            }
            return ApiResponse.notFound("Object not found: " + id);
        } catch (Exception e) {
            log.error("Delete object {} failed", id, e);
            return ApiResponse.badRequest(e.getMessage());
        }
    }

    // ═══════════════ Link CRUD ═══════════════════

    /**
     * GET /api/v1/ontology/links — 链接类型列表。
     */
    @GetMapping("/links")
    public ApiResponse<List<Map<String, Object>>> listLinks() {
        return ApiResponse.success(ontologyService.listAllRelationships());
    }

    /**
     * POST /api/v1/ontology/links — 创建链接。
     * Body 必填: sourceEntityId, targetEntityId; 可选: name, linkType, description
     */
    @PostMapping("/links")
    public ApiResponse<Map<String, Object>> createLink(@RequestBody Map<String, Object> body) {
        try {
            // PMO指令字段兼容: sourceObjectId→sourceEntityId
            String sourceEntityId = String.valueOf(body.getOrDefault("sourceEntityId", body.getOrDefault("sourceObjectId", ""))).trim();
            if (sourceEntityId.isEmpty()) {
                return ApiResponse.badRequest("sourceEntityId is required");
            }
            // PMO指令字段兼容: linkType→relationshipType
            if (body.containsKey("linkType") && !body.containsKey("relationshipType")) {
                body.put("relationshipType", body.get("linkType"));
            }
            Map<String, Object> result = ontologyService.createRelationship(sourceEntityId, body);
            log.info("Link created: {} → {}", sourceEntityId, body.get("targetEntityId"));
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("Create link failed", e);
            return ApiResponse.badRequest(e.getMessage());
        }
    }

    /**
     * DELETE /api/v1/ontology/links/{id} — 删除链接。
     */
    @DeleteMapping("/links/{id}")
    public ApiResponse<Map<String, Object>> deleteLink(@PathVariable String id) {
        try {
            boolean deleted = ontologyService.deleteRelationship(id);
            if (deleted) {
                log.info("Link {} deleted", id);
                return ApiResponse.success(Map.of("deleted", true, "id", id));
            }
            return ApiResponse.notFound("Link not found: " + id);
        } catch (Exception e) {
            log.error("Delete link {} failed", id, e);
            return ApiResponse.badRequest(e.getMessage());
        }
    }
}
