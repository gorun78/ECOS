package com.chinacreator.gzcm.workspace.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.workspace.scenario.ForecastScopeService;
import com.chinacreator.gzcm.workspace.scenario.ForecastScopeVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 向导范围预填端点（卷07 F07-14 N3）。
 * <pre>GET /api/v1/workspace/scenarios/{id}/forecast-scope</pre>
 */
@RestController
@RequestMapping("/api/v1/workspace/scenarios")
public class ForecastScopeController {

    private final ForecastScopeService forecastScopeService;

    public ForecastScopeController(ForecastScopeService forecastScopeService) {
        this.forecastScopeService = forecastScopeService;
    }

    @GetMapping("/{id}/forecast-scope")
    public ApiResponse<ForecastScopeVO> scope(@PathVariable("id") String scenarioId) {
        return ApiResponse.success(forecastScopeService.build(scenarioId));
    }
}
