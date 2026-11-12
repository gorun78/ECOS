package com.chinacreator.gzcm.engine.ai.template;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.chinacreator.gzcm.engine.ai.wagent.WAgentEnums.GoalIntent;
import com.chinacreator.gzcm.engine.ai.wagent.orchestrator.ExplorationPlanner;
import com.chinacreator.gzcm.engine.ai.wagent.orchestrator.WAgentPlan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 内置典型模板目录：八意图覆盖、骨架与 {@link ExplorationPlanner} 同构、确定性、不污染缺口台账。
 */
class BuiltinPipelineTemplateCatalogTest {

    private final BuiltinPipelineTemplateCatalog catalog = new BuiltinPipelineTemplateCatalog();

    @Test
    @DisplayName("目录：八个 GoalIntent 各一枚，ref 前缀隔离且 source=BUILTIN")
    void listsOnePerGoalIntent() {
        List<AipPipelineTemplateService.TemplateItem> items = catalog.list();

        assertEquals(GoalIntent.values().length, items.size());
        for (AipPipelineTemplateService.TemplateItem item : items) {
            assertTrue(item.ref().startsWith(BuiltinPipelineTemplateCatalog.REF_PREFIX));
            assertEquals(AipPipelineTemplateService.SOURCE_BUILTIN, item.source());
            assertFalse(item.name().isBlank(), "展示名不得为空: " + item.ref());
        }
        assertEquals(GoalIntent.values().length,
                items.stream().map(AipPipelineTemplateService.TemplateItem::ref).distinct().count(),
                "ref 不得重复");
    }

    @Test
    @DisplayName("骨架：human_gate 为首步、aggregate 依赖全部工具、工具序列取自 ExplorationPlanner 单源")
    void skeletonMirrorsExplorationPlanner() {
        for (GoalIntent intent : GoalIntent.values()) {
            List<WAgentPlan.Step> steps = catalog.stepsOf(intent);
            List<String> tools = ExplorationPlanner.toolSequenceFor(intent.value());

            // 闸门 + 工具×n + 聚合
            assertEquals(tools.size() + 2, steps.size(), "步骤数应与意图工具数一致: " + intent);
            assertEquals(ExplorationPlanner.HUMAN_GATE_STEP_KEY, steps.get(0).stepKey());
            assertEquals("human_gate", steps.get(0).stepType());
            assertTrue(steps.get(0).dependsOn().isEmpty(), "首步闸门无前驱");

            WAgentPlan.Step aggregate = steps.get(steps.size() - 1);
            assertEquals(ExplorationPlanner.AGGREGATE_STEP_KEY, aggregate.stepKey());
            assertEquals("aggregate", aggregate.stepType());
            assertEquals(tools.size(), aggregate.dependsOn().size(), "聚合依赖全部工具步");

            List<String> toolNames = steps.stream()
                    .filter(WAgentPlan.Step::isToolStep)
                    .map(WAgentPlan.Step::toolName)
                    .toList();
            assertEquals(tools, toolNames, "工具序列必须与 ExplorationPlanner 单源一致: " + intent);
        }
    }

    @Test
    @DisplayName("骨架：确定性（同意图两次生成逐字段相等）+ 链式依赖无分叉")
    void skeletonIsDeterministic() {
        List<WAgentPlan.Step> first = catalog.stepsOf(GoalIntent.DIAGNOSE);
        List<WAgentPlan.Step> second = catalog.stepsOf(GoalIntent.DIAGNOSE);
        assertEquals(first, second, "同输入两次调用必须逐字段相等");

        String previous = ExplorationPlanner.HUMAN_GATE_STEP_KEY;
        for (WAgentPlan.Step step : first.subList(1, first.size() - 1)) {
            assertEquals(List.of(previous), step.dependsOn(), "工具步应为单前驱链");
            previous = step.stepKey();
        }
    }

    @Test
    @DisplayName("骨架：可经拓扑排序（无环、无悬空依赖）")
    void skeletonPassesTopoOrder() {
        for (GoalIntent intent : GoalIntent.values()) {
            WAgentPlan.Plan plan = new WAgentPlan.Plan(
                    com.chinacreator.gzcm.engine.ai.wagent.WAgentEnums.PlanSource.PLAYBOOK,
                    null, null, catalog.stepsOf(intent));
            assertEquals(catalog.stepsOf(intent).size(), WAgentPlan.topoOrder(plan).size(),
                    "拓扑序应覆盖全部步骤: " + intent);
        }
    }

    @Test
    @DisplayName("ref 解析：内置前缀 + 意图名（大小写不敏感），越界/非内置返回 null")
    void resolvesIntentFromRef() {
        assertEquals(GoalIntent.FORECAST, catalog.intentOf("builtin/intent/FORECAST"));
        assertEquals(GoalIntent.FORECAST, catalog.intentOf("builtin/intent/forecast"));
        assertNull(catalog.intentOf("builtin/intent/NOT_AN_INTENT"));
        assertNull(catalog.intentOf("repo/pb1"), "Playbook 引用不属内置");
        assertNull(catalog.intentOf(null));

        assertTrue(catalog.isBuiltinRef("builtin/intent/RANK"));
        assertFalse(catalog.isBuiltinRef("builtin/other"));
        assertFalse(catalog.isBuiltinRef(null));
    }

    @Test
    @DisplayName("目录项摘要如实列出工具链（不写无来源文案）")
    void summaryListsToolChain() {
        for (GoalIntent intent : GoalIntent.values()) {
            String summary = catalog.summaryOf(intent);
            for (String tool : ExplorationPlanner.toolSequenceFor(intent.value())) {
                assertTrue(summary.contains(tool), "摘要应含工具 " + tool + "，实际: " + summary);
            }
        }
    }
}
