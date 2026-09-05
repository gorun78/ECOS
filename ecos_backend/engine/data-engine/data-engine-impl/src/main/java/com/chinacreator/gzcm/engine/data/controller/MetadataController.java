package com.chinacreator.gzcm.engine.data.controller;

import com.chinacreator.gzcm.common.data.model.DataResource;
import com.chinacreator.gzcm.engine.data.datasource.entity.DataSourceEntity;
import com.chinacreator.gzcm.engine.data.DataSourceService;
import com.chinacreator.gzcm.engine.data.service.MetadataCollectionService;
import com.chinacreator.gzcm.engine.data.service.MetadataRowCountService;
import com.chinacreator.gzcm.engine.data.metadata.AutoCollectScheduler;
import com.chinacreator.gzcm.engine.data.metadata.MetadataAsyncTrigger;
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

    public MetadataController(MetadataCollectionService collectionService,
                              MetadataTaskService taskService,
                              AutoCollectScheduler scheduler,
                              MetadataRowCountService rowCountService,
                              DataSourceService dataSourceService,
                              MetadataAsyncTrigger asyncTrigger) {
        this.collectionService = collectionService;
        this.taskService = taskService;
        this.scheduler = scheduler;
        this.rowCountService = rowCountService;
        this.dataSourceService = dataSourceService;
        this.asyncTrigger = asyncTrigger;
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

    @GetMapping("/preview/{resourceId}")
    public Map<String, Object> preview(@PathVariable String resourceId,
                                       @RequestParam(defaultValue = "50") int limit) {
        return collectionService.preview(resourceId, limit);
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
