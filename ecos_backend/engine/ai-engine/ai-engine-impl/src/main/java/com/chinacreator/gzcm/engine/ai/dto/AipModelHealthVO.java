package com.chinacreator.gzcm.engine.ai.dto;

/**
 * AIP 模型健康检查回执：{@code modelId/status/latencyMs/checkedAt}
 * （当前为演示读数：恒 healthy / 120ms，与旧 Map 同值）。
 */
public record AipModelHealthVO(String modelId, String status, int latencyMs, String checkedAt) {
}
