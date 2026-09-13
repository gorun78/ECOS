package com.chinacreator.gzcm.engine.kb.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 图谱节点 VO — POST {@code /api/v1/knowledge/graph/search} 命中 top N 的返回单元。
 *
 * <p>命中 top N 节点 + 邻接摘要（outgoing/incoming 边数）。
 * 字段命名沿用 {@code KnowledgeNode}（id/label/nodeType/description/domain）+
 * 邻接版权度（outDegree/inDegree）供前端 GraphExplorer 单点渲染。</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GraphNodeVO {

    private final String id;
    private final String label;
    private final String nodeType;
    private final String description;
    private final String domain;
    private final int outDegree;
    private final int inDegree;

    public GraphNodeVO(String id, String label, String nodeType, String description,
                       String domain, int outDegree, int inDegree) {
        this.id = id;
        this.label = label;
        this.nodeType = nodeType;
        this.description = description;
        this.domain = domain;
        this.outDegree = outDegree;
        this.inDegree = inDegree;
    }

    public String getId() { return id; }
    public String getLabel() { return label; }
    public String getNodeType() { return nodeType; }
    public String getDescription() { return description; }
    public String getDomain() { return domain; }
    public int getOutDegree() { return outDegree; }
    public int getInDegree() { return inDegree; }
}
