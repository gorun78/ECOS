package com.chinacreator.gzcm.sysman.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import com.chinacreator.gzcm.sysman.service.CausalQueryService;

@RestController("sysmanCausalController")
@RequestMapping("/api/v1/causal")
public class CausalController {

        private final CausalQueryService causalQueryService;
private static final Logger log = LoggerFactory.getLogger(CausalController.class);

    private final ConcurrentHashMap<String, Map<String, Object>> nodeStore = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Map<String, Object>> edgeStore = new ConcurrentHashMap<>();

    public CausalController(CausalQueryService causalQueryService) {
        this.causalQueryService = causalQueryService;
        initSeedData();
    }

    private boolean dbAvailable() {
        try {
            causalQueryService.queryForObject("SELECT 1", Integer.class);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void initSeedData() {
        String[][] nodes = {
                {"n1", "数据采集", "process", "数据域"},
                {"n2", "数据质量", "metric", "数据域"},
                {"n3", "业务决策", "process", "业务域"},
                {"n4", "运营效率", "metric", "业务域"},
                {"n5", "客户满意度", "metric", "客户域"},
                {"n6", "合规风险", "risk", "风控域"},
                {"n7", "数据治理", "process", "数据域"},
                {"n8", "流程自动化", "process", "运营域"},
        };
        for (String[] n : nodes) {
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("id", n[0]);
            node.put("name", n[1]);
            node.put("type", n[2]);
            node.put("group", n[3]);
            nodeStore.put(n[0], node);
        }

        String[][] edges = {
                {"e1", "n1", "n2", "0.85", "causal"},
                {"e2", "n2", "n3", "0.72", "causal"},
                {"e3", "n7", "n2", "0.90", "causal"},
                {"e4", "n3", "n4", "0.68", "causal"},
                {"e5", "n4", "n5", "0.55", "causal"},
                {"e6", "n6", "n3", "0.40", "inhibitor"},
                {"e7", "n8", "n4", "0.78", "causal"},
                {"e8", "n1", "n7", "0.60", "causal"},
                {"e9", "n7", "n6", "0.45", "causal"},
                {"e10", "n5", "n6", "0.30", "feedback"},
        };
        for (String[] e : edges) {
            Map<String, Object> edge = new LinkedHashMap<>();
            edge.put("id", e[0]);
            edge.put("source", e[1]);
            edge.put("target", e[2]);
            edge.put("weight", Double.parseDouble(e[3]));
            edge.put("type", e[4]);
            edgeStore.put(e[0], edge);
        }

        log.info("CausalController 初始化完成，已加载 {} 个节点, {} 条边", nodeStore.size(), edgeStore.size());
    }

    @GetMapping("/graph")
    public ApiResponse<Map<String, Object>> getGraph() {
        try {
            List<Map<String, Object>> nodes;
            List<Map<String, Object>> edges;

            if (dbAvailable()) {
                nodes = causalQueryService.query(
                        "SELECT id, name, type, domain_group FROM ecos_causal_node ORDER BY id",
                        (rs, rowNum) -> {
                            Map<String, Object> n = new LinkedHashMap<>();
                            n.put("id", rs.getString("id"));
                            n.put("name", rs.getString("name"));
                            n.put("type", rs.getString("type"));
                            n.put("group", rs.getString("domain_group"));
                            return n;
                        });
                edges = causalQueryService.query(
                        "SELECT id, source_node_id, target_node_id, weight, edge_type FROM ecos_causal_edge ORDER BY id",
                        (rs, rowNum) -> {
                            Map<String, Object> e = new LinkedHashMap<>();
                            e.put("id", rs.getString("id"));
                            e.put("source", rs.getString("source_node_id"));
                            e.put("target", rs.getString("target_node_id"));
                            e.put("weight", rs.getDouble("weight"));
                            e.put("type", rs.getString("edge_type"));
                            return e;
                        });
            } else {
                nodes = new ArrayList<>(nodeStore.values());
                edges = new ArrayList<>(edgeStore.values());
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("nodes", nodes);
            result.put("edges", edges);
            result.put("nodeCount", nodes.size());
            result.put("edgeCount", edges.size());
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("获取因果图失败", e);
            return ApiResponse.internalError("获取因果图失败: " + e.getMessage());
        }
    }

    @GetMapping("/paths")
    public ApiResponse<Map<String, Object>> getPaths(
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String target,
            @RequestParam(required = false, defaultValue = "5") int maxDepth) {
        try {
            List<Map<String, Object>> paths;

            if (source != null && target != null) {
                paths = findPaths(source, target, maxDepth);
            } else {
                paths = findAllPaths(maxDepth);
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("paths", paths);
            result.put("total", paths.size());
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("获取因果路径失败", e);
            return ApiResponse.internalError("获取因果路径失败: " + e.getMessage());
        }
    }

    private List<Map<String, Object>> findPaths(String source, String target, int maxDepth) {
        List<List<String>> rawPaths = new ArrayList<>();
        Deque<List<String>> queue = new ArrayDeque<>();
        queue.add(List.of(source));

        Set<String> visited;
        while (!queue.isEmpty()) {
            List<String> current = queue.poll();
            String last = current.get(current.size() - 1);

            if (last.equals(target) && current.size() > 1) {
                rawPaths.add(new ArrayList<>(current));
                continue;
            }

            if (current.size() > maxDepth) {
                continue;
            }

            for (Map<String, Object> edge : edgeStore.values()) {
                if (edge.get("source").equals(last)) {
                    String next = (String) edge.get("target");
                    visited = new HashSet<>(current);
                    if (!visited.contains(next)) {
                        List<String> newPath = new ArrayList<>(current);
                        newPath.add(next);
                        queue.add(newPath);
                    }
                }
            }
        }

        return rawPaths.stream().map(path -> {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("nodes", path);
            p.put("length", path.size() - 1);
            double totalWeight = 0.0;
            List<Map<String, Object>> pathEdges = new ArrayList<>();
            for (int i = 0; i < path.size() - 1; i++) {
                String src = path.get(i);
                String tgt = path.get(i + 1);
                for (Map<String, Object> edge : edgeStore.values()) {
                    if (edge.get("source").equals(src) && edge.get("target").equals(tgt)) {
                        totalWeight += (Double) edge.get("weight");
                        pathEdges.add(edge);
                        break;
                    }
                }
            }
            p.put("edges", pathEdges);
            p.put("totalWeight", totalWeight);
            p.put("avgWeight", pathEdges.isEmpty() ? 0.0 : totalWeight / pathEdges.size());
            return p;
        }).collect(Collectors.toList());
    }

    private List<Map<String, Object>> findAllPaths(int maxDepth) {
        List<Map<String, Object>> paths = new ArrayList<>();
        for (String sourceId : nodeStore.keySet()) {
            for (String targetId : nodeStore.keySet()) {
                if (!sourceId.equals(targetId)) {
                    List<Map<String, Object>> found = findPaths(sourceId, targetId, maxDepth);
                    paths.addAll(found);
                }
            }
            if (paths.size() >= 50) {
                break;
            }
        }
        return paths.stream().limit(50).collect(Collectors.toList());
    }

    @PostMapping("/compare")
    public ApiResponse<Map<String, Object>> compare(@RequestBody Map<String, Object> request) {
        try {
            @SuppressWarnings("unchecked")
            List<String> scenarioIds = (List<String>) request.get("scenarioIds");
            if (scenarioIds == null || scenarioIds.size() < 2) {
                return ApiResponse.badRequest("至少需要两个场景进行对比");
            }

            List<Map<String, Object>> scenarios = new ArrayList<>();
            for (String scenarioId : scenarioIds) {
                Map<String, Object> scenario = new LinkedHashMap<>();
                scenario.put("id", scenarioId);
                scenario.put("name", "场景 " + scenarioId);

                List<Map<String, Object>> scenarioNodes = new ArrayList<>();
                for (Map<String, Object> node : nodeStore.values()) {
                    Map<String, Object> projected = new LinkedHashMap<>(node);
                    double mod = 0.8 + Math.random() * 0.4;
                    projected.put("impactFactor", Math.round(mod * 100.0) / 100.0);
                    scenarioNodes.add(projected);
                }
                scenario.put("nodes", scenarioNodes);

                List<Map<String, Object>> scenarioEdges = new ArrayList<>();
                for (Map<String, Object> edge : edgeStore.values()) {
                    Map<String, Object> projected = new LinkedHashMap<>(edge);
                    double baseWeight = (Double) edge.get("weight");
                    double mod = 0.7 + Math.random() * 0.6;
                    projected.put("projectedWeight", Math.round(baseWeight * mod * 100.0) / 100.0);
                    scenarioEdges.add(projected);
                }
                scenario.put("edges", scenarioEdges);

                scenarios.add(scenario);
            }

            List<Map<String, Object>> differences = new ArrayList<>();
            for (int i = 0; i < scenarios.size() - 1; i++) {
                for (int j = i + 1; j < scenarios.size(); j++) {
                    Map<String, Object> diff = new LinkedHashMap<>();
                    diff.put("scenarioA", scenarioIds.get(i));
                    diff.put("scenarioB", scenarioIds.get(j));
                    diff.put("nodeCountDiff",
                            scenarios.get(i).get("nodes") instanceof List<?> a
                                    && scenarios.get(j).get("nodes") instanceof List<?> b
                                    ? a.size() - b.size() : 0);
                    diff.put("edgeCountDiff",
                            scenarios.get(i).get("edges") instanceof List<?> a
                                    && scenarios.get(j).get("edges") instanceof List<?> b
                                    ? a.size() - b.size() : 0);
                    differences.add(diff);
                }
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("scenarios", scenarios);
            result.put("differences", differences);
            result.put("comparedCount", scenarios.size());
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("场景对比失败", e);
            return ApiResponse.internalError("场景对比失败: " + e.getMessage());
        }
    }
}
