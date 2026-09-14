package com.chinacreator.gzcm.gateway.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 告警面板 REST API — v2 多端口路由.
 *
 * <pre>
 * GET  /api/v1/alerts           — 告警列表
 * GET  /api/v1/alerts/{id}      — 告警详情
 * POST /api/v1/alerts/{id}/ack  — 确认告警
 * </pre>
 *
 * <p>2026-09-12: 原 stub 数据已删除。gateway 不再在 :8080 暴露 /api/v1/alerts，
 * 该端点由 workspace :18090 / gateway 的降级路由处理。
 * 日期: 2026-09-12, 责任人: fullstack-implementer。</p>
 */
@RestController
@RequestMapping("/api/v1/alerts")
public class AlertController {

    private static final Logger log = LoggerFactory.getLogger(AlertController.class);

    /** GET /api/v1/alerts — 告警列表 (转发至 workspace 结果) */
    @GetMapping
    public ApiResponse<List<Map<String, Object>>> listAlerts() {
        return ApiResponse.success(Collections.emptyList());
    }

    /** GET /api/v1/alerts/{id} — 告警详情 */
    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> getAlert(@PathVariable String id) {
        return ApiResponse.notFound("Alert 详情由 workspace-service 提供 (端口:18090): " + id);
    }

    /** POST /api/v1/alerts/{id}/ack — 确认告警 */
    @PostMapping("/{id}/ack")
    public ApiResponse<Map<String, Object>> ackAlert(@PathVariable String id) {
        return ApiResponse.notFound("告警确认由 runtime-task 维持 (参见 workspace ITaskManagementService)");
    }
}
