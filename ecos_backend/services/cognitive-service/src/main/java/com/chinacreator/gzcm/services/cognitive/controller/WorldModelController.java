package com.chinacreator.gzcm.services.cognitive.controller;

import com.chinacreator.gzcm.services.cognitive.model.Scenario;
import com.chinacreator.gzcm.services.cognitive.model.SimulationResult;
import com.chinacreator.gzcm.services.cognitive.model.StrategyRecommendation;
import com.chinacreator.gzcm.services.cognitive.model.WorldState;
import com.chinacreator.gzcm.services.cognitive.model.CausalEdge;
import com.chinacreator.gzcm.services.cognitive.worldmodel.WorldModelService;
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
