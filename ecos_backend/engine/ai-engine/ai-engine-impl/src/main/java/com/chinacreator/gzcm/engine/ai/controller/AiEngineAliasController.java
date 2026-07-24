package com.chinacreator.gzcm.engine.ai.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.engine.HealthCheck;
import com.chinacreator.gzcm.engine.ai.AiEngineImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/engine/ai")
public class AiEngineAliasController {

    @Autowired
    private AiEngineImpl engine;

    @GetMapping("/health")
    public ApiResponse<HealthCheck> health() {
        return ApiResponse.success(engine.healthCheck());
    }

    @GetMapping("/config")
    public ApiResponse<Map<String, Object>> config() {
        return ApiResponse.success(engine.getConfig());
    }

    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> status() {
        Map<String, Object> s = Map.of(
                "name", "ai-engine",
                "status", engine.getStatus().name()
        );
        return ApiResponse.success(s);
    }

    @PostMapping("/start")
    public ApiResponse<Map<String, Object>> start() {
        engine.start();
        return ApiResponse.success(Map.of("name", "ai-engine", "status", engine.getStatus().name()));
    }

    @PostMapping("/stop")
    public ApiResponse<Map<String, Object>> stop() {
        engine.stop();
        return ApiResponse.success(Map.of("name", "ai-engine", "status", engine.getStatus().name()));
    }
}
