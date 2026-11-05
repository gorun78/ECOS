package com.chinacreator.gzcm.engine.ai.dto;

import com.chinacreator.gzcm.engine.ai.service.AgentEvaluator.DimensionScores;

/**
 * 5 维评分：{@code toolAccuracy/keywordCoverage/answerQuality/executionEfficiency/hallucinationResist}
 * （键名与前端既有 camelCase 契约一致）。
 */
public record AgentEvalDimensionsVO(
        int toolAccuracy,
        int keywordCoverage,
        int answerQuality,
        int executionEfficiency,
        int hallucinationResist) {

    public static AgentEvalDimensionsVO of(DimensionScores dims) {
        return new AgentEvalDimensionsVO(
                dims.getToolAccuracy(),
                dims.getKeywordCoverage(),
                dims.getAnswerQuality(),
                dims.getExecutionEfficiency(),
                dims.getHallucinationResist());
    }
}
