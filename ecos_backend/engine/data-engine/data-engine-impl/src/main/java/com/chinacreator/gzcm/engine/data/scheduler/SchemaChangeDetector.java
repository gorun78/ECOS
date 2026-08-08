package com.chinacreator.gzcm.engine.data.scheduler;

import com.chinacreator.gzcm.runtime.core.task.model.TaskDescription;
import com.chinacreator.gzcm.runtime.core.task.scheduling.TaskSchedulerService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 每小时对比 information_schema.columns 快照，检测 Schema 变更。
 * <p>
 * 自动维护 schema_snapshots 和 schema_changes 两张表。
 * 双重调度：runtime-task 注册（可见性）+ Spring @Scheduled（实际执行）。
 * </p>
 */
@Component
public class SchemaChangeDetector {

    private static final Logger log = LoggerFactory.getLogger(SchemaChangeDetector.class);
    private static final String CRON_HOURLY = "0 0 * * * ?";

    private final JdbcTemplate jdbc;
    private final TaskSchedulerService taskScheduler;

    public SchemaChangeDetector(JdbcTemplate jdbc, TaskSchedulerService taskScheduler) {
        this.jdbc = jdbc;
        this.taskScheduler = taskScheduler;
    }

    @PostConstruct
    public void init() {
        ensureTables();
        // 注册到 runtime-task 全局调度（满足架构铁律2.3）
        TaskDescription desc = new TaskDescription();
        desc.setTaskId("schema-change-detector");
        desc.setTaskName("Schema变更检测");
        desc.setTaskType("SCHEMA_CHANGE_DETECT");
        desc.setDescription("每小时对比information_schema.columns快照，检测新增/删除/类型变更");
        desc.setAsync(true);
        desc.setTimeout(300_000L);
        desc.setRetryCount(0);
        desc.setParameters(Map.of("cron", CRON_HOURLY));
        desc.setTags(List.of("data-engine", "schema"));
        taskScheduler.scheduleTask(desc, CRON_HOURLY);
        log.info("SchemaChangeDetector registered with runtime-task: cron={}", CRON_HOURLY);
    }

    // ── DDL ──────────────────────────────────────────

    private void ensureTables() {
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS schema_snapshots (
                id          BIGSERIAL PRIMARY KEY,
                datasource_id VARCHAR(128) NOT NULL,
                table_name  VARCHAR(256) NOT NULL,
                column_hash VARCHAR(64)  NOT NULL,
                col_sig     TEXT,
                snapshot_at TIMESTAMP    NOT NULL DEFAULT NOW()
            )
            """);

        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS schema_changes (
                id            BIGSERIAL PRIMARY KEY,
                datasource_id VARCHAR(128) NOT NULL,
                table_name    VARCHAR(256) NOT NULL,
                change_type   VARCHAR(32)  NOT NULL,
                detail_json   TEXT,
                detected_at   TIMESTAMP    NOT NULL DEFAULT NOW(),
                acknowledged  BOOLEAN      NOT NULL DEFAULT FALSE
            )
            """);

        jdbc.execute("""
            CREATE INDEX IF NOT EXISTS idx_schema_changes_ack
                ON schema_changes (acknowledged, detected_at)
            """);
    }

    // ── 调度执行 ─────────────────────────────────────

    /**
     * 每小时执行一次 schema 变更检测。
     */
    @Scheduled(cron = "0 0 * * * ?")
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

    // ── 查询 information_schema ──────────────────────

    private List<SchemaInfo> loadCurrentSchema() {
        String sql = """
            SELECT table_schema || '.' || table_name AS full_table,
                   table_schema AS datasource_id,
                   table_name,
                   STRING_AGG(
                       column_name || '|' || data_type || '|' || COALESCE(character_maximum_length::text,'')
                       || '|' || COALESCE(is_nullable,'YES'),
                       ',' ORDER BY ordinal_position
                   ) AS col_sig
            FROM information_schema.columns
            WHERE table_schema NOT IN ('information_schema', 'pg_catalog')
            GROUP BY table_schema, table_name
            ORDER BY table_schema, table_name
            """;

        return jdbc.query(sql, (rs, rowNum) -> {
            String ds = rs.getString("datasource_id");
            String tn = rs.getString("table_name");
            String colSig = rs.getString("col_sig");
            String hash = sha256(colSig);
            return new SchemaInfo(ds, tn, hash, colSig);
        });
    }

    private List<ColumnInfo> loadCurrentColumns(String datasourceId, String tableName) {
        String sql = """
            SELECT column_name, data_type, COALESCE(character_maximum_length::text,'') AS char_len,
                   COALESCE(is_nullable,'YES') AS is_nullable,
                   ordinal_position
            FROM information_schema.columns
            WHERE table_schema = ? AND table_name = ?
            ORDER BY ordinal_position
            """;

        String schema = datasourceId.contains(".") ? datasourceId.substring(0, datasourceId.indexOf('.')) : datasourceId;
        String table = tableName;

        return jdbc.query(sql, (rs, rowNum) -> {
            String cn = rs.getString("column_name");
            String dt = rs.getString("data_type");
            String cl = rs.getString("char_len");
            String nl = rs.getString("is_nullable");
            return new ColumnInfo(cn, dt, cl, nl);
        }, schema, table);
    }

    // ── 快照存取 ─────────────────────────────────────

    private Map<String, String> loadLatestSnapshots() {
        String sql = """
            SELECT DISTINCT ON (datasource_id, table_name)
                   datasource_id, table_name, column_hash
            FROM schema_snapshots
            ORDER BY datasource_id, table_name, snapshot_at DESC
            """;

        Map<String, String> result = new HashMap<>();
        jdbc.query(sql, (rs) -> {
            String key = rs.getString("datasource_id") + "::" + rs.getString("table_name");
            result.put(key, rs.getString("column_hash"));
        });
        return result;
    }

    private List<ColumnInfo> loadColumnsFromSnapshot(String datasourceId, String tableName, String hash) {
        // 从最近一次快照的 col_sig 字段解析列信息
        String sql = """
            SELECT col_sig FROM schema_snapshots
            WHERE datasource_id = ? AND table_name = ? AND column_hash = ?
            ORDER BY snapshot_at DESC LIMIT 1
            """;

        List<Map<String, Object>> rows = jdbc.queryForList(sql, datasourceId, tableName, hash);
        if (rows.isEmpty()) {
            return Collections.emptyList();
        }
        String colSig = (String) rows.get(0).get("col_sig");
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
        // schema_snapshots 表需要 col_sig 列 — 我们 ALTER 添加（幂等）
        ensureColSigColumn();
        jdbc.update(
            "INSERT INTO schema_snapshots (datasource_id, table_name, column_hash, snapshot_at, col_sig) VALUES (?,?,?,?,?)",
            si.datasourceId, si.tableName, si.columnHash, LocalDateTime.now(), si.colSig
        );
    }

    private void ensureColSigColumn() {
        try {
            jdbc.execute("ALTER TABLE schema_snapshots ADD COLUMN IF NOT EXISTS col_sig TEXT");
        } catch (Exception e) {
            log.debug("col_sig column may already exist: {}", e.getMessage());
        }
    }

    private void insertChange(SchemaChange sc) {
        jdbc.update(
            "INSERT INTO schema_changes (datasource_id, table_name, change_type, detail_json, detected_at, acknowledged) VALUES (?,?,?,?,?,?)",
            sc.datasourceId, sc.tableName, sc.changeType, sc.detailJson, LocalDateTime.now(), false
        );
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
}
