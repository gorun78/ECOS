package com.chinacreator.gzcm.engine.kb.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.engine.HealthCheck;
import com.chinacreator.gzcm.common.engine.IEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/engine/knowledge")
public class KbEngineHealthController {

    private static final Logger log = LoggerFactory.getLogger(KbEngineHealthController.class);

    @Autowired
    @Qualifier("kbEngineImpl")
    private IEngine kbEngine;

    @GetMapping("/health")
    public ApiResponse<HealthCheck> health() {
        return ApiResponse.success(kbEngine.healthCheck());
    }

    @GetMapping("/config")
    public ApiResponse<Map<String, Object>> config() {
        return ApiResponse.success(kbEngine.getConfig());
    }

    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> status() {
        Map<String, Object> s = Map.of(
                "name", kbEngine.getName(),
                "status", kbEngine.getStatus().name()
        );
        return ApiResponse.success(s);
    }

    @PostMapping("/start")
    public ApiResponse<Map<String, Object>> start() {
        kbEngine.start();
        return ApiResponse.success(Map.of("name", kbEngine.getName(), "status", kbEngine.getStatus().name()));
    }

    @PostMapping("/stop")
    public ApiResponse<Map<String, Object>> stop() {
        kbEngine.stop();
        return ApiResponse.success(Map.of("name", kbEngine.getName(), "status", kbEngine.getStatus().name()));
    }
}
