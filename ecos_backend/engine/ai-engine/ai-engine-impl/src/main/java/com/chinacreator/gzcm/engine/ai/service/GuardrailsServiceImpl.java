package com.chinacreator.gzcm.engine.ai.service;

import com.chinacreator.gzcm.common.exception.ValidationException;
import com.chinacreator.gzcm.engine.ai.GuardrailsService;
import com.chinacreator.gzcm.engine.ai.security.AiSecurityEngineClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * F06-04：输入/输出护栏改经 security-engine，且 fail-closed。
 *
 * <p>要点 1（设计-06 §309）：{@link #validate} 不再用本地正则判定（原 PII/SQL 注入/越狱/
 * 幻觉四个正则规则已删），改为经 {@link AiSecurityEngineClient#screenOrThrow} 走
 * security 文本审核端点（§2.4-3/7，护栏算法只在 security 侧，X-16 收口）。
 * <b>本地正则不再充当任何放行依据</b>。</p>
 *
 * <p>fail-closed 语义：security 不可用 / 超时 / 响应不可解析 / 未明确放行 → 返回
 * {@code passed=false, failed=true, errorClass=GUARDRAIL_FAIL_CLOSED}（§2.4-6 宁可误拒），
 * 调用方（AgentLoop / GuardrailsApiController）据此拒绝或终止，<b>绝不降级为本地正则放行</b>。</p>
 *
 * <p>策略 CRUD（{@code listPolicies}/{@code createPolicy}/{@code deletePolicy}）为护栏策略
 * 管理面（与内容审核无关，API 只增不改），本批不动。</p>
 */
@Service
public class GuardrailsServiceImpl implements GuardrailsService {

    private static final Logger log = LoggerFactory.getLogger(GuardrailsServiceImpl.class);
    private final AtomicLong idSeq = new AtomicLong(0);

    private final JdbcTemplate jdbc;

    /**
     * F06-04 要点 2：护栏依赖 security 客户端，<b>必填</b>（原 {@code GuardrailsService}
     * 消费点在 AgentLoop 侧 {@code required=false} 已删，X-15 判空跳过面消除）。
     */
    @Autowired
    private AiSecurityEngineClient securityEngineClient;

    public GuardrailsServiceImpl(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 供离线测试注入 security 客户端（生产走 Spring {@code @Autowired}）。 */
    void setSecurityEngineClient(AiSecurityEngineClient securityEngineClient) {
        this.securityEngineClient = securityEngineClient;
    }

    @Override
    public Map<String, Object> validate(Map<String, Object> req) {
        String scope = req == null || !(req.get("scope") instanceof String s)
                ? "input" : s;
        String text = req == null ? null : (req.get("llmOutput") instanceof String t ? t
                : (req.get("text") instanceof String t2 ? t2 : null));
        // 主体从 token 上下文取（禁请求体/LLM 自报，PMO-74 H9-T1）
        String userId = resolveUserId();
        try {
            // 文本原文只经该端点送 security（审核即脱敏消费方）；仅 security 明确 passed=true 才放行
            securityEngineClient.screenOrThrow(scope, text, userId);
        } catch (Exception e) {
            // F06-04 要点 2：catch(Exception) 不再 log.warn 后放行 → 回 GUARDRAIL_FAIL_CLOSED
            log.warn("[Guardrails] security 文本审核 fail-closed: scope={} reason={}", scope, e.getMessage());
            return failClosed("GUARDRAIL_FAIL_CLOSED: 安全护栏不可用或未明确放行（fail-closed §2.4-6）");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("passed", true);
        result.put("failed", false);
        result.put("violations", Collections.emptyList());
        result.put("checkedAt", Instant.now().toString());
        return result;
    }

    /** GUARDRAIL_FAIL_CLOSED 载体：passed=false（既有的 success 判定契约兼容）+ 显式 failed=true + 错误码。 */
    private Map<String, Object> failClosed(String message) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("passed", false);
        result.put("failed", true);
        result.put("errorClass", "GUARDRAIL_FAIL_CLOSED");
        result.put("message", message);
        result.put("violations", List.of("GUARDRAIL_FAIL_CLOSED"));
        result.put("checkedAt", Instant.now().toString());
        return result;
    }

    private String resolveUserId() {
        try {
            Class<?> uc = Class.forName("com.chinacreator.gzcm.sysman.iam.context.UserContext");
            Object id = uc.getMethod("getCurrentUserId").invoke(null);
            if (id != null && !String.valueOf(id).isBlank()) return String.valueOf(id);
        } catch (Throwable ignored) {
            // sysman 不在 classpath
        }
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null) {
                String name = auth.getName();
                if (name != null && !name.isBlank() && !"anonymousUser".equals(name)) return name;
            }
        } catch (Throwable ignored) {
            // spring-security 不在 classpath
        }
        return "anonymous";
    }

    @Override
    public List<Map<String, Object>> listPolicies() {
        try {
            return jdbc.queryForList("SELECT * FROM ecos_guardrail_policy ORDER BY created_at DESC");
        } catch (Exception e) {
            log.debug("Guardrail policies table not available: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public Map<String, Object> createPolicy(Map<String, Object> policy) {
        if (policy == null) {
            throw new ValidationException("policy is required");
        }
        String name = String.valueOf(policy.getOrDefault("name", ""));
        if (name.isEmpty() || "null".equals(name)) {
            // Wave-6 T-25: 参数校验失败统一走 ValidationException → 400
            throw new ValidationException("name is required");
        }

        String id = String.valueOf(idSeq.incrementAndGet());
        long now = Instant.now().toEpochMilli();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id);
        result.put("name", name);
        result.put("type", policy.getOrDefault("type", "block"));
        result.put("severity", policy.getOrDefault("severity", "medium"));
        result.put("isEnabled", policy.getOrDefault("isEnabled", true));
        result.put("parameters", policy.getOrDefault("parameters", Collections.emptyMap()));
        result.put("createdAt", now);
        result.put("updatedAt", now);

        try {
            jdbc.update(
                "INSERT INTO ecos_guardrail_policy (id, name, type, severity, is_enabled, parameters, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?::jsonb, NOW(), NOW())",
                id, name, result.get("type"), result.get("severity"),
                result.get("isEnabled"), "[]");
        } catch (Exception e) {
            log.debug("Persist guardrail policy failed (using memory): {}", e.getMessage());
        }

        log.info("Guardrails policy created: id={} name={}", id, name);
        return result;
    }

    @Override
    public void deletePolicy(String id) {
        try {
            jdbc.update("DELETE FROM ecos_guardrail_policy WHERE id = ?", id);
        } catch (Exception e) {
            log.debug("Delete guardrail policy from DB failed: {}", e.getMessage());
        }
        log.info("Guardrails policy deleted: id={}", id);
    }
}
