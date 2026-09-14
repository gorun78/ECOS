package com.chinacreator.gzcm.engine.data.quality.controller;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.data.quality.DqGovernanceService;
import com.chinacreator.gzcm.engine.data.quality.DqRuleLifecycleService;
import com.chinacreator.gzcm.engine.data.quality.model.DqDimensionRegistryVO;
import com.chinacreator.gzcm.engine.data.quality.model.DqRuleActionDTO;
import com.chinacreator.gzcm.engine.data.quality.model.DqRuleDTO;
import com.chinacreator.gzcm.engine.data.quality.model.DqRuleDetailVO;
import com.chinacreator.gzcm.engine.data.quality.model.DqRuleQuery;
import com.chinacreator.gzcm.engine.data.quality.model.DqRuleVO;
import com.chinacreator.gzcm.engine.data.quality.model.DqRuleVersionVO;
import com.chinacreator.gzcm.engine.data.quality.model.LogicDeleteResult;
import com.chinacreator.gzcm.engine.data.quality.model.PageResult;

/**
 * DQ 治理 REST API — 数据质量规则全生命周期（PMO-48-B T7c）。
 *
 * <pre>
 * GET    /api/v1/dq/rules                          — 规则列表（category/status/domain 过滤 + 分页）
 * GET    /api/v1/dq/rules/{id}                      — 详情 + 版本历史
 * GET    /api/v1/dq/rules/dimension-registry        — 6 维 rule_type 映射注册表
 * POST   /api/v1/dq/rules                           — 创建草稿规则（status=DRAFT）
 * PUT    /api/v1/dq/rules/{id}                      — 编辑草稿规则（仅 DRAFT 可编辑）
 * DELETE /api/v1/dq/rules/{id}                      — 逻辑删除规则
 * POST   /api/v1/dq/rules/{id}/submit               — 提交审核（DRAFT/REJECTED → IN_REVIEW）
 * POST   /api/v1/dq/rules/{id}/approve              — 审批通过（IN_REVIEW → ACTIVE + 版本快照）
 * POST   /api/v1/dq/rules/{id}/reject               — 驳回（IN_REVIEW → REJECTED）
 * POST   /api/v1/dq/rules/{id}/deprecate            — 废止（→ DEPRECATED）
 * POST   /api/v1/dq/rules/{id}/supersede            — 被替代（→ SUPERSEDED）
 * POST   /api/v1/dq/rules/{id}/disable              — 临时停用（ACTIVE → DISABLED，不写版本）
 * GET    /api/v1/dq/rules/{id}/versions             — 版本历史列表
 * GET    /api/v1/dq/rules/{id}/versions/{ver}       — 指定版本快照
 * </pre>
 * <p>
 * 安全卡（铁律 2.4）：响应前 {@code DqSecurityService.mask*} 脱敏敏感字段；
 * 所有读/写操作异步 audit（POST /api/security/audit/log）。
 * </p>
 *
 * @author PMO-48-A T3 / PMO-48-B T7c
 */
@RestController
@RequestMapping("/api/v1/dq")
public class DqGovernanceController {

    private final DqGovernanceService dqGovernanceService;
    private final DqRuleLifecycleService lifecycleService;

    public DqGovernanceController(DqGovernanceService dqGovernanceService,
                                  DqRuleLifecycleService lifecycleService) {
        this.dqGovernanceService = dqGovernanceService;
        this.lifecycleService = lifecycleService;
    }

    // ==================== GET 只读端点（Phase 1 保留） ====================

    /**
     * 规则列表（过滤 + 分页）。
     */
    @GetMapping("/rules")
    public ApiResponse<PageResult<DqRuleVO>> listRules(DqRuleQuery q) {
        PageResult<DqRuleVO> page = dqGovernanceService.listRules(q);
        return ApiResponse.success("查询成功", page);
    }

    /**
     * 规则详情 + 版本历史。
     */
    @GetMapping("/rules/{id}")
    public ApiResponse<DqRuleDetailVO> getRule(@PathVariable String id) {
        DqRuleDetailVO detail = dqGovernanceService.getRuleDetail(id);
        return ApiResponse.success("查询成功", detail);
    }

    /**
     * 6 维 rule_type 映射注册表。
     */
    @GetMapping("/rules/dimension-registry")
    public ApiResponse<List<DqDimensionRegistryVO>> dimensionRegistry() {
        return ApiResponse.success("查询成功", dqGovernanceService.getDimensionRegistry());
    }

    // ==================== CRUD 写端点（Phase 2 T7c 替换 405 桩） ====================

    /**
     * 创建草稿规则（status=DRAFT, version=1）。
     *
     * @param dto 规则入参
     * @return 新规则 ID
     */
    @PostMapping("/rules")
    public ApiResponse<String> createRule(@RequestBody DqRuleDTO dto) {
        String ruleId = lifecycleService.createDraft(dto);
        return ApiResponse.success("规则创建成功", ruleId);
    }

    /**
     * 编辑草稿规则（仅 DRAFT 状态可编辑）。
     *
     * @param id  规则 ID
     * @param dto 待更新字段（非 null 覆盖）
     */
    @PutMapping("/rules/{id}")
    public ApiResponse<Void> updateRule(@PathVariable String id, @RequestBody DqRuleDTO dto) {
        lifecycleService.updateDraft(id, dto);
        return ApiResponse.success();
    }

    /**
     * 逻辑删除规则（is_deleted=TRUE）。
     *
     * @param id 规则 ID
     * @return 删除结果（含 beforeDeleteAt）
     */
    @DeleteMapping("/rules/{id}")
    public ApiResponse<LogicDeleteResult> deleteRule(@PathVariable String id) {
        LogicDeleteResult result = lifecycleService.deleteRule(id);
        return ApiResponse.success("规则删除成功", result);
    }

    // ==================== 状态转换端点（Phase 2 T7c 新增） ====================

    /**
     * 提交审核：DRAFT/REJECTED → IN_REVIEW。
     *
     * @param id   规则 ID
     * @param body 请求体（submitter 必填）
     * @return 新状态
     */
    @PostMapping("/rules/{id}/submit")
    public ApiResponse<String> submitRule(@PathVariable String id,
                                          @RequestBody(required = false) DqRuleActionDTO body) {
        String submitter = body != null && body.getSubmitter() != null ? body.getSubmitter() : "system";
        String newStatus = lifecycleService.submit(id, submitter);
        return ApiResponse.success("提交成功，状态:", newStatus);
    }

    /**
     * 审批通过：IN_REVIEW → ACTIVE，写版本快照。
     *
     * @param id   规则 ID
     * @param body 请求体（approver 必填）
     * @return 新状态
     */
    @PostMapping("/rules/{id}/approve")
    public ApiResponse<String> approveRule(@PathVariable String id,
                                           @RequestBody(required = false) DqRuleActionDTO body) {
        String approver = body != null && body.getApprover() != null ? body.getApprover() : "system";
        String newStatus = lifecycleService.approve(id, approver);
        return ApiResponse.success("审批通过，状态:", newStatus);
    }

    /**
     * 驳回：IN_REVIEW → REJECTED。
     *
     * @param id   规则 ID
     * @param body 请求体（rejector 必填，reason 可选）
     * @return 新状态
     */
    @PostMapping("/rules/{id}/reject")
    public ApiResponse<String> rejectRule(@PathVariable String id,
                                          @RequestBody(required = false) DqRuleActionDTO body) {
        String rejector = body != null && body.getRejector() != null ? body.getRejector() : "system";
        String reason = body != null ? body.getReason() : null;
        String newStatus = lifecycleService.reject(id, rejector, reason);
        return ApiResponse.success("已驳回，状态:", newStatus);
    }

    /**
     * 废止：DRAFT/ACTIVE/REJECTED → DEPRECATED。
     *
     * @param id   规则 ID
     * @param body 请求体（operator 必填）
     * @return 新状态
     */
    @PostMapping("/rules/{id}/deprecate")
    public ApiResponse<String> deprecateRule(@PathVariable String id,
                                             @RequestBody(required = false) DqRuleActionDTO body) {
        String operator = body != null && body.getOperator() != null ? body.getOperator() : "system";
        String newStatus = lifecycleService.deprecate(id, operator);
        return ApiResponse.success("已废止，状态:", newStatus);
    }

    /**
     * 被替代：DRAFT/ACTIVE/DEPRECATED → SUPERSEDED。
     *
     * @param id   规则 ID
     * @param body 请求体（operator 必填，reason 可选）
     * @return 新状态
     */
    @PostMapping("/rules/{id}/supersede")
    public ApiResponse<String> supersedeRule(@PathVariable String id,
                                             @RequestBody(required = false) DqRuleActionDTO body) {
        String operator = body != null && body.getOperator() != null ? body.getOperator() : "system";
        String reason = body != null ? body.getReason() : null;
        String newStatus = lifecycleService.supersede(id, operator, reason);
        return ApiResponse.success("已被替代，状态:", newStatus);
    }

    /**
     * 临时停用：ACTIVE → DISABLED（不写版本快照）。
     *
     * @param id   规则 ID
     * @param body 请求体（operator 必填）
     * @return 新状态
     */
    @PostMapping("/rules/{id}/disable")
    public ApiResponse<String> disableRule(@PathVariable String id,
                                           @RequestBody(required = false) DqRuleActionDTO body) {
        String operator = body != null && body.getOperator() != null ? body.getOperator() : "system";
        String newStatus = lifecycleService.disable(id, operator);
        return ApiResponse.success("已停用，状态:", newStatus);
    }

    // ==================== 版本查询端点 ====================

    /**
     * 查询规则版本历史（按 versionNumber 降序）。
     *
     * @param id 规则 ID
     * @return 版本列表
     */
    @GetMapping("/rules/{id}/versions")
    public ApiResponse<List<DqRuleVersionVO>> listVersions(@PathVariable String id) {
        List<DqRuleVersionVO> versions = lifecycleService.listVersions(id);
        return ApiResponse.success("查询成功", versions);
    }

    /**
     * 查询指定版本快照。
     *
     * @param id     规则 ID
     * @param ver    版本号
     */
    @GetMapping("/rules/{id}/versions/{ver}")
    public ApiResponse<DqRuleVersionVO> getVersion(@PathVariable String id,
                                                   @PathVariable int ver) {
        DqRuleVersionVO version = lifecycleService.getVersion(id, ver);
        if (version == null) {
            return ApiResponse.notFound("版本 " + ver + " 不存在");
        }
        return ApiResponse.success("查询成功", version);
    }
}
