package com.chinacreator.gzcm.ai.wagent.orchestrator;

import com.chinacreator.gzcm.ai.wagent.WAgentEnums.PlanSource;
import com.chinacreator.gzcm.ai.wagent.WAgentEnums.StepType;
import com.chinacreator.gzcm.ai.wagent.orchestrator.WAgentRunStateMachine.E_STATE;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 分册10 F10-12 · 探索 Plan 硬约束：source=EXPLORED 的 Plan <b>不得</b> 先进 EXECUTING，
 * 必须先过一道 HUMAN_GATE 步。违反 ⇒ 抛 {@link E_STATE}（E-WA-STATE，映射 409）。
 *
 * <p>PLAYBOOK 型无此约束（模板已冻结、可信度前置）。
 * 附带 {@link PlaybookGapEvent}：探索路径未命中任何 playbook 时落产品侧的信号（埋缺失 playbook 需求）。</p>
 */
public final class ExplorationGate {

    private ExplorationGate() {}

    /**
     * 未命中 playbook 探索事件：向产品侧 sink，累积高频意图 ⇒ 沉淀新 playbook。
     * {@code slots} = 已解析槽位；{@code toolSequence} = 本次探索实际串起的工具序列。
     */
    public record PlaybookGapEvent(String intent, Map<String, String> slots,
                                   List<String> toolSequence, String occurredAt) {
        public PlaybookGapEvent {
            Objects.requireNonNull(intent, "intent");
            slots = (slots == null) ? Map.of() : Map.copyOf(slots);
            toolSequence = (toolSequence == null) ? List.of() : List.copyOf(toolSequence);
        }
    }

    /**
     * 安全入执行判定：EXPLORED Plan 必须存在一个 HUMAN_GATE 步且位于首个可执行步之前。
     * 非 EXPLORED 一律放行。违反 EXPLORING-without-gate ⇒ 抛 {@link E_STATE}。
     */
    public static boolean isSafeToEnterExecuting(PlanContext plan) {
        Objects.requireNonNull(plan, "plan");
        if (plan.source() != PlanSource.EXPLORED) return true;

        boolean gateSeen = false;
        for (PlanContext.Step s : plan.steps()) {
            if (s.type() == StepType.HUMAN_GATE) {
                gateSeen = true;
                break;
            }
            // 任何非 gate 的可执行步出现在 gate 之前 = 违规
            if (isExecutable(s.type())) {
                throw new E_STATE("E-WA-STATE: EXPLORED plan enters EXECUTING without preceding HUMAN_GATE at " + s.stepKey());
            }
        }
        if (!gateSeen) {
            throw new E_STATE("E-WA-STATE: EXPLORED plan lacks any HUMAN_GATE before EXECUTING");
        }
        return true;
    }

    private static boolean isExecutable(StepType t) {
        return t == StepType.TOOL || t == StepType.LLM || t == StepType.SUB_RUN
                || t == StepType.AGGREGATE || t == StepType.WAIT_TASK;
    }
}
