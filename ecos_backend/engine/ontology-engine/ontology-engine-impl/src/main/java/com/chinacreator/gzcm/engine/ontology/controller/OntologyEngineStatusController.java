package com.chinacreator.gzcm.engine.ontology.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.engine.HealthCheck;
import com.chinacreator.gzcm.engine.ontology.OntologyEngineImpl;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/engine/ontology")
public class OntologyEngineStatusController {

    private final OntologyEngineImpl engine;

    public OntologyEngineStatusController(OntologyEngineImpl engine) {
        this.engine = engine;
    }

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
                "name", engine.getName(),
                "status", engine.getStatus().name()
        );
        return ApiResponse.success(s);
    }

    @PostMapping("/start")
    public ApiResponse<Map<String, Object>> start() {
        engine.start();
        return ApiResponse.success(Map.of("name", engine.getName(), "status", engine.getStatus().name()));
    }

    @PostMapping("/stop")
    public ApiResponse<Map<String, Object>> stop() {
        engine.stop();
        return ApiResponse.success(Map.of("name", engine.getName(), "status", engine.getStatus().name()));
    }
}
