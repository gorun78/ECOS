package com.chinacreator.gzcm.engine.security.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.security.catalog.SecurityAssetCatalogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 详细设计-01 F01-09 — 敏感数据目录（安全资产）REST 门面。
 *
 * <p>OpenAPI 对齐 §D.5：{@code listSecurityAssets} / {@code upsertSecurityAsset}。
 * 双路径 {@code /api/v1/security/assets}（V1 主形态，CLEARANCE_EXEMPT 同源）+
 * {@code /api/security/assets}（兼容路径）。</p>
 */
@RestController
@RequestMapping({"/api/security/assets", "/api/v1/security/assets"})
public class SecurityAssetCatalogController {

    private static final Logger log = LoggerFactory.getLogger(SecurityAssetCatalogController.class);

    private final SecurityAssetCatalogService catalogService;

    public SecurityAssetCatalogController(SecurityAssetCatalogService catalogService) {
        this.catalogService = catalogService;
    }

    /**
     * 未删除的全量目录，按 asset_key 升序。
     */
    @GetMapping
    public ApiResponse<List<Map<String, Object>>> listSecurityAssets() {
        try {
            return ApiResponse.success(catalogService.listAll());
        } catch (Exception e) {
            log.error("查询安全资产目录失败", e);
            return ApiResponse.internalError("查询失败: " + e.getMessage());
        }
    }

    /**
     * 按 (tenant, asset_key) 条件的 upsert。请求体字段：
     * {@code assetKey}（必填，形态 schema.table[.column]）、{@code kind}（COLUMN|TABLE）、
     * {@code sensitivity}（S0-S4）、{@code guardCombo}（rls/cls/mask 组合）、
     * {@code exemptionRef}（可空，ST03-A 条目号）、{@code ownerRole}、{@code description}、
     * {@code tenantId}（可空）。
     */
    @PutMapping
    public ApiResponse<Map<String, Object>> upsertSecurityAsset(@RequestBody Map<String, Object> body) {
        String assetKey = (String) body.get("assetKey");
        if (assetKey == null || assetKey.isBlank()) {
            return ApiResponse.badRequest("assetKey 不能为空");
        }
        try {
            Map<String, Object> row = catalogService.upsert(
                    assetKey,
                    (String) body.get("kind"),
                    (String) body.get("sensitivity"),
                    (String) body.get("guardCombo"),
                    (String) body.get("exemptionRef"),
                    (String) body.get("ownerRole"),
                    (String) body.get("description"),
                    (String) body.get("tenantId"));
            return ApiResponse.success(row);
        } catch (IllegalArgumentException e) {
            // 400 ECOS-SEC-410：目录参数白名单校验
            return ApiResponse.badRequest("非法资产参数（ECOS-SEC-410）: " + e.getMessage());
        } catch (Exception e) {
            log.error("upsert 安全资产失败: key={}", assetKey, e);
            return ApiResponse.internalError("upsert 失败: " + e.getMessage());
        }
    }

    /**
     * 逻辑删除（is_deleted=1）。仅删，无物理 DROP。
     */
    @DeleteMapping("/{assetKey}")
    public ApiResponse<Map<String, Object>> delete(@PathVariable String assetKey) {
        boolean ok = catalogService.logicalDelete(assetKey);
        if (!ok) return ApiResponse.notFound("安全资产不存在或已删除: " + assetKey);
        return ApiResponse.success(java.util.Map.of("assetKey", assetKey, "deleted", true));
    }
}
