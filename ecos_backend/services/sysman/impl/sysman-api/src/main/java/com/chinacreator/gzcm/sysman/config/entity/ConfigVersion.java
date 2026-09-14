package com.chinacreator.gzcm.sysman.config.entity;

import java.util.Date;

/**
 * 配置版本实体
 * 
 * @author CDRC Design Team
 */
public class ConfigVersion {
    private String versionId;
    private String configId;
    private String version;
    private String configContent;
    private String changelog;
    private Date createdTime;
    private String createdBy;
    
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
    
    public Date getCreatedTime() {
        return createdTime;
    }
    
    public void setCreatedTime(Date createdTime) {
        this.createdTime = createdTime;
    }
    
    public String getCreatedBy() {
        return createdBy;
    }
    
    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }
}


