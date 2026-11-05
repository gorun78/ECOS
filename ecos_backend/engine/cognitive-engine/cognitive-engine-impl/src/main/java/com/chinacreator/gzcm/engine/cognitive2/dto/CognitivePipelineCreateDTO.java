package com.chinacreator.gzcm.engine.cognitive2.dto;

import java.util.List;

/**
 * 认知管线创建请求（DI-2 #147EJ）。
 *
 * <p>字段：name / description / nodes[]；节点内 config 为任意 JSON（落库前 toString，
 * 与原 {@code Map} 取值语义一致）。</p>
 */
public class CognitivePipelineCreateDTO {

    private String name;
    private String description;
    private List<NodeSpec> nodes;

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

    public List<NodeSpec> getNodes() {
        return nodes;
    }

    public void setNodes(List<NodeSpec> nodes) {
        this.nodes = nodes;
    }

    /** 管线节点定义：nodeId / nodeType / config / dependsOn。 */
    public static class NodeSpec {

        private String nodeId;
        private String nodeType;
        private Object config;
        private List<String> dependsOn;

        public String getNodeId() {
            return nodeId;
        }

        public void setNodeId(String nodeId) {
            this.nodeId = nodeId;
        }

        public String getNodeType() {
            return nodeType;
        }

        public void setNodeType(String nodeType) {
            this.nodeType = nodeType;
        }

        public Object getConfig() {
            return config;
        }

        public void setConfig(Object config) {
            this.config = config;
        }

        public List<String> getDependsOn() {
            return dependsOn;
        }

        public void setDependsOn(List<String> dependsOn) {
            this.dependsOn = dependsOn;
        }
    }
}
