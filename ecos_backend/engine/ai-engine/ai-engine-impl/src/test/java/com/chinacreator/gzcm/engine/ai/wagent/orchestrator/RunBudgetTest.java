package com.chinacreator.gzcm.engine.ai.wagent.orchestrator;

import com.chinacreator.gzcm.engine.ai.wagent.WAgentEnums.PlanSource;
import com.chinacreator.gzcm.engine.ai.wagent.WAgentEnums.RunStatus;
import com.chinacreator.gzcm.engine.ai.wagent.WAgentEnums.StepType;
import com.chinacreator.gzcm.engine.ai.wagent.WAgentErrorCodes.WAgentErrorCode;
import com.chinacreator.gzcm.engine.ai.wagent.dao.WAgentRunDao;
import com.chinacreator.gzcm.engine.ai.wagent.dao.WAgentRunEventDao;
import com.chinacreator.gzcm.engine.ai.wagent.dao.WAgentRunStepDao;
import com.chinacreator.gzcm.engine.ai.wagent.dataobject.WAgentRunDO;
import com.chinacreator.gzcm.engine.ai.wagent.dataobject.WAgentRunStepDO;
import com.chinacreator.gzcm.sysman.config.service.impl.SysConfigService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Step 预算终止（详细设计-10 F10-11 / §4.4 预算六键冻结，冻结验收名）。
 *
 * <p>预算数字单源 = 代码冻结默认（V265 {@code wagent.budget} 组同值）∧ sysman 可下调；
 * 消耗判定是纯整数比较（<b>数字不来自 LLM</b>）。越限即 {@code E-WA-BUDGET}，
 * Run 先落 {@code failed} 终态（状态唯一写入口=状态机）再抛，禁裸 500。</p>
 */
class RunBudgetTest {

    @SuppressWarnings("unchecked")
    private static ObjectProvider<SysConfigService> provider(SysConfigService svc) {
        ObjectProvider<SysConfigService> p = mock(ObjectProvider.class);
        when(p.getIfAvailable()).thenReturn(svc);
        return p;
    }

    private static WAgentPlan.Step tool(String key, List<String> dependsOn) {
        return new WAgentPlan.Step(key, StepType.TOOL.value(), "tool_a", "1", 0, dependsOn,
                Map.of(), false, false, null);
    }

    @Test
    @DisplayName("F10-11：消耗达 60 Step（冻结 max_steps）⇒ Run 落 failed + E-WA-BUDGET，禁再执行下一步")
    void stepCountOver60Terminates() {
        WAgentRunDao runDao = mock(WAgentRunDao.class);
        WAgentRunStepDao stepDao = mock(WAgentRunStepDao.class);
        WAgentRunEventDao runEventDao = mock(WAgentRunEventDao.class);
        StepExecutor stepExecutor = mock(StepExecutor.class);
        StepWriter stepWriter = mock(StepWriter.class);

        // 预算默认值实测（sysman 未装配 ⇒ 回退冻结默认；装配面下调由 effective(min) 覆盖）
        WAgentBudgetService budgetService = new WAgentBudgetService(provider(null));
        WAgentBudgetService.BudgetSnapshot snapshot = budgetService.capture();
        assertEquals(WAgentBudgetService.FREEZE_MAX_STEPS, snapshot.maxSteps(), "60 单源（V265 同值）");
        assertEquals(40, snapshot.maxLlmCalls());
        assertEquals(60, snapshot.syncTimeoutSeconds());
        assertEquals(30, snapshot.asyncTimeoutMinutes());
        assertEquals(3, snapshot.maxReplanRounds());
        assertEquals(3, snapshot.maxRefillRounds());
        assertTrue(budgetService.parseSnapshot(budgetService.toSnapshotJson(snapshot)).stepLimitReached(
                new WAgentBudgetService.Consumption(WAgentBudgetService.FREEZE_MAX_STEPS, 0, 0, 0)),
                "budget_json 写出/读回键名单源同名（往返一致）");

        WAgentRunDO run = new WAgentRunDO();
        run.setId("r1");
        run.setTenantId("t1");
        run.setOrgId("o1");
        run.setQuestionId("q1");
        run.setMissionId("m1");
        run.setInitiatedBy("u1");
        run.setAutomationCeiling(1);
        run.setStatus(RunStatus.EXECUTING.value());
        run.setPlanSource(PlanSource.PLAYBOOK.value());
        run.setPlanJson(WAgentPlan.PlanCodec.toJson(new WAgentPlan.Plan(PlanSource.PLAYBOOK,
                "playbooks/profit.git", "1", List.of(tool("s1", List.of()), tool("s2", List.of("s1"))))));
        run.setBudgetJson(budgetService.toSnapshotJson(snapshot));
        run.setConsumptionJson("{\"steps\":60,\"llm_calls\":3,\"replan_rounds\":0,\"refill_rounds\":0}");
        when(runDao.findById("r1")).thenReturn(Optional.of(run));
        when(stepExecutor.parseConsumption(anyString()))
                .thenReturn(new WAgentBudgetService.Consumption(WAgentBudgetService.FREEZE_MAX_STEPS, 0, 0, 0));
        when(stepDao.findByRunIdAndStatus(eq("r1"), anyString())).thenReturn(List.of());

        WAgentOrchestrator orchestrator = new WAgentOrchestrator(runDao, stepDao, runEventDao,
                mock(WAgentQuestionService.class), budgetService,
                new WAgentRunStateMachine(runDao), mock(PlanBuilder.class), mock(PlanValidator.class),
                stepExecutor, stepWriter, mock(WAgentEventPublisher.class));

        WAgentOrchestrationException e = assertThrows(WAgentOrchestrationException.class,
                () -> orchestrator.runPipeline("r1", "u1"));
        assertEquals(WAgentErrorCode.E_WA_BUDGET, e.errorCode());
        assertEquals(429, e.errorCode().httpStatus(), "§5.3：预算耗尽 429 且不可重试");
        assertEquals(RunStatus.FAILED.value(), run.getStatus(), "先落终态再抛（禁状态与响应背离）");
        verify(runDao).updateStatus(eq("r1"), eq(RunStatus.FAILED.value()), eq("u1"));
        verify(stepExecutor, never()).execute(any(), any(), org.mockito.ArgumentMatchers.anyInt(),
                any(), anyString());
        verify(stepWriter, never()).nextAttempt(anyString(), anyString());
    }
}
