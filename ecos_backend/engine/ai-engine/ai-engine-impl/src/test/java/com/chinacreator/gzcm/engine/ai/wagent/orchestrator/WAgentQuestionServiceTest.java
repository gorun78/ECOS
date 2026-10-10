package com.chinacreator.gzcm.engine.ai.wagent.orchestrator;

import com.chinacreator.gzcm.engine.ai.wagent.WAgentErrorCodes.WAgentErrorCode;
import com.chinacreator.gzcm.engine.ai.wagent.dao.WAgentQuestionDao;
import com.chinacreator.gzcm.engine.ai.wagent.dataobject.WAgentQuestionDO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Question 服务（详细设计-10 F10-05 边界，冻结验收名）：
 * depends_on 环路应用层 DFS 检测（禁依赖 DB 触发器），检出即 {@code E-VALIDATION} 拒绝。
 */
class WAgentQuestionServiceTest {

    @Test
    @DisplayName("F10-05：depends_on 成环（q1→q2→q1）⇒ 应用层拓扑检测拒绝，问题行不落库")
    void cyclicDependsOnRejected() {
        WAgentQuestionDao dao = mock(WAgentQuestionDao.class);
        WAgentQuestionDO q1 = new WAgentQuestionDO();
        q1.setId("q1");
        q1.setDependsOnJson("[\"q2\"]");
        WAgentQuestionDO q2 = new WAgentQuestionDO();
        q2.setId("q2");
        q2.setDependsOnJson("[\"q1\"]");
        when(dao.findById("q1")).thenReturn(Optional.of(q1));
        when(dao.findById("q2")).thenReturn(Optional.of(q2));

        // 分类通道可用且高置信：唯一拒绝理由必须是环路检测（排除置信度干扰）
        IntentClassifyPort ready = rawText ->
                IntentClassifyPort.Verdict.of("DIAGNOSE", new BigDecimal("0.95"));
        WAgentQuestionService service =
                new WAgentQuestionService(dao, ready, mock(SlotFillingService.class));

        WAgentOrchestrationException e = assertThrows(WAgentOrchestrationException.class,
                () -> service.fromUtterance(new WAgentQuestionService.QuestionCreateCommand(
                        "t1", "o1", "u1", "m1", "分析成本异动", Map.of(), List.of("q1"))));
        assertEquals(WAgentErrorCode.E_VALIDATION, e.errorCode());
        assertTrue(e.getMessage().contains("环路"), "失败信息须可定位（禁裸 400）: " + e.getMessage());
        verify(dao, never()).insert(any());
    }
}
