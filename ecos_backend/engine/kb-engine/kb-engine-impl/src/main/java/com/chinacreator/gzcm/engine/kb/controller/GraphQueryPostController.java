package com.chinacreator.gzcm.engine.kb.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.kb.dto.GraphNodeVO;
import com.chinacreator.gzcm.engine.kb.dto.GraphPathQuery;
import com.chinacreator.gzcm.engine.kb.dto.GraphSearchQuery;
import com.chinacreator.gzcm.engine.kb.model.KnowledgeNode;
import com.chinacreator.gzcm.engine.kb.repository.KnowledgeNodeMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 知识图谱查询 REST API（POST 版）— 前缀 /api/v1/knowledge/graph
 *
 * <p>PMO-51 T2：为知识工作台 GraphExplorerTab 提供图谱"按 key 搜"+ "双点查询"两个
 * 大查询变体（对前端 {@code knowledgeApi.graphSearch} / {@code graphPath} 对齐）。
 * 端点采用 POST，请求体强类型 {@link GraphSearchQuery} / {@link GraphPathQuery}，
 * 避免长 URL（PG 长参数）+ 与已有 GET {@code /api/v1/knowledge/graph/search}
 * （query only）双通道共存。</p>
 *
 * <p>路径池：</p>
 * <ul>
 *   <li>POST /api/v1/knowledge/graph/search — 按 keyword + nodeType 双过滤（top N）</li>
 *   <li>POST /api/v1/knowledge/graph/path    — 双端点 BFS 短时路径（不存在 → 空 list）</li>
 * </ul>
 *
 * @group GRAPH
 */
@RestController
@RequestMapping("/api/v1/knowledge/graph")
public class GraphQueryPostController {

    private static final Logger log = LoggerFactory.getLogger(GraphQueryPostController.class);

    /** 单次 search 最大返回节点数（防止大结果集） */
    static final int MAX_LIMIT = 100;
    /** 单次 path 加载边数上限（防止大图 OOM） */
    static final int MAX_EDGES = 8000;
    /** path 最大深度（防止死循环 + 性能） */
    static final int MAX_DEPTH = 16;

    private final KnowledgeNodeMapper nodeMapper;
    private final JdbcTemplate jdbcTemplate;

    public GraphQueryPostController(KnowledgeNodeMapper nodeMapper,
                                    JdbcTemplate jdbcTemplate) {
        this.nodeMapper = nodeMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    // ── /search ──────────────────────────────────────────────────────

    /**
     * POST /api/v1/knowledge/graph/search — 按 keyword 全字段模糊 + nodeType 过滤，返回 top N。
     *
     * <p>命中 top N 节点 + 邻接摘要（每个节点 outgoing/incoming 边数）。</p>
     */
    @PostMapping("/search")
    public ApiResponse<List<GraphNodeVO>> search(@RequestBody GraphSearchQuery query) {
        try {
            if (query == null) {
                return ApiResponse.badRequest("请求体不能为空");
            }
            String keyword = query.getKeyword();
            if (keyword == null || keyword.isBlank()) {
                return ApiResponse.badRequest("keyword 不能为空");
            }
            String nodeType = query.getNodeType();
            int limit = query.getLimit() <= 0 ? 20 : Math.min(query.getLimit(), MAX_LIMIT);

            List<KnowledgeNode> nodes = nodeMapper.searchByLabelPattern("%" + keyword + "%");
            if (nodes == null || nodes.isEmpty()) {
                return ApiResponse.success(new ArrayList<>());
            }
            // type 过滤
            List<KnowledgeNode> typed = new ArrayList<>();
            for (KnowledgeNode n : nodes) {
                if (nodeType == null || nodeType.isBlank()
                        || nodeType.equalsIgnoreCase(n.getNodeType())) {
                    typed.add(n);
                    if (typed.size() >= limit) {
                        break;
                    }
                }
            }
            // 邻接计数（limit 条一次性 IN 批量查，避免 N+1）
            Map<String, Integer> outDeg = new HashMap<>();
            Map<String, Integer> inDeg = new HashMap<>();
            countDegrees(typed, outDeg, inDeg);

            List<GraphNodeVO> vos = new ArrayList<>(typed.size());
            for (KnowledgeNode n : typed) {
                vos.add(new GraphNodeVO(
                        n.getId(),
                        n.getLabel(),
                        n.getNodeType(),
                        n.getDescription(),
                        n.getDomain(),
                        orZero(outDeg.get(n.getId())),
                        orZero(inDeg.get(n.getId()))));
            }
            log.debug("GraphSearchPost: keyword='{}', nodeType='{}', limit={}, hits={}",
                    keyword, nodeType, limit, vos.size());
            return ApiResponse.success(vos);
        } catch (DataAccessException e) {
            log.error("Failed to execute graph search: {}", e.getMessage(), e);
            return ApiResponse.internalError("图谱搜索失败: " + e.getMessage());
        }
    }

    // ── /path ────────────────────────────────────────────────────────

    /**
     * POST /api/v1/knowledge/graph/path — 双端点双向 BFS 短时路径。
     *
     * <p>maxDepth 上限 {@value #MAX_DEPTH}（防止大图 O(E) 撑爆）。
     * 路径不存在时按要求返回空 list（不抛 404，对齐契约 §4.8）。</p>
     */
    @PostMapping("/path")
    public ApiResponse<List<String>> path(@RequestBody GraphPathQuery query) {
        try {
            if (query == null) {
                return ApiResponse.badRequest("请求体不能为空");
            }
            String src = query.getSource();
            String tgt = query.getTarget();
            if (src == null || src.isBlank() || tgt == null || tgt.isBlank()) {
                return ApiResponse.badRequest("source 与 target 不能为空");
            }
            int maxDepth = query.getMaxDepth() <= 0 ? 8 : Math.min(query.getMaxDepth(), MAX_DEPTH);

            Map<String, List<String>> adj = loadAdjacency();
            List<String> result = bfsPath(adj, src, tgt, maxDepth);
            log.debug("GraphPathPost: src='{}' tgt='{}' maxDepth={} hits={}",
                    src, tgt, maxDepth, result.size());
            return ApiResponse.success(result);
        } catch (DataAccessException e) {
            log.error("Failed to execute graph path: {}", e.getMessage(), e);
            return ApiResponse.internalError("图谱路径查询失败: " + e.getMessage());
        }
    }

    // ── 内部辅助（BFS 实现）─────────────────────────────────────────

    /**
     * 单向 BFS 求路径；不可达时返回空 list。
     *
     * @param adj      邻接表（双向，已合并 source→target 与 target→source）
     * @param src      起点
     * @param tgt      终点
     * @param maxDepth 最大层数（不含起点，含终点）
     * @return 路径节点 id 数组（src→...→tgt）；不可达返回 emptyList
     */
    static List<String> bfsPath(Map<String, List<String>> adj, String src, String tgt, int maxDepth) {
        if (src.equals(tgt)) {
            return List.of(src);
        }
        Map<String, String> parent = new HashMap<>();
        parent.put(src, null);
        Deque<String> queue = new ArrayDeque<>();
        queue.add(src);
        int depth = 0;
        while (!queue.isEmpty() && depth <= maxDepth) {
            int size = queue.size();
            for (int i = 0; i < size; i++) {
                String v = queue.poll();
                for (String w : adj.getOrDefault(v, List.of())) {
                    if (parent.containsKey(w)) {
                        continue;
                    }
                    parent.put(w, v);
                    if (w.equals(tgt)) {
                        return stitch(parent, w);
                    }
                    queue.add(w);
                }
            }
            depth++;
        }
        return new ArrayList<>();
    }

    /** 反向回溯 parent 链，拼出 src→...→tgt 的路径。 */
    private static List<String> stitch(Map<String, String> parent, String end) {
        List<String> path = new ArrayList<>();
        String cur = end;
        while (cur != null) {
            path.add(cur);
            cur = parent.get(cur);
        }
        java.util.Collections.reverse(path);
        return path;
    }

    /** 双向合并邻接表（含 source/target 双向），limit {@value #MAX_EDGES} 防 OOM。 */
    private Map<String, List<String>> loadAdjacency() {
        Map<String, List<String>> adj = new LinkedHashMap<>();
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT source_id, target_id FROM ecos_knowledge.graph_edge LIMIT " + MAX_EDGES);
            for (Map<String, Object> row : rows) {
                String s = row.get("source_id") == null ? null : String.valueOf(row.get("source_id"));
                String t = row.get("target_id") == null ? null : String.valueOf(row.get("target_id"));
                if (s == null || t == null) {
                    continue;
                }
                adj.computeIfAbsent(s, k -> new ArrayList<>()).add(t);
                adj.computeIfAbsent(t, k -> new ArrayList<>()).add(s);
            }
        } catch (Exception e) {
            log.warn("loadAdjacency failed: {}", e.getMessage());
        }
        return adj;
    }

    /**
     * 批量查邻接度（IN 查询一次带出）。
     * 节点 id 不在边的节点不命中（调用方按 0 处理）。
     */
    private void countDegrees(List<KnowledgeNode> nodes, Map<String, Integer> outDeg, Map<String, Integer> inDeg) {
        if (nodes.isEmpty()) {
            return;
        }
        try {
            String placeholders = placeholders(nodes.size());
            Object[] ids = nodes.stream().map(KnowledgeNode::getId).toArray();
            List<Map<String, Object>> outRows = jdbcTemplate.queryForList(
                    "SELECT source_id AS node, COUNT(*) AS cnt FROM ecos_knowledge.graph_edge " +
                    "WHERE source_id IN (" + placeholders + ") GROUP BY source_id", ids);
            for (Map<String, Object> row : outRows) {
                Object id = row.get("node");
                Object cnt = row.get("cnt");
                outDeg.put(Objects.toString(id, ""), ((Number) cnt).intValue());
            }
            List<Map<String, Object>> inRows = jdbcTemplate.queryForList(
                    "SELECT target_id AS node, COUNT(*) AS cnt FROM ecos_knowledge.graph_edge " +
                    "WHERE target_id IN (" + placeholders + ") GROUP BY target_id", ids);
            for (Map<String, Object> row : inRows) {
                Object id = row.get("node");
                Object cnt = row.get("cnt");
                inDeg.put(Objects.toString(id, ""), ((Number) cnt).intValue());
            }
        } catch (Exception e) {
            // 邻接计数失败不影响主查询
            log.warn("countDegrees fallback (degenerate): {}", e.getMessage());
        }
    }

    private static int orZero(Integer v) {
        return v == null ? 0 : v;
    }

    private static String placeholders(int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append('?');
        }
        return sb.toString();
    }
}
