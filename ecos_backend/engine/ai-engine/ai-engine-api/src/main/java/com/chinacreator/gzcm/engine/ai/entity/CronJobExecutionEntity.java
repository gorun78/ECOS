package com.chinacreator.gzcm.engine.ai.entity;

import java.time.LocalDateTime;

/**
 * 定时任务执行历史 — 对应 ecos_cron_job_execution 表
 */
public class CronJobExecutionEntity {

    private Long id;
    private Long cronJobId;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private String status;       // RUNNING / SUCCESS / FAILED
    private String result;
    private String errorMessage;
    private LocalDateTime createdAt;

    public CronJobExecutionEntity() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getCronJobId() { return cronJobId; }
    public void setCronJobId(Long cronJobId) { this.cronJobId = cronJobId; }

    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }

    public LocalDateTime getFinishedAt() { return finishedAt; }
    public void setFinishedAt(LocalDateTime finishedAt) { this.finishedAt = finishedAt; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
