package com.chinacreator.gzcm.workspace.scenario;

import com.chinacreator.gzcm.common.error.FCScenarioErrorCode;

/**
 * 逻辑编排执行服务不可用（2026-11-23 场景智能逻辑承接批）。
 *
 * <p>场景运行 {@code analysisType=PIPELINE} 时，属主 ai-engine 的
 * {@code POST /api/v1/aip/pipelines/{id}/execute} 不可达 / 应答缺 data / 业务码非 0
 * ⇒ 本异常（HTTP 503）。语义 code 复用 {@code COGNITIVE_UNAVAILABLE} 码位：
 * 编排执行宿主即 AI/认知承流面（aiming :18084），与既有
 * {@code available/wagents}、cognitive detect 等腿的 503 口径同族；
 * 本类只换更准确的提示文案，不新增语义位（FC 码表零扩）。
 *
 * <p><b>不吞编排自身的业务失败</b>：节点 failed / 执行超时是属主的正常返回
 * （{@code degraded=true}），不走本异常——那是业务结果，不是通道故障。</p>
 */
public class AipPipelineUnavailableException extends ScenarioDomainException {

    private final String technicalReason;

    public AipPipelineUnavailableException(String reason) {
        super(503, FCScenarioErrorCode.CODE_COGNITIVE_UNAVAILABLE,
                "编排执行服务暂不可用，请稍后重试或凭追踪号联系管理员");
        this.technicalReason = reason;
    }

    public AipPipelineUnavailableException(String reason, Throwable cause) {
        this(reason);
        initCause(cause);
    }

    public String getTechnicalReason() {
        return technicalReason;
    }
}
