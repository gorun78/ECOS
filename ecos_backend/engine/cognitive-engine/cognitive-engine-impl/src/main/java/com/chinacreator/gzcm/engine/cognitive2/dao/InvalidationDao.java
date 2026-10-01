package com.chinacreator.gzcm.engine.cognitive2.dao;

import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * B2-b 数据访问收口（架构铁律 §2.5 #1 / IR06）：{@code CognitiveInvalidationConsumer}
 * 内联的假设失效联动 SQL 落点——{@code ecos_scenario_run} 关联作废 +
 * {@code ecos_cognitive_run_invalidation}（V130）留痕。
 *
 * <p>只搬不改：SQL 文本 / 表名 / 字段 / 参数序（含 JSONB {@code @> ?::jsonb} 包含匹配
 * 与 WHERE NOT EXISTS 幂等兜底）与原 Consumer 逐字节一致；
 * 事件发布 / 审计语义留在 Consumer 层。</p>
 */
@Repository
public class InvalidationDao extends JdbcTemplateDaoSupport {

    /**
     * 查引用该假设的未决推演 run（simulation_result->'assumptionRefs' JSONB 包含匹配）。
     *
     * @param statusA      可作废状态白名单[0]（SUCCEEDED）
     * @param statusB      可作废状态白名单[1]（SUCCEEDED_DEGRADED）
     * @param refsArrayJson assumptionRefs JSON 数组文本（Consumer 层预计算）
     */
    public List<Map<String, Object>> findCandidateRuns(String statusA, String statusB, String refsArrayJson) {
        return jdbcTemplate.queryForList(
            "SELECT id, status FROM ecos_scenario_run " +
            "WHERE is_deleted = 0 AND status IN (?, ?) " +
            "AND simulation_result IS NOT NULL " +
            "AND (simulation_result->'assumptionRefs') @> ?::jsonb " +
            "ORDER BY create_time DESC",
            statusA, statusB, refsArrayJson);
    }

    /** 单个 run 置 SUPERSEDED（命中 0 行 = 幂等跳过）。 */
    public int supersedeRun(String runId, LocalDateTime now, String statusA, String statusB) {
        return jdbcTemplate.update(
            "UPDATE ecos_scenario_run SET status = 'SUPERSEDED', update_time = ? " +
            "WHERE id = ? AND is_deleted = 0 AND status IN (?, ?)",
            now, runId, statusA, statusB);
    }

    /**
     * impact 留痕（uniq(event_id,run_id) 幂等：WHERE NOT EXISTS 已存在则不插）。
     *
     * @return 实际插入行数（>0 = 新留痕）
     */
    public int insertRunInvalidation(String impactId, String eventId, String hypothesisId, String runId,
                                     boolean autoDetected, LocalDateTime supersededAt, String detail) {
        return jdbcTemplate.update(
            "INSERT INTO ecos_cognitive_run_invalidation " +
            "(id, event_id, hypothesis_id, run_id, auto_detected, superseded_at, detail, create_by, update_by, is_deleted) " +
            "SELECT ?, ?, ?, ?, ?, ?, ?, 'system', 'system', 0 " +
            "WHERE NOT EXISTS (SELECT 1 FROM ecos_cognitive_run_invalidation " +
            "WHERE event_id = ? AND run_id = ? AND is_deleted = 0)",
            impactId, eventId, hypothesisId, runId, autoDetected, supersededAt,
            detail, eventId, runId);
    }
}
