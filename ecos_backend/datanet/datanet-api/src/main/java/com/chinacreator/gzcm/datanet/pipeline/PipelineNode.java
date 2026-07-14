package com.chinacreator.gzcm.datanet.pipeline;

import java.time.LocalDateTime;

/**
 * Pipeline 节点实体 — DAG 中的执行单元。
 *
 * @author DataBridge Datanet Team
 */
public class PipelineNode {

    /** 节点主键 */
    private String id;

    /** 所属 Pipeline 定义 ID */
    private String definitionId;

    /** 前端节点标识 */
    private String nodeId;

    /** 节点类型: SOURCE_JDBC / TRANSFORM_SQL / OUTPUT_OBJECT */
    private String type;

    /** 节点配置 (JSON): SQL语句、JDBC连接信息等 */
    private String config;

    /** 画布 X 坐标 */
    /** 依赖的节点ID列表 (JSON数组) */    private String dependsOn;
    private Integer positionX;

    /** 画布 Y 坐标 */
    private Integer positionY;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;

    // ===== Getters/Setters =====

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getDefinitionId() { return definitionId; }
    public void setDefinitionId(String definitionId) { this.definitionId = definitionId; }

    public String getNodeId() { return nodeId; }
    public void setNodeId(String nodeId) { this.nodeId = nodeId; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getConfig() { return config; }
    public void setConfig(String config) { this.config = config; }

    public String getDependsOn() { return dependsOn; }    public void setDependsOn(String dependsOn) { this.dependsOn = dependsOn; }

    public Integer getPositionX() { return positionX; }
    public void setPositionX(Integer positionX) { this.positionX = positionX; }

    public Integer getPositionY() { return positionY; }
    public void setPositionY(Integer positionY) { this.positionY = positionY; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
