package com.chinacreator.gzcm.dccheng.ontology;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * JdbcTemplate 仓库 — 版本 CRUD（ecos_ontology_version 表）
 */
@Repository
public class OntologyVersionRepository {

    private final JdbcTemplate jdbc;

    public OntologyVersionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

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
        return jdbc.query(
            "SELECT * FROM ecos_ontology_version WHERE ontology_id = ? ORDER BY created_at DESC",
            ROW_MAPPER, ontologyId);
    }

    public Optional<OntologyVersion> findById(String id) {
        List<OntologyVersion> list = jdbc.query(
            "SELECT * FROM ecos_ontology_version WHERE id = ?", ROW_MAPPER, id);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public Optional<OntologyVersion> findByOntologyAndVersion(String ontologyId, String versionNo) {
        List<OntologyVersion> list = jdbc.query(
            "SELECT * FROM ecos_ontology_version WHERE ontology_id = ? AND version_no = ?",
            ROW_MAPPER, ontologyId, versionNo);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public Optional<OntologyVersion> findLatestPublished(String ontologyId) {
        List<OntologyVersion> list = jdbc.query(
            "SELECT * FROM ecos_ontology_version WHERE ontology_id = ? AND status = 'Published' ORDER BY created_at DESC LIMIT 1",
            ROW_MAPPER, ontologyId);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public int insert(OntologyVersion version) {
        return jdbc.update("""
            INSERT INTO ecos_ontology_version (id, ontology_id, version_no, status, snapshot, change_log, publisher, published_at, created_at)
            VALUES (?, ?, ?, ?, ?::jsonb, ?, ?, ?, NOW())
            """, version.getId(), version.getOntologyId(), version.getVersionNo(),
            version.getStatus(), version.getSnapshot(), version.getChangeLog(),
            version.getPublisher(), version.getPublishedAt() != null ? Timestamp.valueOf(version.getPublishedAt()) : null);
    }

    public int updateStatus(String id, String status) {
        return jdbc.update("""
            UPDATE ecos_ontology_version SET status = ?, published_at = CASE WHEN ? = 'Published' THEN NOW() ELSE published_at END
            WHERE id = ?
            """, status, status, id);
    }

    public int delete(String id) {
        return jdbc.update("DELETE FROM ecos_ontology_version WHERE id = ?", id);
    }

    public List<OntologyVersion> findAll() {
        return jdbc.query("SELECT * FROM ecos_ontology_version ORDER BY created_at DESC", ROW_MAPPER);
    }

    public Optional<OntologyVersion> findPreviousVersion(String ontologyId, java.time.LocalDateTime beforeTime) {
        List<OntologyVersion> list = jdbc.query(
            "SELECT * FROM ecos_ontology_version WHERE ontology_id = ? AND created_at < ? ORDER BY created_at DESC LIMIT 1",
            ROW_MAPPER, ontologyId, beforeTime != null ? java.sql.Timestamp.valueOf(beforeTime) : null);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }
}
