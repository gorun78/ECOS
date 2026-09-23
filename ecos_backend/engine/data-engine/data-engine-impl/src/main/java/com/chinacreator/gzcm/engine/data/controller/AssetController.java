package com.chinacreator.gzcm.engine.data.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.data.dto.*;
import com.chinacreator.gzcm.engine.data.service.AssetService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 数据资产 + 分级分类 REST API（PMO-data10）。
 *
 * <p>端点路由（与现有 {@code /api/v1/datanet/**} 同源，ABAC 拦截器覆盖）：</p>
 * <ul>
 *   <li>GET  /api/v1/datanet/assets                — 列表（按 layer/zone/category/level/sensitivity 过滤）</li>
 *   <li>POST /api/v1/datanet/assets                — 新增（强类型 {@link DataAssetSaveDTO}）</li>
 *   <li>GET  /api/v1/datanet/assets/{assetId}      — 详情（含 level/category 名字冗余）</li>
 *   <li>PUT  /api/v1/datanet/assets/{assetId}      — 编辑（COALESCE 语义）</li>
 *   <li>DELETE /api/v1/datanet/assets/{assetId}    — 逻辑删除（IR03 不真删）</li>
 *   <li>GET  /api/v1/datanet/assets/{assetId}/fields — 字段级敏感度分页（可过滤 confirmed）</li>
 *
 *   <li>POST /api/v1/datanet/assets/{assetId}/security-tag — 字段级打标（人工确认后才发 Kafka 事件）</li>
 *   <li>POST /api/v1/llm/realworld/recommend-tags       — LLM 推荐分级（推荐只写 recommend_*, 不自动生效）</li>
 *   <li>GET  /api/v1/datanet/levels                    — 4 级字典（资产前 taxonomy）</li>
 *   <li>GET  /api/v1/datanet/categories                — 业务分类树</li>
 * </ul>
 *
 * <p>设计约束（架构铁律 §3 + 数据库规范）：</p>
 * <ul>
 *   <li>入参/出参 强类型，禁 Map（除 levels/categories list outside VOs —— 字典值无固定 schema）</li>
 *   <li>ID 校验走 {@code ID_PAT} 正则 + {@code requireId()}（防 SQL 注入 + 标号同时用）</li>
 *   <li>敏感列 constraint 在 JD 层 —— service 层不 host PG 连接（IR01）</li>
 *   <li>事件发布走 runtime-event {@code EventBusService}，由 data-engine → security-engine（IR06 不跨引擎直连）</li>
 * </ul>
 *
 * @author DataBridge Datanet Team
 */
@RestController
@RequestMapping("/api/v1/datanet")
public class AssetController {

    private final AssetService assetService;

    public AssetController(AssetService assetService) {
        this.assetService = assetService;
    }

    // ── 1. 分页列表 ────────────────────────────────────────────

    /** 资产分页列表（按 layer/zone/category/level/keyword/owner 过滤）。 */
    @GetMapping("/assets")
    public ApiResponse<List<DataAssetVO>> listAssets(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String sensitivityLevel,
            @RequestParam(required = false) String categoryId,
            @RequestParam(required = false) String layer,
            @RequestParam(required = false) String zone,
            @RequestParam(required = false) String datasourceId,
            @RequestParam(required = false) String owner,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) String domain,
            @RequestParam(required = false) String categoryStatus,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        DataAssetQueryDTO q = new DataAssetQueryDTO();
        q.setKeyword(keyword);
        q.setSensitivityLevel(sensitivityLevel);
        q.setCategoryId(categoryId);
        q.setLayer(layer);
        q.setZone(zone);
        q.setDatasourceId(datasourceId);
        q.setOwner(owner);
        q.setResourceType(resourceType);
        q.setDomain(domain);
        q.setCategoryStatus(categoryStatus);
        q.setPage(page);
        q.setPageSize(pageSize);
        return ApiResponse.success(assetService.listAssets(q));
    }

    // ── 2. 资产 CRUD ───────────────────────────────────────────

    /** 新增资产（resource_id 必须存在于 td_data_resource；unique (resource_id + domain)）。 */
    @PostMapping("/assets")
    public ApiResponse<DataAssetVO> createAsset(@RequestBody DataAssetSaveDTO dto) {
        return ApiResponse.success("资产创建成功", assetService.createAsset(dto));
    }

    /** 资产详情。 */
    @GetMapping("/assets/{assetId}")
    public ApiResponse<DataAssetVO> getAsset(@PathVariable String assetId) {
        DataAssetVO vo = assetService.getAsset(assetId);
        return vo == null
            ? ApiResponse.notFound("ASSET-010: asset 不存在: " + assetId)
            : ApiResponse.success(vo);
    }

    /** 编辑资产（COALESCE 语义，缺失字段不修改）。 */
    @PutMapping("/assets/{assetId}")
    public ApiResponse<DataAssetVO> updateAsset(
            @PathVariable String assetId,
            @RequestBody DataAssetSaveDTO dto) {
        return ApiResponse.success("资产已更新", assetService.updateAsset(assetId, dto));
    }

    /** 逻辑删除资产。 */
    @DeleteMapping("/assets/{assetId}")
    public ApiResponse<Boolean> deleteAsset(
            @PathVariable String assetId,
            @RequestParam(required = false) String operator) {
        return ApiResponse.success("资产已删除", assetService.deleteAsset(assetId, operator));
    }

    // ── 3. 字段级敏感度 ────────────────────────────────────────

    /** 字段级敏感度分页列表（可选 confirmed=true 过滤）。 */
    @GetMapping("/assets/{assetId}/fields")
    public ApiResponse<DataAssetFieldPageVO> listFields(
            @PathVariable String assetId,
            @RequestParam(required = false) Boolean confirmed,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.success(assetService.listFields(assetId, confirmed, page, pageSize));
    }

    /**
     * 字段级打标（人工确认才触发 Kafka 事件 <code>ecos.data.security-tagged</code>）。
     *
     * <p>{@code confirmed=true} 时：</p>
     * <ul>
     *   <li>写入字段 field_sensitivity / mask_strategy / data_type</li>
     *   <li>资产 category_status → CONFIRMED, last_tagged_by/at 同步</li>
     *   <li>资产 sensitivity_level = MAX(字段 sensitivity)</li>
     *   <li>发事件（confirmed 命中 L3/L4 的字段明细，最多 100）</li>
     * </ul>
     * {@code confirmed=false}：仅写 recommend_level / recommend_source（LLM 推荐场景）</li>
     */
    @PostMapping("/assets/{assetId}/security-tag")
    public ApiResponse<DataAssetVO> tagAssetFields(
            @PathVariable String assetId,
            @RequestBody DataAssetFieldTagDTO dto) {
        return ApiResponse.success("打标完成", assetService.tagAssetFields(assetId, dto));
    }

    // ── 4. 字典 ──────────────────────────────────────────────

    /** 4 级字典（公开/内部/敏感/核心机密）。 */
    @GetMapping("/levels")
    public ApiResponse<List<Map<String, Object>>> listLevels() {
        return ApiResponse.success(assetService.listLevelDefs());
    }

    /** 业务分类树（≤3 级，按 level 排序）。 */
    @GetMapping("/categories")
    public ApiResponse<List<Map<String, Object>>> listCategories() {
        return ApiResponse.success(assetService.listCategoryTree());
    }
}
