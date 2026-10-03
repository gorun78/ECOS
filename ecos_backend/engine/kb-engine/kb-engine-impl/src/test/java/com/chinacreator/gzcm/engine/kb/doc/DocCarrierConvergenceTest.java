package com.chinacreator.gzcm.engine.kb.doc;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F04-12 文档载体收敛（三 → 一）验收测试（mvn -Dtest=DocCarrierConvergenceTest）。
 *
 * <p>对治 K-20（三套文档载体并存：{@code public.extraction_drafts} /
 * {@code ecos_knowledge.kb_doc*} / {@code ecos_dw.doc*}）+ K-37（上传落
 * {@code java.io.tmpdir} 终存，绕 MinIO RAW）。R-10=① 裁决：权威 = {@code ecos_knowledge}
 * （知识层 {@code kb_doc}/{@code kb_doc_chunk}），{@code ecos_dw.doc*} 定性演示只停写，
 * {@code extraction_drafts} 降为草稿态；上传一律经 MinIO RAW 层，tmpdir 仅作抽取临时缓冲
 * 且必须 TTL 清理。</p>
 *
 * <p>两条不变式（纯源码/DDL 断言，不虚构 live DB）：
 * <ol>
 *   <li>{@link #singleAuthoritativeDocTable()} — V186 载体登记表
 *       {@code v_kb_doc_carrier_registry} 中，文档级与切片级权威载体各唯一且落在
 *       {@code ecos_knowledge}（{@code kb_doc}/{@code kb_doc_chunk}）；三套并存的其余
 *       载体（{@code extraction_drafts} / {@code ecos_dw.doc} / {@code doc_chunk}）标非权威。</li>
 *   <li>{@link #uploadGoesThroughMinioNotTmpdir()} — 合规上传链
 *       {@code KnowledgeDocIngitService} 将原文写入 MinIO RAW 层（{@code raw/unstructured/}
 *       前缀 + {@code MinioStorageService.putObject}）；本地文件仅作解析临时缓冲
 *       （{@code Files.createTempFile} + finally 清理），且该合规链<b>不</b>含
 *       {@code java.io.tmpdir} 终存耦合（对治 K-37）。</li>
 * </ol>
 *
 * @author ECOS KB Team
 */
class DocCarrierConvergenceTest {

    /** DDL 单源目录 + kb 源码根（从 CWD 逐级向上回溯，兼容 surefire/IDE）。 */
    private static Path findRoot(String markerRel) throws IOException {
        Path cur = Path.of("").toAbsolutePath().normalize();
        for (int i = 0; i < 12 && cur != null; i++) {
            if (Files.isDirectory(cur.resolve(markerRel))) {
                return cur;
            }
            cur = cur.getParent();
        }
        throw new AssertionError("未找到工程根（marker=" + markerRel + " 向上回溯 12 级失败）");
    }

    private static Path fileIn(String markerRel, String fileName) throws IOException {
        Path cur = Path.of("").toAbsolutePath().normalize();
        for (int i = 0; i < 12 && cur != null; i++) {
            Path cand = cur.resolve(markerRel).resolve(fileName);
            if (Files.isRegularFile(cand)) {
                return cand;
            }
            cur = cur.getParent();
        }
        throw new AssertionError("未找到文件 " + fileName);
    }

    /**
     * R-10=①：文档级 + 切片级权威载体各唯一且落 {@code ecos_knowledge}，
     * 其余并存载体全部非权威。
     */
    @Test
    void singleAuthoritativeDocTable() throws IOException {
        Path v186 = fileIn("gateway/src/main/resources/db/migration",
                "V186__kb_doc_carrier_convergence.sql");
        String src = stripComments(Files.readString(v186, StandardCharsets.UTF_8));

        List<String[]> rows = parseRegistryRows(src);
        assertFalse(rows.isEmpty(), "V186 未解析到载体登记表行（is_authoritative 列）");

        List<String> authoritative = new ArrayList<>();
        for (String[] r : rows) {
            if (r[3].equals("1")) {
                authoritative.add(r[0] + "." + r[1]);
            }
        }
        assertEquals(2, authoritative.size(),
                "R-10① 恰有 2 个权威载体行（kb_doc 文档级 + kb_doc_chunk 切片级），实际=" + authoritative);
        assertTrue(authoritative.contains("ecos_knowledge.kb_doc"),
                "文档级权威须为 ecos_knowledge.kb_doc，实际权威集=" + authoritative);
        assertTrue(authoritative.contains("ecos_knowledge.kb_doc_chunk"),
                "切片级权威须为 ecos_knowledge.kb_doc_chunk，实际权威集=" + authoritative);

        // 其余并存载体必须标非权威（0）
        String[] legacy = {"extraction_drafts", "doc", "doc_chunk"};
        for (String lg : legacy) {
            boolean foundNonAuth = rows.stream()
                    .anyMatch(r -> r[1].equals(lg) && r[3].equals("0"));
            assertTrue(foundNonAuth,
                    "并存载体 " + lg + " 须标 is_authoritative=0（只停写/草稿态），未见非权威登记");
        }
        assertTrue(rows.size() >= 5, "载体登记表至少应含 5 行（三套 + 向量 + 双镜像占位），实际=" + rows.size());
    }

    /**
     * 合规上传链：原文经 MinIO RAW 层；本地文件仅解析临时缓冲且 TTL 清理；
     * 该链不得含 {@code java.io.tmpdir} 终存耦合。
     */
    @Test
    void uploadGoesThroughMinioNotTmpdir() throws IOException {
        Path svc = fileIn("engine/kb-engine/kb-engine-impl/src/main/java",
                "com/chinacreator/gzcm/engine/kb/service/KnowledgeDocIngestService.java");
        String src = Files.readString(svc, StandardCharsets.UTF_8);

        // (a) 原文对象键落在 MinIO RAW 位于
        assertTrue(src.contains("raw/unstructured/"),
                "上传原文对象键须落 MinIO RAW 层（raw/unstructured/ 前缀）");
        // (b) 经 runtime-access MinioStorageService 写入（禁 kb 自造 driver）
        assertTrue(src.contains("minioStorageService.putObject("),
                "原文写入须经 MinioStorageService.putObject（统一 MinIO 封装）");
        // (c) 本地文件仅作解析临时缓冲且 finally 清理（TTL/即清）
        assertTrue(src.contains("Files.createTempFile"), "解析应走临时文件缓冲");
        assertTrue(src.contains("deleteQuietly(tempFile)"), "临时文件须在 finally 清理（即 TTL 清理）");
        // (d) 合规上传链不引入 java.io.tmpdir 终存目录（K-37 对治；tmpdir 仅由 createTempFile 内部瞬态使用）
        assertFalse(src.contains("java.io.tmpdir"),
                "合规上传链不得含 java.io.tmpdir 终存目录耦合（K-37：tmpdir 只作抽取临时缓冲，非终存）");
    }

    // ── 工具 ──

    /** 逐行解析 V186 载体登记表 VALUES 行 → {schema, table, role, auth}。 */
    private static List<String[]> parseRegistryRows(String src) {
        List<String[]> out = new ArrayList<>();
        // 匹配单行元祖：('schema', 'table', 'role', auth, 'writeschema', 'action'),
        Pattern row = Pattern.compile(
                "\\('\\s*([A-Za-z_]+)\\s*\\',\\s*\\'([A-Za-z_]+)\\s*\\',\\s*\\'[^']*\\',\\s*([01])\\s*,");
        Matcher m = row.matcher(src);
        while (m.find()) {
            out.add(new String[]{m.group(1), m.group(2), null, m.group(3)});
        }
        return out;
    }

    /** 剥块注释（防 "CONVERGENCE 权威..." 等注释词面误判；registry 行以行内 -- 尾注为主，单独剥）。 */
    private static String stripComments(String raw) {
        String s = raw.replaceAll("(?s)/\\*.*?\\*/", " ");
        s = s.replaceAll("--[^\\n]*", " ");
        return s;
    }
}
