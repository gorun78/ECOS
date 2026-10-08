package com.chinacreator.gzcm.engine.cognitive2.service;

import com.chinacreator.gzcm.runtime.core.task.callback.ITaskStatusCallback;
import com.chinacreator.gzcm.runtime.core.task.executor.ITaskExecutor;
import com.chinacreator.gzcm.runtime.core.task.model.TaskDescription;
import com.chinacreator.gzcm.runtime.core.task.model.TaskExecutionPlan;
import com.chinacreator.gzcm.runtime.core.task.model.TaskStatus;
import com.chinacreator.gzcm.runtime.core.task.parser.ITaskParser;
import com.chinacreator.gzcm.runtime.core.task.scheduling.TaskSchedulerService;
import com.chinacreator.gzcm.runtime.core.task.service.ITaskManagementService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/**
 * 认知引擎运行时扇出通道（F05-15 / 铁律 §2.5.3）— cognitive2 内部并行编排统一委托 runtime-task，
 * 禁止业务侧 {@code Executors.new*} 自建线程池（X-36）。
 *
 * <p>骨架与 {@code MentalScanTask} 同款：{@code @PostConstruct} 向 {@link ITaskManagementService}
 * 注册 Parser/Executor（任务类型 {@value #TASK_TYPE}），每步工作包装为一条 {@link TaskDescription}
 * 经 {@link TaskSchedulerService#scheduleTask(TaskDescription)} 异步投递；调用方通过返回的
 * {@link Group} 以同步等待语义收敛结果（保持 CognitivePipelineExecutor/KnowledgeReasonerService
 * 既有同步契约不变）。</p>
 *
 * <p>降级策略：runtime-task Bean 不在宿主进程（如 cognitive-boot 调试入口）时，退化为
 * 提交线程内串行执行——功能不丢，但不获得并行度；该分支不属于自建线程池，不违反铁律。</p>
 */
@Component
public class CognitiveRuntimeFanout {

    private static final Logger log = LoggerFactory.getLogger(CognitiveRuntimeFanout.class);

    /** runtime-task 任务类型（认知扇出单步）。 */
    public static final String TASK_TYPE = "COGNITIVE_FANOUT_STEP";

    /** parameters 中携带的挂接键：pending 注册表主键（UUID 字符串）。 */
    private static final String PARAM_REF = "cognitiveFanoutRef";

    private final ObjectProvider<ITaskManagementService> taskManagementProvider;
    private final ObjectProvider<TaskSchedulerService> schedulerProvider;

    /** taskId → 待执行步骤；Executor 回调完成后移除。 */
    private final Map<String, PendingStep> pending = new ConcurrentHashMap<>();

    private volatile boolean runtimeTaskAvailable;

    public CognitiveRuntimeFanout(ObjectProvider<ITaskManagementService> taskManagementProvider,
                                  ObjectProvider<TaskSchedulerService> schedulerProvider) {
        this.taskManagementProvider = taskManagementProvider;
        this.schedulerProvider = schedulerProvider;
    }

    /** 向 runtime-task 注册扇出 Parser/Executor；bean 缺失时标记降级模式。 */
    @PostConstruct
    public void registerWithRuntimeTask() {
        ITaskManagementService management = taskManagementProvider.getIfAvailable();
        TaskSchedulerService scheduler = schedulerProvider.getIfAvailable();
        if (management == null || scheduler == null) {
            log.warn("runtime-task 未装配，认知扇出降级为提交线程内串行执行（taskType={}", TASK_TYPE);
            return;
        }
        management.registerParser(TASK_TYPE, new FanoutStepParser());
        management.registerExecutor(TASK_TYPE, new FanoutStepExecutor());
        this.runtimeTaskAvailable = true;
        log.info("认知扇出通道已注册到 runtime-task: taskType={}", TASK_TYPE);
    }

    /** 新建一个扇出组（一次逻辑并行批次）。 */
    public Group group(String groupName) {
        return new Group(groupName);
    }

    /** 单步执行结果状态。 */
    public enum StepState { OK, FAILED, TIMEOUT }

    /** 单步结果：状态 + 值 + 异常（FAILED 时非空）。 */
    public static final class StepResult {
        private final StepState state;
        private final Object value;
        private final Throwable error;

        StepResult(StepState state, Object value, Throwable error) {
            this.state = state;
            this.value = value;
            this.error = error;
        }

        public boolean isOk() {
            return state == StepState.OK;
        }

        public StepState getState() {
            return state;
        }

        public Object getValue() {
            return value;
        }

        public Throwable getError() {
            return error;
        }
    }

    /** 内部登记项：supplier + 完成 future（跨调度线程回传结果）。 */
    private static final class PendingStep {
        final String stepId;
        final Supplier<Object> work;
        final CompletableFuture<Object> future = new CompletableFuture<>();

        PendingStep(String stepId, Supplier<Object> work) {
            this.stepId = stepId;
            this.work = work;
        }
    }

    /**
     * 扇出组：按 step 提交工作，{@link #awaitAll(long)} 以全局超时收敛全部结果。
     * <p>用法（CognitivePipelineExecutor 层内并行 / KnowledgeReasonerService 三路混合推理）：
     * <pre>{@code
     * CognitiveRuntimeFanout.Group g = fanout.group("layer-1");
     * g.step("node-a", () -> capability.executeNode(a, ctx));
     * Map<String, StepResult> rs = g.awaitAll(30_000);
     * }</pre>
     */
    public final class Group {
        private final String groupName;
        private final Map<String, PendingStep> submitted = new LinkedHashMap<>();

        private Group(String groupName) {
            this.groupName = groupName;
        }

        /**
         * 提交一步工作。runtime-task 可用 → scheduleTask 异步投递；否则当前线程串行执行。
         *
         * @param stepId 组内步骤标识（结果 Map 的键）
         * @param work   步骤逻辑（返回值即步骤结果；抛异常记 FAILED）
         */
        public synchronized void step(String stepId, Supplier<Object> work) {
            if (!runtimeTaskAvailable) {
                // 降级：串行执行，结果直接完成 future（不新建线程，铁律兼容）
                PendingStep step = new PendingStep(stepId, work);
                try {
                    step.future.complete(work.get());
                } catch (Throwable t) {
                    step.future.completeExceptionally(t);
                }
                submitted.put(stepId, step);
                return;
            }
            String ref = UUID.randomUUID().toString();
            PendingStep step = new PendingStep(stepId, work);
            pending.put(ref, step);
            submitted.put(stepId, step);

            TaskDescription description = new TaskDescription();
            description.setTaskId(ref);
            description.setTaskName("cognitive-fanout-" + groupName + "-" + stepId);
            description.setTaskType(TASK_TYPE);
            description.setDescription("F05-15 认知扇出单步: group=" + groupName + " step=" + stepId);
            Map<String, Object> params = new HashMap<>();
            params.put(PARAM_REF, ref);
            params.put("stepId", stepId);
            description.setParameters(params);

            TaskSchedulerService scheduler = schedulerProvider.getIfAvailable();
            try {
                scheduler.scheduleTask(description);
            } catch (Exception e) {
                pending.remove(ref);
                // 投递失败 → 同步兜底执行，保证组内步骤不丢
                log.warn("认知扇出投递 runtime-task 失败，降级串行执行 step={}: {}", stepId, e.getMessage());
                submitted.remove(stepId);
                try {
                    step.future.complete(work.get());
                } catch (Throwable t) {
                    step.future.completeExceptionally(t);
                }
                submitted.put(stepId, step);
            }
        }

        /**
         * 收敛全部步骤：整体超时 timeoutMs，超时步骤记 TIMEOUT，其余按 future 状态记 OK/FAILED。
         *
         * @return stepId → 结果（保持提交顺序）
         */
        public Map<String, StepResult> awaitAll(long timeoutMs) {
            long deadline = System.currentTimeMillis() + Math.max(0, timeoutMs);
            Map<String, StepResult> results = new LinkedHashMap<>();
            List<Map.Entry<String, PendingStep>> entries = new ArrayList<>(submitted.entrySet());
            for (Map.Entry<String, PendingStep> entry : entries) {
                long remaining = Math.max(0, deadline - System.currentTimeMillis());
                try {
                    Object value = entry.getValue().future.get(remaining, TimeUnit.MILLISECONDS);
                    results.put(entry.getKey(), new StepResult(StepState.OK, value, null));
                } catch (TimeoutException e) {
                    entry.getValue().future.cancel(false);
                    results.put(entry.getKey(), new StepResult(StepState.TIMEOUT, null, e));
                } catch (ExecutionException e) {
                    results.put(entry.getKey(),
                            new StepResult(StepState.FAILED, null, e.getCause() != null ? e.getCause() : e));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    results.put(entry.getKey(), new StepResult(StepState.FAILED, null, e));
                }
            }
            for (Map.Entry<String, PendingStep> entry : entries) {
                if (!entry.getValue().future.isDone() || entry.getValue().future.isCancelled()) {
                    // 超时/取消步骤清理挂接，避免 pending 泄漏
                    submitted.remove(entry.getKey());
                }
            }
            return results;
        }

        /** 组内已提交步骤数。 */
        public int size() {
            return submitted.size();
        }
    }

    /** 任务描述 → 单步执行计划（与 MentalScanTaskParser 同款骨架）。 */
    private static final class FanoutStepParser implements ITaskParser {

        @Override
        public TaskExecutionPlan parse(TaskDescription taskDescription) throws TaskParseException {
            validate(taskDescription);
            TaskExecutionPlan plan = new TaskExecutionPlan();
            plan.setTaskId(taskDescription.getTaskId());

            TaskExecutionPlan.ExecutionStep step = new TaskExecutionPlan.ExecutionStep();
            step.setStepId("fanout-" + taskDescription.getTaskId());
            step.setStepName(taskDescription.getTaskName());
            step.setStepType(TASK_TYPE);
            step.setExecutor(TASK_TYPE);
            step.setConfig(taskDescription.getParameters() == null
                    ? new HashMap<>() : new HashMap<>(taskDescription.getParameters()));

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
                throw new TaskParseException("cognitive fanout step ref is required");
            }
            if (!supports(taskDescription.getTaskType())) {
                throw new TaskParseException("unsupported cognitive fanout task type: "
                        + taskDescription.getTaskType());
            }
        }
    }

    /**
     * runtime-task 回调执行器：按 taskId 找回挂接步骤，在调度线程内执行 supplier 并回传 future。
     * <p>挂接丢失（组已超时清理/重复回调）视为过期步骤，返回 noop 成功。
     */
    private final class FanoutStepExecutor implements ITaskExecutor {

        @Override
        public String execute(TaskExecutionPlan executionPlan, ITaskStatusCallback statusCallback)
                throws TaskExecutionException {
            String ref = executionPlan == null ? null : executionPlan.getTaskId();
            PendingStep step = ref == null ? null : pending.get(ref);
            if (step == null) {
                log.debug("认知扇出步骤无挂接（已超时或过期），跳过执行 ref={}", ref);
                return "{\"taskType\":\"" + TASK_TYPE + "\",\"skipped\":true}";
            }
            try {
                Object value = step.work.get();
                step.future.complete(value);
                return "{\"taskType\":\"" + TASK_TYPE + "\",\"stepId\":\"" + step.stepId
                        + "\",\"completed\":true}";
            } catch (Throwable t) {
                step.future.completeExceptionally(t);
                throw new TaskExecutionException("cognitive fanout step failed: " + step.stepId, t);
            } finally {
                pending.remove(ref);
            }
        }

        @Override
        public void cancel(String taskId) {
            // 扇出单步不支持独立取消；超时由 Group.awaitAll 处理。
        }

        @Override
        public void pause(String taskId) {
            // 扇出单步为一次性投递，无暂停语义。
        }

        @Override
        public void resume(String taskId) {
            // 扇出单步为一次性投递，无恢复语义。
        }

        @Override
        public TaskStatus getStatus(String taskId) {
            return null;
        }
    }
}
