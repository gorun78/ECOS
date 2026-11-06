package com.chinacreator.gzcm.engine.cognitive2.controller;

import com.chinacreator.gzcm.engine.cognitive.api.context.ContextBuildRequest;
import com.chinacreator.gzcm.engine.cognitive.api.context.ContextBuildResult;
import com.chinacreator.gzcm.engine.cognitive.api.outcome.OutcomeEvaluateRequest;
import com.chinacreator.gzcm.engine.cognitive.api.outcome.OutcomeEvaluateResult;
import com.chinacreator.gzcm.engine.cognitive2.service.context.ContextBuilderService;
import com.chinacreator.gzcm.engine.cognitive2.service.outcome.OutcomeEvaluateService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Context Build / Outcome Evaluate 的 HTTP 契约（路径 + ApiResponse 包络），Service 以 mock 隔离。 */
class CognitiveContextOutcomeContractTest {

    @Test
    void contextBuildEndpointKeepsPathAndEnvelope() throws Exception {
        ContextBuilderService service = mock(ContextBuilderService.class);
        when(service.build(any(ContextBuildRequest.class))).thenReturn(mock(ContextBuildResult.class));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new ContextBuildController(service)).build();

        String body = mvc.perform(post("/api/v1/cognitive/context/build")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertTrue(body.contains("\"code\":0"), body);
        assertTrue(body.contains("\"data\""), body);
    }

    @Test
    void outcomeEvaluateEndpointKeepsPathAndEnvelope() throws Exception {
        OutcomeEvaluateService service = mock(OutcomeEvaluateService.class);
        when(service.evaluate(any(OutcomeEvaluateRequest.class))).thenReturn(mock(OutcomeEvaluateResult.class));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new OutcomeEvaluateController(service)).build();

        String body = mvc.perform(post("/api/v1/cognitive/outcomes/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertTrue(body.contains("\"code\":0"), body);
        assertTrue(body.contains("\"data\""), body);
    }
}
