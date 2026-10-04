package com.chinacreator.gzcm.engine.ontology.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * F03-03 W77/C61 (M0 P0, O-11)：{@code metric_definition} 表合规化 DDL 走查护栏。
 *
 * <p>背景（doc §F03-03 / O-11 / V168.1 迁移）：
 * <ul>
 *   <li>旧表 {@code ecos_ontology.metric_definition} 缺三字段
 *       {@code caliber_id}/{@code formula_version}/{@code caliber_snapshot}；
 *       {@code id VARCHAR(64)} 违 MC01 主键形态（MC01 只允许 {@code VARCHAR(36)} 应用侧 UUID）；
 *       缺 DR06 审计五列 + DR07 version_no + DR08 domain。</li>
 *   <li>V168.1 落地双刀：
 *     <ol>
 *       <li>§1 旧表 {@code ADD COLUMN IF NOT EXISTS} 补三字段 + DR06/07/08 七列（旧表名遵 IR03 不改）；</li>
 *       <li>§2 新表 {@code ecos_ontology.ecos_metric_definition}（MC01 {@code VARCHAR(36)} 主键 +
 *           规范七列；F03-09 语义二分"定义"侧唯一可写入口）；</li>
 *       <li>§3 一次性 INSERT … SELECT 迁移（实测 0 行，幂等）；</li>
 *       <li>§4 旧表停写注释（不 DROP、不改名，IR03 只加不删）。</li>
 *     </ol></li>
 * </ul>
 *
 * <p>本测试 = DDL 静态走查（不触库/不 Spring context/不联网），锁定 V168.1 的：
 * <ol>
 *   <li>旧表 {@code metric_definition} 三字段 + DR06/07/08 全部落 ADD COLUMN（词边界 token 断言）；</li>
 *   <li>新表 {@code ecos_metric_definition} CREATE TABLE 块含规范七列 + 三字段；</li>
 *   <li>新表 PK 形态 = {@code VARCHAR(36) PRIMARY KEY}（MC01）；</li>
 *   <li>新表 schema 限定 {@code ecos_ontology.ecos_metric_definition}（ST07）；</li>
 *   <li>无 MC03 违例：无 {@code ::} PG 私有转型、无 {@code JSONB}、无 {@code BIGSERIAL}/
 *       {@code gen_random_uuid()}；</li>
 *   <li>旧表老提到的 {@code id VARCHAR(64)} 保留不改（IR03 存量定性，不改列类型）——
 *       仅登记不作断言，避免测试越权"逼改"（R9 knownLegacy）。</li>
 * </ol>
 *
 * <p>白线反向：不越权断言旧表 id 已改（现有代码 COMMENT 明写"旧 created_at 停写新读（双写过渡），
 * IR03 只加不删"，禁止老列改名/改型是本项明列约束）。
 */
@DisplayName("W77/C61 指标定义合规化 DDL 走查（V168.1, MC01/DR06/07/08, M0 P0）")
class MetricDefinitionConformTest {

    /** V168.1 迁移脚本文件名（单源目录 gateway/src/main/resources/db/migration/）。 */
    private static final String V168_1 = "V168.1__metric_definition_conform.sql";

    /** 新表名（DR02 前缀 + 单数、无 _v2 后缀）。 */
    private static final String NEW_TABLE = "ecos_metric_definition";

    /** 规范七列（DR06 审计五 + DR07 + DR08）。 */
    private static final String[] CANONICAL_7 = {
            "create_time", "update_time", "create_by", "update_by",
            "is_deleted", "version_no", "domain"
    };

    /** O-11 三字段（REQ-ONTO-02 承诺）。 */
    private static final String[] ONTO_TAG_FIELDS = {"caliber_id", "formula_version", "caliber_snapshot"};

    // ─────────────────────────── 用例 ───────────────────────────

    @Test
    @DisplayName("V168.1 存在 + 旧表 metric_definition 三字段与规范七列 ADD COLUMN 落位")
    void legacyTableAddColumnsConformant() throws IOException {
        Path migrationDir = resolveMigrationDir();
        Path script = migrationDir.resolve(V168_1);
        assertTrue(Files.isRegularFile(script),
                "单源目录缺失 " + V168_1 + "（承载 W77 合规化）—— DDL 单源漂移");
        String sql = Files.readString(script, StandardCharsets.UTF_8);

        // 断言 §1 每列 ADD COLUMN 存在（词边界 token，不容 create_time2 之类子串污染）
        for (String col : concat(CANONICAL_7, ONTO_TAG_FIELDS)) {
            Pattern addColPat = Pattern.compile(
                    "ADD\\s+COLUMN\\s+IF\\s+NOT\\s+EXISTS\\s+" + Pattern.quote(col) + "\\b",
                    Pattern.CASE_INSENSITIVE);
            assertTrue(addColPat.matcher(sql).find(),
                    "旧表 metric_definition 缺 ADD COLUMN " + col + "（W77/O-11 合规化 DDL 面）");
        }
    }

    @Test
    @DisplayName("V168.1 新表 ecos_ontology.ecos_metric_definition CREATE TABLE 块：规范七列 + 三字段 + PK=VARCHAR(36)")
    void newTableDefinitionBlockConformant() throws IOException {
        Path migrationDir = resolveMigrationDir();
        String sql = Files.readString(migrationDir.resolve(V168_1), StandardCharsets.UTF_8);

        String block = extractCreateTableBlock(sql, NEW_TABLE);
        assertNotNull(block, "未能在 V168.1 定位 CREATE TABLE … " + NEW_TABLE + " 块");

        // 规范七列 + 三字段（同包含 .sql 中注释里的列名不可能命中）
        List<String> missing = new ArrayList<>();
        for (String col : concat(CANONICAL_7, ONTO_TAG_FIELDS)) {
            if (!containsToken(block, col)) missing.add(col);
        }
        assertTrue(missing.isEmpty(),
                "新表 " + NEW_TABLE + " 块缺规范列: " + missing + "（DR06/07/08 + O-11 三字段，MC 合规）");

        // ST07 schema 限定名（必须显式 ecos_ontology. 前缀）
        assertTrue(block.contains(NEW_TABLE) && block.toLowerCase().contains("ecos_ontology." + NEW_TABLE),
                "新表形态漂移：必须显式落到 ecos_ontology schema（ST07 5 引擎权威）");

        // MC01 主键 = VARCHAR(36) PRIMARY KEY（禁 BIGSERIAL / gen_random_uuid 等）
        assertTrue(Pattern.compile(
                "id\\s+VARCHAR\\(\\s*36\\s*\\)\\s+PRIMARY\\s+KEY", Pattern.CASE_INSENSITIVE)
                .matcher(block).find(),
                "新表 " + NEW_TABLE + " 主键形态须为 id VARCHAR(36) PRIMARY KEY（MC01 应用侧 UUID；本护栏防 BIGSERIAL / gen_random_uuid 等）");
    }

    @Test
    @DisplayName("V168.1 无 MC 违例：无 :: PG 私有转型、无 JSONB、无 BIGSERIAL、无 gen_random_uuid()")
    void noMcViolationsInV168Dot1() throws IOException {
        Path migrationDir = resolveMigrationDir();
        String sql = Files.readString(migrationDir.resolve(V168_1), StandardCharsets.UTF_8);

        // 排除注释行（-- 前全程）后的语句面
        for (String line : sql.split("\\R", -1)) {
            String trimmed = line.trim();
            if (trimmed.startsWith("--") || trimmed.isEmpty()) continue;
            assertFalse(line.matches("(?is).*::\\s*[a-z].*"),
                    "V168.1 残留 PG 私有 :: 转型（MC03 红线）: " + trimmed);
            assertFalse(line.matches("(?is).*jsonb.*"),
                    "V168.1 引入 JSONB（MC02 红线，金额/快照须 TEXT）: " + trimmed);
            assertFalse(line.matches("(?is).*bigserial.*"),
                    "V168.1 引入 BIGSERIAL（MC01 主键形态红线）: " + trimmed);
            assertFalse(line.matches("(?is).*gen_random_uuid\\s*\\(\\s*\\).*"),
                    "V168.1 引入 gen_random_uuid()（MC01 主键须应用侧 UUID）: " + trimmed);
            assertFalse(line.matches("(?is).*PARTITION\\s+BY.*"),
                    "V168.1 引入 PARTITION BY（MC03 红线）: " + trimmed);
            assertFalse(line.matches("(?is).*CREATE\\s+POLICY.*"),
                    "V168.1 引入 CREATE POLICY（本域 DDL 不含 row-level 策略，security 域专属）: " + trimmed);
        }
    }

    @Test
    @DisplayName("V168.1 §4 COMMENT ON TABLE 标记旧表停写（IR03 只加不删 佐证）")
    void oldTableMarkedStoppedWriting() throws IOException {
        Path migrationDir = resolveMigrationDir();
        String sql = Files.readString(migrationDir.resolve(V168_1), StandardCharsets.UTF_8);
        Pattern commentPat = Pattern.compile(
                "COMMENT\\s+ON\\s+TABLE\\s+[\\w\\.]*(metric_definition)\\s+IS\\s+'[^']*(停写|只读|read-only)[^']*'",
                Pattern.CASE_INSENSITIVE);
        assertTrue(commentPat.matcher(sql).find(),
                "V168.1 缺旧表停写登记 COMMENT ON TABLE（W77/R-4 a+b 明列：旧表停写，新写落 v2）—— 已落库面漂移");
    }

    @Test
    @DisplayName("反向护栏：V168.1 不给旧表 UNIQUE 索引改成条件索引（MC03 partial index 红线）")
    void noConditionalUniqueIndex() throws IOException {
        Path migrationDir = resolveMigrationDir();
        String sql = Files.readString(migrationDir.resolve(V168_1), StandardCharsets.UTF_8);
        // 老表 id 是 VARCHAR(64) 无法 ADD UNIQUE；新表 UNIQUE INDEX 已用 (code, is_deleted) 复合
        // 断言没有 WHERE 尾随（partial index）
        for (String line : sql.split("\\R", -1)) {
            String trimmed = line.trim();
            if (trimmed.startsWith("--") || trimmed.isEmpty()) continue;
            if (!trimmed.toUpperCase().contains("CREATE")) continue;
            // CREATE UNIQUE INDEX IF NOT EXISTS ... WHERE ... 形态 = partial
            if (trimmed.toUpperCase().contains("UNIQUE INDEX")
                    && trimmed.toUpperCase().contains("WHERE")) {
                fail("V168.1 引入条件（partial）UNIQUE INDEX（MC03 红线）: " + trimmed);
            }
        }
    }

    // ─────────────────────────── 工具 ───────────────────────────

    private static <T> T[] concat(T[] a, T[] b) {
        T[] res = java.util.Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, res, a.length, b.length);
        return res;
    }

    private Path resolveMigrationDir() {
        Path cur = Path.of(".").toAbsolutePath().normalize();
        for (int i = 0; i < 12 && cur != null; i++) {
            if (Files.isDirectory(cur.resolve("docs")) && Files.isDirectory(cur.resolve("ecos_backend"))) {
                Path candidate = cur.resolve("ecos_backend").resolve("gateway").resolve("src")
                        .resolve("main").resolve("resources").resolve("db").resolve("migration");
                if (Files.isDirectory(candidate)) {
                    return candidate;
                }
            }
            cur = cur.getParent();
        }
        fail("无法从 CWD 上溯到 ECOS 仓根（含 docs + ecos_backend）以定位 db/migration 单源目录");
        return null;
    }

    private String extractCreateTableBlock(String sql, String table) {
        Pattern anchor = Pattern.compile(
                "CREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?[\\w\\.]*" + Pattern.quote(table) + "\\b",
                Pattern.CASE_INSENSITIVE);
        String[] lines = sql.split("\\R", -1);
        int start = -1;
        for (int i = 0; i < lines.length; i++) {
            if (anchor.matcher(lines[i]).find()) {
                start = i;
                break;
            }
        }
        if (start < 0) return null;
        StringBuilder blk = new StringBuilder();
        for (int i = start; i < lines.length; i++) {
            String line = lines[i];
            blk.append(line).append('\n');
            if (line.trim().endsWith(");")) return blk.toString();
        }
        return null;
    }

    private static boolean containsToken(String block, String token) {
        Pattern p = Pattern.compile("(?<![A-Za-z0-9_])" + Pattern.quote(token) + "(?![A-Za-z0-9_])",
                Pattern.CASE_INSENSITIVE);
        return p.matcher(block).find();
    }
}
