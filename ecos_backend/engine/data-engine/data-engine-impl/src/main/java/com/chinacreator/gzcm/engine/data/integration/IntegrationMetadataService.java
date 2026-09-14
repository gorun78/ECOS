package com.chinacreator.gzcm.engine.data.integration;

import com.chinacreator.gzcm.common.event.KafkaTopics;
import com.chinacreator.gzcm.engine.data.PipelineTaskService;
import com.chinacreator.gzcm.engine.data.quality.service.DqSecurityService;
import com.chinacreator.gzcm.runtime.eventbus.EventBusService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;

/**
 * IntegrationMetadataService — 联邦元数据真实数据查询（T1: 替换 stub）。
 *
 * <p>数据来源（架构铁律 §2.4 审计 / §2.5 总线）：</p>
 * <ul>
 *   <li>logs: 优先查 {@code td_audit_log}（V40__ecos_security_seed.sql 建在 default schema，
 *       位于 {@code public.td_audit_log} 而非 {@code ecos_security} 下），
 *       回退 {@code ecos_audit_log}（AOP CUD 审计），再回退 {@code ecos_object_timeline}
 *       （workspace 结构化事件）。按时间倒序，带 time range + limit 过滤。</li>
 *   <li>drift: 查 {@code ecos_dq.dq_rule_check} 最近 1h 数据质量检测结果（V111__ecos_dq_governance_v1.sql
 *       建表）+ {@code ecos_pipeline_task} 最近运行状态，综合生成漂移/断流结论。</li>
 * </ul>
 *
 * <p>Controller 只调本 Service（铁律：Controller 不直用 JdbcTemplate），
 * 构造器注入 JdbcTemplate / PipelineTaskService / DqSecurityService；
 * EventBusService 用 {@code @Autowired(required=false)} 兜底注入（不可用时审计降级
 * 为 DqSecurityService → security-engine REST，不阻塞主流程）。</p>
 *
 * @author ECOS Integration
 */
@Service
public class IntegrationMetadataService {

    private static final Logger log = LoggerFactory.getLogger(IntegrationMetadataService.class);

    private final JdbcTemplate jdbc;
    private final PipelineTaskService pipelineTaskService;
    /** DQ 治理安全桥接 — 统一走 security-engine REST 审计（同 DQ 先例，禁止各引擎自建 KafkaTemplate） */
    private final DqSecurityService dqSecurityService;

    /** 统一事件总线（runtime-event）— 审计主通道发 Kafka ecos.audit，不可用时降级 DQ REST 审计 */
    @Autowired(required = false)
    private EventBusService eventBusService;

    public IntegrationMetadataService(JdbcTemplate jdbc,
                                      PipelineTaskService pipelineTaskService,
                                      DqSecurityService dqSecurityService) {
        this.jdbc = jdbc;
        this.pipelineTaskService = pipelineTaskService;
        this.dqSecurityService = dqSecurityService;
    }

    /**
     * 查询集成审计日志。
     *
     * @param timeRange 时间窗口小时数（null=默认 24h）
     * @param limit     条数上限（null=默认 200）
     * @return 日志列表（severity/event/details/timestamp），按时间倒序
     */
    public List<Map<String, Object>> fetchIntegrationLogs(Integer timeRange, Integer limit) {
        int hours = timeRange == null || timeRange <= 0 ? 24 : Math.min(timeRange, 720);
        int maxLimit = limit == null || limit <= 0 ? 200 : Math.min(limit, 500);

        // 数据源 1：td_audit_log（security-engine 持久化审计，V40 建在 default schema）
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT event_type, timestamp, resource, action, result, details " +
                "FROM td_audit_log " +
                "WHERE timestamp >= NOW() - make_interval(hours => ?) " +
                "ORDER BY timestamp DESC LIMIT ?", hours, maxLimit);
            List<Map<String, Object>> mapped = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                mapped.add(mapAuditRow(row));
            }
            if (!mapped.isEmpty()) {
                return mapped;
            }
        } catch (Exception e) {
            log.debug("td_audit_log 不可用，回退 ecos_audit_log: {}", e.getMessage());
        }

        // 数据源 2：ecos_audit_log（sysman AOP CUD 审计）
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT operation, entity_type, entity_id, changes, created_at, category " +
                "FROM ecos_audit_log " +
                "WHERE created_at >= NOW() - make_interval(hours => ?) " +
                "ORDER BY created_at DESC LIMIT ?", hours, maxLimit);
            List<Map<String, Object>> mapped = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                Map<String, Object> item = new LinkedHashMap<>();
                Object operation = row.get("operation");
                Object category = row.get("category");
                item.put("severity", category != null && "HIGH".equalsIgnoreCase(category.toString()) ? "HIGH" : "NORMAL");
                item.put("event", operation != null ? operation.toString() : "OPERATION");
                String details = "resource=" + row.get("entity_type") + ":" + row.get("entity_id");
                Object changes = row.get("changes");
                if (changes != null && !changes.toString().isBlank()) {
                    details += " changes=" + changes;
                }
                item.put("details", details);
                item.put("timestamp", row.get("created_at") != null ? row.get("created_at").toString() : "");
                mapped.add(item);
            }
            if (!mapped.isEmpty()) {
                return mapped;
            }
        } catch (Exception e) {
            log.debug("ecos_audit_log 不可用，回退 ecos_object_timeline: {}", e.getMessage());
        }

        // 数据源 3：ecos_object_timeline（workspace 结构化对象事件）
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT event_type, event_summary, actor, details, created_at " +
                "FROM ecos_object_timeline " +
                "WHERE created_at >= NOW() - make_interval(hours => ?) " +
                "ORDER BY created_at DESC LIMIT ?", hours, maxLimit);
            List<Map<String, Object>> mapped = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("severity", determineSeverity(row.get("event_type"), row.get("details")));
                item.put("event", row.get("event_type") != null ? row.get("event_type").toString() : "OBJECT_EVENT");
                String details = row.get("event_summary") != null ? row.get("event_summary").toString() : "";
                if (row.get("details") != null && !row.get("details").toString().isBlank()) {
                    details += (details.isEmpty() ? "" : " ") + row.get("details");
                }
                if (row.get("actor") != null) {
                    details += (details.isEmpty() ? "" : " ") + "by " + row.get("actor");
                }
                item.put("details", details);
                item.put("timestamp", row.get("created_at") != null ? row.get("created_at").toString() : "");
                mapped.add(item);
            }
            return mapped;
        } catch (Exception e) {
            log.warn("集成日志查询全部数据源不可用: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * 查询 Schema 漂移 / SLA 断流检测结论。
     *
     * @param type drift / sla / reset
     * @return 结论（success/message/isSchemaDriftActive/isSlaBreachActive + 明细）
     */
    public Map<String, Object> detectDriftAndSlaStatus(String type) {
        String drift = (type == null || type.isBlank()) ? "drift" : type.trim().toLowerCase();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        String source = "ecos_dq.dq_rule_check (recent 1h) + ecos_pipeline_task";

        // 最近 1h 未通过的数据质量检查
        List<Map<String, Object>> failedChecks = new ArrayList<>();
        try {
            failedChecks = jdbc.queryForList(
                "SELECT rc.rule_id, rc.passed, rc.total_rows, rc.failed_rows, rc.pass_rate, rc.executed_at " +
                "FROM ecos_dq.dq_rule_check rc " +
                "WHERE rc.executed_at >= NOW() - interval '1 hour' " +
                "ORDER BY rc.executed_at DESC LIMIT 20");
        } catch (Exception e) {
            log.debug("dq_rule_check 查询失败（表可能未初始化）: {}", e.getMessage());
        }
        List<Map<String, Object>> failures = new ArrayList<>();
        for (Map<String, Object> row : failedChecks) {
            Boolean passed = row.get("passed") instanceof Boolean b ? b : Boolean.FALSE;
            if (!passed) {
                failures.add(row);
            }
        }

        boolean driftActive = !failures.isEmpty();
        boolean slaBreach = false;
        List<Map<String, Object>> slaTasks = new ArrayList<>();
        // SLA 维度：最近 1h 有变化的 pipeline task 中，非终态成功/失败的视为断流风险
        try {
            Map<String, Object> tasks = pipelineTaskService.listTasks(1, 100);
            Object listObj = tasks != null ? tasks.get("list") : null;
            if (listObj instanceof List) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> taskList = (List<Map<String, Object>>) listObj;
                LocalDateTime cutoff = LocalDateTime.now().minusHours(1);
                for (Map<String, Object> task : taskList) {
                    Object updatedAt = task.get("updated_at");
                    if (updatedAt == null) {
                        continue;
                    }
                    String status = task.get("status") != null ? task.get("status").toString().toUpperCase() : "";
                    boolean recent = isTimestampRecent(updatedAt.toString(), cutoff);
                    if (recent && !"SUCCESS".equals(status) && !"FAILED".equals(status) && !"SUCCEEDED".equals(status)) {
                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("id", String.valueOf(task.getOrDefault("id", "")));
                        item.put("name", String.valueOf(task.getOrDefault("name", "")));
                        item.put("status", status);
                        slaTasks.add(item);
                    }
                }
            }
        } catch (Exception e) {
            log.debug("查询 pipeline task 状态失败: {}", e.getMessage());
        }
        if (!slaTasks.isEmpty()) {
            slaBreach = true;
        }

        boolean effectiveDrift = driftActive || slaBreach;
        if ("sla".equals(drift)) {
            effectiveDrift = slaBreach || driftActive;
        }

        String message;
        if ("reset".equals(drift)) {
            message = effectiveDrift
                    ? "复位请求已受理。当前仍存在实时质量风险：" + buildEffectiveSummary(failures, slaTasks) + "。"
                    : "复位请求已受理。当前无 Schema 漂移或 SLA 断流，全网状态稳定。";
        } else if ("sla".equals(drift)) {
            message = effectiveDrift
                    ? "SLA 断流检测：近 1h 存在风险。" + buildEffectiveSummary(failures, slaTasks)
                    : "SLA 断流检测：近 1h 无未通过质量检查与断流风险任务。";
        } else {
            message = effectiveDrift
                    ? "Schema 漂移/数据质量检测：近 1h 存在 " + failures.size()
                    + " 条未通过质量检查"
                    + (effectiveDrift && !slaTasks.isEmpty() ? "，另有 " + slaTasks.size() + " 个同步任务断流风险" : "")
                    + "。明细见 attribution。"
                    : "Schema 漂移检测：近 1h " + failedChecks.size()
                    + " 条质量检查记录均未发现问题，无漂移。";
        }

        result.put("message", message);
        result.put("dataSource", source);
        Map<String, Object> simState = new LinkedHashMap<>();
        simState.put("isSchemaDriftActive", driftActive || slaBreach);
        simState.put("isSlaBreachActive", slaBreach);
        result.put("simulationState", simState);
        result.put("attribution", buildAttribution(failures, slaTasks));
        result.put("checkedAt", Timestamp.valueOf(LocalDateTime.now()));

        // 写操作审计：drift 检测触发落审计（EventBusService 主通道 → Kafka ecos.audit；
        // EventBusService 不可用时降级 DqSecurityService → security-engine REST）
        publishAudit("INTEGRATION_DRIFT_DETECT", "integration:drift:" + drift,
                "driftType=" + drift + ", failures=" + failures.size() + ", slaTasks=" + slaTasks.size());
        return result;
    }

    /**
     * 写操作审计 — 主通道 EventBusService（runtime-event）发 Kafka ecos.audit（铁律 §2.4 #5）；
     * EventBusService 不可用时降级 DqSecurityService → security-engine REST 审计。失败不阻塞主流程。
     */
    private void publishAudit(String action, String resource, String detail) {
        if (eventBusService != null) {
            try {
                Map<String, Object> event = new LinkedHashMap<>();
                event.put("action", action);
                event.put("resource", resource);
                event.put("result", "SUCCESS");
                event.put("detail", detail);
                event.put("userId", "system");
                event.put("timestamp", LocalDateTime.now().toString());
                eventBusService.publish(KafkaTopics.AUDIT, event);
            } catch (Exception e) {
                log.warn("Integration drift audit (EventBusService) failed, fallback to DqSecurityService: {}", e.getMessage());
            }
        }
        // 降级通道 / 双通道：DQ security-engine REST 审计
        try {
            dqSecurityService.auditWrite(action, resource, "SUCCESS");
        } catch (Exception e) {
            log.warn("Integration drift audit (DqSecurityService) failed (ignored): {}", e.getMessage());
        }
    }

    // ── 内部方法 ──────────────────────────────

    /** 将 td_audit_log 行映射为前端日志结构。 */
    private Map<String, Object> mapAuditRow(Map<String, Object> row) {
        Map<String, Object> item = new LinkedHashMap<>();
        String result = row.get("result") != null ? row.get("result").toString().toUpperCase() : "";
        item.put("severity", determineSeverity(row.get("event_type"), result));
        item.put("event", row.get("event_type") != null ? row.get("event_type").toString() : "AUDIT");
        String details = "action=" + row.get("action") + ", resource=" + row.get("resource")
                + ", result=" + result;
        if (row.get("details") != null && !row.get("details").toString().isBlank()) {
            details += ", detail=" + row.get("details");
        }
        item.put("details", details);
        Object ts = row.get("timestamp");
        item.put("timestamp", ts != null ? ts.toString() : "");
        return item;
    }

    /** 简单 severity 判定：失败/拒绝/异常类事件为 HIGH。 */
    private String determineSeverity(Object eventType, Object resultOrDetails) {
        String joined = ((eventType != null ? eventType : "") + " "
                + (resultOrDetails != null ? resultOrDetails : "")).toUpperCase();
        for (String kw : new String[]{"FAIL", "ERROR", "REJECT", "DENY", "BLOCK", "异常", "失败"}) {
            if (joined.contains(kw)) {
                return "HIGH";
            }
        }
        return "NORMAL";
    }

    /** 判断时间戳字符串是否晚于 cutoff。 */
    private boolean isTimestampRecent(String raw, LocalDateTime cutoff) {
        try {
            String t = raw.trim().replace('T', ' ').replace("+08:00", "");
            LocalDateTime ts = LocalDateTime.parse(t.length() > 19 ? t.substring(0, 19) : t,
                    java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            return !ts.isBefore(cutoff);
        } catch (Exception e) {
            return false;
        }
    }

    /** 构建 drift/sla 检测结论说明。 */
    private String buildEffectiveSummary(List<Map<String, Object>> failures, List<Map<String, Object>> slaTasks) {
        StringBuilder sb = new StringBuilder();
        if (!failures.isEmpty()) {
            sb.append("未通过质量检查 ").append(failures.size()).append(" 条");
        }
        if (!slaTasks.isEmpty()) {
            sb.append(sb.isEmpty() ? "" : "，").append("断流风险同步任务 ").append(slaTasks.size()).append(" 个");
        }
        return sb.toString();
    }

    /** 构建 attribution 明细。 */
    private List<Map<String, Object>> buildAttribution(List<Map<String, Object>> failures,
                                                       List<Map<String, Object>> slaTasks) {
        List<Map<String, Object>> attribution = new ArrayList<>();
        for (Map<String, Object> row : failures) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("kind", "DQ_FAILURE");
            item.put("ruleId", row.get("rule_id"));
            item.put("totalRows", row.get("total_rows"));
            item.put("failedRows", row.get("failed_rows"));
            item.put("passRate", row.get("pass_rate"));
            item.put("executedAt", row.get("executed_at") != null ? row.get("executed_at").toString() : "");
            attribution.add(item);
        }
        for (Map<String, Object> task : slaTasks) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("kind", "SLA_RISK");
            item.put("taskId", task.get("id"));
            item.put("taskName", task.get("name"));
            item.put("status", task.get("status"));
            attribution.add(item);
        }
        return attribution;
    }
}
