package com.chinacreator.gzcm.workspace.security;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * security-engine {@code /rls/apply} 应答中<b>执行契约</b>的 workspace 侧消费形态
 * （#142/B4-3c-2，收口差距 D-B4-17）。
 *
 * <p><b>为什么需要这个类型</b>：{@code whereClause} 字段此前在生产者应答里根本不存在，
 * 三个消费者各自 {@code data.get("whereClause") == null ? "" : …} —— 取到空串就当
 * "没有行级限制"，行过滤在页面通道上恒等于不过滤（fail-open）。生产者现已真发该字段，
 * 但发的是带 {@code ?} 的 JDBC 片段 + 同序 {@code whereParams}：<b>只取片段不取参数</b>
 * 会把未绑定的 {@code ?} 拼进 SQL（语法错误或被截断的语义），同样不可接受。
 * 因此片段与参数必须<b>成对流动</b>，本 record 就是这个"对"的唯一载体。</p>
 *
 * <p><b>构造期不变量（{@link #fromData}）</b> —— 任一条不满足即转拒绝形态
 * {@link #DENY_CLAUSE} 并带 {@code denyReason}，禁降级成"不过滤"：</p>
 * <ol>
 *   <li>{@code whereClause} 缺失/空白 = 应答形态漂移 ⇒ 拒绝（D-B4-17 的原始缺陷形态）；</li>
 *   <li>{@code ?} 个数 ≠ {@code whereParams} 个数 ⇒ 拒绝（IR05：参数化片段必须可完整绑定）；</li>
 *   <li>参数表含 {@code null} ⇒ 拒绝（上下文未解析值不得当成绑定值下发，D-B4-23）；</li>
 *   <li>片段仍含 {@code :name} 占位符 ⇒ 拒绝（生产者未完成渲染，禁把占位符拼进 SQL）。</li>
 * </ol>
 *
 * <p><b>三态分明</b>（详设-01 §4.7.2 要求，禁折叠）：
 * {@link #allowAll()}＝{@code 1=1}（裁决明确"不限制行"，如 {@code rls-finance-all}）；
 * {@link #denied()}＝{@code 1=0} 或本类不变量拒绝；
 * 其余＝非平凡谓词（须由数据载体绑参下推，无法下推的端点自行 fail-closed）。
 * {@code security 进程不可用}不在本类形态内 —— 由
 * {@link WorkspaceSecurityEngineClient} 单点转 503 {@code ECOS-SEC-501} 上抛。</p>
 */
public record RlsFilter(String clause,
                        List<Object> params,
                        boolean denyIfEmpty,
                        boolean noPolicyMatched,
                        List<String> unresolvedBindings,
                        String denyReason) {

    /** 拒绝片段：拼进 WHERE 即零行（与 security-engine {@code RlsPredicateRenderer.DENY_CLAUSE} 同值）。 */
    public static final String DENY_CLAUSE = "1=0";

    /** 恒真片段：裁决明确"不限制行"（与 {@code ALLOW_ALL_CLAUSE} 同值），与非平凡谓词、与拒绝皆不同态。 */
    public static final String ALLOW_ALL_CLAUSE = "1=1";

    /** 应答缺 {@code whereClause} 时的拒绝原因（钉住 D-B4-17 的原始缺陷形态，禁再退回收空串）。 */
    public static final String DRIFT_ABSENT = "RLS_WHERE_CLAUSE_ABSENT(应答未发执行契约，禁当成「无过滤」)";

    /**
     * 从 {@code /rls/apply} 的 {@code data} 段构造并做不变量校验。
     *
     * @param data       REST 应答 {@code ApiResponse.data}（{@code null} 由调用方按漂移处理）
     * @param entityCode 仅用于日志/原因串定位
     */
    public static RlsFilter fromData(Map<String, Object> data, String entityCode) {
        if (data == null) {
            return deny(DRIFT_ABSENT + " entity=" + entityCode, true, List.of());
        }
        boolean denyIfEmpty = bool(data.get("denyIfEmpty"), true);
        List<String> unresolved = strings(data.get("unresolvedBindings"));
        Object whereObj = data.get("whereClause");
        if (whereObj == null || String.valueOf(whereObj).isBlank()) {
            // 生产者未发/发空串：这正是 D-B4-17 的 fail-open 入口，收口后一律拒绝
            return deny(DRIFT_ABSENT + " entity=" + entityCode, denyIfEmpty, unresolved);
        }
        String clause = String.valueOf(whereObj).trim();
        String reason = producerReason(data);
        List<Object> params = objects(data.get("whereParams"));

        if (DENY_CLAUSE.equals(normalize(clause))) {
            return new RlsFilter(DENY_CLAUSE, List.of(), denyIfEmpty,
                    bool(data.get("noPolicyMatched"), true), unresolved,
                    reason == null ? "RLS_DENY_FROM_SECURITY_ENGINE" : reason);
        }
        long marks = clause.chars().filter(c -> c == '?').count();
        if (marks != params.size()) {
            return deny("RLS_PARAM_ARITY_MISMATCH(entity=" + entityCode + ", 片段 ? 数=" + marks
                    + ", 参数数=" + params.size() + ")", denyIfEmpty, unresolved);
        }
        if (containsNullParam(params)) {
            return deny("RLS_NULL_PARAM(entity=" + entityCode + "，上下文未解析值禁作绑定值)",
                    denyIfEmpty, unresolved);
        }
        if (RESIDUAL_PLACEHOLDER.matcher(clause).find()) {
            return deny("RLS_UNRENDERED_PLACEHOLDER(entity=" + entityCode + "，片段仍含 :name)",
                    denyIfEmpty, unresolved);
        }
        return new RlsFilter(clause, params, denyIfEmpty,
                bool(data.get("noPolicyMatched"), false), unresolved, null);
    }

    /**
     * 恒真形态（内部调用链 / 用例直接构造用，无 REST 应答）。
     *
     * <p>命名与实例谓词 {@link #allowAll()} 区分：Java 记录里同名静态工厂与实例方法
     * 无法共存（同一签名的名字冲突），故取 {@code unrestricted()} 表明"裁决明确不限制行"。</p>
     */
    public static RlsFilter unrestricted() {
        return new RlsFilter(ALLOW_ALL_CLAUSE, List.of(), true, false, List.of(), null);
    }

    public static RlsFilter deny(String reason, boolean denyIfEmpty, List<String> unresolved) {
        return new RlsFilter(DENY_CLAUSE, List.of(), denyIfEmpty, true,
                unresolved == null ? List.of() : List.copyOf(unresolved), reason);
    }

    /** 拒绝态：带原因（含本类不变量拒绝与 security 明确 DENY）或片段本身就是 {@code 1=0}。 */
    public boolean denied() {
        return denyReason != null || DENY_CLAUSE.equals(normalize(clause));
    }

    /** 恒真＝不限制行；与「非平凡谓词」和「拒绝」三态互斥。 */
    public boolean allowAll() {
        return !denied() && ALLOW_ALL_CLAUSE.equals(normalize(clause));
    }

    /** 是否需要绑定参数（非平凡谓词的信号，调用方据此决定能否下推）。 */
    public boolean hasParams() {
        return !params.isEmpty();
    }

    /**
     * 三态归一的"拼什么"（SQL 片段）：恒真 → 空串（不必拼，拼了也是噪声）、
     * 拒绝 → {@link #DENY_CLAUSE}、非平凡 → 原片段。
     *
     * <p>抽在这里而不是各调用方写 {@code if} 是为了让"恒真/拒绝/非平凡"的分态只有一处
     * 口径——调用方只决定<b>能不能绑参</b>，不重新解释裁决。</p>
     */
    public String clauseForSplice() {
        if (allowAll()) return "";
        return denied() ? DENY_CLAUSE : clause;
    }

    /** 三态归一的"绑什么"（与 {@link #clauseForSplice()} 里的 {@code ?} 同序）。 */
    public List<Object> paramsForSplice() {
        return (allowAll() || denied()) ? List.of() : params;
    }

    /** JDBC 变参绑定用数组（与 {@code clause} 里的 {@code ?} 同序）。 */
    public Object[] argArray() {
        return params.toArray();
    }

    /** 剥括号与空白（{@code "(1=1)"} 与 {@code "1=1"} 同态；与生产者口径一致）。 */
    public static String normalize(String predicate) {
        if (predicate == null) return "";
        StringBuilder sb = new StringBuilder(predicate.length());
        for (int i = 0; i < predicate.length(); i++) {
            char c = predicate.charAt(i);
            if (c != '(' && c != ')' && !Character.isWhitespace(c)) sb.append(c);
        }
        return sb.toString();
    }

    /** 占位符残留检测（与生产者 {@code RlsPredicateRenderer.PLACEHOLDER} 同形）。 */
    private static final java.util.regex.Pattern RESIDUAL_PLACEHOLDER =
            java.util.regex.Pattern.compile(":(\\w+)");

    private static String producerReason(Map<String, Object> data) {
        Object reason = data.get("rlsDenyReason");
        return reason == null || String.valueOf(reason).isBlank() ? null : String.valueOf(reason);
    }

    private static boolean bool(Object raw, boolean defaultValue) {
        if (raw instanceof Boolean b) return b;
        if (raw == null) return defaultValue;
        return Boolean.parseBoolean(String.valueOf(raw));
    }

    private static List<Object> objects(Object raw) {
        if (!(raw instanceof List<?> list)) return List.of();
        List<Object> out = new ArrayList<>(list.size());
        out.addAll(list);
        return Collections.unmodifiableList(out);
    }

    /**
     * 参数表的 {@code null} 扫描（不变量 3 的实现）。
     *
     * <p><b>为什么不用 {@code list.contains(null)}</b>：JDK 不可变列表（{@code List.of()} /
     * {@code List.copyOf()}）对 {@code contains(null}) 直接抛 {@code NullPointerException}
     * （{@code ImmutableCollections.indexOf} 契约）。本方法在无 {@code whereParams} 段时拿到的
     * 恰是 {@code List.of()}，于是"拒绝未解析绑定"的守卫自己先崩成 500 ——
     * fail-closed 的校验禁成为新的崩溃源（本仓口径：缺陷要归一为可解释的拒绝态）。</p>
     */
    private static boolean containsNullParam(List<Object> params) {
        for (Object p : params) {
            if (p == null) return true;
        }
        return false;
    }

    private static List<String> strings(Object raw) {
        if (!(raw instanceof List<?> list)) return List.of();
        List<String> out = new ArrayList<>(list.size());
        for (Object o : list) {
            if (o != null && !String.valueOf(o).isBlank()) out.add(String.valueOf(o));
        }
        return List.copyOf(out);
    }
}
