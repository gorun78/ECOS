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

    @GetMapping
    public ApiResponse<Map<String, Object>> getLineage(
            @RequestParam(required = false) String datasourceId,
            @RequestParam String tableName) {
        try {
            Map<String, Object> result = lineageService.getLineage(datasourceId, tableName);
            if ((int) result.getOrDefault("total_nodes", 0) == 0) {
                return ApiResponse.success("未找到表 " + tableName + " 的血缘关系", result);
            }
            return ApiResponse.success(result);
        } catch (Exception e) {
            return ApiResponse.badRequest("血缘解析失败: " + e.getMessage());
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
