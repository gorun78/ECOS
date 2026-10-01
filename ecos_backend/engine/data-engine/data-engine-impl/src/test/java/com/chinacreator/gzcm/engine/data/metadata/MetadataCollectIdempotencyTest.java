package com.chinacreator.gzcm.engine.data.metadata;

import com.chinacreator.gzcm.common.data.model.DataResource;
import com.chinacreator.gzcm.engine.data.dao.MetadataGitArchiveConfigDao;
import com.chinacreator.gzcm.engine.data.service.GitRepoRootResolver;
import com.chinacreator.gzcm.runtime.access.git.GitService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * F02-02 验收：同一数据源二次采集 <b>diff 为空</b>（幂等）。
 *
 * <p>实现链路：
 * <ul>
 *   <li>{@link MetadataCollectTaskExecutor#execute} 在执行末尾调用
 *       {@link MetadataCollectGitArchive#archiveAndDiff}；</li>
 *   <li>{@code archiveAndDiff} 写入 {@code {repoRoot}/metadata/{dsId}/metadata.json}，
 *       二次采集时把上一版快照读入 {@code prevSnapshot} 与本次 {@code newSnapshot}
 *       做表清单/行数 diff；</li>
 *   <li>当两次采集的 {@link DataResource} 完全一致（含 rowCount），diff 内容为
 *       {@code "## 无变化"} + summary {@code "不变 N / 新增 0 / 删除 0 / 行数变 0"}。</li>
 * </ul>
 *
 * <p>本测试用 @TempDir 构造仓库根、mock GitRepoRootResolver 指向它，
 * 预置一个 JdbcTemplate stub（no-op，清理历史版本时读上限）+ 空操作 GitService，
 * 跑两次 {@code archiveAndDiff} 并断言：
 * <ol>
 *   <li>第一次 summary 含 "首次采集"；</li>
 *   <li>第二次 summary = {@code 不变 2 / 新增 0 / 删除 0 / 行数变 0}（无新增/删除/行数变化）；</li>
 *   <li>第二次 diffMarkdown 含 {@code "## 无变化"} 且不含 "新增表" / "删除表" / "行数变化" 章节标题；</li>
 *   <li>Git commit 恰好各触发一次（Git 侧不重复记录，等价于"无新历史行"语义）。</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("F02-02：元数据同 ds 二次采集 diff 为空（幂等）")
class MetadataCollectIdempotencyTest {

    private static final String DS_ID = "ds_idempotency_probe";

    @Mock
    private GitService gitService;
    @Mock
    private MetadataGitArchiveConfigDao configDao;
    @Mock
    private GitRepoRootResolver repoRootResolver;

    @TempDir
    Path tmp;

    private MetadataCollectGitArchive archive;

    @BeforeEach
    void setUp() {
        this.archive = new MetadataCollectGitArchive(gitService, configDao, repoRootResolver);
    }

    /** 构造两次采集的 resources —— 完全一致的表清单与 rowCount，模拟稳定数据源 */
    private static List<DataResource> stableResources() {
        DataResource a = new DataResource();
        a.setResourceName("tb_a");
        a.setResourceType("TABLE");
        a.setSourcePath("public.tb_a");
        a.setDescription("first");
        a.setFieldCount(3);
        a.setRecordCount(100L);

        DataResource b = new DataResource();
        b.setResourceName("tb_b");
        b.setResourceType("TABLE");
        b.setSourcePath("public.tb_b");
        b.setDescription("second");
        b.setFieldCount(5);
        b.setRecordCount(200L);
        return List.of(a, b);
    }

    /** 用 @TempDir 骨架搭一个「已存在的可读」Git 根目录 —— resolver 直接回显路径 */
    private Path stubbedRepoRoot() throws Exception {
        Path root = tmp.resolve("git-root");
        Files.createDirectories(root);
        lenient().when(repoRootResolver.resolveRepoRoot()).thenReturn(root.toString().replace('\\', '/'));
        // 清理历史版本时走 sys_config 读保留份数下限；未 stub 时 JdbcTemplate 抛 →
        // resolveHistoryLimit() 兜底 50，无影响
        return root;
    }

    @Test
    @DisplayName("同 ds 采集两次：第二次 diffSummary = '不变 2 / 新增 0 / 删除 0 / 行数变 0'")
    void secondCollectYieldsEmptyDiff() throws Exception {
        Path root = stubbedRepoRoot();

        Map<String, Object> first = archive.archiveAndDiff(DS_ID, stableResources());
        assertEquals(Boolean.TRUE, first.get("archived"), "第一次采集应成功归档");
        assertEquals("首次采集，共 2 张表", first.get("diffSummary"),
                "第一次（无 prev 快照）应标记为首次采集");
        assertTrue(((String) first.get("diffMarkdown")).contains("首次采集"));

        // 校验快照构建成功：仓库内应有 metadata/{dsId}/metadata.json + history/
        Path dsDir = root.resolve("metadata").resolve(DS_ID);
        assertTrue(Files.isRegularFile(dsDir.resolve("metadata.json")),
                "metadata.json 必须落盘（was not created: " + dsDir + "）");
        // 第一次采集无 prev，不产生 history 副本
        assertEquals(0, countHistoryFiles(dsDir), "首次采集无上一版可归档，history 应为空");

        Map<String, Object> second = archive.archiveAndDiff(DS_ID, stableResources());
        assertEquals(Boolean.TRUE, second.get("archived"), "第二次采集应成功归档");
        // F02-02 验收标识：diffSummary 显式「无变化」——唯一新增/删除/行数变全为 0
        assertEquals("不变 2 / 新增 0 / 删除 0 / 行数变 0", second.get("diffSummary"),
                "同 ds 二次采集 diff 必须为空（既有 2 表不变、无增删、无行数变化）");
        String md = (String) second.get("diffMarkdown");
        assertTrue(md.contains("## 无变化"),
                "第二次 diff markdown 应含「## 无变化」章节，实际:\n" + md);
        assertTrue(!md.contains("## 新增表"), "第二次不得出现「## 新增表」章节");
        assertTrue(!md.contains("## 删除表"), "第二次不得出现「## 删除表」章节");
        assertTrue(!md.contains("## 行数变化"), "第二次不得出现「## 行数变化」章节");

        // Git commit 恰好 2 次（每次采集 1 次；不产生额外 commit 即等价于「无新历史 Git 行」语义）
        verify(gitService, org.mockito.Mockito.times(2))
                .commit(org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyList());
        verifyNoMoreInteractions(gitService);
    }

    @Test
    @DisplayName("同 ds 资源内容变化（rowCount 变）第二次 diff 空性失效 → 行数变化章节出现")
    void changedRowCountProducedVisibleDiff() throws Exception {
        stubbedRepoRoot();
        archive.archiveAndDiff(DS_ID, stableResources());

        List<DataResource> mutated = stableResources();
        // 变异第一次数据集合（相同表、不同 rowCount）—— 应**破幂等**
        DataResource a = new DataResource();
        a.setResourceName("tb_a");
        a.setResourceType("TABLE");
        a.setSourcePath("public.tb_a");
        a.setDescription("first");
        a.setFieldCount(3);
        a.setRecordCount(111L); // 变化点：100 → 111
        DataResource b = new DataResource();
        b.setResourceName("tb_b");
        b.setResourceType("TABLE");
        b.setSourcePath("public.tb_b");
        b.setDescription("second");
        b.setFieldCount(5);
        b.setRecordCount(200L);
        Map<String, Object> second = archive.archiveAndDiff(DS_ID, List.of(a, b));
        String summary = String.valueOf(second.get("diffSummary"));
        // 反证保护：对 content 敏感，rowCount 变化必须被 diff 捕获（不再是「无变化」）
        assertTrue(summary.contains("行数变 1"),
                "rowCount 变化必须被 diff 捕获（反证 idempotency 语义），实际 summary=" + summary);
        assertTrue(((String) second.get("diffMarkdown")).contains("## 行数变化"),
                "diffMarkdown 必须含「## 行数变化」章节");
    }

    /** history 目录下 .json 文件数 */
    private static int countHistoryFiles(Path dsDir) throws Exception {
        Path history = dsDir.resolve("history");
        if (!Files.isDirectory(history)) return 0;
        try (var s = Files.list(history)) {
            return (int) s.filter(f -> f.getFileName().toString().endsWith(".json")).count();
        }
    }
}
