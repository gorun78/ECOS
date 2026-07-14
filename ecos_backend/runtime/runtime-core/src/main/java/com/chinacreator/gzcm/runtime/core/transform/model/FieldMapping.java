package com.chinacreator.gzcm.runtime.core.transform.model;

/**
 * 字段映射模型
 * 定义源字段到目标字段的映射规则
 * 
 * @author GZCM Runtime Team
 */
public class FieldMapping {
    
    /**
     * 映射类型枚举
     */
    public enum MappingType {
        DIRECT("直接映射", "字段名相同，直接复制"),
        RENAME("重命名映射", "字段名不同，重命名复制"),
        EXPRESSION("表达式映射", "使用表达式计算目标字段值"),
        CONSTANT("常量映射", "目标字段使用常量值"),
        FUNCTION("函数映射", "使用函数计算目标字段值");
        
        private String name;
        private String description;
        
        MappingType(String name, String description) {
            this.name = name;
            this.description = description;
        }
        
        public String getName() {
            return name;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    /**
     * 源字段名
     */
    private String sourceField;
    
    /**
     * 目标字段名
     */
    private String targetField;
    
    /**
     * 映射类型
     */
    private MappingType mappingType;
    
    /**
     * 表达式（用于EXPRESSION类型）
     */
    private String expression;
    
    /**
     * 常量值（用于CONSTANT类型）
     */
    private Object constantValue;
    
    /**
     * 函数名（用于FUNCTION类型）
     */
    private String functionName;
    
    /**
     * 函数参数（用于FUNCTION类型）
     */
    private java.util.Map<String, Object> functionParams;
    
    /**
     * 数据类型转换（可选）
     */
    private String targetType;
    
    // Getters and Setters
    
    public String getSourceField() {
        return sourceField;
    }
    
    public void setSourceField(String sourceField) {
        this.sourceField = sourceField;
    }
    
    public String getTargetField() {
        return targetField;
    }
    
    public void setTargetField(String targetField) {
        this.targetField = targetField;
    }
    
    public MappingType getMappingType() {
        return mappingType;
    }
    
    public void setMappingType(MappingType mappingType) {
        this.mappingType = mappingType;
    }
    
    public String getExpression() {
        return expression;
    }
    
    public void setExpression(String expression) {
        this.expression = expression;
    }
    
    public Object getConstantValue() {
        return constantValue;
    }
    
    public void setConstantValue(Object constantValue) {
        this.constantValue = constantValue;
    }
    
    public String getFunctionName() {
        return functionName;
    }
    
    public void setFunctionName(String functionName) {
        this.functionName = functionName;
    }
    
    public java.util.Map<String, Object> getFunctionParams() {
        return functionParams;
    }
    
    public void setFunctionParams(java.util.Map<String, Object> functionParams) {
        this.functionParams = functionParams;
    }
    
    public String getTargetType() {
        return targetType;
    }
    
    public void setTargetType(String targetType) {
        this.targetType = targetType;
    }
}
