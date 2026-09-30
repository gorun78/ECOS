package com.chinacreator.gzcm.engine.kb.arch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A3 退出（F02-14 ② / 设计 C.1 验收 {@code KbChunkWriteGoneArchTest}，缺口 W62/W65/D-24）。
 *
 * <p>设计验收口径：kb 侧 {@code INSERT INTO ecos_knowledge.kb_doc_chunk} 停写
 * （grep 该写入 = 0 处），kb 改读 {@code ecos_dw.doc_chunk}（REST），仅保留向量索引写入；
 * 双跑 1 批次 diff=0 才切。</p>
 *
 * <p><b>当前实现状态（A3 未迁，实际主代码仍自持 1 处写入 = {@code KbDocChunkMapper#batchUpsert}，
 * 带 ON CONFLICT ... DO UPDATE 幂等 upsert）</b>：最终「=0」退出断言此时必然失败，故按仓库既有
 * 收口惯例（见同包 {@code NoKbChunkWriteArchTest}）拆为两态：
 * <ul>
 *   <li>{@code singleSanctionedLegacyWriteSite}（<b>启用</b>，drift 护栏）：
 *       断言 kb-engine 主代码里 {@code INSERT INTO ecos_knowledge.kb_doc_chunk} 命中**恰好 1 处**
 *       且该处为已知合法回滚态写点（{@code KbDocChunkMapper#batchUpsert}，幂等 upsert）。
 *       其价值 = 一旦有人<b>新增</b>第二处 kb_doc_chunk 写（路径漂移），本测立即 FAIL 报警，
 *       防止伪迁移/多点静默写扩大 A3 债务；这是今天真实成立且须持续守卫的不变式。</li>
 *   <li>{@code a3ExitZeroWrites}（{@code @Disabled}，目标态）：W62 完成 A3 退出（停写 + 读
 *       ecos_dw.doc_chunk + 双跑 diff=0）后启用，改断 <b>整个 ecos_backend 主代码 0 处命中</b>
 *       —— 即设计验收的字面判据 {@code INSERT INTO ecos_knowledge.kb_doc_chunk = 0}。</li>
 * </ul>
 *
 * <p><b>不 .toURI()</b>（URISyntaxException 会砸 test-compile）；user.dir 上溯 .git 定位仓库根，
 * 逐行 Files.readAllLines 对非注释行正则，注释行忽略。</p>
 */
@DisplayName("A3 退出：kb_doc_chunk 停写（F02-14 ② drift 护栏 + W62 目标态）")
class KbChunkWriteGoneArchTest {

    /** 精确写入判据：完整的 INSERT INTO 语义（区别于注释里的字面引用/读查询 count/SELECT）。 */
    private static final Pattern KB_DOC_CHUNK_WRITE =
            Pattern.compile("INSERT\\s+INTO\\s+ecos_knowledge\\.kb_doc_chunk");

    /** 已知唯一合法回滚态写点。 */
    private static final String KNOWN_WRITE_SITE = "KbDocChunkMapper";

    @Test
    @DisplayName("drift 护栏：kb 主代码 kb_doc_chunk 写入恰为 1 处且是已知合法写点（新增第 2 处即 FAIL）")
    void singleSanctionedLegacyWriteSite() throws IOException {
        Path kbMain = kbModuleRoot().resolve("src/main/java");
        assertTrue(Files.isDirectory(kbMain), "缺 kb-engine-impl src/main/java: " + kbMain);

        List<String> writes = scanWrites(kbMain);
        assertEquals(1, writes.size(),
                "A3 未完成态下 kb 主代码 kb_doc_chunk 写入应恰为 1 处（已知合法回滚态），"
                        + "多于 1 处 = 路径漂移/新增静默写，须立即收敛：\n"
                        + String.join("\n", writes));
        assertTrue(writes.get(0).contains(KNOWN_WRITE_SITE),
                "唯一写点应为 " + KNOWN_WRITE_SITE + "#batchUpsert（ON CONFLICT 幂等 upsert），实际: "
                        + writes.get(0));
    }

    @Test
    @DisplayName("drift 护栏：唯一写点确为幂等 upsert（ON CONFLICT ... DO UPDATE），非裸插/删")
    void onlyWriteSiteIsIdempotentUpsert() throws IOException {
        Path kbMain = kbModuleRoot().resolve("src/main/java");
        List<Path> javaFiles = new ArrayList<>();
        for (Path p : collectJavaFiles(kbMain)) {
            if (p.getFileName().toString().equals(KNOWN_WRITE_SITE + ".java")) {
                javaFiles.add(p);
            }
        }
        assertEquals(1, javaFiles.size(), "主代码应有且仅有 1 个 " + KNOWN_WRITE_SITE + ".java");
        String src = Files.readString(javaFiles.get(0), java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(src.contains("ON CONFLICT"), KNOWN_WRITE_SITE + " 写入须幂等 ON CONFLICT（防重复行）");
        assertTrue(src.contains("DO UPDATE SET"), KNOWN_WRITE_SITE + " 须 ON CONFLICT DO UPDATE（幂等 upsert 语义）");
        // 主代码无 DELETE FROM 该表（IR03 只加不删，停写≠删除，删待授权）
        assertTrue(!src.contains("DELETE FROM ecos_knowledge.kb_doc_chunk"),
                KNOWN_WRITE_SITE + " 不得 DELETE kb_doc_chunk（IR03 只加不删）");
    }

    @Disabled("W62 gap：A3 尚未退出（kb 仍自持 kb_doc_chunk 写 = 1 处）。完成态 = 停写 "
            + "+ kb 改读 ecos_dw.doc_chunk(REST) + 双跑 1 批次 diff=0 才切 + 回滚开关 "
            + "ecos.kb.ingest.legacy_chunk_write。W62 落地后启用本断言并断：<b>整个 ecos_backend"
            + " 主代码 INSERT INTO ecos_knowledge.kb_doc_chunk = 0 处</b>（设计验收字面判据）。")
    @Test
    @DisplayName("(W62 完成态) A3 退出：全 ecos_backend 主代码 kb_doc_chunk 写入 = 0")
    void a3ExitZeroWrites() throws IOException {
        Path backend = repoRoot().resolve("ecos_backend");
        assertEquals(0, scanWrites(backend).size(),
                "W62 完成态要求 A3 写全清：ecos_backend 主代码 kb_doc_chunk 写入须 = 0");
    }

    // ── 扫描工具 ────────────────────────────────────────────────

    /** 在 root（递归）内扫主代码 .java 非注释行的 kb_doc_chunk 写入，返回 relpath#line。 */
    private static List<String> scanWrites(Path root) throws IOException {
        List<String> hits = new ArrayList<>();
        for (Path f : collectJavaFiles(root)) {
            List<String> lines = Files.readAllLines(f, java.nio.charset.StandardCharsets.UTF_8);
            int lin = 0;
            for (String raw : lines) {
                lin++;
                String t = raw.trim();
                if (t.startsWith("//") || t.startsWith("*") || t.startsWith("/*")) {
                    continue;
                }
                if (KB_DOC_CHUNK_WRITE.matcher(raw).find()) {
                    String rel;
                    try {
                        rel = root.relativize(f).toString().replace('\\', '/');
                    } catch (Exception e) {
                        rel = f.getFileName().toString();
                    }
                    hits.add(rel + "#" + lin);
                }
            }
        }
        return hits;
    }

    /** 递归收集 .java（排除 target/）。 */
    private static List<Path> collectJavaFiles(Path root) throws IOException {
        try (var walk = Files.walk(root)) {
            return walk.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> !p.toString().contains("target"))
                    .toList();
        }
    }

    /** 从 user.dir 上溯 .git 定位仓库根（无 .toURI()）。 */
    private static Path repoRoot() {
        Path cur = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (cur != null) {
            if (Files.isRegularFile(cur.resolve(".git")) || Files.isDirectory(cur.resolve(".git"))) {
                return cur;
            }
            cur = cur.getParent();
        }
        throw new IllegalStateException("未能定位仓库根（缺 .git）");
    }

    /** kb-engine-impl 模块根。 */
    private static Path kbModuleRoot() {
        return repoRoot().resolve("ecos_backend/engine/kb-engine/kb-engine-impl");
    }
}
