package com.chinacreator.gzcm.runtime.core.config.entity;

import java.io.Serializable;
import java.sql.Timestamp;

/**
 * 配置版本实体类
 * 对应数据库表 td_config_version
 *
 * @author CDRC Runtime Team
 */
public class ConfigVersionEntity implements Serializable {
    private static final long serialVersionUID = 1L;

    private String versionId;
    private String configId;
    private String version;
    private String configContent;
    private String changelog;
    private Timestamp createdTime;
    private String createdBy;

    // Getters and Setters
    public String getVersionId() {
        return versionId;
    }

    public void setVersionId(String versionId) {
        this.versionId = versionId;
    }

    public String getConfigId() {
        return configId;
    }

    public void setConfigId(String configId) {
        this.configId = configId;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getConfigContent() {
        return configContent;
    }

    public void setConfigContent(String configContent) {
        this.configContent = configContent;
    }

    public String getChangelog() {
        return changelog;
    }

    public void setChangelog(String changelog) {
        this.changelog = changelog;
    }

    public Timestamp getCreatedTime() {
        return createdTime;
    }

    public void setCreatedTime(Timestamp createdTime) {
        this.createdTime = createdTime;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }
}