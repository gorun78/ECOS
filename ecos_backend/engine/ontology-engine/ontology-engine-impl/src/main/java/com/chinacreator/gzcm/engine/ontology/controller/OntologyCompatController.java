package com.chinacreator.gzcm.engine.ontology.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OntologyCompatController — 本体兼容端点空壳（T4: 替换 CeosCompatController 内存 mock）。
 *
 * <p>路径与原 CeosCompatController 的本体端点完全一致，前端无需改动：</p>
 * <ul>
 *   <li>GET  /api/ontology/mappings                     — 本体映射（返回空 mappings + 空 availableTables）</li>
 *   <li>POST /api/ontology/mappings                     — 保存本体映射（接收并返回成功，不持久化 mock）</li>
 *   <li>GET  /api/ontology/export                       — 导出本体对齐知识包（返回空 Markdown）</li>
 *   <li>GET  /api/ontology/entities/{entityType}/instances — 实体实例（返回空列表）</li>
 * </ul>
 *
 * <p><b>缺口说明</b>：ontology-engine 当前没有与 ceos_new 风格本体映射对应的真实端点
 * （OntologyMappingController 走 /api/v1/ontology/mappings，schema 不同）。
 * 此处保留空壳避免前端 404，真实实现待后续补齐。</p>
 */
@RestController
@RequestMapping("/api/ontology")
public class OntologyCompatController {

    private static final Logger log = LoggerFactory.getLogger(OntologyCompatController.class);

    // ════════════════════════════════════════════
    // API: GET /api/ontology/mappings
    // ════════════════════════════════════════════
    @GetMapping("/mappings")
    public ApiResponse getOntologyMappingsCompat() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("mappings", new ArrayList<>());
        result.put("availableTables", new ArrayList<>());
        return ApiResponse.success(result);
    }

    // ════════════════════════════════════════════
    // API: POST /api/ontology/mappings
    // ════════════════════════════════════════════
    @PostMapping("/mappings")
    public ApiResponse saveOntologyMappingsCompat(@RequestBody Map<String, Object> body) {
        Object mappingsObj = body == null ? null : body.get("mappings");
        if (!(mappingsObj instanceof List)) {
            return ApiResponse.error(400, "Mappings must be a valid array");
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> mappings = (List<Map<String, Object>>) mappingsObj;
        log.info("Ontology mappings received (count={}); persistence not yet implemented.", mappings.size());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("message", "本体映射已接收（持久化待后续实现）。");
        result.put("mappings", mappings);
        return ApiResponse.success(result);
    }

    // ════════════════════════════════════════════
    // API: GET /api/ontology/export
    // ════════════════════════════════════════════
    @GetMapping("/export")
    public ApiResponse exportOntologyCompat() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("exportedAt", java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        result.put("rawJson", new ArrayList<>());
        result.put("knowledgeMarkdown",
                "=== AIP Core Ontology Schema & Physical Columns Alignment Pack ===\n" +
                "(No mappings available — ontology mapping persistence not yet implemented.)\n" +
                "=== End of Schema Mapping Pack ===");
        return ApiResponse.success(result);
    }

    // ════════════════════════════════════════════
    // API: GET /api/ontology/entities/{entityType}/instances
    // ════════════════════════════════════════════
    @GetMapping("/entities/{entityType}/instances")
    public ApiResponse<List<Map<String, Object>>> getEntityInstances(@PathVariable String entityType) {
        // 无内存 mock 实例；返回空列表保持契约。
        log.info("Entity instances requested: type={}; returning empty list (no mock data).", entityType);
        return ApiResponse.success(new ArrayList<>());
    }
}
