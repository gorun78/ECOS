package com.chinacreator.gzcm.engine.data.pipeline;

import lombok.Data;

/**
 * Pipeline 执行任务状态视图对象 — runtime-task 任务状态快照。
 * <p>遵循后端规范 XxxVO 命名（Lombok @Data），用于 /execute 与 /tasks/{taskId}/status 响应。
 *
 * @author DataBridge Datanet Team
 */
@Data
public class PipelineTaskStatusVO {

    /** runtime-task 任务 ID（前端轮询键） */
    private String taskId;

    /** 任务状态（runtime-task TaskStatus 枚举 name） */
    private String status;

    /** 状态说明 */
    private String statusMessage;

    /** 进度（0-100） */
    private Integer progress;

    /** 当前步骤 ID */
    private String currentStepId;

    /** 开始时间戳（毫秒） */
    private Long startTime;

    /** 结束时间戳（毫秒） */
    private Long endTime;

    /** 执行结果载荷（类型取决于任务） */
    private Object result;

    /** 错误信息（失败时） */
    private String errorMessage;
}
