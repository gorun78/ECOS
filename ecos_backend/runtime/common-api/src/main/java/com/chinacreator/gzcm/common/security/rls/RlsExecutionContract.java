package com.chinacreator.gzcm.common.security.rls;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 详设-01 C.2.2 / §4.5.4 —— security-engine {@code /rls/apply} 应答中<b>执行契约</b>的
 * 单源读取契约（#142/B4-3d-5，收口差距 D-B4-24，并承载 §4.5.4 分态口径 D-B4-26 的状态模型）。
 *
 * <p><b>缺陷本质（为什么"改对一处字面量"不算收口）</b>：{@code /rls/apply} 的应答同时携带
 * 三个面——表达面 {@code predicate}（{@code predicateTemplate} + {@code bindings}）、
 * 名值面 {@code params}、执行契约面（{@code whereClause} + {@code whereParams} +
 * {@code unresolvedBindings} + {@code noPolicyMatched} + {@code rlsDenyReason}）。
 * <b>安全语义只存在于执行契约面</b>：渲染器 {@code RlsPredicateRenderer} 在这里做
 * 「写期词法校验 / 上下文未解析即拒绝 / 参数按模板出现序展开」三件事，拒绝态归一为
 * {@code whereClause="1=0"} + 机读理由。而四个消费者各自手抄键名字面量解析应答，
 * 于是同一个键面有 <b>四套实现</b>：workspace {@code RlsFilter}（成对读取＋四道不变量）、
 * ontology/ai 客户端（只取片段、含 {@code ?} 即拒）、data-engine {@code QueryController}
 * 读的是 legacy 表达面 {@code predicate.predicateTemplate} + {@code :name}
 * （差距 D-B4-24）。last 那一套的后果不是"写法不同"而是<b>绕过渲染器的拒绝归一</b>：</p>
 * <ul>
 *   <li>{@code CONTEXT_ORG} 未解析时，表达面的 {@code params} 里是 {@code orgId=null}
 *       （{@code RlsPredicateRenderer} 对此返 {@code 1=0} + {@code RLS_CONTEXT_UNRESOLVED}），
 *       data-engine 会把 {@code department_id = ?} 绑上 {@code null} 下推——
 *       结果"恰好零行"，但语义是"未知"而非"拒绝"，且 {@code D-B4-23} 这一缺陷被
 *       伪装成"该用户没有数据"（正是 §4.5.4 禁止的折叠）；</li>
 *   <li>恒真策略（如 {@code rls-finance-all} 的 {@code 1=1}＝明确"不限制行"）在 data-engine
 *       被判成"无有效谓词"直接 403——三态里的 ALLOW_ALL 在这一通道凭空消失。</li>
 * </ul>
 *
 * <p><b>修法</b>：把"怎么读执行契约、读出来分几态"收敛成本类这一个载体，四通道只做委托。
 * 键名字面量在本仓主源码内<b>只允许</b>出现在生产者（{@code RlsPredicateRenderer}
 * 的 {@code toMap()}、{@code SecurityDecisionService} 的段装配）与本类；消费者再写一次
 * {@code "whereClause"} 就是第二套真值，由 {@code RlsExecutionContractGateTest} 静态门禁钉住。</p>
 *
 * <p><b>状态模型（§4.5.4「不可用 ≠ 无权限」的单一口径）</b>：</p>
 * <ul>
 *   <li>{@link State#ALLOW_ALL}＝裁决明确"不限制行"（{@code 1=1}），放行不过滤；</li>
 *   <li>{@link State#FILTERED}＝非平凡谓词，必须把 {@link Execution#params()} 与
 *       {@code ?} 同序绑定后才可用；无参数绑定通道的消费方必须转拒绝（{@link Execution#rejectForUnbindableChannel}）；</li>
 *   <li>{@link State#DENIED}＝拒绝（生产者 {@code 1=0}／无策略命中／本类四道不变量不过），
 *       带 {@link Execution#reason()}，对外 {@code 403}；</li>
 *   <li>{@link State#UNAVAILABLE}＝<b>security 进程不可用</b>（连不上／超时／寻址失败）。
 *       生产者应答里永不含此态，只能由消费者在传输层构造（{@link Execution#unavailable}），
 *       对外 {@code 503 ECOS-SEC-501}——与 {@code DecisionController} 的 fail-closed 出口、
 *       gateway {@code SecurityDownAllChannelDenyTest} 的既有口径同形。
 *       把它放进同一个枚举是为了让"不可用"与"无权限"在类型上不可能被写成同一个值。</li>
 * </ul>
 *
 * <p><b>不做的事</b>：本类不渲染 SQL、不拼客户端值（IR05）、不判定策略（判定在生产者）；
 * 只依赖 JDK，不得反向依赖任何引擎或 Spring（与 {@code ColumnSurfaceContract} 同纪律）。</p>
 */
public final class RlsExecutionContract {

    // ── 执行契约五个键（与 RlsPredicateRenderer.Rendered.toMap() 一一对应） ──

    /** WHERE 片段；拒绝态由生产者写成 {@link #DENY_CLAUSE}。 */
    public static final String KEY_WHERE_CLAUSE = "whereClause";

    /** 与片段里 {@code ?} 同序的参数表；恒真/拒绝态为空表。 */
    public static final String KEY_WHERE_PARAMS = "whereParams";

    /** 解析不到值的上下文绑定名（正常态为空）。 */
    public static final String KEY_UNRESOLVED_BINDINGS = "unresolvedBindings";

    /** 是否「一条策略都没命中」——与「命中但恒真」分态的唯一依据。 */
    public static final String KEY_NO_POLICY_MATCHED = "noPolicyMatched";

    /** 生产者的机读拒绝理由；键缺失＝无理由，禁消费者自造。 */
    public static final String KEY_RLS_DENY_REASON = "rlsDenyReason";

    /** 空谓词默认拒绝开关（生产者默认 true）。 */
    public static final String KEY_DENY_IF_EMPTY = "denyIfEmpty";

    // ── 表达面键（差距 D-B4-24 的分裂源头：生产者写、消费者禁据此拼 SQL） ──

    /** 表达面键：{@code predicate} 段（模板 + 绑定清单）。<b>禁止</b>据此判定执行形态。 */
    public static final String KEY_PREDICATE_SEGMENT = "predicate";

    /**
     * 表达面键：{@code predicateTemplate}（含 {@code :name} 占位符的策略原文）。
     *
     * <p>这一面<b>不含</b>渲染器的拒绝归一（未解析上下文／词法违规在该面不可见），
     * 消费者读它拼 SQL 就是 data-engine {@code QueryController} 的原始缺陷形态
     * （差距 D-B4-24）。执行读数一律走 {@link #read}。</p>
     */
    public static final String KEY_PREDICATE_TEMPLATE = "predicateTemplate";

    // ── 片段字面量（与生产者 RlsPredicateRenderer 同值，禁分叉） ──

    /** 拒绝片段：拼进 WHERE 即零行。 */
    public static final String DENY_CLAUSE = "1=0";

    /** 恒真片段＝裁决明确「不限制行」。 */
    public static final String ALLOW_ALL_CLAUSE = "1=1";

    // ── 理由词表（消费者日志与门禁的机读锚点） ──

    /**
     * 应答缺 {@code whereClause} 的<b>裸码</b>（不含说明文案）。
     *
     * <p>审计事件与对外阻止原因需要的是可机读的码字（供检索/告警规则匹配），
     * 完整理由串则带说明。此前两者在 ai {@code SecurityCheckerNode} 与 ontology
     * 拦截器里各手抄一份，单源后码只在这里定义一次。</p>
     */
    public static final String REASON_WHERE_ABSENT_CODE = "RLS_WHERE_CLAUSE_ABSENT";

    /** 应答缺 {@code whereClause}／空白——即 D-B4-17 的原始 fail-open 入口（码 + 说明）。 */
    public static final String REASON_WHERE_ABSENT =
            REASON_WHERE_ABSENT_CODE + "(应答未发执行契约，禁当成「无过滤」)";

    /** 应答 {@code data} 段整体缺失（无 data／非 Map）。 */
    public static final String REASON_NO_DATA_SEGMENT = "RLS_RESPONSE_HAS_NO_DATA_SEGMENT";

    /** 片段含 {@code ?} 但消费通道没有参数绑定通道——无参数通道侧的拒绝前缀。 */
    public static final String REASON_PARAM_UNBINDABLE = "RLS_PARAM_UNBINDABLE";

    /** {@code ?} 个数与参数个数不符（IR05：参数化片段必须可完整绑定）。 */
    public static final String REASON_PARAM_ARITY_MISMATCH = "RLS_PARAM_ARITY_MISMATCH";

    /** 参数表含 {@code null}——上下文未解析值禁作绑定值（D-B4-23）。 */
    public static final String REASON_NULL_PARAM = "RLS_NULL_PARAM";

    /** 片段仍含 {@code :name}——生产者未完成渲染，禁把占位符拼进 SQL。 */
    public static final String REASON_UNRENDERED_PLACEHOLDER = "RLS_UNRENDERED_PLACEHOLDER";

    /** 消费者在传输层构造的不可用态前缀（§4.5.4：不可用与无权限分态）。 */
    public static final String REASON_SERVICE_UNAVAILABLE = "RLS_SERVICE_UNAVAILABLE";

    /** security 明确 DENY 但应答没带理由时的兜底理由（不冒充"无数据"）。 */
    public static final String REASON_DENY_FROM_SECURITY_ENGINE = "RLS_DENY_FROM_SECURITY_ENGINE";

    // ── 「不可用」态的对外码面（§4.5.4 分态的可寻址形态） ──

    /**
     * 不可用态的对外理由码：security 进程连不上／超时／寻址失败。
     *
     * <p><b>#142/B4-3d-10（D-B4-26 余项）</b>：此码此前只在 workspace 异常类与
     * data-engine {@code QueryController} 各写一份字面量，本体／AI 两条腿连"不可用"
     * 都表达不出来（一律折成 {@code 1=0}）。单源的意义不只是少抄一遍文案：
     * 告警规则要能按这一个码寻址"引擎没答"，抄两份就等于两套检索口径。</p>
     *
     * <p>与 {@code DecisionController} 的 fail-closed 出口、gateway
     * {@code SecurityDownAllChannelDenyTest} 的既有判据同值。</p>
     */
    public static final String SEC_UNAVAILABLE_CODE = "ECOS-SEC-501";

    /**
     * 不可用态的对外信封码（与 {@code ApiResponse.error(503, ECOS-SEC-501, …)} 同源）。
     *
     * <p>取 503 而非 403：403 说的是"你没权限"，503 说的是"裁决机构没答"，
     * 前者不该触发运维告警、后者不该触发权限申诉。</p>
     */
    public static final int SEC_UNAVAILABLE_HTTP = 503;

    /**
     * <b>#142/B4-3e-19（D-B4-63）</b>：调用方<b>未声明行级安全作用域</b>时的对外信封码。
     *
     * <p>与 501／502 分成三个态是必需的，不是文案偏好：501＝「裁决机构没答」（运维告警、可重试），
     * 502＝「OPA 没答」（策略引擎侧），本码＝<b>调用方违约</b>（请求体没有可寻址的安全作用域，
     * 属主<b>从未发起裁决</b>）。把它折进 403「无权限」会让前端弹「找管理员申请权限」的工单，
     * 而真正要修的是调用方代码——所以两者必须有不同的码，日志与告警才寻址得到。</p>
     *
     * <p>取 520 段而非 503：{@code ECOS-SEC-5xx} 是安全裁决族，编号复用 HTTP 状态值会让
     * 「信封码 503」与「HTTP 503」在检索时混成一条（本族既有 501/502 不与 HTTP 值同形）。</p>
     */
    public static final String SCOPE_UNDECLARED_CODE = "ECOS-SEC-520";

    /**
     * 未声明作用域的 HTTP 状态行（与 {@code ApiResponse.error(403, ECOS-SEC-520, …)} 同源）。
     *
     * <p>用 403 而非 503：这不是「服务没答」而是「请求本身不被接受」，重试不会变好；
     * 但信封码必须与策略拒绝（无 501 语义的裸 403）区分，判据见
     * {@code QueryControllerRlsFourStateTest}。</p>
     */
    public static final int SCOPE_UNDECLARED_HTTP = 403;

    /** 占位符残留检测（与生产者 {@code RlsPredicateRenderer.PLACEHOLDER} 同形）。 */
    private static final Pattern RESIDUAL_PLACEHOLDER = Pattern.compile(":(\\w+)");

    private RlsExecutionContract() {
    }

    /** 执行契约的状态机（四态互斥，禁折叠）。 */
    public enum State {
        /** 裁决明确不限制行（{@code 1=1}）。 */
        ALLOW_ALL,
        /** 非平凡谓词，必须成对绑定参数后方可下推。 */
        FILTERED,
        /** 拒绝：生产者 {@code 1=0}／无策略命中／不变量不过。 */
        DENIED,
        /** security 进程不可用（传输／寻址／超时），非「用户无权限」。 */
        UNAVAILABLE
    }

    /**
     * 一次读取的裁决载体：状态 + 片段 + 同序参数 + 理由。
     *
     * @param state              四态之一，永不为 {@code null}
     * @param clause             归一前的 WHERE 片段；拒绝/不可用态为 {@link #DENY_CLAUSE}／空串
     * @param params             与 {@code clause} 里 {@code ?} 同序的参数表（不可变，可为空表）
     * @param unresolvedBindings 生产者报回的未解析上下文绑定名（不可变，可为空表）
     * @param denyIfEmpty        应答 {@code denyIfEmpty}（键缺失按 {@code true}，即默认拒绝）
     * @param noPolicyMatched    应答 {@code noPolicyMatched}（键缺失时按 {@code true} 保守处理）
     * @param reason             拒绝/不可用理由；{@link State#ALLOW_ALL} 与可用 {@link State#FILTERED} 为 {@code null}
     */
    public record Execution(State state,
                            String clause,
                            List<Object> params,
                            List<String> unresolvedBindings,
                            boolean denyIfEmpty,
                            boolean noPolicyMatched,
                            String reason) {

        public Execution {
            params = params == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(params));
            unresolvedBindings = unresolvedBindings == null
                    ? List.of() : Collections.unmodifiableList(new ArrayList<>(unresolvedBindings));
        }

        /** 可用（可以按裁决取数）＝恒真放行或可绑定的非平凡谓词；拒绝与不可用都不可用。 */
        public static boolean isUsable(State state) {
            return state == State.ALLOW_ALL || state == State.FILTERED;
        }

        public boolean allowAll()   { return state == State.ALLOW_ALL; }

        public boolean filtered()   { return state == State.FILTERED; }

        public boolean denied()     { return state == State.DENIED; }

        public boolean unavailable() { return state == State.UNAVAILABLE; }

        /** 消费者唯一放行判据：与 {@link #reason()} 一起构成「可解释的可用性」。 */
        public boolean usable()     { return isUsable(state); }

        /** 是否需要绑定参数（{@code FILTERED} 且参数非空）。 */
        public boolean needsBinding() {
            return state == State.FILTERED && !params.isEmpty();
        }

        /** 参数表是否含 {@code null}（上下文未解析值，禁作绑定值）。 */
        public static boolean containsNullParam(List<Object> values) {
            if (values == null) return false;
            // 不用 List#contains(null)：JDK 不可变列表对其直接抛 NPE（ImmutableCollections 契约），
            // 拒绝校验自身禁成为崩溃源（本仓口径：缺陷归一为可解释的拒绝态）。
            for (Object p : values) {
                if (p == null) return true;
            }
            return false;
        }

        /** 三态归一的「拼什么」：恒真→空串（不必拼）、拒绝/不可用→{@code 1=0}、非平凡→原片段。 */
        public String clauseForSplice() {
            if (allowAll()) return "";
            return (denied() || unavailable()) ? DENY_CLAUSE : clause;
        }

        /** 三态归一的「绑什么」（与 {@link #clauseForSplice()} 里的 {@code ?} 同序）。 */
        public List<Object> paramsForSplice() {
            return usable() ? params : List.of();
        }

        /** 构造拒绝态（默认 {@code denyIfEmpty=true}，与生产者合并默认一致）。 */
        public static Execution deny(String reason, boolean noPolicyMatched, List<String> unresolved) {
            return deny(reason, true, noPolicyMatched, unresolved);
        }

        /** 构造拒绝态（消费者本地不变量不过时使用）。 */
        public static Execution deny(String reason, boolean denyIfEmpty, boolean noPolicyMatched,
                                     List<String> unresolved) {
            return new Execution(State.DENIED, DENY_CLAUSE, List.of(),
                    unresolved == null ? List.of() : unresolved, denyIfEmpty, noPolicyMatched, reason);
        }

        /** 构造不可用态（仅消费者在传输/寻址层失败时可用；生产者应答永不含此态）。 */
        public static Execution unavailable(String reason) {
            return new Execution(State.UNAVAILABLE, "", List.of(), List.of(), true, false,
                    REASON_SERVICE_UNAVAILABLE + "(" + reason + ")");
        }

        /** 构造恒真态（内部调用链显式表达「不限制行」时使用）。
         *
         * <p>命名带 {@code of} 前缀是硬约束：记录体内静态工厂与实例谓词 {@link #allowAll()}
         * 若同名即编译期重复方法（{@code allowAll()} 无参与有参擦除冲突），
         * 而把实例谓词改名会让四消费者的分态判据出现第二套口径，故只改工厂名。</p>
         */
        public static Execution ofAllowAll() {
            return new Execution(State.ALLOW_ALL, ALLOW_ALL_CLAUSE, List.of(), List.of(), true, false, null);
        }

        /**
         * 无参数绑定通道的消费方（ontology / ai 引擎：响应侧过滤、OAG 只传上下文）的唯一出口：
         * {@code FILTERED} 片段一律转拒绝——宁 DENY 不猜值拼接（IR05），
         * 理由带上片段原文与主体，禁把「本片段的值」拼进 SQL。
         *
         * @param subject 仅用于理由串定位（实体名／表名）
         */
        public Execution rejectForUnbindableChannel(String subject) {
            if (state != State.FILTERED) return this;
            long marks = clause == null ? 0 : clause.chars().filter(c -> c == '?').count();
            if (marks == 0) return this; // 裸文本条件，无参数通道的消费方可原样下推
            return deny(REASON_PARAM_UNBINDABLE + "(subject=" + subject + ", clause=" + clause
                    + ", 片段需绑参 " + marks + " 个，本通道无参数通道)",
                    denyIfEmpty, false, unresolvedBindings);
        }
    }

    /**
     * 从 REST 应答的 {@code data} 段读取执行契约，并在构造期做完四道不变量。
     *
     * <p>本方法是四通道唯一的读数入口：缺片段＝漂移即拒绝（不是「无过滤」）、
     * {@code ?} 与参数个数不符＝拒绝、参数含 {@code null}＝拒绝、
     * 片段仍含 {@code :name}＝拒绝。任一条都不降级成「看起来能用」的片段。</p>
     *
     * @param dataSegment {@code resp.get("data")} 的原值（可为 {@code null}）
     * @param subject     主体定位串（实体名／表名），只进理由文本，不参与判定
     */
    public static Execution read(Object dataSegment, String subject) {
        if (!(dataSegment instanceof Map<?, ?> data)) {
            return Execution.deny(REASON_NO_DATA_SEGMENT + " subject=" + subject, true, true, List.of());
        }
        boolean denyIfEmpty = bool(data.get(KEY_DENY_IF_EMPTY), true);
        List<String> unresolved = strings(data.get(KEY_UNRESOLVED_BINDINGS));
        Object whereObj = data.get(KEY_WHERE_CLAUSE);
        // 本类四道不变量不过时，noPolicyMatched 一律按 true 保守处理（与 workspace 收口期
        // RlsFilter.deny(...) 的既有取值一致）：本地判定不掌握「命中了几段」的事实，
        // 保守值只能往「更严」一侧靠，禁拿应答值做出比收口前更宽松的表达。
        if (whereObj == null || String.valueOf(whereObj).isBlank()) {
            return Execution.deny(REASON_WHERE_ABSENT + " subject=" + subject,
                    denyIfEmpty, true, unresolved);
        }
        String clause = String.valueOf(whereObj).trim();
        String producerReason = producerReason(data);
        if (DENY_CLAUSE.equals(normalize(clause))) {
            // 生产者的拒绝态：noPolicyMatched 由应答说了算（键缺失时按 true 保守）
            return new Execution(State.DENIED, DENY_CLAUSE, List.of(), unresolved, denyIfEmpty,
                    bool(data.get(KEY_NO_POLICY_MATCHED), true),
                    producerReason == null ? REASON_DENY_FROM_SECURITY_ENGINE : producerReason);
        }
        List<Object> params = objects(data.get(KEY_WHERE_PARAMS));
        long marks = clause.chars().filter(c -> c == '?').count();
        if (marks != params.size()) {
            return Execution.deny(REASON_PARAM_ARITY_MISMATCH + "(subject=" + subject
                    + ", 片段 ? 数=" + marks + ", 参数数=" + params.size() + ")",
                    denyIfEmpty, true, unresolved);
        }
        if (Execution.containsNullParam(params)) {
            return Execution.deny(REASON_NULL_PARAM + "(subject=" + subject
                    + "，上下文未解析值禁作绑定值)", denyIfEmpty, true, unresolved);
        }
        if (RESIDUAL_PLACEHOLDER.matcher(clause).find()) {
            return Execution.deny(REASON_UNRENDERED_PLACEHOLDER + "(subject=" + subject
                    + "，片段仍含 :name)", denyIfEmpty, true, unresolved);
        }
        // 走到这里说明应答给了可用片段（无策略命中在生产侧已被渲染成 1=0，不会到这里），
        // 故 noPolicyMatched 缺失时按 false —— 与收口期 RlsFilter 的两个可用分支一致。
        boolean matched = bool(data.get(KEY_NO_POLICY_MATCHED), false);
        if (ALLOW_ALL_CLAUSE.equals(normalize(clause))) {
            // 片段原文照存（可能是 "(1=1)" 这类带括号形态），态判定只看 state/normalize，
            // 禁把原文改写后再交给消费者拼 SQL
            return new Execution(State.ALLOW_ALL, clause, List.of(), unresolved,
                    denyIfEmpty, matched, null);
        }
        return new Execution(State.FILTERED, clause, params, unresolved,
                denyIfEmpty, matched, null);
    }

    /** 剥括号与空白（{@code "(1=1)"} 与 {@code "1=1"} 同态；与生产者口径一致）。 */
    public static String normalize(String clause) {
        if (clause == null) return "";
        StringBuilder sb = new StringBuilder(clause.length());
        for (int i = 0; i < clause.length(); i++) {
            char c = clause.charAt(i);
            if (c != '(' && c != ')' && !Character.isWhitespace(c)) sb.append(c);
        }
        return sb.toString();
    }

    /**
     * 是否为「不可用」信封：{@code ApiResponse.code ==}{@link #SEC_UNAVAILABLE_HTTP}
     * 或 {@code message} 以 {@link #SEC_UNAVAILABLE_CODE} 起头。
     *
     * <p><b>为什么需要这一层</b>：security 侧内部裁决失败时以 <b>HTTP 200 + 失败信封</b>
     * 应答（{@code DecisionController} 的 fail-closed 出口），应答里没有 {@code data} 段。
     * 消费者若只看 {@code data}，会把它读成「应答缺执行契约」＝形态漂移（DENIED），
     * 而事实是"引擎没给出结论"＝不可用（§4.5.4 禁止的正是这种折叠）。</p>
     *
     * <p>#142/B4-3d-10 前，这一判据在 ai 客户端与 workspace 客户端各抄一份；
     * 现由本类单源持有，消费者只做委托（HTTP 信封的字段名 {@code code}/{@code message}
     * 是 {@code ApiResponse} 的契约面，不属 RLS 键面）。</p>
     */
    public static boolean isUnavailableEnvelope(Map<?, ?> resp) {
        if (resp == null) return false;
        Object code = resp.get("code");
        if (code instanceof Number n && n.intValue() == SEC_UNAVAILABLE_HTTP) return true;
        Object message = resp.get("message");
        return message instanceof String s && s.startsWith(SEC_UNAVAILABLE_CODE);
    }

    private static String producerReason(Map<?, ?> data) {
        Object reason = data.get(KEY_RLS_DENY_REASON);
        if (!(reason instanceof String raw) || raw.isBlank()) return null;
        return raw;
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
        return out;
    }

    private static List<String> strings(Object raw) {
        if (!(raw instanceof List<?> list)) return List.of();
        List<String> out = new ArrayList<>(list.size());
        for (Object o : list) {
            if (o != null && !String.valueOf(o).isBlank()) out.add(String.valueOf(o));
        }
        return out;
    }
}
