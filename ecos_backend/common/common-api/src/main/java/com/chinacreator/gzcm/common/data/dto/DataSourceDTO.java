package com.chinacreator.gzcm.common.data.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 数据源连接配置 DTO — 用于注册新的数据源连接。
 *
 * @author DataBridge Datanet Team
 */
public class DataSourceDTO {

    @NotBlank(message = "数据源名称不能为空")
    private String datasourceName;

    @NotBlank(message = "数据源类型不能为空")
    private String datasourceType;

    private String orgId;
    private String orgName;
    private String description;

    /** 连接配置（JSON 格式，如 {"jdbcUrl":"...","username":"...","password":"..."}） */
    private String connectionConfig;

    private String tags;

    // ===== PMO-37 元数据获取策略 =====
    /** ON_SAVE / ON_SCHEDULE / MANUAL / ON_DEMAND ；null 表示走默认（MANUAL，向后兼容） */
    private String metadataStrategy;
    /** 是否采集行数 */
    private Boolean includeRowCount;
    /** EXACT / ESTIMATE / OFF */
    private String countMethod;
    /** ON_SCHEDULE 时的 cron（Spring 6 段格式） */
    private String scheduleCron;
    /** 元数据缓存 TTL（分钟），默认 5 */
    private Integer cacheTtlMinutes;
    /** 编辑连接配置后自动触发采集 */
    private Boolean onSourceEdit;
    /** metadata_config JSONB（FE 一次性发送；扁平字段优先，此字段作 fallback 解析） */
    private String metadataConfig;

    // ===== Getters/Setters =====

    public String getDatasourceName() { return datasourceName; }
    public void setDatasourceName(String datasourceName) { this.datasourceName = datasourceName; }

    public String getDatasourceType() { return datasourceType; }
    public void setDatasourceType(String datasourceType) { this.datasourceType = datasourceType; }

    public String getOrgId() { return orgId; }
    public void setOrgId(String orgId) { this.orgId = orgId; }

    public String getOrgName() { return orgName; }
    public void setOrgName(String orgName) { this.orgName = orgName; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getConnectionConfig() { return connectionConfig; }
    public void setConnectionConfig(String connectionConfig) { this.connectionConfig = connectionConfig; }

    public String getTags() { return tags; }
    public void setTags(String tags) { this.tags = tags; }

    public String getMetadataStrategy() { return metadataStrategy; }
    public void setMetadataStrategy(String metadataStrategy) { this.metadataStrategy = metadataStrategy; }

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

    public String getMetadataConfig() { return metadataConfig; }
    public void setMetadataConfig(String metadataConfig) { this.metadataConfig = metadataConfig; }
}
