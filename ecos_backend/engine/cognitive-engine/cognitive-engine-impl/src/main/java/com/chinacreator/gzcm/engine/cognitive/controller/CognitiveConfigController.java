package com.chinacreator.gzcm.engine.cognitive.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.sysman.config.service.impl.SysConfigService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

@RestController
@RequestMapping("/api/v1/cognitive/config")
public class CognitiveConfigController {

    private static final Logger log = LoggerFactory.getLogger(CognitiveConfigController.class);
    private static final AtomicBoolean defaultsLoaded = new AtomicBoolean(false);

    private static final String[][] DEFAULTS = {
        {"prompt-compiler.topK", "5", "cognitive-engine", "int", "混合召回TopK"},
        {"prompt-compiler.vectorModel", "text-embedding-3-small", "cognitive-engine", "string", "向量模型"},
        {"agent-mesh.routingStrategy", "keyword_match", "cognitive-engine", "enum", "路由策略 keyword_match/semantic"},
        {"guardrails.piiDetectionEnabled", "true", "cognitive-engine", "bool", "PII检测开关"},
        {"guardrails.hallucinationCheckEnabled", "true", "cognitive-engine", "bool", "幻觉检测开关"},
        {"action-bridge.autoExecuteEnabled", "true", "cognitive-engine", "bool", "自动执行开关"},
        {"action-bridge.matchConfidenceThreshold", "0.7", "cognitive-engine", "float", "匹配置信度阈值"},
    };

    private final SysConfigService sysConfigService;

    public CognitiveConfigController(SysConfigService sysConfigService) {
        this.sysConfigService = sysConfigService;
    }

    @PostConstruct
    public void init() {
        if (defaultsLoaded.compareAndSet(false, true)) {
            try {
                for (String[] row : DEFAULTS) {
                    sysConfigService.upsertValue(row[0], row[1], row[2], row[3], row[4]);
                }
                log.info("Cognitive engine config defaults loaded ({} items)", DEFAULTS.length);
            } catch (Exception e) {
                log.warn("Failed to load cognitive defaults: {}", e.getMessage());
            }
        }
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> getAll() {
        return ApiResponse.success(sysConfigService.listByGroup("cognitive-engine"));
    }

    @GetMapping("/defaults")
    public ApiResponse<Map<String, String>> getDefaults() {
        Map<String, String> defaults = new LinkedHashMap<>();
        for (String[] row : DEFAULTS) defaults.put(row[0], row[1]);
        return ApiResponse.success(defaults);
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
}
