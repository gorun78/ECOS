package com.chinacreator.gzcm.engine.cognitive2.dao;

import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

/**
 * B2-b 数据访问收口（架构铁律 §2.5 #1 / IR06）：{@code DecisionServiceImpl} 内联的
 * {@code ecos_decision} / {@code ecos_decision_causal_link} / {@code ecos_decision_policy} /
 * {@code ecos_decision_exception} / {@code ecos_provenance_entry} SQL 落点。
 *
 * <p>只搬不改：SQL 文本 / 表名 / 字段 / 参数序与原 Service 逐字节一致；
 * 行 → {@code Decision}/{@code ProvenanceEntry} 映射留在 Service 层。</p>
 */
@Repository
public class DecisionDao extends JdbcTemplateDaoSupport {

    /** 落库一条决策（自动记录溯源由 Service 层触发）。 */
    public void insertDecision(String id, String category, String scenario, String reasoning,
                               String outcome, double confidence, String decisionMaker, Timestamp now) {
        jdbcTemplate.update(
            "INSERT INTO ecos_decision (id, category, scenario, reasoning, outcome, " +
            "confidence, decision_maker, valid_from, created_at, updated_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            id, category, scenario, reasoning, outcome,
            confidence, decisionMaker, now, now, now
        );
    }

    /** 落库一条因果链接。 */
    public void insertCausalLink(String id, String sourceId, String targetId, String relationship) {
        jdbcTemplate.update(
            "INSERT INTO ecos_decision_causal_link (id, source_decision_id, target_decision_id, relationship) " +
            "VALUES (?, ?, ?, ?)",
            id, sourceId, targetId, relationship
        );
    }

    /** 相似决策：category 精确匹配 + scenario/reasoning 关键词 ILIKE（降级策略 SQL）。 */
    public List<Map<String, Object>> findSimilarDecisions(String category, String pattern, int maxResults) {
        String sql =
            "SELECT * FROM ecos_decision " +
            "WHERE category = ? " +
            "   OR scenario ILIKE ? " +
            "   OR reasoning ILIKE ? " +
            "ORDER BY created_at DESC LIMIT ?";
        return jdbcTemplate.queryForList(sql, category, pattern, pattern, maxResults);
    }

    /** 决策详情（按主键）；无记录返回空列表。 */
    public List<Map<String, Object>> findDecisionById(String id) {
        return jdbcTemplate.queryForList(
            "SELECT * FROM ecos_decision WHERE id = ?", id
        );
    }

    /** 上游祖先查询：谁指向了 currentId（target_decision_id = currentId → source 是祖先）。 */
    public List<Map<String, Object>> findParentLinks(String currentId) {
        return jdbcTemplate.queryForList(
            "SELECT source_decision_id FROM ecos_decision_causal_link " +
            "WHERE target_decision_id = ? AND relationship IN ('causes', 'precedes', 'enables') " +
            "ORDER BY created_at LIMIT 1",
            currentId
        );
    }

    /** 下游查询：currentId 指向了谁（source_decision_id = currentId → target 是下游）。 */
    public List<Map<String, Object>> findDownstreamLinks(String sourceId) {
        return jdbcTemplate.queryForList(
            "SELECT target_decision_id, relationship, weight FROM ecos_decision_causal_link " +
            "WHERE source_decision_id = ?",
            sourceId
        );
    }

    /** 查同 category 的 active policy。 */
    public List<Map<String, Object>> findPolicies(String category) {
        return jdbcTemplate.queryForList(
            "SELECT id, name, rules FROM ecos_decision_policy " +
            "WHERE category = ? AND status = 'active'",
            category
        );
    }

    /** 查未批准（pending）的例外。 */
    public List<Map<String, Object>> findPendingExceptions(String decisionId) {
        return jdbcTemplate.queryForList(
            "SELECT id, reason, status FROM ecos_decision_exception " +
            "WHERE decision_id = ? AND status = 'pending'",
            decisionId
        );
    }

    /** 落库一条溯源记录（timestamp = NOW()）。 */
    public void insertProvenance(String id, String entityType, String entityId, String sourceType,
                                 String sourceRef, String agent, String activity) {
        jdbcTemplate.update(
            "INSERT INTO ecos_provenance_entry (id, entity_type, entity_id, source_type, source_ref, agent, activity, timestamp) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, NOW())",
            id, entityType, entityId, sourceType, sourceRef, agent, activity
        );
    }

    /** 溯源查询（timestamp 序）。 */
    public List<Map<String, Object>> queryProvenance(String entityType, String entityId) {
        return jdbcTemplate.queryForList(
            "SELECT * FROM ecos_provenance_entry WHERE entity_type = ? AND entity_id = ? ORDER BY timestamp",
            entityType, entityId
        );
    }
}
