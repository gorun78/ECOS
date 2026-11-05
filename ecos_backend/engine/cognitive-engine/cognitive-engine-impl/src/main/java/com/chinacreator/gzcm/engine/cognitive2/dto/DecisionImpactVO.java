package com.chinacreator.gzcm.engine.cognitive2.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 决策下游影响图（{@code GET /{id}/impact}）：
 * {@code rootId + nodes[{id,category,outcome}] + edges[{source,target,relationship}]}。
 */
public record DecisionImpactVO(String rootId, List<Node> nodes, List<Edge> edges) {

    public record Node(String id, String category, String outcome) {
    }

    public record Edge(String source, String target, String relationship) {
    }

    public static DecisionImpactVO of(Map<String, Object> source) {
        if (source == null) {
            return null;
        }
        List<Node> nodes = new ArrayList<>();
        for (Map<String, Object> n : rows(source.get("nodes"))) {
            nodes.add(new Node(str(n.get("id")), str(n.get("category")), str(n.get("outcome"))));
        }
        List<Edge> edges = new ArrayList<>();
        for (Map<String, Object> e : rows(source.get("edges"))) {
            edges.add(new Edge(str(e.get("source")), str(e.get("target")),
                    str(e.get("relationship"))));
        }
        return new DecisionImpactVO(str(source.get("rootId")), nodes, edges);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> rows(Object value) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> m) {
                    out.add((Map<String, Object>) m);
                }
            }
        }
        return out;
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
