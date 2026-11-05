package com.chinacreator.gzcm.engine.security.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.engine.HealthCheck;
import com.chinacreator.gzcm.engine.security.SecurityEngineImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 安全引擎状态端点 — 提供健康检查、配置快照、状态查询。
 *
 * <p><b>#147CD（DoD DI-2 第八批）</b>：{@code /status|/start|/stop} 三条腿由弱类型 Map 改
 * {@link SecurityStatusVO}（键面仍是 {@code name}/{@code status} 两键、键序不变）。{@code /config} 保持原样——它透传 {@code IEngine#getConfig()} 的
 * Map，属引擎底座契约，改契约须连 IEngine 一并改，另批处理。</p>
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
    public ApiResponse<SecurityStatusVO> status() {
        return ApiResponse.success(statusVO());
    }

    @PostMapping("/start")
    public ApiResponse<SecurityStatusVO> start() {
        engine.start();
        return status();
    }

    @PostMapping("/stop")
    public ApiResponse<SecurityStatusVO> stop() {
        engine.stop();
        return status();
    }

    private SecurityStatusVO statusVO() {
        return new SecurityStatusVO(engine.getName(), engine.getStatus().name());
    }

    /** 引擎状态（2 键，键序与旧 Map.of 一致）。 */
    public record SecurityStatusVO(String name, String status) {
    }
}
