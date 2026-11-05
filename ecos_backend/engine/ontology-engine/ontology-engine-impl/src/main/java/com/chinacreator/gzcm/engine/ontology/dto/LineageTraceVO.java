package com.chinacreator.gzcm.engine.ontology.dto;

import java.util.List;

/**
 * 血缘追溯（{@code GET /ecos/lineages/trace/{nodeId}}）：
 * {@code nodeId + upstream[] + downstream[]}（BFS 可达节点，不含起点，不区分直接/间接）。
 */
public record LineageTraceVO(String nodeId, List<String> upstream, List<String> downstream) {
}
