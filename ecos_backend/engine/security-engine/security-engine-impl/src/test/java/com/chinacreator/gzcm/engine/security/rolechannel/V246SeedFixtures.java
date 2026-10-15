package com.chinacreator.gzcm.engine.security.rolechannel;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 详设-01 §4.7.2「角色 × 通道验收矩阵」页面通道用例集的<b>数据单源夹具</b>（#142/B4-3a）。
 *
 * <p><b>为什么存在</b>：矩阵的四行（财务负责人 / 项目负责人 p1 / 部门用户 d01 / 无权用户）
 * 验收的是「策略集 → 谓词/列投影」的裁决结果，而策略集的唯一真值是
 * {@code docs/50-db_script/02-flyway-migration/gateway/V246__security_policy_seed.sql}
 * （{@code SecuritySeedConsistencyTest} 已把它当单源校验）。本类<b>只解析、不内联</b>策略内容：
 * 任何人改 V246，四个具名用例的输入随之变化，不会出现"测试里另抄一份策略"的第二套真值。</p>
 *
 * <p><b>选取语义的来源（关键，防"测 mock"）</b>：{@link #selectRlsPolicies} /
 * {@link #selectClsPolicies} 复刻的是 DAO 的 WHERE 语义（enabled / user_id 命中或为空 /
 * role_id 为空或在 {@code td_user_role} 里 / priority 升序）。该语义<b>不是手写想当然</b>，
 * 而是本批在 dev PostgreSQL（容器 {@code ecos-postgres}，库 {@code sys_man}）上以
 * 「按 DDL 单源建的 TEMP 影子表 + 真实 PG 优化器执行」实跑出来的（凭证见差距文档 v1.0.14）：
 * 修复前的引号大写形态直接 {@code ERROR: column "ROLE_ID" does not exist}，
 * 修复后同一语句按角色各命中 1 条（u-fin→rls-finance-all、u-p1→rls-project-owner、
 * u-d01→rls-dept-scope、u-none→0 行）。因此本夹具承担的是「决策代数」证据，
 * SQL 可执行性证据由 {@code RoleResolutionSqlShapeTest} + dev PG 实跑凭证共同钉住。</p>
 *
 * <p><b>角色归属是矩阵前提，不是本仓现状</b>：{@link #DEMO_ROLE_ASSIGNMENT} 声明四个演示主体
 * 各自持有哪个角色。仓内<b>没有</b> {@code td_user_role} 的演示角色分配 seed
 * （V246 只播策略表，{@code ecos_control.td_user_role} 实数仅 1 行 {@code u-admin/role_admin}），
 * 因此矩阵的「live 端到端」半边必须等 seed 落库 + 实跑，已登记为差距文档剩余项，
 * 本批不用假数据把它冒充成已完成。</p>
 */
public final class V246SeedFixtures {

    /** 演示主体：财务负责人（唯一 1=1 全量主体）。 */
    public static final String FIN_USER = "u-fin";
    /** 演示主体：项目负责人 p1（本部门项目归属行 + 非财务隐藏时薪/金额）。 */
    public static final String PROJ_USER = "u-p1";
    /** 演示主体：部门用户 d01。 */
    public static final String DEPT_USER = "u-d01";
    /** 演示主体：无任何生效角色的用户（矩阵「无权用户」行）。 */
    public static final String NO_ROLE_USER = "u-none";

    /** 矩阵行 → 角色归属（V246 用到的角色 id 字面量：finance_lead / project_owner / dept_user / non_finance）。 */
    private static final Map<String, Set<String>> DEMO_ROLE_ASSIGNMENT = Map.of(
            FIN_USER, Set.of("finance_lead"),
            PROJ_USER, Set.of("project_owner", "non_finance"),
            DEPT_USER, Set.of("dept_user", "non_finance"),
            NO_ROLE_USER, Set.of("no_such_role"));

    /** 三张预测事实表（V246 播种对象，取自 seed 文件的 table_name 列，非内联常量）。 */
    public static final String T_PERIOD = "ecos_dw.fc_project_period_fact";
    public static final String T_COST = "ecos_dw.fc_cost_allocation_fact";
    public static final String T_RESOURCE = "ecos_dw.fc_resource_input_fact";

    /** fc_cost_allocation_fact 的请求列集（含敏感列 hourly_rate/amount）。 */
    public static final List<String> COST_COLUMNS =
            List.of("project_id", "department_id", "period", "amount", "hourly_rate");
    /** fc_resource_input_fact 的请求列集（含敏感列 hourly_rate）。 */
    public static final List<String> RESOURCE_COLUMNS =
            List.of("project_id", "department_id", "staff_ref", "hours", "hourly_rate");

    private static final String RLS_INSERT_PREFIX = "INSERT INTO public.ecos_rls_policy";
    private static final String CLS_INSERT_PREFIX = "INSERT INTO public.ecos_cls_policy";

    /** V246 rls 12 列序（与 seed 文件 INSERT 列清单一致）。 */
    private static final List<String> RLS_COLS = List.of(
            "id", "policy_name", "table_name", "filter_expr", "role_id", "priority",
            "enabled", "description", "predicate_template", "bindings_json", "source_kind", "create_by");
    /** V246 cls 14 列序。 */
    private static final List<String> CLS_COLS = List.of(
            "id", "policy_name", "table_name", "visible_cols", "blocked_cols", "role_id", "priority",
            "enabled", "description", "column_name", "mode", "scope_type", "scope_id", "create_by");

    private static final Pattern BINDING = Pattern.compile(
            "\\{\\s*\"name\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*\"source\"\\s*:\\s*\"([^\"]+)\""
                    + "(?:\\s*,\\s*\"value\"\\s*:\\s*([^,}]+))?\\s*\\}");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final List<Map<String, Object>> rlsRows;
    private final List<Map<String, Object>> clsRows;

    private V246SeedFixtures(List<Map<String, Object>> rlsRows, List<Map<String, Object>> clsRows) {
        this.rlsRows = rlsRows;
        this.clsRows = clsRows;
    }

    /** 解析 V246（RLS + CLS 两类 seed）；文件缺失或解析为空即抛（禁把"解析不到"当成"无策略"）。 */
    public static V246SeedFixtures load() throws IOException {
        String sql = Files.readString(resolveSeedFile());
        List<Map<String, Object>> rls = parseInserts(sql, RLS_INSERT_PREFIX, RLS_COLS);
        List<Map<String, Object>> cls = parseInserts(sql, CLS_INSERT_PREFIX, CLS_COLS);
        if (rls.size() < 9) {
            throw new IllegalStateException("V246 RLS 种子解析异常（期望 ≥9 条，实得 " + rls.size()
                    + "）——禁把解析失败当成「无策略」");
        }
        if (cls.size() < 2) {
            throw new IllegalStateException("V246 CLS 种子解析异常（期望 ≥2 条，实得 " + cls.size() + "）");
        }
        return new V246SeedFixtures(rls, cls);
    }

    /** 矩阵前提：演示主体的角色归属（见类注释；无 seed 支撑，属登记项）。 */
    public static Set<String> rolesFor(String userId) {
        Set<String> roles = DEMO_ROLE_ASSIGNMENT.get(userId);
        if (roles == null) {
            throw new IllegalArgumentException("未登记的演示主体: " + userId
                    + "（矩阵四行为 FIN_USER/PROJ_USER/DEPT_USER/NO_ROLE_USER）");
        }
        return roles;
    }

    /**
     * RLS 命中集：复刻 {@code RowLevelSecurityDao#findPoliciesForUser} 的 WHERE 语义，
     * 返回列名与该 DAO 的 SELECT 别名一致（{@code filter_expr/priority/policy_name/
     * predicate_template/bindings_json/source_kind}），按 priority 升序。
     */
    public List<Map<String, Object>> selectRlsPolicies(String tableName, String userId) {
        Set<String> roles = rolesFor(userId);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> row : rlsRows) {
            if (!tableName.equals(str(row.get("table_name")))
                    && !tableName.equals(str(row.get("resource_id")))) continue;
            if (!truthy(row.get("enabled"))) continue;
            Object policyUser = row.get("user_id");
            if (policyUser != null && !String.valueOf(policyUser).isEmpty()
                    && !String.valueOf(policyUser).equals(userId)) continue;
            Object policyRole = row.get("role_id");
            if (policyRole != null && !String.valueOf(policyRole).isEmpty()
                    && !roles.contains(String.valueOf(policyRole))) continue;
            out.add(rlsProjection(row));
        }
        out.sort((a, b) -> Integer.compare(intOf(a.get("priority")), intOf(b.get("priority"))));
        return out;
    }

    /** CLS 命中集：同上，语义源为 {@code ColumnLevelSecurityDao#findPoliciesForUser}。 */
    public List<Map<String, Object>> selectClsPolicies(String tableName, String userId) {
        Set<String> roles = rolesFor(userId);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> row : clsRows) {
            if (!tableName.equals(str(row.get("table_name")))
                    && !tableName.equals(str(row.get("resource_id")))) continue;
            if (!truthy(row.get("enabled"))) continue;
            Object policyUser = row.get("user_id");
            if (policyUser != null && !String.valueOf(policyUser).isEmpty()
                    && !String.valueOf(policyUser).equals(userId)) continue;
            Object policyRole = row.get("role_id");
            if (policyRole != null && !String.valueOf(policyRole).isEmpty()
                    && !roles.contains(String.valueOf(policyRole))) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("visible_cols", row.get("visible_cols"));
            m.put("blocked_cols", row.get("blocked_cols"));
            m.put("priority", row.get("priority"));
            m.put("policy_name", row.get("policy_name"));
            out.add(m);
        }
        out.sort((a, b) -> Integer.compare(intOf(a.get("priority")), intOf(b.get("priority"))));
        return out;
    }

    /** 全部已解析 RLS 行（供「角色归属覆盖 seed 用到的每个 role_id」这类交叉断言）。 */
    public List<Map<String, Object>> allRlsRows() {
        return rlsRows;
    }

    /** seed 中出现的 role_id 集合（断言演示角色归属表确实覆盖 seed 用到的角色，防漏配导致用例失真）。 */
    public Set<String> seedRoleIds() {
        Set<String> roles = new LinkedHashSet<>();
        rlsRows.forEach(r -> roles.add(str(r.get("role_id"))));
        clsRows.forEach(r -> roles.add(str(r.get("role_id"))));
        roles.remove(null);
        roles.remove("");
        return roles;
    }

    private static Map<String, Object> rlsProjection(Map<String, Object> row) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("filter_expr", row.get("filter_expr"));
        m.put("priority", row.get("priority"));
        m.put("policy_name", row.get("policy_name"));
        m.put("predicate_template", row.get("predicate_template"));
        m.put("bindings_json", row.get("bindings_json"));
        m.put("source_kind", row.get("source_kind"));
        return m;
    }

    // ── V246 词法解析（切分口径与 SecuritySeedConsistencyTest 一致：顶层括号 + 字符串内逗号不切）──

    private static List<Map<String, Object>> parseInserts(String sql, String prefix, List<String> cols) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int s = 0; (s = sql.indexOf(prefix, s)) >= 0; ) {
            int v = sql.indexOf("VALUES", s);
            if (v < 0) break;
            int open = sql.indexOf('(', v);
            if (open < 0) break;
            int close = matchingParen(sql, open);
            if (close < 0) break;
            List<String> vals = topLevelSplit(sql.substring(open + 1, close));
            if (vals.size() >= cols.size()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 0; i < cols.size(); i++) row.put(cols.get(i), vals.get(i));
                rows.add(row);
            }
            s = close + 1;
        }
        return rows;
    }

    private static int matchingParen(String sql, int start) {
        int depth = 0;
        boolean inStr = false;
        for (int i = start; i < sql.length(); i++) {
            char c = sql.charAt(i);
            if (inStr) {
                if (c == '\'') {
                    if (i + 1 < sql.length() && sql.charAt(i + 1) == '\'') i++;
                    else inStr = false;
                }
                continue;
            }
            if (c == '\'') inStr = true;
            else if (c == '(') depth++;
            else if (c == ')') { depth--; if (depth == 0) return i; }
        }
        return -1;
    }

    private static List<String> topLevelSplit(String inner) {
        List<String> out = new ArrayList<>();
        int depth = 0;
        boolean inStr = false;
        StringBuilder buf = new StringBuilder();
        for (int i = 0; i < inner.length(); i++) {
            char c = inner.charAt(i);
            if (inStr) {
                buf.append(c);
                if (c == '\'') {
                    if (i + 1 < inner.length() && inner.charAt(i + 1) == '\'') { buf.append('\''); i++; }
                    else inStr = false;
                }
                continue;
            }
            if (c == '\'') { inStr = true; buf.append(c); continue; }
            if (c == '(') depth++;
            else if (c == ')') depth--;
            if (c == ',' && depth == 0) { out.add(peel(buf.toString().trim())); buf.setLength(0); continue; }
            buf.append(c);
        }
        if (buf.length() > 0) out.add(peel(buf.toString().trim()));
        return out;
    }

    private static String peel(String s) {
        if (s.length() >= 2 && s.startsWith("'") && s.endsWith("'")) {
            return s.substring(1, s.length() - 1).replace("''", "'");
        }
        return "NULL".equalsIgnoreCase(s) ? null : s;
    }

    /** bindings_json → 结构化列表（用例断言占位符↔绑定名一致时用，禁在测试里重抄 JSON）。 */
    public static List<Map<String, String>> parseBindings(String bindingsJson) {
        List<Map<String, String>> out = new ArrayList<>();
        if (bindingsJson == null) return out;
        Matcher m = BINDING.matcher(bindingsJson);
        while (m.find()) {
            Map<String, String> b = new LinkedHashMap<>();
            b.put("name", m.group(1));
            b.put("source", m.group(2));
            b.put("value", m.group(3) == null ? null : m.group(3).trim());
            out.add(b);
        }
        return out;
    }

    /** JSON 数组文本 → 列名列表（CLS visible_cols/blocked_cols 断言用，走 Jackson 而非手切）。 */
    public static List<String> parseColumnArray(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return MAPPER.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            throw new IllegalStateException("V246 列数组解析失败: " + json, e);
        }
    }

    private static Path resolveSeedFile() throws IOException {
        Path cur = Paths.get("").toAbsolutePath();
        for (int i = 0; i < 8 && cur != null; i++) {
            Path canonical = cur.resolve(
                    "docs/50-db_script/02-flyway-migration/gateway/V246__security_policy_seed.sql");
            if (Files.isRegularFile(canonical)) return canonical;
            cur = cur.getParent();
        }
        throw new IOException("未找到 V246__security_policy_seed.sql（§4.7.2 用例集的策略单源）");
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static boolean truthy(Object o) {
        if (o == null) return false;
        String s = String.valueOf(o).trim();
        return "true".equalsIgnoreCase(s) || "1".equals(s) || "t".equalsIgnoreCase(s);
    }

    private static int intOf(Object o) {
        try {
            return Integer.parseInt(String.valueOf(o).trim());
        } catch (Exception e) {
            return 0;
        }
    }
}
