package com.chinacreator.gzcm.buszhi.workflow.dto;

/** 加签回执（{@code POST /{taskId}/add-sign}）：{@code taskId + status}（恒 AddSignRequested）。 */
public record WorkflowAddSignResultVO(String taskId, String status) {
}
