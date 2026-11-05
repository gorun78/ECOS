package com.chinacreator.gzcm.engine.ai.controller;

import com.chinacreator.gzcm.engine.ai.service.AgentSessionService;
import com.chinacreator.gzcm.engine.ai.service.AgentSessionService.AgentMessage;
import com.chinacreator.gzcm.engine.ai.service.AgentSessionService.AgentSession;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * AgentLoop 会话面 wire 键面门（DI-2 #147DH）。
 *
 * <p>会话 8 键、消息 8 键、列表 2 键、详情 8 键（{@code messages} 仅非空时出现）、
 * 消息分页 4 键；chat/SSE 等动态结构不在本门范围。</p>
 */
class AgentLoopSessionWireShapeTest {

    private static final List<String> SESSION_KEYS = List.of(
            "id", "agentId", "userId", "tenantId", "status", "messageCount", "createdAt", "lastActiveAt");

    private static final List<String> MESSAGE_KEYS = List.of(
            "id", "role", "content", "toolCalls", "toolResults", "tokens", "threadId", "createdAt");

    private AgentSessionService sessionService;
    private AgentLoopController controller;

    @BeforeEach
    void setUp() {
        sessionService = mock(AgentSessionService.class);
        controller = new AgentLoopController();
        org.springframework.test.util.ReflectionTestUtils.setField(controller, "sessionService", sessionService);
    }

    private static AgentSession session() {
        AgentSession s = new AgentSession();
        s.setId("s1");
        s.setAgentId("default");
        s.setUserId("u1");
        s.setTenantId("t1");
        s.setStatus("ACTIVE");
        s.setMessageCount(2);
        s.setCreatedAt(1000L);
        s.setLastActiveAt(2000L);
        return s;
    }

    private static AgentMessage message() {
        AgentMessage m = new AgentMessage();
        m.setId(1L);
        m.setRole("user");
        m.setContent("你好");
        m.setToolCalls(List.of());
        m.setToolResults(null);
        m.setTokens(5);
        m.setThreadId("main");
        m.setCreatedAt(1500L);
        return m;
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
    @DisplayName("创建会话：8 键，时间为毫秒数")
    void createShape() {
        when(sessionService.createSession(anyString(), anyString(), anyString())).thenReturn(session());

        JsonNode data = json(controller.createSession(Map.of("agentId", "default")).getData());

        assertEquals(SESSION_KEYS, keys(data));
        assertEquals(1000L, data.path("createdAt").asLong());
    }

    @Test
    @DisplayName("会话列表：2 键（total/sessions），条目 8 键")
    void listShape() {
        when(sessionService.listSessions(null, null, 50)).thenReturn(List.of(session()));

        JsonNode data = json(controller.listSessions(null, null, 50).getData());

        assertEquals(List.of("total", "sessions"), keys(data));
        assertEquals(SESSION_KEYS, keys(data.path("sessions").get(0)));
    }

    @Test
    @DisplayName("会话详情：8 键；有消息时追加 messages（8 键），无消息时不出现该键")
    void detailShape() {
        AgentSession withMsgs = session();
        when(sessionService.getSession("s1")).thenReturn(withMsgs);
        when(sessionService.getMessages("s1", "main")).thenReturn(List.of(message()));

        JsonNode withMessages = json(controller.getSession("s1", "main").getData());
        List<String> expected = new ArrayList<>(SESSION_KEYS);
        expected.add("messages");
        assertEquals(expected, keys(withMessages));
        assertEquals(MESSAGE_KEYS, keys(withMessages.path("messages").get(0)));

        when(sessionService.getMessages("s1", "main")).thenReturn(null);
        JsonNode noMessages = json(controller.getSession("s1", "main").getData());
        assertEquals(SESSION_KEYS, keys(noMessages));
        assertFalse(noMessages.has("messages"));
    }

    @Test
    @DisplayName("消息分页：4 键（total/page/size/items），条目 8 键，页大小上限 500")
    void messagesShape() {
        when(sessionService.getSession("s1")).thenReturn(session());
        when(sessionService.getMessages(eq("s1"))).thenReturn(List.of(message()));

        JsonNode data = json(controller.listSessionMessages("s1", 1, 999).getData());

        assertEquals(List.of("total", "page", "size", "items"), keys(data));
        assertEquals(500, data.path("size").asInt());
        assertEquals(MESSAGE_KEYS, keys(data.path("items").get(0)));
    }
}
