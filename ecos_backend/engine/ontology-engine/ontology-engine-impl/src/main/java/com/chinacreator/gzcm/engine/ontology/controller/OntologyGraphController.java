package com.chinacreator.gzcm.engine.ontology.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.service.IGraphService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/engine/ontology/graph")
public class OntologyGraphController {

    private static final Logger log = LoggerFactory.getLogger(OntologyGraphController.class);

    private final IGraphService graphService;

    public OntologyGraphController(IGraphService graphService) {
        this.graphService = graphService;
    }

    @GetMapping("/{ontologyId}")
    public ApiResponse<Map<String, Object>> getOntologyGraph(@PathVariable String ontologyId) {
        try {
            Map<String, Object> subgraph = graphService.getSubgraph(ontologyId);
            return ApiResponse.success(subgraph);
        } catch (Exception e) {
            log.warn("Graph query failed for ontology {}: {}", ontologyId, e.getMessage());
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("nodes", Collections.emptyList());
            empty.put("edges", Collections.emptyList());
            empty.put("message", "Graph service unavailable: " + e.getMessage());
            return ApiResponse.success(empty);
        }
    }

    @GetMapping("/full")
    public ApiResponse<Map<String, Object>> getFullGraph() {
        try {
            List<Map<String, Object>> results = graphService.query("MATCH (n) RETURN n LIMIT 100", Map.of());
            Map<String, Object> graph = new LinkedHashMap<>();
            graph.put("nodes", results);
            graph.put("edges", Collections.emptyList());
            return ApiResponse.success(graph);
        } catch (Exception e) {
            log.warn("Full graph query failed: {}", e.getMessage());
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("nodes", Collections.emptyList());
            empty.put("edges", Collections.emptyList());
            empty.put("message", "Graph service unavailable: " + e.getMessage());
            return ApiResponse.success(empty);
        }
    }

    @GetMapping("/trace/{nodeId}")
    public ApiResponse<Map<String, Object>> traceNode(@PathVariable String nodeId) {
        try {
            Map<String, Object> subgraph = graphService.getSubgraph(nodeId);
            return ApiResponse.success(subgraph);
        } catch (Exception e) {
            log.warn("Node trace failed for {}: {}", nodeId, e.getMessage());
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("nodes", Collections.emptyList());
            empty.put("edges", Collections.emptyList());
            empty.put("tracePath", Collections.emptyList());
            empty.put("message", "Graph service unavailable: " + e.getMessage());
            return ApiResponse.success(empty);
        }
    }
}
