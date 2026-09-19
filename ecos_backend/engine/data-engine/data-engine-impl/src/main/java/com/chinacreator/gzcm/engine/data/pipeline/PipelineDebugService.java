package com.chinacreator.gzcm.engine.data.pipeline;

import com.chinacreator.gzcm.common.exception.BusinessException;
import com.chinacreator.gzcm.common.exception.NotFoundException;
import com.chinacreator.gzcm.common.exception.ValidationException;
import com.chinacreator.gzcm.engine.data.DataSourceService;
import com.chinacreator.gzcm.engine.data.UdfService;
import com.chinacreator.gzcm.engine.data.datasource.entity.DataSourceEntity;
import com.chinacreator.gzcm.runtime.access.connector.Connector;
import com.chinacreator.gzcm.runtime.access.connector.ConnectorFactory;
import com.chinacreator.gzcm.runtime.access.connector.CsvConnector;
import com.chinacreator.gzcm.runtime.access.connector.JdbcConnector;
import com.chinacreator.gzcm.runtime.access.connector.RestApiConnector;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Pipeline 断点调试核心服务 — 按节点粒度暂停、单步/继续执行的调试执行器。
 * <p>
 * 设计原则（架构铁律）：
 * <ul>
 *   <li>不修改既有 PipelineExecutionService / PipelineRepository / PipelineController；
 *       节点执行语义对齐 PipelineExecutionService.executeNode（同构独立实现，额外留存 sample/schema 以支撑数据预览与变量快照）</li>
 *   <li>会话状态全部内存态（ConcurrentHashMap），@Scheduled 惰性清理过期会话（默认 30min）</li>
 *   <li>拓扑序：节点按 depends_on 构建反向边 + Kahn 入度排序（与既有 PipelineExecutionService.topologicalSort 同构）</li>
 *   <li>执行落库：sessionStart → DB RUNNING；断点暂停时 DB PAUSED；终态写 COMPLETED/FAILED/CANCELLED；
 *       复用 PipelineRepository（既有 schema 列：status/error_message/rows_processed/started_at/finished_at）</li>
 *   <li>节点级日志：调试会话内保留日志，execLogs 按 executionId 索引；
 *       GET /logs 端点按 executionId 一次性返回 seq 升序列表（前端增量拉取作为 SSE 兜底）</li>
 * </ul>
 *
 * @author DataBridge Datanet Team
 */
@Service
public class PipelineDebugService {

    private static final Logger log = LoggerFactory.getLogger(PipelineDebugService.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    /** 会话默认过期（毫秒） */
    private static final long SESSION_TTL_MS = 30 * 60 * 1000L;
    /** 前端采样数据预览上限（前 N 行） */
    private static final int SAMPLE_LIMIT = 100;
    /** 命中记录保留上限（防 OOM） */
    private static final int MAX_HIT_RECORDS = 50;
    /** 单节点日志保留上限 */
    private static final int MAX_NODE_LOGS = 500;
    /** execution 日志保留时长（毫秒） */
    private static final long EXEC_LOG_TTL_MS = 60 * 60 * 1000L;

    private final PipelineRepository repository;
    private final ConnectorFactory connectorFactory;
    private final JdbcTemplate jdbc;
    private final DataSourceService dataSourceService;
    private final UdfService udfService;
    /** 主执行器：SOURCE_MINIO / SINK_MINIO 节点复用其近源层读写逻辑（避免两处实现漂移） */
    private final PipelineExecutionService pipelineExecutionService;

    /** 全部调试会话（内存态，惰性清理） */
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    /** executionId → 节点级日志列表（SSE 推送索引） */
    private final Map<String, ExecutionLog> execLogs = new ConcurrentHashMap<>();
    /** 日志自增编号 */
    private final AtomicInteger logSeq = new AtomicInteger(0);

    /** 调试会话状态机。 */
    public enum State {
        CREATED, QUEUED, RUNNING, AWAITING, BROKEN, PAUSED, COMPLETED, FAILED, STOPPED
    }

    public PipelineDebugService(PipelineRepository repository,
                                ConnectorFactory connectorFactory,
                                JdbcTemplate jdbc,
                                DataSourceService dataSourceService,
                                UdfService udfService,
                                PipelineExecutionService pipelineExecutionService) {
        this.repository = repository;
        this.connectorFactory = connectorFactory;
        this.jdbc = jdbc;
        this.dataSourceService = dataSourceService;
        this.udfService = udfService;
        this.pipelineExecutionService = pipelineExecutionService;
    }

    // ==================== 会话创建 ====================

    /**
     * 创建调试会话。
     * <p>
     * 入参 PipelineDebugStartDTO 支持两种模式：
     * <ol>
     *   <li>definitionId：引用库中已存在的 Pipeline 定义（节点/依赖从 DB 加载）</li>
     *   <li>definition：内联 ad-hoc 定义（画布未保存时直接调试，不落库）</li>
     * </ol>
     */
    public PipelineDebugSessionVO createSession(PipelineDebugStartDTO dto) {
        if (dto == null) {
            throw new ValidationException("request", "调试请求体不能为空");
        }
        List<PipelineNode> rawNodes;
        String definitionId = null;
        String name = "ad-hoc-pipeline";

        if (dto.getDefinitionId() != null && !dto.getDefinitionId().isBlank()) {
            definitionId = dto.getDefinitionId();
            PipelineDefinition def = repository.findDefinitionById(definitionId);
            if (def == null) {
                throw new NotFoundException("Pipeline definition not found: " + definitionId);
            }
            if ("ARCHIVED".equals(def.getStatus())) {
                throw new NotFoundException("Pipeline 定义已被删除: " + definitionId);
            }
            name = def.getName() != null ? def.getName() : ("pipeline-" + definitionId);
            rawNodes = repository.findNodesByDefinitionId(definitionId);
        } else if (dto.getDefinition() != null && dto.getDefinition().getNodes() != null
                && !dto.getDefinition().getNodes().isEmpty()) {
            PipelineDebugStartDTO.PipelineDebugDefinitionDTO def = dto.getDefinition();
            name = def.getName() != null ? def.getName() : "ad-hoc-pipeline";
            rawNodes = new ArrayList<>(def.getNodes().size());
            for (PipelineDebugStartDTO.PipelineDebugNodeDTO n : def.getNodes()) {
                if (n.getNodeId() == null || n.getNodeId().isBlank()) {
                    throw new ValidationException("nodeId", "节点缺少 nodeId");
                }
                if (n.getType() == null || n.getType().isBlank()) {
                    throw new ValidationException("type", "节点缺少 type");
                }
                PipelineNode p = new PipelineNode();
                p.setNodeId(n.getNodeId());
                p.setType(n.getType());
                p.setConfig(n.getConfig() == null ? null : toJson(n.getConfig()));
                p.setDependsOn(n.getDependsOn() == null ? null : toJson(n.getDependsOn()));
                rawNodes.add(p);
            }
        } else {
            throw new ValidationException("request", "需指定 definitionId 或 definition.nodes（非空）");
        }
        if (rawNodes.isEmpty()) {
            throw new BusinessException("Pipeline 无节点，无法调试");
        }

        // 断点合法性校验：nodeId 必须存在于节点集
        Set<String> nodeIds = new LinkedHashSet<>();
        for (PipelineNode n : rawNodes) {
            nodeIds.add(n.getNodeId());
        }
        Map<String, BreakpointSpec> breakpoints = new LinkedHashMap<>();
        if (dto.getBreakpoints() != null) {
            for (PipelineDebugStartDTO.BreakpointSpecDTO bp : dto.getBreakpoints()) {
                if (bp == null || bp.getNodeId() == null || !nodeIds.contains(bp.getNodeId())) {
                    continue;
                }
                String cond = (bp.getCondition() == null || bp.getCondition().isBlank())
                        ? null : bp.getCondition().trim();
                breakpoints.put(bp.getNodeId(), new BreakpointSpec(bp.getNodeId(), cond));
            }
        }

        // 拓扑排序（Kahn，与 PipelineExecutionService.topologicalSort 同构）
        List<PipelineNode> sorted = topologicalSort(rawNodes);

        Session session = new Session(UUID.randomUUID().toString().replace("-", ""),
                definitionId, name, sorted, breakpoints);
        sessions.put(session.id, session);
        return toSessionVO(session);
    }

    // ==================== 会话控制 ====================

    public PipelineDebugSessionVO getSession(String sessionId) {
        return toSessionVO(requireSession(sessionId));
    }

    /**
     * 单步执行 — 从 CREATED 执行下一个 QUEUED 节点；
     * 若下一个节点是断点则暂停（BROKEN），否则执行完毕。
     */
    public PipelineDebugSessionVO step(String sessionId) {
        Session s = requireSession(sessionId);
        synchronized (s.lock) {
            touch(s);
            if (isTerminal(s.state)) {
                throw new BusinessException("会话已终结，无法单步: state=" + s.state);
            }
            advance(false, s);
            if (s.state != State.BROKEN && s.state != State.AWAITING) {
                if (s.executionId == null) {
                    persistRun(s);
                }
            }
            if (s.finishedAt == null) {
                s.finishedAt = nowIso();
            }
        }
        return toSessionVO(s);
    }

    /**
     * 继续执行 — 从当前位置连续执行，直到命中断点（BROKEN）或结束（COMPLETED/FAILED）。
     */
    public PipelineDebugSessionVO cont(String sessionId) {
        Session s = requireSession(sessionId);
        synchronized (s.lock) {
            touch(s);
            if (isTerminal(s.state)) {
                return toSessionVO(s);
            }
            advance(true, s);
            persistRun(s);
            if (s.finishedAt == null) {
                s.finishedAt = nowIso();
            }
        }
        return toSessionVO(s);
    }

    /**
     * 停止会话 — 终止态 CANCELLED 落库。
     */
    public PipelineDebugSessionVO stop(String sessionId) {
        Session s = requireSession(sessionId);
        synchronized (s.lock) {
            if (isTerminal(s.state)) {
                return toSessionVO(s);
            }
            s.state = State.STOPPED;
            persistRun(s);
            s.finishedAt = nowIso();
        }
        return toSessionVO(s);
    }

    /**
     * 重置会话 — 回到 CREATED（清空 queueIndex/steps/logs，保留断点配置）。
     */
    public PipelineDebugSessionVO reset(String sessionId) {
        Session s = requireSession(sessionId);
        synchronized (s.lock) {
            touch(s);
            // 清空已落库引用，下次 continue/step 时重新 INSERT
            s.executionId = null;
            s.error = null;
            s.state = State.CREATED;
            s.queueIndex = 0;
            s.completedNodes = 0;
            s.totalRows = 0L;
            s.startedAt = null;
            s.finishedAt = null;
            s.currentNodeId = null;
            s.currentStepStatus = null;
            s.variableSnapshot = null;
            s.hitRecords.clear();
            s.steps.clear();
            s.logs.clear();
        }
        return toSessionVO(s);
    }

    public void deleteSession(String sessionId) {
        Session removed = sessions.remove(sessionId);
        if (removed != null) {
            log.info("Pipeline debug session deleted: id={}", sessionId);
        }
    }

    // ==================== 单步/继续 统一推进逻辑 ====================

    /**
     * 推进状态机。continue=true 时循环执行直到 BROKEN/terminated；
     * continue=false 时执行 1 步后（或到达下一断点前暂停）返回。
     */
    private void advance(boolean continueMode, Session s) {
        while (!isTerminal(s.state) && s.state != State.BROKEN && s.state != State.AWAITING) {
            if (s.queueIndex >= s.nodes.size()) {
                s.state = State.COMPLETED;
                break;
            }
            PipelineNode node = s.nodes.get(s.queueIndex);
            // 断点前置判定 — 停在此节点之前
            if (hasBreakpoint(s, node.getNodeId())) {
                markBreaking(s, node);
                break;
            }
            // 同步执行节点
            executeAndRecord(s, node);
            if (!continueMode) {
                break;
            }
        }
    }

    /** 命中断点暂停 — 写变量快照 + 命中记录。 */
    private void markBreaking(Session s, PipelineNode node) {
        s.state = State.BROKEN;
        s.currentNodeId = node.getNodeId();
        BreakpointSpec bp = s.breakpoints.get(node.getNodeId());
        Map<String, Object> snap = new LinkedHashMap<>();
        snap.put("rowsProcessed", s.totalRows);
        snap.put("completedNodes", s.completedNodes);
        snap.put("totalNodes", s.nodes.size());
        snap.put("nextNodeId", node.getNodeId());
        snap.put("state", s.state.name());
        if (bp != null && bp.condition != null) {
            snap.put("breakpointCondition", bp.condition);
        }
        s.variableSnapshot = snap;
        s.hitRecords.add(0, new HitRecord(node.getNodeId(),
                bp != null ? bp.condition : null, nowIso(), snap));
        while (s.hitRecords.size() > MAX_HIT_RECORDS) {
            s.hitRecords.remove(s.hitRecords.size() - 1);
        }
        logNode(s, node.getNodeId(), "WARNING",
                "Pipeline 在断点处暂停: node=" + node.getNodeId());
    }

    /** 实际执行节点（成功/失败）。 */
    private void executeAndRecord(Session s, PipelineNode node) {
        s.state = State.RUNNING;
        s.currentNodeId = node.getNodeId();
        s.currentStepStatus = "RUNNING";
        long start = System.currentTimeMillis();
        logNode(s, node.getNodeId(), "INFO",
                "Node 开始执行: type=" + node.getType());
        try {
            NodeResult res = executeNode(s, node);
            long nodeMs = System.currentTimeMillis() - start;
            s.currentStepStatus = "SUCCEEDED";
            recordStep(s, node, "SUCCEEDED", res, nodeMs, null);
            s.queueIndex++;
            s.totalRows += Math.max(0L, res.rows());
            s.completedNodes++;
            logNode(s, node.getNodeId(), "INFO",
                    "Node 执行成功: rows=" + res.rows() + ", ms=" + nodeMs);
        } catch (Exception e) {
            long nodeMs = System.currentTimeMillis() - start;
            s.currentStepStatus = "FAILED";
            s.error = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            recordStep(s, node, "FAILED",
                    new NodeResult(0L, null, null,
                            Map.of("rowsProcessed", s.totalRows), nowIso(), nodeMs, null),
                    nodeMs, s.error);
            logNode(s, node.getNodeId(), "ERROR", "节点执行失败: " + s.error, e);
            s.state = State.FAILED;
            persistRun(s);
            s.finishedAt = nowIso();
        }
    }

    /**
     * 节点执行 — 与 PipelineExecutionService.executeNode 的语义一致（11 类型）。
     * 保留 sample（前 SAMPLE_LIMIT 行）与列名以支撑数据预览 / 变量快照。
     */
    private NodeResult executeNode(Session s, PipelineNode node) throws Exception {
        Map<String, Object> config = parseConfig(node.getConfig());
        String type = node.getType();
        return switch (type) {
            case "SOURCE_JDBC" -> execSourceJdbcCapture(s, node, config);
            case "SOURCE_CSV" -> execSourceCsvCapture(s, node, config);
            case "SOURCE_REST" -> execSourceRestCapture(s, node, config);
            case "SOURCE_MINIO" -> execSourceMinioCapture(s, node, config);
            case "SOURCE_CDC" -> {
                // D12：SOURCE_CDC 仅在节点枚举中登记，调试执行器未实现 —— 显式拒绝（与 PipelineExecutionService 同源）
                log.warn("SOURCE_CDC 节点未实现，显式拒绝调试执行: nodeId={}", node.getNodeId());
                throw new BusinessException(
                        "SOURCE_CDC 节点尚未实现，请使用 SOURCE_JDBC / SOURCE_CSV / SOURCE_REST");
            }
            case "TRANSFORM_SQL" -> execTransformSqlCapture(s, config);
            case "TRANSFORM_UDF" -> execUdfTransformCapture(s, node, config);
            case "JOIN" -> execJoinCapture(s, node, config);
            case "SINK" -> execSinkCapture(s, node, config);
            case "SINK_MINIO" -> execSinkMinioCapture(s, node, config);
            case "OUTPUT_OBJECT" -> execOutputObjectCapture(s, config);
            default -> throw new ValidationException("type", "不支持的节点类型: " + type);
        };
    }

    private NodeResult execSourceJdbcCapture(Session s, PipelineNode node, Map<String, Object> config) throws Exception {
        String sql = (String) config.get("sql");
        if (sql == null || sql.isEmpty()) {
            throw new ValidationException("sql", "SOURCE_JDBC: sql 必填");
        }
        Object dsIdObj = config.get("datasourceId");
        if (dsIdObj == null || dsIdObj.toString().isEmpty()) {
            throw new ValidationException("datasourceId", "SOURCE_JDBC: datasourceId 必填");
        }
        String datasourceId = dsIdObj.toString();

        DataSourceEntity ds = dataSourceService.getById(datasourceId);
        if (ds == null) {
            throw new NotFoundException("DataSource not found: " + datasourceId);
        }
        String connectionConfig = ds.getConnectionConfig();
        if (connectionConfig == null || connectionConfig.isEmpty()) {
            throw new BusinessException("数据源无连接配置: " + datasourceId);
        }
        Connector connector = connectorFactory.getConnector("JDBC");
        if (!(connector instanceof JdbcConnector jdbcConnector)) {
            throw new BusinessException("Expected JdbcConnector but got: " + connector.getClass().getName());
        }
        int fetchSize = toInt(config.get("fetchSize"), 1000);
        log.info("DEBUG SOURCE_JDBC: datasourceId={}, fetchSize={}", datasourceId, fetchSize);

        List<Map<String, Object>> rows = jdbcConnector.executeSql(connectionConfig, sql, fetchSize);
        // 下游 JOIN/SINK/SINK_MINIO 消费上游产出行（与主执行器 nodeResults 语义一致）
        ensureNodeResults(s).put(node.getNodeId(), rows);
        long affected = extractAffectedRows(rows);
        return buildRowsResult(rows, affected);
    }

    private NodeResult execSourceCsvCapture(Session s, PipelineNode node, Map<String, Object> config) throws Exception {
        String filePath = (String) config.get("filePath");
        if (filePath == null || filePath.isEmpty()) {
            throw new ValidationException("filePath", "SOURCE_CSV: filePath 必填");
        }
        Connector connector = connectorFactory.getConnector("SOURCE_CSV");
        if (!(connector instanceof CsvConnector csvConnector)) {
            throw new BusinessException("Expected CsvConnector but got: " + connector.getClass().getName());
        }
        String connectionConfig = buildCsvConnectionConfig(config);
        int fetchSize = toInt(config.get("fetchSize"), 0);
        log.info("DEBUG SOURCE_CSV: filePath={}", filePath);
        List<Map<String, Object>> rows = csvConnector.readRows(connectionConfig, fetchSize);
        ensureNodeResults(s).put(node.getNodeId(), rows);
        return buildRowsResult(rows, rows.size());
    }

    private NodeResult execSourceRestCapture(Session s, PipelineNode node, Map<String, Object> config) throws Exception {
        String url = (String) config.get("url");
        if (url == null || url.isEmpty()) {
            throw new ValidationException("url", "SOURCE_REST: url 必填");
        }
        Connector connector = connectorFactory.getConnector("SOURCE_REST");
        if (!(connector instanceof RestApiConnector restConnector)) {
            throw new BusinessException("Expected RestApiConnector but got: " + connector.getClass().getName());
        }
        log.info("DEBUG SOURCE_REST: url={}, method={}", url, config.getOrDefault("method", "GET"));
        List<Map<String, Object>> rows = restConnector.fetchData(config);
        ensureNodeResults(s).put(node.getNodeId(), rows);
        return buildRowsResult(rows, rows.size());
    }

    /**
     * SOURCE_MINIO 调试执行 — 复用主执行器近源层读取逻辑（MinIO 对象读取 + 近源层 key 组装）。
     */
    private NodeResult execSourceMinioCapture(Session s, PipelineNode node, Map<String, Object> config) throws Exception {
        long start = System.currentTimeMillis();
        Map<String, List<Map<String, Object>>> results = ensureNodeResults(s);
        long rows = pipelineExecutionService.executeSourceMinioPublic(node, config, results,
                PipelineExecutionService.resolveLakeSourcePublic(s.nodes));
        List<Map<String, Object>> produced = results.getOrDefault(node.getNodeId(), Collections.emptyList());
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("objectName", config.get("objectName"));
        snapshot.put("zone", config.getOrDefault("zone", "STRUCTURED"));
        snapshot.put("rowCount", rows);
        List<String> cols = produced.isEmpty() ? Collections.emptyList() : new ArrayList<>(produced.get(0).keySet());
        return new NodeResult(rows, cols, cols, snapshot, nowIso(), System.currentTimeMillis() - start,
                copySample(produced));
    }

    private NodeResult execTransformSqlCapture(Session s, Map<String, Object> config) {
        String sql = (String) config.get("sql");
        if (sql == null || sql.isEmpty()) {
            throw new ValidationException("sql", "TRANSFORM_SQL: sql 必填");
        }
        long start = System.currentTimeMillis();
        String trimmed = sql.trim();
        // 按 SQL 首 token 白名单分流（对齐 PipelineExecutionService.executeTransformSql）：
        // SELECT/SHOW/DESCRIBE/WITH → queryForList（查询）；其余 DML/DDL → update
        String firstToken = trimmed.split("\\s+")[0].toUpperCase(Locale.ROOT);
        long updated;
        Map<String, Object> snapshot = new LinkedHashMap<>();
        switch (firstToken) {
            case "SELECT", "SHOW", "DESCRIBE", "WITH" -> {
                List<Map<String, Object>> rows = jdbc.queryForList(trimmed);
                updated = rows.size();
                snapshot.put("queryRows", updated);
                snapshot.put("rowsProcessed", s.totalRows + updated);
                snapshot.put("sqlPreview", sql.length() > 120 ? sql.substring(0, 120) + "..." : sql);
            }
            default -> {
                updated = jdbc.update(trimmed);
                snapshot.put("updatedRows", (long) updated);
                snapshot.put("rowsProcessed", s.totalRows + updated);
                snapshot.put("sqlPreview", sql.length() > 120 ? sql.substring(0, 120) + "..." : sql);
            }
        }
        long nodeMs = System.currentTimeMillis() - start;
        List<String> colsOut;
        if ("SELECT".equals(firstToken) || "WITH".equals(firstToken)
                || "SHOW".equals(firstToken) || "DESCRIBE".equals(firstToken)) {
            colsOut = updated > 0 ? List.of("transformed") : Collections.emptyList();
        } else {
            colsOut = List.of("updatedRows");
        }
        return new NodeResult(updated, null, colsOut, snapshot, nowIso(), nodeMs, null);
    }

    @SuppressWarnings("unchecked")
    private NodeResult execOutputObjectCapture(Session s, Map<String, Object> config) {
        String targetTable = (String) config.get("targetTable");
        if (targetTable == null || targetTable.isEmpty()) {
            throw new ValidationException("targetTable", "OUTPUT_OBJECT: targetTable 必填");
        }
        long start = System.currentTimeMillis();
        List<Map<String, Object>> rows = (List<Map<String, Object>>) config.get("rows");
        if (rows == null || rows.isEmpty()) {
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("rowsProcessed", s.totalRows);
            snapshot.put("skipped", "no data rows");
            return new NodeResult(0L, null, null, snapshot, nowIso(), System.currentTimeMillis() - start, null);
        }
        long inserted = 0;
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
            inserted++;
        }
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("insertedRows", inserted);
        snapshot.put("targetTable", targetTable);
        snapshot.put("rowsProcessed", s.totalRows + inserted);
        List<String> cols = new ArrayList<>(rows.get(0).keySet());
        return new NodeResult(inserted, cols, cols, snapshot, nowIso(), System.currentTimeMillis() - start, copySample(rows));
    }

    // ==================== TRANSFORM_UDF / JOIN / SINK（调试链对齐执行器） ====================

    /**
     * TRANSFORM_UDF 调试执行 — 复用 PipelineExecutionService 的 runUdfTransform 逻辑。
     * <p>
     * UDF 执行成功后将输出行写入 Session.nodeResults，供下游 JOIN/SINK 消费。
     */
    private NodeResult execUdfTransformCapture(Session s, PipelineNode node, Map<String, Object> config) throws Exception {
        long start = System.currentTimeMillis();
        Map<String, List<Map<String, Object>>> results = ensureNodeResults(s);
        List<Map<String, Object>> out = PipelineExecutionService.runUdfTransformPublic(node, config, results, udfService);
        results.put(node.getNodeId(), out);
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("udfId", config.get("udfId"));
        snapshot.put("rowsOut", (long) out.size());
        snapshot.put("rowsProcessed", s.totalRows + out.size());
        long nodeMs = System.currentTimeMillis() - start;
        List<String> cols = out.isEmpty() ? Collections.emptyList() : new ArrayList<>(out.get(0).keySet());
        return new NodeResult((long) out.size(), cols, cols, snapshot, nowIso(), nodeMs, copySample(out));
    }

    /**
     * JOIN 调试执行 — 复用 PipelineExecutionService 的 joinFrames + memoryJoin 逻辑。
     */
    private NodeResult execJoinCapture(Session s, PipelineNode node, Map<String, Object> config) throws Exception {
        long start = System.currentTimeMillis();
        Map<String, List<Map<String, Object>>> results = ensureNodeResults(s);
        List<Map<String, Object>> merged = PipelineExecutionService.joinFramesPublic(node, config, results);
        results.put(node.getNodeId(), merged);
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("joinKeys", config.getOrDefault("joinKeys", config.get("on")));
        snapshot.put("joinType", config.getOrDefault("joinType", "inner"));
        snapshot.put("rowsOut", (long) merged.size());
        snapshot.put("rowsProcessed", s.totalRows + merged.size());
        long nodeMs = System.currentTimeMillis() - start;
        List<String> cols = merged.isEmpty() ? Collections.emptyList() : new ArrayList<>(merged.get(0).keySet());
        return new NodeResult((long) merged.size(), cols, cols, snapshot, nowIso(), nodeMs, copySample(merged));
    }

    /**
     * SINK 调试执行 — 复用 PipelineExecutionService 的 executeSink 逻辑。
     */
    private NodeResult execSinkCapture(Session s, PipelineNode node, Map<String, Object> config) throws Exception {
        long start = System.currentTimeMillis();
        Map<String, List<Map<String, Object>>> results = ensureNodeResults(s);
        long written = PipelineExecutionService.executeSinkPublic(node, config, results,
                connectorFactory, dataSourceService);
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("table", configFirst(config, "table", "targetTable"));
        snapshot.put("mode", configFirst(config, "mode"));
        snapshot.put("writtenRows", written);
        snapshot.put("rowsProcessed", s.totalRows + written);
        long nodeMs = System.currentTimeMillis() - start;
        return new NodeResult(written, null, null, snapshot, nowIso(), nodeMs, null);
    }

    /**
     * SINK_MINIO 调试执行 — 复用主执行器的近源层写入逻辑（对象 key 组装 + MinIO 上传 + 近源层登记）。
     */
    private NodeResult execSinkMinioCapture(Session s, PipelineNode node, Map<String, Object> config) throws Exception {
        long start = System.currentTimeMillis();
        Map<String, List<Map<String, Object>>> results = ensureNodeResults(s);
        long written = pipelineExecutionService.executeSinkMinioPublic(node, config, results,
                PipelineExecutionService.resolveLakeSourcePublic(s.nodes));
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("table", configFirst(config, "table", "objectPrefix"));
        snapshot.put("objectName", config.get("objectName"));
        snapshot.put("format", config.get("format"));
        snapshot.put("writtenRows", written);
        snapshot.put("rowsProcessed", s.totalRows + written);
        long nodeMs = System.currentTimeMillis() - start;
        return new NodeResult(written, null, null, snapshot, nowIso(), nodeMs, null);
    }

    /** 确保 Session 的 nodeResults 已初始化。 */
    private Map<String, List<Map<String, Object>>> ensureNodeResults(Session s) {
        if (s.nodeResults == null) {
            s.nodeResults = new LinkedHashMap<>();
        }
        return s.nodeResults;
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

    /** 从 rows 构造带 sample/schema 的 NodeResult（Source 节点用）。 */
    private NodeResult buildRowsResult(List<Map<String, Object>> rows, long rowCount) {
        List<Map<String, Object>> source = rows == null ? Collections.emptyList() : rows;
        List<String> cols = source.isEmpty()
                ? Collections.emptyList() : new ArrayList<>(source.get(0).keySet());
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("rowCount", rowCount);
        List<Map<String, Object>> sample = copySample(source);
        return new NodeResult(rowCount, cols, cols, snapshot, nowIso(), 0L, sample);
    }

    /** 提取样本数据（前 SAMPLE_LIMIT 行）。 */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> copySample(List<? extends Map<String, Object>> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyList();
        }
        List<Map<String, Object>> out = new ArrayList<>(Math.min(source.size(), SAMPLE_LIMIT));
        out.addAll((List<Map<String, Object>>) (List<?>) source.subList(0, Math.min(source.size(), SAMPLE_LIMIT)));
        return out;
    }

    // ==================== 步骤记录 / 执行落库 / 日志 ====================

    private void recordStep(Session s, PipelineNode node, String status,
                            NodeResult res, long elapsedMs, String error) {
        NodeStepVO vo = new NodeStepVO();
        vo.setNodeId(node.getNodeId());
        vo.setType(node.getType());
        vo.setStatus(status);
        vo.setRowsProcessed(res.rows());
        vo.setElapsedMs(res.elapsedMs());
        vo.setColumnsIn(res.columnsIn());
        vo.setColumnsOut(res.columnsOut());
        vo.setSampleRows(res.sampleRows());
        vo.setSnapshot(res.snapshot());
        vo.setErrorMsg(error);
        vo.setFinishedAt(res.finishedAt() != null ? res.finishedAt() : nowIso());
        s.steps.add(vo);
    }

    private NodeStepVO findStep(Session s, String nodeId) {
        NodeStepVO best = null;
        for (NodeStepVO st : s.steps) {
            if (nodeId != null && nodeId.equals(st.getNodeId())) {
                best = st;
            }
        }
        if (best != null) {
            return best;
        }
        for (PipelineNode n : s.nodes) {
            if (nodeId != null && nodeId.equals(n.getNodeId())) {
                NodeStepVO qvo = new NodeStepVO();
                qvo.setNodeId(nodeId);
                qvo.setType(n.getType());
                qvo.setStatus("QUEUED");
                qvo.setRowsProcessed(0L);
                qvo.setElapsedMs(0L);
                return qvo;
            }
        }
        return null;
    }

    /** 将 Session 状态序列化为 VO 步骤列表（按拓扑序，含 QUEUED 占位）。 */
    private List<NodeStepVO> toStepVOs(Session s) {
        Map<String, NodeStepVO> byId = new LinkedHashMap<>();
        for (NodeStepVO vo : s.steps) {
            byId.put(vo.getNodeId(), vo);
        }
        List<NodeStepVO> ordered = new ArrayList<>();
        int idx = 0;
        for (PipelineNode n : s.nodes) {
            NodeStepVO existing = byId.get(n.getNodeId());
            if (existing != null) {
                ordered.add(existing);
            } else {
                NodeStepVO qvo = new NodeStepVO();
                qvo.setNodeId(n.getNodeId());
                qvo.setType(n.getType());
                qvo.setStatus(s.queueIndex == idx && (s.state == State.AWAITING || s.state == State.BROKEN)
                        ? "AWAITING" : "QUEUED");
                qvo.setRowsProcessed(0L);
                qvo.setElapsedMs(0L);
                ordered.add(qvo);
            }
            idx++;
        }
        return ordered;
    }

    /**
     * 调试会话执行落库。
     * <p>
     * 首次执行时 INSERT；终态（COMPLETED/FAILED/STOPPED）UPDATE 回写。
     */
    private void persistRun(Session s) {
        if (s.executionId == null) {
            PipelineExecution exec = new PipelineExecution();
            exec.setId(UUID.randomUUID().toString().replace("-", ""));
            // ad-hoc 调试会话无真实 definitionId；用 id 兜底（pipeline_id 为 NOT NULL 约束）
            exec.setDefinitionId(s.definitionId != null ? s.definitionId : ("adhoc-" + s.id));
            exec.setStatus("RUNNING");
            repository.insertExecution(exec);
            s.executionId = exec.getId() != null ? exec.getId() : UUID.randomUUID().toString().replace("-", "");
            s.startedAt = nowIso();
            execLogs.put(s.executionId, new ExecutionLog(s.executionId, Instant.now().toEpochMilli()));
            logNode(s, null, "INFO", "Pipeline 调试执行已创建: executionId=" + s.executionId);
        }
        if (isTerminal(s.state)) {
            String dbStatus = s.state == State.COMPLETED ? "COMPLETED"
                    : s.state == State.FAILED ? "FAILED"
                    : s.state == State.STOPPED ? "CANCELLED" : "RUNNING";
            try {
                repository.updateExecutionStatus(s.executionId, dbStatus, s.error, s.totalRows);
            } catch (Exception e) {
                log.warn("更新执行状态失败: executionId={}, error={}", s.executionId, e.getMessage());
            }
        }
    }

    // ==================== 节点级执行日志 ====================

    /** 节点级 Log Record — (seq, nodeId, level, message, atMs) 增量推送。 */
    public record NodeLog(long seq, String nodeId, String level, String message, long atMs) {}

    private void logNode(Session s, String nodeId, String level, String message, Throwable cause) {
        String msg = message;
        if (cause != null) {
            msg = message + "\n" + stackTrace(cause);
        }
        if ("ERROR".equals(level)) {
            log.error("Pipeline debug node [nodeId={}]: {}", nodeId, message, cause);
        } else if ("WARNING".equals(level)) {
            log.warn("Pipeline debug [nodeId={}]: {}", nodeId, message);
        } else {
            log.info("Pipeline debug [nodeId={}]: {}", nodeId, message);
        }
        long seq = logSeq.incrementAndGet();
        long atMs = System.currentTimeMillis();
        // 同步到 execLogs（外部按 executionId 拉取）
        if (s.executionId != null) {
            ExecutionLog el = execLogs.get(s.executionId);
            if (el != null) {
                el.lines.add(new NodeLog(seq, nodeId, level, msg, atMs));
                if (el.lines.size() > MAX_NODE_LOGS) {
                    el.lines.remove(0);
                }
            }
        } else {
            // 会话刚创建还未 persistRun 时，临时挂在 byIdNoExec 兜底
            s.logs.add(new NodeLog(seq, nodeId, level, msg, atMs));
            if (s.logs.size() > MAX_NODE_LOGS) {
                s.logs.remove(0);
            }
        }
    }

    private void logNode(Session s, String nodeId, String level, String message) {
        logNode(s, nodeId, level, message, null);
    }

    /** 按 executionId 取日志（GET /executions/{id}/logs 使用）。 */
    public List<NodeLog> getLogs(String executionId) {
        ExecutionLog el = execLogs.get(executionId);
        return el == null ? Collections.emptyList() : unmodifiableList(el.lines);
    }

    /** 按 sessionId 取会话日志（与 state 同生命周期，仅会话失效时丢失）。 */
    public List<NodeLog> getSessionLogs(String sessionId) {
        Session s = requireSession(sessionId);
        return unmodifiableList(s.logs);
    }

    private static <T> List<T> unmodifiableList(List<T> in) {
        List<T> cp = new ArrayList<>(in);
        return Collections.unmodifiableList(cp);
    }

    // ==================== 执行历史（ecos_pipeline_execution 同源列表） ====================

    /**
     * 调试会话执行历史 — 与 PipelineController#listExecutions 同一数据源（ecos_pipeline_execution）。
     * 调试会话执行时 persistRun INSERT 一条记录，作为 execLogs 索引。
     */
    public List<PipelineExecutionVO> listExecutions(String definitionId, int page, int size) {
        List<PipelineExecutionVO> result = new ArrayList<>();
        int offset = (Math.max(page, 1) - 1) * Math.max(size, 1);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, pipeline_id, status, started_at, finished_at, error_message, rows_processed " +
                        "FROM ecos_pipeline_execution WHERE pipeline_id = ? " +
                        "ORDER BY started_at DESC LIMIT ? OFFSET ?",
                definitionId, size, offset);
        for (Map<String, Object> r : rows) {
            PipelineExecutionVO vo = new PipelineExecutionVO();
            vo.setId((String) r.get("id"));
            vo.setDefinitionId((String) r.get("pipeline_id"));
            vo.setStatus((String) r.get("status"));
            long started = toEpochMillis(r.get("started_at"));
            long finished = toEpochMillis(r.get("finished_at"));
            vo.setStartedAt(started == 0L ? null : started);
            vo.setCompletedAt(finished == 0L ? null : finished);
            vo.setErrorMessage((String) r.get("error_message"));
            vo.setRowsProcessed(toLong(r.get("rows_processed")));
            result.add(vo);
        }
        return result;
    }

    // ==================== 数据预览 ====================

    /**
     * 节点数据预览 — 列名（输入/输出）+ 前 100 行样本。
     * columnsIn / columnsOut / sampleRows 由 NodeResult 留存，未执行节点返回空。
     */
    public PipelineStepStatusVO getNodePreview(String sessionId, String nodeId) {
        Session s = requireSession(sessionId);
        NodeStepVO step = findStep(s, nodeId);
        if (step == null) {
            throw new NotFoundException("节点不存在: " + nodeId);
        }
        PipelineStepStatusVO out = new PipelineStepStatusVO();
        out.setNodeId(step.getNodeId());
        out.setType(step.getType());
        out.setStatus(step.getStatus());
        out.setColumnsIn(step.getColumnsIn());
        out.setColumnsOut(step.getColumnsOut());
        out.setSampleRows(step.getSampleRows());
        out.setSnapshot(step.getSnapshot());
        out.setRowsInput(0L);
        out.setRowsOutput(step.getRowsProcessed());
        out.setErrorMsg(step.getErrorMsg());
        out.setFinishedAt(step.getFinishedAt());
        out.setElapsedMs(step.getElapsedMs());
        return out;
    }

    // ==================== 会话过期清理（@Scheduled，60s 一次） ====================

    @Scheduled(fixedDelay = 60_000L)
    public void cleanupSessions() {
        long now = System.currentTimeMillis();
        for (Map.Entry<String, Session> e : sessions.entrySet()) {
            Session s = e.getValue();
            long lastActive = s.lastActiveAtMs == 0 ? s.createdAtMs : s.lastActiveAtMs;
            if (now - lastActive > SESSION_TTL_MS) {
                sessions.remove(e.getKey());
                log.info("Pipeline debug session expired: id={}", e.getKey());
            }
        }
        for (Map.Entry<String, ExecutionLog> e : execLogs.entrySet()) {
            if (now - e.getValue().createdAtMs > EXEC_LOG_TTL_MS) {
                execLogs.remove(e.getKey());
            }
        }
    }

    // ==================== 节点执行内部模型 ====================

    /** 节点单次执行的完整结果（成功/失败都用，sampleRows 失败时 null）。 */
    private record NodeResult(long rows,
            List<String> columnsIn,
            List<String> columnsOut,
            Map<String, Object> snapshot,
            String finishedAt,
            long elapsedMs,
            List<Map<String, Object>> sampleRows) {}

    private static class BreakpointSpec {
        final String nodeId;
        final String condition;

        BreakpointSpec(String nodeId, String condition) {
            this.nodeId = nodeId;
            this.condition = condition;
        }
    }

    private static class HitRecord {
        final String nodeId;
        final String condition;
        final String at;
        final Map<String, Object> snapshot;

        HitRecord(String nodeId, String condition, String at, Map<String, Object> snapshot) {
            this.nodeId = nodeId;
            this.condition = condition;
            this.at = at;
            this.snapshot = snapshot == null ? new LinkedHashMap<>() : new LinkedHashMap<>(snapshot);
        }
    }

    private static class Session {
        final String id;
        final String definitionId;
        final String name;
        final List<PipelineNode> nodes; // 已排序
        final Map<String, BreakpointSpec> breakpoints;
        final Object lock = new Object();
        final long createdAtMs = System.currentTimeMillis();
        final String createdAt = Instant.now().toString();

        State state = State.CREATED;
        int queueIndex = 0;
        int completedNodes = 0;
        long totalRows;
        String executionId;
        String startedAt;
        String finishedAt;
        String currentNodeId;
        String currentStepStatus;
        Map<String, Object> variableSnapshot;
        String error;
        final List<HitRecord> hitRecords = new CopyOnWriteArrayList<>();
        final List<NodeStepVO> steps = new CopyOnWriteArrayList<>();
        final List<NodeLog> logs = new CopyOnWriteArrayList<>();
        long lastActiveAtMs = createdAtMs;
        /** 节点执行结果缓存（nodeId → 行），TRANSFORM_UDF/JOIN/SINK 消费 */
        Map<String, List<Map<String, Object>>> nodeResults;

        Session(String id, String definitionId, String name,
                List<PipelineNode> nodes, Map<String, BreakpointSpec> breakpoints) {
            this.id = id;
            this.definitionId = definitionId;
            this.name = name;
            this.nodes = nodes;
            this.breakpoints = breakpoints;
        }
    }

    private static class ExecutionLog {
        final String executionId;
        final long createdAtMs;
        final List<NodeLog> lines = new CopyOnWriteArrayList<>();

        ExecutionLog(String executionId, long createdAtMs) {
            this.executionId = executionId;
            this.createdAtMs = createdAtMs;
        }
    }

    // ==================== 工具方法 ====================

    private boolean hasBreakpoint(Session s, String nodeId) {
        return s.breakpoints.get(nodeId) != null;
    }

    private boolean isTerminal(State st) {
        return st == State.COMPLETED || st == State.FAILED || st == State.STOPPED;
    }

    /** Kahn 拓扑排序 — 与 PipelineExecutionService.topologicalSort 语义一致。 */
    private List<PipelineNode> topologicalSort(List<PipelineNode> nodes) {
        // 第一次遍历：生成 nodeMap 全集，保证所有 nodeId 已注册
        Map<String, PipelineNode> nodeMap = new LinkedHashMap<>();
        for (PipelineNode node : nodes) {
            nodeMap.put(node.getNodeId(), node);
        }

        // 第二次遍历：构建入度表和邻接表（此时 nodeMap 已完整，所有 from/to 已注册）
        Map<String, Integer> inDegree = new LinkedHashMap<>();
        for (String nodeId : nodeMap.keySet()) {
            inDegree.put(nodeId, 0);
        }
        Map<String, List<String>> children = new LinkedHashMap<>();
        for (PipelineNode node : nodes) {
            List<String> deps = parseDependsOn(node.getDependsOn());
            for (String dep : deps) {
                children.computeIfAbsent(dep, k -> new ArrayList<>()).add(node.getNodeId());
                inDegree.merge(node.getNodeId(), 1, Integer::sum);
            }
        }

        Deque<String> queue = new ArrayDeque<>();
        for (PipelineNode node : nodes) {
            if (inDegree.getOrDefault(node.getNodeId(), 0) == 0) {
                queue.add(node.getNodeId());
            }
        }

        List<PipelineNode> result = new ArrayList<>();
        while (!queue.isEmpty()) {
            String current = queue.poll();
            PipelineNode n = nodeMap.get(current);
            if (n == null) {
                continue;
            }
            result.add(n);
            for (String child : children.getOrDefault(current, Collections.emptyList())) {
                int newDegree = inDegree.merge(child, -1, Integer::sum);
                if (newDegree == 0) {
                    queue.add(child);
                }
            }
        }

        if (result.size() != nodes.size()) {
            Set<String> remaining = new LinkedHashSet<>();
            for (PipelineNode n : nodes) {
                remaining.add(n.getNodeId());
            }
            result.forEach(r -> remaining.remove(r.getNodeId()));
            throw new BusinessException("Pipeline DAG 存在循环依赖或引用未定义节点，未排序: " + remaining);
        }
        return result;
    }

    private Map<String, Object> parseConfig(String configStr) {
        try {
            if (configStr == null || configStr.isBlank()) {
                return Collections.emptyMap();
            }
            return mapper.readValue(configStr, new TypeReference<LinkedHashMap<String, Object>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse config JSON: {}", configStr, e);
            return Collections.emptyMap();
        }
    }

    private List<String> parseDependsOn(String dependsOn) {
        if (dependsOn == null || dependsOn.isBlank() || "[]".equals(dependsOn)) {
            return Collections.emptyList();
        }
        try {
            List<String> out = mapper.readValue(dependsOn, new TypeReference<List<String>>() {});
            return out;
        } catch (Exception e) {
            log.warn("Failed to parse depends_on: {}", dependsOn, e);
            return Collections.emptyList();
        }
    }

    private String buildCsvConnectionConfig(Map<String, Object> config) {
        try {
            Map<String, Object> cc = new LinkedHashMap<>();
            cc.put("filePath", config.get("filePath"));
            cc.put("delimiter", config.getOrDefault("delimiter", ","));
            Object header = config.get("header");
            if (header == null) {
                header = config.get("hasHeader");
            }
            cc.put("hasHeader", header != null ? header : true);
            cc.put("encoding", config.getOrDefault("encoding", "UTF-8"));
            return mapper.writeValueAsString(cc);
        } catch (Exception e) {
            throw new BusinessException("构建 CSV 连接配置失败: " + e.getMessage());
        }
    }

    private String toJson(Object obj) {
        try {
            return mapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }

    private long extractAffectedRows(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            return 0L;
        }
        Map<String, Object> first = rows.get(0);
        if (first != null && first.containsKey("affectedRows")) {
            return toLong(first.get("affectedRows"));
        }
        return rows.size();
    }

    private int toInt(Object val, int def) {
        if (val == null) {
            return def;
        }
        if (val instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(val.toString());
        } catch (Exception e) {
            return def;
        }
    }

    private long toLong(Object val) {
        if (val == null) {
            return 0L;
        }
        if (val instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(val.toString());
        } catch (Exception e) {
            return 0L;
        }
    }

    private long toEpochMillis(Object o) {
        if (o instanceof java.sql.Timestamp ts) {
            return ts.getTime();
        }
        if (o instanceof LocalDateTime ldt) {
            return ldt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        }
        return 0L;
    }

    private String stackTrace(Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }

    private String nowIso() {
        return Instant.now().toString();
    }

    private void touch(Session s) {
        s.lastActiveAtMs = System.currentTimeMillis();
    }

    private Session requireSession(String sessionId) {
        Session s = sessions.get(sessionId);
        if (s == null) {
            throw new NotFoundException("调试会话不存在或已过期: " + sessionId);
        }
        return s;
    }

    // ==================== VO 序列化 ====================

    private PipelineDebugSessionVO toSessionVO(Session s) {
        PipelineDebugSessionVO vo = new PipelineDebugSessionVO();
        vo.setSessionId(s.id);
        vo.setDefinitionId(s.definitionId);
        vo.setPipelineName(s.name);
        vo.setState(s.state.name().toLowerCase(Locale.ROOT));
        vo.setCreatedAt(s.createdAt);
        vo.setStartedAt(s.startedAt);
        vo.setFinishedAt(s.finishedAt);
        vo.setCurrentNodeId(s.currentNodeId);
        vo.setCurrentStepStatus(s.currentStepStatus);
        vo.setRowsProcessed(s.totalRows);
        vo.setExecutionId(s.executionId);
        vo.setTotalNodes(s.nodes.size());
        vo.setCompletedNodes(s.completedNodes);
        vo.setError(s.error);
        if (s.variableSnapshot != null) {
            vo.setVariableSnapshot(new LinkedHashMap<>(s.variableSnapshot));
        } else {
            Map<String, Object> defaultSnap = new LinkedHashMap<>();
            defaultSnap.put("rowsProcessed", s.totalRows);
            defaultSnap.put("completedNodes", s.completedNodes);
            defaultSnap.put("totalNodes", s.nodes.size());
            vo.setVariableSnapshot(defaultSnap);
        }
        List<PipelineDebugSessionVO.BreakpointVO> bvos = new ArrayList<>(s.breakpoints.size());
        for (BreakpointSpec bp : s.breakpoints.values()) {
            PipelineDebugSessionVO.BreakpointVO bvo = new PipelineDebugSessionVO.BreakpointVO();
            bvo.setNodeId(bp.nodeId);
            bvo.setCondition(bp.condition);
            bvo.setEnabled(true);
            bvos.add(bvo);
        }
        vo.setBreakpoints(bvos);
        vo.setSteps(toStepVOs(s));
        List<PipelineDebugSessionVO.HitRecordVO> hitVos = new ArrayList<>(s.hitRecords.size());
        for (HitRecord hr : s.hitRecords) {
            PipelineDebugSessionVO.HitRecordVO hv = new PipelineDebugSessionVO.HitRecordVO();
            hv.setNodeId(hr.nodeId);
            hv.setCondition(hr.condition);
            hv.setAt(hr.at);
            hv.setSnapshot(hr.snapshot);
            hitVos.add(hv);
        }
        vo.setHitRecords(hitVos);
        return vo;
    }
}
