package com.chinacreator.gzcm.engine.ai.service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.chinacreator.gzcm.engine.ai.dao.AgentSessionDao;
import com.fasterxml.jackson.core.JsonProcessingException;
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
 *
 * 数据访问统一收口至 {@link AgentSessionDao}（R2-B4-ai / 架构铁律 §2.5 #1 / IR06）。
 *
 * <p>上下文压缩不在本类：详见 {@link SessionCompressionService}（F06-09 token 阈值语义）。
 * 本类的消息读取走 {@code findContextMessages}，归档原件不进上下文组装。</p>
 */
@Service
public class AgentSessionService {

    private static final Logger log = LoggerFactory.getLogger(AgentSessionService.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    /** 30分钟空闲阈值 (毫秒) */
    private static final long IDLE_THRESHOLD_MS = 30 * 60 * 1000L;

    private final AgentSessionDao agentSessionDao;

    public AgentSessionService(AgentSessionDao agentSessionDao) {
        this.agentSessionDao = agentSessionDao;
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

        agentSessionDao.insertSession(sessionId, agentId, userId, tenantId, now);

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
        List<AgentSession> rows = agentSessionDao.findSessionById(sessionId);

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
     * 追加消息到会话 — 同时更新会话的 last_active_at 和 message_count。
     * 使用默认线程 'main'。
     */
    public AgentMessage appendMessage(String sessionId, String role, String content,
                                       List<Map<String, Object>> toolCalls,
                                       Object toolResults) {
        return appendMessage(sessionId, role, content, toolCalls, toolResults, "main");
    }

    /**
     * 追加消息到会话（指定线程） — 同时更新会话的 last_active_at 和 message_count
     */
    public AgentMessage appendMessage(String sessionId, String role, String content,
                                       List<Map<String, Object>> toolCalls,
                                       Object toolResults,
                                       String threadId) {
        long now = System.currentTimeMillis();

        String toolCallsJson = toJson(toolCalls);
        String toolResultsJson = (toolResults != null) ? toJson(toolResults) : null;
        String tid = threadId != null && !threadId.isBlank() ? threadId : "main";

        agentSessionDao.insertMessage(sessionId, role, content, toolCallsJson, toolResultsJson, tid, now);

        // 更新会话计数与活跃时间
        agentSessionDao.incrementMessageCount(sessionId, now);

        log.debug("Message appended: sessionId={} role={} threadId={} toolCalls={} toolResults={}",
            sessionId, role, tid, toolCalls != null, toolResults != null);

        // 构建返回对象 (id 由数据库序列生成，此处用 -1 占位)
        AgentMessage msg = new AgentMessage();
        msg.setId(-1L);
        msg.setSessionId(sessionId);
        msg.setRole(role);
        msg.setContent(content);
        msg.setToolCalls(toolCalls);
        msg.setToolResults(toolResults);
        msg.setThreadId(tid);
        msg.setCreatedAt(now);
        return msg;
    }

    /**
     * 查所有消息按时间正序（默认线程 'main'）
     */
    public List<AgentMessage> getMessages(String sessionId) {
        return getMessages(sessionId, "main");
    }

    /**
     * 查指定线程的所有消息按时间正序
     */
    public List<AgentMessage> getMessages(String sessionId, String threadId) {
        String tid = threadId != null && !threadId.isBlank() ? threadId : "main";
        return agentSessionDao.findContextMessages(sessionId, tid);
    }

    // ═══════════════════════════════════════════════════
    //  生命周期状态机
    // ═══════════════════════════════════════════════════

    /**
     * 将所有超过30分钟无活动的 ACTIVE 会话标记为 IDLE
     */
    public int markIdleSessions() {
        long cutoff = System.currentTimeMillis() - IDLE_THRESHOLD_MS;
        int affected = agentSessionDao.markIdleSessions(cutoff);
        if (affected > 0) {
            log.info("Marked {} sessions as IDLE", affected);
        }
        return affected;
    }

    /**
     * 将指定会话标记为 EXPIRED
     */
    public boolean expireSession(String sessionId) {
        int affected = agentSessionDao.expireSession(sessionId);
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
        int affected = agentSessionDao.archiveSession(sessionId);
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
        return agentSessionDao.listByStatus(status);
    }

    /**
     * 按 agent 查询会话列表
     */
    public List<AgentSession> listByAgent(String agentId) {
        return agentSessionDao.listByAgent(agentId);
    }

    // ═══════════════════════════════════════════════════
    //  上下文压缩 — 已迁出本类（详见 SessionCompressionService）
    // ═══════════════════════════════════════════════════

    // X-29~X-33 消灭：本类不再保留"条数阈值 + 200 字截断 + 物理 DELETE"形态的 compressHistory。
    // 压缩判定/执行单源 = {@link SessionCompressionService#compress}（token 阈值 + LLM 结构化摘要
    // + 引用保护 + 逻辑归档），由 AgentLoopService 每轮入模前调用。本类只管会话生命周期与消息读写。

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
            agentSessionDao.autoSetIdle(session.getId());
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
        private String threadId;
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

        public String getThreadId() { return threadId; }
        public void setThreadId(String threadId) { this.threadId = threadId; }

        public Long getCreatedAt() { return createdAt; }
        public void setCreatedAt(Long createdAt) { this.createdAt = createdAt; }
    }
}
