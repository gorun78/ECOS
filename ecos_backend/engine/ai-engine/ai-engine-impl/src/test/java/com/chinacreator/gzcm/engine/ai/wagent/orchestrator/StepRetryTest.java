package com.chinacreator.gzcm.engine.ai.wagent.orchestrator;

import com.chinacreator.gzcm.engine.ai.wagent.WAgentEnums.StepType;
import com.chinacreator.gzcm.engine.ai.wagent.dao.WAgentRunStepDao;
import com.chinacreator.gzcm.engine.ai.wagent.dataobject.WAgentRunDO;
import com.chinacreator.gzcm.engine.ai.wagent.dataobject.WAgentRunStepDO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Step 重试新行语义（详细设计-10 F10-14，冻结验收名）。
 *
 * <p>UNIQUE(run_id, step_key, attempt)：重试 = 以 {@link StepWriter#nextAttempt} 递增
 * attempt 写<b>新行</b>，历史行禁改写；同 (runId, stepKey, attempt) 再写 ⇒ 幂等复用既有行，
 * 禁双写。</p>
 */
class StepRetryTest {

    private static WAgentRunDO run() {
        WAgentRunDO run = new WAgentRunDO();
        run.setId("r1");
        run.setTenantId("t1");
        run.setAutomationCeiling(1);
        return run;
    }

    private static WAgentRunStepDO existing(String id, String stepKey, int attempt) {
        WAgentRunStepDO row = new WAgentRunStepDO();
        row.setId(id);
        row.setRunId("r1");
        row.setStepKey(stepKey);
        row.setAttempt(attempt);
        row.setStatus(WAgentStatus.StepStatus.FAILED.value());
        return row;
    }

    private static WAgentPlan.Step step(String key) {
        return new WAgentPlan.Step(key, StepType.TOOL.value(), "query_project_profit", "1",
                0, List.of(), Map.of(), false, false, null);
    }

    @Test
    @DisplayName("F10-14：重试写新 attempt 行（历史行零改写）；同 attempt 重复写幂等复用禁双写")
    void retryWritesNewAttemptRow() {
        WAgentRunStepDao stepDao = mock(WAgentRunStepDao.class);
        WAgentRunStepDO attempt0 = existing("row-a0", "s1", 0);
        WAgentRunStepDO attempt1 = existing("row-a1", "s1", 1);
        when(stepDao.findByRunId("r1")).thenReturn(List.of(
                attempt0, attempt1, existing("row-s2", "s2", 5))); // 他步 attempt 不得串扰
        when(stepDao.findByRunStepAttempt(anyString(), anyString(), any()))
                .thenReturn(Optional.empty());
        StepWriter writer = new StepWriter(stepDao);

        // 重试节奏：max(0,1)+1 = 2（s2@5 属别的 step_key，不参与）
        assertEquals(2, writer.nextAttempt("r1", "s1"));
        assertEquals(6, writer.nextAttempt("r1", "s2"));
        assertEquals(1, writer.nextAttempt("r1", "s-unseen")); // 无历史 ⇒ 首试=1

        // 新 attempt 落新行：id 新生、attempt=2、禁 update 历史
        when(stepDao.findByRunStepAttempt("r1", "s1", 2)).thenReturn(Optional.empty());
        WAgentRunStepDO retried = writer.write(new StepWriter.StepWriteRequest(
                run(), step("s1"), writer.nextAttempt("r1", "s1"),
                WAgentStatus.StepStatus.SUCCEEDED.value(), Map.of("k", 1), Map.of("v", 2),
                null, null, null, null, null, null, null, "u1"));

        ArgumentCaptor<WAgentRunStepDO> cap = ArgumentCaptor.forClass(WAgentRunStepDO.class);
        verify(stepDao).insert(cap.capture());
        assertEquals(2, cap.getValue().getAttempt());
        assertEquals("s1", cap.getValue().getStepKey());
        assertEquals("r1", cap.getValue().getRunId());
        assertNotEquals(attempt0.getId(), retried.getId());
        assertNotEquals(attempt1.getId(), retried.getId());
        verify(stepDao, never()).update(any(WAgentRunStepDO.class)); // 历史行禁改写

        // 幂等复用：同 (runId, stepKey, attempt) 再写 ⇒ 返回既有行、零双写
        when(stepDao.findByRunStepAttempt("r1", "s1", 1)).thenReturn(Optional.of(attempt1));
        WAgentRunStepDO reused = writer.write(new StepWriter.StepWriteRequest(
                run(), step("s1"), 1,
                WAgentStatus.StepStatus.SUCCEEDED.value(), Map.of("k", 9), null,
                null, null, null, null, null, null, null, "u1"));
        assertSame(attempt1, reused);
        verify(stepDao, org.mockito.Mockito.times(1)).insert(any(WAgentRunStepDO.class));
    }
}
