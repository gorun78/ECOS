package com.chinacreator.gzcm.engine.data.metadata;

import com.chinacreator.gzcm.engine.data.service.GitRepoRootResolver;
import com.chinacreator.gzcm.runtime.access.git.GitService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;

/**
 * F02-02 验收：{@code version-history}/{@code version-diff} 数据源 = **Git 归档单源**
 * {@code {repoRoot}/metadata/{dsId}/history/}，<b>不再从库内快照表读</b>（F02-12 单源化）。
 *
 * <p>实现的唯一真源 = {@link MetadataCollectGitArchive#listHistoryVersions}：
 * 直接扫 {@code {repoRoot}/metadata/{dsId}/history/} 下匹配
 * {@code ^\d{8}_\d{6}\.json$} 的文件，逐一读入构造 versionId/collectedAt/tableCount。
 * 该方法<b>不使用 JdbcTemplate</b>，也不读取任何 metadata_snapshot / metadata_history 表——
 * 本测试会以 @TempDir 模拟仓库根，种入 3 个历史版本 JSON，断言返回版本列表 = 种入文件。
 * 同时二次断言 JdbcTemplate 零调用（无 DB 依赖）。</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("F02-02：Metadata history 来自 Git 归档单源（{repoRoot}/metadata/{dsId}/history/）")
class MetadataHistoryFromGitTest {

    private static final String DS_ID = "ds_git_history_probe";

    @Mock
    private GitService gitService;
    @Mock
    private JdbcTemplate jdbc;
    @Mock
    private GitRepoRootResolver repoRootResolver;

    @TempDir
    Path tmp;

    private MetadataCollectGitArchive archive;

    @BeforeEach
    void setUp() {
        this.archive = new MetadataCollectGitArchive(gitService, jdbc, repoRootResolver);
    }

    private Path stubbedRepoRoot() throws Exception {
        Path root = tmp.resolve("git-root");
        Files.createDirectories(root.resolve("metadata").resolve(DS_ID).resolve("history"));
        lenient().when(repoRootResolver.resolveRepoRoot()).thenReturn(root.toString().replace('\\', '/'));
        return root;
    }

    /** 写入一枚历史版本 JSON（只有 collectedAt / tableCount / tables 骨架即可，listHistoryVersions 只读两个字段） */
    private void seedHistory(Path root, String versionId, String collectedAt, int tableCount) throws Exception {
        String json = String.format(
                "{\"datasourceId\":\"%s\",\"collectedAt\":\"%s\",\"tableCount\":%d,\"tables\":[]}",
                DS_ID, collectedAt, tableCount);
        Path file = root.resolve("metadata").resolve(DS_ID).resolve("history").resolve(versionId + ".json");
        Files.writeString(file, json);
    }

    @Test
    @DisplayName("version-history 返回 = history/ 目录下种入文件 1:1 集合，且按时间降序")
    @SuppressWarnings("unchecked")
    void historyListMatchesFilesUnderGitHistoryDir() throws Exception {
        Path root = stubbedRepoRoot();
        String nowAnchor = LocalDateTime.of(2026, 9, 28, 12, 0, 0).toString();
        seedHistory(root, "20260928_120000", nowAnchor, 5);
        seedHistory(root, "20260927_113000", LocalDateTime.of(2026, 9, 27, 11, 30, 0).toString(), 4);
        seedHistory(root, "20260926_101500", LocalDateTime.of(2026, 9, 26, 10, 15, 0).toString(), 3);

        Map<String, Object> result = archive.listHistoryVersions(DS_ID);

        List<Map<String, Object>> versions = (List<Map<String, Object>>) result.get("versions");
        assertEquals(3, versions.size(), "history/ 下 3 个 JSON → 3 条 version 记录");

        // 集合成稳：{文件名 → (collectedAt, tableCount)}
        Set<String> expectedIds = Set.of("20260928_120000", "20260927_113000", "20260926_101500");
        Set<String> actualIds = versions.stream()
                .map(v -> String.valueOf(v.get("versionId")))
                .collect(Collectors.toSet());
        assertEquals(expectedIds, actualIds,
                "versionId 集合必须与 history/ 目录文件同义（1:1 对应）");

        // 时间降序（新 → 旧）
        List<String> orderedIds = versions.stream()
                .map(v -> String.valueOf(v.get("versionId")))
                .toList();
        assertEquals(List.of("20260928_120000", "20260927_113000", "20260926_101500"),
                orderedIds, "versions 必须按时间降序");

        // 具体字段内容：collectedAt / tableCount 来自 JSON 文件
        Map<String, Object> first = versions.get(0);
        assertEquals(nowAnchor, first.get("collectedAt"), "collectedAt 必须等于 history JSON 文件内的值");
        assertEquals(5, first.get("tableCount"), "tableCount 必须等于 history JSON 文件内的值");

        // current 字段：当前 metadata.json 未落盘 → exists=false（未采集完成态）
        Map<String, Object> current = (Map<String, Object>) result.get("current");
        assertEquals(Boolean.FALSE, current.get("exists"),
                "metadata.json 不存在时 current.exists 应为 false");

        // F02-02 单源化的关键断言：**JdbcTemplate 零调用** —— 历史版本不经 DB 快照表
        // （F02-12 之前 ListHistory 旧实现会从 metadata_snapshot/metadata_history 库表读）
        org.mockito.Mockito.verifyNoInteractions(jdbc);
        // Git 服务也零调用（read-only 路径，不入 Git 事务）
        org.mockito.Mockito.verifyNoInteractions(gitService);
    }

    @Test
    @DisplayName("目录不存在 / 无历史版本 → versions 空列表（不是错误）")
    void emptyHistoryDirYieldsEmptyList() throws Exception {
        Path root = stubbedRepoRoot();
        // 删空 history/ 下的文件（stubbedRepoRoot 已创建目录但无 .json）
        try (var s = Files.list(root.resolve("metadata").resolve(DS_ID).resolve("history"))) {
            List<Path> seeded = s.toList();
            assertTrue(seeded.isEmpty(), "前置：history 目录应为空");
        }
        Map<String, Object> result = archive.listHistoryVersions(DS_ID);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> versions = (List<Map<String, Object>>) result.get("versions");
        assertTrue(versions.isEmpty(), "空 history 目录 → versions 必为空列表（不抛、不读 DB）");
        org.mockito.Mockito.verifyNoInteractions(jdbc);
    }

    @Test
    @DisplayName("version-diff（compareWithHistory）读 history/ 下同名 JSON，不读 DB")
    void versionDiffReadsFromHistoryJsonAndIgnoresDb() throws Exception {
        Path root = stubbedRepoRoot();

        // seed 历史版本（有 2 表：tb_a / tb_b）
        Path historyDir = root.resolve("metadata").resolve(DS_ID).resolve("history");
        String histJson = String.format(
                "{\"datasourceId\":\"%s\",\"collectedAt\":\"%s\",\"tableCount\":2,\"tables\":["
                        + "{\"name\":\"tb_a\",\"type\":\"TABLE\",\"sourcePath\":\"public.tb_a\","
                        + "\"description\":\"a\",\"fieldCount\":3,\"rowCount\":100},"
                        + "{\"name\":\"tb_b\",\"type\":\"TABLE\",\"sourcePath\":\"public.tb_b\","
                        + "\"description\":\"b\",\"fieldCount\":5,\"rowCount\":200}"
                        + "]}", DS_ID, "2026-09-27T11:30:00");
        Files.writeString(historyDir.resolve("20260927_113000.json"), histJson);

        // 当前 metadata.json：与历史完全一致（同 2 表同 rowCount）→ diff 空
        String curJson = String.format(
                "{\"datasourceId\":\"%s\",\"collectedAt\":\"%s\",\"tableCount\":2,\"tables\":["
                        + "{\"name\":\"tb_a\",\"type\":\"TABLE\",\"sourcePath\":\"public.tb_a\","
                        + "\"description\":\"a\",\"fieldCount\":3,\"rowCount\":100},"
                        + "{\"name\":\"tb_b\",\"type\":\"TABLE\",\"sourcePath\":\"public.tb_b\","
                        + "\"description\":\"b\",\"fieldCount\":5,\"rowCount\":200}"
                        + "]}", DS_ID, "2026-09-28T12:00:00");
        // 种入的 JSON 自身必须合法（防测试数据笔误污染断言语义）
        new com.fasterxml.jackson.databind.ObjectMapper().readValue(histJson,
                new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
        new com.fasterxml.jackson.databind.ObjectMapper().readValue(curJson,
                new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
        Files.writeString(root.resolve("metadata").resolve(DS_ID).resolve("metadata.json"), curJson);

        Map<String, Object> diff = archive.compareWithHistory(DS_ID, "20260927_113000");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) diff.get("rows");
        assertFalse(rows == null, "rows 键必须存在");
        assertTrue(rows.isEmpty(), "当前 version 与历史 version 相同 → row 差异必须为空");
        // 摘要必须一致（"新增 0 / 删除 0 / 修改 0"）
        String summary = String.valueOf(diff.get("summary"));
        assertTrue(summary.contains("新增 0"), "summary 必须含「新增 0」: " + summary);
        assertTrue(summary.contains("删除 0"), "summary 必须含「删除 0」: " + summary);
        assertTrue(summary.contains("修改 0"), "summary 必须含「修改 0」: " + summary);

        // 铁证：DB 快照表本可承载 version-diff 语义（旧实现），但 F02-02/F02-12 之后必须为 0 调用
        org.mockito.Mockito.verifyNoInteractions(jdbc);
    }
}
