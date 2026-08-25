package com.chinacreator.gzcm.engine.data.pipeline;

import com.chinacreator.gzcm.runtime.core.task.callback.ITaskStatusCallback;
import com.chinacreator.gzcm.runtime.core.task.executor.ITaskExecutor;
import com.chinacreator.gzcm.runtime.core.task.model.TaskExecutionPlan;
import com.chinacreator.gzcm.runtime.core.task.model.TaskStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Pipeline 任务执行器 — 实现 ITaskExecutor，将 Pipeline 执行接入 runtime-task 生命周期。
 * <p>
 * 注册到 ITaskManagementService（taskType/executorType = "PIPELINE"）。
 * runtime-task 的执行流程：submitTask → parseTask（PipelineTaskParser）→ executeTask（本类）。
 * <p>
 * 执行时：从 TaskExecutionPlan.steps[0].config 取 definitionId，
 * 调用 PipelineExecutionService.executePipeline(definitionId, callback, taskId)，
 * 透传 ITaskStatusCallback 回调链（onStepStart/onStepComplete/onProgressUpdate/onTaskComplete）。
 *
 * @author DataBridge Datanet Team
 */
@Component
public class PipelineTaskExecutor implements ITaskExecutor {

    private static final Logger log = LoggerFactory.getLogger(PipelineTaskExecutor.class);

    private final PipelineExecutionService executionService;

    @Autowired
    public PipelineTaskExecutor(PipelineExecutionService executionService) {
        this.executionService = executionService;
    }

    @Override
    public String execute(TaskExecutionPlan plan, ITaskStatusCallback callback) throws TaskExecutionException {
        if (plan == null || plan.getSteps() == null || plan.getSteps().isEmpty()) {
            throw new TaskExecutionException("Pipeline execution plan has no steps");
        }

        String taskId = plan.getTaskId();
        // definitionId 优先从 step.config 取，兼容从 plan.context 取
        String definitionId = null;
        TaskExecutionPlan.ExecutionStep step = plan.getSteps().get(0);
        if (step.getConfig() != null) {
            Object val = step.getConfig().get("definitionId");
            if (val != null) definitionId = val.toString();
        }
        if (definitionId == null && plan.getContext() != null) {
            Object val = plan.getContext().get("definitionId");
            if (val != null) definitionId = val.toString();
        }
        if (definitionId == null || definitionId.isEmpty()) {
            throw new TaskExecutionException("definitionId not found in pipeline execution plan");
        }

        if (callback != null) {
            callback.onProgressUpdate(taskId, 0, "Pipeline execution started");
        }
        log.info("PipelineTaskExecutor.execute: taskId={}, definitionId={}", taskId, definitionId);

        try {
            PipelineExecution exec = executionService.executePipeline(definitionId, callback, taskId);
            boolean success = "COMPLETED".equals(exec.getStatus());
            if (callback != null) {
                callback.onTaskComplete(taskId, success, exec.getId(), exec.getErrorMessage());
            }
            log.info("PipelineTaskExecutor.execute done: taskId={}, executionId={}, success={}",
                    taskId, exec.getId(), success);
            return exec.getId();
        } catch (Exception e) {
            if (callback != null) {
                callback.onTaskComplete(taskId, false, null, e.getMessage());
                callback.onError(taskId, e.getMessage(), getStackTrace(e));
            }
            throw new TaskExecutionException("Pipeline execution failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void cancel(String taskId) throws TaskExecutionException {
        // Pipeline 当前为同步串行执行，不支持运行中取消；透传记录
        log.info("PipelineTaskExecutor.cancel: taskId={} (cancel not supported for synchronous pipeline)", taskId);
    }

    @Override
    public void pause(String taskId) throws TaskExecutionException {
        log.info("PipelineTaskExecutor.pause: taskId={} (pause not supported for synchronous pipeline)", taskId);
    }

    @Override
    public void resume(String taskId) throws TaskExecutionException {
        log.info("PipelineTaskExecutor.resume: taskId={} (resume not supported for synchronous pipeline)", taskId);
    }

    @Override
    public TaskStatus getStatus(String taskId) throws TaskExecutionException {
        // Pipeline 状态由 runtime-task 管理，此处返回透传状态
        TaskStatus status = new TaskStatus();
        status.setTaskId(taskId);
        status.setStatus(TaskStatus.Status.RUNNING);
        return status;
    }

    private String getStackTrace(Throwable t) {
        java.io.StringWriter sw = new java.io.StringWriter();
        t.printStackTrace(new java.io.PrintWriter(sw));
        return sw.toString();
    }
}
