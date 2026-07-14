package com.chinacreator.gzcm.buszhi.workflow.controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.buszhi.workflow.DqService;

/**
 * Data Quality REST API — 数据质量规则/问题/仪表盘（JdbcTemplate 持久化）
 *
 * <pre>
 * GET    /api/v1/ecos/dq/rules              — 规则列表
 * GET    /api/v1/ecos/dq/issues             — 问题列表
 * GET    /api/v1/ecos/dq/dashboard          — 仪表盘汇总
 * POST   /api/v1/ecos/dq/rules              — 创建规则
 * PUT    /api/v1/ecos/dq/rules/{id}         — 更新规则
 * DELETE /api/v1/ecos/dq/rules/{id}         — 删除规则
 * POST   /api/v1/ecos/dq/issues             — 创建问题
 * PUT    /api/v1/ecos/dq/issues/{id}        — 更新问题
 * DELETE /api/v1/ecos/dq/issues/{id}        — 删除问题
 * POST   /api/v1/ecos/dq/check              — 执行检查
 * POST   /api/v1/ecos/dq/issues/{id}/resolve — 解决问题
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/ecos/dq")
public class DqController {

    private static final Logger log = LoggerFactory.getLogger(DqController.class);

    private final DqService dqService;

    public DqController(DqService dqService) {
        this.dqService = dqService;
    }

    // ═══════════════ 规则 CRUD ═══════════════════

    @GetMapping("/rules")
    public ApiResponse<Map<String, Object>> listRules() {
        List<Map<String, Object>> list = dqService.listRules();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("data", list);
        result.put("total", dqService.totalRules());
        return ApiResponse.success(result);
    }

    @GetMapping("/rules/{id}")
    public ApiResponse<Map<String, Object>> getRule(@PathVariable Long id) {
        return dqService.getRule(id)
            .map(ApiResponse::success)
            .orElseGet(() -> ApiResponse.notFound("规则 " + id + " 不存在"));
    }

    @PostMapping("/rules")
    public ApiResponse<Map<String, Object>> createRule(@RequestBody Map<String, Object> body) {
        Map<String, Object> rule = dqService.createRule(body);
        log.info("DQ rule created via DB: {} [{}]", rule.get("id"), rule.get("name"));
        return ApiResponse.success(rule);
    }

    @PutMapping("/rules/{id}")
    public ApiResponse<Map<String, Object>> updateRule(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return dqService.updateRule(id, body)
            .map(ApiResponse::success)
            .orElseGet(() -> ApiResponse.notFound("规则 " + id + " 不存在"));
    }

    @DeleteMapping("/rules/{id}")
    public ApiResponse<Map<String, Object>> deleteRule(@PathVariable Long id) {
        if (dqService.deleteRule(id)) {
            log.info("DQ rule deleted: {}", id);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            return ApiResponse.success(result);
        }
        return ApiResponse.notFound("规则 " + id + " 不存在");
    }

    // ═══════════════ 问题 CRUD ═══════════════════

    @GetMapping("/issues")
    public ApiResponse<Map<String, Object>> listIssues() {
        List<Map<String, Object>> list = dqService.listIssues();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("data", list);
        result.put("total", dqService.totalIssues());
        return ApiResponse.success(result);
    }

    @GetMapping("/issues/{id}")
    public ApiResponse<Map<String, Object>> getIssue(@PathVariable Long id) {
        return dqService.getIssue(id)
            .map(ApiResponse::success)
            .orElseGet(() -> ApiResponse.notFound("问题 " + id + " 不存在"));
    }

    @PostMapping("/issues")
    public ApiResponse<Map<String, Object>> createIssue(@RequestBody Map<String, Object> body) {
        Map<String, Object> issue = dqService.createIssue(body);
        log.info("DQ issue created via DB: {} [rule={}]", issue.get("id"), issue.get("ruleId"));
        return ApiResponse.success(issue);
    }

    @PutMapping("/issues/{id}")
    public ApiResponse<Map<String, Object>> updateIssue(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return dqService.updateRule(id, body)
            .map(ApiResponse::success)
            .orElseGet(() -> ApiResponse.notFound("问题 " + id + " 不存在"));
    }

    @PostMapping("/issues/{id}/resolve")
    public ApiResponse<Map<String, Object>> resolveIssue(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        String resolution = String.valueOf(body.getOrDefault("resolution", "已修复"));
        return dqService.resolveIssue(id, resolution)
            .map(ApiResponse::success)
            .orElseGet(() -> ApiResponse.notFound("问题 " + id + " 不存在"));
    }

    @DeleteMapping("/issues/{id}")
    public ApiResponse<Map<String, Object>> deleteIssue(@PathVariable Long id) {
        if (dqService.deleteIssue(id)) {
            log.info("DQ issue deleted: {}", id);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            return ApiResponse.success(result);
        }
        return ApiResponse.notFound("问题 " + id + " 不存在");
    }

    // ═══════════════ 仪表盘 ═══════════════════

    @GetMapping("/dashboard")
    public ApiResponse<Map<String, Object>> dashboard() {
        return ApiResponse.success(dqService.dashboard());
    }

    // ═══════════════ 检查 ═══════════════════

    @PostMapping("/check")
    public ApiResponse<Map<String, Object>> runCheck() {
        return ApiResponse.success(dqService.runCheck());
    }

    // ═══════════════ 兼容旧版统一路径（委托为新版） ═══════════════════

    /**
     * 兼容旧版 POST /{type} 路径 — 委托至对应的新版端点。
     */
    @PostMapping("/{type}")
    public ApiResponse<Map<String, Object>> createLegacy(@PathVariable String type, @RequestBody Map<String, Object> body) {
        if ("rules".equals(type)) return createRule(body);
        if ("issues".equals(type)) return createIssue(body);
        return ApiResponse.error(400, "不支持的 type: " + type);
    }

    /**
     * 兼容旧版 DELETE /{type}/{id} 路径 — 委托至对应的新版端点。
     */
    @DeleteMapping("/{type}/{id}")
    public ApiResponse<Map<String, Object>> deleteLegacy(@PathVariable String type, @PathVariable Long id) {
        if ("rules".equals(type)) return deleteRule(id);
        if ("issues".equals(type)) return deleteIssue(id);
        return ApiResponse.error(400, "不支持的 type: " + type);
    }
}
