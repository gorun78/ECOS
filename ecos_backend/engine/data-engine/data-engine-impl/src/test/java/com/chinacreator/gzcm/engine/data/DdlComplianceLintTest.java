package com.chinacreator.gzcm.engine.data;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * W59 / D-18 / C48（详细设计-02 E.5 lint 纪律检查② 的 Java 侧护栏，M1）——
 * 迁移脚本单源目录 DDL 合规基线棘轮（<b>DR04 JSONB _json 后缀</b> + <b>MC01/BIGSERIAL 主键</b>）。
 *
 * <p>D-18 的四条成片违规在单源目录 <code>gateway/.../db/migration/</code> 实查：</p>
 * <ul>
 *   <li><code>gen_random_uuid()</code> 4 个文件（V155/V156/V46/V48）——已由
 *       {@link LegacyDebtBaselineRatchetArchTest} 的 W59 计数棘轮锁死（MC01），不在本类重复；</li>
 *   <li><b>JSONB 字段无 {@code _json} 后缀</b>（DR04）——本类锁定（现值 127 处列声明，基线"只减不增"）；</li>
 *   <li><b>BIGSERIAL/SERIAL 主键</b>（MC01/MC02：主键应 VARCHAR(36) 应用侧 UUID）——本类锁定（现值 49 处列声明）；</li>
 *   <li>V6 同名不同内容双份——已由 {@link MigrationBasenameSingleRootArchTest} 锁死（跨根单源碎片），不在本类重复。</li>
 * </ul>
 *
 * <p>口径与既有 W59 棘轮同源：同一单源目录、<b>顶层</b> *.sql、逐行正则；本类从
 * <b>列声明位置</b>（行首标识符 + 类型 token）识别，而非全文 token 命中——避免
 * <code>CAST(x AS JSONB)</code>、裸 cast、注释里出现的 JSONB 字样被计入，且
 * <b>合规新增</b>（<code>name_json JSONB</code>）天然不计入，只把"新增不合规列声明"判红。
 * <b>只减不增</b>：当前计数 ≤ 基线通过；> 基线（新增不合规列）判红并附明细。</p>
 *
 * <p><b>不触库、不 Spring 容器、不联网</b>：纯文件读取 + 行级正则。</p>
 */
@DisplayName("W59/D-18/C48 迁移脚本 DDL 合规基线（DR04 _json 后缀 + MC01 SERIAL 主键，只减不增）")
class DdlComplianceLintTest {

    /** DR04 基线：单源迁移目录内 <b>无 {@code _json} 后缀的 JSONB 列声明</b>行数（2026-10-03 实查）。 */
    static final int W59_BASELINE_JSONB_NO_SUFFIX_LINES = 127;
    /** MC01/MC02 基线：单源迁移目录内 BIGSERIAL/SERIAL 主键列声明行数（2026-10-03 实查）。 */
    static final int W59_BASELINE_SERIAL_PK_LINES = 49;

    /** 行首列声明 & 类型 JSONB（列名不含 _json 后缀才计入，合规后缀天然排除）。 */
    private static final Pattern JSONB_COLUMN =
            Pattern.compile("^\\s*([A-Za-z_][A-Za-z0-9_]*)\\s+JSONB", Pattern.CASE_INSENSITIVE);
    /** 行首列声明 & 类型 BIGSERIAL/SERIAL（裸类型，词边界防 SERIAL_x 尾缀误判）。 */
    private static final Pattern SERIAL_COLUMN =
            Pattern.compile("^\\s*[A-Za-z_][A-Za-z0-9_]*\\s+(?:BIGSERIAL|SERIAL)\\b", Pattern.CASE_INSENSITIVE);

    @Test
    @DisplayName("W59/C48 DR04 — 无 _json 后缀的 JSONB 列声明 只减不增（基线 " + W59_BASELINE_JSONB_NO_SUFFIX_LINES + "）")
    void w59_jsonbNoSuffix_ratchet() throws IOException {
        List<String> detail = new ArrayList<>();
        int hits = scanColumns(JSONB_COLUMN, detail, true);
        ratchet("W59/C48 DR04 无 _json 后缀 JSONB 列声明（应 name_json JSONB）",
                hits, W59_BASELINE_JSONB_NO_SUFFIX_LINES, detail);
    }

    @Test
    @DisplayName("W59/C48 MC01 — BIGSERIAL/SERIAL 主键列声明 只减不增（基线 " + W59_BASELINE_SERIAL_PK_LINES + "）")
    void w59_serialPk_ratchet() throws IOException {
        List<String> detail = new ArrayList<>();
        int hits = scanColumns(SERIAL_COLUMN, detail, false);
        ratchet("W59/C48 MC01 BIGSERIAL/SERIAL 主键（应 VARCHAR(36) 应用侧 UUID）",
                hits, W59_BASELINE_SERIAL_PK_LINES, detail);
    }

    // ─────────────────────────── 公共辅助 ─────────────────────────────

    private int scanColumns(Pattern rule, List<String> detail, boolean jsonbNoSuffix) throws IOException {
        Path dir = backendRoot().resolve("gateway/src/main/resources/db/migration");
        assertTrue(Files.isDirectory(dir), "迁移单源目录不存在: " + dir);
        List<Path> sqls;
        try (Stream<Path> walk = Files.list(dir)) {
            sqls = walk.filter(p -> p.toString().endsWith(".sql")).sorted().collect(Collectors.toList());
        }
        assertTrue(!sqls.isEmpty(), "迁移目录为空，路径异常: " + dir);
        int hits = 0;
        for (Path p : sqls) {
            String fn = p.getFileName().toString();
            for (String raw : read(p).split("\\R", -1)) {
                String line = stripLineComment(raw);
                var m = rule.matcher(line);
                if (!m.find()) {
                    continue;
                }
                if (jsonbNoSuffix) {
                    String col = m.group(1).toLowerCase();
                    if (col.endsWith("_json")) {
                        continue; // 合规后缀，不计
                    }
                }
                hits++;
                detail.add(fn);
            }
        }
        return hits;
    }

    private static void ratchet(String label, int current, int baseline, List<String> detail) {
        assertFalse(current > baseline,
                String.format("[%s] 债务回升：当前 %d > 基线 %d（只允许减少；新增明细前 20）:\n  %s",
                        label, current, baseline,
                        String.join("\n  ", detail.size() > 20 ? detail.subList(0, 20) : detail)));
    }

    /** 剔除行尾 -- 注释（防注释内 JSONB/SERIAL 字样误命中）。 */
    private static String stripLineComment(String line) {
        int idx = line.indexOf("--");
        return idx >= 0 ? line.substring(0, idx) : line;
    }

    private static Path backendRoot() {
        Path r = RlsInjectionGuardArchTest.resolveModuleSrcMainJava();
        // src/main/java → …/data-engine-impl → …/data-engine → engine → ecos_backend
        for (int i = 0; i < 6; i++) {
            r = r.getParent();
        }
        assertNotNull(r, "无法上溯到 ecos_backend 工程根");
        return r;
    }

    private static String read(Path p) {
        try {
            return Files.readString(p, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
