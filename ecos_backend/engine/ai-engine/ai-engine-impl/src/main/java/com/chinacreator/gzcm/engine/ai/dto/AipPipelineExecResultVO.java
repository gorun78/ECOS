package com.chinacreator.gzcm.engine.ai.dto;

/** Pipeline 执行回执：{@code pipelineId/status/message/executedAt}。 */
public record AipPipelineExecResultVO(
        String pipelineId,
        String status,
        String message,
        String executedAt) {
}
