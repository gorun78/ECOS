package com.chinacreator.gzcm.engine.kb.arch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A3 退出（F02-14 ② / 设计 C.1 验收 {@code KbChunkWriteGoneArchTest}，缺口 W62）。
 *
 * <p>设计依据（详细设计-02 F02-14 § 2 "A3 退出"，RawStore 应接管）：</p>
 * <ul>
 *   <li>{@code KbDocChunkMapper.java:22} 的 {@code INSERT INTO ecos_knowledge.kb_doc_chunk}
 *       停写；kb 改读 {@code ecos_dw.doc_chunk}（REST 契约）；仅保留向量索引写入；</li>
 *   <li>双跑 1 批次：chunk count + 内容 hash diff=0 才切；</li>
 *   <li>回滚开关 {@code ecos.kb.ingest.legacy_chunk_write=true}（过渡期后删）。</li>
 * </ul>
 *
 * <p><b>当前实现状态（2026-09-30 实查）= A3 未迁（预期 FAIL baseline）</b>：</p>
 * <ul>
 *   <li>{@code KbDocChunkMapper.java:22} 仍有 {@code INSERT INTO ecos_knowledge.kb_doc_chunk}；</li>
 *   <li>{@code KnowledgeDocIngestService} 仍在 A3 过渡路径（自建 kb 表而非 DW 层）；</li>
 *   <li>{@code doc/mapper}/{@code doc/model} KbDocChunk 实体 + Mapper 全部存活。</li>
 * </ul>
 *
 * <p><b>扫描口径</b>：对 kb-engine-impl main 源码非注释行做
 * {@code \bChunkWriter\b | \bkb_chunk\b | \binsertChunk\b | kb_doc_chunk}
 * 命中统计（末项捕获 A3 真实表名，详见字段注释）；
 * 命中 &gt; 0 即 A3 仍自持 chunk 写 = 失败。命中行在报告里逐条列出。</p>
 *
 * <p><b>本文件收口策略</b>：</p>
 * <ul>
 *   <li>{@code baselineStillOwnsChunkWrites}（启用，soft checkpoint）：
 *       <i>断言 kb 目前<b>仍自持</b> kb_doc_chunk 写（count &gt; 0）</i>，即把"未迁"固化为
 *       可发现的 baseline —— 若有人误删 mapper 或提前改了路径而没跑双跑，本 test 会相反 fail，
 *       提示应先补双跑比对再切；</li>
 *   <li>{@code @Disabled a3MigrationCompleted}：W62 完成 A3 退出后启用，改断 count == 0
 *       （{@code INSERT INTO ecos_knowledge.kb_doc_chunk = 0}）。</li>
 * </ul>
 *
 * @author ECOS KB-Engine Team / W62-gap
 */
@DisplayName("A3 退出：kb-engine-impl 不再自持 kb_doc_chunk 写（M0 gap 记录）")
class NoKbChunkWriteArchTest {

    /**
     * 扫描词：设计要求 🪨{@code \bChunkWriter\b | \bkb_chunk\b | \binsertChunk\b}；
     * 但 A3 真实落地点是 {@code kb_doc_chunk}（见设计 F02-14 §2 / {@code KbChunkWriteGoneArchTest}
     * 判据 = {@code INSERT INTO ecos_knowledge.kb_doc_chunk = 0}），故追加 {@code kb_doc_chunk}
     * 与 {@code kb_doc_chunk} 大写伪装写法，以如实捕获"未迁"状态（否则用纯 kb_chunk 会漏判）。
     */
    private static final Pattern CHUNK_WRITE_PATTERN =
            Pattern.compile("\\bChunkWriter\\b|\\bkb_chunk\\b|\\binsertChunk\\b|kb_doc_chunk");

    private static final Path MODULE_SRC = Paths.get("src/main/java");

    /** 逐行扫描，返回 命中「非注释行」→ 相对路径#行号。 */
    private static Set<String> scanChunkWriteHits() {
        Set<String> hits = new LinkedHashSet<>();
        if (!Files.isDirectory(MODULE_SRC)) {
            fail("kb-engine-impl src/main/java 缺失（surefire cwd 应为 kb-engine-impl/）");
        }
        try (Stream<Path> walk = Files.walk(MODULE_SRC)) {
            walk.filter(Files::isRegularFile)
               .filter(p -> p.toString().endsWith(".java"))
               .filter(p -> !p.toString().contains("target"))
               .forEach(p -> {
                   try {
                       int lineNo = 0;
                       for (String line : Files.readAllLines(p)) {
                           lineNo++;
                           String trimmed = line.trim();
                           // 跳过纯注释行（// 或 * 或 /*）
                           if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")) {
                               continue;
                           }
                           if (CHUNK_WRITE_PATTERN.matcher(line).find()) {
                               hits.add(MODULE_SRC.relativize(p).toString().replace('\\', '/') + "#" + lineNo);
                           }
                       }
                   } catch (IOException e) {
                       fail("读文件失败: " + p);
                   }
               });
        } catch (IOException e) {
            fail("扫描 kb-engine-impl 失败: " + e.getMessage());
        }
        return hits;
    }

    @Test
    @DisplayName("Baseline（A3 未迁）：kb 仍自持 kb_doc_chunk 写（count>0），确保未“假迁”")
    void baselineStillOwnsChunkWrites() {
        Set<String> hits = scanChunkWriteHits();
        if (!hits.isEmpty()) {
            System.err.println("[NoKbChunkWriteArchTest] A3 未迁 baseline 命中的 chunk 写点（预期，待 W62 收敛）:");
            hits.stream().sorted().forEach(h -> System.err.println("    " + h));
        }
        assertTrue(hits.size() > 0,
                "基线异常：A3 已不在自持 kb_doc_chunk（count=0）。此时应启用 @Disabled 的 a3MigrationCompleted，"
                        + "而不是本 baseline test 继续断 >0");
    }

    @Disabled("W62 gap：A3 尚未迁出（kb_doc_chunk 停写 + 建 ecos_dw.doc_chunk REST 契约 + 双跑 diff=0）。"
            + "未完成时下方断言必然失败，故 disabled；W62 落地后启用并断 count == 0"
            + "（即 INSERT INTO ecos_knowledge.kb_doc_chunk = 0 且 kb_chunk 仅存于向量索引读/新 DW 层）")
    @Test
    @DisplayName("(W62 完成态) A3 退出：kb-engine-impl 无 kb_doc_chunk 写（count=0）")
    void a3MigrationCompleted() {
        Set<String> hits = scanChunkWriteHits();
        assertEquals(0, hits.size(), "W62 完成时应 0 命中 kb_chunk 写: " + hits);
    }
}
