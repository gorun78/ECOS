package com.chinacreator.gzcm.buszhi.workflow.dto;

/** 审批转签回执（{@code POST /{taskId}/transfer}）：{@code taskId + decision + newAssignee}。 */
public record WorkflowTransferResultVO(String taskId, String decision, String newAssignee) {
}
