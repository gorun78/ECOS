package com.chinacreator.gzcm.services.agent.runtime.toolrouter.tools;

import com.chinacreator.gzcm.common.security.abac.AbacEvaluateResponseContract;
import com.chinacreator.gzcm.common.security.guardrail.GuardrailDecisionContract;
import com.chinacreator.gzcm.services.agent.runtime.toolrouter.AgentToolEndpointResolver;
import com.chinacreator.gzcm.services.agent.runtime.toolrouter.ToolExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * {@code CheckPermission} 工具腿 —— 向 security-engine 取一次 ABAC 裁决，
 * 并把裁决<b>归一成三态载荷</b>后交回调用方（#142/B4-3e-8，收口差距 <b>D-B4-43</b>）。
 *
 * <p><b>收口前两件事都错</b>：①基址 {@code http://localhost:8080} 整串内联在请求点上，
 * 绕开 route-manifest 两态寻址单源；②把 {@code postForObject} 的<b>应答原始对象</b>
 * 直接透传给 LLM —— 原始 envelope 里除了裁决还有义务段、策略引用与兜底标记，
 * 模型拿到的是"看起来像结论的一坨字段"，而三态里最要紧的
 * {@link AbacEvaluateResponseContract.Verdict#UNEVALUATED}（护栏没说话）
 * 在原文里根本没有对应字面量，只会被读成"应答正常"。</p>
 *
 * <p><b>现口径</b>：基址经 {@link AgentToolEndpointResolver}（覆盖项 → route-manifest → 空），
 * 读数一律经 {@link AbacEvaluateResponseContract}（禁本类内联裁决键），
 * 载荷只留 {@code adjudication}（值取契约枚举名，不另立词表）与 {@code message}
 * （拒绝理由原文 / 不可用理由）。<b>不可用不冒充拒绝</b>：连不上、基址解析不到、
 * 应答无可识别裁决，一律 {@code UNEVALUATED}，由调用侧按 fail-closed 处置；
 * 端点路径与请求入参保持原形（API 只增不改）。</p>
 */
@Component
public class CheckPermissionTool implements ToolExecutor {

    private static final Logger log = LoggerFactory.getLogger(CheckPermissionTool.class);

    /**
     * security-engine 策略裁决端点路径。
     *
     * <p>{@code PolicyEngineController} 同时映射本路径与 {@code /api/v1/security/policy-engine}
     * 别名，两支都在 gateway 限流表登记，故本批不换路径（换路径属行为变更，不在收口基址的范围内）。
     * 写有本字面量的文件即 {@code AbacEvaluateResponseSingleSourceTest} 验收 1 的消费方，
     * 必须逐个登记 —— 本类现属<b>读侧</b>。</p>
     */
    static final String PATH_POLICY_EVALUATE = "/api/v1/policy-engine/evaluate";

    /** 载荷段：护栏三态结论，值一律取 {@link AbacEvaluateResponseContract.Verdict} 枚举名。 */
    static final String KEY_ADJUDICATION = "adjudication";

    /** 载荷段：结论理由（策略签发的拒绝原文，或"护栏不可用"的如实说明）。 */
    static final String KEY_MESSAGE = "message";

    /** 无法发起调用时的如实说明（基址解析不到 / 传输失败，均不属策略拒绝）。 */
    private static final String MSG_GUARDRAIL_UNREACHABLE =
            "护栏未能给出裁决（security-engine 基址不可解析或调用失败），本次操作按 fail-closed 处置";

    /** 应答里没有可识别裁决时的如实说明。 */
    private static final String MSG_VERDICT_ABSENT =
            "护栏应答里没有可识别的裁决（策略未加载或形态漂移），本次操作按 fail-closed 处置";

    private final RestTemplate restTemplate;

    private final AgentToolEndpointResolver endpoints;

    public CheckPermissionTool(RestTemplate restTemplate, AgentToolEndpointResolver endpoints) {
        this.restTemplate = restTemplate;
        this.endpoints = endpoints;
    }

    @Override
    public Object execute(String toolCode, Map<String, Object> params) {
        Optional<String> base = endpoints.securityBaseUrl();
        if (base.isEmpty()) {
            return payload(AbacEvaluateResponseContract.Verdict.UNEVALUATED, MSG_GUARDRAIL_UNREACHABLE);
        }
        Object resp;
        try {
            resp = restTemplate.postForObject(base.get() + PATH_POLICY_EVALUATE, params, Object.class);
        } catch (RestClientException e) {
            log.warn("[agent-tool] CheckPermission 调用 security 裁决腿失败（按不可用处置，不冒充拒绝）: {}",
                    e.getMessage());
            return payload(AbacEvaluateResponseContract.Verdict.UNEVALUATED, MSG_GUARDRAIL_UNREACHABLE);
        }
        return adjudicate(resp);
    }

    @Override
    public boolean supports(String toolCode) {
        return "CheckPermission".equals(toolCode);
    }

    /**
     * 应答 → 三态载荷。
     *
     * <p>取 envelope 的 {@code data} 段交单源读数（与其余读侧同一约定）；
     * 理由文本一律经 {@link GuardrailDecisionContract} 的读数点取，禁在本类内联键名。
     * 载荷不带原始字段：义务段、策略引用与兜底标记属 security 内部形态，
     * 透传给模型等于把键面漂移的风险摊给每一个下游消费者。</p>
     */
    private Map<String, Object> adjudicate(Object resp) {
        Map<String, Object> data = dataSegment(resp);
        AbacEvaluateResponseContract.Verdict verdict = AbacEvaluateResponseContract.verdictOf(data);
        String state = GuardrailDecisionContract.stateOf(data);
        return switch (verdict) {
            case ALLOW -> payload(verdict, state);
            case DENY -> payload(verdict, firstNonBlank(
                    GuardrailDecisionContract.readDeniedReason(
                            GuardrailDecisionContract.obligationsOf(data)), state));
            case UNEVALUATED -> payload(verdict, firstNonBlank(state, MSG_VERDICT_ABSENT));
        };
    }

    /** envelope 的 {@code data} 段；缺段或非对象形态＝空 Map（读数自然落 UNEVALUATED）。 */
    private static Map<String, Object> dataSegment(Object resp) {
        if (!(resp instanceof Map<?, ?> envelope)) {
            return Map.of();
        }
        Object data = envelope.get("data");
        return data instanceof Map<?, ?> m ? cast(m) : Map.of();
    }

    private static Map<String, Object> payload(AbacEvaluateResponseContract.Verdict verdict, String message) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put(KEY_ADJUDICATION, verdict.name());
        if (message != null && !message.isBlank()) {
            out.put(KEY_MESSAGE, message);
        }
        return out;
    }

    private static String firstNonBlank(String primary, String fallbackValue) {
        return primary != null && !primary.isBlank() ? primary : fallbackValue;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> cast(Map<?, ?> m) {
        return (Map<String, Object>) m;
    }
}
