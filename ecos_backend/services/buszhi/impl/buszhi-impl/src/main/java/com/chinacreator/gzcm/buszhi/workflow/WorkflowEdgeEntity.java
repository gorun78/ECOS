package com.chinacreator.gzcm.buszhi.workflow;

import java.time.LocalDateTime;

/**
 * 工作流边投影只读 DTO — 对应 {@code {schema}.ecos_workflow_edge}（V173 §2）。
 *
 * <p>列形态随 V173 DDL：{@code edge_key}(JSON 内边 id)、{@code source_node_key}、
 * {@code target_node_key}、{@code sort_order}、{@code create_time}。只读投影（F03-12 M1）。
 */
public class WorkflowEdgeEntity {

    private String id;
    private String workflowId;
    private String edgeKey;
    private String sourceNodeKey;
    private String targetNodeKey;
    private Integer sortOrder;
    private LocalDateTime createTime;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getWorkflowId() { return workflowId; }
    public void setWorkflowId(String workflowId) { this.workflowId = workflowId; }

    public String getEdgeKey() { return edgeKey; }
    public void setEdgeKey(String edgeKey) { this.edgeKey = edgeKey; }

    public String getSourceNodeKey() { return sourceNodeKey; }
    public void setSourceNodeKey(String sourceNodeKey) { this.sourceNodeKey = sourceNodeKey; }

    public String getTargetNodeKey() { return targetNodeKey; }
    public void setTargetNodeKey(String targetNodeKey) { this.targetNodeKey = targetNodeKey; }

    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}
