package com.chinacreator.gzcm.cognitive.model;

import java.io.Serializable;

/**
 * 因果节点。
 */
public class CausalNode implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 节点 ID */
    private String nodeId;
    /** 节点标签 */
    private String label;
    /** 所属层次 */
    private String layer;

    public String getNodeId() { return nodeId; }
    public void setNodeId(String nodeId) { this.nodeId = nodeId; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public String getLayer() { return layer; }
    public void setLayer(String layer) { this.layer = layer; }
}
