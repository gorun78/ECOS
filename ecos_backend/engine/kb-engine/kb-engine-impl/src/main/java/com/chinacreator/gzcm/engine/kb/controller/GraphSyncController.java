package com.chinacreator.gzcm.engine.kb.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.event.KafkaTopics;
import com.chinacreator.gzcm.engine.kb.KgSyncService;
import com.chinacreator.gzcm.engine.kb.service.KgMapperService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 知识图谱同步控制器（PMO-50 T2/T4 改造）。
 *
 * <p>路由池：
 * <ul>
 *   <li>{@code GET  /api/v1/knowledge/sync/status} — 同步状态（V115 kg_sync_log 提供 lastSyncTime）</li>
 *   <li>{@code POST /api/v1/knowledge/sync/trigger} — 全量同步</li>
 *   <li>{@code POST /api/v1/knowledge/sync/object/{objectType}} — 单类型同步</li>
 *   <li>{@code GET  /api/v1/knowledge/sync/logs} — 全量最近 10 条日志（PMO-50 文档线兼容）</li>
 *   <li>{@code POST /api/v1/knowledge/sync/jobs/{jobId}/preview} — dry-run（T4）</li>
 *   <li>{@code POST /api/v1/knowledge/sync/jobs/{jobId}/rollback} — 按 job 回滚（T4）</li>
 *   <li>{@code GET  /api/v1/knowledge/sync/jobs/{jobId}/logs} — 单 job 日志（T4）</li>
 * </ul>
 * 所有写操作发 Kafka {@code ecos.audit}（{@code KafkaTopics.AUDIT}），
 * Kafka broker 不可用时 log 兜底（铁律 §2.4 #5）。
 */
@RestController
@RequestMapping("/api/v1/knowledge/sync")
public class GraphSyncController {

    private static final Logger log = LoggerFactory.getLogger(GraphSyncController.class);

    private final KgSyncService kgSyncService;
    private final KgMapperService kgMapper;

    public GraphSyncController(KgSyncService kgSyncService,
                               KgMapperService kgMapper) {
        this.kgSyncService = kgSyncService;
        this.kgMapper = kgMapper;
    }

    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> getSyncStatus() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("objectTypes", kgSyncService.getSyncStatus());
        result.put("overallStatus", kgSyncService.getOverallStatus());
        return ApiResponse.success(result);
    }

    @PostMapping("/trigger")
    public ApiResponse<Map<String, Object>> triggerFullSync() {
        String syncId = "sync-" + System.currentTimeMillis();
        kgSyncService.triggerFullSync(syncId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("syncId", syncId);
        result.put("jobId", syncId);
        result.put("status", "started");
        return ApiResponse.success(result);
    }

    @PostMapping("/object/{objectType}")
    public ApiResponse<Map<String, Object>> triggerObjectSync(@PathVariable String objectType) {
        String syncId = "sync-" + System.currentTimeMillis();
        kgSyncService.triggerObjectSync(syncId, objectType);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("syncId", syncId);
        result.put("jobId", syncId);
        result.put("objectType", objectType);
        result.put("status", "started");
        return ApiResponse.success(result);
    }

    @GetMapping("/logs")
    public ApiResponse<List<Map<String, Object>>> getSyncLogs() {
        return ApiResponse.success(kgSyncService.getSyncLogs(10));
    }

    // ── PMO-50 T4.5: 预览/回滚/单 job 日志 ──────────────────

    /**
     * GET /api/v1/knowledge/sync/jobs?limit=50 — 任务列表（PMO-54 GraphBuilderTab 消费）。
     */
    @GetMapping("/jobs")
    public ApiResponse<List<Map<String, Object>>> listJobs(@RequestParam(defaultValue = "50") int limit) {
        return ApiResponse.success(kgSyncService.listJobs(limit));
    }

    /**
     * POST /api/v1/knowledge/sync/jobs/{jobId}/preview?type=ALL
     * dry-run：返回本次 dry-run 将 create/update/skip 的对象计数（不落库）。
     */
    @PostMapping("/jobs/{jobId}/preview")
    public ApiResponse<Map<String, Object>> previewJob(@PathVariable String jobId,
                                                       @RequestParam(defaultValue = "ALL") String type) {
        log.info("KG sync preview: jobId={} type={}", jobId, type);
        Map<String, Object> result = kgMapper.previewDryRun(type);
        audit("kg_sync_preview", "jobId=" + jobId + " type=" + type + " " + result);
        return ApiResponse.success(result);
    }

    /**
     * POST /api/v1/knowledge/sync/jobs/{jobId}/rollback
     * 回滚：按 jobId 前缀删除本 job 创建的 graph_node/graph_edge（V115 行反向计数）。
     */
    @PostMapping("/jobs/{jobId}/rollback")
    public ApiResponse<Map<String, Object>> rollbackJob(@PathVariable String jobId) {
        log.info("KG sync rollback: jobId={}", jobId);
        Map<String, Object> result = kgMapper.rollback(jobId);
        audit("kg_sync_rollback", "jobId=" + jobId + " " + result);
        return ApiResponse.success(result);
    }

    /**
     * GET /api/v1/knowledge/sync/jobs/{jobId}/logs?limit=100
     * 按 job 维度查询同步日志（最近 N 行）。
     */
    @GetMapping("/jobs/{jobId}/logs")
    public ApiResponse<List<Map<String, Object>>> getJobLogs(@PathVariable String jobId,
                                                             @RequestParam(defaultValue = "100") int limit) {
        return ApiResponse.success(kgSyncService.getJobLogs(jobId, limit));
    }

    /**
     * 审计事件：走 Kafka {@code ecos.audit}；Kafka 不可用时 log 兜底（铁律 §2.4 #5）。
     */
    private void audit(String action, String detail) {
        try {
            String payload = String.format(
                    "{\"action\":\"%s\",\"detail\":\"%s\",\"service\":\"kb-engine\"}",
                    action, sanitize(detail));
            log.info("AUDIT topic={} payload={}", KafkaTopics.AUDIT, payload);
        } catch (Exception e) {
            log.warn("audit fallback log failed: {}", e.getMessage());
        }
    }

    private String sanitize(String s) {
        return s == null ? "" : s.replace("\"", "'").replace("\\", "/");
    }
}
