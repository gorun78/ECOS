package com.chinacreator.gzcm.engine.ai.controller;

import java.util.ArrayList;
import java.util.List;

import com.chinacreator.gzcm.engine.ai.dto.AgentEvalDimensionsVO;
import com.chinacreator.gzcm.engine.ai.dto.AgentEvalItemVO;
import com.chinacreator.gzcm.engine.ai.dto.AgentEvalListVO;
import com.chinacreator.gzcm.engine.ai.dto.AgentEvalReportVO;
import com.chinacreator.gzcm.engine.ai.dto.AgentEvalRunVO;
import com.chinacreator.gzcm.engine.ai.dto.AgentEvalSummaryVO;
import com.chinacreator.gzcm.engine.ai.service.AgentEvaluator.DimensionScores;
import com.chinacreator.gzcm.engine.ai.service.AgentEvaluator.EvalItemResult;
import com.chinacreator.gzcm.engine.ai.service.AgentEvaluator.EvalReport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Agent 评估端点 wire 保真（DI-2 #147EH）。
 *
 * <p>启动回执 3 键；摘要 9 键；报告 10 基键 + {@code dimensionAvgs/items/hint} 条件尾键；
 * 单题明细 10 键 + {@code dimensions} 条件尾键；rawScore 两位小数。</p>
 */
class AgentEvalWireShapeTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static EvalItemResult item() {
        DimensionScores dims = new DimensionScores();
        dims.setToolAccuracy(5);
        dims.setKeywordCoverage(3);
        dims.setAnswerQuality(4);
        dims.setExecutionEfficiency(4);
        dims.setHallucinationResist(5);
        EvalItemResult item = new EvalItemResult();
        item.setQuestionId("f1");
        item.setQuestion("问题");
        item.setCategory("finance");
        item.setScore(4);
        item.setRawScore(4.1567);
        item.setAgentAnswer("答案");
        item.setActualTool("query_database");
        item.setMatchedKeywords(List.of("k1"));
        item.setJudgeReason("理由");
        item.setDurationMs(2340);
        item.setDimensions(dims);
        return item;
    }

    @Test
    @DisplayName("启动回执 3 键；摘要 9 键；列表 2 键")
    void runSummaryListKeepKeys() throws Exception {
        assertEquals(List.of("evalId", "questionSet", "message"),
                fieldNames(MAPPER.valueToTree(new AgentEvalRunVO("e1", "default", "msg"))));

        EvalReport report = new EvalReport();
        report.setEvalId("e1");
        report.setQuestionSet("finance_20");
        report.setStatus("completed");
        report.setTotalQuestions(20);
        report.setAvgScore(3.85);
        report.setGrade("A");
        report.setStartedAt("s");
        report.setCompletedAt("c");
        report.setTotalDurationMs(45200L);

        JsonNode summary = MAPPER.valueToTree(AgentEvalSummaryVO.of(report));
        assertEquals(List.of("evalId", "questionSet", "status", "totalQuestions", "avgScore",
                "grade", "startedAt", "completedAt", "totalDurationMs"), fieldNames(summary));

        JsonNode list = MAPPER.valueToTree(new AgentEvalListVO(1, List.of(AgentEvalSummaryVO.of(report))));
        assertEquals(List.of("total", "items"), fieldNames(list));
    }

    @Test
    @DisplayName("报告：10 基键 + dimensionAvgs/items 条件键；running 追加 hint 尾键")
    void reportKeepsConditionalKeys() throws Exception {
        EvalReport report = new EvalReport();
        report.setEvalId("e1");
        report.setStatus("running");
        DimensionScores avgs = new DimensionScores();
        avgs.setToolAccuracy(4);
        avgs.setKeywordCoverage(3);
        avgs.setAnswerQuality(4);
        avgs.setExecutionEfficiency(4);
        avgs.setHallucinationResist(5);
        report.setDimensionAvgs(avgs);
        List<EvalItemResult> items = new ArrayList<>();
        items.add(item());
        report.setItems(items);

        JsonNode data = MAPPER.valueToTree(AgentEvalReportVO.of(report));
        assertEquals(List.of("evalId", "questionSet", "status", "totalQuestions", "totalScore",
                "avgScore", "grade", "totalDurationMs", "startedAt", "completedAt",
                "dimensionAvgs", "items", "hint"), fieldNames(data));
        assertEquals(4, data.get("dimensionAvgs").get("toolAccuracy").asInt());
        assertEquals("评估仍在运行中，请稍后重新查询", data.get("hint").asText());

        EvalReport plain = new EvalReport();
        plain.setEvalId("e2");
        plain.setStatus("completed");
        JsonNode bare = MAPPER.valueToTree(AgentEvalReportVO.of(plain));
        assertEquals(10, bare.size(), "无均分/明细/提示时只剩 10 基键");
        assertFalse(bare.has("dimensionAvgs"));
    }

    @Test
    @DisplayName("单题明细：10 键 + dimensions 尾键；rawScore 两位小数")
    void itemKeepsTailDimensionsAndRounding() throws Exception {
        JsonNode row = MAPPER.valueToTree(AgentEvalItemVO.of(item()));

        assertEquals(List.of("questionId", "question", "category", "score", "rawScore",
                "agentAnswer", "actualTool", "matchedKeywords", "judgeReason", "durationMs",
                "dimensions"), fieldNames(row));
        assertEquals(4.16, row.get("rawScore").asDouble(), 1e-9);
        assertEquals(List.of("toolAccuracy", "keywordCoverage", "answerQuality",
                "executionEfficiency", "hallucinationResist"),
                fieldNames(row.get("dimensions")));
        assertEquals(5, row.get("dimensions").get("hallucinationResist").asInt());
    }

    private static List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }
}
