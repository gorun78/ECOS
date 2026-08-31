package com.chinacreator.gzcm.engine.data.metadata;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * PMO-37 数据源元数据获取策略配置（JSONB metadata_config 节的 POJO）。
 * <p>
 * 全部字段可空 —— 空值走 DEFAULTS 兜底，向后兼容既有行（NULL / '{}'）。
 *
 * @author DataBridge Datanet Team
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class MetadataStrategyConfig {

    public static final String STRATEGY_ON_SAVE = "ON_SAVE";
    public static final String STRATEGY_ON_SCHEDULE = "ON_SCHEDULE";
    public static final String STRATEGY_MANUAL = "MANUAL";
    public static final String STRATEGY_ON_DEMAND = "ON_DEMAND";

    public static final String COUNT_EXACT = "EXACT";
    public static final String COUNT_ESTIMATE = "ESTIMATE";
    public static final String COUNT_OFF = "OFF";

    /** 触发策略，默认 MANUAL */
    private String strategy = STRATEGY_MANUAL;
    /** 是否采集行数（false 时 record_count 记 -1） */
    private Boolean includeRowCount = true;
    /** 行数统计方式 */
    private String countMethod = COUNT_ESTIMATE;
    /** ON_SCHEDULE 的 cron（Spring 6 段格式） */
    private String scheduleCron;
    /** 元数据缓存 TTL（分钟），默认 5 */
    private Integer cacheTtlMinutes = 5;
    /** 编辑连接配置后自动重采 */
    private Boolean onSourceEdit = true;

    public static MetadataStrategyConfig fromJson(String json) {
        if (json == null || json.isBlank() || json.trim().equals("{}")) {
            return new MetadataStrategyConfig();
        }
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(json, MetadataStrategyConfig.class);
        } catch (Exception e) {
            return new MetadataStrategyConfig();
        }
    }

    public String getStrategy() { return strategy; }
    public void setStrategy(String strategy) { this.strategy = strategy; }

    public Boolean getIncludeRowCount() { return includeRowCount; }
    public void setIncludeRowCount(Boolean includeRowCount) { this.includeRowCount = includeRowCount; }

    public String getCountMethod() { return countMethod; }
    public void setCountMethod(String countMethod) { this.countMethod = countMethod; }

    public String getScheduleCron() { return scheduleCron; }
    public void setScheduleCron(String scheduleCron) { this.scheduleCron = scheduleCron; }

    public Integer getCacheTtlMinutes() { return cacheTtlMinutes; }
    public void setCacheTtlMinutes(Integer cacheTtlMinutes) { this.cacheTtlMinutes = cacheTtlMinutes; }

    public Boolean getOnSourceEdit() { return onSourceEdit; }
    public void setOnSourceEdit(Boolean onSourceEdit) { this.onSourceEdit = onSourceEdit; }
}
