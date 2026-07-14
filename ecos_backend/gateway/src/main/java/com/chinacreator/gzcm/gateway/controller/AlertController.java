package com.chinacreator.gzcm.gateway.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 告警面板 REST API。
 *
 * <pre>
 * GET  /api/v1/alerts           — 告警列表
 * GET  /api/v1/alerts/{id}      — 告警详情
 * POST /api/v1/alerts/{id}/ack  — 确认告警
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/alerts")
public class AlertController {

    private static final Logger log = LoggerFactory.getLogger(AlertController.class);

    /** GET /api/v1/alerts — 获取告警列表 */
    @GetMapping
    public ApiResponse<List<Map<String, Object>>> listAlerts() {
        List<Map<String, Object>> alerts = new ArrayList<>();

        Map<String, Object> a1 = new LinkedHashMap<>();
        a1.put("id", "alert-1");
        a1.put("message", "数据库连接池使用率超过50%");
        a1.put("type", "warning");
        a1.put("severity", "WARNING");
        a1.put("source", "System Monitor");
        a1.put("time", "2026-06-30T09:30:00");
        a1.put("acknowledged", false);
        alerts.add(a1);

        Map<String, Object> a2 = new LinkedHashMap<>();
        a2.put("id", "alert-2");
        a2.put("message", "所有服务运行正常");
        a2.put("type", "info");
        a2.put("severity", "INFO");
        a2.put("source", "Health Check");
        a2.put("time", "2026-06-30T09:25:00");
        a2.put("acknowledged", false);
        alerts.add(a2);

        Map<String, Object> a3 = new LinkedHashMap<>();
        a3.put("id", "alert-3");
        a3.put("message", "供应商交货准时率下降至67%，触发CRITICAL偏差");
        a3.put("type", "critical");
        a3.put("severity", "CRITICAL");
        a3.put("source", "WorldModel");
        a3.put("time", "2026-06-30T08:00:00");
        a3.put("acknowledged", false);
        alerts.add(a3);

        return ApiResponse.success(alerts);
    }

    /** GET /api/v1/alerts/{id} — 告警详情 */
    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> getAlert(@PathVariable String id) {
        for (Map<String, Object> a : listAlerts().getData()) {
            if (id.equals(a.get("id"))) {
                return ApiResponse.success(a);
            }
        }
        return ApiResponse.notFound("Alert " + id + " 不存在");
    }

    /** POST /api/v1/alerts/{id}/ack — 确认告警 */
    @PostMapping("/{id}/ack")
    public ApiResponse<Map<String, Object>> ackAlert(@PathVariable String id) {
        for (Map<String, Object> a : listAlerts().getData()) {
            if (id.equals(a.get("id"))) {
                a.put("acknowledged", true);
                log.info("Alert acknowledged: {}", id);
                return ApiResponse.success(a);
            }
        }
        return ApiResponse.notFound("Alert " + id + " 不存在");
    }
}
