package com.chinacreator.gzcm.workspace.scenario;

import com.chinacreator.gzcm.workspace.blueprint.BlueprintQueryInvalidException;
import com.chinacreator.gzcm.workspace.blueprint.ScenarioBlueprintController;
import com.chinacreator.gzcm.workspace.blueprint.ScenarioBlueprintService;
import com.chinacreator.gzcm.workspace.exception.WorkspaceExceptionHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/** GET {id}/gaps 契约面：路径逐字 + ApiResponse 包络 + 未知 severity 400/errorCode 真红。 */
class ScenarioBlueprintGapsContractTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ScenarioBlueprintService service = mock(ScenarioBlueprintService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new ScenarioBlueprintController(service))
                .setControllerAdvice(new WorkspaceExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter())
                .build();
    }

    @Test
    void gapsEndpointReturnsEnvelopeAndPassesSeverity() throws Exception {
        when(service.gaps("bp-1", "WARNING"))
                .thenReturn(Map.of("severityFilter", "WARNING", "openBlockers", 1));

        MvcResult result = mvc.perform(get("/api/v1/business/scenarios/sc-1/blueprints/bp-1/gaps")
                        .param("severity", "WARNING"))
                .andReturn();

        assertEquals(200, result.getResponse().getStatus());
        JsonNode body = MAPPER.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertEquals(0, body.path("code").asInt());
        assertEquals("WARNING", body.path("data").path("severityFilter").asText());
        assertEquals(1, body.path("data").path("openBlockers").asInt());
    }

    @Test
    void unknownSeverityMapsTo400WithErrorCode() throws Exception {
        when(service.gaps("bp-1", "CRITICAL"))
                .thenThrow(new BlueprintQueryInvalidException("severity", "CRITICAL"));

        MvcResult result = mvc.perform(get("/api/v1/business/scenarios/sc-1/blueprints/bp-1/gaps")
                        .param("severity", "CRITICAL"))
                .andReturn();

        assertEquals(400, result.getResponse().getStatus());
        JsonNode body = MAPPER.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertEquals(400, body.path("code").asInt());
        assertEquals("BLUEPRINT_QUERY_INVALID", body.path("errorCode").asText());
    }
}
