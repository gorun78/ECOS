package com.chinacreator.gzcm.runtime.core.datadescription.service.impl;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import com.chinacreator.gzcm.runtime.core.datadescription.discovery.IDataDescriptionDiscovery;
import com.chinacreator.gzcm.runtime.core.datadescription.discovery.impl.DataDescriptionDiscoveryImpl;
import com.chinacreator.gzcm.runtime.core.datadescription.entity.SchemaRegistry;
import com.chinacreator.gzcm.runtime.core.datadescription.model.DataDescription;
import com.chinacreator.gzcm.runtime.core.datadescription.model.DataSchema;
import com.chinacreator.gzcm.runtime.core.datadescription.service.ISchemaRegistryService;
import com.chinacreator.gzcm.runtime.core.datasource.entity.DataSourceEntity;
import com.chinacreator.gzcm.runtime.core.datasource.IDataSourceService;
import com.chinacreator.gzcm.runtime.core.datasource.service.impl.DataSourceServiceImpl;

/**
 * Schema注册服务实现
 * 提供Schema的注册、版本管理、兼容性检查等功能
 * 
 * @author CDRC Runtime Team
 */
public class SchemaRegistryServiceImpl implements ISchemaRegistryService {

    private final Map<String, List<SchemaRegistry>> schemaStore = new HashMap<>();
    
    private final IDataDescriptionDiscovery discoveryService;
    private final IDataSourceService dataSourceService;
    
    public SchemaRegistryServiceImpl() {
        this.discoveryService = new DataDescriptionDiscoveryImpl();
        this.dataSourceService = new DataSourceServiceImpl();
    }
    
    public SchemaRegistryServiceImpl(IDataDescriptionDiscovery discoveryService, IDataSourceService dataSourceService) {
        this.discoveryService = discoveryService;
        this.dataSourceService = dataSourceService;
    }

    @Override
    public SchemaRegistry registerSchema(String subject, DataSchema schema) throws Exception {
        return registerSchema(subject, schema, true);
    }

    @Override
    public SchemaRegistry registerSchema(String subject, DataSchema schema, boolean checkCompatibility) throws Exception {
        if (subject == null || subject.trim().isEmpty()) {
            throw new IllegalArgumentException("Subject cannot be null or empty");
        }
        if (schema == null) {
            throw new IllegalArgumentException("Schema cannot be null");
        }

        List<SchemaRegistry> schemas = schemaStore.computeIfAbsent(subject, k -> new ArrayList<>());

        // 检查兼容性
        String compatibilityLevel = "NONE";
        if (checkCompatibility && !schemas.isEmpty()) {
            SchemaRegistry latest = schemas.get(schemas.size() - 1);
            compatibilityLevel = latest.getCompatibilityLevel() != null ? latest.getCompatibilityLevel() : "NONE";
            
            // 执行兼容性检查
            if (!"NONE".equals(compatibilityLevel)) {
                boolean isCompatible = checkSchemaCompatibility(latest, schema, compatibilityLevel);
                if (!isCompatible) {
                    throw new IllegalArgumentException("Schema is not compatible with previous version. " +
                            "Compatibility level: " + compatibilityLevel);
                }
            }
        }

        // 创建新的Schema注册记录
        SchemaRegistry registry = new SchemaRegistry();
        registry.setId(UUID.randomUUID().toString());
        registry.setSubject(subject);
        registry.setVersion(schemas.size() + 1);
        registry.setSchemaContent(schema.getSchemaContent());
        registry.setSchemaType(schema.getSchemaType() != null ? schema.getSchemaType().name() : "CUSTOM");
        registry.setCompatibilityLevel(compatibilityLevel);
        registry.setCreatedTime(new Date());
        registry.setCreatedBy("system"); // 简化实现
        registry.setModifiedTime(new Date());
        registry.setModifiedBy("system");

        schemas.add(registry);
        return registry;
    }

    @Override
    public SchemaRegistry getSchema(String subject, Integer version) throws Exception {
        if (subject == null || subject.trim().isEmpty()) {
            throw new IllegalArgumentException("Subject cannot be null or empty");
        }
        if (version == null) {
            return getLatestSchema(subject);
        }

        List<SchemaRegistry> schemas = schemaStore.get(subject);
        if (schemas == null || schemas.isEmpty()) {
            return null;
        }

        return schemas.stream()
            .filter(s -> s.getVersion().equals(version))
            .findFirst()
            .orElse(null);
    }

    @Override
    public SchemaRegistry getLatestSchema(String subject) throws Exception {
        if (subject == null || subject.trim().isEmpty()) {
            throw new IllegalArgumentException("Subject cannot be null or empty");
        }

        List<SchemaRegistry> schemas = schemaStore.get(subject);
        if (schemas == null || schemas.isEmpty()) {
            return null;
        }

        return schemas.get(schemas.size() - 1);
    }

    @Override
    public List<Integer> listVersions(String subject) throws Exception {
        if (subject == null || subject.trim().isEmpty()) {
            throw new IllegalArgumentException("Subject cannot be null or empty");
        }

        List<SchemaRegistry> schemas = schemaStore.get(subject);
        if (schemas == null || schemas.isEmpty()) {
            return new ArrayList<>();
        }

        return schemas.stream()
            .map(SchemaRegistry::getVersion)
            .collect(Collectors.toList());
    }

    @Override
    public List<SchemaRegistry> listSchemas(String subject) throws Exception {
        if (subject == null || subject.trim().isEmpty()) {
            throw new IllegalArgumentException("Subject cannot be null or empty");
        }

        List<SchemaRegistry> schemas = schemaStore.get(subject);
        if (schemas == null) {
            return new ArrayList<>();
        }

        return new ArrayList<>(schemas);
    }

    @Override
    public void deleteSchema(String subject, Integer version) throws Exception {
        if (subject == null || subject.trim().isEmpty()) {
            throw new IllegalArgumentException("Subject cannot be null or empty");
        }

        List<SchemaRegistry> schemas = schemaStore.get(subject);
        if (schemas == null || schemas.isEmpty()) {
            return;
        }

        if (version == null) {
            // 删除所有版本
            schemaStore.remove(subject);
        } else {
            // 删除指定版本
            schemas.removeIf(s -> s.getVersion().equals(version));
            if (schemas.isEmpty()) {
                schemaStore.remove(subject);
            }
        }
    }
    
    /**
     * 检查Schema兼容性
     * 
     * @param oldSchema 旧Schema
     * @param newSchema 新Schema
     * @param compatibilityLevel 兼容性级别（BACKWARD, FORWARD, FULL, NONE）
     * @return true表示兼容，false表示不兼容
     */
    private boolean checkSchemaCompatibility(SchemaRegistry oldSchema, DataSchema newSchema, String compatibilityLevel) {
        if (oldSchema == null || newSchema == null) {
            return false;
        }
        
        String oldContent = oldSchema.getSchemaContent();
        String newContent = newSchema.getSchemaContent();
        
        if (oldContent == null || newContent == null) {
            return false;
        }
        
        // 如果内容完全相同，认为兼容
        if (oldContent.equals(newContent)) {
            return true;
        }
        
        // 根据Schema类型进行兼容性检查
        String schemaType = oldSchema.getSchemaType();
        if (schemaType == null) {
            schemaType = "CUSTOM";
        }
        
        try {
            switch (schemaType.toUpperCase()) {
                case "JSON_SCHEMA":
                    return checkJsonSchemaCompatibility(oldContent, newContent, compatibilityLevel);
                case "AVRO":
                    return checkAvroSchemaCompatibility(oldContent, newContent, compatibilityLevel);
                case "PROTOBUF":
                    // Protobuf兼容性检查较复杂，简化实现：允许向后兼容的字段添加
                    return checkProtobufSchemaCompatibility(oldContent, newContent, compatibilityLevel);
                default:
                    // 对于CUSTOM类型，使用简单的字符串比较
                    // 如果兼容性级别不是NONE，则要求内容相同
                    return "NONE".equals(compatibilityLevel) || oldContent.equals(newContent);
            }
        } catch (Exception e) {
            // 兼容性检查出错，默认不允许
            return false;
        }
    }
    
    /**
     * 检查JSON Schema兼容性
     * 简化实现：检查字段的增删改
     */
    private boolean checkJsonSchemaCompatibility(String oldContent, String newContent, String compatibilityLevel) {
        // 简化实现：使用简单的字符串比较和字段提取
        // 实际生产环境应使用JSON Schema库进行完整验证
        
        // BACKWARD兼容：新Schema可以添加可选字段，但不能删除必需字段
        if ("BACKWARD".equals(compatibilityLevel)) {
            // 简化检查：如果新Schema包含旧Schema的所有必需字段，则认为兼容
            // 这里使用简单的字符串包含检查（实际应解析JSON）
            return newContent.contains("required") || !oldContent.contains("required") || 
                   extractRequiredFields(newContent).containsAll(extractRequiredFields(oldContent));
        }
        
        // FORWARD兼容：新Schema可以删除可选字段，但不能添加必需字段
        if ("FORWARD".equals(compatibilityLevel)) {
            // 简化检查：如果旧Schema包含新Schema的所有必需字段，则认为兼容
            return oldContent.contains("required") || !newContent.contains("required") ||
                   extractRequiredFields(oldContent).containsAll(extractRequiredFields(newContent));
        }
        
        // FULL兼容：新Schema和旧Schema必须完全兼容（可以添加/删除可选字段）
        if ("FULL".equals(compatibilityLevel)) {
            // 简化检查：必需字段必须相同
            return extractRequiredFields(oldContent).equals(extractRequiredFields(newContent));
        }
        
        // NONE：不检查兼容性
        return true;
    }
    
    /**
     * 检查Avro Schema兼容性
     * 简化实现：基于字段名称和类型
     */
    private boolean checkAvroSchemaCompatibility(String oldContent, String newContent, String compatibilityLevel) {
        // 简化实现：提取字段名称进行比较
        // 实际应使用Avro库进行完整验证
        
        java.util.Set<String> oldFields = extractFieldNames(oldContent);
        java.util.Set<String> newFields = extractFieldNames(newContent);
        
        if ("BACKWARD".equals(compatibilityLevel)) {
            // 向后兼容：新Schema可以添加字段，但不能删除必需字段
            return newFields.containsAll(oldFields);
        }
        
        if ("FORWARD".equals(compatibilityLevel)) {
            // 向前兼容：新Schema可以删除字段，但不能添加必需字段
            return oldFields.containsAll(newFields);
        }
        
        if ("FULL".equals(compatibilityLevel)) {
            // 完全兼容：字段必须相同
            return oldFields.equals(newFields);
        }
        
        return true;
    }
    
    /**
     * 检查Protobuf Schema兼容性
     */
    private boolean checkProtobufSchemaCompatibility(String oldContent, String newContent, String compatibilityLevel) {
        // Protobuf兼容性规则：
        // - 可以添加新字段（字段号必须唯一）
        // - 可以删除可选字段
        // - 不能删除必需字段
        // - 不能修改字段类型
        
        // 简化实现：使用字段提取和比较
        java.util.Set<String> oldFields = extractFieldNames(oldContent);
        java.util.Set<String> newFields = extractFieldNames(newContent);
        
        if ("BACKWARD".equals(compatibilityLevel) || "FULL".equals(compatibilityLevel)) {
            // 向后兼容或完全兼容：新Schema必须包含所有旧字段
            return newFields.containsAll(oldFields);
        }
        
        if ("FORWARD".equals(compatibilityLevel)) {
            // 向前兼容：旧Schema必须包含所有新字段
            return oldFields.containsAll(newFields);
        }
        
        return true;
    }
    
    /**
     * 从Schema内容中提取必需字段（简化实现）
     */
    private java.util.Set<String> extractRequiredFields(String schemaContent) {
        java.util.Set<String> fields = new java.util.HashSet<>();
        // 简化实现：查找"required"数组中的字段名
        // 实际应使用JSON解析库
        try {
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
            // 解析失败，返回空集合
        }
        return fields;
    }
    
    /**
     * 从Schema内容中提取字段名（简化实现）
     */
    private java.util.Set<String> extractFieldNames(String schemaContent) {
        java.util.Set<String> fields = new java.util.HashSet<>();
        // 简化实现：查找"name"字段
        // 实际应使用相应的Schema解析库
        try {
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"");
            java.util.regex.Matcher matcher = pattern.matcher(schemaContent);
            while (matcher.find()) {
                fields.add(matcher.group(1));
            }
        } catch (Exception e) {
            // 解析失败，返回空集合
        }
        return fields;
    }
}

