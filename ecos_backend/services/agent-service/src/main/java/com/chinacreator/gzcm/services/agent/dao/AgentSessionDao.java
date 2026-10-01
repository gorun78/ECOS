package com.chinacreator.gzcm.services.agent.dao;

import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import com.chinacreator.gzcm.services.agent.runtime.model.MemoryLayer;
import com.chinacreator.gzcm.services.agent.runtime.model.MemoryRecord;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

/**
 * R2-B4b 数据访问收口（架构铁律 §2.5 #1 / IR06）：agent-service 会话/记忆持久化
 * （{@code ecos_ai.agent_memory}）SQL 落点。
 *
 * <p>只搬不改：SQL 文本 / 表名 / 字段 / 参数序与原 MemoryServiceImpl 逐字节一致；
 * 行 → MemoryRecord 归一化逻辑随搬。</p>
 *
 * <p>承接原文件：memory/MemoryServiceImpl.java。</p>
 */
@Repository
public class AgentSessionDao extends JdbcTemplateDaoSupport {

    /** 落库一条长期/企业级记忆。 */
    public int insertMemory(String id, String agentId, String sessionId, String layer,
                            String content, Instant timestamp) {
        return jdbcTemplate.update(
            "INSERT INTO ecos_ai.agent_memory (id, agent_id, session_id, layer, content, created_at) " +
            "VALUES (?, ?, ?, ?, ?, ?)",
            id, agentId, sessionId, layer, content, Timestamp.from(timestamp));
    }

    /** 某 agent 某层记忆（created_at 倒序，topK 限）。行归一化随搬自原 queryFromDb。 */
    public List<MemoryRecord> listMemory(String agentId, String layer, int topK) {
        return jdbcTemplate.query(
            "SELECT id, agent_id, session_id, layer, content, created_at " +
            "FROM ecos_ai.agent_memory WHERE agent_id = ? AND layer = ? " +
            "ORDER BY created_at DESC LIMIT ?",
            (rs, rowNum) -> {
                MemoryRecord r = new MemoryRecord();
                r.setId(rs.getString("id"));
                r.setAgentId(rs.getString("agent_id"));
                r.setSessionId(rs.getString("session_id"));
                r.setLayer(MemoryLayer.valueOf(rs.getString("layer")));
                r.setContent(rs.getString("content"));
                Timestamp ts = rs.getTimestamp("created_at");
                if (ts != null) {
                    r.setTimestamp(ts.toInstant());
                }
                return r;
            },
            agentId, layer, topK);
    }
}
