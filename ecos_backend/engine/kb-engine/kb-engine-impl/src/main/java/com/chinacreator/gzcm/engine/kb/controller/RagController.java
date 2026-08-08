package com.chinacreator.gzcm.engine.kb.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.kb.service.KnowledgeRetrievalServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * RAG检索 + Neo4j健康检查控制器。
 *
 * @author ECOS KB Engine Team
 * @since 2026-08-08
 */
@RestController
@RequestMapping("/api/v1/kb")
public class RagController {

    private static final Logger log = LoggerFactory.getLogger(RagController.class);
    private final KnowledgeRetrievalServiceImpl retrievalService;

    public RagController(KnowledgeRetrievalServiceImpl retrievalService) {
        this.retrievalService = retrievalService;
    }

    /**
     * RAG检索 — 向量相似度Top-K文档检索。
     */
    @PostMapping("/rag")
    public ApiResponse<Map<String, Object>> ragQuery(@RequestBody Map<String, Object> request) {
        try {
            String query = (String) request.get("query");
            int topK = request.containsKey("topK") ? ((Number) request.get("topK")).intValue() : 5;
            Map<String, Object> result = retrievalService.ragQuery(query, topK);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("RAG检索失败: {}", e.getMessage(), e);
            return ApiResponse.badRequest("RAG检索失败: " + e.getMessage());
        }
    }

    /**
     * Neo4j图数据库健康检查。
     */
    @GetMapping("/graph/health")
    public ApiResponse<Map<String, Object>> graphHealth() {
        try {
            Map<String, Object> result = retrievalService.graphHealth();
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("Neo4j健康检查失败: {}", e.getMessage());
            Map<String, Object> err = Map.of("healthy", false, "error", e.getMessage());
            return ApiResponse.badRequest(err.toString());
        }
    }
}
