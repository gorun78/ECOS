package com.chinacreator.gzcm.buszhi.workflow.controller;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.buszhi.workflow.WorkflowApprovalService;

/**
 * 审批中心 REST API — approve/reject/transfer/addSign。
 *
 * <pre>
 * POST   /api/v1/ecos/approvals/{taskId}/approve   — 审批通过
 * POST   /api/v1/ecos/approvals/{taskId}/reject    — 审批驳回
 * POST   /api/v1/ecos/approvals/{taskId}/transfer  — 审批转签
 * POST   /api/v1/ecos/approvals/{taskId}/addSign   — 加签
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/ecos/approvals")
public class WorkflowApprovalController {

    private static final Logger log = LoggerFactory.getLogger(WorkflowApprovalController.class);
    private final WorkflowApprovalService approvalService;

    public WorkflowApprovalController(WorkflowApprovalService approvalService) {
        this.approvalService = approvalService;
    }

    @PostMapping("/{taskId}/approve")
    public ApiResponse<Map<String, Object>> approve(
            @PathVariable String taskId,
            @RequestBody Map<String, Object> body) {
        try {
            Map<String, Object> result = approvalService.approve(taskId, body);
            log.info("Approval approved: task={}", taskId);
            return ApiResponse.success(result);
        } catch (RuntimeException e) {
            log.warn("Approval approve failed: {}", e.getMessage());
            return ApiResponse.badRequest(e.getMessage());
        }
    }

    @PostMapping("/{taskId}/reject")
    public ApiResponse<Map<String, Object>> reject(
            @PathVariable String taskId,
            @RequestBody Map<String, Object> body) {
        try {
            Map<String, Object> result = approvalService.reject(taskId, body);
            log.info("Approval rejected: task={}", taskId);
            return ApiResponse.success(result);
        } catch (RuntimeException e) {
            log.warn("Approval reject failed: {}", e.getMessage());
            return ApiResponse.badRequest(e.getMessage());
        }
    }

    @PostMapping("/{taskId}/transfer")
    public ApiResponse<Map<String, Object>> transfer(
            @PathVariable String taskId,
            @RequestBody Map<String, Object> body) {
        try {
            Map<String, Object> result = approvalService.transfer(taskId, body);
            log.info("Approval transferred: task={}", taskId);
            return ApiResponse.success(result);
        } catch (RuntimeException e) {
            log.warn("Approval transfer failed: {}", e.getMessage());
            return ApiResponse.badRequest(e.getMessage());
        }
    }

    @PostMapping("/{taskId}/addSign")
    public ApiResponse<Map<String, Object>> addSign(
            @PathVariable String taskId,
            @RequestBody Map<String, Object> body) {
        try {
            Map<String, Object> result = approvalService.addSign(taskId, body);
            log.info("AddSign requested: task={}", taskId);
            return ApiResponse.success(result);
        } catch (RuntimeException e) {
            log.warn("AddSign failed: {}", e.getMessage());
            return ApiResponse.badRequest(e.getMessage());
        }
    }
}
