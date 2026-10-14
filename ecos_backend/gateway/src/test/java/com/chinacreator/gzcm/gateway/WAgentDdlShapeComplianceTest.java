package com.chinacreator.gzcm.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * #140 门禁补实现批 · 子批 A-3 —— WAgent DDL 形态合规门禁（从零实施版设计说明书 line 6652）。
 *
 * <p><b>差距来源</b>：{@code docs/20-需求/ECOS详细设计从零实施版-实现差距分析-2026-10-14.md}
 * §1.14 / §15.3 登记「WAgentDdlShapeComplianceTest 门禁不存在」，设计说明书 line 6652 给出的
 * 验收定义是：<b>表名 / schema / PK VARCHAR(36) / 金额 NUMERIC / {@code _json} TEXT /
 * 无 CREATE POLICY / 无分区 / 无 partial index</b>，任一形态违例即 FAIL。
 * 按 R9② 裁决（缺的门禁＝补代码实现，禁从清单删除、禁先写空转测试），本类是该门禁的实现。</p>
 *
 * <p><b>断言对象</b>：迁移单源目录
 * {@code docs/50-db_script/02-flyway-migration/gateway/}（架构铁律 v2.2 §3.1 权威单源）内
 * 文件名含 {@code wagent} 的 19 条脚本（V227~V240 / V265 / V266 / V270 / V283 / V286）中
 * 所有 {@code CREATE TABLE} 的建表形态，逐条对上列 8 项红线做静态扫描（无 DB 依赖）。</p>
 *
 * <p><b>反假绿设计</b>（本仓 lint 已登记 D-15/D-16 两例假绿缺陷，教训直接落地）：
 * <ol>
 *   <li>{@link #scriptsAndTableInventoryAreExactlyThePinnedSet()} —— 先做<b>双向集合相等</b>：
 *       脚本清单与 21 张表清单都是钉死的，脚本被移走/改名/新增未登记表都立刻变红，
 *       避免"扫描面为空 ⇒ 恒 PASS"；</li>
 *   <li>{@link #scannerActuallySeesTheKnownShapes()} —— 用<b>已核实的真实特征</b>反向自检解析器
 *       （必须真能看到 21 张表、必须真能抓到 {@code ecos_wagent_claim_evidence} 的复合主键、
 *       必须真能抓到 V286 的 4 枚金额列），解析器退化即变红；</li>
 *   <li>{@link #noForbiddenTypesOrForms()} 的注释剥离<b>逐行去掉首个 {@code --} 到行尾</b>
 *       （D-15 同源缺陷：V283 行 98 {@code backtest_ref_text TEXT, -- 回测对照指针（文本，不落 JSONB）}
 *       只剥整行注释会误报 JSONB 违例 ⇒ 假红；反过来只扫注释又会假绿）。</li>
 * </ol>
 *
 * <p><b>钉死豁免（逐条带理由，禁静默扩大）</b>：
 * <ul>
 *   <li>复合主键 1 枚：{@code ecos_ai.ecos_wagent_claim_evidence} —— 纯 n:m 关联表（V240），
 *       主键 {@code (claim_id, evidence_id)}，无独立 id 列；</li>
 *   <li>业务域金额表 1 枚：{@code ecos_dw.ecos_application_wagent_option_amount}（V286）——
 *       控制域金额永不可豁免（ST03-A ④）的落地形态 = 金额四列
 *       {@code profit_delta/revenue_delta/cost_delta/cash_impact NUMERIC(18,2)} 落业务域
 *       APPLICATION 层，V283 侧只留 {@code amount_ref VARCHAR(36)} 弱关联引用位；</li>
 *   <li>控制域非金额 NUMERIC 6 个形态（置信度/比率/数值度量/人力影响），见
 *       {@link #CONTROL_DOMAIN_NON_MONETARY_NUMERIC}。</li>
 * </ul>
 *
 * <p>验收：{@code mvn -o test -pl gateway -Dtest='WAgentDdlShapeComplianceTest'}
 * （从 {@code ecos_backend/} 执行）。</p>
 */
@DisplayName("#140/A-3：WAgent DDL 形态合规门禁（表名·schema·PK·金额·_json·RLS·分区·partial index）")
class WAgentDdlShapeComplianceTest {

    private static final String MIGRATION_REL =
            "docs/50-db_script/02-flyway-migration/gateway";

    /** 19 条 wagent 单源脚本（文件名含 wagent；V265/V266/V270 为 seed/ALTER-only，无 CREATE TABLE）。 */
    private static final Set<String> PINNED_SCRIPTS = Set.of(
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
            "V240__wagent_evidence_claim.sql",
            "V265__wagent_sys_config_seed.sql",
            "V266__wagent_tool_contract_seed.sql",
            "V270__wagent_tool_contract_contract_columns.sql",
            "V283__ecos_wagent_decision_action_loop.sql",
            "V286__ecos_dw_wagent_option_amount.sql"
    );

    /** 21 张 wagent 表（20 张控制域 ecos_ai + 1 张业务域 ecos_dw）。 */
    private static final Set<String> PINNED_TABLES = Set.of(
            "ecos_ai.ecos_wagent_goal",
            "ecos_ai.ecos_wagent_mission",
            "ecos_ai.ecos_wagent_question",
            "ecos_ai.ecos_wagent_readiness",
            "ecos_ai.ecos_wagent_readiness_item",
            "ecos_ai.ecos_wagent_readiness_gap",
            "ecos_ai.ecos_wagent_run",
            "ecos_ai.ecos_wagent_run_step",
            "ecos_ai.ecos_wagent_run_event",
            "ecos_ai.ecos_wagent_tool_contract",
            "ecos_ai.ecos_wagent_skill",
            "ecos_ai.ecos_wagent_candidate",
            "ecos_ai.ecos_wagent_candidate_review",
            "ecos_ai.ecos_wagent_evidence",
            "ecos_ai.ecos_wagent_claim",
            "ecos_ai.ecos_wagent_claim_evidence",
            "ecos_ai.ecos_wagent_decision",
            "ecos_ai.ecos_wagent_decision_option",
            "ecos_ai.ecos_wagent_action",
            "ecos_ai.ecos_wagent_action_effect",
            "ecos_dw.ecos_application_wagent_option_amount"
    );

    /** 钉死豁免：纯 n:m 关联表允许复合主键（无独立 id 列），逐表登记理由。 */
    private static final Map<String, String> COMPOSITE_PK_EXEMPTIONS = Map.of(
            "ecos_ai.ecos_wagent_claim_evidence",
            "证据↔主张 n:m 关联表（V240）：主键 (claim_id, evidence_id) 即业务唯一性本体，无 surrogate id"
    );

    /** 钉死豁免：业务域金额承载表（控制域金额永不可豁免 ST03-A ④ ⇒ 金额列只能落这里）。 */
    private static final Set<String> BUSINESS_DOMAIN_AMOUNT_TABLES = Set.of(
            "ecos_dw.ecos_application_wagent_option_amount"
    );

    /**
     * 控制域允许的<b>非金额</b> NUMERIC 列（钉死清单，禁静默扩大）。
     * 键=表名，值=该表允许的 {@code 列名 NUMERIC(p,s)} 集合。理由逐条：
     * <ul>
     *   <li>置信度/覆盖率类：0~1 概率语义，非金额；</li>
     *   <li>{@code numeric_value}：证据数值度量承载位（原文即"数值"，单位由 metric 列文本描述）；</li>
     *   <li>{@code risk_delta}：风险相对差值（无量纲打分差）；</li>
     *   <li>{@code headcount_impact}：人数影响（人，非金额）。</li>
     * </ul>
     * 任何新增控制域 NUMERIC 列都会把本断言判红，必须显式登记理由才可扩大。
     */
    private static final Map<String, Set<String>> CONTROL_DOMAIN_NON_MONETARY_NUMERIC = Map.of(
            "ecos_ai.ecos_wagent_question", Set.of("INTENT_CONFIDENCE NUMERIC(5,4)"),
            "ecos_ai.ecos_wagent_candidate", Set.of("CONFIDENCE NUMERIC(5,4)"),
            "ecos_ai.ecos_wagent_claim", Set.of("NUMERIC_VALUE NUMERIC(18,6)"),
            "ecos_ai.ecos_wagent_claim_evidence", Set.of("COVER_RATIO NUMERIC(5,4)"),
            "ecos_ai.ecos_wagent_decision_option", Set.of(
                    "RISK_DELTA NUMERIC(9,6)",
                    "CONFIDENCE NUMERIC(5,4)",
                    "HEADCOUNT_IMPACT NUMERIC(9,2)")
    );

    /** DR05~DR08 七基线列（与设计 line 6652 同族的数据库访问规范红线）。 */
    private static final String[] BASELINE_COLUMNS = {
            "create_by", "update_by", "create_time", "update_time",
            "is_deleted", "domain", "version_no"
    };

    /** 金额语义列名词根：出现在<b>控制域</b>表列名中 ⇒ 该列禁为 NUMERIC（须引用位/文本承载）。 */
    private static final Pattern MONETARY_NAME = Pattern.compile(
            "(^|_)(amount|money|profit|revenue|cost|cash|balance|price|fee|budget|gmv|ar)(_|$)",
            Pattern.CASE_INSENSITIVE);

    /** 被禁形态：类型红线 + RLS/分区/partial index（设计 line 6652 明文列举）。 */
    private static final Map<String, Pattern> FORBIDDEN_FORMS = new LinkedHashMap<>();

    static {
        FORBIDDEN_FORMS.put("gen_random_uuid（MC01 主键须应用侧 UUID）",
                Pattern.compile("\\bgen_random_uuid\\b", Pattern.CASE_INSENSITIVE));
        FORBIDDEN_FORMS.put("nextval（MC01 序列默认值）",
                Pattern.compile("\\bnextval\\b", Pattern.CASE_INSENSITIVE));
        FORBIDDEN_FORMS.put("SERIAL 族自增列（MC02 不可移植）",
                Pattern.compile("\\b(?:big|small)?serial\\b", Pattern.CASE_INSENSITIVE));
        FORBIDDEN_FORMS.put("JSONB（MC02 新表 JSON 语义用 TEXT）",
                Pattern.compile("\\bjsonb\\b", Pattern.CASE_INSENSITIVE));
        FORBIDDEN_FORMS.put("timestamptz（MC03 时间形态显式统一）",
                Pattern.compile("\\btimestamptz\\b", Pattern.CASE_INSENSITIVE));
        FORBIDDEN_FORMS.put("double precision/float/decimal/money（金额唯一形态 NUMERIC(p,s)，其余浮点/私有类型禁用）",
                Pattern.compile("\\b(?:double\\s+precision|float|decimal|money)\\b", Pattern.CASE_INSENSITIVE));
        FORBIDDEN_FORMS.put("CREATE POLICY（RLS 归 security-engine，DDL 侧禁建策略）",
                Pattern.compile("\\bcreate\\s+(?:row\\s+)?security\\s+policy\\b|\\bcreate\\s+policy\\b",
                        Pattern.CASE_INSENSITIVE));
        FORBIDDEN_FORMS.put("PARTITION BY（分区由数据引擎写通道治理，新表 DDL 禁分区）",
                Pattern.compile("\\bpartition\\s+by\\b", Pattern.CASE_INSENSITIVE));
        FORBIDDEN_FORMS.put("partial index（CREATE INDEX ... WHERE 局部索引不可移植）",
                Pattern.compile("\\bcreate\\s+(?:unique\\s+)?index[^;]*?\\bwhere\\b",
                        Pattern.CASE_INSENSITIVE | Pattern.DOTALL));
    }

    /** 一条 CREATE TABLE 的解析结果。 */
    private record TableShape(String script, String qualifiedName, String schema, String table,
                              String body, int startLine) {
        String simpleName() { return table; }
    }

    // ────────────────────────────────────────────────────────────
    // ① 扫描面本身：脚本 + 表清单双向钉死（防空集合假绿）
    // ────────────────────────────────────────────────────────────
    @Test
    @DisplayName("wagent 脚本与 21 张建表清单双向相等（脚本被移走/改名/新增未登记表即 FAIL）")
    void scriptsAndTableInventoryAreExactlyThePinnedSet() throws IOException {
        Path dir = repoRoot().resolve(MIGRATION_REL);
        assertTrue(Files.isDirectory(dir), "缺迁移 DDL 单源目录（铁律 v2.2 §3.1）: " + dir);

        List<String> missing = new ArrayList<>();
        for (String s : new TreeSet<>(PINNED_SCRIPTS)) {
            if (!Files.isRegularFile(dir.resolve(s))) missing.add(s);
        }
        assertTrue(missing.isEmpty(), "wagent DDL 单源脚本缺失（DDL 单源漂移）：\n" + String.join("\n", missing));

        Set<String> actualScripts = toTree(listWagentScriptNames(dir));
        assertEquals(toTree(PINNED_SCRIPTS), actualScripts,
                "单源目录含 wagent 的脚本清单与钉死清单不一致（新增脚本须登记进本门禁）");

        List<TableShape> tables = allWagentTables(dir);
        Set<String> actualTables = toTree(tables.stream().map(TableShape::qualifiedName).toList());
        assertEquals(new TreeSet<>(PINNED_TABLES), actualTables,
                "wagent 建表清单（21 张：20 ecos_ai + 1 ecos_dw）与钉死清单不一致");
        assertEquals(21, tables.size(), "wagent CREATE TABLE 应为 21 张");
    }

    // ────────────────────────────────────────────────────────────
    // ② 解析器自检：用已核实真实特征反证扫描不是空转
    // ────────────────────────────────────────────────────────────
    @Test
    @DisplayName("扫描器自检：真能看到 21 表 / 复合主键豁免 / V286 四枚金额列（解析退化即 FAIL）")
    void scannerActuallySeesTheKnownShapes() throws IOException {
        List<TableShape> tables = allWagentTables(repoRoot().resolve(MIGRATION_REL));
        Map<String, TableShape> byName = new LinkedHashMap<>();
        tables.forEach(t -> byName.put(t.qualifiedName(), t));

        TableShape junction = byName.get("ecos_ai.ecos_wagent_claim_evidence");
        assertTrue(junction != null, "解析器未抓到 ecos_ai.ecos_wagent_claim_evidence（复合主键豁免表）");
        assertTrue(Pattern.compile("PRIMARY\\s+KEY\\s*\\(\\s*claim_id\\s*,\\s*evidence_id\\s*\\)",
                        Pattern.CASE_INSENSITIVE).matcher(junction.body()).find(),
                "解析器在 claim_evidence 表体内看不到复合主键 ⇒ body 抽取退化");

        TableShape amount = byName.get("ecos_dw.ecos_application_wagent_option_amount");
        assertTrue(amount != null, "解析器未抓到 V286 业务域金额表");
        for (String col : List.of("profit_delta", "revenue_delta", "cost_delta", "cash_impact")) {
            assertTrue(Pattern.compile("\\b" + col + "\\s+NUMERIC\\s*\\(\\s*18\\s*,\\s*2\\s*\\)",
                            Pattern.CASE_INSENSITIVE).matcher(amount.body()).find(),
                    "解析器在金额表体内看不到 " + col + " NUMERIC(18,2) ⇒ 金额面断言将空转");
        }

        // 列解析器本身也必须真能吃到带括号的类型（曾经只匹配到 TEXT 类无括号类型 ⇒ NUMERIC 静默漏检）
        long numericCols = columns(amount.body()).stream()
                .filter(c -> c[1].toUpperCase().startsWith("NUMERIC"))
                .count();
        assertTrue(numericCols >= 4,
                "列解析器在金额表只吃到 " + numericCols + " 个 NUMERIC 列（应 ≥4）⇒ 列级断言会空转");
        assertTrue(columns(byName.get("ecos_ai.ecos_wagent_goal").body()).stream()
                        .anyMatch(c -> c[1].toUpperCase().startsWith("VARCHAR(")),
                "列解析器吃不到 VARCHAR(n) 带括号类型");

        // 逐行剥离行尾注释必须真的生效（否则 V283:98 的 "不落 JSONB" 会造出假红）
        String stripped = stripComments(Files.readString(
                repoRoot().resolve(MIGRATION_REL).resolve("V283__ecos_wagent_decision_action_loop.sql"),
                StandardCharsets.UTF_8));
        assertTrue(stripped.contains("backtest_ref_text"), "V283 列定义不应被误剥");
        assertTrue(!Pattern.compile("\\bjsonb\\b", Pattern.CASE_INSENSITIVE).matcher(stripped).find(),
                "行尾注释剥离失效：V283 注释里的「不落 JSONB」泄漏进 SQL 面（D-15 同源缺陷）");
    }

    // ────────────────────────────────────────────────────────────
    // ③ 表名 + schema 归属（DR01 前缀 + ST07 单库 schema 归属）
    // ────────────────────────────────────────────────────────────
    @Test
    @DisplayName("表名 ecos_ 前缀 + schema 只落 ecos_ai（控制域）/ ecos_dw（业务域金额承载）")
    void tableNamesAndSchemasFollowRedlines() throws IOException {
        List<TableShape> tables = allWagentTables(repoRoot().resolve(MIGRATION_REL));
        List<String> violations = new ArrayList<>();
        for (TableShape t : tables) {
            if (!t.schema().equals("ecos_ai") && !t.schema().equals("ecos_dw")) {
                violations.add(t.qualifiedName() + " 落在未授权 schema（ST07：W 域控制面=ecos_ai，金额承载=ecos_dw）");
            }
            if (!t.table().startsWith("ecos_")) {
                violations.add(t.qualifiedName() + " 表名缺 ecos_ 前缀（DR01）");
            }
            boolean business = t.schema().equals("ecos_dw");
            if (business && !BUSINESS_DOMAIN_AMOUNT_TABLES.contains(t.qualifiedName())) {
                violations.add(t.qualifiedName() + " 是未登记的 ecos_dw wagent 表（钉死清单外）");
            }
        }
        assertTrue(violations.isEmpty(), "表名/schema 红线违例：\n" + String.join("\n", violations));

        // W 域控制面主体必须真在 ecos_ai（防止"整批漂到 knownLegacy schema"式假绿）
        long inEcosAi = tables.stream().filter(t -> t.schema().equals("ecos_ai")).count();
        assertEquals(20, inEcosAi, "ecos_ai 内 wagent 控制面表应为 20 张");
    }

    // ────────────────────────────────────────────────────────────
    // ④ MC01 主键形态 + DR05~DR08 七基线列
    // ────────────────────────────────────────────────────────────
    @Test
    @DisplayName("PK=应用侧 id VARCHAR(36)（仅钉死的 n:m 关联表可复合主键）+ 七基线列齐备")
    void primaryKeyAndBaselineColumns() throws IOException {
        List<TableShape> tables = allWagentTables(repoRoot().resolve(MIGRATION_REL));
        List<String> violations = new ArrayList<>();
        for (TableShape t : tables) {
            String exemption = COMPOSITE_PK_EXEMPTIONS.get(t.qualifiedName());
            if (exemption != null) {
                List<String> pkCols = primaryKeyColumns(t.body());
                if (pkCols.size() < 2) {
                    violations.add(t.qualifiedName() + " 登记为复合主键豁免但实际 PK 列=" + pkCols
                            + "（豁免形同虚设 ⇒ 门禁退化）");
                }
            } else {
                if (!Pattern.compile("\\bid\\s+VARCHAR\\(36\\)[^,\\n]*\\bPRIMARY\\s+KEY\\b",
                        Pattern.CASE_INSENSITIVE).matcher(t.body()).find()) {
                    violations.add(t.qualifiedName() + " 缺 MC01 形态 `id VARCHAR(36) ... PRIMARY KEY`"
                            + "（PK 列实测 " + primaryKeyColumns(t.body()) + "，非豁免表须应用侧 UUID 单列主键）");
                }
            }
            List<String> missingBase = new ArrayList<>();
            for (String col : BASELINE_COLUMNS) {
                if (!hasColumn(t.body(), col)) missingBase.add(col);
            }
            if (!missingBase.isEmpty()) {
                violations.add(t.qualifiedName() + " 缺 DR05~DR08 基线列 " + missingBase);
            }
        }
        assertTrue(violations.isEmpty(), "主键/基线列违例：\n" + String.join("\n", violations));
    }

    // ────────────────────────────────────────────────────────────
    // ⑤ 被禁形态全量扫描（类型红线 + RLS + 分区 + partial index）
    // ────────────────────────────────────────────────────────────
    @Test
    @DisplayName("零 gen_random_uuid/nextval/SERIAL/JSONB/timestamptz/私有浮点 + 零 CREATE POLICY/分区/partial index")
    void noForbiddenTypesOrForms() throws IOException {
        Path dir = repoRoot().resolve(MIGRATION_REL);
        List<String> violations = new ArrayList<>();
        for (String script : new TreeSet<>(PINNED_SCRIPTS)) {
            String stripped = stripComments(Files.readString(dir.resolve(script), StandardCharsets.UTF_8));
            String[] lines = stripped.split("\n", -1);
            // 逐类红线做「行级」定位（partial index 需跨行，单独整文件判定）
            for (Map.Entry<String, Pattern> e : FORBIDDEN_FORMS.entrySet()) {
                if (e.getKey().startsWith("partial index")) continue;
                for (int i = 0; i < lines.length; i++) {
                    if (e.getValue().matcher(lines[i]).find()) {
                        violations.add(script + ":" + (i + 1) + " 命中被禁形态 [" + e.getKey()
                                + "] ⇒ " + lines[i].trim());
                    }
                }
            }
            Matcher partial = FORBIDDEN_FORMS.get("partial index（CREATE INDEX ... WHERE 局部索引不可移植）")
                    .matcher(stripped);
            while (partial.find()) {
                int line = 1 + stripped.substring(0, partial.start()).split("\n", -1).length - 1;
                violations.add(script + ":" + line + " 命中 partial index（CREATE INDEX ... WHERE）⇒ "
                        + oneLine(partial.group()));
            }
        }
        assertTrue(violations.isEmpty(), "WAgent DDL 被禁形态违例（设计说明书 line 6652）：\n"
                + String.join("\n", violations));
    }

    // ────────────────────────────────────────────────────────────
    // ⑥ JSON 语义列：必为 *_json + TEXT，禁裸 JSON 类型
    // ────────────────────────────────────────────────────────────
    @Test
    @DisplayName("JSON 语义列一律 *_json TEXT（MC02）；禁裸 JSON 类型；带 _json 后缀者不得为其它类型")
    void jsonSemanticsColumnsAreText() throws IOException {
        List<TableShape> tables = allWagentTables(repoRoot().resolve(MIGRATION_REL));
        List<String> violations = new ArrayList<>();
        for (TableShape t : tables) {
            for (String[] col : columns(t.body())) {
                String name = col[0], type = col[1];
                if (name.toLowerCase().endsWith("_json") && !type.equalsIgnoreCase("TEXT")) {
                    violations.add(t.qualifiedName() + "." + name + " 类型=" + type
                            + "（*_json 约定列必须 TEXT 承载，MC02）");
                }
                if (type.equalsIgnoreCase("JSON") || type.toUpperCase().startsWith("JSON ")) {
                    violations.add(t.qualifiedName() + "." + name + " 使用裸 JSON 类型（须 TEXT + *_json 命名）");
                }
            }
        }
        assertTrue(violations.isEmpty(), "JSON 承载形态违例：\n" + String.join("\n", violations));

        // 反向自检：至少真的扫到了 JSON 语义列（防止正则退化为恒零命中 ⇒ 该断言空转）
        long jsonCols = tables.stream()
                .flatMap(t -> columns(t.body()).stream())
                .filter(c -> c[0].toLowerCase().endsWith("_json"))
                .count();
        assertTrue(jsonCols >= 15,
                "*_json 列命中数=" + jsonCols + "（2026-10-14 实测 27 枚，骤降说明列解析退化）");
    }

    // ────────────────────────────────────────────────────────────
    // ⑦ 金额列落域（ST03-A ④ 控制域金额永不可豁免；金额唯一形态 NUMERIC(p,s)）
    // ────────────────────────────────────────────────────────────
    @Test
    @DisplayName("金额实列只落业务域 ecos_dw（NUMERIC(18,2)）；控制域 NUMERIC 必须是钉死的非金额清单")
    void amountColumnsLiveOnlyInBusinessDomain() throws IOException {
        List<TableShape> tables = allWagentTables(repoRoot().resolve(MIGRATION_REL));
        List<String> violations = new ArrayList<>();
        for (TableShape t : tables) {
            boolean business = t.schema().equals("ecos_dw");
            List<String[]> numerics = new ArrayList<>();
            for (String[] col : columns(t.body())) {
                Matcher mm = Pattern.compile("^NUMERIC\\s*\\(\\s*(\\d+)\\s*(?:,\\s*(\\d+)\\s*)?\\)$",
                        Pattern.CASE_INSENSITIVE).matcher(col[1]);
                if (!mm.matches()) continue;
                numerics.add(new String[] {col[0], col[1]});
                if (MONETARY_NAME.matcher(col[0]).find()
                        && !BUSINESS_DOMAIN_AMOUNT_TABLES.contains(t.qualifiedName())) {
                    violations.add(t.qualifiedName() + "." + col[0] + " 金额语义 NUMERIC 列"
                            + (business
                                    ? " 落在未登记的 ecos_dw 承载表（登记清单=" + BUSINESS_DOMAIN_AMOUNT_TABLES + "）"
                                    : "落在控制域（ST03-A ④：控制域金额永不可豁免，"
                                            + "须改 amount_ref VARCHAR(36) 引用位或文本承载）"));
                }
            }
            if (business) {
                for (String[] n : numerics) {
                    if (!n[1].toUpperCase().replaceAll("[^0-9,]", "").matches("\\d+,2")) {
                        violations.add(t.qualifiedName() + "." + n[0] + " 业务域金额列形态=" + n[1]
                                + "（基线形态 NUMERIC(p,2)）");
                    }
                }
            } else {
                Set<String> allowed = CONTROL_DOMAIN_NON_MONETARY_NUMERIC
                        .getOrDefault(t.qualifiedName(), Set.of());
                for (String[] n : numerics) {
                    String form = normalize(n[0] + " " + n[1]);
                    if (!allowed.contains(form)) {
                        violations.add(t.qualifiedName() + "." + n[0] + " 控制域 NUMERIC 形态 " + form
                                + " 不在钉死非金额清单（新增须显式登记理由，禁静默扩大）");
                    }
                }
            }
            // 业务域金额表必须真带金额列（防止「整表被清空」式假绿）
            if (BUSINESS_DOMAIN_AMOUNT_TABLES.contains(t.qualifiedName()) && numerics.size() < 4) {
                violations.add(t.qualifiedName() + " 业务域金额承载表 NUMERIC 列数=" + numerics.size()
                        + "（应 ≥4，实列被清空即门禁空转）");
            }
        }
        assertTrue(violations.isEmpty(), "金额落域违例：\n" + String.join("\n", violations));

        // 控制域内金额语义必须以非数值形态存在（反向确认引用位确实在位）
        TableShape option = tables.stream()
                .filter(t -> t.qualifiedName().equals("ecos_ai.ecos_wagent_decision_option"))
                .findFirst().orElseThrow();
        assertTrue(hasColumn(option.body(), "amount_ref"),
                "V283 decision_option 缺 amount_ref 引用位（ST03-A ④ 金额面衔接位）");
        assertTrue(columns(option.body()).stream()
                        .filter(c -> c[0].equals("amount_ref"))
                        .allMatch(c -> c[1].toUpperCase().startsWith("VARCHAR(36)")),
                "amount_ref 必须是 VARCHAR(36) 弱关联引用位");
    }

    // ────────────────────────────────────────────────────────────
    // 解析辅助
    // ────────────────────────────────────────────────────────────

    /** 扫描全部 wagent 脚本里的 CREATE TABLE，抽取 schema/table 与配平括号内的表体。 */
    private static List<TableShape> allWagentTables(Path dir) throws IOException {
        Pattern create = Pattern.compile(
                "CREATE\\s+TABLE(?:\\s+IF\\s+NOT\\s+EXISTS)?\\s+([A-Za-z0-9_]+)\\.([A-Za-z0-9_]+)\\s*\\(",
                Pattern.CASE_INSENSITIVE);
        List<TableShape> out = new ArrayList<>();
        for (String script : new TreeSet<>(PINNED_SCRIPTS)) {
            String sql = stripComments(Files.readString(dir.resolve(script), StandardCharsets.UTF_8));
            Matcher m = create.matcher(sql);
            while (m.find()) {
                int open = sql.indexOf('(', m.end() - 1);
                int depth = 0, i = open;
                for (; i < sql.length(); i++) {
                    char c = sql.charAt(i);
                    if (c == '(') depth++;
                    else if (c == ')') {
                        depth--;
                        if (depth == 0) break;
                    }
                }
                assertTrue(depth == 0, script + " 的 CREATE TABLE " + m.group(2) + " 括号未配平（解析前提失效）");
                String body = sql.substring(open + 1, i);
                int startLine = 1 + sql.substring(0, m.start()).split("\n", -1).length - 1;
                out.add(new TableShape(script, m.group(1) + "." + m.group(2), m.group(1), m.group(2),
                        body, startLine));
            }
        }
        return out;
    }

    /** 表体内顶层（括号深度 0）列定义，返回 [列名, 类型串（含括号）]。 */
    private static List<String[]> columns(String body) {
        List<String[]> out = new ArrayList<>();
        for (String raw : splitTopLevel(body)) {
            String frag = raw.trim();
            if (frag.isEmpty()) continue;
            Matcher m = Pattern.compile(
                    "^([A-Za-z_][A-Za-z0-9_]*)\\s+([A-Za-z][A-Za-z0-9_]*(?:\\s*\\([^)]*\\))?)",
                    Pattern.CASE_INSENSITIVE).matcher(frag);
            if (!m.find()) continue;
            String name = m.group(1), type = m.group(2).replaceAll("\\s+", " ").trim();
            // 排除把 CONSTRAINT/PRIMARY/UNIQUE/FOREIGN/CHECK 等关键字当列名
            if (Set.of("constraint", "primary", "unique", "foreign", "check").contains(name.toLowerCase())) {
                continue;
            }
            out.add(new String[] {name, type});
        }
        return out;
    }

    /** 按逗号切分，只在括号深度 0 处切。 */
    private static List<String> splitTopLevel(String body) {
        List<String> parts = new ArrayList<>();
        int depth = 0, start = 0;
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') depth--;
            else if (c == ',' && depth == 0) {
                parts.add(body.substring(start, i));
                start = i + 1;
            }
        }
        parts.add(body.substring(start));
        return parts;
    }

    private static List<String> primaryKeyColumns(String body) {
        List<String> cols = new ArrayList<>();
        Matcher inline = Pattern.compile("\\b([A-Za-z_][A-Za-z0-9_]*)\\s+[A-Za-z][A-Za-z0-9_]*(?:\\s*\\([^)]*\\))?"
                + "[^,\\n]*?\\bPRIMARY\\s+KEY\\b", Pattern.CASE_INSENSITIVE).matcher(body);
        while (inline.find()) cols.add(inline.group(1));
        Matcher named = Pattern.compile("\\bPRIMARY\\s+KEY\\s*\\(([^)]*)\\)", Pattern.CASE_INSENSITIVE)
                .matcher(body);
        while (named.find()) {
            for (String p : named.group(1).split(",")) cols.add(p.trim());
        }
        return cols;
    }

    private static boolean hasColumn(String body, String column) {
        return Pattern.compile("(^|[^A-Za-z0-9_])" + Pattern.quote(column) + "([^A-Za-z0-9_]|$)")
                .matcher(body).find();
    }

    private static String normalize(String s) {
        return s.toUpperCase().replaceAll("\\s*,\\s*", ",").replaceAll("\\s+", " ").trim();
    }

    private static List<String> listWagentScriptNames(Path dir) throws IOException {
        try (var stream = Files.list(dir)) {
            return stream.filter(Files::isRegularFile)
                    .map(p -> p.getFileName().toString())
                    .filter(n -> n.contains("wagent"))
                    .sorted()
                    .toList();
        }
    }

    private static String oneLine(String s) {
        String t = s.replaceAll("\\s+", " ").trim();
        return t.length() > 120 ? t.substring(0, 120) + "…" : t;
    }

    private static Set<String> toTree(java.util.Collection<String> in) {
        return new TreeSet<>(in);
    }

    /** 剥注释：整行 {@code --} + <b>逐行首个 {@code --} 到行尾</b> + 块注释（D-15 教训）。 */
    private static String stripComments(String sql) {
        StringBuilder sb = new StringBuilder();
        boolean inBlock = false;
        for (String line : sql.split("\r?\n")) {
            String cur = line;
            if (inBlock) {
                int end = cur.indexOf("*/");
                if (end < 0) {
                    sb.append("\n");
                    continue;
                }
                inBlock = false;
                cur = cur.substring(end + 2);
            }
            while (true) {
                int b = cur.indexOf("/*");
                if (b < 0) break;
                int e = cur.indexOf("*/", b + 2);
                if (e < 0) {
                    cur = cur.substring(0, b);
                    inBlock = true;
                    break;
                }
                cur = cur.substring(0, b) + cur.substring(e + 2);
            }
            int d = cur.indexOf("--");
            if (d >= 0) cur = cur.substring(0, d);
            sb.append(cur).append('\n');
        }
        return sb.toString();
    }

    private static Path repoRoot() throws IOException {
        Path p = Paths.get("").toAbsolutePath();
        while (p != null) {
            if (Files.isDirectory(p.resolve("ecos_backend"))
                    && Files.isDirectory(p.resolve("docs"))) return p;
            Path parent = p.getParent();
            if (parent == null || parent.equals(p)) break;
            p = parent;
        }
        throw new IOException("cwd 未锚定 ECOS 仓库根（ecos_backend/ 与 docs/ 未同时命中）: " + p);
    }
}
