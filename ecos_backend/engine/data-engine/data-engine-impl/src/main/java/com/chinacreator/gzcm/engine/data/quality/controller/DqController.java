package com.chinacreator.gzcm.engine.data.quality.controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.data.DataDomainErrorCode;
import com.chinacreator.gzcm.engine.data.quality.service.DqService;

/**
 * Data Quality REST API — 数据质量规则/问题/仪表盘（委托 DqService 持久化）
 *
 * <p><b>F02-08 规格 5（2026-09-28）</b>：本控制器为 <b>legacy 只读兼容端点</b>
 * （读 {@code ecos_dq_rule_v2} / {@code ecos_quality_rule}）。只读 GET 端点保留 ≥2 迭代，
 * 写端点（POST/PUT/DELETE 创建/更新/删除 rules/issues/check）统一停用，
 * 返回 <b>409 + errorCode=ECOS-DATA-011</b>，提示改用 <b>{@code /api/v1/dq/**}</b>
 * 治理端点（{@code DqGovernanceController}）。</p>
 *
 * <pre>
 * GET    /api/v1/ecos/dq/rules              — 规则列表（deprecated）
 * GET    /api/v1/ecos/dq/issues             — 问题列表（deprecated）
 * GET    /api/v1/ecos/dq/dashboard          — 仪表盘汇总（deprecated）
 * POST   /api/v1/ecos/dq/rules              — 已停用（409 ECOS-DATA-011）
 * PUT    /api/v1/ecos/dq/rules/{id}         — 已停用（409 ECOS-DATA-011）
 * DELETE /api/v1/ecos/dq/rules/{id}         — 已停用（409 ECOS-DATA-011）
 * POST   /api/v1/ecos/dq/issues             — 已停用（409 ECOS-DATA-011）
 * PUT    /api/v1/ecos/dq/issues/{id}        — 已停用（409 ECOS-DATA-011）
 * DELETE /api/v1/ecos/dq/issues/{id}        — 已停用（409 ECOS-DATA-011）
 * POST   /api/v1/ecos/dq/check              — 已停用（409 ECOS-DATA-011）
 * POST   /api/v1/ecos/dq/issues/{id}/resolve — 已停用（409 ECOS-DATA-011）
 * </pre>
 *
 * @deprecated F02-08 DQ 治理单模型收敛：写端点停用，读端点保持只读兼容 ≥2 迭代；
 *             治理写操作请使用 {@code /api/v1/dq/rules|work-orders|scores/**}
 */
@Deprecated
@RestController
@RequestMapping("/api/v1/ecos/dq")
public class DqController {

    private static final Logger log = LoggerFactory.getLogger(DqController.class);

    private final DqService dqService;

    public DqController(DqService dqService) {
        this.dqService = dqService;
    }

    // ═══════════════ 规则 CRUD ═══════════════════

    /** @deprecated F02-08 规格 5：legacy 只读兼容 ≥2 迭代，读新治理端点 {@code /api/v1/dq/rules} 已就绪。 */
    @Deprecated
    @GetMapping("/rules")
    public ApiResponse<Map<String, Object>> listRules() {
        List<Map<String, Object>> list = dqService.listRules();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("data", list);
        result.put("total", dqService.totalRules());
        return ApiResponse.success(result);
    }

    /** @deprecated F02-08 规格 5：legacy 只读兼容 ≥2 迭代，读新治理端点 {@code /api/v1/dq/rules/{id}} 已就绪。 */
    @Deprecated
    @GetMapping("/rules/{id}")
    public ApiResponse<Map<String, Object>> getRule(@PathVariable Long id) {
        return dqService.getRule(id)
            .map(ApiResponse::success)
            .orElseGet(() -> ApiResponse.notFound("规则 " + id + " 不存在"));
    }

    /**
     * F02-08 规格 5：DQ legacy 写端点已停用。
     * <p>写操作统一 <b>409 + errorCode=ECOS-DATA-011</b>，提示改用
     * {@code /api/v1/dq/**} 治理端点（{@code DqGovernanceController}）。
     * 只读 GET 端点保持只读兼容（读旧表 ecos_dq_rule_v2 / ecos_quality_rule）。</p>
     */
    private static final String DEPRECATED_MSG = "该端点已停用，请使用 /api/v1/dq/** 治理端点";

    private static ApiResponse<Map<String, Object>> legacyWriteBlocked(String hint) {
        log.warn("DQ legacy write blocked: {}", hint);
        return ApiResponse.error(409, DataDomainErrorCode.ECOS_DATA_011, DEPRECATED_MSG);
    }

    /** @deprecated F02-08 规格 5：写端点已停用，使用 {@code POST /api/v1/dq/rules} 创建规则。 */
    @Deprecated
    @PostMapping("/rules")
    public ApiResponse<Map<String, Object>> createRule(@RequestBody Map<String, Object> body) {
        return legacyWriteBlocked("createRule");
    }

    /** @deprecated F02-08 规格 5：写端点已停用，使用 {@code PUT /api/v1/dq/rules/{id}} 更新规则。 */
    @Deprecated
    @PutMapping("/rules/{id}")
    public ApiResponse<Map<String, Object>> updateRule(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return legacyWriteBlocked("updateRule id=" + id);
    }

    /** @deprecated F02-08 规格 5：写端点已停用，使用 {@code DELETE /api/v1/dq/rules/{id}} 删除规则。 */
    @Deprecated
    @DeleteMapping("/rules/{id}")
    public ApiResponse<Map<String, Object>> deleteRule(@PathVariable Long id) {
        return legacyWriteBlocked("deleteRule id=" + id);
    }

    // ═══════════════ 问题 CRUD ═══════════════════

    /** @deprecated F02-08 规格 5：legacy 只读兼容 ≥2 迭代，读新治理端点 {@code /api/v1/dq/work-orders|alerts}。 */
    @Deprecated
    @GetMapping("/issues")
    public ApiResponse<Map<String, Object>> listIssues() {
        List<Map<String, Object>> list = dqService.listIssues();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("data", list);
        result.put("total", dqService.totalIssues());
        return ApiResponse.success(result);
    }

    /** @deprecated F02-08 规格 5：legacy 只读兼容 ≥2 迭代，读新治理端点 {@code /api/v1/dq/work-orders/{id}}。 */
    @Deprecated
    @GetMapping("/issues/{id}")
    public ApiResponse<Map<String, Object>> getIssue(@PathVariable Long id) {
        return dqService.getIssue(id)
            .map(ApiResponse::success)
            .orElseGet(() -> ApiResponse.notFound("问题 " + id + " 不存在"));
    }

    /** @deprecated F02-08 规格 5：写端点已停用，DQ 工单自动化走告警→工单联动 {@code /api/v1/dq/work-orders}。 */
    @Deprecated
    @PostMapping("/issues")
    public ApiResponse<Map<String, Object>> createIssue(@RequestBody Map<String, Object> body) {
        return legacyWriteBlocked("createIssue");
    }

    /** @deprecated F02-08 规格 5：写端点已停用。 */
    @Deprecated
    @PutMapping("/issues/{id}")
    public ApiResponse<Map<String, Object>> updateIssue(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return legacyWriteBlocked("updateIssue id=" + id);
    }

    /** @deprecated F02-08 规格 5：写端点已停用。 */
    @Deprecated
    @PostMapping("/issues/{id}/resolve")
    public ApiResponse<Map<String, Object>> resolveIssue(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return legacyWriteBlocked("resolveIssue id=" + id);
    }

    /** @deprecated F02-08 规格 5：写端点已停用。 */
    @Deprecated
    @DeleteMapping("/issues/{id}")
    public ApiResponse<Map<String, Object>> deleteIssue(@PathVariable Long id) {
        return legacyWriteBlocked("deleteIssue id=" + id);
    }

    // ═══════════════ 仪表盘 ═══════════════════

    /** @deprecated F02-08 规格 5：legacy 只读兼容 ≥2 迭代。 */
    @Deprecated
    @GetMapping("/dashboard")
    public ApiResponse<Map<String, Object>> dashboard() {
        return ApiResponse.success(dqService.dashboard());
    }

    // ═══════════════ 检查 ═══════════════════

    /** @deprecated F02-08 规格 5：写端点已停用，DQ 检查执行走治理端点 {@code /api/v1/dq/rules|work-orders|scores/recompute}。 */
    @Deprecated
    @PostMapping("/check")
    public ApiResponse<Map<String, Object>> runCheck() {
        return legacyWriteBlocked("runCheck");
    }

    // ═══════════════ 兼容旧版统一路径（写操作已停用 409 ECOS-DATA-011） ═══════════════════

    /**
     * 兼容旧版 POST /{type} 路径 — F02-08 已停用（409 + ECOS-DATA-011）。
     *
     * @deprecated F02-08 规格 5
     */
    @Deprecated
    @PostMapping("/{type}")
    public ApiResponse<Map<String, Object>> createLegacy(@PathVariable String type, @RequestBody Map<String, Object> body) {
        return legacyWriteBlocked("createLegacy type=" + type);
    }

    /**
     * 兼容旧版 DELETE /{type}/{id} 路径 — F02-08 已停用（409 + ECOS-DATA-011）。
     *
     * @deprecated F02-08 规格 5
     */
    @Deprecated
    @DeleteMapping("/{type}/{id}")
    public ApiResponse<Map<String, Object>> deleteLegacy(@PathVariable String type, @PathVariable Long id) {
        return legacyWriteBlocked("deleteLegacy type=" + type + " id=" + id);
    }
}
