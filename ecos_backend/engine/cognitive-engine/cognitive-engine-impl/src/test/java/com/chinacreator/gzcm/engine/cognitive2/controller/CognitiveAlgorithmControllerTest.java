package com.chinacreator.gzcm.engine.cognitive2.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.chinacreator.gzcm.engine.cognitive2.service.Nsga2Optimizer;
import com.chinacreator.gzcm.engine.cognitive2.service.algorithm.BeliefCausalAlgorithms;
import com.chinacreator.gzcm.engine.cognitive2.service.algorithm.CognitiveAlgorithmService;
import com.chinacreator.gzcm.engine.cognitive2.service.algorithm.DecisionAlgorithms;
import com.chinacreator.gzcm.engine.cognitive2.service.algorithm.DiagnosticAlgorithms;
import com.chinacreator.gzcm.engine.cognitive2.service.algorithm.ForecastSimulationAlgorithms;

/** 算法执行 HTTP 面门禁：路由、入参解包、数值回传。 */
class CognitiveAlgorithmControllerTest {

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        CognitiveAlgorithmService service = new CognitiveAlgorithmService(List.of(
                new BeliefCausalAlgorithms(),
                new ForecastSimulationAlgorithms(),
                new DecisionAlgorithms(new Nsga2Optimizer()),
                new DiagnosticAlgorithms()));
        mvc = MockMvcBuilders.standaloneSetup(new CognitiveAlgorithmController(service)).build();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> data(String json) throws Exception {
        if (!json.trim().startsWith("{")) {
            throw new AssertionError("非 JSON 响应: " + json.substring(0, Math.min(400, json.length())));
        }
        Map<String, Object> body = new ObjectMapper().readValue(json, Map.class);
        return (Map<String, Object>) body.get("data");
    }

    @Test
    @DisplayName("POST /algorithms/bayes-update/execute：inputs 解包 + 后验回传")
    void executesBayes() throws Exception {
        String json = mvc.perform(post("/api/v1/cognitive/algorithms/bayes-update/execute")
                        .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)
                        .content("{\"scenarioId\":\"sc1\",\"inputs\":{\"prior\":[0.5,0.5],"
                                + "\"observation\":[0.8,0.2]}}"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andReturn().getResponse().getContentAsString();
        Map<String, Object> data = data(json);
        assertEquals("SUCCEEDED", data.get("status"));
        @SuppressWarnings("unchecked")
        Map<String, Object> metrics = (Map<String, Object>) data.get("metrics");
        @SuppressWarnings("unchecked")
        List<Number> posterior = (List<Number>) metrics.get("posterior");
        assertEquals(0.8, posterior.get(0).doubleValue(), 1e-6);
        assertNotNull(data.get("elapsedMs"));
    }

    @Test
    @DisplayName("POST linear-regression：真实公式经 HTTP 面返回")
    void executesRegression() throws Exception {
        String json = mvc.perform(post("/api/v1/cognitive/algorithms/linear-regression/execute")
                        .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)
                        .content("{\"inputs\":{\"x\":[1,2,3],\"y\":[3,5,7]}}"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andReturn().getResponse().getContentAsString();
        @SuppressWarnings("unchecked")
        Map<String, Object> metrics = (Map<String, Object>) data(json).get("metrics");
        assertEquals(2.0, ((Number) metrics.get("slope")).doubleValue(), 1e-9);
        assertEquals(1.0, ((Number) metrics.get("intercept")).doubleValue(), 1e-9);
    }
}
