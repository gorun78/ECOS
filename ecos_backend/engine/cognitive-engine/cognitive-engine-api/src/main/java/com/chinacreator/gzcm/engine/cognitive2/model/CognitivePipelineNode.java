package com.chinacreator.gzcm.engine.cognitive2.model;

import java.util.List;

public class CognitivePipelineNode {
    private String nodeId;
    private NodeType nodeType;
    private String config;
    private List<String> dependsOn;

    public CognitivePipelineNode() {
    }

    public CognitivePipelineNode(String nodeId, NodeType nodeType, String config, List<String> dependsOn) {
        this.nodeId = nodeId;
        this.nodeType = nodeType;
        this.config = config;
        this.dependsOn = dependsOn;
    }

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public NodeType getNodeType() {
        return nodeType;
    }

    public void setNodeType(NodeType nodeType) {
        this.nodeType = nodeType;
    }

    public String getConfig() {
        return config;
    }

    public void setConfig(String config) {
        this.config = config;
    }

    public List<String> getDependsOn() {
        return dependsOn;
    }

    public void setDependsOn(List<String> dependsOn) {
        this.dependsOn = dependsOn;
    }
}
