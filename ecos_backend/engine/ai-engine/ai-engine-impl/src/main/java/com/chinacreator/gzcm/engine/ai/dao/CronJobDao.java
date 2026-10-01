package com.chinacreator.gzcm.engine.ai.dao;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.chinacreator.gzcm.engine.ai.entity.CronJobEntity;
import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;

/**
 * 数据访问收口（R2-B4-ai / 架构铁律 §2.5 #1 / IR06）：{@code ecos_cron_job} CRUD 的 JDBC 落点。
 * <p>SQL 与 {@code RowMapper} 原样搬运自原 {@code repository.CronJobRepository}，只搬不改语义。</p>
 */
@Repository
public class CronJobDao extends JdbcTemplateDaoSupport {

    private static final Logger log = LoggerFactory.getLogger(CronJobDao.class);

    private final RowMapper<CronJobEntity> ROW_MAPPER = (rs, rowNum) -> {
        CronJobEntity e = new CronJobEntity();
        e.setId(rs.getLong("id"));
        e.setName(rs.getString("name"));
        e.setCronExpression(rs.getString("cron_expression"));
        e.setDescription(rs.getString("description"));
        e.setEnabled(rs.getBoolean("enabled"));
        e.setLastRunAt(rs.getTimestamp("last_run_at") != null
            ? rs.getTimestamp("last_run_at").toLocalDateTime() : null);
        e.setNextRunAt(rs.getTimestamp("next_run_at") != null
            ? rs.getTimestamp("next_run_at").toLocalDateTime() : null);
        e.setStatus(rs.getString("status"));
        e.setCreatedBy(rs.getString("created_by"));
        e.setCreatedAt(rs.getTimestamp("created_at") != null
            ? rs.getTimestamp("created_at").toLocalDateTime() : null);
        e.setUpdatedAt(rs.getTimestamp("updated_at") != null
            ? rs.getTimestamp("updated_at").toLocalDateTime() : null);
        return e;
    };

    public List<CronJobEntity> findAll(String status, Boolean enabled) {
        StringBuilder sql = new StringBuilder("SELECT * FROM ecos_cron_job WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (status != null && !status.isBlank()) {
            sql.append(" AND status = ?");
            params.add(status);
        }
        if (enabled != null) {
            sql.append(" AND enabled = ?");
            params.add(enabled);
        }
        sql.append(" ORDER BY created_at DESC");
        return jdbcTemplate.query(sql.toString(), ROW_MAPPER, params.toArray());
    }

    public Optional<CronJobEntity> findById(Long id) {
        String sql = "SELECT * FROM ecos_cron_job WHERE id = ?";
        List<CronJobEntity> list = jdbcTemplate.query(sql, ROW_MAPPER, id);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public int insert(CronJobEntity entity) {
        String sql = """
            INSERT INTO ecos_cron_job (name, cron_expression, description, enabled, last_run_at, next_run_at, status, created_by, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
            """;
        return jdbcTemplate.update(sql,
            entity.getName(), entity.getCronExpression(), entity.getDescription(),
            entity.getEnabled(), entity.getLastRunAt(), entity.getNextRunAt(),
            entity.getStatus(), entity.getCreatedBy());
    }

    public int update(CronJobEntity entity) {
        String sql = """
            UPDATE ecos_cron_job SET
                name = COALESCE(?, name),
                cron_expression = COALESCE(?, cron_expression),
                description = COALESCE(?, description),
                enabled = COALESCE(?, enabled),
                last_run_at = COALESCE(?, last_run_at),
                next_run_at = COALESCE(?, next_run_at),
                status = COALESCE(?, status),
                created_by = COALESCE(?, created_by),
                updated_at = NOW()
            WHERE id = ?
            """;
        return jdbcTemplate.update(sql,
            entity.getName(), entity.getCronExpression(), entity.getDescription(),
            entity.getEnabled(), entity.getLastRunAt(), entity.getNextRunAt(),
            entity.getStatus(), entity.getCreatedBy(), entity.getId());
    }

    public int updateEnabled(Long id, boolean enabled) {
        return jdbcTemplate.update("UPDATE ecos_cron_job SET enabled = ?, updated_at = NOW() WHERE id = ?", enabled, id);
    }

    public int deleteById(Long id) {
        return jdbcTemplate.update("DELETE FROM ecos_cron_job WHERE id = ?", id);
    }

    public long count() {
        Long result = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ecos_cron_job", Long.class);
        return result != null ? result : 0L;
    }

    // H8-T1: ecos_cron_job 建表 DDL 收编至 db/migration（V162），运行时不再内嵌 DDL。
}
