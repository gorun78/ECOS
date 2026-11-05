package com.chinacreator.gzcm.engine.ai.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.ai.dto.AgentEvalListVO;
import com.chinacreator.gzcm.engine.ai.dto.AgentEvalReportVO;
import com.chinacreator.gzcm.engine.ai.dto.AgentEvalRunVO;
import com.chinacreator.gzcm.engine.ai.dto.AgentEvalSummaryVO;
import com.chinacreator.gzcm.engine.ai.service.AgentEvaluator;
import com.chinacreator.gzcm.engine.ai.service.AgentEvaluator.DimensionScores;
import com.chinacreator.gzcm.engine.ai.service.AgentEvaluator.EvalItemResult;
import com.chinacreator.gzcm.engine.ai.service.AgentEvaluator.EvalReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
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
     * <p>启动失败不再返回 internalError，由 {@link AiErrorAdvice} 分类为 ECOS-AI-* 响应。</p>
     */
    @PostMapping("/run")
    public ApiResponse<AgentEvalRunVO> runEvaluation(
            @RequestBody(required = false) java.util.Map<String, Object> body) {
        String questionSet = null;
        if (body != null && body.containsKey("questionSet")) {
            questionSet = String.valueOf(body.get("questionSet"));
        }
        String evalId = agentEvaluator.startEvaluation(questionSet);

        log.info("Eval run triggered: evalId={} questionSet={}", evalId, questionSet);
        return ApiResponse.success(new AgentEvalRunVO(
                evalId,
                questionSet != null ? questionSet : "default",
                "评估已启动，通过 GET /api/v1/aip/eval/" + evalId + "/report 查询结果"));
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
     * <p>查询失败不再返回 internalError，由 {@link AiErrorAdvice} 分类为 ECOS-AI-* 响应。</p>
     */
    @GetMapping("/list")
    public ApiResponse<AgentEvalListVO> listEvaluations() {
        List<EvalReport> reports = agentEvaluator.listReports();

        List<AgentEvalSummaryVO> items = new ArrayList<>();
        for (EvalReport report : reports) {
            items.add(AgentEvalSummaryVO.of(report));
        }

        return ApiResponse.success(new AgentEvalListVO(items.size(), items));
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
     * <p>查询失败不再返回 internalError，由 {@link AiErrorAdvice} 分类为 ECOS-AI-* 响应。</p>
     */
    @GetMapping("/{id}/report")
    public ApiResponse<AgentEvalReportVO> getReport(@PathVariable String id) {
        EvalReport report = agentEvaluator.getReport(id);

        if (report == null) {
            return ApiResponse.notFound("评估 " + id + " 不存在");
        }

        return ApiResponse.success(AgentEvalReportVO.of(report));
    }
}
