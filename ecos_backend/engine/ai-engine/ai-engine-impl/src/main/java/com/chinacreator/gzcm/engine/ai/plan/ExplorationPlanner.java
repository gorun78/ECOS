package com.chinacreator.gzcm.engine.ai.plan;

import com.chinacreator.gzcm.engine.ai.plan.AiPlanEnums.GoalIntent;
import com.chinacreator.gzcm.engine.ai.plan.AiPlanEnums.PlanSource;
import com.chinacreator.gzcm.engine.ai.plan.AiPlanEnums.StepType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 探索规划器（详细设计-10 F10-13，REQ-WAG-13）。
 *
 * <p>无在用 Playbook 时按意图生成 <b>EXPLORED</b> 计划。生成过程为确定性模板
 * （意图 → 业务语义工具序列，F10-19 名录），LLM 不参与步骤编排——"数字不来自 LLM"
 * 在计划面的形态。产物结构上强制 <b>human_gate 为拓扑序首步</b>（§4.2 EXPLORED 必经闸；
 * 执行面硬约束写在 {@link WAgentRunStateMachine#canTransition}，两闸独立成立）。
 * 同时经 {@link WAgentPlaybookGapEventService} 记录"缺少 Playbook"事件供产品沉淀。</p>
 */
@Component
public class ExplorationPlanner {

    /** 首步闸步骤键（单源；审批入口按此键写 succeeded 行清闸）。 */
    public static final String HUMAN_GATE_STEP_KEY = "s1_human_gate";

    /** 聚合尾步键。 */
    public static final String AGGREGATE_STEP_KEY = "s9_aggregate";

    /**
     * 意图 → 业务语义工具序列（F10-19 首批 12 工具名录内选取；确定性模板，禁运行时 LLM 编排）。
     * 工具是否注册/active 由 {@link PlanValidator} TOOL 检查在校验期把关。
     */
    private static final Map<GoalIntent, List<String>> INTENT_TOOL_SEQUENCE = Map.of(
            GoalIntent.FORECAST, List.of("freeze_data_snapshot", "run_profit_forecast"),
            GoalIntent.DIAGNOSE, List.of("query_project_profit", "find_historical_profile", "run_cognitive_model"),
            GoalIntent.SIMULATE, List.of("freeze_data_snapshot", "run_cognitive_model"),
            GoalIntent.RANK, List.of("query_project_profit", "find_historical_profile"),
            GoalIntent.MONITOR, List.of("check_metric_caliber", "query_project_profit"),
            GoalIntent.BUILD, List.of("find_historical_profile", "create_action_draft"),
            GoalIntent.DECIDE, List.of("run_cognitive_model", "create_decision_option"),
            GoalIntent.REVIEW, List.of("get_action_effect", "query_project_profit"));

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(ExplorationPlanner.class);

    /** 缺口登记端口（可选实现，2026-11-23 去 wagent 化）：无 Bean 时只落日志，不阻断计划生成。 */
    private final org.springframework.beans.factory.ObjectProvider<PlaybookGapRecorder> gapRecorder;

    public ExplorationPlanner(org.springframework.beans.factory.ObjectProvider<PlaybookGapRecorder> gapRecorder) {
        this.gapRecorder = gapRecorder;
    }

    /** 意图对应的工具序列（未登记者空序列——仍出 EXPLORED 计划骨架并记缺口）。 */
    public static List<String> toolSequenceFor(String intent) {
        GoalIntent gi = intent == null ? null : GoalIntent.ALL.stream()
                .filter(v -> v.equalsIgnoreCase(intent.trim()))
                .map(GoalIntent::valueOf)
                .findFirst().orElse(null);
        if (gi != null && INTENT_TOOL_SEQUENCE.containsKey(gi)) {
            return INTENT_TOOL_SEQUENCE.get(gi);
        }
        return List.of("query_project_profit");
    }

    /**
     * 生成 EXPLORED 计划：{@code [human_gate, tool×n(链式依赖), aggregate(依赖全部 tool)]}。
     * human_gate 自动化档位记 0（闸本身只读不写）；tool 步骤默认档位 0（读类工具；
     * 真实档位由 TOOL/LEVEL 校验对照契约 min_level 与 Run ceiling）。
     */
    public AiPlan.Plan plan(String tenantId, String runId, String missionId, String intent,
                                Map<String, Object> slots, String operator) {
        List<String> tools = toolSequenceFor(intent);
        List<AiPlan.Step> steps = new ArrayList<>();
        steps.add(new AiPlan.Step(HUMAN_GATE_STEP_KEY, StepType.HUMAN_GATE.value(),
                null, null, 0, List.of(), Map.of(), false, false, null));
        String previous = HUMAN_GATE_STEP_KEY;
        int idx = 2;
        List<String> toolKeys = new ArrayList<>();
        for (String tool : tools) {
            String key = "s" + idx + "_" + tool;
            steps.add(new AiPlan.Step(key, StepType.TOOL.value(), tool, "1",
                    0, List.of(previous), slots == null ? Map.of() : Map.copyOf(slots),
                    false, false, null));
            toolKeys.add(key);
            previous = key;
            idx++;
        }
        steps.add(new AiPlan.Step(AGGREGATE_STEP_KEY, StepType.AGGREGATE.value(),
                null, null, 0, List.copyOf(toolKeys), Map.of(), false, false, null));

        // 缺口登记（F10-13 明文：含意图、槽位、工具序列，供产品沉淀 Playbook）
        String aggregateId = missionId == null || missionId.isBlank() ? runId : missionId;
        PlaybookGapRecorder recorder = gapRecorder == null ? null : gapRecorder.getIfAvailable();
        if (recorder != null) {
            recorder.recordGap(tenantId, runId, aggregateId,
                    intent, slots == null ? List.of() : slots.keySet(), tools, operator);
        } else {
            // 无缺口登记实现（wagent 事件总线已下线）：只落日志，不阻断主链、不伪造事件
            log.info("[plan] 缺口未登记（无 PlaybookGapRecorder Bean）intent={} runId={} aggregateId={} tools={}",
                    intent, runId, aggregateId, tools);
        }

        return new AiPlan.Plan(PlanSource.EXPLORED, null, null, steps);
    }
}
