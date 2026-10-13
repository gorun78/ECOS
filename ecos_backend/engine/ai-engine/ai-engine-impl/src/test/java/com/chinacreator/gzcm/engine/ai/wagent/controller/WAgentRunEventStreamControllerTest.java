package com.chinacreator.gzcm.engine.ai.wagent.controller;

import com.chinacreator.gzcm.engine.ai.wagent.WAgentEnums.RunStatus;
import com.chinacreator.gzcm.engine.ai.wagent.dao.WAgentRunDao;
import com.chinacreator.gzcm.engine.ai.wagent.dao.WAgentRunEventDao;
import com.chinacreator.gzcm.engine.ai.wagent.dataobject.WAgentRunDO;
import com.chinacreator.gzcm.engine.ai.wagent.dataobject.WAgentRunEventDO;
import com.chinacreator.gzcm.engine.ai.wagent.web.WAgentIdentityResolver;
import com.chinacreator.gzcm.engine.ai.wagent.web.WAgentIdentityResolver.Identity;
import com.chinacreator.gzcm.engine.ai.wagent.web.WAgentOrchestrationAdvice;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** #7 新增 SSE 映射（.../events/stream）：fail-fast 404 + 流建立 + 续传/终态判定纯函数。 */
class WAgentRunEventStreamControllerTest {

    private WAgentRunDao runDao;
    private WAgentRunEventDao eventDao;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        runDao = mock(WAgentRunDao.class);
        eventDao = mock(WAgentRunEventDao.class);
        WAgentIdentityResolver identityResolver = mock(WAgentIdentityResolver.class);
        when(identityResolver.require()).thenReturn(new Identity("t1", "u1", "o1"));

        mockMvc = MockMvcBuilders
                .standaloneSetup(new WAgentRunEventStreamController(runDao, eventDao, identityResolver, Runnable::run))
                .setControllerAdvice(new WAgentOrchestrationAdvice())
                .setMessageConverters(new MappingJackson2HttpMessageConverter())
                .build();
    }

    @Test
    @DisplayName("#7-SSE 未知/跨租户 run ⇒ 同步段 404 E-NOT-FOUND（fail-fast，禁空流 200）")
    void unknownRunFailsFast404() throws Exception {
        when(runDao.findById("ghost")).thenReturn(Optional.empty());
        MvcResult r = mockMvc.perform(get("/api/v1/wagent/runs/ghost/events/stream")).andReturn();
        assertEquals(404, r.getResponse().getStatus());
        assertEquals("E-NOT-FOUND", new ObjectMapper().readTree(
                r.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("errorCode").asText());
    }

    @Test
    @DisplayName("#7-SSE 在位 run ⇒ 同步 200 流建立（既有 #7 JSON 面不动，本条为新增映射；standalone MockMvc 不回写 SSE Content-Type，故只断言状态码，帧语义由 resumeIndex/isTerminal 用例覆盖）")
    void streamEstablished() throws Exception {
        WAgentRunDO run = new WAgentRunDO();
        run.setId("r1");
        run.setTenantId("t1");
        run.setStatus(RunStatus.COMPLETED.value()); // 终态 ⇒ 泵自然收流，测试不滞留后台轮询
        when(runDao.findById("r1")).thenReturn(Optional.of(run));
        when(eventDao.findByRunId("r1")).thenReturn(List.of(event("e1")));

        MvcResult r = mockMvc.perform(get("/api/v1/wagent/runs/r1/events/stream"))
                .andExpect(status().isOk())
                .andReturn();
        // 异步泵线程自行收流（run_terminal ⇒ complete），此处不阻塞等待帧内容
        assertEquals(200, r.getResponse().getStatus());
    }

    @Test
    @DisplayName("Last-Event-ID 续传定位：命中 id 取后序；未知 id 全量回放（只读无害）")
    void resumeIndexSemantics() {
        WAgentRunEventDO e1 = event("e1");
        WAgentRunEventDO e2 = event("e2");
        List<WAgentRunEventDO> events = List.of(e1, e2);
        assertEquals(0, WAgentRunEventStreamController.resumeIndex(events, null));
        assertEquals(2, WAgentRunEventStreamController.resumeIndex(events, "e2"));
        assertEquals(0, WAgentRunEventStreamController.resumeIndex(events, "forged-id"));
    }

    @Test
    @DisplayName("五终态单源（completed/completed_degraded/failed/cancelled/timed_out）")
    void terminalStatuses() {
        assertTrue(WAgentRunEventStreamController.isTerminal("completed"));
        assertTrue(WAgentRunEventStreamController.isTerminal("timed_out"));
        assertFalse(WAgentRunEventStreamController.isTerminal("executing"));
        assertFalse(WAgentRunEventStreamController.isTerminal("unknown-status"));
    }

    private static WAgentRunEventDO event(String id) {
        WAgentRunEventDO e = new WAgentRunEventDO();
        e.setId(id);
        e.setRunId("r1");
        e.setEventType("step_started");
        e.setOccurTime(LocalDateTime.now());
        return e;
    }
}
