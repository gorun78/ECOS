package com.chinacreator.gzcm.engine.data.quality;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F02-08 / W45 — V166 迁移脚本 legacy → 治理字段映射逐列断言（M0）。
 *
 * <p>规格源：docs/30-设计/详细设计-02 §F02-08-4 字段映射。
 * <ul>
 *   <li>{@code rule_type → rule_type 原值保留 + category 粗映射}</li>
 *   <li>{@code params / config_json → parameters_json}</li>
 *   <li>{@code target_entity → target_table}</li>
 *   <li>{@code target_field → target_field}</li>
 *   <li>status 一律 {@code 'DRAFT'} 待人工签核</li>
 * </ul>
 *
 * <p>本测试读源码 SQL（不连库）：V166 里 MUST 有至少 1 条
 * {@code INSERT INTO ecos_dq.dq_rule ... SELECT ... FROM public.ecos_dq_rule ...}
 * 并显式包含上述 5 个字段映射（V166 用 A/B 双源形自适应分支，任一分支命中即可）。
 */
@DisplayName("F02-08 / W45：V166 dq_rule legacy 迁移字段映射逐列断言")
class DqLegacyMigrationParityTest {

    // V200 治理后 DDL 单源目录（数据库访问规范 v1.4 §八），仓库根相对
    private static final Path MIGRATION_V166 = Path.of(
            "docs/50-db_script/02-flyway-migration/gateway/V166__dq_rule_legacy_migration.sql");

    @Test
    @DisplayName("V166 含 INSERT INTO 治理表 SELECT FROM legacy 源的批量迁移（≥1 条）")
    void governanceBatchFromLegacySource() throws IOException {
        String sql = readMigration();

        int governanceInserts = count(sql,
                Pattern.compile("INSERT\\s+INTO\\s+ecos_dq\\.dq_rule\\b", Pattern.CASE_INSENSITIVE));
        int selectsFromLegacy = count(sql,
                Pattern.compile("FROM\\s+public\\.ecos_dq_rule\\b", Pattern.CASE_INSENSITIVE));

        assertTrue(governanceInserts >= 1,
                "V166 MUST ≥1 条 INSERT INTO ecos_dq.dq_rule（治理表）;实际=" + governanceInserts);
        assertTrue(selectsFromLegacy >= 1,
                "V166 治理表批 MUST 由 public.ecos_dq_rule 读入;实际=" + selectsFromLegacy);
    }

    @Test
    @DisplayName("5 个 spec 字段映射全部显式出现在 V166（源侧投影 + 目侧赋值 + status DRAFT）")
    void fiveSpecColumnsAllMapped() throws IOException {
        String sql = readMigration();
        List<String> missing = new ArrayList<>();

        // rule_type → rule_type 原值保留（源侧 o.rule_type 出现在 SELECT 列）
        if (!has(sql, "o\\.rule_type"))                    missing.add("源侧 o.rule_type 投影");
        // rule_type → 目标 rule_type 列
        if (!has(sql, "\\brule_type\\b"))                  missing.add("目侧 rule_type 列");
        // rule_type → category 粗映射（CASE * → TECHNICAL/BUSINESS）
        if (!has(sql, "CASE\\s+WHEN\\s+o\\.rule_type\\s+IN\\s*\\([\\s\\S]*?THEN\\s+'TECHNICAL'\\s+ELSE\\s+'BUSINESS'"))
                 missing.add("rule_type→category CASE 粗映射");
        // params → parameters_json（参数聚合列）
        if (!has(sql, "\\bparameters_json\\b"))            missing.add("目侧 parameters_json 列（params 聚合）");
        // target_entity → target_table（源侧 target_entity 投影）
        if (!has(sql, "o\\.target_entity\\b"))             missing.add("源侧 o.target_entity → target_table 映射");
        // target_field → target_field（目侧 target_field 列字面量在治理表 INSERT 列表里）
        if (!sql.toUpperCase().contains("TARGET_FIELD"))   missing.add("目侧 target_field 列");
        // status 一律 DRAFT 字面量
        if (!has(sql, "'DRAFT'"))                           missing.add("status 字面量 'DRAFT'");

        assertTrue(missing.isEmpty(),
                "V166 治理表 INSERT 缺以下 F02-08-4 显式字段映射:\n  - "
                        + String.join("\n  - ", missing));
    }

    @Test
    @DisplayName("V166 头注 trace 本测试名 + 17 条 legacy 口径")
    void headerReferencesSpec() throws IOException {
        String sql = readMigration();
        assertTrue(sql.contains("DqLegacyMigrationParityTest"),
                "V166 头注 MUST trace 本测试（F02-08-4 验收标识）");
        assertTrue(has(sql, "\\b17\\b"),
                "V166 头注 MUST 提及 17 条 legacy rule（17→17 口径）");
    }

    // ─────────── helpers ───────────

    private static String readMigration() throws IOException {
        Path canonical = repoRoot().resolve(MIGRATION_V166);
        assertTrue(Files.isRegularFile(canonical), "V166 缺文件: " + canonical);
        return Files.readString(canonical, StandardCharsets.UTF_8);
    }

    /** 从 user.dir 上溯仓库根（含 .git）；兜底回溯找含 ecos_backend/ 的目录。 */
    private static Path repoRoot() {
        Path dir = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        Path root = dir;
        while (root != null) {
            if (Files.isRegularFile(root.resolve(".git")) || Files.isDirectory(root.resolve(".git"))) {
                return root;
            }
            root = root.getParent();
        }
        Path cur = dir;
        while (cur != null) {
            if (Files.isDirectory(cur.resolve("ecos_backend"))) {
                return cur;
            }
            cur = cur.getParent();
        }
        throw new IllegalStateException("未找到 ECOS 仓库根（缺 .git）");
    }

    private static int count(String text, Pattern p) {
        int n = 0;
        java.util.regex.Matcher m = p.matcher(text);
        while (m.find()) n++;
        return n;
    }

    private static boolean has(String text, String re) {
        return Pattern.compile(re, Pattern.CASE_INSENSITIVE).matcher(text).find();
    }
}
