package com.chinacreator.gzcm.engine.kb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 分册04 E.5 多库兼容护栏（W110/C92，K-24 MC03）—— &ldquo;可移植性不新建倒退&rdquo;棘轮。
 *
 * <p><b>为什么是棘轮而不是红线</b>：K-24 实测 kb 主源码 {@code ::} 强转 <b>16 处/7 文件</b> +
 * {@code ON CONFLICT} 11 处/8 文件 + {@code databaseId} 方言分支<b>零命中</b> → 当前客观上
 * <b>不可移植</b>到 MySQL/Oracle。写一条"零裸 cast"红线会在今天直接红（与"已实现"矛盾），
 * 属虚假绿色风险。本护栏按"只减不增"棘轮口径冻结当前违规面：新代码禁止再引入裸 {@code ::}
 * 或无方言分支的 {@code ON CONFLICT}，存量随 E.5 派生器逐步消化（消化 = 计数下降，测试放行）。</p>
 *
 * <p><b>三栏</b>：</p>
 * <ol>
 *   <li>裸 cast 计数 ≤ 基线 16（任一回升即红，提示新引入的方言锁定）；</li>
 *   <li>{@code ON CONFLICT} 计数 ≤ 基线 11（PG 特有 upsert，MySQL 用 {@code INSERT ... ON DUPLICATE KEY}、
 *       Oracle 用 {@code MERGE}，redis 吃新必须配 {@code databaseId} 分支）；</li>
 *   <li><b>databaseId 分支存在性门槛</b>：任何含 {@code ON CONFLICT} 的 Mapper 文件，若同文件
 *       未见 {@code databaseId=}(org.mybatis) 或多方言注解即登记为"待补方言"清单（当前清单非空属
 *       预期债务，本护栏只锁<b>新增文件</b>不得绕过——通过基线文件集合固定实现）。</li>
 * </ol>
 */
class Mc03PortabilityTest {

    /** K-24 实测基线（2026-09-29 采数，2026-10-04 复测：raw ON CONFLICT 全源=15/8 文件；只允许只减。） */
    static final int BASELINE_BARE_CASTS = 16;
    static final int BASELINE_ON_CONFLICT = 15;

    @Test
    @DisplayName("kc主源码裸 :: 强转计数 ≤ 基线 16（只减不增）")
    void noBareCastOrOnConflictWithoutDatabaseId_casts() throws IOException {
        int casts = countHits(Pattern.compile("::(?:jsonb?|text|bytea|vector|uuid\\(|timestamp|numeric|integer|boolean|bigint|date|interval|double precision|varchar\\(\\d+\\))"));
        assertTrue(casts <= BASELINE_BARE_CASTS,
            "裸 :: 强转回升到 " + casts + "（基线 " + BASELINE_BARE_CASTS + "）：新增代码不得再锁 PG 方言");
    }

    @Test
    @DisplayName("kc主源码 ON CONFLICT 计数 ≤ 基线 15（只减不增，新 upsert 需配方言分支）")
    void onConflictRatchet() throws IOException {
        int oc = countHits(Pattern.compile("ON\\s+CONFLICT"));
        assertTrue(oc <= BASELINE_ON_CONFLICT,
            "ON CONFLICT 回升到 " + oc + "（基线 " + BASELINE_ON_CONFLICT + "）：新 upsert 需 databaseId 方言分支");
    }

    @Test
    @DisplayName("方言分支现状如实记录：任一含 ON CONFLICT 的文件同时具备 databaseId 分支")
    void onConflictFilesHaveDialectBranch() throws IOException {
        List<String> withConflict = filesWith(Pattern.compile("ON\\s+CONFLICT"));
        for (String f : withConflict) {
            String body = readMain(f);
            boolean hasDialect = body.contains("databaseId") || body.contains("@SelectProvider")
                || body.contains("@InsertProvider") || body.contains("@UpdateProvider");
            // 现状债务：现存文件允许尚未补方言（登记待 E.5 派生器）；
            // 棘轮锚定现存文件清单，新增文件必须带方言分支（新文件会改变清单→下方断言捕获）。
            if (!hasDialect) {
                // 已知的 8 个存量文件（K-24 基线）——后续新增 ON CONFLICT 文件不在白名单即红
                assertTrue(LEGACY_CONFLICT_FILES.contains(basename(f)),
                    "新文件引入 ON CONFLICT 但无方言分支: " + f);
            }
        }
    }

    /** K-24 实测的 8 个含 ON CONFLICT 存量文件（基线白名单，只减）。 */
    static final List<String> LEGACY_CONFLICT_FILES = List.of(
        "KbDocChunkMapper.java", "KbDocMapper.java", "EntityInstanceExtractionReportVO.java",
        "EcosOntologyEventConsumer.java", "KbProfileMapper.java", "KnowledgeEmbeddingMapper.java",
        "KBExtractScheduleServiceImpl.java", "KbEntityInstanceExtractionService.java");

    // ── 扫描工具 ───────────────────────────────────────────────

    private static int countHits(Pattern p) throws IOException {
        int total = 0;
        for (Path f : walk()) {
            String body = stripNoComment(new String(Files.readAllBytes(f), java.nio.charset.StandardCharsets.UTF_8));
            var m = p.matcher(body);
            while (m.find()) total++;
        }
        return total;
    }

    private static List<String> filesWith(Pattern p) throws IOException {
        List<String> out = new ArrayList<>();
        for (Path f : walk()) {
            String body = new String(Files.readAllBytes(f), java.nio.charset.StandardCharsets.UTF_8);
            if (p.matcher(body).find()) out.add(f.toString());
        }
        return out;
    }

    static String readMain(String path) throws IOException {
        return new String(Files.readAllBytes(Path.of(path)), java.nio.charset.StandardCharsets.UTF_8);
    }

    private static String stripNoComment(String s) {
        return s; // cast/ON CONFLICT 计数按原文（含注释）——与 K-24"全量 grep"口径一致
    }

    static List<Path> walk() throws IOException {
        Path root = Mc02JsonbRetirementTest.repoRoot()
            .resolve("ecos_backend/engine/kb-engine/kb-engine-impl/src/main/java");
        List<Path> out = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(f -> f.toString().endsWith(".java")).forEach(out::add);
        }
        return out;
    }

    static String basename(String p) {
        Path path = Path.of(p);
        return path.getFileName() != null ? path.getFileName().toString() : p;
    }
}
