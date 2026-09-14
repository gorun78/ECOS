package com.chinacreator.gzcm.buszhi.workflow;

import java.time.LocalDateTime;

/**
 * 工作流日志实体 — 对应 ecos_workflow_log 表。
 */
public class WorkflowLogEntity {

    private String id;
    private String instanceId;
    private String nodeId;
    private String nodeType;
    private String eventType;            // NodeStarted/NodeCompleted/TaskCreated/TaskAssigned/AgentInvoked/AgentCompleted
    private String message;
    private String details;              // JSONB
    private Long durationMs;
    private String traceId;
    private LocalDateTime createdAt;

    public WorkflowLogEntity() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getInstanceId() { return instanceId; }
    public void setInstanceId(String instanceId) { this.instanceId = instanceId; }

    public String getNodeId() { return nodeId; }
    public void setNodeId(String nodeId) { this.nodeId = nodeId; }

    public String getNodeType() { return nodeType; }
    public void setNodeType(String nodeType) { this.nodeType = nodeType; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }

    public Long getDurationMs() { return durationMs; }
    public void setDurationMs(Long durationMs) { this.durationMs = durationMs; }

    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
