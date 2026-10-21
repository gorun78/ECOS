package com.chinacreator.gzcm.common.security.sql;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 物理查表腿的<b>只读单表前置判据</b>（#142/B4-3e-16，收口 <b>D-B4-49</b> 的任意 SQL 子项）。
 *
 * <p><b>为什么必须有</b>：agent 工具 {@code QueryPhysicalTable} 把 LLM 给的 {@code sql}
 * 整串原样下推（旧形态 {@code jdbcTemplate.queryForList(sql + " LIMIT " + maxRows)}），
 * 于是「多语句 {@code ;}」「注释截断」「DML/DDL」全都能进数据库连接，而这一腿既不经
 * security-engine 裁决、也不受属主引擎写通道约束（铁律 §2.4 与约束 3／5）。
 * 判据放在<em>调用方发出请求之前</em>，是因为越境的 SQL 一旦到达属主端点，能不能被拦住
 * 取决于策略数据而不是取决于契约（现网 dev 的策略表零行，见 D-B4-19）。</p>
 *
 * <p><b>fail-closed 取向</b>：本判据<b>不做</b>「尽力解析」，凡解析不出一个明确的单表只读
 * 查询就拒绝。因此 CTE（{@code WITH}）、子查询、{@code JOIN}、{@code UNION}、多语句、
 * 带注释、{@code SELECT ... INTO}、{@code FOR UPDATE} 一律拒绝——这是有意收窄而不是漏判：
 * 工具面服务的是「查一张物理表」，跨表拼装属于本体／管道腿的职责。</p>
 *
 * <p><b>已知粗糙处（如实登记）</b>：关键字与形态扫描按整词在<b>未剥离字符串字面量</b>的
 * 文本上进行，故 {@code WHERE remark = 'please delete this'} 会被误拒。误拒属 fail-closed
 * 的可接受代价，误放不可接受，本批不做词法解析器（全仓无 JSqlParser 依赖）。
 * 数据库侧函数另走一条<b>前缀</b>判据（{@link #DANGEROUS_FUNCTION_PREFIXES}）：
 * {@code pg_sleep}／{@code dblink_connect} 与列名 {@code call_id} 在正则里同形，
 * 只按词边界扫会两头都错（前者漏判、后者误判），故把函数命名空间单独按前缀拒绝。</p>
 *
 * <p><b>判据双源在册（D-B4-60）</b>：ai-engine {@code AgentToolPolicyGate} 第 176-229 行已有
 * 一整套只读判据（含表／列白名单），本类是第二份实现面。两者口径一致、载体不同：前者绑
 * ai-engine 的白名单装载与工具语义，后者只回答「单表只读吗？表名是什么？」。合流到本单源
 * 属后续批次（并轨会牵动 ai-engine 白名单装配），本批只登记不并轨。</p>
 */
public final class PhysicalQueryGuard {

    /** 拒绝理由前缀（机读，供调用方与门禁按前缀识别拒绝来源）。 */
    public static final String PREFIX = "PHYSICAL_QUERY_REJECTED:";

    /** 工具面单次取数行数上限（调用方给的 {@code maxRows} 只会向下收，不会放大）。 */
    public static final int MAX_ROWS_CAP = 5000;

    /** 缺省取数行数（与工具收口前的缺省值同形，避免外形变更）。 */
    public static final int DEFAULT_MAX_ROWS = 1000;

    /** 允许的唯一语句形态：{@code SELECT} 起首（CTE 的 {@code WITH} 明确不在服务面内）。 */
    private static final Pattern READ_ONLY_HEAD = Pattern.compile("^select\\s");

    /** 物理表名提取：{@code FROM} 后的限定名（schema.table 或 table）。 */
    private static final Pattern FROM_TABLE = Pattern.compile(
            "\\bfrom\\s+([a-z_][a-z0-9_$]*(\\.[a-z_][a-z0-9_$]*)?)", Pattern.CASE_INSENSITIVE);

    /**
     * 整词拒绝表：命中任一即判为「非只读单表查询」。
     *
     * <p>按词边界匹配，故 {@code created_at}／{@code call_id} 这类含子串的列名不会被误杀；
     * 反之 {@code into}／{@code lock}／{@code set} 这类"看起来无害"的词必须拒——
     * {@code SELECT ... INTO} 是写，{@code ... FOR UPDATE} 是锁。</p>
     */
    private static final Set<String> FORBIDDEN_WORDS = Set.of(
            "insert", "update", "delete", "drop", "alter", "truncate", "create", "rename",
            "grant", "revoke", "merge", "upsert", "call", "exec", "execute", "copy",
            "lock", "into", "set", "do", "vacuum", "analyze", "refresh", "comment");

    /** 显式跨表形态（单列一条，堵住「一条 FROM + N 个 JOIN」这种只有一处 FROM 的写法）。 */
    private static final Set<String> MULTI_TABLE_WORDS = Set.of(
            "join", "union", "intersect", "except");

    /**
     * {@code FROM a, b} 隐式笛卡尔积形态：{@code JOIN} 词表抓不到它，而 {@code FROM} 提取又只会
     * 吃到第一张表 —— 不单独钉就会出现「判成单表、实际两张表」的<b>误放</b>（列清单里的逗号
     * 是合法形态，故只在 {@code FROM} 之后的表名位上判逗号）。
     */
    private static final Pattern FROM_COMMA_LIST = Pattern.compile(
            "\\bfrom\\s+[a-z_][a-z0-9_$]*(\\.[a-z_][a-z0-9_$]*)?\\s*,", Pattern.CASE_INSENSITIVE);

    /**
     * 数据库侧危险函数<b>前缀</b>（按子串命中，不带右词边界）。
     *
     * <p>为什么单独一条腿：{@code pg_sleep}／{@code dblink_connect}／{@code lo_export} 这类
     * 标识符内部含 {@code _}，与列名 {@code call_id}／{@code created_at} 在正则里是同一种形状，
     * 按词边界扫会「{@code dblink_connect} 漏判、{@code call_id} 误判」两头都错。前缀扫描把这
     * 两类分开：函数命名空间（{@code pg_*}／{@code dblink_*}／{@code lo_*}／{@code xp_*}）
     * 整体拒绝，列名一律不受影响。</p>
     */
    private static final Set<String> DANGEROUS_FUNCTION_PREFIXES = Set.of(
            "pg_", "dblink_", "lo_import", "lo_export", "lo_get", "lo_put", "xp_");

    private PhysicalQueryGuard() {
    }

    /**
     * 判据结论。
     *
     * @param allowed   是否为「单表只读查询」
     * @param tableName 放行时必填的物理表名（小写，属主端点据此定位行级安全策略）；拒绝时为 {@code null}
     * @param reason    拒绝理由（带 {@link #PREFIX}），放行时为 {@code null}
     */
    public record Decision(boolean allowed, String tableName, String reason) {

        private static Decision reject(String reason) {
            return new Decision(false, null, PREFIX + reason);
        }

        private static Decision allow(String table) {
            return new Decision(true, table, null);
        }
    }

    /**
     * 判定一条 SQL 能否作为「单表只读物理查表」下发。
     *
     * @param sql 待判定 SQL（{@code null}／空白即拒绝）
     * @return 结论，见 {@link Decision}
     */
    public static Decision inspect(String sql) {
        if (sql == null || sql.isBlank()) {
            return Decision.reject("SQL 为空");
        }
        String body = sql.strip();
        if (body.endsWith(";")) {
            body = body.substring(0, body.length() - 1).strip();
        }
        if (body.indexOf(';') >= 0) {
            return Decision.reject("含多语句分隔符（禁一次提交多条语句）");
        }
        if (body.contains("--") || body.contains("/*") || body.contains("*/") || body.contains("#")) {
            return Decision.reject("含注释形态（注释是语句截断的常用绕行面）");
        }
        String lower = body.toLowerCase(Locale.ROOT);
        if (!READ_ONLY_HEAD.matcher(lower).find()) {
            return Decision.reject("非只读查询（语句必须 SELECT 起首）");
        }
        if (countWords(lower, "select") > 1) {
            // 子查询／CTE 体里的 SELECT 会让「提取到的表名」与实际读到的表脱钩，宁可误拒
            return Decision.reject("含嵌套 SELECT（子查询形态，无法定位唯一物理表）");
        }
        for (String word : FORBIDDEN_WORDS) {
            if (containsWord(lower, word)) {
                return Decision.reject("含非只读关键字 " + word);
            }
        }
        for (String prefix : DANGEROUS_FUNCTION_PREFIXES) {
            if (lower.contains(prefix)) {
                return Decision.reject("含数据库侧危险函数命名空间 " + prefix + "*");
            }
        }
        for (String word : MULTI_TABLE_WORDS) {
            if (containsWord(lower, word)) {
                return Decision.reject("含跨表形态 " + word + "（本工具面只支持单表查询）");
            }
        }
        if (FROM_COMMA_LIST.matcher(body).find()) {
            return Decision.reject("含 FROM 逗号多表形态（隐式笛卡尔积，本工具面只支持单表查询）");
        }
        Set<String> tables = new LinkedHashSet<>();
        Matcher m = FROM_TABLE.matcher(body);
        while (m.find()) {
            tables.add(m.group(1).toLowerCase(Locale.ROOT));
        }
        if (tables.isEmpty()) {
            return Decision.reject("未能从 FROM 提取到物理表名（属主端点需要表名定位行级安全策略）");
        }
        if (tables.size() > 1) {
            return Decision.reject("涉及多张物理表 " + tables + "（本工具面只支持单表查询）");
        }
        return Decision.allow(tables.iterator().next());
    }

    /**
     * 把调用方给的行数收进 {@code [1, }{@link #MAX_ROWS_CAP}{@code ]}。
     *
     * <p>非法／缺省值回落到 {@link #DEFAULT_MAX_ROWS}；超上限<b>向下收</b>而非拒绝，
     * 因为"要得太多"属可归一的外形，不构成越权。</p>
     */
    public static int clampMaxRows(Object raw) {
        if (raw instanceof Number n && n.intValue() > 0) {
            return Math.min(n.intValue(), MAX_ROWS_CAP);
        }
        return DEFAULT_MAX_ROWS;
    }

    private static boolean containsWord(String text, String word) {
        return Pattern.compile("\\b" + Pattern.quote(word) + "\\b").matcher(text).find();
    }

    private static int countWords(String text, String word) {
        Matcher m = Pattern.compile("\\b" + Pattern.quote(word) + "\\b").matcher(text);
        int n = 0;
        while (m.find()) {
            n++;
        }
        return n;
    }
}
