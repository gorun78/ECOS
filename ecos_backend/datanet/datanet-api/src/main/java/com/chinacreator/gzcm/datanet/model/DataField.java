package com.chinacreator.gzcm.datanet.model;

/**
 * 数据字段 — 描述数据资源中的一个字段/列。
 *
 * @author DataBridge Datanet Team
 */
public class DataField {

    /** 字段 ID */
    private String fieldId;

    /** 所属资源 ID */
    private String resourceId;

    /** 字段名称 */
    private String fieldName;

    /** 字段中文别名 */
    private String fieldAlias;

    /** 数据类型: VARCHAR, INTEGER, DECIMAL, DATETIME, TEXT, etc. */
    private String dataType;

    /** 字段长度 */
    private Integer dataLength;

    /** 小数位数 */
    private Integer dataPrecision;

    /** 是否可为空 */
    private Boolean nullable;

    /** 是否主键 */
    private Boolean primaryKey;

    /** 默认值 */
    private String defaultValue;

    /** 字段描述 */
    private String description;

    /** 排序号 */
    private Integer sortOrder;

    // ===== Getters/Setters =====

    public String getFieldId() { return fieldId; }
    public void setFieldId(String fieldId) { this.fieldId = fieldId; }

    public String getResourceId() { return resourceId; }
    public void setResourceId(String resourceId) { this.resourceId = resourceId; }

    public String getFieldName() { return fieldName; }
    public void setFieldName(String fieldName) { this.fieldName = fieldName; }

    public String getFieldAlias() { return fieldAlias; }
    public void setFieldAlias(String fieldAlias) { this.fieldAlias = fieldAlias; }

    public String getDataType() { return dataType; }
    public void setDataType(String dataType) { this.dataType = dataType; }

    public Integer getDataLength() { return dataLength; }
    public void setDataLength(Integer dataLength) { this.dataLength = dataLength; }

    public Integer getDataPrecision() { return dataPrecision; }
    public void setDataPrecision(Integer dataPrecision) { this.dataPrecision = dataPrecision; }

    public Boolean getNullable() { return nullable; }
    public void setNullable(Boolean nullable) { this.nullable = nullable; }

    public Boolean getPrimaryKey() { return primaryKey; }
    public void setPrimaryKey(Boolean primaryKey) { this.primaryKey = primaryKey; }

    public String getDefaultValue() { return defaultValue; }
    public void setDefaultValue(String defaultValue) { this.defaultValue = defaultValue; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
}
