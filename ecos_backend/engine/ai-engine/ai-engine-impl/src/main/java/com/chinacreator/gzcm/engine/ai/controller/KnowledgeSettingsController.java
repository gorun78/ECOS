package com.chinacreator.gzcm.engine.ai.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.sysman.config.service.impl.SysConfigService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

@RestController
@RequestMapping("/api/knowledge/settings")
public class KnowledgeSettingsController {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeSettingsController.class);
    private static final AtomicBoolean defaultsLoaded = new AtomicBoolean(false);

    private static final String[][] DEFAULTS = {
        {"knowledge.graph.defaultDomain", "default", "knowledge", "string", "默认图谱域"},
        {"knowledge.graph.maxNeighborDegree", "3", "knowledge", "int", "邻居展开最大度数"},
        {"knowledge.index.autoSyncEnabled", "true", "knowledge", "bool", "索引自动同步开关"},
        {"knowledge.index.batchSize", "500", "knowledge", "int", "索引批量大小"},
        {"knowledge.rag.topK", "5", "knowledge", "int", "RAG召回TopK"},
        {"knowledge.rag.similarityThreshold", "0.7", "knowledge", "float", "RAG相似度阈值"},
        {"knowledge.rag.model", "text-embedding-3-small", "knowledge", "string", "RAG向量模型"},
        {"knowledge.lineage.maxDepth", "10", "knowledge", "int", "血缘最大深度"},
    };

    private final SysConfigService sysConfigService;

    public KnowledgeSettingsController(SysConfigService sysConfigService) {
        this.sysConfigService = sysConfigService;
    }

    @PostConstruct
    public void init() {
        if (defaultsLoaded.compareAndSet(false, true)) {
            try {
                for (String[] row : DEFAULTS) {
                    sysConfigService.upsertValue(row[0], row[1], row[2], row[3], row[4]);
                }
                log.info("Knowledge settings defaults loaded ({} items)", DEFAULTS.length);
            } catch (Exception e) {
                log.warn("Failed to load knowledge defaults: {}", e.getMessage());
            }
        }
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> getAll() {
        return ApiResponse.success(sysConfigService.listByGroup("knowledge"));
    }

    @PutMapping
    public ApiResponse<Map<String, Object>> batchUpdate(@RequestBody List<Map<String, String>> updates) {
        int count = sysConfigService.updateBatch(updates);
        return ApiResponse.success(Map.of("updated", count));
    }
}
