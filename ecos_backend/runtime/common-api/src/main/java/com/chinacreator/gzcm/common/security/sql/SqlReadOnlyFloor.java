package com.chinacreator.gzcm.common.security.sql;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SQL <b>只读本地地板</b>的单源判据（#142/B4-3e-20，收口 <b>D-B4-60</b>「只读判据双源」）。
 *
 * <p><b>为什么是单源而不是二选一</b>：合流前仓里有两套各自成形的只读判据——
 * ①本包 {@link PhysicalQueryGuard}（属主查表腿，只回答「单表只读吗」）；
 * ②ai-engine {@code AgentToolPolicyGate.sqlLocalFloor}（工具裁决前的本地地板，
 * 允许 {@code WITH} 与多表，另接表／列白名单）。台账原登记的口径是「两者一致、只是载体不同」，
 * <b>实测不成立</b>：危险关键字集两侧各有一批对方没有的词（{@code upsert}／{@code rename}／
 * {@code comment} 只在 ① 有，{@code begin}／{@code commit}／{@code rollback}／{@code savepoint}/
 * {@code prepare}／{@code deallocate}／{@code reset}／{@code shutdown}／{@code reindex}／
 * {@code cluster}／{@code listen}／{@code notify}／{@code checkpoint} 只在 ② 有——逐词实测，
 * 而 {@code do}／{@code into}／{@code set}／{@code lock} 两面都有），字面量掩码也只有 ② 做（① 的注释里已自认
 * {@code WHERE remark = 'please delete this'} 会误拒）。两套判据各自演进，
 * 漂移方向不可控：任何一侧漏词，另一侧的放行面就是那条 SQL 的实际通路。</p>
 *
 * <p><b>修法裁决</b>：把<em>公共部分</em>（空语句、多语句、注释、语句头部、危险关键字、
 * 危险函数、字面量掩码、表引用提取）单源化到本类，<em>面专属的附加约束</em>留在各调用方——
 * 「必须单表」属查表面、「表／列白名单」属工具面，都不进公共地板。两侧的差异由此
 * 从「两套判据」降为「一个参数」：{@code allowWith}。</p>
 *
 * <p><b>合流带来的双向变化（如实登记，不冒充无损）</b>：
 * ①两面关键字面统一为<b>并集</b> ⇒ 两侧都只变严不变宽（fail-closed 方向）；
 * ②危险函数判据统一为<b>调用位前缀</b>（{@code 前缀 + 标识符 + 可选空白 + (}）——
 * 旧 ① 用 {@code contains("pg_")} 会把 {@code FROM pg_class} 这类表名一起拒掉，
 * 旧 ② 用词边界具名清单会漏掉清单外的 {@code pg_*} 函数；带括号的调用位判据同时修掉这两头
 * （函数调用不带括号不成立，故不构成新的放行面）；
 * ③字面量掩码统一后，查表面的「字符串字面量里的关键字误拒」一并消除。</p>
 *
 * <p><b>显式未执行项</b>：本类仍是正则形态的词法地板，不是 SQL 解析器（全仓无 JSqlParser 依赖）；
 * 标识符内注释（{@code SELECT /*+ hint *\/}）与 dollar-quoted 字符串（PG 的 {@code $$...$$}）
 * 不在覆盖内。凭证等级＝单元级，非 live（dev 策略表零行的前置见 D-B4-19）。</p>
 */
public final class SqlReadOnlyFloor {

    /** 允许的唯一语句形态：{@code SELECT} 起首。 */
    private static final Pattern SELECT_HEAD = Pattern.compile("^select\\s");

    /** {@code WITH} 起首（CTE）：只有显式声明支持的工具面放行。 */
    private static final Pattern WITH_HEAD = Pattern.compile("^with\\b");

    /** 注释形态：{@code --}／块注释／{@code #}，都是语句截断的常用绕行面。 */
    private static final Pattern COMMENT_FORMS = Pattern.compile("--|/\\*|\\*/|#");

    /** 字符串字面量（含 {@code ''} 转义）：扫描前先掩掉，避免字面量里的词被当关键字。 */
    private static final Pattern STRING_LITERAL = Pattern.compile("'(?:''|[^'])*'");

    /**
     * 整词拒绝表（＝合流前两面判据的并集）。
     *
     * <p>按词边界匹配，故 {@code created_at}／{@code call_id}／{@code column_comment}
     * 这类含子串的列名不会被误杀。{@code into}（{@code SELECT ... INTO} 是写）、
     * {@code lock}（{@code FOR UPDATE}）、{@code set} 这类"看起来无害"的词必须在表内。</p>
     */
    private static final Set<String> FORBIDDEN_WORDS = Set.of(
            "insert", "update", "delete", "drop", "alter", "truncate", "create", "rename",
            "grant", "revoke", "merge", "upsert", "call", "exec", "execute", "copy",
            "lock", "into", "set", "do", "vacuum", "analyze", "refresh", "comment",
            "begin", "commit", "rollback", "savepoint", "prepare", "deallocate",
            "reset", "shutdown", "reindex", "cluster", "listen", "notify", "checkpoint");

    /**
     * 数据库侧危险函数的命名空间前缀（只在<b>调用位</b>上判）。
     *
     * <p>{@code pg_sleep}／{@code dblink_connect} 与列名 {@code call_id} 在正则里同形，
     * 按词边界扫会「前者漏判、后者误判」；而裸 {@code contains("pg_")} 又会把
     * {@code FROM pg_class} 这类表名一起误拒。故取中间形态：前缀 + 标识符 + 可选空白 + 左括号。</p>
     */
    private static final Set<String> DANGEROUS_FUNCTION_PREFIXES = Set.of(
            "pg_", "dblink", "lo_", "xp_");

    /** 表引用提取：{@code FROM} 或 {@code JOIN} 后的限定名（schema.table 或 table）。 */
    private static final Pattern TABLE_REF = Pattern.compile(
            "\\b(?:from|join)\\s+([a-z_][a-z0-9_$]*(?:\\.[a-z_][a-z0-9_$]*)?)",
            Pattern.CASE_INSENSITIVE);

    private SqlReadOnlyFloor() {
    }

    /**
     * 地板结论。
     *
     * @param allowed    是否只读形态
     * @param normalized 归一化后的语句文本（尾分号已剥、小写、字面量已掩），供调用方做面专属判定
     * @param tables     提取到的表引用（小写、保序去重）；地板拒绝时为 {@code null}
     * @param reason     拒绝理由（<b>不带</b>调用方前缀——前缀属各面的机读约定，由调用方自持）
     */
    public record Decision(boolean allowed, String normalized, Set<String> tables, String reason) {

        private static Decision reject(String reason) {
            return new Decision(false, null, null, reason);
        }

        private static Decision allow(String normalized, Set<String> tables) {
            return new Decision(true, normalized, tables, null);
        }
    }

    /**
     * 判定 SQL 是否落在只读地板上。
     *
     * @param sql       待判定 SQL（{@code null}／空白即拒绝）
     * @param allowWith 是否允许 {@code WITH}（CTE）起首——属主查表面传 {@code false}，
     *                  工具裁决面按其既有语义传 {@code true}
     * @return 结论，见 {@link Decision}
     */
    public static Decision inspect(String sql, boolean allowWith) {
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
        if (COMMENT_FORMS.matcher(body).find()) {
            return Decision.reject("含注释形态（注释是语句截断的常用绕行面）");
        }
        String masked = maskLiterals(body.toLowerCase(Locale.ROOT));
        if (!SELECT_HEAD.matcher(masked).find()
                && !(allowWith && WITH_HEAD.matcher(masked).find())) {
            return Decision.reject(allowWith
                    ? "非只读查询（语句必须 SELECT / WITH 起首）"
                    : "非只读查询（语句必须 SELECT 起首）");
        }
        for (String word : FORBIDDEN_WORDS) {
            if (containsWord(masked, word)) {
                return Decision.reject("含非只读关键字 " + word);
            }
        }
        for (String prefix : DANGEROUS_FUNCTION_PREFIXES) {
            Matcher m = callSite(prefix).matcher(masked);
            if (m.find()) {
                return Decision.reject("含数据库侧危险函数命名空间 " + m.group());
            }
        }
        return Decision.allow(masked, extractTables(body));
    }

    /**
     * 提取表引用：先掩掉字符串字面量，再扫 {@code FROM}／{@code JOIN}，返回小写保序去重集合。
     *
     * <p>解析失败按空集合返回——两面都把空集合当作「无法定位受控表」来拒绝（fail-closed），
     * 本类不猜表名。</p>
     */
    public static Set<String> extractTables(String sql) {
        Set<String> tables = new LinkedHashSet<>();
        if (sql == null) {
            return tables;
        }
        Matcher m = TABLE_REF.matcher(maskLiterals(sql.toLowerCase(Locale.ROOT)));
        while (m.find()) {
            tables.add(m.group(1));
        }
        return tables;
    }

    /** 掩掉字符串字面量（保留引号外壳，使词边界仍成立）。 */
    public static String maskLiterals(String sql) {
        return STRING_LITERAL.matcher(sql).replaceAll("' '");
    }

    private static Pattern callSite(String prefix) {
        return Pattern.compile("\\b" + Pattern.quote(prefix) + "[a-z0-9_$]*\\s*\\(");
    }

    private static boolean containsWord(String text, String word) {
        return Pattern.compile("\\b" + Pattern.quote(word) + "\\b").matcher(text).find();
    }
}
