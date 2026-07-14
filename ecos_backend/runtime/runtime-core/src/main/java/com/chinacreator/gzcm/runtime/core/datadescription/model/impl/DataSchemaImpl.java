package com.chinacreator.gzcm.runtime.core.datadescription.model.impl;

import java.util.ArrayList;
import java.util.List;

import com.chinacreator.gzcm.runtime.core.datadescription.model.DataSchema;

/**
 * DataSchema简单实现类
 * 用于存储数据库表的Schema信息
 * 
 * @author CDRC Runtime Team
 */
public class DataSchemaImpl implements DataSchema {
    
    private String name;
    private List<SchemaField> fields = new ArrayList<>();
    private SchemaType schemaType = SchemaType.CUSTOM;
    private String schemaContent;
    private String version;
    
    /**
     * Schema字段定义
     */
    public static class SchemaField {
        private String name;
        private String type;
        private Integer size;
        private Boolean nullable;
        private String defaultValue;
        
        // Getters and Setters
        public String getName() {
            return name;
        }
        
        public void setName(String name) {
            this.name = name;
        }
        
        public String getType() {
            return type;
        }
        
        public void setType(String type) {
            this.type = type;
        }
        
        public Integer getSize() {
            return size;
        }
        
        public void setSize(Integer size) {
            this.size = size;
        }
        
        public Boolean getNullable() {
            return nullable;
        }
        
        public void setNullable(Boolean nullable) {
            this.nullable = nullable;
        }
        
        public String getDefaultValue() {
            return defaultValue;
        }
        
        public void setDefaultValue(String defaultValue) {
            this.defaultValue = defaultValue;
        }
    }
    
    @Override
    public SchemaType getSchemaType() {
        return schemaType;
    }
    
    public void setSchemaType(SchemaType schemaType) {
        this.schemaType = schemaType;
    }
    
    @Override
    public String getSchemaContent() {
        return schemaContent;
    }
    
    public void setSchemaContent(String schemaContent) {
        this.schemaContent = schemaContent;
    }
    
    @Override
    public boolean validate(Object data) throws ValidationException {
        // 简化实现：总是返回true
        return true;
    }
    
    @Override
    public String getVersion() {
        return version;
    }
    
    @Override
    public void setVersion(String version) {
        this.version = version;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public List<SchemaField> getFields() {
        return fields;
    }
    
    public void setFields(List<SchemaField> fields) {
        this.fields = fields != null ? fields : new ArrayList<>();
    }
}

