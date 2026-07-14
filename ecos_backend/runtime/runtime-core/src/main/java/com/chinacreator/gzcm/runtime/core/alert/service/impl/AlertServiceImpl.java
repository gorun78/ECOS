package com.chinacreator.gzcm.runtime.core.alert.service.impl;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

import com.chinacreator.gzcm.runtime.core.alert.AlertRecord;
import com.chinacreator.gzcm.runtime.core.alert.AlertRule;
import com.chinacreator.gzcm.runtime.core.alert.IAlertService;

/**
 * 告警服务实现
 * 提供告警规则管理和告警记录功能
 * 
 * @author CDRC Runtime Team
 */
public class AlertServiceImpl implements IAlertService {

    // 内存存储：ruleId -> AlertRule
    private final ConcurrentMap<String, AlertRule> rules = new ConcurrentHashMap<>();
    
    // 内存存储：alertId -> AlertRecord
    private final ConcurrentMap<String, AlertRecord> records = new ConcurrentHashMap<>();

    @Override
    public String createAlertRule(AlertRule rule) throws IAlertService.AlertException {
        if (rule == null) {
            throw new IAlertService.AlertException("Alert rule cannot be null");
        }
        if (rule.getRuleId() == null) {
            rule.setRuleId(UUID.randomUUID().toString());
        }
        if (rules.containsKey(rule.getRuleId())) {
            throw new IAlertService.AlertException("Alert rule with ID " + rule.getRuleId() + " already exists");
        }
        rules.put(rule.getRuleId(), rule);
        return rule.getRuleId();
    }

    @Override
    public void updateAlertRule(AlertRule rule) throws IAlertService.AlertException {
        if (rule == null || rule.getRuleId() == null) {
            throw new IAlertService.AlertException("Alert rule and rule ID cannot be null");
        }
        if (!rules.containsKey(rule.getRuleId())) {
            throw new IAlertService.AlertException("Alert rule with ID " + rule.getRuleId() + " not found");
        }
        rules.put(rule.getRuleId(), rule);
    }

    @Override
    public void deleteAlertRule(String ruleId) throws IAlertService.AlertException {
        if (ruleId == null) {
            throw new IAlertService.AlertException("Rule ID cannot be null");
        }
        AlertRule removed = rules.remove(ruleId);
        if (removed == null) {
            throw new IAlertService.AlertException("Alert rule with ID " + ruleId + " not found");
        }
    }

    @Override
    public AlertRule getAlertRuleById(String ruleId) throws IAlertService.AlertException {
        if (ruleId == null) {
            throw new IAlertService.AlertException("Rule ID cannot be null");
        }
        return rules.get(ruleId);
    }

    @Override
    public List<AlertRule> queryAlertRules(String enabled) throws IAlertService.AlertException {
        List<AlertRule> allRules = new ArrayList<>(rules.values());
        if (enabled == null || enabled.trim().isEmpty()) {
            return allRules;
        }
        return allRules.stream()
            .filter(r -> enabled.equalsIgnoreCase(r.getEnabled()))
            .collect(Collectors.toList());
    }

    @Override
    public void triggerAlert(String ruleId, String alertType, String nodeId, String taskId, String message)
            throws IAlertService.AlertException {
        if (ruleId == null) {
            throw new IAlertService.AlertException("Rule ID cannot be null");
        }
        
        // 检查规则是否存在
        AlertRule rule = rules.get(ruleId);
        if (rule == null) {
            throw new IAlertService.AlertException("Alert rule with ID " + ruleId + " not found");
        }
        
        // 检查规则是否启用
        if (!"1".equals(rule.getEnabled())) {
            return; // 规则未启用，不触发告警
        }
        
        AlertRecord record = new AlertRecord();
        record.setAlertId(UUID.randomUUID().toString());
        record.setRuleId(ruleId);
        record.setRuleName(rule.getRuleName());
        record.setAlertType(alertType != null ? alertType : rule.getMetricType());
        record.setNodeId(nodeId);
        record.setTaskId(taskId);
        record.setAlertMessage(message);
        record.setAlertStatus("PENDING"); // 初始状态为待处理
        record.setAlertLevel(rule.getAlertLevel() != null ? rule.getAlertLevel() : "P3");
        record.setAlertTime(new java.sql.Timestamp(System.currentTimeMillis()));
        records.put(record.getAlertId(), record);
    }

    @Override
    public List<AlertRecord> queryAlertRecords(String ruleId, String alertStatus, String alertLevel, Integer page,
            Integer size) throws IAlertService.AlertException {
        List<AlertRecord> allRecords = new ArrayList<>(records.values());
        
        // 过滤
        List<AlertRecord> filtered = allRecords.stream()
            .filter(r -> {
                if (ruleId != null && !ruleId.equals(r.getRuleId())) {
                    return false;
                }
                if (alertStatus != null && !alertStatus.equals(r.getAlertStatus())) {
                    return false;
                }
                if (alertLevel != null && !alertLevel.equals(r.getAlertLevel())) {
                    return false;
                }
                return true;
            })
            .collect(Collectors.toList());
        
        // 分页
        if (page != null && size != null && page > 0 && size > 0) {
            int start = (page - 1) * size;
            int end = Math.min(start + size, filtered.size());
            if (start < filtered.size()) {
                return filtered.subList(start, end);
            }
            return new ArrayList<>();
        }
        
        return filtered;
    }

    @Override
    public void resolveAlert(String alertId, String resolveBy, String resolveNote) throws IAlertService.AlertException {
        if (alertId == null) {
            throw new IAlertService.AlertException("Alert ID cannot be null");
        }
        
        AlertRecord record = records.get(alertId);
        if (record == null) {
            throw new IAlertService.AlertException("Alert record with ID " + alertId + " not found");
        }
        
        record.setAlertStatus("RESOLVED");
        record.setResolveBy(resolveBy);
        record.setResolveNote(resolveNote);
        record.setResolveTime(new java.sql.Timestamp(System.currentTimeMillis()));
    }
}

