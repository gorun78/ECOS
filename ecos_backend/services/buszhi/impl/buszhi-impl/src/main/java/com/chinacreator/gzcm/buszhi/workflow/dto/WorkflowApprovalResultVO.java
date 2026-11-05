package com.chinacreator.gzcm.buszhi.workflow.dto;

/** 审批通过/驳回回执（{@code POST /{taskId}/approve|reject}）：{@code approvalId + decision}。 */
public record WorkflowApprovalResultVO(String approvalId, String decision) {
}
