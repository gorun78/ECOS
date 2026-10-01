package com.chinacreator.gzcm.engine.data.dao;

import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

/**
 * B1 数据访问收口（R2-B1 / 架构铁律 §2.5 #1 / IR06）：{@code PipelineExecutionEngine}
 * （YAML 执行器，F02-03 已 {@code @Deprecated} 只读）内联的 Pipeline/Run/Step/Source/Resource
 * gateway raw-SQL 落点。
 *
 * <p>该引擎因 F02-03 单模型守门（{@code PipelineSingleModelArchTest} 源码扫描）被固定在
 * {@code ..service..} 路径，无法整体移到 {@code ..dao..}；故仅把其对
 * {@code jdbcTemplate} 的直接依赖剥离为此 DAO，方法体执行逻辑原样留在引擎。
 * 每个方法只搬不改 SQL 语义/表名/字段/参数序。</p>
 */
@Repository
public class PipelineRunDao extends JdbcTemplateDaoSupport {

    /** run 置 RUNNING。 */
    public int markRunRunning(String runId, Timestamp startedAt) {
        return jdbcTemplate.update(
            "UPDATE ecos_pipeline_run SET status = 'RUNNING', started_at = ? WHERE id = ?",
            startedAt, runId);
    }

    /** 取 task（含 yaml_content + config_json 文本化）。 */
    public Map<String, Object> findTask(String taskId) {
        return jdbcTemplate.queryForMap(
            "SELECT id, name, yaml_content, config_json::text as config_json, status FROM ecos_pipeline_task WHERE id = ?",
            taskId);
    }

    /** 按 step_order 取某 task 的全部 step。 */
    public List<Map<String, Object>> findStepsByTask(String taskId) {
        return jdbcTemplate.queryForList(
            "SELECT id, task_id, step_order, node_id, node_type, config_json::text as config_json, depends_on::text as depends_on FROM ecos_pipeline_step WHERE task_id = ? ORDER BY step_order",
            taskId);
    }

    /** run 置 FAILED（验证失败提前返回路径）。 */
    public int markRunFailed(String runId, String errorMsg) {
        return jdbcTemplate.update(
            "UPDATE ecos_pipeline_run SET status = 'FAILED', error_msg = ? WHERE id = ?",
            errorMsg, runId);
    }

    /** 取某 run 的全部 step_run（created_at 序）。 */
    public List<Map<String, Object>> findStepRuns(String runId) {
        return jdbcTemplate.queryForList(
            "SELECT id, run_id, step_id, node_id, status, rows_input, rows_output, started_at, finished_at, elapsed_ms, error_msg, created_at FROM ecos_pipeline_step_run WHERE run_id = ? ORDER BY created_at",
            runId);
    }

    /** step_run 置 RUNNING。 */
    public int markStepRunRunning(String stepRunId, Timestamp startedAt) {
        return jdbcTemplate.update(
            "UPDATE ecos_pipeline_step_run SET status = 'RUNNING', started_at = ? WHERE id = ?",
            startedAt, stepRunId);
    }

    /** step_run 置 SUCCEEDED（finished_at=NOW()，落 elapsed/rows）。 */
    public int markStepRunSucceeded(String stepRunId, long elapsedMs, int rowsInput, int rowsOutput) {
        return jdbcTemplate.update(
            "UPDATE ecos_pipeline_step_run SET status = 'SUCCEEDED', finished_at = NOW(), elapsed_ms = ?, rows_input = ?, rows_output = ? WHERE id = ?",
            elapsedMs, rowsInput, rowsOutput, stepRunId);
    }

    /** run 累计 completed_steps。 */
    public int bumpRunCompletedSteps(String runId, int completed) {
        return jdbcTemplate.update(
            "UPDATE ecos_pipeline_run SET completed_steps = ? WHERE id = ?",
            completed, runId);
    }

    /** step_run 置 FAILED（降级/终止路径，带 retry_count）。 */
    public int markStepRunFailed(String stepRunId, long elapsedMs, String errorMsg, int retryCount) {
        return jdbcTemplate.update(
            "UPDATE ecos_pipeline_step_run SET status = 'FAILED', finished_at = NOW(), elapsed_ms = ?, error_msg = ?, retry_count = ? WHERE id = ?",
            elapsedMs, errorMsg, retryCount, stepRunId);
    }

    /** run 置 SUCCEEDED（finished_at=NOW()，落 elapsed/completed）。 */
    public int markRunSucceeded(String runId, long elapsedMs, int completedSteps) {
        return jdbcTemplate.update(
            "UPDATE ecos_pipeline_run SET status = 'SUCCEEDED', finished_at = NOW(), elapsed_ms = ?, completed_steps = ? WHERE id = ?",
            elapsedMs, completedSteps, runId);
    }

    /** task 置 SUCCEEDED。 */
    public int markTaskSucceeded(String taskId) {
        return jdbcTemplate.update(
            "UPDATE ecos_pipeline_task SET status = 'SUCCEEDED' WHERE id = ?",
            taskId);
    }

    /** 顶层 catch 兜底：run 置 FAILED（finished_at=NOW()，落 elapsed/error）。 */
    public int finalizeRunFailed(String runId, long elapsedMs, String errorDetail) {
        return jdbcTemplate.update(
            "UPDATE ecos_pipeline_run SET status = 'FAILED', finished_at = NOW(), elapsed_ms = ?, error_msg = ? WHERE id = ?",
            elapsedMs, errorDetail, runId);
    }

    /** SOURCE 步骤取数据源连接配置。 */
    public Map<String, Object> findDatasourceConfig(String datasourceId) {
        return jdbcTemplate.queryForMap(
            "SELECT connection_config FROM td_datasource WHERE id = ?",
            datasourceId);
    }

    /** 取某 run 对应 sink step 的 config_json（子查询锁 task_id）。 */
    public List<Map<String, Object>> findSinkStepConfigs(String runId) {
        return jdbcTemplate.queryForList(
            "SELECT config_json::text as config_json FROM ecos_pipeline_step WHERE task_id = "
            + "(SELECT task_id FROM ecos_pipeline_run WHERE id = ?) AND node_type = 'sink'",
            runId);
    }

    /** SINK 分层回填：按 source_path 更新 td_data_resource.layer。 */
    public int updateResourceLayer(String layer, String sourcePath) {
        return jdbcTemplate.update(
            "UPDATE td_data_resource SET layer = ? WHERE source_path = ?",
            layer, sourcePath);
    }

    /** run 行整体读取（不存在时调用方判空自行抛）。 */
    public List<Map<String, Object>> findRun(String runId) {
        return jdbcTemplate.queryForList(
            "SELECT id, task_id, status, total_steps, completed_steps, triggered_by, "
            + "log_json::text as log_json, started_at, finished_at, elapsed_ms, error_msg, created_at "
            + "FROM ecos_pipeline_run WHERE id = ?",
            runId);
    }

    /** SINK 真写入：动态 INSERT（含 ON CONFLICT DO NOTHING 依 writeMode）批量落目标表。 */
    public int[] batchWrite(String sql, List<Object[]> batchArgs) {
        return jdbcTemplate.batchUpdate(sql, batchArgs);
    }
}
