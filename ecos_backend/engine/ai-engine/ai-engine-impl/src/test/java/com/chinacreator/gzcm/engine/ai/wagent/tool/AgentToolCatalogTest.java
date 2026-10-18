package com.chinacreator.gzcm.engine.ai.wagent.tool;

import com.chinacreator.gzcm.engine.ai.wagent.WAgentEngineCatalog;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 详细设计-10 F10-19（REQ-WAG-19）三验收 —— <b>业务语义工具目录</b>。
 *
 * <p>设计原文（册 §F10-19）：首批 12 个业务语义工具登记，每工具
 * {@code endpoint.kind=rest} 且指向既有 Engine 端点；<b>目录中不存在任何通用 CRUD 或
 * {@code query_db} 型工具</b>（附件 {@code query_db} 且允许 LLM 写 SQL 的形态与卷 06 C138
 * 同罪，明确排除）。三个验收名 {@code noGenericCrudOrRawSqlToolExposed} /
 * {@code everyToolMapsToEngineEndpoint} / {@code initialTwelveToolsRegistered}
 * 在本批之前<b>全仓不存在</b>（差距分析 B5 行登记的"验收缺失"项）。</p>
 *
 * <p><b>无 DB 静态判据</b>（与 gateway {@code WAgentDdlShapeComplianceTest} 同族）：
 * 直接解析单源迁移目录里的 V266 seed 文本，把 INSERT 元组按列名还原成行 Map 后判定。
 * 选静态而非 DB 取数的理由：本验收断言的是<b>登记内容</b>（哪些工具进了目录、指向哪里），
 * 该内容单源就是迁移脚本；连库反而引入"库被运维改过 ⇒ 验收随环境漂移"的第二真值。</p>
 *
 * <p><b>诚实边界</b>（见各用例内的钉住断言）：12 行里 {@code submit_readiness_task} 的目标
 * {@code /api/v1/task/submit} 在 {@link WAgentEngineCatalog} 里<b>无属主归属</b>（gateway 承流的
 * runtime-task 门面面，条目 {@code RUNTIME} 按设计刻意不留 REST 前缀）。
 * 本类不为此在白名单里开后门：既如实断言"当前不归属"，又把它钉成一条会变的断言 ——
 * 目录日后补登记该前缀，本用例即红，逼两侧同时订正。</p>
 */
class AgentToolCatalogTest {

    /** 单源迁移目录里的首批工具契约 seed（铁律 v2.2 §3.1 单源目录口径）。 */
    private static final String SEED_SCRIPT = "V266__wagent_tool_contract_seed.sql";

    private static final String TABLE = "ecos_ai.ecos_wagent_tool_contract";

    /** 设计 §F10-19 逐字列出的首批 12 个业务语义工具名。 */
    private static final Set<String> DESIGN_TWELVE = new LinkedHashSet<>(List.of(
            "query_project_profit", "get_project_acceptance_status", "run_profit_forecast",
            "find_historical_profile", "run_cognitive_model", "create_decision_option",
            "create_action_draft", "get_action_effect", "check_metric_caliber",
            "select_knowledge_profiles", "freeze_data_snapshot", "submit_readiness_task"));

    /** V236 词表单源 {@code ToolCategory} 五值（read/compute/candidate/commit/notify）。 */
    private static final Set<String> TOOL_CATEGORIES = Set.of("read", "compute", "candidate",
            "commit", "notify");

    /**
     * 通用可执行面工具名（F10-19 禁入项 = 附件 {@code query_db} 型形态 + 咽喉侧通用工具名）。
     * 判据是"目录里不得出现让 LLM 自拼 SQL/URL 的通用面"，故名单同时覆盖
     * {@code ToolExecutorService} 既有通用工具与 registry 具名工具。
     */
    private static final Set<String> GENERIC_SURFACE_NAMES = Set.of(
            "query_db", "execute_sql", "run_sql", "invoke_rest", "call_api",
            "read_file", "write_file", "patch", "search_files", "generic_crud", "crud");

    /** 通用代理端点形态（即便工具名是业务语义名，指向通用代理也算复发）。 */
    private static final List<String> GENERIC_PROXY_URL_MARKERS = List.of(
            "invoke-rest", "invoke_rest", "generic", "/sql", "execute-sql", "execute_sql");

    /** {@code submit_readiness_task} 的已登记归属缺口（差距分析 B5 批缺陷号见台账 D-B5-*）。 */
    private static final String ATTRIBUTION_GAP_TOOL = "submit_readiness_task";
    private static final String ATTRIBUTION_GAP_PATH = "/api/v1/task/submit";

    // ── 验收 1：首批 12 工具登记齐备、版本与状态形态正确 ───────────────────

    @Test
    @DisplayName("F10-19 initialTwelveToolsRegistered：seed 行名集合与设计 12 工具逐字相等，状态=registered、版本非空")
    void initialTwelveToolsRegistered() throws IOException {
        List<Map<String, String>> rows = seedRows();
        assertEquals(12, rows.size(), "V266 应恰好登记首批 12 行（多行=目录面与设计脱钩）");

        Set<String> names = new LinkedHashSet<>();
        for (Map<String, String> row : rows) {
            String name = row.get("name");
            assertNotNull(name, "行缺 name: " + row.get("id"));
            assertTrue(names.add(name), "seed 内工具名重复（uniq(name,version) 被绕过）: " + name);

            String version = row.get("version");
            assertNotNull(version, "缺 version ⇒ 无法做版本闭合定位: " + name);
            assertFalse(version.isBlank(), "version 空白 ⇒ EngineEndpointInvoker 的 name+version 地板必拒: " + name);

            // F10-17 状态机：seed 落 registered 属设计形态（active 由治理动作推进，禁 seed 直发）
            assertEquals("registered", row.get("status"), "seed 状态口径: " + name);
            assertTrue(TOOL_CATEGORIES.contains(row.get("category")),
                    "category 未命中 ToolCategory 五值: " + name + "=" + row.get("category"));
            assertNotNull(row.get("input_schema_json"), "缺 input_schema_json ⇒ 入参地板无从判: " + name);
        }
        assertEquals(DESIGN_TWELVE, names,
                "目录名单与设计 §F10-19 首批 12 工具不一致（漏项或多项都算脱钩）");
    }

    // ── 验收 2：每工具 endpoint 指向既有 Engine 端点 ────────────────────────

    @Test
    @DisplayName("F10-19 everyToolMapsToEngineEndpoint：endpoint_kind=rest + 相对路径 + 方法合法 + 命中在位 Engine 前缀族")
    void everyToolMapsToEngineEndpoint() throws IOException {
        List<Map<String, String>> rows = seedRows();
        int attributed = 0;
        for (Map<String, String> row : rows) {
            String name = row.get("name");
            String url = row.get("endpoint_url");
            assertEquals("rest", row.get("endpoint_kind"), "endpoint_kind 必须是 rest: " + name);
            assertNotNull(url, "endpoint_url 为空 ⇒ 契约通道无从出向: " + name);
            assertTrue(url.startsWith("/api/v1/"),
                    "端点必须是 /api/v1/ 打头的相对路径（绝对 URL 会让入参或配置左右 host）: " + name + "=" + url);
            assertFalse(url.contains(".."), "端点含 .. 段: " + name + "=" + url);
            assertTrue(List.of("GET", "POST").contains(row.get("endpoint_method")),
                    "endpoint_method 越界（契约通道只承认 GET/POST 形态）: " + name
                            + "=" + row.get("endpoint_method"));

            if (ATTRIBUTION_GAP_TOOL.equals(name)) {
                // 钉住已登记归属缺口：该行目标属 gateway 承流的 runtime-task 门面面，
                // WAgentEngineCatalog 刻意不给 RUNTIME 条目登记 REST 前缀 ⇒ 现在不可归属。
                assertEquals(ATTRIBUTION_GAP_PATH, url, "归属缺口钉死后路径又被改动，需同步订正台账");
                assertFalse(WAgentEngineCatalog.isLiveServicePrefix(url),
                        "该前缀若已被登记为 Engine 面，请把本断言与台账 D-B5 项一并撤掉（两侧必须同批）");
                continue;
            }
            attributed++;
            assertTrue(WAgentEngineCatalog.isLiveServicePrefix(url),
                    "端点未命中任何在位 Engine 前缀族（F10-02 属主归属破坏）: " + name + "=" + url);
        }
        assertEquals(11, attributed, "除已登记的 1 行归属缺口外，其余 11 行都必须可归属");
    }

    // ── 验收 3：目录中不存在通用 CRUD / query_db 型工具 ─────────────────────

    @Test
    @DisplayName("F10-19 noGenericCrudOrRawSqlToolExposed：12 行无通用面，且同一判据对合成的 query_db 行确实变红")
    void noGenericCrudOrRawSqlToolExposed() throws IOException {
        for (Map<String, String> row : seedRows()) {
            assertEquals(List.of(), genericSurfaceViolations(row),
                    "业务语义目录出现通用可执行面: " + row.get("name"));
        }
        // 非空洞自检：把"缺陷原形"喂给同一判据，必须能变红（否则本验收是恒真装饰）
        Map<String, String> fake = new LinkedHashMap<>();
        fake.put("name", "query_db");
        fake.put("category", "read");
        fake.put("endpoint_kind", "rest");
        fake.put("endpoint_url", "/api/v1/datanet/sql/execute");
        fake.put("endpoint_method", "POST");
        fake.put("input_schema_json", "{\"type\":\"object\",\"properties\":{\"sql\":{\"type\":\"string\"}}}");
        assertFalse(genericSurfaceViolations(fake).isEmpty(),
                "判据失效：通用 SQL 工具形态未被拒绝");
    }

    /** 一行的「通用面」违例清单；空集即合规。 */
    private static List<String> genericSurfaceViolations(Map<String, String> row) {
        List<String> v = new ArrayList<>();
        String name = row.get("name");
        if (name != null && GENERIC_SURFACE_NAMES.contains(name.trim().toLowerCase(java.util.Locale.ROOT))) {
            v.add("工具名属通用面名单: " + name);
        }
        String url = row.get("endpoint_url") == null ? "" : row.get("endpoint_url").toLowerCase(java.util.Locale.ROOT);
        for (String marker : GENERIC_PROXY_URL_MARKERS) {
            if (url.contains(marker)) {
                v.add("端点指向通用代理面: " + marker);
            }
        }
        String schema = row.get("input_schema_json") == null ? "" : row.get("input_schema_json");
        // 允许 LLM 递交 SQL 文本 = 卷 06 C138 同罪形态（与 ToolAdjudicationRequest 的
        // 「SQL 原文只进本地地板校验、不入裁决载荷」纪律同源）
        if (schema.contains("\"sql\"")) {
            v.add("入参模式携带 sql 字段");
        }
        if ("commit".equals(row.get("category"))
                && (row.get("rollback_plan_text") == null || row.get("rollback_plan_text").isBlank())) {
            v.add("commit 类工具缺回滚预案（V236 ck_wagent_tool_rollback）");
        }
        return v;
    }

    // ── V266 文本解析（无 DB；按列名还原 INSERT 元组） ──────────────────────

    /** 解析 seed 的 INSERT 语句：列名 → 值，NULL 折成 {@code null}，单引号 {@code ''} 转义还原。 */
    private static List<Map<String, String>> seedRows() throws IOException {
        String sql = stripSqlComments(Files.readString(migrationDir().resolve(SEED_SCRIPT)));
        int insertAt = sql.indexOf("INSERT INTO " + TABLE);
        assertTrue(insertAt >= 0, "seed 里找不到目标表 INSERT: " + TABLE);
        int colsStart = sql.indexOf('(', insertAt);
        int colsEnd = sql.indexOf(')', colsStart);
        List<String> columns = new ArrayList<>();
        for (String column : splitTopLevel(sql.substring(colsStart + 1, colsEnd))) {
            columns.add(unquote(column));
        }

        int valuesAt = sql.indexOf("VALUES", colsEnd);
        assertTrue(valuesAt > 0, "seed 缺 VALUES 段");
        int conflictAt = sql.indexOf("ON CONFLICT", valuesAt);
        String tuples = sql.substring(valuesAt + "VALUES".length(), conflictAt > 0 ? conflictAt : sql.length());

        List<Map<String, String>> rows = new ArrayList<>();
        for (String tuple : topLevelTuples(tuples)) {
            List<String> values = splitTopLevel(tuple);
            assertEquals(columns.size(), values.size(),
                    "列数与值数不符（seed 结构变化需同步本验收）: 列=" + columns.size() + " 值=" + values.size());
            Map<String, String> row = new LinkedHashMap<>();
            for (int i = 0; i < columns.size(); i++) {
                String raw = values.get(i).trim();
                row.put(columns.get(i), "NULL".equalsIgnoreCase(raw) ? null : unquote(raw));
            }
            rows.add(row);
        }
        assertFalse(rows.isEmpty(), "seed 未解析出任何行（判据将恒真，必须修解析而非放宽断言）");
        return rows;
    }

    /** 取顶层 {@code (...)} 组，字符串字面量内的括号不参与计数。 */
    private static List<String> topLevelTuples(String body) {
        List<String> out = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inString = false;
        int depth = 0;
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (c == '\'') {
                if (inString && i + 1 < body.length() && body.charAt(i + 1) == '\'') {
                    current.append("''");
                    i++;
                    continue;
                }
                inString = !inString;
                if (depth > 0) current.append(c);
                continue;
            }
            if (!inString && c == '(') {
                depth++;
                if (depth == 1) {
                    current.setLength(0);
                    continue;
                }
            }
            if (!inString && c == ')') {
                depth--;
                if (depth == 0) {
                    out.add(current.toString());
                    current.setLength(0);
                    continue;
                }
            }
            if (depth > 0) current.append(c);
        }
        assertEquals(0, depth, "括号不配对：解析器与 seed 形态脱节");
        return out;
    }

    /** 顶层逗号切分（字符串与嵌套括号内的逗号不算分隔）。 */
    private static List<String> splitTopLevel(String body) {
        List<String> out = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inString = false;
        int depth = 0;
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (c == '\'') {
                if (inString && i + 1 < body.length() && body.charAt(i + 1) == '\'') {
                    current.append("''");
                    i++;
                    continue;
                }
                inString = !inString;
                current.append(c);
                continue;
            }
            if (!inString && (c == '(' || c == '[')) depth++;
            if (!inString && (c == ')' || c == ']')) depth--;
            if (!inString && c == ',' && depth == 0) {
                out.add(current.toString());
                current.setLength(0);
                continue;
            }
            current.append(c);
        }
        if (!current.isEmpty()) out.add(current.toString());
        return out;
    }

    private static String unquote(String raw) {
        String value = raw.trim();
        if (value.length() >= 2 && value.charAt(0) == '\'' && value.charAt(value.length() - 1) == '\'') {
            return value.substring(1, value.length() - 1).replace("''", "'");
        }
        return value;
    }

    /** 剥 {@code --} 行注释与 {@code /* }块注释，字符串字面量原文保留（注释里的示例 SQL 不得进判据）。 */
    private static String stripSqlComments(String sql) {
        StringBuilder out = new StringBuilder(sql.length());
        boolean inString = false;
        int i = 0;
        while (i < sql.length()) {
            char c = sql.charAt(i);
            char next = i + 1 < sql.length() ? sql.charAt(i + 1) : 0;
            if (c == '\'') {
                inString = !inString;
                out.append(c);
                i++;
                continue;
            }
            if (!inString && c == '-' && next == '-') {
                while (i < sql.length() && sql.charAt(i) != '\n') i++;
                continue;
            }
            if (!inString && c == '/' && next == '*') {
                i += 2;
                while (i < sql.length() && !(sql.charAt(i) == '*' && i + 1 < sql.length()
                        && sql.charAt(i + 1) == '/')) i++;
                i += 2;
                continue;
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }

    /** DDL 单源目录（铁律 v2.2 §3.1）：上溯到含该目录的仓根，禁写本机绝对路径。 */
    private static Path migrationDir() {
        Path cur = Paths.get("").toAbsolutePath();
        while (cur != null) {
            Path dir = cur.resolve("docs/50-db_script/02-flyway-migration/gateway");
            if (Files.isDirectory(dir)) return dir;
            cur = cur.getParent();
        }
        throw new IllegalStateException("定位不到 DDL 单源目录 docs/50-db_script/02-flyway-migration/gateway");
    }
}
