package com.chinacreator.gzcm.engine.data.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.sysman.config.service.impl.SysConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 数据工作台配置 API — 管理数据引擎相关 sys_config 配置。
 * <p>
 * 配置位于 config_group='data-engine'，通过 config_key 前缀进一步分子组：
 * execution / lake / storage / pipeline / quality / lineage / general。
 */
@RestController
@RequestMapping("/api/v1/engine/data/settings")
public class DataEngineConfigController {

    private static final Logger log = LoggerFactory.getLogger(DataEngineConfigController.class);

    @Autowired
    private SysConfigService sysConfigService;

    /** 默认值常量：config_key → default_value */
    private static final Map<String, String> DEFAULTS = new LinkedHashMap<>();
    static {
        // 执行引擎
        DEFAULTS.put("dw.execution.mode",              "memory");
        DEFAULTS.put("dw.execution.memory.max_rows",   "100000");
        DEFAULTS.put("dw.execution.memory.threads",    "4");
        DEFAULTS.put("dw.execution.doris.host",        "localhost");
        DEFAULTS.put("dw.execution.doris.port",        "9030");
        DEFAULTS.put("dw.execution.doris.user",        "root");
        DEFAULTS.put("dw.execution.doris.database",    "ecos_dw");
        DEFAULTS.put("dw.execution.doris.batch_size",  "10000");
        DEFAULTS.put("dw.execution.timeout",           "600");
        // 数据湖
        DEFAULTS.put("dw.lake.enabled",                "false");
        DEFAULTS.put("dw.lake.datasource_id",          "");
        DEFAULTS.put("dw.lake.storage_format",         "parquet");
        DEFAULTS.put("dw.lake.partition_by",           "dt");
        DEFAULTS.put("dw.lake.retention_days",         "90");
        // 对象存储
        DEFAULTS.put("dw.storage.type",                "minio");
        DEFAULTS.put("dw.storage.minio.endpoint",      "http://localhost:9000");
        DEFAULTS.put("dw.storage.minio.access_key",    "minioadmin");
        DEFAULTS.put("dw.storage.minio.secret_key",    "minioadmin");
        DEFAULTS.put("dw.storage.minio.bucket",        "ecos-data");
        DEFAULTS.put("dw.storage.minio.region",        "us-east-1");
        DEFAULTS.put("dw.storage.minio.ssl",           "false");
        // 管道
        DEFAULTS.put("dw.pipeline.max_steps",          "20");
        DEFAULTS.put("dw.pipeline.parallel_steps",     "4");
        DEFAULTS.put("dw.pipeline.default_chunk_size", "10000");
        DEFAULTS.put("dw.pipeline.temp_table_prefix",  "ecos_tmp_");
        DEFAULTS.put("dw.pipeline.temp_table_ttl_hours","24");
        DEFAULTS.put("dw.pipeline.retry_max",          "3");
        DEFAULTS.put("dw.pipeline.retry_backoff_ms",   "5000");
        // 数据质量
        DEFAULTS.put("dw.quality.sample_rate",         "1.0");
        DEFAULTS.put("dw.quality.sample_max_rows",     "1000000");
        DEFAULTS.put("dw.quality.stale_threshold_hours","24");
        DEFAULTS.put("dw.quality.default_alert_score", "80");
        DEFAULTS.put("dw.quality.concurrent_checks",   "2");
        DEFAULTS.put("dw.quality.check_timeout",       "300");
        // 血缘
        DEFAULTS.put("dw.lineage.enabled",             "true");
        DEFAULTS.put("dw.lineage.parser",              "sql");
        DEFAULTS.put("dw.lineage.max_depth",           "10");
        DEFAULTS.put("dw.lineage.cache_ttl_minutes",   "30");
        DEFAULTS.put("dw.lineage.neo4j_enabled",       "false");
        // 数据引擎自身
        DEFAULTS.put("dw.sync.batch_size",             "5000");
        DEFAULTS.put("dw.sync.max_retries",            "3");
        DEFAULTS.put("dw.query.max_rows",              "10000");
        DEFAULTS.put("dw.query.timeout",               "30");
        DEFAULTS.put("dw.cache.ttl_seconds",           "300");
        DEFAULTS.put("dw.engine.auto_start",           "true");
        // Pipeline 2.0 新增
        DEFAULTS.put("dw.pipeline.log_storage",        "db");
        DEFAULTS.put("dw.pipeline.log_retention_days", "30");
        DEFAULTS.put("dw.pipeline.resume_enabled",     "true");
        DEFAULTS.put("dw.pipeline.resume_max_retries", "3");
        DEFAULTS.put("dw.pipeline.keep_history",       "false");
        DEFAULTS.put("dw.pipeline.history_max_versions","10");
        DEFAULTS.put("dw.pipeline.preview_mode",       "sample");
        DEFAULTS.put("dw.pipeline.preview_max_rows",   "1000");
        DEFAULTS.put("dw.pipeline.alert_on_failure",   "true");
        DEFAULTS.put("dw.pipeline.alert_on_success",   "false");
        DEFAULTS.put("dw.pipeline.template_repo_url",  "");
        DEFAULTS.put("dw.pipeline.monaco_theme",       "vs-dark");
        DEFAULTS.put("dw.notify.channel",              "internal");
        // Copilot
        DEFAULTS.put("dw.copilot.enabled",             "false");
        DEFAULTS.put("dw.copilot.provider",            "openai");
        DEFAULTS.put("dw.copilot.model",               "gpt-4o");
        DEFAULTS.put("dw.copilot.temperature",         "0.2");
        DEFAULTS.put("dw.copilot.max_tokens",          "4096");
        // 迁移项
        DEFAULTS.put("dw.datasource.page_size",        "20");
        DEFAULTS.put("dw.datasource.conn_timeout",     "30000");
        DEFAULTS.put("dw.metadata.collect_timeout",    "60");
        DEFAULTS.put("dw.catalog.search_limit",        "500");
    }

    /** config_key 前缀 → 显示子组名 */
    private static final Map<String, String> PREFIX_TO_SUBGROUP = new LinkedHashMap<>();
    static {
        PREFIX_TO_SUBGROUP.put("dw.execution.", "execution");
        PREFIX_TO_SUBGROUP.put("dw.lake.",      "lake");
        PREFIX_TO_SUBGROUP.put("dw.storage.",   "storage");
        PREFIX_TO_SUBGROUP.put("dw.pipeline.",  "pipeline");
        PREFIX_TO_SUBGROUP.put("dw.quality.",   "quality");
        PREFIX_TO_SUBGROUP.put("dw.lineage.",   "lineage");
        // remaining dw.* keys fall into "general"
    }

    // ── 端点 ──────────────────────────────────────────────

    /**
     * GET /api/v1/engine/data/config
     * 返回所有数据引擎配置，按子组分组。
     */
    @GetMapping
    public ApiResponse<Map<String, Map<String, String>>> getAllConfig() {
        try {
            List<Map<String, Object>> rows = sysConfigService.getByGroup("data-engine");
            if (rows.isEmpty()) {
                // lazy init — 首次访问自动插入默认配置
                initDefaultRows();
                rows = sysConfigService.getByGroup("data-engine");
            }
            return ApiResponse.success(groupBySubGroup(rows));
        } catch (Exception e) {
            log.error("获取数据引擎配置失败", e);
            return ApiResponse.internalError("获取配置失败: " + e.getMessage());
        }
    }

    /** 插入默认配置行 (SysConfigService.ensureDefaultConfigs 可能在 schema 就绪前执行) */
    private void initDefaultRows() {
        try {
            for (Map.Entry<String, String> e : DEFAULTS.entrySet()) {
                sysConfigService.upsertValue(e.getKey(), e.getValue(), "data-engine", "string", "");
            }
            log.info("Data engine config defaults initialized, {} items", DEFAULTS.size());
        } catch (Exception ex) {
            log.warn("initDefaultRows partial failure: {}", ex.getMessage());
        }
    }

    /**
     * GET /api/v1/engine/data/settings/defaults
     * 返回所有数据引擎配置的默认值。
     */
    @GetMapping("/defaults")
    public ApiResponse<Map<String, String>> getDefaults() {
        return ApiResponse.success(new LinkedHashMap<>(DEFAULTS));
    }

    /**
     * GET /api/v1/engine/data/settings/{group}
     * 返回指定 config_group 的配置，按子组分组。
     */
    @GetMapping("/{group}")
    public ApiResponse<Map<String, Map<String, String>>> getConfigByGroup(@PathVariable String group) {
        try {
            List<Map<String, Object>> rows = sysConfigService.getByGroup(group);
            return ApiResponse.success(groupBySubGroup(rows));
        } catch (Exception e) {
            log.error("获取配置组 {} 失败", group, e);
            return ApiResponse.internalError("获取配置失败: " + e.getMessage());
        }
    }

    /**
     * PUT /api/v1/engine/data/config
     * 批量更新配置 [{config_key, config_value}, ...]
     */
    @PutMapping
    public ApiResponse<Map<String, Object>> updateConfig(@RequestBody List<Map<String, String>> updates) {
        try {
            int count = sysConfigService.updateBatch(updates);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("updated", count);
            result.put("total", updates.size());
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("批量更新配置失败", e);
            return ApiResponse.internalError("更新失败: " + e.getMessage());
        }
    }

    /**
     * POST /api/v1/engine/data/config/refresh
     * 刷新 sys_config 缓存。
     */
    @PostMapping("/refresh")
    public ApiResponse<Map<String, Object>> refreshCache() {
        try {
            sysConfigService.refreshCache();
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("refreshed", true);
            result.put("cache_size", sysConfigService.cacheSize());
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("刷新配置缓存失败", e);
            return ApiResponse.internalError("刷新失败: " + e.getMessage());
        }
    }


    // ── 私有方法 ──────────────────────────────────────────

    /**
     * 将配置行按 config_key 前缀分子组。
     * 例如 dw.execution.mode → execution: {mode: "memory"}
     */
    private Map<String, Map<String, String>> groupBySubGroup(List<Map<String, Object>> rows) {
        Map<String, Map<String, String>> groups = new LinkedHashMap<>();

        for (Map<String, Object> row : rows) {
            String key = (String) row.get("config_key");
            String value = (String) row.get("config_value");
            if (key == null) continue;

            // 确定子组名
            String subGroup = "general";
            String shortKey = key;
            for (Map.Entry<String, String> e : PREFIX_TO_SUBGROUP.entrySet()) {
                if (key.startsWith(e.getKey())) {
                    subGroup = e.getValue();
                    shortKey = key.substring(e.getKey().length());
                    break;
                }
            }
            // 对于 general 组，也尝试从 dw.* 提取第二段
            if ("general".equals(subGroup) && key.startsWith("dw.")) {
                shortKey = key.substring(3); // strip "dw."
            }

            groups.computeIfAbsent(subGroup, k -> new LinkedHashMap<>())
                  .put(shortKey, value != null ? value : "");
        }
        return groups;
    }
}
