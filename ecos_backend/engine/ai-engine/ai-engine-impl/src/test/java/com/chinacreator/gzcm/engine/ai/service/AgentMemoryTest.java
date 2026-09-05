package com.chinacreator.gzcm.engine.ai.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Wave-5.1 T-09 — Agent Memory 等价行为测试。
 *
 * <p>任务原文中的 AgentMemory 在 ai-engine 当前实现中由 {@link AgentSessionService}
 * + {@link MemoryExtractor} 承载。本测试 mock JdbcTemplate，不连接 PG。
 *
 * @author ECOS AI Engine Team
 * @since 2026-09-02 (Wave-5.1)
 */
@ExtendWith(MockitoExtension.class)
class AgentMemoryTest {

    @Mock
    private JdbcTemplate jdbc;

    private AgentSessionService newService() {
        return new AgentSessionService(jdbc);
    }

    @Test
    @DisplayName("T-09-2-1: 创建会话 — 默认 ACTIVE、空历史、tenantId 落库")
    void createSessionStoresTenantContext() {
        AgentSessionService service = newService();

        AgentSessionService.AgentSession session = service.createSession("agent-1", "user-1", "tenant-1");

        assertNotNull(session.getId());
        assertEquals("agent-1", session.getAgentId());
        assertEquals("tenant-1", session.getTenantId());
        assertEquals("ACTIVE", session.getStatus());
        assertTrue(session.getMessages().isEmpty());
        verify(jdbc).update(anyString(),
                eq(session.getId()),
                eq("agent-1"),
                eq("user-1"),
                eq("tenant-1"),
                eq("ACTIVE"),
                eq(0),
                any(Long.class),
                any(Long.class));
    }

    @Test
    @DisplayName("T-09-2-2: 追加消息 — 写入 JSON、更新 lastActiveAt 并返回消息对象")
    void appendMessagePersistsMessageAndUpdatesActiveTime() {
        AgentSessionService service = newService();
        List<Map<String, Object>> toolCalls = List.of(Map.of("name", "search_kb"));
        Map<String, Object> toolResults = Map.of("result", "ok");

        AgentSessionService.AgentMessage msg =
                service.appendMessage("sess-1", "assistant", "回复内容", toolCalls, toolResults);

        assertEquals(-1L, msg.getId());
        assertEquals("sess-1", msg.getSessionId());
        assertEquals("assistant", msg.getRole());
        assertEquals("main", msg.getThreadId());
        verify(jdbc).update(
                contains("INSERT INTO sys_agent_message"),
                eq("sess-1"),
                eq("assistant"),
                eq("回复内容"),
                argThat((String json) -> json != null && json.contains("search_kb")),
                argThat((String json) -> json != null && json.contains("ok")),
                eq("main"),
                anyLong());
        verify(jdbc).update(
                contains("UPDATE sys_agent_session SET message_count = message_count + 1"),
                anyLong(),
                eq("sess-1"));
    }

    @Test
    @DisplayName("T-09-2-3: 取历史 — 按默认 main 线程查询，消息对象透传")
    void getMessagesUsesMainThreadByDefault() {
        AgentSessionService service = newService();

        service.getMessages("sess-1");

        verify(jdbc).query(
                contains("FROM sys_agent_message WHERE session_id = ? AND thread_id = ? ORDER BY id ASC"),
                any(RowMapper.class),
                eq("sess-1"),
                eq("main"));
    }

    @Test
    @DisplayName("T-09-2-4: 压缩历史 — 未达阈值时不删除旧消息")
    void compressHistorySkipsWhenBelowThreshold() {
        AgentSessionService service = newService();
        when(jdbc.queryForObject(
                contains("SELECT COUNT(*) FROM sys_agent_message WHERE session_id = ?"),
                eq(Integer.class),
                eq("sess-1")))
                .thenReturn(3);

        service.compressHistory("sess-1", 5);

        verify(jdbc).queryForObject(startsWith("SELECT COUNT(*) FROM sys_agent_message"), eq(Integer.class), eq("sess-1"));
        verify(jdbc, never()).update(
                startsWith("DELETE FROM sys_agent_message"),
                anyString(),
                anyLong());
    }

    @Test
    @DisplayName("T-09-2-5: 过期会话 — 更新命中 1 行返回 true")
    void expireSessionReturnsTrueWhenRowUpdated() {
        AgentSessionService service = newService();
        when(jdbc.update(
                contains("SET status = 'EXPIRED' WHERE id = ?"),
                eq("sess-1")))
                .thenReturn(1);

        assertTrue(service.expireSession("sess-1"));
    }

    @Test
    @DisplayName("T-09-2-6: 归档会话 — 更新 0 行返回 false")
    void archiveSessionReturnsFalseWhenNoRowUpdated() {
        AgentSessionService service = newService();
        when(jdbc.update(
                contains("SET status = 'ARCHIVED' WHERE id = ?"),
                eq("sess-1")))
                .thenReturn(0);

        assertFalse(service.archiveSession("sess-1"));
    }

    @Test
    @DisplayName("T-09-2-7: MemoryExtractor — 仅 user 且长文本命中偏好关键词")
    void memoryExtractorChoosesLongUserPreferenceMessages() {
        MemoryExtractor extractor = new MemoryExtractor();
        AgentSessionService.AgentMessage userMsg = new AgentSessionService.AgentMessage();
        userMsg.setRole("user");
        userMsg.setContent("我一直习惯每天早上审查销售仪表盘，并且偏好使用表格展示毛利率、回款周期、延期率等核心指标，请不要用卡片摘要替代。");

        AgentSessionService.AgentMessage shortMsg = new AgentSessionService.AgentMessage();
        shortMsg.setRole("user");
        shortMsg.setContent("会学习记住偏好，但是这段话太短，没有达到内置最少五十个字符的抽取阈值，所以不应进入跨会话记忆。");

        AgentSessionService.AgentMessage assistantMsg = new AgentSessionService.AgentMessage();
        assistantMsg.setRole("assistant");
        assistantMsg.setContent("助手复述偏好也会被忽略，我只抽取用户主动表达的长期习惯。这条消息内容很长，但角色不是 user，所以不会命中。");

        List<String> facts = extractor.extractFacts(List.of(userMsg, shortMsg, assistantMsg));

        assertEquals(1, facts.size());
        // 关键词按 ["偏好","习惯","总是","不要","记住","喜欢"] 顺序检查, "偏好" 先命中 → fact 前缀 = "用户偏好："
        assertTrue(facts.get(0).startsWith("用户偏好："), "实际=" + facts.get(0));
        assertTrue(facts.get(0).contains("销售仪表盘"), "实际=" + facts.get(0));
    }
}
