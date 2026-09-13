package com.chinacreator.gzcm.engine.kb.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 图谱双点路径查询请求 DTO — POST {@code /api/v1/knowledge/graph/path} 入参。
 *
 * <p>{@code source} / {@code target} 必填，对应 graph_node.id；
 * {@code maxDepth} 默认 8，上限 16（防止大图 BFS 爆炸）。
 * 路径不存在时按要求返回空 list（不抛 404，对齐契约 §4.8）。</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GraphPathQuery {

    private String source;
    private String target;
    private int maxDepth;

    public GraphPathQuery() {}

    /** Jackson 反序列化从 {@code source_id} / {@code sourceNodeId} / {@code s} 双向兼容。 */
    public String getSource() { return source; }

    @JsonAlias({"source_id", "sourceNodeId", "s"})
    public void setSource(String source) { this.source = source; }

    public String getTarget() { return target; }

    @JsonAlias({"target_id", "targetNodeId", "t"})
    public void setTarget(String target) { this.target = target; }

    public int getMaxDepth() { return maxDepth; }

    public void setMaxDepth(int maxDepth) { this.maxDepth = maxDepth; }
}
