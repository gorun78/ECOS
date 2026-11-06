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
 *
 * <p>B28（2026-11-04）账实订正（live 42703 复证：发布版本 500）：
 * <ul>
 *   <li>时间戳列名断裂——原读写 {@code created_at}，live 实列 {@code create_time}
 *       （V122 §2.6 六基线列），建行与排序全部失败；</li>
 *   <li>快照列——V170.1 起新写落 {@code snapshot_json}(TEXT)，旧 {@code snapshot} 停写；
 *       原 INSERT 仍写 {@code snapshot ?::jsonb}，与 live TEXT 列/列名双重不符；</li>
 *   <li>列投影改显式列名（含 {@code COALESCE(snapshot_json, snapshot)}），兼容停写前存量行。</li>
 * </ul>
 */
@Repository
public class OntologyVersionDao extends JdbcTemplateDaoSupport {

    /** 显式列投影：兼容 V170.1 双列过渡（存量行只有 snapshot，新写行只有 snapshot_json）。 */
    private static final String COLUMNS =
            "id, ontology_id, version_no, status, COALESCE(snapshot_json, snapshot) AS snapshot, "
            + "change_log, publisher, published_at, create_time, git_ref";

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
        v.setCreatedAt(ts(rs.getTimestamp("create_time")));
        // #147D：补 git_ref 读腿。V170.1 起 setGitRef 一直在写该列，但行映射从未读回——
        // 「写了却读不到」使回滚计划（§2.9.6②／DF-3 需按 git ref 检出）无 ref 可取，
        // 也让 UI 无从区分"已归档"与"待归档"。列由 setGitRef 的同一 UPDATE 语句证实存在。
        v.setGitRef(rs.getString("git_ref"));
        return v;
    };

    private static LocalDateTime ts(Timestamp t) {
        return t != null ? t.toLocalDateTime() : null;
    }

    public List<OntologyVersion> findByOntology(String ontologyId) {
        return jdbcTemplate.query(
            "SELECT " + COLUMNS + " FROM ecos_ontology_version WHERE ontology_id = ? ORDER BY create_time DESC",
            ROW_MAPPER, ontologyId);
    }

    public Optional<OntologyVersion> findById(String id) {
        List<OntologyVersion> list = jdbcTemplate.query(
            "SELECT " + COLUMNS + " FROM ecos_ontology_version WHERE id = ?", ROW_MAPPER, id);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public Optional<OntologyVersion> findByOntologyAndVersion(String ontologyId, String versionNo) {
        List<OntologyVersion> list = jdbcTemplate.query(
            "SELECT " + COLUMNS + " FROM ecos_ontology_version WHERE ontology_id = ? AND version_no = ?",
            ROW_MAPPER, ontologyId, versionNo);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public Optional<OntologyVersion> findLatestPublished(String ontologyId) {
        List<OntologyVersion> list = jdbcTemplate.query(
            "SELECT " + COLUMNS + " FROM ecos_ontology_version WHERE ontology_id = ? AND status = 'Published' ORDER BY create_time DESC LIMIT 1",
            ROW_MAPPER, ontologyId);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public int insert(OntologyVersion version) {
        return jdbcTemplate.update("""
            INSERT INTO ecos_ontology_version (id, ontology_id, version_no, status, snapshot_json, change_log, publisher, published_at, create_time)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW())
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
        return jdbcTemplate.query(
            "SELECT " + COLUMNS + " FROM ecos_ontology_version ORDER BY create_time DESC", ROW_MAPPER);
    }

    public String nextId() {
        Long max = jdbcTemplate.queryForObject(
            "SELECT COALESCE(MAX(NULLIF(regexp_replace(id, '\\D', '', 'g'), '')::bigint), 5000) "
                + "FROM ecos_ontology_version", Long.class);
        return "ver" + ((max == null ? 5000L : max) + 1L);
    }

    public Optional<OntologyVersion> findPreviousVersion(String ontologyId, LocalDateTime beforeTime) {
        List<OntologyVersion> list = jdbcTemplate.query(
            "SELECT " + COLUMNS + " FROM ecos_ontology_version WHERE ontology_id = ? AND create_time < ? ORDER BY create_time DESC LIMIT 1",
            ROW_MAPPER, ontologyId, beforeTime != null ? Timestamp.valueOf(beforeTime) : null);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }
}
