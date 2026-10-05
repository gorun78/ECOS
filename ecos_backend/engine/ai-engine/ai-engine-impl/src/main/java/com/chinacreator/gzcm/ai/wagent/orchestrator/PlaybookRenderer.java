package com.chinacreator.gzcm.ai.wagent.orchestrator;

import com.chinacreator.gzcm.ai.wagent.WAgentEnums.PlanSource;
import com.chinacreator.gzcm.ai.wagent.WAgentEnums.StepType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 分册10 F10-12 · Playbook 模板冻结器：把 {@link PlaybookTemplate} + {@link Params}
 * 冻结为 source=PLAYBOOK 的确定性 {@link PlanContext}。
 *
 * <p>确定性是硬契约（测试 {@code PlaybookRendererTest#parametersFilledDeterministically}）：
 * 同 template + 同 params ⇒ 同 PlanContext（逐槽位参数确定替换 + 槽位顺序恒为模板顺序，
 * stepKey 由 template 槽位顺序 + 序号生成，禁随机/时钟/遍历不确定顺序）。
 * 无占位可补时 std {@link StepSlot#fillIdempotent()} 原样返回同一步。
 */
public final class PlaybookRenderer {

    private PlaybookRenderer() {}

    /** 模板槽位：占位文本 → 填参后生成一个 Step。slotOrder 决定冻结后的步序。 */
    public record PlaybookTemplate(String templateId, int version, List<StepSlot> slots) {
        public PlaybookTemplate {
            Objects.requireNonNull(templateId, "templateId");
            slots = (slots == null) ? List.of() : List.copyOf(slots);
        }
    }

    /** 填写函数契约：同 (stepKey, params) ⇒ 同一 Step（幂等、无副作用）。实现方不得用时间/随机。 */
    @FunctionalInterface
    public interface StepSlot {
        /**
         * 幂等填槽：给定已确定的 stepKey + params，返回该槽位对应的 Step。
         * 契约：相同 (stepKey, params) 调用多次 ⇒ 相等 Step（deterministic）。
         */
        PlanContext.Step fillIdempotent(int slotIndex, String stepKey, Map<String, String> params);
    }

    /** 冻结渲染入口。 */
    public static PlanContext render(String questionId, String runId,
                                     PlaybookTemplate template, Map<String, String> params) {
        Objects.requireNonNull(template, "template");
        Map<String, String> p = (params == null) ? Map.of() : Map.copyOf(params);
        List<PlanContext.Step> steps = new ArrayList<>(template.slots().size());
        for (int i = 0; i < template.slots().size(); i++) {
            String stepKey = template.templateId() + ":v" + template.version() + ":" + i;
            steps.add(template.slots().get(i).fillIdempotent(i, stepKey, p));
        }
        return new PlanContext(questionId, runId, PlanSource.PLAYBOOK, steps, 0);
    }

    /** 便捷工厂：fromSlot 无参实现一个 TOOL 型步（toolName/wLevel 取 params 或默认）。 */
    public static StepSlot toolSlot(String defaultToolName, int defaultLevel) {
        return (slotIndex, stepKey, params) -> {
            String tool = params.getOrDefault("tool:" + slotIndex, defaultToolName);
            int level = 0;
            String lvl = params.get("level:" + slotIndex);
            if (lvl != null) level = Math.max(0, Math.min(3, Integer.parseInt(lvl)));
            return new PlanContext.Step(stepKey, StepType.TOOL, tool, level, Map.of());
        };
    }
}
