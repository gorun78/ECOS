package com.chinacreator.gzcm.engine.data.metadata;

import com.chinacreator.gzcm.common.data.model.DataResource;
import com.chinacreator.gzcm.runtime.access.git.GitService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 元数据采集 Git 存档 + 版本差异对比。
 * <p>
 * 采集完成后将表清单快照写入 Git 仓库，并生成与上一版本的 Markdown diff 报告。
 * <p>
 * 仓库结构: {repoRoot}/metadata/{datasourceId}/
 * <pre>
 *   metadata/{datasourceId}/
 *     metadata.json     — 本次采集快照
 *     DIFF-latest.md    — 最近一次 diff 报告 (Markdown)
 *     history/
 *       20260907_113000.json  — 历史版本（按时间戳命名）
 * </pre>
 *
 * @author ECOS Data Engine Team
 * @since 2026-09-07
 */
@Component
public class MetadataCollectGitArchive {

    private static final Logger log = LoggerFactory.getLogger(MetadataCollectGitArchive.class);
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    /** 历史版本保留份数默认上限（可通过 sys_config 的 dw.metadata.history_versions 覆盖） */
    private static final int DEFAULT_MAX_HISTORY_VERSIONS = 50;
    /** 历史版本保留份数允许范围 */
    private static final int MIN_HISTORY_VERSIONS = 1;
    private static final int MAX_HISTORY_VERSIONS_LIMIT = 500;
    /** 历史版本文件名格式（防路径穿越：仅允许 yyyyMMdd_HHmmss） */
    private static final String VERSION_FILE_PATTERN = "^\\d{8}_\\d{6}\\.json$";
    /** 对比字段清单（结构化差异逐字段对比用） */
    private static final List<String> COMPARE_FIELDS = List.of("type", "sourcePath", "description", "fieldCount", "rowCount");

    private final GitService gitService;
    private final JdbcTemplate jdbc;

    /** 仓库根路径，从 sys_config 读取（Windows/WSL 通用） */
    private String repoRoot = Paths.get(System.getProperty("user.home"), "ecos-git-repos").toString();

    public MetadataCollectGitArchive(GitService gitService, JdbcTemplate jdbc) {
        this.gitService = gitService;
        this.jdbc = jdbc;
    }

    /**
     * 采集完成后调用: 将元数据快照归档到 Git 并生成与上一版本的差异报告。
     *
     * @param datasourceId 数据源 ID
     * @param resources    本次采集的表清单
     * @return 包含 diffMarkdown / diffSummary / commitMessage 的 Map
     */
    public Map<String, Object> archiveAndDiff(String datasourceId, List<DataResource> resources) {
        if (resources == null || resources.isEmpty()) {
            return Map.of("archived", false, "error", "无表清单可归档");
        }

        initRepoRoot();
        Path dsDirPath = Paths.get(repoRoot, "metadata", datasourceId);
        String dsDir = dsDirPath.toString();
        String snapshotPath = Paths.get(dsDir, "metadata.json").toString();
        String diffPath = Paths.get(dsDir, "DIFF-latest.md").toString();

        String commitMsg = null;
        Map<String, Object> diffResult = null;

        try {
            // 1. 确保目录结构
            Files.createDirectories(Paths.get(dsDir, "history"));

            // 2. 读取上一版本快照（可能不存在 = 首次采集）
            Map<String, Object> prevSnapshot = readSnapshot(snapshotPath);

            // 3. 构建新快照
            Map<String, Object> newSnapshot = buildSnapshot(datasourceId, resources);

            // 4. 生成 diff 报告
            diffResult = generateDiff(prevSnapshot, newSnapshot, datasourceId);

            // 5. 归档上一版本旧快照到 history/（在新快照覆盖前拷贝）
            String ts = LocalDateTime.now().format(DT_FMT);
            Path snapshotFile = Paths.get(snapshotPath);
            if (Files.exists(snapshotFile)) {
                Path historyDest = Paths.get(dsDir, "history", ts + ".json");
                Files.copy(snapshotFile, historyDest);
            }

            // 6. 写新快照 + diff 报告
            mapper.writerWithDefaultPrettyPrinter().writeValue(snapshotFile.toFile(), newSnapshot);
            Files.writeString(Paths.get(diffPath), (String) diffResult.get("diffMarkdown"));

            // 7. 清理超出上限的历史版本
            cleanupHistory(Paths.get(dsDir, "history").toString());

            // 8. git commit
            String timestamp = LocalDateTime.now().format(DT_FMT);
            commitMsg = "metadata-collect: " + datasourceId + " " + timestamp;
            gitService.commit(dsDir, commitMsg,
                    List.of("metadata.json", "DIFF-latest.md", "history"));
            log.info("Git 存档完成: datasource={}, commit={}", datasourceId, commitMsg);

        } catch (Exception e) {
            log.warn("Git 存档失败（不影响采集任务成功）: datasource={}, error={}", datasourceId, e.getMessage());
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("archived", commitMsg != null);
        result.put("commitMessage", commitMsg != null ? commitMsg : "Git存档失败");
        if (diffResult != null) {
            result.put("diffMarkdown", diffResult.get("diffMarkdown"));
            result.put("diffSummary", diffResult.get("diffSummary"));
        }
        return result;
    }

    /** 从 sys_config 读取仓库根路径 */
    private void initRepoRoot() {
        try {
            String val = jdbc.queryForObject(
                    "SELECT config_value FROM sys_config WHERE config_key = 'ecos_git_repo_root'",
                    String.class);
            if (val != null && !val.trim().isEmpty()) {
                repoRoot = val.trim();
            }
        } catch (Exception e) {
            log.debug("读取 ecos_git_repo_root 失败，使用默认值 {}", repoRoot);
        }
    }

    /**
     * 从 sys_config 读取历史版本保留份数上限（配置键 dw.metadata.history_versions，
     * 引擎配置面板"历史版本数保存记录"）。
     * 非法/缺省时回退默认值 50，范围钳制 [1, 500]。
     */
    private int resolveHistoryLimit() {
        try {
            String val = jdbc.queryForObject(
                    "SELECT config_value FROM sys_config WHERE config_key = 'dw.metadata.history_versions'",
                    String.class);
            if (val == null || val.trim().isEmpty()) {
                return DEFAULT_MAX_HISTORY_VERSIONS;
            }
            int parsed = Integer.parseInt(val.trim());
            return Math.max(MIN_HISTORY_VERSIONS, Math.min(MAX_HISTORY_VERSIONS_LIMIT, parsed));
        } catch (Exception e) {
            log.debug("读取 dw.metadata.history_versions 失败，使用默认值 {}", DEFAULT_MAX_HISTORY_VERSIONS);
            return DEFAULT_MAX_HISTORY_VERSIONS;
        }
    }

    private Map<String, Object> buildSnapshot(String datasourceId, List<DataResource> resources) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("datasourceId", datasourceId);
        snapshot.put("collectedAt", LocalDateTime.now().toString());
        snapshot.put("tableCount", resources.size());

        List<Map<String, Object>> tables = new ArrayList<>();
        for (DataResource r : resources) {
            Map<String, Object> tbl = new LinkedHashMap<>();
            tbl.put("name", r.getResourceName());
            tbl.put("type", r.getResourceType());
            tbl.put("sourcePath", r.getSourcePath());
            tbl.put("description", r.getDescription());
            tbl.put("fieldCount", r.getFieldCount());
            tbl.put("rowCount", r.getRecordCount());
            tables.add(tbl);
        }
        snapshot.put("tables", tables);
        return snapshot;
    }

    private Map<String, Object> readSnapshot(String path) {
        try {
            Path p = Paths.get(path);
            if (!Files.exists(p)) return null;
            String content = Files.readString(p);
            return mapper.readValue(content, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.debug("读取上一版快照失败: {}", e.getMessage());
            return null;
        }
    }

    /** 清理超出上限的历史版本文件（上限从 sys_config 读取，见 resolveHistoryLimit） */
    private void cleanupHistory(String historyDir) {
        try {
            int limit = resolveHistoryLimit();
            List<Path> files = Files.list(Paths.get(historyDir))
                    .filter(f -> f.toString().endsWith(".json"))
                    .sorted()
                    .collect(Collectors.toList());
            int removed = 0;
            while (files.size() > limit) {
                Files.delete(files.remove(0));
                removed++;
            }
            if (removed > 0) {
                log.info("历史版本清理完成: 删除最旧 {} 份, 保留 {} 份 (上限 {})", removed, files.size(), limit);
            }
        } catch (Exception e) {
            log.debug("清理历史版本失败: {}", e.getMessage());
        }
    }

    // ====================================================================
    // 历史版本比较（PMO: 数据表目录历史版本比较功能）
    // ====================================================================

    /**
     * 列出数据源的所有历史版本 + 当前版本（供前端版本选择对话框）。
     * 版本来源: {repoRoot}/metadata/{datasourceId}/history/{yyyyMMdd_HHmmss}.json。
     *
     * @param datasourceId 数据源 ID
     * @return { versions: [{versionId, collectedAt, tableCount}], current: {collectedAt, tableCount} }
     *         versions 按时间降序（新→旧）；目录不存在时返回空列表（非错误）
     */
    public Map<String, Object> listHistoryVersions(String datasourceId) {
        initRepoRoot();
        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> versions = new ArrayList<>();

        Path historyDir = Paths.get(repoRoot, "metadata", datasourceId, "history");
        if (Files.isDirectory(historyDir)) {
            try (var stream = Files.list(historyDir)) {
                List<Path> files = stream
                        .filter(f -> f.getFileName().toString().matches(VERSION_FILE_PATTERN))
                        .sorted(Comparator.reverseOrder()) // 新版本在前
                        .collect(Collectors.toList());
                for (Path f : files) {
                    Map<String, Object> snap = readSnapshot(f.toString());
                    if (snap == null) {
                        continue; // 损坏的快照文件跳过，不影响整体列表
                    }
                    Map<String, Object> v = new LinkedHashMap<>();
                    v.put("versionId", f.getFileName().toString().replace(".json", ""));
                    v.put("collectedAt", snap.get("collectedAt"));
                    v.put("tableCount", snap.get("tableCount"));
                    versions.add(v);
                }
            } catch (Exception e) {
                log.warn("扫描历史版本目录失败: ds={}, err={}", datasourceId, e.getMessage());
            }
        }

        // 当前版本（metadata.json 可能不存在 = 从未采集）
        Map<String, Object> currentSnap = readSnapshot(
                Paths.get(repoRoot, "metadata", datasourceId, "metadata.json").toString());
        Map<String, Object> current = new LinkedHashMap<>();
        current.put("exists", currentSnap != null);
        if (currentSnap != null) {
            current.put("collectedAt", currentSnap.get("collectedAt"));
            current.put("tableCount", currentSnap.get("tableCount"));
        }

        result.put("versions", versions);
        result.put("current", current);
        return result;
    }

    /**
     * 指定历史版本与当前版本的结构化字段级对比。
     * 内存 diff（两份 JSON 快照哈希对比），数据量大时依然快速。
     *
     * @param datasourceId 数据源 ID
     * @param versionId    历史版本 ID（= history 目录下文件名，yyyyMMdd_HHmmss）
     * @return { rows: [...], currentCollectedAt, versionCollectedAt, summary }
     *         rows 每行: changeType(ADDED/DELETED/MODIFIED) / tableName / field /
     *                    currentValue / previousValue / changeTime / commitMessage
     * @throws IllegalArgumentException 版本不存在或参数非法
     */
    public Map<String, Object> compareWithHistory(String datasourceId, String versionId) {
        // 严格校验 versionId 格式，防止路径穿越
        if (versionId == null || !versionId.matches("^\\d{8}_\\d{6}$")) {
            throw new IllegalArgumentException("非法版本号: " + versionId);
        }
        initRepoRoot();

        String baseDir = Paths.get(repoRoot, "metadata", datasourceId).toString();
        Map<String, Object> currentSnap = readSnapshot(Paths.get(baseDir, "metadata.json").toString());
        if (currentSnap == null) {
            throw new IllegalArgumentException("当前版本快照不存在（数据源尚未完成元数据采集）");
        }
        Map<String, Object> prevSnap = readSnapshot(Paths.get(baseDir, "history", versionId + ".json").toString());
        if (prevSnap == null) {
            throw new IllegalArgumentException("历史版本不存在: " + versionId);
        }

        Map<String, Map<String, Object>> currentTables = indexTables(currentSnap);
        Map<String, Map<String, Object>> prevTables = indexTables(prevSnap);
        String currentCollectedAt = String.valueOf(currentSnap.getOrDefault("collectedAt", ""));
        String versionCollectedAt = String.valueOf(prevSnap.getOrDefault("collectedAt", ""));

        List<Map<String, Object>> rows = new ArrayList<>();

        // 新增表：当前版本有、历史版本无
        for (String name : currentTables.keySet()) {
            if (!prevTables.containsKey(name)) {
                rows.add(buildDiffRow("ADDED", name, "(table)", describeTable(currentTables.get(name)),
                        "", currentCollectedAt));
            }
        }
        // 删除表：历史版本有、当前版本无
        for (String name : prevTables.keySet()) {
            if (!currentTables.containsKey(name)) {
                rows.add(buildDiffRow("DELETED", name, "(table)", "",
                        describeTable(prevTables.get(name)), currentCollectedAt));
            }
        }
        // 修改表：逐字段对比
        for (Map.Entry<String, Map<String, Object>> e : currentTables.entrySet()) {
            Map<String, Object> prev = prevTables.get(e.getKey());
            if (prev == null) {
                continue;
            }
            Map<String, Object> curr = e.getValue();
            for (String field : COMPARE_FIELDS) {
                Object cv = curr.get(field);
                Object pv = prev.get(field);
                if (!Objects.equals(cv, pv)) {
                    rows.add(buildDiffRow("MODIFIED", e.getKey(), field,
                            valueToString(cv), valueToString(pv), currentCollectedAt));
                }
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("rows", rows);
        result.put("currentCollectedAt", currentCollectedAt);
        result.put("versionCollectedAt", versionCollectedAt);
        result.put("summary", String.format("新增 %d / 删除 %d / 修改 %d",
                rows.stream().filter(r -> "ADDED".equals(r.get("changeType"))).count(),
                rows.stream().filter(r -> "DELETED".equals(r.get("changeType"))).count(),
                rows.stream().filter(r -> "MODIFIED".equals(r.get("changeType"))).count()));
        return result;
    }

    /** 快照 tables 列表 → 以表名为键的索引（内存 diff 用） */
    private Map<String, Map<String, Object>> indexTables(Map<String, Object> snapshot) {
        Map<String, Map<String, Object>> idx = new LinkedHashMap<>();
        Object tbls = snapshot.get("tables");
        if (tbls instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Map<?, ?> m && m.get("name") instanceof String name) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> table = (Map<String, Object>) m;
                    idx.put(name, table);
                }
            }
        }
        return idx;
    }

    /** 构造一行结构化差异（对应前端差异表格 7 列） */
    private Map<String, Object> buildDiffRow(String changeType, String tableName, String field,
                                             String currentValue, String previousValue, String changeTime) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("changeType", changeType);
        row.put("tableName", tableName);
        row.put("field", field);
        row.put("currentValue", currentValue);
        row.put("previousValue", previousValue);
        row.put("changeTime", changeTime);
        return row;
    }

    /** 表对象 → 简要描述（新增/删除整表时展示） */
    private String describeTable(Map<String, Object> table) {
        return String.format("type=%s, rows=%s, fields=%s",
                table.getOrDefault("type", ""),
                table.getOrDefault("rowCount", "?"),
                table.getOrDefault("fieldCount", "?"));
    }

    /** 值 → 展示字符串（null 显示为空） */
    private String valueToString(Object v) {
        return v == null ? "" : String.valueOf(v);
    }

    private Map<String, Object> generateDiff(Map<String, Object> old, Map<String, Object> new_, String datasourceId) {
        StringBuilder md = new StringBuilder();
        md.append("# 元数据采集差异记录\n\n");
        md.append("- 数据源: `").append(datasourceId).append("`\n");
        md.append("- 本次采集: ").append(LocalDateTime.now()).append("\n");

        if (old == null) {
            md.append("- 上次采集: **首次采集**（无前版本对比）\n\n");
            // 列出所有表
            md.append("## 表清单（").append(countTable(new_)).append("）\n\n");
            for (String table : getTableNames(new_)) {
                md.append("- ").append(table).append("\n");
            }
            return result(md.toString(), "首次采集，共 " + countTable(new_) + " 张表");
        }

        md.append("- 上次采集: ").append(old.getOrDefault("collectedAt", "N/A")).append("\n\n");

        Set<String> oldTables = getTableNames(old);
        Set<String> newTables = getTableNames(new_);

        Set<String> added = newTables.stream().filter(t -> !oldTables.contains(t)).collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> removed = oldTables.stream().filter(t -> !newTables.contains(t)).collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> unchanged = newTables.stream().filter(t -> oldTables.contains(t)).collect(Collectors.toCollection(LinkedHashSet::new));

        if (added.isEmpty() && removed.isEmpty()) {
            md.append("## 无变化\n\n表清单无增减，共 ").append(newTables.size()).append(" 张表。\n\n");
        } else {
            if (!added.isEmpty()) {
                md.append("## 新增表（").append(added.size()).append("）\n");
                added.stream().sorted().forEach(t -> md.append("- `").append(t).append("`\n"));
                md.append("\n");
            }
            if (!removed.isEmpty()) {
                md.append("## 删除表（").append(removed.size()).append("）\n");
                removed.stream().sorted().forEach(t -> md.append("- `").append(t).append("`\n"));
                md.append("\n");
            }
        }

        // 行数变化
        Map<String, Object> newRowCount = getTableRowCountMap(new_);
        Map<String, Object> oldRowCount = getTableRowCountMap(old);
        List<String> changedRows = new ArrayList<>();
        for (String t : unchanged) {
            Object oldC = oldRowCount.get(t);
            Object newC = newRowCount.get(t);
            if (isRowCountDiff(oldC, newC)) {
                changedRows.add("### " + t + "\n- 上次: " + oldC + "\n- 本次: " + newC + "\n");
            }
        }
        if (!changedRows.isEmpty()) {
            md.append("## 行数变化（").append(changedRows.size()).append(" 张表）\n\n");
            md.append(changedRows.stream().sorted().collect(Collectors.joining("\n")));
            md.append("\n");
        }

        md.append("---\n");
        md.append("总计: ").append(newTables.size()).append(" 张表")
          .append("（不变 ").append(unchanged.size())
          .append(" / +新增 ").append(added.size())
          .append(" / −删除 ").append(removed.size()).append("）\n");

        return result(md.toString(), String.format("不变 %d / 新增 %d / 删除 %d / 行数变 %d",
                unchanged.size(), added.size(), removed.size(), changedRows.size()));
    }

    private Map<String, Object> result(String markdown, String summary) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("diffMarkdown", markdown);
        r.put("diffSummary", summary);
        return r;
    }

    private int countTable(Map<String, Object> snapshot) {
        if (snapshot == null) return 0;
        Object cnt = snapshot.get("tableCount");
        if (cnt instanceof Number n) return n.intValue();
        return getTableNames(snapshot).size();
    }

    @SuppressWarnings("unchecked")
    private Set<String> getTableNames(Map<String, Object> snapshot) {
        if (snapshot == null) return Set.of();
        Object tbls = snapshot.get("tables");
        if (!(tbls instanceof List<?>)) return Set.of();
        Set<String> names = new LinkedHashSet<>();
        for (Object o : (List<?>) tbls) {
            if (o instanceof Map<?, ?> m) {
                String name = (String) m.get("name");
                if (name != null) names.add(name);
            }
        }
        return names;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getTableRowCountMap(Map<String, Object> snapshot) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (snapshot == null) return map;
        Object tbls = snapshot.get("tables");
        if (!(tbls instanceof List<?>)) return map;
        for (Object o : (List<?>) tbls) {
            if (o instanceof Map<?, ?> m) {
                String name = (String) m.get("name");
                Object rc = m.get("rowCount");
                if (name != null) map.put(name, rc == null ? "未知" : String.valueOf(rc));
            }
        }
        return map;
    }

    private boolean isRowCountDiff(Object a, Object b) {
        if (a == null || b == null) return !Objects.equals(a, b);
        return !a.equals(b);
    }
}
