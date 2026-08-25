package com.chinacreator.gzcm.engine.data.pipeline;

import com.chinacreator.gzcm.engine.data.DataSourceService;
import com.chinacreator.gzcm.engine.data.datasource.entity.DataSourceEntity;
import com.chinacreator.gzcm.runtime.access.connector.Connector;
import com.chinacreator.gzcm.runtime.access.connector.ConnectorFactory;
import com.chinacreator.gzcm.runtime.access.connector.CsvConnector;
import com.chinacreator.gzcm.runtime.access.connector.JdbcConnector;
import com.chinacreator.gzcm.runtime.access.connector.RestApiConnector;
import com.chinacreator.gzcm.runtime.core.alert.IAlertService;
import com.chinacreator.gzcm.runtime.core.logging.ILoggingService;
import com.chinacreator.gzcm.runtime.core.task.callback.ITaskStatusCallback;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Pipeline 执行引擎 — 按 DAG 拓扑序串行执行节点。
 * <p>
 * 架构规则 2.5：所有 SOURCE 节点（JDBC/CSV/REST）通过 ConnectorFactory 访问外部数据源，
 * 禁止使用系统 JdbcTemplate 执行外部数据源 SQL。
 * 架构规则 2.3：本类不自建调度，调度由 runtime-task (TaskSchedulerService) 负责。
 * <p>
 * 执行过程对接 ITaskStatusCallback（runtime-task 回调链），每个 DAG 节点执行时回调
 * onStepStart → onStepComplete，整体进度回调 onProgressUpdate，完成时 onTaskComplete。
 *
 * @author DataBridge Datanet Team
 */
@Service
public class PipelineExecutionService {

    private static final Logger log = LoggerFactory.getLogger(PipelineExecutionService.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private final PipelineRepository repository;
    private final ConnectorFactory connectorFactory;
    private final JdbcTemplate jdbc;
    private final DataSourceService dataSourceService;

    /** 可选注入，无 bean 时不影响核心流程 */
    @Autowired(required = false)
    private ILoggingService loggingService;

    @Autowired(required = false)
    private IAlertService alertService;

    /** 失败告警开关：dw.pipeline.alert_on_failure=true 时触发 IAlertService */
    @Value("${dw.pipeline.alert_on_failure:false}")
    private boolean alertOnFailure;

    public PipelineExecutionService(PipelineRepository repository,
                                     ConnectorFactory connectorFactory,
                                     JdbcTemplate jdbc,
                                     DataSourceService dataSourceService) {
        this.repository = repository;
        this.connectorFactory = connectorFactory;
        this.jdbc = jdbc;
        this.dataSourceService = dataSourceService;
    }

    // ==================== 执行入口 ====================

    /**
     * 执行 Pipeline（向后兼容：无 runtime-task 回调）。
     *
     * @param definitionId Pipeline 定义 ID
     * @return 执行记录
     */
    public PipelineExecution executePipeline(String definitionId) {
        return executePipeline(definitionId, null, null);
    }

    /**
     * 执行 Pipeline，携带 runtime-task 回调与 taskId 透传。
     *
     * @param definitionId Pipeline 定义 ID
     * @param callback     runtime-task 状态回调（可为 null）
     * @param taskId       runtime-task 任务 ID（可为 null，则用 executionId 代替）
     * @return 执行记录
     */
    public PipelineExecution executePipeline(String definitionId,
                                              ITaskStatusCallback callback,
                                              String taskId) {
        PipelineDefinition def = repository.findDefinitionById(definitionId);
        if (def == null) {
            throw new IllegalArgumentException("Pipeline definition not found: " + definitionId);
        }

        // 创建执行记录
        PipelineExecution exec = new PipelineExecution();
        exec.setId(UUID.randomUUID().toString().replace("-", ""));
        exec.setDefinitionId(definitionId);
        exec.setStatus("PENDING");

        exec = repository.insertExecution(exec);
        // taskId 透传：回调用 runtime-task 的 taskId，持久化用 executionId
        String cbTaskId = taskId != null ? taskId : exec.getId();
        logInfo("Pipeline execution started: definitionId={}, executionId={}, taskId={}",
                definitionId, exec.getId(), cbTaskId);

        try {
            // 更新状态为 RUNNING
            repository.updateExecutionStatus(exec.getId(), "RUNNING", null, 0L);
            exec.setStatus("RUNNING");
            exec.setStartedAt(java.time.LocalDateTime.now());

            if (callback != null) {
                callback.onProgressUpdate(cbTaskId, 0, "Pipeline execution started");
            }

            // 获取节点列表，按拓扑序排序
            List<PipelineNode> allNodes = repository.findNodesByDefinitionId(definitionId);
            if (allNodes.isEmpty()) {
                throw new IllegalStateException("Pipeline has no nodes");
            }

            // 拓扑排序 (Kahn's algorithm)
            List<PipelineNode> sorted = topologicalSort(allNodes);
            logInfo("Pipeline nodes sorted: {} nodes, order: {}",
                    sorted.size(), sorted.stream().map(PipelineNode::getNodeId).toList());

            long totalRows = 0;
            int total = sorted.size();
            for (int i = 0; i < sorted.size(); i++) {
                PipelineNode node = sorted.get(i);
                logInfo("Executing node: nodeId={}, type={}", node.getNodeId(), node.getType());

                if (callback != null) {
                    callback.onStepStart(cbTaskId, node.getNodeId(), node.getType());
                }

                long nodeRows;
                try {
                    nodeRows = executeNode(node);
                } catch (Exception e) {
                    if (callback != null) {
                        callback.onStepComplete(cbTaskId, node.getNodeId(), node.getType(), false, e.getMessage());
                    }
                    throw e;
                }
                totalRows += nodeRows;
                logInfo("Node executed: nodeId={}, rows={}", node.getNodeId(), nodeRows);

                if (callback != null) {
                    callback.onStepComplete(cbTaskId, node.getNodeId(), node.getType(), true,
                            "rows=" + nodeRows);
                    int progress = total > 0 ? (int) (((i + 1) * 100L) / total) : 100;
                    callback.onProgressUpdate(cbTaskId, progress,
                            "Executed " + (i + 1) + "/" + total + " nodes");
                }
            }

            // 标记成功
            repository.updateExecutionStatus(exec.getId(), "COMPLETED", null, totalRows);
            exec.setStatus("COMPLETED");
            exec.setCompletedAt(java.time.LocalDateTime.now());
            exec.setRowsProcessed(totalRows);

            logInfo("Pipeline execution completed: executionId={}, totalRows={}", exec.getId(), totalRows);

            if (callback != null) {
                callback.onTaskComplete(cbTaskId, true, exec.getId(), null);
            }
            return exec;

        } catch (Exception e) {
            logError("Pipeline execution failed: executionId={}, error={}", exec.getId(), e.getMessage(), e);
            repository.updateExecutionStatus(exec.getId(), "FAILED", e.getMessage(), 0L);
            exec.setStatus("FAILED");
            exec.setErrorMessage(e.getMessage());
            exec.setCompletedAt(java.time.LocalDateTime.now());

            if (callback != null) {
                callback.onTaskComplete(cbTaskId, false, exec.getId(), e.getMessage());
                callback.onError(cbTaskId, e.getMessage(), getStackTrace(e));
            }

            // 失败告警（配置项 dw.pipeline.alert_on_failure=true 时）
            triggerFailureAlert(def, exec, e);

            return exec;
        }
    }

    // ==================== 节点执行 ====================

    /**
     * 执行单个节点。
     */
    private long executeNode(PipelineNode node) throws Exception {
        Map<String, Object> config = parseConfig(node.getConfig());
        String type = node.getType();

        return switch (type) {
            case "SOURCE_JDBC" -> executeSourceJdbc(config);
            case "SOURCE_CSV" -> executeSourceCsv(config);
            case "SOURCE_REST" -> executeSourceRest(config);
            case "SOURCE_CDC" -> throw new UnsupportedOperationException("CDC is flagship edition only");
            case "TRANSFORM_SQL" -> executeTransformSql(config);
            case "OUTPUT_OBJECT" -> executeOutputObject(config);
            default -> throw new IllegalArgumentException("Unsupported node type: " + type);
        };
    }

    /**
     * SOURCE_JDBC: 通过 ConnectorFactory 获取 JdbcConnector，用外部数据源连接执行 config.sql。
     * <p>
     * 架构规则 2.5：禁止使用系统 JdbcTemplate 执行外部数据源 SQL。
     * 流程：config.datasourceId → DataSourceService.getById → connectionConfig →
     * ConnectorFactory.getConnector("JDBC") → JdbcConnector.executeSql(connectionConfig, sql, fetchSize)。
     */
    private long executeSourceJdbc(Map<String, Object> config) throws Exception {
        String sql = (String) config.get("sql");
        if (sql == null || sql.isEmpty()) {
            throw new IllegalArgumentException("SOURCE_JDBC: sql is required");
        }

        Object dsIdObj = config.get("datasourceId");
        if (dsIdObj == null || dsIdObj.toString().isEmpty()) {
            throw new IllegalArgumentException("SOURCE_JDBC: datasourceId is required");
        }
        String datasourceId = dsIdObj.toString();

        // 查询数据源连接配置
        DataSourceEntity ds = dataSourceService.getById(datasourceId);
        if (ds == null) {
            throw new IllegalArgumentException("DataSource not found: " + datasourceId);
        }
        String connectionConfig = ds.getConnectionConfig();
        if (connectionConfig == null || connectionConfig.isEmpty()) {
            throw new IllegalStateException("DataSource has no connectionConfig: " + datasourceId);
        }

        // 通过 ConnectorFactory 获取 JDBC Connector，建立外部连接执行 SQL
        Connector connector = connectorFactory.getConnector("JDBC");
        if (!(connector instanceof JdbcConnector jdbcConnector)) {
            throw new IllegalStateException("Expected JdbcConnector but got: " + connector.getClass().getName());
        }

        int fetchSize = toInt(config.get("fetchSize"), 1000);
        logInfo("SOURCE_JDBC executing via Connector: datasourceId={}, type=JDBC, fetchSize={}", datasourceId, fetchSize);

        List<Map<String, Object>> rows = jdbcConnector.executeSql(connectionConfig, sql, fetchSize);

        // 返回真实行数：SELECT 返回结果行数，DML 返回 affectedRows
        long affected = extractAffectedRows(rows);
        logInfo("SOURCE_JDBC done: datasourceId={}, rows={}", datasourceId, affected);
        return affected;
    }

    /**
     * SOURCE_CSV: 通过 ConnectorFactory 获取 CsvConnector，读取 config.filePath。
     */
    private long executeSourceCsv(Map<String, Object> config) throws Exception {
        String filePath = (String) config.get("filePath");
        if (filePath == null || filePath.isEmpty()) {
            throw new IllegalArgumentException("SOURCE_CSV: filePath is required");
        }

        Connector connector = connectorFactory.getConnector("SOURCE_CSV");
        if (!(connector instanceof CsvConnector csvConnector)) {
            throw new IllegalStateException("Expected CsvConnector but got: " + connector.getClass().getName());
        }

        // CsvConnector.readRows 需要 connectionConfig JSON，从节点 config 构造
        String connectionConfig = buildCsvConnectionConfig(config);
        int fetchSize = toInt(config.get("fetchSize"), 0); // 0 = no limit
        logInfo("SOURCE_CSV executing via Connector: filePath={}", filePath);

        List<Map<String, Object>> rows = csvConnector.readRows(connectionConfig, fetchSize);
        logInfo("SOURCE_CSV done: filePath={}, rows={}", filePath, rows.size());
        return rows.size();
    }

    /**
     * SOURCE_REST: 通过 ConnectorFactory 获取 RestApiConnector，调用 config.url。
     */
    private long executeSourceRest(Map<String, Object> config) throws Exception {
        String url = (String) config.get("url");
        if (url == null || url.isEmpty()) {
            throw new IllegalArgumentException("SOURCE_REST: url is required");
        }

        Connector connector = connectorFactory.getConnector("SOURCE_REST");
        if (!(connector instanceof RestApiConnector restConnector)) {
            throw new IllegalStateException("Expected RestApiConnector but got: " + connector.getClass().getName());
        }

        logInfo("SOURCE_REST executing via Connector: url={}, method={}", url, config.getOrDefault("method", "GET"));

        List<Map<String, Object>> rows = restConnector.fetchData(config);
        logInfo("SOURCE_REST done: url={}, rows={}", url, rows.size());
        return rows.size();
    }

    /**
     * TRANSFORM_SQL: 用系统 JdbcTemplate 执行 config.sql（转换在系统库内，允许）。
     * 返回真实影响行数。
     */
    private long executeTransformSql(Map<String, Object> config) {
        String sql = (String) config.get("sql");
        if (sql == null || sql.isEmpty()) {
            throw new IllegalArgumentException("TRANSFORM_SQL: sql is required");
        }
        return jdbc.update(sql);
    }

    /**
     * OUTPUT_OBJECT: 插入结果到目标表。
     */
    @SuppressWarnings("unchecked")
    private long executeOutputObject(Map<String, Object> config) {
        String targetTable = (String) config.get("targetTable");
        if (targetTable == null || targetTable.isEmpty()) {
            throw new IllegalArgumentException("OUTPUT_OBJECT: targetTable is required");
        }

        List<Map<String, Object>> rows = (List<Map<String, Object>>) config.get("rows");
        if (rows == null || rows.isEmpty()) {
            log.warn("OUTPUT_OBJECT: no data rows, skip insert targetTable={}", targetTable);
            return 0;
        }

        long count = 0;
        for (Map<String, Object> row : rows) {
            StringBuilder cols = new StringBuilder();
            StringBuilder vals = new StringBuilder();
            List<Object> params = new ArrayList<>();

            for (Map.Entry<String, Object> entry : row.entrySet()) {
                if (!cols.isEmpty()) {
                    cols.append(", ");
                    vals.append(", ");
                }
                cols.append(entry.getKey());
                vals.append("?");
                params.add(entry.getValue());
            }

            String sql = "INSERT INTO " + targetTable + " (" + cols + ") VALUES (" + vals + ")";
            jdbc.update(sql, params.toArray());
            count++;
        }

        logInfo("OUTPUT_OBJECT: inserted {} rows into {}", count, targetTable);
        return count;
    }

    // ==================== 辅助方法 ====================

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseConfig(String configStr) {
        try {
            if (configStr == null || configStr.isEmpty()) return Collections.emptyMap();
            return mapper.readValue(configStr, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse config JSON: {}", configStr, e);
            return Collections.emptyMap();
        }
    }

    /**
     * 从 SOURCE_JDBC 执行结果提取真实行数。
     * JdbcConnector.executeSql 对 SELECT 返回结果行，对 DML 返回 {affectedRows: N}。
     */
    private long extractAffectedRows(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) return 0;
        // DML: 第一行含 affectedRows 键
        Map<String, Object> first = rows.get(0);
        if (first != null && first.containsKey("affectedRows")) {
            Object v = first.get("affectedRows");
            return toLong(v, 0);
        }
        // SELECT: 行数
        return rows.size();
    }

    /**
     * 将 SOURCE_CSV 节点 config 构造为 CsvConnector.readRows 所需的 connectionConfig JSON。
     */
    private String buildCsvConnectionConfig(Map<String, Object> config) {
        try {
            Map<String, Object> cc = new LinkedHashMap<>();
            cc.put("filePath", config.get("filePath"));
            cc.put("delimiter", config.getOrDefault("delimiter", ","));
            // 前端 header 字段映射到 Connector 的 hasHeader
            Object header = config.get("header");
            if (header == null) header = config.get("hasHeader");
            cc.put("hasHeader", header != null ? header : true);
            cc.put("encoding", config.getOrDefault("encoding", "UTF-8"));
            return mapper.writeValueAsString(cc);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to build CSV connection config: " + e.getMessage(), e);
        }
    }

    private int toInt(Object val, int def) {
        if (val == null) return def;
        if (val instanceof Number n) return n.intValue();
        try { return Integer.parseInt(val.toString()); } catch (Exception e) { return def; }
    }

    private long toLong(Object val, long def) {
        if (val == null) return def;
        if (val instanceof Number n) return n.longValue();
        try { return Long.parseLong(val.toString()); } catch (Exception e) { return def; }
    }

    private String getStackTrace(Throwable t) {
        java.io.StringWriter sw = new java.io.StringWriter();
        t.printStackTrace(new java.io.PrintWriter(sw));
        return sw.toString();
    }

    // ==================== 日志与告警 ====================

    private void logInfo(String pattern, Object... args) {
        log.info(pattern, args);
        if (loggingService != null) {
            try { loggingService.log(ILoggingService.LogLevel.INFO, formatSlf(pattern, args)); } catch (Exception ignored) {}
        }
    }

    private void logError(String pattern, Object... args) {
        Throwable cause = args != null && args.length > 0 && args[args.length - 1] instanceof Throwable t ? t : null;
        log.error(pattern, args);
        if (loggingService != null) {
            try {
                if (cause != null) loggingService.log(ILoggingService.LogLevel.ERROR, formatSlf(pattern, args), cause);
                else loggingService.log(ILoggingService.LogLevel.ERROR, formatSlf(pattern, args));
            } catch (Exception ignored) {}
        }
    }

    /**
     * 将 SLF4J 风格的 "{}" 占位符替换为参数值（用于 ILoggingService 等不接受 {} 的接口）。
     */
    private String formatSlf(String pattern, Object... args) {
        if (pattern == null) return "";
        if (args == null || args.length == 0) return pattern;
        StringBuilder sb = new StringBuilder(pattern);
        int argIdx = 0;
        int searchFrom = 0;
        while (argIdx < args.length) {
            int pos = sb.indexOf("{}", searchFrom);
            if (pos < 0) break;
            Object val = args[argIdx++];
            // 跳过作为 cause 的 Throwable（SLF4J 不把最后一个 Throwable 当占位符参数）
            String replacement = (val instanceof Throwable) ? val.toString() : String.valueOf(val);
            sb.replace(pos, pos + 2, replacement);
            searchFrom = pos + replacement.length();
        }
        return sb.toString();
    }

    /**
     * 失败告警：配置项 dw.pipeline.alert_on_failure=true 时触发 IAlertService。
     */
    private void triggerFailureAlert(PipelineDefinition def, PipelineExecution exec, Exception e) {
        if (!alertOnFailure || alertService == null) {
            return;
        }
        try {
            String ruleId = "pipeline-failure-rule";
            String alertType = "PIPELINE_EXECUTION_FAILURE";
            String nodeId = def != null ? def.getId() : "unknown";
            String taskId = exec != null ? exec.getId() : "unknown";
            String message = "Pipeline failed: " + def.getName() + " (" + taskId + "): " + e.getMessage();
            alertService.triggerAlert(ruleId, alertType, nodeId, taskId, message);
            logInfo("Pipeline failure alert triggered: definitionId={}", nodeId);
        } catch (Exception ex) {
            log.warn("Failed to trigger pipeline failure alert: {}", ex.getMessage());
        }
    }

    // ==================== 拓扑排序 ====================

    /**
     * Kahn 拓扑排序 — 根据 depends_on (JSON数组) 构建 DAG 并返回执行顺序。
     * 无依赖的节点优先执行。存在循环依赖时抛异常。
     */
    private List<PipelineNode> topologicalSort(List<PipelineNode> nodes) {
        // nodeId → node 映射
        Map<String, PipelineNode> nodeMap = new LinkedHashMap<>();
        // nodeId → 入度 (有多少节点依赖我)
        Map<String, Integer> inDegree = new LinkedHashMap<>();
        // nodeId → [我依赖哪些节点] (反向边: A depends on B → B → A)
        Map<String, List<String>> children = new LinkedHashMap<>();

        for (PipelineNode node : nodes) {
            nodeMap.put(node.getNodeId(), node);
            inDegree.putIfAbsent(node.getNodeId(), 0);

            // 解析 depends_on
            List<String> deps = parseDependsOn(node.getDependsOn());
            for (String dep : deps) {
                children.computeIfAbsent(dep, k -> new ArrayList<>()).add(node.getNodeId());
                inDegree.merge(node.getNodeId(), 1, Integer::sum);
            }
        }

        // Kahn: 入度为0的节点入队
        Queue<String> queue = new ArrayDeque<>();
        for (PipelineNode node : nodes) {
            if (inDegree.getOrDefault(node.getNodeId(), 0) == 0) {
                queue.add(node.getNodeId());
            }
        }

        List<PipelineNode> result = new ArrayList<>();
        while (!queue.isEmpty()) {
            String current = queue.poll();
            result.add(nodeMap.get(current));

            for (String child : children.getOrDefault(current, Collections.emptyList())) {
                int newDegree = inDegree.merge(child, -1, Integer::sum);
                if (newDegree == 0) {
                    queue.add(child);
                }
            }
        }

        if (result.size() != nodes.size()) {
            // 循环依赖
            Set<String> remaining = new LinkedHashSet<>();
            for (PipelineNode n : nodes) remaining.add(n.getNodeId());
            result.forEach(r -> remaining.remove(r.getNodeId()));
            throw new IllegalStateException(
                    "Pipeline DAG has cycle, cannot sort. Remaining nodes: " + remaining);
        }

        return result;
    }

    @SuppressWarnings("unchecked")
    private List<String> parseDependsOn(String dependsOn) {
        if (dependsOn == null || dependsOn.isEmpty() || "[]".equals(dependsOn)) {
            return Collections.emptyList();
        }
        try {
            return mapper.readValue(dependsOn, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse depends_on: {}", dependsOn, e);
            return Collections.emptyList();
        }
    }
}
