package com.chinacreator.gzcm.dccheng.ontology;

import java.time.LocalDateTime;

/**
 * 本体属性持久化 POJO
 */
public class OntologyProperty {

    private String id;
    private String entityId;
    private String code;
    private String name;
    private String propertyType;
    private int requiredFlag;
    private int searchableFlag;
    private int uniqueFlag;
    private int sortOrder;
    private String enumValues;       // JSON 数组: ["VIP","Gold","Silver"]
    private String defaultValue;
    private String validationRule;
    private String refEntityCode;
    private Integer maxLength;
    private Double minValue;
    private Double maxValue;
    private String functionType;       // EXPRESSION / AGGREGATION / LOOKUP
    private String functionExpression; // 表达式文本
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public OntologyProperty() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getEntityId() { return entityId; }
    public void setEntityId(String entityId) { this.entityId = entityId; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPropertyType() { return propertyType; }
    public void setPropertyType(String propertyType) { this.propertyType = propertyType; }

    public int getRequiredFlag() { return requiredFlag; }
    public void setRequiredFlag(int requiredFlag) { this.requiredFlag = requiredFlag; }

    public int getSearchableFlag() { return searchableFlag; }
    public void setSearchableFlag(int searchableFlag) { this.searchableFlag = searchableFlag; }

    public int getUniqueFlag() { return uniqueFlag; }
    public void setUniqueFlag(int uniqueFlag) { this.uniqueFlag = uniqueFlag; }

    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }

    public String getEnumValues() { return enumValues; }
    public void setEnumValues(String enumValues) { this.enumValues = enumValues; }

    public String getDefaultValue() { return defaultValue; }
    public void setDefaultValue(String defaultValue) { this.defaultValue = defaultValue; }

    public String getValidationRule() { return validationRule; }
    public void setValidationRule(String validationRule) { this.validationRule = validationRule; }

    public String getRefEntityCode() { return refEntityCode; }
    public void setRefEntityCode(String refEntityCode) { this.refEntityCode = refEntityCode; }

    public Integer getMaxLength() { return maxLength; }
    public void setMaxLength(Integer maxLength) { this.maxLength = maxLength; }

    public Double getMinValue() { return minValue; }
    public void setMinValue(Double minValue) { this.minValue = minValue; }

    public Double getMaxValue() { return maxValue; }
    public void setMaxValue(Double maxValue) { this.maxValue = maxValue; }

    public String getFunctionType() { return functionType; }
    public void setFunctionType(String functionType) { this.functionType = functionType; }

    public String getFunctionExpression() { return functionExpression; }
    public void setFunctionExpression(String functionExpression) { this.functionExpression = functionExpression; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
