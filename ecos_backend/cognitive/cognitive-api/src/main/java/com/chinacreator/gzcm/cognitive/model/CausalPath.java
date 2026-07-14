package com.chinacreator.gzcm.cognitive.model;

import java.io.Serializable;
import java.util.List;

/**
 * 因果链路径。
 */
public class CausalPath implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 路径 ID */
    private String pathId;
    /** 因果节点序列（从因到果） */
    private List<CausalNode> nodes;
    /** 路径总体置信度 */
    private Double totalConfidence;
    /** 路径长度 */
    private Integer pathLength;

    public String getPathId() { return pathId; }
    public void setPathId(String pathId) { this.pathId = pathId; }
    public List<CausalNode> getNodes() { return nodes; }
    public void setNodes(List<CausalNode> nodes) { this.nodes = nodes; }
    public Double getTotalConfidence() { return totalConfidence; }
    public void setTotalConfidence(Double totalConfidence) { this.totalConfidence = totalConfidence; }
    public Integer getPathLength() { return pathLength; }
    public void setPathLength(Integer pathLength) { this.pathLength = pathLength; }
}
