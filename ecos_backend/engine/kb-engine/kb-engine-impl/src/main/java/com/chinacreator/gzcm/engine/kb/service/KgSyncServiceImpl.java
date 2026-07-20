package com.chinacreator.gzcm.engine.kb.service;

import com.chinacreator.gzcm.engine.kb.KgSyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class KgSyncServiceImpl implements KgSyncService {

    private static final Logger log = LoggerFactory.getLogger(KgSyncServiceImpl.class);
    private static final DateTimeFormatter ISO_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
                    .withZone(ZoneId.of("Asia/Shanghai"));

    private final ConcurrentLinkedQueue<Map<String, Object>> syncLogs = new ConcurrentLinkedQueue<>();
    private final AtomicLong syncIdCounter = new AtomicLong(0);
    private static final int MAX_LOG_SIZE = 100;

    private String now() {
        return ISO_FMT.format(Instant.now());
    }

    private void trimLogs() {
        while (syncLogs.size() > MAX_LOG_SIZE) {
            syncLogs.poll();
        }
    }

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

    @Override
    public List<Map<String, Object>> getSyncStatus() {
        List<Map<String, Object>> objectTypes = new ArrayList<>();
        String[] types = {"Table", "Column", "Task", "Indicator"};
        for (String type : types) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("type", type);
            item.put("syncedCount", 0);
            item.put("totalCount", 0);
            item.put("lastSyncTime", null);
            objectTypes.add(item);
        }
        return objectTypes;
    }

    @Override
    public String getOverallStatus() {
        return "not_configured";
    }

    @Override
    public void triggerFullSync(String syncId) {
        log.info("Full sync triggered: syncId={}", syncId);
        addLog(syncId, "ALL", "FULL_SYNC", "started", "Full sync triggered");
    }

    @Override
    public void triggerObjectSync(String syncId, String objectType) {
        log.info("Object sync triggered: syncId={}, objectType={}", syncId, objectType);
        addLog(syncId, objectType, "OBJECT_SYNC", "started", "Object sync triggered: " + objectType);
    }

    @Override
    public List<Map<String, Object>> getSyncLogs(int limit) {
        List<Map<String, Object>> logs = new ArrayList<>(syncLogs);
        Collections.reverse(logs);
        if (logs.size() > limit) {
            logs = logs.subList(0, limit);
        }
        return logs;
    }
}