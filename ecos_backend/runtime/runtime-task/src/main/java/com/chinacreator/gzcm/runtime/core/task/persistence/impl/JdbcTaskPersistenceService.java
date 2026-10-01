package com.chinacreator.gzcm.runtime.core.task.persistence.impl;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import com.chinacreator.gzcm.runtime.core.task.model.TaskDescription;
import com.chinacreator.gzcm.runtime.core.task.model.TaskExecutionPlan;
import com.chinacreator.gzcm.runtime.core.task.model.TaskStatus;
import com.chinacreator.gzcm.runtime.core.task.persistence.ITaskPersistenceService;
import com.chinacreator.gzcm.runtime.core.task.persistence.TaskPersistenceException;
import com.chinacreator.gzcm.runtime.core.task.persistence.dao.TaskPersistenceDao;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * PMO-72 W4 — R2 B4c-runtime（架构铁律 §2.5#1 / IR06）收口后的任务持久化服务。
 *
 * <p>R2 收口：raw-SQL 全部下沉到 {@link TaskPersistenceDao}（sys_man 5 张表
 * td_runtime_task / _plan / _status / _log / _execution），本类退化为编排骨架
 * + 失败容忍策略（saveX 抛 / watch 吞掉）+ DTO↔row 映射门面，生产类不再持有
 * JdbcTemplate。</p>
 *
 * @author PMO Worker (W4)
 */
@Service
@Primary
@ConditionalOnProperty(name = "runtime.task.persistence.enabled", havingValue = "true", matchIfMissing = false)
public class JdbcTaskPersistenceService implements ITaskPersistenceService {

    private static final Logger logger = LoggerFactory.getLogger(JdbcTaskPersistenceService.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final TypeReference<List<String>> LIST_TYPE = new TypeReference<List<String>>() { };
    private static final TypeReference<Object> JSON_ANY = new TypeReference<Object>() { };

    private final TaskPersistenceDao dao;

    public JdbcTaskPersistenceService(TaskPersistenceDao dao) {
        this.dao = dao;
    }

    // ============================= 任务 =============================

    @Override
    public void saveTask(TaskDescription task) throws TaskPersistenceException {
        if (task == null || task.getTaskId() == null) {
            throw new TaskPersistenceException("TaskDescription.taskId 不能为空");
        }
        try {
            dao.saveTask(task);
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
            List<Map<String, Object>> rows = dao.queryTask(taskId);
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
            dao.savePlan(plan);
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
            List<Map<String, Object>> rows = dao.queryPlan(taskId);
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
            int r = dao.updatePlanState(taskId, taskName, taskType, cronExpression,
                    nextRunAt, executionMode, targetNodeId);
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
            int r = dao.markLastRun(taskId, lastRunAt, lastStatus);
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
            List<Map<String, Object>> rows = dao.queryPlansByType(taskType);
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
            dao.softDeletePlan(taskId);
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
            dao.saveStatus(status);
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
            List<Map<String, Object>> rows = dao.queryStatus(taskId);
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
            List<Map<String, Object>> rows = dao.queryTasks(condition, offset, limit);
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
            List<Map<String, Object>> rows = dao.queryTaskStatuses(condition, offset, limit);
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
            dao.softDeleteTask(taskId);
        } catch (Exception e) {
            logger.error("PG deleteTask 失败: taskId={}", taskId, e);
            throw new TaskPersistenceException("PG deleteTask 失败: " + e.getMessage(), e);
        }
    }

    // ============================= log / execution 辅助 =============================

    public void logTask(String executionId, String taskId, String level, String message) {
        try {
            dao.logTask(executionId, taskId, level, message);
        } catch (Exception e) {
            logger.warn("PG logTask 失败: taskId={}", taskId, e);
        }
    }

    public void recordExecution(String executionId, String taskId, String status,
            Date startTime, Date endTime, String result, String errorMessage,
            String errorStack, String executionNodeId) {
        try {
            dao.recordExecution(executionId, taskId, status, startTime, endTime,
                    result, errorMessage, errorStack, executionNodeId);
        } catch (Exception e) {
            logger.warn("PG recordExecution 失败: taskId={}", taskId, e);
        }
    }

    // ============================= DTO 映射工具 =============================

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
