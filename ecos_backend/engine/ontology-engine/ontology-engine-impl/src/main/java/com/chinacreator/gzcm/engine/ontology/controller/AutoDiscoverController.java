package com.chinacreator.gzcm.engine.ontology.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.ontology.service.AutoDiscoverService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/ecos")
public class AutoDiscoverController {

    private final AutoDiscoverService autoDiscoverService;

    public AutoDiscoverController(AutoDiscoverService autoDiscoverService) {
        this.autoDiscoverService = autoDiscoverService;
    }

    @PostMapping("/domains/{domainCode}/auto-discover")
    public ApiResponse<List<Map<String, Object>>> autoDiscover(
            @PathVariable String domainCode,
            @RequestBody Map<String, Object> body) {
        try {
            String datasourceId = (String) body.get("datasourceId");
            @SuppressWarnings("unchecked")
            List<String> resourceNames = (List<String>) body.get("resourceNames");

            if (datasourceId == null || datasourceId.isBlank()) {
                return ApiResponse.badRequest("datasourceId is required");
            }
            if (resourceNames == null || resourceNames.isEmpty()) {
                return ApiResponse.badRequest("resourceNames is required");
            }

            List<Map<String, Object>> results = autoDiscoverService.autoDiscover(domainCode, datasourceId, resourceNames);
            return ApiResponse.success(results);
        } catch (Exception e) {
            return ApiResponse.internalError("AutoDiscover failed: " + e.getMessage());
        }
    }

    @GetMapping("/entity-mappings")
    public ApiResponse<List<Map<String, Object>>> listMappings(@RequestParam(required = false) String domainCode) {
        List<Map<String, Object>> rows = autoDiscoverService.listMappings(domainCode);
        return ApiResponse.success(rows);
    }
}
