package com.chinacreator.gzcm.engine.ontology.dao;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.chinacreator.gzcm.engine.ontology.model.OntologyVersion;
import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * B3c 数据访问收口（R2/B3c）：版本 CRUD（{@code ecos_ontology_version} 表）。
 *
 * <p>SQL / RowMapper 全量搬运自 {@code OntologyVersionRepository}（含 nextId 的
 * {@code regexp_replace ... ::bigint} 语法，属 W83/W77 范围外存量，非 proposal 链路）。
 * 方法名保留 {@code findByOntology} / {@code findById} / {@code findPreviousVersion}
 * 等（HistoryNotInDbTest 依赖）。</p>
 */
@Repository
public class OntologyVersionDao extends JdbcTemplateDaoSupport {

    private final RowMapper<OntologyVersion> ROW_MAPPER = (rs, rn) -> {
        OntologyVersion v = new OntologyVersion();
        v.setId(rs.getString("id"));
        v.setOntologyId(rs.getString("ontology_id"));
        v.setVersionNo(rs.getString("version_no"));
        v.setStatus(rs.getString("status"));
        v.setSnapshot(rs.getString("snapshot"));
        v.setChangeLog(rs.getString("change_log"));
        v.setPublisher(rs.getString("publisher"));
        v.setPublishedAt(ts(rs.getTimestamp("published_at")));
        v.setCreatedAt(ts(rs.getTimestamp("created_at")));
        return v;
    };

    private static LocalDateTime ts(Timestamp t) {
        return t != null ? t.toLocalDateTime() : null;
    }

    public List<OntologyVersion> findByOntology(String ontologyId) {
        return jdbcTemplate.query(
            "SELECT * FROM ecos_ontology_version WHERE ontology_id = ? ORDER BY created_at DESC",
            ROW_MAPPER, ontologyId);
    }

    public Optional<OntologyVersion> findById(String id) {
        List<OntologyVersion> list = jdbcTemplate.query(
            "SELECT * FROM ecos_ontology_version WHERE id = ?", ROW_MAPPER, id);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public Optional<OntologyVersion> findByOntologyAndVersion(String ontologyId, String versionNo) {
        List<OntologyVersion> list = jdbcTemplate.query(
            "SELECT * FROM ecos_ontology_version WHERE ontology_id = ? AND version_no = ?",
            ROW_MAPPER, ontologyId, versionNo);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public Optional<OntologyVersion> findLatestPublished(String ontologyId) {
        List<OntologyVersion> list = jdbcTemplate.query(
            "SELECT * FROM ecos_ontology_version WHERE ontology_id = ? AND status = 'Published' ORDER BY created_at DESC LIMIT 1",
            ROW_MAPPER, ontologyId);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public int insert(OntologyVersion version) {
        return jdbcTemplate.update("""
            INSERT INTO ecos_ontology_version (id, ontology_id, version_no, status, snapshot, change_log, publisher, published_at, created_at)
            VALUES (?, ?, ?, ?, ?::jsonb, ?, ?, ?, NOW())
            """, version.getId(), version.getOntologyId(), version.getVersionNo(),
            version.getStatus(), version.getSnapshot(), version.getChangeLog(),
            version.getPublisher(), version.getPublishedAt() != null ? Timestamp.valueOf(version.getPublishedAt()) : null);
    }

    public int updateStatus(String id, String status) {
        return jdbcTemplate.update("""
            UPDATE ecos_ontology_version SET status = ?, published_at = CASE WHEN ? = 'Published' THEN NOW() ELSE published_at END
            WHERE id = ?
            """, status, status, id);
    }

    /** F03-15 (W83)：Git 归档成功/入补偿失败时回填 git_ref。additive only。 */
    public int setGitRef(String id, String gitRef) {
        return jdbcTemplate.update(
            "UPDATE ecos_ontology_version SET git_ref = ?, update_time = NOW() WHERE id = ? AND is_deleted = 0",
            gitRef, id);
    }

    public int delete(String id) {
        return jdbcTemplate.update(
            "UPDATE ecos_ontology_version SET is_deleted = 1, status = 'ARCHIVED', " +
            "update_time = now(), update_by = 'system' WHERE id = ? AND is_deleted = 0", id);
    }

    public List<OntologyVersion> findAll() {
        return jdbcTemplate.query("SELECT * FROM ecos_ontology_version ORDER BY created_at DESC", ROW_MAPPER);
    }

    public String nextId() {
        Long max = jdbcTemplate.queryForObject(
            "SELECT COALESCE(MAX(NULLIF(regexp_replace(id, '\\D', '', 'g'), '')::bigint), 5000) "
                + "FROM ecos_ontology_version", Long.class);
        return "ver" + ((max == null ? 5000L : max) + 1L);
    }

    public Optional<OntologyVersion> findPreviousVersion(String ontologyId, LocalDateTime beforeTime) {
        List<OntologyVersion> list = jdbcTemplate.query(
            "SELECT * FROM ecos_ontology_version WHERE ontology_id = ? AND created_at < ? ORDER BY created_at DESC LIMIT 1",
            ROW_MAPPER, ontologyId, beforeTime != null ? Timestamp.valueOf(beforeTime) : null);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }
}
