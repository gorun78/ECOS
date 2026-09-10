package com.chinacreator.gzcm.engine.data.quality.controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.data.quality.service.DqService;

/**
 * Data Quality REST API — 数据质量规则/问题/仪表盘（委托 DqService 持久化）
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

    /**
     * PMO-48-A T4：DQ 旧写端点只读兼容下线。
     * 写操作统一 410 Gone，只读 GET 端点保持只读兼容（读旧表 ecos_dq_rule_v2 / ecos_quality_rule）。
     * 新端点在 /api/v1/dq/*（DqGovernanceController，T3 交付）。
     */
    private static final String GONE_MSG = "DQ 写操作已迁移到 /api/v1/dq/rules（Phase 1 只读兼容，写操作 Phase 2 在新端点开放）";

    @PostMapping("/rules")
    public ApiResponse<Map<String, Object>> createRule(@RequestBody Map<String, Object> body) {
        log.warn("DQ legacy write blocked: createRule");
        return ApiResponse.error(410, "GONE", GONE_MSG);
    }

    @PutMapping("/rules/{id}")
    public ApiResponse<Map<String, Object>> updateRule(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        log.warn("DQ legacy write blocked: updateRule id={}", id);
        return ApiResponse.error(410, "GONE", GONE_MSG);
    }

    @DeleteMapping("/rules/{id}")
    public ApiResponse<Map<String, Object>> deleteRule(@PathVariable Long id) {
        log.warn("DQ legacy write blocked: deleteRule id={}", id);
        return ApiResponse.error(410, "GONE", GONE_MSG);
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
        log.warn("DQ legacy write blocked: createIssue");
        return ApiResponse.error(410, "GONE", GONE_MSG);
    }

    @PutMapping("/issues/{id}")
    public ApiResponse<Map<String, Object>> updateIssue(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        log.warn("DQ legacy write blocked: updateIssue id={}", id);
        return ApiResponse.error(410, "GONE", GONE_MSG);
    }

    @PostMapping("/issues/{id}/resolve")
    public ApiResponse<Map<String, Object>> resolveIssue(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        log.warn("DQ legacy write blocked: resolveIssue id={}", id);
        return ApiResponse.error(410, "GONE", GONE_MSG);
    }

    @DeleteMapping("/issues/{id}")
    public ApiResponse<Map<String, Object>> deleteIssue(@PathVariable Long id) {
        log.warn("DQ legacy write blocked: deleteIssue id={}", id);
        return ApiResponse.error(410, "GONE", GONE_MSG);
    }

    // ═══════════════ 仪表盘 ═══════════════════

    @GetMapping("/dashboard")
    public ApiResponse<Map<String, Object>> dashboard() {
        return ApiResponse.success(dqService.dashboard());
    }

    // ═══════════════ 检查 ═══════════════════

    @PostMapping("/check")
    public ApiResponse<Map<String, Object>> runCheck() {
        log.warn("DQ legacy write blocked: runCheck");
        return ApiResponse.error(410, "GONE", GONE_MSG);
    }

    // ═══════════════ 兼容旧版统一路径（写操作已下线 410） ═══════════════════

    /**
     * 兼容旧版 POST /{type} 路径 — 已随写操作统一 410 Gone（PMO-48-A T4）。
     */
    @PostMapping("/{type}")
    public ApiResponse<Map<String, Object>> createLegacy(@PathVariable String type, @RequestBody Map<String, Object> body) {
        log.warn("DQ legacy write blocked: createLegacy type={}", type);
        return ApiResponse.error(410, "GONE", GONE_MSG);
    }

    /**
     * 兼容旧版 DELETE /{type}/{id} 路径 — 已随写操作统一 410 Gone（PMO-48-A T4）。
     */
    @DeleteMapping("/{type}/{id}")
    public ApiResponse<Map<String, Object>> deleteLegacy(@PathVariable String type, @PathVariable Long id) {
        log.warn("DQ legacy write blocked: deleteLegacy type={} id={}", type, id);
        return ApiResponse.error(410, "GONE", GONE_MSG);
    }
}
