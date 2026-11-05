package com.chinacreator.gzcm.engine.ai.dto;

import com.chinacreator.gzcm.engine.ai.service.AgentEvaluator.EvalReport;

/**
 * 评估摘要（列表条目）：{@code evalId/questionSet/status/totalQuestions/avgScore/grade/
 * startedAt/completedAt/totalDurationMs}。
 */
public record AgentEvalSummaryVO(
        String evalId,
        String questionSet,
        String status,
        int totalQuestions,
        double avgScore,
        String grade,
        String startedAt,
        String completedAt,
        long totalDurationMs) {

    public static AgentEvalSummaryVO of(EvalReport report) {
        return new AgentEvalSummaryVO(
                report.getEvalId(),
                report.getQuestionSet(),
                report.getStatus(),
                report.getTotalQuestions(),
                report.getAvgScore(),
                report.getGrade(),
                report.getStartedAt(),
                report.getCompletedAt(),
                report.getTotalDurationMs());
    }
}
