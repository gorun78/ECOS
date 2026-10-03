package com.chinacreator.gzcm.runtime.core.alert.service.impl;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.chinacreator.gzcm.common.context.TraceContext;
import com.chinacreator.gzcm.runtime.core.alert.AlertRecord;
import com.chinacreator.gzcm.runtime.core.alert.AlertRule;
import com.chinacreator.gzcm.runtime.core.alert.IAlertService;
import com.chinacreator.gzcm.runtime.core.alert.mapper.AlertRecordMapper;
import com.chinacreator.gzcm.runtime.core.alert.mapper.AlertRuleMapper;
import com.chinacreator.gzcm.runtime.core.alert.mapper.entity.AlertRecordRow;
import com.chinacreator.gzcm.runtime.core.alert.mapper.entity.AlertRuleRow;

/**
 * 告警服务实现（F00-09 / W07 持久化改造，详细设计-00 C.5 + §6.2）。
 * <p>
 * 双写模型：{@code ConcurrentMap} 降为<b>进程内缓存</b>（保留既有内存语义与
 * {@link IAlertService.AlertException} 行为），DB（V241 两表）为权威存储：
 * <ul>
 *   <li>create/update/delete 规则：先写 DB（id = 应用侧 {@link UUID#randomUUID()}，MC01），
 *       再同步缓存；无 DB（mapper 未注入 / dev 态表不存在）时降级纯内存，WARN 不挂主链；</li>
 *   <li>queryAlertRules / queryAlertRecords：优先查 DB；表不存在或无行时回退内存缓存兜底
 *       （dev 态可启动、可演示）；</li>
 *   <li>triggerAlert：落 record（status=open，severity 沿用 rule（alertLevel→severity 映射），
 *       title=alertType+message 截 255，trace 取 {@link TraceContext#current()}）+ 缓存；</li>
 *   <li>resolveAlert：语义映射 DB status=closed（既有内存语义 RESOLVED 保留）；ACK 另走
 *       {@code AlertRecordMapper.ack}（只 mark 不自动 close — 审计兜底 critical 告警人工确认口径）。</li>
 * </ul>
 *
 * <p>装配：无参构造保留（runtime-task 等 {@code new AlertServiceImpl()} 既有调用零改动）；
 * Spring 装配走 {@link com.chinacreator.gzcm.runtime.core.alert.AlertAutoConfiguration}。
 *
 * @author CDRC Runtime Team
 */
public class AlertServiceImpl implements IAlertService {

    private static final Logger log = LoggerFactory.getLogger(AlertServiceImpl.class);

    /** 版本列默认值（无 Git 版本链路时落静态版本号；版本×Git 归档属 VG 系列，非本表口径） */
    static final String DEFAULT_VERSION_NO = "1";
    static final String DEFAULT_DOMAIN = "default";

    // 进程内缓存：ruleId -> AlertRule（F-11 原有存储，降级为缓存层）
    private final ConcurrentMap<String, AlertRule> rules = new ConcurrentHashMap<>();

    // 进程内缓存：alertId -> AlertRecord
    private final ConcurrentMap<String, AlertRecord> records = new ConcurrentHashMap<>();

    /** 可空：未注入（无参构造 / dev 态）时全部降级纯内存 */
    private AlertRuleMapper ruleMapper;
    private AlertRecordMapper recordMapper;

    public AlertServiceImpl() {
    }

    /** Spring 装配构造器（mapper 均可空：runtime-core 单独宿主时允许纯内存模式） */
    public AlertServiceImpl(AlertRuleMapper ruleMapper, AlertRecordMapper recordMapper) {
        this.ruleMapper = ruleMapper;
        this.recordMapper = recordMapper;
    }

    // ── 规则 CRUD ────────────────────────────────────────────────

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
        // 落库（ID 应用侧 UUID；ruleCode 缺省回退 ruleId 保证 uk (tenant_id, rule_code) 唯一）
        if (ruleMapper != null) {
            try {
                ruleMapper.insert(toRow(rule));
            } catch (Exception e) {
                log.warn("[Alert] rule 落库失败降级纯内存 ruleId={} errType={} msg={}",
                        rule.getRuleId(), e.getClass().getSimpleName(), e.getMessage());
            }
        }
        rules.put(rule.getRuleId(), rule);
        return rule.getRuleId();
    }

    @Override
    public void updateAlertRule(AlertRule rule) throws IAlertService.AlertException {
        if (rule == null || rule.getRuleId() == null) {
            throw new IAlertService.AlertException("Alert rule and rule ID cannot be null");
        }
        if (!rules.containsKey(rule.getRuleId()) && !dbRuleExists(rule.getRuleId())) {
            throw new IAlertService.AlertException("Alert rule with ID " + rule.getRuleId() + " not found");
        }
        if (ruleMapper != null) {
            try {
                ruleMapper.update(toRow(rule));
            } catch (Exception e) {
                log.warn("[Alert] rule 更新落库失败降级纯内存 ruleId={} errType={} msg={}",
                        rule.getRuleId(), e.getClass().getSimpleName(), e.getMessage());
            }
        }
        rules.put(rule.getRuleId(), rule);
    }

    @Override
    public void deleteAlertRule(String ruleId) throws IAlertService.AlertException {
        if (ruleId == null) {
            throw new IAlertService.AlertException("Rule ID cannot be null");
        }
        AlertRule removed = rules.remove(ruleId);
        if (removed == null && !dbRuleExists(ruleId)) {
            throw new IAlertService.AlertException("Alert rule with ID " + ruleId + " not found");
        }
        if (ruleMapper != null) {
            try {
                ruleMapper.softDelete(ruleId, "system");
            } catch (Exception e) {
                log.warn("[Alert] rule 软删落库失败降级纯内存 ruleId={} errType={} msg={}",
                        ruleId, e.getClass().getSimpleName(), e.getMessage());
            }
        }
    }

    @Override
    public AlertRule getAlertRuleById(String ruleId) throws IAlertService.AlertException {
        if (ruleId == null) {
            throw new IAlertService.AlertException("Rule ID cannot be null");
        }
        if (ruleMapper != null) {
            try {
                AlertRuleRow row = ruleMapper.findById(ruleId);
                if (row != null) {
                    return toDomain(row);
                }
            } catch (Exception e) {
                log.debug("[Alert] rule DB 查询失败回退缓存 ruleId={} err={}", ruleId, e.getMessage());
            }
        }
        return rules.get(ruleId);
    }

    @Override
    public List<AlertRule> queryAlertRules(String enabled) throws IAlertService.AlertException {
        if (ruleMapper != null) {
            try {
                List<AlertRuleRow> rows = ruleMapper.listByFilter(enabled);
                if (rows != null && !rows.isEmpty()) {
                    return rows.stream().map(AlertServiceImpl::toDomain).collect(Collectors.toList());
                }
                // DB 无行 → 内存缓存兜底（dev 态）
            } catch (Exception e) {
                log.debug("[Alert] rules DB 查询失败回退缓存 err={}", e.getMessage());
            }
        }
        List<AlertRule> allRules = new ArrayList<>(rules.values());
        if (enabled == null || enabled.trim().isEmpty()) {
            return allRules;
        }
        return allRules.stream()
            .filter(r -> enabled.equalsIgnoreCase(r.getEnabled()))
            .collect(Collectors.toList());
    }

    // ── 告警触发与记录 ───────────────────────────────────────────

    @Override
    public void triggerAlert(String ruleId, String alertType, String nodeId, String taskId, String message)
            throws IAlertService.AlertException {
        if (ruleId == null) {
            throw new IAlertService.AlertException("Rule ID cannot be null");
        }

        // 检查规则是否存在（缓存 miss 时查 DB，dev 态纯 DB 建规则场景）
        AlertRule rule = rules.get(ruleId);
        if (rule == null && ruleMapper != null) {
            try {
                AlertRuleRow row = ruleMapper.findById(ruleId);
                if (row != null) {
                    rule = toDomain(row);
                }
            } catch (Exception e) {
                log.debug("[Alert] trigger 规则 DB 查询失败 ruleId={} err={}", ruleId, e.getMessage());
            }
        }
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
        record.setAlertStatus("PENDING"); // 既有内存语义（PENDING 初始待处理）
        record.setAlertLevel(rule.getAlertLevel() != null ? rule.getAlertLevel() : "P3");
        record.setAlertTime(new Timestamp(System.currentTimeMillis()));
        records.put(record.getAlertId(), record);

        // 落 record：DB 权威（status=open，severity 沿用 rule，title=alertType+message 截 255，trace 取 MDC）
        if (recordMapper != null) {
            try {
                AlertRecordRow row = new AlertRecordRow();
                row.setId(record.getAlertId());
                row.setRuleCode(rule.getMetricType() != null ? rule.getMetricType()
                        : (alertType != null ? alertType : "alert"));
                row.setSeverity(levelToSeverity(record.getAlertLevel()));
                row.setStatus("open");
                String title = (record.getAlertType() == null ? "" : record.getAlertType())
                        + (message == null ? "" : " " + message);
                row.setTitle(title.length() > 255 ? title.substring(0, 255) : title);
                row.setDetailJson(buildDetailJson(nodeId, taskId, message, record.getAlertLevel()));
                row.setSourceModule(rule.getRuleName());
                row.setTraceId(TraceContext.current());
                row.setOccurredAt(record.getAlertTime());
                row.setVersionNo(DEFAULT_VERSION_NO);
                row.setIsDeleted((short) 0);
                row.setDomain(DEFAULT_DOMAIN);
                recordMapper.insert(row);
            } catch (Exception e) {
                log.warn("[Alert] record 落库失败降级纯内存 alertId={} errType={} msg={}",
                        record.getAlertId(), e.getClass().getSimpleName(), e.getMessage());
            }
        }
    }

    @Override
    public List<AlertRecord> queryAlertRecords(String ruleId, String alertStatus, String alertLevel,
            Integer page, Integer size) throws IAlertService.AlertException {
        // DB 优先：severity 过滤取 alertLevel 映射，status 过滤直传；无 DB 行/表缺失 → 内存兜底
        if (recordMapper != null) {
            try {
                List<AlertRecordRow> rows = recordMapper.page(
                        alertLevel != null ? levelToSeverity(alertLevel) : null,
                        alertStatus, 200, 0);
                if (rows != null && !rows.isEmpty()) {
                    List<AlertRecord> mapped = rows.stream().map(AlertServiceImpl::toMemoryRecord)
                            .filter(r -> ruleId == null || ruleId.equals(r.getRuleId())
                                    || ruleId.equals(r.getRuleName()))
                            .collect(Collectors.toList());
                    if (page != null && size != null && page > 0 && size > 0) {
                        int start = (page - 1) * size;
                        int end = Math.min(start + size, mapped.size());
                        if (start < mapped.size()) {
                            return mapped.subList(start, end);
                        }
                        return new ArrayList<>();
                    }
                    return mapped;
                }
            } catch (Exception e) {
                log.debug("[Alert] records DB 查询失败回退缓存 err={}", e.getMessage());
            }
        }
        return queryRecordsFromMemory(ruleId, alertStatus, alertLevel, page, size);
    }

    @Override
    public void resolveAlert(String alertId, String resolveBy, String resolveNote) throws IAlertService.AlertException {
        if (alertId == null) {
            throw new IAlertService.AlertException("Alert ID cannot be null");
        }

        AlertRecord record = records.get(alertId);
        if (record == null && recordMapper != null) {
            try {
                AlertRecordRow row = recordMapper.findById(alertId);
                if (row != null) {
                    record = toMemoryRecord(row);
                    records.put(alertId, record);
                }
            } catch (Exception e) {
                log.debug("[Alert] resolve 记录 DB 查询失败 alertId={} err={}", alertId, e.getMessage());
            }
        }
        if (record == null) {
            throw new IAlertService.AlertException("Alert record with ID " + alertId + " not found");
        }

        record.setAlertStatus("RESOLVED");
        record.setResolveBy(resolveBy);
        record.setResolveNote(resolveNote);
        record.setResolveTime(new Timestamp(System.currentTimeMillis()));

        // 语义映射落库：resolve → status=closed
        if (recordMapper != null) {
            try {
                recordMapper.close(alertId, resolveBy, record.getResolveTime());
            } catch (Exception e) {
                log.warn("[Alert] resolve 落库失败降级纯内存 alertId={} errType={} msg={}",
                        alertId, e.getClass().getSimpleName(), e.getMessage());
            }
        }
    }

    // ── 编程入口（DLQ / 审计兜底消费方复用，ruleCode 溯源，DB 权威）────────

    @Override
    public String triggerAlertByRuleCode(String ruleCode, String provider, String metricKey, String severity,
                                         Double threshold, Integer upgradeMin,
                                         String alertType, String nodeId, String taskId, String message)
            throws IAlertService.AlertException {
        if (ruleCode == null || ruleCode.trim().isEmpty()) {
            throw new IAlertService.AlertException("Rule code cannot be null");
        }
        AlertRule rule = findOrCreateRuleByCode(ruleCode, provider, metricKey, severity, threshold, upgradeMin);
        if (rule == null) {
            // 无 DB 且缓存无此 ruleCode（dev 态纯内存）→ 内存兜底建规则，保证触发链可演练
            rule = new AlertRule();
            rule.setRuleId(UUID.randomUUID().toString());
            rule.setRuleName(ruleCode);
            rule.setMetricType(ruleCode);
            rule.setAlertLevel(severityToLevel(severity));
            rule.setEnabled("1");
            rules.put(rule.getRuleId(), rule);
        }

        AlertRecord record = new AlertRecord();
        record.setAlertId(UUID.randomUUID().toString());
        record.setRuleId(rule.getRuleId());
        record.setRuleName(rule.getRuleName() != null ? rule.getRuleName() : ruleCode);
        record.setAlertType(alertType != null ? alertType : rule.getMetricType());
        record.setNodeId(nodeId);
        record.setTaskId(taskId);
        record.setAlertMessage(message);
        record.setAlertStatus("PENDING");
        record.setAlertLevel(rule.getAlertLevel() != null ? rule.getAlertLevel() : severityToLevel(severity));
        record.setAlertTime(new Timestamp(System.currentTimeMillis()));
        records.put(record.getAlertId(), record);

        if (recordMapper != null) {
            try {
                AlertRecordRow row = new AlertRecordRow();
                row.setId(record.getAlertId());
                row.setRuleCode(ruleCode);
                row.setSeverity(severityToSeverity(record.getAlertLevel()));
                row.setStatus("open");
                String title = (record.getAlertType() == null ? "" : record.getAlertType())
                        + (message == null ? "" : " " + message);
                row.setTitle(title.length() > 255 ? title.substring(0, 255) : title);
                row.setDetailJson(buildDetailJson(nodeId, taskId, message, record.getAlertLevel()));
                row.setSourceModule(provider != null ? provider : "runtime-core");
                row.setTraceId(TraceContext.current());
                row.setOccurredAt(record.getAlertTime());
                row.setVersionNo(DEFAULT_VERSION_NO);
                row.setIsDeleted((short) 0);
                row.setDomain(DEFAULT_DOMAIN);
                recordMapper.insert(row);
            } catch (Exception e) {
                log.warn("[Alert] tracker record 落库失败降级纯内存 ruleCode={} errType={} msg={}",
                        ruleCode, e.getClass().getSimpleName(), e.getMessage());
            }
        }
        return record.getAlertId();
    }

    /** 按 ruleCode 查规则（DB 优先 → 缓存）；DB 可用且不存在时自动创建（provider 落 create_by） */
    private AlertRule findOrCreateRuleByCode(String ruleCode, String provider, String metricKey, String severity,
                                             Double threshold, Integer upgradeMin) {
        if (ruleMapper != null) {
            AlertRuleRow row;
            try {
                row = ruleMapper.findByRuleCode(ruleCode);
            } catch (Exception e) {
                log.debug("[Alert] rule DB 查询失败回退缓存 ruleCode={} err={}", ruleCode, e.getMessage());
                row = null;
            }
            if (row != null) {
                return toDomain(row);
            }
            // 自动创建（幂等 by uk (tenant_id, rule_code)；并发重复插入 catch 兜底再查）
            try {
                AlertRuleRow created = new AlertRuleRow();
                created.setId(UUID.randomUUID().toString());
                created.setRuleCode(ruleCode);
                created.setMetricKey(metricKey != null ? metricKey : ruleCode);
                created.setSeverity(severity != null && !severity.isEmpty() ? severity.toLowerCase() : "warn");
                created.setThresholdNum(threshold != null ? BigDecimal.valueOf(threshold) : null);
                created.setWindowSec(300);
                created.setUpgradeMin(upgradeMin != null ? upgradeMin.shortValue() : null);
                created.setEnabled((short) 1);
                created.setChannel("log");
                created.setVersionNo(DEFAULT_VERSION_NO);
                created.setIsDeleted((short) 0);
                created.setDomain(DEFAULT_DOMAIN);
                created.setCreateBy(provider != null && provider.length() <= 36 ? provider : "runtime");
                ruleMapper.insert(created);
                return toDomain(created);
            } catch (Exception e) {
                // uk 冲突（并发双建）→ 再查一次；其他错误（表缺失）返回 null 走内存兜底
                log.warn("[Alert] rule 自动创建失败 ruleCode={} errType={} msg={}",
                        ruleCode, e.getClass().getSimpleName(), e.getMessage());
                try {
                    row = ruleMapper.findByRuleCode(ruleCode);
                    if (row != null) {
                        return toDomain(row);
                    }
                } catch (Exception ignore) {
                    // fall through to memory
                }
                return null;
            }
        }
        // 无 DB：缓存里找 metricType/ruleName 匹配
        for (AlertRule r : rules.values()) {
            if (ruleCode.equals(r.getMetricType()) || ruleCode.equals(r.getRuleName())) {
                return r;
            }
        }
        return null;
    }

    // ── 内存兜底路径（既有序列，签名与语义不动）────────────────────

    private List<AlertRecord> queryRecordsFromMemory(String ruleId, String alertStatus, String alertLevel,
            Integer page, Integer size) {
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

    private boolean dbRuleExists(String ruleId) {
        if (ruleMapper == null) {
            return false;
        }
        try {
            return ruleMapper.findById(ruleId) != null;
        } catch (Exception e) {
            log.debug("[Alert] rule 存在性 DB 查询失败 ruleId={} err={}", ruleId, e.getMessage());
            return false;
        }
    }

    // ── 内部映射工具 ─────────────────────────────────────────────

    /** P0~P3 → info|warn|error|critical（P3 最低 → info；未知降级 warn） */
    static String levelToSeverity(String alertLevel) {
        if (alertLevel == null) {
            return "warn";
        }
        switch (alertLevel.toUpperCase()) {
            case "P0": return "critical";
            case "P1": return "error";
            case "P2": return "warn";
            case "P3": return "info";
            default:   return alertLevel.toLowerCase().matches("info|warn|error|critical")
                    ? alertLevel.toLowerCase() : "warn";
        }
    }

    private String buildDetailJson(String nodeId, String taskId, String message, String level) {
        StringBuilder sb = new StringBuilder(256).append('{');
        appendJson(sb, "nodeId", nodeId);
        appendJson(sb, "taskId", taskId);
        appendJson(sb, "level", level);
        appendJson(sb, "message", message);
        sb.append('}');
        return sb.toString();
    }

    private static void appendJson(StringBuilder sb, String key, String value) {
        if (sb.charAt(sb.length() - 1) != '{') {
            sb.append(',');
        }
        sb.append('"').append(key).append("\":");
        if (value == null) {
            sb.append("null");
        } else {
            sb.append('"').append(value.replace("\\", "\\\\").replace("\"", "\\\"")).append('"');
        }
    }

    static AlertRuleRow toRow(AlertRule rule) {
        AlertRuleRow row = new AlertRuleRow();
        row.setId(rule.getRuleId());
        row.setRuleCode(rule.getMetricType() != null ? rule.getMetricType() : rule.getRuleId());
        row.setMetricKey(rule.getMetricType() != null ? rule.getMetricType() : "custom");
        row.setSeverity(levelToSeverity(rule.getAlertLevel()));
        row.setThresholdNum(rule.getThreshold() != null ? BigDecimal.valueOf(rule.getThreshold()) : null);
        row.setWindowSec(rule.getDuration() != null ? rule.getDuration() : 300);
        row.setEnabled("1".equals(rule.getEnabled()) ? (short) 1 : (short) 0);
        row.setChannel("log");
        row.setVersionNo(DEFAULT_VERSION_NO);
        row.setIsDeleted((short) 0);
        row.setDomain(DEFAULT_DOMAIN);
        row.setCreateBy(rule.getCreateBy());
        row.setUpdateBy(rule.getUpdateBy());
        return row;
    }

    static AlertRule toDomain(AlertRuleRow row) {
        AlertRule rule = new AlertRule();
        rule.setRuleId(row.getId());
        rule.setRuleName(row.getRuleCode());
        rule.setMetricType(row.getMetricKey());
        rule.setAlertLevel(severityToLevel(row.getSeverity()));
        rule.setEnabled(row.getEnabled() != null && row.getEnabled() == 1 ? "1" : "0");
        rule.setDescription(row.getRuleCode());
        rule.setCreateTime(row.getCreateTime());
        rule.setUpdateTime(row.getUpdateTime());
        rule.setCreateBy(row.getCreateBy());
        rule.setUpdateBy(row.getUpdateBy());
        if (row.getThresholdNum() != null) {
            rule.setThreshold(row.getThresholdNum().doubleValue());
        }
        if (row.getWindowSec() != null) {
            rule.setDuration(row.getWindowSec());
        }
        return rule;
    }

    static AlertRecord toMemoryRecord(AlertRecordRow row) {
        AlertRecord record = new AlertRecord();
        record.setAlertId(row.getId());
        record.setRuleId(row.getRuleCode());
        record.setRuleName(row.getSourceModule());
        record.setAlertType(row.getTitle());
        record.setAlertLevel(severityToLevel(row.getSeverity()));
        record.setAlertStatus("open".equals(row.getStatus()) ? "PENDING"
                : "acked".equals(row.getStatus()) ? "NOTIFIED" : "RESOLVED");
        record.setAlertMessage(row.getDetailJson());
        record.setAlertTime(row.getOccurredAt());
        if ("closed".equals(row.getStatus())) {
            record.setResolveTime(row.getCloseTime());
            record.setResolveBy(row.getAckBy());
        } else if ("acked".equals(row.getStatus())) {
            record.setLastNotificationTime(row.getAckTime());
        }
        return record;
    }

    static String severityToLevel(String severity) {
        if (severity == null) {
            return "P3";
        }
        switch (severity.toLowerCase()) {
            case "critical": return "P0";
            case "error":    return "P1";
            case "warn":     return "P2";
            case "info":     return "P3";
            default:         return "P3";
        }
    }

    /** 反向映射：内部 AlertLevel（P0~P3）→ DB severity 形态；null → 原样返回 */
    static String severityToSeverity(String alertLevel) {
        if (alertLevel == null) {
            return null;
        }
        switch (alertLevel.toUpperCase()) {
            case "P0":  return "critical";
            case "P1":  return "error";
            case "P2":  return "warn";
            case "P3":  return "info";
            default:    return alertLevel.toLowerCase();
        }
    }

    // ── 测试可见（单测跨包 @VisibleForTesting）────────────────────

    /** @implNote 测试可见：进程内缓存读口（纯内存模式断言用） */
    public ConcurrentMap<String, AlertRule> cachedRules() {
        return rules;
    }

    /** @implNote 测试可见：进程内缓存读口（纯内存模式断言用） */
    public ConcurrentMap<String, AlertRecord> cachedRecords() {
        return records;
    }

    /** 测试可见 mapper 注入口（替代生产装配路径） */
    public void setRuleMapper(AlertRuleMapper ruleMapper) {
        this.ruleMapper = ruleMapper;
    }

    /** 测试可见 mapper 注入口（替代生产装配路径） */
    public void setRecordMapper(AlertRecordMapper recordMapper) {
        this.recordMapper = recordMapper;
    }
}
