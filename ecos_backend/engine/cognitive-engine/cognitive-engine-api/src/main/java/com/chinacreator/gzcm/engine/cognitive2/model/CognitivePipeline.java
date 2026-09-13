package com.chinacreator.gzcm.engine.cognitive2.model;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CognitivePipeline {
    private String id;
    private String name;
    private String description;
    private String status;
    private List<CognitivePipelineNode> nodes;
    private Timestamp createdAt;
    private Timestamp updatedAt;
    /** 创建人（持久化用） */
    private String createdBy;

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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
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

    /** 从 JSON 字符串构建管线对象（用于 JdbcTemplate RowMapper） */
    @SuppressWarnings("unchecked")
    public static CognitivePipeline fromNodesJson(String nodesJson) {
        CognitivePipeline pipeline = new CognitivePipeline();
        if (nodesJson == null || nodesJson.isBlank()) {
            pipeline.setNodes(new java.util.ArrayList<>());
            return pipeline;
        }
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            java.util.List<java.util.Map<String, Object>> nodeList =
                    mapper.readValue(nodesJson, new com.fasterxml.jackson.core.type.TypeReference<>() {});
            List<CognitivePipelineNode> nodes = new java.util.ArrayList<>();
            for (java.util.Map<String, Object> nm : nodeList) {
                String nodeId = (String) nm.get("nodeId");
                String nodeTypeStr = (String) nm.get("nodeType");
                NodeType nodeType = nodeTypeStr != null ? NodeType.fromString(nodeTypeStr) : null;
                Object configObj = nm.get("config");
                String config = configObj != null ? configObj.toString() : "{}";
                java.util.List<String> dependsOn = (java.util.List<String>) nm.getOrDefault("dependsOn", java.util.Collections.emptyList());
                nodes.add(new CognitivePipelineNode(nodeId, nodeType, config, dependsOn));
            }
            pipeline.setNodes(nodes);
        } catch (Exception e) {
            // 解析失败返回空节点，避免 RowMapper 抛异常
            pipeline.setNodes(new java.util.ArrayList<>());
        }
        return pipeline;
    }

    /** 将节点集合序列化为 JSON 字符串（用于 JdbcTemplate INSERT/UPDATE） */
    public String toNodesJson() {
        if (nodes == null || nodes.isEmpty()) {
            return "[]";
        }
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            java.util.List<java.util.Map<String, Object>> nodeList = new java.util.ArrayList<>();
            for (CognitivePipelineNode node : nodes) {
                java.util.Map<String, Object> nm = new java.util.LinkedHashMap<>();
                nm.put("nodeId", node.getNodeId());
                nm.put("nodeType", node.getNodeType() != null ? node.getNodeType().name() : "UNKNOWN");
                nm.put("config", node.getConfig());
                nm.put("dependsOn", node.getDependsOn() != null ? node.getDependsOn() : java.util.Collections.emptyList());
                nodeList.add(nm);
            }
            return mapper.writeValueAsString(nodeList);
        } catch (Exception e) {
            return "[]";
        }
    }
}
