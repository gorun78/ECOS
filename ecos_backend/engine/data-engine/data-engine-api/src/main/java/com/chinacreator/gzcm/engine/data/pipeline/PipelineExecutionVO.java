package com.chinacreator.gzcm.engine.data.pipeline;

import lombok.Data;

/**
 * Pipeline 执行记录视图对象 — 对应 {@link PipelineExecution} 实体。
 * <p>遵循后端规范 XxxVO 命名（Lombok @Data），用于 /executions 系列端点响应。
 *
 * @author DataBridge Datanet Team
 */
@Data
public class PipelineExecutionVO {

    /** 执行记录主键 */
    private String id;

    /** 所属 Pipeline 定义 ID */
    private String definitionId;

    /** 状态: PENDING / RUNNING / COMPLETED / FAILED */
    private String status;

    /** 开始时间戳（毫秒） */
    private Long startedAt;

    /** 完成时间戳（毫秒） */
    private Long completedAt;

    /** 错误信息（失败时） */
    private String errorMessage;

    /** 处理行数 */
    private Long rowsProcessed;
}
