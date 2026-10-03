package com.chinacreator.gzcm.engine.data.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;

import com.chinacreator.gzcm.common.data.model.DataResource;
import com.chinacreator.gzcm.engine.data.service.GitRepoRootResolver;
import com.chinacreator.gzcm.runtime.access.git.GitService;

/**
 * F02-02（详细设计-02，DATA P1）—— 元数据同 ds 二次采集<b>幂等 / diff 为空</b>。
 *
 * <p>设计 F02-02 验收「{@code MetadataCollectIdempotencyTest}（同 ds 二次采集 diff 为空）」。
 * {@link MetadataCollectGitArchive#archiveAndDiff} 的 diff 完全由 <b>两份内存 JSON 快照</b>
 * （上次 {@code metadata.json} vs 本次 {@code buildSnapshot}）内存对比得出，不触库、不依赖服务端。
 * 本测试用 {@code @TempDir} 作仓库根、Mockito 打桩 {@link GitRepoRootResolver} 指向该目录：
 * 对<b>同一份表清单</b>连续 {@code archiveAndDiff} 两次，断言第二次 diff 摘要<b>新增 0 / 删除 0 /
 * 行数变 0</b>（幂等）；反向：清单发生变化时 diff 必须随之非零（证明 diff 非恒空、未被写成常绿桩）。</p>
 */
@DisplayName("F02-02 元数据同 ds 二次采集幂等 diff 为空（P1）")
class MetadataCollectIdempotencyTest {

    @TempDir
    Path repoRoot;

    private MetadataCollectGitArchive svc;

    @BeforeEach
    void setUp() {
        GitRepoRootResolver resolver = mock(GitRepoRootResolver.class);
        when(resolver.resolveRepoRoot()).thenReturn(repoRoot.toString());
        // archiveAndDiff 只经 resolver + 文件 IO；gitService.commit 委托 runtime（mock 空实现）；
        // jdbc 仅在 clean-up 限额读取处被触碰（unstubbed 返回 null → 默认 50，不触库）
        svc = new MetadataCollectGitArchive(mock(GitService.class), mock(JdbcTemplate.class), resolver);
    }

    private static DataResource table(String name, long rowCount, int fieldCount) {
        DataResource r = new DataResource();
        r.setResourceName(name);
        r.setResourceType("TABLE");
        r.setSourcePath("src.example." + name);
        r.setDescription(name + " desc");
        r.setFieldCount(fieldCount);
        r.setRecordCount(rowCount);
        return r;
    }

    @Test
    @DisplayName("MetadataCollectIdempotencyTest — 同一清单二次采集 ⇒ 新增0/删除0/行数变0（幂等）")
    void secondCollectOfSameList_hasEmptyDiff() {
        List<DataResource> same = List.of(table("t_a", 100L, 5), table("t_b", 200L, 7));

        Map<String, Object> first = svc.archiveAndDiff("ds1", same);
        assertEquals(Boolean.TRUE, first.get("archived"), "首次采集应归档成功");

        Map<String, Object> second = svc.archiveAndDiff("ds1", same);
        String summary = String.valueOf(second.get("diffSummary"));
        assertTrue(summary.contains("新增 0"), "同清单二次采集应无新增表; 实际 " + summary);
        assertTrue(summary.contains("删除 0"), "同清单二次采集应无删除表; 实际 " + summary);
        assertTrue(summary.contains("行数变 0"), "同清单二次采集应无行数变化; 实际 " + summary);
        assertTrue(String.valueOf(second.get("diffMarkdown")).contains("无变化"),
                "diff 正文应显式标注无变化; 实际 " + second.get("diffMarkdown"));
    }

    @Test
    @DisplayName("MetadataCollectIdempotencyTest（反向）— 清单增加 1 表 ⇒ diff 宣告新增 1（证明非恒空）")
    void changedList_reflectsAddition() {
        List<DataResource> firstList = List.of(table("t_a", 100L, 5));
        svc.archiveAndDiff("ds2", firstList);

        List<DataResource> secondList = List.of(table("t_a", 100L, 5), table("t_new", 42L, 3));
        Map<String, Object> second = svc.archiveAndDiff("ds2", secondList);
        String summary = String.valueOf(second.get("diffSummary"));
        assertTrue(summary.contains("新增 1"), "新增 1 表应被 diff 捕获; 实际 " + summary);
        assertTrue(summary.contains("删除 0"), "无删除; 实际 " + summary);
    }
}
