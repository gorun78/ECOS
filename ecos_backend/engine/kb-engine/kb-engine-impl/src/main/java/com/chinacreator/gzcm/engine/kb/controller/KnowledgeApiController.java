package com.chinacreator.gzcm.engine.kb.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.kb.KnowledgeRetrievalService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/knowledge")
public class KnowledgeApiController {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeApiController.class);

    @Autowired
    private KnowledgeRetrievalService knowledgeRetrievalService;

    @GetMapping("/index-status")
    public ApiResponse<Map<String, Object>> getIndexStatus() {
        return ApiResponse.success(knowledgeRetrievalService.getIndexStatus());
    }

    @PostMapping("/sync")
    public ApiResponse<Map<String, Object>> triggerSync() {
        log.info("Knowledge full sync triggered");
        knowledgeRetrievalService.triggerSync();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "queued");
        return ApiResponse.success("sync queued", result);
    }

    @PostMapping("/query")
    public ApiResponse<List<Object>> query(@RequestBody Map<String, Object> body) {
        String query = body == null ? "" : (String) body.getOrDefault("query", "");
        log.info("Knowledge query: {}", query);
        return ApiResponse.success(knowledgeRetrievalService.query(query));
    }

    @PostMapping("/rag")
    public ApiResponse<Map<String, Object>> rag(@RequestBody Map<String, Object> body) {
        String query = body == null ? "" : (String) body.getOrDefault("query", "");
        int topK = body != null && body.get("topK") != null
                ? ((Number) body.get("topK")).intValue() : 5;
        double threshold = body != null && body.get("threshold") != null
                ? ((Number) body.get("threshold")).doubleValue() : 0.7;
        return ApiResponse.success(knowledgeRetrievalService.ragQuery(query, topK, threshold));
    }
}