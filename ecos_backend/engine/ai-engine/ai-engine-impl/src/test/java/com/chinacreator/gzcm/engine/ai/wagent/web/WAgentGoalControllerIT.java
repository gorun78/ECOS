package com.chinacreator.gzcm.engine.ai.wagent.web;

import com.chinacreator.gzcm.engine.ai.wagent.controller.WAgentGoalController;
import com.chinacreator.gzcm.engine.ai.wagent.dao.WAgentGoalDao;
import com.chinacreator.gzcm.engine.ai.wagent.dao.WAgentMissionDao;
import com.chinacreator.gzcm.engine.ai.wagent.dao.WAgentQuestionDao;
import com.chinacreator.gzcm.engine.ai.wagent.dao.WAgentRunDao;
import com.chinacreator.gzcm.engine.ai.wagent.dataobject.WAgentGoalDO;
import com.chinacreator.gzcm.engine.ai.wagent.dataobject.WAgentMissionDO;
import com.chinacreator.gzcm.engine.ai.wagent.dataobject.WAgentRunDO;
import com.chinacreator.gzcm.engine.ai.wagent.orchestrator.SlotFillingService;
import com.chinacreator.gzcm.engine.ai.wagent.orchestrator.WAgentEventPublisher;
import com.chinacreator.gzcm.engine.ai.wagent.orchestrator.WAgentGoalService;
import com.chinacreator.gzcm.engine.ai.wagent.web.WAgentIdentityResolver.Identity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Goal PATCH 端点 409 出口（详细设计-10 F10-07/§5.3，冻结验收名）。
 *
 * <p>MockMvc standalone（不拉真实引擎上下文）：非口径字段 PATCH ⇒ updateInPlace 面，
 * Goal 已被 Run 引用（Goal→Mission→Run 探测链命中）⇒ 409
 * {@code ECOS_WA_CALIBER_LOCKED}，extras 经 Advice 落 {@code data.goalId}，历史 Goal 零改写。</p>
 */
class WAgentGoalControllerIT {

    private WAgentGoalDao goalDao;
    private WAgentMissionDao missionDao;
    private WAgentRunDao runDao;
    private WAgentIdentityResolver identityResolver;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        goalDao = mock(WAgentGoalDao.class);
        missionDao = mock(WAgentMissionDao.class);
        runDao = mock(WAgentRunDao.class);
        WAgentEventPublisher publisher = mock(WAgentEventPublisher.class);

        WAgentGoalDO goal = new WAgentGoalDO();
        goal.setId("g1");
        goal.setTenantId("t1");
        goal.setName("原目标");
        goal.setGoalType("forecast");
        goal.setStatus("draft");
        goal.setVersionNo("1");
        when(goalDao.findById("g1")).thenReturn(Optional.of(goal));

        // 引用链命中：g1 → mission mi1 → run r1（存在即锁口径）
        WAgentMissionDO mission = new WAgentMissionDO();
        mission.setId("mi1");
        when(missionDao.findByGoalId("g1")).thenReturn(List.of(mission));
        when(runDao.findByMissionId("mi1")).thenReturn(List.of(new WAgentRunDO()));

        WAgentGoalService goalService =
                new WAgentGoalService(goalDao, missionDao, runDao, publisher);
        SlotFillingService slotFillingService =
                new SlotFillingService(mock(WAgentQuestionDao.class));

        identityResolver = mock(WAgentIdentityResolver.class);
        when(identityResolver.require()).thenReturn(new Identity("t1", "u1", "o1"));

        mockMvc = MockMvcBuilders
                .standaloneSetup(new WAgentGoalController(goalService, slotFillingService, identityResolver))
                .setControllerAdvice(new WAgentOrchestrationAdvice())
                // standalone 默认转换器集含 JAXB XML，会让 ApiResponse 序列化成 XML；
                // 契约为 JSON ⇒ 只留 Jackson
                .setMessageConverters(new MappingJackson2HttpMessageConverter())
                .build();
    }

    @Test
    @DisplayName("F10-07/§5.3：已被 Run 引用的 Goal 收到非口径字段 PATCH ⇒ 409 ECOS_WA_CALIBER_LOCKED（data.goalId 定位，禁就地改写）")
    void updateLockedGoalReturns409() throws Exception {
        // 纯运营字段（successCriteriaText 不在 CALIBER_FIELDS）⇒ updateInPlace 面 ⇒ 被引用即锁
        // 本模块 standalone MockMvc 的既有约定：不用 jayway jsonPath（JsonSmartJsonProvider
        // 会把 body 读成 String 而抛 PathNotFoundException）；且 setUp 已按 JSON 契约
        // 收敛为 Jackson 单转换器（默认 converter 集含 JAXB 时会把 body 序列化成 XML）
        MvcResult result = mockMvc.perform(patch("/api/v1/wagent/goals/g1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"successCriteriaText\":\"新的成功标准\"}"))
                .andExpect(status().isConflict())
                .andReturn();

        JsonNode body = new ObjectMapper().readTree(
                result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertEquals(409, body.path("code").asInt(), "响应体: " + body);
        assertEquals("ECOS_WA_CALIBER_LOCKED", body.path("errorCode").asText(), "响应体: " + body);
        assertEquals("g1", body.path("data").path("goalId").asText(), "响应体: " + body);
        assertEquals(false, body.path("message").asText().isEmpty(), "响应体: " + body);

        verify(goalDao, never()).update(any(WAgentGoalDO.class)); // 锁定面零改写
        verify(goalDao, never()).insert(any(WAgentGoalDO.class)); // 409 也不得偷走新版本链旁路
    }
}
