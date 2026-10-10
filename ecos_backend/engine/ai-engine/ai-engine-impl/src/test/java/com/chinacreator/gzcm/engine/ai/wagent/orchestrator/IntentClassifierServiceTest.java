package com.chinacreator.gzcm.engine.ai.wagent.orchestrator;

import com.chinacreator.gzcm.engine.ai.wagent.WAgentErrorCodes.WAgentErrorCode;
import com.chinacreator.gzcm.engine.ai.wagent.dao.WAgentQuestionDao;
import com.chinacreator.gzcm.engine.ai.wagent.dataobject.WAgentQuestionDO;
import com.chinacreator.gzcm.runtime.llm.gateway.ChatRequest;
import com.chinacreator.gzcm.runtime.llm.gateway.ChatResponse;
import com.chinacreator.gzcm.runtime.llm.gateway.LLMGateway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 意图分类准入闸（详细设计-10 F10-05 / REQ-WAG-05，冻结验收名）。
 *
 * <p>纯单元面：LLMGateway 全 mock（禁真引擎出网），断言两条失败路径——
 * 置信 &lt; 0.70 与分类器不可用——都必须阻断建 Run（{@code assertRunEligible}
 * 抛 {@code E-VALIDATION}），后者还须落 clarifying（禁 fail-open 造 Run）。</p>
 */
class IntentClassifierServiceTest {

    @SuppressWarnings("unchecked")
    private static IntentClassifierService serviceWith(LLMGateway gateway) {
        ObjectProvider<LLMGateway> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(gateway);
        return new IntentClassifierService(provider);
    }

    @Test
    @DisplayName("F10-05：置信 0.55 < 0.70 ⇒ readyForRun=false 且 assertRunEligible 拒绝建 Run")
    void belowThresholdRejectsRunCreation() {
        LLMGateway gateway = mock(LLMGateway.class);
        when(gateway.call(any(ChatRequest.class)))
                .thenReturn(ChatResponse.ok("INTENT=FORECAST|CONFIDENCE=0.55", 10, 5, "unit-test-model"));
        IntentClassifierService svc = serviceWith(gateway);

        IntentClassifyPort.Verdict verdict = svc.classify("预测下季度项目利润走势");
        assertTrue(verdict.classifierAvailable(), "通道可用（不是降级态）");
        assertFalse(verdict.readyForRun(), "0.55 < 0.70 禁建 Run（F10-05 冻结阈值单源）");
        assertEquals("FORECAST", verdict.intent());

        // 下游准入闸实测：低置信 Question 直接拒绝（异常即未建 Run 的等价证据）
        WAgentQuestionService questionService = new WAgentQuestionService(
                mock(WAgentQuestionDao.class), raw -> verdict, mock(SlotFillingService.class));
        WAgentQuestionDO lowConf = new WAgentQuestionDO();
        lowConf.setId("q1");
        lowConf.setStatus(WAgentStatus.QuestionStatus.OPEN.value());
        lowConf.setIntent("FORECAST");
        lowConf.setIntentConfidence(new BigDecimal("0.55"));
        WAgentOrchestrationException e = assertThrows(WAgentOrchestrationException.class,
                () -> questionService.assertRunEligible(lowConf));
        assertEquals(WAgentErrorCode.E_VALIDATION, e.errorCode());
    }

    @Test
    @DisplayName("F10-05：分类器不可用 ⇒ clarify（intent 置空、状态 clarifying），禁 fail-open 建 Run")
    void classifierUnavailableYieldsClarifyNotRun() {
        IntentClassifierService svc = serviceWith(null); // 网关未装配 = 不可用通道
        IntentClassifyPort.Verdict verdict = svc.classify("随便说点什么");
        assertFalse(verdict.classifierAvailable());
        assertFalse(verdict.readyForRun());

        WAgentQuestionDao questionDao = mock(WAgentQuestionDao.class);
        SlotFillingService slotFilling = mock(SlotFillingService.class);
        WAgentQuestionService questionService =
                new WAgentQuestionService(questionDao, svc, slotFilling);
        WAgentQuestionService.QuestionOutcome outcome = questionService.fromUtterance(
                new WAgentQuestionService.QuestionCreateCommand("t1", "o1", "u1", "m1",
                        "随便说点什么", Map.of(), null));

        assertTrue(outcome.requiresClarify(), "不可用 ⇒ 保守 clarify（禁默认猜测）");
        assertEquals(WAgentStatus.QuestionStatus.CLARIFYING.value(), outcome.question().getStatus());
        assertNull(outcome.question().getIntent(), "clarify 态不得携带意图（防下游误准入）");
        WAgentOrchestrationException e = assertThrows(WAgentOrchestrationException.class,
                () -> questionService.assertRunEligible(outcome.question()));
        assertEquals(WAgentErrorCode.E_VALIDATION, e.errorCode());
        // 澄清配额计入（F10-06 联动）
        org.mockito.Mockito.verify(slotFilling).recordClarifyRound(any(), any());
    }
}
