package com.chinacreator.gzcm.engine.kb.dao;

import com.chinacreator.gzcm.engine.kb.service.assumption.KbAssumptionDO;
import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * 数据访问收口（R2-B2b kb 子包 / 架构铁律 §2.5 / IR06）：知识库情景假设 JDBC 落点。
 * <p>SQL 原样搬运自原 {@code service.assumption.JdbcAssumptionStore}（schema
 * {@code ${kb.control-schema:ecos_knowledge}}.ecos_kb_assumption）。命名专属以避开根层同名冲突。
 *
 * <p>V200/R3-B1: schema 由 {@code ${kb.control-schema:ecos_knowledge}} 注入 (MC06 全面落地)。</p>
 */
@Repository
public class KbAssumptionStoreDao extends JdbcTemplateDaoSupport {

    /**
     * V200/R3-B1: schema 由 {@code ${kb.control-schema:ecos_knowledge}} 注入（MC06），
     * 转写为非静态 final 字段以承载运行时 schema。
     */
    private final String TABLE;

    public KbAssumptionStoreDao(@Value("${kb.control-schema:ecos_knowledge}") String schema) {
        this.TABLE = schema + ".ecos_kb_assumption";
    }

    static final RowMapper<KbAssumptionDO> ROW_MAPPER = (rs, rowNum) -> {
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
        return jdbcTemplate.query(sql.toString(), ROW_MAPPER, args.toArray());
    }

    public List<KbAssumptionDO> findLatestByKeyRows(String assumptionKey) {
        return jdbcTemplate.query(
                "SELECT * FROM " + TABLE + " WHERE is_deleted = 0 AND assumption_key = ? "
                        + "ORDER BY create_time DESC LIMIT 1",
                ROW_MAPPER, assumptionKey);
    }

    public List<KbAssumptionDO> findByIdRows(String id) {
        return jdbcTemplate.query(
                "SELECT * FROM " + TABLE + " WHERE is_deleted = 0 AND id = ?",
                ROW_MAPPER, id);
    }

    public void insert(KbAssumptionDO row) {
        jdbcTemplate.update(
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

    static String strOrNull(String s) {
        return s == null || s.isEmpty() ? null : s;
    }

    static long epochsOf(java.sql.ResultSet rs, String col) throws java.sql.SQLException {
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
