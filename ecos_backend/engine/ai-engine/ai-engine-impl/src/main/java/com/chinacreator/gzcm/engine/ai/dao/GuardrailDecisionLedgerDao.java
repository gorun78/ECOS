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

    /**
     * 台账查询（对账面，详设-06 §5.2 / 详设说明书 §9.3.1 #14）：按谓词读的<b>单源</b>读面，
     * 供 {@code GET /api/v1/agent/guardrail-decisions} 的 {@code traceId}/{@code toolKey}/
     * {@code decision}/{@code from}/{@code to} 五项过滤。
     *
     * <p>口径：条件全为「可空即不约束」；排序固定 {@code create_time DESC, id DESC}（同刻按
     * 应用侧 UUID 定序，结果确定可复现）；{@code limit} 由调用方夹紧（契约单 500 Ceiling），
     * 本方法只收已夹紧的正整数。谓词带 {@code is_deleted = 0}（DR05），列面为契约七键投影
     * （{@code decision/policyRef/latencyMs/createTime} 之外附 {@code traceId/sessionId/toolKey}
     * 供对账跳转），<b>不投影</b> {@code obligations}/{@code subject_snapshot}
     * （时点缓存含主体快照，入读响应＝把标识符搬进业务面，与 R-18 ② 禁策略副本同红线）。</p>
     */
    public List<Map<String, Object>> listFiltered(String traceId, String toolKey,
                                                  String decision, String fromIso, String toIso, int limit) {
        StringBuilder sb = new StringBuilder(
                "SELECT trace_id, session_id, tool_key, decision, policy_id, latency_ms, create_time "
                + "FROM ecos_ai.ecos_ai_guardrail_decision_ledger WHERE is_deleted = 0");
        List<Object> params = new java.util.ArrayList<>(5);
        if (traceId != null && !traceId.isBlank()) {
            sb.append(" AND trace_id = ?");
            params.add(traceId);
        }
        if (toolKey != null && !toolKey.isBlank()) {
            sb.append(" AND tool_key = ?");
            params.add(toolKey);
        }
        if (decision != null && !decision.isBlank()) {
            sb.append(" AND decision = ?");
            params.add(decision);
        }
        if (fromIso != null && !fromIso.isBlank()) {
            sb.append(" AND create_time >= CAST(? AS TIMESTAMP)");
            params.add(fromIso);
        }
        if (toIso != null && !toIso.isBlank()) {
            sb.append(" AND create_time < CAST(? AS TIMESTAMP)");
            params.add(toIso);
        }
        sb.append(" ORDER BY create_time DESC, id DESC LIMIT ?");
        params.add(limit);
        return jdbcTemplate.queryForList(sb.toString(), params.toArray());
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
