package com.chinacreator.gzcm.market.controller;

import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.market.MarketplaceAssetEntity;
import com.chinacreator.gzcm.market.MarketplaceRepository;
import com.chinacreator.gzcm.market.MarketplaceService;

/**
 * 数据市场 REST API — 资产 CRUD + 上下架 + 申请审批 + 评价 + 搜索
 *
 * <pre>
 * GET    /api/marketplace/assets              — 资产列表 (?sort=popular&limit=10)
 * GET    /api/marketplace/assets/{id}          — 资产详情
 * POST   /api/marketplace/assets              — 发布新资产
 * PUT    /api/marketplace/assets/{id}          — 更新资产
 * PUT    /api/marketplace/assets/{id}/shelf    — 上下架 (body: {"action":"publish|deprecate"})
 * POST   /api/marketplace/request-access       — 申请访问
 * GET    /api/marketplace/requests             — 申请列表 (?status=PENDING)
 * PUT    /api/marketplace/requests/{id}/review — 审批 (body: {"approved":true})
 * POST   /api/marketplace/assets/{id}/review   — 评价
 * GET    /api/marketplace/assets/{id}/review   — 评价列表
 * GET    /api/marketplace/search               — 搜索 (?keyword=&category=)
 * PUT    /api/marketplace/assets/{id}/ontology — 关联本体 (body: {"ontologyEntityId":"xxx"})
 * GET    /api/marketplace/ontology-assets       — 按本体实体ID查询资产 (?entityId=xxx)
 * GET    /api/marketplace/dashboard             — 仪表盘统计
 * </pre>
 */
@RestController
@RequestMapping("/api/marketplace")
public class MarketplaceController {

    private static final Logger log = LoggerFactory.getLogger(MarketplaceController.class);

    private final MarketplaceRepository repository;
    private final MarketplaceService service;

    public MarketplaceController(MarketplaceRepository repository, MarketplaceService service) {
        this.repository = repository;
        this.service = service;
    }

    // ═══════════════ 资产列表 + 详情 ═══════════════════

    @GetMapping("/assets")
    public ApiResponse<Map<String, Object>> listAssets(
            @RequestParam(defaultValue = "popular") String sort,
            @RequestParam(defaultValue = "10") int limit) {
        List<MarketplaceAssetEntity> assets = repository.findAllAssets(sort, limit);
        List<Map<String, Object>> items = assets.stream()
            .map(this::toAssetMap)
            .toList();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("total", repository.countAssets());
        response.put("items", items);
        response.put("sort", sort);
        return ApiResponse.success(response);
    }

    @GetMapping("/assets/{id}")
    public ApiResponse<Map<String, Object>> getAsset(@PathVariable Long id) {
        return repository.findAssetById(id)
            .map(a -> ApiResponse.success(toAssetMap(a)))
            .orElseGet(() -> ApiResponse.notFound("资产 " + id + " 不存在"));
    }

    // ═══════════════ 资产发布 + 更新 + 上下架 ═══════════

    @PostMapping("/assets")
    public ApiResponse<Map<String, Object>> publishAsset(@RequestBody Map<String, Object> body) {
        try {
            return ApiResponse.success(service.publishAsset(body));
        } catch (Exception e) {
            log.error("Failed to publish asset", e);
            return ApiResponse.badRequest(e.getMessage());
        }
    }

    @PutMapping("/assets/{id}")
    public ApiResponse<Map<String, Object>> updateAsset(@PathVariable Long id,
                                                         @RequestBody Map<String, Object> body) {
        return service.updateAsset(id, body)
            .map(ApiResponse::success)
            .orElseGet(() -> ApiResponse.notFound("资产 " + id + " 不存在"));
    }

    @PutMapping("/assets/{id}/shelf")
    public ApiResponse<String> shelfAsset(@PathVariable Long id,
                                           @RequestBody Map<String, Object> body) {
        String action = String.valueOf(body.getOrDefault("action", "publish"));
        boolean ok = service.shelfAsset(id, action);
        return ok ? ApiResponse.success("资产 " + id + " " + ("publish".equalsIgnoreCase(action) ? "已上架" : "已下架"))
                  : ApiResponse.notFound("资产 " + id + " 不存在");
    }

    // ═══════════════ 申请 + 审批 ═══════════════════

    @PostMapping("/request-access")
    public ApiResponse<Map<String, Object>> requestAccess(@RequestBody Map<String, Object> body) {
        try {
            return ApiResponse.success(service.requestAccess(body));
        } catch (IllegalArgumentException e) {
            return ApiResponse.badRequest(e.getMessage());
        }
    }

    @GetMapping("/requests")
    public ApiResponse<List<Map<String, Object>>> listRequests(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String applicant) {
        return ApiResponse.success(service.listRequests(status, applicant));
    }

    @PutMapping("/requests/{id}/review")
    public ApiResponse<Map<String, Object>> approveRequest(@PathVariable Long id,
                                                            @RequestBody Map<String, Object> body) {
        boolean approved = Boolean.parseBoolean(String.valueOf(body.getOrDefault("approved", "true")));
        String comment = String.valueOf(body.getOrDefault("comment", ""));
        return service.approveRequest(id, approved, comment)
            .map(ApiResponse::success)
            .orElseGet(() -> ApiResponse.notFound("申请 " + id + " 不存在"));
    }

    // ═══════════════ 评价 ═══════════════════

    @PostMapping("/assets/{id}/review")
    public ApiResponse<Map<String, Object>> addReview(@PathVariable Long id,
                                                       @RequestBody Map<String, Object> body) {
        try {
            return ApiResponse.success(service.addReview(id, body));
        } catch (IllegalArgumentException e) {
            return ApiResponse.badRequest(e.getMessage());
        }
    }

    @GetMapping("/assets/{id}/review")
    public ApiResponse<List<Map<String, Object>>> listReviews(@PathVariable Long id) {
        return ApiResponse.success(service.listReviews(id));
    }

    // ═══════════════ 搜索 ═══════════════════

    @GetMapping("/search")
    public ApiResponse<List<Map<String, Object>>> search(
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(required = false) String category) {
        return ApiResponse.success(service.searchAssets(keyword, category));
    }

    // ═══════════════ 本体关联 ═══════════════════

    @PutMapping("/assets/{id}/ontology")
    public ApiResponse<Map<String, Object>> associateOntology(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        String ontologyEntityId = String.valueOf(body.getOrDefault("ontologyEntityId", ""));
        Map<String, Object> result = service.associateOntology(id, ontologyEntityId);
        if (result == null) {
            return ApiResponse.notFound("资产 " + id + " 不存在");
        }
        return ApiResponse.success(result);
    }

    @GetMapping("/ontology-assets")
    public ApiResponse<List<Map<String, Object>>> listOntologyAssets(
            @RequestParam String entityId) {
        return ApiResponse.success(service.getOntologyAssets(entityId));
    }

    // ═══════════════ 仪表盘 ═══════════════════

    @GetMapping("/dashboard")
    public ApiResponse<Map<String, Object>> dashboard() {
        return ApiResponse.success(service.getDashboard());
    }

    // ═══════════════ 内部映射 ═══════════════════

    private Map<String, Object> toAssetMap(MarketplaceAssetEntity entity) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", entity.getId());
        map.put("name", entity.getName());
        map.put("description", entity.getDescription());
        map.put("category", entity.getCategory());
        map.put("owner", entity.getOwner());
        map.put("rating", entity.getRating());
        map.put("popularity", entity.getPopularity());
        map.put("status", entity.getStatus());
        map.put("ontologyEntityId", entity.getOntologyEntityId());
        map.put("createdAt", entity.getCreatedAt() != null ? entity.getCreatedAt().toString() : null);

        int hotScore = (entity.getPopularity() != null ? entity.getPopularity() : 0)
            + (entity.getRating() != null ? entity.getRating().multiply(java.math.BigDecimal.valueOf(100)).intValue() : 0);
        map.put("hotScore", hotScore);
        return map;
    }
}
