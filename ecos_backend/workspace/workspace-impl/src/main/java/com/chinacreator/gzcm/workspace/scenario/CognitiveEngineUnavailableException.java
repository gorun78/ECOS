package com.chinacreator.gzcm.workspace.scenario;

/**
 * 认知引擎不可用（F07-12 / F07-13，HTTP 503，code=COGNITIVE_UNAVAILABLE）。
 *
 * <p>X-41：reason 仅存日志面（经 advice 输出），不进响应体。</p>
 *
 * <p>#142/B4-3e-41 追加携带 cause 的重载：属主侧把「下游不可用」腿从 {@code IllegalStateException}
 * 改为本载体时，原抛出点的 cause（连接超时、下游 URL 原文堆栈）不能随之丢失——
 * advice 的日志面除 {@code technicalReason} 外仍需堆栈可追。既有单参调用方不受影响。</p>
 */
public class CognitiveEngineUnavailableException extends ScenarioDomainException {

    private final String technicalReason;

    public CognitiveEngineUnavailableException(String reason) {
        super(503, "COGNITIVE_UNAVAILABLE", "认知引擎暂不可用，请稍后重试或凭追踪号联系管理员");
        this.technicalReason = reason;
    }

    public CognitiveEngineUnavailableException(String reason, Throwable cause) {
        this(reason);
        initCause(cause);
    }

    public String getTechnicalReason() {
        return technicalReason;
    }
}
