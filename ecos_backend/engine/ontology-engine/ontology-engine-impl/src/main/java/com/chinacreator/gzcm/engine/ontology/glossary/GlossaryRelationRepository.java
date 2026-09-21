package com.chinacreator.gzcm.engine.ontology.glossary;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * JdbcTemplate 仓库 — 词条关系边 CRUD，对应 ecos_glossary_term_relation 表。
 *
 * <p>显式列出查询列（禁止 {@code SELECT *}）；边为有向二元关系，
 * 两端词条删除时由外键 {@code ON DELETE CASCADE} 一并清理。
 */
@Repository
public class GlossaryRelationRepository {

    /** 显式列清单 */
    private static final String COLUMNS =
        "id, from_term_id, to_term_id, relation_type, weight, description, created_by, created_at";

    private final JdbcTemplate jdbc;

    public GlossaryRelationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

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

    /**
     * 按条件查询关系边。
     *
     * @param fromTermId   起点词条 id，可空
     * @param toTermId     终点词条 id，可空
     * @param relationType 边类型，可空
     * @param termId       任一端词条 id（起点或终点命中），可空
     */
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

        return jdbc.query(sql.toString(), rowMapper, params.toArray());
    }

    /** 按 id 查关系边。 */
    public Optional<GlossaryRelationEntity> findById(Long id) {
        String sql = "SELECT " + COLUMNS + " FROM ecos_glossary_term_relation WHERE id = ?";
        List<GlossaryRelationEntity> list = jdbc.query(sql, rowMapper, id);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    /**
     * 查询任一端落在给定词条集合内的全部边（图谱逐层展开用）。
     *
     * @param termIds 词条 id 集合，空集合返回空列表
     */
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
        return jdbc.query(sql, rowMapper, params.toArray());
    }

    /** 判定同向同类型边是否已存在（幂等新增用）。 */
    public boolean exists(Long fromTermId, Long toTermId, String relationType) {
        String sql = "SELECT COUNT(*) FROM ecos_glossary_term_relation"
            + " WHERE from_term_id = ? AND to_term_id = ? AND relation_type = ?";
        Integer count = jdbc.queryForObject(sql, Integer.class, fromTermId, toTermId, relationType);
        return count != null && count > 0;
    }

    /**
     * 新增关系边。
     *
     * <p>以 {@code RETURNING id} 回填自增主键到入参实体（创建后回读与前端定位依赖该 id）。
     */
    public int insert(GlossaryRelationEntity entity) {
        String sql = """
            INSERT INTO ecos_glossary_term_relation
                (from_term_id, to_term_id, relation_type, weight, description, created_by, created_at)
            VALUES (?, ?, ?, ?, ?, ?, NOW())
            RETURNING id
            """;
        Long id = jdbc.queryForObject(sql, Long.class,
            entity.getFromTermId(), entity.getToTermId(), entity.getRelationType(),
            entity.getWeight() == null ? 100 : entity.getWeight(),
            entity.getDescription(), entity.getCreatedBy());
        entity.setId(id);
        return id == null ? 0 : 1;
    }

    /** 删除关系边。 */
    public int deleteById(Long id) {
        return jdbc.update("DELETE FROM ecos_glossary_term_relation WHERE id = ?", id);
    }

    /** 关系边总数。 */
    public long count() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM ecos_glossary_term_relation", Long.class);
    }
}