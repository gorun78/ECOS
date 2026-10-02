package com.chinacreator.gzcm.engine.kb.dao;

import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

/**
 * 数据访问收口（R2-B2b kb 子包 / 架构铁律 §2.5 / IR06）：结构化实例抽取定时任务 JDBC 落点。
 *
 * <p>SQL 原样搬运自原 {@code service.kb.KBExtractScheduleServiceImpl}：</p>
 * <ul>
 *   <li>主源事实源表 {@code td_runtime_task_plan}（<b>无 schema 前缀</b>，verbatim 保留）</li>
 *   <li>旧 fallback 镜像表 {@code ${kb.control-schema:ecos_knowledge}}.kb_scheduled_extract</li>
 * </ul>
 * 异常语义（主源失败回退旧表 / 旧表失败 warn）仍由 Service 层 try/catch 维持，DAO 只抛。
 *
 * <p>V200/R3-B1: {@code LEGACY_TABLE} 的 schema 由 {@code ${kb.control-schema:ecos_knowledge}} 注入 (MC06 全面落地)。</p>
 */
@Repository
public class KBExtractScheduleDao extends JdbcTemplateDaoSupport {

    /**
     * 旧 fallback 镜像表（V141，已 deprecate）。
     * <p>V200/R3-B1: schema 由 {@code ${kb.control-schema:ecos_knowledge}} 注入（MC06），
     * 转写为非静态 final 字段以承载运行时 schema。</p>
     */
    private final String LEGACY_TABLE;

    /**
     * runtime-task plan 事实源表（V152 W4 持久化）。
     * <p>无 schema 前缀（verbatim 保留，非本引擎控制域协议表），不进 MC06 配置化。</p>
     */
    static final String PLAN_TABLE = "td_runtime_task_plan";

    public KBExtractScheduleDao(@Value("${kb.control-schema:ecos_knowledge}") String schema) {
        this.LEGACY_TABLE = schema + ".kb_scheduled_extract";
    }

    // ── 主源 td_runtime_task_plan ─────────────────────────────────────

    /** list 主源计数。 */
    public Number countPlans(Boolean enabled, String taskType) {
        String enabledSql = enabled == null ? "" : (enabled ? " AND task_status = 'RUNNING'" : " AND task_status = 'PAUSED'");
        String totalSql = "SELECT COUNT(*) AS c FROM " + PLAN_TABLE +
                " WHERE task_type = ? AND is_deleted = 0" + enabledSql;
        return jdbcTemplate.queryForObject(totalSql, Number.class, taskType);
    }

    /** list 主源行。 */
    public List<Map<String, Object>> listPlans(Boolean enabled, String taskType, int pageSize, int offset) {
        String enabledSql = enabled == null ? "" : (enabled ? " AND task_status = 'RUNNING'" : " AND task_status = 'PAUSED'");
        String sql = "SELECT task_id, task_name, task_type, cron_expression, next_run_at, "
                + "last_run_at, last_status, task_status, parameters, created_by, create_time "
                + "FROM " + PLAN_TABLE +
                " WHERE task_type = ? AND is_deleted = 0 " + enabledSql +
                " ORDER BY create_time DESC LIMIT ? OFFSET ?";
        return jdbcTemplate.queryForList(sql, taskType, pageSize, offset);
    }

    /** update 主源：名称/参数/状态。 */
    public void updatePlan(String taskName, String parametersJson, String taskStatus, String taskId) {
        jdbcTemplate.update("UPDATE " + PLAN_TABLE
                + " SET task_name = ?, parameters = ?::jsonb, task_status = ? "
                + " WHERE task_id = ? AND is_deleted = 0",
                taskName, parametersJson, taskStatus, taskId);
    }

    /** delete 主源：逻辑删除。 */
    public void softDeletePlan(String taskId) {
        jdbcTemplate.update("UPDATE " + PLAN_TABLE
                + " SET is_deleted = 1, update_time = NOW() "
                + " WHERE task_id = ? AND is_deleted = 0",
                taskId);
    }

    /** pause：task_status='PAUSED'。 */
    public int pausePlan(String taskId) {
        return jdbcTemplate.update("UPDATE " + PLAN_TABLE
                + " SET task_status = 'PAUSED', update_time = NOW() "
                + " WHERE task_id = ? AND is_deleted = 0", taskId);
    }

    /** resume：task_status='RUNNING'。 */
    public int resumePlan(String taskId) {
        return jdbcTemplate.update("UPDATE " + PLAN_TABLE
                + " SET task_status = 'RUNNING', update_time = NOW() "
                + " WHERE task_id = ? AND is_deleted = 0", taskId);
    }

    /** 读 plan 单行（含 is_deleted 过滤）。 */
    public List<Map<String, Object>> fetchPlanRows(String scheduleId) {
        return jdbcTemplate.queryForList(
                "SELECT task_id, task_name, task_type, cron_expression, next_run_at, "
                        + "last_run_at, last_status, task_status, parameters, description, "
                        + "created_by, create_time FROM " + PLAN_TABLE
                        + " WHERE task_id = ? AND is_deleted = 0",
                scheduleId);
    }

    // ── 旧 fallback 镜像表 kb_scheduled_extract ──────────────────────────

    /** 旧表 fallback 行查询。 */
    public List<Map<String, Object>> queryLegacyRows(Boolean enabled, int limit, int offset) {
        String enabledSql = enabled == null ? "" : (enabled ? " AND enabled = 1" : " AND enabled = 0");
        return jdbcTemplate.queryForList(
                "SELECT id, schedule_id, name, "
                        + "convert_from(ontology_ids::bytea,'UTF8') AS ontology_ids, "
                        + "mode, period, cron_expression, next_run_at, enabled, last_run_at, "
                        + "last_status, created_at FROM " + LEGACY_TABLE
                        + " WHERE is_deleted = 0" + enabledSql
                        + " ORDER BY created_at DESC LIMIT ? OFFSET ?",
                limit, offset);
    }

    /** 旧表同步软删（无该行则 0 行）。 */
    public void softDeleteLegacy(String scheduleId) {
        jdbcTemplate.update("UPDATE " + LEGACY_TABLE
                + " SET is_deleted = 1, updated_at = NOW() WHERE schedule_id = ?",
                scheduleId);
    }

    /** 旧表列 sync（幂等；列名由调用方传入，调用方负责校验）。 */
    public void setLegacyColumn(String scheduleId, String column, String value) {
        jdbcTemplate.update("UPDATE " + LEGACY_TABLE + " SET " + column + " = ? WHERE schedule_id = ?",
                value, scheduleId);
    }

    /** 落旧镜像行（upsert，RETURNING id）。 */
    public Long insertLegacyMirror(String scheduleId, String name, String ontologyIdsJson,
                                   String mode, String period, String cron, Timestamp nextRunAt) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO " + LEGACY_TABLE
                        + " (schedule_id, name, ontology_ids, mode, period, cron_expression, "
                        + "next_run_at, enabled, created_by, prefers_runtime_task) "
                        + "VALUES (?, ?, ?::jsonb, ?, ?, ?, ?, 1, 'kb-scheduled-tb1', 'td_runtime_task_plan') "
                        + "ON CONFLICT (schedule_id) DO UPDATE SET "
                        + " name = EXCLUDED.name, mode = EXCLUDED.mode, period = EXCLUDED.period, "
                        + " cron_expression = EXCLUDED.cron_expression, "
                        + " next_run_at = EXCLUDED.next_run_at, updated_at = NOW(), "
                        + " ontology_ids = EXCLUDED.ontology_ids "
                        + " RETURNING id",
                Long.class, scheduleId, name,
                ontologyIdsJson, mode, period, cron, nextRunAt);
    }
}
