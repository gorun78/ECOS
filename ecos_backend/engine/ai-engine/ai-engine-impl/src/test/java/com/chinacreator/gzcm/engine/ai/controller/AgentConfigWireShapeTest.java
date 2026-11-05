package com.chinacreator.gzcm.engine.ai.controller;

import com.chinacreator.gzcm.engine.ai.config.AgentConfigService;
import com.chinacreator.gzcm.engine.ai.config.AgentConfigService.AgentRow;
import com.chinacreator.gzcm.engine.ai.config.AgentConfigService.AgentTestResponse;
import com.chinacreator.gzcm.engine.ai.config.AgentConfigService.PromptVersionRow;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@code /api/v1/agents} 族 wire 键面门（DI-2 #147DA）。
 *
 * <p>Agent 行 10 键、列表包裹 2 键、删除回执 1 键、模型 3 键、Prompt 模板 6/7 键
 * （{@code agentId} 仅汇总腿出现）、测试回执三态并集（成功 9 / 不可用 4 / 失败 5）。
 * 键序与旧 {@code Map}/{@code Map.of} 时代逐键一致。</p>
 */
class AgentConfigWireShapeTest {

    private static final List<String> AGENT_KEYS = List.of(
            "id", "name", "modelProvider", "modelName", "systemPrompt", "tools",
            "knowledge", "status", "createdAt", "updatedAt");

    private AgentConfigService service;
    private AgentConfigController controller;

    @BeforeEach
    void setUp() {
        service = mock(AgentConfigService.class);
        controller = new AgentConfigController(service);
    }

    private static AgentRow row() {
        return new AgentRow("agent-101", "经营诊断", "deepseek", "deepseek-v4-flash", "sys",
                List.of("query_db"), List.of("kb1"), "draft",
                "2026-11-05T09:00", "2026-11-05T09:10");
    }

    private static List<String> keys(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    private static JsonNode json(Object payload) {
        return new ObjectMapper().valueToTree(payload);
    }

    @Test
    @DisplayName("列表：包裹 2 键（data/total），Agent 行 10 键且 tools/knowledge 为数组")
    void listShape() {
        when(service.listAgents(100)).thenReturn(List.of(row()));
        when(service.totalCount()).thenReturn(1L);

        JsonNode data = json(controller.listAgents(100).getData());

        assertEquals(List.of("data", "total"), keys(data));
        assertEquals(AGENT_KEYS, keys(data.path("data").get(0)));
        assertEquals("query_db", data.path("data").get(0).path("tools").get(0).asText());
        assertEquals(1, data.path("total").asInt());
    }

    @Test
    @DisplayName("详情 10 键；删除回执 1 键（success）")
    void detailAndDeleteShape() {
        when(service.getAgent("agent-101")).thenReturn(Optional.of(row()));
        when(service.deleteAgent("agent-101")).thenReturn(true);

        assertEquals(AGENT_KEYS, keys(json(controller.getAgent("agent-101").getData())));
        assertEquals(List.of("success"), keys(json(controller.deleteAgent("agent-101").getData())));
    }

    @Test
    @DisplayName("模型 4 条，每条 3 键（id/name/provider）")
    void modelsShape() {
        JsonNode data = json(controller.listModels().getData());

        assertEquals(4, data.size());
        assertEquals(List.of("id", "name", "provider"), keys(data.get(0)));
        assertEquals("deepseek-v4-pro", data.get(0).path("id").asText());
    }

    @Test
    @DisplayName("Prompt 汇总：来自 Agent 时 7 键（含 agentId）；无 Agent 时默认两条且 6 键")
    void promptsShape() {
        when(service.listAgents(100)).thenReturn(List.of(row()));
        when(service.listPromptVersions("agent-101"))
                .thenReturn(List.of(new PromptVersionRow("v1.0", "内容", "2026-11-05T09:00")));

        JsonNode withAgent = json(controller.listAllPrompts().getData());
        assertEquals(List.of("id", "title", "filename", "content", "version", "category", "agentId"),
                keys(withAgent.get(0)));
        assertEquals("agent-101", withAgent.get(0).path("agentId").asText());

        when(service.listAgents(100)).thenReturn(List.of());
        JsonNode fallback = json(controller.listAllPrompts().getData());
        assertEquals(2, fallback.size());
        assertEquals(List.of("id", "title", "filename", "content", "version", "category"),
                keys(fallback.get(0)));
        assertFalse(fallback.get(0).has("agentId"));
    }

    @Test
    @DisplayName("测试回执三态并集：成功 9 键 / 依赖不可用 4 键 / 失败 5 键")
    void testResponseShapes() {
        when(service.getAgent("agent-101")).thenReturn(Optional.of(row()));

        when(service.testAgent(org.mockito.ArgumentMatchers.eq("agent-101"),
                org.mockito.ArgumentMatchers.anyMap())).thenReturn(new AgentTestResponse(
                "agent-101", "sess-1", "completed", true, "你好", "答", 12, 34, 56L, null));
        assertEquals(List.of("agentId", "sessionId", "status", "success", "message", "response",
                "tokensInput", "tokensOutput", "durationMs"),
                keys(json(controller.testAgent("agent-101", java.util.Map.of()).getData())));

        when(service.testAgent(org.mockito.ArgumentMatchers.eq("agent-101"),
                org.mockito.ArgumentMatchers.anyMap())).thenReturn(new AgentTestResponse(
                "agent-101", null, "unavailable", null, "你好", "Hermes 未就绪", null, null, null, null));
        assertEquals(List.of("agentId", "status", "message", "response"),
                keys(json(controller.testAgent("agent-101", java.util.Map.of()).getData())));

        when(service.testAgent(org.mockito.ArgumentMatchers.eq("agent-101"),
                org.mockito.ArgumentMatchers.anyMap())).thenReturn(new AgentTestResponse(
                "agent-101", null, "error", null, "你好", "失败", null, null, 0L, null));
        assertEquals(List.of("agentId", "status", "message", "response", "durationMs"),
                keys(json(controller.testAgent("agent-101", java.util.Map.of()).getData())));
    }
}
