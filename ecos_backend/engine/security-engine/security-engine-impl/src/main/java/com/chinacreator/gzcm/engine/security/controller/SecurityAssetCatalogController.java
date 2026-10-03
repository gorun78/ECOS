package com.chinacreator.gzcm.engine.security.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.security.service.SecurityAssetCatalogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 详细设计-01 D.2 — 敏感数据目录管理端点（受保护资产品类）。
 * 三通道唯一入口的"资产受保护清单来源"（C.2.1）：
 * {@code POST /api/v1/security/decision} 传入的 {@code AssetRef.table} 必须
 * 命中本目录（否则按未受保护草稿放行 → 保守口径 fail-closed 见
 * {@code SecurityDecisionService}）。
 */
@RestController
@RequestMapping({"/api/v1/security/assets", "/api/security/assets"})
public class SecurityAssetCatalogController {

    private static final Logger log = LoggerFactory.getLogger(SecurityAssetCatalogController.class);

    private final SecurityAssetCatalogService catalog;

    public SecurityAssetCatalogController(SecurityAssetCatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> listAssets(
            @RequestParam(required = false) String tenantId,
            @RequestParam(required = false) String keyword) {
        try {
            return ApiResponse.success(catalog.list(tenantId, keyword));
        } catch (Exception e) {
            log.error("security assets list failed", e);
            return ApiResponse.internalError("资产目录查询失败");
        }
    }

    @GetMapping("/{tenantId}/{assetKey}")
    public ApiResponse<?> getAsset(@PathVariable String tenantId, @PathVariable String assetKey) {
        try {
            Map<String, Object> row = catalog.getByKey(tenantId, assetKey);
            return row == null ? ApiResponse.notFound("资产不存在: " + assetKey) : ApiResponse.success(row);
        } catch (Exception e) {
            log.error("security asset get failed", e);
            return ApiResponse.internalError("资产查询失败");
        }
    }

    /** upsert（新表 / 更新 guard_combo / 版本递增；不物理删）。 */
    @PostMapping
    public ApiResponse<?> upsertAsset(@RequestBody Map<String, Object> body) {
        try {
            return ApiResponse.success(catalog.upsert(body));
        } catch (IllegalArgumentException e) {
            return ApiResponse.badRequest(e.getMessage());
        } catch (Exception e) {
            log.error("security asset upsert failed", e);
            return ApiResponse.internalError("资产写入失败");
        }
    }

    /** 停用（IR03：只停写）。 */
    @DeleteMapping("/{tenantId}/{assetKey}")
    public ApiResponse<?> disableAsset(@PathVariable String tenantId, @PathVariable String assetKey) {
        try {
            Map<String, Object> r = catalog.disable(tenantId, assetKey);
            return r == null ? ApiResponse.notFound("资产不存在") : ApiResponse.success(r);
        } catch (Exception e) {
            log.error("security asset disable failed", e);
            return ApiResponse.internalError("资产停用失败");
        }
    }
}
