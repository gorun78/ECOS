package com.chinacreator.gzcm.workspace.security;

import com.chinacreator.gzcm.common.security.rls.RlsExecutionContract;

import java.util.List;
import java.util.Map;

/**
 * security-engine {@code /rls/apply} 应答中<b>执行契约</b>的 workspace 侧消费形态
 * （#142/B4-3c-2，收口差距 D-B4-17；#142/B4-3d-5 起读数归一至
 * {@link RlsExecutionContract}，差距 D-B4-24）。
 *
 * <p><b>为什么需要这个类型</b>：{@code whereClause} 字段此前在生产者应答里根本不存在，
 * 三个消费者各自 {@code data.get(...)} 取到 {@code null} 后返回空串＝"没有行级限制"，
 * 行过滤在页面通道上恒等于不过滤（fail-open）。生产者现已真发该字段，但发的是带
 * {@code ?} 的 JDBC 片段 + 同序 {@code whereParams}：<b>只取片段不取参数</b>
 * 会把未绑定的 {@code ?} 拼进 SQL（语法错误或被截断的语义），同样不可接受。
 * 因此片段与参数必须<b>成对流动</b>，本 record 就是这个"对"在页面通道的载体。</p>
 *
 * <p><b>构造期不变量</b>（#142/B4-3d-5 上收单源，本类不再自持解析逻辑）：
 * 缺片段／{@code ?} 数与参数数不符／参数含 {@code null}／占位符残留，
 * 任一条不过即 {@link RlsExecutionContract.State#DENIED}，禁降级成"不过滤"。
 * 详见 {@link RlsExecutionContract#read}。</p>
 *
 * <p><b>三态分明</b>（详设-01 §4.7.2 要求，禁折叠）：
 * {@link #allowAll()}＝{@code 1=1}（裁决明确"不限制行"，如 {@code rls-finance-all}）；
 * {@link #denied()}＝{@code 1=0} 或单源不变量拒绝；
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

    /** 拒绝片段：拼进 WHERE 即零行（单源常量，禁本模块另写字面量）。 */
    public static final String DENY_CLAUSE = RlsExecutionContract.DENY_CLAUSE;

    /** 恒真片段：裁决明确"不限制行"（单源常量）。 */
    public static final String ALLOW_ALL_CLAUSE = RlsExecutionContract.ALLOW_ALL_CLAUSE;

    /**
     * 应答缺 {@code whereClause} 时的拒绝原因（钉住 D-B4-17 的原始缺陷形态，禁再退回收空串）。
     *
     * <p>#142/B4-3d-5 起文案归 {@link RlsExecutionContract#REASON_WHERE_ABSENT} 单源，
     * 本常量只是页面上游既有引用面的别名（值同源）。</p>
     */
    public static final String DRIFT_ABSENT = RlsExecutionContract.REASON_WHERE_ABSENT;

    /**
     * 从 {@code /rls/apply} 的 {@code data} 段构造（读数与不变量在单源里做）。
     *
     * @param data       REST 应答 {@code ApiResponse.data}（{@code null} 由单源按漂移处理）
     * @param entityCode 仅用于日志/原因串定位
     */
    public static RlsFilter fromData(Map<String, Object> data, String entityCode) {
        return from(RlsExecutionContract.read(data, "entity=" + entityCode));
    }

    /**
     * 单源裁决 → 页面通道载体。
     *
     * <p>{@link RlsExecutionContract.State#UNAVAILABLE} 在页面通道由
     * {@link WorkspaceSecurityEngineClient} 单点转 503 上抛，正常不会流到这里；
     * 万一流入也按拒绝承载（禁"不可用"被读成"不限制"）。</p>
     */
    public static RlsFilter from(RlsExecutionContract.Execution exec) {
        if (exec == null) {
            return deny(RlsExecutionContract.REASON_NO_DATA_SEGMENT, true, List.of());
        }
        return switch (exec.state()) {
            case DENIED, UNAVAILABLE -> new RlsFilter(DENY_CLAUSE, List.of(), exec.denyIfEmpty(),
                    exec.noPolicyMatched(), exec.unresolvedBindings(), exec.reason());
            case ALLOW_ALL, FILTERED -> new RlsFilter(exec.clause(), exec.params(), exec.denyIfEmpty(),
                    exec.noPolicyMatched(), exec.unresolvedBindings(), null);
        };
    }

    /** 恒真形态（内部调用链 / 用例直接构造用，无 REST 应答）。 */
    public static RlsFilter unrestricted() {
        return new RlsFilter(ALLOW_ALL_CLAUSE, List.of(), true, false, List.of(), null);
    }

    public static RlsFilter deny(String reason, boolean denyIfEmpty, List<String> unresolved) {
        return new RlsFilter(DENY_CLAUSE, List.of(), denyIfEmpty, true,
                unresolved == null ? List.of() : List.copyOf(unresolved), reason);
    }

    /** 拒绝态：带原因（含单源不变量拒绝与 security 明确 DENY）或片段本身就是 {@code 1=0}。 */
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

    /** 剥括号与空白（{@code "(1=1)"} 与 {@code "1=1"} 同态；口径单源在契约类）。 */
    public static String normalize(String predicate) {
        return RlsExecutionContract.normalize(predicate);
    }
}
