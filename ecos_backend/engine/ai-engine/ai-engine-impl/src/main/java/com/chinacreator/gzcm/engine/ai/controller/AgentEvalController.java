package com.chinacreator.gzcm.engine.ai.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.ai.service.AgentEvaluator;
import com.chinacreator.gzcm.engine.ai.service.AgentEvaluator.EvalItemResult;
import com.chinacreator.gzcm.engine.ai.service.AgentEvaluator.EvalReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Agent 评估控制器 — 标准问题集打分。
 *
 * <h3>端点</h3>
 * <ul>
 *   <li>POST /api/v1/aip/eval/run          — 启动评估 → 返回 evalId</li>
 *   <li>GET  /api/v1/aip/eval/{id}/report   — 查询评估报告</li>
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
     *
     * <p>请求体（可选）：</p>
     * <pre>
     * {
     *   "questionSet": "default"  // 问题集名称，默认 "default"
     * }
     * </pre>
     *
     * <p>返回示例：</p>
     * <pre>
     * {
     *   "code": 200,
     *   "data": {
     *     "evalId": "eval-a1b2c3d4e5f6",
     *     "message": "评估已启动，通过 GET /api/v1/aip/eval/{evalId}/report 查询结果"
     *   }
     * }
     * </pre>
     */
    @PostMapping("/run")
    public ApiResponse<Map<String, Object>> runEvaluation(@RequestBody(required = false) Map<String, Object> body) {
        try {
            String evalId = agentEvaluator.startEvaluation();

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("evalId", evalId);
            data.put("message", "评估已启动，通过 GET /api/v1/aip/eval/" + evalId + "/report 查询结果");

            log.info("Eval run triggered: evalId={}", evalId);
            return ApiResponse.success(data);
        } catch (Exception e) {
            log.error("Failed to start evaluation", e);
            return ApiResponse.internalError("启动评估失败: " + e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  GET /api/v1/aip/eval/{id}/report — 查询评估报告
    // ═══════════════════════════════════════════════════════════════

    /**
     * 查询评估报告。
     *
     * <p>返回示例：</p>
     * <pre>
     * {
     *   "code": 200,
     *   "data": {
     *     "evalId": "eval-a1b2c3d4e5f6",
     *     "status": "completed",
     *     "totalQuestions": 5,
     *     "totalScore": 22.0,
     *     "avgScore": 4.4,
     *     "grade": "A",
     *     "totalDurationMs": 15420,
     *     "startedAt": "2026-08-03T...",
     *     "completedAt": "2026-08-03T...",
     *     "items": [
     *       {
     *         "questionId": "q1",
     *         "question": "查询所有数据源列表",
     *         "category": "data_query",
     *         "score": 5,
     *         "agentAnswer": "...",
     *         "actualTool": "list_tables",
     *         "matchedKeywords": ["数据源", "表"],
     *         "judgeReason": "得分: 5/5. 预期工具: list_tables, 实际工具: list_tables. 匹配关键词: [数据源, 表].",
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
            data.put("status", report.getStatus());
            data.put("totalQuestions", report.getTotalQuestions());
            data.put("totalScore", report.getTotalScore());
            data.put("avgScore", report.getAvgScore());
            data.put("grade", report.getGrade());
            data.put("totalDurationMs", report.getTotalDurationMs());
            data.put("startedAt", report.getStartedAt());
            data.put("completedAt", report.getCompletedAt());

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
        m.put("agentAnswer", item.getAgentAnswer());
        m.put("actualTool", item.getActualTool());
        m.put("matchedKeywords", item.getMatchedKeywords());
        m.put("judgeReason", item.getJudgeReason());
        m.put("durationMs", item.getDurationMs());
        return m;
    }
}
