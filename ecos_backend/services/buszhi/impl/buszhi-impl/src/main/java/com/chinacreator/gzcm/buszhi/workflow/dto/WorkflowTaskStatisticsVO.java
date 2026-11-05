package com.chinacreator.gzcm.buszhi.workflow.dto;

/**
 * 任务统计（{@code GET /api/v1/ecos/tasks/statistics}）：
 * {@code total/pending/inProgress/completed} 四键（当前为占位统计，恒 0）。
 */
public record WorkflowTaskStatisticsVO(int total, int pending, int inProgress, int completed) {
}
