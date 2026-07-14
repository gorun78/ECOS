package com.chinacreator.gzcm.engine.ontology.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.sysman.config.service.impl.SysConfigService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

@RestController
@RequestMapping("/api/v1/engine/ontology/settings")
public class OntologyConfigController {

    private static final Logger log = LoggerFactory.getLogger(OntologyConfigController.class);
    private static final AtomicBoolean defaultsLoaded = new AtomicBoolean(false);

    private static final String[][] DEFAULTS = {
        {"ontology.graph.backend", "pg", "ontology-engine", "enum", "图存储后端 pg/neo4j"},
        {"ontology.graph.neo4j.uri", "bolt://localhost:7687", "ontology-engine", "string", "Neo4j连接URI"},
        {"ontology.graph.neo4j.user", "neo4j", "ontology-engine", "string", "Neo4j用户名"},
        {"ontology.graph.neo4j.password", "neo4j123", "ontology-engine", "password", "Neo4j密码"},
        {"ontology.cache.ttl_seconds", "300", "ontology-engine", "int", "缓存TTL秒数"},
        {"ontology.version.max_versions", "10", "ontology-engine", "int", "最大版本数"},
        {"ontology.version.auto_version", "true", "ontology-engine", "bool", "自动版本化"},
        {"ontology.version.require_approval", "true", "ontology-engine", "bool", "版本发布需审批"},
        {"ontology.import.batch_size", "1000", "ontology-engine", "int", "导入批次大小"},
        {"ontology.export.format", "jsonld", "ontology-engine", "enum", "导出格式 jsonld/owl/ttl"},
        {"ontology.proposal.auto_publish", "false", "ontology-engine", "bool", "提案自动发布"},
        {"ontology.workflow.engine", "buszhi", "ontology-engine", "string", "工作流引擎"},
    };

    private final SysConfigService sysConfigService;

    public OntologyConfigController(SysConfigService sysConfigService, JdbcTemplate jdbc) {
        this.sysConfigService = sysConfigService;
    }

    @PostConstruct
    public void init() {
        if (defaultsLoaded.compareAndSet(false, true)) {
            loadDefaults();
        }
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> getAll() {
        return ApiResponse.success(sysConfigService.listByGroup("ontology-engine"));
    }

    @GetMapping("/defaults")
    public ApiResponse<Map<String, String>> getDefaults() {
        Map<String, String> defaults = new LinkedHashMap<>();
        for (String[] row : DEFAULTS) {
            defaults.put(row[0], row[1]);
        }
        return ApiResponse.success(defaults);
    }

    @GetMapping("/{group}")
    public ApiResponse<List<Map<String, Object>>> getByGroup(@PathVariable String group) {
        String fullGroup = "ontology-" + group;
        return ApiResponse.success(sysConfigService.listByGroup(fullGroup));
    }

    @PutMapping
    public ApiResponse<Map<String, Object>> batchUpdate(@RequestBody List<Map<String, String>> updates) {
        int count = sysConfigService.updateBatch(updates);
        return ApiResponse.success(Map.of("updated", count));
    }

    @PostMapping("/refresh")
    public ApiResponse<Map<String, Object>> refresh() {
        sysConfigService.refreshCache();
        return ApiResponse.success(Map.of("cacheSize", sysConfigService.cacheSize()));
    }

    private void loadDefaults() {
        try {
            for (String[] row : DEFAULTS) {
                sysConfigService.upsertValue(row[0], row[1], row[2], row[3], row[4]);
            }
            log.info("Ontology engine defaults loaded ({} items)", DEFAULTS.length);
        } catch (Exception e) {
            log.warn("Failed to load ontology defaults: {}", e.getMessage());
        }
    }
}
