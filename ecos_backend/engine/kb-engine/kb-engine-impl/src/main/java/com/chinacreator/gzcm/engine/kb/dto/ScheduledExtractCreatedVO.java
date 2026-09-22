package com.chinacreator.gzcm.engine.kb.dto;

import lombok.Data;

/**
 * 定时抽取任务创建/更新/触发响应 VO（T5 + TB-1 共享出参）。
 *
 * <p>字段 {@code { id, scheduleId, nextRunAt }} —— 创建 / 更新；
 * {@code taskId} —— 触发端点回显（submitTask + executeTask 真实任务 ID）。</p>
 */
@Data
public class ScheduledExtractCreatedVO {

    /** 主键 id（fallback 镜像行 id，主流程 id 仅回退 UI 使用） */
    private Long id;

    /** runtime-task 调度 id == td_runtime_task_plan.task_id（事实源） */
    private String scheduleId;

    /** 下次运行时间 ISO 字符串（如 {@code 2026-09-21T08:00:00}） */
    private String nextRunAt;

    /** trigger 端点回显 taskId（submitTask + executeTask 返回的真实任务 ID，铁律 §1.6-1） */
    private String taskId;
}
