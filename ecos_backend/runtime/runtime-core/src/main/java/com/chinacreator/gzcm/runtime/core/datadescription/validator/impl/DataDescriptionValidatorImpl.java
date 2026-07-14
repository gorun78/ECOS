package com.chinacreator.gzcm.runtime.core.datadescription.validator.impl;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.chinacreator.gzcm.runtime.core.datadescription.model.DataDescription;
import com.chinacreator.gzcm.runtime.core.datadescription.model.DataMetadata;
import com.chinacreator.gzcm.runtime.core.datadescription.model.DataSchema;
import com.chinacreator.gzcm.runtime.core.datadescription.validator.IDataDescriptionValidator;

/**
 * 数据描述验证服务实现
 * 提供数据描述和数据验证功能
 * 
 * @author CDRC Runtime Team
 */
public class DataDescriptionValidatorImpl implements IDataDescriptionValidator {
    
    @Override
    public ValidationResult validateDescription(DataDescription description) throws Exception {
        List<String> errors = new ArrayList<>();
        
        if (description == null) {
            return new ValidationResult(false, "DataDescription cannot be null", errors);
        }
        
        // 验证数据类型
        if (description.getDataType() == null) {
            errors.add("DataType is required");
        }
        
        // 验证元数据
        DataMetadata metadata = description.getMetadata();
        if (metadata == null) {
            errors.add("DataMetadata is required");
        } else {
            if (metadata.getName() == null || metadata.getName().trim().isEmpty()) {
                errors.add("DataMetadata name is required");
            }
            if (metadata.getType() == null || metadata.getType().trim().isEmpty()) {
                errors.add("DataMetadata type is required");
            }
        }
        
        // 验证Schema（如果存在）
        DataSchema schema = description.getSchema();
        if (schema != null) {
            try {
                // 验证Schema内容
                String schemaContent = schema.getSchemaContent();
                if (schemaContent == null || schemaContent.trim().isEmpty()) {
                    errors.add("Schema content is required when schema is provided");
                } else {
                    // 验证Schema格式（简化实现）
                    if (!isValidSchemaFormat(schemaContent, schema.getSchemaType())) {
                        errors.add("Invalid schema format for type: " + schema.getSchemaType());
                    }
                }
            } catch (Exception e) {
                errors.add("Schema validation error: " + e.getMessage());
            }
        }
        
        // 调用DataDescription的validate方法
        try {
            if (!description.validate()) {
                errors.add("DataDescription internal validation failed");
            }
        } catch (DataDescription.ValidationException e) {
            errors.add("DataDescription validation exception: " + e.getMessage());
        }
        
        boolean valid = errors.isEmpty();
        String message = valid ? "DataDescription is valid" : "DataDescription validation failed";
        
        return new ValidationResult(valid, message, errors);
    }
    
    @Override
    public ValidationResult validateData(DataDescription description, Object data) throws Exception {
        List<String> errors = new ArrayList<>();
        
        if (description == null) {
            return new ValidationResult(false, "DataDescription cannot be null", errors);
        }
        
        if (data == null) {
            return new ValidationResult(false, "Data cannot be null", errors);
        }
        
        // 根据数据类型进行验证
        if (description.getDataType() != null) {
            switch (description.getDataType()) {
                case STRUCTURED:
                    errors.addAll(validateStructuredData(description, data));
                    break;
                case FILE:
                    errors.addAll(validateFileData(description, data));
                    break;
                case SERVICE:
                    errors.addAll(validateServiceData(description, data));
                    break;
                case STREAM:
                    errors.addAll(validateStreamingData(description, data));
                    break;
                default:
                    errors.add("Unsupported data type: " + description.getDataType());
            }
        }
        
        // 使用Schema验证数据
        DataSchema schema = description.getSchema();
        if (schema != null) {
            try {
                if (!schema.validate(data)) {
                    errors.add("Data does not conform to schema");
                }
            } catch (DataSchema.ValidationException e) {
                errors.add("Schema validation failed: " + e.getMessage());
            }
        }
        
        // 调用DataDescription的validateData方法
        try {
            if (!description.validateData(data)) {
                errors.add("DataDescription internal validation failed");
            }
        } catch (DataDescription.ValidationException e) {
            errors.add("DataDescription validation exception: " + e.getMessage());
        }
        
        boolean valid = errors.isEmpty();
        String message = valid ? "Data is valid" : "Data validation failed";
        
        return new ValidationResult(valid, message, errors);
    }
    
    /**
     * 验证结构化数据
     */
    private List<String> validateStructuredData(DataDescription description, Object data) {
        List<String> errors = new ArrayList<>();
        
        if (data instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) data;
            
            // 检查必需字段
            DataSchema schema = description.getSchema();
            if (schema != null) {
                // 简化实现：检查Schema中标记为required的字段
                String schemaContent = schema.getSchemaContent();
                if (schemaContent != null && schemaContent.contains("\"required\"")) {
                    // 提取必需字段（简化实现）
                    List<String> requiredFields = extractRequiredFields(schemaContent);
                    for (String field : requiredFields) {
                        if (!map.containsKey(field) || map.get(field) == null) {
                            errors.add("Required field missing: " + field);
                        }
                    }
                }
            }
        } else if (data instanceof List) {
            @SuppressWarnings("unchecked")
            List<Object> list = (List<Object>) data;
            // 验证列表中的每个元素
            for (int i = 0; i < list.size(); i++) {
                List<String> itemErrors = validateStructuredData(description, list.get(i));
                for (String error : itemErrors) {
                    errors.add("Item[" + i + "]: " + error);
                }
            }
        } else {
            errors.add("Structured data must be a Map or List, got: " + data.getClass().getName());
        }
        
        return errors;
    }
    
    /**
     * 验证文件数据
     */
    private List<String> validateFileData(DataDescription description, Object data) {
        List<String> errors = new ArrayList<>();
        
        if (data instanceof File) {
            File file = (File) data;
            
            // 检查文件是否存在
            if (!file.exists()) {
                errors.add("File does not exist: " + file.getPath());
            }
            
            // 检查文件大小
            DataMetadata metadata = description.getMetadata();
            if (metadata != null && metadata.getSize() != null) {
                if (file.length() > metadata.getSize()) {
                    errors.add("File size exceeds limit: " + file.length() + " > " + metadata.getSize());
                }
            }
            
            // 检查文件格式
            if (metadata != null && metadata.getFormat() != null) {
                String fileName = file.getName();
                String expectedFormat = metadata.getFormat().toLowerCase();
                String actualFormat = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
                if (!expectedFormat.equals(actualFormat)) {
                    errors.add("File format mismatch: expected " + expectedFormat + ", got " + actualFormat);
                }
            }
        } else if (data instanceof String) {
            // 字符串路径
            String filePath = (String) data;
            File file = new File(filePath);
            errors.addAll(validateFileData(description, file));
        } else {
            errors.add("File data must be a File or String path, got: " + data.getClass().getName());
        }
        
        return errors;
    }
    
    /**
     * 验证服务接口数据
     */
    private List<String> validateServiceData(DataDescription description, Object data) {
        List<String> errors = new ArrayList<>();
        
        // 简化实现：服务接口数据验证
        if (data instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) data;
            
            // 检查基本字段
            if (!map.containsKey("url") && !map.containsKey("endpoint")) {
                errors.add("Service data must contain 'url' or 'endpoint'");
            }
        } else {
            errors.add("Service data must be a Map, got: " + data.getClass().getName());
        }
        
        return errors;
    }
    
    /**
     * 验证流数据
     */
    private List<String> validateStreamingData(DataDescription description, Object data) {
        List<String> errors = new ArrayList<>();
        
        // 简化实现：流数据验证
        if (data instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) data;
            
            // 检查基本字段
            if (!map.containsKey("topic") && !map.containsKey("stream")) {
                errors.add("Streaming data must contain 'topic' or 'stream'");
            }
        } else {
            errors.add("Streaming data must be a Map, got: " + data.getClass().getName());
        }
        
        return errors;
    }
    
    /**
     * 验证Schema格式
     */
    private boolean isValidSchemaFormat(String schemaContent, DataSchema.SchemaType schemaType) {
        if (schemaContent == null || schemaContent.trim().isEmpty()) {
            return false;
        }
        
        switch (schemaType) {
            case JSON_SCHEMA:
                // 简化验证：检查是否为有效的JSON格式
                return schemaContent.trim().startsWith("{") && schemaContent.trim().endsWith("}");
            case AVRO:
                // 简化验证：检查是否包含Avro特征
                return schemaContent.contains("\"type\"") || schemaContent.contains("record");
            case PROTOBUF:
                // 简化验证：检查是否包含protobuf特征
                return schemaContent.contains("message") || schemaContent.contains("syntax");
            case XML_SCHEMA:
                // 简化验证：检查是否为XML格式
                return schemaContent.trim().startsWith("<") && schemaContent.trim().endsWith(">");
            case CUSTOM:
                // 自定义Schema，总是认为有效
                return true;
            default:
                return false;
        }
    }
    
    /**
     * 从Schema内容中提取必需字段
     */
    private List<String> extractRequiredFields(String schemaContent) {
        List<String> fields = new ArrayList<>();
        
        try {
            // 简化实现：使用正则表达式提取required数组中的字段名
            int requiredIndex = schemaContent.indexOf("\"required\"");
            if (requiredIndex >= 0) {
                int start = schemaContent.indexOf("[", requiredIndex);
                int end = schemaContent.indexOf("]", start);
                if (start >= 0 && end > start) {
                    String requiredArray = schemaContent.substring(start + 1, end);
                    // 提取引号中的字段名
                    java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\"([^\"]+)\"");
                    java.util.regex.Matcher matcher = pattern.matcher(requiredArray);
                    while (matcher.find()) {
                        fields.add(matcher.group(1));
                    }
                }
            }
        } catch (Exception e) {
            // 解析失败，返回空列表
        }
        
        return fields;
    }
}

