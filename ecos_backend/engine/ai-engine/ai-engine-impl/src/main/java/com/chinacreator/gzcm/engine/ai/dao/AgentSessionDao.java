package com.chinacreator.gzcm.engine.ai.dao;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
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
 *
 * <p><b>显式 bean name</b>：{@code @Repository("engineAgentSessionDao")}。
 * 与 {@code com.chinacreator.gzcm.services.agent.dao.AgentSessionDao}
 * （bean 名 {@code agentSessionDao}，长期/企业级记忆持久化）默认同 simple-name
 * 冲突。gateway 单 fat-JAR 同时命中 ai-engine + services.agent.runtime/model，
 * 不显式命名会触发 {@code ConflictingBeanDefinitionException}（2026-10-02 Gate C 实测）。
 */
@Repository("engineAgentSessionDao")
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

    public List<AgentSessionService.AgentMessage> findMessages(String sessionId, String threadId) {
        return jdbcTemplate.query(
            "SELECT id, session_id, role, content, tool_calls, tool_results, tokens, thread_id, created_at " +
            "FROM sys_agent_message WHERE session_id = ? AND thread_id = ? ORDER BY id ASC",
            MESSAGE_ROW_MAPPER, sessionId, threadId);
    }

    // ── F06-09 压缩治理（逻辑归档，禁物理 DELETE；列面见迁移脚本 V202）──

    /** 治理列探测结果缓存：一旦就位不再重复探测（未就位时每次重探，V202 落库后免重启）。 */
    private volatile Boolean governanceColumnsPresent;

    /** 上下文读取面：治理列就位 ⇒ 只取活行；未就位 ⇒ 回落全量（见 {@link #findContextMessages}）。 */
    public boolean governanceColumnsReady() {
        if (Boolean.TRUE.equals(governanceColumnsPresent)) {
            return true;
        }
        Integer present = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM information_schema.columns " +
            "WHERE table_schema = current_schema() AND table_name = 'sys_agent_message' " +
            "AND column_name IN ('compressed','is_deleted','compression_batch_id','key_refs','token_count','tool_call_ref_ids')",
            Integer.class);
        boolean ready = present != null && present >= 6;
        if (ready) {
            governanceColumnsPresent = Boolean.TRUE;
        }
        return ready;
    }

    /**
     * 上下文组装/压缩判定的统一读取面。
     *
     * <p>治理列就位后归档原件（{@code compressed=1}）不得再进上下文，否则压缩等于没做；
     * V202 尚未对库执行时活行谓词会让整条读路径报错，故回落全量读 —— 此时压缩链路自身
     * 已在 {@code SCHEMA_UNAVAILABLE} 处停止判定，库内不会出现归档行，回落是等价的。</p>
     */
    public List<AgentSessionService.AgentMessage> findContextMessages(String sessionId, String threadId) {
        return governanceColumnsReady()
                ? findActiveMessages(sessionId, threadId)
                : findMessages(sessionId, threadId);
    }

    /** 活消息（未压缩归档、未逻辑删）正序 —— 压缩判定的唯一严格读取面。 */
    public List<AgentSessionService.AgentMessage> findActiveMessages(String sessionId, String threadId) {
        return jdbcTemplate.query(
            "SELECT id, session_id, role, content, tool_calls, tool_results, tokens, thread_id, created_at " +
            "FROM sys_agent_message " +
            "WHERE session_id = ? AND thread_id = ? " +
            "AND COALESCE(compressed, 0) = 0 AND COALESCE(is_deleted, 0) = 0 " +
            "ORDER BY id ASC",
            MESSAGE_ROW_MAPPER, sessionId, threadId);
    }

    /**
     * 逻辑归档（替代旧 {@code DELETE FROM sys_agent_message}，X-31）。
     *
     * <p>只置标记 + 挂批次号，历史原件保留 —— 这是 F06-08 回放与 §2.2-2 语义抽检的前提。
     * 幂等：已归档行不再命中（WHERE 带活消息谓词）。</p>
     */
    public int archiveMessagesByIds(String sessionId, List<Long> messageIds, String batchId) {
        if (messageIds == null || messageIds.isEmpty()) {
            return 0;
        }
        String placeholders = String.join(", ", Collections.nCopies(messageIds.size(), "?"));
        Object[] args = new Object[messageIds.size() + 2];
        args[0] = batchId;
        args[1] = sessionId;
        for (int i = 0; i < messageIds.size(); i++) {
            args[i + 2] = messageIds.get(i);
        }
        return jdbcTemplate.update(
            "UPDATE sys_agent_message SET compressed = 1, is_deleted = 1, compression_batch_id = ? " +
            "WHERE session_id = ? AND compressed = 0 AND id IN (" + placeholders + ")",
            args);
    }

    /**
     * 插入摘要行（引用式，非替换式）：{@code key_refs} 为逗号分引用串（MC02 禁 jsonb），
     * {@code token_count} 记本行估算 token，供下一轮阈值判定。
     */
    public int insertSummaryMessage(String sessionId, String summary, String keyRefs,
                                    String batchId, int tokenCount, long now) {
        return jdbcTemplate.update(
            "INSERT INTO sys_agent_message (session_id, role, content, thread_id, " +
            "compression_batch_id, key_refs, token_count, created_at) " +
            "VALUES (?::varchar, 'system', ?::text, 'main', ?::varchar, ?::text, ?, ?)",
            sessionId, summary, batchId, keyRefs, tokenCount, now);
    }

    /** 会话面压缩模式（LLM_SUMMARY | TRUNCATE_FALLBACK，SSE done 帧同源）。 */
    public int updateSessionCompressionMode(String sessionId, String mode) {
        return jdbcTemplate.update(
            "UPDATE sys_agent_session SET compression_mode = ? WHERE id = ?", mode, sessionId);
    }

    public int recalcMessageCount(String sessionId) {
        return jdbcTemplate.update(
            "UPDATE sys_agent_session SET message_count = (" +
            "SELECT CAST(COUNT(*) AS INTEGER) FROM sys_agent_message " +
            "WHERE session_id = ? AND COALESCE(compressed, 0) = 0 AND COALESCE(is_deleted, 0) = 0) WHERE id = ?",
            sessionId, sessionId);
    }

    // ── 会话预算读数（V202 治理列，P1.3 #12 context）──────────────

    /** 已逻辑归档（compressed=1）的消息条数读面（P1.3 #12 context 的 compressedTurns 承载）。 */
    public int countCompressedMessages(String sessionId) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM sys_agent_message " +
            "WHERE session_id = ? AND COALESCE(compressed, 0) = 1",
            Integer.class, sessionId);
        return count != null ? count : 0;
    }

    /** V202 会话治理列面的探测缓存（与消息面 {@link #governanceColumnsReady} 同口径，各自缓存不串探）。 */
    private volatile Boolean sessionGovernanceColumnsPresent;

    /**
     * V202 会话治理列面是否就位（{@code total_prompt_tokens}/{@code total_completion_tokens}/
     * {@code compression_mode}）。V202 未实跑时这些列不存在，读面必须先判而非抛错掩蔽
     * （与 {@link #governanceColumnsReady} 的探针形态同族）。
     */
    public boolean sessionGovernanceColumnsReady() {
        if (Boolean.TRUE.equals(sessionGovernanceColumnsPresent)) {
            return true;
        }
        Integer present = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM information_schema.columns " +
            "WHERE table_schema = current_schema() AND table_name = 'sys_agent_session' " +
            "AND column_name IN ('total_prompt_tokens','total_completion_tokens','compression_mode')",
            Integer.class);
        boolean ready = present != null && present >= 3;
        if (ready) {
            sessionGovernanceColumnsPresent = Boolean.TRUE;
        }
        return ready;
    }

    /**
     * 会话面的真实计量读数（V202 列，网关计量点的权威记账）与压缩模式。
     *
     * <p>返回三元素数组 {@code [totalPromptTokens, totalCompletionTokens, compressionMode]}；
     * 治理列未就位（V202 未实跑）⇒ 全 null —— 调用方须按"读数不可得"处理，
     * 禁按 0 冒充"零用量"。</p>
     */
    public Object[] tokenStats(String sessionId) {
        if (!sessionGovernanceColumnsReady()) {
            return new Object[]{null, null, null};
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT total_prompt_tokens, total_completion_tokens, compression_mode " +
            "FROM sys_agent_session WHERE id = ?", sessionId);
        if (rows.isEmpty()) {
            return new Object[]{null, null, null};
        }
        Map<String, Object> row = rows.get(0);
        return new Object[]{
            row.get("total_prompt_tokens"),
            row.get("total_completion_tokens"),
            row.get("compression_mode")};
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
