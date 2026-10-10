package com.chinacreator.gzcm.engine.ai.wagent.orchestrator;

import com.chinacreator.gzcm.engine.ai.wagent.WAgentEnums.RunStatus;
import com.chinacreator.gzcm.engine.ai.wagent.dao.WAgentRunDao;
import com.chinacreator.gzcm.engine.ai.wagent.dao.WAgentRunEventDao;
import com.chinacreator.gzcm.engine.ai.wagent.dao.WAgentRunStepDao;
import com.chinacreator.gzcm.engine.ai.wagent.dataobject.WAgentQuestionDO;
import com.chinacreator.gzcm.engine.ai.wagent.dataobject.WAgentRunDO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 幂等键冲突语义（详细设计-10 F10-11 / §4.4，冻结验收名）。
 *
 * <p>口径：UNIQUE(tenant_id, initiated_by, idempotency_key) + 应用侧 sha256 先查后插；
 * 命中同键 <b>返回既有 run_id</b>（禁新建第二 Run、禁重复回写 Question 状态）。
 * 全 mock 切片，禁真库真引擎。</p>
 */
class RunIdempotencyTest {

    @Test
    @DisplayName("F10-11/§4.4：同幂等键二次请求 ⇒ 命中既有 run_id，不新建、不回写 Question")
    void duplicateKeyReturnsSameRun() {
        WAgentRunDao runDao = mock(WAgentRunDao.class);
        WAgentQuestionService questionService = mock(WAgentQuestionService.class);

        // §4.4 散列形态冻结：sha256(tenant|initiated_by|raw_key)，分隔符防拼接歧义
        String hashed = WAgentHashSupport.idempotencyHash("t1", "u1", "key-1");
        assertEquals(64, hashed.length(), "sha256 hex 恰容 idempotency_key 列宽");
        assertEquals(WAgentHashSupport.sha256Hex("t1|u1|key-1"), hashed);
        assertNotEquals(hashed, WAgentHashSupport.idempotencyHash("t1", "u", "1key-1"),
                "三段分隔符必须消歧（禁拼接同键）");

        WAgentRunDO existing = new WAgentRunDO();
        existing.setId("r-existing");
        existing.setTenantId("t1");
        existing.setInitiatedBy("u1");
        existing.setStatus(RunStatus.EXECUTING.value());
        when(runDao.findByIdempotencyKey("t1", "u1", hashed)).thenReturn(Optional.of(existing));

        WAgentQuestionDO question = new WAgentQuestionDO();
        question.setId("q1");
        question.setTenantId("t1");
        question.setStatus(WAgentStatus.QuestionStatus.OPEN.value());
        question.setIntent("FORECAST");
        question.setIntentConfidence(new BigDecimal("0.90"));
        when(questionService.get("q1", "t1")).thenReturn(question);

        WAgentOrchestrator orchestrator = new WAgentOrchestrator(runDao,
                mock(WAgentRunStepDao.class), mock(WAgentRunEventDao.class), questionService,
                mock(WAgentBudgetService.class), mock(WAgentRunStateMachine.class),
                mock(PlanBuilder.class), mock(PlanValidator.class), mock(StepExecutor.class),
                mock(StepWriter.class), mock(WAgentEventPublisher.class));

        WAgentOrchestrator.RunStartOutcome outcome = orchestrator.startRun(
                new WAgentOrchestrator.RunStartCommand("t1", "o1", "u1", "q1", "m1",
                        "key-1", null, null, null, null));

        assertTrue(outcome.idempotentReplay(), "命中即幂等重放（§4.4 口径）");
        assertEquals("r-existing", outcome.run().getId(), "返回既有 run_id，禁新建");
        verify(runDao, never()).insert(any());
        verify(questionService, never()).markRunning(anyString(), anyString());
    }
}
