package com.chinacreator.gzcm.sysman.config.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 系统配置服务 — JdbcTemplate + 内存缓存实现。
 * <p>
 * 启动时自动从 sys_config 表加载所有配置到 ConcurrentHashMap，
 * 提供 getString / getInt / getLong / getBoolean 便捷取值方法。
 */
@Service
public class SysConfigService {

    private static final Logger log = LoggerFactory.getLogger(SysConfigService.class);

    private final JdbcTemplate jdbcTemplate;

    /** 内存缓存：configKey → config_value (字符串) */
    private final ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<>();

    public SysConfigService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // ── 初始化 ──────────────────────────────────────────

    @PostConstruct
    public void init() {
        ensureSchema();
        ensureDefaultConfigs();
        refreshCache();
        log.info("SysConfigService 初始化完成，已加载 {} 条配置", cache.size());
    }

    /** 确保 sys_config 表有新字段 (config_group, description, config_type) */
    private void ensureSchema() {
        try {
            jdbcTemplate.execute("ALTER TABLE sys_config ADD COLUMN IF NOT EXISTS config_group VARCHAR(50) DEFAULT 'general'");
            jdbcTemplate.execute("ALTER TABLE sys_config ADD COLUMN IF NOT EXISTS description TEXT");
            jdbcTemplate.execute("ALTER TABLE sys_config ADD COLUMN IF NOT EXISTS config_type VARCHAR(20) DEFAULT 'string'");
            // Pipeline 2.0 tables
            jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS ecos_pipeline_function (" +
                "  id VARCHAR(36) PRIMARY KEY, name VARCHAR(100) NOT NULL UNIQUE," +
                "  category VARCHAR(50) NOT NULL, signature TEXT, return_type VARCHAR(50)," +
                "  description TEXT, example TEXT, is_builtin BOOLEAN DEFAULT true," +
                "  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
            jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS ecos_pipeline_udf (" +
                "  id VARCHAR(36) PRIMARY KEY, name VARCHAR(200) NOT NULL UNIQUE," +
                "  category VARCHAR(50), language VARCHAR(20) DEFAULT 'python'," +
                "  signature TEXT, source_code TEXT NOT NULL, compiled_path VARCHAR(500)," +
                "  version INTEGER DEFAULT 1, author VARCHAR(100), is_shared BOOLEAN DEFAULT false," +
                "  description TEXT, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP)");
        } catch (Exception e) {
            log.error("扩展 sys_config 表结构失败: {}", e.getMessage());
        }
    }

    /** 确保数据引擎默认配置已入库 (ON CONFLICT 语义) */
    private void ensureDefaultConfigs() {
        String[][] defaults = {
            {"dw.execution.mode","memory","data-engine","enum","执行模式 memory/doris"},
            {"dw.execution.memory.max_rows","100000","data-engine","int","内存模式最大处理行数"},
            {"dw.execution.memory.threads","4","data-engine","int","并行线程数"},
            {"dw.execution.doris.host","localhost","data-engine","string","Doris FE地址"},
            {"dw.execution.doris.port","9030","data-engine","int","Doris端口"},
            {"dw.execution.doris.user","root","data-engine","string","Doris用户名"},
            {"dw.execution.doris.database","ecos_dw","data-engine","string","Doris默认库"},
            {"dw.execution.doris.batch_size","10000","data-engine","int","Doris批量写入行数"},
            {"dw.execution.timeout","600","data-engine","int","任务超时秒数"},
            {"dw.lake.enabled","false","data-engine","bool","是否启用数据湖"},
            {"dw.lake.datasource_id","","data-engine","string","数据湖目标数据源ID"},
            {"dw.lake.storage_format","parquet","data-engine","enum","存储格式"},
            {"dw.lake.partition_by","dt","data-engine","string","默认分区字段"},
            {"dw.lake.retention_days","90","data-engine","int","数据保留天数"},
            {"dw.storage.type","minio","data-engine","enum","对象存储类型"},
            {"dw.storage.minio.endpoint","http://localhost:9000","data-engine","string","MinIO地址"},
            {"dw.storage.minio.access_key","minioadmin","data-engine","string","AccessKey"},
            {"dw.storage.minio.secret_key","minioadmin","data-engine","password","SecretKey"},
            {"dw.storage.minio.bucket","ecos-data","data-engine","string","默认Bucket"},
            {"dw.storage.minio.region","us-east-1","data-engine","string","区域"},
            {"dw.storage.minio.ssl","false","data-engine","bool","启用SSL"},
            {"dw.pipeline.max_steps","20","data-engine","int","最大步骤数"},
            {"dw.pipeline.parallel_steps","4","data-engine","int","并行步骤数"},
            {"dw.pipeline.default_chunk_size","10000","data-engine","int","分块行数"},
            {"dw.pipeline.temp_table_prefix","ecos_tmp_","data-engine","string","临时表前缀"},
            {"dw.pipeline.temp_table_ttl_hours","24","data-engine","int","临时表过期h"},
            {"dw.pipeline.retry_max","3","data-engine","int","重试次数"},
            {"dw.pipeline.retry_backoff_ms","5000","data-engine","int","重试间隔ms"},
            {"dw.quality.sample_rate","1.0","data-engine","float","采样率"},
            {"dw.quality.sample_max_rows","1000000","data-engine","int","采样最大行数"},
            {"dw.quality.stale_threshold_hours","24","data-engine","int","过期阈值h"},
            {"dw.quality.default_alert_score","80","data-engine","int","告警分数阈值"},
            {"dw.quality.concurrent_checks","2","data-engine","int","并发检查数"},
            {"dw.quality.check_timeout","300","data-engine","int","检查超时秒"},
            {"dw.lineage.enabled","true","data-engine","bool","启血缘采集"},
            {"dw.lineage.parser","sql","data-engine","enum","血缘解析引擎"},
            {"dw.lineage.max_depth","10","data-engine","int","最大追溯深度"},
            {"dw.lineage.cache_ttl_minutes","30","data-engine","int","缓存时间min"},
            {"dw.lineage.neo4j_enabled","false","data-engine","bool","Neo4j图存储"},
            {"dw.sync.batch_size","5000","data-engine","int","同步批次大小"},
            {"dw.sync.max_retries","3","data-engine","int","同步最大重试"},
            {"dw.query.max_rows","10000","data-engine","int","查询最大行数"},
            {"dw.query.timeout","30","data-engine","int","查询超时秒"},
            {"dw.cache.ttl_seconds","300","data-engine","int","缓存时间秒"},
            {"dw.engine.auto_start","true","data-engine","bool","自动启动引擎"},
            {"dw.datasource.page_size","20","data-engine","int","[迁移]数据源分页"},
            {"dw.datasource.conn_timeout","30000","data-engine","int","[迁移]连接超时ms"},
            {"dw.metadata.collect_timeout","60","data-engine","int","[迁移]采集超时s"},
            {"dw.catalog.search_limit","500","data-engine","int","[迁移]目录搜索限制"},
        };
        try {
            for (String[] row : defaults) {
                jdbcTemplate.update(
                    "INSERT INTO sys_config (config_key, config_value, config_group, config_type, description, status) " +
                    "VALUES (?, ?, ?, ?, ?, 'active') " +
                    "ON CONFLICT (config_key) DO UPDATE SET description = EXCLUDED.description",
                    row[0], row[1], row[2], row[3], row[4]
                );
            }
        } catch (Exception e) {
            log.error("初始化默认配置失败: {}", e.getMessage());
        }
    }

    /**
     * 刷新缓存：重新从数据库加载所有 active 配置。
     */
    public void refreshCache() {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT config_key, config_value FROM sys_config WHERE status = 'active'"
            );
            cache.clear();
            for (Map<String, Object> row : rows) {
                String key = (String) row.get("config_key");
                String value = (String) row.get("config_value");
                if (key != null && value != null) {
                    cache.put(key, value);
                }
            }
            log.info("缓存刷新完成，当前 {} 条配置", cache.size());
        } catch (Exception e) {
            log.error("刷新 sys_config 缓存失败", e);
        }
    }

    // ── 取值方法 ────────────────────────────────────────

    /** 获取字符串值 */
    public String getString(String key) {
        return cache.get(key);
    }

    /** 获取字符串值（带默认值） */
    public String getString(String key, String defaultValue) {
        String val = cache.get(key);
        return val != null ? val : defaultValue;
    }

    /** 获取整数值 */
    public int getInt(String key) {
        return getInt(key, 0);
    }

    /** 获取整数值（带默认值） */
    public int getInt(String key, int defaultValue) {
        String val = cache.get(key);
        if (val == null) return defaultValue;
        try {
            return Integer.parseInt(val);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /** 获取长整数值 */
    public long getLong(String key) {
        return getLong(key, 0L);
    }

    /** 获取长整数值（带默认值） */
    public long getLong(String key, long defaultValue) {
        String val = cache.get(key);
        if (val == null) return defaultValue;
        try {
            return Long.parseLong(val);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /** 获取布尔值 */
    public boolean getBoolean(String key) {
        return getBoolean(key, false);
    }

    /** 获取布尔值（带默认值） */
    public boolean getBoolean(String key, boolean defaultValue) {
        String val = cache.get(key);
        if (val == null) return defaultValue;
        return "true".equalsIgnoreCase(val) || "1".equals(val) || "yes".equalsIgnoreCase(val);
    }

    // ── 查询方法 ────────────────────────────────────────

    /** 获取全部缓存（快照） */
    public Map<String, String> getAll() {
        return new HashMap<>(cache);
    }

    /** 按分组查询配置列表 */
    public List<Map<String, Object>> listByGroup(String group) {
        String sql;
        Object[] args;
        if (group != null && !group.isEmpty()) {
            sql = "SELECT * FROM sys_config WHERE config_group = ? AND status = 'active' ORDER BY sort_order";
            args = new Object[]{group};
        } else {
            sql = "SELECT * FROM sys_config WHERE status = 'active' ORDER BY config_group, sort_order";
            args = new Object[]{};
        }
        return jdbcTemplate.queryForList(sql, args);
    }

    /** 按 key 获取完整配置行 */
    public Map<String, Object> getByKey(String key) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT * FROM sys_config WHERE config_key = ? AND status = 'active'", key
        );
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** 更新配置值并刷新缓存 */
    public boolean updateValue(String key, String value) {
        int rows = jdbcTemplate.update(
            "UPDATE sys_config SET config_value = ?, updated_at = NOW() WHERE config_key = ?",
            value, key
        );
        if (rows > 0) {
            cache.put(key, value);
            log.info("配置已更新: {} = {}", key, value);
            return true;
        }
        return false;
    }

    /** 插入或更新配置值 (SELECT-check + INSERT/UPDATE)，并刷新缓存 */
    public void upsertValue(String configKey, String configValue, String configGroup, String configType, String description) {
        // Check if exists
        int count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM sys_config WHERE config_key = ?", Integer.class, configKey);
        if (count > 0) {
            jdbcTemplate.update(
                "UPDATE sys_config SET config_value = ?, updated_at = NOW() WHERE config_key = ?",
                configValue, configKey);
        } else {
            String label = configKey.contains(".") ? configKey.substring(configKey.lastIndexOf('.') + 1) : configKey;
            jdbcTemplate.update(
                "INSERT INTO sys_config (id, config_key, config_value, config_group, config_type, config_label, description, sort_order, status, edition) " +
                "VALUES (gen_random_uuid(), ?, ?, ?, ?, ?, ?, 100, 'active', 'all')",
                configKey, configValue, configGroup, configType, label, description);
        }
        cache.put(configKey, configValue);
    }

    /** 获取缓存大小 */
    public int cacheSize() {
        return cache.size();
    }

    // ── 数据工作台配置方法 ────────────────────────────

    /**
     * 获取指定 config_group 的所有配置行。
     */
    public List<Map<String, Object>> getByGroup(String group) {
        return listByGroup(group);
    }

    /**
     * 获取所有 active 配置，按 config_group 分组返回。
     * @return Map<config_group, List<config_row>>
     */
    public Map<String, List<Map<String, Object>>> getAllGrouped() {
        List<Map<String, Object>> all = listByGroup(null);
        Map<String, List<Map<String, Object>>> grouped = new LinkedHashMap<>();
        for (Map<String, Object> row : all) {
            String grp = (String) row.getOrDefault("config_group", "default");
            grouped.computeIfAbsent(grp, k -> new ArrayList<>()).add(row);
        }
        return grouped;
    }

    /**
     * 批量更新配置值并刷新缓存。
     * @param updates  [{config_key, config_value}, ...]
     * @return 成功更新的条数
     */
    public int updateBatch(List<Map<String, String>> updates) {
        int count = 0;
        for (Map<String, String> item : updates) {
            String key = item.get("config_key");
            String value = item.get("config_value");
            if (key != null && value != null && updateValue(key, value)) {
                count++;
            }
        }
        return count;
    }
}
