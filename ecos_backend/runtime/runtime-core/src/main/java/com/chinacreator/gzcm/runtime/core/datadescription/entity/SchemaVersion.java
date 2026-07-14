package com.chinacreator.gzcm.runtime.core.datadescription.entity;

import java.util.Date;

/**
 * Schema鐗堟湰瀹炰綋绫?
 * 瀵瑰簲鏁版嵁搴撹〃锛歍D_SCHEMA_VERSION
 * 
 * @author CDRC Runtime Team
 */
public class SchemaVersion {
    
    /**
     * 鐗堟湰ID
     */
    private String versionId;
    
    /**
     * Schema涓婚
     */
    private String subject;
    
    /**
     * 鐗堟湰鍙?
     */
    private Integer version;
    
    /**
     * 鍏宠仈鐨凷chema ID
     */
    private String schemaId;
    
    /**
     * 鍒涘缓鏃堕棿
     */
    private Date createdTime;
    
    /**
     * 鍒涘缓鑰匢D
     */
    private String createdBy;
    
    // Getters and Setters
    
    public String getVersionId() {
        return versionId;
    }
    
    public void setVersionId(String versionId) {
        this.versionId = versionId;
    }
    
    public String getSubject() {
        return subject;
    }
    
    public void setSubject(String subject) {
        this.subject = subject;
    }
    
    public Integer getVersion() {
        return version;
    }
    
    public void setVersion(Integer version) {
        this.version = version;
    }
    
    public String getSchemaId() {
        return schemaId;
    }
    
    public void setSchemaId(String schemaId) {
        this.schemaId = schemaId;
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
    
    @Override
    public String toString() {
        return "SchemaVersion{" +
                "versionId='" + versionId + '\'' +
                ", subject='" + subject + '\'' +
                ", version=" + version +
                ", schemaId='" + schemaId + '\'' +
                ", createdTime=" + createdTime +
                ", createdBy='" + createdBy + '\'' +
                '}';
    }
}

