package com.chinacreator.gzcm.engine.kb.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.kb.KnowledgeSettingsService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

@RestController
@RequestMapping("/api/knowledge/settings")
public class KnowledgeSettingsController {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeSettingsController.class);
    private static final AtomicBoolean defaultsLoaded = new AtomicBoolean(false);

    private static final String[][] DEFAULTS = {
        {"knowledge.graph.defaultDomain", "default", "knowledge", "string", "Default graph domain"},
        {"knowledge.graph.maxNeighborDegree", "3", "knowledge", "int", "Max neighbor expansion degree"},
        {"knowledge.index.autoSyncEnabled", "true", "knowledge", "bool", "Auto sync toggle"},
        {"knowledge.index.batchSize", "500", "knowledge", "int", "Index batch size"},
        {"knowledge.rag.topK", "5", "knowledge", "int", "RAG recall TopK"},
        {"knowledge.rag.similarityThreshold", "0.7", "knowledge", "float", "RAG similarity threshold"},
        {"knowledge.rag.model", "text-embedding-3-small", "knowledge", "string", "RAG vector model"},
        {"knowledge.lineage.maxDepth", "10", "knowledge", "int", "Lineage max depth"},
    };

    @Autowired
    private KnowledgeSettingsService settingsService;

    @PostConstruct
    public void init() {
        if (defaultsLoaded.compareAndSet(false, true)) {
            try {
                for (String[] row : DEFAULTS) {
                    settingsService.upsertSetting(row[0], row[1], row[2], row[3], row[4]);
                }
                log.info("Knowledge settings defaults loaded ({} items)", DEFAULTS.length);
            } catch (Exception e) {
                log.warn("Failed to load knowledge defaults: {}", e.getMessage());
            }
        }
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> getAll() {
        return ApiResponse.success(settingsService.getAllSettings());
    }

    @PutMapping
    public ApiResponse<Map<String, Object>> batchUpdate(@RequestBody List<Map<String, String>> updates) {
        int count = settingsService.batchUpdate(updates);
        return ApiResponse.success(Map.of("updated", count));
    }
}