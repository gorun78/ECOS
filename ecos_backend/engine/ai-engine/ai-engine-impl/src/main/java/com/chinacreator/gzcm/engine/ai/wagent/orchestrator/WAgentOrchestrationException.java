package com.chinacreator.gzcm.engine.ai.wagent.orchestrator;

import com.chinacreator.gzcm.engine.ai.wagent.WAgentErrorCodes.WAgentErrorCode;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * W Agent 编排域统一业务异常（详细设计-10 §5.3 错误码出口的载体）。
 *
 * <p>只携带 {@link WAgentErrorCode}（wireCode/HTTP 状态/消息模板单源 = {@code WAgentErrorCodes}）
 * 与结构化 extras（如幂等命中时的既有 {@code runId}、状态机冲突时的当前状态）；
 * <b>禁</b>把异常堆栈或依赖原文塞进响应体（X-49 同类预防，消息一律经
 * {@link WAgentErrorCode#renderMessage} 渲染）。</p>
 *
 * <p>{@code web/} 面由 {@code WAgentOrchestrationAdvice} 统一映射为
 * {@code ApiResponse(code=wireCode, errorCode, message, traceId)}。</p>
 */
public class WAgentOrchestrationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final WAgentErrorCode errorCode;
    private final Map<String, Object> extras;

    public WAgentOrchestrationException(WAgentErrorCode errorCode) {
        this(errorCode, null, Collections.emptyMap());
    }

    public WAgentOrchestrationException(WAgentErrorCode errorCode, String diagnostic,
                                        Map<String, Object> extras) {
        // 消息只作服务端日志诊断线索；对外文案一律 Advice 经 renderMessage(traceId) 渲染
        super(errorCode.wireCode() + (diagnostic == null || diagnostic.isBlank() ? "" : ": " + diagnostic));
        this.errorCode = errorCode;
        this.extras = extras == null ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(extras));
    }

    public WAgentErrorCode errorCode() {
        return errorCode;
    }

    /** 结构化补充（禁含敏感原文）：如 {@code existingRunId}/{@code currentState}/{@code allowedOps}。 */
    public Map<String, Object> extras() {
        return extras;
    }

    public static WAgentOrchestrationException stateConflict(String from, String to) {
        Map<String, Object> extras = new LinkedHashMap<>();
        extras.put("currentState", from);
        extras.put("attemptedTransition", to);
        return new WAgentOrchestrationException(WAgentErrorCode.E_WA_STATE,
                "非法迁移 " + from + " -> " + to, extras);
    }

    public static WAgentOrchestrationException validation(String diagnostic) {
        return new WAgentOrchestrationException(WAgentErrorCode.E_VALIDATION, diagnostic, null);
    }

    public static WAgentOrchestrationException notFound() {
        // 文案与 403 不可区分（防资源枚举，§5.3）
        return new WAgentOrchestrationException(WAgentErrorCode.E_NOT_FOUND, null, null);
    }

    public static WAgentOrchestrationException budget(String diagnostic) {
        return new WAgentOrchestrationException(WAgentErrorCode.E_WA_BUDGET, diagnostic, null);
    }

    public static WAgentOrchestrationException policy(String diagnostic) {
        return new WAgentOrchestrationException(WAgentErrorCode.E_POLICY, diagnostic, null);
    }

    public static WAgentOrchestrationException caliberLocked(String goalId) {
        Map<String, Object> extras = new LinkedHashMap<>();
        extras.put("goalId", goalId);
        return new WAgentOrchestrationException(WAgentErrorCode.ECOS_WA_CALIBER_LOCKED,
                "Goal 已被 Run 引用，禁就地更新: " + goalId, extras);
    }

    public static WAgentOrchestrationException dependencyUnavailable(String service) {
        Map<String, Object> extras = new LinkedHashMap<>();
        extras.put("service", service);
        return new WAgentOrchestrationException(WAgentErrorCode.E_WA_DEP, service, extras);
    }
}
