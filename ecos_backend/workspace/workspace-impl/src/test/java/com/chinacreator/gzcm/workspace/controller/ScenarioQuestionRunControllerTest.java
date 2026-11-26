package com.chinacreator.gzcm.workspace.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.chinacreator.gzcm.workspace.analysis.ScenarioQuestionRunService;
import com.chinacreator.gzcm.workspace.dao.WorkspaceDataDao;

/** 问题运行 HTTP 契约门禁：执行载体/算法入参逐字透传到 service。 */
class ScenarioQuestionRunControllerTest {

    private ScenarioQuestionRunService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(ScenarioQuestionRunService.class);
        mvc = MockMvcBuilders.standaloneSetup(
                new ScenarioQuestionRunController(service, mock(WorkspaceDataDao.class))).build();
    }

    @Test
    @DisplayName("AI_AGENT 载体 + 算法入参：controller 不改写、不丢参")
    void passesExecutorAndAlgorithmInputs() throws Exception {
        when(service.run(any(), any(), any(), anyList(), any(), any(), any(), any()))
                .thenReturn(Map.of("runId", "r1", "executorType", "AI_AGENT"));

        mvc.perform(post("/api/v1/workspace/scenarios/sc1/question-runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"请分析\",\"executorType\":\"AI_AGENT\","
                                + "\"executorId\":\"agent-1\",\"algorithmId\":\"alg-1\","
                                + "\"algorithmInputs\":{\"x\":1},\"hypotheses\":[]}"))
                .andExpect(status().isOk());

        verify(service).run(eq("sc1"), eq("请分析"), eq(""), anyList(),
                eq("alg-1"), eq(Map.of("x", 1)), eq("AI_AGENT"), eq("agent-1"));
    }

    @Test
    @DisplayName("旧请求（只有 mentalModel）：executorType/executorId 为 null，由 service 兼容默认")
    void legacyRequestPassesNullExecutor() throws Exception {
        when(service.run(any(), any(), any(), anyList(), any(), any(), any(), any()))
                .thenReturn(Map.of("runId", "r2"));

        mvc.perform(post("/api/v1/workspace/scenarios/sc1/question-runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"q\",\"mentalModel\":\"FIRST_PRINCIPLES\"}"))
                .andExpect(status().isOk());

        verify(service).run(eq("sc1"), eq("q"), eq("FIRST_PRINCIPLES"), anyList(),
                eq(""), eq(Map.of()), eq(""), eq(""));
    }
}
