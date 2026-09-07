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
    /** 历史版本保留份数上限 */
    private static final int MAX_HISTORY_VERSIONS = 50;

    private final GitService gitService;
    private final JdbcTemplate jdbc;

    /** 仓库根路径，从 sys_config 读取 */
    private String repoRoot = "/home/guorongxiao/ecos-git-repos";

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

    /** 清理超出上限的历史版本文件 */
    private void cleanupHistory(String historyDir) {
        try {
            List<Path> files = Files.list(Paths.get(historyDir))
                    .filter(f -> f.toString().endsWith(".json"))
                    .sorted()
                    .collect(Collectors.toList());
            while (files.size() > MAX_HISTORY_VERSIONS) {
                Files.delete(files.remove(0));
            }
        } catch (Exception e) {
            log.debug("清理历史版本失败: {}", e.getMessage());
        }
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
