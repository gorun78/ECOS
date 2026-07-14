package com.chinacreator.gzcm.engine.data.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.engine.HealthCheck;
import com.chinacreator.gzcm.engine.data.DataEngineImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 数据引擎状态端点 — 提供健康检查、配置快照、状态查询。
 */
@RestController
@RequestMapping("/api/v1/engine/data")
public class DataEngineStatusController {

    @Autowired
    private DataEngineImpl engine;

    @GetMapping("/health")
    public ApiResponse<HealthCheck> health() {
        return ApiResponse.success(engine.healthCheck());
    }

    @GetMapping("/engine-config")
    public ApiResponse<Map<String, Object>> engineConfig() {
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
}
