package com.chinacreator.gzcm.engine.cognitive2.controller;

import com.chinacreator.gzcm.engine.cognitive2.WorldModelService;
import com.chinacreator.gzcm.engine.cognitive2.model.*;
import com.chinacreator.gzcm.common.base.ApiResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/world-model")
public class WorldModelController {
    @Autowired
    private WorldModelService worldModelService;
    @GetMapping("/state")
    public ApiResponse<WorldState> getCurrentState() {
        return ApiResponse.success(worldModelService.getCurrentState());
    }
    @PostMapping("/scenarios")
    public ApiResponse<SimulationResult> simulate(@RequestBody Scenario scenario) {
        return ApiResponse.success(worldModelService.simulate(scenario));
    }
    @PostMapping("/strategy/recommend")
    public ApiResponse<StrategyRecommendation> recommendStrategy(@RequestParam String goal) {
        return ApiResponse.success(worldModelService.recommendStrategy(goal));
    }
    @GetMapping("/causal-graph")
    public ApiResponse<List<CausalEdge>> getCausalGraph() {
        return ApiResponse.success(worldModelService.getCausalGraph());
    }
}
