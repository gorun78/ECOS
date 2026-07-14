package com.chinacreator.gzcm.worldmodel.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.service.IGraphService;
import com.chinacreator.gzcm.worldmodel.WorldModelService;
import com.chinacreator.gzcm.worldmodel.service.OntologyKgSyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * P2-4 Causal Graph API — 图查询入口。
 *
 * <p>通过 IGraphService 抽象，企业版使用 Neo4j，标准版使用 PG 关系表。</p>
 *
 * <pre>
 * GET  /api/causal/graph              — full causal DAG (nodes + edges)
 * GET  /api/causal/paths?from=X&to=Y — shortest paths between nodes
 * POST /api/causal/compare            — compare two scenarios
 * </pre>
 */
@RestController
@RequestMapping("/api/causal")
public class CausalController {

    private static final Logger log = LoggerFactory.getLogger(CausalController.class);

    private final IGraphService graphService;
    private final WorldModelService worldModelService;
    private final Optional<OntologyKgSyncService> ontologyKgSyncService;

    public CausalController(IGraphService graphService,
                            WorldModelService worldModelService,
                            Optional<OntologyKgSyncService> ontologyKgSyncService) {
        this.graphService = graphService;
        this.worldModelService = worldModelService;
        this.ontologyKgSyncService = ontologyKgSyncService;
    }

    // ═══ GET /api/causal/graph ═══
    @GetMapping("/graph")
    public ApiResponse<Map<String, Object>> graph() {
        try {
            // dev mode: query PostgreSQL ecos_wm_causal_link instead of Neo4j
            List<Map<String, Object>> links = worldModelService.listCausalLinks();
            Set<String> nodeNames = new LinkedHashSet<>();
            List<Map<String, Object>> nodes = new ArrayList<>();
            List<Map<String, Object>> edges = new ArrayList<>();

            for (Map<String, Object> link : links) {
                String srcId = String.valueOf(link.getOrDefault("sourceGoalId", ""));
                String tgtId = String.valueOf(link.getOrDefault("targetGoalId", ""));
                // Resolve goal names
                var srcGoal = worldModelService.getGoal(parseLong(srcId));
                var tgtGoal = worldModelService.getGoal(parseLong(tgtId));
                String srcName = srcGoal.map(g -> String.valueOf(g.get("name"))).orElse("Goal-" + srcId);
                String tgtName = tgtGoal.map(g -> String.valueOf(g.get("name"))).orElse("Goal-" + tgtId);

                if (nodeNames.add(srcName)) {
                    Map<String, Object> node = new LinkedHashMap<>();
                    node.put("id", srcId);
                    node.put("name", srcName);
                    node.put("type", "GOAL");
                    node.put("category", "");
                    nodes.add(node);
                }
                if (nodeNames.add(tgtName)) {
                    Map<String, Object> node = new LinkedHashMap<>();
                    node.put("id", tgtId);
                    node.put("name", tgtName);
                    node.put("type", "GOAL");
                    node.put("category", "");
                    nodes.add(node);
                }

                Map<String, Object> edge = new LinkedHashMap<>();
                edge.put("source", srcId);
                edge.put("target", tgtId);
                edge.put("strength", link.getOrDefault("strength", 0.5));
                edge.put("type", link.getOrDefault("relationshipType", "CAUSES"));
                edges.add(edge);
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("nodes", nodes);
            result.put("edges", edges);
            result.put("nodeCount", nodes.size());
            result.put("edgeCount", edges.size());
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("Causal graph query failed", e);
            return ApiResponse.internalError("因果图查询失败: " + e.getMessage());
        }
    }

    private Long parseLong(String s) {
        try { return Long.parseLong(s); } catch (Exception e) { return null; }
    }

    // ═══ GET /api/causal/paths?from=X&to=Y ═══
    @GetMapping("/paths")
    public ApiResponse<Map<String, Object>> paths(
            @RequestParam String from,
            @RequestParam String to) {
        try {
            List<Map<String, Object>> pathResults = graphService.query(
                "MATCH (a:CausalNode {name: $from}), (b:CausalNode {name: $to}), " +
                "p = shortestPath((a)-[*..5]->(b)) " +
                "RETURN nodes(p) AS pathNodes, relationships(p) AS pathRels, length(p) AS hops " +
                "LIMIT 1",
                Map.of("from", from, "to", to)
            );

            if (pathResults.isEmpty()) {
                return ApiResponse.notFound("未找到 " + from + " → " + to + " 的因果路径");
            }

            Map<String, Object> rec = pathResults.get(0);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> pathNodes = parseListField(rec, "pathNodes");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> pathRels = parseListField(rec, "pathRels");

            // Normalize path nodes
            List<Map<String, Object>> normalizedNodes = new ArrayList<>();
            if (pathNodes != null) {
                for (Map<String, Object> node : pathNodes) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", node.getOrDefault("name", node.get("id")));
                    m.put("name", node.get("name"));
                    normalizedNodes.add(m);
                }
            }

            // Normalize path edges
            List<Map<String, Object>> normalizedEdges = new ArrayList<>();
            if (pathRels != null) {
                for (Map<String, Object> edge : pathRels) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("type", edge.getOrDefault("type", ""));
                    m.put("strength", edge.getOrDefault("strength", 0.0));
                    normalizedEdges.add(m);
                }
            }

            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("from", from);
            resp.put("to", to);
            resp.put("hops", rec.getOrDefault("hops", 0));
            resp.put("nodes", normalizedNodes);
            resp.put("edges", normalizedEdges);
            return ApiResponse.success(resp);
        } catch (Exception e) {
            log.error("Causal path query failed", e);
            return ApiResponse.internalError("路径查询失败: " + e.getMessage());
        }
    }

    // ═══ POST /api/causal/compare ═══
    @PostMapping("/compare")
    public ApiResponse<List<Map<String, Object>>> compare(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Number> rawIds = (List<Number>) body.getOrDefault("scenarioIds", List.of());
        List<Long> scenarioIds = rawIds.stream().map(Number::longValue).toList();
        List<Map<String, Object>> result = worldModelService.compareScenarios(scenarioIds);
        return ApiResponse.success(result);
    }

    // ═══ POST /api/causal/ontology/sync ═══
    @PostMapping("/ontology/sync")
    public ApiResponse<Map<String, Object>> syncOntology() {
        if (ontologyKgSyncService.isEmpty()) {
            return ApiResponse.internalError("Ontology同步仅在企业版/旗舰版可用 (Neo4j required)");
        }
        try {
            Map<String, Object> result = ontologyKgSyncService.get().syncOntologyToNeo4j();
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("Ontology sync failed", e);
            return ApiResponse.internalError("Ontology同步失败: " + e.getMessage());
        }
    }

    /**
     * 安全地将 Map 字段解析为 List&lt;Map&gt;，兼容 Neo4j Value.asList 和 PG JDBC 返回。
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseListField(Map<String, Object> record, String key) {
        Object val = record.get(key);
        if (val instanceof List) {
            return (List<Map<String, Object>>) val;
        }
        return List.of();
    }
}
