package com.chinacreator.gzcm.engine.ai.dao;

import java.util.List;
import java.util.Map;

import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.stereotype.Repository;

/**
 * Guardrail 裁决台账（详细设计-06 F06-03 / V204 {@code ecos_ai.ecos_ai_guardrail_decision_ledger}）。
 *
 * <p>定性：裁决事件的索引化落点，与 Kafka {@code ecos.audit} 的 {@code GUARDRAIL_EVAL} 事件同批双写，
 * 供 PRD-06 §1.5-1「无裁决记录的工具调用次数 = 0」的对账查询。
 * {@code policy_id}/{@code obligations} 只是 security-engine 裁决响应的时点缓存，
 * <b>不作为任何运行期判定输入</b>（R-18 ②，禁策略副本语义）。</p>
 */
@Repository
public class GuardrailDecisionLedgerDao extends JdbcTemplateDaoSupport {

    private static final String INSERT_SQL =
            "INSERT INTO ecos_ai.ecos_ai_guardrail_decision_ledger "
            + "(id, trace_id, session_id, tool_key, decision, policy_id, obligations, latency_ms, "
            + " subject_snapshot, domain, version_no, is_deleted, create_by, create_time) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'default', '1', 0, ?, CURRENT_TIMESTAMP)";

    /** 写入一条裁决台账；id 由应用侧生成（MC01）。 */
    public void insert(String id, String traceId, String sessionId, String toolKey,
                       String decision, String policyId, String obligationsJson,
                       int latencyMs, String subjectSnapshot, String createBy) {
        insert(INSERT_SQL, id, traceId, sessionId, toolKey, decision, policyId,
                obligationsJson, latencyMs, subjectSnapshot, createBy);
    }

    /** 按 traceId 取本次链路的全部裁决记录（回放/对账用）。 */
    public List<Map<String, Object>> findByTrace(String traceId) {
        return jdbcTemplate.queryForList(
                "SELECT id, trace_id, session_id, tool_key, decision, policy_id, obligations, "
                + "latency_ms, subject_snapshot, create_time "
                + "FROM ecos_ai.ecos_ai_guardrail_decision_ledger "
                + "WHERE trace_id = ? AND is_deleted = 0 ORDER BY create_time",
                traceId);
    }

    /** 对账：统计指定时间窗内的裁决条数（按 decision 分组）。 */
    public List<Map<String, Object>> countByDecisionSince(String sinceIso) {
        return jdbcTemplate.queryForList(
                "SELECT decision, COUNT(1) AS cnt "
                + "FROM ecos_ai.ecos_ai_guardrail_decision_ledger "
                + "WHERE is_deleted = 0 AND create_time >= CAST(? AS TIMESTAMP) "
                + "GROUP BY decision",
                sinceIso);
    }
}
