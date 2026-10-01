package com.chinacreator.gzcm.engine.ontology.dao;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import com.chinacreator.gzcm.engine.ontology.glossary.GlossaryRelationEntity;
import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * B3c 数据访问收口（R2/B3c）：词条关系边 CRUD，对应 {@code ecos_glossary_term_relation} 表。
 *
 * <p>显式列清单（禁 {@code SELECT *}）；SQL 全量搬运自 {@code GlossaryRelationRepository}。</p>
 */
@Repository
public class GlossaryRelationDao extends JdbcTemplateDaoSupport {

    private static final String COLUMNS =
        "id, from_term_id, to_term_id, relation_type, weight, description, created_by, created_at";

    private final RowMapper<GlossaryRelationEntity> rowMapper = (rs, rowNum) -> {
        GlossaryRelationEntity e = new GlossaryRelationEntity();
        e.setId(rs.getLong("id"));
        e.setFromTermId(rs.getLong("from_term_id"));
        e.setToTermId(rs.getLong("to_term_id"));
        e.setRelationType(rs.getString("relation_type"));
        int weight = rs.getInt("weight");
        e.setWeight(rs.wasNull() ? null : weight);
        e.setDescription(rs.getString("description"));
        e.setCreatedBy(rs.getString("created_by"));
        e.setCreatedAt(rs.getTimestamp("created_at") != null
            ? rs.getTimestamp("created_at").toLocalDateTime() : null);
        return e;
    };

    public List<GlossaryRelationEntity> findAll(Long fromTermId, Long toTermId,
                                                String relationType, Long termId) {
        StringBuilder sql = new StringBuilder(
            "SELECT " + COLUMNS + " FROM ecos_glossary_term_relation WHERE 1=1");
        List<Object> params = new ArrayList<>();
        if (fromTermId != null) {
            sql.append(" AND from_term_id = ?");
            params.add(fromTermId);
        }
        if (toTermId != null) {
            sql.append(" AND to_term_id = ?");
            params.add(toTermId);
        }
        if (relationType != null && !relationType.isBlank()) {
            sql.append(" AND relation_type = ?");
            params.add(relationType);
        }
        if (termId != null) {
            sql.append(" AND (from_term_id = ? OR to_term_id = ?)");
            params.add(termId);
            params.add(termId);
        }
        sql.append(" ORDER BY relation_type, id");

        return jdbcTemplate.query(sql.toString(), rowMapper, params.toArray());
    }

    public Optional<GlossaryRelationEntity> findById(Long id) {
        String sql = "SELECT " + COLUMNS + " FROM ecos_glossary_term_relation WHERE id = ?";
        List<GlossaryRelationEntity> list = jdbcTemplate.query(sql, rowMapper, id);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public List<GlossaryRelationEntity> findEdgesByTermIds(List<Long> termIds) {
        if (termIds == null || termIds.isEmpty()) {
            return Collections.emptyList();
        }
        String placeholders = String.join(",", Collections.nCopies(termIds.size(), "?"));
        String sql = "SELECT " + COLUMNS + " FROM ecos_glossary_term_relation"
            + " WHERE from_term_id IN (" + placeholders + ")"
            + " OR to_term_id IN (" + placeholders + ")";
        List<Object> params = new ArrayList<>(termIds);
        params.addAll(termIds);
        return jdbcTemplate.query(sql, rowMapper, params.toArray());
    }

    public boolean exists(Long fromTermId, Long toTermId, String relationType) {
        String sql = "SELECT COUNT(*) FROM ecos_glossary_term_relation"
            + " WHERE from_term_id = ? AND to_term_id = ? AND relation_type = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, fromTermId, toTermId, relationType);
        return count != null && count > 0;
    }

    public int insert(GlossaryRelationEntity entity) {
        String sql = """
            INSERT INTO ecos_glossary_term_relation
                (from_term_id, to_term_id, relation_type, weight, description, created_by, created_at)
            VALUES (?, ?, ?, ?, ?, ?, NOW())
            RETURNING id
            """;
        Long id = jdbcTemplate.queryForObject(sql, Long.class,
            entity.getFromTermId(), entity.getToTermId(), entity.getRelationType(),
            entity.getWeight() == null ? 100 : entity.getWeight(),
            entity.getDescription(), entity.getCreatedBy());
        entity.setId(id);
        return id == null ? 0 : 1;
    }

    public int deleteById(Long id) {
        return jdbcTemplate.update("DELETE FROM ecos_glossary_term_relation WHERE id = ?", id);
    }

    public long count() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ecos_glossary_term_relation", Long.class);
    }
}
