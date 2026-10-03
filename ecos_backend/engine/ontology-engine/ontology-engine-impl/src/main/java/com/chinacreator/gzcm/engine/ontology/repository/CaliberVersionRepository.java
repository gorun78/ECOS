package com.chinacreator.gzcm.engine.ontology.repository;

import com.chinacreator.gzcm.engine.ontology.model.CaliberVersion;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 口径版本仓库（表 {@code ecos_ontology.ecos_caliber_version}，V167.1）。
 * 行不可变：不提供 UPDATE 内容列的方法；状态迁移通过 {@link #updateStatus}（仅 DRAFT→REVIEWING→APPROVED→RETIRED 由 service 守卫）。
 */
@Repository
public class CaliberVersionRepository {

    private static final String COLS =
            "id, caliber_id, version_no, formula, additive, period_granularity, status, approved_by, approved_at, git_ref, " +
            "create_time, update_time, create_by, update_by, is_deleted, domain";

    private final JdbcTemplate jdbc;
    private final OntologySchemaSupport schema;

    public CaliberVersionRepository(JdbcTemplate jdbc, OntologySchemaSupport schema) {
        this.jdbc = jdbc;
        this.schema = schema;
    }

    private final RowMapper<CaliberVersion> MAPPER = (rs, rn) -> {
        CaliberVersion v = new CaliberVersion();
        v.setId(rs.getString("id"));
        v.setCaliberId(rs.getString("caliber_id"));
        v.setVersionNo(rs.getString("version_no"));
        v.setFormula(rs.getString("formula"));
        v.setAdditive(intOf(rs.getObject("additive")));
        v.setPeriodGranularity(rs.getString("period_granularity"));
        v.setStatus(rs.getString("status"));
        v.setApprovedBy(rs.getString("approved_by"));
        v.setApprovedAt(ts(rs.getTimestamp("approved_at")));
        v.setGitRef(rs.getString("git_ref"));
        v.setCreateTime(ts(rs.getTimestamp("create_time")));
        v.setUpdateTime(ts(rs.getTimestamp("update_time")));
        v.setCreateBy(rs.getString("create_by"));
        v.setUpdateBy(rs.getString("update_by"));
        v.setIsDeleted(intOf(rs.getObject("is_deleted")));
        v.setDomain(rs.getString("domain"));
        return v;
    };

    private static LocalDateTime ts(Timestamp t) { return t != null ? t.toLocalDateTime() : null; }
    private static Integer intOf(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.intValue();
        try { return Integer.parseInt(o.toString()); } catch (NumberFormatException e) { return null; }
    }

    public List<CaliberVersion> findByCaliberId(String caliberId) {
        String table = schema.t("ecos_caliber_version");
        return jdbc.query(
                "SELECT " + COLS + " FROM " + table + " WHERE caliber_id = ? AND is_deleted = 0 ORDER BY create_time DESC",
                MAPPER, caliberId);
    }

    public Optional<CaliberVersion> findByCaliberIdAndVersion(String caliberId, String versionNo) {
        String table = schema.t("ecos_caliber_version");
        List<CaliberVersion> list = jdbc.query(
                "SELECT " + COLS + " FROM " + table + " WHERE caliber_id = ? AND version_no = ? AND is_deleted = 0",
                MAPPER, caliberId, versionNo);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public Optional<CaliberVersion> findById(String id) {
        String table = schema.t("ecos_caliber_version");
        List<CaliberVersion> list = jdbc.query(
                "SELECT " + COLS + " FROM " + table + " WHERE id = ? AND is_deleted = 0", MAPPER, id);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public List<CaliberVersion> findApprovedByCaliberId(String caliberId) {
        String table = schema.t("ecos_caliber_version");
        return jdbc.query(
                "SELECT " + COLS + " FROM " + table + " WHERE caliber_id = ? AND status = 'APPROVED' AND is_deleted = 0 " +
                "ORDER BY create_time DESC", MAPPER, caliberId);
    }

    public int insert(CaliberVersion v) {
        String table = schema.t("ecos_caliber_version");
        return jdbc.update("""
                INSERT INTO %s (id, caliber_id, version_no, formula, additive, period_granularity, status,
                    approved_by, approved_at, git_ref, create_time, update_time, create_by, update_by, is_deleted, domain)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, 'default')
                """.formatted(table),
                v.getId(), v.getCaliberId(), v.getVersionNo(), v.getFormula(), v.getAdditive(),
                v.getPeriodGranularity(), v.getStatus() != null ? v.getStatus() : "DRAFT",
                v.getApprovedBy(), tsVal(v.getApprovedAt()), v.getGitRef(),
                tsVal(v.getCreateTime()), tsVal(v.getUpdateTime()), v.getCreateBy(), v.getUpdateBy());
    }

    /** 仅状态与审批人/时间可改（内容列 formula/additive/period 一经写入不可变，由 service 层守卫）。 */
    public int updateStatus(String id, String status, String approvedBy, LocalDateTime approvedAt, String by) {
        String table = schema.t("ecos_caliber_version");
        return jdbc.update("""
                UPDATE %s SET status = ?, approved_by = ?, approved_at = ?,
                    update_time = CURRENT_TIMESTAMP, update_by = ?
                WHERE id = ? AND is_deleted = 0
                """.formatted(table), status, approvedBy, tsVal(approvedAt), by, id);
    }

    public int countByCaliberId(String caliberId) {
        String table = schema.t("ecos_caliber_version");
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE caliber_id = ? AND is_deleted = 0", Integer.class, caliberId);
        return n != null ? n : 0;
    }

    private static Timestamp tsVal(LocalDateTime t) { return t != null ? Timestamp.valueOf(t) : new Timestamp(System.currentTimeMillis()); }
}
