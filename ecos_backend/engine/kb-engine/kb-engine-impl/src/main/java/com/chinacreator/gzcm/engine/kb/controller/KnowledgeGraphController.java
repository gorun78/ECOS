package com.chinacreator.gzcm.engine.kb.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.kb.KnowledgeGraphService;
import com.chinacreator.gzcm.engine.kb.model.KnowledgeNode;
import com.chinacreator.gzcm.engine.kb.model.KnowledgeEdge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeGraphController {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeGraphController.class);

    @Autowired
    private KnowledgeGraphService kgService;

    @GetMapping("/graph")
    public ApiResponse<Map<String, Object>> getGraph(@RequestParam(required = false) String domain) {
        return ApiResponse.success(kgService.getGraph(domain));
    }

    @GetMapping("/nodes/{id}")
    public ApiResponse<Map<String, Object>> getNode(@PathVariable String id) {
        Map<String, Object> detail = kgService.getNodeDetail(id);
        if (detail == null) return ApiResponse.notFound("Node " + id + " not found");
        return ApiResponse.success(detail);
    }

    @GetMapping("/search")
    public ApiResponse<List<KnowledgeNode>> search(@RequestParam String q) {
        return ApiResponse.success(kgService.search(q));
    }

    @GetMapping("/path")
    public ApiResponse<Map<String, Object>> getShortestPath(
            @RequestParam("s") String sourceNodeId,
            @RequestParam("t") String targetNodeId) {
        if (sourceNodeId == null || sourceNodeId.isEmpty()) {
            return ApiResponse.badRequest("Missing param s (sourceNodeId)");
        }
        if (targetNodeId == null || targetNodeId.isEmpty()) {
            return ApiResponse.badRequest("Missing param t (targetNodeId)");
        }
        return ApiResponse.success(kgService.getShortestPath(sourceNodeId, targetNodeId));
    }

    @GetMapping("/neighbors/{id}")
    public ApiResponse<Map<String, Object>> getNeighbors(
            @PathVariable String id,
            @RequestParam(defaultValue = "1") int d) {
        if (d < 1) d = 1;
        if (d > 5) d = 5;
        return ApiResponse.success(kgService.getNeighbors(id, d));
    }

    @PostMapping("/nodes")
    public ApiResponse<KnowledgeNode> createNode(@RequestBody Map<String, Object> body) {
        String label = (String) body.getOrDefault("label", "");
        String nodeType = (String) body.getOrDefault("nodeType", "Concept");
        String description = (String) body.getOrDefault("description", "");
        String propertiesJson = body.containsKey("properties") ? body.get("properties").toString() : "{}";
        KnowledgeNode node = kgService.createNode(label, nodeType, description, propertiesJson);
        log.info("Knowledge node created: {} [{}]", node.getId(), node.getLabel());
        return ApiResponse.success(node);
    }

    @PostMapping("/edges")
    public ApiResponse<KnowledgeEdge> createEdge(@RequestBody Map<String, Object> body) {
        String sourceNodeId = (String) body.get("sourceNodeId");
        String targetNodeId = (String) body.get("targetNodeId");
        String relationship = (String) body.getOrDefault("relationship", "related_to");
        Double weight = body.containsKey("weight") ? ((Number) body.get("weight")).doubleValue() : 1.0;
        KnowledgeEdge edge = kgService.createEdge(sourceNodeId, targetNodeId, relationship, weight);
        log.info("Knowledge edge created: {}", edge.getId());
        return ApiResponse.success(edge);
    }

    @GetMapping("/source")
    public ApiResponse<Map<String, Object>> getDataSource() {
        Map<String, Object> info = new LinkedHashMap<>();
        String source = kgService.getDataSource();
        info.put("source", source);
        info.put("status", source.startsWith("PostgreSQL") ? "ok" : "degraded");
        return ApiResponse.success(info);
    }
}