package com.chinacreator.gzcm.engine.data.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 健康检查 Controller。
 */
@RestController
@RequestMapping("/datanet")
public class DatanetHealthController {

    @GetMapping("/api/health")
    public ApiResponse<Map<String, Object>> health() {
        return ApiResponse.success(Map.of(
                "status", "UP",
                "service", "datanet-service",
                "timestamp", LocalDateTime.now().toString()
        ));
    }
}
