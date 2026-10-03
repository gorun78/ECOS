package com.chinacreator.gzcm.engine.data;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * W60/C49 M2 扩展形态 B：V168 迁移报告 + V170 管道单源两表的规范审计五列 +
 * version_no + domain 补齐面（DR06/07/08，同主型 AuditColumnPresenceTest 门禁）。
 * V170 头注已点名门禁标识 = W60/C49 AuditColumnPresenceTest，CREATE + ALTER ADD COLUMN
 * 混合形态需扩展提取器（主类只读 CREATE ... ');' 块），故独立扩展。
 * 主类的 td_datasource 白名单反例不重复（正交面）。
 */
@DisplayName("W60/C49 V168/V170 数据域 V* 补写表规范审计七列（DR06/07/08，M2 扩展形态 B）")
class AuditColumnPresenceExtendedTest {

    private static final String[] CANONICAL_7 = {
            "create_time", "update_time", "create_by", "update_by",
            "is_deleted", "version_no", "domain"
    };

    private static final String[][] TARGET_TABLES = {
            {"ecos_pipeline_yaml_migration_report", "V168__migrate_yaml_pipeline_task.sql"},
            {"ecos_pipeline_definition",            "V170__pipeline_definition_single_source.sql"},
            {"ecos_pipeline_execution",             "V170__pipeline_definition_single_source.sql"},
    };

    @Test
    @DisplayName("V168+V170 三张表的 CREATE+ALTER 并集均含规范七列")
    void v168_v170TablesCarryCanonicalSevenColumns() throws IOException {
        Path migrationDir = resolveMigrationDir();
        for (String[] entry : TARGET_TABLES) {
            String table = entry[0];
            String file = entry[1];
            Path sqlFile = migrationDir.resolve(file);
            assertTrue(Files.isRegularFile(sqlFile),
                    "单源目录缺失迁移文件 " + file + "（承载表 " + table + "）—— DDL 单源漂移，W60 违规");
            String body = Files.readString(sqlFile, StandardCharsets.UTF_8);
            String block = extractCombinedBlock(body, table);
            assertTrue(block != null,
                    "未能在 " + file + " 定位 CREATE TABLE " + table
                            + "（含后续 ALTER 合并块）—— 脚本形态漂移");
            List<String> missing = new ArrayList<>();
            for (String col : CANONICAL_7) {
                if (!containsToken(block, col)) {
                    missing.add(col);
                }
            }
            assertTrue(missing.isEmpty(),
                    "表 " + table + "（" + file + "）在 CREATE 块 + 该表 ALTER 并集内缺规范审计列: "
                            + missing
                            + "（DR06 审计五列 + DR07 version_no + DR08 domain；"
                            + "V170 头注 2026-09-30 四补收口：'七列齐补'）");
        }
    }

    @Test
    @DisplayName("白线 V170 ALTER TABLE ADD COLUMN IF NOT EXISTS domain/version_no 字面实存（防形态 B 被绕过退化）")
    void v170ShapeBWhiteLine_alterColumnsExist() throws IOException {
        Path migrationDir = resolveMigrationDir();
        Path v170 = migrationDir.resolve("V170__pipeline_definition_single_source.sql");
        assertTrue(Files.isRegularFile(v170), "V170 应实存（E.5 表登记项）");
        String body = Files.readString(v170, StandardCharsets.UTF_8);
        assertTrue(Pattern.compile("(?im)^\\s*ALTER\\s+TABLE\\s+ecos_data\\.ecos_pipeline_definition\\s+"
                        + "ADD\\s+COLUMN\\s+IF\\s+NOT\\s+EXISTS\\s+domain\\b")
                .matcher(body).find(),
                "白线撤下信号：V170 应含 ALTER TABLE ecos_data.ecos_pipeline_definition ADD COLUMN IF NOT EXISTS domain；"
                        + "若已改用别方式补写 DR08 请同步下调本白线");
        assertTrue(Pattern.compile("(?im)^\\s*ALTER\\s+TABLE\\s+ecos_data\\.ecos_pipeline_execution\\s+"
                        + "ADD\\s+COLUMN\\s+IF\\s+NOT\\s+EXISTS\\s+version_no\\b")
                .matcher(body).find(),
                "白线撤下信号：V170 应含 ALTER TABLE ecos_data.ecos_pipeline_execution ADD COLUMN IF NOT EXISTS version_no；"
                        + "若已改用别方式补写 DR07 请同步下调本白线");
    }

    // ─────────────────────────── 工具 ────────────────────

    /**
     * 提取「CREATE TABLE … <table> … ');」+「该表后续所有 ALTER TABLE ADD COLUMN」并集。
     * 与主类 {@code AuditColumnPresenceTest#extractCreateTableBlock} 差异：
     * 主类只看 CREATE 块（形态 A），本类为形态 B（CREATE + ALTER 补列）追加 ALTER 行。
     * 一条 ALTER TABLE 只针对单表；限定 keyword = 该表裸名（禁 schema 前缀，通常无）。
     */
    private String extractCombinedBlock(String sql, String table) {
        String quote = Pattern.quote(table);
        Pattern anchor = Pattern.compile(
                "CREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?[\\w\\.]*" + quote + "\\b",
                Pattern.CASE_INSENSITIVE);
        String[] lines = sql.split("\\R", -1);
        int start = -1;
        int end = -1;
        for (int i = 0; i < lines.length; i++) {
            if (anchor.matcher(lines[i]).find()) {
                start = i;
                for (int j = i; j < lines.length; j++) {
                    if (lines[j].trim().endsWith(");")) {
                        end = j;
                        break;
                    }
                }
                if (end < 0) {
                    return null;
                }
                break;
            }
        }
        if (start < 0 || end < 0) {
            return null;
        }
        StringBuilder block = new StringBuilder();
        for (int i = start; i <= end; i++) {
            block.append(lines[i]).append('\n');
        }
        // ALTER TABLE [schema.]<table> ADD COLUMN [IF NOT EXISTS] <col> …
        Pattern alter = Pattern.compile(
                "(?i)\\s*ALTER\\s+TABLE\\s+((?:[\\w]+\\.)?)" + quote
                        + "\\s+ADD\\s+COLUMN\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?[\\w]+\\b.*",
                Pattern.CASE_INSENSITIVE);
        for (int i = end + 1; i < lines.length; i++) {
            if (alter.matcher(lines[i]).find()) {
                block.append(lines[i]).append('\n');
            }
        }
        return block.toString();
    }

    /** 词边界 token 匹配（与 {@link AuditColumnPresenceTest} 同口径，防 create_timeX 之类子串误命中）。 */
    private static boolean containsToken(String block, String token) {
        Pattern p = Pattern.compile("(?<![A-Za-z0-9_])" + Pattern.quote(token) + "(?![A-Za-z0-9_])",
                Pattern.CASE_INSENSITIVE);
        return p.matcher(block).find();
    }

    /** 与 {@link AuditColumnPresenceTest} 同一口径的仓根定位。 */
    private Path resolveMigrationDir() {
        Path srcMain = RlsInjectionGuardArchTest.resolveModuleSrcMainJava();
        Path cur = srcMain;
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
        fail("无法从 " + srcMain + " 上溯到 ECOS 仓根（含 docs + ecos_backend）以定位 db/migration 单源目录");
        return null; // unreachable
    }
}
