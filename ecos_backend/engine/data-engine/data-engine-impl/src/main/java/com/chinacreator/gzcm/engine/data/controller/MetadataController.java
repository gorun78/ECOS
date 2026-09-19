package com.chinacreator.gzcm.engine.data.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.data.model.DataField;
import com.chinacreator.gzcm.common.data.model.DataResource;
import com.chinacreator.gzcm.common.exception.ValidationException;
import com.chinacreator.gzcm.engine.data.MetadataService;
import com.chinacreator.gzcm.engine.data.dto.DataResourceRegisterDTO;
import com.chinacreator.gzcm.engine.data.dto.DataResourceVO;
import com.chinacreator.gzcm.engine.data.service.DataLakeResourceService;
import com.chinacreator.gzcm.engine.data.datasource.entity.DataSourceEntity;
import com.chinacreator.gzcm.engine.data.DataSourceService;
import com.chinacreator.gzcm.engine.data.service.MetadataCollectionService;
import com.chinacreator.gzcm.engine.data.service.MetadataRowCountService;
import com.chinacreator.gzcm.engine.data.metadata.AutoCollectScheduler;
import com.chinacreator.gzcm.engine.data.metadata.MetadataAsyncTrigger;
import com.chinacreator.gzcm.engine.data.metadata.MetadataCollectGitArchive;
import com.chinacreator.gzcm.engine.data.metadata.MetadataCollectTaskParser;
import com.chinacreator.gzcm.engine.data.metadata.MetadataStrategyConfig;
import com.chinacreator.gzcm.engine.data.metadata.MetadataTaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 元数据采集 + 数据表目录接口（data-engine）。
 *
 * 路由前缀: /datanet/metadata
 *
 * PMO-37 新增端点（既有端点路径签名不变）：
 *   POST  /datanet/metadata/collect-async/{datasourceId}   手动触发异步采集
 *   GET   /datanet/metadata/collect-status/{taskId}        查任务引擎状态
 *   GET   /datanet/metadata/catalog/{datasourceId}         数据表目录（分页 + 行数）
 *   GET   /datanet/metadata/collect-logs/{datasourceId}    采集审计日志
 *   GET   /datanet/metadata/config                         策略配置常量
 *   POST  /datanet/metadata/collect-sync/{datasourceId}    同步立即采集（前端轮询 /collect-status 获取实时进度）
 *
 * 既有端点（签名不变）：
 *   POST  /datanet/metadata/collect/{datasourceId}   （保持同步采集语义）
 *   GET   /datanet/metadata/resources/{datasourceId}
 *   GET   /datanet/metadata/resources/all
 */
@RestController
@RequestMapping({"/api/datanet/metadata", "/api/v1/datanet/metadata"})
public class MetadataController {

    private static final Logger log = LoggerFactory.getLogger(MetadataController.class);

    private final MetadataCollectionService collectionService;
    private final MetadataTaskService taskService;
    private final AutoCollectScheduler scheduler;
    private final MetadataRowCountService rowCountService;
    private final DataSourceService dataSourceService;
    private final MetadataAsyncTrigger asyncTrigger;
    private final MetadataCollectGitArchive gitArchive;
    private final MetadataService metadataService;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;

    /** 数据湖分层资源登记服务（B5-1：通用资源登记，供知识工作台登记解析文本）。 */
    private final DataLakeResourceService dataLakeResourceService;

    private static final com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>> MAP_TYPE =
            new com.fasterxml.jackson.core.type.TypeReference<>() {};

    public MetadataController(MetadataCollectionService collectionService,
                              MetadataTaskService taskService,
                              AutoCollectScheduler scheduler,
                              MetadataRowCountService rowCountService,
                              DataSourceService dataSourceService,
                              MetadataAsyncTrigger asyncTrigger,
                              MetadataCollectGitArchive gitArchive,
                              MetadataService metadataService,
                              org.springframework.jdbc.core.JdbcTemplate jdbc,
                              DataLakeResourceService dataLakeResourceService) {
        this.collectionService = collectionService;
        this.taskService = taskService;
        this.scheduler = scheduler;
        this.rowCountService = rowCountService;
        this.dataSourceService = dataSourceService;
        this.asyncTrigger = asyncTrigger;
        this.gitArchive = gitArchive;
        this.metadataService = metadataService;
        this.jdbc = jdbc;
        this.dataLakeResourceService = dataLakeResourceService;
    }

    // ===== 既有端点（签名不变） =====

    @PostMapping("/collect/{datasourceId}")
    public Map<String, Object> collect(@PathVariable String datasourceId) {
        // 原有同步采集语义保持不变
        Map<String, Object> r = collectionService.collect(datasourceId);
        if (r != null) {
            scheduler.markSuccess(datasourceId);
        } else {
            scheduler.markFailure(datasourceId);
        }
        return r;
    }

    @GetMapping("/resources/{datasourceId}")
    public List<DataResource> getResources(@PathVariable String datasourceId) {
        return collectionService.getResources(datasourceId);
    }

    @GetMapping("/resources/all")
    public List<Map<String, Object>> getAllResources() {
        return collectionService.getAllResources();
    }

    /**
     * 通用数据资源登记（B5-1，方案 §5.3「知识→数据 登记数据资源」）。
     *
     * <p>A3 过渡态下知识工作台据此把「解析文本」登记为 {@code layer=CURATED} 资源；
     * 强制校验 layer 合法性枚举与 zone 合法性矩阵（非 RAW 层 zone 必须为空）。
     * 按 source_path 幂等（已存在则更新，不产生重复行）。
     *
     * @param req 登记入参（强类型）
     * @return 登记结果（ApiResponse&lt;DataResourceVO&gt;）
     */
    @PostMapping("/resources")
    public ApiResponse<DataResourceVO> registerResource(@RequestBody DataResourceRegisterDTO req) {
        try {
            return ApiResponse.success(dataLakeResourceService.registerResource(req));
        } catch (ValidationException e) {
            return ApiResponse.badRequest(e.getMessage());
        }
    }

    @GetMapping("/preview/{resourceId}")
    public Map<String, Object> preview(@PathVariable String resourceId,
                                       @RequestParam(defaultValue = "50") int limit) {
        return collectionService.preview(resourceId, limit);
    }

    // ===== 表字段元数据（供"数据表下拉展开"表格展示 名称/类型/长度/主键）=====

    /**
     * 查询表的字段清单（强类型 DataField，含 dataType/dataLength/primaryKey/nullable）。
     * 走 MetadataService.getFields，读 td_data_field（采集时已落库），非实时查库。
     * 出参为 API 规范化列表 {@code {code:0, success:true, data: DataField[]}}。
     */
    @GetMapping("/fields/{resourceId}")
    public Map<String, Object> getFields(@PathVariable String resourceId) {
        List<DataField> fields;
        try {
            fields = metadataService.getFields(resourceId);
        } catch (Exception e) {
            log.warn("getFields 失败 resource={}: {}", resourceId, e.getMessage());
            fields = java.util.Collections.emptyList();
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("code", 0);
        r.put("success", true);
        r.put("message", "ok");
        r.put("data", fields);
        return r;
    }

    // ===== PMO-37 新增：手动触发异步采集 =====

    @PostMapping("/collect-async/{datasourceId}")
    public Map<String, Object> collectAsync(@PathVariable String datasourceId) {
        String taskId = null;
        try {
            taskId = asyncTrigger.submitAsync(datasourceId);
        } catch (Exception e) {
            log.warn("collect-async 提交失败 datasource={}: {}", datasourceId, e.getMessage());
            scheduler.markFailure(datasourceId);
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("submitted", taskId != null);
        if (taskId != null) {
            r.put("taskId", taskId);
            r.put("status", "SUBMITTED");
            r.put("note", "任务已提交任务引擎，轮询 /collect-status/{taskId} 或 /catalog/{id} 查看采集结果");
        } else {
            r.put("status", "FAILED");
            r.put("note", "任务提交失败（任务引擎不可用），请稍后重试或检查 data-engine 日志");
        }
        r.put("datasourceId", datasourceId);
        r.put("taskType", MetadataCollectTaskParser.TASK_TYPE);
        return r;
    }

    // ===== 历史版本比较：同步"立即采集"按钮 — 异步任务 + 前端轮询进度 =====
    //
    // 行为：
    //   1) submit + parse 立即执行（<200ms 返回 taskId）；
    //   2) executeTask 在后台线程池异步跑（与定时采集走同一 ITaskExecutor + ITaskStatusCallback 链路）；
    //   3) 前端用 collect-status/{taskId} 拉取 TaskStatus（含 progress/statusMessage/processedRecords/totalRecords）实时渲染；
    //   4) 完成后 result JSON 统一存 TaskStatus.result，采集任务中心亦能正常展示。

    @PostMapping("/collect-sync/{datasourceId}")
    public Map<String, Object> collectSync(@PathVariable String datasourceId) {
        String taskId = null;
        Map<String, Object> r = new LinkedHashMap<>();
        try {
            taskId = asyncTrigger.submitAsync(datasourceId);
        } catch (Exception e) {
            log.warn("collect-sync 提交失败 datasource={}: {}", datasourceId, e.getMessage());
            scheduler.markFailure(datasourceId);
        }
        r.put("submitted", taskId != null);
        if (taskId != null) {
            r.put("taskId", taskId);
            r.put("status", "SUBMITTED");
            r.put("mode", "sync-ui");
            r.put("note", "任务已提交后端任务引擎，前端请轮询 /collect-status/{taskId} 观察进度反馈");
        } else {
            r.put("status", "FAILED");
            r.put("note", "任务提交失败（任务引擎不可用），请稍后重试或检查 data-engine 日志");
        }
        r.put("datasourceId", datasourceId);
        r.put("taskType", MetadataCollectTaskParser.TASK_TYPE);
        return r;
    }

    // ===== PMO-37 新增：查任务引擎状态 =====

    @GetMapping("/collect-status/{taskId}")
    public Map<String, Object> collectStatus(@PathVariable String taskId) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("taskId", taskId);
        var status = taskService.queryStatus(taskId);
        if (status == null) {
            r.put("available", false);
            return r;
        }
        r.put("available", true);
        r.put("status", status.getStatus().name());
        r.put("progress", status.getProgress());
        r.put("result", status.getResult());
        r.put("errorMessage", status.getErrorMessage());
        r.put("startTime", status.getStartTime());
        r.put("endTime", status.getEndTime());
        return r;
    }

    // ===== PMO-37 新增：数据表目录（分页 + 行数） =====

    @GetMapping("/catalog/{datasourceId}")
    public Map<String, Object> catalog(@PathVariable String datasourceId,
                                       @RequestParam(defaultValue = "1") int pageNum,
                                       @RequestParam(defaultValue = "10") int pageSize) {
        int pn = Math.max(1, pageNum);
        int ps = Math.max(1, Math.min(pageSize, 100));

        DataSourceEntity ds = dataSourceService.getById(datasourceId);
        if (ds == null) {
            return error("数据源不存在: " + datasourceId);
        }

        List<Object> items;
        try {
            items = collectionService.getResourcePages(datasourceId, pn, ps);
        } catch (Exception e) {
            log.warn("catalog 查询失败 {}: {}", datasourceId, e.getMessage());
            return error("目录查询失败: " + e.getMessage());
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("items", items.get(0));
        data.put("total", ((Number) items.get(1)).longValue());

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("datasourceId", ds.getDatasourceId());
        meta.put("datasourceName", ds.getDatasourceName());
        meta.put("datasourceType", ds.getDatasourceType());
        meta.put("pageNum", pn);
        meta.put("pageSize", ps);
        data.put("meta", meta);

        // 策略 + 最近采集时间
        MetadataStrategyConfig cfg = MetadataStrategyConfig.fromJson(ds.getMetadataConfig());
        data.put("strategy", cfg.getStrategy());
        data.put("countMethod", cfg.getCountMethod());
        Timestamp lct = rowCountService.getLastCollectTime(datasourceId);
        data.put("lastCollectTime", lct);
        data.put("collected", lct != null);

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("code", 0);
        r.put("message", "ok");
        r.put("data", data);
        return r;
    }

    // ===== PMO-37 新增：采集审计日志 =====

    @GetMapping("/collect-logs/{datasourceId}")
    public Map<String, Object> collectLogs(@PathVariable String datasourceId,
                                           @RequestParam(defaultValue = "10") int limit) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("logs", rowCountService.recentLogs(datasourceId, limit));
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("code", 0);
        r.put("message", "ok");
        r.put("data", data);
        return r;
    }

    // ===== 新增：采集差异记录 =====

    @GetMapping("/collect-diff/{datasourceId}")
    public Map<String, Object> collectDiff(@PathVariable String datasourceId,
                                           @RequestParam(defaultValue = "5") int limit) {
        Map<String, Object> data = new LinkedHashMap<>();
        try {
            List<Map<String, Object>> logs = jdbc.queryForList(
                    "SELECT result, created_at, task_id FROM td_metadata_collect_log " +
                    "WHERE datasource_id = ? AND result IS NOT NULL ORDER BY created_at DESC LIMIT ?",
                    datasourceId, Math.max(1, limit));
            // 解析 result JSON 提取 diffSummary/diffMarkdown
            List<Map<String, Object>> diffs = new java.util.ArrayList<>();
            for (Map<String, Object> row : logs) {
                String resultJson = (String) row.get("result");
                if (resultJson == null || resultJson.isEmpty()) continue;
                try {
                    Map<String, Object> r = new com.fasterxml.jackson.databind.ObjectMapper()
                            .readValue(resultJson, MAP_TYPE);
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("collectedAt", row.get("created_at"));
                    item.put("taskId", row.get("task_id"));
                    item.put("diffSummary", r.get("diffSummary"));
                    item.put("diffMarkdown", r.get("diffMarkdown"));
                    item.put("gitCommit", r.get("gitCommit"));
                    item.put("tablesTotal", r.get("tablesTotal"));
                    item.put("tablesOk", r.get("tablesOk"));
                    item.put("tablesFailed", r.get("tablesFailed"));
                    diffs.add(item);
                } catch (Exception e) {
                    log.debug("解析 result JSON 失败: {}", e.getMessage());
                }
            }
            data.put("diffs", diffs);
        } catch (Exception e) {
            log.warn("collect-diff 查询失败: {}", e.getMessage());
            return error("差异记录查询失败: " + e.getMessage());
        }
        data.put("datasourceId", datasourceId);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("code", 0);
        r.put("message", "ok");
        r.put("data", data);
        return r;
    }

    // ===== 新增：历史版本比较（数据表目录 Git 版本对比） =====

    /**
     * GET /datanet/metadata/version-history/{datasourceId}
     * 列出数据源的全部 Git 历史版本 + 当前版本（供前端版本选择对话框）。
     */
    @GetMapping("/version-history/{datasourceId}")
    public Map<String, Object> versionHistory(@PathVariable String datasourceId) {
        try {
            Map<String, Object> data = gitArchive.listHistoryVersions(datasourceId);
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("code", 0);
            r.put("message", "ok");
            r.put("data", data);
            return r;
        } catch (IllegalArgumentException e) {
            return error(e.getMessage());
        } catch (Exception e) {
            log.warn("version-history 查询失败 ds={}: {}", datasourceId, e.getMessage());
            return error("历史版本查询失败: " + e.getMessage());
        }
    }

    /**
     * GET /datanet/metadata/version-diff/{datasourceId}?version={yyyyMMdd_HHmmss}
     * 指定历史版本与当前版本的结构化字段级对比（内存 diff，大数据量快速）。
     */
    @GetMapping("/version-diff/{datasourceId}")
    public Map<String, Object> versionDiff(@PathVariable String datasourceId,
                                           @RequestParam String version) {
        try {
            Map<String, Object> data = gitArchive.compareWithHistory(datasourceId, version);
            // 提交说明：优先取采集审计日志中的 gitCommit，回退到固定格式构造
            enrichCommitMessage(datasourceId, data);
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("code", 0);
            r.put("message", "ok");
            r.put("data", data);
            return r;
        } catch (IllegalArgumentException e) {
            return error(e.getMessage());
        } catch (Exception e) {
            log.warn("version-diff 查询失败 ds={} version={}: {}", datasourceId, version, e.getMessage());
            return error("版本比较失败: " + e.getMessage());
        }
    }

    /** 为 diff 结果补充提交说明（查 td_metadata_collect_log 的 gitCommit，回退固定格式） */
    private void enrichCommitMessage(String datasourceId, Map<String, Object> data) {
        String currentCollectedAt = String.valueOf(data.getOrDefault("currentCollectedAt", ""));
        String commitMessage = null;
        // 快照 collectedAt 为 LocalDateTime.toString()（ISO 格式），解析为 Timestamp 后传参
        java.sql.Timestamp collectedTs = null;
        try {
            collectedTs = java.sql.Timestamp.valueOf(java.time.LocalDateTime.parse(currentCollectedAt));
        } catch (Exception e) {
            log.debug("解析 currentCollectedAt 失败: {}", currentCollectedAt);
        }
        if (collectedTs != null) {
            try {
                List<Map<String, Object>> logs = jdbc.queryForList(
                        "SELECT result FROM td_metadata_collect_log " +
                        "WHERE datasource_id = ? AND created_at <= ? AND result LIKE '%gitCommit%' " +
                        "ORDER BY created_at DESC LIMIT 1",
                        datasourceId, collectedTs);
                if (!logs.isEmpty()) {
                    String resultJson = (String) logs.get(0).get("result");
                    if (resultJson != null && !resultJson.isEmpty()) {
                        Map<String, Object> parsed = new com.fasterxml.jackson.databind.ObjectMapper()
                                .readValue(resultJson, MAP_TYPE);
                        Object gc = parsed.get("gitCommit");
                        if (gc != null && !String.valueOf(gc).isEmpty()) {
                            commitMessage = String.valueOf(gc);
                        }
                    }
                }
            } catch (Exception e) {
                log.debug("查询 gitCommit 失败: {}", e.getMessage());
            }
        }
        if (commitMessage == null) {
            commitMessage = "metadata-collect: " + datasourceId + " " + currentCollectedAt;
        }
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) data.get("rows");
        if (rows != null) {
            for (Map<String, Object> row : rows) {
                row.put("commitMessage", commitMessage);
            }
        }
    }

    // ===== PMO-37 新增：策略配置常量（前端下拉选项来源） =====

    @GetMapping("/config")
    public Map<String, Object> config() {
        Map<String, Object> strategies = new LinkedHashMap<>();
        strategies.put("ON_SAVE", "保存后立即采集");
        strategies.put("ON_SCHEDULE", "定时采集（cron）");
        strategies.put("MANUAL", "手动触发");
        strategies.put("ON_DEMAND", "按需触发");

        Map<String, Object> countMethods = new LinkedHashMap<>();
        countMethods.put("EXACT", "精确统计（SELECT COUNT，小表）");
        countMethods.put("ESTIMATE", "估算统计（pg_stat，快）");
        countMethods.put("OFF", "不统计行数");

        Map<String, Object> defaults = new LinkedHashMap<>();
        defaults.put("strategy", MetadataStrategyConfig.STRATEGY_ON_SAVE);
        defaults.put("includeRowCount", true);
        defaults.put("countMethod", MetadataStrategyConfig.COUNT_ESTIMATE);
        defaults.put("cacheTtlMinutes", 5);
        defaults.put("onSourceEdit", true);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("strategies", strategies);
        data.put("countMethods", countMethods);
        data.put("defaults", defaults);

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("code", 0);
        r.put("message", "ok");
        r.put("data", data);
        return r;
    }

    // ===== P0-3 新增：保存数据源元数据策略配置 =====

    @PutMapping("/strategy/{datasourceId}")
    public Map<String, Object> saveStrategy(@PathVariable String datasourceId,
                                            @RequestBody Map<String, Object> body) {
        DataSourceEntity ds = dataSourceService.getById(datasourceId);
        if (ds == null) {
            return error("数据源不存在: " + datasourceId);
        }
        MetadataStrategyConfig cfg = MetadataStrategyConfig.fromJson(ds.getMetadataConfig());
        if (body.containsKey("strategy")) {
            String s = String.valueOf(body.get("strategy"));
            if (!s.equals(MetadataStrategyConfig.STRATEGY_ON_SAVE)
                    && !s.equals(MetadataStrategyConfig.STRATEGY_ON_SCHEDULE)
                    && !s.equals(MetadataStrategyConfig.STRATEGY_MANUAL)
                    && !s.equals(MetadataStrategyConfig.STRATEGY_ON_DEMAND)) {
                return error("未知策略: " + s);
            }
            cfg.setStrategy(s);
        }
        if (body.containsKey("countMethod")) {
            String cm = String.valueOf(body.get("countMethod"));
            if (!cm.equals(MetadataStrategyConfig.COUNT_EXACT)
                    && !cm.equals(MetadataStrategyConfig.COUNT_ESTIMATE)
                    && !cm.equals(MetadataStrategyConfig.COUNT_OFF)) {
                return error("未知行数统计方式: " + cm);
            }
            cfg.setCountMethod(cm);
        }
        if (body.containsKey("scheduleCron")) {
            cfg.setScheduleCron(body.get("scheduleCron") == null ? null : String.valueOf(body.get("scheduleCron")));
        }
        try {
            String json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(cfg);
            dataSourceService.updateMetadataConfig(datasourceId, json);
        } catch (Exception e) {
            log.warn("策略保存失败 datasource={}: {}", datasourceId, e.getMessage());
            return error("策略保存失败: " + e.getMessage());
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("code", 0);
        r.put("message", "ok");
        r.put("success", true);
        r.put("datasourceId", datasourceId);
        return r;
    }

    private Map<String, Object> error(String msg) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("code", -1);
        r.put("message", msg);
        return r;
    }
}
