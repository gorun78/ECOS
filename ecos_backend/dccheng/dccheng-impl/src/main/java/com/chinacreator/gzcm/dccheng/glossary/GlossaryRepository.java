package com.chinacreator.gzcm.dccheng.glossary;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * JdbcTemplate 仓库 — 术语库 CRUD
 * 对应 ecos_glossary_term 表
 */
@Repository
public class GlossaryRepository {

    private static final Logger log = LoggerFactory.getLogger(GlossaryRepository.class);

    private final JdbcTemplate jdbc;

    public GlossaryRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private final RowMapper<GlossaryEntity> ROW_MAPPER = (rs, rowNum) -> {
        GlossaryEntity e = new GlossaryEntity();
        e.setId(rs.getLong("id"));
        e.setCode(rs.getString("code"));
        e.setName(rs.getString("name"));
        e.setDefinition(rs.getString("definition"));
        e.setDomain(rs.getString("domain"));
        e.setOwner(rs.getString("owner"));
        e.setStatus(rs.getString("status"));
        e.setCreatedBy(rs.getString("created_by"));
        e.setCreatedAt(rs.getTimestamp("created_at") != null
            ? rs.getTimestamp("created_at").toLocalDateTime() : null);
        e.setUpdatedAt(rs.getTimestamp("updated_at") != null
            ? rs.getTimestamp("updated_at").toLocalDateTime() : null);
        return e;
    };

    public List<GlossaryEntity> findAll(String domain, String status) {
        StringBuilder sql = new StringBuilder("SELECT * FROM ecos_glossary_term WHERE 1=1");
        if (domain != null && !domain.isBlank()) {
            sql.append(" AND domain = ?");
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND status = ?");
        }
        sql.append(" ORDER BY created_at DESC");

        List<Object> params = new java.util.ArrayList<>();
        if (domain != null && !domain.isBlank()) params.add(domain);
        if (status != null && !status.isBlank()) params.add(status);

        return jdbc.query(sql.toString(), ROW_MAPPER, params.toArray());
    }

    public Optional<GlossaryEntity> findById(Long id) {
        String sql = "SELECT * FROM ecos_glossary_term WHERE id = ?";
        List<GlossaryEntity> list = jdbc.query(sql, ROW_MAPPER, id);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public int insert(GlossaryEntity entity) {
        String sql = """
            INSERT INTO ecos_glossary_term (code, name, definition, domain, owner, status, created_by, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
            """;
        return jdbc.update(sql,
            entity.getCode(), entity.getName(), entity.getDefinition(),
            entity.getDomain(), entity.getOwner(), entity.getStatus(),
            entity.getCreatedBy());
    }

    public int update(GlossaryEntity entity) {
        String sql = """
            UPDATE ecos_glossary_term SET
                code = COALESCE(?, code),
                name = COALESCE(?, name),
                definition = COALESCE(?, definition),
                domain = COALESCE(?, domain),
                owner = COALESCE(?, owner),
                status = COALESCE(?, status),
                created_by = COALESCE(?, created_by),
                updated_at = NOW()
            WHERE id = ?
            """;
        return jdbc.update(sql,
            entity.getCode(), entity.getName(), entity.getDefinition(),
            entity.getDomain(), entity.getOwner(), entity.getStatus(),
            entity.getCreatedBy(), entity.getId());
    }

    public int deleteById(Long id) {
        return jdbc.update("DELETE FROM ecos_glossary_term WHERE id = ?", id);
    }

    public long count() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM ecos_glossary_term", Long.class);
    }
}
