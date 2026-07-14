package com.chinacreator.gzcm.dccheng.knowledgegraph.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.runtime.core.agent.mesh.knowledge.KnowledgeGraphService;
import com.chinacreator.gzcm.runtime.core.agent.mesh.knowledge.entity.KnowledgeNode;
import com.chinacreator.gzcm.runtime.core.agent.mesh.knowledge.entity.KnowledgeEdge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 知识图谱 Controller — 节点/边 CRUD + Cypher 图查询。
 *
 * <h3>8 个端点：</h3>
 * <ol>
 *   <li>GET    /api/knowledge/graph              — 全图节点+边（?domain= 过滤，优先 Neo4j）</li>
 *   <li>GET    /api/knowledge/nodes/{id}          — 节点详情+关联边</li>
 *   <li>GET    /api/knowledge/search?q=            — 语义搜索</li>
 *   <li>GET    /api/knowledge/path?s=&t=           — 两节点最短路径（Neo4j 专有）</li>
 *   <li>GET    /api/knowledge/neighbors/{id}?d=    — N 度邻居查询（Neo4j 专有）</li>
 *   <li>POST   /api/knowledge/nodes               — 创建节点</li>
 *   <li>POST   /api/knowledge/edges               — 创建边</li>
 *   <li>GET    /api/knowledge/source              — 查询数据来源（Neo4j / PG）</li>
 * </ol>
 */
@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeGraphController {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeGraphController.class);

    @Autowired(required = false)
    private KnowledgeGraphService kgService;

    // ════════════════════════════════════════════════════
    // 1. GET /api/knowledge/graph — 全图（Neo4j 优先，PG 降级）
    // ════════════════════════════════════════════════════
    @GetMapping("/graph")
    public ApiResponse<Map<String, Object>> getGraph(
            @RequestParam(required = false) String domain) {
        if (kgService == null) return ApiResponse.internalError("KnowledgeGraphService 未就绪");
        return ApiResponse.success(kgService.getGraph(domain));
    }

    // ════════════════════════════════════════════════════
    // 2. GET /api/knowledge/nodes/{id} — 节点详情
    // ════════════════════════════════════════════════════
    @GetMapping("/nodes/{id}")
    public ApiResponse<Map<String, Object>> getNode(@PathVariable String id) {
        if (kgService == null) return ApiResponse.internalError("KnowledgeGraphService 未就绪");
        Map<String, Object> detail = kgService.getNodeDetail(id);
        if (detail == null) return ApiResponse.notFound("节点 " + id + " 不存在");
        return ApiResponse.success(detail);
    }

    // ════════════════════════════════════════════════════
    // 3. GET /api/knowledge/search?q= — 搜索
    // ════════════════════════════════════════════════════
    @GetMapping("/search")
    public ApiResponse<List<KnowledgeNode>> search(@RequestParam String q) {
        if (kgService == null) return ApiResponse.internalError("KnowledgeGraphService 未就绪");
        return ApiResponse.success(kgService.search(q));
    }

    // ════════════════════════════════════════════════════
    // 4. GET /api/knowledge/path — 最短路径（Neo4j 专有）
    // ════════════════════════════════════════════════════
    @GetMapping("/path")
    public ApiResponse<Map<String, Object>> getShortestPath(
            @RequestParam("s") String sourceNodeId,
            @RequestParam("t") String targetNodeId) {
        if (kgService == null) return ApiResponse.internalError("KnowledgeGraphService 未就绪");
        if (sourceNodeId == null || sourceNodeId.isEmpty()) {
            return ApiResponse.badRequest("缺少参数 s (sourceNodeId)");
        }
        if (targetNodeId == null || targetNodeId.isEmpty()) {
            return ApiResponse.badRequest("缺少参数 t (targetNodeId)");
        }
        return ApiResponse.success(kgService.getShortestPath(sourceNodeId, targetNodeId));
    }

    // ════════════════════════════════════════════════════
    // 5. GET /api/knowledge/neighbors/{id} — N 度邻居（Neo4j 专有）
    // ════════════════════════════════════════════════════
    @GetMapping("/neighbors/{id}")
    public ApiResponse<Map<String, Object>> getNeighbors(
            @PathVariable String id,
            @RequestParam(defaultValue = "1") int d) {
        if (kgService == null) return ApiResponse.internalError("KnowledgeGraphService 未就绪");
        if (d < 1) d = 1;
        if (d > 5) d = 5;
        return ApiResponse.success(kgService.getNeighbors(id, d));
    }

    // ════════════════════════════════════════════════════
    // 6. POST /api/knowledge/nodes — 创建节点
    // ════════════════════════════════════════════════════
    @PostMapping("/nodes")
    public ApiResponse<KnowledgeNode> createNode(@RequestBody Map<String, Object> body) {
        if (kgService == null) return ApiResponse.internalError("KnowledgeGraphService 未就绪");
        String label = (String) body.getOrDefault("label", "");
        String nodeType = (String) body.getOrDefault("nodeType", "Concept");
        String description = (String) body.getOrDefault("description", "");
        String propertiesJson = body.containsKey("properties") ?
                body.get("properties").toString() : "{}";
        KnowledgeNode node = kgService.createNode(label, nodeType, description, propertiesJson);
        log.info("Knowledge node created: {} [{}]", node.getId(), node.getLabel());
        return ApiResponse.success(node);
    }

    // ════════════════════════════════════════════════════
    // 7. POST /api/knowledge/edges — 创建边
    // ════════════════════════════════════════════════════
    @PostMapping("/edges")
    public ApiResponse<KnowledgeEdge> createEdge(@RequestBody Map<String, Object> body) {
        if (kgService == null) return ApiResponse.internalError("KnowledgeGraphService 未就绪");
        String sourceNodeId = (String) body.get("sourceNodeId");
        String targetNodeId = (String) body.get("targetNodeId");
        String relationship = (String) body.getOrDefault("relationship", "related_to");
        Double weight = body.containsKey("weight") ?
                ((Number) body.get("weight")).doubleValue() : 1.0;
        KnowledgeEdge edge = kgService.createEdge(sourceNodeId, targetNodeId, relationship, weight);
        log.info("Knowledge edge created: {} [{}]-[{}]->[{}]",
                edge.getId(), sourceNodeId, relationship, targetNodeId);
        return ApiResponse.success(edge);
    }

    // ════════════════════════════════════════════════════
    // 8. GET /api/knowledge/source — 数据来源诊断
    // ════════════════════════════════════════════════════
    @GetMapping("/source")
    public ApiResponse<Map<String, Object>> getDataSource() {
        Map<String, Object> info = new LinkedHashMap<>();
        if (kgService == null) {
            info.put("status", "unavailable");
            info.put("reason", "KnowledgeGraphService 未就绪");
            return ApiResponse.success(info);
        }
        // 尝试获取 Neo4j 状态
        try {
            var graph = kgService.getGraph(null);
            @SuppressWarnings("unchecked")
            List<?> nodes = (List<?>) graph.get("nodes");
            info.put("source", "Neo4j");
            info.put("nodes", nodes != null ? nodes.size() : 0);
            info.put("status", "ok");
        } catch (Exception e) {
            info.put("source", "PostgreSQL (JDBC fallback)");
            info.put("status", "degraded");
            info.put("reason", e.getMessage());
        }
        return ApiResponse.success(info);
    }
}
