package com.chinacreator.gzcm.buszhi.workflow;

import java.time.LocalDateTime;

/**
 * 工作流持久化实体
 */
public class WorkflowEntity {

    private String id;
    private String name;
    private String description;
    private String status;
    private String mode;
    private String nodes;
    private String edges;
    private LocalDateTime publishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public WorkflowEntity() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }

    public String getNodes() { return nodes; }
    public void setNodes(String nodes) { this.nodes = nodes; }

    public String getEdges() { return edges; }
    public void setEdges(String edges) { this.edges = edges; }

    public LocalDateTime getPublishedAt() { return publishedAt; }
    public void setPublishedAt(LocalDateTime publishedAt) { this.publishedAt = publishedAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
