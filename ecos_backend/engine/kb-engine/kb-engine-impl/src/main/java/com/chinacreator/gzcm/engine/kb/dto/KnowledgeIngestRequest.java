package com.chinacreator.gzcm.engine.kb.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/**
 * 知识实体接入请求 DTO — POST {@code /api/v1/knowledge/ingest} 入参。
 *
 * <p>必填 {@code entityId}（幂等键，对应 graph_node.id）；
 * {@code type} 节点类型（默认 Concept）；
 * {@code sourceRef} 来源标识（落到 description）；
 * {@code properties} 业务属性 Map（合并到 propertiesJson）；
 * {@code payload} 原始载荷（合并到 propertiesJson 的 {@code payload} 键）。</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class KnowledgeIngestRequest {

    private String entityId;
    private String label;
    private String type;
    private String sourceRef;
    private Map<String, String> properties;
    private Object payload;

    public KnowledgeIngestRequest() {}

    public String getEntityId() { return entityId; }

    @JsonAlias({"id", "idOf"})
    public void setEntityId(String entityId) { this.entityId = entityId; }

    public String getLabel() { return label; }

    public void setLabel(String label) { this.label = label; }

    public String getType() { return type; }

    @JsonAlias({"nodeType", "node_type"})
    public void setType(String type) { this.type = type; }

    public String getSourceRef() { return sourceRef; }

    public void setSourceRef(String sourceRef) { this.sourceRef = sourceRef; }

    public Map<String, String> getProperties() { return properties; }

    public void setProperties(Map<String, String> properties) { this.properties = properties; }

    public Object getPayload() { return payload; }

    public void setPayload(Object payload) { this.payload = payload; }
}
