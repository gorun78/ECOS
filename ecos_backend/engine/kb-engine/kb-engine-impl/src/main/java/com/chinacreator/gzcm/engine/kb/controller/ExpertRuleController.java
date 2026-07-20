package com.chinacreator.gzcm.engine.kb.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.kb.ExpertRuleService;
import com.chinacreator.gzcm.engine.kb.model.ExpertRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/kb/rules")
public class ExpertRuleController {

    private static final Logger log = LoggerFactory.getLogger(ExpertRuleController.class);

    @Autowired
    private ExpertRuleService expertRuleService;

    @GetMapping
    public ApiResponse<List<ExpertRule>> listRules(@RequestParam(required = false) String domain) {
        return ApiResponse.success(expertRuleService.listRules(domain));
    }

    @GetMapping("/{ruleId}")
    public ApiResponse<ExpertRule> getRule(@PathVariable String ruleId) {
        ExpertRule rule = expertRuleService.getRule(ruleId);
        if (rule == null) return ApiResponse.notFound("Rule " + ruleId + " not found");
        return ApiResponse.success(rule);
    }

    @PostMapping
    public ApiResponse<ExpertRule> createRule(@RequestBody ExpertRule rule) {
        return ApiResponse.success(expertRuleService.createRule(rule));
    }

    @PutMapping("/{ruleId}")
    public ApiResponse<ExpertRule> updateRule(@PathVariable String ruleId, @RequestBody ExpertRule rule) {
        return ApiResponse.success(expertRuleService.updateRule(ruleId, rule));
    }

    @DeleteMapping("/{ruleId}")
    public ApiResponse<Map<String, Object>> deleteRule(@PathVariable String ruleId) {
        expertRuleService.deleteRule(ruleId);
        return ApiResponse.success(Map.of("deleted", ruleId));
    }

    @PostMapping("/{ruleId}/execute")
    public ApiResponse<Map<String, Object>> executeRule(
            @PathVariable String ruleId, @RequestBody Map<String, Object> context) {
        return ApiResponse.success(expertRuleService.executeRule(ruleId, context));
    }

    @PostMapping("/batch-import")
    public ApiResponse<Map<String, Object>> batchImport(@RequestBody List<ExpertRule> rules) {
        int count = expertRuleService.batchImport(rules);
        return ApiResponse.success(Map.of("imported", count));
    }
}