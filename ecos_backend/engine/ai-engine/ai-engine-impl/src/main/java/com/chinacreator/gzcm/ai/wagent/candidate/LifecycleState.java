package com.chinacreator.gzcm.ai.wagent.candidate;

import com.chinacreator.gzcm.ai.wagent.WAgentEnums.CandidateStatus;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 分册10 F10-20 · Candidate 10 态生命周期迁移表（附件 §6.1）。
 *
 * <pre>
 * DRAFT → VALIDATING → READY → PENDING_APPROVAL → { APPROVED, REJECTED }
 * APPROVED → PUBLISHED ;   REJECTED → REVOKED
 * 任意 → EXPIRED ;   任意 → SUPERSEDED
 * </pre>
 *
 * <p>非法迁移抛 {@link IllegalStateException}，前缀 {@code E-WA-STATE}
 * （与 {@code WAgentRunStateMachine} 一致，铁律 :14 不可绕）。</p>
 */
public final class LifecycleState {

    /** 语义异常：E-WA-STATE（IllegalStateException 前缀）。 */
    public static class InvalidCandidateTransition extends IllegalStateException {
        public InvalidCandidateTransition(String msg) { super(msg); }
    }

    private static final Map<CandidateStatus, Set<CandidateStatus>> TRANS = new EnumMap<>(CandidateStatus.class);
    private static final Set<CandidateStatus> TERMINAL = EnumSet.of(
            CandidateStatus.APPROVED, CandidateStatus.REJECTED, CandidateStatus.PUBLISHED,
            CandidateStatus.EXPIRED, CandidateStatus.SUPERSEDED, CandidateStatus.REVOKED);

    private static void allow(CandidateStatus from, CandidateStatus... to) {
        TRANS.put(from, to.length == 0 ? EnumSet.noneOf(CandidateStatus.class) : EnumSet.copyOf(java.util.Arrays.asList(to)));
    }

    static {
        // 任意活跃态都可达 EXPIRED / SUPERSEDED（过期 / 被新候选取代）。
        allow(CandidateStatus.DRAFT,
                CandidateStatus.VALIDATING, CandidateStatus.REJECTED,
                CandidateStatus.EXPIRED, CandidateStatus.SUPERSEDED);
        allow(CandidateStatus.VALIDATING,
                CandidateStatus.READY, CandidateStatus.REJECTED,
                CandidateStatus.EXPIRED, CandidateStatus.SUPERSEDED);
        allow(CandidateStatus.READY,
                CandidateStatus.PENDING_APPROVAL, CandidateStatus.REJECTED,
                CandidateStatus.EXPIRED, CandidateStatus.SUPERSEDED);
        allow(CandidateStatus.PENDING_APPROVAL,
                CandidateStatus.APPROVED, CandidateStatus.REJECTED,
                CandidateStatus.EXPIRED, CandidateStatus.SUPERSEDED);
        allow(CandidateStatus.APPROVED,
                CandidateStatus.PUBLISHED, CandidateStatus.REVOKED,
                CandidateStatus.EXPIRED, CandidateStatus.SUPERSEDED);
        allow(CandidateStatus.REJECTED,
                CandidateStatus.REVOKED, CandidateStatus.EXPIRED, CandidateStatus.SUPERSEDED);
        // 终态：不迁出（EXPIRED/SUPERSEDED/REVOKED/PUBLISHED 均落到不迁出）。
        allow(CandidateStatus.PUBLISHED);
        allow(CandidateStatus.EXPIRED);
        allow(CandidateStatus.SUPERSEDED);
        allow(CandidateStatus.REVOKED);
    }

    private LifecycleState() {}

    /** 探活：非法迁移恒为 false（审计对账基准）。 */
    public static boolean canTransition(CandidateStatus from, CandidateStatus to) {
        if (from == null || to == null) return false;
        if (from == to) return false;
        Set<CandidateStatus> s = TRANS.get(from);
        return s != null && s.contains(to);
    }

    /** 状态迁移断言：非法 ⇒ 抛 {@code E-WA-STATE}（IllegalStateException 派生）。 */
    public static CandidateStatus ensureTransition(CandidateStatus from, CandidateStatus to) {
        if (!canTransition(from, to)) {
            throw new InvalidCandidateTransition("E-WA-STATE: 非法 Candidate 迁移 " + from + " -> " + to);
        }
        return to;
    }

    /** 终态判定（APPROVED/REJECTED/PUBLISHED/EXPIRED/SUPERSEDED/REVOKED）。 */
    public static boolean isTerminal(CandidateStatus s) {
        return s != null && TERMINAL.contains(s);
    }

    public static Set<CandidateStatus> allowFrom(CandidateStatus from) {
        return TRANS.getOrDefault(from, EnumSet.noneOf(CandidateStatus.class));
    }
}
