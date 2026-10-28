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

    // ── 差距分析附录A 第 19 条（2026-10-28 #146v 追加，只追加本段，上方行零改写）──

    /**
     * 审批链查询（决策依据解析面的"授权范围/审批"段供数，V103 五元组之 ApprovalChain）。
     *
     * <p><b>新增读一律 schema 限定</b>（数据库访问规范 §四附则1）：本段两条查询写
     * {@code ecos_cognitive.} 全限定表名；上方 B2-b 收口时逐字节搬迁的六条 legacy 查询仍是
     * 裸表名（F05-13 M1 存量收敛面，已登记，不在本批改动范围）。排序 = {@code level} 后
     * {@code created_at}，同决策多级审批按层级定序，禁依赖物理行序。</p>
     */
    public List<Map<String, Object>> findApprovalsByDecision(String decisionId) {
        return jdbcTemplate.queryForList(
            "SELECT id, approver, level, status, comment, created_at " +
            "FROM ecos_cognitive.ecos_decision_approval WHERE decision_id = ? " +
            "ORDER BY level NULLS LAST, created_at",
            decisionId
        );
    }

    /** 先例查询（决策依据解析面的"先例引用"段供数，V103 五元组之 Precedent）。 */
    public List<Map<String, Object>> findPrecedentsByDecision(String decisionId) {
        return jdbcTemplate.queryForList(
            "SELECT id, similar_decision_id, similarity, note, created_at " +
            "FROM ecos_cognitive.ecos_decision_precedent WHERE decision_id = ? " +
            "ORDER BY similarity DESC NULLS LAST, created_at",
            decisionId
        );
    }

    /**
     * 适用策略查询（决策依据解析面的"约束与口径"供数）。
     *
     * <p>与上方 legacy {@code findPolicies(String)} 谓词同形（{@code category} 精确 + active），
     * 但列面收窄：<b>不出 {@code rules} 载体列</b>（jsonb，本面不解析规则体，禁把未解析 JSON
     * 冒充结构化依据段），并补出 {@code version} 供"策略版本"事实呈现。两条查询各自服务
     * 不同读面（legacy 面走合规门判定需要规则体，本面是依据解析），故不合并。</p>
     */
    public List<Map<String, Object>> findActivePoliciesForBasis(String category) {
        return jdbcTemplate.queryForList(
            "SELECT id, name, category, version, status, created_at " +
            "FROM ecos_cognitive.ecos_decision_policy WHERE category = ? AND status = 'active' " +
            "ORDER BY version DESC NULLS LAST, created_at",
            category
        );
    }
}
