package com.chinacreator.gzcm.gateway.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.gateway.twin.DigitalTwinService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * P3-4 数字孪生 REST API 控制器。
 */
@RestController
@RequestMapping("/api/twins")
@Tag(name = "DigitalTwin", description = "数字孪生 — 设备注册、影子和遥测管理")
public class TwinController {

    private static final Logger log = LoggerFactory.getLogger(TwinController.class);

    private final DigitalTwinService twinService;

    public TwinController(DigitalTwinService twinService) {
        this.twinService = twinService;
    }

    @Operation(summary = "健康检查", description = "MQTT Broker + OPC-UA Server 状态")
    @GetMapping("/health")
    public ApiResponse<Map<String, Object>> health() {
        log.debug("P3-4 health check");
        Map<String, Object> health = twinService.getHealth();
        return ApiResponse.success(health);
    }

    @Operation(summary = "聚合状态", description = "获取所有设备聚合状态概览")
    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> aggregateStatus() {
        log.debug("P3-4 aggregate status request");
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("mqtt_up", twinService.getHealth().get("mqtt"));
        java.util.List<Map<String, Object>> devices = twinService.getDeviceList();
        long onlineCount = devices.stream()
                .filter(d -> "online".equals(d.get("status")))
                .count();
        long offlineCount = devices.size() - onlineCount;
        result.put("total_devices", devices.size());
        result.put("online_devices", onlineCount);
        result.put("offline_devices", offlineCount);
        result.put("devices", devices);
        return ApiResponse.success(result);
    }

    @Operation(summary = "设备列表", description = "获取所有注册设备的信息")
    @GetMapping("/devices")
    public ApiResponse<List<Map<String, Object>>> getDevices() {
        log.debug("P3-4 device list request");
        return ApiResponse.success(twinService.getDeviceList());
    }

    @Operation(summary = "遥测数据", description = "获取指定设备最近的遥测数据点")
    @GetMapping("/{deviceId}/telemetry")
    public ApiResponse<List<Map<String, Object>>> getTelemetry(
            @PathVariable String deviceId,
            @RequestParam(defaultValue = "20") int limit) {
        log.debug("P3-4 telemetry request: deviceId={}, limit={}", deviceId, limit);
        return ApiResponse.success(twinService.getTelemetry(deviceId, Math.min(limit, 100)));
    }

    @Operation(summary = "设备状态", description = "获取指定设备的状态和影子")
    @GetMapping("/{deviceId}/status")
    public ApiResponse<Map<String, Object>> getStatus(@PathVariable String deviceId) {
        log.debug("P3-4 status request: deviceId={}", deviceId);
        Map<String, Object> status = twinService.getDeviceStatus(deviceId);
        if (status.isEmpty() || !status.containsKey("deviceId")) {
            return ApiResponse.notFound("device not found: " + deviceId);
        }
        return ApiResponse.success(status);
    }

    @Operation(summary = "下发指令", description = "向指定设备下发控制指令")
    @PostMapping("/{deviceId}/command")
    public ApiResponse<Map<String, Object>> sendCommand(
            @PathVariable String deviceId,
            @RequestBody Map<String, Object> body) {
        String command = (String) body.get("command");
        if (command == null || command.isBlank()) {
            return ApiResponse.badRequest("参数 'command' 不能为空");
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) body.get("params");
        if (params == null) {
            params = Map.of();
        }

        log.info("P3-4 command: deviceId={}, command={}, params={}", deviceId, command, params);
        Map<String, Object> result = twinService.sendCommand(deviceId, command, params);
        return ApiResponse.success(result);
    }
}
