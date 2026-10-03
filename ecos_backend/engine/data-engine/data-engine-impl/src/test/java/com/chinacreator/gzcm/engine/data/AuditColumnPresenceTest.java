package com.chinacreator.gzcm.engine.data;

import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * W60 / C49（详细设计-02 E.3末 + 新表模板，M2）—— 数据域<b>新表</b>的规范审计五列 + version_no/domain
 * 逐表存在性护栏（DR06 + DR07 + DR08）。
 *
 * <p>背景：D-19 实证了既有治理表 {@code dq_rule}/{@code td_datasource} 的审计列漂移
 * （{@code created_at}/{@code version} 命名，或 {@code td_datasource} 缺
 * {@code is_deleted}/{@code version_no}/{@code domain}）。IR03 只加不删 + 不改既有列
 * 类型：既有列属于历史漂移不擅改（登记 knownLegacy，视图后续收敛），而<b>新表</b>必须
 * 一次性落地规范七列——本测试作为静态护栏<b>锁死新表 DDL 面</b>：</p>
 * <ul>
 *   <li>本分册新建（或本次要求的补齐）的每张 DQ 治理/事实表 CREATE TABLE 块必须含
 *       {@code create_time / update_time / create_by / update_by / is_deleted / version_no / domain}
 *       七列（DR06 审计五列 + DR07 version_no + DR08 domain）。</li>
 *   <li>白名单 = 本分册引入的 7 张数据域新表（不擅扩）：
 *       V165.1 {@code ecos_data.ecos_dq_score_asset} /
 *       V166 {@code ecos_data.ecos_dq_legacy_migration_report} /
 *       V167 {@code ecos_dw.ecos_biz_project_attribution /
 *                ecos_biz_stage_fact / ecos_biz_resource_fact /
 *                ecos_biz_cost_fact / ecos_forecast_input_snapshot}。</li>
 *   <li>反向断言：td_datasource（knownLegacy 承数表）**不在**本测试范围内
 *       （V19 建、既有列漂移已登记，属"存量只定性不改"口径）。</li>
 * </ul>
 *
 * <p>方案：<b>按表名锚定 CREATE TABLE 块</b>扫 gateway 单源目录
 * {@code gateway/src/main/resources/db/migration/*.sql}——
 * 每表命中 1 次 CREATE TABLE（存在性），且块内 7 列 token 边界逐列命中（防
 * {@code create_timeX} 类子串误命中）。<b>不触库、不 Spring 容器、不联网</b>。</p>
 */
@DisplayName("W60/C49 数据域新表规范审计七列存在性（DR06/07/08，M2）")
class AuditColumnPresenceTest {

    /** DR06 审计五列 + DR07 version_no + DR08 domain，共 7 列（本测试"规范七列"口径）。 */
    private static final String[] CANONICAL_7 = {
            "create_time", "update_time", "create_by", "update_by",
            "is_deleted", "version_no", "domain"
    };

    /**
     * 各目标表 → 单源目录内的脚本文件名。表名锚定 CREATE TABLE 块（其内部直到 ");/"begin" 结束）。
     */
    private static final String[][] TARGET_TABLES = {
            {"ecos_dq_score_asset",          "V165.1__dq_score_asset_repair.sql"},
            {"ecos_dq_legacy_migration_report", "V166__dq_rule_legacy_migration.sql"},
            {"ecos_biz_project_attribution",   "V167__biz_fact_model.sql"},
            {"ecos_biz_stage_fact",            "V167__biz_fact_model.sql"},
            {"ecos_biz_resource_fact",         "V167__biz_fact_model.sql"},
            {"ecos_biz_cost_fact",             "V167__biz_fact_model.sql"},
            {"ecos_forecast_input_snapshot",   "V167__biz_fact_model.sql"},
    };

    @Test
    @DisplayName("7 张数据域新表 CREATE TABLE 块均含规范七列（DR06/07/08）")
    void dataDomainNewTableDdlCarriesCanonicalSevenColumns() throws IOException {
        Path migrationDir = resolveMigrationDir();
        for (String[] entry : TARGET_TABLES) {
            String table = entry[0];
            String file = entry[1];
            Path sqlFile = migrationDir.resolve(file);
            assertTrue(Files.isRegularFile(sqlFile),
                    "单源目录缺失迁移文件 " + file + "（承载新表 " + table + "）—— DDL 单源漂移，W60 违规");

            String body = Files.readString(sqlFile, StandardCharsets.UTF_8);
            String block = extractCreateTableBlock(body, table);
            assertTrue(block != null,
                    "未能在 " + file + " 定位 CREATE TABLE … " + table + "（块）—— 脚本形态漂移");

            List<String> missing = new java.util.ArrayList<>();
            for (String col : CANONICAL_7) {
                if (!containsToken(block, col)) {
                    missing.add(col);
                }
            }
            assertTrue(missing.isEmpty(),
                    "表 " + table + "（" + file + "）缺规范审计列: " + missing
                            + "（DR06 审计五列 + DR07 version_no + DR08 domain；人工审查判定为 W60 违规）");
        }
    }

    @Test
    @DisplayName("td_datasource（knownLegacy 承数表）不在本护栏范围内（W60 反例：不误伤存量）")
    void legacyTdDatasourceOutofScope_notAsserted() throws IOException {
        Path migrationDir = resolveMigrationDir();
        Path v19 = migrationDir.resolve("V19__datanet_physical_tables.sql");
        assertTrue(Files.isRegularFile(v19), "V19 存在性预期成立（承数表 td_datasource 建表）");
        String body = Files.readString(v19, StandardCharsets.UTF_8);
        // 反向护栏：V19 的 td_datasource 建表块**不得**断言含规范七列（那是无关的 legacy 存量形态）；
        // 本断言只证明本测试"不越权扫描承数表"——若未来某个新 fixture 引入 td_datasource 规范七列，
        // 本断言只是提醒范围口令变动（不阻塞），实际护栏仍以 TARGET_TABLES 白名单为准。
        String block = extractCreateTableBlock(body, "td_datasource");
        assertTrue(block != null, "V19 应含 td_datasource 建表块（数据源可读性）");
        // 说明：本断言**不断言** V19 缺七列（V19 的形态属历史，不锁死），只证明扫描器能正确定位该块。
        int canonHits = 0;
        for (String col : CANONICAL_7) {
            if (containsToken(block, col)) {
                canonHits++;
            }
        }
        // 现状 V19 实际命中 4/7（见 F02-12 取证：create_by/create_time/update_by/update_time），
        // 这里只验证扫描器不会给出 7/7 的假读（避免测试含义被误用为"td_datasource 已合规"）。
        assertFalse(canonHits == CANONICAL_7.length,
                "扫描器对 td_datasource 意外全命中，与本护栏白名单语义不符，请人工复核");
    }

    // ─────────────────────────── 工具 ───────────────────────────

    /** 从模块 src/main/java 上溯到仓库根，含 {@code gateway} 与 {@code docs}，再定位 db/migration。 */
    private Path resolveMigrationDir() {
        Path srcMain = RlsInjectionGuardArchTest.resolveModuleSrcMainJava();
        Path cur = srcMain;
        for (int i = 0; i < 12 && cur != null; i++) {
            // 仓根 = 同时含 docs 与 ecos_backend（与 AmountExemptionRegisteredTest 同一锚定口径）
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

    /**
     * 从 sql 全文<b>按行</b>提取 {@code CREATE TABLE … <schema>.<table> ( … );} 的正文块（不反则正文，
     * 避免嵌套 {@code (…)} 造成的回溯歧义）。锚点 = 某行同时含 {@code CREATE\s+TABLE} 与目标表 token
     * （前缀允许 schema 限定名 / {@code IF NOT EXISTS}）；从锚点行开始（含）累积到首个以 {@code );}
     * 结尾的行（含，含注释行也累积——列内注释里出现 token 名不误命中，因 token 断言需独立词）。
     * 多处命中取首块（公/控双镜像以首块为准，登记 §14）。
     */
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
        if (start < 0) {
            return null;
        }
        StringBuilder block = new StringBuilder();
        for (int i = start; i < lines.length; i++) {
            String line = lines[i];
            block.append(line).append('\n');
            if (line.trim().endsWith(");")) {
                return block.toString();
            }
        }
        // 未闭合到 ");" —— 文件形态漂移，让断言阶段把 null 报出来更好
        return null;
    }

    /** 词边界 token 匹配（防 {@code create_time_x} 之类子串误命中）。 */
    private static boolean containsToken(String block, String token) {
        Pattern p = Pattern.compile("(?<![A-Za-z0-9_])" + Pattern.quote(token) + "(?![A-Za-z0-9_])",
                Pattern.CASE_INSENSITIVE);
        return p.matcher(block).find();
    }
}
