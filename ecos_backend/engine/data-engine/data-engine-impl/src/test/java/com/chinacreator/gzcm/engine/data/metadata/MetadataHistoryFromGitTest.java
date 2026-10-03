package com.chinacreator.gzcm.engine.data.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;

import com.chinacreator.gzcm.engine.data.service.GitRepoRootResolver;
import com.chinacreator.gzcm.runtime.access.git.GitService;

/**
 * F02-02（详细设计-02，DATA P1）—— 元数据 {@code version-history} 的<b>数据源改为 Git 归档单源</b>，
 * 不再从库内快照表读。
 *
 * <p>设计 F02-02 验收「{@code MetadataHistoryFromGitTest}（断言 history 列表来自
 * {@code {repoRoot}/metadata/{dsId}/history/}）」。本测试对 {@link MetadataCollectGitArchive#listHistoryVersions}
 * 做离线运行时判定：仓库根经 {@link GitRepoRootResolver#resolveRepoRoot} 单源解析（此处打桩指向
 * {@code @TempDir}），断言历史版本列表<b>逐条来自磁盘的子目录文件</b>（{@code yyyyMMdd_HHmmss.json}
 * 命名、按时间降序、携带该 JSON 内的 {@code tableCount}/{@code collectedAt}），当前版本来自
 * {@code metadata.json}；非符合命名模式的文件不计入。<b>不触库、不连服务</b>。</p>
 */
@DisplayName("F02-02 元数据 version-history 来自 Git 归档单源（P1）")
class MetadataHistoryFromGitTest {

    @TempDir
    Path repoRoot;

    private MetadataCollectGitArchive svc;

    @BeforeEach
    void setUp() throws Exception {
        GitRepoRootResolver resolver = mock(GitRepoRootResolver.class);
        when(resolver.resolveRepoRoot()).thenReturn(repoRoot.toString());
        // listHistoryVersions 只用 resolver + 文件 IO；gitService/jdbc 此处不被触碰，给 mock 即可
        svc = new MetadataCollectGitArchive(mock(GitService.class), mock(JdbcTemplate.class), resolver);
    }

    @Test
    @DisplayName("MetadataHistoryFromGitTest — history 列表逐项来自 {repoRoot}/metadata/{dsId}/history/*.json，降序 + 当前版来自 metadata.json")
    @SuppressWarnings("unchecked")
    void historyList_readFromGitArchiveDir() throws Exception {
        Path ds = repoRoot.resolve("metadata").resolve("ds01");
        Path history = ds.resolve("history");
        Files.createDirectories(history);

        Files.writeString(history.resolve("20260907_100000.json"),
                "{\"datasourceId\":\"ds01\",\"collectedAt\":\"2026-09-07T10:00:00\",\"tableCount\":3,\"tables\":[]}");
        Files.writeString(history.resolve("20260908_120000.json"),
                "{\"datasourceId\":\"ds01\",\"collectedAt\":\"2026-09-08T12:00:00\",\"tableCount\":4,\"tables\":[]}");
        // 干扰项：不符合 yyyyMMdd_HHmmss.json 命名模式 → 必须被忽略
        Files.writeString(history.resolve("notes.txt"), "not a version");
        Files.writeString(history.resolve("badname.json"), "{\"tableCount\":99}");
        Files.writeString(ds.resolve("metadata.json"),
                "{\"datasourceId\":\"ds01\",\"collectedAt\":\"2026-09-09T09:00:00\",\"tableCount\":5,\"tables\":[]}");

        Map<String, Object> res = svc.listHistoryVersions("ds01");
        List<Map<String, Object>> versions = (List<Map<String, Object>>) res.get("versions");

        assertEquals(2, versions.size(), "仅 2 个合规历史文件计入（notes.txt / badname.json 被忽略）");
        // 降序：新版本在前
        assertEquals("20260908_120000", versions.get(0).get("versionId"), "新→旧排序: 首行应为较新版本");
        assertEquals(4, versions.get(0).get("tableCount"), "count 应来自该历史 JSON 的 tableCount 字段");
        assertEquals("20260907_100000", versions.get(1).get("versionId"));
        assertEquals(3, versions.get(1).get("tableCount"));

        Map<String, Object> current = (Map<String, Object>) res.get("current");
        assertEquals(Boolean.TRUE, current.get("exists"), "存在 metadata.json ⇒ current.exists=true");
        assertEquals(5, current.get("tableCount"), "当前版本 tableCount 应来自 metadata.json");
    }

    @Test
    @DisplayName("MetadataHistoryFromGitTest（反向）— 目录不存在/从未采集 ⇒ 空版本列表且 current.exists=false（非错误）")
    @SuppressWarnings("unchecked")
    void missingArchiveDir_emptyListNotError() {
        Map<String, Object> res = svc.listHistoryVersions("never-collected");
        List<Map<String, Object>> versions = (List<Map<String, Object>>) res.get("versions");
        assertTrue(versions.isEmpty(), "无历史目录 ⇒ 版本列表为空");
        Map<String, Object> current = (Map<String, Object>) res.get("current");
        assertFalse((Boolean) current.get("exists"), "从未采集 ⇒ current.exists=false");
    }

    @Test
    @DisplayName("MetadataHistoryFromGitTest（反向·安全）— dsId 含 .. → not_available 且版本为空（path-traversal 拦截）")
    void illegalSegment_rejected() {
        Map<String, Object> res = svc.listHistoryVersions("../escape");
        // requireSafeSegment 抛异常 → not_available 分支
        assertTrue(((List<?>) res.get("versions")).isEmpty(), "非法段不得穿透到文件系统列目录");
        assertEquals("not_available", res.get("status"), "非法 Git 仓库根/段应返回 not_available 而非抛裸异常");
    }
}
