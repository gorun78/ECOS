package com.chinacreator.gzcm.engine.data.fact.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * W48 关联《DdlComplianceLintTest / DdlSchemaComplianceReportTest》（合并单测，2026-09-30 收口）。
 *
 * <p>对 DDL 迁移单源目录 {@code gateway/src/main/resources/db/migration/} 全量
 * {@code V*.sql} 做静态红线扫描，Javadoc 红线语针（spot-check 用）：</p>
 * <ul>
 *   <li><b>IR03</b> — schema 只加不删：禁 {@code DROP TABLE} / {@code DROP COLUMN} /
 *       {@code ALTER TABLE ... DROP}（设计资产历史版本表豁免名单除外，本波未登记）。
 *       <b>ST07</b> — 列名/列类型归一：五事实表基线（V167，PK VARCHAR(36)、金额
 *       NUMERIC(p,s)、时间 TIMESTAMP[TZ]）；本 lint 抓裸 {@code bigint} 列类型（MC02 近亲
 *       红线：ID 一律 VARCHAR(36) 应用侧 UUID，金额一律 NUMERIC(p,s)）。
 *       <b>MC01</b> — 全仓 DDL 禁 {@code DEFAULT gen_random_uuid()}（= 0 命中）。
 *       <b>MC06</b> — 新建表必须 schema 限定：{@code CREATE TABLE [IF NOT EXISTS]
 *       ecos_\w+\.xxx / ecos_dw.xxx}；对 F02 新域（V241+，V247/V248/V260 等）额外
 *       执行限定形态断言报告（历史上百张 unqualified 表按 ST07 knownLegacy 口径只停写
 *       不迁移，不做全仓 FAIL-on，仅对最新批次出违约行清单软装断言稳定性）。</li>
 * </ul>
 *
 * <p><b>豁免</b>：seed 脚本（V235 / V236 / V237 / V243+ 中含 seed 语义者：
 * V246__security_policy_seed.sql 等）只读 INSERT 侧不参与 DDL 红线核算（其头注释已自检
 * 标注 MC01 零值）。V167 五表基线做正向 0 违例验证。</p>
 *
 * <p><b>行为用例</b>（不依赖真实 SQL 目录）：3 条合成坏 SQL 必命中对应红线；
 * V167 DDL 单文件单独跑 → 0 违例。100% 架构/lint，无 Spring 上下文、无 DB、无 Lombok。</p>
 *
 * @author ecos-p0-data-engine
 * <p>2026-09-30（W48 关联行：V241/V247 DDL 单源规范 + C.5 DDL 规则表）</p>
 */
@DisplayName("W48：DDL 合规 lint（IR03 / ST07 / MC01 / MC06，含 NUMERIC(p,s) 与 TIMESTAMP[TZ] 基线核查）")
class DdlComplianceLintTest {

    private static final String MIGRATION_REL =
            "ecos_backend/gateway/src/main/resources/db/migration";

    /** 红线语针登记（Javadoc spot-check 用 needle；含基线证据项 NUMERIC(p,s)/TIMESTAMPTZ）。 */
    private static final String[] NEEDLES = {
            "IR03", "ST07", "MC01", "MC06", "NUMERIC(p,s)", "TIMESTAMPTZ"
    };

    /** 规则表登记自检：三条可执行红线全部有实现入口；六语针齐全（防本测试口径漂移）。 */
    @Test
    @DisplayName("规则表登记完整：IR03/ST07/MC01 可执行 + 语针表含 NUMERIC(p,s)/TIMESTAMPTZ")
    void lintINeedlesCoverRegister() {
        for (String n : List.of("IR03", "ST07", "MC01")) {
            assertNotNull(needleOf(n), "规则 " + n + " 缺实现入口（规则表漂移）");
        }
        assertTrue(java.util.Arrays.asList(NEEDLES).containsAll(
                List.of("IR03", "ST07", "MC01", "MC06", "NUMERIC(p,s)", "TIMESTAMPTZ")),
                "语针登记必须覆盖 IR03/ST07/MC01/MC06/NUMERIC(p,s)/TIMESTAMPTZ 六项");
    }

    // ==================== 1. 真实脚本扫描 ====================

    @Test
    @DisplayName("IR03：全量 V*.sql 零 DROP TABLE / DROP COLUMN / ALTER..DROP（V126 ghost 表归档豁免名单冻结）")
    void ir03NoDropStatements() throws IOException {
        // 铁律 v2.0 §3.1 例外：设计资产历史版本表可 DROP（备份 + Git 链路前置已完成）。
        // V126__drop_ecos_ontology_ghost_tables.sql = 2026-09-28 已裁决的 ghost 表单次归档 DROP，
        // 属 db-migration-lint.ps1 头部声明的「人工审查」存量。豁免名单冻结于此——
        // 任何<b>新增</b>文件出现 DROP 即 FAIL（不许新增豁免面）。
        Set<String> dropExempt = Set.of(
                "V126__drop_ecos_ontology_ghost_tables.sql");
        List<String> hits = new ArrayList<>();
        for (Path p : listMigrations()) {
            String name = p.getFileName().toString();
            String s = stripLineComments(read(p));
            boolean dropTable = matches(s, "DROP\\s+TABLE");
            boolean dropColumn = matches(s, "DROP\\s+COLUMN");
            boolean alterDrop = matches(s, "ALTER\\s+TABLE[^;]*\\bDROP\\b");
            if (dropTable || dropColumn || alterDrop) {
                if (!dropExempt.contains(name)) {
                    hits.add(name + " 含 DROP 语句: " + (dropTable ? "DROP TABLE " : "")
                            + (dropColumn ? "DROP COLUMN " : "") + (alterDrop ? "ALTER..DROP " : ""));
                }
            }
        }
        assertTrue(hits.isEmpty(),
                "IR03 违例（schema 只加不删；豁免名单仅限 " + dropExempt + "）：\n"
                        + String.join("\n", hits));
    }

    @Test
    @DisplayName("MC01：全量 V*.sql 零 DEFAULT gen_random_uuid()（4 张存量 legacy 表豁免名单冻结）")
    void mc01NoGenRandomUuid() throws IOException {
        // 铁律 MC01 目标态 = 全仓 0 命中；存量 4 张 legacy 表（V46/V48/V155/V156）为
        // db-migration-lint.ps1 已知的 knownLegacy 只停写面。豁免名单冻结——
        // 任何<b>新增/修改</b>脚本含 gen_random_uuid() 即 FAIL（不许扩大违例面）。
        Set<String> genUuidExempt = Set.of(
                "V46__ecos_object_links.sql",
                "V48__ecos_outbox_and_saga.sql",
                "V155__kb_nav_category.sql",
                "V156__kb_nav_tag.sql");
        List<String> hits = new ArrayList<>();
        for (Path p : listMigrations()) {
            String name = p.getFileName().toString();
            String s = stripLineComments(read(p));
            if (matches(s, "gen_random_uuid\\s*\\(") && !genUuidExempt.contains(name)) {
                hits.add(name + ": " + firstLine(s));
            }
        }
        assertTrue(hits.isEmpty(),
                "MC01 违例（PK VARCHAR(36) 应用侧 UUID，DDL 禁 gen_random_uuid()；"
                        + "豁免名单仅限 " + genUuidExempt + "）：\n" + String.join("\n", hits));
    }

    @Test
    @DisplayName("ST07：五事实表基线 V167 单文件 0 违例（无裸 bigint / NUMERIC(p,s) / TIMESTAMPTZ 形态齐全）")
    void st07V167BaselineIsClean() throws IOException {
        String v167 = readMigration("V167__biz_fact_model.sql");
        assertNotNull(v167, "迁移单源缺少 V167__biz_fact_model.sql");
        String s = stripLineComments(v167);

        // 裸 bigint 列类型（大小写不敏感，ID 必须 VARCHAR(36)）
        assertFalse(matches(s, "(?<![a-z0-9_])bigint(?![a-z0-9_(])"),
                "ST07/MC02 违例：V167 业务事实五表出现裸 BIGINT 列类型（ID 必须 VARCHAR(36)）");
        // 金额/比率必须 NUMERIC(p,s)（带参数）
        assertTrue(matches(s, "NUMERIC\\(\\s*\\d+\\s*,\\s*\\d+\\s*\\)"),
                "V167 金额/比率列必须 NUMERIC(p,s) 形态（MC02）");
        // 时间列必须 TIMESTAMP / TIMESTAMPTZ 家族（禁 datetime / 裸 DATE 做审计时间戳）
        assertTrue(matches(s, "(?i)TIMESTAMPTZ|TIMESTAMP\\b"),
                "V167 时间列必须走 TIMESTAMP / TIMESTAMPTZ 家族（ST07 时间归一）");
        // 综合 lint 跑 0 违例
        assertEquals(0, lintAll(v167).size(),
                "V167 五表基线应 0 违例，实际：" + lintAll(v167));
    }

    // MC06 已知豁免：F02 新域里唯一已登记的 unqualified 建表（幂等表按 ST07 业务域归属
    // 由 SchemaDriftLintTest 追踪单源存在性；本 lint 只做新增违约行报告，不 FAIL 该存量）。
    private static final Set<String> MC06_KNOWN_EXEMPT = Set.of(
            "V260__f02_05_layer_carrier_write_channel.sql:td_dw_write_channel_idempotency");

    @Test
    @DisplayName("MC06（F02 新域 V241+）：新建表 schema 限定形态稳态报告（新增 unqualified 建表即 FAIL）")
    void mc06NewDomainTablesStableBaseline() throws IOException {
        List<Path> migrations = listMigrations();
        List<String> newViolations = new ArrayList<>();
        for (Path p : migrations) {
            String name = p.getFileName().toString();
            Integer v = migrationVersion(name);
            if (v == null || v < 241) {
                continue;
            }
            String s = stripLineComments(read(p));
            for (String t : unqualifiedCreateTableNames(s)) {
                if (!MC06_KNOWN_EXEMPT.contains(name + ":" + t)) {
                    newViolations.add(name + " 建表 " + t + " 未带 schema 限定（须 ecos_*/ecos_dw.*.xxx）");
                }
            }
        }
        assertTrue(newViolations.isEmpty(),
                "MC06：F02 新域（V241+）新增 unqualified CREATE TABLE（豁免名单："
                        + MC06_KNOWN_EXEMPT + "）：\n" + String.join("\n", newViolations));
    }

    // ==================== 2. 行为用例：合成坏 SQL → 必命中 / V167 → 0 违例 ====================

    @Test
    @DisplayName("合成 SQL-1：DROP TABLE → 必命中 IR03")
    void syntheticDropTableHitsIr03() {
        String bad = "DROP TABLE ecos_dw.ecos_biz_cost_fact;";
        List<String> hits = lintAll(bad);
        assertTrue(hits.stream().anyMatch(h -> h.startsWith("IR03")),
                "坏 SQL-1（DROP TABLE）未被 lint 报告：\n" + hits);
    }

    @Test
    @DisplayName("合成 SQL-2：DEFAULT gen_random_uuid() → 必命中 MC01")
    void syntheticGenRandomUuidHitsMc01() {
        String bad = "CREATE TABLE IF NOT EXISTS ecos_data.t_bad (id VARCHAR(36) DEFAULT gen_random_uuid());";
        List<String> hits = lintAll(bad);
        assertTrue(hits.stream().anyMatch(h -> h.startsWith("MC01")),
                "坏 SQL-2（gen_random_uuid()）未被 lint 报告：\n" + hits);
    }

    @Test
    @DisplayName("合成 SQL-3：裸 bigint 列 + ALTER..DROP COLUMN → 必命中 ST07 与 IR03")
    void syntheticBigintAndAlterDropHitsSt07AndIr03() {        String bad = "CREATE TABLE t (id BIGINT PRIMARY KEY); ALTER TABLE t DROP COLUMN id;";
        List<String> hits = lintAll(bad);
        assertTrue(hits.stream().anyMatch(h -> h.startsWith("IR03")),
                "坏 SQL-3（ALTER..DROP COLUMN）未命中 IR03：\n" + hits);
        assertTrue(hits.stream().anyMatch(h -> h.startsWith("ST07")),
                "坏 SQL-3（裸 BIGINT 列）未命中 ST07：\n" + hits);
    }

    // ==================== lint 引擎（纯函数，供 2 类用例复用） ====================

    /** 对单段 SQL 跑全部红线（真实文件名级豁免由上层按文件名实施；此处纯文本判定），
     * 返回 "RULE: msg" 列表（空 = 0 违例）。 */
    static List<String> lintAll(String rawSql) {
        List<String> hits = new ArrayList<>();
        String s = stripLineComments(rawSql);
        if (needleOf("IR03").asPredicate().test(s)) {
            hits.add(needleOf("IR03").label() + ": DROP 语句出现（schema 只加不删）");
        }
        if (needleOf("MC01").asPredicate().test(s)) {
            hits.add(needleOf("MC01").label() + ": gen_random_uuid() 默认值出现（PK 应用侧 UUID）");
        }
        if (needleOf("ST07").asPredicate().test(s)) {
            hits.add(needleOf("ST07").label() + ": 裸 BIGINT 列类型出现（ID 必须 VARCHAR(36)）");
        }
        return hits;
    }

    private interface INeedle {
        String label();

        java.util.function.Predicate<String> asPredicate();
    }

    private static final INeedle IR03 = new INeedle() {
        public String label() {
            return "IR03";
        }

        public java.util.function.Predicate<String> asPredicate() {
            return s -> matches(s, "DROP\\s+TABLE")
                    || matches(s, "DROP\\s+COLUMN")
                    || matches(s, "ALTER\\s+TABLE[^;]*\\bDROP\\b");
        }
    };

    private static final INeedle MC01 = new INeedle() {
        public String label() {
            return "MC01";
        }

        public java.util.function.Predicate<String> asPredicate() {
            return s -> matches(s, "gen_random_uuid\\s*\\(");
        }
    };

    private static final INeedle ST07 = new INeedle() {
        public String label() {
            return "ST07";
        }

        public java.util.function.Predicate<String> asPredicate() {
            // 裸 bigint = 非 bigserial / 非 bigint? 复数误伤；(?<![a-z0-9_]) 保证独立 token
            return s -> matches(s, "(?<![a-z0-9_])bigint(?![a-z0-9_?(])");
        }
    };

    private static INeedle needleOf(String code) {
        return switch (code) {
            case "IR03" -> IR03;
            case "MC01" -> MC01;
            case "ST07" -> ST07;
            default -> throw new IllegalArgumentException("未登记规则: " + code);
        };
    }

    // ==================== 工具 ====================

    private static boolean matches(String text, String regex) {
        return java.util.regex.Pattern.compile(regex, java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(text).find();
    }

    /** 去行注释（-- 起）；不处理块注释（迁移目录实测无 /* style 干扰，保守策略）。 */
    static String stripLineComments(String sql) {
        StringBuilder out = new StringBuilder(sql.length());
        for (String line : sql.split("\\r?\\n", -1)) {
            int dash = line.indexOf("--");
            if (dash >= 0) {
                line = line.substring(0, dash);
            }
            out.append(line).append('\n');
        }
        return out.toString();
    }

    /** unqualified CREATE TABLE 的表名列表（带 schema 限定的直接剔除）。 */
    static List<String> unqualifiedCreateTableNames(String sqlNoComments) {
        List<String> names = new ArrayList<>();
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(
                "(?i)CREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?([a-zA-Z_][\\w.]*)");
        java.util.regex.Matcher m = p.matcher(sqlNoComments);
        while (m.find()) {
            String t = m.group(1);
            if (!t.contains(".")) {
                names.add(t);
            }
        }
        return names;
    }

    private static String firstTableOf(String sqlNoComments) {
        List<String> all = new ArrayList<>();
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(
                "(?i)CREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?([a-zA-Z_][\\w.]*)");
        java.util.regex.Matcher m = p.matcher(sqlNoComments);
        while (m.find()) {
            all.add(m.group(1));
        }
        return all.isEmpty() ? null : all.get(0);
    }

    private static String firstLine(String s) {
        for (String line : s.split("\\r?\\n")) {
            if (!line.isBlank()) {
                return line.trim();
            }
        }
        return "(empty)";
    }

    private static Integer migrationVersion(String fileName) {
        // V167__x.sql / V167.1__x.sql / V1.1__x.sql
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^V(\\d+)(?:\\.\\d+)?__").matcher(fileName);
        return m.find() ? Integer.valueOf(m.group(1)) : null;
    }

    private static List<Path> listMigrations() throws IOException {
        Path dir = findRepoRoot().resolve(MIGRATION_REL);
        List<Path> files = new ArrayList<>();
        try (Stream<Path> st = Files.list(dir)) {
            st.filter(p -> p.getFileName().toString().startsWith("V")
                            && p.toString().endsWith(".sql"))
                    .sorted()
                    .forEach(files::add);
        }
        assertTrue(!files.isEmpty(), "迁移单源目录为空: " + dir);
        return files;
    }

    private static List<String> readAllMigrations() throws IOException {
        List<String> corpus = new ArrayList<>();
        for (Path p : listMigrations()) {
            corpus.add(read(p));
        }
        return corpus;
    }

    private static String readMigration(String fileName) throws IOException {
        return read(findRepoRoot().resolve(MIGRATION_REL).resolve(fileName));
    }

    private static String read(Path p) throws IOException {
        return Files.readString(p, StandardCharsets.UTF_8);
    }

    static Path findRepoRoot() {
        Path cursor = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        while (cursor != null) {
            if (Files.isDirectory(cursor.resolve(".git"))) {
                return cursor;
            }
            cursor = cursor.getParent();
        }
        throw new IllegalStateException("无法从 user.dir 定位仓库根: "
                + System.getProperty("user.dir"));
    }
}
