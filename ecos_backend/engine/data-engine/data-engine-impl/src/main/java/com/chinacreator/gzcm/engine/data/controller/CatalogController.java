package com.chinacreator.gzcm.engine.data.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.datanet.dto.CatalogQueryDTO;
import com.chinacreator.gzcm.datanet.model.CatalogItem;
import com.chinacreator.gzcm.datanet.model.DataResource;
import com.chinacreator.gzcm.datanet.service.CatalogService;
import com.chinacreator.gzcm.engine.data.service.CatalogDashboardService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/datanet/catalog")
public class CatalogController {

    private final CatalogService catalogService;
    private final CatalogDashboardService dashboardService;

    public CatalogController(CatalogService catalogService,
                              CatalogDashboardService dashboardService) {
        this.catalogService = catalogService;
        this.dashboardService = dashboardService;
    }

    @GetMapping("/search")
    public ApiResponse<List<CatalogItem>> search(CatalogQueryDTO query) {
        return ApiResponse.success(catalogService.search(query));
    }

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

    @GetMapping("/dashboard")
    public ApiResponse<Map<String, Object>> dashboard() {
        return ApiResponse.success(dashboardService.getDashboard());
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
