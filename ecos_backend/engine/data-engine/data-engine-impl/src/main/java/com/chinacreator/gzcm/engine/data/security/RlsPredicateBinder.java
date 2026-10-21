package com.chinacreator.gzcm.engine.data.security;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * RLS 执行契约片段 → data-engine 命名参数形态的适配器（#142/B4-3d-5，差距 D-B4-24）。
 *
 * <p><b>为什么需要这一层</b>：security-engine 的 {@code /rls/apply} 执行契约交付的是
 * JDBC 位置形态（{@code whereClause} 里的 {@code ?} + 同序 {@code whereParams}），
 * 而 data-engine 的受保护查询通道（{@code QueryExecutionServiceImpl#bindNamedParameters}
 * → {@code JdbcConnector#executeQueryGuarded}）只接「带 {@code :name} 的模板 SQL +
 * 名→值表」，由那条已审计的链路按<b>模板出现序</b>把 {@code :name} 改写为 {@code ?}
 * 并收集绑定值（详细设计-01 W31，失败判据「字面量入 SQL」）。
 * 把 RLS 谓词直接以 {@code ?} 拼进 SQL 会让位置参数落到那条链路之外——同一语句里
 * 用户自己的 {@code :name} 与 RLS 的 {@code ?} 混排时，绑定序无法由 {@code params} 单表表达。</p>
 *
 * <p><b>适配方式</b>：在<b>不改动</b>既有 {@code :name} 绑定链路的前提下，把片段里的
 * {@code ?} 逐个改写为合成分段名 {@code rlsP0}/{@code rlsP1}…，并把值以同名并入参数表。
 * 位置形态与命名形态因此一一等价，值仍然只走 {@code PreparedStatement} 通道，
 * <b>没有任何值进入 SQL 文本</b>（IR05）。</p>
 *
 * <p><b>三条不变量</b>（任一不过即 {@link Bound#ok()} 为假并带理由，禁返回「看起来能用」的模板）：</p>
 * <ol>
 *   <li>{@code ?} 个数必须与参数个数相等（契约侧已校验过，此处再校验一次防调用序错位）；</li>
 *   <li>字符串字面量（{@code '...'}，含 {@code ''} 转义）与双引号标识符内的 {@code ?}
 *       <b>不是</b>占位符，必须原样保留——否则会吃掉一个参数位并把语义错位绑到别的列；</li>
 *   <li>合成分段名不得与已有参数名冲突（冲突即拒绝，禁覆盖用户参数值——那是越权面）。</li>
 * </ol>
 */
public final class RlsPredicateBinder {

    /** 合成分段名前缀（与用户 {@code :name} 同通道参与绑定，值不进 SQL 文本）。 */
    public static final String PARAM_NAME_PREFIX = "rlsP";

    /** {@code ?} 数与参数数不符。 */
    public static final String REASON_ARITY = "RLS_BIND_ARITY_MISMATCH";

    /** 合成分段名与调用方已传参数名冲突（禁覆盖用户参数）。 */
    public static final String REASON_NAME_COLLISION = "RLS_BIND_NAME_COLLISION";

    /** 片段为空（恒真/拒绝态不该走到本适配器）。 */
    public static final String REASON_CLAUSE_ABSENT = "RLS_BIND_CLAUSE_ABSENT";

    private RlsPredicateBinder() {
    }

    /**
     * 适配结果。
     *
     * @param ok         三段不变量是否全过
     * @param template   改写后的 {@code :name} 形态片段（{@code ok=false} 时为 {@code null}）
     * @param namedParams 合成分段名 → 绑定值（{@code ok=false} 时为空表）
     * @param reason     失败理由（{@code ok=true} 时为 {@code null}）
     */
    public record Bound(boolean ok, String template, Map<String, Object> namedParams, String reason) {

        public static Bound fail(String reason) {
            return new Bound(false, null, Map.of(), reason);
        }

        public static Bound of(String template, Map<String, Object> namedParams) {
            // 不用 Map.copyOf：其对 null 值直接抛 NPE（ImmutableCollections 契约）。
            // 参数值在契约层已判过 null，但适配层禁把自己变成新的崩溃源（本仓口径：
            // 归一为可解释的拒绝态，而不是 500）。
            return new Bound(true, template, java.util.Collections.unmodifiableMap(
                    new LinkedHashMap<>(namedParams)), null);
        }
    }

    /**
     * 把带 {@code ?} 的片段改写为带 {@code :rlsPn} 的模板 + 名→值表。
     *
     * @param clause        生产者交付的 {@code whereClause}（含 {@code ?}）
     * @param values        与 {@code ?} 同序的 {@code whereParams}
     * @param existingNames 调用方已有参数名集（用于冲突检测，可为 {@code null}）
     */
    public static Bound toNamedForm(String clause, List<Object> values, Map<String, Object> existingNames) {
        return toNamedForm(clause, values, existingNames, 0);
    }

    /**
     * 多作用域形态（#142/B4-3e-19，D-B4-63）：同一请求声明了多张表时，逐表绑定必须
     * 从不同槽位起编，否则第二张表的 {@code rlsP0} 会撞上第一张表已生成的名字——
     * 那不是「冲突即拒绝」要防的越权覆盖，而是两个合法作用域互相挤掉。
     *
     * @param slotOffset 已占用合成槽位数（本批从累计绑定数起编）
     */
    public static Bound toNamedForm(String clause, List<Object> values, Map<String, Object> existingNames,
                                    int slotOffset) {
        if (clause == null || clause.isBlank()) {
            return Bound.fail(REASON_CLAUSE_ABSENT);
        }
        List<Object> args = values == null ? List.of() : values;
        long marks = countPlaceholders(clause);
        if (marks != args.size()) {
            return Bound.fail(REASON_ARITY + "(? 数=" + marks + ", 参数数=" + args.size() + ")");
        }
        Map<String, Object> named = new LinkedHashMap<>();
        StringBuilder out = new StringBuilder(clause.length() + 16);
        int i = 0;
        int n = clause.length();
        int slot = 0;
        while (i < n) {
            char c = clause.charAt(i);
            if (c == '\'' || c == '"') {
                char quote = c;
                out.append(c);
                i++;
                while (i < n) {
                    char d = clause.charAt(i);
                    out.append(d);
                    i++;
                    if (d == quote) {
                        if (i < n && clause.charAt(i) == quote) { // '' / "" 转义，仍在字面量内
                            out.append(clause.charAt(i));
                            i++;
                        } else {
                            break;
                        }
                    }
                }
                continue;
            }
            if (c == '?') {
                String key = PARAM_NAME_PREFIX + (slotOffset + slot);
                if ((existingNames != null && existingNames.containsKey(key)) || named.containsKey(key)) {
                    return Bound.fail(REASON_NAME_COLLISION + "(" + key + ")");
                }
                out.append(':').append(key);
                named.put(key, args.get(slot));
                slot++;
                i++;
                continue;
            }
            out.append(c);
            i++;
        }
        return Bound.of(out.toString(), named);
    }

    /**
     * 占位符计数（跳过字符串字面量与双引号标识符内的 {@code ?}）。
     *
     * <p>与 {@link #toNamedForm} 用同一套字符态机，避免「计数按全文、改写跳过字面量」
     * 这种两套口径导致的错位绑定。</p>
     */
    public static long countPlaceholders(String clause) {
        if (clause == null) return 0;
        long count = 0;
        int i = 0;
        int n = clause.length();
        while (i < n) {
            char c = clause.charAt(i);
            if (c == '\'' || c == '"') {
                char quote = c;
                i++;
                while (i < n) {
                    char d = clause.charAt(i);
                    i++;
                    if (d == quote) {
                        if (i < n && clause.charAt(i) == quote) {
                            i++;
                        } else {
                            break;
                        }
                    }
                }
                continue;
            }
            if (c == '?') count++;
            i++;
        }
        return count;
    }
}
