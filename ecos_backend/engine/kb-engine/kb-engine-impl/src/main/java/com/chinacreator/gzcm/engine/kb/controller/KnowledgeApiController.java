package com.chinacreator.gzcm.engine.kb.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.kb.KnowledgeRetrievalService;
import com.chinacreator.gzcm.engine.kb.model.dto.RagSearchRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 知识 RAG + 综合查询 Controller（PMO-B T1 增补：检索侧打通 categoryIds 过滤）。
 *
 * <p>回归保证：未带 {@code categoryIds} 字段的旧客户端 → 行为与历史版本一致（全量检索）。</p>
 */
@RestController
@RequestMapping("/api/v1/knowledge")
public class KnowledgeApiController {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeApiController.class);

    /** RAG topK 默认值（与历史 KnowledgeApiController.rag 一致） */
    private static final int DEFAULT_TOP_K = 5;
    /** RAG threshold 默认值（与历史 KnowledgeApiController.rag 一致） */
    private static final double DEFAULT_THRESHOLD = 0.7;

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

    /**
     * 全量知识查询（不带类目过滤）。
     *
     * <p>历史签名 `body.get("query")` → 现升级为强类型 {@link RagSearchRequest}（兼容字段）。
     * 检索侧同样支持可选 {@code categoryIds} / {@code tags} 过滤。</p>
     */
    @PostMapping("/query")
    public ApiResponse<List<Object>> query(@RequestBody(required = false) RagSearchRequest req) {
        String queryText = req == null ? "" : (req.getQuery() == null ? "" : req.getQuery());
        log.info("Knowledge query: {}", queryText);
        return ApiResponse.success(knowledgeRetrievalService.query(queryText));
    }

    /**
     * RAG 检索（PMO-B T1：支持 knowledge-nav 目录 / 标签过滤）。
     *
     * <p>兼容历史客户端：仅送 {@code query / topK / threshold} 仍可用（categoryIds 缺省 null →
     * 全量检索）。响应体追加 {@code categoryIds / tags} 字段让前端回放"刚选择了哪些导航节点"。</p>
     */
    @PostMapping("/rag")
    public ApiResponse<Map<String, Object>> rag(@RequestBody(required = false) RagSearchRequest req) {
        String query = req == null ? "" : (req.getQuery() == null ? "" : req.getQuery());
        int topK = req != null && req.getTopK() != null ? req.getTopK() : DEFAULT_TOP_K;
        double threshold = req != null && req.getThreshold() != null ? req.getThreshold() : DEFAULT_THRESHOLD;
        List<String> categoryIds = req == null ? null : req.getCategoryIds();
        List<String> tags = req == null ? null : req.getTags();
        return ApiResponse.success(knowledgeRetrievalService.ragQuery(query, topK, threshold, categoryIds, tags));
    }
}
