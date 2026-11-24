package com.chinacreator.gzcm.engine.ai.approval;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AI 编排域统一业务异常（原 {@code wagent.orchestrator.WAgentOrchestrationException}，
 * 2026-11-23 去 wagent 化迁入本包）。
 *
 * <p>只携带 {@link AiOrchestrationErrorCodes.Code}（wireCode/HTTP 状态/消息模板单源）与结构化
 * extras（如幂等命中时的既有 {@code runId}、状态机冲突时的当前状态）；
 * <b>禁</b>把异常堆栈或依赖原文塞进响应体（X-49 同类预防，消息一律经
 * {@link AiOrchestrationErrorCodes.Code#renderMessage} 渲染）。</p>
 */
public class AiOrchestrationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final AiOrchestrationErrorCodes.Code errorCode;
    private final Map<String, Object> extras;

    public AiOrchestrationException(AiOrchestrationErrorCodes.Code errorCode) {
        this(errorCode, null, Collections.emptyMap());
    }

    public AiOrchestrationException(AiOrchestrationErrorCodes.Code errorCode, String diagnostic,
                                    Map<String, Object> extras) {
        // 消息只作服务端日志诊断线索；对外文案一律经 renderMessage(traceId) 渲染
        super(errorCode.wireCode() + (diagnostic == null || diagnostic.isBlank() ? "" : ": " + diagnostic));
        this.errorCode = errorCode;
        this.extras = extras == null ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(extras));
    }

    public AiOrchestrationErrorCodes.Code errorCode() {
        return errorCode;
    }

    /** 结构化补充（禁含敏感原文）：如 {@code existingRunId}/{@code currentState}/{@code allowedOps}。 */
    public Map<String, Object> extras() {
        return extras;
    }

    public static AiOrchestrationException stateConflict(String from, String to) {
        Map<String, Object> extras = new LinkedHashMap<>();
        extras.put("currentState", from);
        extras.put("attemptedTransition", to);
        return new AiOrchestrationException(AiOrchestrationErrorCodes.Code.E_WA_STATE,
                "非法迁移 " + from + " -> " + to, extras);
    }

    public static AiOrchestrationException validation(String diagnostic) {
        return new AiOrchestrationException(AiOrchestrationErrorCodes.Code.E_VALIDATION, diagnostic, null);
    }

    public static AiOrchestrationException notFound() {
        // 文案与 403 不可区分（防资源枚举，§5.3）
        return new AiOrchestrationException(AiOrchestrationErrorCodes.Code.E_NOT_FOUND, null, null);
    }

    public static AiOrchestrationException budget(String diagnostic) {
        return new AiOrchestrationException(AiOrchestrationErrorCodes.Code.E_WA_BUDGET, diagnostic, null);
    }

    public static AiOrchestrationException policy(String diagnostic) {
        return new AiOrchestrationException(AiOrchestrationErrorCodes.Code.E_POLICY, diagnostic, null);
    }

    public static AiOrchestrationException caliberLocked(String goalId) {
        Map<String, Object> extras = new LinkedHashMap<>();
        extras.put("goalId", goalId);
        return new AiOrchestrationException(AiOrchestrationErrorCodes.Code.ECOS_WA_CALIBER_LOCKED,
                "Goal 已被 Run 引用，禁就地更新: " + goalId, extras);
    }

    public static AiOrchestrationException dependencyUnavailable(String service) {
        Map<String, Object> extras = new LinkedHashMap<>();
        extras.put("service", service);
        return new AiOrchestrationException(AiOrchestrationErrorCodes.Code.E_WA_DEP, service, extras);
    }
}
