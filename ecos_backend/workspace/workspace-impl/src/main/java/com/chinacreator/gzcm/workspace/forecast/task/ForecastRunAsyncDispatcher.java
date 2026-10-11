package com.chinacreator.gzcm.workspace.forecast.task;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;

import com.chinacreator.gzcm.runtime.core.task.model.TaskDescription;
import com.chinacreator.gzcm.runtime.core.task.service.ITaskManagementService;
import com.chinacreator.gzcm.workspace.forecast.ForecastRunCommand;
import com.chinacreator.gzcm.workspace.forecast.ForecastRunNotLiveException;
import com.chinacreator.gzcm.workspace.scenario.RunMode;

/**
 * 预测 run 异步派发器 —— 详细设计-09 v1.2 §十一 H-6（PRD-09 §七-1，C218）。
 *
 * <p><b>底座收口（§2.5）</b>：异步任务唯一通道 = runtime-task
 * {@link ITaskManagementService#submitTask(TaskDescription)}，返回<b>真实 taskId</b>
 * （UUID，落 {@code td_runtime_task_status}），前端 AsyncTaskCenter 与 run 进度页据此消费；
 * <b>禁自建任务表 / 自造 jobId</b>。门面未装配 ⇒ 501 {@code STUB_CAPABILITY}
 * （禁假异步 / 200 造数，X-44/X-50）。</p>
 *
 * <p><b>阈值（PRD-09 §七-1 原文）</b>：预估 {@code >50} 个项目或 {@code >100000} 行
 * ⇒ 转异步；否则同步直算（{@code ForecastRunOrchestrationService.create} 分流）。</p>
 *
 * <p><b>执行派发载体</b>：runtime-task 无"提交即跑"通用门面（{@code executeTask} 在调用线程
 * 同步执行），故本派发器把 {@code executeTask(taskId)} 提交到 workspace 命名执行器
 * {@code workspaceTaskExecutor}（{@code WorkspaceTaskExecutorConfig}，W181 底座收口）——
 * 禁裸 {@code new Thread}、禁 {@code CompletableFuture.runAsync} common pool
 * （{@code RuntimeConvergenceArchTest} 门禁）。</p>
 */
@Component
public class ForecastRunAsyncDispatcher {

    private static final Logger log = LoggerFactory.getLogger(ForecastRunAsyncDispatcher.class);

    /** runtime-task 任务类型/执行器类型字面量（Parser/Executor/Registrar 与本类共享，单源）。 */
    public static final String TASK_TYPE = "FORECAST_RUN";

    /** PRD-09 §七-1：项目数阈值（>50 转异步）。 */
    public static final int PROJECT_COUNT_THRESHOLD = 50;
    /** PRD-09 §七-1：行数阈值（>10 万行转异步）。 */
    public static final long ROW_ESTIMATE_THRESHOLD = 100_000L;

    /** 任务参数键：规范化命令的全字段序列化（Executor 侧经 {@link #commandFrom(Map)} 还原）。 */
    public static final String PARAM_COMMAND = "forecastRunCommand";

    private final ObjectProvider<ITaskManagementService> taskManagementProvider;
    private final TaskExecutor workspaceTaskExecutor;

    public ForecastRunAsyncDispatcher(ObjectProvider<ITaskManagementService> taskManagementProvider,
                                      @Qualifier("workspaceTaskExecutor") TaskExecutor workspaceTaskExecutor) {
        this.taskManagementProvider = taskManagementProvider;
        this.workspaceTaskExecutor = workspaceTaskExecutor;
    }

    /** 是否命中异步阈值（PRD-09 §七-1：>50 项目 或 >10 万行）。 */
    public boolean isAsyncEligible(ForecastRunCommand command) {
        return command.getProjectCount() > PROJECT_COUNT_THRESHOLD
                || command.getRowEstimate() > ROW_ESTIMATE_THRESHOLD;
    }

    /**
     * 派发异步 run：① 组 {@link TaskDescription}（taskType=FORECAST_RUN，async=true，
     * parameters=规范化命令）；② {@code submitTask} 取<b>真实</b> taskId；
     * ③ 把 {@code executeTask(taskId)} 提交到命名执行器（parser→executor 全生命周期走底座）。
     *
     * @return runtime-task 真实 taskId（UUID，前端 AsyncTaskCenter 消费）
     * @throws ForecastRunNotLiveException 门面未装配（501，禁假异步）；提交失败（依赖故障原样上抛语义）
     */
    public String dispatch(ForecastRunCommand command) {
        ITaskManagementService taskService = taskManagementProvider.getIfAvailable();
        if (taskService == null) {
            throw new ForecastRunNotLiveException("workspace/forecast-run/async",
                    "ITaskManagementService 未装配（runtime-task 底座不在 classpath 或条件未满足），"
                            + "异步派发不可用——禁自造 jobId 假异步");
        }
        TaskDescription description = taskDescriptionOf(command);
        String taskId;
        try {
            taskId = taskService.submitTask(description);
        } catch (ITaskManagementService.TaskManagementException e) {
            // 依赖故障 fail-loud：不降级为同步、不自造 taskId
            throw new ForecastRunNotLiveException("workspace/forecast-run/async",
                    "runtime-task submitTask 失败: " + e.getMessage());
        }
        final String asyncTaskId = taskId;
        workspaceTaskExecutor.execute(() -> {
            try {
                taskService.executeTask(asyncTaskId);
            } catch (Exception e) {
                log.warn("预测 run 异步执行失败 taskId={} scenario={} reason={}",
                        asyncTaskId, command.getScenarioId(), e.getMessage());
            }
        });
        log.info("预测 run 已转异步 taskId={} scenario={} projectCount={} rowEstimate={}",
                taskId, command.getScenarioId(), command.getProjectCount(), command.getRowEstimate());
        return taskId;
    }

    /** 命令 → runtime-task 参数面（键名与 {@code ForecastRunRequestValidator} 入参键一致，单源）。 */
    public static TaskDescription taskDescriptionOf(ForecastRunCommand command) {
        TaskDescription description = new TaskDescription();
        description.setTaskName("forecast-run:" + command.getScenarioId());
        description.setTaskType(TASK_TYPE);
        description.setDescription("预测 run 异步计算（H-6，PRD-09 §七-1 阈值转异步）");
        description.setAsync(Boolean.TRUE);
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put(PARAM_COMMAND, paramsOf(command));
        description.setParameters(parameters);
        return description;
    }

    /** 规范化命令 → 参数 Map（Executor 侧重建命令的唯一序列化通道）。 */
    public static Map<String, Object> paramsOf(ForecastRunCommand command) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("scenarioId", command.getScenarioId());
        m.put("runType", command.getRunType());
        m.put("caliberId", command.getCaliberId());
        m.put("caliberVersion", command.getCaliberVersion());
        m.put("asOfTime", command.getAsOfTime());
        m.put("snapshotId", command.getSnapshotId());
        m.put("formulaVersion", command.getFormulaVersion());
        m.put("idempotencyKey", command.getIdempotencyKey());
        m.put("contentHash", command.getContentHash());
        m.put("runMode", command.getRunMode() == null ? null : command.getRunMode().name());
        m.put("baselineRunId", command.getBaselineRunId());
        m.put("projectCount", command.getProjectCount());
        m.put("rowEstimate", command.getRowEstimate());
        return m;
    }

    /** 参数 Map → 规范化命令（缺失任一要素 fail-loud，禁默认值吞掉）。 */
    public static ForecastRunCommand commandFrom(Map<String, Object> p) {
        Map<String, Object> m = p == null ? Map.of() : p;
        String required = firstMissing(m);
        if (required != null) {
            throw new IllegalArgumentException("FORECAST_RUN 任务参数缺少必需要素: " + required);
        }
        return new ForecastRunCommand(
                str(m.get("scenarioId")), str(m.get("runType")),
                str(m.get("caliberId")), str(m.get("caliberVersion")),
                str(m.get("asOfTime")), str(m.get("snapshotId")), str(m.get("formulaVersion")),
                str(m.get("idempotencyKey")), str(m.get("contentHash")),
                RunMode.parseOrDefault(str(m.get("runMode"))),
                str(m.get("baselineRunId")), null,
                intOf(m.get("projectCount")), longOf(m.get("rowEstimate")));
    }

    private static String firstMissing(Map<String, Object> m) {
        for (String key : new String[] {"scenarioId", "runType", "caliberId", "caliberVersion",
                "asOfTime", "snapshotId", "formulaVersion", "idempotencyKey", "contentHash"}) {
            if (str(m.get(key)) == null) {
                return key;
            }
        }
        return null;
    }

    private static String str(Object v) {
        if (v == null) {
            return null;
        }
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? null : s;
    }

    private static int intOf(Object v) {
        return v == null ? 0 : Integer.parseInt(String.valueOf(v));
    }

    private static long longOf(Object v) {
        return v == null ? 0L : Long.parseLong(String.valueOf(v));
    }
}
