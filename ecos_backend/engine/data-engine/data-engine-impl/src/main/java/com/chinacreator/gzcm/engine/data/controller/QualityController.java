package com.chinacreator.gzcm.engine.data.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.data.QualityService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/engine/data/quality")
public class QualityController {

    private final QualityService qualityService;

    public QualityController(QualityService qualityService) {
        this.qualityService = qualityService;
    }

    @PostMapping("/rules")
    public ApiResponse<Map<String, Object>> createRule(@RequestBody Map<String, Object> body) {
        try {
            return ApiResponse.success(qualityService.createRule(body));
        } catch (Exception e) {
            return ApiResponse.badRequest(e.getMessage());
        }
    }

    @PutMapping("/rules/{ruleId}")
    public ApiResponse<Map<String, Object>> updateRule(@PathVariable String ruleId,
                                                        @RequestBody Map<String, Object> body) {
        try {
            return ApiResponse.success(qualityService.updateRule(ruleId, body));
        } catch (Exception e) {
            return ApiResponse.badRequest(e.getMessage());
        }
    }

    @DeleteMapping("/rules/{ruleId}")
    public ApiResponse<Void> deleteRule(@PathVariable String ruleId) {
        try {
            qualityService.deleteRule(ruleId);
            return ApiResponse.success("删除成功", null);
        } catch (Exception e) {
            return ApiResponse.badRequest(e.getMessage());
        }
    }

    @GetMapping("/rules/{ruleId}")
    public ApiResponse<Map<String, Object>> getRule(@PathVariable String ruleId) {
        try {
            return ApiResponse.success(qualityService.getRule(ruleId));
        } catch (Exception e) {
            return ApiResponse.notFound(e.getMessage());
        }
    }

    @GetMapping("/rules")
    public ApiResponse<Map<String, Object>> listRules(
            @RequestParam(required = false) String datasetId,
            @RequestParam(required = false) String ruleType,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.success(qualityService.listRules(datasetId, ruleType, page, pageSize));
    }

    @PostMapping("/evaluate")
    public ApiResponse<Map<String, Object>> evaluate(@RequestBody Map<String, Object> body) {
        try {
            String datasetId = (String) body.get("dataset_id");
            String datasourceId = (String) body.get("datasource_id");
            String tableName = (String) body.get("table_name");
            int sampleSize = body.containsKey("sample_size") ? ((Number) body.get("sample_size")).intValue() : 1000;

            if (datasetId == null) return ApiResponse.badRequest("dataset_id 不能为空");

            return ApiResponse.success(qualityService.evaluate(datasetId, datasourceId, tableName, sampleSize));
        } catch (Exception e) {
            return ApiResponse.internalError("质量评估失败: " + e.getMessage());
        }
    }

    @PostMapping("/rules/{ruleId}/evaluate")
    public ApiResponse<Map<String, Object>> evaluateRule(@PathVariable String ruleId,
                                                          @RequestBody Map<String, Object> body) {
        try {
            String datasourceId = (String) body.get("datasource_id");
            String tableName = (String) body.get("table_name");
            int sampleSize = body.containsKey("sample_size") ? ((Number) body.get("sample_size")).intValue() : 1000;

            return ApiResponse.success(qualityService.evaluateRule(ruleId, datasourceId, tableName, sampleSize));
        } catch (Exception e) {
            return ApiResponse.internalError("规则评估失败: " + e.getMessage());
        }
    }

    @GetMapping("/evaluations")
    public ApiResponse<Map<String, Object>> getEvaluationHistory(
            @RequestParam(required = false) String datasetId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.success(qualityService.getEvaluationHistory(datasetId, page, pageSize));
    }
}
