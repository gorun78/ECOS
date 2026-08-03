package com.chinacreator.gzcm.runtime.core.task.callback;

import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.chinacreator.gzcm.runtime.core.database.ISystemDatabaseAccess;
import com.chinacreator.gzcm.runtime.core.task.model.TaskDescription;
import com.chinacreator.gzcm.runtime.core.task.model.TaskStatus;
import com.chinacreator.gzcm.runtime.core.task.persistence.ITaskPersistenceService;

/**
 * Agent Cron 任务执行回调器
 * 实现 ITaskStatusCallback，在任务完成时将执行记录写入 ecos_cron_job_execution 表，
 * 并更新 ecos_cron_job 的 next_run_at。
 *
 * @author CDRC Runtime Team
 */
@Component
public class AgentCronTaskExecutor implements ITaskStatusCallback {

    private static final Logger logger = LoggerFactory.getLogger(AgentCronTaskExecutor.class);

    private final ITaskPersistenceService persistenceService;
    private final ISystemDatabaseAccess databaseAccess;

    @Autowired
    public AgentCronTaskExecutor(ITaskPersistenceService persistenceService,
                                 ISystemDatabaseAccess databaseAccess) {
        this.persistenceService = persistenceService;
        this.databaseAccess = databaseAccess;
    }

    @Override
    public void onStatusUpdate(TaskStatus status) {
        // 状态更新，暂不做处理
        logger.debug("AgentCronTaskExecutor.onStatusUpdate: taskId={}, status={}",
                status.getTaskId(), status.getStatus());
    }

    @Override
    public void onProgressUpdate(String taskId, Integer progress, String message) {
        // 进度更新，暂不做处理
    }

    @Override
    public void onStepStart(String taskId, String stepId, String stepName) {
        // 步骤开始，暂不做处理
    }

    @Override
    public void onStepComplete(String taskId, String stepId, String stepName, boolean success, String message) {
        // 步骤完成，暂不做处理
    }

    @Override
    public void onTaskComplete(String taskId, boolean success, String result, String errorMessage) {
        try {
            // 从持久化服务获取任务描述
            TaskDescription taskDescription = persistenceService.getTask(taskId);
            if (taskDescription == null || taskDescription.getExtensions() == null) {
                logger.debug("AgentCronTaskExecutor.onTaskComplete: no extensions found for taskId={}", taskId);
                return;
            }

            // 从 extensions 中提取 cronJobId
            Object cronJobIdObj = taskDescription.getExtensions().get("cronJobId");
            if (cronJobIdObj == null) {
                logger.debug("AgentCronTaskExecutor.onTaskComplete: no cronJobId in extensions for taskId={}", taskId);
                return;
            }

            Long cronJobId = toLong(cronJobIdObj);
            if (cronJobId == null) {
                logger.warn("AgentCronTaskExecutor.onTaskComplete: invalid cronJobId in extensions for taskId={}", taskId);
                return;
            }

            // 写入执行历史到 ecos_cron_job_execution
            writeExecutionHistory(cronJobId, success, result, errorMessage);

            // 更新 CronJob 的 nextRunAt
            updateCronJobNextRunAt(cronJobId);

            logger.info("AgentCronTaskExecutor.onTaskComplete: cronJobId={}, taskId={}, success={}",
                    cronJobId, taskId, success);

        } catch (Exception e) {
            logger.warn("AgentCronTaskExecutor.onTaskComplete failed for taskId={}: {}", taskId, e.getMessage(), e);
        }
    }

    @Override
    public void onError(String taskId, String error, String stackTrace) {
        logger.warn("AgentCronTaskExecutor.onError: taskId={}, error={}", taskId, error);
    }

    /**
     * 将执行记录写入 ecos_cron_job_execution 表
     */
    private void writeExecutionHistory(Long cronJobId, boolean success, String result, String errorMessage) {
        try {
            String status = success ? "SUCCESS" : "FAILED";
            String sql = "INSERT INTO ecos_cron_job_execution " +
                    "(cron_job_id, started_at, finished_at, status, result, error_message, created_at) " +
                    "VALUES (?, NOW(), NOW(), ?, ?, ?, NOW())";

            databaseAccess.executeUpdate(sql, cronJobId, status, result, errorMessage);
            logger.debug("Execution history written for cronJobId={}, status={}", cronJobId, status);
        } catch (Exception e) {
            logger.warn("Failed to write execution history for cronJobId={}: {}", cronJobId, e.getMessage(), e);
        }
    }

    /**
     * 更新 ecos_cron_job 的 next_run_at 字段
     * 将 next_run_at 设置为当前时间（表示该 cron job 已执行，等待下一次调度）
     */
    private void updateCronJobNextRunAt(Long cronJobId) {
        try {
            String sql = "UPDATE ecos_cron_job SET " +
                    "last_run_at = NOW(), " +
                    "next_run_at = NULL, " +
                    "status = 'IDLE', " +
                    "updated_at = NOW() " +
                    "WHERE id = ?";

            int affected = databaseAccess.executeUpdate(sql, cronJobId);
            if (affected > 0) {
                logger.debug("CronJob nextRunAt updated for cronJobId={}", cronJobId);
            } else {
                logger.warn("CronJob not found for update: id={}", cronJobId);
            }
        } catch (Exception e) {
            logger.warn("Failed to update CronJob nextRunAt for cronJobId={}: {}", cronJobId, e.getMessage(), e);
        }
    }

    /**
     * 将 Object 转换为 Long
     */
    private Long toLong(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Long) return (Long) obj;
        if (obj instanceof Integer) return ((Integer) obj).longValue();
        if (obj instanceof Number) return ((Number) obj).longValue();
        try {
            return Long.parseLong(obj.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
