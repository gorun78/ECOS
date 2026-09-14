package com.chinacreator.gzcm.engine.ontology.controller;

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
import com.chinacreator.gzcm.engine.ontology.dto.LineageParseResponse;
import com.chinacreator.gzcm.engine.ontology.dto.SqlLineageRequest;
import com.chinacreator.gzcm.engine.ontology.lineage.OntologyLineageService;
import com.chinacreator.gzcm.engine.ontology.model.LineageEvent;
import com.chinacreator.gzcm.engine.ontology.repository.LineageEventRepository;
import com.chinacreator.gzcm.engine.ontology.service.OntologyService;

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

    /** 内存存储：lineageId → 血缘边记录（CRUD 端点仍使用，兼容 B' 并行开发） */
    private final Map<String, Map<String, Object>> store = new ConcurrentHashMap<>();

    private final OntologyService ontologyService;

    /** 血缘事件持久化仓库（kb_lineage_event 表，parse 事件写入 PG） */
    private final LineageEventRepository lineageEventRepository;

    /** 血缘真实解析/多跳影响分析服务（PMO-52 T2：jsqlparser 字段级 + 多跳 BFS） */
    private final OntologyLineageService ontologyLineageService;

    public LineageController(OntologyService ontologyService,
                             LineageEventRepository lineageEventRepository,
                             OntologyLineageService ontologyLineageService) {
        this.ontologyService = ontologyService;
        this.lineageEventRepository = lineageEventRepository;
        this.ontologyLineageService = ontologyLineageService;
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
     * POST /api/v1/lineage/parse — 真实解析 SQL/OpenLineage/Atlas 血缘（PMO-52 T2）。
     *
     * <p>流程：
     * <ol>
     *   <li>强类型 {@link SqlLineageRequest} 反序列化（query/format/data/payload 均兼容）</li>
     *   <li>调 {@link OntologyLineageService#parse(SqlLineageRequest)} — 本地 JSqlParser
     *       字段级真实解析（非 echo；不 import data-engine-impl 类，符合架构铁律 2.1 依赖方向）</li>
     *   <li>解析结果序列化 nodes/edges JSON 持久化到 kb_lineage_event（PG，批次C 既有能力保留）</li>
     *   <li>边同步写入内存 store，供 /graph、/trace/{id}、/impact 继续可用（只增不改）</li>
     * </ol>
     */
    @PostMapping("/parse")
    public ApiResponse<LineageParseResponse> parseLineage(@RequestBody SqlLineageRequest body) {
        LineageParseResponse resp =
                ontologyLineageService.parse(body != null ? body : new SqlLineageRequest());
        List<Map<String, Object>> nodes = resp.getNodes() != null ? resp.getNodes() : new ArrayList<>();
        List<Map<String, Object>> edges = resp.getEdges() != null ? resp.getEdges() : new ArrayList<>();

        // 持久化到 kb_lineage_event（保留批次 C 插入逻辑，仅换为真实解析结果）
        String eventId = UUID.randomUUID().toString().replace("-", "");
        String query = body != null && body.getQuery() != null ? body.getQuery() : "";
        String format = resp.getFormat() != null ? resp.getFormat() : "openlineage";
        try {
            lineageEventRepository.insert(eventId, query, format, toNodesJson(nodes), toEdgesJson(edges));
        } catch (Exception e) {
            // 持久化失败不阻塞解析结果返回（kb_lineage_event 表未初始化等边缘场景），记录日志
            log.error("血缘事件持久化失败 eventId={}: {}", eventId, e.getMessage(), e);
        }

        // 写入内存 store（供 CRUD 端点 traceLineage / getLineageGraph / impactAnalysis 使用）
        for (Map<String, Object> edge : edges) {
            String src = String.valueOf(edge.get("source"));
            String tgt = String.valueOf(edge.get("target"));
            if (!src.isEmpty() && !"null".equals(src) && !tgt.isEmpty() && !"null".equals(tgt)) {
                String edgeId = "lin_parse_" + UUID.randomUUID().toString().substring(0, 8);
                Map<String, Object> lineage = new LinkedHashMap<>();
                lineage.put("id", edgeId);
                lineage.put("source", src);
                lineage.put("target", tgt);
                lineage.put("lineageType", String.valueOf(edge.getOrDefault("type", "DERIVED")));
                lineage.put("eventId", eventId);
                lineage.put("createdAt", Instant.now().toString());
                lineage.put("updatedAt", Instant.now().toString());
                store.put(edgeId, lineage);
            }
        }
        log.info("解析血缘真实数据并持久化: format={}, eventId={}, nodes={}, edges={}",
                format, eventId, nodes.size(), edges.size());
        return ApiResponse.success(resp);
    }

    /**
     * GET /api/v1/lineage/events — 查询历史血缘事件（从 PG 读取）
     *
     * @param format 可选，按格式过滤
     */
    @GetMapping("/events")
    public ApiResponse<List<Map<String, Object>>> listEvents(
            @RequestParam(required = false) String format) {
        List<LineageEvent> events = (format != null && !format.isEmpty())
                ? lineageEventRepository.findByFormat(format)
                : lineageEventRepository.findAll();
        List<Map<String, Object>> result = new ArrayList<>();
        for (LineageEvent e : events) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("eventId", e.getEventId());
            m.put("query", e.getQuery());
            m.put("format", e.getFormat());
            m.put("nodes", parseJsonArray(e.getNodesJson()));
            m.put("edges", parseJsonArray(e.getEdgesJson()));
            m.put("parseAt", e.getParseAt() != null ? e.getParseAt().toString() : "");
            result.add(m);
        }
        return ApiResponse.success(result);
    }

    // ═══════════════ parse 辅助方法 ═══════════════════

    /** 节点列表 → JSON 字符串 */
    private String toNodesJson(List<Map<String, Object>> nodes) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(nodes);
        } catch (Exception e) {
            return "[]";
        }
    }

    /** 边列表 → JSON 字符串 */
    private String toEdgesJson(List<Map<String, Object>> edges) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(edges);
        } catch (Exception e) {
            return "[]";
        }
    }

    /** JSON 字符串 → List<Map>（用于 GET /events 响应） */
    private List<Map<String, Object>> parseJsonArray(String json) {
        if (json == null || json.isBlank()) return new ArrayList<>();
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(json,
                    new com.fasterxml.jackson.core.type.TypeReference<>() {});
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    /**
     * GET /api/v1/lineage/impact — 下游多跳影响分析（PMO-52 T2）。
     *
     * <p>从 root 对象出发，基于内存 store 中的血缘边真实执行分层 BFS（depth 有效，
     * 缺省 3、上限 16），返回受影响对象列表（{id, path, hopCount, riskScore...}）。</p>
     *
     * @param rootObject 根节点 ID（优先）
     * @param objectId   兼容旧参数字段
     * @param startNode  兼容前端 fetchLineageImpact 传参字段
     * @param depth      最大跳数
     */
    @GetMapping("/impact")
    public ApiResponse<Map<String, Object>> impactAnalysis(
            @RequestParam(value = "rootObject", required = false) String rootObject,
            @RequestParam(value = "objectId", required = false) String objectId,
            @RequestParam(value = "startNode", required = false) String startNode,
            @RequestParam(defaultValue = "3") int depth) {
        String id = rootObject != null ? rootObject
                : (objectId != null ? objectId : startNode);

        // 从 store 构建边集合（source/target → source/target 字段）
        List<Map<String, Object>> edgeList = new ArrayList<>();
        for (Map<String, Object> lin : store.values()) {
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("source", lin.get("source"));
            e.put("target", lin.get("target"));
            edgeList.add(e);
        }

        // 真实多跳 BFS
        List<Map<String, Object>> impactedObjects =
                ontologyLineageService.impactAnalysis(id, edgeList, depth);

        // 前端兼容字段：impactedNodes（path/hopCount/riskScore/id/type）+ 总风险 + 严重级
        List<Map<String, Object>> impactedNodes = new ArrayList<>();
        for (Map<String, Object> imp : impactedObjects) {
            Map<String, Object> n = new LinkedHashMap<>(imp);
            n.putIfAbsent("path", List.of(id, String.valueOf(imp.get("id"))));
            impactedNodes.add(n);
        }
        int totalRisk = impactedNodes.stream()
                .mapToInt(n -> n.get("riskScore") instanceof Number v ? v.intValue() : 0)
                .sum();
        String severity = impactedNodes.isEmpty() ? "NONE"
                : (totalRisk >= 240 || impactedNodes.size() >= 8 ? "CRITICAL"
                : (totalRisk >= 90 || impactedNodes.size() >= 3 ? "HIGH" : "NORMAL"));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("rootObject", id);
        result.put("impactedObjects", impactedObjects); // 新契约（PMO）
        result.put("impactedNodes", impactedNodes);     // 兼容前端 LineageTab
        result.put("totalRisk", totalRisk);
        result.put("severity", severity);
        result.put("depth", depth);
        return ApiResponse.success(result);
    }
}
