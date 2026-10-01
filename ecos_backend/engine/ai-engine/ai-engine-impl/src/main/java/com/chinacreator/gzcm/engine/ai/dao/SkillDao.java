package com.chinacreator.gzcm.engine.ai.dao;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.chinacreator.gzcm.engine.ai.entity.SkillEntity;
import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;

/**
 * 数据访问收口（R2-B4-ai / 架构铁律 §2.5 #1 / IR06）：{@code ecos_skill} CRUD 的 JDBC 落点。
 * <p>SQL 与 {@code RowMapper} 原样搬运自原 {@code repository.SkillRepository}，只搬不改语义。</p>
 */
@Repository
public class SkillDao extends JdbcTemplateDaoSupport {

    private static final Logger log = LoggerFactory.getLogger(SkillDao.class);


    private final RowMapper<SkillEntity> ROW_MAPPER = (rs, rowNum) -> {
        SkillEntity e = new SkillEntity();
        e.setId(rs.getLong("id"));
        e.setName(rs.getString("name"));
        e.setDescription(rs.getString("description"));
        e.setVersion(rs.getString("version"));
        e.setEnabled(rs.getBoolean("enabled"));
        e.setCategory(rs.getString("category"));
        e.setPackageInfo(rs.getString("package_info"));
        e.setCreatedBy(rs.getString("created_by"));
        e.setCreatedAt(rs.getTimestamp("created_at") != null
            ? rs.getTimestamp("created_at").toLocalDateTime() : null);
        e.setUpdatedAt(rs.getTimestamp("updated_at") != null
            ? rs.getTimestamp("updated_at").toLocalDateTime() : null);
        return e;
    };

    public List<SkillEntity> findAll(String category, Boolean enabled) {
        StringBuilder sql = new StringBuilder("SELECT * FROM ecos_skill WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (category != null && !category.isBlank()) {
            sql.append(" AND category = ?");
            params.add(category);
        }
        if (enabled != null) {
            sql.append(" AND enabled = ?");
            params.add(enabled);
        }
        sql.append(" ORDER BY created_at DESC");
        return jdbcTemplate.query(sql.toString(), ROW_MAPPER, params.toArray());
    }

    public Optional<SkillEntity> findById(Long id) {
        String sql = "SELECT * FROM ecos_skill WHERE id = ?";
        List<SkillEntity> list = jdbcTemplate.query(sql, ROW_MAPPER, id);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public List<SkillEntity> findByName(String name) {
        return jdbcTemplate.query("SELECT * FROM ecos_skill WHERE name = ? ORDER BY created_at DESC", ROW_MAPPER, name);
    }

    public int insert(SkillEntity entity) {
        String sql = """
            INSERT INTO ecos_skill (name, description, version, enabled, category, package_info, created_by, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
            """;
        return jdbcTemplate.update(sql,
            entity.getName(), entity.getDescription(), entity.getVersion(),
            entity.getEnabled(), entity.getCategory(), entity.getPackageInfo(),
            entity.getCreatedBy());
    }

    public int update(SkillEntity entity) {
        String sql = """
            UPDATE ecos_skill SET
                name = COALESCE(?, name),
                description = COALESCE(?, description),
                version = COALESCE(?, version),
                enabled = COALESCE(?, enabled),
                category = COALESCE(?, category),
                package_info = COALESCE(?, package_info),
                created_by = COALESCE(?, created_by),
                updated_at = NOW()
            WHERE id = ?
            """;
        return jdbcTemplate.update(sql,
            entity.getName(), entity.getDescription(), entity.getVersion(),
            entity.getEnabled(), entity.getCategory(), entity.getPackageInfo(),
            entity.getCreatedBy(), entity.getId());
    }

    public int updateEnabled(Long id, boolean enabled) {
        return jdbcTemplate.update("UPDATE ecos_skill SET enabled = ?, updated_at = NOW() WHERE id = ?", enabled, id);
    }

    public int deleteById(Long id) {
        return jdbcTemplate.update("DELETE FROM ecos_skill WHERE id = ?", id);
    }

    public long count() {
        Long result = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ecos_skill", Long.class);
        return result != null ? result : 0L;
    }

    // H8-T1: ecos_skill 建表 DDL 收编至 db/migration（V162），运行时不再内嵌 DDL。
}
