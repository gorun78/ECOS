package com.chinacreator.gzcm.gateway.arch;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * W59 / M1《DdlComplianceLintTest》— 数据域新增 DDL 的空库合规 lint（MC01/MC02）。
 *
 * <p>设计 §E.2 / §E.5 / §7.1 W59：DDL 违规成片（{@code gen_random_uuid()} /
 * {@code JSONB} / {@code BIGSERIAL|SERIAL}）必须收敛为应用侧 UUID 主键（MC01）、
 * TEXT 承载 JSON（MC02，新表禁用 JSONB）、禁止自增列（MC02/可移植性）。
 * 状态修订（E.2 与 V165.1/V167/V169/V170/V171/V241/V247/V248/V260 落定版规则，
 * §14.4）明确数据域 E.2/E.5 新增表 DDL 必须全合规。</p>
 *
 * <p>本测试守住一条真实不变量：<b>数据域新增 DDL 批次</b>（E.2/E.5 与 F02 入驻的
 * 控制面/事实表/载体/写通道/凭据类建表脚本）的 <b>非注释 SQL 行</b>中，
 * 不得出现任何 {@code gen_random_uuid}（MC01 应用侧 UUID）、
 * {@code SERIAL/BIGSERIAL/SMALLSERIAL}（MC02 自增列不可移植）、
 * {@code JSONB}（MC02 新表用 TEXT）三类红线 token。命中即 FAIL。</p>
 *
 * <p>边界：本测试只 lint 本批次（设计 doc 承诺合规的数据域新表 DDL），不侵入
 * 含大量历史 JSONB/自增/双镜像的 <b>knownLegacy 存量脚本</b>（如 V50/V101/V124/…
 * 旧知识库/认知/工作流/agent 表），后者 W59 余项排行由 db-migration-lint.ps1
 * 全局基线另行管理，不在本 M1 单元函数库约束之内——避免本测试因存量既定资产漂移。</p>
 */
@DisplayName("W59/M1：数据域新增 DDL 单源脚本 MC01/MC02 合规 lint")
class DdlComplianceLintTest {

    private static final String MIGRATION_REL =
            "docs/50-db_script/02-flyway-migration/gateway";

    /**
     * 数据域 E.2/E.5 与 F02 入驻本批次的单源 DDL 脚本（设计 doc E.5 版本清单 + F02-03/-05/-11）。
     * 这些脚本承载数据域新增 ECOS 控制面/DQ/事实/载体/写通道/凭据表，必须 MC01/MC02 全合规。
     */
    private static final List<String> STRICT_DATA_DOMAIN_DDL_SCRIPTS = List.of(
            "V164.1__datasource_credential_split.sql",   // F02-01/W52 凭据拆分
            "V165.1__dq_score_asset_repair.sql",         // F02-08/E.3 dq_score_asset 补表
            "V167__biz_fact_model.sql",                  // F02-06/E.2 业务事实五表
            "V168__migrate_yaml_pipeline_task.sql",      // F02-03/W44 YAML→definition
            "V169__layer_carrier_registry.sql",          // F02-05/E.4 载体登记扩展
            "V171__dq_active_flag_and_indexes.sql",      // F02-06-3 is_active/索引
            "V241__td_datasource_add_is_default_remark.sql", // F02 数据源默认标识
            "V247__td_datasource_credential_enc.sql",    // F02-01/W52 凭据加密列
            "V248__f02_11_lineage_incremental_watermark.sql", // F02-11 血缘增量水位
            "V260__f02_05_layer_carrier_write_channel.sql"    // F02-05 载体写通道
    );

    /**
     * 已知 legacy 镜像类遗留，允许 <b>仅</b> JSONB 保留（MC02 只约束新表；V170 现网镜像形态 +
     * V166 迁移诊断 NOTISE 内提及 JSONB），但 <b>仍禁</b> gen_random_uuid 与 SERIAL。
     * 白名单逐列登记理由（W59 收口 §14.4 定版规则）：
     * <ul>
     *   <li>V166__dq_rule_legacy_migration.sql：迁移脚本，源头 (params JSONB) 列不动、目标写 TEXT；
     *       行 94 出现的 "params(JSONB)" 只是 PL/pgSQL DO 块内的诊断文本，非新表列声明。</li>
     *   <li>V170__pipeline_definition_single_source.sql：补写单源（W58），镜像现网
     *       {@code definition JSONB} 列（knownLegacy，MC02 只约束新表），行 23 保留；
     *       合规形态同时在行 58 与行 79 加 {@code definition_json TEXT} 走 forward-progress 双写。</li>
     * </ul>
     */
    private static final List<String> LEGACY_JSONB_TOLERATED_SCRIPTS = List.of(
            "V166__dq_rule_legacy_migration.sql",
            "V170__pipeline_definition_single_source.sql"
    );

    /** MC01：库内 gen_random_uuid 默认值（主键/唯一键须应用侧 UUID）。 */
    private static final Pattern GEN_RANDOM_UUID = Pattern.compile(
            "\\bgen_random_uuid\\b", Pattern.CASE_INSENSITIVE);

    /** MC02：自增列（SERIAL/BIGSERIAL/SMALLSERIAL，跨库不可移植 + 禁 gen_random_uuid 同族）。 */
    private static final Pattern SERIAL_COLUMN = Pattern.compile(
            "\\b(?:bigserial|smallserial|serial)\\b", Pattern.CASE_INSENSITIVE);

    /** MC02：新表禁用 JSONB（用 TEXT 承载，禁参与 WHERE/索引）。 */
    private static final Pattern JSONB = Pattern.compile(
            "\\bjsonb\\b", Pattern.CASE_INSENSITIVE);

    @Test
    @DisplayName("数据域 E.2/E.5 新增 DDL 非注释行 0 处 gen_random_uuid/SERIAL（严格）+ 仅白名单脚本容许 JSONB 遗留")
    void dataDomainDdlBatchIsMcCompliant() throws IOException {
        Path migrationDir = findRepoRoot().resolve(MIGRATION_REL);
        assertTrue(Files.isDirectory(migrationDir), "缺迁移单源目录: " + migrationDir);

        // ① 先断言批次文件确实全部存在于单源目录（否则脚本被移走/改名即漂移，立刻 FAIL）
        List<String> allScripts = new ArrayList<>(STRICT_DATA_DOMAIN_DDL_SCRIPTS);
        allScripts.addAll(LEGACY_JSONB_TOLERATED_SCRIPTS);
        List<String> missingScripts = new ArrayList<>();
        for (String script : allScripts) {
            if (!Files.isRegularFile(migrationDir.resolve(script))) {
                missingScripts.add(script);
            }
        }
        assertTrue(missingScripts.isEmpty(),
                "W59/M1：以下数据域 DDL 单源脚本缺失（DDL 单源漂移，铁律 v2.0 §3.1）：\n"
                        + String.join("\n", missingScripts));

        // ② 逐脚本 lint 非注释行三类红线 token
        List<String> violations = new ArrayList<>();
        for (String script : allScripts) {
            boolean jsonbTolerated = LEGACY_JSONB_TOLERATED_SCRIPTS.contains(script);
            List<String> lines = Files.readAllLines(migrationDir.resolve(script), StandardCharsets.UTF_8);
            int lin = 0;
            for (String raw : lines) {
                lin++;
                String t = raw.trim();
                if (t.startsWith("--") || t.startsWith("/*") || t.startsWith("*")) {
                    continue; // 注释行豁免（合规标注常出现在注释里）
                }
                if (GEN_RANDOM_UUID.matcher(raw).find()) {
                    violations.add(script + ":" + lin + ": gen_random_uuid (MC01) — " + t);
                }
                if (SERIAL_COLUMN.matcher(raw).find()) {
                    violations.add(script + ":" + lin + ": SERIAL (MC02) — " + t);
                }
                if (JSONB.matcher(raw).find() && !jsonbTolerated) {
                    violations.add(script + ":" + lin + ": JSONB (MC02, 未在白名单) — " + t);
                }
            }
        }
        assertTrue(violations.isEmpty(),
                "W59/M1：数据域 E.2/E.5 新增 DDL 非注释行命中 gen_random_uuid/SERIAL/JSONB"
                        + "（MC01/MC02 红线，须应用侧 UUID + TEXT 承载 + 禁自增列）：\n"
                        + String.join("\n", violations));
    }

    static Path findRepoRoot() {
        Path dir = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        Path cursor = dir;
        while (cursor != null) {
            if (Files.isDirectory(cursor.resolve(".git"))
                    || Files.exists(cursor.resolve(".git"))) {
                return cursor;
            }
            cursor = cursor.getParent();
        }
        cursor = dir;
        while (cursor != null) {
            if (Files.exists(cursor.resolve("pom.xml"))) {
                return cursor;
            }
            cursor = cursor.getParent();
        }
        throw new IllegalStateException("无法从 user.dir=" + dir + " 定位仓库根（.git/pom.xml 均缺失）");
    }
}
