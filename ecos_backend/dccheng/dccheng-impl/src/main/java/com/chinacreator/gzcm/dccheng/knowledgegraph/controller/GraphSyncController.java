package com.chinacreator.gzcm.dccheng.knowledgegraph.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 图谱数据同步 Controller — Object → Neo4j 同步管理。
 *
 * <h3>4 个端点：</h3>
 * <ol>
 *   <li>GET    /api/knowledge/sync/status           — 同步状态统计</li>
 *   <li>POST   /api/knowledge/sync/trigger          — 触发全量同步</li>
 *   <li>POST   /api/knowledge/sync/object/{objectType} — 触发单类 Object 同步</li>
 *   <li>GET    /api/knowledge/sync/logs             — 最近同步历史</li>
 * </ol>
 */
@RestController
@RequestMapping("/api/knowledge")
public class GraphSyncController {

    private static final Logger log = LoggerFactory.getLogger(GraphSyncController.class);
    private static final DateTimeFormatter ISO_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
                    .withZone(ZoneId.of("Asia/Shanghai"));

    /**
     * ObjectKgSyncService — 后续 Step 3 补充，初期 required=false 占位对接。
     */
    @Autowired(required = false)
    private ObjectKgSyncService objectKgSyncService;

    // ── 同步日志存储（内存，最多保留 100 条） ──────────────────
    private final ConcurrentLinkedQueue<Map<String, Object>> syncLogs = new ConcurrentLinkedQueue<>();
    private final AtomicLong syncIdCounter = new AtomicLong(0);
    private static final int MAX_LOG_SIZE = 100;

    private String now() {
        return ISO_FORMATTER.format(Instant.now());
    }

    /**
     * 裁剪日志队列，保持不超过 MAX_LOG_SIZE。
     */
    private void trimLogs() {
        while (syncLogs.size() > MAX_LOG_SIZE) {
            syncLogs.poll();
        }
    }

    /**
     * 追加一条同步日志。
     */
    private void addLog(String syncId, String objectType, String operation, String status, String message) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("syncId", syncId);
        entry.put("objectType", objectType);
        entry.put("operation", operation);
        entry.put("status", status);
        entry.put("timestamp", now());
        entry.put("message", message);
        syncLogs.add(entry);
        trimLogs();
    }

    // ════════════════════════════════════════════════════
    // 1. GET /api/knowledge/sync/status
    // ════════════════════════════════════════════════════
    @GetMapping("/sync/status")
    public ApiResponse<Map<String, Object>> getSyncStatus() {
        Map<String, Object> result = new LinkedHashMap<>();

        // 占位数据 — 后续 ObjectKgSyncService 就绪后替换
        List<Map<String, Object>> objectTypes = new ArrayList<>();

        if (objectKgSyncService != null) {
            try {
                result.put("objectTypes", objectKgSyncService.getSyncStatus());
                result.put("overallStatus", objectKgSyncService.getOverallStatus());
                return ApiResponse.success(result);
            } catch (Exception e) {
                log.warn("ObjectKgSyncService.getSyncStatus failed, returning placeholder data", e);
            }
        }

        // 占位数据
        String[] types = {"Table", "Column", "Task", "Indicator"};
        for (String type : types) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("type", type);
            item.put("syncedCount", 0);
            item.put("totalCount", 0);
            item.put("lastSyncTime", null);
            objectTypes.add(item);
        }
        result.put("objectTypes", objectTypes);
        result.put("overallStatus", "not_configured");
        return ApiResponse.success(result);
    }

    // ════════════════════════════════════════════════════
    // 2. POST /api/knowledge/sync/trigger — 全量同步
    // ════════════════════════════════════════════════════
    @PostMapping("/sync/trigger")
    public ApiResponse<Map<String, Object>> triggerFullSync() {
        String syncId = "sync-" + syncIdCounter.incrementAndGet();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("syncId", syncId);
        result.put("startedAt", now());

        if (objectKgSyncService == null) {
            result.put("status", "rejected");
            result.put("message", "ObjectKgSyncService 未就绪，同步已排队但未执行");
            addLog(syncId, "ALL", "FULL_SYNC", "rejected", "服务未就绪");
            return ApiResponse.success(result);
        }

        try {
            objectKgSyncService.triggerFullSync(syncId);
            result.put("status", "started");
            addLog(syncId, "ALL", "FULL_SYNC", "started", "全量同步已触发");
        } catch (Exception e) {
            log.error("Full sync trigger failed: syncId={}", syncId, e);
            result.put("status", "error");
            result.put("message", e.getMessage());
            addLog(syncId, "ALL", "FULL_SYNC", "error", e.getMessage());
        }
        return ApiResponse.success(result);
    }

    // ════════════════════════════════════════════════════
    // 3. POST /api/knowledge/sync/object/{objectType}
    // ════════════════════════════════════════════════════
    @PostMapping("/sync/object/{objectType}")
    public ApiResponse<Map<String, Object>> triggerObjectSync(@PathVariable String objectType) {
        String syncId = "sync-" + syncIdCounter.incrementAndGet();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("syncId", syncId);
        result.put("objectType", objectType);

        if (objectKgSyncService == null) {
            result.put("status", "rejected");
            result.put("message", "ObjectKgSyncService 未就绪，同步已排队但未执行");
            addLog(syncId, objectType, "OBJECT_SYNC", "rejected", "服务未就绪");
            return ApiResponse.success(result);
        }

        try {
            objectKgSyncService.triggerObjectSync(syncId, objectType);
            result.put("status", "started");
            addLog(syncId, objectType, "OBJECT_SYNC", "started", "单类同步已触发: " + objectType);
        } catch (Exception e) {
            log.error("Object sync trigger failed: syncId={}, objectType={}", syncId, objectType, e);
            result.put("status", "error");
            result.put("message", e.getMessage());
            addLog(syncId, objectType, "OBJECT_SYNC", "error", e.getMessage());
        }
        return ApiResponse.success(result);
    }

    // ════════════════════════════════════════════════════
    // 4. GET /api/knowledge/sync/logs — 最近 10 条
    // ════════════════════════════════════════════════════
    @GetMapping("/sync/logs")
    public ApiResponse<List<Map<String, Object>>> getSyncLogs() {
        List<Map<String, Object>> logs = new ArrayList<>(syncLogs);
        // 倒序（最新的在前），最多返回 10 条
        Collections.reverse(logs);
        if (logs.size() > 10) {
            logs = logs.subList(0, 10);
        }
        return ApiResponse.success(logs);
    }

    // ════════════════════════════════════════════════════
    // ObjectKgSyncService 对接接口（内部，后续 Step 3 实现）
    // ════════════════════════════════════════════════════

    /**
     * Object → KG 同步服务接口。
     * 当前为占位接口，后续 Phase B 提供具体实现。
     */
    public interface ObjectKgSyncService {
        /** 获取各 Object 类型同步统计 */
        List<Map<String, Object>> getSyncStatus();

        /** 获取整体状态 */
        String getOverallStatus();

        /** 触发全量同步 */
        void triggerFullSync(String syncId);

        /** 触发单类 Object 同步 */
        void triggerObjectSync(String syncId, String objectType);
    }
}
