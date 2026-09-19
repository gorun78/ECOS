package com.chinacreator.gzcm.engine.data.pipeline;

import com.chinacreator.gzcm.common.exception.BusinessException;
import com.chinacreator.gzcm.common.exception.NotFoundException;
import com.chinacreator.gzcm.common.exception.ValidationException;
import com.chinacreator.gzcm.engine.data.DataSourceService;
import com.chinacreator.gzcm.engine.data.UdfService;
import com.chinacreator.gzcm.engine.data.datasource.entity.DataSourceEntity;
import com.chinacreator.gzcm.engine.data.service.DataLakeResourceService;
import com.chinacreator.gzcm.engine.data.service.UdfSandbox;
import com.chinacreator.gzcm.runtime.access.connector.Connector;
import com.chinacreator.gzcm.runtime.access.connector.ConnectorFactory;
import com.chinacreator.gzcm.runtime.access.connector.CsvConnector;
import com.chinacreator.gzcm.runtime.access.connector.JdbcConnector;
import com.chinacreator.gzcm.runtime.access.connector.RestApiConnector;
import com.chinacreator.gzcm.runtime.access.storage.MinioStorageService;
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
    private final UdfService udfService;
    private final MinioStorageService minioStorageService;
    private final DataLakeResourceService dataLakeResourceService;

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
                                     DataSourceService dataSourceService,
                                     UdfService udfService,
                                     MinioStorageService minioStorageService,
                                     DataLakeResourceService dataLakeResourceService) {
        this.repository = repository;
        this.connectorFactory = connectorFactory;
        this.jdbc = jdbc;
        this.dataSourceService = dataSourceService;
        this.udfService = udfService;
        this.minioStorageService = minioStorageService;
        this.dataLakeResourceService = dataLakeResourceService;
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
            throw new NotFoundException("Pipeline definition not found: " + definitionId);
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
                throw new BusinessException("Pipeline 无节点，无法执行");
            }

            // 拓扑排序 (Kahn's algorithm)
            List<PipelineNode> sorted = topologicalSort(allNodes);
            logInfo("Pipeline nodes sorted: {} nodes, order: {}",
                    sorted.size(), sorted.stream().map(PipelineNode::getNodeId).toList());

            // 近源层对象 key 的 {source} 段（取 DAG 中首个 SOURCE_JDBC 节点的 datasourceId）
            String lakeSource = resolveLakeSource(sorted);

            long totalRows = 0;
            int total = sorted.size();
            // 上游结果表：nodeId → 该节点执行产出的行（供 JOIN/SINK/TRANSFORM_UDF 消费）
            Map<String, List<Map<String, Object>>> nodeResults = new LinkedHashMap<>();
            for (int i = 0; i < sorted.size(); i++) {
                PipelineNode node = sorted.get(i);
                logInfo("Executing node: nodeId={}, type={}", node.getNodeId(), node.getType());

                if (callback != null) {
                    callback.onStepStart(cbTaskId, node.getNodeId(), node.getType());
                }

                long nodeRows;
                try {
                    nodeRows = executeNode(node, nodeResults, lakeSource);
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
     * 解析近源层对象 key 的 {source} 段 —— 取 DAG 中首个 SOURCE_JDBC 节点的 datasourceId。
     * <p>无 SOURCE_JDBC 节点或未配置 datasourceId 时回退 {@code default}。
     *
     * @param nodes 拓扑序节点列表
     * @return 数据源标识
     */
    private String resolveLakeSource(List<PipelineNode> nodes) {
        for (PipelineNode n : nodes) {
            if (!"SOURCE_JDBC".equals(n.getType())) {
                continue;
            }
            String dsId = strOrNull(parseConfig(n.getConfig()).get("datasourceId"));
            if (dsId != null && !dsId.isEmpty()) {
                return dsId;
            }
        }
        return "default";
    }

    /**
     * 执行单个节点。
     *
     * @param node        节点
     * @param nodeResults 已执行节点的结果表（nodeId → 行），JOIN/SINK 消费上游数据
     * @param lakeSource  近源层对象 key 的 {source} 段（SINK_MINIO 默认对象名使用）
     */
    private long executeNode(PipelineNode node, Map<String, List<Map<String, Object>>> nodeResults,
                             String lakeSource) throws Exception {
        Map<String, Object> config = parseConfig(node.getConfig());
        String type = node.getType();

        return switch (type) {
            case "SOURCE_JDBC" -> {
                List<Map<String, Object>> rows = readSourceJdbcRows(config);
                nodeResults.put(node.getNodeId(), rows);
                long affected = extractAffectedRows(rows);
                logInfo("SOURCE_JDBC done: rows={}", affected);
                yield affected;
            }
            case "SOURCE_CSV" -> {
                String filePath = (String) config.get("filePath");
                if (filePath == null || filePath.isEmpty()) {
                    throw new ValidationException("filePath", "SOURCE_CSV: filePath 必填");
                }
                List<Map<String, Object>> rows = readSourceCsvRows(config);
                nodeResults.put(node.getNodeId(), rows);
                logInfo("SOURCE_CSV done: filePath={}, rows={}", filePath, rows.size());
                yield rows.size();
            }
            case "SOURCE_REST" -> {
                List<Map<String, Object>> rows = readSourceRestRows(config);
                nodeResults.put(node.getNodeId(), rows);
                logInfo("SOURCE_REST done: url={}, rows={}", config.get("url"), rows.size());
                yield rows.size();
            }
            case "SOURCE_CDC" -> {
                // D12：SOURCE_CDC 仅在节点枚举中登记，执行器未实现 —— 显式拒绝（不落 default、不返回空）
                log.warn("SOURCE_CDC 节点未实现，显式拒绝执行: nodeId={}", node.getNodeId());
                throw new BusinessException(
                        "SOURCE_CDC 节点尚未实现，请使用 SOURCE_JDBC / SOURCE_CSV / SOURCE_REST");
            }
            case "TRANSFORM_SQL" -> executeTransformSql(config);
            case "TRANSFORM_UDF" -> {
                List<Map<String, Object>> out = runUdfTransform(node, config, nodeResults).rows;
                nodeResults.put(node.getNodeId(), out);
                logInfo("TRANSFORM_UDF done: udfId={}, rows={}", config.get("udfId"), out.size());
                yield out.size();
            }
            case "JOIN" -> {
                List<Map<String, Object>> merged = joinFrames(node, config, nodeResults);
                nodeResults.put(node.getNodeId(), merged);
                logInfo("JOIN done: keys={}, type={}, rows={}",
                        config.get("joinKeys") != null ? config.get("joinKeys") : config.get("on"),
                        String.valueOf(config.getOrDefault("joinType", "inner")), merged.size());
                yield merged.size();
            }
            case "SINK" -> executeSink(node, config, nodeResults);
            case "SINK_MINIO" -> executeSinkMinio(node, config, nodeResults, lakeSource);
            case "OUTPUT_OBJECT" -> executeOutputObject(config);
            default -> throw new ValidationException("type", "不支持的节点类型: " + type);
        };
    }

    /**
     * 读取 SOURCE_JDBC 源行（不含 affectedRows 包装）。
     */
    private List<Map<String, Object>> readSourceJdbcRows(Map<String, Object> config) throws Exception {
        DataSourceEntity ds = resolveDatasource(config.get("datasourceId"), "SOURCE_JDBC: datasourceId 必填");
        String sql = (String) config.get("sql");
        if (sql == null || sql.isEmpty()) {
            throw new ValidationException("sql", "SOURCE_JDBC: sql 必填");
        }
        Connector connector = connectorFactory.getConnector("JDBC");
        JdbcConnector jdbcConnector = requireJdbcConnector(connector);
        int fetchSize = toInt(config.get("fetchSize"), 1000);
        logInfo("SOURCE_JDBC executing via Connector: datasourceId={}, type=JDBC, fetchSize={}",
                config.get("datasourceId"), fetchSize);
        return jdbcConnector.executeSql(ds.getConnectionConfig(), sql, fetchSize);
    }

    /**
     * 读取 SOURCE_CSV 源行。
     */
    private List<Map<String, Object>> readSourceCsvRows(Map<String, Object> config) throws Exception {
        Connector connector = connectorFactory.getConnector("SOURCE_CSV");
        CsvConnector csvConnector = requireCsvConnector(connector);
        return csvConnector.readRows(buildCsvConnectionConfig(config), toInt(config.get("fetchSize"), 0));
    }

    /**
     * 读取 SOURCE_REST 源行。
     */
    private List<Map<String, Object>> readSourceRestRows(Map<String, Object> config) throws Exception {
        String url = (String) config.get("url");
        if (url == null || url.isEmpty()) {
            throw new ValidationException("url", "SOURCE_REST: url 必填");
        }
        Connector connector = connectorFactory.getConnector("SOURCE_REST");
        RestApiConnector restConnector = requireRestConnector(connector);
        logInfo("SOURCE_REST executing via Connector: url={}, method={}",
                url, config.getOrDefault("method", "GET"));
        return restConnector.fetchData(config);
    }

    /**
     * TRANSFORM_SQL: 用系统 JdbcTemplate 执行 config.sql（转换在系统库内，允许）。
     * <p>按 SQL 首 token 分流：SELECT/SHOW/DESCRIBE/WITH 走 queryForList 返回行数；
     * INSERT/UPDATE/DELETE/REPLACE/CREATE/ALTER/DROP/TRUNCATE 走 update 返回受影响行数。
     * 返回真实影响行数。
     */
    private long executeTransformSql(Map<String, Object> config) {
        String sql = (String) config.get("sql");
        if (sql == null || sql.isEmpty()) {
            throw new ValidationException("sql", "TRANSFORM_SQL: sql 必填");
        }
        String trimmed = sql.trim();
        // 提取首 token（大写归一化），按白名单判断是否为查询语句
        String firstToken = trimmed.split("[\\s]+")[0].toUpperCase();
        return switch (firstToken) {
            // 查询类 → queryForList，返回行数
            case "SELECT", "SHOW", "DESCRIBE", "WITH" -> {
                List<Map<String, Object>> rows = jdbc.queryForList(trimmed);
                logInfo("TRANSFORM_SQL query: rows={}", rows.size());
                yield rows.size();
            }
            // DML/DDL 类 → update，返回受影响行数
            case "INSERT", "UPDATE", "DELETE", "REPLACE", "CREATE", "ALTER", "DROP", "TRUNCATE" -> {
                long affected = jdbc.update(trimmed);
                logInfo("TRANSFORM_SQL update: affected={}", affected);
                yield affected;
            }
            default -> {
                log.warn("TRANSFORM_SQL 未知首 token: {}, 按 update 处理", firstToken);
                yield jdbc.update(trimmed);
            }
        };
    }

    // ==================== TRANSFORM_UDF / JOIN / SINK（Wave 5 节点扩充） ====================

    /**
     * 执行 UDF 沙箱（TRANSFORM_UDF 节点）。
     * <p>
     * 数据面：收集上游输入行（DAG 可达的前驱节点产出行合并），以
     * {@code rows}/{@code params} 绑定进沙箱；输出行按 JS 数组 JSON 解析回
     * 行列表（供下游 SINK/JOIN 消费）。UDF 不存在抛 ValidationException；
     * 沙箱执行失败抛 BusinessException（含 UDF 名称）。
     * 架构铁律 P2-01 §8：禁止执行器自建脚本引擎，统一走 UdfSandbox。
     */
    private UdfRun runUdfTransform(PipelineNode node, Map<String, Object> config,
                                   Map<String, List<Map<String, Object>>> nodeResults) {
        Object udfIdObj = config.get("udfId");
        if (udfIdObj == null || udfIdObj.toString().isEmpty()) {
            throw new ValidationException("udfId", "TRANSFORM_UDF: udfId 必填");
        }
        String udfId = udfIdObj.toString();

        Map<String, Object> udf;
        try {
            udf = udfService.getById(udfId);
        } catch (Exception e) {
            throw new ValidationException("udfId", "UDF 不存在: " + udfId);
        }
        String udfName = String.valueOf(udf.get("name"));
        String language = String.valueOf(udf.getOrDefault("language", "python"));
        String sourceCode = (String) udf.get("source_code");
        if (sourceCode == null || sourceCode.isEmpty()) {
            throw new BusinessException("UDF 无源码，无法执行: " + udfName);
        }

        // 入参: rows=上游输入行（合并），params=节点 config.params
        Map<String, Object> sandboxParams = new LinkedHashMap<>();
        sandboxParams.put("rows", collectInputs(node, nodeResults));
        Object paramsCfg = config.get("params");
        if (paramsCfg instanceof Map<?, ?> m) {
            m.forEach((k, v) -> sandboxParams.put(String.valueOf(k), v));
        }
        if (!sandboxParams.containsKey("params")) {
            sandboxParams.put("params", paramsCfg instanceof Map<?, ?> m2
                    ? new LinkedHashMap<>(m2) : new LinkedHashMap<String, Object>());
        }

        UdfSandbox.SandboxOutput output;
        try {
            output = UdfSandbox.execute(language, sourceCode, sandboxParams);
        } catch (Exception e) {
            log.error("UDF 沙箱执行异常: udf={}, error={}", udfName, e.getMessage(), e);
            throw new BusinessException("UDF 执行失败 [" + udfName + "]: " + e.getMessage());
        }
        if (!output.success) {
            throw new BusinessException("UDF 执行失败 [" + udfName + "]: " + output.error);
        }
        return new UdfRun(parseUdfRows(output.output));
    }

    /**
     * JOIN 节点（合并多个上游/内联数据源按 joinKeys 做内存关系代数运算）。
     * config: joinKeys（数组或逗号串）/ joinType（inner|left|right|full|cross, 默认 inner）/
     * on（P2-01 关联对 [{left,right}]；left==right 时归一为单键，left-only 行在 full 语义下保留）/
     * leftNode/rightNode（可选，显式指定左右输入 nodeId，缺省按入边推导）/
     * inlineData/rightData（可选，右侧内联行）。
     */
    private List<Map<String, Object>> joinFrames(PipelineNode node, Map<String, Object> config,
                                                 Map<String, List<Map<String, Object>>> nodeResults) {
        List<String> joinKeys = normalizeJoinKeys(config.get("joinKeys"));
        if (joinKeys.isEmpty()) {
            joinKeys = joinKeysFromOn(config.get("on"));
        }
        String joinType = String.valueOf(config.getOrDefault("joinType", "inner")).toLowerCase();

        List<String> deps = parseDependsOn(node.getDependsOn());
        // 左侧输入: leftNode 指定节点或第一个上游
        List<String> leftIds = normalizeNodeIdList(config.get("leftNode"));
        if (leftIds.isEmpty()) {
            leftIds = deps.isEmpty() ? List.of() : List.of(deps.get(0));
        }
        // 右侧输入: rightNode 指定节点 / inlineData 内联 / 其余上游
        List<String> rightIds = normalizeNodeIdList(config.get("rightNode"));
        Object inlineRaw = configFirst(config, "inlineData", "rightData");
        List<Map<String, Object>> rightRows = readInlineRows(inlineRaw);
        if (inlineRaw == null && rightIds.isEmpty()) {
            rightIds = deps.size() > 1 ? deps.subList(1, deps.size()) : List.of();
        }

        List<Map<String, Object>> leftRows = new ArrayList<>();
        for (String id : leftIds) {
            List<Map<String, Object>> rows = nodeOf(nodeResults, id);
            if (rows != null) {
                leftRows.addAll(rows);
            }
        }
        for (String id : rightIds) {
            List<Map<String, Object>> rows = nodeOf(nodeResults, id);
            if (rows != null) {
                rightRows.addAll(rows);
            }
        }

        return memoryJoin(leftRows, rightRows, joinKeys, joinType);
    }

    /** 首非空配置取值（兼容多别名键）。 */
    private static Object configFirst(Map<String, Object> config, String... keys) {
        for (String k : keys) {
            if (config.containsKey(k) && config.get(k) != null) {
                return config.get(k);
            }
        }
        return null;
    }

    /** 从 P2-01 关联对 {@code on: [{left,right}]} 抽取 joinKeys（left==right 归一为单键）。 */
    private List<String> joinKeysFromOn(Object onObj) {
        if (!(onObj instanceof List<?> pairs)) {
            return List.of();
        }
        List<String> keys = new ArrayList<>();
        for (Object o : pairs) {
            if (o instanceof Map<?, ?> m) {
                Object l = m.get("left");
                Object r = m.get("right");
                if (l == null && r == null) {
                    continue;
                }
                String lk = l == null ? null : l.toString();
                String rk = r == null ? null : r.toString();
                if (lk != null && lk.equals(rk)) {
                    keys.add(lk);
                } else if (lk != null) {
                    keys.add(lk);
                } else if (rk != null) {
                    keys.add(rk);
                }
            }
        }
        return keys;
    }

    /** 结果表按 nodeId 取行（不存在返回 null）。 */
    private static List<Map<String, Object>> nodeOf(Map<String, List<Map<String, Object>>> results, String id) {
        return results != null ? results.get(id) : null;
    }

    /** 首非空（P2-01 leftNode/rightNode 优先，兼容 sLeft/sRight）。 */
    private static Object firstNonNull(Object a, Object b) {
        return a != null ? a : b;
    }

    /**
     * 内存 JOIN（移植 PipelineExecutionEngine 的 join_keys 合并语义并扩展 left/right/full/cross）。
     * <p>
     * cross: 笛卡尔积（显式覆盖 other 逻辑）；
     * other (inner/left/right/full): 按 joinKeys 复合键索引右表，左表扫描 ± 追加未匹配右行。
     */
    static List<Map<String, Object>> memoryJoin(List<Map<String, Object>> left, List<Map<String, Object>> right,
                                                List<String> joinKeys, String joinType) {
        List<Map<String, Object>> result = new ArrayList<>();
        if ("cross".equals(joinType)) {
            // 笛卡尔积（无 joinKeys 时为全表 cross）
            for (Map<String, Object> l : left) {
                for (Map<String, Object> r : right) {
                    result.add(joinKeys == null || joinKeys.isEmpty()
                            ? new LinkedHashMap<>(mergeTwoRows(l, r))
                            : mergeRow(l, r, joinKeys));
                }
            }
            return result;
        }
        if (joinKeys == null || joinKeys.isEmpty()) {
            // 无 joinKeys: 退化为 union（不丢数据）
            if (left != null) result.addAll(left);
            if (right != null) result.addAll(right);
            return result;
        }
        Map<String, Deque<Map<String, Object>>> rightIndex = new LinkedHashMap<>();
        for (Map<String, Object> r : right) {
            rightIndex.computeIfAbsent(joinKeyOf(r, joinKeys), k -> new ArrayDeque<>()).add(r);
        }
        Set<String> matchedLeftKeys = new LinkedHashSet<>();

        for (Map<String, Object> l : left) {
            String key = joinKeyOf(l, joinKeys);
            Deque<Map<String, Object>> matches = rightIndex.get(key);
            if (matches != null && !matches.isEmpty()) {
                for (Map<String, Object> r : matches) {
                    result.add(mergeRow(l, r, joinKeys));
                }
                matchedLeftKeys.add(key);
            } else if ("left".equals(joinType) || "full".equals(joinType)) {
                // 左表未匹配: left/full 保留原行
                result.add(new LinkedHashMap<>(l));
            }
        }
        // 右表未匹配到左表的键: right/full 模式追加（补 null 左键）
        if ("right".equals(joinType) || "full".equals(joinType)) {
            for (Map.Entry<String, Deque<Map<String, Object>>> e : rightIndex.entrySet()) {
                if (matchedLeftKeys.contains(e.getKey())) {
                    continue;
                }
                for (Map<String, Object> r : e.getValue()) {
                    result.add(new LinkedHashMap<>(r));
                }
            }
        }
        return result;
    }

    /** 两 Map 浅合并（右覆盖同键），用于 cross join 无 joinKeys 场景。 */
    private static Map<String, Object> mergeTwoRows(Map<String, Object> l, Map<String, Object> r) {
        Map<String, Object> out = new LinkedHashMap<>(l);
        out.putAll(r);
        return out;
    }

    /**
     * SINK: 统一写入算子 — 经 ConnectorFactory 获取 JdbcConnector，批量 INSERT 到外部数据源目标表。
     * <p>
     * config: datasourceId/targetDatasourceId(目标数据源, 兼容双别名) / table/targetTable(目标表) /
     * mode(append|overwrite, 默认 append) / batchSize(默认 1000) / columns(可选列映射) /
     * inlineData(可选内联行，缺省取上游节点产出行)。
     * 架构铁律 §2.5: 禁系统 JdbcTemplate，统一走 runtime-access JdbcConnector。
     */
    private long executeSink(PipelineNode node, Map<String, Object> config,
                             Map<String, List<Map<String, Object>>> nodeResults) throws Exception {
        Object tableObj = configFirst(config, "table", "targetTable");
        if (tableObj == null || tableObj.toString().isEmpty()) {
            throw new ValidationException("table", "SINK: table 必填");
        }
        String table = tableObj.toString();

        DataSourceEntity ds = resolveDatasource(configFirst(config, "datasourceId", "targetDatasourceId"),
                "SINK: datasourceId 必填");
        String mode = String.valueOf(configFirst(config, "mode")).toLowerCase();
        if (!mode.equals("append") && !mode.equals("overwrite")) {
            throw new ValidationException("mode", "SINK: mode 仅支持 append/overwrite");
        }
        Object bs = config.get("batchSize");
        int batchSize = toInt(bs, 1000);

        // 行来源: 节点 inlineData 优先，否则取上游节点产出行（DAG 前驱合并）
        List<Map<String, Object>> rows = readInlineRows(config.get("inlineData"));
        if (rows.isEmpty()) {
            rows = collectInputs(node, nodeResults);
        }
        if (rows.isEmpty()) {
            log.info("SINK: no data rows (table={}), skip", table);
            return 0;
        }

        // 列集: config.columns 优先，否则首行键集
        List<String> columns = normalizeStringList(config.get("columns"));
        if (columns.isEmpty()) {
            columns = new ArrayList<>(rows.get(0).keySet());
        }

        JdbcConnector jdbcConnector = requireJdbcConnector(connectorFactory.getConnector("JDBC"));
        // overwrite 模式: 先清空目标表
        if ("overwrite".equals(mode)) {
            jdbcConnector.executeSql(ds.getConnectionConfig(), "DELETE FROM " + table, 1);
            logInfo("SINK overwrite: cleared {}", table);
        }

        // 构建参数化 INSERT SQL + 批量写入（1 batch = 1 事务，跨 batch 不回滚）
        String insertSql = buildInsertSql(table, columns);
        List<Object[]> batchValues = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            Object[] arr = new Object[columns.size()];
            for (int i = 0; i < columns.size(); i++) {
                arr[i] = row.get(columns.get(i));
            }
            batchValues.add(arr);
        }
        int written = jdbcConnector.executeBatch(ds.getConnectionConfig(), insertSql, batchValues, Math.max(1, batchSize));
        // 标记 DW 层（layer=CURATED）；失败不影响写入结果
        dataLakeResourceService.markCurated(table);
        logInfo("SINK done: table={}, mode={}, rows={}, batchSize={}", table, mode, written, batchSize);
        return written;
    }

    /**
     * 构建参数化 INSERT SQL（列名/表名白名单校验，防 SQL 注入）。
     */
    private String buildInsertSql(String table, List<String> columns) {
        if (!table.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new BusinessException("SINK: 非法表名 " + table);
        }
        StringBuilder sb = new StringBuilder("INSERT INTO ").append(table).append(" (");
        List<String> placeholders = new ArrayList<>(columns.size());
        for (int i = 0; i < columns.size(); i++) {
            String col = columns.get(i);
            if (!col.matches("[A-Za-z_][A-Za-z0-9_]*")) {
                throw new BusinessException("SINK: 非法列名 " + col);
            }
            if (i > 0) {
                sb.append(", ");
                placeholders.add(", ?");
            } else {
                placeholders.add("?");
            }
            sb.append(col);
        }
        sb.append(") VALUES (");
        sb.append(String.join("", placeholders));
        sb.append(")");
        return sb.toString();
    }

    /**
     * SINK_MINIO：将上游节点产出行（或 inlineData）序列化为 CSV 后上传数据湖近源层（MinIO）。
     * <p>config 字段：bucket(可选，默认数据湖配置 bucket)、objectName(可选，默认
     * {@code raw/structured/{source}/{table}/dt={yyyy-MM-dd}/{table}_{yyyyMMddHHmmss}.csv})、
     * table(必填，对象名前缀)、format(可选，仅支持 csv)、columns(可选，指定列顺序)、
     * inlineData(可选内联行)。
     * <p>上传成功后登记/标记 td_data_resource（layer=RAW, zone=STRUCTURED）并返回行数；
     * MinIO 不可用或上传失败抛 BusinessException（执行失败可回溯）。
     *
     * @param lakeSource 近源层对象 key 的 {source} 段
     */
    private long executeSinkMinio(PipelineNode node, Map<String, Object> config,
                                  Map<String, List<Map<String, Object>>> nodeResults,
                                  String lakeSource) throws Exception {
        Object tableObj = configFirst(config, "table", "objectPrefix");
        if (tableObj == null || tableObj.toString().isEmpty()) {
            throw new ValidationException("table", "SINK_MINIO: table 必填");
        }
        String table = tableObj.toString();

        String format = String.valueOf(config.getOrDefault("format", "csv")).toLowerCase();
        if (!"csv".equals(format)) {
            throw new ValidationException("format", "SINK_MINIO: format 仅支持 csv");
        }

        // 行来源: 节点 inlineData 优先，否则取上游节点产出行（DAG 前驱合并）
        List<Map<String, Object>> rows = readInlineRows(config.get("inlineData"));
        if (rows.isEmpty()) {
            rows = collectInputs(node, nodeResults);
        }
        if (rows.isEmpty()) {
            log.info("SINK_MINIO: no data rows (table={}), skip", table);
            return 0;
        }

        // 列集: config.columns 优先，否则首行键集（保持行序稳定）
        List<String> columns = normalizeStringList(config.get("columns"));
        if (columns.isEmpty()) {
            columns = new ArrayList<>(rows.get(0).keySet());
        }

        // 对象名: 显式 objectName > raw/structured/{source}/{table}/dt={yyyy-MM-dd}/{table}_{ts}.csv
        String objectName = strOrNull(config.get("objectName"));
        if (objectName == null || objectName.isEmpty()) {
            java.time.LocalDateTime now = java.time.LocalDateTime.now();
            String ts = now.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
            String dt = now.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            objectName = "raw/structured/" + lakeSource + "/" + table + "/dt=" + dt + "/" + table + "_" + ts + ".csv";
        }

        byte[] csvBytes = toCsv(rows, columns);
        Map<String, Object> upload = minioStorageService.putObject(objectName, csvBytes, "text/csv; charset=UTF-8");
        if (!"success".equals(upload.get("status"))) {
            throw new BusinessException("SINK_MINIO 上传失败: " + upload.get("message"));
        }
        // 登记/标记近源层对象（layer=RAW, zone=STRUCTURED）；失败不影响上传结果
        dataLakeResourceService.markNearSourceStructured(
                table, objectName, lakeSource, columns.size(), (long) rows.size());
        logInfo("SINK_MINIO done: object={}, rows={}, bucket={}", objectName, rows.size(), upload.get("bucket"));
        return rows.size();
    }

    /** 将行集序列化为 CSV（UTF-8）。值 null → 空串；含逗号/引号/换行 → 双引号包裹。 */
    private byte[] toCsv(List<Map<String, Object>> rows, List<String> columns) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(escapeCsv(columns.get(i)));
        }
        sb.append('\n');
        for (Map<String, Object> row : rows) {
            for (int i = 0; i < columns.size(); i++) {
                if (i > 0) sb.append(',');
                Object v = row.get(columns.get(i));
                sb.append(escapeCsv(v == null ? "" : String.valueOf(v)));
            }
            sb.append('\n');
        }
        return sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    /** CSV 字段转义：含逗号/引号/换行/回车时用双引号包裹并转义内部引号。 */
    private String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        if (value.indexOf(',') >= 0 || value.indexOf('"') >= 0
                || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private String strOrNull(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    // ==================== 调试链委托入口（供 PipelineDebugService 复用） ====================

    /**
     * 供 PipelineDebugService 调用的 UDF 执行入口。
     * 独立实例化辅助方法，不依赖 PipelineExecutionService 的成员字段。
     */
    public static List<Map<String, Object>> runUdfTransformPublic(
            PipelineNode node, Map<String, Object> config,
            Map<String, List<Map<String, Object>>> nodeResults,
            UdfService udfService) {
        Object udfIdObj = config.get("udfId");
        if (udfIdObj == null || udfIdObj.toString().isEmpty()) {
            throw new ValidationException("udfId", "TRANSFORM_UDF: udfId 必填");
        }
        String udfId = udfIdObj.toString();
        Map<String, Object> udf;
        try {
            udf = udfService.getById(udfId);
        } catch (Exception e) {
            throw new ValidationException("udfId", "UDF 不存在: " + udfId);
        }
        String udfName = String.valueOf(udf.get("name"));
        String language = String.valueOf(udf.getOrDefault("language", "python"));
        String sourceCode = (String) udf.get("source_code");
        if (sourceCode == null || sourceCode.isEmpty()) {
            throw new BusinessException("UDF 无源码，无法执行: " + udfName);
        }
        Map<String, Object> sandboxParams = new LinkedHashMap<>();
        sandboxParams.put("rows", collectInputsStatic(node, nodeResults));
        Object paramsCfg = config.get("params");
        if (paramsCfg instanceof Map<?, ?> m) {
            m.forEach((k, v) -> sandboxParams.put(String.valueOf(k), v));
        }
        if (!sandboxParams.containsKey("params")) {
            sandboxParams.put("params", paramsCfg instanceof Map<?, ?> m2
                    ? new LinkedHashMap<>(m2) : new LinkedHashMap<String, Object>());
        }
        UdfSandbox.SandboxOutput output;
        try {
            output = UdfSandbox.execute(language, sourceCode, sandboxParams);
        } catch (Exception e) {
            throw new BusinessException("UDF 执行失败 [" + udfName + "]: " + e.getMessage());
        }
        if (!output.success) {
            throw new BusinessException("UDF 执行失败 [" + udfName + "]: " + output.error);
        }
        return parseUdfRowsStatic(output.output);
    }

    /**
     * 供 PipelineDebugService 调用的 JOIN 执行入口。
     */
    public static List<Map<String, Object>> joinFramesPublic(
            PipelineNode node, Map<String, Object> config,
            Map<String, List<Map<String, Object>>> nodeResults) {
        List<String> joinKeys = normalizeJoinKeysStatic(config.get("joinKeys"));
        if (joinKeys.isEmpty()) {
            joinKeys = joinKeysFromOnStatic(config.get("on"));
        }
        String joinType = String.valueOf(config.getOrDefault("joinType", "inner")).toLowerCase();
        List<String> deps = parseDependsOnStatic(node.getDependsOn());
        List<String> leftIds = normalizeNodeIdListStatic(config.get("leftNode"));
        if (leftIds.isEmpty()) {
            leftIds = deps.isEmpty() ? List.of() : List.of(deps.get(0));
        }
        List<String> rightIds = normalizeNodeIdListStatic(config.get("rightNode"));
        Object inlineRaw = configFirstStatic(config, "inlineData", "rightData");
        List<Map<String, Object>> rightRows = readInlineRowsStatic(inlineRaw);
        if (inlineRaw == null && rightIds.isEmpty()) {
            rightIds = deps.size() > 1 ? deps.subList(1, deps.size()) : List.of();
        }
        List<Map<String, Object>> leftRows = new ArrayList<>();
        for (String id : leftIds) {
            List<Map<String, Object>> rows = nodeOf(nodeResults, id);
            if (rows != null) {
                leftRows.addAll(rows);
            }
        }
        for (String id : rightIds) {
            List<Map<String, Object>> rows = nodeOf(nodeResults, id);
            if (rows != null) {
                rightRows.addAll(rows);
            }
        }
        return memoryJoin(leftRows, rightRows, joinKeys, joinType);
    }

    /**
     * 供 PipelineDebugService 调用的 SINK 执行入口。
     */
    public static long executeSinkPublic(
            PipelineNode node, Map<String, Object> config,
            Map<String, List<Map<String, Object>>> nodeResults,
            ConnectorFactory connectorFactory,
            DataSourceService dataSourceService) throws Exception {
        Object tableObj = configFirstStatic(config, "table", "targetTable");
        if (tableObj == null || tableObj.toString().isEmpty()) {
            throw new ValidationException("table", "SINK: table 必填");
        }
        String table = tableObj.toString();
        DataSourceEntity ds = resolveDatasourcePublic(
                configFirstStatic(config, "datasourceId", "targetDatasourceId"),
                "SINK: datasourceId 必填", dataSourceService);
        String mode = String.valueOf(configFirstStatic(config, "mode")).toLowerCase();
        if (!mode.equals("append") && !mode.equals("overwrite")) {
            throw new ValidationException("mode", "SINK: mode 仅支持 append/overwrite");
        }
        Object bs = config.get("batchSize");
        int batchSize = toIntStatic(bs, 1000);
        List<Map<String, Object>> rows = readInlineRowsStatic(config.get("inlineData"));
        if (rows.isEmpty()) {
            rows = collectInputsStatic(node, nodeResults);
        }
        if (rows.isEmpty()) {
            return 0;
        }
        List<String> columns = normalizeStringListStatic(config.get("columns"));
        if (columns.isEmpty()) {
            columns = new ArrayList<>(rows.get(0).keySet());
        }
        Connector connector = connectorFactory.getConnector("JDBC");
        if (!(connector instanceof JdbcConnector jc)) {
            throw new BusinessException("Expected JdbcConnector but got: " + connector.getClass().getName());
        }
        if ("overwrite".equals(mode)) {
            jc.executeSql(ds.getConnectionConfig(), "DELETE FROM " + table, 1);
        }
        String insertSql = buildInsertSqlStatic(table, columns);
        List<Object[]> batchValues = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            Object[] arr = new Object[columns.size()];
            for (int i = 0; i < columns.size(); i++) {
                arr[i] = row.get(columns.get(i));
            }
            batchValues.add(arr);
        }
        int written = jc.executeBatch(ds.getConnectionConfig(), insertSql, batchValues, Math.max(1, batchSize));
        return written;
    }

    /** 静态版：解析数据源。 */
    private static DataSourceEntity resolveDatasourcePublic(Object dsIdObj, String requiredMsg,
                                                            DataSourceService dataSourceService) {
        if (dsIdObj == null || dsIdObj.toString().isEmpty()) {
            throw new ValidationException("datasourceId", requiredMsg);
        }
        DataSourceEntity ds = dataSourceService.getById(dsIdObj.toString());
        if (ds == null) {
            throw new NotFoundException("DataSource not found: " + dsIdObj);
        }
        if (ds.getConnectionConfig() == null || ds.getConnectionConfig().isEmpty()) {
            throw new BusinessException("数据源无连接配置: " + dsIdObj);
        }
        return ds;
    }

    /** 静态版：合并前驱节点产出行。 */
    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> collectInputsStatic(PipelineNode node,
                                                                 Map<String, List<Map<String, Object>>> nodeResults) {
        List<String> deps = parseDependsOnStatic(node.getDependsOn());
        List<Map<String, Object>> merged = new ArrayList<>();
        for (String dep : deps) {
            List<Map<String, Object>> rows = nodeOf(nodeResults, dep);
            if (rows != null) {
                merged.addAll(rows);
            }
        }
        return merged;
    }

    /** 静态版：规范化 joinKeys。 */
    private static List<String> normalizeJoinKeysStatic(Object raw) {
        if (raw == null) return List.of();
        if (raw instanceof List<?> list) {
            return list.stream().map(String::valueOf).map(String::trim).filter(s -> !s.isEmpty()).toList();
        }
        String s = raw.toString().trim();
        if (s.isEmpty()) return List.of();
        return Arrays.stream(s.split(",")).map(String::trim).filter(x -> !x.isEmpty()).toList();
    }

    /** 静态版：规范化 nodeId 列表。 */
    private static List<String> normalizeNodeIdListStatic(Object raw) {
        return normalizeJoinKeysStatic(raw);
    }

    /** 静态版：规范化字符串列表。 */
    private static List<String> normalizeStringListStatic(Object raw) {
        return normalizeJoinKeysStatic(raw);
    }

    /** 静态版：解析 dependsOn。 */
    private static List<String> parseDependsOnStatic(String dependsOn) {
        if (dependsOn == null || dependsOn.isBlank() || "[]".equals(dependsOn)) {
            return Collections.emptyList();
        }
        try {
            return mapper.readValue(dependsOn, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    /** 静态版：首非空配置取值。 */
    private static Object configFirstStatic(Map<String, Object> config, String... keys) {
        for (String k : keys) {
            if (config.containsKey(k) && config.get(k) != null) {
                return config.get(k);
            }
        }
        return null;
    }

    /** 静态版：从 on 关联对抽取 joinKeys。 */
    private static List<String> joinKeysFromOnStatic(Object onObj) {
        if (!(onObj instanceof List<?> pairs)) {
            return List.of();
        }
        List<String> keys = new ArrayList<>();
        for (Object o : pairs) {
            if (o instanceof Map<?, ?> m) {
                Object l = m.get("left");
                Object r = m.get("right");
                if (l == null && r == null) continue;
                String lk = l == null ? null : l.toString();
                String rk = r == null ? null : r.toString();
                if (lk != null && lk.equals(rk)) keys.add(lk);
                else if (lk != null) keys.add(lk);
                else if (rk != null) keys.add(rk);
            }
        }
        return keys;
    }

    /** 静态版：解析内联行。 */
    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> readInlineRowsStatic(Object raw) {
        if (raw instanceof List<?> list) {
            List<Map<String, Object>> rows = new ArrayList<>();
            for (Object o : list) {
                if (o instanceof Map<?, ?> m) {
                    rows.add((Map<String, Object>) m);
                }
            }
            return rows;
        }
        return new ArrayList<>();
    }

    /** 静态版：toInt。 */
    private static int toIntStatic(Object val, int def) {
        if (val == null) return def;
        if (val instanceof Number n) return n.intValue();
        try { return Integer.parseInt(val.toString()); } catch (Exception e) { return def; }
    }

    /** 静态版：构建参数化 INSERT SQL（列名/表名白名单校验）。 */
    private static String buildInsertSqlStatic(String table, List<String> columns) {
        if (!table.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new BusinessException("SINK: 非法表名 " + table);
        }
        StringBuilder sb = new StringBuilder("INSERT INTO ").append(table).append(" (");
        List<String> placeholders = new ArrayList<>(columns.size());
        for (int i = 0; i < columns.size(); i++) {
            String col = columns.get(i);
            if (!col.matches("[A-Za-z_][A-Za-z0-9_]*")) {
                throw new BusinessException("SINK: 非法列名 " + col);
            }
            if (i > 0) {
                sb.append(", ");
                placeholders.add(", ?");
            } else {
                placeholders.add("?");
            }
            sb.append(col);
        }
        sb.append(") VALUES (");
        sb.append(String.join("", placeholders));
        sb.append(")");
        return sb.toString();
    }

    /** 静态版：解析 UDF 输出为行列表。 */
    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> parseUdfRowsStatic(String output) {
        if (output == null) return List.of();
        String s = output.trim();
        if (s.isEmpty() || "undefined".equals(s) || "null".equals(s)) return List.of();
        try {
            Object parsed = mapper.readValue(s, Object.class);
            if (parsed instanceof List<?> list) {
                List<Map<String, Object>> rows = new ArrayList<>();
                for (Object o : list) {
                    if (o instanceof Map<?, ?> m) rows.add((Map<String, Object>) m);
                    else if (o != null) rows.add(Map.of("value", String.valueOf(o)));
                }
                return rows;
            }
            if (parsed instanceof Map<?, ?> m) {
                return List.of((Map<String, Object>) new LinkedHashMap<>(m));
            }
            return List.of(Map.of("value", s));
        } catch (Exception e) {
            return List.of(Map.of("value", s));
        }
    }

    // ==================== 既有私有方法 ====================

    /**
     * OUTPUT_OBJECT: 插入结果到目标表。
     */
    @SuppressWarnings("unchecked")
    private long executeOutputObject(Map<String, Object> config) {
        String targetTable = (String) config.get("targetTable");
        if (targetTable == null || targetTable.isEmpty()) {
            throw new ValidationException("targetTable", "OUTPUT_OBJECT: targetTable 必填");
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

        // 标记 DW 层（layer=CURATED）；失败不影响写入结果
        dataLakeResourceService.markCurated(targetTable);
        logInfo("OUTPUT_OBJECT: inserted {} rows into {}", count, targetTable);
        return count;
    }

    // ==================== 辅助方法 ====================

    /** 数据源解析: 校验存在且带连接配置（SINK/JOIN/SOURCE 共用）。 */
    private DataSourceEntity resolveDatasource(Object dsIdObj, String requiredMsg) {
        if (dsIdObj == null || dsIdObj.toString().isEmpty()) {
            throw new ValidationException("datasourceId", requiredMsg);
        }
        String datasourceId = dsIdObj.toString();
        DataSourceEntity ds = dataSourceService.getById(datasourceId);
        if (ds == null) {
            throw new NotFoundException("DataSource not found: " + datasourceId);
        }
        if (ds.getConnectionConfig() == null || ds.getConnectionConfig().isEmpty()) {
            throw new BusinessException("数据源无连接配置: " + datasourceId);
        }
        return ds;
    }

    /** 校验 Connector 实际类型。 */
    private JdbcConnector requireJdbcConnector(Connector c) {
        if (!(c instanceof JdbcConnector jc)) {
            throw new BusinessException("Expected JdbcConnector but got: " + c.getClass().getName());
        }
        return jc;
    }

    private CsvConnector requireCsvConnector(Connector c) {
        if (!(c instanceof CsvConnector cc)) {
            throw new BusinessException("Expected CsvConnector but got: " + c.getClass().getName());
        }
        return cc;
    }

    private RestApiConnector requireRestConnector(Connector c) {
        if (!(c instanceof RestApiConnector rc)) {
            throw new BusinessException("Expected RestApiConnector but got: " + c.getClass().getName());
        }
        return rc;
    }

    /**
     * 合并所有前驱节点（dependsOn）的产出行，供 UDF/SINK 节点消费。
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> collectInputs(PipelineNode node,
                                                    Map<String, List<Map<String, Object>>> nodeResults) {
        List<String> deps = parseDependsOn(node.getDependsOn());
        List<Map<String, Object>> merged = new ArrayList<>();
        for (String dep : deps) {
            List<Map<String, Object>> rows = nodeOf(nodeResults, dep);
            if (rows != null) {
                merged.addAll(rows);
            }
        }
        return merged;
    }

    /** 规范化 joinKeys（逗号串或 JSON 数组均可）。 */
    private List<String> normalizeJoinKeys(Object raw) {
        if (raw == null) {
            return List.of();
        }
        if (raw instanceof List<?> list) {
            return list.stream().map(String::valueOf).map(String::trim).filter(s -> !s.isEmpty()).toList();
        }
        String s = raw.toString().trim();
        if (s.isEmpty()) {
            return List.of();
        }
        return Arrays.stream(s.split(",")).map(String::trim).filter(x -> !x.isEmpty()).toList();
    }

    /** 规范化节点 ID 列表（逗号串或 JSON 数组）。 */
    private List<String> normalizeNodeIdList(Object raw) {
        return normalizeJoinKeys(raw);
    }

    /** 规范化字符串数组（columns 配置）。 */
    private List<String> normalizeStringList(Object raw) {
        return normalizeJoinKeys(raw);
    }

    /** 解析内联数据行（List&lt;Map&gt;，支持 JSON 数组）。 */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> readInlineRows(Object raw) {
        if (raw instanceof List<?> list) {
            List<Map<String, Object>> rows = new ArrayList<>();
            for (Object o : list) {
                if (o instanceof Map<?, ?> m) {
                    rows.add((Map<String, Object>) m);
                }
            }
            return rows;
        }
        return new ArrayList<>();
    }

    /** 拼接行在 joinKeys 上的复合键（null 值统一 '∅' 标记）。 */
    private static String joinKeyOf(Map<String, Object> row, List<String> joinKeys) {
        StringBuilder sb = new StringBuilder();
        for (String key : joinKeys) {
            Object val = row.get(key);
            sb.append(val == null ? "∅" : String.valueOf(val)).append('|');
        }
        return sb.toString();
    }

    /** 合并左右行（右行 name 冲突时降级 r_ 前缀，join 键除外）。 */
    private static Map<String, Object> mergeRow(Map<String, Object> l, Map<String, Object> r, List<String> joinKeys) {
        Map<String, Object> out = new LinkedHashMap<>(l);
        for (Map.Entry<String, Object> e : r.entrySet()) {
            if (joinKeys.contains(e.getKey())) {
                continue;
            }
            String name = e.getKey();
            if (out.containsKey(name) && !Objects.equals(out.get(name), e.getValue())) {
                name = "r_" + name;
            }
            out.put(name, e.getValue());
        }
        return out;
    }

    /** 解析 UDF 沙箱输出为行列表（支持 JSON 数组/对象，非 JSON 视为单行摘要）。 */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseUdfRows(String output) {
        if (output == null) {
            return List.of();
        }
        String s = output.trim();
        if (s.isEmpty() || "undefined".equals(s) || "null".equals(s)) {
            return List.of();
        }
        try {
            Object parsed = mapper.readValue(s, Object.class);
            if (parsed instanceof List<?> list) {
                List<Map<String, Object>> rows = new ArrayList<>();
                for (Object o : list) {
                    if (o instanceof Map<?, ?> m) {
                        rows.add((Map<String, Object>) m);
                    } else if (o != null) {
                        rows.add(Map.of("value", String.valueOf(o)));
                    }
                }
                return rows;
            }
            if (parsed instanceof Map<?, ?> m) {
                return List.of((Map<String, Object>) new LinkedHashMap<>(m));
            }
            return List.of(Map.of("value", s));
        } catch (Exception e) {
            return List.of(Map.of("value", s));
        }
    }

    /** UDF 执行产出（rows=输出行）。 */
    private static final class UdfRun {
        final List<Map<String, Object>> rows;

        UdfRun(List<Map<String, Object>> rows) {
            this.rows = rows;
        }
    }

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
            throw new BusinessException("构建 CSV 连接配置失败: " + e.getMessage());
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
            for (PipelineNode n : nodes) {
                remaining.add(n.getNodeId());
            }
            result.forEach(r -> remaining.remove(r.getNodeId()));
            throw new BusinessException("Pipeline DAG 存在循环依赖，无法拓扑排序，未排序节点: " + remaining);
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
