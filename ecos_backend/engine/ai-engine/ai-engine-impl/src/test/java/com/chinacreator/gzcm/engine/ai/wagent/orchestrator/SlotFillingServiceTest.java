package com.chinacreator.gzcm.engine.ai.wagent.orchestrator;

import com.chinacreator.gzcm.engine.ai.wagent.dao.WAgentQuestionDao;
import com.chinacreator.gzcm.engine.ai.wagent.dataobject.WAgentGoalDO;
import com.chinacreator.gzcm.engine.ai.wagent.dataobject.WAgentQuestionDO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 槽位澄清配额与全推断提醒（详细设计-10 F10-06，冻结验收名）。
 *
 * <p>一轮 ≤3 项裁剪 + 连续两轮无新增信息 ⇒ {@code awaiting_input} 挂起；
 * 全槽位 {@code AGENT_INFERRED} ⇒ 提醒信号（AC-02 数据基）。纯单测：DAO 全 mock。</p>
 */
class SlotFillingServiceTest {

    private static WAgentQuestionDO question(String id) {
        WAgentQuestionDO q = new WAgentQuestionDO();
        q.setId(id);
        return q;
    }

    private static WAgentGoalDO goalWithSlots(String slotsSourceJson) {
        WAgentGoalDO g = new WAgentGoalDO();
        g.setId("g1");
        g.setSlotsSourceJson(slotsSourceJson);
        return g;
    }

    @Test
    @DisplayName("F10-06：澄清一轮最多三项（超出裁剪留待下轮）；连续两轮无新增信息 ⇒ awaiting_input 挂起")
    void clarificationRoundCappedAtThree() {
        WAgentQuestionDao questionDao = mock(WAgentQuestionDao.class);
        SlotFillingService service = new SlotFillingService(questionDao);

        // 一轮 ≤3：五项缺失只出前三（原序裁剪，禁一次抛满屏问题）
        List<String> missing = List.of("metric", "as_of_date", "scope", "granularity", "baseline");
        List<String> round = service.nextClarificationItems(missing);
        assertEquals(SlotFillingService.MAX_CLARIFICATION_ITEMS_PER_ROUND, round.size());
        assertEquals(List.of("metric", "as_of_date", "scope"), round);
        assertEquals(List.of(), service.nextClarificationItems(List.of()));
        assertEquals(List.of(), service.nextClarificationItems(null));

        // 连续两轮无新增信息 ⇒ 挂起：同一槽位键集合指纹连续命中才计数
        WAgentQuestionDO q = question("q1");
        WAgentQuestionDO stored = question("q1");
        stored.setStatus("clarifying");
        when(questionDao.findById(anyString())).thenReturn(Optional.of(stored));
        when(questionDao.update(stored)).thenReturn(1);

        Set<String> same = Set.of("metric", "as_of_date");
        assertFalse(service.recordClarifyRound(q, same), "首轮无基线，不计挂起");
        assertFalse(service.recordClarifyRound(q, same), "第二轮无新增（streak=1 < 2）");
        assertTrue(service.recordClarifyRound(q, same), "连续两轮无新增信息 ⇒ 挂起");

        ArgumentCaptor<WAgentQuestionDO> cap = ArgumentCaptor.forClass(WAgentQuestionDO.class);
        verify(questionDao).update(cap.capture());
        assertEquals(WAgentStatus.QuestionStatus.AWAITING_INPUT.value(), cap.getValue().getStatus());

        // 新增信息 ⇒ streak 清零重新计数（另一问题实例验证不串扰）
        WAgentQuestionDO q2 = question("q2");
        assertFalse(service.recordClarifyRound(q2, Set.of("metric")));
        assertFalse(service.recordClarifyRound(q2, Set.of("metric", "scope")), "有新增信息 ⇒ 不累积");
        assertFalse(service.recordClarifyRound(q2, Set.of("metric", "scope")), "streak=1 仍不挂起");
        assertTrue(service.recordClarifyRound(q2, Set.of("metric", "scope")), "streak=2 ⇒ 挂起");
    }

    @Test
    @DisplayName("F10-06/AC-02：全部槽位为推断 ⇒ 提醒；混入显式来源或空 slots_source ⇒ 不提醒（缺项走 fail-loud）")
    void allInferredSlotsRaiseWarning() {
        WAgentQuestionDao questionDao = mock(WAgentQuestionDao.class);
        SlotFillingService service = new SlotFillingService(questionDao);

        WAgentGoalDO allInferred = goalWithSlots(
                "{\"metric\":\"AGENT_INFERRED\",\"as_of_date\":\"AGENT_INFERRED\"}");
        assertTrue(service.raisesAllInferredWarning(allInferred));
        assertTrue(service.allInferredWarning(allInferred).isPresent());
        assertEquals(SlotFillingService.WARNING_ALL_SLOTS_INFERRED,
                service.allInferredWarning(allInferred).get());

        WAgentGoalDO mixed = goalWithSlots(
                "{\"metric\":\"USER_EXPLICIT\",\"as_of_date\":\"AGENT_INFERRED\"}");
        assertFalse(service.raisesAllInferredWarning(mixed));
        assertFalse(service.allInferredWarning(mixed).isPresent());

        // 空/脏 slots_source ⇒ 不提醒（禁宽容把缺项当全推断）
        assertFalse(service.raisesAllInferredWarning(goalWithSlots(null)));
        assertFalse(service.raisesAllInferredWarning(goalWithSlots("  ")));
        assertFalse(service.raisesAllInferredWarning(goalWithSlots("{not-json")));

        verify(questionDao, never()).update((WAgentQuestionDO) org.mockito.ArgumentMatchers.any());
    }
}
