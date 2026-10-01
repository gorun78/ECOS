package com.chinacreator.gzcm.engine.data.dao;

import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

/**
 * Pipeline 任务域数据访问（ecos_pipeline_task / ecos_pipeline_step /
 * ecos_pipeline_run / ecos_pipeline_step_run）。
 *
 * <p>架构铁律 §2.5#1 / IR06：Service 不持有 JdbcTemplate，一律经 runtime 数据访问基类落库。</p>
 */
@Repository
public class PipelineTaskDao extends JdbcTemplateDaoSupport {

    // ── task ──

    public void insertTask(String id, Object name, Object description, Object yamlContent,
                           Object gitUrl, Object gitBranch, Object status,
                           Object cronExpression, Object configJson, Object taskType, Object createdBy) {
        jdbcTemplate.update(
                "INSERT INTO ecos_pipeline_task (id, name, description, yaml_content, git_url, git_branch, status, cron_expression, config_json, task_type, created_by) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?)",
                id, name, description, yamlContent, gitUrl, gitBranch, status, cronExpression,
                configJson, taskType, createdBy);
    }

    public void updateTask(String id, Object name, Object description, Object yamlContent,
                           Object gitUrl, Object gitBranch, Object status,
                           Object cronExpression, Object taskType) {
        jdbcTemplate.update(
                "UPDATE ecos_pipeline_task SET name = COALESCE(?, name), description = COALESCE(?, description), "
                        + "yaml_content = COALESCE(?, yaml_content), git_url = COALESCE(?, git_url), "
                        + "git_branch = COALESCE(?, git_branch), status = COALESCE(?, status), "
                        + "cron_expression = COALESCE(?, cron_expression), task_type = COALESCE(?, task_type), "
                        + "updated_at = NOW() WHERE id = ?",
                name, description, yamlContent, gitUrl, gitBranch, status, cronExpression, taskType, id);
    }

    public List<Map<String, Object>> queryTask(String id) {
        return jdbcTemplate.queryForList("SELECT * FROM ecos_pipeline_task WHERE id = ?", id);
    }

    public int countTasks() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ecos_pipeline_task", Integer.class);
    }

    public List<Map<String, Object>> pagedTasks(int pageSize, int offset) {
        return jdbcTemplate.queryForList(
                "SELECT * FROM ecos_pipeline_task ORDER BY updated_at DESC LIMIT ? OFFSET ?", pageSize, offset);
    }

    public void deleteTask(String id) {
        jdbcTemplate.update("DELETE FROM ecos_pipeline_task WHERE id = ?", id);
    }

    public void upsertTaskFromGit(String id, String name, String description, String yamlContent,
                                  Object gitUrl, String gitBranch) {
        jdbcTemplate.update(
                "INSERT INTO ecos_pipeline_task (id, name, description, yaml_content, git_url, git_branch, status) "
                        + "VALUES (?, ?, ?, ?, ?, ?, 'DRAFT') ON CONFLICT (id) DO UPDATE SET yaml_content = EXCLUDED.yaml_content, updated_at = NOW()",
                id, name, description, yamlContent, gitUrl, gitBranch);
    }

    // ── step ──

    public void insertStep(String id, String taskId, int stepOrder, Object nodeId, Object nodeType,
                           Object configJson, Object dependsOn, Object positionX, Object positionY) {
        jdbcTemplate.update(
                "INSERT INTO ecos_pipeline_step (id, task_id, step_order, node_id, node_type, config_json, depends_on, position_x, position_y) "
                        + "VALUES (?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?)",
                id, taskId, stepOrder, nodeId, nodeType, configJson, dependsOn, positionX, positionY);
    }

    public List<Map<String, Object>> queryStepsByTask(String taskId) {
        return jdbcTemplate.queryForList(
                "SELECT * FROM ecos_pipeline_step WHERE task_id = ? ORDER BY step_order", taskId);
    }

    public void deleteStepsByTask(String taskId) {
        jdbcTemplate.update("DELETE FROM ecos_pipeline_step WHERE task_id = ?", taskId);
    }

    // ── run ──

    public void insertRun(String runId, String taskId, Object triggeredBy, int totalSteps) {
        jdbcTemplate.update(
                "INSERT INTO ecos_pipeline_run (id, task_id, status, triggered_by, total_steps, started_at) "
                        + "VALUES (?, ?, 'QUEUED', ?, ?, NOW())",
                runId, taskId, triggeredBy, totalSteps);
    }

    public List<Map<String, Object>> queryRunsByTask(String taskId) {
        return jdbcTemplate.queryForList(
                "SELECT * FROM ecos_pipeline_run WHERE task_id = ? ORDER BY created_at DESC LIMIT 50", taskId);
    }

    public List<Map<String, Object>> queryRun(String runId) {
        return jdbcTemplate.queryForList("SELECT * FROM ecos_pipeline_run WHERE id = ?", runId);
    }

    public void deleteRunsByTask(String taskId) {
        jdbcTemplate.update("DELETE FROM ecos_pipeline_run WHERE task_id = ?", taskId);
    }

    // ── step_run ──

    public void insertStepRun(String id, String runId, Object stepId, Object nodeId) {
        jdbcTemplate.update(
                "INSERT INTO ecos_pipeline_step_run (id, run_id, step_id, node_id, status) VALUES (?, ?, ?, ?, 'QUEUED')",
                id, runId, stepId, nodeId);
    }

    public List<Map<String, Object>> queryStepRunsByRun(String runId) {
        return jdbcTemplate.queryForList(
                "SELECT * FROM ecos_pipeline_step_run WHERE run_id = ? ORDER BY created_at", runId);
    }

    public void deleteStepRunsByTask(String taskId) {
        jdbcTemplate.update(
                "DELETE FROM ecos_pipeline_step_run WHERE run_id IN (SELECT id FROM ecos_pipeline_run WHERE task_id = ?)",
                taskId);
    }

    // ── 取消 ──

    public void cancelRun(String runId) {
        jdbcTemplate.update(
                "UPDATE ecos_pipeline_run SET status = 'CANCELLED', finished_at = NOW() WHERE id = ? AND status IN ('QUEUED','RUNNING')",
                runId);
        jdbcTemplate.update(
                "UPDATE ecos_pipeline_step_run SET status = 'CANCELLED', finished_at = NOW() WHERE run_id = ? AND status IN ('QUEUED','RUNNING')",
                runId);
    }
}
