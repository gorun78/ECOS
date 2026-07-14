package com.chinacreator.gzcm.runtime.core.monitor.service.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.chinacreator.gzcm.runtime.core.monitor.collection.ICollectionMonitorService;
import com.chinacreator.gzcm.runtime.core.monitor.collection.CollectionProgress;

/**
 * 监控数据采集服务实现
 * 
 * @author CDRC Runtime Team
 */
public class CollectionMonitorServiceImpl implements ICollectionMonitorService {
    
    // 内存存储：taskId -> Map<String, Object> (任务元数据)
    private final Map<String, Map<String, Object>> taskMetadata = new ConcurrentHashMap<>();
    
    // 内存存储：taskId -> CollectionProgress
    private final Map<String, CollectionProgress> taskProgress = new ConcurrentHashMap<>();
    
    // 内存存储：taskId -> List<Map<String, Object>> (历史记录)
    private final Map<String, List<Map<String, Object>>> taskHistory = new ConcurrentHashMap<>();
    
    @Override
    public void recordStart(String taskId, Map<String, Object> metadata) {
        taskMetadata.put(taskId, metadata != null ? new HashMap<>(metadata) : new HashMap<>());
        CollectionProgress progress = new CollectionProgress();
        progress.setStartTime(System.currentTimeMillis());
        progress.setProgress(0);
        taskProgress.put(taskId, progress);
    }
    
    @Override
    public void updateProgress(String taskId, CollectionProgress progress) {
        if (progress != null) {
            taskProgress.put(taskId, progress);
        }
    }
    
    @Override
    public void recordComplete(String taskId, Map<String, Object> result) {
        CollectionProgress progress = taskProgress.get(taskId);
        if (progress != null) {
            progress.setProgress(100);
            progress.setCurrentTime(System.currentTimeMillis());
        }
        
        Map<String, Object> record = new HashMap<>();
        record.put("taskId", taskId);
        record.put("status", "COMPLETED");
        record.put("result", result);
        record.put("timestamp", System.currentTimeMillis());
        taskHistory.computeIfAbsent(taskId, k -> new ArrayList<>()).add(record);
    }
    
    @Override
    public void recordError(String taskId, String error, Throwable cause) {
        CollectionProgress progress = taskProgress.get(taskId);
        if (progress != null) {
            progress.setCurrentTime(System.currentTimeMillis());
        }
        
        Map<String, Object> record = new HashMap<>();
        record.put("taskId", taskId);
        record.put("status", "ERROR");
        record.put("error", error);
        if (cause != null) {
            record.put("cause", cause.getMessage());
        }
        record.put("timestamp", System.currentTimeMillis());
        taskHistory.computeIfAbsent(taskId, k -> new ArrayList<>()).add(record);
    }
    
    @Override
    public Map<String, Object> getPerformanceMetrics(String taskId) {
        Map<String, Object> metrics = new HashMap<>();
        CollectionProgress progress = taskProgress.get(taskId);
        if (progress != null) {
            metrics.put("progress", progress.getProgress());
            metrics.put("totalRecords", progress.getTotalRecords());
            metrics.put("processedRecords", progress.getProcessedRecords());
            metrics.put("failedRecords", progress.getFailedRecords());
            metrics.put("recordsPerSecond", progress.getRecordsPerSecond());
        }
        return metrics;
    }
    
    @Override
    public List<Map<String, Object>> getCollectionHistory(String taskId, int limit) {
        List<Map<String, Object>> history = taskHistory.get(taskId);
        if (history == null) {
            return new ArrayList<>();
        }
        
        int size = history.size();
        int start = Math.max(0, size - limit);
        return new ArrayList<>(history.subList(start, size));
    }
    
    @Override
    public void sendAlert(String taskId, String alertType, String message) {
        // 占位实现：记录告警信息
        Map<String, Object> alert = new HashMap<>();
        alert.put("taskId", taskId);
        alert.put("alertType", alertType);
        alert.put("message", message);
        alert.put("timestamp", System.currentTimeMillis());
        taskHistory.computeIfAbsent(taskId, k -> new ArrayList<>()).add(alert);
    }
}

