package com.chinacreator.gzcm.engine.data.scheduler;

import com.chinacreator.gzcm.engine.data.dao.SchemaDetectionDao;
import com.chinacreator.gzcm.runtime.core.task.callback.ITaskStatusCallback;
import com.chinacreator.gzcm.runtime.core.task.executor.ITaskExecutor;
import com.chinacreator.gzcm.runtime.core.task.model.TaskDescription;
import com.chinacreator.gzcm.runtime.core.task.model.TaskExecutionPlan;
import com.chinacreator.gzcm.runtime.core.task.model.TaskStatus;
import com.chinacreator.gzcm.runtime.core.task.parser.ITaskParser;
import com.chinacreator.gzcm.runtime.core.task.scheduling.TaskSchedulerService;
import com.chinacreator.gzcm.runtime.core.task.service.ITaskManagementService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * 每小时对比 information_schema.columns 快照，检测 Schema 变更。
 * <p>
 * 自动维护 schema_snapshots 和 schema_changes 两张表。
 * 单一调度：runtime-task 注册 + cron 触发（H8-T3 收口，已移除 Spring @Scheduled 双轨）。
 * </p>
 *
 * <p>B4a 收口（R2-B4a）：SQL 下沉 {@link SchemaDetectionDao}（..dao..），
 * 本类不再继承 JdbcTemplateDaoSupport / 私持 JdbcTemplate；
 * diff / 哈希 / 列解析逻辑保持原位。</p>
 */
@Component
public class SchemaChangeDetector {

    private static final Logger log = LoggerFactory.getLogger(SchemaChangeDetector.class);
    private static final String CRON_HOURLY = "0 0 * * * ?";
    private static final String TASK_TYPE = "SCHEMA_CHANGE_DETECT";

    private final TaskSchedulerService taskScheduler;
    private final ITaskManagementService taskManagementService;
    /** B4a 收口：information_schema 扫描 + schema_snapshots/schema_changes 读写 */
    private final SchemaDetectionDao dao;

    public SchemaChangeDetector(TaskSchedulerService taskScheduler,
                                ITaskManagementService taskManagementService,
                                SchemaDetectionDao dao) {
        this.taskScheduler = taskScheduler;
        this.taskManagementService = taskManagementService;
        this.dao = dao;
    }

    @PostConstruct
    public void init() {
        // H8-T1: schema_snapshots / schema_changes 建表 DDL 收编至 db/migration（V162），运行时不再内嵌 DDL。
        taskManagementService.registerParser(TASK_TYPE, new SchemaDetectParser());
        taskManagementService.registerExecutor(TASK_TYPE, new SchemaDetectExecutor());

        TaskDescription desc = new TaskDescription();
        desc.setTaskId("schema-change-detector");
        desc.setTaskName("Schema变更检测");
        desc.setTaskType(TASK_TYPE);
        desc.setDescription("每小时对比information_schema.columns快照，检测新增/删除/类型变更");
        desc.setAsync(true);
        desc.setTimeout(300_000L);
        desc.setRetryCount(0);
        desc.setParameters(Map.of("cron", CRON_HOURLY));
        desc.setTags(List.of("data-engine", "schema"));
        taskScheduler.scheduleTask(desc, CRON_HOURLY);
        log.info("SchemaChangeDetector registered with runtime-task: cron={}", CRON_HOURLY);
    }

    // ── 调度执行 ─────────────────────────────────────

    /**
     * 每小时执行一次 schema 变更检测。
     */
    public void scheduledDetect() {
        log.debug("SchemaChangeDetector hourly scan starting");
        try {
            DetectionResult result = detect();
            log.info("SchemaChangeDetector done: {} changes in {} tables",
                    result.getChangeCount(), result.getTableCount());
        } catch (Exception e) {
            log.error("SchemaChangeDetector scan failed: {}", e.getMessage(), e);
        }
    }

    // ── 核心检测逻辑 ─────────────────────────────────

    /**
     * 执行一次 schema 变更检测。对比当前列信息与最近快照，发现差异即写入 schema_changes。
     */
    public DetectionResult detect() {
        List<SchemaInfo> current = loadCurrentSchema();
        Map<String, String> currentHashes = new HashMap<>();
        for (SchemaInfo si : current) {
            String key = si.datasourceId + "::" + si.tableName;
            currentHashes.put(key, si.columnHash);
        }

        Map<String, String> lastHashes = loadLatestSnapshots();
        List<SchemaChange> changes = new ArrayList<>();

        for (SchemaInfo si : current) {
            String key = si.datasourceId + "::" + si.tableName;
            String lastHash = lastHashes.get(key);
            if (lastHash == null) {
                // 新发现的表
                changes.add(new SchemaChange(si.datasourceId, si.tableName, "NEW_TABLE",
                        "{\"table\":\"" + si.tableName + "\"}"));
            } else if (!lastHash.equals(si.columnHash)) {
                // 列发生变化，做细粒度对比
                List<SchemaChange> colChanges = diffColumns(si.datasourceId, si.tableName, lastHash, si.columnHash);
                changes.addAll(colChanges);
            }
        }

        // 检查已删除的表
        Set<String> currentKeys = currentHashes.keySet();
        for (Map.Entry<String, String> e : lastHashes.entrySet()) {
            if (!currentKeys.contains(e.getKey())) {
                String[] parts = e.getKey().split("::", 2);
                changes.add(new SchemaChange(parts[0], parts[1], "DROP_TABLE",
                        "{\"table\":\"" + parts[1] + "\"}"));
            }
        }

        // 持久化变化
        if (!changes.isEmpty()) {
            for (SchemaChange sc : changes) {
                insertChange(sc);
            }
            log.info("Detected {} schema change(s)", changes.size());
        }

        // 保存本次快照（仅保存变化的/新增的，避免全量重写）
        for (SchemaInfo si : current) {
            String key = si.datasourceId + "::" + si.tableName;
            String lastHash = lastHashes.get(key);
            if (lastHash == null || !lastHash.equals(si.columnHash)) {
                insertSnapshot(si);
            }
        }

        return new DetectionResult(changes.size(), current.size());
    }

    // ── 列级 diff ────────────────────────────────────

    private List<SchemaChange> diffColumns(String datasourceId, String tableName,
                                            String previousHash, String currentHash) {
        List<ColumnInfo> prevCols = loadColumnsFromSnapshot(datasourceId, tableName, previousHash);
        List<ColumnInfo> currCols = loadCurrentColumns(datasourceId, tableName);

        Map<String, ColumnInfo> prevMap = new HashMap<>();
        for (ColumnInfo c : prevCols) prevMap.put(c.columnName, c);
        Map<String, ColumnInfo> currMap = new HashMap<>();
        for (ColumnInfo c : currCols) currMap.put(c.columnName, c);

        List<SchemaChange> changes = new ArrayList<>();

        for (ColumnInfo c : currCols) {
            ColumnInfo prev = prevMap.get(c.columnName);
            if (prev == null) {
                changes.add(new SchemaChange(datasourceId, tableName, "NEW_COLUMN",
                        "{\"column\":\"" + c.columnName + "\",\"type\":\"" + c.dataType + "\"}"));
            } else if (!c.typeSignature().equals(prev.typeSignature())) {
                changes.add(new SchemaChange(datasourceId, tableName, "TYPE_CHANGE",
                        "{\"column\":\"" + c.columnName + "\",\"oldType\":\"" + prev.typeSignature()
                                + "\",\"newType\":\"" + c.typeSignature() + "\"}"));
            }
        }

        for (ColumnInfo c : prevCols) {
            if (!currMap.containsKey(c.columnName)) {
                changes.add(new SchemaChange(datasourceId, tableName, "DROP_COLUMN",
                        "{\"column\":\"" + c.columnName + "\",\"type\":\"" + c.dataType + "\"}"));
            }
        }

        return changes;
    }

    // ── 查询 information_schema（SQL 在 SchemaDetectionDao）─────────────

    private List<SchemaInfo> loadCurrentSchema() {
        List<Map<String, Object>> rows = dao.scanCurrentSchema();
        List<SchemaInfo> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String ds = (String) row.get("datasource_id");
            String tn = (String) row.get("table_name");
            String colSig = (String) row.get("col_sig");
            String hash = sha256(colSig);
            result.add(new SchemaInfo(ds, tn, hash, colSig));
        }
        return result;
    }

    private List<ColumnInfo> loadCurrentColumns(String datasourceId, String tableName) {
        String schema = datasourceId.contains(".") ? datasourceId.substring(0, datasourceId.indexOf('.')) : datasourceId;
        String table = tableName;

        List<Map<String, Object>> rows = dao.scanCurrentColumns(schema, table);
        List<ColumnInfo> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String cn = (String) row.get("column_name");
            String dt = (String) row.get("data_type");
            String cl = (String) row.get("char_len");
            String nl = (String) row.get("is_nullable");
            result.add(new ColumnInfo(cn, dt, cl, nl));
        }
        return result;
    }

    // ── 快照存取（SQL 在 SchemaDetectionDao）────────────

    private Map<String, String> loadLatestSnapshots() {
        return dao.findLatestSnapshotHashes();
    }

    private List<ColumnInfo> loadColumnsFromSnapshot(String datasourceId, String tableName, String hash) {
        // 从最近一次快照的 col_sig 字段解析列信息
        String colSig = dao.findSnapshotColSig(datasourceId, tableName, hash);
        if (colSig == null || colSig.isBlank()) {
            return Collections.emptyList();
        }

        List<ColumnInfo> result = new ArrayList<>();
        for (String part : colSig.split(",")) {
            String[] fields = part.split("\\|");
            if (fields.length >= 4) {
                result.add(new ColumnInfo(fields[0], fields[1], fields[2], fields[3]));
            }
        }
        return result;
    }

    private void insertSnapshot(SchemaInfo si) {
        // H8-T1: schema_snapshots.col_sig 列由 db/migration（V162）建表时即包含，运行时不再 ALTER。
        dao.insertSnapshot(si.datasourceId, si.tableName, si.columnHash, si.colSig);
    }

    private void insertChange(SchemaChange sc) {
        dao.insertChange(sc.datasourceId, sc.tableName, sc.changeType, sc.detailJson);
    }

    // ── 工具 ─────────────────────────────────────────

    private static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    // ── 内部数据类 ────────────────────────────────────

    static class SchemaInfo {
        final String datasourceId;
        final String tableName;
        final String columnHash;
        final String colSig;

        SchemaInfo(String datasourceId, String tableName, String columnHash, String colSig) {
            this.datasourceId = datasourceId;
            this.tableName = tableName;
            this.columnHash = columnHash;
            this.colSig = colSig;
        }
    }

    static class ColumnInfo {
        final String columnName;
        final String dataType;
        final String charLength;
        final String isNullable;

        ColumnInfo(String columnName, String dataType, String charLength, String isNullable) {
            this.columnName = columnName;
            this.dataType = dataType;
            this.charLength = charLength;
            this.isNullable = isNullable;
        }

        String typeSignature() {
            return dataType + (charLength != null && !charLength.isEmpty() ? "(" + charLength + ")" : "")
                    + (isNullable != null && isNullable.equalsIgnoreCase("NO") ? " NOT NULL" : "");
        }
    }

    static class SchemaChange {
        final String datasourceId;
        final String tableName;
        final String changeType;
        final String detailJson;

        SchemaChange(String datasourceId, String tableName, String changeType, String detailJson) {
            this.datasourceId = datasourceId;
            this.tableName = tableName;
            this.changeType = changeType;
            this.detailJson = detailJson;
        }
    }

    public static class DetectionResult {
        private final int changeCount;
        private final int tableCount;

        public DetectionResult(int changeCount, int tableCount) {
            this.changeCount = changeCount;
            this.tableCount = tableCount;
        }

        public int getChangeCount() { return changeCount; }
        public int getTableCount() { return tableCount; }
    }

    private final class SchemaDetectParser implements ITaskParser {
        @Override
        public TaskExecutionPlan parse(TaskDescription taskDescription) throws TaskParseException {
            validate(taskDescription);
            TaskExecutionPlan plan = new TaskExecutionPlan();
            plan.setTaskId(taskDescription.getTaskId());
            TaskExecutionPlan.ExecutionStep step = new TaskExecutionPlan.ExecutionStep();
            step.setStepId("step-1");
            step.setStepName("Schema 变更检测");
            step.setStepType(TASK_TYPE);
            step.setExecutor(TASK_TYPE);
            step.setConfig(taskDescription.getParameters() == null
                    ? new HashMap<>() : new HashMap<>(taskDescription.getParameters()));
            List<TaskExecutionPlan.ExecutionStep> steps = new ArrayList<>();
            steps.add(step);
            plan.setSteps(steps);
            return plan;
        }

        @Override
        public boolean supports(String taskType) {
            return TASK_TYPE.equalsIgnoreCase(taskType);
        }

        @Override
        public void validate(TaskDescription taskDescription) throws TaskParseException {
            if (taskDescription == null
                    || taskDescription.getTaskId() == null
                    || taskDescription.getTaskId().isEmpty()) {
                throw new TaskParseException("schema change detect task id is required");
            }
            if (!supports(taskDescription.getTaskType())) {
                throw new TaskParseException("unsupported schema change task type: "
                        + taskDescription.getTaskType());
            }
        }
    }

    private final class SchemaDetectExecutor implements ITaskExecutor {
        @Override
        public String execute(TaskExecutionPlan executionPlan, ITaskStatusCallback statusCallback)
                throws TaskExecutionException {
            try {
                scheduledDetect();
                return String.format("{\"taskType\":\"%s\",\"completedAt\":\"%s\"}",
                        TASK_TYPE, java.time.Instant.now().toString());
            } catch (Exception ex) {
                log.error("SchemaChangeDetector runtime-task 执行失败: {}", ex.getMessage(), ex);
                throw new TaskExecutionException("schema change detect failed", ex);
            }
        }

        @Override
        public void cancel(String taskId) { }

        @Override
        public void pause(String taskId) { }

        @Override
        public void resume(String taskId) { }

        @Override
        public TaskStatus getStatus(String taskId) {
            return null;
        }
    }
}
