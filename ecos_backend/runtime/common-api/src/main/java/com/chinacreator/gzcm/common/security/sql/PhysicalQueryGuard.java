package com.chinacreator.gzcm.common.security.sql;

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
 * <p><b>判据单源（#142/B4-3e-20 收口 D-B4-60）</b>：只读地板的公共部分（空语句／多语句／
 * 注释／语句头部／危险关键字／危险函数／字面量掩码／表引用提取）已单源化到
 * {@link SqlReadOnlyFloor}，本类只保留<b>查表面专属</b>的附加约束：必须单表、必须能唯一定位
 * 物理表、禁 CTE。原先与本类并存的第二套判据（ai-engine {@code AgentToolPolicyGate} 的
 * {@code sqlLocalFloor}）同样改为委托该单源，两侧差异只剩参数 {@code allowWith}；
 * 合流前两面各自缺词的关键字集已取并集，故两面都只变严不变宽。
 * 拒绝理由的前缀 {@link #PREFIX} 属本面的机读约定，仍由本类自持。</p>
 *
 * <p><b>已知粗糙处（如实登记）</b>：整词与函数扫描在未剥离字符串字面量之外的文本上进行，
 * 形态之外的旁路（如 dollar-quoted {@code $$...$$}）不在覆盖内；解析不出唯一表名即拒，
 * 误拒属 fail-closed 的可接受代价，误放不可接受，本批不做词法解析器（全仓无 JSqlParser 依赖）。</p>
 */
public final class PhysicalQueryGuard {

    /** 拒绝理由前缀（机读，供调用方与门禁按前缀识别拒绝来源）。 */
    public static final String PREFIX = "PHYSICAL_QUERY_REJECTED:";

    /** 工具面单次取数行数上限（调用方给的 {@code maxRows} 只会向下收，不会放大）。 */
    public static final int MAX_ROWS_CAP = 5000;

    /** 缺省取数行数（与工具收口前的缺省值同形，避免外形变更）。 */
    public static final int DEFAULT_MAX_ROWS = 1000;

    /**
     * 显式跨表形态（单列一条，堵住「一条 FROM + N 个 JOIN」这种只有一处 FROM 的写法）。
     * 与本面的单表约束绑定，不进公共地板——工具面按表／列白名单允许多表。
     */
    private static final Set<String> MULTI_TABLE_WORDS = Set.of(
            "join", "union", "intersect", "except");

    /**
     * {@code FROM a, b} 隐式笛卡尔积形态：{@code JOIN} 词表抓不到它，而 {@code FROM} 提取又只会
     * 吃到第一张表 —— 不单独钉就会出现「判成单表、实际两张表」的<b>误放</b>（列清单里的逗号
     * 是合法形态，故只在 {@code FROM} 之后的表名位上判逗号）。
     */
    private static final Pattern FROM_COMMA_LIST = Pattern.compile(
            "\\bfrom\\s+[a-z_][a-z0-9_$]*(\\.[a-z_][a-z0-9_$]*)?\\s*,", Pattern.CASE_INSENSITIVE);

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
        SqlReadOnlyFloor.Decision floor = SqlReadOnlyFloor.inspect(sql, false);
        if (!floor.allowed()) {
            return Decision.reject(floor.reason());
        }
        String lower = floor.normalized();
        if (countWords(lower, "select") > 1) {
            // 子查询／CTE 体里的 SELECT 会让「提取到的表名」与实际读到的表脱钩，宁可误拒
            return Decision.reject("含嵌套 SELECT（子查询形态，无法定位唯一物理表）");
        }
        for (String word : MULTI_TABLE_WORDS) {
            if (containsWord(lower, word)) {
                return Decision.reject("含跨表形态 " + word + "（本工具面只支持单表查询）");
            }
        }
        if (FROM_COMMA_LIST.matcher(sql).find()) {
            return Decision.reject("含 FROM 逗号多表形态（隐式笛卡尔积，本工具面只支持单表查询）");
        }
        Set<String> tables = floor.tables();
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
