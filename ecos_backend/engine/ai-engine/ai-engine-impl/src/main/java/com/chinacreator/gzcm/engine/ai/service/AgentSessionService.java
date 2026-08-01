package com.chinacreator.gzcm.engine.ai.service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Agent 会话持久化 + 消息历史管理
 *
 * 会话生命周期:
 *   ACTIVE → IDLE (30min无活动) → EXPIRED → ARCHIVED (不物理删除)
 *
 * 数据存储:
 *   sys_agent_session  — 会话主表
 *   sys_agent_message  — 消息表
 */
@Service
public class AgentSessionService {

    private static final Logger log = LoggerFactory.getLogger(AgentSessionService.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    /** 30分钟空闲阈值 (毫秒) */
    private static final long IDLE_THRESHOLD_MS = 30 * 60 * 1000L;

    private final JdbcTemplate jdbc;

    public AgentSessionService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ═══════════════════════════════════════════════════
    //  会话生命周期
    // ═══════════════════════════════════════════════════

    /**
     * 创建新会话
     */
    public AgentSession createSession(String agentId, String userId, String tenantId) {
        String sessionId = "sess-" + UUID.randomUUID().toString().replace("-", "");
        long now = System.currentTimeMillis();

        jdbc.update(
            "INSERT INTO sys_agent_session (id, agent_id, user_id, tenant_id, status, message_count, created_at, last_active_at) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            sessionId, agentId, userId, tenantId, "ACTIVE", 0, now, now
        );

        log.info("Session created: id={} agentId={} userId={} tenantId={}", sessionId, agentId, userId, tenantId);

        AgentSession session = new AgentSession();
        session.setId(sessionId);
        session.setAgentId(agentId);
        session.setUserId(userId);
        session.setTenantId(tenantId);
        session.setStatus("ACTIVE");
        session.setMessageCount(0);
        session.setCreatedAt(now);
        session.setLastActiveAt(now);
        session.setMessages(Collections.emptyList());
        return session;
    }

    /**
     * 查询会话 + 加载消息历史
     */
    public AgentSession getSession(String sessionId) {
        List<AgentSession> rows = jdbc.query(
            "SELECT id, agent_id, user_id, tenant_id, status, message_count, created_at, last_active_at "
            + "FROM sys_agent_session WHERE id = ?",
            new SessionRowMapper(), sessionId
        );

        if (rows.isEmpty()) {
            return null;
        }

        AgentSession session = rows.get(0);
        // 加载消息
        session.setMessages(getMessages(sessionId));

        // 静默更新生命周期状态
        refreshLifecycle(session);

        return session;
    }

    /**
     * 追加消息到会话 — 同时更新会话的 last_active_at 和 message_count
     */
    public AgentMessage appendMessage(String sessionId, String role, String content,
                                       List<Map<String, Object>> toolCalls,
                                       Object toolResults) {
        long now = System.currentTimeMillis();

        String toolCallsJson = toJson(toolCalls);
        String toolResultsJson = (toolResults != null) ? toJson(toolResults) : null;

        jdbc.update(
            "INSERT INTO sys_agent_message (session_id, role, content, tool_calls, tool_results, created_at) "
            + "VALUES (?::varchar, ?::varchar, ?::text, ?::jsonb, ?::jsonb, ?)",
            sessionId, role, content, toolCallsJson, toolResultsJson, now
        );

        // 更新会话计数与活跃时间
        jdbc.update(
            "UPDATE sys_agent_session SET message_count = message_count + 1, last_active_at = ? WHERE id = ?",
            now, sessionId
        );

        log.debug("Message appended: sessionId={} role={} toolCalls={} toolResults={}",
            sessionId, role, toolCalls != null, toolResults != null);

        // 构建返回对象 (id 由数据库序列生成，此处用 -1 占位)
        AgentMessage msg = new AgentMessage();
        msg.setId(-1L);
        msg.setSessionId(sessionId);
        msg.setRole(role);
        msg.setContent(content);
        msg.setToolCalls(toolCalls);
        msg.setToolResults(toolResults);
        msg.setCreatedAt(now);
        return msg;
    }

    /**
     * 查所有消息按时间正序
     */
    public List<AgentMessage> getMessages(String sessionId) {
        return jdbc.query(
            "SELECT id, session_id, role, content, tool_calls, tool_results, tokens, created_at "
            + "FROM sys_agent_message WHERE session_id = ? ORDER BY id ASC",
            new MessageRowMapper(), sessionId
        );
    }

    // ═══════════════════════════════════════════════════
    //  生命周期状态机
    // ═══════════════════════════════════════════════════

    /**
     * 将所有超过30分钟无活动的 ACTIVE 会话标记为 IDLE
     */
    public int markIdleSessions() {
        long cutoff = System.currentTimeMillis() - IDLE_THRESHOLD_MS;
        int affected = jdbc.update(
            "UPDATE sys_agent_session SET status = 'IDLE' WHERE status = 'ACTIVE' AND last_active_at < ?",
            cutoff
        );
        if (affected > 0) {
            log.info("Marked {} sessions as IDLE", affected);
        }
        return affected;
    }

    /**
     * 将指定会话标记为 EXPIRED
     */
    public boolean expireSession(String sessionId) {
        int affected = jdbc.update(
            "UPDATE sys_agent_session SET status = 'EXPIRED' WHERE id = ?", sessionId
        );
        if (affected > 0) {
            log.info("Session expired: {}", sessionId);
            return true;
        }
        return false;
    }

    /**
     * 将指定会话标记为 ARCHIVED (软删除)
     */
    public boolean archiveSession(String sessionId) {
        int affected = jdbc.update(
            "UPDATE sys_agent_session SET status = 'ARCHIVED' WHERE id = ?", sessionId
        );
        if (affected > 0) {
            log.info("Session archived: {}", sessionId);
            return true;
        }
        return false;
    }

    /**
     * 按状态查询会话列表
     */
    public List<AgentSession> listByStatus(String status) {
        return jdbc.query(
            "SELECT id, agent_id, user_id, tenant_id, status, message_count, created_at, last_active_at "
            + "FROM sys_agent_session WHERE status = ? ORDER BY last_active_at DESC",
            new SessionRowMapper(), status
        );
    }

    /**
     * 按 agent 查询会话列表
     */
    public List<AgentSession> listByAgent(String agentId) {
        return jdbc.query(
            "SELECT id, agent_id, user_id, tenant_id, status, message_count, created_at, last_active_at "
            + "FROM sys_agent_session WHERE agent_id = ? ORDER BY last_active_at DESC",
            new SessionRowMapper(), agentId
        );
    }

    // ═══════════════════════════════════════════════════
    //  内部工具
    // ═══════════════════════════════════════════════════

    /** 静默刷新生命周期 — ACTIVE → IDLE (超过30min) */
    private void refreshLifecycle(AgentSession session) {
        if (session == null) return;
        long now = System.currentTimeMillis();
        if ("ACTIVE".equals(session.getStatus())
                && session.getLastActiveAt() != null
                && (now - session.getLastActiveAt()) > IDLE_THRESHOLD_MS) {
            jdbc.update("UPDATE sys_agent_session SET status = 'IDLE' WHERE id = ?", session.getId());
            session.setStatus("IDLE");
            log.debug("Session {} auto-transitioned to IDLE", session.getId());
        }
    }

    private String toJson(Object obj) {
        if (obj == null) return null;
        try {
            return mapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.warn("JSON serialization failed: {}", e.getMessage());
            return null;
        }
    }

    // ═══════════════════════════════════════════════════
    //  RowMapper
    // ═══════════════════════════════════════════════════

    private static class SessionRowMapper implements RowMapper<AgentSession> {
        @Override
        public AgentSession mapRow(ResultSet rs, int rowNum) throws SQLException {
            AgentSession s = new AgentSession();
            s.setId(rs.getString("id"));
            s.setAgentId(rs.getString("agent_id"));
            s.setUserId(rs.getString("user_id"));
            s.setTenantId(rs.getString("tenant_id"));
            s.setStatus(rs.getString("status"));
            s.setMessageCount(rs.getInt("message_count"));
            s.setCreatedAt(getLong(rs, "created_at"));
            s.setLastActiveAt(getLong(rs, "last_active_at"));
            return s;
        }

        private Long getLong(ResultSet rs, String column) throws SQLException {
            long val = rs.getLong(column);
            return rs.wasNull() ? null : val;
        }
    }

    private static class MessageRowMapper implements RowMapper<AgentMessage> {
        @Override
        public AgentMessage mapRow(ResultSet rs, int rowNum) throws SQLException {
            AgentMessage m = new AgentMessage();
            m.setId(rs.getLong("id"));
            m.setSessionId(rs.getString("session_id"));
            m.setRole(rs.getString("role"));
            m.setContent(rs.getString("content"));
            m.setToolCalls(parseJsonListOfMap(rs.getString("tool_calls")));
            m.setToolResultsRaw(rs.getString("tool_results"));
            m.setTokens(getInt(rs, "tokens"));
            m.setCreatedAt(getLong(rs, "created_at"));
            return m;
        }

        private Integer getInt(ResultSet rs, String column) throws SQLException {
            int val = rs.getInt(column);
            return rs.wasNull() ? null : val;
        }

        private Long getLong(ResultSet rs, String column) throws SQLException {
            long val = rs.getLong(column);
            return rs.wasNull() ? null : val;
        }

        private List<Map<String, Object>> parseJsonListOfMap(String json) {
            if (json == null || json.isBlank()) return null;
            try {
                return mapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
            } catch (Exception e) {
                log.debug("Failed to parse tool_calls JSON (len={}): {}", json.length(), e.getMessage());
                return null;
            }
        }
    }

    // ═══════════════════════════════════════════════════
    //  POJO: AgentSession
    // ═══════════════════════════════════════════════════

    public static class AgentSession {
        private String id;
        private String agentId;
        private String userId;
        private String tenantId;
        private String status;
        private int messageCount;
        private Long createdAt;
        private Long lastActiveAt;
        private List<AgentMessage> messages;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getAgentId() { return agentId; }
        public void setAgentId(String agentId) { this.agentId = agentId; }

        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }

        public String getTenantId() { return tenantId; }
        public void setTenantId(String tenantId) { this.tenantId = tenantId; }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }

        public int getMessageCount() { return messageCount; }
        public void setMessageCount(int messageCount) { this.messageCount = messageCount; }

        public Long getCreatedAt() { return createdAt; }
        public void setCreatedAt(Long createdAt) { this.createdAt = createdAt; }

        public Long getLastActiveAt() { return lastActiveAt; }
        public void setLastActiveAt(Long lastActiveAt) { this.lastActiveAt = lastActiveAt; }

        public List<AgentMessage> getMessages() { return messages; }
        public void setMessages(List<AgentMessage> messages) { this.messages = messages; }
    }

    // ═══════════════════════════════════════════════════
    //  POJO: AgentMessage
    // ═══════════════════════════════════════════════════

    public static class AgentMessage {
        private Long id;
        private String sessionId;
        private String role;
        private String content;
        private List<Map<String, Object>> toolCalls;
        private Object toolResults;
        private String toolResultsRaw; // 原始 JSONB 字符串，供前端使用
        private Integer tokens;
        private Long createdAt;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }

        public String getSessionId() { return sessionId; }
        public void setSessionId(String sessionId) { this.sessionId = sessionId; }

        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }

        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }

        public List<Map<String, Object>> getToolCalls() { return toolCalls; }
        public void setToolCalls(List<Map<String, Object>> toolCalls) { this.toolCalls = toolCalls; }

        public Object getToolResults() { return toolResults; }
        public void setToolResults(Object toolResults) { this.toolResults = toolResults; }

        public String getToolResultsRaw() { return toolResultsRaw; }
        public void setToolResultsRaw(String toolResultsRaw) { this.toolResultsRaw = toolResultsRaw; }

        public Integer getTokens() { return tokens; }
        public void setTokens(Integer tokens) { this.tokens = tokens; }

        public Long getCreatedAt() { return createdAt; }
        public void setCreatedAt(Long createdAt) { this.createdAt = createdAt; }
    }
}
