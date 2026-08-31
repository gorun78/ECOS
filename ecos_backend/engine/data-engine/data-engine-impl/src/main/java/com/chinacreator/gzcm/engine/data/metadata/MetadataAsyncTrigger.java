package com.chinacreator.gzcm.engine.data.metadata;

import com.chinacreator.gzcm.common.data.dto.DataSourceDTO;
import com.chinacreator.gzcm.engine.data.datasource.entity.DataSourceEntity;
import com.chinacreator.gzcm.engine.data.service.MetadataCollectionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * PMO-37 采集触发器 —— 数据源生命周期事件 → 任务引擎。
 * <p>
 * 触发点：
 * <ul>
 *   <li>afterRegister()  —— strategy=ON_SAVE 时异步提交</li>
 *   <li>afterUpdate()    —— strategy=ON_SAVE 或（连接配置变更 且 onSourceEdit）时异步提交</li>
 *   <li>triggerManualAsync() —— 手动触发端点（MetadataController 新增）</li>
 * </ul>
 * 提交走 {@link MetadataTaskService#submitCollect(String, boolean)}；
 * 任务引擎不可用时降级为同步直采（保持既有 POST /metadata/collect 可用性兜底）。
 *
 * @author DataBridge Datanet Team
 */
@Component
public class MetadataAsyncTrigger {

    private static final Logger log = LoggerFactory.getLogger(MetadataAsyncTrigger.class);

    private final MetadataTaskService taskService;
    private final MetadataCollectionService collectionService;

    public MetadataAsyncTrigger(MetadataTaskService taskService,
                                MetadataCollectionService collectionService) {
        this.taskService = taskService;
        this.collectionService = collectionService;
    }

    /** 数据源注册后（ON_SAVE 策略触发） */
    @Async
    public void afterRegister(DataSourceEntity saved) {
        if (saved == null) {
            return;
        }
        String strategy = StrategySupport.strategyOf(saved.getMetadataConfig());
        if (strategy != null && MetadataStrategyConfig.STRATEGY_ON_SAVE.equalsIgnoreCase(strategy)) {
            submitAsync(saved.getDatasourceId());
        }
    }

    /** 数据源更新后（ON_SAVE 或连接配置变更 + onSourceEdit 触发） */
    @Async
    public void afterUpdate(String datasourceId, String newMetadataConfigJson,
                            String oldConnectionConfig, DataSourceDTO dto) {
        if (datasourceId == null) {
            return;
        }
        MetadataStrategyConfig cfg = MetadataStrategyConfig.fromJson(newMetadataConfigJson);
        String strategy = cfg.getStrategy() == null
                ? (dto.getMetadataStrategy() != null ? dto.getMetadataStrategy() : "MANUAL")
                : cfg.getStrategy();
        boolean onSave = MetadataStrategyConfig.STRATEGY_ON_SAVE.equalsIgnoreCase(strategy);
        boolean configChanged = oldConnectionConfig != null
                && dto.getConnectionConfig() != null
                && !oldConnectionConfig.equals(dto.getConnectionConfig());
        boolean onEdit = configChanged && !Boolean.FALSE.equals(cfg.getOnSourceEdit());
        if (onSave || onEdit) {
            submitAsync(datasourceId);
        }
    }

    /** 手动触发（异步） */
    @Async
    public void triggerManualAsync(String datasourceId) {
        submitAsync(datasourceId);
    }

    /** 提交采集任务（同步返回 taskId；内部走任务引擎） */
    public String submitAsync(String datasourceId) {
        return taskService.submitCollect(datasourceId, true);
    }

    // ===== 静态工具（供 DataSourceServiceImpl / Scheduler / Publisher 复用） =====

    /** 把 DTO 扁平字段序列化进 metadata_config JSONB（FE 可能只发 metadataConfig JSON 串，此处解析回填扁平字段） */
    public static Map<String, Object> metadataConfigMap(DataSourceDTO dto, String fallbackStrategy) {
        hydrateFromMetadataConfigJson(dto);
        Map<String, Object> cfg = new HashMap<>();
        cfg.put("strategy", dto.getMetadataStrategy() != null && !dto.getMetadataStrategy().isBlank()
                ? dto.getMetadataStrategy().toUpperCase()
                : (fallbackStrategy != null ? fallbackStrategy : "MANUAL"));
        cfg.put("includeRowCount", dto.getIncludeRowCount() != null ? dto.getIncludeRowCount() : true);
        cfg.put("countMethod", dto.getCountMethod() != null && !dto.getCountMethod().isBlank()
                ? dto.getCountMethod().toUpperCase() : "ESTIMATE");
        if (dto.getScheduleCron() != null && !dto.getScheduleCron().isBlank()) {
            cfg.put("scheduleCron", dto.getScheduleCron());
        }
        if (dto.getCacheTtlMinutes() != null && dto.getCacheTtlMinutes() > 0) {
            cfg.put("cacheTtlMinutes", dto.getCacheTtlMinutes());
        }
        cfg.put("onSourceEdit", dto.getOnSourceEdit() != null ? dto.getOnSourceEdit() : true);
        cfg.put("last_updated", LocalDateTime.now().toString());
        return cfg;
    }

    public static boolean isOnSchedule(MetadataStrategyConfig cfg) {
        return cfg != null
                && MetadataStrategyConfig.STRATEGY_ON_SCHEDULE.equalsIgnoreCase(cfg.getStrategy())
                && cfg.getScheduleCron() != null && !cfg.getScheduleCron().isBlank();
    }

    /** FE 可能一次性发 metadataConfig JSON 串而扁平字段为 null — 解析回填，兼容两种写法 */
    private static void hydrateFromMetadataConfigJson(DataSourceDTO dto) {
        if (dto.getMetadataConfig() == null || dto.getMetadataConfig().isBlank()) return;
        if (dto.getMetadataStrategy() != null && dto.getCountMethod() != null) return; // 扁平已全，直接用
        try {
            com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
            java.util.Map<String, Object> m = om.readValue(dto.getMetadataConfig(), java.util.Map.class);
            if (dto.getMetadataStrategy() == null) {
                Object t = m.get("trigger") != null ? m.get("trigger") : m.get("strategy");
                if (t != null) dto.setMetadataStrategy(String.valueOf(t));
            }
            if (dto.getCountMethod() == null) {
                Object c = m.get("countMethod") != null ? m.get("countMethod") : m.get("count_method");
                if (c != null) dto.setCountMethod(String.valueOf(c));
            }
            if (dto.getScheduleCron() == null && m.get("scheduleCron") != null) {
                dto.setScheduleCron(String.valueOf(m.get("scheduleCron")));
            }
        } catch (Exception ignore) {
            // 解析失败回退默认策略
        }
    }

    public static boolean cronIsValid(String cron) {
        if (cron == null || cron.isBlank()) {
            return false;
        }
        try {
            CronExpression.parse(cron.trim());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static LocalDateTime nextFireTime(String cron, LocalDateTime from) {
        try {
            return CronExpression.parse(cron.trim()).next(from);
        } catch (Exception e) {
            return null;
        }
    }
}

/**
 * 策略读取小工具（避免循环依赖，独立小类）。
 */
final class StrategySupport {

    static String strategyOf(String metadataConfigJson) {
        return MetadataStrategyConfig.fromJson(metadataConfigJson).getStrategy();
    }
}
