package com.chinacreator.gzcm.datanet.pipeline;

import java.time.LocalDateTime;

/**
 * Pipeline 执行记录实体 — 追踪每次 Pipeline 的运行状态。
 *
 * @author DataBridge Datanet Team
 */
public class PipelineExecution {

    /** 执行记录主键 */
    private String id;

    /** 所属 Pipeline 定义 ID */
    private String definitionId;

    /** 状态: PENDING / RUNNING / COMPLETED / FAILED */
    private String status;

    /** 开始时间 */
    private LocalDateTime startedAt;

    /** 完成时间 */
    private LocalDateTime completedAt;

    /** 错误信息 */
    private String errorMessage;

    /** 处理行数 */
    private Long rowsProcessed;

    // ===== Getters/Setters =====

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getDefinitionId() { return definitionId; }
    public void setDefinitionId(String definitionId) { this.definitionId = definitionId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }

    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public Long getRowsProcessed() { return rowsProcessed; }
    public void setRowsProcessed(Long rowsProcessed) { this.rowsProcessed = rowsProcessed; }
}
