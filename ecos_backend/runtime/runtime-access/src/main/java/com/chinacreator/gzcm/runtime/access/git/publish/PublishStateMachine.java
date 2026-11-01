package com.chinacreator.gzcm.runtime.access.git.publish;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * 发布 / 双写状态机——§2.9.6① 的 <b>16 态 + 完整合法转换表</b>单源（验收 §3.1.4 DF-1；#147D）。
 *
 * <p><b>本类是三张表的唯一持有面</b>：状态词表在 {@link PublishState}、转换表在本类
 * {@link #TRANSITIONS}、判定表在 {@link PublishReconciler}。任何域都不许再写一份"自己的发布态"
 * ——由 common-api 的 {@code PublishStateMachineSingleSourceGateTest} 仓扫门钉住。</p>
 *
 * <p><b>转换表的两条结构约束（单测逐条取证，非口头承诺）</b>：</p>
 * <ol>
 *   <li><b>主链单向且不跳跃</b>：{@code DRAFT→VALIDATING→…→SUCCEEDED} 每一枚主链态的出边
 *       必须含「序号 +1」的那枚，且主链出边只此一枚向前；其余出边一律落在旁支失败族。</li>
 *   <li><b>失败必须产生可识别状态</b>（DF-1 的判据本身）：每一个可能失败的入口态都有明确去向
 *       （{@code REJECTED}/{@code FAILED}/{@code PARTIAL_FAILURE}/{@code DRIFTED}），
 *       禁把失败写成"停在原态 + 一条 warn 日志"——那正是收口前本体发布的真实形态
 *       （DB 改态成功、Git 归档失败只 warn，对外仍报已发布）。</li>
 * </ol>
 *
 * <p><b>终态由表导出，不另行钉值</b>：{@link #terminalStates()} 取「出边为空」的态，
 * 现表下恰为 {@link PublishState#ROLLED_BACK} 一枚（§2.9.6① 的恢复轨终点）。把终态写成派生量
 * 是为了让「改表」与「改终态口径」不能各说各话。</p>
 *
 * <p><b>回滚可及集合同样派生</b>：{@link #rollbackEligibleStates()} = 有出边指向
 * {@link PublishState#ROLLBACK_PENDING} 的全部态。禁在任何地方另写一份"哪些态能回滚"的判断。</p>
 *
 * <p><b>纯规则面</b>：本类不触数据库、不拼 SQL、不发 HTTP（§2.9.6② 的回滚机制是
 * 「发布已知历史版本」而非任意反向 SQL，由 {@link RollbackPlan} 的类型面唯一性保证）。
 * 该不变量由门禁以「属主包内剥注释后不得出现任何 DML 动词」的形式取证。</p>
 *
 * @author ECOS Runtime底座 #147D
 */
public final class PublishStateMachine {

    /** 初始态：一次发布请求总是从 {@link PublishState#DRAFT} 起手。 */
    public static final PublishState INITIAL = PublishState.DRAFT;

    /** 完整合法转换表（16 枚起点全覆盖，含终态的空集）。 */
    private static final Map<PublishState, Set<PublishState>> TRANSITIONS = buildTransitions();

    private PublishStateMachine() {
    }

    /**
     * 转换表本体。
     *
     * <p>逐枚口径（§2.9.6①②③，改动任一枚须同批改单测与台账）：</p>
     * <ul>
     *   <li>{@code DRAFT} — 校验不通过或提交即被驳回：{@code VALIDATING}/{@code REJECTED}。</li>
     *   <li>{@code VALIDATING} — 过校验进审批；校验硬失败 {@code FAILED}；
     *       校验期间被撤回 {@code REJECTED}（人拒与机拒分态）。</li>
     *   <li>{@code PENDING_APPROVAL} — 批准 {@code APPROVED}；拒绝 {@code REJECTED}；
     *       审批过期/服务不可用 {@code FAILED}；发起人撤回 {@code DRAFT}。</li>
     *   <li>{@code APPROVED} — 进可应用 {@code READY_TO_APPLY}；审批被撤销 {@code REJECTED}；
     *       前置环境故障 {@code FAILED}。</li>
     *   <li>{@code READY_TO_APPLY} — 开跑 {@code APPLYING}；前置门失效 {@code FAILED}；
     *       被重新编辑 {@code DRAFT}（批准结果随之作废）。</li>
     *   <li>{@code APPLYING} — 权威库落 {@code APPLIED}；<b>三腿不成套 →
     *       {@code PARTIAL_FAILURE}</b>（DF-2 的转换落点）；全腿败 {@code FAILED}；
     *       直接进入恢复 {@code ROLLBACK_PENDING}。</li>
     *   <li>{@code APPLIED} — 进对账 {@code VERIFYING}；对账前即发现不成套
     *       {@code PARTIAL_FAILURE}；申请恢复 {@code ROLLBACK_PENDING}。</li>
     *   <li>{@code VERIFYING} — 判定一致 {@code SUCCEEDED}；判定漂移族 {@code DRIFTED}；
     *       判定缺失/多余/部分 {@code PARTIAL_FAILURE}；判回滚 {@code ROLLBACK_PENDING}。
     *       判定「无法验证」<b>无出边</b>：未取到证据不是结论，状态不得推进，
     *       也绝不落成功（{@link #stateAfterVerification}）。</li>
     *   <li>{@code SUCCEEDED} — 事后发现漂移 {@code DRIFTED}；申请回滚
     *       {@code ROLLBACK_PENDING}。<b>成功不是终态</b>：对账是持续的，
     *       漂移必须能从成功态被打回。</li>
     *   <li>{@code REJECTED} — 修订重提 {@code DRAFT}（拒绝是业务裁决，可再来一轮）。</li>
     *   <li>{@code PARTIAL_FAILURE} — 幂等重试 {@code APPLYING}；补证据后复核
     *       {@code VERIFYING}；转恢复 {@code ROLLBACK_PENDING}；确认放弃 {@code FAILED}。</li>
     *   <li>{@code FAILED} — 重新起草 {@code DRAFT}；同请求重试 {@code APPLYING}；
     *       失败态已写过权威库时转 {@code ROLLBACK_PENDING}。</li>
     *   <li>{@code ROLLBACK_PENDING} — 计划可执行则 {@code ROLLING_BACK}；
     *       登记失败 {@code FAILED}（不可执行时停在待回滚并出 {@link RollbackPlan} 的
     *       {@code UNAVAILABLE} 原因，§2.9.6②「保留上下文待人工」）。</li>
     *   <li>{@code ROLLING_BACK} — 完成 {@code ROLLED_BACK}；回滚自身部分失败
     *       {@code PARTIAL_FAILURE}；回滚失败 {@code FAILED}。
     *       <b>无回退到成功态的边</b>：回滚了就是回滚了。</li>
     *   <li>{@code DRIFTED} — 重应用消漂 {@code APPLYING}；补证据复核 {@code VERIFYING}；
     *       转恢复 {@code ROLLBACK_PENDING}。</li>
     *   <li>{@code ROLLED_BACK} — <b>无出边（唯一终态）</b>。</li>
     * </ul>
     */
    private static Map<PublishState, Set<PublishState>> buildTransitions() {
        Map<PublishState, Set<PublishState>> m = new EnumMap<>(PublishState.class);
        m.put(PublishState.DRAFT, EnumSet.of(
                PublishState.VALIDATING, PublishState.REJECTED));
        m.put(PublishState.VALIDATING, EnumSet.of(
                PublishState.PENDING_APPROVAL, PublishState.FAILED, PublishState.REJECTED));
        m.put(PublishState.PENDING_APPROVAL, EnumSet.of(
                PublishState.APPROVED, PublishState.REJECTED, PublishState.FAILED, PublishState.DRAFT));
        m.put(PublishState.APPROVED, EnumSet.of(
                PublishState.READY_TO_APPLY, PublishState.REJECTED, PublishState.FAILED));
        m.put(PublishState.READY_TO_APPLY, EnumSet.of(
                PublishState.APPLYING, PublishState.FAILED, PublishState.DRAFT));
        m.put(PublishState.APPLYING, EnumSet.of(
                PublishState.APPLIED, PublishState.PARTIAL_FAILURE,
                PublishState.FAILED, PublishState.ROLLBACK_PENDING));
        m.put(PublishState.APPLIED, EnumSet.of(
                PublishState.VERIFYING, PublishState.PARTIAL_FAILURE, PublishState.ROLLBACK_PENDING));
        m.put(PublishState.VERIFYING, EnumSet.of(
                PublishState.SUCCEEDED, PublishState.DRIFTED,
                PublishState.PARTIAL_FAILURE, PublishState.ROLLBACK_PENDING));
        m.put(PublishState.SUCCEEDED, EnumSet.of(
                PublishState.DRIFTED, PublishState.ROLLBACK_PENDING));
        m.put(PublishState.REJECTED, EnumSet.of(PublishState.DRAFT));
        m.put(PublishState.PARTIAL_FAILURE, EnumSet.of(
                PublishState.APPLYING, PublishState.VERIFYING,
                PublishState.ROLLBACK_PENDING, PublishState.FAILED));
        m.put(PublishState.FAILED, EnumSet.of(
                PublishState.DRAFT, PublishState.APPLYING, PublishState.ROLLBACK_PENDING));
        m.put(PublishState.ROLLBACK_PENDING, EnumSet.of(
                PublishState.ROLLING_BACK, PublishState.FAILED));
        m.put(PublishState.ROLLING_BACK, EnumSet.of(
                PublishState.ROLLED_BACK, PublishState.PARTIAL_FAILURE, PublishState.FAILED));
        m.put(PublishState.DRIFTED, EnumSet.of(
                PublishState.APPLYING, PublishState.VERIFYING, PublishState.ROLLBACK_PENDING));
        m.put(PublishState.ROLLED_BACK, EnumSet.noneOf(PublishState.class));

        Map<PublishState, Set<PublishState>> frozen = new EnumMap<>(m);
        for (Map.Entry<PublishState, Set<PublishState>> e : frozen.entrySet()) {
            e.setValue(Collections.unmodifiableSet(e.getValue()));
        }
        return Collections.unmodifiableMap(frozen);
    }

    /** 合法转换表（不可变视图，DF-1 要求「完整合法转换表」可被外部读出）。 */
    public static Map<PublishState, Set<PublishState>> transitionTable() {
        return TRANSITIONS;
    }

    /** 该态的全部出边（终态返回空集）。 */
    public static Set<PublishState> allowedTransitions(PublishState from) {
        requireKnown(from);
        return TRANSITIONS.get(from);
    }

    public static boolean canTransition(PublishState from, PublishState to) {
        requireKnown(from);
        requireKnown(to);
        return TRANSITIONS.get(from).contains(to);
    }

    /** 出边指向 {@code target} 的全部起点（派生量，禁另写口径）。 */
    public static Set<PublishState> statesWithEdgeTo(PublishState target) {
        requireKnown(target);
        Set<PublishState> out = new LinkedHashSet<>();
        for (Map.Entry<PublishState, Set<PublishState>> e : TRANSITIONS.entrySet()) {
            if (e.getValue().contains(target)) {
                out.add(e.getKey());
            }
        }
        return Collections.unmodifiableSet(out);
    }

    /** 终态集合：出边为空的态（现表恰为 {@link PublishState#ROLLED_BACK} 一枚）。 */
    public static Set<PublishState> terminalStates() {
        Set<PublishState> out = new LinkedHashSet<>();
        for (Map.Entry<PublishState, Set<PublishState>> e : TRANSITIONS.entrySet()) {
            if (e.getValue().isEmpty()) {
                out.add(e.getKey());
            }
        }
        return Collections.unmodifiableSet(out);
    }

    public static boolean isTerminal(PublishState state) {
        requireKnown(state);
        return TRANSITIONS.get(state).isEmpty();
    }

    /** 可发起回滚的态＝有出边指向 {@link PublishState#ROLLBACK_PENDING}（派生）。 */
    public static Set<PublishState> rollbackEligibleStates() {
        return statesWithEdgeTo(PublishState.ROLLBACK_PENDING);
    }

    /**
     * 校验转换合法性；非法即抛 {@link IllegalPublishTransitionException}（409 语义 + 出边集）。
     *
     * <p>终态出发的任何转换用 {@code PUB_TERMINAL_STATE} 单独分码——「已回滚的请求不得再推进」
     * 与「普通跳步」在审计上是两件事，折叠成一码会让前者看起来像笔误。</p>
     */
    public static void assertTransition(PublishState from, PublishState to) {
        requireKnown(from);
        requireKnown(to);
        if (isTerminal(from)) {
            throw new IllegalPublishTransitionException(
                    IllegalPublishTransitionException.CODE_TERMINAL_STATE,
                    "终态不可再推进：" + from + " → " + to + "（出边集为空）",
                    from, to, allowedTransitions(from));
        }
        if (!canTransition(from, to)) {
            throw new IllegalPublishTransitionException(
                    IllegalPublishTransitionException.CODE_ILLEGAL_TRANSITION,
                    "非法状态转换：" + from + " → " + to + "，合法出边=" + allowedTransitions(from),
                    from, to, allowedTransitions(from));
        }
    }

    /**
     * 由三腿记录推出<b>失败/中间态</b>（§2.9.6① 红线「三者分别记录，禁把部分成功报完全成功」）。
     *
     * <p>返回的态一律不是 {@link PublishState#SUCCEEDED}：成功需要
     * {@link PublishReconciler} 给出 {@link ReconciliationVerdict#CONSISTENT}
     * 并经 {@link #stateAfterVerification} 推进——本函数只回答"腿跑到哪了"，
     * 不回答"两边一致吗"。</p>
     *
     * <ul>
     *   <li>关键安全变更且审计腿未确证 → {@link PublishState#FAILED}（fail-closed，§2.9.6②）；</li>
     *   <li>无任何腿成功 → {@link PublishState#FAILED}；</li>
     *   <li>有腿成功但三腿不成套（含 {@code UNKNOWN}、{@code FAILED}、成功腿缺正向证据指针）
       → {@link PublishState#PARTIAL_FAILURE}（DF-2 断点注入的期望态）；</li>
     *   <li>三腿成套 → {@link PublishState#APPLIED}（权威库已应用，待对账）。</li>
     * </ul>
     */
    public static PublishState deriveState(DualWriteOutcome outcome) {
        if (outcome == null) {
            throw new IllegalArgumentException("三腿记录必填（§2.9.6① 分别记录，禁缺省折叠）");
        }
        if (outcome.securityCritical() && !outcome.auditLeg().isSuccess()) {
            return PublishState.FAILED;
        }
        if (!outcome.anyLegSucceeded()) {
            return PublishState.FAILED;
        }
        if (!outcome.allLegsCleared()
                || (outcome.gitLeg().isSuccess() && !outcome.gitEvidencePresent())
                || (outcome.dbLeg().isSuccess() && !outcome.versionEvidencePresent())) {
            return PublishState.PARTIAL_FAILURE;
        }
        return PublishState.APPLIED;
    }

    /**
     * 对账判定后的态推进（§2.9.6③）。
     *
     * <p>{@link ReconciliationVerdict#UNVERIFIABLE} 返回 {@code from} 本身：
     * 证据没取到不是结论，既不许报成功也不许报漂移，只能停在验证中并把
     * {@code evidenceGaps} 带出去补取数。</p>
     */
    public static PublishState stateAfterVerification(PublishState from, ReconciliationVerdict verdict) {
        requireKnown(from);
        if (verdict == null) {
            return from;
        }
        switch (verdict) {
            case CONSISTENT:
                return PublishState.SUCCEEDED;
            case CONTENT_DRIFT:
            case VERSION_REF_MISMATCH:
            case DEPENDENCY_MISMATCH:
                return PublishState.DRIFTED;
            case MISSING_OBJECT:
            case EXTRA_OBJECT:
            case PARTIAL_PUBLISH:
                return PublishState.PARTIAL_FAILURE;
            case UNVERIFIABLE:
            default:
                return from;
        }
    }

    /**
     * 产出回滚计划（§2.9.6②／DF-3）。
     *
     * <p>状态不在可回滚集内 → 抛 {@code PUB_ILLEGAL_TRANSITION}（携带合法出边集）；
     * 引用不全 → 返回 {@link RollbackPlan.Mode#UNAVAILABLE} 并附原因，
     * <b>不</b>抛异常：待人工是设计要求的处置，不是调用方的编程错误。</p>
     */
    public static RollbackPlan planRollback(PublishState from, String repositoryId,
                                            String gitRef, String versionNo) {
        requireKnown(from);
        if (!rollbackEligibleStates().contains(from)) {
            throw new IllegalPublishTransitionException(
                    IllegalPublishTransitionException.CODE_ILLEGAL_TRANSITION,
                    "状态 " + from + " 不可发起回滚（无指向 ROLLBACK_PENDING 的出边），合法出边="
                            + allowedTransitions(from),
                    from, PublishState.ROLLBACK_PENDING, allowedTransitions(from));
        }
        return RollbackPlan.republishKnownVersion(repositoryId, gitRef, versionNo);
    }

    /**
     * 执行回滚前的最后一道门：计划必须可执行，否则抛 {@code PUB_ROLLBACK_REF_REQUIRED}。
     *
     * <p>本方法的存在让"拼一段反向写操作把数据抹回去"在类型面上无路可走——
     * 可执行的回滚机制名只有一个（{@link RollbackPlan#MECHANISM_REPUBLISH_KNOWN_VERSION}），
     * 拿不到它就只有待人工。</p>
     */
    public static void assertRollbackExecutable(RollbackPlan plan) {
        if (plan == null || !plan.isExecutable()) {
            throw new IllegalPublishTransitionException(
                    IllegalPublishTransitionException.CODE_ROLLBACK_REF_REQUIRED,
                    "回滚须发布已知历史版本（git ref 检出重应用）；"
                            + (plan == null ? "无计划" : plan.reason())
                            + "。禁以任意反向写操作代替（§2.9.6②／DF-3）",
                    null, null, Collections.emptySet());
        }
    }

    /** 主链上的下一枚（禁跳跃的正面表达）；不在主链或已是链尾时返回 {@code null}。 */
    public static PublishState mainChainNext(PublishState from) {
        requireKnown(from);
        if (!from.isMainChain() || from == PublishState.SUCCEEDED) {
            return null;
        }
        int nextOrder = from.mainChainOrder() + 1;
        for (PublishState cand : allowedTransitions(from)) {
            if (cand.isMainChain() && cand.mainChainOrder() == nextOrder) {
                return cand;
            }
        }
        return null;
    }

    /**
     * 装载期不变量自检：主链每枚态都恰有一枚「序号 +1」的前进出边，且主链出边不跳步。
     *
     * <p>放在静态初始化里而非只靠单测：转换表被人改坏时，任何用到本类的运行面立刻失败，
     * 而不是等到某个消费路径恰好没覆盖到那枚边才静默放行。</p>
     */
    private static void assertMainChainIsForwardOnlyAndUnskippable() {
        for (PublishState s : PublishState.values()) {
            if (!s.isMainChain() || s == PublishState.SUCCEEDED) {
                continue;
            }
            if (mainChainNext(s) == null) {
                throw new IllegalStateException("主链断裂：状态 " + s
                        + " 缺少指向序号 " + (s.mainChainOrder() + 1) + " 的出边");
            }
            int forward = 0;
            for (PublishState t : TRANSITIONS.get(s)) {
                if (t.isMainChain() && t.mainChainOrder() > s.mainChainOrder()) {
                    forward++;
                    if (t.mainChainOrder() != s.mainChainOrder() + 1) {
                        throw new IllegalStateException("主链禁跳跃：" + s + " → " + t);
                    }
                }
            }
            if (forward != 1) {
                throw new IllegalStateException("主链前进边必须恰一枚：" + s + " 实得 " + forward);
            }
        }
        if (TRANSITIONS.size() != PublishState.values().length) {
            throw new IllegalStateException("转换表起点数 " + TRANSITIONS.size()
                    + " 与状态词表枚数 " + PublishState.values().length + " 不一致");
        }
        if (!terminalStates().equals(Set.of(PublishState.ROLLED_BACK))) {
            throw new IllegalStateException("终态派生结果与 §2.9.6① 的 ROLLED_BACK 唯一终态口径不符："
                    + terminalStates());
        }
    }

    static {
        assertMainChainIsForwardOnlyAndUnskippable();
    }

    private static void requireKnown(PublishState state) {
        if (state == null) {
            throw new IllegalArgumentException("状态必填（禁以 null 表达「未知态」，§2.9.6①）");
        }
        if (!TRANSITIONS.containsKey(state)) {
            throw new IllegalStateException("状态词表外来客：" + state
                    + "——新增态须同批改转换表与门禁");
        }
    }

    /** 供对账报告/门禁使用的稳定排序视图。 */
    public static Set<PublishState> orderedOutEdges(PublishState from) {
        return Collections.unmodifiableSet(new TreeSet<>(allowedTransitions(from)));
    }
}
