package com.chinacreator.gzcm.engine.data.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.data.service.DataLineageService;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * LineageCompatController — 血缘兼容端点（T3: 替换 CeosCompatController 内存 mock）。
 *
 * <p>路径与原 CeosCompatController 完全一致，前端无需改动：</p>
 * <ul>
 *   <li>GET  /api/lineage/impact?startNode=X  — 下游影响度分析（调 DataLineageService 真实血缘）</li>
 *   <li>POST /api/lineage/parse               — 解析 OpenLineage/Atlas 血缘 JSON（调 DataLineageService）</li>
 * </ul>
 *
 * <p>DataLineageService 已有 JSqlParser 字段级血缘，getLineage(datasourceId, tableName)
 * 返回真实血缘解析结果。若 startNode 格式不匹配或无法解析，返回空 nodes/edges。</p>
 *
 * <p><b>#147DI 载体</b>：影响/解析/血缘视图改 record；血缘行本身由
 * {@link DataLineageService} 按解析结果动态产出（节点可有 {@code id} 或 {@code table}，
 * 边可有 {@code source/target} 或 {@code source_node_id/target_node_id}），故节点/边集合
 * 仍以 {@code Object} 承载原样 JSON——**不冻结动态 schema**，这是属主契约而非本类欠账。</p>
 */
@RestController
@RequestMapping("/api/lineage")
public class LineageCompatController {

    private static final Logger log = LoggerFactory.getLogger(LineageCompatController.class);

    private final DataLineageService lineageService;

    public LineageCompatController(DataLineageService lineageService) {
        this.lineageService = lineageService;
    }

    /** 影响节点（6 键；id/label/type 取原行原值，可能是字符串或数字）。 */
    public record ImpactNodeVO(@JsonProperty("id") Object id,
                               @JsonProperty("label") Object label,
                               @JsonProperty("type") Object type,
                               @JsonProperty("hopCount") int hopCount,
                               @JsonProperty("path") List<String> path,
                               @JsonProperty("riskScore") int riskScore) {
    }

    /** 影响分析响应（7 键）。 */
    public record ImpactVO(@JsonProperty("success") boolean success,
                           @JsonProperty("startNode") String startNode,
                           @JsonProperty("impactedNodes") List<ImpactNodeVO> impactedNodes,
                           @JsonProperty("totalRisk") int totalRisk,
                           @JsonProperty("severity") String severity,
                           @JsonProperty("nodes") Object nodes,
                           @JsonProperty("edges") Object edges) {
    }

    /** 解析结果内的血缘视图（2 键：nodes/links，形状由解析器定义）。 */
    public record LineageViewVO(@JsonProperty("nodes") Object nodes,
                                @JsonProperty("links") Object links) {
    }

    /** 解析响应（5 键）。 */
    public record ParseResultVO(@JsonProperty("success") boolean success,
                                @JsonProperty("message") String message,
                                @JsonProperty("addedNodes") int addedNodes,
                                @JsonProperty("addedLinks") int addedLinks,
                                @JsonProperty("lineage") LineageViewVO lineage) {
    }

    /** BFS 队列条目（内部载体，不外发）。 */
    private record BfsItem(String id, int hop, List<String> path) {
    }

    // ════════════════════════════════════════════
    // API: GET /api/lineage/impact?startNode=X
    // ════════════════════════════════════════════
    @GetMapping("/impact")
    public ApiResponse<ImpactVO> lineageImpact(@RequestParam("startNode") String startNode) {
        if (startNode == null || startNode.isEmpty()) {
            return ApiResponse.error(400, "Missing startNode query parameter");
        }

        try {
            // startNode 格式可能是 "datasourceId.tableName" 或纯表名。
            // 尝试拆分以适配 DataLineageService.getLineage(datasourceId, tableName)。
            String datasourceId = null;
            String tableName = startNode;

            int dot = startNode.indexOf('.');
            if (dot > 0 && dot < startNode.length() - 1) {
                // 仅当首段不含空格/特殊字符时才视作 datasourceId
                String head = startNode.substring(0, dot);
                String tail = startNode.substring(dot + 1);
                // 若 tail 仍含 "."，取最后一段作为 tableName，前面合并为 datasourceId
                int lastDot = tail.lastIndexOf('.');
                if (lastDot >= 0) {
                    datasourceId = head + "." + tail.substring(0, tail.lastIndexOf('.'));
                    tableName = tail.substring(lastDot + 1);
                } else {
                    datasourceId = head;
                    tableName = tail;
                }
            }

            Map<String, Object> lineage = asMap(lineageService.getLineage(datasourceId, tableName));
            List<?> nodes = lineage == null ? List.of()
                    : (List<?>) lineage.getOrDefault("nodes", new ArrayList<>());
            List<?> edges = lineage == null ? List.of()
                    : (List<?>) lineage.getOrDefault("edges", new ArrayList<>());

            // 计算下游影响：从 startNode 出发沿 edges 做 BFS，计算 hop 与风险评分
            List<ImpactNodeVO> impacted = computeImpact(startNode, nodes, edges);

            int maxRisk = impacted.stream().mapToInt(ImpactNodeVO::riskScore).max().orElse(0);
            String severity = "LOW";
            if (maxRisk > 80) severity = "CRITICAL";
            else if (maxRisk > 55) severity = "HIGH";
            else if (maxRisk > 30) severity = "MEDIUM";

            return ApiResponse.success(new ImpactVO(true, startNode, impacted, maxRisk,
                    severity, nodes, edges));
        } catch (Exception e) {
            log.error("Impact analysis failed for startNode={}", startNode, e);
            // 格式不匹配或解析失败：返回空结果（非错误）
            return ApiResponse.success(new ImpactVO(true, startNode, new ArrayList<>(), 0,
                    "LOW", new ArrayList<>(), new ArrayList<>()));
        }
    }

    // ════════════════════════════════════════════
    // API: POST /api/lineage/parse
    // ════════════════════════════════════════════
    @PostMapping("/parse")
    public ApiResponse<ParseResultVO> parseLineage(@RequestBody Map<String, Object> body) {
        if (body == null) {
            return ApiResponse.error(400, "Missing request body");
        }
        Object payload = body.get("payload");
        if (payload == null) {
            // 允许直接传 OpenLineage RunEvent（顶层含 job/inputs/outputs）
            payload = body;
        }

        try {
            // 尝试从 OpenLineage/Atlas payload 提取 outputs 表名，调 DataLineageService 解析真实血缘。
            List<String> tableNames = extractTableNames(payload);
            if (tableNames.isEmpty()) {
                return emptyParseResult("无法从 payload 提取表名，返回空血缘。");
            }

            List<Object> allNodes = new ArrayList<>();
            List<Object> allEdges = new ArrayList<>();
            int addedNodes = 0;
            int addedEdges = 0;

            for (String tableName : tableNames) {
                Map<String, Object> lineage = asMap(lineageService.getLineage(null, tableName));
                List<?> nodes = lineage == null ? List.of()
                        : (List<?>) lineage.getOrDefault("nodes", new ArrayList<>());
                List<?> edges = lineage == null ? List.of()
                        : (List<?>) lineage.getOrDefault("edges", new ArrayList<>());
                allNodes.addAll(nodes);
                allEdges.addAll(edges);
                addedNodes += nodes.size();
                addedEdges += edges.size();
            }

            return ApiResponse.success(new ParseResultVO(true, "血缘解析完成", addedNodes,
                    addedEdges, new LineageViewVO(allNodes, allEdges)));
        } catch (Exception e) {
            log.error("Lineage parse failed", e);
            // 无法解析：返回空结果（非错误）
            return emptyParseResult("Parse failed: " + e.getMessage());
        }
    }

    // ════════════════════════════════════════════
    // 内部方法
    // ════════════════════════════════════════════

    /** 从 OpenLineage/Atlas payload 提取涉及的表名。 */
    private List<String> extractTableNames(Object payload) {
        List<String> names = new ArrayList<>();
        Map<String, Object> map = asMap(payload);
        if (map == null) {
            return names;
        }
        // OpenLineage: inputs + outputs
        for (String key : new String[]{"inputs", "outputs"}) {
            Object listObj = map.get(key);
            if (listObj instanceof List<?> list) {
                for (Object item : list) {
                    Map<String, Object> itemMap = asMap(item);
                    if (itemMap != null) {
                        Object name = itemMap.get("name");
                        if (name != null) {
                            names.add(simpleName(name.toString()));
                        }
                    }
                }
            }
        }
        // 兼容：直接含 tableName/table 字段
        Object tn = map.get("tableName");
        if (tn == null) tn = map.get("table");
        if (tn instanceof String s) names.add(simpleName(s));
        return names;
    }

    /** 取限定名的最后一段作为表名。 */
    private String simpleName(String qualified) {
        if (qualified == null) return "";
        int dot = qualified.lastIndexOf('.');
        return dot >= 0 ? qualified.substring(dot + 1) : qualified;
    }

    /** BFS 计算从 startNodeId 出发的下游影响节点与风险评分。 */
    private List<ImpactNodeVO> computeImpact(String startNodeId, List<?> nodes, List<?> edges) {
        List<ImpactNodeVO> results = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Deque<BfsItem> queue = new ArrayDeque<>();

        queue.add(new BfsItem(startNodeId, 0, new ArrayList<>(List.of(startNodeId))));

        // 构建 source->targets 邻接表（兼容 edges 字段名 source/target 与 lineage edges）
        Map<String, List<String>> adj = new LinkedHashMap<>();
        for (Object edgeObj : edges) {
            Map<String, Object> e = asMap(edgeObj);
            if (e == null) continue;
            Object src = e.get("source");
            Object tgt = e.get("target");
            if (src == null) src = e.get("source_node_id");
            if (tgt == null) tgt = e.get("target_node_id");
            if (src == null || tgt == null) continue;
            adj.computeIfAbsent(src.toString(), k -> new ArrayList<>()).add(tgt.toString());
        }

        // 节点 id -> node 信息（兼容 nodes 中 id/table 字段）
        Map<String, Map<String, Object>> nodeIndex = new LinkedHashMap<>();
        for (Object nodeObj : nodes) {
            Map<String, Object> n = asMap(nodeObj);
            if (n == null) continue;
            Object id = n.get("id");
            if (id == null) id = n.get("table");
            if (id != null) nodeIndex.put(id.toString(), n);
        }

        while (!queue.isEmpty()) {
            BfsItem current = queue.poll();
            if (visited.contains(current.id())) continue;
            visited.add(current.id());

            if (!current.id().equals(startNodeId)) {
                Map<String, Object> nodeInfo = nodeIndex.get(current.id());
                if (nodeInfo != null) {
                    int rawScore = (int) Math.round(100 * Math.pow(0.80, current.hop()));
                    int riskScore = Math.min(100, Math.max(15, rawScore));
                    results.add(new ImpactNodeVO(nodeInfo.getOrDefault("id", current.id()),
                            nodeInfo.getOrDefault("label", nodeInfo.getOrDefault("name", current.id())),
                            nodeInfo.getOrDefault("type", "unknown"),
                            current.hop(), current.path(), riskScore));
                }
            }

            List<String> targets = adj.getOrDefault(current.id(), new ArrayList<>());
            for (String target : targets) {
                if (!visited.contains(target)) {
                    List<String> newPath = new ArrayList<>(current.path());
                    newPath.add(target);
                    queue.add(new BfsItem(target, current.hop() + 1, newPath));
                }
            }
        }
        return results;
    }

    private ApiResponse<ParseResultVO> emptyParseResult(String message) {
        return ApiResponse.success(new ParseResultVO(true, message, 0, 0,
                new LineageViewVO(new ArrayList<>(), new ArrayList<>())));
    }

    /** 动态 JSON 对象 → 键值视图（形状由解析器/调用方定义，不为定型伪造列）。 */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object o) {
        return o instanceof Map<?, ?> m ? new LinkedHashMap<>((Map<String, Object>) m) : null;
    }
}
