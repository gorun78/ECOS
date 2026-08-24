package com.chinacreator.gzcm.engine.data.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.data.service.DataLineageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
 */
@RestController
@RequestMapping("/api/lineage")
public class LineageCompatController {

    private static final Logger log = LoggerFactory.getLogger(LineageCompatController.class);

    private final DataLineageService lineageService;

    public LineageCompatController(DataLineageService lineageService) {
        this.lineageService = lineageService;
    }

    // ════════════════════════════════════════════
    // API: GET /api/lineage/impact?startNode=X
    // ════════════════════════════════════════════
    @GetMapping("/impact")
    public ApiResponse lineageImpact(@RequestParam("startNode") String startNode) {
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
                    datasourceId = head + "." + tail.substring(0, lastDot);
                    tableName = tail.substring(lastDot + 1);
                } else {
                    datasourceId = head;
                    tableName = tail;
                }
            }

            Map<String, Object> lineage = lineageService.getLineage(datasourceId, tableName);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> nodes = (List<Map<String, Object>>) lineage.getOrDefault("nodes", new ArrayList<>());
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> edges = (List<Map<String, Object>>) lineage.getOrDefault("edges", new ArrayList<>());

            // 计算下游影响：从 startNode 出发沿 edges 做 BFS，计算 hop 与风险评分
            List<Map<String, Object>> impacted = computeImpact(startNode, nodes, edges);

            int maxRisk = impacted.stream()
                    .mapToInt(r -> ((Number) r.getOrDefault("riskScore", 0)).intValue())
                    .max().orElse(0);
            String severity = "LOW";
            if (maxRisk > 80) severity = "CRITICAL";
            else if (maxRisk > 55) severity = "HIGH";
            else if (maxRisk > 30) severity = "MEDIUM";

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("startNode", startNode);
            result.put("impactedNodes", impacted);
            result.put("totalRisk", maxRisk);
            result.put("severity", severity);
            result.put("nodes", nodes);
            result.put("edges", edges);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("Impact analysis failed for startNode={}", startNode, e);
            // 格式不匹配或解析失败：返回空结果（非错误）
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("startNode", startNode);
            result.put("impactedNodes", new ArrayList<>());
            result.put("totalRisk", 0);
            result.put("severity", "LOW");
            result.put("nodes", new ArrayList<>());
            result.put("edges", new ArrayList<>());
            return ApiResponse.success(result);
        }
    }

    // ════════════════════════════════════════════
    // API: POST /api/lineage/parse
    // ════════════════════════════════════════════
    @PostMapping("/parse")
    public ApiResponse parseLineage(@RequestBody Map<String, Object> body) {
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

            List<Map<String, Object>> allNodes = new ArrayList<>();
            List<Map<String, Object>> allEdges = new ArrayList<>();
            int addedNodes = 0;
            int addedEdges = 0;

            for (String tableName : tableNames) {
                Map<String, Object> lineage = lineageService.getLineage(null, tableName);
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> nodes = (List<Map<String, Object>>) lineage.getOrDefault("nodes", new ArrayList<>());
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> edges = (List<Map<String, Object>>) lineage.getOrDefault("edges", new ArrayList<>());
                allNodes.addAll(nodes);
                allEdges.addAll(edges);
                addedNodes += nodes.size();
                addedEdges += edges.size();
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("message", "血缘解析完成");
            result.put("addedNodes", addedNodes);
            result.put("addedLinks", addedEdges);
            Map<String, Object> lineageView = new LinkedHashMap<>();
            lineageView.put("nodes", allNodes);
            lineageView.put("links", allEdges);
            result.put("lineage", lineageView);
            return ApiResponse.success(result);
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
    @SuppressWarnings("unchecked")
    private List<String> extractTableNames(Object payload) {
        List<String> names = new ArrayList<>();
        if (payload instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) payload;
            // OpenLineage: inputs + outputs
            for (String key : new String[]{"inputs", "outputs"}) {
                Object listObj = map.get(key);
                if (listObj instanceof List) {
                    for (Object item : (List<Object>) listObj) {
                        if (item instanceof Map) {
                            Object name = ((Map<String, Object>) item).get("name");
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
            if (tn instanceof String) names.add(simpleName((String) tn));
        }
        return names;
    }

    /** 取限定名的最后一段作为表名。 */
    private String simpleName(String qualified) {
        if (qualified == null) return "";
        int dot = qualified.lastIndexOf('.');
        return dot >= 0 ? qualified.substring(dot + 1) : qualified;
    }

    /** BFS 计算从 startNodeId 出发的下游影响节点与风险评分。 */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> computeImpact(String startNodeId,
                                                     List<Map<String, Object>> nodes,
                                                     List<Map<String, Object>> edges) {
        List<Map<String, Object>> results = new ArrayList<>();
        java.util.Set<String> visited = new java.util.HashSet<>();
        java.util.Deque<Map<String, Object>> queue = new java.util.LinkedList<>();

        List<String> initialPath = new ArrayList<>();
        initialPath.add(startNodeId);
        Map<String, Object> startItem = new LinkedHashMap<>();
        startItem.put("id", startNodeId);
        startItem.put("hop", 0);
        startItem.put("path", initialPath);
        queue.add(startItem);

        // 构建 source->targets 邻接表（兼容 edges 字段名 source/target 与 lineage edges）
        Map<String, List<String>> adj = new LinkedHashMap<>();
        for (Map<String, Object> e : edges) {
            Object src = e.get("source");
            Object tgt = e.get("target");
            if (src == null) src = e.get("source_node_id");
            if (tgt == null) tgt = e.get("target_node_id");
            if (src == null || tgt == null) continue;
            adj.computeIfAbsent(src.toString(), k -> new ArrayList<>()).add(tgt.toString());
        }

        // 节点 id -> node 信息（兼容 nodes 中 id/table 字段）
        Map<String, Map<String, Object>> nodeIndex = new LinkedHashMap<>();
        for (Map<String, Object> n : nodes) {
            Object id = n.get("id");
            if (id == null) id = n.get("table");
            if (id != null) nodeIndex.put(id.toString(), n);
        }

        while (!queue.isEmpty()) {
            Map<String, Object> current = queue.poll();
            String currentId = (String) current.get("id");
            int currentHop = ((Number) current.get("hop")).intValue();
            if (visited.contains(currentId)) continue;
            visited.add(currentId);

            if (!currentId.equals(startNodeId)) {
                Map<String, Object> nodeInfo = nodeIndex.get(currentId);
                if (nodeInfo != null) {
                    int rawScore = (int) Math.round(100 * Math.pow(0.80, currentHop));
                    int riskScore = Math.min(100, Math.max(15, rawScore));
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", nodeInfo.getOrDefault("id", currentId));
                    item.put("label", nodeInfo.getOrDefault("label", nodeInfo.getOrDefault("name", currentId)));
                    item.put("type", nodeInfo.getOrDefault("type", "unknown"));
                    item.put("hopCount", currentHop);
                    item.put("path", current.get("path"));
                    item.put("riskScore", riskScore);
                    results.add(item);
                }
            }

            List<String> targets = adj.getOrDefault(currentId, new ArrayList<>());
            for (String target : targets) {
                if (!visited.contains(target)) {
                    List<String> newPath = new ArrayList<>((List<String>) current.get("path"));
                    newPath.add(target);
                    Map<String, Object> next = new LinkedHashMap<>();
                    next.put("id", target);
                    next.put("hop", currentHop + 1);
                    next.put("path", newPath);
                    queue.add(next);
                }
            }
        }
        return results;
    }

    private ApiResponse emptyParseResult(String message) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("message", message);
        result.put("addedNodes", 0);
        result.put("addedLinks", 0);
        Map<String, Object> lineageView = new LinkedHashMap<>();
        lineageView.put("nodes", new ArrayList<>());
        lineageView.put("links", new ArrayList<>());
        result.put("lineage", lineageView);
        return ApiResponse.success(result);
    }
}
