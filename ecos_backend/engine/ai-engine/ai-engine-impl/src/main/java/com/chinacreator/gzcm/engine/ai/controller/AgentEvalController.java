package com.chinacreator.gzcm.engine.ai.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.ai.service.AgentEvaluator;
import com.chinacreator.gzcm.engine.ai.service.AgentEvaluator.DimensionScores;
import com.chinacreator.gzcm.engine.ai.service.AgentEvaluator.EvalItemResult;
import com.chinacreator.gzcm.engine.ai.service.AgentEvaluator.EvalReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Agent 评估控制器 — 标准问题集5维打分。
 *
 * <h3>端点</h3>
 * <ul>
 *   <li>POST /api/v1/aip/eval/run          — 启动评估 → 返回 evalId</li>
 *   <li>GET  /api/v1/aip/eval/list         — 列出所有评估报告摘要</li>
 *   <li>GET  /api/v1/aip/eval/{id}/report   — 查询评估报告（含5维评分明细）</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/aip/eval")
public class AgentEvalController {

    private static final Logger log = LoggerFactory.getLogger(AgentEvalController.class);

    @Autowired
    private AgentEvaluator agentEvaluator;

    // ═══════════════════════════════════════════════════════════════
    //  POST /api/v1/aip/eval/run — 启动评估
    // ═══════════════════════════════════════════════════════════════

    /**
     * 启动 Agent 评估 — 异步执行，立即返回 evalId。
     * 支持指定问题集：{"questionSet": "finance_20"}。
     *
     * <p>可用问题集: default, finance_20, legal_20, compliance_20</p>
     *
     * <p>返回示例：</p>
     * <pre>
     * {
     *   "code": 200,
     *   "data": {
     *     "evalId": "eval-a1b2c3d4e5f6",
     *     "questionSet": "finance_20",
     *     "message": "评估已启动，通过 GET /api/v1/aip/eval/{evalId}/report 查询结果"
     *   }
     * }
     * </pre>
     */
    @PostMapping("/run")
    public ApiResponse<Map<String, Object>> runEvaluation(@RequestBody(required = false) Map<String, Object> body) {
        try {
            String questionSet = null;
            if (body != null && body.containsKey("questionSet")) {
                questionSet = String.valueOf(body.get("questionSet"));
            }
            String evalId = agentEvaluator.startEvaluation(questionSet);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("evalId", evalId);
            data.put("questionSet", questionSet != null ? questionSet : "default");
            data.put("message", "评估已启动，通过 GET /api/v1/aip/eval/" + evalId + "/report 查询结果");

            log.info("Eval run triggered: evalId={} questionSet={}", evalId, questionSet);
            return ApiResponse.success(data);
        } catch (Exception e) {
            log.error("Failed to start evaluation", e);
            return ApiResponse.internalError("启动评估失败: " + e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  GET /api/v1/aip/eval/list — 列出所有评估
    // ═══════════════════════════════════════════════════════════════

    /**
     * 列出所有评估报告摘要（按启动时间倒序）。
     *
     * <p>返回示例：</p>
     * <pre>
     * {
     *   "code": 200,
     *   "data": {
     *     "total": 3,
     *     "items": [
     *       {
     *         "evalId": "eval-a1b2c3d4e5f6",
     *         "questionSet": "finance_20",
     *         "status": "completed",
     *         "totalQuestions": 20,
     *         "avgScore": 3.85,
     *         "grade": "A",
     *         "startedAt": "2026-08-06T...",
     *         "completedAt": "2026-08-06T...",
     *         "totalDurationMs": 45200
     *       }
     *     ]
     *   }
     * }
     * </pre>
     */
    @GetMapping("/list")
    public ApiResponse<Map<String, Object>> listEvaluations() {
        try {
            List<EvalReport> reports = agentEvaluator.listReports();

            List<Map<String, Object>> items = new ArrayList<>();
            for (EvalReport report : reports) {
                Map<String, Object> summary = new LinkedHashMap<>();
                summary.put("evalId", report.getEvalId());
                summary.put("questionSet", report.getQuestionSet());
                summary.put("status", report.getStatus());
                summary.put("totalQuestions", report.getTotalQuestions());
                summary.put("avgScore", report.getAvgScore());
                summary.put("grade", report.getGrade());
                summary.put("startedAt", report.getStartedAt());
                summary.put("completedAt", report.getCompletedAt());
                summary.put("totalDurationMs", report.getTotalDurationMs());
                items.add(summary);
            }

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("total", items.size());
            data.put("items", items);

            return ApiResponse.success(data);
        } catch (Exception e) {
            log.error("Failed to list evaluations", e);
            return ApiResponse.internalError("查询评估列表失败: " + e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  GET /api/v1/aip/eval/{id}/report — 查询评估报告
    // ═══════════════════════════════════════════════════════════════

    /**
     * 查询评估报告（包含5维评分明细）。
     *
     * <p>返回示例：</p>
     * <pre>
     * {
     *   "code": 200,
     *   "data": {
     *     "evalId": "eval-a1b2c3d4e5f6",
     *     "questionSet": "finance_20",
     *     "status": "completed",
     *     "totalQuestions": 20,
     *     "totalScore": 77.0,
     *     "avgScore": 3.85,
     *     "grade": "A",
     *     "totalDurationMs": 45200,
     *     "dimensionAvgs": {
     *       "toolAccuracy": 4, "keywordCoverage": 3, "answerQuality": 4,
     *       "executionEfficiency": 4, "hallucinationResist": 5
     *     },
     *     "startedAt": "2026-08-06T...",
     *     "completedAt": "2026-08-06T...",
     *     "items": [
     *       {
     *         "questionId": "f1",
     *         "question": "查询供应商表中所有未结清的应付账款记录",
     *         "category": "finance_ap",
     *         "score": 4,
     *         "rawScore": 4.15,
     *         "agentAnswer": "...",
     *         "actualTool": "query_database",
     *         "matchedKeywords": ["应付账款", "未结清"],
     *         "dimensions": {
     *           "toolAccuracy": 5, "keywordCoverage": 3, "answerQuality": 4,
     *           "executionEfficiency": 4, "hallucinationResist": 5
     *         },
     *         "judgeReason": "...",
     *         "durationMs": 2340
     *       }
     *     ]
     *   }
     * }
     * </pre>
     */
    @GetMapping("/{id}/report")
    public ApiResponse<Map<String, Object>> getReport(@PathVariable String id) {
        try {
            EvalReport report = agentEvaluator.getReport(id);

            if (report == null) {
                return ApiResponse.notFound("评估 " + id + " 不存在");
            }

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("evalId", report.getEvalId());
            data.put("questionSet", report.getQuestionSet());
            data.put("status", report.getStatus());
            data.put("totalQuestions", report.getTotalQuestions());
            data.put("totalScore", report.getTotalScore());
            data.put("avgScore", report.getAvgScore());
            data.put("grade", report.getGrade());
            data.put("totalDurationMs", report.getTotalDurationMs());
            data.put("startedAt", report.getStartedAt());
            data.put("completedAt", report.getCompletedAt());

            // 5维汇总均分
            if (report.getDimensionAvgs() != null) {
                data.put("dimensionAvgs", dimensionsToMap(report.getDimensionAvgs()));
            }

            if (report.getItems() != null) {
                data.put("items", report.getItems().stream().map(this::itemToMap).collect(Collectors.toList()));
            }

            // 如果还在运行中，返回提示
            if ("running".equals(report.getStatus())) {
                data.put("hint", "评估仍在运行中，请稍后重新查询");
            }

            return ApiResponse.success(data);
        } catch (Exception e) {
            log.error("Failed to get eval report: id={}", id, e);
            return ApiResponse.internalError("查询评估报告失败: " + e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  私有工具方法
    // ═══════════════════════════════════════════════════════════════

    private Map<String, Object> itemToMap(EvalItemResult item) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("questionId", item.getQuestionId());
        m.put("question", item.getQuestion());
        m.put("category", item.getCategory());
        m.put("score", item.getScore());
        m.put("rawScore", Math.round(item.getRawScore() * 100.0) / 100.0);
        m.put("agentAnswer", item.getAgentAnswer());
        m.put("actualTool", item.getActualTool());
        m.put("matchedKeywords", item.getMatchedKeywords());
        m.put("judgeReason", item.getJudgeReason());
        m.put("durationMs", item.getDurationMs());

        // 5维评分明细
        if (item.getDimensions() != null) {
            m.put("dimensions", dimensionsToMap(item.getDimensions()));
        }

        return m;
    }

    private Map<String, Object> dimensionsToMap(DimensionScores dims) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("toolAccuracy", dims.getToolAccuracy());
        m.put("keywordCoverage", dims.getKeywordCoverage());
        m.put("answerQuality", dims.getAnswerQuality());
        m.put("executionEfficiency", dims.getExecutionEfficiency());
        m.put("hallucinationResist", dims.getHallucinationResist());
        return m;
    }
}
