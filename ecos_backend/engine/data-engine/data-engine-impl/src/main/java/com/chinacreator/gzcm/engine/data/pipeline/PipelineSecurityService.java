package com.chinacreator.gzcm.engine.data.pipeline;

import com.chinacreator.gzcm.common.security.abac.AbacEvaluateResponseContract;
import com.chinacreator.gzcm.common.security.caller.CallerCredentialSource;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Pipeline 安全桥接服务 — 收敛 security-engine 强制卡（架构铁律 §2.4）。
 * <p>
 * 三大能力：
 * <ol>
 *   <li>写操作异步审计：{@link #auditWrite(String, String, String)} 通过
 *       {@code POST /api/v1/pipeline→security: /api/security/audit/log} 异步落审计，不阻塞主流程。</li>
 *   <li>敏感字段脱敏：{@link #maskNodeConfig(Map)} 将 SOURCE_JDBC/SOURCE_REST 节点的
 *       password/token 等替换为 {@code ******}，明文仅入库（§4.3）。</li>
 *   <li>ABAC 裁决：{@link #evaluateExecute(String)} 执行前调
 *       {@code POST /api/v1/security/policy-engine/evaluate}，security 不可用时落
 *       {@link AbacEvaluateResponseContract.Verdict#UNEVALUATED}（不放行，但不冒充"无权限"，§4.7.2）。</li>
 * </ol>
 * 统一经本类 REST 调用 security-engine，禁止各调用点重复实现安全逻辑（§2.4 第 7 条）。
 *
 * <p>P2#4 安全补齐：RestTemplate 显式配置 connect/read 超时（默认 30s/60s），
 * 避免第三方 security-engine 慢响应/不可达导致线程永远挂起。
 *
 * @author DataBridge Datanet Team
 */
@Service
public class PipelineSecurityService {

    private static final Logger log = LoggerFactory.getLogger(PipelineSecurityService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 脱敏占位符 */
    private static final String MASKED = "******";

    /** 敏感字段键名（节点 config 命中即脱敏） */
    private static final Set<String> SENSITIVE_KEYS =
            Set.of("password", "token", "secret", "apikey", "api_key", "authorization", "credential");

    /** P2#4：第三方 security-engine REST 调用的连接超时（毫秒）*/
    private static final int CONNECT_TIMEOUT_MS = 30000;

    /** P2#4：第三方 security-engine REST 调用的读取超时（毫秒）*/
    private static final int READ_TIMEOUT_MS = 60000;

    /** security-engine 基地址（gateway 聚合后走本进程 8080；独立部署时按环境覆盖） */
    @Value("${dw.security.base-url:http://localhost:8080}")
    private String securityBaseUrl;

    /**
     * REST 客户端（P2#4：SimpleClientHttpRequestFactory 显式 30s/60s connect/read 超时，
     * 第三方迟延或不可达时快速失败走不可用态/默认脱敏分支）。
     */
    private final RestTemplate restTemplate = newRestTemplateWithTimeouts(CONNECT_TIMEOUT_MS, READ_TIMEOUT_MS);

    /**
     * 构造带显式超时的 RestTemplate（P2#4）。
     * 抽独立方法便于单测 / 后续切换更复杂的工厂（如 SSL）时 class 字段不动。
     */
    static RestTemplate newRestTemplateWithTimeouts(int connectTimeoutMs, int readTimeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Math.max(1, connectTimeoutMs));
        factory.setReadTimeout(Math.max(1, readTimeoutMs));
        return new RestTemplate(factory);
    }

    // ==================== 1. 写操作异步审计 ====================

    /**
     * 异步写审计日志（不阻塞主流程）。
     *
     * @param action   动作名（如 PIPELINE_CREATE / PIPELINE_UPDATE / PIPELINE_DELETE / PIPELINE_EXECUTE）
     * @param resource 资源 ID（definitionId）
     * @param userId   操作者 ID（可空，默认 system）
     */
    @Async
    public void auditWrite(String action, String resource, String userId) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("userId", userId != null && !userId.isBlank() ? userId : "system");
            body.put("action", action);
            body.put("resource", "pipeline:" + resource);
            body.put("result", "SUCCESS");
            body.put("detail", "action=" + action + ", definitionId=" + resource);
            Object response = restTemplate.postForObject(
                    securityBaseUrl + "/api/security/audit/log", body, Object.class);
            log.debug("Pipeline audit accepted: action={}, resource={}, resp={}", action, resource, response);
        } catch (Exception e) {
            // 审计失败不阻塞主流程，但必须留痕（warn 级）
            log.warn("Pipeline audit write failed (ignored): action={}, resource={}, error={}",
                    action, resource, e.getMessage());
        }
    }

    // ==================== 2. 敏感字段脱敏 ====================

    /**
     * 脱敏节点 config 中的敏感字段（递归处理 map 值）。
     * <p>命中 {@link #SENSITIVE_KEYS} 键名时，将对应字符串值替换为 {@code ******}。
     * 返回新 Map（不修改入参），供 VO 序列化使用。
     *
     * @param raw 原始 config（含明文）
     * @return 脱敏后的 config（同结构），入参为 null 时返回空 Map
     */
    public Map<String, Object> maskNodeConfig(Map<String, Object> raw) {
        if (raw == null || raw.isEmpty()) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> masked = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : raw.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (isSensitiveKey(key) && value != null && !(value instanceof Map)) {
                masked.put(key, MASKED);
            } else if (value instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> nested = (Map<String, Object>) value;
                masked.put(key, maskNodeConfig(nested));
            } else {
                masked.put(key, value);
            }
        }
        return masked;
    }

    /**
     * 节点 config JSON 字符串 → 脱敏 Map（解析失败返回空 Map）。
     */
    public Map<String, Object> parseAndMaskConfig(String configJson) {
        if (configJson == null || configJson.isBlank() || "{}".equals(configJson.trim())) {
            return new LinkedHashMap<>();
        }
        try {
            Map<String, Object> raw = MAPPER.readValue(configJson, new TypeReference<Map<String, Object>>() {});
            return maskNodeConfig(raw);
        } catch (Exception e) {
            log.warn("Failed to parse node config for masking: {}", e.getMessage());
            return new LinkedHashMap<>();
        }
    }

    private boolean isSensitiveKey(String key) {
        return key != null && SENSITIVE_KEYS.contains(key.toLowerCase());
    }

    // ==================== 2.1 出站凭证中继（ADR-7 信任链） ====================

    /**
     * 把当前入口请求的凭证与信任头中继到 policy-engine 出站请求（卷07 F07-17 同型判例，
     * 范式见 {@code WorkspaceSecurityEngineClient#relayCallerCredentials}）。
     *
     * <p>2026-10-16 live 实证：本类出站只带 Content-Type、不带任何凭证 ⇒ 打到 gateway
     * 自家 :8080 的 {@code /api/v1/security/policy-engine/evaluate} 被 Spring Security
     * {@code anyRequest().authenticated()} 判 403 ⇒ 被下方 {@code catch (Exception)} 折叠成
     * {@code SECURITY_ENGINE_UNAVAILABLE} ⇒ 已登录用户被误报"ABAC 策略拒绝"。
     * 这是 plumbing 缺口，不是策略裁决。
     *
     * <p><b>fail-closed 语义不变</b>：无入口请求上下文（异步/定时线程）⇒ 不伪造凭证，
     * 由被调方按默认策略裁决；只逐字转发入口请求已带的 {@code Authorization} 与
     * {@code X-ECOS-USER/ROLE/TENANT/DOMAIN}，不升权、不拼接、不加兜底租户。
     */
    private static void relayCallerCredentials(HttpHeaders headers) {
        // 判据单源在 CallerCredentialSource（#142/B4-3e-21 收口 D-B4-61）：
        // 无请求上下文即原样不加，由被调方按默认策略裁决。
        CallerCredentialSource.applyTo(headers);
    }

    /**
     * 构造 policy-engine 出站请求头：JSON Content-Type + <b>显式 Accept: application/json</b>
     * + 调用方凭证中继。
     *
     * <p>Accept 必须显式钉死：{@code jackson-dataformat-xml}（runtime-core 传递依赖）注册了
     * XML 转换器，而 RestTemplate 默认 Accept 含 {@code application/xml} ⇒ 内容协商会把
     * {@code ApiResponse} 序列化成 XML，本类 {@code MAPPER.readValue} 以
     * {@code Unexpected character ('<')} 抛错并折叠成 {@code SECURITY_ENGINE_UNAVAILABLE}
     * ——又一个"把可达的裁决误报成引擎不可用"（2026-10-16 live 实证）。
     */
    private HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
        relayCallerCredentials(headers);
        return headers;
    }

    // ==================== 3. ABAC 裁决（execute 前置） ====================

    /**
     * 执行前置 ABAC 裁决。
     * <p>调 security-engine {@code POST /api/v1/security/policy-engine/evaluate}，
     * 命中 allow 才放行。security-engine 不可用（连接/调用异常）时<b>不放行</b>（§2.4 第 6 条
     * fail-closed 成立），但落不可用态而非策略拒绝：
     * {@code AllowedResult(allowed=false, "SECURITY_ENGINE_UNAVAILABLE", UNEVALUATED)}（§4.7.2），
     * 消费侧禁据此报 403。
     *
     * @param definitionId Pipeline 定义 ID
     * @return 裁决结果
     */
    public AllowedResult evaluateExecute(String definitionId) {
        try {
            Map<String, Object> input = new LinkedHashMap<>();
            input.put("action", "pipeline.execute");
            input.put("resource", "pipeline:" + definitionId);
            input.put("role", "system");

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("policy", "rbac");
            body.put("input", input);

            // 用 HttpHeaders 显式声明 JSON Content-Type + Accept（避免 RestTemplate 默认 XML 序列化）
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, jsonHeaders());

            ResponseEntity<String> response = restTemplate.postForEntity(
                    securityBaseUrl + "/api/v1/security/policy-engine/evaluate",
                    entity, String.class);

            String bodyStr = response.getBody();
            if (bodyStr == null || bodyStr.isEmpty()) {
                log.warn("ABAC evaluate returned empty body: definitionId={}", definitionId);
                return AllowedResult.unavailable("ABAC_EVALUATE_FAILED");
            }
            // 解析 JSON 响应
            Map<String, Object> apiResp = MAPPER.readValue(bodyStr,
                    new TypeReference<Map<String, Object>>() {});
            if (apiResp == null) {
                log.warn("ABAC evaluate returned null: definitionId={}", definitionId);
                return AllowedResult.unavailable("ABAC_EVALUATE_FAILED");
            }
            // ApiResponse 包装：业务数据在 "data" 字段内
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) apiResp.get("data");
            if (data == null || data.isEmpty()) {
                // fallback: 若 data 为空（旧版 security-engine 兼容），尝试直接从顶层取
                data = apiResp;
            }
            AbacEvaluateResponseContract.Verdict verdict =
                    AbacEvaluateResponseContract.verdictOf(data);
            String reason = data.get(AbacEvaluateResponseContract.KEY_FALLBACK) != null
                    ? data.get(AbacEvaluateResponseContract.KEY_FALLBACK).toString()
                    : switch (verdict) {
                        case ALLOW -> "ALLOW";
                        case DENY -> "DENY";
                        case UNEVALUATED -> "ABAC_UNEVALUATED";
                    };
            switch (verdict) {
                case ALLOW -> log.info("ABAC allow pipeline execute: definitionId={}, reason={}",
                        definitionId, reason);
                case DENY -> log.warn("ABAC DENY (策略拒绝) pipeline execute: definitionId={}, reason={}",
                        definitionId, reason);
                case UNEVALUATED -> log.warn("ABAC UNEVALUATED (护栏未评估/不可用，非策略拒绝，§4.7.2) "
                        + "pipeline execute: definitionId={}, reason={}", definitionId, reason);
            }
            return switch (verdict) {
                case ALLOW -> AllowedResult.allow(reason);
                case DENY -> AllowedResult.deny(reason);
                case UNEVALUATED -> AllowedResult.unavailable(reason);
            };
        } catch (Exception e) {
            // security-engine 不可用 → 不可用态（仍不放行，但不冒充"这人没权限"，§4.7.2）
            log.warn("ABAC evaluate failed (fail-closed UNEVALUATED): definitionId={}, error={}",
                    definitionId, e.getMessage());
            return AllowedResult.unavailable("SECURITY_ENGINE_UNAVAILABLE");
        }
    }

    /**
     * 数据治理候选 apply 前置 ABAC 裁决（《补充详细设计 v1.0》§7.5 DATA_CHANGE_APPLY）。
     * <p>与 {@link #evaluateExecute} 同一咽喉（POST /api/v1/security/policy-engine/evaluate），
     * 但 action 为 {@code data.governance.apply}（治理写操作独立策略语义）。
     * security-engine 不可用/裁决 FAILED → 不可用态（fail-closed 不放行，SUP-DATA-02；
     * 与"策略拒绝"分态，§4.7.2）。
     *
     * @param candidateId 候选 ID（resource 引用）
     * @return 裁决结果（allowed=false + reason 供审计留痕）
     */
    public AllowedResult evaluateDataGovernanceApply(String candidateId) {
        try {
            Map<String, Object> input = new LinkedHashMap<>();
            input.put("action", "data.governance.apply");
            input.put("resource", "data_governance_candidate:" + candidateId);
            input.put("role", "system");

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("policy", "rbac");
            body.put("input", input);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, jsonHeaders());

            ResponseEntity<String> response = restTemplate.postForEntity(
                    securityBaseUrl + "/api/v1/security/policy-engine/evaluate",
                    entity, String.class);

            String bodyStr = response.getBody();
            if (bodyStr == null || bodyStr.isEmpty()) {
                log.warn("ABAC evaluate_data_governance_apply returned empty body: candidateId={}", candidateId);
                return AllowedResult.unavailable("ABAC_EVALUATE_FAILED");
            }
            Map<String, Object> apiResp = MAPPER.readValue(bodyStr,
                    new TypeReference<Map<String, Object>>() {});
            if (apiResp == null) {
                return AllowedResult.unavailable("ABAC_EVALUATE_FAILED");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) apiResp.get("data");
            if (data == null || data.isEmpty()) {
                data = apiResp;
            }
            AbacEvaluateResponseContract.Verdict verdict =
                    AbacEvaluateResponseContract.verdictOf(data);
            String reason = data.get(AbacEvaluateResponseContract.KEY_FALLBACK) != null
                    ? data.get(AbacEvaluateResponseContract.KEY_FALLBACK).toString()
                    : switch (verdict) {
                        case ALLOW -> "ALLOW";
                        case DENY -> "DENY";
                        case UNEVALUATED -> "ABAC_UNEVALUATED";
                    };
            switch (verdict) {
                case ALLOW -> log.info("ABAC allow data governance apply: candidateId={}, reason={}",
                        candidateId, reason);
                case DENY -> log.warn("ABAC DENY (策略拒绝) data governance apply: candidateId={}, reason={}",
                        candidateId, reason);
                case UNEVALUATED -> log.warn("ABAC UNEVALUATED (护栏未评估/不可用，非策略拒绝，§4.7.2) "
                        + "data governance apply: candidateId={}, reason={}", candidateId, reason);
            }
            return switch (verdict) {
                case ALLOW -> AllowedResult.allow(reason);
                case DENY -> AllowedResult.deny(reason);
                case UNEVALUATED -> AllowedResult.unavailable(reason);
            };
        } catch (Exception e) {
            // security-engine 不可用 → 不可用态（不放行，但不冒充无权限，§7.5 / §4.7.2）
            log.warn("ABAC evaluate_data_governance_apply failed (fail-closed UNEVALUATED): candidateId={}, error={}",
                    candidateId, e.getMessage());
            return AllowedResult.unavailable("SECURITY_ENGINE_UNAVAILABLE");
        }
    }

    /**
     * 通用策略裁决（H-3 交付①：期间关账财务角色门槛，卷09 §十一；后续数据域写操作权限判定统一走本咽喉）。
     * <p>与 {@link #evaluateExecute} / {@link #evaluateDataGovernanceApply} 同一 policy-engine 端点、
     * 同一响应解析（{@code AbacEvaluateResponseContract#verdictOf} 读 ApiResponse.data）；
     * <b>fail-closed 但不折叠</b>：security-engine 不可用 / 响应异常 /
     * 空响应 ⇒ UNEVALUATED（§2.4 第 6 条 / SUP-DATA-02 不放行；§4.7.2 禁报成 403）。
     *
     * <p>调用方约束：{@code role} 只准透传调用方身份声明（如 UserContext claims 原值），
     * <b>禁</b>在数据域内做"角色名 → 权限"硬编码映射——角色语义归 security-engine 策略承载。
     *
     * @param action   策略动作名（如 {@code data.period.close}）
     * @param resource 资源引用（如 {@code ecos_data_period_status:2025-01}）
     * @param role     调用方角色声明原值（可空 → 传空串，由策略裁决）
     * @return 裁决结果（不放行时 verdict 区分 DENY／UNEVALUATED，reason 供审计留痕与文案）
     */
    public AllowedResult evaluate(String action, String resource, String role) {
        try {
            Map<String, Object> input = new LinkedHashMap<>();
            input.put("action", action);
            input.put("resource", resource);
            input.put("role", role == null ? "" : role);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("policy", "rbac");
            body.put("input", input);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, jsonHeaders());

            ResponseEntity<String> response = restTemplate.postForEntity(
                    securityBaseUrl + "/api/v1/security/policy-engine/evaluate",
                    entity, String.class);

            String bodyStr = response.getBody();
            if (bodyStr == null || bodyStr.isEmpty()) {
                log.warn("ABAC evaluate({}) returned empty body (fail-closed UNEVALUATED)", action);
                return AllowedResult.unavailable("ABAC_EVALUATE_FAILED");
            }
            Map<String, Object> apiResp = MAPPER.readValue(bodyStr,
                    new TypeReference<Map<String, Object>>() {});
            if (apiResp == null) {
                return AllowedResult.unavailable("ABAC_EVALUATE_FAILED");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) apiResp.get("data");
            if (data == null || data.isEmpty()) {
                data = apiResp;
            }
            AbacEvaluateResponseContract.Verdict verdict =
                    AbacEvaluateResponseContract.verdictOf(data);
            String reason = data.get(AbacEvaluateResponseContract.KEY_FALLBACK) != null
                    ? data.get(AbacEvaluateResponseContract.KEY_FALLBACK).toString()
                    : switch (verdict) {
                        case ALLOW -> "ALLOW";
                        case DENY -> "DENY";
                        case UNEVALUATED -> "ABAC_UNEVALUATED";
                    };
            switch (verdict) {
                case ALLOW -> log.info("ABAC allow {}: resource={}, reason={}", action, resource, reason);
                case DENY -> log.warn("ABAC DENY (策略拒绝) {}: resource={}, reason={}", action, resource, reason);
                case UNEVALUATED -> log.warn("ABAC UNEVALUATED (护栏未评估/不可用，非策略拒绝，§4.7.2) "
                        + "{}: resource={}, reason={}", action, resource, reason);
            }
            return switch (verdict) {
                case ALLOW -> AllowedResult.allow(reason);
                case DENY -> AllowedResult.deny(reason);
                case UNEVALUATED -> AllowedResult.unavailable(reason);
            };
        } catch (Exception e) {
            // security-engine 不可用 → 不可用态（§2.4 第 6 条 fail-closed 仍成立：不放行；
            // 但 §4.7.2 要求它与"无权限"分态，故不得再报成 403——关账场景由调用方落 503）
            log.warn("ABAC evaluate({}) failed (fail-closed UNEVALUATED): resource={}, error={}",
                    action, resource, e.getMessage());
            return AllowedResult.unavailable("SECURITY_ENGINE_UNAVAILABLE");
        }
    }

    /**
     * ABAC 裁决结果。
     *
     * <p><b>#142/B4-3e-13（差距 D-B4-44）</b>：载荷自此带第三态位 {@code verdict}。收口前只有
     * 布尔 {@code allowed}，于是「策略说不行」与「护栏没说话（security-engine 不可用）」
     * 同值，四个消费点一律转 403 —— 违 §4.7.2「不可用 ≠ 无权限」。</p>
     *
     * <p>生产侧一律经 {@link #allow}／{@link #deny}／{@link #unavailable} 三个工厂，
     * <b>禁</b>直接调三参规范构造器（绕开工厂＝状态位可以凭空丢失，本类的
     * {@code allowed} 与 {@code verdict} 只在工厂里保持互推）。消费侧判据取
     * {@link #verdict()}，{@link #allowed()} 只留给"放行/不放行"的二值展示，
     * 不得据它反推拒绝原因。</p>
     */
    public record AllowedResult(boolean allowed, String reason,
                                AbacEvaluateResponseContract.Verdict verdict) {

        public static AllowedResult allow(String reason) {
            return new AllowedResult(true, reason, AbacEvaluateResponseContract.Verdict.ALLOW);
        }

        /** 策略显式拒绝 —— 护栏说话了，结论是不行。 */
        public static AllowedResult deny(String reason) {
            return new AllowedResult(false, reason, AbacEvaluateResponseContract.Verdict.DENY);
        }

        /** 护栏不可用／未评估 —— 不可用 ≠ 无权限（§4.7.2），消费侧禁转 403。 */
        public static AllowedResult unavailable(String reason) {
            return new AllowedResult(false, reason, AbacEvaluateResponseContract.Verdict.UNEVALUATED);
        }
    }
}
