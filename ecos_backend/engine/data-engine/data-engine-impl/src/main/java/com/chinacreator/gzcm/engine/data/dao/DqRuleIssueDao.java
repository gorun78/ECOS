package com.chinacreator.gzcm.engine.data.dao;

import com.chinacreator.gzcm.engine.data.quality.entity.DqIssueEntity;
import com.chinacreator.gzcm.engine.data.quality.entity.DqRuleEntity;
import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * B4a 数据访问收口（R2-B4a）：{@code DqRepository}（..quality.repository..）
 * 内联 raw-SQL（ecos_dq_rule_v2 / ecos_dq_issue）的 DAO 承载点。
 *
 * <p>架构铁律 §2.5#1 / IR06：Repository 层禁私持 {@code JdbcTemplate}，
 * SQL 一律下沉 {@code ..dao..}；本 DAO 只搬不改，SQL 文本/表名/字段/
 * 参数序与 {@code DqRepository} 原实现逐字节一致（含 EntityManager 时代
 * 的 PREPARED_STATEMENT + RETURN_GENERATED_KEYS 分支）。</p>
 */
@Repository
public class DqRuleIssueDao extends JdbcTemplateDaoSupport {

    private static final Logger log = LoggerFactory.getLogger(DqRuleIssueDao.class);

    // ═══════════════════ Rule RowMapper ═══════════════════════

    private static final RowMapper<DqRuleEntity> RULE_MAPPER = (rs, rowNum) -> {
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

    private static final RowMapper<DqIssueEntity> ISSUE_MAPPER = (rs, rowNum) -> {
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
        return jdbcTemplate.query(sql, RULE_MAPPER);
    }

    public Optional<DqRuleEntity> findRuleById(Long id) {
        String sql = "SELECT * FROM ecos_dq_rule_v2 WHERE id = ?";
        List<DqRuleEntity> list = jdbcTemplate.query(sql, RULE_MAPPER, id);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public long insertRule(DqRuleEntity entity) {
        String sql = """
            INSERT INTO ecos_dq_rule_v2 (name, description, rule_type, config_json, severity, enabled, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, NOW(), NOW())
            """;
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, entity.getName());
            ps.setString(2, entity.getDescription());
            ps.setString(3, entity.getRuleType());
            ps.setString(4, entity.getConfigJson());
            ps.setString(5, entity.getSeverity());
            ps.setBoolean(6, entity.getEnabled() != null && entity.getEnabled());
            return ps;
        }, keyHolder);
        return resolveGeneratedKey(keyHolder, "ecoss_dq_rule");
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
        return jdbcTemplate.update(sql, name, description, ruleType, configJson, severity, enabled, id);
    }

    public int deleteRuleById(Long id) {
        return jdbcTemplate.update("DELETE FROM ecos_dq_rule_v2 WHERE id = ?", id);
    }

    public long countRules() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ecos_dq_rule_v2", Long.class);
    }

    // ═══════════════════ Issue CRUD ═══════════════════════════

    public List<DqIssueEntity> findAllIssues() {
        String sql = "SELECT id, rule_id, entity_id as asset_id, description, status, severity, detected_at as created_at, resolved_at FROM ecos_dq_issue ORDER BY detected_at DESC";
        return jdbcTemplate.query(sql, ISSUE_MAPPER);
    }

    public Optional<DqIssueEntity> findIssueById(Long id) {
        String sql = "SELECT id, rule_id, entity_id as asset_id, description, status, severity, detected_at as created_at, resolved_at FROM ecos_dq_issue WHERE id = ?";
        List<DqIssueEntity> list = jdbcTemplate.query(sql, ISSUE_MAPPER, id);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public int insertIssue(DqIssueEntity entity) {
        String sql = """
            INSERT INTO ecos_dq_issue (id, rule_id, entity_id, description, status, severity, detected_at, resolved_at)
            VALUES (?, ?, ?, ?, ?, ?, NOW(), ?)
            """;
        return jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql);
            ps.setString(1, entity.getId());
            ps.setString(2, entity.getRuleId());
            ps.setString(3, entity.getAssetId());
            ps.setString(4, entity.getDescription());
            ps.setString(5, entity.getStatus() != null ? entity.getStatus() : "open");
            ps.setString(6, entity.getSeverity());
            ps.setObject(7, entity.getResolvedAt() != null
                ? java.sql.Timestamp.valueOf(entity.getResolvedAt()) : null);
            return ps;
        });
    }

    public int updateIssue(Long id, String description, String status, String severity) {
        String sql = """
            UPDATE ecos_dq_issue
            SET description = COALESCE(?, description),
                status = COALESCE(?, status),
                severity = COALESCE(?, severity),
                updated_at = NOW(),
                resolved_at = CASE WHEN ?::text IN ('resolved', 'closed') THEN NOW() ELSE resolved_at END
            WHERE id = ?
            """;
        return jdbcTemplate.update(sql, description, status, severity, status, id);
    }

    public int resolveIssue(Long id, String resolution) {
        String sql = "UPDATE ecos_dq_issue SET status = 'resolved', resolved_at = NOW() WHERE id = ? AND status = 'open'";
        return jdbcTemplate.update(sql, id);
    }

    public int deleteIssueById(Long id) {
        return jdbcTemplate.update("DELETE FROM ecos_dq_issue WHERE id = ?", id);
    }

    public long countIssues() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ecos_dq_issue", Long.class);
    }

    public long countIssuesByStatus(String status) {
        String sql = "SELECT COUNT(*) FROM ecos_dq_issue WHERE status = ?";
        return jdbcTemplate.queryForObject(sql, Long.class, status);
    }

    public Double avgPassRate() {
        String sql = "SELECT COALESCE(AVG(CAST(config_json::jsonb->>'passRate' AS NUMERIC)), 0) FROM ecos_dq_rule_v2";
        return jdbcTemplate.queryForObject(sql, Double.class);
    }

    public Map<String, Long> countIssuesBySeverity() {
        String sql = "SELECT severity, COUNT(*) as cnt FROM ecos_dq_issue GROUP BY severity ORDER BY severity";
        Map<String, Long> result = new LinkedHashMap<>();
        jdbcTemplate.query(sql, (rs) -> {
            result.put(rs.getString("severity"), rs.getLong("cnt"));
        });
        return result;
    }

    public List<Map<String, Object>> findRecentIssues(int limit) {
        String sql = "SELECT id, rule_id, entity_id as asset_id, entity_type, description, status, severity, detected_at as created_at FROM ecos_dq_issue ORDER BY status = 'open' DESC, detected_at DESC LIMIT ?";
        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            Map<String, Object> issue = new LinkedHashMap<>();
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

    public Map<String, Long> countRulesByType() {
        String sql = "SELECT rule_type, COUNT(*) as cnt FROM ecos_dq_rule_v2 GROUP BY rule_type ORDER BY rule_type";
        Map<String, Long> result = new LinkedHashMap<>();
        jdbcTemplate.query(sql, (rs) -> {
            result.put(rs.getString("rule_type"), rs.getLong("cnt"));
        });
        return result;
    }

    /**
     * Wave-6 T-25: 从 KeyHolder 解析生成的主键（PG 在 INSERT 后可能回多列，
     * 走 keys() 兜底取第一个数值列）。
     */
    static long resolveGeneratedKey(KeyHolder keyHolder, String table) {
        Map<String, Object> keys = keyHolder.getKeys();
        if (keys == null || keys.isEmpty()) {
            log.warn("No generated key returned from {}", table);
            throw new RuntimeException("Failed to retrieve generated key for " + table);
        }
        Object idVal = keys.get("id");
        if (idVal instanceof Number n) {
            return n.longValue();
        }
        for (Object v : keys.values()) {
            if (v instanceof Number n) {
                return n.longValue();
            }
        }
        log.warn("No numeric generated key in {} for table {}", keys, table);
        throw new RuntimeException("Failed to retrieve numeric generated key for " + table);
    }
}
