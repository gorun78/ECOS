package com.chinacreator.gzcm.engine.kb.controller;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 本体树端点 wire 保真（DI-2 #147DY）。
 *
 * <p>远端载荷解码（数组/单对象两种 {@code data} 形态）迁入 {@code OntologyTreeRemotePayload} 后，
 * 树的领域分桶、本体条目（id/code/name/entityCodes/entities/domainBreakdown）与实体摘要
 * （code/name/entityType/domainId/sortOrder）键面不变；远端不可达仍返回空数组。</p>
 */
class OntologyTreeWireShapeTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 按 URL 返回固定远端应答的 RestTemplate 桩。 */
    private static RestTemplate fakeRest(String ontologiesJsonForm, boolean entitiesUnreachable) {
        return new RestTemplate() {
            @SuppressWarnings("unchecked")
            @Override
            public <T> T getForObject(String url, Class<T> responseType, Object... uriVariables) {
                if (entitiesUnreachable && url.contains("/entities")) {
                    throw new RestClientException("connection refused");
                }
                if (url.endsWith("/ecos/ontologies")) {
                    Map<String, Object> body = new LinkedHashMap<>();
                    body.put("data", ontologiesJsonForm.equals("single")
                            ? Map.of("id", "1", "code", "ONT_A", "name", "A 本体")
                            : List.of(Map.of("id", "1", "code", "ONT_A", "name", "A 本体")));
                    return (T) body;
                }
                if (url.contains("/entities")) {
                    Map<String, Object> entity = new LinkedHashMap<>();
                    entity.put("code", "E1");
                    entity.put("name", "实体一");
                    entity.put("entityType", "MASTER");
                    entity.put("domainId", "dom-1");
                    entity.put("sortOrder", "2");
                    Map<String, Object> body = new LinkedHashMap<>();
                    body.put("data", List.of(entity));
                    return (T) body;
                }
                return (T) new LinkedHashMap<String, Object>();
            }
        };
    }

    private static OntologyTreeController controller(RestTemplate rest) {
        return new OntologyTreeController(rest, MAPPER, "http://localhost:8080/api/v1");
    }

    @Test
    @DisplayName("树形：domain 分桶 + 本体条目键面 + 实体摘要键面（sortOrder 字符串转 int）")
    void treeKeepsShape() throws Exception {
        JsonNode data = dataOf(controller(fakeRest("list", false)).tree());

        assertEquals(1, data.size());
        assertEquals(List.of("domain", "ontologies"), fieldNames(data.get(0)));
        JsonNode item = data.get(0).get("ontologies").get(0);
        assertEquals(List.of("id", "code", "name", "entityCodes", "entities", "domainBreakdown"),
                fieldNames(item));
        assertEquals("ONT_A", item.get("code").asText());
        assertEquals("E1", item.get("entityCodes").get(0).asText());
        JsonNode brief = item.get("entities").get(0);
        assertEquals(List.of("code", "name", "entityType", "domainId", "sortOrder"),
                fieldNames(brief));
        assertEquals(2, brief.get("sortOrder").asInt());
        assertTrue(item.get("domainBreakdown").has("dom-1"), "按 domainId 分桶");
    }

    @Test
    @DisplayName("data 单对象形态兼容；实体腿不可达 → 空 codes/entities（不 500）")
    void singleObjectAndUnreachableAreTolerated() throws Exception {
        JsonNode single = dataOf(controller(fakeRest("single", false)).tree());
        assertEquals("ONT_A", single.get(0).get("ontologies").get(0).get("code").asText());

        JsonNode degraded = dataOf(controller(fakeRest("list", true)).tree());
        JsonNode item = degraded.get(0).get("ontologies").get(0);
        assertEquals(0, item.get("entityCodes").size());
        assertEquals(0, item.get("entities").size());
    }

    private static JsonNode dataOf(ApiResponse<?> resp) throws Exception {
        assertEquals(0, resp.getCode(), "夹具必须走成功信封: " + resp.getMessage());
        return MAPPER.valueToTree(resp).get("data");
    }

    private static List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }
}
