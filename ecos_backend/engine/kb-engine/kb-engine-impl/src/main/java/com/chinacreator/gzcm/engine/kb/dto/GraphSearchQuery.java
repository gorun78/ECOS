package com.chinacreator.gzcm.engine.kb.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 图谱全字段搜索请求 DTO — POST {@code /api/v1/knowledge/graph/search} 入参。
 *
 * <p>关键词 {@code keyword} 必填，命中 {@code graph_node.label ILIKE}；
 * {@code nodeType} 可选，命中后按 {@code node_type} 过滤；
 * {@code limit} 默认 20，上限 100。</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GraphSearchQuery {

    private String keyword;
    private String nodeType;
    private int limit;

    public String getKeyword() { return keyword; }

    public void setKeyword(String keyword) { this.keyword = keyword; }

    public String getNodeType() { return nodeType; }

    public void setNodeType(String nodeType) { this.nodeType = nodeType; }

    public int getLimit() { return limit; }

    public void setLimit(int limit) { this.limit = limit; }
}
