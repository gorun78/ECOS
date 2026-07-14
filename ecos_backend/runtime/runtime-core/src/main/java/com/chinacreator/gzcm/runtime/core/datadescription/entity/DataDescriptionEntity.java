package com.chinacreator.gzcm.runtime.core.datadescription.entity;

import java.util.Date;

/**
 * 鏁版嵁鎻忚堪瀹炰綋绫?
 * 瀵瑰簲鏁版嵁搴撹〃锛歍D_DATA_DESCRIPTION
 * 
 * @author CDRC Runtime Team
 */
public class DataDescriptionEntity {
    
    /**
     * 鏁版嵁鎻忚堪ID
     */
    private String id;
    
    /**
     * 鏁版嵁绫诲瀷锛圫TRUCTURED, FILE, SERVICE, STREAM锛?
     */
    private String dataType;
    
    /**
     * 鏁版嵁鍚嶇О
     */
    private String name;
    
    /**
     * 鏁版嵁鏍煎紡锛圕SV, JSON, Parquet, Avro, REST, Kafka绛夛級
     */
    private String format;
    
    /**
     * Schema鍐呭锛圝SON鏍煎紡锛?
     */
    private String schemaContent;
    
    /**
     * Schema绫诲瀷锛圝SON_SCHEMA, AVRO绛夛級
     */
    private String schemaType;
    
    /**
     * 鍏冩暟鎹紙JSON鏍煎紡锛?
     */
    private String metadataContent;
    
    /**
     * 鎻忚堪
     */
    private String description;
    
    /**
     * 鍒涘缓鏃堕棿
     */
    private Date createdTime;
    
    /**
     * 鍒涘缓鑰匢D
     */
    private String createdBy;
    
    /**
     * 鏇存柊鏃堕棿
     */
    private Date updatedTime;
    
    /**
     * 鏇存柊鑰匢D
     */
    private String updatedBy;
    
    /**
     * 鐘舵€侊紙ACTIVE, INACTIVE, DELETED锛?
     */
    private String status;
    
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
    
    public String getDataType() {
        return dataType;
    }
    
    public void setDataType(String dataType) {
        this.dataType = dataType;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getFormat() {
        return format;
    }
    
    public void setFormat(String format) {
        this.format = format;
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
    
    public String getMetadataContent() {
        return metadataContent;
    }
    
    public void setMetadataContent(String metadataContent) {
        this.metadataContent = metadataContent;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
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
    
    public Date getUpdatedTime() {
        return updatedTime;
    }
    
    public void setUpdatedTime(Date updatedTime) {
        this.updatedTime = updatedTime;
    }
    
    public String getUpdatedBy() {
        return updatedBy;
    }
    
    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public String getRemark() {
        return remark;
    }
    
    public void setRemark(String remark) {
        this.remark = remark;
    }
    
    @Override
    public String toString() {
        return "DataDescriptionEntity{" +
                "id='" + id + '\'' +
                ", dataType='" + dataType + '\'' +
                ", name='" + name + '\'' +
                ", format='" + format + '\'' +
                ", schemaType='" + schemaType + '\'' +
                ", status='" + status + '\'' +
                '}';
    }
}

