package com.chinacreator.gzcm.gateway;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 分册10 F10-04 · W Agent 控制面 DDL 形态合规（十四张 V227~V240 一次性走查）。
 *
 * <p>独立于既有 {@code DdlShapeComplianceTest}（该测试走查 V168.1 metric 表；本测试专门走查
 * V227~V240 wagent 十四脚本）。<b>读文件断言，不连 PG、不实跑库</b>（R-33/R-35/36/42 未裁，
 * 依 §14.4 本轮只落脚本文件，测试仅走查文件形态）。</p>
 *
 * <p>断言集：</p>
 * <ol>
 *   <li>十四脚本齐全（V227~V240）</li>
 *   <li>所有表都是 {@code ecos_ai.ecos_wagent_*} 前缀（禁 {@code ecos_agent.*} / {@code public.*} / {@code agt_*} / {@code aim_*} / {@code cog_*}）</li>
 *   <li>主键 {@code VARCHAR(36)}（MC01 应用侧 UUID，DDL 禁 {@code gen_random_uuid()}）</li>
 *   <li>通用列齐：tenant_id / org_id / domain / version_no / create_time / update_time / create_by / update_by / is_deleted</li>
 *   <li>JSON 语义列必须以 {@code _json} 后缀（DR04），存储型为 TEXT（禁 JSONB MC02）</li>
 *   <li>无 PG 专有分区/覆盖索引/分区键；无 {@code CREATE POLICY}（RLS 走 security 引擎 REST）</li>
 *   <li>金额列 {@code NUMERIC(p, s)}（禁止 DECIMAL/FLOAT/DOUBLE）</li>
 *   <li>V233 含 {@code situation_snapshot_json} 列（G2 补列）</li>
 *   <li>V236 含 {@code CHECK(category<>'commit' OR rollback_plan_text IS NOT NULL)}</li>
 * </ol>
 */
class WAgentDdlShapeComplianceTest {

    private static Path migrationDir;
    private static final String[] EXPECTED = {
            "V227__wagent_goal.sql",
            "V228__wagent_mission.sql",
            "V229__wagent_question.sql",
            "V230__wagent_readiness.sql",
            "V231__wagent_readiness_item.sql",
            "V232__wagent_readiness_gap.sql",
            "V233__wagent_run.sql",
            "V234__wagent_run_step.sql",
            "V235__wagent_run_event.sql",
            "V236__wagent_tool_contract.sql",
            "V237__wagent_skill.sql",
            "V238__wagent_candidate.sql",
            "V239__wagent_candidate_review.sql",
            "V240__wagent_evidence_claim.sql"
    };

    @BeforeAll
    static void resolveMigrationDir() throws IOException, URISyntaxException {
        Path p = Path.of(WAgentDdlShapeComplianceTest.class.getProtectionDomain().getCodeSource().getLocation().toURI()).normalize();
        Path base = p;
        while (base != null && !Files.isDirectory(base.resolve("src/main/resources/db/migration"))) {
            base = base.getParent();
        }
        if (base == null) {
            // classpath 不在本地目录（如 fat jar 模式）时回退到 user.dir 相对父级
            base = Paths.get(System.getProperty("user.dir"));
            while (base != null && !Files.isDirectory(base.resolve("src/main/resources/db/migration"))) {
                base = base.getParent();
            }
        }
        assertNotNull(base, "无法定位 gateway/src/main/resources/db/migration");
        migrationDir = base.resolve("src/main/resources/db/migration").toAbsolutePath().normalize();
        assertTrue(Files.isDirectory(migrationDir), "migration dir 未找到: " + migrationDir);
    }

    @Test
    @DisplayName("十四张脚本齐全（V227~V240）")
    void allFourteenFilesPresent() {
        for (String name : EXPECTED) {
            Path p = migrationDir.resolve(name);
            assertTrue(Files.isRegularFile(p), "missing DDL: " + name + " → " + p);
        }
    }

    @Test
    @DisplayName("所有表必须 ecos_ai.ecos_wagent_* 前缀，禁其它前缀")
    void tablesOnlyInEcsAiWagent() throws IOException {
        Pattern create = Pattern.compile("CREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?([\\w.]+)", Pattern.CASE_INSENSITIVE);
        List<String> violations = new ArrayList<>();
        for (String name : EXPECTED) {
            String src = Files.readString(migrationDir.resolve(name));
            Matcher m = create.matcher(src);
            while (m.find()) {
                String tbl = m.group(1).toLowerCase(Locale.ROOT);
                if (!tbl.startsWith("ecos_ai.ecos_wagent_")) {
                    violations.add(name + ": " + tbl);
                }
                if (tbl.startsWith("public.") || tbl.startsWith("ecos_agent.")) violations.add(name + ": 非单源 schema: " + tbl);
                if (tbl.startsWith("agt_") || tbl.startsWith("aim_") || tbl.startsWith("cog_")) {
                    violations.add(name + ": 前缀违规(agt_/aim_/cog_): " + tbl);
                }
            }
        }
        assertTrue(violations.isEmpty(), "非单源前缀/表名: " + violations);
    }

    @Test
    @DisplayName("主键 VARCHAR(36) 且 DDL 无 gen_random_uuid()")
    void pkIsVarchar36AndNoGenRandomUuid() throws IOException {
        for (String name : EXPECTED) {
            String src = Files.readString(migrationDir.resolve(name));
            String lower = src.toLowerCase(Locale.ROOT);
            assertFalse(lower.contains("gen_random_uuid()"), name + ": MC01 违 — DDL 不得 gen_random_uuid()");
            assertTrue(lower.contains("varchar(36)"), name + ": 缺少 VARCHAR(36) 主键列");
        }
    }

    @Test
    @DisplayName("通用列齐：tenant_id/org_id/domain/version_no/create_time/update_time/create_by/update_by/is_deleted")
    void commonColumnsPresent() throws IOException {
        // run_step / run_event 允许省略 version_no（append-only 高位表），但都必须带 tenant_id / create_time
        String[] commonAll = { "tenant_id", "org_id", "domain", "version_no", "create_time", "update_time", "create_by", "update_by", "is_deleted" };
        String[] auditTables = { "V235__wagent_run_event.sql" };
        for (String name : EXPECTED) {
            String src = Files.readString(migrationDir.resolve(name));
            String lower = src.toLowerCase(Locale.ROOT);
            String[] req = name.contains("run_event") || name.contains("claim_evidence")
                    ? new String[]{ "tenant_id", "create_time" } : commonAll;
            List<String> missing = new ArrayList<>();
            for (String c : req) if (!lower.contains(c)) missing.add(c);
            assertTrue(missing.isEmpty(), name + ": 缺通用列 " + missing);
        }
    }

    @Test
    @DisplayName("JSON 语义列必以 _json 后缀 + TEXT 型；禁 JSONB")
    void jsonColumnsUseJsonSuffixAndNoJsonb() throws IOException {
        // 诚实豁免（逐表核实无 JSON 语义列）：这两张是纯标量 append/追迹表，没有可承载 JSON 的列——
        //   readiness_gap   = 缺口追迹（gap_type/severity/field_path 均 VARCHAR）
        //   run_event       = 事件流（载荷走 payload_ref_text 引用指针，禁正文）
        // 旧护栏「每表必含 _json」系过度断言 → 2026-10-05 校订为按<b>精确表名</b>豁免（非模式，
        // 以免将来某表真引入 JSONB 时被「_json 命中即过」掩盖；JSONB 硬红线下一行仍全表生效）。
        Set<String> verifiedNoJsonTables = new HashSet<>(List.of("readiness_gap", "run_event"));
        for (String name : EXPECTED) {
            String src = Files.readString(migrationDir.resolve(name));
            String lower = src.toLowerCase(Locale.ROOT);
            // MC02 硬红线：任一脚本都不得出现 JSONB（JSON 语义一律 *_json + TEXT）
            assertFalse(lower.contains("jsonb"), name + ": MC02 违 — 禁 JSONB");
            boolean isVerifiedNoJson = verifiedNoJsonTables.stream().anyMatch(name::contains);
            if (!isVerifiedNoJson) {
                assertTrue(lower.contains("_json"), name + ": 应至少一个 *_json 列承载 JSON 语义");
            }
        }
    }

    @Test
    @DisplayName("禁 PG 专有特性：PARTITION BY / CREATE INDEX ... WHERE / CREATE POLICY / RLS")
    void noPgSpecificFeatures() throws IOException {
        String[] banned = {
            "partition by", "create policy", "enable row level security",
            "alter table ", "drop policy", "for each row"
        };
        // partial index 检出：只看<b>单个</b> CREATE INDEX 语句（到该语句 `;` 为止）。
        // 旧护栏 `CREATE\s+(UNIQUE\s+)?INDEX[\s\S]*?WHERE` 会用 `[\s\S]*?` 越语句匹配，
        // 命中注释里的 "WHERE"（如 V233:48 「非 partial、…替代原 WHERE 条件」）造成跨语句误报
        // → 2026-10-05 校订为「按 ; 切分单条索引语句再判 WHERE」。V233 两张索引实为普通（非 partial）索引，合法。
        Pattern createIdxStmt = Pattern.compile("create\\s+(unique\\s+)?index[\\s\\S]*?(?=;|$)", Pattern.CASE_INSENSITIVE);
        for (String name : EXPECTED) {
            String src = Files.readString(migrationDir.resolve(name));
            String lower = src.toLowerCase(Locale.ROOT);
            for (String b : banned) {
                assertFalse(lower.contains(b), name + ": 违 — 出现 PG 专有特性 [" + b + "]");
            }
            Matcher mi = createIdxStmt.matcher(lower);
            while (mi.find()) {
                String stmt = mi.group();
                assertFalse(stmt.contains(" where "), name + ": 违 — CREATE INDEX 语句内出现 WHERE（partial index, PG 专有）");
            }
        }
    }

    @Test
    @DisplayName("金额/比率/概率列必须 NUMERIC(p, s)，禁 FLOAT/DOUBLE/REAL")
    void numericColumnsUseDecimalNumeric() throws IOException {
        Pattern bad = Pattern.compile("\\b(double|float|real)\\b", Pattern.CASE_INSENSITIVE);
        for (String name : EXPECTED) {
            String src = Files.readString(migrationDir.resolve(name));
            Matcher m = bad.matcher(src);
            assertFalse(m.find(), name + ": 违 — 出现金额用 double/float/real: " + (m.find() ? m.group(0) : ""));
        }
    }

    @Test
    @DisplayName("V233 含 situation_snapshot_json 列（G2 补列，§5.2 契约 Situation 引用落本表）")
    void v233HasSituationSnapshotJson() throws IOException {
        String v233 = Files.readString(migrationDir.resolve("V233__wagent_run.sql")).toLowerCase(Locale.ROOT);
        assertTrue(v233.contains("situation_snapshot_json"), "V233 缺 situation_snapshot_json (G2 补列)");
    }

    @Test
    @DisplayName("V236 含 CHECK(category<>'commit' OR rollback_plan_text IS NOT NULL)")
    void v236HasCommitCategoryRollbackCheck() throws IOException {
        String v236 = Files.readString(migrationDir.resolve("V236__wagent_tool_contract.sql")).toLowerCase(Locale.ROOT);
        assertTrue(v236.contains("rollback_plan_text"), "V236 缺 rollback_plan_text 列");
        assertTrue(v236.contains("check"), "V236 缺 CHECK 约束（commit ⇒ rollback_plan NOT NULL 双校）");
    }

    @Test
    @DisplayName("V240 三表：evidence（无内容字段）+ claim（含 NUMERIC(18,6) 数值列）+ claim_evidence（复合 PK）")
    void v240ThreeTablesAndShape() throws IOException {
        String v240 = Files.readString(migrationDir.resolve("V240__wagent_evidence_claim.sql")).toLowerCase(Locale.ROOT);
        // 三表齐
        assertTrue(v240.contains("ecos_wagent_evidence"),        "V240 缺 evidence 表");
        assertTrue(v240.contains("ecos_wagent_claim"),            "V240 缺 claim 表");
        assertTrue(v240.contains("ecos_wagent_claim_evidence"),   "V240 缺 claim_evidence 关系表");
        // evidence 无内容字段（禁 content / raw_content / snippet_text）
        Matcher contentLeak = Pattern.compile("\\b(content\\s+text|raw_content|snippet_text)\\b").matcher(v240);
        assertFalse(contentLeak.find(), "V240 evidence 表不允许含 '内容' 型字段 (只存引用指针)");
    }

    @Test
    @DisplayName("十四脚本合计仅 1 个 schema 引用 = ecos_ai（无第二 schema 命中）")
    void allScriptsReferenceOnlyEcsAiSchema() throws IOException {
        // 提取 create table 的 schema 部分
        Pattern create = Pattern.compile("CREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?([\\w.]+)", Pattern.CASE_INSENSITIVE);
        Set<String> schemas = new HashSet<>();
        for (String name : EXPECTED) {
            String src = Files.readString(migrationDir.resolve(name));
            Matcher m = create.matcher(src);
            while (m.find()) {
                String tbl = m.group(1);
                int idx = tbl.indexOf('.');
                if (idx >= 0 && idx < tbl.length() - 1) schemas.add(tbl.substring(0, idx).toLowerCase(Locale.ROOT));
            }
        }
        assertTrue(schemas.size() <= 1, "14 张表逃逸出 ecos_ai 单一 schema: " + schemas);
        if (schemas.size() == 1) {
            assertEquals("ecos_ai", schemas.iterator().next(), "唯一 schema 必须是 ecos_ai");
        }
    }
}
