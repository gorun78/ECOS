package com.chinacreator.gzcm.engine.data.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.data.service.DataLineageService;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/engine/data/lineage")
public class DataLineageController {

    private final DataLineageService lineageService;

    public DataLineageController(DataLineageService lineageService) {
        this.lineageService = lineageService;
    }

    @GetMapping("/pipeline/{taskId}")
    public ApiResponse<Map<String, Object>> pipelineLineage(@PathVariable String taskId) {
        try {
            return ApiResponse.success(lineageService.getPipelineLineage(taskId));
        } catch (Exception e) {
            return ApiResponse.notFound("Pipeline " + taskId + " 不存在或解析失败");
        }
    }

    @GetMapping("/nodes")
    public ApiResponse<List<Map<String, Object>>> listNodes() {
        return ApiResponse.success(lineageService.listNodes());
    }

    @GetMapping("/edges")
    public ApiResponse<List<Map<String, Object>>> listEdges() {
        return ApiResponse.success(lineageService.listEdges());
    }

    @PostMapping("/build")
    public ApiResponse<Map<String, Object>> buildTopology(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<String> pipelineIds = (List<String>) body.getOrDefault("pipeline_ids", List.of());
        boolean includeDb = Boolean.TRUE.equals(body.getOrDefault("include_databases", true));
        boolean includeTables = Boolean.TRUE.equals(body.getOrDefault("include_tables", true));

        return ApiResponse.success(lineageService.buildTopology(pipelineIds, includeDb, includeTables));
    }
}
