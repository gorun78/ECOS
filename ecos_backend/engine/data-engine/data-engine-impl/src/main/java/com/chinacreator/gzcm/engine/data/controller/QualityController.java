package com.chinacreator.gzcm.engine.data.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.data.QualityService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 数据质量 REST API（旧端点，只读兼容层）— PMO-48-A T4 写操作下线 410。
 * <p>
 * 规则/评估写操作（规则 CRUD + evaluate 触发）已随 DQ 基础设施合并统一 410 Gone，
 * 只读 GET 端点（规则列表/详情、评估历史）保持只读兼容，读旧表 ecos_quality_rule / dq_evaluation_results。
 * 新端点在 /api/v1/dq/*（DqGovernanceController，T3 交付）。
 * </p>
 */
@RestController
@RequestMapping("/api/v1/engine/data/quality")
public class QualityController {

    private static final Logger log = LoggerFactory.getLogger(QualityController.class);

    /** PMO-48-A T4：写操作统一 410 Gone，说明迁移到 /api/v1/dq/*。 */
    private static final String GONE_MSG = "DQ 写操作已迁移到 /api/v1/dq/rules（Phase 1 只读兼容，写操作 Phase 2 在新端点开放）";

    private final QualityService qualityService;

    public QualityController(QualityService qualityService) {
        this.qualityService = qualityService;
    }

    @PostMapping("/rules")
    public ApiResponse<Map<String, Object>> createRule(@RequestBody Map<String, Object> body) {
        log.warn("DQ legacy write blocked: createRule");
        return ApiResponse.error(410, "GONE", GONE_MSG);
    }

    @PutMapping("/rules/{ruleId}")
    public ApiResponse<Map<String, Object>> updateRule(@PathVariable String ruleId,
                                                        @RequestBody Map<String, Object> body) {
        log.warn("DQ legacy write blocked: updateRule ruleId={}", ruleId);
        return ApiResponse.error(410, "GONE", GONE_MSG);
    }

    @DeleteMapping("/rules/{ruleId}")
    public ApiResponse<Void> deleteRule(@PathVariable String ruleId) {
        log.warn("DQ legacy write blocked: deleteRule ruleId={}", ruleId);
        return ApiResponse.error(410, "GONE", GONE_MSG);
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
        log.warn("DQ legacy write blocked: evaluate");
        return ApiResponse.error(410, "GONE", GONE_MSG);
    }

    @PostMapping("/rules/{ruleId}/evaluate")
    public ApiResponse<Map<String, Object>> evaluateRule(@PathVariable String ruleId,
                                                          @RequestBody Map<String, Object> body) {
        log.warn("DQ legacy write blocked: evaluateRule ruleId={}", ruleId);
        return ApiResponse.error(410, "GONE", GONE_MSG);
    }

    @GetMapping("/evaluations")
    public ApiResponse<Map<String, Object>> getEvaluationHistory(
            @RequestParam(required = false) String datasetId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.success(qualityService.getEvaluationHistory(datasetId, page, pageSize));
    }

    /**
     * 手动全量巡检 — 触发评估+写 evaluation 表，属写操作，已下线 410（PMO-48-A T4）。
     */
    @PostMapping("/evaluate-all")
    public ApiResponse<Map<String, Object>> evaluateAll() {
        log.warn("DQ legacy write blocked: evaluateAll");
        return ApiResponse.error(410, "GONE", GONE_MSG);
    }
}
