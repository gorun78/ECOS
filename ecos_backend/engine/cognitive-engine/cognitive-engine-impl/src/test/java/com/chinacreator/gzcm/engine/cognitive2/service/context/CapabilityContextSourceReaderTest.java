package com.chinacreator.gzcm.engine.cognitive2.service.context;

import com.chinacreator.gzcm.engine.cognitive.api.context.ContextBuildRequest;
import com.chinacreator.gzcm.engine.cognitive.api.context.ContextSourceRef;
import com.chinacreator.gzcm.runtime.access.endpoint.ServiceEndpointResolver;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** CAPABILITY 读取面：目录精确命中 + contractVersion 必载；未命中/缺版本失败。 */
class CapabilityContextSourceReaderTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final RestTemplate restTemplate = mock(RestTemplate.class);
    private final ServiceEndpointResolver resolver = mock(ServiceEndpointResolver.class);
    private final CapabilityContextSourceReader reader = new CapabilityContextSourceReader(restTemplate, resolver);

    @Test
    void supportsCapabilityOnly() {
        assertTrue(reader.supports("CAPABILITY"));
        assertFalse(reader.supports("KNOWLEDGE"));
        assertFalse(reader.supports("DATA_ASSET"));
    }

    @Test
    @DisplayName("命中能力：version=contractVersion，能力引用带 capabilityId")
    void readsCapabilityContractVersion() throws Exception {
        when(resolver.resolve("aiming", ServiceEndpointResolver.RouteMode.MONOLITH))
                .thenReturn(Optional.of("http://ai-test"));
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), nullable(HttpEntity.class), eq(JsonNode.class)))
                .thenReturn(new ResponseEntity<>(MAPPER.readTree("""
                        {"code":0,"data":[
                          {"capabilityId":"KB_RETRIEVE","contractVersion":"v2","automationLevel":1},
                          {"capabilityId":"FORECAST_RUN","contractVersion":"v3"}
                        ]}
                        """), HttpStatus.OK));

        ContextSourceReader.SourceReadResult r = reader.read(new ContextBuildRequest(), ref("KB_RETRIEVE"));
        assertEquals("v2", r.getVersion());
        assertEquals("KB_RETRIEVE", r.getCapabilities().get(0).getCapabilityId());
        assertEquals("v2", r.getCapabilities().get(0).getContractVersion());
    }

    @Test
    @DisplayName("目录未命中/缺契约版本 ⇒ 失败，不伪造空成功")
    void missingCapabilityOrContractFails() throws Exception {
        when(resolver.resolve("aiming", ServiceEndpointResolver.RouteMode.MONOLITH))
                .thenReturn(Optional.of("http://ai-test"));
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), nullable(HttpEntity.class), eq(JsonNode.class)))
                .thenReturn(new ResponseEntity<>(MAPPER.readTree("""
                        {"code":0,"data":[{"capabilityId":"KB_RETRIEVE"}]}
                        """), HttpStatus.OK));

        assertThrows(ContextReadException.class,
                () -> reader.read(new ContextBuildRequest(), ref("NO_SUCH_CAP")));
        assertThrows(ContextReadException.class,
                () -> reader.read(new ContextBuildRequest(), ref("KB_RETRIEVE")));
    }

    private static ContextSourceRef ref(String id) {
        ContextSourceRef r = new ContextSourceRef();
        r.setSourceType("CAPABILITY");
        r.setSourceId(id);
        r.setVersion("v1");
        r.setRequired(true);
        return r;
    }
}
