package com.chinacreator.gzcm.engine.cognitive.controller;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.chinacreator.gzcm.common.base.ApiResponse;

/**
 * AIP Guardrails REST API — AI 护栏与审计日志管理。
 *
 * <h3>端点：</h3>
 * <ul>
 *   <li>GET    /api/v1/aip/guardrails          — 列出所有护栏规则</li>
 *   <li>POST   /api/v1/aip/guardrails          — 创建护栏规则</li>
 *   <li>GET    /api/v1/aip/guardrails/{id}     — 护栏详情</li>
 *   <li>PUT    /api/v1/aip/guardrails/{id}     — 更新护栏</li>
 *   <li>DELETE /api/v1/aip/guardrails/{id}     — 删除护栏</li>
 *   <li>GET    /api/v1/aip/audit-logs          — 审计日志列表</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/aip")
public class AIPGuardrailController {

    private static final Logger log = LoggerFactory.getLogger(AIPGuardrailController.class);

    private final Map<String, Map<String, Object>> guardrailStore = new ConcurrentHashMap<>();
    private final List<Map<String, Object>> auditLogs = new ArrayList<>();

    // ═══════════════ Guardrails CRUD ═══════════════════

    @GetMapping("/guardrails")
    public ApiResponse<List<Map<String, Object>>> listGuardrails() {
        List<Map<String, Object>> result = List.copyOf(guardrailStore.values());
        return ApiResponse.success(result);
    }

    @GetMapping("/guardrails/{id}")
    public ApiResponse<Map<String, Object>> getGuardrail(@PathVariable String id) {
        Map<String, Object> g = guardrailStore.get(id);
        if (g == null) return ApiResponse.notFound("Guardrail " + id + " 不存在");
        return ApiResponse.success(g);
    }

    @PostMapping("/guardrails")
    public ApiResponse<Map<String, Object>> createGuardrail(@RequestBody Map<String, Object> body) {
        String name = String.valueOf(body.getOrDefault("name", "")).trim();
        if (name.isEmpty()) {
            return ApiResponse.badRequest("AIP-001: 'name' is required");
        }
        String id = "gr_" + UUID.randomUUID().toString().substring(0, 8);
        Map<String, Object> g = new LinkedHashMap<>();
        g.put("id", id);
        g.put("name", name);
        g.put("type", String.valueOf(body.getOrDefault("type", "content_filter")));
        g.put("rule", body.getOrDefault("rule", ""));
        g.put("action", String.valueOf(body.getOrDefault("action", "block")));
        g.put("priority", body.getOrDefault("priority", 1));
        g.put("enabled", body.getOrDefault("enabled", true));
        g.put("description", String.valueOf(body.getOrDefault("description", "")));
        g.put("createdAt", Instant.now().toString());
        g.put("updatedAt", Instant.now().toString());
        guardrailStore.put(id, g);
        log.info("AIP Guardrail created: {} [{}]", id, name);
        return ApiResponse.success(g);
    }

    @PutMapping("/guardrails/{id}")
    public ApiResponse<Map<String, Object>> updateGuardrail(
            @PathVariable String id,
            @RequestBody Map<String, Object> body) {
        Map<String, Object> existing = guardrailStore.get(id);
        if (existing == null) return ApiResponse.notFound("Guardrail " + id + " 不存在");

        Map<String, Object> updated = new LinkedHashMap<>(existing);
        if (body.containsKey("name")) updated.put("name", body.get("name"));
        if (body.containsKey("type")) updated.put("type", body.get("type"));
        if (body.containsKey("rule")) updated.put("rule", body.get("rule"));
        if (body.containsKey("action")) updated.put("action", body.get("action"));
        if (body.containsKey("priority")) updated.put("priority", body.get("priority"));
        if (body.containsKey("enabled")) updated.put("enabled", body.get("enabled"));
        if (body.containsKey("description")) updated.put("description", body.get("description"));
        updated.put("updatedAt", Instant.now().toString());
        guardrailStore.put(id, updated);
        log.info("AIP Guardrail updated: {}", id);
        return ApiResponse.success(updated);
    }

    @DeleteMapping("/guardrails/{id}")
    public ApiResponse<Map<String, Object>> deleteGuardrail(@PathVariable String id) {
        if (guardrailStore.remove(id) != null) {
            log.info("AIP Guardrail deleted: {}", id);
            return ApiResponse.success(Map.of("deleted", true, "id", id));
        }
        return ApiResponse.notFound("Guardrail " + id + " 不存在");
    }

    // ═══════════════ Audit Logs ═══════════════════

    @GetMapping("/audit-logs")
    public ApiResponse<List<Map<String, Object>>> listAuditLogs() {
        // 生成模拟审计日志
        if (auditLogs.isEmpty()) {
            for (int i = 1; i <= 3; i++) {
                Map<String, Object> logEntry = new LinkedHashMap<>();
                logEntry.put("id", "audit_" + i);
                logEntry.put("event", "agent_call");
                logEntry.put("agentId", "agent-101");
                logEntry.put("user", "admin");
                logEntry.put("action", "query");
                logEntry.put("result", "success");
                logEntry.put("timestamp", Instant.now().minusSeconds(i * 3600).toString());
                logEntry.put("details", Map.of("tokensUsed", 150 * i, "durationMs", 320 * i));
                auditLogs.add(logEntry);
            }
        }
        return ApiResponse.success(auditLogs);
    }
}
