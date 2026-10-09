package com.chinacreator.gzcm.workspace.exception;

/**
 * 认知引擎不可用（F07-12 四端点 / 演练，HTTP 503 / COGNITIVE_UNAVAILABLE，A3 禁 200 空体）。
 *
 * <p>X-41：下游原文（reason/cause）只进日志面供回查，不进响应体 message/details。</p>
 */
public class CognitiveEngineUnavailableException extends WorkspaceDomainException {

    private final String technicalReason;

    public CognitiveEngineUnavailableException(String reason) {
        this(reason, null);
    }

    public CognitiveEngineUnavailableException(String reason, Throwable cause) {
        super(503, "COGNITIVE_UNAVAILABLE",
                "认知引擎暂不可用，请稍后重试或凭追踪号联系管理员", cause);
        this.technicalReason = reason;
        putDetail("engine", "cognitive");
    }

    public String getTechnicalReason() {
        return technicalReason;
    }
}
