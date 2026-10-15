package com.chinacreator.gzcm.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * #140 门禁补实现批 · 子批 C-1 —— 本体域「禁度量金额列」DDL 形态门禁
 * （详设-03 line 447 规格：<i>本体域只产定义与版本，不产数值；任何金额数值出现在本体表即视为越界，
 * {@code OntologyNoFactValueArchTest} 断言本体 DDL 不含 {@code NUMERIC(18,2)} 度量列，
 * 除口径元数据的单位/精度描述列外</i>；差距登记见实现差距分析 §1.6 门禁 4 / §6.2.5 / §6.7 / §15.3）。
 *
 * <p><b>为什么这是「补代码实现」而不是「补一份空转测试」</b>（用户 R9② 裁决口径）：
 * 被测不变量已经存在于 DDL 单源
 * {@code docs/50-db_script/02-flyway-migration/gateway/}（架构铁律 v2.2 §3.1 权威单源）——
 * 本域全部建表脚本的列形态是可静态解析的制品，门禁把它从「文档里的红线」变成「可跑的断言」。</p>
 *
 * <p><b>类名落差登记（诚实，不改名掩盖）</b>：规格给的类名带 {@code ArchTest}，
 * 但规格判据是 <b>DDL 列形态</b>而非 Java 依赖方向，故按同族静态 DDL 扫描
 * （{@code AiDdlComplianceTest} / {@code WAgentDdlShapeComplianceTest}）实现，
 * 不引 ArchUnit —— 用 ArchUnit 断言 Java 类根本表达不了「表里不能有金额列」。</p>
 *
 * <p><b>扫描面判定规则</b>（单点定义在 {@link #isOntologyDomainTable}）：
 * schema ∈ {ecos_ontology, ecos_object, ecos_workflow}（本体域三 schema，ST07 口径）
 * 或裸表名命中本域前缀清单；扫出的表集合与 {@link #PINNED_ONTOLOGY_TABLES} 做<b>双向集合相等</b>，
 * 防「作用域解析为空 ⇒ 恒 PASS」这一本仓已登记的假绿形态（D-16 同源教训）。</p>
 *
 * <p><b>反假绿三把锁</b>：
 * <ol>
 *   <li>双向钉死扫描面（{@link #scannedTableUniverseIsExactlyThePinnedSet()}）；</li>
 *   <li>解析器自检（{@link #scannerActuallySeesTheKnownShapes()}）：必须真看到
 *       V273 的 {@code confidence_value NUMERIC(6,5)}、必须真看到 V167.1 的
 *       {@code unit VARCHAR(32)}、必须在全单源里真抓到 NUMERIC(18,2) 命中 ——
 *       解析器退化成"什么都看不见"时门禁立刻变红而不是变绿；</li>
 *   <li>注释剥离沿用 D-15 教训（{@link #stripComments}：块注释 + <b>逐行首个 {@code --} 到行尾</b>），
 *       既不把注释里的"JSONB/NUMERIC"字样误判成违例（假红），也不因整行注释漏剥而放过真实列（假绿）。</li>
 * </ol>
 *
 * <p><b>钉死豁免（逐条带理由，禁静默扩大）</b>：见
 * {@link #CONTROL_SCHEMA_ALLOWED_NON_MONETARY_NUMERIC} —— 实扫本域 NUMERIC 形态只有 1 枚
 * （{@code ecos_ontology.ecos_ontology_candidate.confidence_value NUMERIC(6,5)}，提案置信度评分，
 * 非金额度量）。规格那句"除口径元数据的单位/精度描述列外"<b>实测为空集</b>
 * （{@code unit} 是 VARCHAR(32)、{@code currency} 是 VARCHAR(8)、精度类列全仓用 INT/INTEGER），
 * 因此本类不预留"单位/精度列"式宽口子，只钉这一枚具体列：口径元数据永不得借该口子加 NUMERIC 金额列。</p>
 *
 * <p>验收：{@code mvn -o test -pl gateway -Dtest='OntologyNoFactValueArchTest'}
 * （从 {@code ecos_backend/} 执行）。</p>
 */
@DisplayName("#140/C-1：本体域禁度量金额列（NUMERIC(18,2) 与任意数值度量列形态门禁）")
class OntologyNoFactValueArchTest {

    private static final String MIGRATION_REL =
            "docs/50-db_script/02-flyway-migration/gateway";

    /** 本体域三 schema（ST07 控制域内本体侧；outbox 事件表亦属本域写通道）。 */
    private static final Set<String> ONTOLOGY_SCHEMAS = Set.of(
            "ecos_ontology", "ecos_object", "ecos_workflow");

    /** 历史裸表（public schema，v1 时代前缀命名，多数属 knownLegacy 只停写不迁移）仍在扫描面内：停写表更不得新增金额列。 */
    private static final Set<String> ONTOLOGY_TABLE_PREFIXES = Set.of(
            "ecos_ontology_", "ecos_object_", "ecos_workflow_", "ecos_caliber",
            "ecos_metric_definition", "ecos_glossary_", "ecos_function_audit",
            "ecos_entity_table_mapping");

    /**
     * 实扫钉死的本体域表全集（双向相等，见 {@link #scannedTableUniverseIsExactlyThePinnedSet()}）。
     * 名称形态 = {@code schema.table}，无 schema 限定时以 {@code public.} 前缀显式登记。
     */
    private static final Set<String> PINNED_ONTOLOGY_TABLES = Set.of(
            "ecos_object.outbox_event",
            "ecos_ontology.action_definition",
            "ecos_ontology.ecos_caliber",
            "ecos_ontology.ecos_caliber_version",
            "ecos_ontology.ecos_entity_table_mapping",
            "ecos_ontology.ecos_function_audit",
            "ecos_ontology.ecos_metric_definition",
            "ecos_ontology.ecos_ontology_candidate",
            "ecos_ontology.ecos_ontology_proposal",
            "ecos_ontology.ecos_workflow",
            "ecos_ontology.ecos_workflow_approval",
            "ecos_ontology.ecos_workflow_edge",
            "ecos_ontology.ecos_workflow_node",
            "ecos_ontology.entity_definition",
            "ecos_ontology.event_definition",
            "ecos_ontology.metric_definition",
            "ecos_ontology.outbox_event",
            "ecos_ontology.policy_definition",
            "ecos_ontology.relationship_definition",
            "ecos_workflow.outbox_event",
            "public.ecos_function_audit_log",
            "public.ecos_glossary_term",
            "public.ecos_glossary_term_relation",
            "public.ecos_object_attachment",
            "public.ecos_object_links",
            "public.ecos_object_relationship",
            "public.ecos_object_state_machine",
            "public.ecos_object_timeline",
            "public.ecos_object_version",
            "public.ecos_ontology_action",
            "public.ecos_ontology_data",
            "public.ecos_ontology_domain",
            "public.ecos_ontology_entity",
            "public.ecos_ontology_git_archive_pending",
            "public.ecos_ontology_property",
            "public.ecos_ontology_proposals",
            "public.ecos_ontology_relationship",
            "public.ecos_ontology_rule",
            "public.ecos_ontology_version",
            "public.ecos_ontology_workbench_object",
            "public.ecos_workflow_approval",
            "public.ecos_workflow_instance",
            "public.ecos_workflow_log",
            "public.ecos_workflow_task");

    /**
     * 本域允许的非金额 NUMERIC 列（唯一豁免，理由见类注释）。
     * key = {@code schema.table}，value = {@code 列名 大写类型(p,s)} 集合（大小写归一后比对）。
     */
    private static final Map<String, Set<String>> CONTROL_SCHEMA_ALLOWED_NON_MONETARY_NUMERIC = Map.of(
            "ecos_ontology.ecos_ontology_candidate", Set.of("CONFIDENCE_VALUE NUMERIC(6,5)"));

    /**
     * 全单源 {@code NUMERIC(18,2)}（金额唯一合法形态，数据库访问规范 MC01 v1.2）出现的脚本文件名钉死基线：
     * 新增金额列必须先进这份登记表（且只能落业务域 {@code ecos_dw}），防止"顺手在本域加一枚金额列"。
     * 登记含历史债（public 侧 v1 表），其归属由 {@link #moneyScale182NeverLandsInOntologyDomain()} 单独裁走。
     */
    private static final Set<String> PINNED_SCRIPTS_CONTAINING_MONEY_NUMERIC = Set.of(
            "V17__ecos_goal_hierarchy.sql",
            "V31__ecos_goal_tracking_config.sql",
            "V167__biz_fact_model.sql",
            "V205__ecos_business_scenario_v2.sql",
            "V211__ecos_decision_record_v2.sql",
            "V213__ecos_scenario_legacy_readonly_views.sql",
            "V216__fc_result_detail.sql",
            "V218__fc_action_ext.sql",
            "V279__dw_action_outcome.sql",
            "V286__ecos_dw_wagent_option_amount.sql");

    /** 数值度量类型（金额/浮点一律禁入本域；整数类枚举/开关列不在禁列，见 {@link #MONEY_NAME_TOKENS} 的语义列名判据）。 */
    private static final Pattern NUMERIC_TYPE = Pattern.compile(
            "^(NUMERIC|DECIMAL|FLOAT|DOUBLE|REAL|NUMBER|MONEY|SMALLMONEY|BFLOAT4|BFLOAT8)(\\s*\\([^)]*\\))?$",
            Pattern.CASE_INSENSITIVE);

    /** 金额语义列名词根：命中即视为「本域存数值」，无论声明类型（TEXT 存金额同样是越界，规格原文「任何金额数值」）。 */
    private static final Set<String> MONEY_NAME_TOKENS = Set.of(
            "amount", "money", "price", "cost", "fee", "revenue", "profit", "cash",
            "balance", "salary", "payable", "receivable");

    // ═══════════════════════ ① 扫描面自证 ═══════════════════════

    @Test
    @DisplayName("本体域建表面 = 钉死清单（双向相等；扫描面为空或被挪走即变红，禁空转假绿）")
    void scannedTableUniverseIsExactlyThePinnedSet() throws IOException {
        List<TableShape> tables = ontologyTables();
        Set<String> discovered = new TreeSet<>();
        for (TableShape t : tables) discovered.add(t.qualifiedName);
        assertTrue(discovered.size() >= 30,
                "本体域扫描面异常缩小（< 30 张）⇒ 解析器或 schema 规则失效，此时任何 PASS 都不可信: "
                        + discovered);
        assertEquals(new TreeSet<>(PINNED_ONTOLOGY_TABLES), discovered,
                "本体域表清单与钉死基线不符（新增/改名/挪走脚本都必须先在本类登记，禁静默扩大扫描面）");
    }

    @Test
    @DisplayName("解析器自检：必须真看到 V273 的 NUMERIC(6,5)、V167.1 的 unit VARCHAR(32)、以及全单源的 NUMERIC(18,2)")
    void scannerActuallySeesTheKnownShapes() throws IOException {
        List<TableShape> tables = ontologyTables();

        TableShape candidate = null;
        for (TableShape t : tables) {
            if ("ecos_ontology.ecos_ontology_candidate".equals(t.qualifiedName)) candidate = t;
        }
        assertTrue(candidate != null, "扫描面丢失 V273 的 ecos_ontology_candidate ⇒ 表体解析退化");
        String confidence = typeOf(candidate, "confidence_value");
        assertEquals("NUMERIC(6,5)", confidence,
                "解析器必须能把 confidence_value 的类型读成 NUMERIC(6,5)（豁免判据的比对前提）");

        TableShape caliber = null;
        for (TableShape t : tables) {
            if ("ecos_ontology.ecos_caliber".equals(t.qualifiedName)) caliber = t;
        }
        assertTrue(caliber != null, "扫描面丢失 V167.1 的 ecos_caliber ⇒ 表体解析退化");
        assertEquals("VARCHAR(32)", typeOf(caliber, "unit"),
                "口径元数据的单位列必须是 VARCHAR(32)（规格豁免对象实测非 NUMERIC 形态，此断言即该结论的钉）");

        int corpusHits = moneyNumeric182Occurrences();
        assertTrue(corpusHits >= 10,
                "全单源 NUMERIC(18,2) 命中数骤减（< 10）⇒ 剥离/遍历退化，"
                        + "本类的『本域零命中』将变成恒真的假绿: 实扫=" + corpusHits);
    }

    // ═══════════════════════ ② 规格红线本体 ═══════════════════════

    @Test
    @DisplayName("本域禁 NUMERIC(18,2)（规格字面判据：金额数值出现即越界）")
    void noMoneyScaleNumericColumnsInOntologyDomain() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (TableShape t : ontologyTables()) {
            for (String[] col : t.columns) {
                String type = normalize(col[1]);
                if (type.startsWith("NUMERIC(18,2)") || type.startsWith("DECIMAL(18,2)")) {
                    offenders.add(t.script + ":" + t.startLine + " " + t.qualifiedName
                            + "." + col[0] + " " + col[1]);
                }
            }
        }
        assertTrue(offenders.isEmpty(),
                "铁律 §0.6 + ADR-14 + 详设-03 line 447：本体域只产定义与版本，禁金额度量列 NUMERIC(18,2)。违例:\n  "
                        + String.join("\n  ", offenders)
                        + "\n金额列唯一合法落点 = 业务域 ecos_dw（数据库访问规范 ST03-A / MC01）。");
    }

    @Test
    @DisplayName("本域禁任意数值度量列（NUMERIC/DECIMAL/FLOAT/DOUBLE/REAL/NUMBER/MONEY…），唯一豁免=钉死的置信度列")
    void noNumericMeasureColumnsBeyondThePinnedExemption() throws IOException {
        List<String> offenders = new ArrayList<>();
        Map<String, Set<String>> usedExemptions = new LinkedHashMap<>();
        for (TableShape t : ontologyTables()) {
            for (String[] col : t.columns) {
                if (!NUMERIC_TYPE.matcher(col[1].trim()).matches()) continue;
                String normalized = normalize(col[0]) + " " + normalize(col[1]);
                Set<String> allowed = CONTROL_SCHEMA_ALLOWED_NON_MONETARY_NUMERIC
                        .getOrDefault(t.qualifiedName, Set.of());
                if (allowed.contains(normalized)) {
                    usedExemptions.computeIfAbsent(t.qualifiedName, k -> new TreeSet<>()).add(normalized);
                    continue;
                }
                offenders.add(t.script + ":" + t.startLine + " " + t.qualifiedName + "."
                        + col[0] + " " + col[1]);
            }
        }
        assertTrue(offenders.isEmpty(),
                "本域出现未登记豁免的数值度量列。规格只放行「口径元数据的单位/精度描述列」，"
                        + "而实测该类描述列全为 VARCHAR/INT ⇒ 数值列口子已封死，加列须先裁决并在此登记。违例:\n  "
                        + String.join("\n  ", offenders));
        // 豁免必须被真实用到：若那枚列被改名/删除，白名单就成了悬空口子（防"登记了但扫不到"的静默漂移）
        assertEquals(CONTROL_SCHEMA_ALLOWED_NON_MONETARY_NUMERIC.keySet(), usedExemptions.keySet(),
                "钉死的非金额 NUMERIC 豁免未全部命中实际列 ⇒ 基线过期，必须同步订正而不是留着当空口子");
        for (Map.Entry<String, Set<String>> e : CONTROL_SCHEMA_ALLOWED_NON_MONETARY_NUMERIC.entrySet()) {
            assertEquals(e.getValue(), usedExemptions.get(e.getKey()),
                    "表 " + e.getKey() + " 的豁免列集合与实扫不符（禁借豁免面加列）");
        }
    }

    @Test
    @DisplayName("本域禁金额语义列名（TEXT 存金额同样越界：规格判据是「任何金额数值」而非仅类型）")
    void noMonetaryNamedColumnsInOntologyDomain() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (TableShape t : ontologyTables()) {
            for (String[] col : t.columns) {
                String lower = col[0].toLowerCase();
                for (String token : MONEY_NAME_TOKENS) {
                    if (lower.contains(token)) {
                        offenders.add(t.script + " " + t.qualifiedName + "." + col[0] + " " + col[1]
                                + "（词根 " + token + "）");
                        break;
                    }
                }
            }
        }
        assertTrue(offenders.isEmpty(),
                "本域出现金额语义列名的列 ⇒ 「只产定义不产数值」的语义面失守。"
                        + "若确为引用位（如 amount_ref）必须改名为 *_ref 之外的中性名或登记裁决依据。违例:\n  "
                        + String.join("\n  ", offenders));
    }

    @Test
    @DisplayName("金额形态 NUMERIC(18,2) 全单源命中脚本 = 钉死基线，且零条属本体域")
    void moneyScaleNumericSurfaceIsPinnedAndNeverOntological() throws IOException {
        Set<String> scripts = new TreeSet<>();
        Path dir = migrationDir();
        try (Stream<Path> s = Files.list(dir)) {
            s.filter(p -> p.getFileName().toString().matches("V.*\\.sql"))
                    .forEach(p -> {
                        try {
                            String sql = stripComments(Files.readString(p, StandardCharsets.UTF_8));
                            if (Pattern.compile("\\b(NUMERIC|DECIMAL)\\s*\\(\\s*18\\s*,\\s*2\\s*\\)",
                                    Pattern.CASE_INSENSITIVE).matcher(sql).find()) {
                                scripts.add(p.getFileName().toString());
                            }
                        } catch (IOException e) {
                            throw new IllegalStateException("读取失败: " + p, e);
                        }
                    });
        }
        assertEquals(new TreeSet<>(PINNED_SCRIPTS_CONTAINING_MONEY_NUMERIC), scripts,
                "全单源金额形态命中面与钉死基线不符：新增金额脚本必须先登记（且只能落 ecos_dw），"
                        + "脚本被删除也要在此订正，禁让门禁对现实视而不见");
        for (TableShape t : ontologyTables()) {
            for (String[] col : t.columns) {
                assertFalse(normalize(col[1]).startsWith("NUMERIC(18,2)"),
                        "本域表 " + t.qualifiedName + " 命中金额形态列 " + col[0]);
            }
        }
    }

    // ═══════════════════════ 解析辅助 ═══════════════════════

    /** 本体域建表（qualified + bare 两形态）与逐列类型。 */
    private static List<TableShape> ontologyTables() throws IOException {
        Path dir = migrationDir();
        List<String> scripts;
        try (Stream<Path> s = Files.list(dir)) {
            scripts = s.map(p -> p.getFileName().toString())
                    .filter(n -> n.matches("V.*\\.sql"))
                    .sorted()
                    .toList();
        }
        Pattern qualified = Pattern.compile(
                "CREATE\\s+TABLE(?:\\s+IF\\s+NOT\\s+EXISTS)?\\s+([A-Za-z0-9_]+)\\.([A-Za-z0-9_]+)\\s*\\(",
                Pattern.CASE_INSENSITIVE);
        Pattern bare = Pattern.compile(
                "CREATE\\s+TABLE(?:\\s+IF\\s+NOT\\s+EXISTS)?\\s+([A-Za-z0-9_]+)\\s*\\(",
                Pattern.CASE_INSENSITIVE);
        List<TableShape> out = new ArrayList<>();
        for (String script : scripts) {
            String sql = stripComments(Files.readString(dir.resolve(script), StandardCharsets.UTF_8));
            for (int[] pass = {0}; pass[0] < 2; pass[0]++) {
                Matcher m = (pass[0] == 0 ? qualified : bare).matcher(sql);
                while (m.find()) {
                    String schema = pass[0] == 0 ? m.group(1) : "public";
                    String table = pass[0] == 0 ? m.group(2) : m.group(1);
                    if (!isOntologyDomainTable(schema, table)) continue;
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
                    assertTrue(depth == 0, script + " 的 " + schema + "." + table + " 括号未配平（解析前提失效）");
                    List<String[]> cols = columns(sql.substring(open + 1, i));
                    int startLine = 1 + (int) sql.substring(0, m.start()).chars().filter(c -> c == '\n').count();
                    out.add(new TableShape(script, schema + "." + table, table, cols, startLine));
                }
            }
        }
        return out;
    }

    private static boolean isOntologyDomainTable(String schema, String table) {
        String s = schema.toLowerCase(), t = table.toLowerCase();
        if (ONTOLOGY_SCHEMAS.contains(s)) return true;
        if (!"public".equals(s)) return false;
        for (String prefix : ONTOLOGY_TABLE_PREFIXES) {
            if (t.startsWith(prefix)) return true;
        }
        return false;
    }

    private static Path migrationDir() throws IOException {
        return repoRoot().resolve(MIGRATION_REL);
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

    /** 全单源 NUMERIC/DECIMAL(18,2) 命中次数（列与视图 CAST 均计，作解析器自检的第二把尺）。 */
    private static int moneyNumeric182Occurrences() throws IOException {
        Path dir = migrationDir();
        Pattern money = Pattern.compile("\\b(NUMERIC|DECIMAL)\\s*\\(\\s*18\\s*,\\s*2\\s*\\)",
                Pattern.CASE_INSENSITIVE);
        int hits = 0;
        List<String> scripts;
        try (Stream<Path> s = Files.list(dir)) {
            scripts = s.map(p -> p.getFileName().toString()).filter(n -> n.matches("V.*\\.sql"))
                    .sorted().toList();
        }
        for (String script : scripts) {
            String sql = stripComments(Files.readString(dir.resolve(script), StandardCharsets.UTF_8));
            Matcher m = money.matcher(sql);
            while (m.find()) hits++;
        }
        return hits;
    }

    private static String typeOf(TableShape t, String column) {
        for (String[] c : t.columns) {
            if (c[0].equalsIgnoreCase(column)) return normalize(c[1]);
        }
        return null;
    }

    private static String normalize(String s) {
        return s == null ? null : s.replaceAll("\\s+", " ").trim().toUpperCase();
    }

    /** 表体内顶层（括号深度 0）列定义 → [列名, 类型串（含括号）]。 */
    private static List<String[]> columns(String body) {
        List<String[]> out = new ArrayList<>();
        for (String raw : splitTopLevel(body)) {
            String frag = raw.trim();
            if (frag.isEmpty()) continue;
            Matcher m = Pattern.compile(
                    "^([A-Za-z_][A-Za-z0-9_]*)\\s+([A-Za-z][A-Za-z0-9_]*(?:\\s*\\([^)]*\\))?)",
                    Pattern.CASE_INSENSITIVE).matcher(frag);
            if (!m.find()) continue;
            String name = m.group(1);
            if (Set.of("constraint", "primary", "unique", "foreign", "check", "like").contains(name.toLowerCase())) {
                continue;
            }
            out.add(new String[] {name, m.group(2).replaceAll("\\s+", " ").trim()});
        }
        return out;
    }

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

    /** 剥注释：块注释 + 整行 {@code --} + <b>逐行首个 {@code --} 到行尾</b>（D-15 教训）。 */
    private static String stripComments(String sql) {
        StringBuilder sb = new StringBuilder();
        boolean inBlock = false;
        for (String line : sql.split("\r?\n")) {
            String cur = line;
            if (inBlock) {
                int end = cur.indexOf("*/");
                if (end < 0) {
                    sb.append('\n');
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

    /** 一条本体域建表记录。 */
    private static final class TableShape {
        private final String script;
        private final String qualifiedName;
        private final String table;
        private final List<String[]> columns;
        private final int startLine;

        private TableShape(String script, String qualifiedName, String table,
                           List<String[]> columns, int startLine) {
            this.script = script;
            this.qualifiedName = qualifiedName;
            this.table = table;
            this.columns = columns;
            this.startLine = startLine;
        }
    }
}
