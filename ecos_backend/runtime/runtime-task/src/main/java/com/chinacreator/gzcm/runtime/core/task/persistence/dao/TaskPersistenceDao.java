package com.chinacreator.gzcm.runtime.core.task.persistence.dao;

import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import com.chinacreator.gzcm.runtime.core.task.model.TaskDescription;
import com.chinacreator.gzcm.runtime.core.task.model.TaskExecutionPlan;
import com.chinacreator.gzcm.runtime.core.task.model.TaskStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * PMO-72 W4 — sys_man 任务持久化 5 张表 (td_runtime_task / _plan / _status / _log / _execution)
 * raw-SQL 收口点（R2 B4c-runtime · 架构铁律 §2.5#1 / IR06）。
 *
 * <p>全部 5 表 SQL 自 {@code JdbcTaskPersistenceService} 逐字节下沉，SQL 文本、参数顺序、
 * 双引号列名（pg_attribute quote_ident 验证）保持不变；service 退化为编排骨架 + DTO 映射
 * 门面，SQL 只经本 DAO（{@link JdbcTemplateDaoSupport} 的 {@code jdbcTemplate} 通道）执行。
 * 本 DAO 只出原始结果行 {@code List<Map<String,Object>>} / 影响行数，DTO↔row 映射留在 service。</p>
 *
 * <p>失败容忍语义（saveX 抛、logTask/recordExecution/watch 吞掉）仍由 service 决定，
 * 本 DAO 只忠实执行并抛出底层异常。</p>
 *
 * <p>仅在有 {@link JdbcTemplate} 且 {@code runtime.task.persistence.enabled=true} 的容器内生效。</p>
 *
 * <p>红线 EN01/IR01：全部 slot 参数化。</p>
 */
@Repository
@ConditionalOnBean(JdbcTemplate.class)
public class TaskPersistenceDao extends JdbcTemplateDaoSupport {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static final String T_TASK = "td_runtime_task";
    private static final String T_PLAN = "td_runtime_task_plan";
    private static final String T_STATUS = "td_runtime_task_status";
    private static final String T_EXEC = "td_runtime_task_execution";
    private static final String T_LOG = "td_runtime_task_log";

    // ============================= 任务 =============================

    public void saveTask(TaskDescription task) {
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
    }

    public List<Map<String, Object>> queryTask(String taskId) {
        String sql = "SELECT \"TASK_ID\",\"TASK_NAME\",\"TASK_TYPE\",\"DESCRIPTION\",\"TASK_CONFIG\"," +
                " \"PARAMETERS\",\"PRIORITY\",\"TIMEOUT\",\"RETRY_COUNT\",\"ASYNC_FLAG\"," +
                " \"DEPENDENCIES\",\"SCHEDULE_ID\",\"NODE_ID\",\"EXECUTION_MODE\"," +
                " \"CREATED_BY\",\"TENANT_ID\",\"TAGS\",\"EXTENSIONS\",\"CREATED_TIME\" " +
                "FROM " + T_TASK +
                " WHERE \"TASK_ID\"=? AND is_deleted=0";
        return jdbcTemplate.queryForList(sql, taskId);
    }

    // ============================= 计划 =============================

    public void savePlan(TaskExecutionPlan plan) {
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
    }

    public List<Map<String, Object>> queryPlan(String taskId) {
        String sql = "SELECT \"TASK_ID\",\"PLAN_CONTENT\",\"EXECUTION_MODE\",\"TARGET_NODE_ID\" " +
                "FROM " + T_PLAN +
                " WHERE \"TASK_ID\"=? AND is_deleted=0";
        return jdbcTemplate.queryForList(sql, taskId);
    }

    // -------- PMO-72 W4 新增：调度四要素、last_run 等 plan 行更新 ----

    /** T4 调度器：注册定时计划后，将 4 要素与题目信息写入 plan 行。返回影响行数。 */
    public int updatePlanState(String taskId, String taskName, String taskType,
            String cronExpression, Date nextRunAt, String executionMode, String targetNodeId) {
        return jdbcTemplate.update("UPDATE " + T_PLAN +
                " SET \"CRON_EXPRESSION\"=?, \"NEXT_RUN_AT\"=?, \"TASK_NAME\"=?, \"TASK_TYPE\"=?, " +
                "     \"EXECUTION_MODE\"=?, \"TARGET_NODE_ID\"=?, \"UPDATED_TIME\"=NOW() " +
                "WHERE \"TASK_ID\"=?",
                cronExpression, ts(nextRunAt), taskName, taskType,
                executionMode, targetNodeId, taskId);
    }

    /** T4 调度循环：每轮触发后记录 last_run_at + last_status。返回影响行数。 */
    public int markLastRun(String taskId, Date lastRunAt, String lastStatus) {
        return jdbcTemplate.update("UPDATE " + T_PLAN +
                " SET \"LAST_RUN_AT\"=?, \"LAST_STATUS\"=?, \"UPDATED_TIME\"=NOW() " +
                "WHERE \"TASK_ID\"=?",
                ts(lastRunAt), lastStatus, taskId);
    }

    /** T2/T3 启动恢复：按 taskType 过滤拉取所有计划行（null = 全部），返回原始行。 */
    public List<Map<String, Object>> queryPlansByType(String taskType) {
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
        return jdbcTemplate.queryForList(sql, params);
    }

    /** PMO-72 W4: 逻辑删除计划行（is_deleted=1）。 */
    public void softDeletePlan(String taskId) {
        // R9：逻辑删除
        jdbcTemplate.update("UPDATE " + T_PLAN +
                " SET \"is_deleted\"=1, \"UPDATED_TIME\"=NOW() WHERE \"TASK_ID\"=?",
                taskId);
    }

    // ============================= 状态 =============================

    public void saveStatus(TaskStatus status) {
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
    }

    public List<Map<String, Object>> queryStatus(String taskId) {
        String sql = "SELECT \"TASK_ID\",\"STATUS\",\"STATUS_MESSAGE\",\"PROGRESS\",\"CURRENT_STEP_ID\"," +
                " \"START_TIME\",\"END_TIME\",\"ESTIMATED_REMAINING_TIME\"," +
                " \"PROCESSED_COUNT\",\"TOTAL_COUNT\",\"ERROR_MESSAGE\",\"ERROR_STACK\"," +
                " \"RESULT\",\"METRICS\",\"EXECUTION_NODE_ID\",\"UPDATED_TIME\" " +
                "FROM " + T_STATUS +
                " WHERE \"TASK_ID\"=?";
        return jdbcTemplate.queryForList(sql, taskId);
    }

    // ============================= 查询 =============================

    public List<Map<String, Object>> queryTasks(Map<String, Object> condition, int offset, int limit) {
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
        return jdbcTemplate.queryForList(sql, params.toArray());
    }

    public List<Map<String, Object>> queryTaskStatuses(Map<String, Object> condition, int offset, int limit) {
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
        return jdbcTemplate.queryForList(sql, params.toArray());
    }

    /** R9: 逻辑删除（is_deleted=1），不物理删 —— status / plan / task 三行。 */
    public void softDeleteTask(String taskId) {
        jdbcTemplate.update("UPDATE " + T_STATUS + " SET \"is_deleted\"=1 WHERE \"TASK_ID\"=?", taskId);
        jdbcTemplate.update("UPDATE " + T_PLAN + " SET \"is_deleted\"=1 WHERE \"TASK_ID\"=?", taskId);
        jdbcTemplate.update("UPDATE " + T_TASK +
                " SET \"is_deleted\"=1, \"UPDATED_TIME\"=NOW() WHERE \"TASK_ID\"=?", taskId);
    }

    // ============================= log / execution =============================

    public void logTask(String executionId, String taskId, String level, String message) {
        jdbcTemplate.update("INSERT INTO " + T_LOG +
                " (\"EXECUTION_ID\",\"TASK_ID\",\"LOG_LEVEL\",\"MESSAGE\",\"CREATED_TIME\") " +
                "VALUES (?,?,?, ?,NOW())",
                executionId, taskId,
                level == null ? null : level.toUpperCase(), message);
    }

    public void recordExecution(String executionId, String taskId, String status,
            Date startTime, Date endTime, String result, String errorMessage,
            String errorStack, String executionNodeId) {
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
    }

    // ============================= SQL-arg 工具（自 service 逐字下沉） =============================

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
}
