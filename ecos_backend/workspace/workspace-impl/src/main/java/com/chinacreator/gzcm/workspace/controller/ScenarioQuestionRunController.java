package com.chinacreator.gzcm.workspace.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.workspace.analysis.ScenarioQuestionRunService;
import com.chinacreator.gzcm.workspace.dao.WorkspaceDataDao;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 问题驱动的场景运行面（2026-11-24 场景运行页重构）。
 *
 * <pre>
 * POST /api/v1/workspace/scenarios/{id}/question-runs/prepare  — 意图识别 + 假设 + 算法 + 心智模型清单（不执行）
 * POST /api/v1/workspace/scenarios/{id}/question-runs          — 按已确认假设与选定心智模型执行
 * GET  /api/v1/workspace/scenarios/{id}/question-runs          — 历史运行（对比用）
 * GET  /api/v1/workspace/question-runs/{runId}                 — 单次运行详情
 * GET  /api/v1/workspace/question-runs/{a}/compare/{b}         — 两次运行对比
 * </pre>
 *
 * <p>与既有 {@code /analyses} 面并存不冲突：那面是「选分析类型直接跑」，
 * 这面是「输入问题 → 智能体判意图 → 确认假设 → 选心智模型 → 执行 → 给建议」。</p>
 */
@RestController
@RequestMapping("/api/v1/workspace")
public class ScenarioQuestionRunController {

    private final ScenarioQuestionRunService questionRunService;
    private final WorkspaceDataDao dao;

    public ScenarioQuestionRunController(ScenarioQuestionRunService questionRunService,
                                         WorkspaceDataDao dao) {
        this.questionRunService = questionRunService;
        this.dao = dao;
    }

    /** 阶段一：只做准备（意图/假设/算法/模型清单），不执行——供用户确认假设。 */
    @PostMapping("/scenarios/{id}/question-runs/prepare")
    public ApiResponse<Map<String, Object>> prepare(@PathVariable("id") String scenarioId,
                                                    @RequestBody Map<String, Object> body) {
        String question = text(body.get("question"));
        return ApiResponse.success(questionRunService.prepare(scenarioId, question));
    }

    /** 阶段二：按用户确认的假设 + 选定心智模型执行，返回执行过程与决策建议。 */
    @PostMapping("/scenarios/{id}/question-runs")
    @SuppressWarnings("unchecked")
    public ApiResponse<Map<String, Object>> run(@PathVariable("id") String scenarioId,
                                                @RequestBody Map<String, Object> body) {
        String question = text(body.get("question"));
        String modelCode = text(body.get("mentalModel"));
        String algorithmId = text(body.get("algorithmId"));
        List<Map<String, Object>> hypotheses = body.get("hypotheses") instanceof List<?> l
                ? (List<Map<String, Object>>) l : List.of();
        Map<String, Object> algorithmInputs = body.get("algorithmInputs") instanceof Map<?, ?> m
                ? (Map<String, Object>) m : Map.of();
        String executorType = text(body.get("executorType"));
        String executorId = text(body.get("executorId"));
        return ApiResponse.success(
                questionRunService.run(scenarioId, question, modelCode, hypotheses, algorithmId,
                        algorithmInputs, executorType, executorId));
    }

    /** 历史运行列表（倒序）。 */
    @GetMapping("/scenarios/{id}/question-runs")
    public ApiResponse<List<Map<String, Object>>> list(@PathVariable("id") String scenarioId,
                                                       @RequestParam(value = "limit", defaultValue = "20") int limit) {
        return ApiResponse.success(dao.listQuestionRuns(scenarioId, Math.min(Math.max(limit, 1), 100)));
    }

    /** 单次运行详情。 */
    @GetMapping("/question-runs/{runId}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable String runId) {
        List<Map<String, Object>> rows = dao.findQuestionRun(runId);
        if (rows.isEmpty()) {
            return ApiResponse.notFound("运行记录不存在: " + runId);
        }
        return ApiResponse.success(rows.get(0));
    }

    /**
     * 两次运行对比（问题/意图/心智模型/假设/建议/耗时并排）。
     *
     * <p>对比面只呈现两条记录各自的原值，<b>不</b>计算「谁更好」——那是人的判断。</p>
     */
    @GetMapping("/question-runs/{a}/compare/{b}")
    public ApiResponse<Map<String, Object>> compare(@PathVariable String a, @PathVariable String b) {
        List<Map<String, Object>> ra = dao.findQuestionRun(a);
        List<Map<String, Object>> rb = dao.findQuestionRun(b);
        if (ra.isEmpty() || rb.isEmpty()) {
            return ApiResponse.notFound("对比记录缺失: " + (ra.isEmpty() ? a : b));
        }
        Map<String, Object> left = ra.get(0);
        Map<String, Object> right = rb.get(0);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("left", left);
        out.put("right", right);
        // 差异面：只列人工关心的字段，逐字段给两侧原值
        List<String> fields = List.of("question", "intent", "mental_model", "status", "degraded");
        Map<String, Object> diff = new LinkedHashMap<>();
        for (String f : fields) {
            Map<String, Object> cell = new LinkedHashMap<>();
            cell.put("left", left.get(f));
            cell.put("right", right.get(f));
            cell.put("same", java.util.Objects.equals(left.get(f), right.get(f)));
            diff.put(f, cell);
        }
        out.put("diff", diff);
        return ApiResponse.success(out);
    }

    private static String text(Object v) {
        return v == null ? "" : String.valueOf(v);
    }
}
