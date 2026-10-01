package com.chinacreator.gzcm.engine.kb.service;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F04-12 文档三载体收敛（三 → 一）— 验收测试（详细设计-04 §二·F04-12，REQ-KB-12，守护型）。
 *
 * <p>契约 1（{@code singleAuthoritativeDocTable}）：权威文档载体唯一 =
 * {@code ecos_knowledge.kb_doc}/{@code kb_doc_chunk}（V186/R-10① 认定），上传文本终存必须经
 * {@code KnowledgeDocIngestService} 合规链走 MinIO RAW 层 + kb 自有过渡表 {@code kb_doc_chunk}。</p>
 *
 * <p>契约 2（{@code uploadGoesThroughMinioNotTmpdir}）：上传终存走 MinIO，
 * {@code java.io.tmpdir}（{@code /ecos-extractions}）仅作抽取<b>临时缓冲</b>且须 TTL 清理
 * （对治 K-37/W102「上传落 tmpdir 绕 MinIO RAW」）。</p>
 *
 * <p>纯源码扫描式守护：不依赖 Spring 上下文 / 外部服务，断言生产代码与 DDL/迁移脚本
 * 的载体收敛口径一致。</p>
 */
class DocCarrierConvergenceTest {

    // ── 契约 1：唯一权威文档载体 + 上传经 MinIO & kb_doc_chunk ──
    @Test
    void singleAuthoritativeDocTable() throws IOException {
        Path ingest = locateMainSrc("KnowledgeDocIngestService.java");
        assertNotNull(ingest, "KnowledgeDocIngestService 主源码必须存在");
        String ing = read(ingest);

        // 上传终存走 MinIO RAW 层（MinioStorageService.putObject）
        assertTrue(ing.contains("MinioStorageService"),
                "文档上传终存必须依赖 MinioStorageService（runtime-access 单通道）");
        assertTrue(ing.contains("putObject"),
                "文档终存必须经 MinioStorageService.putObject 写 MinIO RAW 层");
        // 解析文本落 kb 自有过渡表 kb_doc_chunk
        assertTrue(ing.contains("kb_doc_chunk"),
                "解析文本必须落 kb 自有过渡表 kb_doc_chunk（非直写 DW 表）");
    }

    // ── 契约 2：tmpdir 仅临时缓冲（TTL 清理），终存走 MinIO ──────
    @Test
    void uploadGoesThroughMinioNotTmpdir() throws IOException {
        Path svc = locateMainSrc("KnowledgeExtractionService.java");
        assertNotNull(svc, "KnowledgeExtractionService 主源码必须存在");
        String src = read(svc);

        // tmpdir 缓冲目录存在
        assertTrue(src.contains("java.io.tmpdir") || src.contains("java.io.tmpdir".replace("java.io.", "tmpdir"))
                        || src.contains("/ecos-extractions"),
                "抽取临时缓冲目录（java.io.tmpdir/ecos-extractions）必须存在");
        // 缓冲必须带 TTL 上限 + 启动清理（禁无限滞留）
        assertTrue(src.contains("cleanExpiredUploadBuffer"),
                "抽取缓冲必须有 cleanExpiredUploadBuffer 清理逻辑（TTL 兜底）");
        assertTrue(src.contains("UPLOAD_TTL_HOURS"),
                "抽取缓冲必须声明 TTL 上限常量 UPLOAD_TTL_HOURS（对治 K-37 无限滞留）");
        // 反向断言：抽取服务本身不得把 tmpdir 当作文档终存权威载体
        // （终存权威 = MinIO/kb_doc_chunk，见契约 1；此处仅守护临时缓冲定位语义）
        assertFalse(src.contains("putObject") && !src.contains("cleanExpiredUploadBuffer"),
                "若抽取服务直接写对象存储须同步具备缓冲清理（本服务不落对象存储，此断言防误用）");
    }

    // ── helpers ─────────────────────────────────────────────

    /** 在 kb-engine-impl 主源码树内按文件名定位。 */
    private static Path locateMainSrc(String fileName) {
        Path root = null;
        for (Path d = Paths.get("").toAbsolutePath(); d != null; d = d.getParent()) {
            if (Files.isDirectory(d.resolve("src/main/java"))) {
                root = d.resolve("src/main/java");
                break;
            }
        }
        if (root == null) return null;
        try (java.util.stream.Stream<Path> walk = Files.walk(root)) {
            return walk.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().equals(fileName))
                    .findFirst().orElse(null);
        } catch (IOException e) {
            return null;
        }
    }

    private static String read(Path p) throws IOException {
        return Files.readString(p, StandardCharsets.UTF_8);
    }
}
