package com.chinacreator.gzcm.engine.data.quality.dao;

import java.sql.ResultSet;
import java.util.List;
import java.util.Map;

import com.chinacreator.gzcm.engine.data.quality.model.DqAssetScoreVO;
import com.chinacreator.gzcm.engine.data.quality.model.DqScoreTrendVO;
import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * DqScoreDao — DQ 评分数据访问层（PMO-48-B T8）。
 * <p>
 * 架构铁律 §2.5 #1 收口：原内联在 {@code DqScoreServiceImpl} 的 JdbcTemplate 调用迁移到本 DAO，
 * SQL 文本一律离开 Service。表前缀统一为 {@code ecos_data}。
 *
 * @author PMO-48-B T8
 */
@Repository
public class DqScoreDao extends JdbcTemplateDaoSupport {

    private static final String ASSET_COLUMNS =
            "SELECT asset_type AS assetType, asset_id AS assetId, overall_score AS rolledScore," +
            " rolled_up_scores, last_evaluated_at AS lastEvaluatedAt FROM ecos_data.dq_score_asset";

    public int insertSnapshot(String dimension, String scopeType, String scopeId, double score,
                              int weight, int sampleSize, int distinctRuleCount, String metadataJson) {
        return jdbcTemplate.update(
                "INSERT INTO ecos_data.dq_score_snapshot" +
                " (dimension, scope_type, scope_id, score_value, weight, sample_size, distinct_rule_count, evaluated_at, metadata)" +
                " VALUES (?, ?, ?, ?, ?, ?, ?, NOW(), ?::jsonb)",
                dimension, scopeType, scopeId, score, weight, sampleSize, distinctRuleCount, metadataJson);
    }

    public int upsertAssetScore(String assetType, String assetId, double rolled, String rolledUpJson) {
        return jdbcTemplate.update(
                "INSERT INTO ecos_data.dq_score_asset" +
                " (asset_type, asset_id, overall_score, rolled_up_scores, last_evaluated_at, is_deleted)" +
                " VALUES (?, ?, ?, ?, NOW(), FALSE)" +
                " ON CONFLICT (asset_type, asset_id) DO UPDATE" +
                " SET overall_score = EXCLUDED.overall_score," +
                "     rolled_up_scores = EXCLUDED.rolled_up_scores," +
                "     last_evaluated_at = EXCLUDED.last_evaluated_at," +
                "     is_deleted = FALSE",
                assetType, assetId, rolled, rolledUpJson);
    }

    public List<DqAssetScoreVO> findAssetScore(String assetType, String assetId, RowMapper<DqAssetScoreVO> mapper) {
        return jdbcTemplate.query(
                ASSET_COLUMNS + " WHERE asset_type = ? AND asset_id = ? AND is_deleted = FALSE",
                mapper, assetType, assetId);
    }

    public List<DqScoreTrendVO> trend(String assetType, String assetId, int days) {
        return jdbcTemplate.query(
                "SELECT CAST(evaluated_at AS DATE) AS day," +
                " dimension, AVG(score_value) AS score_value, COUNT(*) AS hits" +
                " FROM ecos_data.dq_score_snapshot" +
                " WHERE scope_type = ? AND scope_id = ?" +
                "   AND evaluated_at >= NOW() - (? || ' days')::interval" +
                " GROUP BY 1, 2 ORDER BY 1 DESC, 2",
                (rs, i) -> new DqScoreTrendVO(
                        rs.getDate("day") != null ? rs.getDate("day").toLocalDate() : java.time.LocalDate.now(),
                        rs.getString("dimension"),
                        rs.getDouble("score_value"),
                        rs.getInt("hits")),
                assetType, assetId, days);
    }

    public List<DqAssetScoreVO> listAllAssetScores(RowMapper<DqAssetScoreVO> mapper) {
        return jdbcTemplate.query(
                ASSET_COLUMNS + " WHERE is_deleted = FALSE ORDER BY overall_score ASC",
                mapper);
    }

    public Double avgOverallScore() {
        return jdbcTemplate.queryForObject(
                "SELECT AVG(overall_score) FROM ecos_data.dq_score_asset WHERE is_deleted = FALSE", Double.class);
    }

    public Integer countAssetScores() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ecos_data.dq_score_asset WHERE is_deleted = FALSE", Integer.class);
    }

    public void perDimensionScores(java.util.function.BiConsumer<String, Double> sink) {
        // ResultSetExtractor 形态：入参 ResultSet 停在首行之前，必须先 next() 再取列，
        // 否则 PG 驱动抛 SQLState 24000（同 loadRecentChecks 的历史 500 根因）。
        jdbcTemplate.query(
                "SELECT dimension, AVG(score_value) AS avg_score" +
                " FROM ecos_data.dq_score_snapshot" +
                " WHERE evaluated_at >= NOW() - interval '1 day'" +
                " GROUP BY dimension",
                rs -> {
                    while (rs.next()) {
                        sink.accept(rs.getString("dimension"), rs.getDouble("avg_score"));
                    }
                    return null;
                });
    }

    public List<Map<String, Object>> loadRulesForAsset(String assetId) {
        return jdbcTemplate.query(
                "SELECT id, rule_type AS ruleType, target_field AS targetField," +
                " parameters::text AS parametersJson, domain, severity" +
                " FROM ecos_data.dq_rule" +
                " WHERE target_kind = 'TABLE' AND target_id = ?" +
                "   AND status = 'ACTIVE' AND is_deleted = FALSE",
                (rs, i) -> {
                    Map<String, Object> m = new java.util.LinkedHashMap<>();
                    m.put("id", rs.getString("id"));
                    m.put("ruleType", rs.getString("ruleType"));
                    m.put("targetField", rs.getString("targetField"));
                    m.put("parametersJson", rs.getString("parametersJson"));
                    m.put("domain", rs.getString("domain"));
                    m.put("severity", rs.getString("severity"));
                    return m;
                },
                assetId);
    }

    public void loadRecentChecks(String inClause, String oneHourInterval,
                                 java.util.function.BiConsumer<String, Map<String, Object>> sink, Object[] ids) {
        jdbcTemplate.query(
                "SELECT rule_id AS ruleId, passed, total_rows AS totalRows, failed_rows AS failedRows," +
                " pass_rate AS passRate, executed_at AS executedAt" +
                " FROM ecos_data.dq_rule_check" +
                " WHERE rule_id IN (" + inClause + ")" +
                "   AND executed_at >= NOW() - " + oneHourInterval,
                rs -> {
                    while (rs.next()) {
                        Map<String, Object> m = new java.util.LinkedHashMap<>();
                        m.put("passed", rs.getBoolean("passed"));
                        m.put("totalRows", rs.getLong("totalRows"));
                        m.put("failedRows", rs.getLong("failedRows"));
                        m.put("passRate", rs.getDouble("passRate"));
                        java.sql.Timestamp execTs = rs.getTimestamp("executedAt");
                        m.put("executedAt", execTs != null ? execTs.toLocalDateTime() : null);
                        sink.accept(rs.getString("ruleId"), m);
                    }
                    return null;
                },
                ids);
    }
}
