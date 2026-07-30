package com.chinacreator.gzcm.engine.ai.repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.chinacreator.gzcm.engine.ai.entity.CronJobEntity;

/**
 * JdbcTemplate 仓库 — CronJob CRUD
 * 对应 ecos_cron_job 表
 */
@Repository
public class CronJobRepository {

    private static final Logger log = LoggerFactory.getLogger(CronJobRepository.class);

    private final JdbcTemplate jdbc;

    public CronJobRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

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
        return jdbc.query(sql.toString(), ROW_MAPPER, params.toArray());
    }

    public Optional<CronJobEntity> findById(Long id) {
        String sql = "SELECT * FROM ecos_cron_job WHERE id = ?";
        List<CronJobEntity> list = jdbc.query(sql, ROW_MAPPER, id);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public int insert(CronJobEntity entity) {
        String sql = """
            INSERT INTO ecos_cron_job (name, cron_expression, description, enabled, last_run_at, next_run_at, status, created_by, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
            """;
        return jdbc.update(sql,
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
        return jdbc.update(sql,
            entity.getName(), entity.getCronExpression(), entity.getDescription(),
            entity.getEnabled(), entity.getLastRunAt(), entity.getNextRunAt(),
            entity.getStatus(), entity.getCreatedBy(), entity.getId());
    }

    public int updateEnabled(Long id, boolean enabled) {
        return jdbc.update("UPDATE ecos_cron_job SET enabled = ?, updated_at = NOW() WHERE id = ?", enabled, id);
    }

    public int deleteById(Long id) {
        return jdbc.update("DELETE FROM ecos_cron_job WHERE id = ?", id);
    }

    public long count() {
        Long result = jdbc.queryForObject("SELECT COUNT(*) FROM ecos_cron_job", Long.class);
        return result != null ? result : 0L;
    }

    /** 检查表是否存在，不存在则创建 */
    public void ensureTable() {
        try {
            jdbc.execute("""
                CREATE TABLE IF NOT EXISTS ecos_cron_job (
                    id              BIGSERIAL       PRIMARY KEY,
                    name            VARCHAR(255)    NOT NULL,
                    cron_expression VARCHAR(100),
                    description     TEXT,
                    enabled         BOOLEAN         DEFAULT TRUE,
                    last_run_at     TIMESTAMP,
                    next_run_at     TIMESTAMP,
                    status          VARCHAR(50)     DEFAULT 'IDLE',
                    created_by      VARCHAR(100),
                    created_at      TIMESTAMP       DEFAULT NOW(),
                    updated_at      TIMESTAMP       DEFAULT NOW()
                )
                """);
            log.info("Table ecos_cron_job ensured");
        } catch (Exception ex) {
            log.warn("Failed to ensure table ecos_cron_job: {}", ex.getMessage());
        }
    }
}
