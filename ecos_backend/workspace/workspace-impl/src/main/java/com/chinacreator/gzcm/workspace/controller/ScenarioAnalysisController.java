package com.chinacreator.gzcm.workspace.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.workspace.analysis.AnalysisCompareVO;
import com.chinacreator.gzcm.workspace.analysis.AnalysisRunRequest;
import com.chinacreator.gzcm.workspace.analysis.AnalysisRunVO;
import com.chinacreator.gzcm.workspace.analysis.ScenarioAnalysisService;
import com.chinacreator.gzcm.workspace.scenario.ForbiddenOperationException;
import com.chinacreator.gzcm.workspace.scenario.MigrationPermissionChecker;
import com.chinacreator.gzcm.workspace.security.ScenarioReadGuard;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 场景分析运行面（2026-11-21 场景工作台简化 v0.3）。
 *
 * <pre>
 * GET  /api/v1/workspace/scenarios/{id}/analyses      — 运行记录（锚点统一读）
 * POST /api/v1/workspace/scenarios/{id}/analyses      — 发起正式分析
 * GET  /api/v1/workspace/analyses/{analysisId}        — 单条分析
 * GET  /api/v1/workspace/analyses/compare             — 两条预测分析对比
 * </pre>
 *
 * <p>独立 Controller（不撞既有契约属主闭包）。正式仿真/因果走既有 ScenarioRunService；
 * 预测走 ForecastRunOrchestrationService；What-If 试算仍在原认知通道，显式标探索。</p>
 */
@RestController
@RequestMapping("/api/v1/workspace")
public class ScenarioAnalysisController {

    private final ScenarioAnalysisService analysisService;
    private final ScenarioReadGuard readGuard;
    private final MigrationPermissionChecker permissionChecker;

    public ScenarioAnalysisController(ScenarioAnalysisService analysisService,
                                      ScenarioReadGuard readGuard,
                                      MigrationPermissionChecker permissionChecker) {
        this.analysisService = analysisService;
        this.readGuard = readGuard;
        this.permissionChecker = permissionChecker;
    }

    @GetMapping("/scenarios/{id}/analyses")
    public ApiResponse<List<AnalysisRunVO>> list(@PathVariable("id") String scenarioId,
                                                @RequestParam(value = "limit", defaultValue = "20") int limit) {
        readGuard.assertReadable("scenario_analyses");
        return ApiResponse.success(AnalysisRunVO.listOf(analysisService.list(scenarioId, limit)));
    }

    @PostMapping("/scenarios/{id}/analyses")
    public ApiResponse<AnalysisRunVO> run(@PathVariable("id") String scenarioId,
                                          @RequestBody(required = false) AnalysisRunRequest body) {
        assertAllowed("scenario:write");
        return ApiResponse.success(AnalysisRunVO.of(
                analysisService.run(scenarioId, body == null ? Map.of() : body.toParams())));
    }

    @GetMapping("/analyses/{analysisId}")
    public ApiResponse<AnalysisRunVO> get(@PathVariable String analysisId) {
        readGuard.assertReadable("scenario_analysis");
        return ApiResponse.success(AnalysisRunVO.of(analysisService.get(analysisId)));
    }

    @GetMapping("/analyses/compare")
    public ApiResponse<AnalysisCompareVO> compare(@RequestParam String baselineAnalysisId,
                                                  @RequestParam String candidateAnalysisId) {
        readGuard.assertReadable("scenario_analysis_compare");
        return ApiResponse.success(AnalysisCompareVO.of(
                analysisService.compare(baselineAnalysisId, candidateAnalysisId)));
    }

    private void assertAllowed(String permissionPoint) {
        if (!permissionChecker.isAllowed(permissionPoint, currentSubject())) {
            throw new ForbiddenOperationException(permissionPoint);
        }
    }

    private String currentSubject() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() != null) {
            return String.valueOf(auth.getPrincipal());
        }
        return "anonymous";
    }
}
