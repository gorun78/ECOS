package com.chinacreator.gzcm.buszhi.workflow.dto;

/**
 * 工作流任务行（我的任务/详情/签收/完成/转签/驳回共用）。
 *
 * <p>键面与顺序：{@code id/instanceId/nodeId/taskType/title/assignee/status/priority/
 * dueDate/completedAt/createdAt}；时间列以 ISO 字符串出（无值为 {@code null}，键恒出现）。</p>
 */
public record WorkflowTaskVO(
        String id,
        String instanceId,
        String nodeId,
        String taskType,
        String title,
        String assignee,
        String status,
        String priority,
        String dueDate,
        String completedAt,
        String createdAt) {
}
