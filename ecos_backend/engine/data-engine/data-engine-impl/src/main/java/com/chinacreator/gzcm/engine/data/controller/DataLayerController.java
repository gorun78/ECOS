package com.chinacreator.gzcm.engine.data.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.data.model.DataLayer;
import com.chinacreator.gzcm.engine.data.service.DataLayerService;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/engine/data/layers")
public class DataLayerController {

    private final DataLayerService dataLayerService;

    public DataLayerController(DataLayerService dataLayerService) {
        this.dataLayerService = dataLayerService;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> getLayerSummary() {
        Map<String, Object> summary = dataLayerService.getLayerSummary();
        return ApiResponse.success(summary);
    }

    @GetMapping("/{layer}")
    public ApiResponse<Map<String, Object>> getResourcesByLayer(@PathVariable String layer) {
        try {
            DataLayer.valueOf(layer);
        } catch (IllegalArgumentException e) {
            return ApiResponse.badRequest("Invalid layer: " + layer);
        }
        List<Map<String, Object>> resources = dataLayerService.getResourcesByLayer(layer);
        return ApiResponse.success(Map.of("layer", layer, "resources", resources, "total", resources.size()));
    }
}
