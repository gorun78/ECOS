package com.chinacreator.gzcm.engine.kb.service.assumption;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * {@link KbAssumptionStore} 的 JdbcTemplate 实现（schema 限定 {@code ecos_knowledge.ecos_kb_assumption}，
 * 所有条件参数化；金额/值列 NUMERIC，时间列 TIMESTAMP 于 SQL 端 epoch 转换）。
 */
@Repository
public class JdbcAssumptionStore implements KbAssumptionStore {

    private static final String TABLE = "ecos_knowledge.ecos_kb_assumption";

    private static final RowMapper<KbAssumptionDO> ROW_MAPPER = (rs, rowNum) -> {
        KbAssumptionDO d = new KbAssumptionDO();
        d.setId(rs.getString("id"));
        d.setAssumptionKey(strOrNull(rs.getString("assumption_key")));
        d.setScenarioType(rs.getString("scenario_type"));
        d.setMetricCode(rs.getString("metric_code"));
        d.setDimScopeJson(strOrNull(rs.getString("dim_scope_json")));
        d.setValueType(rs.getString("value_type"));
        d.setValue(rs.getBigDecimal("value"));
        d.setValueSemantic(rs.getString("value_semantic"));
        d.setBaseAssumptionId(strOrNull(rs.getString("base_assumption_id")));
        d.setValidFrom(rs.getString("valid_from"));
        d.setValidTo(strOrNull(rs.getString("valid_to")));
        d.setReason(rs.getString("reason"));
        d.setEvidenceRef(strOrNull(rs.getString("evidence_ref")));
        d.setIsUnverified(rs.getInt("is_unverified"));
        d.setExpireAt(epochsOf(rs, "expire_at"));
        d.setAssumptionVersion(rs.getString("assumption_version"));
        d.setStatus(rs.getString("status"));
        d.setApprovedBy(strOrNull(rs.getString("approved_by")));
        d.setApprovedTime(epochsOf(rs, "approved_time"));
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

    public JdbcAssumptionStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<KbAssumptionDO> query(String scenarioType, String metricCode, String status) {
        StringBuilder sql = new StringBuilder("SELECT * FROM ").append(TABLE)
                .append(" WHERE is_deleted = 0");
        List<Object> args = new ArrayList<>();
        if (scenarioType != null && !scenarioType.isBlank()) {
            sql.append(" AND scenario_type = ?");
            args.add(scenarioType.trim());
        }
        if (metricCode != null && !metricCode.isBlank()) {
            sql.append(" AND metric_code = ?");
            args.add(metricCode.trim());
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND status = ?");
            args.add(status.trim());
        }
        sql.append(" ORDER BY create_time DESC");
        return jdbc.query(sql.toString(), ROW_MAPPER, args.toArray());
    }

    @Override
    public KbAssumptionDO findLatestByKey(String assumptionKey) {
        List<KbAssumptionDO> rows = jdbc.query(
                "SELECT * FROM " + TABLE + " WHERE is_deleted = 0 AND assumption_key = ? "
                        + "ORDER BY create_time DESC LIMIT 1",
                ROW_MAPPER, assumptionKey);
        return rows.isEmpty() ? null : rows.get(0);
    }

    @Override
    public KbAssumptionDO findById(String id) {
        List<KbAssumptionDO> rows = jdbc.query(
                "SELECT * FROM " + TABLE + " WHERE is_deleted = 0 AND id = ?",
                ROW_MAPPER, id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    @Override
    public void insert(KbAssumptionDO row) {
        jdbc.update(
                "INSERT INTO " + TABLE
                        + " (id, assumption_key, scenario_type, metric_code, dim_scope_json, value_type,"
                        + " value, value_semantic, base_assumption_id, valid_from, valid_to, reason,"
                        + " evidence_ref, is_unverified, expire_at, assumption_version, status,"
                        + " approved_by, trace_id, domain, version_no, is_deleted, create_by, update_by,"
                        + " approved_time, create_time, update_time) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,"
                        + " ?,?,?,?,?,?,?,?,?)"
                        + " ON CONFLICT (id) DO NOTHING",
                row.getId(), row.getAssumptionKey(), row.getScenarioType(), row.getMetricCode(),
                row.getDimScopeJson() == null ? "{}" : row.getDimScopeJson(), row.getValueType(),
                row.getValue(), row.getValueSemantic() == null ? "ABSOLUTE" : row.getValueSemantic(),
                row.getBaseAssumptionId(), row.getValidFrom(), row.getValidTo(), row.getReason(),
                row.getEvidenceRef(), row.getIsUnverified() == null ? 0 : row.getIsUnverified(),
                tsOrNull(row.getExpireAt()), row.getAssumptionVersion(),
                row.getStatus() == null ? "DRAFT" : row.getStatus(),
                row.getApprovedBy(), row.getTraceId(), row.getDomain(),
                row.getVersionNo() <= 0 ? 1 : row.getVersionNo(), row.getIsDeleted(),
                row.getCreateBy(), row.getUpdateBy(),
                tsOrNull(row.getApprovedTime()), tsOrNull(row.getCreateTime()), tsOrNull(row.getUpdateTime()));
    }

    private static Timestamp tsOrNull(long epochMillis) {
        return epochMillis > 0 ? new Timestamp(epochMillis) : null;
    }

    private static String strOrNull(String s) {
        return s == null || s.isEmpty() ? null : s;
    }

    private static long epochsOf(java.sql.ResultSet rs, String col) throws java.sql.SQLException {
        Object o = rs.getObject(col);
        if (o == null) {
            return 0L;
        }
        if (o instanceof Timestamp ts) {
            return ts.getTime();
        }
        if (o instanceof java.util.Date d) {
            return d.getTime();
        }
        return 0L;
    }
}
