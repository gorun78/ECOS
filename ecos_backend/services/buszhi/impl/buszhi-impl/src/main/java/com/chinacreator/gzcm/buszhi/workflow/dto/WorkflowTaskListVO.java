package com.chinacreator.gzcm.buszhi.workflow.dto;

import java.util.List;

/** 我的任务列表（{@code GET /api/v1/ecos/tasks}）：{@code data + total}。 */
public record WorkflowTaskListVO(List<WorkflowTaskVO> data, int total) {
}
