package com.chinacreator.gzcm.workspace.scenario;

/**
 * 认知引擎不可用（F07-12 / F07-13，HTTP 503，code=COGNITIVE_UNAVAILABLE）。
 *
 * <p>X-41：reason 仅存日志面（经 advice 输出），不进响应体。</p>
 */
public class CognitiveEngineUnavailableException extends ScenarioDomainException {

    private final String technicalReason;

    public CognitiveEngineUnavailableException(String reason) {
        super(503, "COGNITIVE_UNAVAILABLE", "认知引擎暂不可用，请稍后重试或凭追踪号联系管理员");
        this.technicalReason = reason;
    }

    public String getTechnicalReason() {
        return technicalReason;
    }
}
