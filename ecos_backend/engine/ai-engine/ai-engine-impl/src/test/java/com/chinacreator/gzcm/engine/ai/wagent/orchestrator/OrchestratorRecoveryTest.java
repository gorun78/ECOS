package com.chinacreator.gzcm.engine.ai.wagent.orchestrator;

import com.chinacreator.gzcm.engine.ai.wagent.WAgentEnums.PlanSource;
import com.chinacreator.gzcm.engine.ai.wagent.WAgentEnums.RunStatus;
import com.chinacreator.gzcm.engine.ai.wagent.WAgentEnums.StepType;
import com.chinacreator.gzcm.engine.ai.wagent.dao.WAgentRunDao;
import com.chinacreator.gzcm.engine.ai.wagent.dao.WAgentRunEventDao;
import com.chinacreator.gzcm.engine.ai.wagent.dao.WAgentRunStepDao;
import com.chinacreator.gzcm.engine.ai.wagent.dataobject.WAgentRunDO;
import com.chinacreator.gzcm.engine.ai.wagent.dataobject.WAgentRunStepDO;
import com.chinacreator.gzcm.engine.ai.wagent.orchestrator.WAgentBudgetService.Consumption;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 崩溃恢复（详细设计-10 F10-11 / §4.2 恢复算法，冻结验收名）。
 *
 * <p>Orchestrator <b>无状态</b>：重启后读 run_id 下已 {@code succeeded} 的 Step 集合，
 * 按拓扑序跳过已完成步、从第一个未完成步续跑（与正常推进同一条 {@code executeLoop} 路径）。
 * 本用例只 mock 步执行通道，断言"已完成步不再执行、未完成步各执行一次、终态 completed"。</p>
 */
class OrchestratorRecoveryTest {

    private static WAgentPlan.Step tool(String key, List<String> dependsOn) {
        return new WAgentPlan.Step(key, StepType.TOOL.value(), "tool_a", "1", 0, dependsOn,
                Map.of(), false, false, null);
    }

    private static WAgentRunStepDO succeededRow(String stepKey) {
        WAgentRunStepDO row = new WAgentRunStepDO();
        row.setId("row-" + stepKey);
        row.setRunId("r1");
        row.setStepKey(stepKey);
        row.setAttempt(1);
        row.setStepType(StepType.TOOL.value());
        row.setStatus(WAgentStatus.StepStatus.SUCCEEDED.value());
        return row;
    }

    @Test
    @DisplayName("F10-11：崩溃后从最后一个 succeeded Step 续跑（s1 不重跑，s2/s3 各跑一次 → completed）")
    void resumesFromLastCompletedStepAfterCrash() {
        WAgentRunDao runDao = mock(WAgentRunDao.class);
        WAgentRunStepDao stepDao = mock(WAgentRunStepDao.class);
        StepExecutor stepExecutor = mock(StepExecutor.class);
        StepWriter stepWriter = mock(StepWriter.class);
        WAgentQuestionService questionService = mock(WAgentQuestionService.class);

        WAgentRunDO run = new WAgentRunDO();
        run.setId("r1");
        run.setTenantId("t1");
        run.setQuestionId("q1");
        run.setMissionId("m1");
        run.setInitiatedBy("u1");
        run.setAutomationCeiling(1);
        run.setStatus(RunStatus.EXECUTING.value());
        run.setPlanSource(PlanSource.PLAYBOOK.value());
        run.setPlanJson(WAgentPlan.PlanCodec.toJson(new WAgentPlan.Plan(PlanSource.PLAYBOOK,
                "playbooks/profit.git", "1",
                List.of(tool("s1", List.of()), tool("s2", List.of("s1")), tool("s3", List.of("s2"))))));
        // budget_json 缺失路径 ⇒ parseSnapshot 回退冻结默认（60 步预算，本轮不越限）；
        // 脏值禁宽容（F10-11 fail-loud），故此处以 null 走"缺失"面而非塞非 JSON 串
        run.setBudgetJson(null);
        when(runDao.findById("r1")).thenReturn(Optional.of(run));

        // 崩溃前已落库的 succeeded 集合：只 s1
        when(stepDao.findByRunIdAndStatus("r1", WAgentStatus.StepStatus.SUCCEEDED.value()))
                .thenReturn(List.of(succeededRow("s1")));
        when(stepWriter.nextAttempt(anyString(), anyString())).thenReturn(1);
        when(stepWriter.canonicalJson(any())).thenReturn("{\"v\":1}");
        when(stepExecutor.parseConsumption(any())).thenReturn(Consumption.zero());
        when(stepExecutor.execute(any(), any(), anyInt(), any(), anyString()))
                .thenReturn(StepExecutor.StepResult.succeeded(Map.of("v", 1), null));

        // budget 快照缺失 ⇒ 冻结默认路径（上面 setBudgetJson(null)）
        WAgentOrchestrator orchestrator = new WAgentOrchestrator(runDao, stepDao,
                mock(WAgentRunEventDao.class), questionService,
                new WAgentBudgetService(mock(org.springframework.beans.factory.ObjectProvider.class)),
                new WAgentRunStateMachine(runDao), mock(PlanBuilder.class), mock(PlanValidator.class),
                stepExecutor, stepWriter, mock(WAgentEventPublisher.class));

        WAgentRunDO out = orchestrator.resume("r1", "u1");

        ArgumentCaptor<WAgentPlan.Step> executed = ArgumentCaptor.forClass(WAgentPlan.Step.class);
        verify(stepExecutor, times(2)).execute(eq(run), executed.capture(), anyInt(), any(), eq("u1"));
        assertEquals(List.of("s2", "s3"), executed.getAllValues().stream()
                .map(WAgentPlan.Step::stepKey).toList(), "已 succeeded 的 s1 禁重跑（恢复只续未完成步）");
        assertEquals(RunStatus.COMPLETED.value(), out.getStatus());
        assertFalse(run.getPlanJson().isBlank(), "计划快照仍在库行上（无状态恢复的读面依据）");
        verify(questionService).markAnswered(eq("q1"), any());
    }
}
