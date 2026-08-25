package com.chinacreator.gzcm.engine.data.pipeline;

import java.time.LocalDateTime;

/**
 * Pipeline 定义实体 — 描述一个数据管线的拓扑结构。
 *
 * @author DataBridge Datanet Team
 */
public class PipelineDefinition {

    /** 定义唯一标识 */
    private String id;

    /** Pipeline 名称 */
    private String name;

    /** 描述 */
    private String description;

    /** 状态: DRAFT / ACTIVE / ARCHIVED */
    private String status;

    /** 定时调度 cron 表达式（来自 P2-01 schedule.cron，为空表示不调度） */
    private String scheduleCron;

    /** 扩展属性（含 runtime-task 调度 ID 等） */
    private java.util.Map<String, Object> extensions;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;

    // ===== Getters/Setters =====

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getScheduleCron() { return scheduleCron; }
    public void setScheduleCron(String scheduleCron) { this.scheduleCron = scheduleCron; }

    public java.util.Map<String, Object> getExtensions() { return extensions; }
    public void setExtensions(java.util.Map<String, Object> extensions) { this.extensions = extensions; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
