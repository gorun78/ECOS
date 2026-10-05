package com.chinacreator.gzcm.ai.wagent.orchestrator;

import com.chinacreator.gzcm.ai.wagent.WAgentEnums.PlanSource;
import com.chinacreator.gzcm.ai.wagent.WAgentEnums.StepType;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 分册10 F10-11/F10-12 · Plan 上下文（无状态 orchestrator 的规划产物核心 DTO）。
 *
 * <p>一条 Run 对应一个 {@code PlanContext}：{@code questionId}/ {@code runId} 双键定位，
 * {@code source} 区分 PLAYBOOK（模板冻结）与 EXPLORED（自由探索，必走 human_gate 硬约束，
 * 见 {@code ExplorationGate}）。steps 有序、stepKey 唯一（落表由 {@code StepWriter} 保证
 * UNIQUE(run_id, step_key, attempt)）。 immutable record，禁就地改。</p>
 */
public record PlanContext(String questionId, String runId, PlanSource source,
                          List<Step> steps, int replanCount) {

    public PlanContext {
        Objects.requireNonNull(questionId, "questionId");
        Objects.requireNonNull(runId, "runId");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(steps, "steps");
        replanCount = Math.max(0, replanCount);
        steps = List.copyOf(steps);
        for (Step s : steps) Objects.requireNonNull(s, "step null");
    }

    /**
     * 单步：{@code stepKey} 唯一标识；{@code type} 六型；{@code toolName} = TOOLS 型时指向
     * {@code ToolContractRegistry}；{@code wLevel} = 本步所需自动化等级（0..3，L3 需 approval_token）；
     * {@code inputsRefs} = 输入槽位引用（step 间数据流，空 = 无引用）。
     */
    public record Step(String stepKey, StepType type, String toolName, int wLevel,
                       Map<String, String> inputsRefs) {
        public Step {
            Objects.requireNonNull(stepKey, "stepKey");
            Objects.requireNonNull(type, "type");
            wLevel = Math.max(0, Math.min(3, wLevel));
            inputsRefs = (inputsRefs == null) ? Map.of() : Map.copyOf(inputsRefs);
        }
    }

    /** 测试/装配便捷工厂：构造一条 source=EXPLORED、replan=0 的计划。 */
    public static PlanContext fromJson(String source, List<Step> steps) {
        PlanSource src = (source == null) ? PlanSource.EXPLORED : PlanSource.valueOf(source);
        return new PlanContext("q-" + System.nanoTime(), "run-" + System.nanoTime(), src, steps, 0);
    }

    /** 按 stepKey 取步（不存在 ⇒ null，调用方判空）。 */
    public Step step(String stepKey) {
        for (Step s : steps) {
            if (s.stepKey().equals(stepKey)) return s;
        }
        return null;
    }

    /** 是否含某类型步（供 ExplorationGate 判 HUMAN_GATE）。 */
    public boolean hasStepType(StepType type) {
        return steps.stream().anyMatch(s -> s.type() == type);
    }
}
