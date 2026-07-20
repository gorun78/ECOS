package com.chinacreator.gzcm.engine.cognitive2.model;

public class CausalEdge {
    private String id;
    private String sourceNode;
    private String targetNode;
    private double weight;
    private String description;
    public CausalEdge() {}
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getSourceNode() { return sourceNode; }
    public void setSourceNode(String sourceNode) { this.sourceNode = sourceNode; }
    public String getTargetNode() { return targetNode; }
    public void setTargetNode(String targetNode) { this.targetNode = targetNode; }
    public double getWeight() { return weight; }
    public void setWeight(double weight) { this.weight = weight; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
