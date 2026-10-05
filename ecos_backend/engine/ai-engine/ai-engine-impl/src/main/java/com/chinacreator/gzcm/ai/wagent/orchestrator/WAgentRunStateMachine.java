package com.chinacreator.gzcm.ai.wagent.orchestrator;

import com.chinacreator.gzcm.ai.wagent.WAgentEnums.RunStatus;
import java.util.*;

/**
 * 分册10 F10-11 · W Agent Run 状态机迁移表（附件二册 §2.2 全 12 态）。
 *
 * <p><b>唯一写入口</b>：任何迁移动作都必须过 {@link #canTransition(RunStatus, RunStatus)}，
 * 否则抛 {@link E_STATE}（映射到 HTTP 409 E-WA-STATE，铁律 :14 不可绕）。
 * 恢复语义：无状态 Orchestrator 重启后读 run_id 最大 succeeded Step 继续——不在本类。</p>
 *
 * <p>审计对账 {@code RunStateMachineTest#illegalTransitionRejected}：
 * 每个 from-state 的 toSet 冻结，非法迁移恒为 false。</p>
 */
public final class WAgentRunStateMachine {

    /** 语义异常：E-WA-STATE。 */
    public static final class E_STATE extends IllegalStateException {
        public E_STATE(String msg) { super(msg); }
    }

    private static final Map<RunStatus, Set<RunStatus>> TRANS = new EnumMap<>(RunStatus.class);
    static {
        allow(RunStatus.PLANNING,
                RunStatus.AWAITING_INPUT, RunStatus.AWAITING_APPROVAL,
                RunStatus.PLAN_VALIDATED, RunStatus.FAILED, RunStatus.CANCELLED);
        allow(RunStatus.AWAITING_INPUT,
                RunStatus.PLANNING, RunStatus.AWAITING_APPROVAL,
                RunStatus.FAILED, RunStatus.CANCELLED);
        allow(RunStatus.AWAITING_APPROVAL,
                RunStatus.PLAN_VALIDATED, RunStatus.FAILED, RunStatus.CANCELLED);
        allow(RunStatus.PLAN_VALIDATED,
                RunStatus.EXECUTING, RunStatus.FAILED, RunStatus.CANCELLED);
        allow(RunStatus.EXECUTING,
                RunStatus.WAITING_TASK, RunStatus.PAUSED,
                RunStatus.COMPLETED, RunStatus.COMPLETED_DEGRADED,
                RunStatus.FAILED, RunStatus.CANCELLED, RunStatus.TIMED_OUT);
        allow(RunStatus.WAITING_TASK,
                RunStatus.EXECUTING, RunStatus.FAILED, RunStatus.CANCELLED, RunStatus.TIMED_OUT);
        allow(RunStatus.PAUSED,
                RunStatus.EXECUTING, RunStatus.CANCELLED);
        // 终态：不迁出
        allow(RunStatus.COMPLETED);
        allow(RunStatus.COMPLETED_DEGRADED);
        allow(RunStatus.FAILED);
        allow(RunStatus.CANCELLED);
        allow(RunStatus.TIMED_OUT);
    }

    private static void allow(RunStatus from, RunStatus... to) {
        TRANS.put(from, to.length == 0 ? EnumSet.noneOf(RunStatus.class) : EnumSet.copyOf(Arrays.asList(to)));
    }

    /** 探活：from!=null，to!=null，from∈enum，to∈enum，且 (from,to)∈TRANS[from]。 */
    public static boolean canTransition(RunStatus from, RunStatus to) {
        if (from == null || to == null) return false;
        Set<RunStatus> s = TRANS.get(from);
        return s != null && s.contains(to);
    }

    /** 状态迁移断言：非法 ⇒ 抛 {@link E_STATE}（controller 层映射 E-WA-STATE）。 */
    public static RunStatus ensureTransition(RunStatus from, RunStatus to) {
        if (!canTransition(from, to)) {
            throw new E_STATE("E-WA-STATE: 非法迁移 " + from + " -> " + to);
        }
        return to;
    }

    /** Read-only 皇后：可从哪里进入终态（cancelled/completed/completed_degraded/failed/timed_out）。 */
    public static boolean isTerminal(RunStatus s) {
        switch (s) {
            case COMPLETED: case COMPLETED_DEGRADED: case FAILED:
            case CANCELLED: case TIMED_OUT: return true;
            default: return false;
        }
    }

    public static Set<RunStatus> allowFrom(RunStatus from) {
        return TRANS.getOrDefault(from, EnumSet.noneOf(RunStatus.class));
    }
}
