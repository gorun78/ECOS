package com.chinacreator.gzcm.dccheng.ontology.controller;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.chinacreator.gzcm.common.base.ApiResponse;

/**
 * ceos_new 兼容层 — 提供前端 KnowledgeView / DataWorkbench 所需的 ceos_new 风格 API。
 *
 * <p>这些端点在 ceos_new server.ts (Express) 中存在，但 Java 后端原本缺少。
 * 使用内存存储 + 种子数据，与 ceos_new 行为一致。</p>
 *
 * <h3>端点：</h3>
 * <ul>
 *   <li>GET  /api/integration/metadata         — 联邦物理元数据（连接 + 同步任务 + 血缘 + 模拟状态）</li>
 *   <li>GET  /api/integration/logs             — 审计日志（映射为 severity/event/details 格式）</li>
 *   <li>POST /api/integration/metadata/drift   — 触发 Schema 漂移 / SLA 断流 / 重置模拟</li>
 *   <li>GET  /api/ontology/mappings            — 本体映射（ceos_new 风格，含 availableTables）</li>
 *   <li>POST /api/ontology/mappings            — 保存本体映射</li>
 *   <li>GET  /api/ontology/export              — 导出本体对齐知识包（Markdown）</li>
 *   <li>POST /api/lineage/parse                — 解析 OpenLineage / Atlas 血缘 JSON</li>
 *   <li>GET  /api/lineage/impact               — 下游影响度分析（BFS + 风险评分）</li>
 * </ul>
 */
@RestController
@RequestMapping("/api")
public class CeosCompatController {

    private static final Logger log = LoggerFactory.getLogger(CeosCompatController.class);
    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // ════════════════════════════════════════════
    // 内存状态（与 ceos_new server.ts state 对齐）
    // ════════════════════════════════════════════

    private volatile boolean isSchemaDriftActive = false;
    private volatile boolean isSlaBreachActive = false;

    /** 审计日志 */
    private final List<Map<String, Object>> auditLogs = new CopyOnWriteArrayList<>();

    /** 本体映射 */
    private final List<Map<String, Object>> ontologyMappings = new CopyOnWriteArrayList<>();

    /** 血缘图 */
    private final Map<String, Object> lineage = new ConcurrentHashMap<>();

    /** 连接列表 */
    private final List<Map<String, Object>> connections = new CopyOnWriteArrayList<>();

    /** 同步任务 */
    private final List<Map<String, Object>> syncTasks = new CopyOnWriteArrayList<>();

    public CeosCompatController() {
        seedData();
    }

    private void seedData() {
        // 审计日志种子
        auditLogs.add(logEntry("INFO", "SECURITY", "系统启动: 成功加载欧盟 GDPR 区域合规 ACL 规则模型。"));
        auditLogs.add(logEntry("INFO", "INTEGRATION", "DolphinScheduler 调度: flights_raw 原始流抽取任务顺利完成，同步 2,410 条记录。"));

        // 连接种子
        connections.add(connEntry("doris_production_olap", "生产分析数据仓库 (Apache Doris)", "doris", "connected",
                "10.160.12.35", 9030, "aip_olap_reader",
                tableEntry("ds_flights_clean", 14250,
                        col("flight_id", "VARCHAR(64)"), col("flight_num", "VARCHAR(16)"),
                        col("dep_airport", "VARCHAR(8)"), col("arr_airport", "VARCHAR(8)"),
                        col("scheduled_departure", "DATETIME"), col("actual_departure", "DATETIME"),
                        col("pilot_id", "VARCHAR(64)"), col("pilot_name", "VARCHAR(64)")),
                tableEntry("ds_pilots_biography", 380,
                        col("pilot_id", "VARCHAR(64)"), col("pilot_name", "VARCHAR(128)"),
                        col("ssn_number", "VARCHAR(32)"), col("base_salary", "DOUBLE"),
                        col("hours_flown", "INT"), col("licence_rating", "VARCHAR(32)"))));

        connections.add(connEntry("postgresql_raw_sched", "签派调度主物理库 (PostgreSQL)", "postgresql", "connected",
                "10.150.2.14", 5432, "foundry_read_user",
                tableEntry("flights_raw", 240000,
                        col("flight_id", "varchar(64)"), col("raw_payload", "text"),
                        col("received_at", "timestamp"))));

        // 同步任务种子
        syncTasks.add(syncEntry("ds_task_flights_sync", "DolphinScheduler_航班物理清洗管道",
                "*/10 * * * *", "success", 15, 2));
        syncTasks.add(syncEntry("ds_task_pilots_sync", "DolphinScheduler_飞行员资质增量抽取",
                "0 2 * * *", "success", 120, 5));

        // 血缘种子
        List<Map<String, Object>> nodes = new ArrayList<>();
        nodes.add(node("postgresql_raw_sched.flights_raw", "physical_table", "PG: flights_raw (原始ACARS流)"));
        nodes.add(node("ds_task_flights_sync", "etl_job", "DolphinScheduler: 航班物理清洗任务"));
        nodes.add(node("doris_production_olap.ds_flights_clean", "olap_table", "Doris: ds_flights_clean (航班宽表)"));
        nodes.add(node("ontology.AviationFlight", "ontology_object", "Ontology: AviationFlight (航班实体)"));
        nodes.add(node("postgresql_raw_sched.pilots_raw", "physical_table", "PG: pilots_raw (物理飞行员数据)"));
        nodes.add(node("ds_task_pilots_sync", "etl_job", "DolphinScheduler: 飞行员资质清洗任务"));
        nodes.add(node("doris_production_olap.ds_pilots_biography", "olap_table", "Doris: ds_pilots_biography (飞行员资质宽表)"));
        nodes.add(node("ontology.AviationPilot", "ontology_object", "Ontology: AviationPilot (飞行员实体)"));
        nodes.add(node("report.weekly_flight_sla_dashboard", "dashboard", "Dashboard: 周运行时效监控报表"));
        nodes.add(node("report.crew_schedule_efficiency", "dashboard", "Dashboard: 机组排班效益分析看板"));

        List<Map<String, Object>> links = new ArrayList<>();
        links.add(link("postgresql_raw_sched.flights_raw", "ds_task_flights_sync"));
        links.add(link("ds_task_flights_sync", "doris_production_olap.ds_flights_clean"));
        links.add(link("doris_production_olap.ds_flights_clean", "ontology.AviationFlight"));
        links.add(link("ontology.AviationFlight", "report.weekly_flight_sla_dashboard"));
        links.add(link("postgresql_raw_sched.pilots_raw", "ds_task_pilots_sync"));
        links.add(link("ds_task_pilots_sync", "doris_production_olap.ds_pilots_biography"));
        links.add(link("doris_production_olap.ds_pilots_biography", "ontology.AviationPilot"));
        links.add(link("ontology.AviationPilot", "report.crew_schedule_efficiency"));

        lineage.put("nodes", nodes);
        lineage.put("links", links);

        // 本体映射种子
        ontologyMappings.add(mappingEntry("AviationFlight", "AviationFlight", "航班核心本体",
                "统一抽象航班的核心计划、实际执行时间、起降机场及指派人员逻辑字段。",
                mapEntry("flightId", "String", "ds_flights_clean", "flight_id", "航班全球唯一标识键"),
                mapEntry("flightNumber", "String", "ds_flights_clean", "flight_num", "执行航班号"),
                mapEntry("departureAirport", "String", "ds_flights_clean", "dep_airport", "始发机场 IATA 三字码"),
                mapEntry("arrivalAirport", "String", "ds_flights_clean", "arr_airport", "到达机场 IATA 三字码"),
                mapEntry("scheduledDeparture", "DateTime", "ds_flights_clean", "scheduled_departure", "计划起飞标准时间"),
                mapEntry("actualDeparture", "DateTime", "ds_flights_clean", "actual_departure", "实际执行起飞时间"),
                mapEntry("assignedPilotId", "String", "ds_flights_clean", "pilot_id", "执行航班的主机长飞行员工号")));

        ontologyMappings.add(mappingEntry("AviationPilot", "AviationPilot", "飞行员资质本体",
                "描述执勤飞行员的个人档案、适航资质等级、累计安全飞行时长及财务敏感等级。",
                mapEntry("pilotId", "String", "ds_pilots_biography", "pilot_id", "飞行员身份证主键"),
                mapEntry("pilotName", "String", "ds_pilots_biography", "pilot_name", "飞行员真实姓名"),
                mapEntry("ssn", "String", "ds_pilots_biography", "ssn_number", "安全网格掩膜社保号"),
                mapEntry("salary", "Double", "ds_pilots_biography", "base_salary", "保底协议薪酬"),
                mapEntry("safeHours", "Integer", "ds_pilots_biography", "hours_flown", "总累计安全执飞总小时数"),
                mapEntry("rating", "String", "ds_pilots_biography", "licence_rating", "适航机型执照级别"),
                mapEntry("lastAssignedFlightId", "String", "ds_flights_clean", "flight_id", "最近排班航班流水号")));

        log.info("CeosCompatController seeded: {} connections, {} syncTasks, {} ontologyMappings, {} lineage nodes",
                connections.size(), syncTasks.size(), ontologyMappings.size(), nodes.size());
    }

    // ════════════════════════════════════════════
    // API: GET /api/integration/metadata
    // ════════════════════════════════════════════
    @GetMapping("/integration/metadata")
    public ApiResponse getIntegrationMetadata() {
        try {
            // 动态调整状态
            List<Map<String, Object>> dynConnections = new ArrayList<>();
            for (Map<String, Object> c : connections) {
                Map<String, Object> dc = new LinkedHashMap<>(c);
                if ("doris_production_olap".equals(c.get("id")) && isSchemaDriftActive) {
                    dc.put("status", "error");
                }
                dynConnections.add(dc);
            }

            List<Map<String, Object>> dynSyncTasks = new ArrayList<>();
            for (Map<String, Object> t : syncTasks) {
                Map<String, Object> dt = new LinkedHashMap<>(t);
                if ("ds_task_flights_sync".equals(t.get("id")) && isSlaBreachActive) {
                    dt.put("status", "failed");
                    dt.put("actualDelayMinutes", 45);
                }
                dynSyncTasks.add(dt);
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("connections", dynConnections);
            result.put("syncTasks", dynSyncTasks);
            result.put("lineage", lineage);

            Map<String, Object> simState = new LinkedHashMap<>();
            simState.put("isSchemaDriftActive", isSchemaDriftActive);
            simState.put("isSlaBreachActive", isSlaBreachActive);
            result.put("simulationState", simState);

            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("Failed to retrieve metadata", e);
            return ApiResponse.error(500, "Failed to retrieve metadata: " + e.getMessage());
        }
    }

    // ════════════════════════════════════════════
    // API: GET /api/integration/logs
    // ════════════════════════════════════════════
    @GetMapping("/integration/logs")
    public ApiResponse getIntegrationLogs() {
        List<Map<String, Object>> mappedLogs = new ArrayList<>();
        for (Map<String, Object> logEntry : auditLogs) {
            String level = (String) logEntry.getOrDefault("level", "INFO");
            String severity = "INFO";
            if ("CRITICAL".equals(level)) severity = "HIGH";
            else if ("WARNING".equals(level)) severity = "MEDIUM";

            Map<String, Object> mapped = new LinkedHashMap<>();
            mapped.put("timestamp", logEntry.get("timestamp"));
            mapped.put("severity", severity);
            mapped.put("event", logEntry.get("source"));
            mapped.put("details", logEntry.get("message"));
            mappedLogs.add(mapped);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("logs", mappedLogs);
        return ApiResponse.success(result);
    }

    // ════════════════════════════════════════════
    // API: POST /api/integration/metadata/drift
    // ════════════════════════════════════════════
    @PostMapping("/integration/metadata/drift")
    public ApiResponse triggerDrift(@RequestBody Map<String, Object> body) {
        String type = (String) body.getOrDefault("type", "reset");
        String timestamp = LocalDateTime.now().format(TS_FMT);

        if ("drift".equals(type)) {
            isSchemaDriftActive = true;
            auditLogs.add(0, logEntry("CRITICAL", "INTEGRATION",
                    "🚨 [SCHEMA_DRIFT] 警告：检测到 Apache Doris ds_pilots_biography 物理表发生 Schema 篡改！"));
            return ApiResponse.success(msgMap("成功模拟并推送 Doris Schema 漂移异常事件至安全中心！"));
        } else if ("sla".equals(type)) {
            isSlaBreachActive = true;
            auditLogs.add(0, logEntry("WARNING", "DOLPHINSCHEDULER",
                    "⏰ [SLA_BREACH] 延迟警告：flights_raw 管道延迟达 45 分钟，突破了 15 分钟的既定 SLA 时效底线。"));
            return ApiResponse.success(msgMap("成功注入 DolphinScheduler 时效断流 SLA 异常事件！"));
        } else {
            isSchemaDriftActive = false;
            isSlaBreachActive = false;
            auditLogs.add(0, logEntry("INFO", "SYSTEM",
                    "✅ [RESET] 联邦数据网格恢复健康，所有异常事件均已闭环处置完毕。"));
            return ApiResponse.success(msgMap("重置成功，全网状态恢复至正常稳定状态！"));
        }
    }

    // ════════════════════════════════════════════
    // API: GET /api/ontology/mappings (ceos_new 风格)
    // ════════════════════════════════════════════
    @GetMapping("/ontology/mappings")
    public ApiResponse getOntologyMappingsCompat() {
        try {
            List<Map<String, Object>> availableTables = new ArrayList<>();
            for (Map<String, Object> c : connections) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> tables = (List<Map<String, Object>>) c.getOrDefault("tablesAvailable", new ArrayList<>());
                for (Map<String, Object> t : tables) {
                    Map<String, Object> tableInfo = new LinkedHashMap<>();
                    tableInfo.put("connectionId", c.get("id"));
                    tableInfo.put("tableName", t.get("name"));
                    tableInfo.put("columns", t.get("columns"));
                    availableTables.add(tableInfo);
                }
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("mappings", ontologyMappings);
            result.put("availableTables", availableTables);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("Failed to retrieve ontology mappings", e);
            return ApiResponse.error(500, "Failed to retrieve ontology mappings: " + e.getMessage());
        }
    }

    // ════════════════════════════════════════════
    // API: POST /api/ontology/mappings (ceos_new 风格)
    // ════════════════════════════════════════════
    @PostMapping("/ontology/mappings")
    public ApiResponse saveOntologyMappingsCompat(@RequestBody Map<String, Object> body) {
        Object mappingsObj = body.get("mappings");
        if (!(mappingsObj instanceof List)) {
            return ApiResponse.error(400, "Mappings must be a valid array");
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> mappings = (List<Map<String, Object>>) mappingsObj;

        ontologyMappings.clear();
        ontologyMappings.addAll(mappings);

        String timestamp = LocalDateTime.now().format(TS_FMT);
        auditLogs.add(0, logEntry("INFO", "ONTOLOGY_MANAGER",
                "🔧 强类型本体对齐更新: 已重置实体与物理宽表的许多对多映射规则 (共 " + mappings.size() + " 个逻辑实体)。"));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("message", "联邦本体与物理宽表多对多对齐映射规则已成功保存并同步！");
        result.put("mappings", ontologyMappings);
        return ApiResponse.success(result);
    }

    // ════════════════════════════════════════════
    // API: GET /api/ontology/export (ceos_new 风格)
    // ════════════════════════════════════════════
    @GetMapping("/ontology/export")
    public ApiResponse exportOntologyCompat() {
        try {
            String timestamp = LocalDateTime.now().format(TS_FMT);

            StringBuilder doc = new StringBuilder();
            doc.append("=== AIP Core Ontology Schema & Physical Columns Alignment Pack ===\n");
            doc.append("Generation Time: ").append(timestamp).append("\n");
            doc.append("This schema mapping packet aligns logical ontology entity schemas with the distributed physical OLAP column fields.\n\n");

            for (Map<String, Object> ent : ontologyMappings) {
                doc.append("## Entity [").append(ent.get("entityId")).append("] / ").append(ent.getOrDefault("chineseName", ent.get("entityName"))).append("\n");
                doc.append("- System ID: ").append(ent.get("entityId")).append("\n");
                doc.append("- Description: ").append(ent.get("description")).append("\n");
                doc.append("- Column Mappings (Many-to-Many Alignments):\n");

                @SuppressWarnings("unchecked")
                List<Map<String, Object>> mappings = (List<Map<String, Object>>) ent.getOrDefault("mappings", new ArrayList<>());
                if (mappings.isEmpty()) {
                    doc.append("  (No mappings defined for this entity)\n");
                } else {
                    for (Map<String, Object> m : mappings) {
                        doc.append("  * Logical Field: `").append(m.get("logicalField"))
                           .append("` (Type: ").append(m.get("logicalType"))
                           .append(") ➔ Physical Table: `").append(m.get("physicalTable"))
                           .append("` | Column: `").append(m.get("physicalColumn"))
                           .append("` | Metadata Description: ").append(m.getOrDefault("description", "N/A"))
                           .append("\n");
                    }
                }
                doc.append("\n");
            }
            doc.append("=== End of Schema Mapping Pack ===");

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("exportedAt", timestamp);
            result.put("rawJson", ontologyMappings);
            result.put("knowledgeMarkdown", doc.toString());
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("Failed to export ontology schema", e);
            return ApiResponse.error(500, "Failed to export ontology schema: " + e.getMessage());
        }
    }

    // ════════════════════════════════════════════
    // API: POST /api/lineage/parse
    // ════════════════════════════════════════════
    @PostMapping("/lineage/parse")
    public ApiResponse parseLineage(@RequestBody Map<String, Object> body) {
        Object payloadObj = body.get("payload");
        if (payloadObj == null) {
            return ApiResponse.error(400, "Missing metadata payload in body");
        }

        try {
            Map<String, Object> parsed = parseOpenLineageOrAtlas(payloadObj);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> existingNodes = (List<Map<String, Object>>) lineage.getOrDefault("nodes", new ArrayList<>());
            Map<String, Map<String, Object>> nodesMap = new LinkedHashMap<>();
            for (Map<String, Object> n : existingNodes) {
                nodesMap.put((String) n.get("id"), n);
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> newNodes = (List<Map<String, Object>>) parsed.get("nodes");
            for (Map<String, Object> n : newNodes) {
                nodesMap.put((String) n.get("id"), n);
            }

            List<Map<String, Object>> mergedNodes = new ArrayList<>(nodesMap.values());
            lineage.put("nodes", mergedNodes);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> existingLinks = (List<Map<String, Object>>) lineage.getOrDefault("links", new ArrayList<>());
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> newLinks = (List<Map<String, Object>>) parsed.get("links");
            for (Map<String, Object> link : newLinks) {
                boolean exists = existingLinks.stream().anyMatch(l ->
                    l.get("source").equals(link.get("source")) && l.get("target").equals(link.get("target")));
                if (!exists) {
                    existingLinks.add(link);
                }
            }

            String timestamp = LocalDateTime.now().format(TS_FMT);
            auditLogs.add(0, logEntry("INFO", "LINEAGE_PARSER",
                    "📥 血缘解析器: 成功解析并合并 " + parsed.get("format") + " 格式血缘！新增 " +
                    newNodes.size() + " 个拓扑节点，" + newLinks.size() + " 条物理依赖链路。"));

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("message", "成功解析 " + parsed.get("format") + " 血缘拓扑！");
            result.put("addedNodes", newNodes.size());
            result.put("addedLinks", newLinks.size());
            result.put("lineage", lineage);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("Lineage parse failed", e);
            return ApiResponse.error(400, "Parse failed: " + e.getMessage());
        }
    }

    // ════════════════════════════════════════════
    // API: GET /api/lineage/impact
    // ════════════════════════════════════════════
    @GetMapping("/lineage/impact")
    public ApiResponse lineageImpact(@RequestParam("startNode") String startNode) {
        if (startNode == null || startNode.isEmpty()) {
            return ApiResponse.error(400, "Missing startNode query parameter");
        }

        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> nodes = (List<Map<String, Object>>) lineage.getOrDefault("nodes", new ArrayList<>());
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> links = (List<Map<String, Object>>) lineage.getOrDefault("links", new ArrayList<>());

            Map<String, Object> analysis = computeImpactAnalysis(startNode, nodes, links);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("startNode", startNode);
            result.putAll(analysis);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("Impact analysis failed", e);
            return ApiResponse.error(500, "Impact analysis failed: " + e.getMessage());
        }
    }

    // ════════════════════════════════════════════
    // 内部方法：血缘解析
    // ════════════════════════════════════════════
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseOpenLineageOrAtlas(Object payloadObj) {
        List<Map<String, Object>> newNodes = new ArrayList<>();
        List<Map<String, Object>> newLinks = new ArrayList<>();

        Map<String, Object> payload = null;
        if (payloadObj instanceof Map) {
            payload = (Map<String, Object>) payloadObj;
        }

        // OpenLineage RunEvent
        if (payload != null && (payload.containsKey("eventType") || payload.containsKey("job") ||
                payload.containsKey("inputs") || payload.containsKey("outputs"))) {

            Map<String, Object> job = (Map<String, Object>) payload.getOrDefault("job", new LinkedHashMap<>());
            String jobName = (String) job.getOrDefault("name", "anonymous_job");
            String jobNamespace = (String) job.getOrDefault("namespace", "default");
            String jobId = jobNamespace + "." + jobName;

            newNodes.add(node(jobId, "etl_job", "Job: " + jobName + " (OpenLineage)"));

            List<Map<String, Object>> inputs = (List<Map<String, Object>>) payload.getOrDefault("inputs", new ArrayList<>());
            for (Map<String, Object> input : inputs) {
                String inputName = (String) input.getOrDefault("name", "input");
                String inputNamespace = (String) input.getOrDefault("namespace", "default");
                String inputId = inputNamespace + "." + inputName;
                newNodes.add(node(inputId, "physical_table", "Input: " + inputName));
                newLinks.add(link(inputId, jobId));
            }

            List<Map<String, Object>> outputs = (List<Map<String, Object>>) payload.getOrDefault("outputs", new ArrayList<>());
            for (Map<String, Object> output : outputs) {
                String outputName = (String) output.getOrDefault("name", "output");
                String outputNamespace = (String) output.getOrDefault("namespace", "default");
                String outputId = outputNamespace + "." + outputName;
                newNodes.add(node(outputId, "olap_table", "Output: " + outputName));
                newLinks.add(link(jobId, outputId));
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("nodes", newNodes);
            result.put("links", newLinks);
            result.put("format", "OpenLineage");
            return result;
        }

        // Apache Atlas
        List<Map<String, Object>> entities = new ArrayList<>();
        if (payloadObj instanceof List) {
            entities = (List<Map<String, Object>>) payloadObj;
        } else if (payload != null) {
            Object entObj = payload.get("entities");
            if (entObj instanceof List) {
                entities = (List<Map<String, Object>>) entObj;
            }
        }

        if (!entities.isEmpty() && entities.stream().anyMatch(e ->
                e.containsKey("typeName") || e.containsKey("attributes"))) {

            List<Map<String, Object>> processEntities = new ArrayList<>();
            for (Map<String, Object> e : entities) {
                String typeName = (String) e.getOrDefault("typeName", "");
                Map<String, Object> attrs = (Map<String, Object>) e.get("attributes");
                if (typeName.toLowerCase().contains("process") || typeName.toLowerCase().contains("job") ||
                        (attrs != null && (attrs.containsKey("inputs") || attrs.containsKey("outputs")))) {
                    processEntities.add(e);
                }
            }

            if (!processEntities.isEmpty()) {
                for (Map<String, Object> process : processEntities) {
                    Map<String, Object> attrs = (Map<String, Object>) process.getOrDefault("attributes", new LinkedHashMap<>());
                    String procName = (String) attrs.getOrDefault("name", "atlas_process");
                    String procId = (String) attrs.getOrDefault("qualifiedName", procName);

                    newNodes.add(node(procId, "etl_job", "Process: " + procName + " (Atlas)"));

                    List<Map<String, Object>> inputs = (List<Map<String, Object>>) attrs.getOrDefault("inputs", new ArrayList<>());
                    for (Map<String, Object> input : inputs) {
                        String inputQName = "input";
                        if (input.containsKey("uniqueAttributes")) {
                            Map<String, Object> ua = (Map<String, Object>) input.get("uniqueAttributes");
                            inputQName = (String) ua.getOrDefault("qualifiedName", inputQName);
                        }
                        inputQName = inputQName.replace("@cluster", "");
                        String cleanName = inputQName.contains(".") ? inputQName.substring(inputQName.lastIndexOf('.') + 1) : inputQName;
                        newNodes.add(node(inputQName, "physical_table", "Atlas In: " + cleanName));
                        newLinks.add(link(inputQName, procId));
                    }

                    List<Map<String, Object>> outputs = (List<Map<String, Object>>) attrs.getOrDefault("outputs", new ArrayList<>());
                    for (Map<String, Object> output : outputs) {
                        String outputQName = "output";
                        if (output.containsKey("uniqueAttributes")) {
                            Map<String, Object> ua = (Map<String, Object>) output.get("uniqueAttributes");
                            outputQName = (String) ua.getOrDefault("qualifiedName", outputQName);
                        }
                        outputQName = outputQName.replace("@cluster", "");
                        String cleanName = outputQName.contains(".") ? outputQName.substring(outputQName.lastIndexOf('.') + 1) : outputQName;
                        newNodes.add(node(outputQName, "olap_table", "Atlas Out: " + cleanName));
                        newLinks.add(link(procId, outputQName));
                    }
                }

                Map<String, Object> result = new LinkedHashMap<>();
                result.put("nodes", newNodes);
                result.put("links", newLinks);
                result.put("format", "Apache Atlas");
                return result;
            }
        }

        throw new IllegalArgumentException("Payload structure does not match standard OpenLineage RunEvent or Apache Atlas process structure.");
    }

    // ════════════════════════════════════════════
    // 内部方法：影响度分析
    // ════════════════════════════════════════════
    @SuppressWarnings("unchecked")
    private Map<String, Object> computeImpactAnalysis(String startNodeId, List<Map<String, Object>> nodes, List<Map<String, Object>> links) {
        Set<String> visited = new HashSet<>();
        Deque<Map<String, Object>> queue = new LinkedList<>();

        List<String> initialPath = new ArrayList<>();
        initialPath.add(startNodeId);
        Map<String, Object> startItem = new LinkedHashMap<>();
        startItem.put("id", startNodeId);
        startItem.put("hop", 0);
        startItem.put("path", initialPath);
        queue.add(startItem);

        List<Map<String, Object>> results = new ArrayList<>();

        while (!queue.isEmpty()) {
            Map<String, Object> current = queue.poll();
            String currentId = (String) current.get("id");
            int currentHop = (Integer) current.get("hop");

            if (visited.contains(currentId)) continue;
            visited.add(currentId);

            if (!currentId.equals(startNodeId)) {
                final String cid = currentId;
                Map<String, Object> nodeInfo = nodes.stream().filter(n -> cid.equals(n.get("id"))).findFirst().orElse(null);
                if (nodeInfo != null) {
                    double baseMultiplier = 1.0;
                    if (isSlaBreachActive && (startNodeId.contains("flight") || currentId.contains("flight"))) {
                        baseMultiplier = 1.6;
                    }
                    if (isSchemaDriftActive && (startNodeId.contains("pilot") || currentId.contains("pilot"))) {
                        baseMultiplier = 1.9;
                    }

                    int rawScore = (int) Math.round(100 * baseMultiplier * Math.pow(0.80, currentHop));
                    int riskScore = Math.min(100, Math.max(15, rawScore));

                    Map<String, Object> resultItem = new LinkedHashMap<>();
                    resultItem.put("id", nodeInfo.get("id"));
                    resultItem.put("label", nodeInfo.get("label"));
                    resultItem.put("type", nodeInfo.get("type"));
                    resultItem.put("hopCount", currentHop);
                    resultItem.put("path", current.get("path"));
                    resultItem.put("riskScore", riskScore);
                    results.add(resultItem);
                }
            }

            final String cid = currentId;
            for (Map<String, Object> link : links) {
                if (cid.equals(link.get("source"))) {
                    String target = (String) link.get("target");
                    if (!visited.contains(target)) {
                        List<String> newPath = new ArrayList<>((List<String>) current.get("path"));
                        newPath.add(target);
                        Map<String, Object> newItem = new LinkedHashMap<>();
                        newItem.put("id", target);
                        newItem.put("hop", currentHop + 1);
                        newItem.put("path", newPath);
                        queue.add(newItem);
                    }
                }
            }
        }

        int maxRisk = results.stream().mapToInt(r -> (Integer) r.get("riskScore")).max().orElse(0);
        String severity = "LOW";
        if (maxRisk > 80) severity = "CRITICAL";
        else if (maxRisk > 55) severity = "HIGH";
        else if (maxRisk > 30) severity = "MEDIUM";

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("impactedNodes", results);
        result.put("totalRisk", maxRisk);
        result.put("severity", severity);
        return result;
    }

    // ════════════════════════════════════════════
    // 辅助构造方法
    // ════════════════════════════════════════════

    private Map<String, Object> logEntry(String level, String source, String message) {
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("timestamp", LocalDateTime.now().format(TS_FMT));
        e.put("level", level);
        e.put("source", source);
        e.put("message", message);
        return e;
    }

    private Map<String, Object> msgMap(String msg) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("success", true);
        m.put("message", msg);
        return m;
    }

    private Map<String, Object> connEntry(String id, String name, String type, String status,
            String host, int port, String username, Map<String, Object>... tables) {
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("id", id);
        c.put("name", name);
        c.put("type", type);
        c.put("status", status);
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("host", host);
        config.put("port", port);
        config.put("username", username);
        config.put("lastTested", LocalDateTime.now().format(TS_FMT));
        c.put("config", config);
        c.put("tablesAvailable", Arrays.asList(tables));
        return c;
    }

    private Map<String, Object> tableEntry(String name, int rowCount, Map<String, Object>... columns) {
        Map<String, Object> t = new LinkedHashMap<>();
        t.put("name", name);
        t.put("rowCount", rowCount);
        t.put("columns", Arrays.asList(columns));
        return t;
    }

    private Map<String, Object> col(String name, String type) {
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("name", name);
        c.put("type", type);
        return c;
    }

    private Map<String, Object> syncEntry(String id, String name, String schedule, String status,
            int slaMinutes, int actualDelayMinutes) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("id", id);
        s.put("name", name);
        s.put("engine", "Apache DolphinScheduler v3.1");
        s.put("schedule", schedule);
        s.put("status", status);
        s.put("slaMinutes", slaMinutes);
        s.put("actualDelayMinutes", actualDelayMinutes);
        s.put("lastRunTime", LocalDateTime.now().format(TS_FMT));
        return s;
    }

    private Map<String, Object> node(String id, String type, String label) {
        Map<String, Object> n = new LinkedHashMap<>();
        n.put("id", id);
        n.put("type", type);
        n.put("label", label);
        return n;
    }

    private Map<String, Object> link(String source, String target) {
        Map<String, Object> l = new LinkedHashMap<>();
        l.put("source", source);
        l.put("target", target);
        return l;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mappingEntry(String entityId, String entityName, String chineseName,
            String description, Map<String, Object>... mappings) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("entityId", entityId);
        m.put("entityName", entityName);
        m.put("chineseName", chineseName);
        m.put("description", description);
        m.put("mappings", Arrays.asList(mappings));
        return m;
    }

    private Map<String, Object> mapEntry(String logicalField, String logicalType,
            String physicalTable, String physicalColumn, String description) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("logicalField", logicalField);
        m.put("logicalType", logicalType);
        m.put("physicalTable", physicalTable);
        m.put("physicalColumn", physicalColumn);
        m.put("description", description);
        return m;
    }

    // ═══════════════════════════════════════════════════════════
    //  本体实体实例（OperationalApps 运营应用）
    // ═══════════════════════════════════════════════════════════

    /**
     * GET /api/ontology/entities/{entityType}/instances
     * 返回指定实体类型的所有实例。当前支持 Customer、Facility、Order、Product。
     */
    @GetMapping("/ontology/entities/{entityType}/instances")
    public ApiResponse<List<Map<String, Object>>> getEntityInstances(
            @PathVariable String entityType) {
        List<Map<String, Object>> instances = new ArrayList<>();

        switch (entityType.toLowerCase()) {
            case "customer" -> {
                instances.add(customer("cust-001", "华南科技集团", true, 8_500_000, "华东", 0.12));
                instances.add(customer("cust-002", "北疆能源控股", true, 12_300_000, "华北", 0.05));
                instances.add(customer("cust-003", "长三角基建", false, 2_100_000, "华东", 0.38));
                instances.add(customer("cust-004", "西南航空物流", true, 6_700_000, "西南", 0.09));
                instances.add(customer("cust-005", "中部智能制造", true, 4_200_000, "华中", 0.15));
            }
            case "facility" -> {
                instances.add(facility("MF-001", 42.5, "normal", false));
                instances.add(facility("MF-002", 58.3, "warning", true));
                instances.add(facility("MF-003", 39.1, "normal", false));
                instances.add(facility("MF-004", 65.7, "critical", true));
            }
            case "order" -> {
                Map<String, Object> o = new LinkedHashMap<>();
                o.put("id", "ORD-001"); o.put("name", "采购订单-钢材"); o.put("status", "active");
                instances.add(o);
            }
            case "product" -> {
                Map<String, Object> p = new LinkedHashMap<>();
                p.put("id", "PRD-001"); p.put("name", "高速公路护栏"); p.put("category", "建材");
                instances.add(p);
            }
            default -> {
                // 返回通用实例
                Map<String, Object> gen = new LinkedHashMap<>();
                gen.put("id", entityType.substring(0, 4).toUpperCase() + "-001");
                gen.put("name", entityType + " 实例");
                gen.put("code", entityType.toLowerCase());
                instances.add(gen);
            }
        }

        log.info("Entity instances: type={}, count={}", entityType, instances.size());
        return ApiResponse.success(instances);
    }

    private Map<String, Object> customer(String id, String name, boolean isActive,
            int revenue, String region, double churnRisk) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("name", name);
        m.put("isActive", isActive);
        m.put("revenue", revenue);
        m.put("region", region);
        m.put("churn_risk", churnRisk);
        return m;
    }

    private Map<String, Object> facility(String machineId, double temp,
            String tempStatus, boolean hasFault) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("machine_id", machineId);
        m.put("temperature", temp);
        m.put("temp_status", tempStatus);
        m.put("has_fault", hasFault);
        return m;
    }
}
