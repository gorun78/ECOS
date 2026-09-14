package com.chinacreator.gzcm.engine.ontology.controller;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.ontology.dto.OntologyRuleEvaluateQuery;
import com.chinacreator.gzcm.engine.ontology.dto.OntologyRuleEvaluationVO;
import com.chinacreator.gzcm.engine.ontology.dto.OntologyRuleSaveDTO;
import com.chinacreator.gzcm.engine.ontology.dto.OntologyRuleVO;
import com.chinacreator.gzcm.engine.ontology.service.OntologyRuleService;

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
 *
 * <p>T16-1 (2026-09-12)：方法入/出参由 {@code Map<String,Object>} 改为强类型 DTO/VO
 * （{@code OntologyRuleSaveDTO} / {@code OntologyRuleVO} / {@code OntologyRuleEvaluateQuery} /
 * {@code OntologyRuleEvaluationVO}）。
 */
@RestController
@RequestMapping("/api/v1/ecos")
public class OntologyRuleController {

    private static final Logger log = LoggerFactory.getLogger(OntologyRuleController.class);

    private final OntologyRuleService ruleService;

    public OntologyRuleController(OntologyRuleService ruleService) {
        this.ruleService = ruleService;
    }

    /** 列出实体的规则（强类型版本）。 */
    @GetMapping("/entities/{entityId}/rules")
    public ApiResponse<List<OntologyRuleVO>> listRules(@PathVariable String entityId) {
        return ApiResponse.success(ruleService.listRulesByEntityVO(entityId));
    }

    /** 创建规则 — 接收 {@code OntologyRuleSaveDTO}。 */
    @PostMapping("/entities/{entityId}/rules")
    public ApiResponse<OntologyRuleVO> createRule(
            @PathVariable String entityId,
            @RequestBody OntologyRuleSaveDTO dto) {
        OntologyRuleVO rule = ruleService.createRule(entityId, dto);
        log.info("Rule created: {} [{}] type={}", rule.getId(), rule.getCode(), rule.getRuleType());
        return ApiResponse.success(rule);
    }

    /** 列出全部规则（强类型版本）。 */
    @GetMapping("/rules")
    public ApiResponse<List<OntologyRuleVO>> listAllRules() {
        return ApiResponse.success(ruleService.listAllRulesVO());
    }

    /** 规则详情 — 强类型版本。 */
    @GetMapping("/rules/{ruleId}")
    public ApiResponse<OntologyRuleVO> getRule(@PathVariable String ruleId) {
        OntologyRuleVO rule = ruleService.getRuleVO(ruleId);
        if (rule == null) {
            return ApiResponse.notFound("ONT-001: Rule '" + ruleId + "' not found");
        }
        return ApiResponse.success(rule);
    }

    /** 更新规则 — 接收 {@code OntologyRuleSaveDTO}。 */
    @PutMapping("/rules/{ruleId}")
    public ApiResponse<OntologyRuleVO> updateRule(
            @PathVariable String ruleId,
            @RequestBody OntologyRuleSaveDTO dto) {
        return ruleService.updateRule(ruleId, dto)
            .map(ApiResponse::success)
            .orElseGet(() -> ApiResponse.notFound("ONT-001: Rule '" + ruleId + "' not found"));
    }

    /** 删除规则（强类型未对 boolean 引入新 VO，保留 String 简返，与现有动作删除路径一致）。 */
    @DeleteMapping("/rules/{ruleId}")
    public ApiResponse<String> deleteRule(@PathVariable String ruleId) {
        if (ruleService.deleteRule(ruleId)) {
            return ApiResponse.success("Rule '" + ruleId + "' deleted");
        }
        return ApiResponse.notFound("ONT-001: Rule '" + ruleId + "' not found");
    }

    /** 测试单条规则 — 强类型版本。 */
    @PostMapping("/rules/{ruleId}/test")
    public ApiResponse<OntologyRuleEvaluationVO> testRule(@PathVariable String ruleId) {
        try {
            return ApiResponse.success(ruleService.testRuleVO(ruleId));
        } catch (IllegalArgumentException e) {
            return ApiResponse.badRequest(e.getMessage());
        }
    }

    /** 批量评估规则 — 接收 {@code OntologyRuleEvaluateQuery}（entityIds）。 */
    @PostMapping("/rules/evaluate")
    public ApiResponse<List<OntologyRuleEvaluationVO>> evaluateRules(
            @RequestBody OntologyRuleEvaluateQuery query) {
        return ApiResponse.success(ruleService.evaluateRulesVO(
            query.getEntityIds() == null ? java.util.Collections.emptyList() : query.getEntityIds()));
    }
}
