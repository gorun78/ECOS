package com.chinacreator.gzcm.gateway.telemetry;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.chinacreator.gzcm.runtime.core.task.callback.ITaskStatusCallback;
import com.chinacreator.gzcm.runtime.core.task.executor.ITaskExecutor;
import com.chinacreator.gzcm.runtime.core.task.model.TaskDescription;
import com.chinacreator.gzcm.runtime.core.task.model.TaskExecutionPlan;
import com.chinacreator.gzcm.runtime.core.task.model.TaskStatus;
import com.chinacreator.gzcm.runtime.core.task.parser.ITaskParser;
import com.chinacreator.gzcm.runtime.core.task.scheduling.TaskSchedulerService;
import com.chinacreator.gzcm.runtime.core.task.service.ITaskManagementService;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A7: 租户用量定时聚合器。
 *
 * <p>每分钟从 ecos_spans 和 ecos_token_usage 表中聚合租户 API 调用量，
 * 写入 ecos_tenant_usage 表。该聚合任务统一委托 runtime-task 调度，
 * 网关层不再保留本地 @Scheduled 定时器。</p>
 */
@Component
public class UsageCollector {

    private static final Logger log = LoggerFactory.getLogger(UsageCollector.class);

    /** runtime-task 调度任务类型。 */
    private static final String TASK_TYPE = "USAGE_AGGREGATION";

    /** 聚合周期：60 秒。 */
    private static final long PERIOD_MILLIS = 60_000L;

    private final JdbcTemplate jdbc;
    private final ITaskManagementService taskManagementService;
    private final TaskSchedulerService taskSchedulerService;

    public UsageCollector(JdbcTemplate jdbc,
                          ITaskManagementService taskManagementService,
                          TaskSchedulerService taskSchedulerService) {
        this.jdbc = jdbc;
        this.taskManagementService = taskManagementService;
        this.taskSchedulerService = taskSchedulerService;
    }

    /**
     * 将租户用量聚合注册为 runtime-task 周期任务，替代网关本地定时器。
     */
    @PostConstruct
    public void registerWithRuntimeTask() {
        taskManagementService.registerParser(TASK_TYPE, new UsageTaskParser());
        taskManagementService.registerExecutor(TASK_TYPE, new UsageTaskExecutor());

        TaskDescription description = new TaskDescription();
        description.setTaskName("usage-aggregation");
        description.setTaskType(TASK_TYPE);
        description.setDescription("A7 租户 API 与 token 用量聚合");
        description.setParameters(new HashMap<>());

        String scheduleId = taskSchedulerService.schedulePeriodicTask(description, 0L, PERIOD_MILLIS);
        log.info("A7 租户用量聚合任务已注册到 runtime-task: taskType={}, scheduleId={}", TASK_TYPE, scheduleId);
    }

    /**
     * 聚合过去 1 分钟内的租户 API 调用量与 token 用量。
     *
     * @return 受影响记录总数，用于任务执行结果
     */
    int aggregateApiUsage() {
        String today = LocalDate.now().toString();

        // Wave-7 T-29 (R5) 修复:
        //   旧 SQL 用 s.tenant_id, 但 ecos_spans 表实际无 tenant_id 列 (schema 漂移),
        //   导致每 60s BadSqlGrammarException "column s.tenant_id does not exist" (每分钟的 ERROR)。
        //   现按"unknown"租户兜底聚合 (span 不追踪 tenant 是当前 reality),
        //   避免跨租户误归因。后续如 spans 增 tenant_id 列, 可改回 GROUP BY s.tenant_id。
        String aggregateSql =
                "INSERT INTO ecos_tenant_usage (tenant_id, usage_date, quota_type, used_count, updated_at) " +
                "SELECT 'unknown' AS tenant_id, ?::date, 'API_CALLS', COUNT(*) AS used_count, NOW() " +
                "FROM ecos_spans s " +
                "WHERE s.created_at::date = ?::date " +
                "ON CONFLICT (tenant_id, usage_date, quota_type) " +
                "DO UPDATE SET used_count = EXCLUDED.used_count, updated_at = NOW()";

        int rows = jdbc.update(aggregateSql, today, today);

        String tokenAggregateSql =
                // Wave-7 T-29 (R5) 二次修复:
                //   旧 SQL "SUM(tokens)" 用了不存在的列 (实际是 total_tokens),
                //   且 ecos_token_usage 也无 tenant_id 列 (schema 漂移)。
                //   现按"unknown"租户兜底, 用 total_tokens 列。
                "INSERT INTO ecos_tenant_usage (tenant_id, usage_date, quota_type, used_count, updated_at) " +
                "SELECT 'unknown' AS tenant_id, ?::date, 'TOKENS', COALESCE(SUM(total_tokens), 0) AS used_count, NOW() " +
                "FROM ecos_token_usage " +
                "WHERE created_at::date = ?::date " +
                "ON CONFLICT (tenant_id, usage_date, quota_type) " +
                "DO UPDATE SET used_count = EXCLUDED.used_count, updated_at = NOW()";

        int tokenRows = jdbc.update(tokenAggregateSql, today, today);

        if (rows > 0 || tokenRows > 0) {
            log.debug("A7 UsageCollector aggregated {} API + {} token usage records for {} on {}",
                    rows, tokenRows, today, scheduleScenario());
        }

        return rows + tokenRows;
    }

    private String scheduleScenario() {
        return "today=" + LocalDate.now().toString();
    }

    /**
     * runtime-task 解析器：把租户用量聚合任务描述转换为单步执行计划。
     */
    private final class UsageTaskParser implements ITaskParser {

        @Override
        public TaskExecutionPlan parse(TaskDescription taskDescription) throws TaskParseException {
            validate(taskDescription);

            TaskExecutionPlan plan = new TaskExecutionPlan();
            plan.setTaskId(taskDescription.getTaskId());

            TaskExecutionPlan.ExecutionStep step = new TaskExecutionPlan.ExecutionStep();
            step.setStepId("step-1");
            step.setStepName("A7 租户用量聚合");
            step.setStepType(TASK_TYPE);
            step.setExecutor(TASK_TYPE);
            step.setConfig(taskDescription.getParameters() == null ? new HashMap<>() : new HashMap<>(taskDescription.getParameters()));

            List<TaskExecutionPlan.ExecutionStep> steps = new ArrayList<>();
            steps.add(step);
            plan.setSteps(steps);
            return plan;
        }

        @Override
        public boolean supports(String taskType) {
            return TASK_TYPE.equalsIgnoreCase(taskType);
        }

        @Override
        public void validate(TaskDescription taskDescription) throws TaskParseException {
            if (taskDescription == null
                    || taskDescription.getTaskId() == null
                    || taskDescription.getTaskId().isEmpty()) {
                throw new TaskParseException("usage aggregation task id is required");
            }
            if (!supports(taskDescription.getTaskType())) {
                throw new TaskParseException("unsupported task type: " + taskDescription.getTaskType());
            }
        }
    }

    /**
     * runtime-task 执行器：执行租户用量 SQL 聚合。
     */
    private final class UsageTaskExecutor implements ITaskExecutor {

        @Override
        public String execute(TaskExecutionPlan executionPlan, ITaskStatusCallback statusCallback) throws TaskExecutionException {
            try {
                int affectedRows = aggregateApiUsage();
                return String.format("{\"taskType\":\"%s\",\"affectedRows\":%d,\"completedAt\":\"%s\"}",
                        TASK_TYPE, affectedRows, java.time.Instant.now().toString());
            } catch (Exception ex) {
                log.error("A7 租户用量聚合任务执行失败: {}", ex.getMessage(), ex);
                throw new TaskExecutionException("usage aggregation failed", ex);
            }
        }

        @Override
        public void cancel(String taskId) {
            // 周期聚合任务不支持单次取消，忽略该操作。
        }

        @Override
        public void pause(String taskId) {
            // 周期聚合任务不支持单次暂停，忽略该操作。
        }

        @Override
        public void resume(String taskId) {
            // 周期聚合任务不支持单次恢复，忽略该操作。
        }

        @Override
        public TaskStatus getStatus(String taskId) {
            return null;
        }
    }
}
