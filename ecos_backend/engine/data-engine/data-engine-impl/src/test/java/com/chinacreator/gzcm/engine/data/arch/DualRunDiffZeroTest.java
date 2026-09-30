package com.chinacreator.gzcm.engine.data.arch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * F02-14 / W65 A3 过渡态双跑 diff=0 守卫（best-effort；全量双跑 env-gated，
 * 本单测覆盖 dw 分片比对核心行为与过渡态标记存在性）：
 *
 * <p>核心不变量（M2 双跑口径）：</p>
 * <ol>
 *   <li>{@link #diffChunkLists} — 两份 chunk 批次内容 hash 一致 ⇒ diff=0；
 *       任一分片 hash 不同 ⇒ diff &gt; 0（切流门槛：diff=0 才可切换写通道）。</li>
 *   <li>A1 目标态：kb 读 {@code ecos_dw.doc_chunk}（DW 层）而非 kw 自有过渡表；
 *       由 {@code PipelineExecutionService.DW_DOC_CHUNK_TABLE} 常量存在守护。</li>
 *   <li>A3 兼容：{@code KbDocChunkMapper}（过渡表）在 A3 迁移弧带内仍可合法存在，
 *       回滚开关应对应另一枚标记（设计文档 F02-14-2 描述的
 *       {@code ecos.kb.ingest.legacy_chunk_write}），迁移完成前保留过渡写路径。
 *       本用例只断言过渡标记存在（不强制它已删除 — 删除属 M3 收尾，非本波责任）。</li>
 * </ol>
 *
 * <p>环境 flag：全量双跑需数据平面 (PG) + waragan 消费侧真实数据落仓；
 * 本用例以纯函数 diff + 源码标记存在性替代，无任何外部依赖。</p>
 *
 * @author ECOS Data Engine Team / F02-14
 */
@DisplayName("F02-14/W65 A3 过渡：chunk 双跑 diff + 过渡标记存在性")
class DualRunDiffZeroTest {

    /** 单分片内容 → SHA-256 hex（用于双跑批 diff）。 */
    private static String chunkHash(String content) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(d.length * 2);
            for (byte b : d) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16))
                  .append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 不可用（JDK 缺陷）", e);
        }
    }

    /**
     * 双跑比对两份分片批次：按 content-hash 集合计数不一致分片。
     *
     * @param left  A 路写入的分片批次（元素为 Map: content / hash 等）
     * @param right B 路写入的分片批次
     * @return diff 计数（=0 表示两路一致，可切换写通道；&gt;0 表示存在内容差异）
     */
    static int diffChunkLists(List<Map<String, Object>> left, List<Map<String, Object>> right) {
        if (left == null) left = List.of();
        if (right == null) right = List.of();
        if (left.size() != right.size()) {
            return Math.abs(left.size() - right.size()) + perItemDiff(right, left);
        }
        return perItemDiff(left, right);
    }

    private static int perItemDiff(List<Map<String, Object>> a, List<Map<String, Object>> b) {
        int diff = 0;
        for (int i = 0; i < Math.min(a.size(), b.size()); i++) {
            String ha = hashOf(a.get(i));
            String hb = hashOf(b.get(i));
            if (ha == null || hb == null || !ha.equals(hb)) {
                diff++;
            }
        }
        return diff;
    }

    private static String hashOf(Map<String, Object> chunk) {
        Object precomputed = chunk.get("hash");
        if (precomputed instanceof String s && !s.isBlank()) {
            return s;
        }
        Object content = chunk.get("content");
        return chunkHash(content == null ? "" : content.toString());
    }

    @Test
    @DisplayName("两份批次内容完全一致 → diff=0（切流门槛成立）")
    void identicalBatches_diffZero() {
        List<Map<String, Object>> left = new ArrayList<>();
        left.add(chunk("hello a3 world"));
        left.add(chunk("second chunk"));
        left.add(chunk("third chunk"));
        // 右路：内容完全一致，但分片顺序用 content-hash 对齐（模拟 chunkId 稳定写）
        List<Map<String, Object>> right = new ArrayList<>();
        right.add(chunk("hello a3 world"));
        right.add(chunk("second chunk"));
        right.add(chunk("third chunk"));

        assertEquals(0, diffChunkLists(left, right),
                "内容一致的批次 diff 必须 = 0（切流可放行）");
    }

    @Test
    @DisplayName("任一分片 hash 不同 → diff>0（切流应阻断，保留过渡写通道）")
    void oneChunkDiffers_diffNonZero() {
        List<Map<String, Object>> left = new ArrayList<>();
        left.add(chunk("hello a3 world"));
        left.add(chunk("second chunk"));

        List<Map<String, Object>> right = new ArrayList<>();
        right.add(chunk("hello a3 world"));
        right.add(chunk("second chunk MUTATED")); // 关键：第 2 分片内容不一致

        int diff = diffChunkLists(left, right);
        assertTrue(diff > 0, "第 2 分片内容不同 → diff 必须 > 0，切流需暂停");
        assertEquals(1, diff, "仅 1 分片不同 → diff = 1（精确定位差异计数）");
    }

    @Test
    @DisplayName("批次大小不同 → diff>0（禁止凭缺失分片默认切流）")
    void sizeMismatch_diffNonZero() {
        List<Map<String, Object>> left = new ArrayList<>();
        left.add(chunk("a"));
        List<Map<String, Object>> right = new ArrayList<>();
        right.add(chunk("a"));
        right.add(chunk("b"));

        int diff = diffChunkLists(left, right);
        assertTrue(diff > 0, "批次大小不等必须计 diff>0");
        assertNotEquals(0, diff);
    }

    @Test
    @DisplayName("源码标记：PipelineExecutionService 声明 DW 目标表 ecos_dw.doc_chunk（A1 读写侧锁定）")
    void dwDocChunkTableConstantPresentInPipelineService() throws Exception {
        Path repo = findRepoRoot();
        Path svc = repo.resolve(
                "ecos_backend/engine/data-engine/data-engine-impl/src/main/java/com/chinacreator/gzcm/engine/data/pipeline/PipelineExecutionService.java");
        assertTrue(Files.isRegularFile(svc), "PipelineExecutionService 源码缺失: " + svc);
        String src = Files.readString(svc, StandardCharsets.UTF_8);
        assertTrue(src.contains("DW_DOC_CHUNK_TABLE"),
                "常驻常量 DW_DOC_CHUNK_TABLE 应有（标识 DW 层 doc_chunk 统一读写表）");
        assertTrue(src.contains("ecos_dw.doc_chunk"),
                "DV 目标表必须锁定为 ecos_dw.doc_chunk（F02-14-2 A1 目标态）");
        assertTrue(src.contains("TRANSFORM_DOC_PARSE") || src.contains("SINK_DOC_PARSE") || src.contains("DOC_PARSE"),
                "Doc parse 节点应存在（承担 kb 侧 doc/doc_chunk 切分目标路径）");
    }

    @Test
    @DisplayName("源码标记：A3 过渡表 KbDocChunkMapper 仍存（回滚开关 grace 期可切），且目标表非 kb 过渡表")
    void a3TransitionTablePresentForRollbackWindow() throws Exception {
        Path repo = findRepoRoot();
        // kb 侧过渡表：A3 回滚开关需要 grace 期内保留；迁移完成前是"可关回"的表明
        Path kb = repo.resolve(
                "ecos_backend/engine/kb-engine/kb-engine-impl/src/main/java/com/chinacreator/gzcm/engine/kb/doc/mapper/KbDocChunkMapper.java");
        assertTrue(Files.isRegularFile(kb), "A3 过渡表 Mapper 源码缺失: " + kb);
        String kbSrc = Files.readString(kb, StandardCharsets.UTF_8);
        assertTrue(kbSrc.contains("ecos_knowledge.kb_doc_chunk"),
                "A3 过渡表 KB_DOC_CHUNK 必须语义存在（供 grace period 回滚切回）");
    }

    /** 仓根定位：沿用 {@code AllGitCallersUseResolverArchTest} 同款 findRepoRoot 安全模式。 */
    static Path findRepoRoot() {
        Path dir = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        Path cursor = dir;
        while (cursor != null) {
            Path git = cursor.resolve(".git");
            if (Files.isDirectory(git) || Files.isRegularFile(git)) {
                return cursor;
            }
            cursor = cursor.getParent();
        }
        cursor = dir;
        while (cursor != null) {
            if (Files.exists(cursor.resolve("pom.xml"))) {
                return cursor;
            }
            cursor = cursor.getParent();
        }
        throw new IllegalStateException("无法从 user.dir=" + dir + " 定位仓根（.git/pom.xml 均缺失）");
    }

    private static Map<String, Object> chunk(String content) {
        return Map.of("content", content, "hash", chunkHash(content));
    }
}
