package com.chinacreator.gzcm.runtime.core.task.persistence.impl;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.chinacreator.gzcm.runtime.core.task.model.TaskDescription;
import com.chinacreator.gzcm.runtime.core.task.model.TaskExecutionPlan;
import com.chinacreator.gzcm.runtime.core.task.model.TaskStatus;
import com.chinacreator.gzcm.runtime.core.task.persistence.ITaskPersistenceService;
import com.chinacreator.gzcm.runtime.core.task.persistence.TaskPersistenceException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * PMO-72 W4 — JdbcTemplate 直连 sys_man 的任务持久化服务。
 *
 * <p>替代 {@link DatabaseTaskPersistenceServiceImpl} 通过 ISystemDatabaseAccess 反射落库的"假实现"。
 * 这里 5 张表 (td_runtime_task / _plan / _status / _log / _execution) 全部走 Spring JdbcTemplate
 * (HikariCP 默认池, IR01/EN01)。全部 SQL 列名必须双引号包裹 (pg_attribute quote_ident 验证)。</p>
 *
 * <p>失败容忍：{@code saveX} 失败抛 {@link TaskPersistenceException}（与接口契约一致）；
 * {@code updatePlanXxxPersist} / {@code logTask} / {@code recordExecution} 失败仅 warn，吞掉不抛（双态同 Kafka）。</p>
 *
 * @author PMO Worker (W4)
 */
@Service
@ConditionalOnProperty(name = "runtime.task.persistence.enabled", havingValue = "true", matchIfMissing = true)
public class JdbcTaskPersistenceService implements ITaskPersistenceService {

    private static final Logger logger = LoggerFactory.getLogger(JdbcTaskPersistenceService.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final TypeReference<List<String>> LIST_TYPE = new TypeReference<List<String>>() { };
    private static final TypeReference<Object> JSON_ANY = new TypeReference<Object>() { };

    private static final String T_TASK = "td_runtime_task";
    private static final String T_PLAN = "td_runtime_task_plan";
    private static final String T_STATUS = "td_runtime_task_status";
    private static final String T_EXEC = "td_runtime_task_execution";
    private static final String T_LOG = "td_runtime_task_log";

    private final JdbcTemplate jdbcTemplate;

    @SuppressWarnings("unused")
    @Value("${spring.datasource.url:}")
    private String jdbcUrl;

    public JdbcTaskPersistenceService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // ============================= 任务 =============================

    @Override
    public void saveTask(TaskDescription task) throws TaskPersistenceException {
        if (task == null || task.getTaskId() == null) {
            throw new TaskPersistenceException("TaskDescription.taskId 不能为空");
        }
        try {
            String sql = "INSERT INTO " + T_TASK +
                    " (\"TASK_ID\",\"TASK_NAME\",\"TASK_TYPE\",\"DESCRIPTION\",\"TASK_CONFIG\"," +
                    "  \"PARAMETERS\",\"PRIORITY\",\"TIMEOUT\",\"RETRY_COUNT\",\"ASYNC_FLAG\"," +
                    "  \"DEPENDENCIES\",\"SCHEDULE_ID\",\"NODE_ID\",\"EXECUTION_MODE\"," +
                    "  \"CREATED_BY\",\"TENANT_ID\",\"TAGS\",\"EXTENSIONS\"," +
                    "  \"CREATED_TIME\",\"UPDATED_TIME\",\"create_time\",\"update_time\"," +
                    "  \"create_by\",\"update_by\",\"is_deleted\",\"domain\",\"version_no\") " +
                    "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?, ?,NOW(),NOW(),?,?,?,0,'default','1') " +
                    "ON CONFLICT (\"TASK_ID\") DO UPDATE SET " +
                    "  \"TASK_NAME\"=EXCLUDED.\"TASK_NAME\", " +
                    "  \"TASK_TYPE\"=EXCLUDED.\"TASK_TYPE\", " +
                    "  \"DESCRIPTION\"=EXCLUDED.\"DESCRIPTION\", " +
                    "  \"TASK_CONFIG\"=EXCLUDED.\"TASK_CONFIG\", " +
                    "  \"PARAMETERS\"=EXCLUDED.\"PARAMETERS\", " +
                    "  \"PRIORITY\"=EXCLUDED.\"PRIORITY\", " +
                    "  \"TIMEOUT\"=EXCLUDED.\"TIMEOUT\", " +
                    "  \"RETRY_COUNT\"=EXCLUDED.\"RETRY_COUNT\", " +
                    "  \"ASYNC_FLAG\"=EXCLUDED.\"ASYNC_FLAG\", " +
                    "  \"DEPENDENCIES\"=EXCLUDED.\"DEPENDENCIES\", " +
                    "  \"SCHEDULE_ID\"=EXCLUDED.\"SCHEDULE_ID\", " +
                    "  \"NODE_ID\"=EXCLUDED.\"NODE_ID\", " +
                    "  \"EXECUTION_MODE\"=EXCLUDED.\"EXECUTION_MODE\", " +
                    "  \"CREATED_BY\"=EXCLUDED.\"CREATED_BY\", " +
                    "  \"TENANT_ID\"=EXCLUDED.\"TENANT_ID\", " +
                    "  \"TAGS\"=EXCLUDED.\"TAGS\", " +
                    "  \"EXTENSIONS\"=EXCLUDED.\"EXTENSIONS\", " +
                    "  \"UPDATED_TIME\"=NOW(), \"update_time\"=NOW()";
            jdbcTemplate.update(sql,
                    task.getTaskId(), task.getTaskName(), task.getTaskType(),
                    task.getDescription(), task.getTaskConfig(),
                    jstr(task.getParameters()),
                    intOrZero(task.getPriority()),
                    longOrZero(task.getTimeout()),
                    intOrZero(task.getRetryCount()),
                    task.getAsync() ? "1" : "0",
                    jstr(task.getDependencies()),
                    ext(task, "scheduleId"), ext(task, "nodeId"), ext(task, "executionMode"),
                    task.getCreatedBy(), task.getTenantId(),
                    jstr(task.getTags()), jstr(task.getExtensions()),
                    task.getCreatedBy(), task.getCreatedBy());

            // 同步原大写字段（V150 双轨）
            if (task.getCreateTime() != null) {
                jdbcTemplate.update("UPDATE " + T_TASK +
                        " SET \"CREATED_TIME\"=? WHERE \"TASK_ID\"=?",
                        ts(task.getCreateTime()), task.getTaskId());
            }
        } catch (Exception e) {
            logger.error("PG saveTask 失败: taskId={}", task.getTaskId(), e);
            throw new TaskPersistenceException("PG saveTask 失败: " + e.getMessage(), e);
        }
    }

    @Override
    public TaskDescription getTask(String taskId) throws TaskPersistenceException {
        if (taskId == null) {
            throw new TaskPersistenceException("taskId 不能为空");
        }
        try {
            String sql = "SELECT \"TASK_ID\",\"TASK_NAME\",\"TASK_TYPE\",\"DESCRIPTION\",\"TASK_CONFIG\"," +
                    " \"PARAMETERS\",\"PRIORITY\",\"TIMEOUT\",\"RETRY_COUNT\",\"ASYNC_FLAG\"," +
                    " \"DEPENDENCIES\",\"SCHEDULE_ID\",\"NODE_ID\",\"EXECUTION_MODE\"," +
                    " \"CREATED_BY\",\"TENANT_ID\",\"TAGS\",\"EXTENSIONS\",\"CREATED_TIME\" " +
                    "FROM " + T_TASK +
                    " WHERE \"TASK_ID\"=? AND is_deleted=0";
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, taskId);
            if (rows.isEmpty()) {
                return null;
            }
            return mapToTaskDescription(rows.get(0));
        } catch (Exception e) {
            logger.error("PG getTask 失败: taskId={}", taskId, e);
            throw new TaskPersistenceException("PG getTask 失败: " + e.getMessage(), e);
        }
    }

    // ============================= 计划 =============================

    @Override
    public void savePlan(TaskExecutionPlan plan) throws TaskPersistenceException {
        if (plan == null || plan.getTaskId() == null) {
            throw new TaskPersistenceException("TaskExecutionPlan.taskId 不能为空");
        }
        try {
            String sql = "INSERT INTO " + T_PLAN +
                    " (\"TASK_ID\",\"PLAN_CONTENT\",\"EXECUTION_MODE\",\"TARGET_NODE_ID\"," +
                    "  \"CREATED_TIME\",\"UPDATED_TIME\") " +
                    "VALUES (?, ?, ?, ?, NOW(), NOW()) " +
                    "ON CONFLICT (\"TASK_ID\") DO UPDATE SET " +
                    "  \"PLAN_CONTENT\"=EXCLUDED.\"PLAN_CONTENT\", " +
                    "  \"EXECUTION_MODE\"=COALESCE(EXCLUDED.\"EXECUTION_MODE\", \"EXECUTION_MODE\"), " +
                    "  \"TARGET_NODE_ID\"=COALESCE(EXCLUDED.\"TARGET_NODE_ID\", \"TARGET_NODE_ID\"), " +
                    "  \"UPDATED_TIME\"=NOW()";
            jdbcTemplate.update(sql,
                    plan.getTaskId(),
                    jstr(plan),
                    metaStr(plan.getMetadata(), "executionMode"),
                    metaStr(plan.getMetadata(), "targetNodeId"));
        } catch (Exception e) {
            logger.error("PG savePlan 失败: taskId={}", plan.getTaskId(), e);
            throw new TaskPersistenceException("PG savePlan 失败: " + e.getMessage(), e);
        }
    }

    @Override
    public TaskExecutionPlan getPlan(String taskId) throws TaskPersistenceException {
        if (taskId == null) {
            throw new TaskPersistenceException("taskId 不能为空");
        }
        try {
            String sql = "SELECT \"TASK_ID\",\"PLAN_CONTENT\",\"EXECUTION_MODE\",\"TARGET_NODE_ID\" " +
                    "FROM " + T_PLAN +
                    " WHERE \"TASK_ID\"=? AND is_deleted=0";
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, taskId);
            if (rows.isEmpty()) {
                return null;
            }
            Map<String, Object> r = rows.get(0);
            Object content = r.get("PLAN_CONTENT");
            if (content == null) {
                return null;
            }
            TaskExecutionPlan p = objectMapper.readValue(String.valueOf(content), TaskExecutionPlan.class);
            if (p.getTaskId() == null) {
                p.setTaskId(String.valueOf(r.get("TASK_ID")));
            }
            Map<String, Object> md = new HashMap<>();
            if (r.get("EXECUTION_MODE") != null) {
                md.put("executionMode", String.valueOf(r.get("EXECUTION_MODE")));
            }
            if (r.get("TARGET_NODE_ID") != null) {
                md.put("targetNodeId", String.valueOf(r.get("TARGET_NODE_ID")));
            }
            if (!md.isEmpty()) {
                p.setMetadata(md);
            }
            return p;
        } catch (Exception e) {
            logger.error("PG getPlan 失败: taskId={}", taskId, e);
            throw new TaskPersistenceException("PG getPlan 失败: " + e.getMessage(), e);
        }
    }

    // -------- PMO-72 W4 新增：调度四要素（cron / next / last / last_status） --------

    /**
     * T4 调度器：注册定时计划后调用，将 4 要素与题目信息写入 plan 行（必须存在同 ID 的 task）。
     *
     * @param taskId         plan 行主键（对定时调度器= scheduleId 虚拟主键；对即时任务= task_id）
     * @param taskName       人类可读名（来自 TaskDescription）
     * @param taskType       业务类型
     * @param cronExpression 规范化 cron（6 位 Spring）
     * @param nextRunAt      下次触发时间
     * @return true=成功 false=失败
     */
    public boolean saveJdbcPlanState(String taskId, String taskName, String taskType,
            String cronExpression, Date nextRunAt, String executionMode, String targetNodeId) {
        if (taskId == null || cronExpression == null) {
            return false;
        }
        try {
            int r = jdbcTemplate.update("UPDATE " + T_PLAN +
                    " SET \"CRON_EXPRESSION\"=?, \"NEXT_RUN_AT\"=?, \"TASK_NAME\"=?, \"TASK_TYPE\"=?, " +
                    "     \"EXECUTION_MODE\"=?, \"TARGET_NODE_ID\"=?, \"UPDATED_TIME\"=NOW() " +
                    "WHERE \"TASK_ID\"=?",
                    cronExpression, ts(nextRunAt), taskName, taskType,
                    executionMode, targetNodeId, taskId);
            return r >= 0;
        } catch (Exception e) {
            logger.warn("PG saveJdbcPlanState 失败: taskId={}", taskId, e);
            return false;
        }
    }

    /**
     * T4 调度循环：每轮触发后调用，记录 last_run_at + last_status。
     */
    public boolean markLastRun(String taskId, Date lastRunAt, String lastStatus) {
        if (taskId == null) {
            return false;
        }
        try {
            int r = jdbcTemplate.update("UPDATE " + T_PLAN +
                    " SET \"LAST_RUN_AT\"=?, \"LAST_STATUS\"=?, \"UPDATED_TIME\"=NOW() " +
                    "WHERE \"TASK_ID\"=?",
                    ts(lastRunAt), lastStatus, taskId);
            return r >= 0;
        } catch (Exception e) {
            logger.warn("PG markLastRun 失败: taskId={}", taskId, e);
            return false;
        }
    }

    /**
     * T2/T3 启动恢复：按 taskType 过滤拉取所有计划（null = 全部）。
     * 失败返回空列表（双态）。
     */
    public List<JdbcPlanState> listPlansByType(String taskType) {
        try {
            String sql;
            Object[] params;
            if (taskType != null && !taskType.isEmpty()) {
                sql = "SELECT \"TASK_ID\",\"PLAN_CONTENT\",\"TASK_NAME\",\"TASK_TYPE\"," +
                        "  \"CRON_EXPRESSION\",\"NEXT_RUN_AT\",\"LAST_RUN_AT\",\"LAST_STATUS\"," +
                        "  \"EXECUTION_MODE\",\"TARGET_NODE_ID\" " +
                        "FROM " + T_PLAN +
                        " WHERE \"TASK_TYPE\"= ? AND is_deleted=0 " +
                        "ORDER BY \"UPDATED_TIME\" DESC NULLS LAST LIMIT 1000";
                params = new Object[]{taskType};
            } else {
                sql = "SELECT \"TASK_ID\",\"PLAN_CONTENT\",\"TASK_NAME\",\"TASK_TYPE\"," +
                        "  \"CRON_EXPRESSION\",\"NEXT_RUN_AT\",\"LAST_RUN_AT\",\"LAST_STATUS\"," +
                        "  \"EXECUTION_MODE\",\"TARGET_NODE_ID\" " +
                        "FROM " + T_PLAN +
                        " WHERE is_deleted=0 " +
                        "ORDER BY \"UPDATED_TIME\" DESC NULLS LAST LIMIT 1000";
                params = new Object[]{ };
            }
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, params);
            List<JdbcPlanState> out = new ArrayList<>(rows.size());
            for (Map<String, Object> r : rows) {
                JdbcPlanState p = new JdbcPlanState();
                p.setTaskId(str(r.get("TASK_ID")));
                p.setTaskName(str(r.get("TASK_NAME")));
                p.setTaskType(str(r.get("TASK_TYPE")));
                p.setCronExpression(str(r.get("CRON_EXPRESSION")));
                p.setNextRunAt(date(r.get("NEXT_RUN_AT")));
                p.setLastRunAt(date(r.get("LAST_RUN_AT")));
                p.setLastStatus(str(r.get("LAST_STATUS")));
                p.setTaskBodyJson(str(r.get("PLAN_CONTENT")));
                p.setExecutionMode(str(r.get("EXECUTION_MODE")));
                p.setTargetNodeId(str(r.get("TARGET_NODE_ID")));
                out.add(p);
            }
            return out;
        } catch (Exception e) {
            logger.warn("PG listPlansByType 失败: taskType={}", taskType, e);
            return new ArrayList<>();
        }
    }

    /**
     * PMO-72 W4: 逻辑删除计划行（is_deleted=1）。
     * 非接口契约方法；对应 DELETE /api/v1/task/{taskId}/archive 等归档语义。
     */
    public void deletePlan(String taskId) throws TaskPersistenceException {
        if (taskId == null) {
            throw new TaskPersistenceException("taskId 不能为空");
        }
        try {
            // R9：逻辑删除
            jdbcTemplate.update("UPDATE " + T_PLAN +
                    " SET \"is_deleted\"=1, \"UPDATED_TIME\"=NOW() WHERE \"TASK_ID\"=?",
                    taskId);
        } catch (Exception e) {
            logger.error("PG deletePlan 失败: taskId={}", taskId, e);
            throw new TaskPersistenceException("PG deletePlan 失败: " + e.getMessage(), e);
        }
    }

    // ============================= 状态 =============================

    @Override
    public void saveStatus(TaskStatus status) throws TaskPersistenceException {
        if (status == null || status.getTaskId() == null) {
            throw new TaskPersistenceException("TaskStatus.taskId 不能为空");
        }
        try {
            String sql = "INSERT INTO " + T_STATUS +
                    " (\"TASK_ID\",\"STATUS\",\"STATUS_MESSAGE\",\"PROGRESS\",\"CURRENT_STEP_ID\"," +
                    "  \"START_TIME\",\"END_TIME\",\"ESTIMATED_REMAINING_TIME\"," +
                    "  \"PROCESSED_COUNT\",\"TOTAL_COUNT\",\"ERROR_MESSAGE\",\"ERROR_STACK\"," +
                    "  \"RESULT\",\"METRICS\",\"EXECUTION_NODE_ID\",\"UPDATED_TIME\") " +
                    "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,NOW()) " +
                    "ON CONFLICT (\"TASK_ID\") DO UPDATE SET " +
                    "  \"STATUS\"=EXCLUDED.\"STATUS\", " +
                    "  \"STATUS_MESSAGE\"=EXCLUDED.\"STATUS_MESSAGE\", " +
                    "  \"PROGRESS\"=EXCLUDED.\"PROGRESS\", " +
                    "  \"CURRENT_STEP_ID\"=EXCLUDED.\"CURRENT_STEP_ID\", " +
                    "  \"START_TIME\"=COALESCE(EXCLUDED.\"START_TIME\", \"START_TIME\"), " +
                    "  \"END_TIME\"=COALESCE(EXCLUDED.\"END_TIME\", \"END_TIME\"), " +
                    "  \"ESTIMATED_REMAINING_TIME\"=EXCLUDED.\"ESTIMATED_REMAINING_TIME\", " +
                    "  \"PROCESSED_COUNT\"=EXCLUDED.\"PROCESSED_COUNT\", " +
                    "  \"TOTAL_COUNT\"=EXCLUDED.\"TOTAL_COUNT\", " +
                    "  \"ERROR_MESSAGE\"=EXCLUDED.\"ERROR_MESSAGE\", " +
                    "  \"ERROR_STACK\"=EXCLUDED.\"ERROR_STACK\", " +
                    "  \"RESULT\"=EXCLUDED.\"RESULT\", " +
                    "  \"METRICS\"=EXCLUDED.\"METRICS\", " +
                    "  \"EXECUTION_NODE_ID\"=EXCLUDED.\"EXECUTION_NODE_ID\", " +
                    "  \"UPDATED_TIME\"=NOW()";
            jdbcTemplate.update(sql,
                    status.getTaskId(),
                    status.getStatus() != null ? status.getStatus().name() : "PENDING",
                    status.getStatusMessage(),
                    status.getProgress(),
                    status.getCurrentStepId(),
                    ts(status.getStartTime()), ts(status.getEndTime()),
                    status.getEstimatedRemainingTime(),
                    status.getProcessedRecords(), status.getTotalRecords(),
                    status.getErrorMessage(), status.getErrorStack(),
                    status.getResult(), jstr(status.getMetrics()),
                    metricNode(status));
        } catch (Exception e) {
            logger.error("PG saveStatus 失败: taskId={}", status.getTaskId(), e);
            throw new TaskPersistenceException("PG saveStatus 失败: " + e.getMessage(), e);
        }
    }

    @Override
    public TaskStatus getStatus(String taskId) throws TaskPersistenceException {
        if (taskId == null) {
            throw new TaskPersistenceException("taskId 不能为空");
        }
        try {
            String sql = "SELECT \"TASK_ID\",\"STATUS\",\"STATUS_MESSAGE\",\"PROGRESS\",\"CURRENT_STEP_ID\"," +
                    " \"START_TIME\",\"END_TIME\",\"ESTIMATED_REMAINING_TIME\"," +
                    " \"PROCESSED_COUNT\",\"TOTAL_COUNT\",\"ERROR_MESSAGE\",\"ERROR_STACK\"," +
                    " \"RESULT\",\"METRICS\",\"EXECUTION_NODE_ID\",\"UPDATED_TIME\" " +
                    "FROM " + T_STATUS +
                    " WHERE \"TASK_ID\"=?";
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, taskId);
            if (rows.isEmpty()) {
                return null;
            }
            return mapToStatus(rows.get(0));
        } catch (Exception e) {
            logger.error("PG getStatus 失败: taskId={}", taskId, e);
            throw new TaskPersistenceException("PG getStatus 失败: " + e.getMessage(), e);
        }
    }

    // ============================= 查询 =============================

    @Override
    public List<TaskDescription> queryTasks(Map<String, Object> condition, int offset, int limit)
            throws TaskPersistenceException {
        try {
            StringBuilder where = new StringBuilder(" WHERE is_deleted=0");
            List<Object> params = new ArrayList<>();
            if (condition != null) {
                if (condition.get("taskType") != null) {
                    where.append(" AND \"TASK_TYPE\"=?");
                    params.add(condition.get("taskType"));
                }
                if (condition.get("taskId") != null) {
                    where.append(" AND \"TASK_ID\"=?");
                    params.add(condition.get("taskId"));
                }
                if (condition.get("tenantId") != null) {
                    where.append(" AND \"TENANT_ID\"=?");
                    params.add(condition.get("tenantId"));
                }
                if (condition.get("createdBy") != null) {
                    where.append(" AND \"CREATED_BY\"=?");
                    params.add(condition.get("createdBy"));
                }
            }
            int safeOffset = Math.max(0, offset);
            int safeLimit = limit <= 0 ? 1000 : limit;
            String sql = "SELECT \"TASK_ID\",\"TASK_NAME\",\"TASK_TYPE\",\"DESCRIPTION\",\"TASK_CONFIG\"," +
                    " \"PARAMETERS\",\"PRIORITY\",\"TIMEOUT\",\"RETRY_COUNT\",\"ASYNC_FLAG\"," +
                    " \"DEPENDENCIES\",\"SCHEDULE_ID\",\"NODE_ID\",\"EXECUTION_MODE\"," +
                    " \"CREATED_BY\",\"TENANT_ID\",\"TAGS\",\"EXTENSIONS\",\"CREATED_TIME\" " +
                    "FROM " + T_TASK + where +
                    " ORDER BY \"CREATED_TIME\" DESC NULLS LAST LIMIT ? OFFSET ?";
            params.add(safeLimit);
            params.add(safeOffset);
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, params.toArray());
            List<TaskDescription> out = new ArrayList<>(rows.size());
            for (Map<String, Object> r : rows) {
                out.add(mapToTaskDescription(r));
            }
            return out;
        } catch (Exception e) {
            logger.error("PG queryTasks 失败", e);
            throw new TaskPersistenceException("PG queryTasks 失败: " + e.getMessage(), e);
        }
    }

    @Override
    public List<TaskStatus> queryTaskStatuses(Map<String, Object> condition, int offset, int limit)
            throws TaskPersistenceException {
        try {
            StringBuilder where = new StringBuilder(" WHERE 1=1");
            List<Object> params = new ArrayList<>();
            if (condition != null) {
                if (condition.get("taskId") != null) {
                    where.append(" AND \"TASK_ID\"=?");
                    params.add(condition.get("taskId"));
                }
                if (condition.get("status") != null) {
                    where.append(" AND \"STATUS\"=?");
                    params.add(condition.get("status"));
                }
            }
            int safeOffset = Math.max(0, offset);
            int safeLimit = limit <= 0 ? 1000 : limit;
            String sql = "SELECT \"TASK_ID\",\"STATUS\",\"STATUS_MESSAGE\",\"PROGRESS\",\"CURRENT_STEP_ID\"," +
                    " \"START_TIME\",\"END_TIME\",\"ESTIMATED_REMAINING_TIME\"," +
                    " \"PROCESSED_COUNT\",\"TOTAL_COUNT\",\"ERROR_MESSAGE\",\"ERROR_STACK\"," +
                    " \"RESULT\",\"METRICS\",\"EXECUTION_NODE_ID\",\"UPDATED_TIME\" " +
                    "FROM " + T_STATUS + where +
                    " ORDER BY \"UPDATED_TIME\" DESC NULLS LAST LIMIT ? OFFSET ?";
            params.add(safeLimit);
            params.add(safeOffset);
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, params.toArray());
            List<TaskStatus> out = new ArrayList<>(rows.size());
            for (Map<String, Object> r : rows) {
                out.add(mapToStatus(r));
            }
            return out;
        } catch (Exception e) {
            logger.error("PG queryTaskStatuses 失败", e);
            throw new TaskPersistenceException("PG queryTaskStatuses 失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void deleteTask(String taskId) throws TaskPersistenceException {
        if (taskId == null) {
            throw new TaskPersistenceException("taskId 不能为空");
        }
        try {
            // R9: 逻辑删除（is_deleted=1），不物理删
            jdbcTemplate.update("UPDATE " + T_STATUS + " SET \"is_deleted\"=1 WHERE \"TASK_ID\"=?", taskId);
            jdbcTemplate.update("UPDATE " + T_PLAN + " SET \"is_deleted\"=1 WHERE \"TASK_ID\"=?", taskId);
            jdbcTemplate.update("UPDATE " + T_TASK +
                    " SET \"is_deleted\"=1, \"UPDATED_TIME\"=NOW() WHERE \"TASK_ID\"=?", taskId);
        } catch (Exception e) {
            logger.error("PG deleteTask 失败: taskId={}", taskId, e);
            throw new TaskPersistenceException("PG deleteTask 失败: " + e.getMessage(), e);
        }
    }

    // ============================= log / execution 辅助 =============================

    public void logTask(String executionId, String taskId, String level, String message) {
        try {
            jdbcTemplate.update("INSERT INTO " + T_LOG +
                    " (\"EXECUTION_ID\",\"TASK_ID\",\"LOG_LEVEL\",\"MESSAGE\",\"CREATED_TIME\") " +
                    "VALUES (?,?,?, ?,NOW())",
                    executionId, taskId,
                    level == null ? null : level.toUpperCase(), message);
        } catch (Exception e) {
            logger.warn("PG logTask 失败: taskId={}", taskId, e);
        }
    }

    public void recordExecution(String executionId, String taskId, String status,
            Date startTime, Date endTime, String result, String errorMessage,
            String errorStack, String executionNodeId) {
        try {
            Long duration = null;
            if (startTime != null && endTime != null) {
                duration = endTime.getTime() - startTime.getTime();
            }
            jdbcTemplate.update("INSERT INTO " + T_EXEC +
                    " (\"EXECUTION_ID\",\"TASK_ID\",\"STATUS\",\"START_TIME\",\"END_TIME\"," +
                    "  \"DURATION\",\"RESULT\",\"ERROR_MESSAGE\",\"ERROR_STACK\"," +
                    "  \"EXECUTION_NODE_ID\",\"CREATED_TIME\") " +
                    "VALUES (?,?,?,?,?,?,?,?,?,NOW()) " +
                    "ON CONFLICT (\"EXECUTION_ID\") DO NOTHING",
                    executionId, taskId, status, ts(startTime), ts(endTime),
                    duration, result, errorMessage, errorStack, executionNodeId);
        } catch (Exception e) {
            logger.warn("PG recordExecution 失败: taskId={}", taskId, e);
        }
    }

    // ============================= 工具 =============================

    private static Timestamp ts(Date d) {
        if (d == null) {
            return null;
        }
        return new Timestamp(d.getTime());
    }

    private static String jstr(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof String s) {
            return s;
        }
        try {
            return objectMapper.writeValueAsString(o);
        } catch (Exception e) {
            return String.valueOf(o);
        }
    }

    private static int intOrZero(Integer i) {
        return i == null ? 0 : i;
    }

    private static long longOrZero(Long l) {
        return l == null ? 0L : l;
    }

    private static String ext(TaskDescription t, String key) {
        if (t == null || t.getExtensions() == null) {
            return null;
        }
        Object v = t.getExtensions().get(key);
        return v == null ? null : String.valueOf(v);
    }

    private static String metaStr(Map<String, Object> md, String key) {
        if (md == null) {
            return null;
        }
        Object v = md.get(key);
        return v == null ? null : String.valueOf(v);
    }

    private static String metricNode(TaskStatus s) {
        if (s == null || s.getMetrics() == null) {
            return null;
        }
        Object v = s.getMetrics().get("executionNodeId");
        return v == null ? null : String.valueOf(v);
    }

    private TaskDescription mapToTaskDescription(Map<String, Object> r) {
        TaskDescription t = new TaskDescription();
        t.setTaskId(str(r.get("TASK_ID")));
        t.setTaskName(str(r.get("TASK_NAME")));
        t.setTaskType(str(r.get("TASK_TYPE")));
        t.setDescription(str(r.get("DESCRIPTION")));
        t.setTaskConfig(str(r.get("TASK_CONFIG")));
        try {
            Object pr = jobj(str(r.get("PARAMETERS")));
            if (pr instanceof Map<?, ?> pm) {
                @SuppressWarnings("unchecked")
                Map<String, Object> params = (Map<String, Object>) pm;
                t.setParameters(params);
            }
            Object deps = jobj(str(r.get("DEPENDENCIES")));
            if (deps != null) {
                t.setDependencies(objectMapper.convertValue(deps, LIST_TYPE));
            }
            Object tags = jobj(str(r.get("TAGS")));
            if (tags != null) {
                t.setTags(objectMapper.convertValue(tags, LIST_TYPE));
            }
            Map<String, Object> ext = new HashMap<>();
            if (r.get("SCHEDULE_ID") != null) {
                ext.put("scheduleId", str(r.get("SCHEDULE_ID")));
            }
            if (r.get("NODE_ID") != null) {
                ext.put("nodeId", str(r.get("NODE_ID")));
            }
            if (r.get("EXECUTION_MODE") != null) {
                ext.put("executionMode", str(r.get("EXECUTION_MODE")));
            }
            if (jobj(str(r.get("EXTENSIONS"))) instanceof Map<?, ?> em) {
                for (Map.Entry<?, ?> e : em.entrySet()) {
                    ext.put(String.valueOf(e.getKey()), e.getValue());
                }
            }
            if (!ext.isEmpty()) {
                t.setExtensions(ext);
            }
        } catch (Exception ignored) {
            // 反序列化失败不影响主字段
        }
        if (r.get("PRIORITY") instanceof Number n) {
            t.setPriority(n.intValue());
        }
        if (r.get("TIMEOUT") instanceof Number n) {
            t.setTimeout(n.longValue());
        }
        if (r.get("RETRY_COUNT") instanceof Number n) {
            t.setRetryCount(n.intValue());
        }
        t.setAsync("1".equals(str(r.get("ASYNC_FLAG"))));
        t.setCreatedBy(str(r.get("CREATED_BY")));
        t.setTenantId(str(r.get("TENANT_ID")));
        if (r.get("CREATED_TIME") instanceof Timestamp ts) {
            t.setCreateTime(new Date(ts.getTime()));
        }
        return t;
    }

    private TaskStatus mapToStatus(Map<String, Object> r) {
        TaskStatus s = new TaskStatus();
        s.setTaskId(str(r.get("TASK_ID")));
        String st = str(r.get("STATUS"));
        if (st != null && !st.isEmpty()) {
            try {
                s.setStatus(TaskStatus.Status.valueOf(st));
            } catch (IllegalArgumentException e) {
                s.setStatus(TaskStatus.Status.PENDING);
            }
        }
        s.setStatusMessage(str(r.get("STATUS_MESSAGE")));
        if (r.get("PROGRESS") instanceof Number n) {
            s.setProgress(n.intValue());
        }
        s.setCurrentStepId(str(r.get("CURRENT_STEP_ID")));
        s.setStartTime(date(r.get("START_TIME")));
        s.setEndTime(date(r.get("END_TIME")));
        if (r.get("ESTIMATED_REMAINING_TIME") instanceof Number n) {
            s.setEstimatedRemainingTime(n.longValue());
        }
        if (r.get("PROCESSED_COUNT") instanceof Number n) {
            s.setProcessedRecords(n.longValue());
        }
        if (r.get("TOTAL_COUNT") instanceof Number n) {
            s.setTotalRecords(n.longValue());
        }
        s.setErrorMessage(str(r.get("ERROR_MESSAGE")));
        s.setErrorStack(str(r.get("ERROR_STACK")));
        s.setResult(str(r.get("RESULT")));
        Object m = jobj(str(r.get("METRICS")));
        if (m instanceof Map<?, ?> mm) {
            Map<String, Object> mm2 = new HashMap<>();
            for (Map.Entry<?, ?> e : mm.entrySet()) {
                mm2.put(String.valueOf(e.getKey()), e.getValue());
            }
            s.setMetrics(mm2);
        }
        s.setUpdateTime(date(r.get("UPDATED_TIME")));
        return s;
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static Object jobj(String s) {
        if (s == null || s.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.readValue(s, JSON_ANY);
        } catch (Exception e) {
            return s;
        }
    }

    private static Date date(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Timestamp ts) {
            return new Date(ts.getTime());
        }
        if (o instanceof Date d) {
            return d;
        }
        return new Date(System.currentTimeMillis());
    }
}
