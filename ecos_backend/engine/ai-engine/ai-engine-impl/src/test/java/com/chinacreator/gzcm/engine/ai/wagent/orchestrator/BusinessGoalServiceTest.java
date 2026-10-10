package com.chinacreator.gzcm.engine.ai.wagent.orchestrator;

import com.chinacreator.gzcm.engine.ai.wagent.WAgentErrorCodes.WAgentErrorCode;
import com.chinacreator.gzcm.engine.ai.wagent.dao.WAgentGoalDao;
import com.chinacreator.gzcm.engine.ai.wagent.dao.WAgentMissionDao;
import com.chinacreator.gzcm.engine.ai.wagent.dao.WAgentRunDao;
import com.chinacreator.gzcm.engine.ai.wagent.dataobject.WAgentGoalDO;
import com.chinacreator.gzcm.engine.ai.wagent.dataobject.WAgentMissionDO;
import com.chinacreator.gzcm.engine.ai.wagent.dataobject.WAgentRunDO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 业务目标口径锁（详细设计-10 F10-07 / REQ-WAG-07，冻结验收名）。
 *
 * <p>两半各证：①口径修订 ⇒ 只写新版本行（supersedes 链 + version_no 确定性递增），
 * 前代行零改动（update 禁调用）；②已被 Run 引用（Goal→Mission→Run 链）的 Goal
 * 就地更新 ⇒ {@code ECOS_WA_CALIBER_LOCKED} 拒绝。</p>
 */
class BusinessGoalServiceTest {

    private static WAgentGoalDO persistedGoal() {
        WAgentGoalDO g = new WAgentGoalDO();
        g.setId("g1");
        g.setName("利润口径基线");
        g.setGoalType("FORECAST");
        g.setCaliberVersion("c1");
        g.setMetricId("m-profit");
        g.setMetricVersion("v1");
        g.setAsOfDate(LocalDate.of(2026, 9, 30));
        g.setStatus(WAgentStatus.GoalStatus.DRAFT.value());
        g.setVersionNo("1");
        g.setInferredCount(0);
        g.setTenantId("t1");
        g.setOrgId("o1");
        return g;
    }

    @Test
    @DisplayName("F10-07：口径变更 ⇒ 新版本行（supersedes=g1、version=2），前代零改动；被引用就地改 ⇒ 409 口径锁")
    void caliberChangeCreatesNewVersionNotInPlaceEdit() {
        WAgentGoalDao goalDao = mock(WAgentGoalDao.class);
        WAgentMissionDao missionDao = mock(WAgentMissionDao.class);
        WAgentRunDao runDao = mock(WAgentRunDao.class);
        WAgentEventPublisher publisher = mock(WAgentEventPublisher.class);
        when(goalDao.findById("g1")).thenReturn(Optional.of(persistedGoal()));
        WAgentGoalService service = new WAgentGoalService(goalDao, missionDao, runDao, publisher);

        // ① 口径修订 ⇒ 新版本链
        WAgentGoalDO next = service.revise("g1", Map.of("caliberVersion", "c2"), "u1", "t1");
        ArgumentCaptor<WAgentGoalDO> inserted = ArgumentCaptor.forClass(WAgentGoalDO.class);
        verify(goalDao).insert(inserted.capture());
        assertEquals("g1", inserted.getValue().getSupersedesGoalId());
        assertEquals("2", inserted.getValue().getVersionNo());
        assertEquals("c2", inserted.getValue().getCaliberVersion());
        assertSame(next, inserted.getValue(), "返回值即新插入的版本行");
        assertNotEquals("g1", inserted.getValue().getId(), "新版本必须新行 id");
        // 未提供字段继承前代（确定性拷贝，非 LLM）
        assertEquals("m-profit", inserted.getValue().getMetricId());
        assertEquals(LocalDate.of(2026, 9, 30), inserted.getValue().getAsOfDate());
        // 前代行禁被改动
        verify(goalDao, never()).update(any());

        // ② 被 Run 引用 ⇒ 就地更新拒绝（ECOS_WA_CALIBER_LOCKED）
        WAgentMissionDO mission = new WAgentMissionDO();
        mission.setId("mi1");
        when(missionDao.findByGoalId("g1")).thenReturn(List.of(mission));
        WAgentRunDO run = new WAgentRunDO();
        run.setId("r1");
        when(runDao.findByMissionId("mi1")).thenReturn(List.of(run));

        WAgentOrchestrationException e = assertThrows(WAgentOrchestrationException.class,
                () -> service.updateInPlace("g1", Map.of("name", "改名"), "u1"));
        assertEquals(WAgentErrorCode.ECOS_WA_CALIBER_LOCKED, e.errorCode());
        assertEquals("g1", e.extras().get("goalId"));
    }
}
