package com.chinacreator.gzcm.runtime.core.alert.service.impl;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

import com.chinacreator.gzcm.runtime.core.alert.AlertRecord;
import com.chinacreator.gzcm.runtime.core.alert.AlertRule;
import com.chinacreator.gzcm.runtime.core.alert.IAlertService;
import com.chinacreator.gzcm.runtime.core.alert.dao.AlertDao;

/**
 * 告警服务实现
 * 提供告警规则管理和告警记录功能
 *
 * <p>详细设计-00 F00-09 (W07)：进程内 {@link ConcurrentMap} 降为<b>可失效重建的进程内缓存</b>，
 * 记录触发时若容器内存在 {@link com.chinacreator.gzcm.runtime.core.alert.dao.AlertDao}（gateway / 各 service 有数据源栈）则同步落
 * 控制域 {@code ecos_runtime_alert_rule/_record}，使 ack/close/升级跨进程可见。</p>
 *
 * @author CDRC Runtime Team
 */
public class AlertServiceImpl implements IAlertService {

    // 进程内缓存：ruleId -> AlertRule（可失效重建，非权威）
    private final ConcurrentMap<String, AlertRule> rules = new ConcurrentHashMap<>();

    // 进程内缓存：alertId -> AlertRecord（可失效重建，非权威）
    private final ConcurrentMap<String, AlertRecord> records = new ConcurrentHashMap<>();

    // 详细设计-00 W07：可选持久化通道（无数据源栈的容器 = 纯内存缓存态，行为不回退）
    private final AlertDao repository;

    public AlertServiceImpl() {
        this(null);
    }

    public AlertServiceImpl(AlertDao repository) {
        this.repository = repository;
    }

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
        if (repository != null) {
            try {
                repository.insertRule(rule.getRuleId(), nullSafe(rule.getMetricType()),
                        nullSafe(rule.getAlertLevel()).isEmpty() ? "P3" : nullSafe(rule.getAlertLevel()),
                        rule.getThreshold() == null ? null : java.math.BigDecimal.valueOf(rule.getThreshold()),
                        rule.getDuration(), null, "1".equals(rule.getEnabled()) ? 1 : 0,
                        "log", null);
            } catch (Exception ignore) {
                // 持久化失败不阻断规则管理（缓存态仍可用）
            }
        }
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

        // 详细设计-00 W07：同步落库（跨进程权威），失败仅告警不阻断
        if (repository != null) {
            try {
                Map<String, Object> detail = new java.util.LinkedHashMap<>();
                detail.put("nodeId", nodeId);
                detail.put("taskId", taskId);
                detail.put("alertType", nullSafe(record.getAlertType()));
                detail.put("message", nullSafe(message));
                repository.insertRecord(record.getRuleId(), nullSafe(record.getAlertLevel()),
                        nullSafe(message), detail, "runtime-core", null, null);
            } catch (Exception ex) {
                // 持久化失败不阻断告警触发（缓存态仍保留）
            }
        }
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

        if (repository != null) {
            try {
                repository.ackRecord(alertId, resolveBy, resolveNote);
                repository.closeRecord(alertId);
            } catch (Exception ignore) {
                // 忽略
            }
        }
    }

    private static String nullSafe(String s) { return s == null ? "" : s; }

    /**
     * 告警异常
     */
    class AlertException extends Exception {
        private static final long serialVersionUID = 1L;

        public AlertException(String message) {
            super(message);
        }

        public AlertException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
