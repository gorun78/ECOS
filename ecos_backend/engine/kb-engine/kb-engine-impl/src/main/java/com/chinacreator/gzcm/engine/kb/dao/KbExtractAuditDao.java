package com.chinacreator.gzcm.engine.kb.dao;

import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

/**
 * 数据访问收口（R2-B2b kb 子包 / 架构铁律 §2.5 / IR06）：KB 导入抽取审计台账 JDBC 落点。
 * <p>SQL 原样搬运自原 {@code task.KbImportTaskExecutor#writeAudit}（schema
 * {@code ecos_knowledge.kb_extract_audit} 保留）。</p>
 * <p>V200/R3-B1: schema 由 `${kb.control-schema:ecos_knowledge}` 注入 (MC06 全面落地)。</p>
 */
@Repository
public class KbExtractAuditDao extends JdbcTemplateDaoSupport {

    private final String schema;

    public KbExtractAuditDao(
            @Value("${kb.control-schema:ecos_knowledge}") String schema) {
        this.schema = schema;
    }

    /** 写 kb_extract_audit 行。 */
    public void insertAudit(String jobId, String taskId, String tier, String mode, String status,
                            long durationMs, long rowsTotal, long rowsOk,
                            long rowsFailed, long mismatched, String errorMessage) {
        jdbcTemplate.update(
                "INSERT INTO " + schema + ".kb_extract_audit "
                        + "(job_id, task_id, tier, mode, status, duration_ms, rows_total, rows_ok, rows_failed, mismatched, error_message) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                jobId, taskId, tier, mode, status,
                durationMs, rowsTotal, rowsOk, rowsFailed, mismatched, errorMessage);
    }
}
