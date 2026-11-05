package com.chinacreator.gzcm.engine.ontology.dto;

import java.util.List;

/**
 * 血缘图（{@code GET /ecos/lineages/graph}）：{@code nodes[{id}] + edges[{id,source,target,lineageType}]}。
 */
public record LineageGraphVO(List<Node> nodes, List<Edge> edges) {

    public record Node(String id) {
    }

    public record Edge(String id, String source, String target, String lineageType) {
    }
}
