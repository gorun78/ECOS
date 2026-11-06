package com.chinacreator.gzcm.engine.cognitive2.controller;

import com.chinacreator.gzcm.engine.cognitive.api.CognitiveErrorCode;
import com.chinacreator.gzcm.engine.cognitive.api.framework.CognitiveFrameworkDefinition;
import com.chinacreator.gzcm.engine.cognitive2.controller.scenario.CognitiveErrorAdvice;
import com.chinacreator.gzcm.engine.cognitive2.service.framework.CognitiveExecutionService;
import com.chinacreator.gzcm.engine.cognitive2.service.framework.CognitiveFrameworkRegistryService;
import com.chinacreator.gzcm.engine.cognitive2.service.framework.FrameworkSelectionService;
import com.chinacreator.gzcm.engine.cognitive2.service.framework.PlanValidator;
import com.chinacreator.gzcm.engine.cognitive2.service.scenario.CognitiveGuardException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 新端点 HTTP 契约（阶段 1：路径/响应包络/错误状态）。
 * stand-alone MockMvc；认证/密级由网关既有三滤波器覆盖，不在本单测冒充。
 * 注：stand-alone 默认以 XML 渲染 ApiResponse（既有契约），断言按包络字段而非 JSON 语法。
 */
class CognitiveFrameworkContractTest {

    private final CognitiveFrameworkRegistryService registry = mock(CognitiveFrameworkRegistryService.class);
    private final FrameworkSelectionService selection = mock(FrameworkSelectionService.class);
    private final PlanValidator validator = new PlanValidator();
    private final CognitiveExecutionService executions = new CognitiveExecutionService();
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders
                .standaloneSetup(
                        new CognitiveFrameworkController(registry, selection, validator),
                        new CognitiveExecutionController(executions))
                .setControllerAdvice(new CognitiveErrorAdvice())
                .build();
    }

    @Test
    @DisplayName("GET /api/v1/cognitive/frameworks 返回 ApiResponse 包络")
    void listFrameworksPathAndEnvelope() throws Exception {
        CognitiveFrameworkDefinition d = new CognitiveFrameworkDefinition();
        d.setFrameworkId("CF-01-01");
        d.setVersion("1.0.0");
        d.setName("problem-framing");
        d.setCategory("PROBLEM_FRAMING");
        d.setStatus("PUBLISHED");
        when(registry.list(null, null, "PUBLISHED")).thenReturn(List.of(d));

        String body = body(mvc.perform(get("/api/v1/cognitive/frameworks")
                        .accept(MediaType.APPLICATION_XML)
                        .param("status", "PUBLISHED"))
                .andExpect(status().isOk()));
        assertTrue(body.contains("<code>0</code>"), body);
        assertTrue(body.contains("<frameworkId>CF-01-01</frameworkId>"), body);
    }

    @Test
    @DisplayName("POST /frameworks/select 走同一单源路径并返回硬过滤结果")
    void selectPathAndEnvelope() throws Exception {
        when(selection.select(any())).thenReturn(Map.of(
                "selectedFrameworks", List.of(),
                "excludedFrameworks", List.of(),
                "missingInputs", List.of("anomaly_definition_present"),
                "status", "NEEDS_INPUT_OR_HUMAN"));

        String body = body(mvc.perform(post("/api/v1/cognitive/frameworks/select")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_XML)
                        .content("{\"problemType\":\"metric_deviation\"}"))
                .andExpect(status().isOk()));
        assertTrue(body.contains("<code>0</code>"), body);
        assertTrue(body.contains("<status>NEEDS_INPUT_OR_HUMAN</status>"), body);
    }

    @Test
    @DisplayName("非法框架状态 → 400 错误体")
    void invalidStatusMapsTo400() throws Exception {
        when(registry.list(any(), any(), anyString()))
                .thenThrow(new CognitiveGuardException(CognitiveErrorCode.E400_INVALID_PARAM,
                        CognitiveErrorCode.MSG_INVALID_PARAM, "status=BAD"));

        String body = body(mvc.perform(get("/api/v1/cognitive/frameworks")
                        .accept(MediaType.APPLICATION_XML)
                        .param("status", "BAD"))
                .andExpect(status().isBadRequest()));
        assertTrue(body.contains("<code>400</code>"), body);
    }

    @Test
    @DisplayName("执行记录：创建 → 非法跳转 409；合法链路 200")
    void executionLifecycleHttp() throws Exception {
        String created = body(mvc.perform(post("/api/v1/cognitive/executions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_XML)
                        .content("{\"idempotencyKey\":\"http-k1\",\"scenarioId\":\"s1\"}"))
                .andExpect(status().isOk()));
        assertTrue(created.contains("<status>CREATED</status>"), created);
        String id = created.split("<executionId>")[1].split("</executionId>")[0];

        String rejected = body(mvc.perform(post("/api/v1/cognitive/executions/" + id + "/transitions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_XML)
                        .content("{\"targetStatus\":\"RUNNING\"}"))
                .andExpect(status().isConflict()));
        assertTrue(rejected.contains("<code>409</code>"), rejected);

        String planned = body(mvc.perform(post("/api/v1/cognitive/executions/" + id + "/transitions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_XML)
                        .content("{\"targetStatus\":\"PLANNED\"}"))
                .andExpect(status().isOk()));
        assertTrue(planned.contains("<status>PLANNED</status>"), planned);
    }

    private static String body(ResultActions actions) throws Exception {
        return actions.andReturn().getResponse().getContentAsString();
    }
}
