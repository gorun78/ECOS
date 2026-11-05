package com.chinacreator.gzcm.engine.ai.dto;

import com.chinacreator.gzcm.engine.ai.service.AgentEvaluator.EvalItemResult;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;

/**
 * 单题评分明细：{@code questionId/question/category/score/rawScore/agentAnswer/actualTool/
 * matchedKeywords/judgeReason/durationMs[/dimensions]}。
 *
 * <p>{@code rawScore} 保留两位（旧实现四舍五入同口径）；{@code dimensions} 仅非空时以<b>尾键</b>出现
 * （与旧 Map 追加顺序一致）。</p>
 */
@JsonPropertyOrder({"questionId", "question", "category", "score", "rawScore", "agentAnswer",
        "actualTool", "matchedKeywords", "judgeReason", "durationMs", "dimensions"})
public record AgentEvalItemVO(
        String questionId,
        String question,
        String category,
        int score,
        double rawScore,
        String agentAnswer,
        String actualTool,
        List<String> matchedKeywords,
        String judgeReason,
        long durationMs,
        @JsonInclude(JsonInclude.Include.NON_NULL) AgentEvalDimensionsVO dimensions) {

    public static AgentEvalItemVO of(EvalItemResult item) {
        return new AgentEvalItemVO(
                item.getQuestionId(),
                item.getQuestion(),
                item.getCategory(),
                item.getScore(),
                Math.round(item.getRawScore() * 100.0) / 100.0,
                item.getAgentAnswer(),
                item.getActualTool(),
                item.getMatchedKeywords(),
                item.getJudgeReason(),
                item.getDurationMs(),
                item.getDimensions() == null ? null : AgentEvalDimensionsVO.of(item.getDimensions()));
    }
}
