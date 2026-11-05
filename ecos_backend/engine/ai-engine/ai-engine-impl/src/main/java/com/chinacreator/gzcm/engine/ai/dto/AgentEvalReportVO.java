package com.chinacreator.gzcm.engine.ai.dto;

import com.chinacreator.gzcm.engine.ai.service.AgentEvaluator.EvalReport;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 评估报告：10 基键 + {@code [/dimensionAvgs][/items][/hint]}（条件键 NON_NULL，尾序同旧 Map）。
 */
@JsonPropertyOrder({"evalId", "questionSet", "status", "totalQuestions", "totalScore", "avgScore",
        "grade", "totalDurationMs", "startedAt", "completedAt", "dimensionAvgs", "items", "hint"})
public record AgentEvalReportVO(
        String evalId,
        String questionSet,
        String status,
        int totalQuestions,
        double totalScore,
        double avgScore,
        String grade,
        long totalDurationMs,
        String startedAt,
        String completedAt,
        @JsonInclude(JsonInclude.Include.NON_NULL) AgentEvalDimensionsVO dimensionAvgs,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<AgentEvalItemVO> items,
        @JsonInclude(JsonInclude.Include.NON_NULL) String hint) {

    public static AgentEvalReportVO of(EvalReport report) {
        List<AgentEvalItemVO> items = report.getItems() == null ? null
                : report.getItems().stream().map(AgentEvalItemVO::of).collect(Collectors.toList());
        String hint = "running".equals(report.getStatus())
                ? "评估仍在运行中，请稍后重新查询" : null;
        return new AgentEvalReportVO(
                report.getEvalId(),
                report.getQuestionSet(),
                report.getStatus(),
                report.getTotalQuestions(),
                report.getTotalScore(),
                report.getAvgScore(),
                report.getGrade(),
                report.getTotalDurationMs(),
                report.getStartedAt(),
                report.getCompletedAt(),
                report.getDimensionAvgs() == null ? null
                        : AgentEvalDimensionsVO.of(report.getDimensionAvgs()),
                items,
                hint);
    }
}
