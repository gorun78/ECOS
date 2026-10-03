package com.chinacreator.gzcm.engine.data;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * E.5 lint 纪律 ①「裸表名（无 schema 限定）」离线护栏（M1）。
 *
 * <p>D-18/ST07 铁律 §3.1 + D.2 data-domain single-source 约定：本册新落的 8 张 V* 迁移脚本
 * 里所有 CREATE/ALTER/INSERT/UPDATE/DELETE/DROP/TRUNCATE 命中表都必须带 schema 限定
 * （{@code ecos_data.}/{@code ecos_dw.}/{@code ecos_dq.}/{@code ecos_ontology.}/
 * {@code ecos_knowledge.}/{@code ecos_ai.}/{@code ecos_cognitive.}/{@code public.}），
 * 否则跨 schema 的默认 search_path 会在三档（standard/enterprise/ultimate）环境下漂移。
 *
 * <p><b>扫描面</b>：只扫 E.5 中本册新落的 8 张 V* 脚本
 * （V164.1/V165.1/V166/V167/V168/V169/V170/V171），不外推现有 190+ 存量脚本。
 * 理由：本批脚本是本册自实现产物（V167 DDL 已带限定，V164.1/V170/V171 等双限定/单限定
 * 也已一致），设 0 baseline 的 ratchet 干净；未来本册若新增 V* 脚本，向基线脚本集合
 * 追加即纳入本护栏，无需修改扫描逻辑。</p>
 *
 * <p><b>禁误伤</b>：</p>
 * <ul>
 *   <li>行首为 {@code --} 的 SQL 注释行跳过（顶注/尾注/回滚说明命中不应算）</li>
 *   <li>识别动词：{@code CREATE [UNIQUE] INDEX ... ON schema.table}、
 *       {@code CREATE [OR REPLACE] [MATERIALIZED] (TABLE|VIEW) [IF NOT EXISTS] schema.table}、
 *       {@code ALTER [TABLE] schema.table}、
 *       {@code (INSERT INTO|UPDATE|DELETE FROM|DROP [TABLE] [IF EXISTS]|TRUNCATE [TABLE]) [IF EXISTS] schema.table}</li>
 *   <li>抓动词后第一个标识符（允许嵌套 optional token，如 {@code IF NOT EXISTS}）；
 *       标识符含点（{@code .}）= 合规限定；否则为裸表名违规。</li>
 *   <li>无点号但被转义/内含别的 shape（如系统保留字）的正则是空白操作；实际命中率高、误伤低。</li>
 * </ul>
 *
 * <p><b>棘轮口径</b>：当前 unqualified count 必须 ≤ baseline（当前 0）—— 即所有命中都合规；
 * 任何新增裸表名判红。**不采用**"knownLegacy 白名单"体系：本册脚本是"新写的"（本窗口产物），
 * 未做 schema 限定就打回评审重写，不该成继受债务。</p>
 *
 * <p><b>不触库 / 不 Spring / 不联网</b>：纯文件读取 + 正则扫。落 {@code data-engine-impl}
 * 单测 M1；走本模块既有 {@link RlsInjectionGuardArchTest#resolveModuleSrcMainJava()}
 * 上溯仓根，与 {@link MigrationBasenameSingleRootArchTest} / {@link DdlComplianceLintTest}
 * / {@link PipelineDdlSingleSourcePairingTest} / {@link ActiveFlagUniquenessDdlTest}
 * 同一片单源目录 + 同一口语径。</p>
 */
@DisplayName("E.5 ① 本册 8 张 V* 脚本裸表名护栏（M1 / Ratchet / 0 baseline）")
class BareTableNameRatchetArchTest {

    /**
     * E.5 中本册新落的 8 张 V* 脚本（gateway/src/main/resources/db/migration/ 之下）。
     * 未来本册归档新增 V* 脚本，追加到这里即纳入扫描。
     */
    static final List<String> W60_SCOPE_SCRIPT_BASENAMES = List.of(
            "V164.1__datasource_credential_split.sql",
            "V165.1__dq_score_asset_repair.sql",
            "V166__dq_rule_legacy_migration.sql",
            "V167__biz_fact_model.sql",
            "V168__migrate_yaml_pipeline_task.sql",
            "V169__layer_carrier_registry.sql",
            "V170__pipeline_definition_single_source.sql",
            "V171__dq_active_flag_and_indexes.sql");

    /**
     * 基线：本批次 8 脚本内**裸表名**行数 = 0（2026-10-03 实测：DML/DDL 表标识符 100% 带限定）。
     * 只减不增（这里其实是"只从 0 减到 0"）；任何新增违规即刻红。
     */
    static final int W60_BASELINE_BARE_TABLE_LINES = 0;

    /** 动词 → 抓动词后第一个表标识符（允许跨 schema.dot.name，允许 IF [NOT] EXISTS 可选）。 */
    private static final Pattern BARE_KW = Pattern.compile(
            "(?i)(?:CREATE\\s+(?:UNIQUE\\s+)?INDEX\\s+[A-Za-z_][A-Za-z0-9_]*\\s+ON\\s+" // CREATE [UNIQUE] INDEX name ON <table>
                    + "|CREATE\\s+(?:OR\\s+REPLACE\\s+)?(?:MATERIALIZED\\s+)?(?:TABLE|VIEW)\\s+(?:IF\\s+(?:NOT\\s+)?EXISTS\\s+)?" // CREATE [MATERIALI] (TABLE|VIEW) [IF ...] <table>
                    + "|ALTER\\s+(?:TABLE|INDEX|SEQUENCE)\\s+"                                                  // ALTER [TABLE|INDEX|SEQUENCE] <ident>
                    + "|TRUNCATE\\s+(?:TABLE\\s+)?(?:IF\\s+EXISTS\\s+)?"                                        // TRUNCATE [TABLE] [IF EXISTS] <table>
                    + "|DROP\\s+(?:TABLE|INDEX|SEQUENCE|VIEW|SCHEMA|FUNCTION|MATERIALIZED\\s+VIEW)\\s+(?:IF\\s+EXISTS\\s+)?" // DROP [obj] [IF EXISTS] <ident>
                    + "|INSERT\\s+INTO\\s+"                                                                     // INSERT INTO <table>
                    + "|UPDATE\\s+(?:ONLY\\s+)?"                                                                // UPDATE [ONLY] <table>
                    + "|DELETE\\s+FROM\\s+"                                                                      // DELETE FROM <table>
                    + ")([A-Za-z_][A-Za-z0-9_]*)(\\.?)", Pattern.CASE_INSENSITIVE);

    @Test
    @DisplayName("8 本册 V* 脚本内 DML/DDL 表标识符一律 schema-限定（棘轮 baseline=0）")
    void bareTableName_inBookEightScripts_ratchet() throws IOException {
        Path migration = resolveMigrationDir();
        List<String> detail = new ArrayList<>();
        int hits = 0;
        for (String basename : W60_SCOPE_SCRIPT_BASENAMES) {
            Path p = migration.resolve(basename);
            assertTrue(Files.isRegularFile(p), "本册 V* 脚本缺失（E.5 表登记项应实存）: " + p);
            List<String> lines = Files.readAllLines(p);
            for (int i = 1; i <= lines.size(); i++) {
                String line = lines.get(i - 1);
                if (line.trim().isEmpty()) {
                    continue;
                }
                String t = line.trim();
                if (t.startsWith("--")) {
                    continue; // 单行 SQL 注释（顶注/回滚注释/纪律说明）跳过
                }
                Matcher m = BARE_KW.matcher(line);
                while (m.find()) {
                    String ident = m.group(1);
                    String dot = m.group(2);
                    if (dot != null && dot.contains(".")) {
                        // schema 限定（ecos_data.name / public.name 等） → 合规，跳过
                        continue;
                    }
                    // 完全排除 SQL 元动词误命中（例：DROP SCHEMA IF EXISTS public 抓 ident = public 但不是表）
                    if ("SCHEMA".equalsIgnoreCase(ident)) {
                        continue;
                    }
                    if (BARE_ID_WHITELIST.contains(ident.toUpperCase())) {
                        continue;
                    }
                    hits++;
                    detail.add(String.format("%s:%d  bare=%-24s line: %s",
                            basename, i, ident, verbSnippet(line, m.start())));
                }
            }
        }
        assertFalse(hits > W60_BASELINE_BARE_TABLE_LINES,
                "[E.5 ① 裸表名护栏] 违规回升：当前 " + hits + " > baseline " + W60_BASELINE_BARE_TABLE_LINES
                        + "（ST07 schema 归属要求 DML/DDL 表标识符一律带限定；明细）:\n  "
                        + String.join("\n  ", detail.subList(0, Math.min(detail.size(), 30)))
                        + "\n——新增/修改本册 8 V* 脚本时，所有 CREATE/ALTER/INSERT/UPDATE/DELETE/DROP/TRUNCATE "
                        + "*必须* 在表标识符前声明 schema。参考合规形如：`ALTER TABLE ecos_data.ecos_pipeline_definition ...`。");

        // 白线：至少扫到一段带限定的 DML/DDL 命中，防正则退化后空转判绿
        String sample = Files.readString(migration.resolve("V170__pipeline_definition_single_source.sql"));
        assertTrue(sample.contains("CREATE TABLE IF NOT EXISTS ecos_data.ecos_pipeline_definition"),
                "白线撤下信号：V170 应含 CREATE TABLE IF NOT EXISTS ecos_data.ecos_pipeline_definition；"
                        + "若已改名请同步更新本断言");
    }

    /**
     * 极少数情况下正则抓到的"标识符"其实是 SQL 关键字字面（例：无表目标动词），不视为裸表名：
     * ALTER/CREATE/DROP 之前少有 such 场景，此处防御性收口。
     */
    private static final List<String> BARE_ID_WHITELIST = List.of(
            "SCHEMA", "SCHEMAS", "INDEX", "INDEXES", "SEQUENCE", "SEQUENCES",
            "TABLE", "TABLES", "VIEW", "VIEWS", "FUNCTION", "FUNCTIONS");

    private static String verbSnippet(String line, int verbEnd) {
        String tail = line.substring(Math.min(verbEnd, line.length()), Math.min(line.length(), verbEnd + 32));
        return tail.endsWith("\n") ? tail.substring(0, tail.length() - 1) : tail;
    }

    private Path resolveMigrationDir() {
        Path srcMain = RlsInjectionGuardArchTest.resolveModuleSrcMainJava();
        Path cur = srcMain;
        for (int i = 0; i < 12 && cur != null; i++) {
            if (Files.isDirectory(cur.resolve("docs")) && Files.isDirectory(cur.resolve("ecos_backend"))) {
                return cur.resolve("ecos_backend/gateway/src/main/resources/db/migration");
            }
            cur = cur.getParent();
        }
        fail("无法从 " + srcMain + " 上溯到 ECOS 仓根，以定位 gateway/.../db/migration/");
        return null; // unreachable
    }
}
