package com.chinacreator.gzcm.runtime.core.datadescription.entity;

import java.util.Date;

/**
 * Schema娉ㄥ唽瀹炰綋绫?
 * 瀵瑰簲鏁版嵁搴撹〃锛歍D_SCHEMA_REGISTRY
 * 
 * @author CDRC Runtime Team
 */
public class SchemaRegistry {
    
    /**
     * 涓婚敭ID
     */
    private String id;
    
    /**
     * Schema涓婚锛堝锛歶ser-schema锛?
     */
    private String subject;
    
    /**
     * Schema鐗堟湰鍙?
     */
    private Integer version;
    
    /**
     * Schema鍐呭锛圝SON鏍煎紡锛?
     */
    private String schemaContent;
    
    /**
     * Schema绫诲瀷锛圝SON_SCHEMA銆丄VRO銆丳ROTOBUF绛夛級
     */
    private String schemaType;
    
    /**
     * 鍏煎鎬х骇鍒紙BACKWARD銆丗ORWARD銆丗ULL銆丯ONE锛?
     */
    private String compatibilityLevel;
    
    /**
     * 鍒涘缓鏃堕棿
     */
    private Date createdTime;
    
    /**
     * 鍒涘缓鑰匢D
     */
    private String createdBy;
    
    /**
     * 淇敼鏃堕棿
     */
    private Date modifiedTime;
    
    /**
     * 淇敼鑰匢D
     */
    private String modifiedBy;
    
    /**
     * 澶囨敞
     */
    private String remark;
    
    // Getters and Setters
    
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
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
    
    public String getSchemaContent() {
        return schemaContent;
    }
    
    public void setSchemaContent(String schemaContent) {
        this.schemaContent = schemaContent;
    }
    
    public String getSchemaType() {
        return schemaType;
    }
    
    public void setSchemaType(String schemaType) {
        this.schemaType = schemaType;
    }
    
    public String getCompatibilityLevel() {
        return compatibilityLevel;
    }
    
    public void setCompatibilityLevel(String compatibilityLevel) {
        this.compatibilityLevel = compatibilityLevel;
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
    
    public Date getModifiedTime() {
        return modifiedTime;
    }
    
    public void setModifiedTime(Date modifiedTime) {
        this.modifiedTime = modifiedTime;
    }
    
    public String getModifiedBy() {
        return modifiedBy;
    }
    
    public void setModifiedBy(String modifiedBy) {
        this.modifiedBy = modifiedBy;
    }
    
    public String getRemark() {
        return remark;
    }
    
    public void setRemark(String remark) {
        this.remark = remark;
    }
    
    @Override
    public String toString() {
        return "SchemaRegistry{" +
                "id='" + id + '\'' +
                ", subject='" + subject + '\'' +
                ", version=" + version +
                ", schemaType='" + schemaType + '\'' +
                ", compatibilityLevel='" + compatibilityLevel + '\'' +
                ", createdTime=" + createdTime +
                ", createdBy='" + createdBy + '\'' +
                '}';
    }
}

