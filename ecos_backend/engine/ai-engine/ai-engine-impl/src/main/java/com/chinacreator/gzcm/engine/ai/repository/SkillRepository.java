package com.chinacreator.gzcm.engine.ai.repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.chinacreator.gzcm.engine.ai.entity.SkillEntity;

/**
 * JdbcTemplate 仓库 — Skill CRUD
 * 对应 ecos_skill 表
 */
@Repository
public class SkillRepository {

    private static final Logger log = LoggerFactory.getLogger(SkillRepository.class);

    private final JdbcTemplate jdbc;

    public SkillRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

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
        return jdbc.query(sql.toString(), ROW_MAPPER, params.toArray());
    }

    public Optional<SkillEntity> findById(Long id) {
        String sql = "SELECT * FROM ecos_skill WHERE id = ?";
        List<SkillEntity> list = jdbc.query(sql, ROW_MAPPER, id);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public List<SkillEntity> findByName(String name) {
        return jdbc.query("SELECT * FROM ecos_skill WHERE name = ? ORDER BY created_at DESC", ROW_MAPPER, name);
    }

    public int insert(SkillEntity entity) {
        String sql = """
            INSERT INTO ecos_skill (name, description, version, enabled, category, package_info, created_by, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
            """;
        return jdbc.update(sql,
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
        return jdbc.update(sql,
            entity.getName(), entity.getDescription(), entity.getVersion(),
            entity.getEnabled(), entity.getCategory(), entity.getPackageInfo(),
            entity.getCreatedBy(), entity.getId());
    }

    public int updateEnabled(Long id, boolean enabled) {
        return jdbc.update("UPDATE ecos_skill SET enabled = ?, updated_at = NOW() WHERE id = ?", enabled, id);
    }

    public int deleteById(Long id) {
        return jdbc.update("DELETE FROM ecos_skill WHERE id = ?", id);
    }

    public long count() {
        Long result = jdbc.queryForObject("SELECT COUNT(*) FROM ecos_skill", Long.class);
        return result != null ? result : 0L;
    }

    /** 检查表是否存在，不存在则创建 */
    public void ensureTable() {
        try {
            jdbc.execute("""
                CREATE TABLE IF NOT EXISTS ecos_skill (
                    id              BIGSERIAL       PRIMARY KEY,
                    name            VARCHAR(255)    NOT NULL,
                    description     TEXT,
                    version         VARCHAR(50)     DEFAULT '1.0.0',
                    enabled         BOOLEAN         DEFAULT TRUE,
                    category        VARCHAR(100),
                    package_info    TEXT,
                    created_by      VARCHAR(100),
                    created_at      TIMESTAMP       DEFAULT NOW(),
                    updated_at      TIMESTAMP       DEFAULT NOW()
                )
                """);
            log.info("Table ecos_skill ensured");
        } catch (Exception ex) {
            log.warn("Failed to ensure table ecos_skill: {}", ex.getMessage());
        }
    }
}
