package com.chinacreator.gzcm.engine.cognitive2.service.mental;

import com.chinacreator.gzcm.common.cognitive.EvidenceRecordVO;
import com.chinacreator.gzcm.common.cognitive.HypothesisVO;
import com.chinacreator.gzcm.engine.cognitive2.service.CognitiveEvidenceService;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * P2b 心智层定时补算任务（PMO-59 / API006）— 统一委托 runtime-task 全局调度（铁律 §2.5.3，
 * 禁止自建 ScheduledExecutorService）。
 *
 * <p>骨架与 gateway {@code UsageCollector} 同款：{@code @PostConstruct} 注册
 * Parser/Executor → {@code schedulePeriodicTask} 周期执行。周期读配置
 * {@code ecos.cognitive.scan-interval}（Duration 语义，如 {@code 10m}），默认 10min。</p>
 *
 * <p>每代扫描语义：取最近 {@value #SCAN_RECENT_LIMIT} 条证据（活动/冲突态），逐一过
 * {@link MentalInvalidationDetector#detectAndInvalidate}——命中→假设 INVALIDATED
 * + 失效事件 + 告警（经 service 单点收口）。与"新证据登记即时检测"互补：登记链路只覆盖
 * 登记时刻有效的假设，补算兜底"假设/证据先后序错位"（证据先到、假设后到命中同 metric）。</p>
 */
@Component
public class MentalScanTask {

    private static final Logger log = LoggerFactory.getLogger(MentalScanTask.class);

    /** runtime-task 调度任务类型。 */
    private static final String TASK_TYPE = "COGNITIVE_MENTAL_SCAN";

    /** 每代扫描的证据数量上限（create_time 倒序取近 N 条，兼顾充分性与开销）。 */
    private static final int SCAN_RECENT_LIMIT = 200;

    private final CognitiveEvidenceService evidenceService;
    private final MentalInvalidationDetector detector;
    private final ITaskManagementService taskManagementService;
    private final TaskSchedulerService taskSchedulerService;

    /** 补算周期（Duration 语义：30s / 10m / 1h...），默认 10m。 */
    @Value("${ecos.cognitive.scan-interval:10m}")
    private Duration scanInterval;

    public MentalScanTask(CognitiveEvidenceService evidenceService,
                          MentalInvalidationDetector detector,
                          ITaskManagementService taskManagementService,
                          TaskSchedulerService taskSchedulerService) {
        this.evidenceService = evidenceService;
        this.detector = detector;
        this.taskManagementService = taskManagementService;
        this.taskSchedulerService = taskSchedulerService;
    }

    /** 将心智补算注册为 runtime-task 周期任务（周期 = ecos.cognitive.scan-interval，默认 10min）。 */
    @PostConstruct
    public void registerWithRuntimeTask() {
        taskManagementService.registerParser(TASK_TYPE, new MentalScanTaskParser());
        taskManagementService.registerExecutor(TASK_TYPE, new MentalScanTaskExecutor());

        TaskDescription description = new TaskDescription();
        description.setTaskName("cognitive-mental-scan");
        description.setTaskType(TASK_TYPE);
        description.setDescription("PMO-59 认知心智层补算：近期证据 × 有效假设 失效检测兜底");
        description.setParameters(new HashMap<>());

        long interval = scanInterval != null && scanInterval.toMillis() > 0 ? scanInterval.toMillis() : 600_000L;
        String scheduleId = taskSchedulerService.schedulePeriodicTask(description, 0L, interval);
        log.info("PMO-59 心智层补算任务已注册到 runtime-task: taskType={}, scheduleId={}, periodMillis={}",
                TASK_TYPE, scheduleId, interval);
    }

    /**
     * 执行一代补算：扫描近 {@value #SCAN_RECENT_LIMIT} 条证据，逐条过失效检测器。
     *
     * @return 汇总（scanned=扫描证据数，invalidatedTotal=命中失效假设数）
     */
    int[] scanOnce() {
        int invalidatedTotal = 0;
        List<EvidenceRecordVO> evidences = evidenceService.list(null, null);
        List<EvidenceRecordVO> recent = new ArrayList<>(
                Math.min(evidences.size(), SCAN_RECENT_LIMIT));
        for (EvidenceRecordVO evidence : evidences) {
            recent.add(evidence);
            if (recent.size() >= SCAN_RECENT_LIMIT) {
                break;
            }
        }
        for (EvidenceRecordVO evidence : recent) {
            try {
                List<HypothesisVO> invalidated = detector.detectAndInvalidate(evidence);
                invalidatedTotal += invalidated == null ? 0 : invalidated.size();
            } catch (Exception e) {
                log.warn("心智补算单证据扫描异常 (不阻塞其余证据): evidence={} err={}",
                        evidence.getId(), e.getMessage());
            }
        }
        log.info("PMO-59 心智层补算一代完成: scanned={} invalidated={}", recent.size(), invalidatedTotal);
        return new int[]{recent.size(), invalidatedTotal};
    }

    /** runtime-task 解析器：心智补算任务描述 → 单步执行计划。 */
    private final class MentalScanTaskParser implements ITaskParser {

        @Override
        public TaskExecutionPlan parse(TaskDescription taskDescription) throws TaskParseException {
            validate(taskDescription);
            TaskExecutionPlan plan = new TaskExecutionPlan();
            plan.setTaskId(taskDescription.getTaskId());

            TaskExecutionPlan.ExecutionStep step = new TaskExecutionPlan.ExecutionStep();
            step.setStepId("step-1");
            step.setStepName("PMO-59 心智层补算（近期证据×有效假设）");
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
                throw new TaskParseException("mental scan task id is required");
            }
            if (!supports(taskDescription.getTaskType())) {
                throw new TaskParseException("unsupported mental scan task type: " + taskDescription.getTaskType());
            }
        }
    }

    /** runtime-task 执行器：触发一代心智补算扫描。 */
    private final class MentalScanTaskExecutor implements ITaskExecutor {

        @Override
        public String execute(TaskExecutionPlan executionPlan, ITaskStatusCallback statusCallback)
                throws TaskExecutionException {
            try {
                int[] result = scanOnce();
                return String.format("{\"taskType\":\"%s\",\"scanned\":%d,\"invalidated\":%d,\"completedAt\":\"%s\"}",
                        TASK_TYPE, result[0], result[1], java.time.Instant.now().toString());
            } catch (Exception ex) {
                log.error("PMO-59 心智层补算任务执行失败: {}", ex.getMessage(), ex);
                throw new TaskExecutionException("cognitive mental scan failed", ex);
            }
        }

        @Override
        public void cancel(String taskId) {
            // 周期补算任务不支持单次取消，忽略该操作。
        }

        @Override
        public void pause(String taskId) {
            // 周期补算任务不支持单次暂停，忽略该操作。
        }

        @Override
        public void resume(String taskId) {
            // 周期补算任务不支持单次恢复，忽略该操作。
        }

        @Override
        public TaskStatus getStatus(String taskId) {
            return null;
        }
    }
}
