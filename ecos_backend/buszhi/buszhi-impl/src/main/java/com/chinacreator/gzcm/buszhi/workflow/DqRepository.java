package com.chinacreator.gzcm.buszhi.workflow;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;

/**
 * JdbcTemplate 仓库 — 数据质量规则 & 问题持久化 CRUD
 * <p>
 * 遵循 AgentRepository / WorkflowRepository 模式。
 * </p>
 */
@Repository
public class DqRepository {

    private static final Logger log = LoggerFactory.getLogger(DqRepository.class);

    private final JdbcTemplate jdbc;

    public DqRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ═══════════════════ Rule RowMapper ═══════════════════════

    private final RowMapper<DqRuleEntity> RULE_MAPPER = (rs, rowNum) -> {
        DqRuleEntity e = new DqRuleEntity();
        e.setId(rs.getString("id"));
        e.setName(rs.getString("name"));
        e.setDescription(rs.getString("description"));
        e.setRuleType(rs.getString("rule_type"));
        e.setConfigJson(rs.getString("config_json"));
        e.setSeverity(rs.getString("severity"));
        e.setEnabled(rs.getBoolean("enabled"));
        e.setCreatedAt(rs.getTimestamp("created_at") != null
            ? rs.getTimestamp("created_at").toLocalDateTime() : null);
        e.setUpdatedAt(rs.getTimestamp("updated_at") != null
            ? rs.getTimestamp("updated_at").toLocalDateTime() : null);
        return e;
    };

    // ═══════════════════ Issue RowMapper ═══════════════════════

    private final RowMapper<DqIssueEntity> ISSUE_MAPPER = (rs, rowNum) -> {
        DqIssueEntity e = new DqIssueEntity();
        e.setId(rs.getString("id"));
        e.setRuleId(rs.getString("rule_id"));
        e.setAssetId(rs.getString("asset_id"));
        e.setDescription(rs.getString("description"));
        e.setStatus(rs.getString("status"));
        e.setSeverity(rs.getString("severity"));
        e.setCreatedAt(rs.getTimestamp("created_at") != null
            ? rs.getTimestamp("created_at").toLocalDateTime() : null);
        e.setResolvedAt(rs.getTimestamp("resolved_at") != null
            ? rs.getTimestamp("resolved_at").toLocalDateTime() : null);
        return e;
    };

    // ═══════════════════ Rule CRUD ════════════════════════════

    public List<DqRuleEntity> findAllRules() {
        String sql = "SELECT * FROM ecos_dq_rule_v2 ORDER BY updated_at DESC";
        return jdbc.query(sql, RULE_MAPPER);
    }

    public Optional<DqRuleEntity> findRuleById(Long id) {
        String sql = "SELECT * FROM ecos_dq_rule_v2 WHERE id = ?";
        List<DqRuleEntity> list = jdbc.query(sql, RULE_MAPPER, id);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public long insertRule(DqRuleEntity entity) {
        String sql = """
            INSERT INTO ecos_dq_rule_v2 (name, description, rule_type, config_json, severity, enabled, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, NOW(), NOW())
            """;
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, entity.getName());
            ps.setString(2, entity.getDescription());
            ps.setString(3, entity.getRuleType());
            ps.setString(4, entity.getConfigJson());
            ps.setString(5, entity.getSeverity());
            ps.setBoolean(6, entity.getEnabled() != null && entity.getEnabled());
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key != null) {
            return key.longValue();
        }
        throw new RuntimeException("Failed to retrieve generated key for ecos_dq_rule");
    }

    public int updateRule(Long id, String name, String description, String ruleType,
                          String configJson, String severity, Boolean enabled) {
        String sql = """
            UPDATE ecos_dq_rule_v2 SET
                name = COALESCE(?, name),
                description = COALESCE(?, description),
                rule_type = COALESCE(?, rule_type),
                config_json = COALESCE(?, config_json),
                severity = COALESCE(?, severity),
                enabled = COALESCE(?, enabled),
                updated_at = NOW()
            WHERE id = ?
            """;
        return jdbc.update(sql, name, description, ruleType, configJson, severity, enabled, id);
    }

    public int deleteRuleById(Long id) {
        return jdbc.update("DELETE FROM ecos_dq_rule_v2 WHERE id = ?", id);
    }

    public long countRules() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM ecos_dq_rule_v2", Long.class);
    }

    // ═══════════════════ Issue CRUD ═══════════════════════════

    public List<DqIssueEntity> findAllIssues() {
        String sql = "SELECT id, rule_id, entity_id as asset_id, description, status, severity, detected_at as created_at, resolved_at FROM ecos_dq_issue ORDER BY detected_at DESC";
        return jdbc.query(sql, ISSUE_MAPPER);
    }

    public Optional<DqIssueEntity> findIssueById(Long id) {
        String sql = "SELECT id, rule_id, entity_id as asset_id, description, status, severity, detected_at as created_at, resolved_at FROM ecos_dq_issue WHERE id = ?";
        List<DqIssueEntity> list = jdbc.query(sql, ISSUE_MAPPER, id);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public long insertIssue(DqIssueEntity entity) {
        String sql = """
            INSERT INTO ecos_dq_issue (rule_id, entity_id, description, status, severity, detected_at, resolved_at)
            VALUES (?, ?, ?, ?, ?, NOW(), ?)
            """;
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, entity.getRuleId());
            ps.setString(2, entity.getAssetId());
            ps.setString(3, entity.getDescription());
            ps.setString(4, entity.getStatus() != null ? entity.getStatus() : "open");
            ps.setString(5, entity.getSeverity());
            ps.setObject(6, entity.getResolvedAt() != null
                ? java.sql.Timestamp.valueOf(entity.getResolvedAt()) : null);
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key != null) {
            return key.longValue();
        }
        throw new RuntimeException("Failed to retrieve generated key for ecos_dq_issue");
    }

    public int resolveIssue(Long id, String resolution) {
        String sql = "UPDATE ecos_dq_issue SET status = 'resolved', resolved_at = NOW() WHERE id = ? AND status = 'open'";
        return jdbc.update(sql, id);
    }

    public int deleteIssueById(Long id) {
        return jdbc.update("DELETE FROM ecos_dq_issue WHERE id = ?", id);
    }

    public long countIssues() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM ecos_dq_issue", Long.class);
    }

    /**
     * Count issues by status.
     */
    public long countIssuesByStatus(String status) {
        String sql = "SELECT COUNT(*) FROM ecos_dq_issue WHERE status = ?";
        return jdbc.queryForObject(sql, Long.class, status);
    }

    /**
     * Average passRate across all rules (parsed from config_json).
     */
    public double avgPassRate() {
        String sql = "SELECT COALESCE(AVG(CAST(config_json::jsonb->>'passRate' AS NUMERIC)), 0) FROM ecos_dq_rule_v2";
        Double result = jdbc.queryForObject(sql, Double.class);
        return result != null ? result : 0.0;
    }

    // ═══════════════════ S3-BE02 Dashboard enhancements ═══════

    /**
     * Count issues grouped by severity.
     */
    public Map<String, Long> countIssuesBySeverity() {
        String sql = "SELECT severity, COUNT(*) as cnt FROM ecos_dq_issue GROUP BY severity ORDER BY severity";
        Map<String, Long> result = new java.util.LinkedHashMap<>();
        jdbc.query(sql, (rs) -> {
            result.put(rs.getString("severity"), rs.getLong("cnt"));
        });
        return result;
    }

    /**
     * Find most recent N issues (unresolved first, then by created_at).
     */
    public List<Map<String, Object>> findRecentIssues(int limit) {
        String sql = "SELECT id, rule_id, entity_id as asset_id, entity_type, description, status, severity, detected_at as created_at FROM ecos_dq_issue ORDER BY status = 'open' DESC, detected_at DESC LIMIT ?";
        return jdbc.query(sql, (rs, rowNum) -> {
            Map<String, Object> issue = new java.util.LinkedHashMap<>();
            issue.put("id", rs.getLong("id"));
            issue.put("ruleId", rs.getString("rule_id"));
            issue.put("assetId", rs.getString("asset_id"));
            issue.put("description", rs.getString("description"));
            issue.put("status", rs.getString("status"));
            issue.put("severity", rs.getString("severity"));
            issue.put("createdAt", rs.getTimestamp("created_at") != null
                    ? rs.getTimestamp("created_at").toString() : null);
            return issue;
        }, limit);
    }

    /**
     * Count rules grouped by rule_type.
     */
    public Map<String, Long> countRulesByType() {
        String sql = "SELECT rule_type, COUNT(*) as cnt FROM ecos_dq_rule_v2 GROUP BY rule_type ORDER BY rule_type";
        Map<String, Long> result = new java.util.LinkedHashMap<>();
        jdbc.query(sql, (rs) -> {
            result.put(rs.getString("rule_type"), rs.getLong("cnt"));
        });
        return result;
    }
}
