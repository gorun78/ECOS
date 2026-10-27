package com.chinacreator.gzcm.engine.kb.dao;

import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * 数据访问收口（R2-B2b kb 子包 / 架构铁律 §2.5 / IR06）：KB 导入抽取审计台账 JDBC 落点。
 * <p>V200/R3-B1: schema 由 `${kb.control-schema:ecos_knowledge}` 注入 (MC06 全面落地)。</p>
 *
 * <p><b>#146h D-7 收口（2026-10-27）</b>：写端由裸名 {@code kb_extract_audit}（V142，
 * BIGSERIAL 形态，dev PG 库面不存在、未实跑）切至 V179 合规新表
 * {@code ecos_knowledge.ecos_kb_extract_audit}（{@code VARCHAR(36)} 应用侧 UUID PK，MC01）；
 * 列契约随新表：{@code tier→extract_tier}、{@code mode→extract_mode}；
 * 时间/基线列（create_time 等）取 DDL 默认值不显式写。
 * 裸名 V142 判 knownLegacy 停再跑（见其头注注记）。</p>
 */
@Repository
public class KbExtractAuditDao extends JdbcTemplateDaoSupport {

    private final String schema;

    public KbExtractAuditDao(
            @Value("${kb.control-schema:ecos_knowledge}") String schema) {
        this.schema = schema;
    }

    /**
     * 写 ecos_kb_extract_audit 行（PK {@code id} 应用侧生成 UUID，MC01/VARCHAR(36)）。
     */
    public void insertAudit(String jobId, String taskId, String tier, String mode, String status,
                            long durationMs, long rowsTotal, long rowsOk,
                            long rowsFailed, long mismatched, String errorMessage) {
        jdbcTemplate.update(
                "INSERT INTO " + schema + ".ecos_kb_extract_audit "
                        + "(id, job_id, task_id, extract_tier, extract_mode, status, duration_ms, rows_total, rows_ok, rows_failed, mismatched, error_message) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID().toString(),
                jobId, taskId, tier, mode, status,
                durationMs, rowsTotal, rowsOk, rowsFailed, mismatched, errorMessage);
    }
}
