package com.chinacreator.gzcm.engine.cognitive2.model;

import java.sql.Timestamp;
import java.util.List;

public class CognitivePipeline {
    private String id;
    private String name;
    private String description;
    private List<CognitivePipelineNode> nodes;
    private Timestamp createdAt;
    private Timestamp updatedAt;

    public CognitivePipeline() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<CognitivePipelineNode> getNodes() {
        return nodes;
    }

    public void setNodes(List<CognitivePipelineNode> nodes) {
        this.nodes = nodes;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    public Timestamp getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Timestamp updatedAt) {
        this.updatedAt = updatedAt;
    }
}
