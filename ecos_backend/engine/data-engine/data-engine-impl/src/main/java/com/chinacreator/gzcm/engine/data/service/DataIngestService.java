package com.chinacreator.gzcm.engine.data.service;

import com.chinacreator.gzcm.common.exception.BusinessException;
import com.chinacreator.gzcm.common.exception.ValidationException;
import com.chinacreator.gzcm.engine.data.DataSourceService;
import com.chinacreator.gzcm.engine.data.datasource.entity.DataSourceEntity;
import com.chinacreator.gzcm.engine.data.pipeline.PipelineDefinition;
import com.chinacreator.gzcm.engine.data.pipeline.PipelineSaveDTO;
import com.chinacreator.gzcm.engine.data.pipeline.PipelineService;
import com.chinacreator.gzcm.runtime.core.task.model.TaskDescription;
import com.chinacreator.gzcm.runtime.core.task.model.TaskStatus;
import com.chinacreator.gzcm.runtime.core.task.service.ITaskManagementService;
import com.chinacreator.gzcm.sysman.config.service.impl.SysConfigService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * DataIngestService — 数据采集（特殊管道任务：SOURCE_JDBC → SINK_MINIO，MinIO 作近源库）。
 * <p>
 * 设计（符合"采集任务作为一类特殊管道任务处理"）：
 * <ul>
 *   <li>每张表的采集 = 一条采集型管道定义（{@code 采集-{datasourceId}-{table}}），
 *       节点 SOURCE_JDBC(读外部源表) → SINK_MINIO(写数据湖近源库 CSV)。</li>
 *   <li>即时采集：创建/复用定义 → 提交 runtime-task(PIPELINE) 执行，返回 taskId。</li>
 *   <li>定时采集：策略写 {@code td_datasource.metadata_config.ingest}（JSON 段），
 *       由 {@link DataIngestScheduler} 扫描触发，执行链路与即时采集一致。</li>
 *   <li>默认目标 = 数据湖 MinIO（近源库），对象名按分层规范 §三 组装为
 *       {@code raw/structured/{datasourceId}/{table}/{dw.lake.partition_by}=YYYY-MM-DD/{table}_{ts}.csv}。</li>
 * </ul>
 */
@Service
public class DataIngestService {

    private static final Logger log = LoggerFactory.getLogger(DataIngestService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 采集管道定义名前缀 */
    public static final String INGEST_DEF_PREFIX = "采集-";
    /** 表名白名单（字母/数字/下划线/点，防 SQL 注入） */
    private static final String TABLE_PATTERN = "[A-Za-z0-9_.]{1,128}";

    private final PipelineService pipelineService;
    private final ITaskManagementService taskManagementService;
    private final DataSourceService dataSourceService;

    /** 系统配置（读取 dw.lake.partition_by）；可选注入，无 bean 时回退默认 dt */
    @Autowired(required = false)
    private SysConfigService sysConfigService;

    public DataIngestService(PipelineService pipelineService,
                             ITaskManagementService taskManagementService,
                             DataSourceService dataSourceService) {
        this.pipelineService = pipelineService;
        this.taskManagementService = taskManagementService;
        this.dataSourceService = dataSourceService;
    }

    /** 数据湖分区字段名（dw.lake.partition_by，默认 dt；非法值由 LakeObjectKeys 回退并 warn）。 */
    private String resolvePartitionField() {
        return LakeObjectKeys.resolvePartitionField(
                sysConfigService != null ? sysConfigService.getString(LakeObjectKeys.CFG_PARTITION_BY) : null);
    }

    /**
     * 即时采集：对每张表创建/复用采集型管道定义并提交 runtime-task 执行。
     *
     * @param datasourceId 源数据源 ID
     * @param tableNames   待采集表名列表（白名单校验）
     * @return {@code {datasourceId, submitted, tasks:[{table, definitionId, taskId, status}]}}
     */
    public Map<String, Object> runOnce(String datasourceId, List<String> tableNames) {
        return runOnce(datasourceId, tableNames, false);
    }

    /**
     * 采集执行（内部）：async=false 同步等待完成（前端即时采集，可立即看到结果）；
     * async=true 非阻塞提交（定时调度器触发，避免占用调度线程）。
     */
    public Map<String, Object> runOnce(String datasourceId, List<String> tableNames, boolean async) {
        DataSourceEntity ds = requireDatasource(datasourceId);
        if (tableNames == null || tableNames.isEmpty()) {
            throw new ValidationException("tableNames", "数据采集: tableNames 不能为空");
        }
        List<Map<String, Object>> tasks = new ArrayList<>();
        for (String table : tableNames) {
            String validTable = validateTable(table);
            PipelineDefinition def = createOrGetIngestDefinition(datasourceId, validTable);
            String taskId;
            try {
                TaskDescription desc = new TaskDescription();
                desc.setTaskType("PIPELINE");
                desc.setTaskName("Ingest-" + datasourceId + "-" + validTable);
                Map<String, Object> params = new LinkedHashMap<>();
                params.put("definitionId", def.getId());
                desc.setParameters(params);
                desc.setAsync(async);
                taskId = taskManagementService.submitTask(desc);
                taskManagementService.executeTask(taskId);
            } catch (ITaskManagementService.TaskManagementException e) {
                log.error("数据采集任务提交失败: ds={}, table={}, err={}", datasourceId, validTable, e.getMessage(), e);
                throw new BusinessException("数据采集任务提交失败: " + validTable + " → " + e.getMessage());
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("table", validTable);
            item.put("definitionId", def.getId());
            item.put("taskId", taskId);
            item.put("status", "SUBMITTED");
            tasks.add(item);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("datasourceId", ds.getDatasourceId());
        result.put("datasourceName", ds.getDatasourceName());
        result.put("submitted", !tasks.isEmpty());
        result.put("taskCount", tasks.size());
        result.put("tasks", tasks);
        return result;
    }

    /**
     * 查询采集任务状态（委托 runtime-task）。
     */
    public Map<String, Object> getTaskStatus(String taskId) {
        TaskStatus status;
        try {
            status = taskManagementService.getTaskStatus(taskId);
        } catch (ITaskManagementService.TaskManagementException e) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("taskId", taskId);
            err.put("available", false);
            err.put("errorMessage", e.getMessage());
            return err;
        }
        Map<String, Object> r = new LinkedHashMap<>();
        if (status == null) {
            r.put("taskId", taskId);
            r.put("available", false);
            return r;
        }
        r.put("taskId", taskId);
        r.put("available", true);
        r.put("status", status.getStatus() != null ? status.getStatus().name() : "UNKNOWN");
        r.put("progress", status.getProgress());
        r.put("result", status.getResult());
        r.put("errorMessage", status.getErrorMessage());
        r.put("startTime", status.getStartTime());
        r.put("endTime", status.getEndTime());
        return r;
    }

    /**
     * 保存定时采集策略到 td_datasource.metadata_config JSON（复用现有字段，无新表）。
     * cron 为空 → 停用定时采集。
     *
     * @return 保存后的 ingest 段
     */
    public Map<String, Object> saveSchedule(String datasourceId, List<String> tableNames, String cron) {
        DataSourceEntity ds = requireDatasource(datasourceId);
        List<String> validTables = tableNames == null ? List.of()
                : tableNames.stream().map(this::validateTable).toList();

        Map<String, Object> cfg = parseMetadataConfig(ds.getMetadataConfig());
        boolean enabled = cron != null && !cron.isBlank();
        Map<String, Object> ingest = new LinkedHashMap<>();
        ingest.put("enabled", enabled);
        if (enabled) {
            ingest.put("cron", cron.trim());
        } else {
            ingest.put("cron", "");
        }
        ingest.put("tables", validTables);
        ingest.put("updatedAt", LocalDateTime.now().toString());
        cfg.put("ingest", ingest);
        try {
            dataSourceService.updateMetadataConfig(datasourceId, MAPPER.writeValueAsString(cfg));
        } catch (Exception e) {
            throw new BusinessException("保存定时采集策略失败: " + e.getMessage());
        }
        log.info("数据采集定时策略已保存: ds={}, tables={}, cron={}, enabled={}",
                datasourceId, validTables, cron, enabled);
        return ingest;
    }

    /**
     * 读取定时采集策略。
     */
    public Map<String, Object> getSchedule(String datasourceId) {
        DataSourceEntity ds = requireDatasource(datasourceId);
        Map<String, Object> cfg = parseMetadataConfig(ds.getMetadataConfig());
        @SuppressWarnings("unchecked")
        Map<String, Object> ingest = cfg.get("ingest") instanceof Map<?, ?>
                ? (Map<String, Object>) cfg.get("ingest") : new LinkedHashMap<>();
        ingest.putIfAbsent("enabled", false);
        ingest.putIfAbsent("cron", "");
        ingest.putIfAbsent("tables", List.of());
        return ingest;
    }

    /**
     * 可用目标数据湖（MINIO 类型数据源，近源库默认目标）。
     */
    public List<Map<String, Object>> listTargets() {
        List<Map<String, Object>> targets = new ArrayList<>();
        for (DataSourceEntity ds : dataSourceService.listAll()) {
            if (ds.getDatasourceType() != null && "MINIO".equalsIgnoreCase(ds.getDatasourceType())) {
                Map<String, Object> t = new LinkedHashMap<>();
                t.put("datasourceId", ds.getDatasourceId());
                t.put("name", ds.getDatasourceName());
                t.put("type", ds.getDatasourceType());
                targets.add(t);
            }
        }
        return targets;
    }

    /**
     * 更新数据源 metadata_config JSON（供定时调度器更新 ingest.lastRunAt）。
     */
    public void updateIngestConfig(String datasourceId, String json) {
        dataSourceService.updateMetadataConfig(datasourceId, json);
    }

    // ── 内部 ──────────────────────────────────────────

    /** 创建或复用采集型管道定义（按固定 name 去重，避免重复建定义）。 */
    private PipelineDefinition createOrGetIngestDefinition(String datasourceId, String table) {
        String defName = INGEST_DEF_PREFIX + datasourceId + "-" + table;
        for (PipelineDefinition def : pipelineService.listDefinitions()) {
            if (defName.equals(def.getName()) && def.getStatus() != null
                    && !"ARCHIVED".equalsIgnoreCase(def.getStatus())) {
                return def;
            }
        }
        return pipelineService.createDefinition(buildIngestDefinition(datasourceId, table, defName));
    }

    /** 构造采集型管道定义：SOURCE_JDBC → SINK_MINIO。 */
    private PipelineSaveDTO buildIngestDefinition(String datasourceId, String table, String defName) {
        PipelineSaveDTO dto = new PipelineSaveDTO();
        dto.setName(defName);
        dto.setDescription("数据采集: 源 " + datasourceId + " 表 " + table + " → 数据湖 MinIO 近源库");

        PipelineSaveDTO.NodeSpec source = new PipelineSaveDTO.NodeSpec();
        source.setNodeId("src_" + table);
        source.setType("SOURCE_JDBC");
        Map<String, Object> sourceConfig = new LinkedHashMap<>();
        sourceConfig.put("datasourceId", datasourceId);
        sourceConfig.put("sql", "SELECT * FROM " + table);
        sourceConfig.put("fetchSize", 1000);
        source.setConfig(sourceConfig);
        source.setPositionX(100);
        source.setPositionY(100);

        PipelineSaveDTO.NodeSpec sink = new PipelineSaveDTO.NodeSpec();
        sink.setNodeId("sink_" + table);
        sink.setType("SINK_MINIO");
        // 近源层对象 key 由 LakeObjectKeys 按分层规范 §三 组装：
        // raw/structured/{source}/{table}/{dw.lake.partition_by}=YYYY-MM-DD/{table}_{ts}.csv
        Map<String, Object> sinkConfig = new LinkedHashMap<>();
        sinkConfig.put("table", table);
        sinkConfig.put("format", "csv");
        sinkConfig.put("objectName", LakeObjectKeys.structuredObjectKey(
                datasourceId, table, resolvePartitionField(), "csv", null));
        sink.setConfig(sinkConfig);
        sink.setPositionX(400);
        sink.setPositionY(100);

        PipelineSaveDTO.EdgeSpec edge = new PipelineSaveDTO.EdgeSpec();
        edge.setFrom(source.getNodeId());
        edge.setTo(sink.getNodeId());
        edge.setLabel("采集");

        dto.setNodes(List.of(source, sink));
        dto.setEdges(List.of(edge));
        return dto;
    }

    /** 解析 metadata_config JSON 为 Map（空/非法 → 空 Map）。 */
    private Map<String, Object> parseMetadataConfig(String json) {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return MAPPER.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {
            });
        } catch (Exception e) {
            log.warn("metadata_config 解析失败，按空配置处理: {}", e.getMessage());
            return new LinkedHashMap<>();
        }
    }

    /** 表名白名单校验。 */
    private String validateTable(String table) {
        if (table == null || !table.matches(TABLE_PATTERN)) {
            throw new ValidationException("table", "非法表名: " + table);
        }
        return table;
    }

    private DataSourceEntity requireDatasource(String datasourceId) {
        DataSourceEntity ds = dataSourceService.getById(datasourceId);
        if (ds == null) {
            throw new BusinessException("数据源不存在: " + datasourceId);
        }
        return ds;
    }
}
