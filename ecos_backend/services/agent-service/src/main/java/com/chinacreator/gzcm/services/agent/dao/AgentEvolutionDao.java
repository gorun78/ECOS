package com.chinacreator.gzcm.services.agent.dao;

import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

/**
 * R2-B4b 数据访问收口（架构铁律 §2.5 #1 / IR06）：agent-service evolution 记录
 * （{@code ecos_ai.ecos_evolution_log}）SQL 落点。
 *
 * <p>只搬不改：SQL 文本 / 表名 / 字段 / 参数序与原 EvolutionOrchestrator 逐字节一致。</p>
 *
 * <p>承接原文件：evolution/EvolutionOrchestrator.java。</p>
 */
@Repository
public class AgentEvolutionDao extends JdbcTemplateDaoSupport {

    /** 插入一条 evolution 阶段日志（STARTED）。 */
    public int insertEvolutionLog(String logId, String missionId, String stage, String agentId, String contextJson) {
        return jdbcTemplate.update(
            "INSERT INTO ecos_ai.ecos_evolution_log (id, mission_id, stage, agent_id, input_context, status, started_at) VALUES (?,?,?,?,?::jsonb,'STARTED',NOW())",
            logId, missionId, stage, agentId, contextJson);
    }

    /** 阶段完成。 */
    public int markEvolutionCompleted(String outputJson, String logId) {
        return jdbcTemplate.update(
            "UPDATE ecos_ai.ecos_evolution_log SET status='COMPLETED', output_result=?::jsonb, completed_at=NOW() WHERE id=?",
            outputJson, logId);
    }

    /** 阶段失败。 */
    public int markEvolutionFailed(String errorJson, String logId) {
        return jdbcTemplate.update(
            "UPDATE ecos_ai.ecos_evolution_log SET status='FAILED', output_result=?::jsonb, completed_at=NOW() WHERE id=?",
            errorJson, logId);
    }

    /** 某 mission 的 evolution 日志（started_at 升序）。 */
    public List<Map<String, Object>> listEvolutionLog(String missionId) {
        return jdbcTemplate.queryForList(
            "SELECT id, mission_id, stage, agent_id, status, started_at, completed_at FROM ecos_ai.ecos_evolution_log WHERE mission_id = ? ORDER BY started_at",
            missionId);
    }
}
