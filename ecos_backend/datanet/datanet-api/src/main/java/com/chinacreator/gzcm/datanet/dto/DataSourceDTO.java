package com.chinacreator.gzcm.datanet.dto;

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
}
