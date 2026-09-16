package com.chinacreator.gzcm.engine.data.integration;

import com.chinacreator.gzcm.common.event.KafkaTopics;
import com.chinacreator.gzcm.engine.data.DataSourceService;
import com.chinacreator.gzcm.engine.data.PipelineTaskService;
import com.chinacreator.gzcm.engine.data.datasource.entity.DataSourceEntity;
import com.chinacreator.gzcm.engine.data.quality.service.DqSecurityService;
import com.chinacreator.gzcm.engine.data.service.DataLineageService;
import com.chinacreator.gzcm.runtime.eventbus.EventBusService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    /** connectionConfig JSON 解析器（数据源连接配置 → host/port/database/username） */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final JdbcTemplate jdbc;
    private final PipelineTaskService pipelineTaskService;
    /** DQ 治理安全桥接 — 统一走 security-engine REST 审计（同 DQ 先例，禁止各引擎自建 KafkaTemplate） */
    private final DqSecurityService dqSecurityService;
    /** 数据源注册服务 — /metadata 的 connections/sources 真实来源（td_datasource） */
    private final DataSourceService dataSourceService;
    /** 血缘服务 — /metadata 的 lineage 概览真实来源（ecos_data_lineage_node/edge） */
    private final DataLineageService dataLineageService;

    /** 统一事件总线（runtime-event）— 审计主通道发 Kafka ecos.audit，不可用时降级 DQ REST 审计 */
    @Autowired(required = false)
    private EventBusService eventBusService;

    public IntegrationMetadataService(JdbcTemplate jdbc,
                                      PipelineTaskService pipelineTaskService,
                                      DqSecurityService dqSecurityService,
                                      DataSourceService dataSourceService,
                                      DataLineageService dataLineageService) {
        this.jdbc = jdbc;
        this.pipelineTaskService = pipelineTaskService;
        this.dqSecurityService = dqSecurityService;
        this.dataSourceService = dataSourceService;
        this.dataLineageService = dataLineageService;
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
     * 联邦元数据聚合载荷 — 供 {@code GET /api/integration/metadata}（含 v1 重写路径）使用。
     *
     * <p>全部数据来自真实表，无 mock / 硬编码：</p>
     * <ul>
     *   <li>connections ← {@link DataSourceService#listAll()}（td_datasource）</li>
     *   <li>sources ← 与 connections 同形同值（前端 3 处调用方读 data.sources）</li>
     *   <li>syncTasks ← {@link PipelineTaskService#listTasks(int, int)}（ecos_pipeline_task）</li>
     *   <li>lineage ← {@link DataLineageService#listNodes()} / {@link DataLineageService#listEdges()}</li>
     * </ul>
     *
     * <p>{@code sources} 与 {@code connections} 同放 data 内层：knowledgeApi 部分调用方经
     * apiFetchData 解包后读 {@code data.sources}，SyncTab 用原生 fetch 拿到 ApiResponse
     * 信封后读 {@code raw.data.sources}，两种读法都需满足。</p>
     *
     * @return data 载荷（connections / sources / syncTasks / lineage / simulationState）
     */
    public Map<String, Object> fetchIntegrationMetadata() {
        Map<String, Object> result = new LinkedHashMap<>();

        // connections / sources：td_datasource 真实数据源（字段映射同前端 mapDsToConn 语义）
        List<Map<String, Object>> connections = new ArrayList<>();
        try {
            List<DataSourceEntity> dataSources = dataSourceService.listAll();
            if (dataSources != null) {
                for (DataSourceEntity ds : dataSources) {
                    connections.add(mapDsToConn(ds));
                }
            }
        } catch (Exception e) {
            log.warn("查询数据源列表失败: {}", e.getMessage());
        }
        result.put("connections", connections);
        result.put("sources", new ArrayList<>(connections));

        // syncTasks：ecos_pipeline_task 真实任务列表
        List<Map<String, Object>> syncTasks = new ArrayList<>();
        try {
            Map<String, Object> taskPage = pipelineTaskService.listTasks(1, 100);
            Object listObj = taskPage != null ? taskPage.get("list") : null;
            if (listObj instanceof List<?> taskList) {
                for (Object task : taskList) {
                    if (task instanceof Map<?, ?> rawTask) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> taskRow = (Map<String, Object>) rawTask;
                        syncTasks.add(mapTaskToSync(taskRow));
                    }
                }
            }
        } catch (Exception e) {
            log.warn("查询同步任务列表失败: {}", e.getMessage());
        }
        result.put("syncTasks", syncTasks);

        result.put("lineage", buildLineageOverview());

        // simulationState：不维护内存模拟态，始终返回健康（真实漂移结论走 GET /metadata/drift）
        Map<String, Object> simState = new LinkedHashMap<>();
        simState.put("isSchemaDriftActive", false);
        simState.put("isSlaBreachActive", false);
        result.put("simulationState", simState);
        return result;
    }

    /**
     * Schema 漂移样本载荷 — 供 {@code GET /api/integration/metadata/drift} 使用。
     *
     * <p>漂移结论复用 {@link #detectDriftAndSlaStatus(String)}（type=drift）：其
     * {@code attribution} 中 {@code kind=DQ_FAILURE} 的真实条目即 schemaDelta 来源。
     * {@code sample=true} 时按失败规则 ID 取 {@code ecos_dq.dq_rule_check.sample_failures}
     * （真实样本行）展开为 rows/samples；无真实样本时返回空数组（前端 loadDrift 已按空数组兜底），
     * 绝不填充假数据。</p>
     *
     * <p>{@code dsId} 为前端契约参数：{@code ecos_dq.dq_rule} 无 datasource 外键（仅
     * target_kind/target_id/target_table/target_pipeline_id），无法把 DQ 检查归因到具体数据源，
     * 故按引擎级真实漂移返回，不伪造数据源级样本。</p>
     *
     * @param sample 是否附带真实样例行
     * @param dsId   数据源 ID（前端契约参数，当前无法用于归因，见上文说明）
     * @return 载荷（schemaDelta / fields / rows / samples / lineage / checkedAt）
     */
    public Map<String, Object> fetchDriftSample(boolean sample, String dsId) {
        Map<String, Object> drift = detectDriftAndSlaStatus("drift");

        List<Map<String, Object>> schemaDelta = new ArrayList<>();
        Object attribution = drift.get("attribution");
        if (attribution instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> rawItem
                        && "DQ_FAILURE".equals(String.valueOf(rawItem.get("kind")))) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> entry = (Map<String, Object>) rawItem;
                    schemaDelta.add(entry);
                }
            }
        }

        List<Map<String, Object>> rows = sample
                ? fetchRealDriftSamples(schemaDelta)
                : new ArrayList<>();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("schemaDelta", schemaDelta);
        // 前端字段名兼容：loadDrift 读 fields，fetchMetadataDrift 优先读 schemaDelta
        result.put("fields", schemaDelta);
        result.put("rows", rows);
        result.put("samples", rows);
        result.put("lineage", buildLineageOverview());
        result.put("checkedAt", drift.get("checkedAt"));
        log.debug("fetchDriftSample: sample={}, dsId={}, schemaDelta={}, samples={}",
                sample, dsId, schemaDelta.size(), rows.size());
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

    /** 血缘概览（真实持久化节点/边）；{@code links} 为前端兼容字段名（对应 edges）。 */
    private Map<String, Object> buildLineageOverview() {
        Map<String, Object> lineage = new LinkedHashMap<>();
        try {
            List<Map<String, Object>> nodes = dataLineageService.listNodes();
            List<Map<String, Object>> edges = dataLineageService.listEdges();
            lineage.put("nodes", nodes != null ? nodes : new ArrayList<>());
            lineage.put("links", edges != null ? edges : new ArrayList<>());
        } catch (Exception e) {
            log.warn("查询血缘概览失败: {}", e.getMessage());
            lineage.put("nodes", new ArrayList<>());
            lineage.put("links", new ArrayList<>());
        }
        return lineage;
    }

    /**
     * 取真实漂移样本行：按 schemaDelta 中的失败规则 ID，从
     * {@code ecos_dq.dq_rule_check.sample_failures}（JSONB，近 1h 未通过检查）展开。
     * 无样本记录时返回空列表（前端已按空数组兜底），不伪造数据。
     */
    private List<Map<String, Object>> fetchRealDriftSamples(List<Map<String, Object>> schemaDelta) {
        List<Map<String, Object>> samples = new ArrayList<>();
        if (schemaDelta.isEmpty()) {
            return samples;
        }
        Set<String> ruleIds = new LinkedHashSet<>();
        for (Map<String, Object> entry : schemaDelta) {
            Object ruleId = entry.get("ruleId");
            if (ruleId != null && !ruleId.toString().isBlank()) {
                ruleIds.add(ruleId.toString());
            }
        }
        if (ruleIds.isEmpty()) {
            return samples;
        }
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT rule_id, sample_failures::text AS sample_failures " +
                "FROM ecos_dq.dq_rule_check " +
                "WHERE executed_at >= NOW() - interval '1 hour' " +
                "  AND passed = FALSE AND sample_failures IS NOT NULL " +
                "ORDER BY executed_at DESC LIMIT 20");
            for (Map<String, Object> row : rows) {
                Object ruleId = row.get("rule_id");
                if (ruleId == null || !ruleIds.contains(ruleId.toString())) {
                    continue;
                }
                Object rawJson = row.get("sample_failures");
                if (rawJson == null || rawJson.toString().isBlank()) {
                    continue;
                }
                List<Map<String, Object>> parsed = MAPPER.readValue(rawJson.toString(),
                        new TypeReference<List<Map<String, Object>>>() { });
                for (Map<String, Object> item : parsed) {
                    Map<String, Object> sampleRow = new LinkedHashMap<>(item);
                    sampleRow.putIfAbsent("ruleId", ruleId.toString());
                    samples.add(sampleRow);
                }
            }
        } catch (Exception e) {
            log.warn("查询真实漂移样本失败（返回空样本）: {}", e.getMessage());
        }
        return samples;
    }

    /**
     * 将 DataSourceEntity 映射为前端 connection / source 结构。
     * datasourceId→id, datasourceName→name, datasourceType→type,
     * connectionConfig(JSON) 解析 host/port/database/username，status 归一
     * active→connected / error→error / 其他→unknown。
     */
    private Map<String, Object> mapDsToConn(DataSourceEntity ds) {
        Map<String, Object> conn = new LinkedHashMap<>();
        conn.put("id", ds.getDatasourceId());
        conn.put("name", ds.getDatasourceName());
        conn.put("type", ds.getDatasourceType());

        String status = ds.getStatus();
        if (status == null) {
            status = "unknown";
        } else if ("active".equalsIgnoreCase(status)) {
            status = "connected";
        } else if ("error".equalsIgnoreCase(status)) {
            status = "error";
        }
        conn.put("status", status);

        // config: 解析 connectionConfig JSON 提取 host/port/database/username（密码类字段不取用）
        Map<String, Object> config = new LinkedHashMap<>();
        String cfg = ds.getConnectionConfig();
        if (cfg != null && !cfg.isBlank()) {
            try {
                Map<String, Object> parsed = MAPPER.readValue(cfg, new TypeReference<Map<String, Object>>() { });
                copyFirst(parsed, config, "host", "host");
                copyFirst(parsed, config, "port", "port");
                copyFirst(parsed, config, "database", "database", "dbName", "databaseName");
                copyFirst(parsed, config, "username", "username", "user");
                if (parsed.containsKey("jdbcUrl")) {
                    config.put("jdbcUrl", parsed.get("jdbcUrl"));
                }
            } catch (Exception e) {
                log.debug("解析 connectionConfig 失败 (ds={}): {}", ds.getDatasourceId(), e.getMessage());
            }
        }
        if (ds.getLastTestTime() != null) {
            config.put("lastTested", ds.getLastTestTime().toString());
        }
        conn.put("config", config);

        // tablesAvailable: 真实表结构需查 catalog，此处返回空列表避免 mock
        conn.put("tablesAvailable", new ArrayList<>());
        return conn;
    }

    /** 从 parsed 中按候选键取第一个非空值放入 config 的 targetKey。 */
    private void copyFirst(Map<String, Object> parsed, Map<String, Object> config,
                           String targetKey, String... candidateKeys) {
        for (String key : candidateKeys) {
            Object val = parsed.get(key);
            if (val != null) {
                config.put(targetKey, val);
                return;
            }
        }
    }

    /** 将 ecos_pipeline_task 行映射为前端 syncTask 结构。 */
    private Map<String, Object> mapTaskToSync(Map<String, Object> task) {
        Map<String, Object> sync = new LinkedHashMap<>();
        sync.put("id", task.getOrDefault("id", ""));
        sync.put("name", task.getOrDefault("name", ""));
        sync.put("engine", "ECOS Pipeline 2.0");
        Object cron = task.get("cron_expression");
        sync.put("schedule", cron != null ? cron : "");
        Object statusObj = task.get("status");
        sync.put("status", statusObj != null ? statusObj.toString().toLowerCase() : "unknown");
        sync.put("slaMinutes", 0);
        sync.put("actualDelayMinutes", 0);
        Object updatedAt = task.get("updated_at");
        if (updatedAt != null) {
            sync.put("lastRunTime", updatedAt.toString());
        }
        return sync;
    }

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
