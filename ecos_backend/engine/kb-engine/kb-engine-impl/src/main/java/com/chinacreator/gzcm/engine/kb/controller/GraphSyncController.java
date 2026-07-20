package com.chinacreator.gzcm.engine.kb.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.kb.KgSyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/knowledge")
public class GraphSyncController {

    private static final Logger log = LoggerFactory.getLogger(GraphSyncController.class);

    @Autowired
    private KgSyncService kgSyncService;

    @GetMapping("/sync/status")
    public ApiResponse<Map<String, Object>> getSyncStatus() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("objectTypes", kgSyncService.getSyncStatus());
        result.put("overallStatus", kgSyncService.getOverallStatus());
        return ApiResponse.success(result);
    }

    @PostMapping("/sync/trigger")
    public ApiResponse<Map<String, Object>> triggerFullSync() {
        String syncId = "sync-" + System.currentTimeMillis();
        kgSyncService.triggerFullSync(syncId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("syncId", syncId);
        result.put("status", "started");
        return ApiResponse.success(result);
    }

    @PostMapping("/sync/object/{objectType}")
    public ApiResponse<Map<String, Object>> triggerObjectSync(@PathVariable String objectType) {
        String syncId = "sync-" + System.currentTimeMillis();
        kgSyncService.triggerObjectSync(syncId, objectType);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("syncId", syncId);
        result.put("objectType", objectType);
        result.put("status", "started");
        return ApiResponse.success(result);
    }

    @GetMapping("/sync/logs")
    public ApiResponse<List<Map<String, Object>>> getSyncLogs() {
        return ApiResponse.success(kgSyncService.getSyncLogs(10));
    }
}