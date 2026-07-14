package com.chinacreator.gzcm.dccheng.ontology;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * JdbcTemplate 仓库 — 领域 CRUD（ecos_domain 表）
 */
@Repository
public class OntologyDomainRepository {

    private final JdbcTemplate jdbc;

    public OntologyDomainRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private final RowMapper<OntologyDomain> ROW_MAPPER = (rs, rn) -> {
        OntologyDomain d = new OntologyDomain();
        d.setId(rs.getString("id"));
        d.setCode(rs.getString("code"));
        d.setName(rs.getString("name"));
        d.setOwner(rs.getString("owner"));
        d.setDescription(rs.getString("description"));
        d.setStatus(rs.getString("status"));
        d.setSortOrder(rs.getInt("sort_order"));
        d.setCreatedAt(ts(rs.getTimestamp("created_at")));
        d.setUpdatedAt(ts(rs.getTimestamp("updated_at")));
        return d;
    };

    private static LocalDateTime ts(Timestamp t) {
        return t != null ? t.toLocalDateTime() : null;
    }

    public List<OntologyDomain> findAll() {
        return jdbc.query("SELECT * FROM ecos_domain ORDER BY sort_order, created_at DESC", ROW_MAPPER);
    }

    public Optional<OntologyDomain> findByCode(String code) {
        List<OntologyDomain> list = jdbc.query("SELECT * FROM ecos_domain WHERE code = ?", ROW_MAPPER, code);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public Optional<OntologyDomain> findById(String id) {
        List<OntologyDomain> list = jdbc.query("SELECT * FROM ecos_domain WHERE id = ?", ROW_MAPPER, id);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public boolean existsByCode(String code) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM ecos_domain WHERE code = ?", Long.class, code);
        return count != null && count > 0;
    }

    public int insert(OntologyDomain domain) {
        return jdbc.update("""
            INSERT INTO ecos_domain (id, code, name, owner, description, status, sort_order, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
            """, domain.getId(), domain.getCode(), domain.getName(),
            domain.getOwner(), domain.getDescription(), domain.getStatus(), domain.getSortOrder());
    }

    public int update(String id, String code, String name, String owner, String description, String status) {
        return jdbc.update("""
            UPDATE ecos_domain SET
                code = COALESCE(?, code),
                name = COALESCE(?, name),
                owner = COALESCE(?, owner),
                description = COALESCE(?, description),
                status = COALESCE(?, status),
                updated_at = NOW()
            WHERE id = ?
            """, code, name, owner, description, status, id);
    }

    public int delete(String id) {
        return jdbc.update("DELETE FROM ecos_domain WHERE id = ?", id);
    }

    public int countEntitiesByDomain(String domainCode) {
        Long count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM ecos_ontology_entity e " +
            "INNER JOIN ecos_domain d ON e.domain_id = d.id " +
            "WHERE d.code = ?", Long.class, domainCode);
        return count != null ? count.intValue() : 0;
    }
}
