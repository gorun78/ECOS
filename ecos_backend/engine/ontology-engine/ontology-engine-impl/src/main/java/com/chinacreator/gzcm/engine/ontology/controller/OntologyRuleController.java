package com.chinacreator.gzcm.engine.ontology.controller;

import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.dccheng.ontology.OntologyRuleService;

/**
 * Rule Controller — 规则设计器（四种规则类型 CRUD + 测试 + 批量评估）
 *
 * <h3>端点：</h3>
 * <ul>
 *   <li>GET    /api/v1/ecos/entities/{entityId}/rules                  — 规则列表</li>
 *   <li>POST   /api/v1/ecos/entities/{entityId}/rules                  — 创建规则</li>
 *   <li>GET    /api/v1/ecos/rules/{ruleId}                             — 规则详情</li>
 *   <li>PUT    /api/v1/ecos/rules/{ruleId}                             — 更新规则</li>
 *   <li>DELETE /api/v1/ecos/rules/{ruleId}                             — 删除规则</li>
 *   <li>POST   /api/v1/ecos/rules/{ruleId}/test                        — 测试规则</li>
 *   <li>POST   /api/v1/ecos/rules/evaluate                             — 批量评估规则</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/ecos")
public class OntologyRuleController {

    private static final Logger log = LoggerFactory.getLogger(OntologyRuleController.class);

    private final OntologyRuleService ruleService;

    public OntologyRuleController(OntologyRuleService ruleService) {
        this.ruleService = ruleService;
    }

    @GetMapping("/entities/{entityId}/rules")
    public ApiResponse<List<Map<String, Object>>> listRules(@PathVariable String entityId) {
        return ApiResponse.success(ruleService.listRulesByEntity(entityId));
    }

    @PostMapping("/entities/{entityId}/rules")
    public ApiResponse<Map<String, Object>> createRule(
            @PathVariable String entityId,
            @RequestBody Map<String, Object> body) {
        Map<String, Object> rule = ruleService.createRule(entityId, body);
        log.info("Rule created: {} [{}] type={}", rule.get("id"), rule.get("code"), rule.get("ruleType"));
        return ApiResponse.success(rule);
    }

    @GetMapping("/rules")
    public ApiResponse<List<Map<String, Object>>> listAllRules() {
        return ApiResponse.success(ruleService.listAllRules());
    }

    @GetMapping("/rules/{ruleId}")
    public ApiResponse<Map<String, Object>> getRule(@PathVariable String ruleId) {
        Map<String, Object> rule = ruleService.getRule(ruleId);
        if (rule == null) return ApiResponse.notFound("ONT-001: Rule '" + ruleId + "' not found");
        return ApiResponse.success(rule);
    }

    @PutMapping("/rules/{ruleId}")
    public ApiResponse<Map<String, Object>> updateRule(
            @PathVariable String ruleId,
            @RequestBody Map<String, Object> body) {
        return ruleService.updateRule(ruleId, body)
            .map(ApiResponse::success)
            .orElseGet(() -> ApiResponse.notFound("ONT-001: Rule '" + ruleId + "' not found"));
    }

    @DeleteMapping("/rules/{ruleId}")
    public ApiResponse<String> deleteRule(@PathVariable String ruleId) {
        if (ruleService.deleteRule(ruleId)) {
            return ApiResponse.success("Rule '" + ruleId + "' deleted");
        }
        return ApiResponse.notFound("ONT-001: Rule '" + ruleId + "' not found");
    }

    @PostMapping("/rules/{ruleId}/test")
    public ApiResponse<Map<String, Object>> testRule(@PathVariable String ruleId) {
        try {
            return ApiResponse.success(ruleService.testRule(ruleId));
        } catch (IllegalArgumentException e) {
            return ApiResponse.badRequest(e.getMessage());
        }
    }

    @PostMapping("/rules/evaluate")
    @SuppressWarnings("unchecked")
    public ApiResponse<List<Map<String, Object>>> evaluateRules(@RequestBody Map<String, Object> body) {
        List<String> entityIds = (List<String>) body.getOrDefault("entityIds", Collections.emptyList());
        return ApiResponse.success(ruleService.evaluateRules(entityIds));
    }
}
