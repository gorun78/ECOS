package com.chinacreator.gzcm.engine.data.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.data.dto.IntegrationDriftRequest;
import com.chinacreator.gzcm.engine.data.integration.IntegrationMetadataService;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * IntegrationMetadataController — 联邦物理元数据端点（T1: 真实化 logs/drift）。
 *
 * <p>路径保持前端兼容（knowledgeApi 走 /api/v1/integration/**，gateway 重写到 /api/integration）：</p>
 * <ul>
 *   <li>GET  /api/integration/logs            — 真实审计日志（td_audit_log / ecos_audit_log /
 *       ecos_object_timeline 优先级回退，time range + limit 过滤，时间倒序）</li>
 *   <li>POST /api/integration/metadata/drift  — 真实 Schema 漂移 / SLA 断流检测
 *       （dq_rule_check + pipeline_task，写操作发 Kafka AUDIT 审计）</li>
 * </ul>
 *
 * <p>字段映射兼容前端 knowledgeApi：logs→array（severity/event/details/timestamp），
 * drift→{success, message, dataSource, attribution, checkedAt}。</p>
 *
 * @author ECOS Integration
 */
@org.springframework.web.bind.annotation.RestController
@org.springframework.web.bind.annotation.RequestMapping("/api/integration")
public class IntegrationMetadataController {

    private final IntegrationMetadataService integrationMetadataService;

    public IntegrationMetadataController(IntegrationMetadataService integrationMetadataService) {
        this.integrationMetadataService = integrationMetadataService;
    }

    /**
     * GET /api/integration/logs — 真实审计日志。
     *
     * @param timeRange 时间窗口小时数（可选，默认 24，上限 720）
     * @param limit     条数上限（可选，默认 200，上限 500）
     */
    @org.springframework.web.bind.annotation.GetMapping("/logs")
    public ApiResponse<Map<String, Object>> fetchIntegrationLogs(
            @org.springframework.web.bind.annotation.RequestParam(name = "timeRange", required = false) Integer timeRange,
            @org.springframework.web.bind.annotation.RequestParam(name = "limit", required = false) Integer limit) {
        List<Map<String, Object>> logs = integrationMetadataService.fetchIntegrationLogs(timeRange, limit);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("logs", logs);
        return ApiResponse.success(result);
    }

    /**
     * POST /api/integration/metadata/drift — 真实漂移 / SLA 检测。
     *
     * @param body 请求体（type: drift / sla / reset，缺省 drift）
     */
    @org.springframework.web.bind.annotation.PostMapping("/metadata/drift")
    public ApiResponse<Map<String, Object>> triggerDrift(
            @org.springframework.web.bind.annotation.RequestBody(required = false) IntegrationDriftRequest body) {
        String type = body != null && body.getType() != null ? body.getType() : "drift";
        Map<String, Object> report = integrationMetadataService.detectDriftAndSlaStatus(type);
        return ApiResponse.success(report);
    }
}
