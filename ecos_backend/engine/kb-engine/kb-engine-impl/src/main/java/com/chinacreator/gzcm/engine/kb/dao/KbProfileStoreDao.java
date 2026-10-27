package com.chinacreator.gzcm.engine.kb.dao;

import com.chinacreator.gzcm.engine.kb.service.profile.KbProfileDO;
import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据访问收口（R2-B2b kb 子包 / 架构铁律 §2.5 / IR06）：知识库历史画像 JDBC 落点。
 * <p>SQL 原样搬运自原 {@code service.profile.JdbcProfileStore}（schema
 * {@code ${kb.control-schema:ecos_knowledge}}.ecos_kb_profile）；动态 WHERE 拼接保持 byte-for-byte。
 *
 * <p>V200/R3-B1: schema 由 {@code ${kb.control-schema:ecos_knowledge}} 注入 (MC06 全面落地)。</p>
 */
@Repository
public class KbProfileStoreDao extends JdbcTemplateDaoSupport {

    /**
     * V200/R3-B1: schema 由 {@code ${kb.control-schema:ecos_knowledge}} 注入（MC06），
     * 转写为非静态 final 字段以承载运行时 schema。
     */
    private final String TABLE;

    public KbProfileStoreDao(@Value("${kb.control-schema:ecos_knowledge}") String schema) {
        this.TABLE = schema + ".ecos_kb_profile";
    }

    static final RowMapper<KbProfileDO> ROW_MAPPER = (rs, rowNum) -> {
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

    /** 任一画像 list 查询（条件是可选过滤 + ORDER BY 收敛，SQL byte-for-byte 保留）。 */
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
        return jdbcTemplate.query(sql.toString(), ROW_MAPPER, args.toArray());
    }

    public List<KbProfileDO> findLatestByKeyRows(String profileKey) {
        return jdbcTemplate.query(
                "SELECT * FROM " + TABLE + " WHERE is_deleted = 0 AND profile_key = ? "
                        + "ORDER BY create_time DESC LIMIT 1",
                ROW_MAPPER, profileKey);
    }

    public List<KbProfileDO> findByIdRows(String id) {
        return jdbcTemplate.query(
                "SELECT * FROM " + TABLE + " WHERE is_deleted = 0 AND id = ?",
                ROW_MAPPER, id);
    }

    /**
     * 画像存量两计（只读，一次查询）：命中行数 + {@code sample_count} 合计
     * （供 WAgent Readiness 定级 {@code know.sample_size} 度量取数）。
     *
     * <p>SQL 形态（动态 WHERE 与 {@link #query} 同风格，等值列全部 {@code ?} 参数化）：
     * <pre>SELECT COUNT(*), COALESCE(SUM(sample_count), 0)
     *      FROM {schema}.ecos_kb_profile WHERE is_deleted = 0
     *      [AND status = ?] [AND metric_code = ?] [AND domain IN (...)]</pre>
     *
     * <p>口径：{@code hasStatusFilter}/{@code metricCode}/{@code domains} 为空则不过滤；
     * {@code domains} 逐枚 trim 后经 {@code IN (?,...)} 展开，禁拼接值；
     * 本方法<b>不内置任何默认过滤值</b>（status 缺省策略属服务层安全裁决，不下沉 SQL）。
     *
     * @return {@code {profiles: int, sampleSum: long}}；行集为空时
     *         {@code COUNT(*)=0} 且 {@code COALESCE(SUM(...),0)=0}，两计恒有值。
     */
    public Map<String, Object> profileStock(String status, String metricCode, List<String> domains) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*), COALESCE(SUM(sample_count), 0)")
                .append(" FROM ").append(TABLE).append(" WHERE is_deleted = 0");
        List<Object> args = new ArrayList<>();
        if (status != null && !status.isBlank()) {
            sql.append(" AND status = ?");
            args.add(status.trim());
        }
        if (metricCode != null && !metricCode.isBlank()) {
            sql.append(" AND metric_code = ?");
            args.add(metricCode.trim());
        }
        if (domains != null) {
            List<String> valid = domains.stream()
                    .filter(d -> d != null && !d.isBlank())
                    .map(String::trim)
                    .toList();
            if (!valid.isEmpty()) {
                sql.append(" AND domain IN (")
                        .append(String.join(", ", java.util.Collections.nCopies(valid.size(), "?")))
                        .append(")");
                args.addAll(valid);
            }
        }
        // 两计单行聚合：与既有 DAO 腿同口径走 RowMapper（禁在 DAO 外扩 ResultSetExtractor 绑定面）
        Object[] row = jdbcTemplate.queryForObject(
                sql.toString(), (rs, rowNum) -> new Object[]{
                        rs.getInt(1),
                        rs.getObject(2) == null ? null : ((Number) rs.getObject(2)).longValue()},
                args.toArray());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("profiles", row[0]);
        out.put("sampleSum", row[1]);
        return out;
    }

    public void insert(KbProfileDO row) {
        jdbcTemplate.update(
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
    static java.sql.Timestamp tsOrNull(long epochMillis) {
        return epochMillis > 0 ? new java.sql.Timestamp(epochMillis) : null;
    }

    static String strOrNull(String s) {
        return s == null || s.isEmpty() ? null : s;
    }

    static long epochsOf(java.sql.ResultSet rs, String col) throws java.sql.SQLException {
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
