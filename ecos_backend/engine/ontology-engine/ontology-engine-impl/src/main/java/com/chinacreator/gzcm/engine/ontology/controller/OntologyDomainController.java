package com.chinacreator.gzcm.engine.ontology.controller;

import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.ontology.dto.OntologyDomainSaveDTO;
import com.chinacreator.gzcm.engine.ontology.dto.OntologyDomainVO;
import com.chinacreator.gzcm.engine.ontology.dto.OntologyEntityVO;
import com.chinacreator.gzcm.engine.ontology.service.OntologyDomainService;
import com.chinacreator.gzcm.engine.ontology.repository.OntologyRepository;

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
 *
 * <p>T16-2 (2026-09-12)：方法入/出参由 {@code Map<String,Object>} 改为强类型
 * {@code OntologyDomainVO} / {@code OntologyDomainSaveDTO}（JSDoc 溯源 {@code T16-2}）。
 * Service 旧 Map 签名保留（Wave31 C1 mock 兼容）。
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

    /** 列出全部领域（强类型 {@link OntologyDomainVO}）。 */
    @GetMapping
    public ApiResponse<List<OntologyDomainVO>> listDomains() {
        return ApiResponse.success(domainService.listDomainsVO());
    }

    /** 创建领域 — 强类型 {@link OntologyDomainSaveDTO}。 */
    @PostMapping
    public ApiResponse<OntologyDomainVO> createDomain(@RequestBody OntologyDomainSaveDTO dto) {
        try {
            OntologyDomainVO dom = domainService.createDomain(dto);
            log.info("Domain created (VO): {} [{}]", dom.getId(), dom.getCode());
            return ApiResponse.success(dom);
        } catch (IllegalArgumentException e) {
            return ApiResponse.badRequest(e.getMessage());
        }
    }

    /** 领域详情（不存在 404）。 */
    @GetMapping("/{domainCode}")
    public ApiResponse<OntologyDomainVO> getDomain(@PathVariable String domainCode) {
        OntologyDomainVO dom = domainService.getDomainVO(domainCode);
        if (dom == null) {
            return ApiResponse.notFound("ONT-008: Domain '" + domainCode + "' not found");
        }
        return ApiResponse.success(dom);
    }

    /** 更新领域 — 强类型 {@link OntologyDomainSaveDTO}；字段 null 表示不动（与既有 Map 版语义一致）。 */
    @PutMapping("/{domainCode}")
    public ApiResponse<OntologyDomainVO> updateDomain(
            @PathVariable String domainCode,
            @RequestBody OntologyDomainSaveDTO dto) {
        return domainService.updateDomain(domainCode, dto)
            .map(ApiResponse::success)
            .orElseGet(() -> ApiResponse.notFound("ONT-008: Domain '" + domainCode + "' not found"));
    }

    /** 删除领域（含实体时拒绝）。 */
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

    /** 发布领域（Draft → Published）。 */
    @PostMapping("/{domainCode}/publish")
    public ApiResponse<OntologyDomainVO> publishDomain(@PathVariable String domainCode) {
        try {
            return ApiResponse.success(domainService.publishDomainVO(domainCode));
        } catch (IllegalArgumentException e) {
            return ApiResponse.badRequest(e.getMessage());
        }
    }

    /** 废弃领域。 */
    @PostMapping("/{domainCode}/deprecate")
    public ApiResponse<OntologyDomainVO> deprecateDomain(@PathVariable String domainCode) {
        try {
            return ApiResponse.success(domainService.deprecateDomainVO(domainCode));
        } catch (IllegalArgumentException e) {
            return ApiResponse.badRequest(e.getMessage());
        }
    }

    // ═══════════════ search 端点 (PMO E8) ═════════════════
    // GET /api/v1/ecos/domains/search?q=&limit=20
    // 模糊匹配 name/code/description, 按 sort_order + created_at DESC 返回。
    // 无租户上下文时返回全部域; 有上下文时只返回当前租户及 NULL 共享域。

    /** 搜索领域（强类型 {@link OntologyDomainVO}）。 */
    @GetMapping("/search")
    public ApiResponse<List<OntologyDomainVO>> searchDomains(
            @RequestParam(required = false) String q,
            @RequestParam(required = false, defaultValue = "20") Integer limit) {
        return ApiResponse.success(domainService.searchDomainsVO(q, limit));
    }

    // ═══════════════ Domain → Entities 子资源 ═══════════════════

    /**
     * 列出领域下实体（强类型 {@link OntologyEntityVO}，复用 T16-1 既有 VO）。
     * <p>走 VO 版 {@code getDomainVO} 检查存在性；实体行的字段映射在 Controller 内
     * 手动落地（与 T16-1 既有 {@code OntologyEntityVO} 字段对齐）。
     */
    @GetMapping("/{domainCode}/entities")
    public ApiResponse<List<OntologyEntityVO>> listDomainEntities(@PathVariable String domainCode) {
        if (domainService.getDomainVO(domainCode) == null) {
            return ApiResponse.notFound("ONT-008: Domain '" + domainCode + "' not found");
        }
        List<OntologyEntityVO> entities = ontologyRepository.findEntitiesByDomain(domainCode).stream()
            .map(e -> {
                OntologyEntityVO vo = new OntologyEntityVO();
                vo.setId(e.getId());
                vo.setCode(e.getCode());
                vo.setName(e.getName());
                vo.setEntityType(e.getEntityType());
                vo.setDescription(e.getDescription());
                // domainId 存 domainCode（与既有 Map 行为一致：listDomainEntities Map 形态
                // 的 `domainCode` key 值即 domainCode，前端消费时不依赖 domain.id 形态）
                vo.setDomainId(domainCode);
                return vo;
            })
            .collect(Collectors.toList());
        return ApiResponse.success(entities);
    }
}
