package com.chinacreator.gzcm.workspace.controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.*;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.exception.BusinessException;
import com.chinacreator.gzcm.workspace.analysis.ScenarioResearchApprovalService;
import com.chinacreator.gzcm.workspace.analysis.ScenarioResearchRunService;
import com.chinacreator.gzcm.workspace.analysis.task.ResearchRunAsyncDispatcher;
import com.chinacreator.gzcm.workspace.security.ScenarioReadGuard;

/**
 * 场景运行 L3 · 研究循环面（RD-Agent 模式接入首切片，2026-11-25）。
 *
 * <pre>
 * POST /api/v1/workspace/scenarios/{id}/research-runs/prepare      — 研究准备（不执行）
 * POST /api/v1/workspace/scenarios/{id}/research-runs              — 发起研究循环（同步首切片）
 * GET  /api/v1/workspace/scenarios/{id}/research-runs              — 研究谱系列表
 * GET  /api/v1/workspace/research-runs/{runId}                     — 单轮详情（含产物引用）
 * GET  /api/v1/workspace/research-runs/{a}/compare/{b}             — 两轮对比（只给原值）
 * </pre>
 *
 * <p>与既有 {@code question-runs}（L1 单轮问答）并存不冲突：那面是「问一次、答一次」，
 * 这面是「多轮假设—实验—反馈—记录」。L3 运行一律 SANDBOX 落库，不污染正式口径。</p>
 *
 * <p>读面复用既有已登记 RLS 实体键（{@code scenario_analyses} / {@code scenario_analysis_compare}），
 * 不新增未登记键（{@code ScenarioReadGuard} 对未知实体 fail-closed DENY）。</p>
 */
@RestController
@RequestMapping("/api/v1/workspace")
public class ScenarioResearchRunController {

    private final ScenarioResearchRunService researchRunService;
    private final ScenarioResearchApprovalService approvalService;
    private final ScenarioReadGuard readGuard;
    private final ResearchRunAsyncDispatcher asyncDispatcher;

    public ScenarioResearchRunController(ScenarioResearchRunService researchRunService,
                                         ScenarioResearchApprovalService approvalService,
                                         ScenarioReadGuard readGuard,
                                         ResearchRunAsyncDispatcher asyncDispatcher) {
        this.researchRunService = researchRunService;
        this.approvalService = approvalService;
        this.readGuard = readGuard;
        this.asyncDispatcher = asyncDispatcher;
    }

    /** 研究准备：场景上下文摘要 + 默认预算 + 首轮假设（不执行、不落库）。 */
    @PostMapping("/scenarios/{id}/research-runs/prepare")
    public ApiResponse<Map<String, Object>> prepare(@PathVariable("id") String scenarioId,
                                                    @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> b = body == null ? Map.of() : body;
        String question = b.get("question") == null ? "" : String.valueOf(b.get("question"));
        return ApiResponse.success(researchRunService.prepare(scenarioId, question));
    }

    /**
     * 发起研究循环。
     *
     * <p><b>同步</b>（默认）：阻塞到循环收敛后返回完整谱系——适合少量轮次。
     * <b>异步</b>（{@code ?async=true}）：提交 runtime-task 后立即返回 {@code taskId}，
     * 由前端轮询任务中心；循环本体走同一条实现（禁双轨）。异步底座未装配时如实回 501。</p>
     */
    @PostMapping("/scenarios/{id}/research-runs")
    public ApiResponse<Map<String, Object>> run(@PathVariable("id") String scenarioId,
                                                @RequestBody(required = false) Map<String, Object> body,
                                                @RequestParam(value = "async", defaultValue = "false") boolean async) {
        Map<String, Object> payload = body == null ? Map.of() : body;
        if (!async) {
            return ApiResponse.success(researchRunService.run(scenarioId, payload));
        }
        try {
            String taskId = asyncDispatcher.dispatch(scenarioId, payload);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("scenarioId", scenarioId);
            out.put("async", true);
            out.put("taskId", taskId);
            out.put("note", "研究循环已提交 runtime-task；产物按轮落 ecos_scenario_run，"
                    + "经 GET /scenarios/{id}/research-runs 观察谱系生长");
            return ApiResponse.success(out);
        } catch (ResearchRunAsyncDispatcher.ResearchAsyncUnavailableException e) {
            // 501：底座缺失，禁降级成同步冒充「已异步」
            throw new BusinessException(501, 501, "RESEARCH-501: " + e.getMessage());
        }
    }

    /** 研究谱系列表（含轮次/父子/SOTA/停止原因）。 */
    @GetMapping("/scenarios/{id}/research-runs")
    public ApiResponse<List<Map<String, Object>>> list(@PathVariable("id") String scenarioId,
                                                       @RequestParam(value = "limit", defaultValue = "50") int limit) {
        readGuard.assertReadable("scenario_analyses");
        List<Map<String, Object>> rows = researchRunService.list(scenarioId, limit);
        return ApiResponse.success(readGuard.applyColumns("scenario_analyses", rows));
    }

    /** 单轮研究详情（含产物引用）。 */
    @GetMapping("/research-runs/{runId}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable String runId) {
        readGuard.assertReadable("scenario_analysis");
        Map<String, Object> detail = researchRunService.detail(runId);
        List<Map<String, Object>> clipped = readGuard.applyColumns("scenario_analysis", List.of(detail));
        return ApiResponse.success(clipped.isEmpty() ? detail : clipped.get(0));
    }

    /** 两轮对比（逐字段原值 + 是否相同，不判优劣）。 */
    @GetMapping("/research-runs/{a}/compare/{b}")
    public ApiResponse<Map<String, Object>> compare(@PathVariable String a, @PathVariable String b) {
        readGuard.assertReadable("scenario_analysis_compare");
        return ApiResponse.success(researchRunService.compare(a, b));
    }

    /**
     * 候选审批：APPROVED 必带 approvalRef（禁生成即审批）；REJECTED 可带 reason。
     *
     * <p>只追加留痕，<b>不</b>自动回写蓝图/指标；正式口径须另行发起 FORMAL 运行。</p>
     */
    @PostMapping("/research-runs/{runId}/approve")
    public ApiResponse<Map<String, Object>> approve(@PathVariable String runId,
                                                    @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> b = body == null ? Map.of() : body;
        String decision = b.get("decision") == null ? "APPROVED" : String.valueOf(b.get("decision"));
        String approvalRef = b.get("approvalRef") == null ? null : String.valueOf(b.get("approvalRef"));
        String reason = b.get("reason") == null ? null : String.valueOf(b.get("reason"));
        String digest = b.get("evidenceDigest") == null ? null : String.valueOf(b.get("evidenceDigest"));
        return ApiResponse.success(approvalService.decide(runId, decision, approvalRef, reason, digest));
    }

    /** 某轮研究运行的审批历史（空=尚未审批；只追加不覆盖）。 */
    @GetMapping("/research-runs/{runId}/approvals")
    public ApiResponse<List<Map<String, Object>>> approvals(@PathVariable String runId) {
        readGuard.assertReadable("scenario_analyses");
        return ApiResponse.success(readGuard.applyColumns("scenario_analyses",
                approvalService.history(runId)));
    }
}
