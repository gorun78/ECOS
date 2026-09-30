package com.chinacreator.gzcm.engine.ai.repository;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.chinacreator.gzcm.engine.ai.entity.CronJobExecutionEntity;

/**
 * JdbcTemplate 仓库 — CronJob 执行历史
 * 对应 ecos_cron_job_execution 表
 */
@Repository
public class CronJobExecutionRepository {

    private static final Logger log = LoggerFactory.getLogger(CronJobExecutionRepository.class);

    private final JdbcTemplate jdbc;

    public CronJobExecutionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private final RowMapper<CronJobExecutionEntity> ROW_MAPPER = (rs, rowNum) -> {
        CronJobExecutionEntity e = new CronJobExecutionEntity();
        e.setId(rs.getLong("id"));
        e.setCronJobId(rs.getLong("cron_job_id"));
        e.setStartedAt(rs.getTimestamp("started_at") != null
            ? rs.getTimestamp("started_at").toLocalDateTime() : null);
        e.setFinishedAt(rs.getTimestamp("finished_at") != null
            ? rs.getTimestamp("finished_at").toLocalDateTime() : null);
        e.setStatus(rs.getString("status"));
        e.setResult(rs.getString("result"));
        e.setErrorMessage(rs.getString("error_message"));
        e.setCreatedAt(rs.getTimestamp("created_at") != null
            ? rs.getTimestamp("created_at").toLocalDateTime() : null);
        return e;
    };

    public List<CronJobExecutionEntity> findByCronJobId(Long cronJobId, int limit) {
        String sql = "SELECT * FROM ecos_cron_job_execution WHERE cron_job_id = ? ORDER BY created_at DESC LIMIT ?";
        return jdbc.query(sql, ROW_MAPPER, cronJobId, limit);
    }

    public int insert(CronJobExecutionEntity entity) {
        String sql = """
            INSERT INTO ecos_cron_job_execution (cron_job_id, started_at, finished_at, status, result, error_message, created_at)
            VALUES (?, ?, ?, ?, ?, ?, NOW())
            """;
        return jdbc.update(sql,
            entity.getCronJobId(), entity.getStartedAt(), entity.getFinishedAt(),
            entity.getStatus(), entity.getResult(), entity.getErrorMessage());
    }

    public long countByCronJobId(Long cronJobId) {
        Long result = jdbc.queryForObject(
            "SELECT COUNT(*) FROM ecos_cron_job_execution WHERE cron_job_id = ?", Long.class, cronJobId);
        return result != null ? result : 0L;
    }

    // H8-T1: ecos_cron_job_execution 建表 DDL 收编至 db/migration（V162），运行时不再内嵌 DDL。
}
