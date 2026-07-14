package com.chinacreator.gzcm.datanet.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.datanet.dto.CatalogQueryDTO;
import com.chinacreator.gzcm.datanet.model.CatalogItem;
import com.chinacreator.gzcm.datanet.model.DataField;
import com.chinacreator.gzcm.datanet.model.DataResource;
import com.chinacreator.gzcm.datanet.service.CatalogService;
import com.chinacreator.gzcm.datanet.service.MetadataService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 数据目录 Controller — 资源发现和搜索。
 *
 * @author DataBridge Datanet Team
 */
@RestController
@RequestMapping("/datanet/catalog")
public class CatalogController {

    private final CatalogService catalogService;
    private final MetadataService metadataService;

    public CatalogController(CatalogService catalogService,
                              MetadataService metadataService) {
        this.catalogService = catalogService;
        this.metadataService = metadataService;
    }

    @GetMapping("/search")
    public ApiResponse<List<CatalogItem>> search(CatalogQueryDTO query) {
        return ApiResponse.success(catalogService.search(query));
    }

    /**
     * 按字段名搜索资源。
     */
    @GetMapping("/search/field")
    public ApiResponse<Map<String, Object>> searchByField(
            @RequestParam String fieldName,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        List<CatalogItem> items = catalogService.searchByFieldName(fieldName, page, pageSize);
        long total = catalogService.countByFieldName(fieldName);
        return ApiResponse.success(Map.of(
                "items", items,
                "total", total,
                "page", page,
                "pageSize", pageSize
        ));
    }

    /**
     * 仪表盘统计 — 总览各维度数据。
     */
    @GetMapping("/dashboard")
    public ApiResponse<Map<String, Object>> dashboard() {
        Map<String, Object> stats = new java.util.LinkedHashMap<>();
        long totalRes = catalogService.count();
        stats.put("totalResources", totalRes);
        stats.put("totalFields", countAllFields());
        stats.put("resourceTypes", countByType());
        return ApiResponse.success(stats);
    }

    private long countAllFields() {
        // 从完整的数据集汇总字段数
        long total = 0;
        int page = 1;
        int pageSize = 100;
        java.util.List<CatalogItem> batch;
        do {
            CatalogQueryDTO q = new CatalogQueryDTO();
            q.setPage(page++);
            q.setPageSize(pageSize);
            batch = catalogService.search(q);
            for (CatalogItem item : batch) {
                total += item.getFieldCount() != null ? item.getFieldCount() : 0;
            }
        } while (batch.size() == pageSize);
        return total;
    }

    private Map<String, Long> countByType() {
        // 按资源类型统计 — 分页遍历全部数据
        Map<String, Long> typeCounts = new java.util.HashMap<>();
        int page = 1;
        int pageSize = 100;
        java.util.List<CatalogItem> batch;
        do {
            CatalogQueryDTO q = new CatalogQueryDTO();
            q.setPage(page++);
            q.setPageSize(pageSize);
            batch = catalogService.search(q);
            for (CatalogItem item : batch) {
                String type = item.getResourceType() != null ? item.getResourceType() : "OTHER";
                typeCounts.merge(type, 1L, Long::sum);
            }
        } while (batch.size() == pageSize);
        return typeCounts;
    }

    @GetMapping("/{catalogId}")
    public ApiResponse<CatalogItem> getById(@PathVariable String catalogId) {
        CatalogItem item = catalogService.getById(catalogId);
        if (item == null) {
            return ApiResponse.error(404, "目录项不存在: " + catalogId);
        }
        return ApiResponse.success(item);
    }

    @PostMapping("/register")
    public ApiResponse<CatalogItem> register(@RequestBody DataResource resource) {
        return ApiResponse.success(catalogService.register(resource));
    }

    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> stats() {
        return ApiResponse.success(Map.of("total", catalogService.count()));
    }
}
