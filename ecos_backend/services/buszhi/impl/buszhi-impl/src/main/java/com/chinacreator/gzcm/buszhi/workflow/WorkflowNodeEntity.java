package com.chinacreator.gzcm.buszhi.workflow;

import java.time.LocalDateTime;

/**
 * 工作流节点投影只读 DTO — 对应 {@code {schema}.ecos_workflow_node}（V173 §2）。
 *
 * <p>列形态随 V173 DDL：{@code node_key}(JSON 内节点 id)、{@code name}、{@code node_type}、
 * {@code sort_order}、{@code create_time}。检索走投影表，主表 {@code nodes_json} 不参与 WHERE（DR04 MC02）。
 * 本 DTO 仅供只读投影查询（F03-12 M1）；写路径拆解延至 M2。
 */
public class WorkflowNodeEntity {

    private String id;
    private String workflowId;
    private String nodeKey;
    private String name;
    private String nodeType;
    private Integer sortOrder;
    private LocalDateTime createTime;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getWorkflowId() { return workflowId; }
    public void setWorkflowId(String workflowId) { this.workflowId = workflowId; }

    public String getNodeKey() { return nodeKey; }
    public void setNodeKey(String nodeKey) { this.nodeKey = nodeKey; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getNodeType() { return nodeType; }
    public void setNodeType(String nodeType) { this.nodeType = nodeType; }

    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}
