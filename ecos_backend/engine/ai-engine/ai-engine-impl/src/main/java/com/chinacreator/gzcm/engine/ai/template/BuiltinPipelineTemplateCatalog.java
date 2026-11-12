package com.chinacreator.gzcm.engine.ai.template;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.chinacreator.gzcm.engine.ai.wagent.WAgentEnums.GoalIntent;
import com.chinacreator.gzcm.engine.ai.wagent.WAgentEnums.StepType;
import com.chinacreator.gzcm.engine.ai.wagent.orchestrator.ExplorationPlanner;
import com.chinacreator.gzcm.engine.ai.wagent.orchestrator.WAgentPlan;

/**
 * 内置典型模板目录（AI 工作台逻辑编排「从 Wagent 模板新建」的第三来源）。
 *
 * <p><b>为什么需要</b>：Playbook 来源依赖 Git 归档内容源（{@code PlaybookContentSource}，
 * 真实实现随 #131/R3-A 落地），已发布候选依赖 candidate 行声明 playbook 引用——两者在本
 * 环境常态为空，目录永远空则「从模板新建」形同不存在。内置来源把<b>智能体编排域已冻结的
 * 确定性骨架</b>直接暴露为模板，结构完全取自单源，不手写第二套编排规则。</p>
 *
 * <p><b>结构单源（禁第二套语义）</b>：每个意图的骨架 = {@link ExplorationPlanner} 的
 * EXPLORED 形态 {@code [human_gate → tool×n(链式依赖) → aggregate]}，工具序列取
 * {@link ExplorationPlanner#toolSequenceFor}（F10-19 十二工具名录内的意图映射）。
 * 刻意<b>不调用</b> {@code ExplorationPlanner#plan}：那条路径会登记「缺少 Playbook」缺口
 * 事件，而模板目录是设计态消费，不该污染运行期缺口台账。</p>
 *
 * <p><b>不是 Playbook 的替身</b>：ref 前缀 {@link #REF_PREFIX} 与 Playbook git 引用空间
 * 隔离，目录项 source 标 {@code BUILTIN}；实例化不读 Git 内容源，也不伪造
 * {@code plan_source=PLAYBOOK}（返回步骤集，不经 {@link WAgentPlan.Plan} 包装）。</p>
 */
@Component
public class BuiltinPipelineTemplateCatalog {

    /** ref 前缀单源（与 Playbook git 引用空间隔离；instantiate 按此判别来源）。 */
    public static final String REF_PREFIX = "builtin/intent/";

    /** 内置模板统一版本号（骨架结构变更时递增，前端展示 v{version}）。 */
    private static final String VERSION = "1";

    /** 意图 → 展示名（键集由 GoalIntent 单源驱动；漏配即 list() 期 NPE 暴露，禁静默跳过）。 */
    private static final Map<GoalIntent, String> INTENT_LABELS = Map.of(
            GoalIntent.FORECAST, "经营预测",
            GoalIntent.DIAGNOSE, "利润诊断",
            GoalIntent.SIMULATE, "情景推演",
            GoalIntent.RANK, "项目排名",
            GoalIntent.MONITOR, "指标监控",
            GoalIntent.BUILD, "行动方案编制",
            GoalIntent.DECIDE, "决策选项生成",
            GoalIntent.REVIEW, "行动效果复盘");

    /** 内置模板目录项（ref 形如 {@code builtin/intent/FORECAST}）。 */
    public List<AipPipelineTemplateService.TemplateItem> list() {
        List<AipPipelineTemplateService.TemplateItem> items = new ArrayList<>();
        for (GoalIntent intent : GoalIntent.values()) {
            items.add(new AipPipelineTemplateService.TemplateItem(
                    refOf(intent), displayNameOf(intent), VERSION, summaryOf(intent),
                    "default", AipPipelineTemplateService.SOURCE_BUILTIN));
        }
        return items;
    }

    /** 该 ref 是否属于内置来源。 */
    public boolean isBuiltinRef(String ref) {
        return ref != null && ref.startsWith(REF_PREFIX);
    }

    /** ref 对应的意图；非内置或意图越界返回 null（由调用方判 404）。 */
    public GoalIntent intentOf(String ref) {
        if (!isBuiltinRef(ref)) {
            return null;
        }
        String raw = ref.substring(REF_PREFIX.length()).trim();
        for (GoalIntent intent : GoalIntent.values()) {
            if (intent.value().equalsIgnoreCase(raw)) {
                return intent;
            }
        }
        return null;
    }

    /**
     * 生成骨架步骤集：{@code [human_gate, tool×n 链式, aggregate]}。
     * 步骤键与 {@link ExplorationPlanner} 同构（{@code s1_human_gate} /
     * {@code s{n}_{tool}} / {@code s9_aggregate}）。tool 版本取工具契约在用版本 {@code 1.0.0}
     * （V236 登记口径），非 {@code ExplorationPlanner} 的探索态缺省值。
     */
    public List<WAgentPlan.Step> stepsOf(GoalIntent intent) {
        List<String> tools = ExplorationPlanner.toolSequenceFor(intent.value());
        List<WAgentPlan.Step> steps = new ArrayList<>();
        steps.add(new WAgentPlan.Step(ExplorationPlanner.HUMAN_GATE_STEP_KEY,
                StepType.HUMAN_GATE.value(), null, null, 0, List.of(), Map.of(), false, false, null));
        String previous = ExplorationPlanner.HUMAN_GATE_STEP_KEY;
        List<String> toolKeys = new ArrayList<>();
        int idx = 2;
        for (String tool : tools) {
            String key = "s" + idx + "_" + tool;
            steps.add(new WAgentPlan.Step(key, StepType.TOOL.value(), tool, "1.0.0",
                    0, List.of(previous), Map.of(), false, false, null));
            toolKeys.add(key);
            previous = key;
            idx++;
        }
        steps.add(new WAgentPlan.Step(ExplorationPlanner.AGGREGATE_STEP_KEY,
                StepType.AGGREGATE.value(), null, null, 0, List.copyOf(toolKeys), Map.of(), false, false, null));
        return steps;
    }

    /** 展示名（实例化未命名时的兜底）。 */
    public String displayNameOf(GoalIntent intent) {
        return INTENT_LABELS.get(intent) + "模板";
    }

    /** 目录项摘要：如实列出骨架的工具链（不写无来源的营销文案）。 */
    public String summaryOf(GoalIntent intent) {
        List<String> tools = ExplorationPlanner.toolSequenceFor(intent.value());
        return "意图 " + intent.value() + "：人工闸门 → " + String.join(" → ", tools) + " → 聚合";
    }

    public String refOf(GoalIntent intent) {
        return REF_PREFIX + intent.value();
    }
}
