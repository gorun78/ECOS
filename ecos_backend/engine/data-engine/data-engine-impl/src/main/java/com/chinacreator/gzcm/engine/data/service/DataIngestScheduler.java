package com.chinacreator.gzcm.engine.data.service;

import com.chinacreator.gzcm.engine.data.datasource.entity.DataSourceEntity;
import com.chinacreator.gzcm.engine.data.metadata.MetadataAsyncTrigger;
import com.chinacreator.gzcm.engine.data.repository.DataSourceRepository;
import com.chinacreator.gzcm.runtime.core.task.callback.ITaskStatusCallback;
import com.chinacreator.gzcm.runtime.core.task.executor.ITaskExecutor;
import com.chinacreator.gzcm.runtime.core.task.model.TaskDescription;
import com.chinacreator.gzcm.runtime.core.task.model.TaskExecutionPlan;
import com.chinacreator.gzcm.runtime.core.task.model.TaskStatus;
import com.chinacreator.gzcm.runtime.core.task.parser.ITaskParser;
import com.chinacreator.gzcm.runtime.core.task.scheduling.TaskSchedulerService;
import com.chinacreator.gzcm.runtime.core.task.service.ITaskManagementService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * DataIngestScheduler — 数据采集定时触发。
 * <p>
 * 每 60s 扫描各数据源 {@code metadata_config.ingest} 段：
 * enabled=true 且 cron 到点（相对上次触发时间）→ 异步提交采集型管道任务（async=true，不阻塞调度线程）。
 * 复用 {@link AutoCollectScheduler} 的扫描模式与 cron 计算工具（MetadataAsyncTrigger.nextFireTime）。
 * <p>
 * 定时采集与即时采集共用 {@link DataIngestService#runOnce(String, List, boolean)} 执行链，
 * 统一走 runtime-task（PIPELINE 类型），满足"定时采集进异步任务中心"。
 */
@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnBean(com.chinacreator.gzcm.engine.data.pipeline.PipelineService.class)
public class DataIngestScheduler {

    private static final Logger log = LoggerFactory.getLogger(DataIngestScheduler.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String TASK_TYPE = "DATA_INGEST_SCAN";

    private final DataSourceRepository dsRepository;
    private final DataIngestService ingestService;
    private final ITaskManagementService taskManagementService;
    private final TaskSchedulerService taskSchedulerService;

    public DataIngestScheduler(DataSourceRepository dsRepository,
                               DataIngestService ingestService,
                               ITaskManagementService taskManagementService,
                               TaskSchedulerService taskSchedulerService) {
        this.dsRepository = dsRepository;
        this.ingestService = ingestService;
        this.taskManagementService = taskManagementService;
        this.taskSchedulerService = taskSchedulerService;
    }

    @PostConstruct
    public void registerWithRuntimeTask() {
        taskManagementService.registerParser(TASK_TYPE, new ScanParser());
        taskManagementService.registerExecutor(TASK_TYPE, new ScanExecutor());

        TaskDescription description = new TaskDescription();
        description.setTaskName("data-ingest-scan");
        description.setTaskType(TASK_TYPE);
        description.setDescription("定时扫描 metadata_config.ingest 到点数据源并提交采集");
        description.setParameters(new HashMap<>());
        taskSchedulerService.schedulePeriodicTask(description, 30_000L, 60_000L);
    }

    public void scanDueIngest() {
        Iterable<DataSourceEntity> all;
        try {
            all = dsRepository.findAll();
        } catch (Exception e) {
            log.warn("DataIngestScheduler 扫描数据源失败: {}", e.getMessage());
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        for (DataSourceEntity ds : all) {
            try {
                Map<String, Object> cfg = parseMetadataConfig(ds.getMetadataConfig());
                if (!(cfg.get("ingest") instanceof Map<?, ?> ingest)) {
                    continue;
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> ingestCfg = (Map<String, Object>) ingest;
                if (!Boolean.TRUE.equals(ingestCfg.get("enabled"))) {
                    continue;
                }
                String cron = String.valueOf(ingestCfg.getOrDefault("cron", ""));
                if (cron.isBlank()) {
                    continue;
                }
                Object tablesObj = ingestCfg.get("tables");
                List<String> tables = toTables(tablesObj);
                if (tables.isEmpty()) {
                    continue;
                }
                String lastRunStr = String.valueOf(ingestCfg.getOrDefault("lastRunAt", ""));
                LocalDateTime lastRun = parseDateTime(lastRunStr);
                if (lastRun != null) {
                    LocalDateTime next = MetadataAsyncTrigger.nextFireTime(cron, lastRun);
                    if (next == null || next.isAfter(now)) {
                        continue; // 未到下次触发
                    }
                }
                // 到点触发：异步提交，不阻塞调度线程
                log.info("定时数据采集触发: ds={}, tables={}, cron={}", ds.getDatasourceId(), tables, cron);
                ingestService.runOnce(ds.getDatasourceId(), tables, true);
                // 更新 lastRunAt，避免下一周期重复触发
                ingestCfg.put("lastRunAt", now.toString());
                cfg.put("ingest", ingestCfg);
                String json = MAPPER.writeValueAsString(cfg);
                ingestService.updateIngestConfig(ds.getDatasourceId(), json);
            } catch (Exception e) {
                log.debug("DataIngestScheduler 处理数据源 {} 失败: {}", ds.getDatasourceId(), e.getMessage());
            }
        }
    }

    /** 供 DataIngestScheduler 更新 metadata_config（委托 DataIngestService 的 updateMetadataConfig）。 */
    private List<String> toTables(Object tablesObj) {
        if (tablesObj instanceof List<?> list) {
            return list.stream().map(String::valueOf).filter(s -> !s.isBlank()).toList();
        }
        return List.of();
    }

    private LocalDateTime parseDateTime(String s) {
        if (s == null || s.isBlank() || "null".equals(s)) {
            return null;
        }
        try {
            return LocalDateTime.parse(s);
        } catch (Exception e) {
            return null;
        }
    }

    private Map<String, Object> parseMetadataConfig(String json) {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return MAPPER.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {
            });
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    private final class ScanParser implements ITaskParser {
        @Override
        public TaskExecutionPlan parse(TaskDescription taskDescription) throws TaskParseException {
            validate(taskDescription);
            TaskExecutionPlan plan = new TaskExecutionPlan();
            plan.setTaskId(taskDescription.getTaskId());
            TaskExecutionPlan.ExecutionStep step = new TaskExecutionPlan.ExecutionStep();
            step.setStepId("step-1");
            step.setStepName("DataIngest scanDueIngest");
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
                throw new TaskParseException("data ingest task id is required");
            }
            if (!supports(taskDescription.getTaskType())) {
                throw new TaskParseException("unsupported data ingest task type: "
                        + taskDescription.getTaskType());
            }
        }
    }

    private final class ScanExecutor implements ITaskExecutor {
        @Override
        public String execute(TaskExecutionPlan executionPlan, ITaskStatusCallback statusCallback)
                throws TaskExecutionException {
            try {
                scanDueIngest();
                return String.format("{\"taskType\":\"%s\",\"completedAt\":\"%s\"}",
                        TASK_TYPE, java.time.Instant.now().toString());
            } catch (Exception ex) {
                log.error("DataIngestScheduler runtime-task 执行失败: {}", ex.getMessage(), ex);
                throw new TaskExecutionException("data ingest scan failed", ex);
            }
        }

        @Override
        public void cancel(String taskId) { }

        @Override
        public void pause(String taskId) { }

        @Override
        public void resume(String taskId) { }

        @Override
        public TaskStatus getStatus(String taskId) {
            return null;
        }
    }
}
