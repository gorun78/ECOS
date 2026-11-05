package com.chinacreator.gzcm.engine.ontology.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 下游影响分析（{@code GET /lineage/impact}）：
 * {@code rootObject/impactedObjects/impactedNodes/totalRisk/severity/depth}。
 *
 * <p>{@code impactedObjects} 为属主 {@code OntologyLineageService#impactAnalysis} 的分层行
 * （{@code id/path/hopCount/riskScore...} 动态），原样透传；{@code impactedNodes} 是前端兼容面：
 * 同一行补齐 {@code path} 缺省值后合并。{@code totalRisk}=各行 riskScore 之和；
 * 严重级按「≥240 或 ≥8 条 → CRITICAL；≥90 或 ≥3 条 → HIGH；空 → NONE；否则 NORMAL」。</p>
 */
public record LineageImpactVO(
        String rootObject,
        List<Map<String, Object>> impactedObjects,
        List<Map<String, Object>> impactedNodes,
        int totalRisk,
        String severity,
        int depth) {

    public static LineageImpactVO of(String rootObject,
                                     List<Map<String, Object>> impactedObjects,
                                     int depth) {
        List<Map<String, Object>> nodes = new ArrayList<>();
        for (Map<String, Object> imp : impactedObjects) {
            Map<String, Object> n = new java.util.LinkedHashMap<>(imp);
            n.putIfAbsent("path", List.of(rootObject, String.valueOf(imp.get("id"))));
            nodes.add(n);
        }
        int totalRisk = nodes.stream()
                .mapToInt(n -> n.get("riskScore") instanceof Number v ? v.intValue() : 0)
                .sum();
        String severity = nodes.isEmpty() ? "NONE"
                : (totalRisk >= 240 || nodes.size() >= 8 ? "CRITICAL"
                : (totalRisk >= 90 || nodes.size() >= 3 ? "HIGH" : "NORMAL"));
        return new LineageImpactVO(rootObject, impactedObjects, nodes, totalRisk, severity, depth);
    }
}
