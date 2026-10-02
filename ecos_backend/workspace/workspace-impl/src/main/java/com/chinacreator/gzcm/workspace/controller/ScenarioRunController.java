package com.chinacreator.gzcm.workspace.controller;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.workspace.scenario.ScenarioRunService;
import io.swagger.v3.oas.annotations.Operation;

/**
 * 场景运行编排 REST API — 场景工作台认知闭环（PMO-52 T2）。
 *
 * <pre>
 * POST /api/v1/workspace/scenarios/{id}/runs         — 发起场景运行（4 类认知调用 + 指标回写）
 * GET  /api/v1/workspace/scenarios/{id}/runs         — 场景运行历史（?limit=N）
 * </pre>
 *
 * <p>【校订 2026-10-02，详细设计-07 F07-01-5 / W165 / C147】承流与鉴权接线（三滤波器第①条
 * {@code VersionPrefixRewriteFilter} 双向映射 + gateway 反向代理路由 + :18090 态与 gateway 等价的
 * Security 链 + 两态鉴权等价）尚未完成，gateway 现以 REGEX 排除本 Controller，:18090 态尚未装
 * JWT 校验——**不得视为已放行**。接线落点与范围详见 详细设计-07 §F07-01，勿在此声称接线已完成。</p>
 */
@RestController
@RequestMapping("/api/v1/workspace/scenarios")
public class ScenarioRunController {

    private static final Logger log = LoggerFactory.getLogger(ScenarioRunController.class);

    private final ScenarioRunService scenarioRunService;

    public ScenarioRunController(ScenarioRunService scenarioRunService) {
        this.scenarioRunService = scenarioRunService;
    }

    /** 发起场景运行：body 需 runTypes[]（DIAGNOSE/FORECAST/SIMULATE/STRATEGY 组合），可选 metric/deviation/series/simulateVariables。 */
    @Operation(operationId = "runScenario", summary = "runScenario")
    @PostMapping("/{id}/runs")
    public ApiResponse<Map<String, Object>> run(@PathVariable String id,
                                                @RequestBody Map<String, Object> param) {
        log.info("发起场景运行 scenario={} params={}", id, param);
        return ApiResponse.success(scenarioRunService.run(id, param));
    }

    /** 场景运行历史（倒序）。 */
    @Operation(operationId = "listScenarioRuns", summary = "listScenarioRuns")
    @GetMapping("/{id}/runs")
    public ApiResponse<List<Map<String, Object>>> history(@PathVariable String id,
                                                          @RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.success(scenarioRunService.history(id, limit));
    }
}
