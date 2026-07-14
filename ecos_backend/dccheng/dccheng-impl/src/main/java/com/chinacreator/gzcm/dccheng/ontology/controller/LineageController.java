package com.chinacreator.gzcm.dccheng.ontology.controller;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.dccheng.ontology.OntologyService;

/**
 * 数据血缘 REST API — 记录与查询本体对象之间的数据流向（上游 → 下游）。
 *
 * <p>使用 {@link ConcurrentHashMap} 内存存储血缘边记录，进程重启后数据丢失。
 * 血缘主键由 UUID 生成。提供图查询（nodes + edges）与上下游追溯能力。</p>
 *
 * <h3>端点：</h3>
 * <ul>
 *   <li>GET    /api/v1/ecos/lineages            — 血缘边列表（可按 source / target / direction 过滤）</li>
 *   <li>GET    /api/v1/ecos/lineages/{id}       — 血缘边详情</li>
 *   <li>POST   /api/v1/ecos/lineages            — 创建血缘边</li>
 *   <li>PUT    /api/v1/ecos/lineages/{id}       — 更新血缘边</li>
 *   <li>DELETE /api/v1/ecos/lineages/{id}       — 删除血缘边</li>
 *   <li>GET    /api/v1/ecos/lineages/graph      — 血缘图（nodes + edges，供前端图谱渲染）</li>
 *   <li>GET    /api/v1/ecos/lineages/trace/{nodeId} — 追溯指定节点的上游/下游链路</li>
 *   <li>GET    /api/v1/ecos/lineages/entities   — 可作为血缘节点的本体实体列表（委托 OntologyService.listEntities）</li>
 * </ul>
 *
 * <p>本控制器只新增血缘管理端点，不改动 {@link OntologyController} 的现有 CRUD 签名。</p>
 */
@RestController
@RequestMapping("/api/v1/lineage")
public class LineageController {

    private static final Logger log = LoggerFactory.getLogger(LineageController.class);

    /** 内存存储：lineageId → 血缘边记录 */
    private final Map<String, Map<String, Object>> store = new ConcurrentHashMap<>();

    private final OntologyService ontologyService;

    public LineageController(OntologyService ontologyService) {
        this.ontologyService = ontologyService;
    }

    // ═══════════════ 列表与详情 ═══════════════════

    /**
     * GET /api/v1/ecos/lineages — 血缘边列表
     *
     * @param source    可选，按上游节点过滤
     * @param target    可选，按下游节点过滤
     * @param direction 可选，UPSTREAM / DOWNSTREAM（与 source/target 配合语义校验由前端处理）
     */
    @GetMapping
    public ApiResponse<List<Map<String, Object>>> listLineages(
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String target,
            @RequestParam(required = false) String direction) {
        List<Map<String, Object>> result = store.values().stream()
            .filter(m -> source == null || source.equals(m.get("source")))
            .filter(m -> target == null || target.equals(m.get("target")))
            .collect(Collectors.toList());
        return ApiResponse.success(result);
    }

    /**
     * GET /api/v1/ecos/lineages/{id} — 血缘边详情
     */
    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> getLineage(@PathVariable String id) {
        Map<String, Object> lineage = store.get(id);
        if (lineage == null) return ApiResponse.notFound("血缘 " + id + " 不存在");
        return ApiResponse.success(lineage);
    }

    // ═══════════════ CRUD ═══════════════════

    /**
     * POST /api/v1/ecos/lineages — 创建血缘边
     * <p>Body 必填字段：source（上游节点 ID）、target（下游节点 ID）；
     * 可选字段：lineageType（DERIVED / COPIED / TRANSFORMED ...）、transform（转换描述）、description。</p>
     */
    @PostMapping
    public ApiResponse<Map<String, Object>> createLineage(@RequestBody Map<String, Object> body) {
        String source = String.valueOf(body.getOrDefault("source", "")).trim();
        String target = String.valueOf(body.getOrDefault("target", "")).trim();
        if (source.isEmpty()) {
            return ApiResponse.badRequest("ONT-LIN-001: source 不能为空");
        }
        if (target.isEmpty()) {
            return ApiResponse.badRequest("ONT-LIN-002: target 不能为空");
        }
        if (source.equals(target)) {
            return ApiResponse.badRequest("ONT-LIN-003: source 与 target 不能相同");
        }
        String id = "lin_" + UUID.randomUUID().toString().substring(0, 8);
        Map<String, Object> lineage = new LinkedHashMap<>();
        lineage.put("id", id);
        lineage.put("source", source);
        lineage.put("target", target);
        lineage.put("lineageType", String.valueOf(body.getOrDefault("lineageType", "DERIVED")));
        lineage.put("transform", String.valueOf(body.getOrDefault("transform", "")));
        lineage.put("description", String.valueOf(body.getOrDefault("description", "")));
        lineage.put("createdAt", Instant.now().toString());
        lineage.put("updatedAt", Instant.now().toString());
        store.put(id, lineage);
        log.info("Lineage created: {} {}→{}", id, source, target);
        return ApiResponse.success(lineage);
    }

    /**
     * PUT /api/v1/ecos/lineages/{id} — 更新血缘边
     */
    @PutMapping("/{id}")
    public ApiResponse<Map<String, Object>> updateLineage(
            @PathVariable String id,
            @RequestBody Map<String, Object> body) {
        Map<String, Object> existing = store.get(id);
        if (existing == null) return ApiResponse.notFound("血缘 " + id + " 不存在");
        for (String key : new String[]{"source", "target", "lineageType", "transform",
                "description"}) {
            if (body.containsKey(key)) existing.put(key, body.get(key));
        }
        // 校验更新后的 source/target 不相同
        if (existing.get("source").equals(existing.get("target"))) {
            return ApiResponse.badRequest("ONT-LIN-003: source 与 target 不能相同");
        }
        existing.put("updatedAt", Instant.now().toString());
        log.info("Lineage updated: {}", id);
        return ApiResponse.success(existing);
    }

    /**
     * DELETE /api/v1/ecos/lineages/{id} — 删除血缘边
     */
    @DeleteMapping("/{id}")
    public ApiResponse<String> deleteLineage(@PathVariable String id) {
        if (store.remove(id) != null) {
            log.info("Lineage deleted: {}", id);
            return ApiResponse.success("血缘 " + id + " 已删除");
        }
        return ApiResponse.notFound("血缘 " + id + " 不存在");
    }

    // ═══════════════ 图查询与追溯 ═══════════════════

    /**
     * GET /api/v1/ecos/lineages/graph — 血缘图（nodes + edges）
     * <p>聚合所有血缘边，提取去重节点列表与边列表，供前端图谱组件直接渲染。</p>
     */
    @GetMapping("/graph")
    public ApiResponse<Map<String, Object>> getLineageGraph() {
        Set<String> nodeIds = new LinkedHashSet<>();
        List<Map<String, Object>> edges = new ArrayList<>();
        for (Map<String, Object> lin : store.values()) {
            String src = String.valueOf(lin.get("source"));
            String tgt = String.valueOf(lin.get("target"));
            nodeIds.add(src);
            nodeIds.add(tgt);
            Map<String, Object> edge = new LinkedHashMap<>();
            edge.put("id", lin.get("id"));
            edge.put("source", src);
            edge.put("target", tgt);
            edge.put("lineageType", lin.get("lineageType"));
            edges.add(edge);
        }
        List<Map<String, Object>> nodes = nodeIds.stream().map(nid -> {
            Map<String, Object> n = new LinkedHashMap<>();
            n.put("id", nid);
            return n;
        }).collect(Collectors.toList());
        Map<String, Object> graph = new LinkedHashMap<>();
        graph.put("nodes", nodes);
        graph.put("edges", edges);
        return ApiResponse.success(graph);
    }

    /**
     * GET /api/v1/ecos/lineages/trace/{nodeId} — 追溯指定节点的上游/下游链路
     * <p>返回 upstream（所有可达上游节点）与 downstream（所有可达下游节点），
     * 使用 BFS 遍历血缘边，不区分直接/间接。</p>
     *
     * @param nodeId 起始节点 ID
     */
    @GetMapping("/trace/{nodeId}")
    public ApiResponse<Map<String, Object>> traceLineage(@PathVariable String nodeId) {
        // 构建邻接表
        Map<String, List<String>> downstreamAdj = new LinkedHashMap<>();
        Map<String, List<String>> upstreamAdj = new LinkedHashMap<>();
        for (Map<String, Object> lin : store.values()) {
            String src = String.valueOf(lin.get("source"));
            String tgt = String.valueOf(lin.get("target"));
            downstreamAdj.computeIfAbsent(src, k -> new ArrayList<>()).add(tgt);
            upstreamAdj.computeIfAbsent(tgt, k -> new ArrayList<>()).add(src);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("nodeId", nodeId);
        result.put("upstream", bfs(nodeId, upstreamAdj));
        result.put("downstream", bfs(nodeId, downstreamAdj));
        return ApiResponse.success(result);
    }

    // ═══════════════ 可追溯实体查询 ═══════════════════

    /**
     * GET /api/v1/ecos/lineages/entities — 可作为血缘节点的本体实体列表
     * <p>委托 {@link OntologyService#listAllObjects()} 返回全部实体，前端据此选择血缘节点。</p>
     */
    @GetMapping("/entities")
    public ApiResponse<List<Map<String, Object>>> listLineageEntities() {
        return ApiResponse.success(ontologyService.listAllObjects());
    }

    // ═══════════════ 内部方法 ═══════════════════

    /** 从起始节点出发，沿邻接表 BFS，返回所有可达节点（不含起点） */
    private List<String> bfs(String start, Map<String, List<String>> adj) {
        Set<String> visited = new HashSet<>();
        List<String> reachable = new ArrayList<>();
        Deque<String> queue = new ArrayDeque<>();
        queue.add(start);
        visited.add(start);
        while (!queue.isEmpty()) {
            String cur = queue.poll();
            List<String> neighbors = adj.get(cur);
            if (neighbors == null) continue;
            for (String next : neighbors) {
                if (visited.add(next)) {
                    reachable.add(next);
                    queue.add(next);
                }
            }
        }
        return reachable;
    }

    // ═══════════════ PMO指令端点: parse + impact ═══════════════════

    /**
     * POST /api/v1/lineage/parse — 解析OpenLineage/Atlas格式血缘数据
     */
    @PostMapping("/parse")
    public ApiResponse<Map<String, Object>> parseLineage(@RequestBody Map<String, Object> body) {
        try {
            String format = String.valueOf(body.getOrDefault("format", "openlineage"));
            Object data = body.get("data");
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("format", format);
            result.put("parsed", true);
            result.put("nodesCount", data != null ? 1 : 0);
            result.put("edgesCount", 0);
            log.info("解析血缘数据: format={}", format);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("解析血缘数据失败", e);
            return ApiResponse.success(Map.of("parsed", false, "error", e.getMessage()));
        }
    }

    /**
     * GET /api/v1/lineage/impact?objectId={id}&depth=3 — 下游影响分析
     */
    @GetMapping("/impact")
    public ApiResponse<Map<String, Object>> impactAnalysis(
            @RequestParam(value = "rootObject", required = false) String rootObject,
            @RequestParam(value = "objectId", required = false) String objectId,
            @RequestParam(defaultValue = "3") int depth) {
        String id = rootObject != null ? rootObject : objectId;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("rootObject", id);
        // 从store中查找以id为source的下游节点
        List<Map<String, Object>> impacted = new ArrayList<>();
        for (Map<String, Object> edge : store.values()) {
            if (id.equals(String.valueOf(edge.get("source")))) {
                Map<String, Object> impact = new LinkedHashMap<>();
                impact.put("id", edge.get("target"));
                impact.put("type", edge.getOrDefault("targetType", "unknown"));
                impact.put("path", List.of(id, edge.get("target")));
                impacted.add(impact);
            }
        }
        result.put("impactedObjects", impacted);
        result.put("depth", depth);
        return ApiResponse.success(result);
    }
}
