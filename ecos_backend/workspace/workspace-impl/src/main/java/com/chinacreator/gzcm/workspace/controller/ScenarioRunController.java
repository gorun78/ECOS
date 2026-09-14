package com.chinacreator.gzcm.workspace.controller;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.workspace.scenario.ScenarioRunService;

/**
 * 场景运行编排 REST API — 场景工作台认知闭环（PMO-52 T2）。
 *
 * <pre>
 * POST /api/v1/workspace/scenarios/{id}/runs         — 发起场景运行（4 类认知调用 + 指标回写）
 * GET  /api/v1/workspace/scenarios/{id}/runs         — 场景运行历史（?limit=N）
 * </pre>
 *
 * <p>三滤波器：{@code /api/v1/workspace/**} 已由 SecurityConfig + ClearanceInterceptor 放行。</p>
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
    @PostMapping("/{id}/runs")
    public ApiResponse<Map<String, Object>> run(@PathVariable String id,
                                                @RequestBody Map<String, Object> param) {
        log.info("发起场景运行 scenario={} params={}", id, param);
        return ApiResponse.success(scenarioRunService.run(id, param));
    }

    /** 场景运行历史（倒序）。 */
    @GetMapping("/{id}/runs")
    public ApiResponse<List<Map<String, Object>>> history(@PathVariable String id,
                                                          @RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.success(scenarioRunService.history(id, limit));
    }
}
