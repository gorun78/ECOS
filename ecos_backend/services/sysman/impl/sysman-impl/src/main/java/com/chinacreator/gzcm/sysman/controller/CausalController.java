package com.chinacreator.gzcm.sysman.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import com.chinacreator.gzcm.sysman.service.CausalQueryService;
import com.chinacreator.gzcm.sysman.service.CausalQueryService.CausalEdgeRow;
import com.chinacreator.gzcm.sysman.service.CausalQueryService.CausalNodeRow;

/**
 * 因果图端点（graph / paths / compare）。
 *
 * <p><b>#147DE 载体</b>：节点/边/路径/场景/差异全部改 record（键名与旧 Map 逐键一致）；
 * 请求体 {@code /compare} 仍为自由 Map（只读 {@code scenarioIds}）。</p>
 */
@RestController("sysmanCausalController")
@RequestMapping("/api/v1/causal")
public class CausalController {

    private final CausalQueryService causalQueryService;
    private static final Logger log = LoggerFactory.getLogger(CausalController.class);

    private final ConcurrentHashMap<String, CausalNodeRow> nodeStore = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CausalEdgeRow> edgeStore = new ConcurrentHashMap<>();

    public CausalController(CausalQueryService causalQueryService) {
        this.causalQueryService = causalQueryService;
        initSeedData();
    }

    /** 因果图（4 键）。 */
    public record CausalGraphVO(@JsonProperty("nodes") List<CausalNodeRow> nodes,
                                @JsonProperty("edges") List<CausalEdgeRow> edges,
                                @JsonProperty("nodeCount") int nodeCount,
                                @JsonProperty("edgeCount") int edgeCount) {
    }

    /** 因果路径（5 键）。 */
    public record CausalPathVO(@JsonProperty("nodes") List<String> nodes,
                               @JsonProperty("length") int length,
                               @JsonProperty("edges") List<CausalEdgeRow> edges,
                               @JsonProperty("totalWeight") double totalWeight,
                               @JsonProperty("avgWeight") double avgWeight) {
    }

    /** 路径列表（2 键）。 */
    public record CausalPathsVO(@JsonProperty("paths") List<CausalPathVO> paths,
                                @JsonProperty("total") int total) {
    }

    /** 对比场景节点（原节点 4 键 + {@code impactFactor}）。 */
    public record ProjectedNodeVO(@JsonProperty("id") String id,
                                  @JsonProperty("name") String name,
                                  @JsonProperty("type") String type,
                                  @JsonProperty("group") String group,
                                  @JsonProperty("impactFactor") double impactFactor) {
    }

    /** 对比场景边（原边 5 键 + {@code projectedWeight}）。 */
    public record ProjectedEdgeVO(@JsonProperty("id") String id,
                                  @JsonProperty("source") String source,
                                  @JsonProperty("target") String target,
                                  @JsonProperty("weight") double weight,
                                  @JsonProperty("type") String type,
                                  @JsonProperty("projectedWeight") double projectedWeight) {
    }

    /** 对比场景（4 键）。 */
    public record CausalScenarioVO(@JsonProperty("id") String id,
                                   @JsonProperty("name") String name,
                                   @JsonProperty("nodes") List<ProjectedNodeVO> nodes,
                                   @JsonProperty("edges") List<ProjectedEdgeVO> edges) {
    }

    /** 场景差异（4 键）。 */
    public record CausalDiffVO(@JsonProperty("scenarioA") String scenarioA,
                               @JsonProperty("scenarioB") String scenarioB,
                               @JsonProperty("nodeCountDiff") int nodeCountDiff,
                               @JsonProperty("edgeCountDiff") int edgeCountDiff) {
    }

    /** 对比响应（3 键）。 */
    public record CausalCompareVO(@JsonProperty("scenarios") List<CausalScenarioVO> scenarios,
                                  @JsonProperty("differences") List<CausalDiffVO> differences,
                                  @JsonProperty("comparedCount") int comparedCount) {
    }

    private boolean dbAvailable() {
        try {
            causalQueryService.ping();
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
            nodeStore.put(n[0], new CausalNodeRow(n[0], n[1], n[2], n[3]));
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
            edgeStore.put(e[0], new CausalEdgeRow(e[0], e[1], e[2], Double.parseDouble(e[3]), e[4]));
        }

        log.info("CausalController 初始化完成，已加载 {} 个节点, {} 条边", nodeStore.size(), edgeStore.size());
    }

    @GetMapping("/graph")
    public ApiResponse<CausalGraphVO> getGraph() {
        try {
            List<CausalNodeRow> nodes;
            List<CausalEdgeRow> edges;

            if (dbAvailable()) {
                nodes = causalQueryService.listNodes();
                edges = causalQueryService.listEdges();
            } else {
                nodes = new ArrayList<>(nodeStore.values());
                edges = new ArrayList<>(edgeStore.values());
            }

            return ApiResponse.success(new CausalGraphVO(nodes, edges, nodes.size(), edges.size()));
        } catch (Exception e) {
            log.error("获取因果图失败", e);
            return ApiResponse.internalError("获取因果图失败");
        }
    }

    @GetMapping("/paths")
    public ApiResponse<CausalPathsVO> getPaths(
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String target,
            @RequestParam(required = false, defaultValue = "5") int maxDepth) {
        try {
            List<CausalPathVO> paths;

            if (source != null && target != null) {
                paths = findPaths(source, target, maxDepth);
            } else {
                paths = findAllPaths(maxDepth);
            }

            return ApiResponse.success(new CausalPathsVO(paths, paths.size()));
        } catch (Exception e) {
            log.error("获取因果路径失败", e);
            return ApiResponse.internalError("获取因果路径失败");
        }
    }

    private List<CausalPathVO> findPaths(String source, String target, int maxDepth) {
        List<List<String>> rawPaths = new ArrayList<>();
        Deque<List<String>> queue = new ArrayDeque<>();
        queue.add(List.of(source));

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

            for (CausalEdgeRow edge : edgeStore.values()) {
                if (edge.source().equals(last)) {
                    String next = edge.target();
                    Set<String> visited = new HashSet<>(current);
                    if (!visited.contains(next)) {
                        List<String> newPath = new ArrayList<>(current);
                        newPath.add(next);
                        queue.add(newPath);
                    }
                }
            }
        }

        List<CausalPathVO> out = new ArrayList<>(rawPaths.size());
        for (List<String> path : rawPaths) {
            double totalWeight = 0.0;
            List<CausalEdgeRow> pathEdges = new ArrayList<>();
            for (int i = 0; i < path.size() - 1; i++) {
                String src = path.get(i);
                String tgt = path.get(i + 1);
                for (CausalEdgeRow edge : edgeStore.values()) {
                    if (edge.source().equals(src) && edge.target().equals(tgt)) {
                        totalWeight += edge.weight();
                        pathEdges.add(edge);
                        break;
                    }
                }
            }
            out.add(new CausalPathVO(path, path.size() - 1, pathEdges, totalWeight,
                    pathEdges.isEmpty() ? 0.0 : totalWeight / pathEdges.size()));
        }
        return out;
    }

    private List<CausalPathVO> findAllPaths(int maxDepth) {
        List<CausalPathVO> paths = new ArrayList<>();
        for (String sourceId : nodeStore.keySet()) {
            for (String targetId : nodeStore.keySet()) {
                if (!sourceId.equals(targetId)) {
                    paths.addAll(findPaths(sourceId, targetId, maxDepth));
                }
            }
            if (paths.size() >= 50) {
                break;
            }
        }
        return paths.stream().limit(50).toList();
    }

    @PostMapping("/compare")
    public ApiResponse<CausalCompareVO> compare(@RequestBody Map<String, Object> request) {
        try {
            @SuppressWarnings("unchecked")
            List<String> scenarioIds = (List<String>) request.get("scenarioIds");
            if (scenarioIds == null || scenarioIds.size() < 2) {
                return ApiResponse.badRequest("至少需要两个场景进行对比");
            }

            List<CausalScenarioVO> scenarios = new ArrayList<>();
            for (String scenarioId : scenarioIds) {
                List<ProjectedNodeVO> scenarioNodes = new ArrayList<>();
                for (CausalNodeRow node : nodeStore.values()) {
                    double mod = 0.8 + Math.random() * 0.4;
                    scenarioNodes.add(new ProjectedNodeVO(node.id(), node.name(), node.type(),
                            node.group(), Math.round(mod * 100.0) / 100.0));
                }

                List<ProjectedEdgeVO> scenarioEdges = new ArrayList<>();
                for (CausalEdgeRow edge : edgeStore.values()) {
                    double mod = 0.7 + Math.random() * 0.6;
                    scenarioEdges.add(new ProjectedEdgeVO(edge.id(), edge.source(), edge.target(),
                            edge.weight(), edge.type(),
                            Math.round(edge.weight() * mod * 100.0) / 100.0));
                }

                scenarios.add(new CausalScenarioVO(scenarioId, "场景 " + scenarioId,
                        scenarioNodes, scenarioEdges));
            }

            List<CausalDiffVO> differences = new ArrayList<>();
            for (int i = 0; i < scenarios.size() - 1; i++) {
                for (int j = i + 1; j < scenarios.size(); j++) {
                    differences.add(new CausalDiffVO(scenarioIds.get(i), scenarioIds.get(j),
                            scenarios.get(i).nodes().size() - scenarios.get(j).nodes().size(),
                            scenarios.get(i).edges().size() - scenarios.get(j).edges().size()));
                }
            }

            return ApiResponse.success(new CausalCompareVO(scenarios, differences, scenarios.size()));
        } catch (Exception e) {
            log.error("场景对比失败", e);
            return ApiResponse.internalError("场景对比失败");
        }
    }
}
