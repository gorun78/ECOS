package com.chinacreator.gzcm.engine.kb.service.profile;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * {@link KbProfileStore} 的 JdbcTemplate 实现（schema 限定 {@code ecos_knowledge.ecos_kb_profile}，
 * 所有条件参数化防注入；金额/统计量列 NUMERIC，时间列 TIMESTAMP 于 SQL 端 epoch 转换）。
 */
@Repository
public class JdbcProfileStore implements KbProfileStore {

    private static final String TABLE = "ecos_knowledge.ecos_kb_profile";

    private static final RowMapper<KbProfileDO> ROW_MAPPER = (rs, rowNum) -> {
        KbProfileDO d = new KbProfileDO();
        d.setId(rs.getString("id"));
        d.setProfileKey(strOrNull(rs.getString("profile_key")));
        d.setMetricCode(rs.getString("metric_code"));
        d.setGroupDimsJson(strOrNull(rs.getString("group_dims_json")));
        d.setWindowFrom(rs.getString("window_from"));
        d.setWindowTo(rs.getString("window_to"));
        d.setSampleCount(rs.getInt("sample_count"));
        d.setMissingRate(rs.getBigDecimal("missing_rate"));
        d.setP10(rs.getBigDecimal("p10"));
        d.setP50(rs.getBigDecimal("p50"));
        d.setP90(rs.getBigDecimal("p90"));
        d.setMeanValue(rs.getBigDecimal("mean_value"));
        d.setCiLow(rs.getBigDecimal("ci_low"));
        d.setCiHigh(rs.getBigDecimal("ci_high"));
        d.setCiLevel(strOrNull(rs.getString("ci_level")));
        d.setStatsMethod(strOrNull(rs.getString("stats_method")));
        d.setConfidence(rs.getString("confidence"));
        d.setDegradeFrom(strOrNull(rs.getString("degrade_from")));
        d.setDegradePathJson(strOrNull(rs.getString("degrade_path_json")));
        d.setApplicableCondition(strOrNull(rs.getString("applicable_condition")));
        d.setSourceQueryRef(strOrNull(rs.getString("source_query_ref")));
        d.setProfileVersion(rs.getString("profile_version"));
        d.setStatus(rs.getString("status"));
        d.setApprovedBy(strOrNull(rs.getString("approved_by")));
        d.setApprovedTime(epochsOf(rs, "approved_time"));
        d.setTaskId(strOrNull(rs.getString("task_id")));
        d.setTraceId(strOrNull(rs.getString("trace_id")));
        d.setDomain(rs.getString("domain"));
        d.setVersionNo(rs.getInt("version_no"));
        d.setIsDeleted(rs.getInt("is_deleted"));
        d.setCreateBy(strOrNull(rs.getString("create_by")));
        d.setUpdateBy(strOrNull(rs.getString("update_by")));
        d.setCreateTime(epochsOf(rs, "create_time"));
        d.setUpdateTime(epochsOf(rs, "update_time"));
        return d;
    };

    private final JdbcTemplate jdbc;

    public JdbcProfileStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<KbProfileDO> query(String metricCode, String status, String domain, String profileKeyPrefix) {
        StringBuilder sql = new StringBuilder("SELECT * FROM ").append(TABLE)
                .append(" WHERE is_deleted = 0");
        List<Object> args = new ArrayList<>();
        if (metricCode != null && !metricCode.isBlank()) {
            sql.append(" AND metric_code = ?");
            args.add(metricCode.trim());
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND status = ?");
            args.add(status.trim());
        }
        if (domain != null && !domain.isBlank()) {
            sql.append(" AND domain = ?");
            args.add(domain.trim());
        }
        if (profileKeyPrefix != null && !profileKeyPrefix.isBlank()) {
            // 前缀用 ?? 参数化（=? LIKE ? 需通义词；这里用 profile_key = ? 精确或前缀由调用方传入指标）
            sql.append(" AND profile_key = ?");
            args.add(profileKeyPrefix.trim());
        }
        sql.append(" ORDER BY window_from DESC, window_to DESC");
        return jdbc.query(sql.toString(), ROW_MAPPER, args.toArray());
    }

    @Override
    public KbProfileDO findLatestByKey(String profileKey) {
        List<KbProfileDO> rows = jdbc.query(
                "SELECT * FROM " + TABLE + " WHERE is_deleted = 0 AND profile_key = ? "
                        + "ORDER BY create_time DESC LIMIT 1",
                ROW_MAPPER, profileKey);
        return rows.isEmpty() ? null : rows.get(0);
    }

    @Override
    public KbProfileDO findById(String id) {
        List<KbProfileDO> rows = jdbc.query(
                "SELECT * FROM " + TABLE + " WHERE is_deleted = 0 AND id = ?",
                ROW_MAPPER, id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    @Override
    public void insert(KbProfileDO row) {
        jdbc.update(
                "INSERT INTO " + TABLE
                        + " (id, profile_key, metric_code, group_dims_json, window_from, window_to,"
                        + " sample_count, missing_rate, p10, p50, p90, mean_value, ci_low, ci_high, ci_level,"
                        + " stats_method, confidence, degrade_from, degrade_path_json, applicable_condition,"
                        + " source_query_ref, profile_version, status, approved_by, task_id, trace_id, domain,"
                        + " version_no, is_deleted, create_by, update_by,"
                        + " approved_time, create_time, update_time) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,"
                        + " ?,?,?,?,?,?,?,?,?,?,?,?,?,?)"
                        + " ON CONFLICT (id) DO NOTHING",
                row.getId(), row.getProfileKey(), row.getMetricCode(), row.getGroupDimsJson(),
                row.getWindowFrom(), row.getWindowTo(), row.getSampleCount(), row.getMissingRate(),
                row.getP10(), row.getP50(), row.getP90(), row.getMeanValue(), row.getCiLow(), row.getCiHigh(),
                row.getCiLevel() == null ? "95" : row.getCiLevel(),
                row.getStatsMethod() == null ? "T_APPROX" : row.getStatsMethod(),
                row.getConfidence(), row.getDegradeFrom(), row.getDegradePathJson(),
                row.getApplicableCondition(), row.getSourceQueryRef(), row.getProfileVersion(),
                row.getStatus() == null ? "DRAFT" : row.getStatus(), row.getApprovedBy(),
                row.getTaskId(), row.getTraceId(), row.getDomain(),
                row.getVersionNo() <= 0 ? 1 : row.getVersionNo(), row.getIsDeleted(), row.getCreateBy(),
                row.getUpdateBy(),
                tsOrNull(row.getApprovedTime()), tsOrNull(row.getCreateTime()), tsOrNull(row.getUpdateTime()));
    }

    /** epoch 毫秒 → SQL Timestamp；0/负 → NULL（PG 端不可用 0 作 epoch）。 */
    private static java.sql.Timestamp tsOrNull(long epochMillis) {
        return epochMillis > 0 ? new java.sql.Timestamp(epochMillis) : null;
    }

    private static String strOrNull(String s) {
        return s == null || s.isEmpty() ? null : s;
    }

    private static long epochsOf(java.sql.ResultSet rs, String col) throws java.sql.SQLException {
        Object o = rs.getObject(col);
        if (o == null) {
            return 0L;
        }
        if (o instanceof java.sql.Timestamp ts) {
            return ts.getTime();
        }
        if (o instanceof java.util.Date d) {
            return d.getTime();
        }
        return 0L;
    }
}
