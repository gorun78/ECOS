package com.chinacreator.gzcm.workspace.scenario;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * F07-20 场景 7 表 DDL 合规重建（MC01/MC02/MC03/DR06-08/IR03/ST07/§四附则1）— 静态扫描门禁。
 *
 * <p>合规面（可在此纯静态层证明，无需 PG）：
 * <ul>
 *   <li><b>IR03</b>：V205~V212 正文（剥注释后）<b>零</b> DROP TABLE/COLUMN/INDEX/CONSTRAINT/SEQUENCE（V213 视图只加只读，不含 DROP）；</li>
 *   <li><b>MC01</b>：零 {@code gen_random_uuid()}（应用侧 UUID 生成分配）；PK 均 {@code VARCHAR(36)}；</li>
 *   <li><b>MC02</b>：零 {@code JSONB}（历史 JSON 载荷统一 {@code _json TEXT}）；</li>
 *   <li><b>MC03</b>：零 {@code UNIQUE (…) WHERE …} partial index（多库兼容）；</li>
 *   <li><b>DR06-08</b>：每个 CREATE TABLE 段均含 create_time/update_time/create_by/update_by/is_deleted/version_no/domain 7 基线列；</li>
 *   <li><b>§附则1 裸表名</b>：CREATE TABLE 后落 {@code public.} 前缀（ST07 控制域现基线）。</li>
 * </ul>
 *
 * <p><b>不适用此门禁</b>（需真库 pg_catalog 元数据，属 P-2/P-3 载体建成后再跑 IT 层；本窗不虚构）：
 * 行数校验、{@code information_schema} 字段元数据断言、种子数据剥离行数断言（E-6）。
 * 该部分在 F07-20 验收原文已注"读 pg_catalog 元数据（只读）"。</p>
 *
 * <p>发现镜像文件中断链（DDL 单源目录 gateway/src/main/resources/db/migration/*.sql，ST07 单库 5+1 权威）：
 * 本测试只读该目录，不多目录交叉扫。</p>
 */
class ScenarioSchemaComplianceTest {

    /** DDL 单源目录（铁律 v2.0 §3.1，禁分域另立目录）。
     *  从 CWD 逐级向上回溯找 gateway/src/main/resources/db/migration，
     *  兼容 surefire（CWD=模块目录）与 IDE（CWD=工程根）两种运行形态。 */
    private static final Path MIGRATION_DIR = resolveMigrationDir();

    private static Path resolveMigrationDir() {
        Path cur = Path.of("").toAbsolutePath().normalize();
        for (int i = 0; i < 8 && cur != null; i++) {
            Path candidate = cur.resolve("gateway/src/main/resources/db/migration");
            if (Files.isDirectory(candidate)
                    && Files.exists(candidate.resolve("V205__ecos_business_scenario_v2.sql"))) {
                return candidate;
            }
            cur = cur.getParent();
        }
        throw new AssertionError(
                "未找到 DDL 单源目录 gateway/src/main/resources/db/migration（ST07 铁律 §3.1 单源目录）；"
                + "从 " + Path.of("").toAbsolutePath().normalize() + " 向上递归 8 级均失败");
    }

    /** 场景 7 表脚本（V205~V212；V213 只读对账视图单独一段，断言面不同）。 */
    private static final List<String> SCENARIO_TABLE_SCRIPTS = List.of(
            "V205__ecos_business_scenario_v2.sql",
            "V206__ecos_scenario_binding_v2.sql",
            "V207__ecos_scenario_binding_link_v2.sql",
            "V208__ecos_scenario_mind_v2.sql",
            "V209__ecos_scenario_run_v2.sql",
            "V210__ecos_scenario_sandbox_layout_v2.sql",
            "V211__ecos_decision_record_v2.sql",
            "V212__ecos_scenario_query_history.sql");

    private static final String V213_VIEWS = "V213__ecos_scenario_legacy_readonly_views.sql";

    // ── 工具：剥注释（整行 + 行尾）+ 字符串字面量（防 COMMENT '...JSONB...' 误判）──
    private static String bodyOnly(Path sql) throws IOException {
        String raw = Files.readString(sql, StandardCharsets.UTF_8);
        // 1) 块注释 /* … */ 整段剔除
        raw = raw.replaceAll("(?s)/\\*.*?\\*/", " ");
        // 2) 单引号字符串字面量（COMMENT ON ... IS '...' 内的 "JSONB/DR03" 等词面不再计入）
        raw = raw.replaceAll("'[^']*'", "''");
        // 3) 双引号标识符字面量
        raw = raw.replaceAll("\"[^\"]*\"", "\"\"");
        // 4) 行尾 -- 到行末剔除
        raw = raw.replaceAll("--[^\\n]*", " ");
        // 5) 整行 -- 剔除（读性）
        raw = raw.replaceAll("(?m)^\\s*--.*$", "");
        return raw;
    }

    private static Path file(String name) {
        Path p = MIGRATION_DIR.resolve(name);
        if (!Files.exists(p)) {
            fail("DDL 单源目录未找到 " + p.toAbsolutePath() + "（ST07 铁律 §3.1 单源目录）");
        }
        return p;
    }

    // ── IR03：只加不删，禁 DROP ─────────────────────
    @ParameterizedTest
    @ValueSource(strings = {"V205__ecos_business_scenario_v2.sql",
            "V206__ecos_scenario_binding_v2.sql",
            "V207__ecos_scenario_binding_link_v2.sql",
            "V208__ecos_scenario_mind_v2.sql",
            "V209__ecos_scenario_run_v2.sql",
            "V210__ecos_scenario_sandbox_layout_v2.sql",
            "V211__ecos_decision_record_v2.sql",
            "V212__ecos_scenario_query_history.sql",
            "V213__ecos_scenario_legacy_readonly_views.sql"})
    void migrationNeverDropsColumnOrTable(String script) throws IOException {
        String body = bodyOnly(file(script));
        Pattern drop = Pattern.compile("\\bDROP\\s+(TABLE|COLUMN|INDEX|CONSTRAINT|SEQUENCE)\\b",
                Pattern.CASE_INSENSITIVE);
        assertFalse(drop.matcher(body).find(),
                script + " 出现 DROP TABLE/COLUMN/INDEX/CONSTRAINT/SEQUENCE（IR03 只加不删；设计资产历史版本表例外需显式登记，场景域不属例外）");
    }

    // ── MC01：应用侧 UUID 形态 ─────────────────────
    @ParameterizedTest
    @ValueSource(strings = {"V205__ecos_business_scenario_v2.sql",
            "V206__ecos_scenario_binding_v2.sql",
            "V207__ecos_scenario_binding_link_v2.sql",
            "V208__ecos_scenario_mind_v2.sql",
            "V209__ecos_scenario_run_v2.sql",
            "V210__ecos_scenario_sandbox_layout_v2.sql",
            "V211__ecos_decision_record_v2.sql",
            "V212__ecos_scenario_query_history.sql"})
    void primaryKeyIsVarchar36AcrossSevenTables(String script) throws IOException {
        String body = bodyOnly(file(script));
        // 零 gen_random_uuid（X-46 已合规，本测试固化）
        assertFalse(body.contains("gen_random_uuid"),
                script + " 出现 gen_random_uuid()（MC01：PK 应用侧 UUID，DDL 禁默认值）");
        // 每个 CREATE TABLE 段必须含 VARCHAR(36) PRIMARY KEY
        // 简化：段数 ⇔ VARCHAR(36) PK 出现次数
        int tableCount = countMatches(body, "(?i)CREATE TABLE IF NOT EXISTS\\s+public\\.");
        int pk36Count = countMatches(body, "(?i)VARCHAR\\(36\\)\\s+PRIMARY KEY");
        assertTrue(tableCount >= 1, script + " 至少 1 张 CREATE TABLE（否则本脚本无表可验）");
        assertTrue(pk36Count >= tableCount,
                script + " 表数量 " + tableCount + " 但 VARCHAR(36) PK 只 " + pk36Count
                        + "（MC01：主键统一 VARCHAR(36) 应用侧 UUID）");
    }

    // ── MC02：JSONB 白名单（场景域判为 0，全部 _json TEXT）─
    @ParameterizedTest
    @ValueSource(strings = {"V205__ecos_business_scenario_v2.sql",
            "V206__ecos_scenario_binding_v2.sql",
            "V207__ecos_scenario_binding_link_v2.sql",
            "V208__ecos_scenario_mind_v2.sql",
            "V209__ecos_scenario_run_v2.sql",
            "V210__ecos_scenario_sandbox_layout_v2.sql",
            "V211__ecos_decision_record_v2.sql",
            "V212__ecos_scenario_query_history.sql"})
    void noJsonbOutsideWhitelistInScenarioTables(String script) throws IOException {
        String body = bodyOnly(file(script));
        // 场景域当前兑底策略：JSONB 全部改 TEXT + _json 后缀（MC02 白名单零留存）
        assertFalse(Pattern.compile("(?i)\\bJSONB\\b").matcher(body).find(),
                script + " 出现 JSONB（MC02 白名单用途未登记，属违规；若属白名单请补登记 then 放宽断言）");
    }

    // ── MC03：多库兼容（无 partial unique index）─────────
    @ParameterizedTest
    @ValueSource(strings = {"V205__ecos_business_scenario_v2.sql",
            "V206__ecos_scenario_binding_v2.sql",
            "V207__ecos_scenario_binding_link_v2.sql",
            "V208__ecos_scenario_mind_v2.sql",
            "V209__ecos_scenario_run_v2.sql",
            "V210__ecos_scenario_sandbox_layout_v2.sql",
            "V211__ecos_decision_record_v2.sql",
            "V212__ecos_scenario_query_history.sql"})
    void noPartialUniqueIndexInScenarioDomain(String script) throws IOException {
        String body = bodyOnly(file(script));
        Pattern partialUnique = Pattern.compile(
                "(?is)UNIQUE\\s*\\([^)]*\\)\\s+WHERE\\b|CREATE\\s+(UNIQUE\\s+)?INDEX[^\\;]*WHERE\\b",
                Pattern.CASE_INSENSITIVE);
        assertFalse(partialUnique.matcher(body).find(),
                script + " 出现 partial unique index（MC03 Multi-DB 违规；应改为普通唯一 + 服务层软删唯一性校验）");
    }

    // ── DR06-08：七基线列每表齐备 ─────────────────────
    @ParameterizedTest
    @ValueSource(strings = {"V205__ecos_business_scenario_v2.sql",
            "V206__ecos_scenario_binding_v2.sql",
            "V207__ecos_scenario_binding_link_v2.sql",
            "V208__ecos_scenario_mind_v2.sql",
            "V209__ecos_scenario_run_v2.sql",
            "V210__ecos_scenario_sandbox_layout_v2.sql",
            "V211__ecos_decision_record_v2.sql",
            "V212__ecos_scenario_query_history.sql"})
    void allScenarioTablesHaveSevenBaselineColumns(String script) throws IOException {
        String body = bodyOnly(file(script));
        int tableCount = countMatches(body, "(?i)CREATE TABLE IF NOT EXISTS\\s+public\\.");
        assertTrue(tableCount >= 1, script + " 至少 1 张 CREATE TABLE（否则本脚本无表可验）");
        String[] cols = {
            "(?i)\\bcreate_time\\s+TIMESTAMP",
            "(?i)\\bupdate_time\\s+TIMESTAMP",
            "(?i)\\bcreate_by\\s+VARCHAR",
            "(?i)\\bupdate_by\\s+VARCHAR",
            "(?i)\\bis_deleted\\s+SMALLINT",
            "(?i)\\bversion_no\\s+VARCHAR",
            "(?i)\\bdomain\\s+VARCHAR"
        };
        for (String col : cols) {
            int n = countMatches(body, col);
            assertTrue(n >= tableCount,
                    script + " 表数量 " + tableCount + " 但基线列 " + col
                            + " 只 " + n + "（DR06-08 七基线列全部齐备）");
        }
    }

    // ── ST07 控制域落 public.（目标 ecos_control 由配置注前缀）──
    @ParameterizedTest
    @ValueSource(strings = {"V205__ecos_business_scenario_v2.sql",
            "V206__ecos_scenario_binding_v2.sql",
            "V207__ecos_scenario_binding_link_v2.sql",
            "V208__ecos_scenario_mind_v2.sql",
            "V209__ecos_scenario_run_v2.sql",
            "V210__ecos_scenario_sandbox_layout_v2.sql",
            "V211__ecos_decision_record_v2.sql",
            "V212__ecos_scenario_query_history.sql"})
    void tablesAreSchemaQualified(String script) throws IOException {
        String body = bodyOnly(file(script));
        // 每条 CREATE TABLE 后的首个对象名必须带 schema 前缀（当前控制域 = public.，ST07）
        Pattern createTable = Pattern.compile(
                "(?i)CREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?([\\w\\.]+)");
        var m = createTable.matcher(body);
        int n = 0;
        while (m.find()) {
            String tableName = m.group(1);
            if (!tableName.startsWith("public.")) {
                fail(script + " CREATE TABLE 目标对象 " + tableName
                        + " 未带 schema 前缀（§四附则1 裸表名 FAIL；ST07 控制域落 public.）");
            }
            n++;
        }
        assertTrue(n >= 1, script + " 至少 1 台 CREATE TABLE（否则本脚本无表可验）");
    }

    // ── 视图脚本 V213 只读对账（对象名反检查）──────────
    @Test
    void legacyReadOnlyViewsUsePrefixAndNeverCreateSameNameTable() throws IOException {
        String body = bodyOnly(file(V213_VIEWS));
        // 视图都必须带 v_legacy_ 前缀（防与旧停写表同名冲突）
        Pattern createView = Pattern.compile("(?i)CREATE (OR REPLACE )?VIEW (public\\.)?(\\w+)");
        var m = createView.matcher(body);
        int v = 0;
        while (m.find()) {
            String name = m.group(3);
            if (!name.startsWith("v_legacy_")) {
                fail(V213_VIEWS + " 视图 " + name + " 未用 v_legacy_ 前缀（对象名冲突，属主表已被旧停写表占用）");
            }
            v++;
        }
        assertTrue(v >= 1, V213_VIEWS + " 至少 1 条 CREATE VIEW");
    }

    // ── 工具：计数 ──────────────────────────────────
    private static int countMatches(String text, String regex) {
        int n = 0;
        var m = Pattern.compile(regex).matcher(text);
        while (m.find()) n++;
        return n;
    }

    private static void unused(List<String> ignored) { /* Arrays imported for clarity */ }
}
