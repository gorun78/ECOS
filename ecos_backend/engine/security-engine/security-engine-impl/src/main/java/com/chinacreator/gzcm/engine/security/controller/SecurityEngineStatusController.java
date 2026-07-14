package com.chinacreator.gzcm.engine.security.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.engine.HealthCheck;
import com.chinacreator.gzcm.engine.security.SecurityEngineImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 安全引擎状态端点 — 提供健康检查、配置快照、状态查询。
 */
@RestController
@RequestMapping("/api/v1/engine/security")
public class SecurityEngineStatusController {

    @Autowired
    private SecurityEngineImpl engine;

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
        return status();
    }

    @PostMapping("/stop")
    public ApiResponse<Map<String, Object>> stop() {
        engine.stop();
        return status();
    }
}
