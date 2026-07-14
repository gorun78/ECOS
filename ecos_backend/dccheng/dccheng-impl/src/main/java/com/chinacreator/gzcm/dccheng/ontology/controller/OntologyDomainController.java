package com.chinacreator.gzcm.dccheng.ontology.controller;

import java.util.*;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.dccheng.ontology.OntologyDomainService;
import com.chinacreator.gzcm.dccheng.ontology.OntologyRepository;

/**
 * Domain Controller — 领域管理 CRUD + 生命周期
 *
 * <h3>端点：</h3>
 * <ul>
 *   <li>GET    /api/v1/ecos/domains                             — 领域列表</li>
 *   <li>POST   /api/v1/ecos/domains                             — 创建领域</li>
 *   <li>GET    /api/v1/ecos/domains/{domainCode}                 — 领域详情</li>
 *   <li>PUT    /api/v1/ecos/domains/{domainCode}                 — 更新领域</li>
 *   <li>DELETE /api/v1/ecos/domains/{domainCode}                 — 删除领域</li>
 *   <li>POST   /api/v1/ecos/domains/{domainCode}/publish         — 发布领域</li>
 *   <li>POST   /api/v1/ecos/domains/{domainCode}/deprecate       — 废弃领域</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/ecos/domains")
public class OntologyDomainController {

    private static final Logger log = LoggerFactory.getLogger(OntologyDomainController.class);

    private final OntologyDomainService domainService;
    private final OntologyRepository ontologyRepository;

    public OntologyDomainController(OntologyDomainService domainService,
                                     OntologyRepository ontologyRepository) {
        this.domainService = domainService;
        this.ontologyRepository = ontologyRepository;
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> listDomains() {
        return ApiResponse.success(domainService.listDomains());
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> createDomain(@RequestBody Map<String, Object> body) {
        try {
            Map<String, Object> dom = domainService.createDomain(body);
            log.info("Domain created: {} [{}]", dom.get("id"), dom.get("code"));
            return ApiResponse.success(dom);
        } catch (IllegalArgumentException e) {
            return ApiResponse.badRequest(e.getMessage());
        }
    }

    @GetMapping("/{domainCode}")
    public ApiResponse<Map<String, Object>> getDomain(@PathVariable String domainCode) {
        Map<String, Object> dom = domainService.getDomain(domainCode);
        if (dom == null) return ApiResponse.notFound("ONT-008: Domain '" + domainCode + "' not found");
        return ApiResponse.success(dom);
    }

    @PutMapping("/{domainCode}")
    public ApiResponse<Map<String, Object>> updateDomain(
            @PathVariable String domainCode,
            @RequestBody Map<String, Object> body) {
        return domainService.updateDomain(domainCode, body)
            .map(ApiResponse::success)
            .orElseGet(() -> ApiResponse.notFound("ONT-008: Domain '" + domainCode + "' not found"));
    }

    @DeleteMapping("/{domainCode}")
    public ApiResponse<String> deleteDomain(@PathVariable String domainCode) {
        try {
            if (domainService.deleteDomain(domainCode)) {
                return ApiResponse.success("Domain '" + domainCode + "' deleted");
            }
            return ApiResponse.notFound("ONT-008: Domain '" + domainCode + "' not found");
        } catch (IllegalStateException e) {
            return ApiResponse.badRequest(e.getMessage());
        }
    }

    @PostMapping("/{domainCode}/publish")
    public ApiResponse<Map<String, Object>> publishDomain(@PathVariable String domainCode) {
        try {
            return ApiResponse.success(domainService.publishDomain(domainCode));
        } catch (IllegalArgumentException e) {
            return ApiResponse.badRequest(e.getMessage());
        }
    }

    @PostMapping("/{domainCode}/deprecate")
    public ApiResponse<Map<String, Object>> deprecateDomain(@PathVariable String domainCode) {
        try {
            return ApiResponse.success(domainService.deprecateDomain(domainCode));
        } catch (IllegalArgumentException e) {
            return ApiResponse.badRequest(e.getMessage());
        }
    }

    // ═══════════════ Domain → Entities 子资源 ═══════════════════

    @GetMapping("/{domainCode}/entities")
    public ApiResponse<List<Map<String, Object>>> listDomainEntities(@PathVariable String domainCode) {
        Map<String, Object> dom = domainService.getDomain(domainCode);
        if (dom == null) return ApiResponse.notFound("ONT-008: Domain '" + domainCode + "' not found");
        return ApiResponse.success(
            ontologyRepository.findEntitiesByDomain(domainCode).stream()
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", e.getId());
                    m.put("code", e.getCode());
                    m.put("name", e.getName());
                    m.put("entityType", e.getEntityType());
                    m.put("description", e.getDescription());
                    m.put("domainCode", domainCode);
                    return m;
                })
                .collect(Collectors.toList())
        );
    }
}
