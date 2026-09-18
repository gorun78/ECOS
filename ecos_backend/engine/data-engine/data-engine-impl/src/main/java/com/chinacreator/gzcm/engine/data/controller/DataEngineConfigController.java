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
 * <p>配置位于 config_group='data-engine'，提供两套并行的分组视图（互不影响，config_key 不变）：
 * <ul>
 *   <li>{@code GET /settings} — 旧子组视图：execution / lake / storage / pipeline / quality /
 *       lineage / general，前端按 {@code dw.{子组}.{短键}} 反拼取值（务必保持稳定）</li>
 *   <li>{@code GET /settings/groups} — 数据湖分层分段视图：near-source（近源层）/ dw（DW 层）/
 *       semantic（语义层占位）/ global（全局），项保留完整 config_key，供分层配置面板使用</li>
 * </ul>
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
        // 数据表目录历史版本保留份数（Git 元数据存档，超限自动清理最旧版本）
        DEFAULTS.put("dw.metadata.history_versions",   "50");
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

    /** 分段顺序（UI 左侧导航顺序） */
    private static final List<String> SEGMENT_ORDER =
            List.of("near-source", "dw", "semantic", "global");

    /**
     * 分段用：config_key 前缀 → 子分区 id。
     * <p>与 {@link #PREFIX_TO_SUBGROUP} 相互独立 —— 后者服务于旧 {@code GET /settings} 的拼键逻辑
     * （前端 flattenConfig 依赖），本表服务于分层分段（{@code GET /settings/groups}），两者不得混用。
     */
    private static final Map<String, String> SEGMENT_PREFIX_TO_SUBGROUP = new LinkedHashMap<>();
    static {
        // ── 近源层：数据湖 / 对象存储 / 同步 / 数据源接入 ──
        SEGMENT_PREFIX_TO_SUBGROUP.put("dw.lake.",       "lake");
        SEGMENT_PREFIX_TO_SUBGROUP.put("dw.storage.",    "storage");
        SEGMENT_PREFIX_TO_SUBGROUP.put("dw.sync.",       "sync");
        SEGMENT_PREFIX_TO_SUBGROUP.put("dw.datasource.", "datasource");
        // ── DW 层：执行 / 管道 / 质量 / 血缘 / 查询 / 缓存 / 目录 / 元数据 ──
        SEGMENT_PREFIX_TO_SUBGROUP.put("dw.execution.",  "execution");
        SEGMENT_PREFIX_TO_SUBGROUP.put("dw.pipeline.",   "pipeline");
        SEGMENT_PREFIX_TO_SUBGROUP.put("dw.quality.",    "quality");
        SEGMENT_PREFIX_TO_SUBGROUP.put("dw.lineage.",    "lineage");
        SEGMENT_PREFIX_TO_SUBGROUP.put("dw.query.",      "query");
        SEGMENT_PREFIX_TO_SUBGROUP.put("dw.cache.",      "cache");
        SEGMENT_PREFIX_TO_SUBGROUP.put("dw.catalog.",    "catalog");
        SEGMENT_PREFIX_TO_SUBGROUP.put("dw.metadata.",   "metadata");
        // ── 全局：引擎 / 通知 / Copilot ──
        SEGMENT_PREFIX_TO_SUBGROUP.put("dw.engine.",     "engine");
        SEGMENT_PREFIX_TO_SUBGROUP.put("dw.notify.",     "notify");
        SEGMENT_PREFIX_TO_SUBGROUP.put("dw.copilot.",    "copilot");
    }

    /**
     * 精确 key → 子分区覆盖：把 {@code dw.pipeline.} 前缀下的 Pipeline 2.0 运行期配置
     * 细分到 {@code pipeline-advanced}，与基础管道参数（{@code pipeline}）分开展示。
     */
    private static final Map<String, String> KEY_OVERRIDE_SUBGROUP = new LinkedHashMap<>();
    static {
        for (String k : List.of(
                "dw.pipeline.log_storage", "dw.pipeline.log_retention_days",
                "dw.pipeline.resume_enabled", "dw.pipeline.resume_max_retries",
                "dw.pipeline.keep_history", "dw.pipeline.history_max_versions",
                "dw.pipeline.preview_mode", "dw.pipeline.preview_max_rows",
                "dw.pipeline.alert_on_failure", "dw.pipeline.alert_on_success",
                "dw.pipeline.template_repo_url", "dw.pipeline.monaco_theme")) {
            KEY_OVERRIDE_SUBGROUP.put(k, "pipeline-advanced");
        }
    }

    /** 子分区 → 段（semantic 段当前无配置项，仅作占位段） */
    private static final Map<String, String> SUBGROUP_TO_SEGMENT = Map.ofEntries(
            Map.entry("lake", "near-source"), Map.entry("storage", "near-source"),
            Map.entry("sync", "near-source"), Map.entry("datasource", "near-source"),
            Map.entry("execution", "dw"), Map.entry("pipeline", "dw"),
            Map.entry("pipeline-advanced", "dw"), Map.entry("quality", "dw"),
            Map.entry("lineage", "dw"), Map.entry("query", "dw"),
            Map.entry("cache", "dw"), Map.entry("catalog", "dw"),
            Map.entry("metadata", "dw"),
            Map.entry("engine", "global"), Map.entry("notify", "global"),
            Map.entry("copilot", "global"));

    /** 未命中任何前缀的配置项兜底归属段 / 子分区 */
    private static final String FALLBACK_SEGMENT = "global";

    /** 未命中任何前缀的配置项兜底子分区 */
    private static final String FALLBACK_SUBGROUP = "general";

    /**
     * 子分区展示顺序（段内）。未列入的子分区排在末尾并保持库中返回顺序，
     * 保证 UI 小节顺序稳定、不随 sys_config 行序漂移。
     */
    private static final List<String> SUBGROUP_ORDER = List.of(
            // near-source
            "lake", "storage", "sync", "datasource",
            // dw
            "execution", "pipeline", "pipeline-advanced", "quality", "lineage",
            "query", "cache", "catalog", "metadata",
            // global
            "engine", "notify", "copilot",
            // 兜底
            FALLBACK_SUBGROUP);

    // ── 端点 ──────────────────────────────────────────────

    /**
     * GET /api/v1/engine/data/config
     * 返回所有数据引擎配置，按子组分组。
     */
    @GetMapping
    public ApiResponse<Map<String, Map<String, String>>> getAllConfig() {
        try {
            return ApiResponse.success(groupBySubGroup(loadConfigRows()));
        } catch (Exception e) {
            log.error("获取数据引擎配置失败", e);
            return ApiResponse.internalError("获取配置失败: " + e.getMessage());
        }
    }

    /**
     * GET /api/v1/engine/data/settings/groups
     * 按数据湖分层分段返回配置：near-source（近源层）/ dw（DW 层）/ semantic（语义层占位）/ global（全局）。
     * <p>段与子分区的归属由本端点决定，是前端分层展示的唯一真相源；段名/子分区名的显示文案由前端 i18n 决定。
     * <p>响应结构：{@code data.segments[].id}（段）+ {@code data.segments[].subgroups[].id/configs}（子分区与配置）。
     */
    @GetMapping("/groups")
    public ApiResponse<Map<String, Object>> getConfigBySegment() {
        try {
            return ApiResponse.success(groupBySegment(loadConfigRows()));
        } catch (Exception e) {
            log.error("获取数据引擎分段配置失败", e);
            return ApiResponse.internalError("获取配置失败: " + e.getMessage());
        }
    }

    /**
     * 加载 data-engine 配置行；首次访问自动插入默认值，并补齐版本迭代新增的 key（幂等）。
     */
    private List<Map<String, Object>> loadConfigRows() {
        List<Map<String, Object>> rows = sysConfigService.getByGroup("data-engine");
        if (rows.isEmpty()) {
            // lazy init — 首次访问自动插入默认配置
            initDefaultRows();
        } else {
            // 补插缺失的默认配置项（版本迭代新增 key 时自动补齐，幂等）
            ensureMissingDefaults(rows);
        }
        return sysConfigService.getByGroup("data-engine");
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

    /** 补插 DEFAULTS 中已定义但库里缺失的配置项（如新增的 dw.metadata.history_versions） */
    private void ensureMissingDefaults(List<Map<String, Object>> rows) {
        try {
            Set<String> existing = new HashSet<>();
            for (Map<String, Object> row : rows) {
                Object k = row.get("config_key");
                if (k != null) {
                    existing.add(k.toString());
                }
            }
            int added = 0;
            for (Map.Entry<String, String> e : DEFAULTS.entrySet()) {
                if (!existing.contains(e.getKey())) {
                    sysConfigService.upsertValue(e.getKey(), e.getValue(), "data-engine", "string", "");
                    added++;
                }
            }
            if (added > 0) {
                log.info("补充缺失的默认配置项 {} 个", added);
            }
        } catch (Exception ex) {
            log.warn("ensureMissingDefaults partial failure: {}", ex.getMessage());
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

    /**
     * 将配置行按数据湖分层分段。
     * <p>段顺序固定为 {@link #SEGMENT_ORDER}；段内子分区按 {@link #SUBGROUP_ORDER} 固定顺序；
     * 段内项保留完整 config_key（前端直接以 key 取值，不再反拼），未归类项落 global/general 兜底不丢弃。
     *
     * @param rows 配置行（含 config_key / config_value）
     * @return {@code {"segments": [{"id": 段, "subgroups": [{"id": 子分区, "configs": {key: value}}]}]}}
     */
    private Map<String, Object> groupBySegment(List<Map<String, Object>> rows) {
        Map<String, Map<String, LinkedHashMap<String, String>>> bySegment = new LinkedHashMap<>();

        for (Map<String, Object> row : rows) {
            String key = (String) row.get("config_key");
            if (key == null) {
                continue;
            }
            String value = (String) row.get("config_value");

            String subgroup = resolveSegmentSubgroup(key);
            String segment = subgroup != null ? SUBGROUP_TO_SEGMENT.get(subgroup) : null;
            if (segment == null) {
                segment = FALLBACK_SEGMENT;
                subgroup = FALLBACK_SUBGROUP;
            }

            bySegment.computeIfAbsent(segment, k -> new LinkedHashMap<>())
                     .computeIfAbsent(subgroup, k -> new LinkedHashMap<>())
                     .put(key, value != null ? value : "");
        }

        List<Map<String, Object>> segments = new ArrayList<>();
        for (String segmentId : SEGMENT_ORDER) {
            List<Map<String, Object>> subgroupList = new ArrayList<>();
            Map<String, LinkedHashMap<String, String>> subgroups = bySegment.get(segmentId);
            if (subgroups != null) {
                List<String> orderedIds = new ArrayList<>(subgroups.keySet());
                // 稳定排序：已知子分区按 SUBGROUP_ORDER，未知的保持库中返回顺序排在其后
                orderedIds.sort(Comparator.comparingInt(
                        (String id) -> {
                            int idx = SUBGROUP_ORDER.indexOf(id);
                            return idx < 0 ? Integer.MAX_VALUE : idx;
                        }));
                for (String subgroupId : orderedIds) {
                    Map<String, Object> subgroup = new LinkedHashMap<>();
                    subgroup.put("id", subgroupId);
                    subgroup.put("configs", subgroups.get(subgroupId));
                    subgroupList.add(subgroup);
                }
            }
            Map<String, Object> segment = new LinkedHashMap<>();
            segment.put("id", segmentId);
            segment.put("subgroups", subgroupList);
            segments.add(segment);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("segments", segments);
        return result;
    }

    /**
     * 解析配置项所属子分区：精确 key 覆盖优先，其次最长前缀匹配。
     *
     * @param key config_key
     * @return 子分区 id；无匹配返回 null
     */
    private String resolveSegmentSubgroup(String key) {
        String override = KEY_OVERRIDE_SUBGROUP.get(key);
        if (override != null) {
            return override;
        }
        String matched = null;
        int matchedLen = -1;
        for (Map.Entry<String, String> e : SEGMENT_PREFIX_TO_SUBGROUP.entrySet()) {
            if (key.startsWith(e.getKey()) && e.getKey().length() > matchedLen) {
                matched = e.getValue();
                matchedLen = e.getKey().length();
            }
        }
        return matched;
    }
}
