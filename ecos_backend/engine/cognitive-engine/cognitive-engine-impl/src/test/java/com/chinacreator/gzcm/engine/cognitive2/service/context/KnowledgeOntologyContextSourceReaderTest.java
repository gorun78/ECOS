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
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** K/I 读取面（CE-01 来源追溯）：只认 KNOWLEDGE/ONTOLOGY，版本取源侧，缺失/404 不伪造。 */
class KnowledgeOntologyContextSourceReaderTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final RestTemplate restTemplate = mock(RestTemplate.class);
    private final ServiceEndpointResolver resolver = mock(ServiceEndpointResolver.class);
    private final KnowledgeOntologyContextSourceReader reader =
            new KnowledgeOntologyContextSourceReader(restTemplate, resolver);

    @Test
    @DisplayName("只承接 KNOWLEDGE/ONTOLOGY，其余面保持未接线")
    void supportsKnowledgeAndOntologyOnly() {
        assertTrue(reader.supports("KNOWLEDGE"));
        assertTrue(reader.supports("ONTOLOGY"));
        assertFalse(reader.supports("DATA_ASSET"));
        assertFalse(reader.supports("COGNITIVE"));
        assertFalse(reader.supports(null));
    }

    @Test
    @DisplayName("KNOWLEDGE 读取：version 取源侧 updatedAt，来源/状态/时点齐全")
    void readsKnowledgeArticle() throws Exception {
        when(resolver.resolve("dccheng", ServiceEndpointResolver.RouteMode.MONOLITH))
                .thenReturn(Optional.of("http://kb-test"));
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), nullable(HttpEntity.class), eq(JsonNode.class)))
                .thenReturn(new ResponseEntity<>(MAPPER.readTree("""
                        {"code":0,"data":{"id":"art-1","title":"年度口径","status":"active","updatedAt":1699999999000}}
                        """), HttpStatus.OK));

        ContextSourceReader.SourceReadResult result = reader.read(new ContextBuildRequest(), ref("KNOWLEDGE", "art-1"));

        assertEquals("1699999999000", result.getVersion());
        assertEquals("1699999999000", result.getAsOfTime());
        assertEquals("art-1", result.getAssetRefs().get(0).getRefId());
        assertEquals("active", result.getAssetRefs().get(0).getStatus());
        assertFalse(result.isAuthoritative());
    }

    @Test
    @DisplayName("ONTOLOGY 读取：走 buszhi 实体详情；源侧无版本即失败，禁回填请求版本")
    void ontologyMissingVersionFailsLoud() throws Exception {
        when(resolver.resolve("buszhi", ServiceEndpointResolver.RouteMode.MONOLITH))
                .thenReturn(Optional.of("http://ont-test"));
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), nullable(HttpEntity.class), eq(JsonNode.class)))
                .thenReturn(new ResponseEntity<>(MAPPER.readTree("""
                        {"code":0,"data":{"id":"ent-1","code":"PROJECT"}}
                        """), HttpStatus.OK));

        ContextSourceRef ref = ref("ONTOLOGY", "ent-1");
        ref.setVersion("1");
        assertThrows(ContextReadException.class, () -> reader.read(new ContextBuildRequest(), ref));
    }

    @Test
    @DisplayName("源侧 404/读失败与真空态分列：抛异常而非空集合")
    void sourceFailureIsNotAnEmptySuccess() {
        when(resolver.resolve("dccheng", ServiceEndpointResolver.RouteMode.MONOLITH))
                .thenReturn(Optional.of("http://kb-test"));
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), nullable(HttpEntity.class), eq(JsonNode.class)))
                .thenThrow(HttpClientErrorException.create(HttpStatus.NOT_FOUND, "not found", null, null, null));

        assertThrows(ContextReadException.class,
                () -> reader.read(new ContextBuildRequest(), ref("KNOWLEDGE", "missing")));
        assertThrows(ContextReadException.class,
                () -> reader.read(new ContextBuildRequest(), ref("KNOWLEDGE", " ")));
    }

    private static ContextSourceRef ref(String type, String id) {
        ContextSourceRef r = new ContextSourceRef();
        r.setSourceType(type);
        r.setSourceId(id);
        r.setVersion("placeholder");
        r.setRequired(true);
        return r;
    }
}
