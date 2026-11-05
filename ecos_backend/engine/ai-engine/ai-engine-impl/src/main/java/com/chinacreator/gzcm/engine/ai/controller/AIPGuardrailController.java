package com.chinacreator.gzcm.engine.ai.controller;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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
import com.chinacreator.gzcm.engine.ai.dto.AipAuditLogVO;
import com.chinacreator.gzcm.engine.ai.dto.AipDeleteResultVO;
import com.chinacreator.gzcm.engine.ai.dto.AipGuardrailVO;

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
 *
 * <p>DI-2 #147DX：内存行为 {@link AipGuardrailVO}（{@code rule/priority/enabled} 为请求体原值
 * 透传位，保持 {@code Object}）；审计日志为 {@link AipAuditLogVO}；建/改为自由请求体。</p>
 */
@RestController
@RequestMapping("/api/v1/aip")
public class AIPGuardrailController {

    private static final Logger log = LoggerFactory.getLogger(AIPGuardrailController.class);

    private final Map<String, AipGuardrailVO> guardrailStore = new ConcurrentHashMap<>();
    private final List<AipAuditLogVO> auditLogs = new ArrayList<>();

    // ═══════════════ Guardrails CRUD ═══════════════

    @GetMapping("/guardrails")
    public ApiResponse<List<AipGuardrailVO>> listGuardrails() {
        return ApiResponse.success(List.copyOf(guardrailStore.values()));
    }

    @GetMapping("/guardrails/{id}")
    public ApiResponse<AipGuardrailVO> getGuardrail(@PathVariable String id) {
        AipGuardrailVO g = guardrailStore.get(id);
        if (g == null) return ApiResponse.notFound("Guardrail " + id + " 不存在");
        return ApiResponse.success(g);
    }

    @PostMapping("/guardrails")
    public ApiResponse<AipGuardrailVO> createGuardrail(@RequestBody Map<String, Object> body) {
        String name = String.valueOf(body.getOrDefault("name", "")).trim();
        if (name.isEmpty()) {
            return ApiResponse.badRequest("AIP-001: 'name' is required");
        }
        String id = "gr_" + UUID.randomUUID().toString().substring(0, 8);
        String now = Instant.now().toString();
        AipGuardrailVO g = new AipGuardrailVO(
                id,
                name,
                String.valueOf(body.getOrDefault("type", "content_filter")),
                body.getOrDefault("rule", ""),
                String.valueOf(body.getOrDefault("action", "block")),
                body.getOrDefault("priority", 1),
                body.getOrDefault("enabled", true),
                String.valueOf(body.getOrDefault("description", "")),
                now,
                now);
        guardrailStore.put(id, g);
        log.info("AIP Guardrail created: {} [{}]", id, name);
        return ApiResponse.success(g);
    }

    @PutMapping("/guardrails/{id}")
    public ApiResponse<AipGuardrailVO> updateGuardrail(
            @PathVariable String id,
            @RequestBody Map<String, Object> body) {
        AipGuardrailVO existing = guardrailStore.get(id);
        if (existing == null) return ApiResponse.notFound("Guardrail " + id + " 不存在");

        AipGuardrailVO updated = existing.withUpdated(
                body.containsKey("name") ? String.valueOf(body.get("name")) : null,
                body.containsKey("type") ? String.valueOf(body.get("type")) : null,
                body.get("rule"),
                body.containsKey("action") ? String.valueOf(body.get("action")) : null,
                body.get("priority"),
                body.get("enabled"),
                body.containsKey("description") ? String.valueOf(body.get("description")) : null,
                Instant.now().toString());
        guardrailStore.put(id, updated);
        log.info("AIP Guardrail updated: {}", id);
        return ApiResponse.success(updated);
    }

    @DeleteMapping("/guardrails/{id}")
    public ApiResponse<AipDeleteResultVO> deleteGuardrail(@PathVariable String id) {
        if (guardrailStore.remove(id) != null) {
            log.info("AIP Guardrail deleted: {}", id);
            return ApiResponse.success(new AipDeleteResultVO(true, id));
        }
        return ApiResponse.notFound("Guardrail " + id + " 不存在");
    }

    // ═══════════════ Audit Logs ═══════════════

    @GetMapping("/audit-logs")
    public ApiResponse<List<AipAuditLogVO>> listAuditLogs() {
        // 生成模拟审计日志
        if (auditLogs.isEmpty()) {
            for (int i = 1; i <= 3; i++) {
                auditLogs.add(new AipAuditLogVO(
                        "audit_" + i,
                        "agent_call",
                        "agent-101",
                        "admin",
                        "query",
                        "success",
                        Instant.now().minusSeconds(i * 3600).toString(),
                        Map.of("tokensUsed", 150 * i, "durationMs", 320 * i)));
            }
        }
        return ApiResponse.success(auditLogs);
    }
}
