package com.chinacreator.gzcm.cognitive.model;

import java.io.Serializable;

/**
 * 根因节点。
 */
public class RootCause implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 节点 ID */
    private String nodeId;
    /** 节点标签 */
    private String nodeLabel;
    /** 所属层次 */
    private String layer;
    /** 置信度 0~1 */
    private Double confidence;
    /** 影响力评分 0~1 */
    private Double impactScore;

    public String getNodeId() { return nodeId; }
    public void setNodeId(String nodeId) { this.nodeId = nodeId; }
    public String getNodeLabel() { return nodeLabel; }
    public void setNodeLabel(String nodeLabel) { this.nodeLabel = nodeLabel; }
    public String getLayer() { return layer; }
    public void setLayer(String layer) { this.layer = layer; }
    public Double getConfidence() { return confidence; }
    public void setConfidence(Double confidence) { this.confidence = confidence; }
    public Double getImpactScore() { return impactScore; }
    public void setImpactScore(Double impactScore) { this.impactScore = impactScore; }
}
