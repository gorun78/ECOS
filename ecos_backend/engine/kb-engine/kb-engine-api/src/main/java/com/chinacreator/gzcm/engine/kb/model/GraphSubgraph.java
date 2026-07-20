package com.chinacreator.gzcm.engine.kb.model;

import java.util.List;

public class GraphSubgraph {

    private String id;
    private String name;
    private String domain;
    private List<String> nodeIds;
    private List<String> edgeIds;
    private String description;
    private long createdAt;

    public GraphSubgraph() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }
    public List<String> getNodeIds() { return nodeIds; }
    public void setNodeIds(List<String> nodeIds) { this.nodeIds = nodeIds; }
    public List<String> getEdgeIds() { return edgeIds; }
    public void setEdgeIds(List<String> edgeIds) { this.edgeIds = edgeIds; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
}