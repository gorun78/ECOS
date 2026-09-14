package com.chinacreator.gzcm.engine.data.quality.service;

import com.fasterxml.jackson.core.type.TypeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * DQ 治理安全桥接服务 — 收敛 security-engine 强制卡（架构铁律 §2.4）。
 * <p>
 * 能力（对齐 PipelineSecurityService 先例）：
 * <ol>
 *   <li>读/写操作异步审计：{@link #auditRead(String, String)} / {@link #auditWrite(String, String, String)}
 *       通过 {@code POST /api/security/audit/log} 异步落审计，失败不阻塞主流程。</li>
 *   <li>敏感字段脱敏：{@link #maskParameters(Map)} 将 phone/mobile/id_card/bank_card/amount 等
 *       键（含中文话术）值替换为 {@code ******}；对 target_field 等敏感维度做统一脱敏判定
 *       {@link #containsSensitiveField(String)}（铁律 2.4 #3）。</li>
 * </ol>
 * 同名 REST 契约：gateway 聚合后走本进程 8080；{@code /api/security/mask} 与
 * {@code ISecretService} 保持同语义（响应前脱敏）。
 *
 * @author PMO-48-A T3
 */
@Service("ecosDqSecurityService")
public class DqSecurityService {

    private static final Logger log = LoggerFactory.getLogger(DqSecurityService.class);

    /** 脱敏占位符 */
    public static final String MASKED = "******";

    /** 敏感字段键名 / 话术（小写包含匹配） */
    private static final Set<String> SENSITIVE_HINTS = Set.of(
            "phone", "mobile", "id_card", "idcard", "bank_card", "bankcard", "amount",
            "身份证", "手机", "银行卡", "金额");

    /** P2#4：第三方 security-engine REST 调用的连接超时（毫秒） */
    public static final int CONNECT_TIMEOUT_MS = 30000;

    /** P2#4：第三方 security-engine REST 调用的读取超时（毫秒） */
    public static final int READ_TIMEOUT_MS = 60000;

    /** security-engine 基地址（gateway 聚合后走本进程 8080；独立部署时按环境覆盖） */
    @Value("${dw.security.base-url:http://localhost:8080}")
    private String securityBaseUrl;

    /**
     * REST 客户端（P2#4：SimpleClientHttpRequestFactory 显式 30s/60s connect/read 超时，
     * 与 PipelineSecurityService 一致，security-engine 不可达时快速失败走默认 DENY/默认脱敏）。
     */
    private final RestTemplate restTemplate = buildRestTemplate();

    private static RestTemplate buildRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT_MS);
        factory.setReadTimeout(READ_TIMEOUT_MS);
        return new RestTemplate(factory);
    }

    // ==================== 1. 审计 ====================

    /**
     * 异步读操作审计（list/detail 返回前调用，不阻塞主流程）。
     *
     * @param action   动作名（如 DQ_RULE_LIST / DQ_RULE_DETAIL）
     * @param resource 资源 ID（空表示批量）
     */
    @Async
    public void auditRead(String action, String resource) {
        doAudit(action, resource, "system", "SUCCESS");
    }

    /**
     * 异步写操作审计（含被 405/410 拒绝的写尝试，也计审计留痕 — 铁律 2.4 #5）。
     *
     * @param action   动作名（如 DQ_RULE_CREATE_REJECTED）
     * @param resource 资源 ID
     * @param result   SUCCESS / REJECTED
     */
    @Async
    public void auditWrite(String action, String resource, String result) {
        doAudit(action, resource, "system", result);
    }

    private void doAudit(String action, String resource, String userId, String result) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("userId", userId);
            body.put("action", action);
            body.put("resource", "dq_rule:" + (resource != null ? resource : "batch"));
            body.put("result", result);
            body.put("detail", "action=" + action + ", ruleId=" + resource + ", result=" + result);
            restTemplate.postForObject(securityBaseUrl + "/api/security/audit/log", body, Object.class);
            log.debug("DQ audit accepted: action={}, resource={}", action, resource);
        } catch (Exception e) {
            // 审计失败不阻塞主流程，但必须留痕（warn 级）
            log.warn("DQ audit write failed (ignored): action={}, resource={}, error={}",
                    action, resource, e.getMessage());
        }
    }

    // ==================== 2. 敏感字段脱敏 ====================

    /**
     * 判定字段名/话术是否命中敏感关键词（用于 target_field 等维度脱敏）。
     *
     * @param fieldName 字段名或文案
     * @return true 表示需脱敏
     */
    public boolean containsSensitiveField(String fieldName) {
        if (fieldName == null || fieldName.isBlank()) {
            return false;
        }
        String lower = fieldName.toLowerCase();
        for (String hint : SENSITIVE_HINTS) {
            if (lower.contains(hint.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 对规则参数 Map 中命中敏感键的值脱敏（同结构返回新 Map，不修改入参）。
     *
     * @param raw 原始参数
     * @return 脱敏后参数；入参为 null 时返回空 Map
     */
    public Map<String, Object> maskParameters(Map<String, Object> raw) {
        if (raw == null || raw.isEmpty()) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> masked = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : raw.entrySet()) {
            Object value = entry.getValue();
            if (containsSensitiveField(entry.getKey()) && value != null && !(value instanceof Map)) {
                masked.put(entry.getKey(), MASKED);
            } else {
                masked.put(entry.getKey(), value);
            }
        }
        return masked;
    }

    /**
     * security REST 脱敏（POST /api/security/mask）— 单值脱敏兜底。
     * security-engine 不可用时默认占位符（默认 DENY 语义的脱敏侧体现：宁脱敏不漏明文）。
     *
     * @param value    原始值
     * @param maskType PHONE / ID_CARD / AMOUNT / SHA256
     * @return 脱敏后的值
     */
    public String maskValue(String value, String maskType) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("value", value);
            body.put("maskType", maskType);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(
                    securityBaseUrl + "/api/security/mask", entity, String.class);
            String respBody = response.getBody();
            if (respBody != null && !respBody.isEmpty()) {
                Map<String, Object> apiResp = MAPPER.readValue(respBody, new TypeReference<Map<String, Object>>() {});
                Object data = apiResp.get("data");
                if (data instanceof Map) {
                    Object masked = ((Map<?, ?>) data).get("masked");
                    if (masked != null) {
                        return masked.toString();
                    }
                }
            }
            log.warn("SecurityEngine mask response empty, default mask: maskType={}", maskType);
        } catch (Exception e) {
            // security-engine 不可用 — 默认脱敏（宁可误脱，不可漏明文）
            log.warn("SecurityEngine mask unavailable (default mask): maskType={}, error={}", maskType, e.getMessage());
        }
        return MASKED;
    }

    private static final com.fasterxml.jackson.databind.ObjectMapper MAPPER =
            new com.fasterxml.jackson.databind.ObjectMapper();
}
