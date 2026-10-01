package com.chinacreator.gzcm.engine.ai.dao;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import com.chinacreator.gzcm.engine.ai.service.AgentSessionService;
import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * 数据访问收口（R2-B4-ai / 架构铁律 §2.5 #1 / IR06）：Agent 会话/消息的 JDBC 落点。
 *
 * <p>SQL 与 {@code RowMapper} 原样搬运自原 {@code service.AgentSessionService}，
 * 只搬不改语义；会话生命周期状态机、上下文压缩的业务编排保留在 Service 层。
 * POJO（{@code AgentSession}/{@code AgentMessage}）留在 Service 供 Controller 消费。</p>
 */
@Repository
public class AgentSessionDao extends JdbcTemplateDaoSupport {

    private static final Logger log = LoggerFactory.getLogger(AgentSessionDao.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    // ── 会话 ────────────────────────────────────────────

    public void insertSession(String sessionId, String agentId, String userId, String tenantId,
                              long now) {
        jdbcTemplate.update(
            "INSERT INTO sys_agent_session (id, agent_id, user_id, tenant_id, status, message_count, created_at, last_active_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            sessionId, agentId, userId, tenantId, "ACTIVE", 0, now, now);
    }

    public List<AgentSessionService.AgentSession> findSessionById(String sessionId) {
        return jdbcTemplate.query(
            "SELECT id, agent_id, user_id, tenant_id, status, message_count, created_at, last_active_at " +
            "FROM sys_agent_session WHERE id = ?",
            SESSION_ROW_MAPPER, sessionId);
    }

    public List<AgentSessionService.AgentSession> listByStatus(String status) {
        return jdbcTemplate.query(
            "SELECT id, agent_id, user_id, tenant_id, status, message_count, created_at, last_active_at " +
            "FROM sys_agent_session WHERE status = ? ORDER BY last_active_at DESC",
            SESSION_ROW_MAPPER, status);
    }

    public List<AgentSessionService.AgentSession> listByAgent(String agentId) {
        return jdbcTemplate.query(
            "SELECT id, agent_id, user_id, tenant_id, status, message_count, created_at, last_active_at " +
            "FROM sys_agent_session WHERE agent_id = ? ORDER BY last_active_at DESC",
            SESSION_ROW_MAPPER, agentId);
    }

    public int markIdleSessions(long cutoff) {
        return jdbcTemplate.update(
            "UPDATE sys_agent_session SET status = 'IDLE' WHERE status = 'ACTIVE' AND last_active_at < ?",
            cutoff);
    }

    public int expireSession(String sessionId) {
        return jdbcTemplate.update(
            "UPDATE sys_agent_session SET status = 'EXPIRED' WHERE id = ?", sessionId);
    }

    public int archiveSession(String sessionId) {
        return jdbcTemplate.update(
            "UPDATE sys_agent_session SET status = 'ARCHIVED' WHERE id = ?", sessionId);
    }

    public int incrementMessageCount(String sessionId, long now) {
        return jdbcTemplate.update(
            "UPDATE sys_agent_session SET message_count = message_count + 1, last_active_at = ? WHERE id = ?",
            now, sessionId);
    }

    public int autoSetIdle(String sessionId) {
        return jdbcTemplate.update(
            "UPDATE sys_agent_session SET status = 'IDLE' WHERE id = ?", sessionId);
    }

    // ── 消息 ────────────────────────────────────────────

    public void insertMessage(String sessionId, String role, String content,
                              String toolCallsJson, String toolResultsJson,
                              String threadId, long now) {
        jdbcTemplate.update(
            "INSERT INTO sys_agent_message (session_id, role, content, tool_calls, tool_results, thread_id, created_at) " +
            "VALUES (?::varchar, ?::varchar, ?::text, ?::jsonb, ?::jsonb, ?::varchar, ?)",
            sessionId, role, content, toolCallsJson, toolResultsJson, threadId, now);
    }

    public void insertSummaryMessage(String sessionId, String summary, long now) {
        jdbcTemplate.update(
            "INSERT INTO sys_agent_message (session_id, role, content, thread_id, created_at) " +
            "VALUES (?::varchar, 'system', ?::text, 'main', ?)",
            sessionId, summary, now);
    }

    public List<AgentSessionService.AgentMessage> findMessages(String sessionId, String threadId) {
        return jdbcTemplate.query(
            "SELECT id, session_id, role, content, tool_calls, tool_results, tokens, thread_id, created_at " +
            "FROM sys_agent_message WHERE session_id = ? AND thread_id = ? ORDER BY id ASC",
            MESSAGE_ROW_MAPPER, sessionId, threadId);
    }

    public Integer countMessages(String sessionId) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM sys_agent_message WHERE session_id = ?",
            Integer.class, sessionId);
    }

    public List<AgentSessionService.AgentMessage> findOldestMessages(String sessionId, int limit) {
        return jdbcTemplate.query(
            "SELECT id, session_id, role, content, tool_calls, tool_results, tokens, thread_id, created_at " +
            "FROM sys_agent_message WHERE session_id = ? ORDER BY id ASC LIMIT ?",
            MESSAGE_ROW_MAPPER, sessionId, limit);
    }

    public int deleteMessagesUpTo(String sessionId, long maxId) {
        return jdbcTemplate.update(
            "DELETE FROM sys_agent_message WHERE session_id = ? AND id <= ?",
            sessionId, maxId);
    }

    public int recalcMessageCount(String sessionId) {
        return jdbcTemplate.update(
            "UPDATE sys_agent_session SET message_count = (SELECT COUNT(*) FROM sys_agent_message WHERE session_id = ?) WHERE id = ?",
            sessionId, sessionId);
    }

    // ── RowMapper ───────────────────────────────────────

    private final RowMapper<AgentSessionService.AgentSession> SESSION_ROW_MAPPER = (rs, rowNum) -> {
        AgentSessionService.AgentSession s = new AgentSessionService.AgentSession();
        s.setId(rs.getString("id"));
        s.setAgentId(rs.getString("agent_id"));
        s.setUserId(rs.getString("user_id"));
        s.setTenantId(rs.getString("tenant_id"));
        s.setStatus(rs.getString("status"));
        s.setMessageCount(rs.getInt("message_count"));
        s.setCreatedAt(getLong(rs, "created_at"));
        s.setLastActiveAt(getLong(rs, "last_active_at"));
        return s;
    };

    private final RowMapper<AgentSessionService.AgentMessage> MESSAGE_ROW_MAPPER = (rs, rowNum) -> {
        AgentSessionService.AgentMessage m = new AgentSessionService.AgentMessage();
        m.setId(rs.getLong("id"));
        m.setSessionId(rs.getString("session_id"));
        m.setRole(rs.getString("role"));
        m.setContent(rs.getString("content"));
        m.setToolCalls(parseJsonListOfMap(rs.getString("tool_calls")));
        m.setToolResultsRaw(rs.getString("tool_results"));
        m.setTokens(getInt(rs, "tokens"));
        m.setThreadId(rs.getString("thread_id"));
        m.setCreatedAt(getLong(rs, "created_at"));
        return m;
    };

    private static Long getLong(ResultSet rs, String column) throws SQLException {
        long val = rs.getLong(column);
        return rs.wasNull() ? null : val;
    }

    private static Integer getInt(ResultSet rs, String column) throws SQLException {
        int val = rs.getInt(column);
        return rs.wasNull() ? null : val;
    }

    private static List<Map<String, Object>> parseJsonListOfMap(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return mapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            return null;
        }
    }
}
