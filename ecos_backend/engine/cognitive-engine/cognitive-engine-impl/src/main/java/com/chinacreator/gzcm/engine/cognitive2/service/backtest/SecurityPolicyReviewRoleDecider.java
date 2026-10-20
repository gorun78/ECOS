package com.chinacreator.gzcm.engine.cognitive2.service.backtest;

import com.chinacreator.gzcm.common.security.abac.AbacEvaluateResponseContract;
import com.chinacreator.gzcm.common.security.guardrail.GuardrailDecisionContract;
import com.chinacreator.gzcm.runtime.access.endpoint.ServiceEndpointResolver;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link BacktestReviewRoleDecider} 的 security-engine REST 适配器（详设-09 v1.2 §十一 H-4 要点 4）。
 *
 * <p><b>真实契约</b>（以代码为准，非文档臆测）：
 * {@code POST /api/v1/security/policy-engine/evaluate}，请求体
 * {@code {"policy": <策略名>, "input": {...}}}（{@code PolicyEngineController#evaluate} 的
 * {@code Map} 形参），响应信封 {@code ApiResponse<Map>} 的 {@code data} 段含
 * {@code allow / policyRef / inputDigest / obligations[]}（{@code OpaPolicyService#evaluate} 产出）。
 * 复审角色只准从 {@code obligations[]} 里<b>透传</b>（键 {@code reviewerRole}，兼容 {@code role}）；
 * 认知侧不解释、不映射、不兜底（禁"角色名→权限"硬编码）。</p>
 *
 * <p><b>fail-closed 三态</b>：OPA 不可用（服务端自身 fail-close 返 {@code allow=false} +
 * {@code fallback=DENY_OPA_DOWN}）/ 网络不可达 / 当前请求无凭证可透传 ⇒
 * 一律 {@link ReviewRoleDecision#unavailable}（上层 503，禁默认放行）；
 * 明确 DENY ⇒ {@code deny}（上层 403）。两者在结局词表上分列，不混为一谈（DG-6）。</p>
 *
 * <p><b>底座收口</b>：复用 runtime 共享客户端 {@code cognitiveRestTemplate} +
 * {@link ServiceEndpointResolver} 两态寻址（铁律 §2.5，禁 new RestTemplate；
 * {@code /api/v1/security} 的 route-manifest owner = sysman，故按 sysman 承流基址解析）。</p>
 */
@Component
public class SecurityPolicyReviewRoleDecider implements BacktestReviewRoleDecider {

    private static final Logger log = LoggerFactory.getLogger(SecurityPolicyReviewRoleDecider.class);

    /** 裁决端点（属主 = security-engine，路径以 impl 代码为唯一事实源）。 */
    static final String PATH_POLICY_EVALUATE = "/api/v1/security/policy-engine/evaluate";

    /** route-manifest 里 {@code /api/v1/security} 的 owner 服务名。 */
    static final String SECURITY_OWNER_SERVICE = "sysman";

    /**
     * 请求 OPA 评估的策略名（进 {@code body.policy}，与 workspace 客户端传 {@code "rbac"} 同形态）。
     * <p>这是"问哪条策略"的选择器，<b>不是</b>角色→权限映射表；策略内容由 security-engine 侧维护。</p>
     */
    static final String POLICY_NAME = "fc_backtest_review";

    /** 裁决 obligations 中承载复审角色的键（透传，不改写、不大写化、不校验枚举）。 */
    static final List<String> ROLE_OBLIGATION_KEYS = List.of("reviewerRole", "reviewer_role", "role");

    private final RestTemplate restTemplate;
    private final ServiceEndpointResolver endpointResolver;

    public SecurityPolicyReviewRoleDecider(@Qualifier("cognitiveRestTemplate") RestTemplate restTemplate,
                                           ServiceEndpointResolver endpointResolver) {
        this.restTemplate = restTemplate;
        this.endpointResolver = endpointResolver;
    }

    @Override
    public ReviewRoleDecision decide(ReviewRoleQuery query) {
        if (query == null) {
            return ReviewRoleDecision.unavailable(null, "review-role query is null (fail-closed)");
        }
        String base = endpointResolver
                .resolve(SECURITY_OWNER_SERVICE, ServiceEndpointResolver.RouteMode.MONOLITH)
                .orElse(null);
        if (base == null) {
            return ReviewRoleDecision.unavailable(PATH_POLICY_EVALUATE,
                    "route-manifest 未登记服务 " + SECURITY_OWNER_SERVICE + "，裁决基址无法解析（fail-closed）");
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String authorization = currentAuthorization();
        if (authorization == null) {
            // 无凭证出站会被 Spring Security 默认 DENY 吞成"策略拒绝"，那是误 DENY 不是裁决；
            // 如实按不可用处理（503），禁把链路缺陷伪装成业务拒绝（X-44 / DG-6）。
            return ReviewRoleDecision.unavailable(PATH_POLICY_EVALUATE,
                    "no caller credential on outbound call: security-engine would answer default-DENY(403)"
                    + " which is not a policy decision; fail-closed without creating a task");
        }
        headers.set(HttpHeaders.AUTHORIZATION, authorization);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("policy", POLICY_NAME);
        body.put("input", inputOf(query));
        try {
            ResponseEntity<JsonNode> resp = restTemplate.exchange(base + PATH_POLICY_EVALUATE,
                    HttpMethod.POST, new HttpEntity<>(body, headers), JsonNode.class);
            return parse(resp.getBody(), query);
        } catch (RestClientException e) {
            log.error("security-engine 复审裁决调用失败: {}", e.getMessage());
            return ReviewRoleDecision.unavailable(PATH_POLICY_EVALUATE,
                    "security-engine evaluate call failed: " + e.getClass().getSimpleName());
        }
    }

    private Map<String, Object> inputOf(ReviewRoleQuery q) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("action", q.action());
        input.put("evaluationId", q.evaluationId());
        input.put("runId", q.runId());
        input.put("scenarioId", q.scenarioId());
        input.put("tenantId", q.tenantId());
        input.put("orgId", q.orgId());
        input.put("breachedItems", q.breachedItems());
        input.put("measuredValues", q.measuredValues());
        input.put("thresholdSources", q.thresholdSources());
        return input;
    }

    ReviewRoleDecision parse(JsonNode envelope, ReviewRoleQuery q) {
        if (envelope == null) {
            return ReviewRoleDecision.unavailable(PATH_POLICY_EVALUATE,
                    "empty response body from policy-engine (treated as unavailable, never as allow)");
        }
        JsonNode data = envelope.path("data");
        if (data.isMissingNode() || data.isNull()) {
            return ReviewRoleDecision.unavailable(PATH_POLICY_EVALUATE,
                    "policy-engine envelope without data (contract break)");
        }
        String decisionRef = data.path(GuardrailDecisionContract.KEY_POLICY_REF).asText(POLICY_NAME)
                + "#" + data.path("inputDigest").asText("-")
                + "@" + PATH_POLICY_EVALUATE;
        if (AbacEvaluateResponseContract.VALUE_SOURCE_FALLBACK.equalsIgnoreCase(
                    data.path(AbacEvaluateResponseContract.KEY_SOURCE).asText(""))
                || AbacEvaluateResponseContract.VALUE_FALLBACK_DENY_OPA_DOWN.equalsIgnoreCase(
                    data.path(AbacEvaluateResponseContract.KEY_FALLBACK).asText(""))) {
            return ReviewRoleDecision.unavailable(decisionRef,
                    "policy engine answered fail-close fallback (DENY_OPA_DOWN): unavailable, not a policy DENY");
        }
        if (!data.path(GuardrailDecisionContract.KEY_ALLOW).asBoolean(false)) {
            return ReviewRoleDecision.deny(decisionRef,
                    "policy denied review assignment" + deniedReasonOf(data));
        }
        String role = roleFromObligations(data);
        if (role == null) {
            return new ReviewRoleDecision(true, false, null, decisionRef,
                    "policy allowed but returned no reviewer role (no local role inference allowed)");
        }
        return ReviewRoleDecision.allow(role, decisionRef, "review role assigned by security-engine policy");
    }

    /** 角色只从 obligations 透传；认知侧零映射。 */
    private String roleFromObligations(JsonNode data) {
        JsonNode obligations = data.path(GuardrailDecisionContract.KEY_OBLIGATIONS);
        if (!obligations.isArray()) {
            return null;
        }
        for (JsonNode o : obligations) {
            for (String key : ROLE_OBLIGATION_KEYS) {
                JsonNode v = o.path(key);
                if (!v.isMissingNode() && !v.isNull() && !v.asText().isBlank()) {
                    return v.asText();
                }
            }
        }
        return null;
    }

    private String deniedReasonOf(JsonNode data) {
        JsonNode obligations = data.path(GuardrailDecisionContract.KEY_OBLIGATIONS);
        if (obligations.isArray()) {
            for (JsonNode o : obligations) {
                JsonNode r = o.path(GuardrailDecisionContract.KEY_DENIED_REASON);
                if (!r.isMissingNode() && !r.isNull() && !r.asText().isBlank()) {
                    return " deniedReason=" + r.asText();
                }
            }
        }
        return "";
    }

    /** 透传调用方 Authorization 头（与 workspace/identity 安全客户端同纪律；无请求上下文 ⇒ null）。 */
    private String currentAuthorization() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            String value = attrs.getRequest().getHeader(HttpHeaders.AUTHORIZATION);
            return value == null || value.isBlank() ? null : value;
        }
        return null;
    }
}
