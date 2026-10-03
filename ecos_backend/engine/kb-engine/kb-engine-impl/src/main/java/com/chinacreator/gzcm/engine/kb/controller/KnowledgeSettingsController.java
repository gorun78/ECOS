package com.chinacreator.gzcm.engine.kb.controller;

import com.chinacreator.gzcm.common.annotation.RequirePermission;
import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.kb.KnowledgeSettingsService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 知识引擎配置管理 Controller（{@code /api/v1/knowledge/settings/*}）。
 *
 * @group OVERVIEW
 */
@RestController
@RequestMapping("/api/v1/knowledge/settings")
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
        {"extract.allow_direct_upload", "false", "knowledge", "bool", "是否允许知识工作台直接上传临时文件（非结构化快路径，默认关闭）"},
        {"extract.page_limit", "500", "knowledge", "int", "结构化抽取单页行数"},
        {"extract.max_pages", "200", "knowledge", "int", "单资源最大页数"},
        {"extract.periodic_enabled", "false", "knowledge", "bool", "是否启用周期增量抽取（委托 runtime-task，本批次默认关闭）"},
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
    @RequirePermission(permission = "knowledge:settings:write")
    public ApiResponse<Map<String, Object>> batchUpdate(@RequestBody List<Map<String, String>> updates) {
        int count = settingsService.batchUpdate(updates);
        return ApiResponse.success(Map.of("updated", count));
    }
}