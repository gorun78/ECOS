package com.chinacreator.gzcm.engine.ai.controller;

import com.chinacreator.gzcm.engine.ai.agent.mesh.entity.AgentRegistryEntity;
import com.chinacreator.gzcm.engine.ai.agent.mesh.repository.AgentRegistryRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@code /api/v1/aip/agents} 族 wire 键面门（DI-2 #147CY）。
 *
 * <p>摘要 6 键 / 详情 11 键 / 删除回执 2 键，键名与旧 {@code Map} 逐键一致；
 * 请求体与 metadata JSON 仍是自由面（键面由调用方/登记方定义），不在钉死范围。</p>
 */
class AipAgentWireShapeTest {

    private static final List<String> SUMMARY_KEYS = List.of(
            "id", "name", "status", "createdAt", "modelProvider", "modelName");

    private static final List<String> DETAIL_KEYS = List.of(
            "id", "name", "status", "role", "description", "modelProvider", "modelName",
            "systemPrompt", "tools", "createdAt", "updatedAt");

    private AIPAgentController controller;

    @BeforeEach
    void setUp() {
        AgentRegistryRepository repo = (AgentRegistryRepository) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{AgentRegistryRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findAll" -> List.of(agent());
                    case "findById" -> agent();
                    case "delete" -> 1;
                    default -> null;
                });
        controller = new AIPAgentController();
        ReflectionTestUtils.setField(controller, "agentRepo", repo);
    }

    private static AgentRegistryEntity agent() {
        AgentRegistryEntity a = new AgentRegistryEntity();
        a.setId("aip_agent_1");
        a.setName("经营诊断");
        a.setStatus("active");
        a.setRole("aip");
        a.setMetadata("{\"description\":\"诊断\",\"modelProvider\":\"deepseek\","
                + "\"modelName\":\"deepseek-v4-flash\",\"systemPrompt\":\"sys\"}");
        a.setCapability("{\"tools\":[\"query_db\"]}");
        a.setCreatedAt(LocalDateTime.of(2026, 11, 5, 9, 0, 0));
        a.setUpdatedAt(LocalDateTime.of(2026, 11, 5, 9, 30, 0));
        return a;
    }

    private static List<String> keys(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    private static JsonNode data(Object payload) {
        return new ObjectMapper().valueToTree(payload);
    }

    @Test
    @DisplayName("列表：摘要 6 键，模型面取自 metadata（缺省为空串）")
    void listShape() {
        JsonNode data = data(controller.listAgents().getData());

        assertEquals(1, data.size());
        assertEquals(SUMMARY_KEYS, keys(data.get(0)));
        assertEquals("deepseek", data.get(0).path("modelProvider").asText());
        assertEquals("2026-11-05T09:00", data.get(0).path("createdAt").asText());
    }

    @Test
    @DisplayName("详情：11 键；tools 从 capability JSON 还原为数组")
    void detailShape() {
        JsonNode data = data(controller.getAgent("aip_agent_1").getData());

        assertEquals(DETAIL_KEYS, keys(data));
        assertEquals("aip", data.path("role").asText());
        assertEquals("sys", data.path("systemPrompt").asText());
        assertEquals("query_db", data.path("tools").get(0).asText());
        assertEquals("2026-11-05T09:30", data.path("updatedAt").asText());
    }

    @Test
    @DisplayName("删除回执：2 键（deleted/id）")
    void deleteShape() {
        JsonNode data = data(controller.deleteAgent("aip_agent_1").getBody().getData());

        assertEquals(List.of("deleted", "id"), keys(data));
        assertEquals(true, data.path("deleted").asBoolean());
        assertEquals("aip_agent_1", data.path("id").asText());
    }
}
