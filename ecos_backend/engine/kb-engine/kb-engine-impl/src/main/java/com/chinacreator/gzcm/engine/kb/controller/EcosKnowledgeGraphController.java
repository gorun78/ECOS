package com.chinacreator.gzcm.engine.kb.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.kb.EcosKnowledgeGraphService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/ecos/knowledge-graph")
public class EcosKnowledgeGraphController {

    private static final Logger log = LoggerFactory.getLogger(EcosKnowledgeGraphController.class);

    @Autowired
    private EcosKnowledgeGraphService ecosKgService;

    @GetMapping
    public ApiResponse<Map<String, Object>> getGraph() {
        try {
            return ApiResponse.success(ecosKgService.getGraphSnapshot());
        } catch (Exception e) {
            log.error("Knowledge graph query failed", e);
            return ApiResponse.internalError("Knowledge graph query failed: " + e.getMessage());
        }
    }

    @PostMapping("/sync")
    public ApiResponse<Map<String, Object>> syncToNeo4j() {
        try {
            return ApiResponse.success(ecosKgService.syncToNeo4j());
        } catch (Exception e) {
            log.error("Knowledge graph sync failed", e);
            return ApiResponse.internalError("Sync failed: " + e.getMessage());
        }
    }
}