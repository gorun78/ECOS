package com.chinacreator.gzcm.runtime.core.datadescription.service.impl;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.chinacreator.gzcm.runtime.core.database.ISystemDatabaseAccess;
import com.chinacreator.gzcm.runtime.core.datadescription.entity.SchemaRegistry;
import com.chinacreator.gzcm.runtime.core.datadescription.model.DataSchema;
import com.chinacreator.gzcm.runtime.core.datadescription.service.ISchemaRegistryService;

/**
 * 数据库版本的Schema注册服务实现
 * 使用 ISystemDatabaseAccess 访问数据库持久化
 * 
 * @author CDRC Runtime Team
 */
public class DatabaseSchemaRegistryServiceImpl implements ISchemaRegistryService {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseSchemaRegistryServiceImpl.class);
    
    private static final String TABLE_NAME = "td_schema_registry";
    
    private final ISystemDatabaseAccess databaseAccess;
    
    public DatabaseSchemaRegistryServiceImpl(ISystemDatabaseAccess databaseAccess) {
        this.databaseAccess = databaseAccess;
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
        
        try {
            // 获取当前版本号
            int newVersion = 1;
            SchemaRegistry latest = getLatestSchema(subject);
            if (latest != null) {
                newVersion = latest.getVersion() + 1;
                
                // 兼容性检查
                if (checkCompatibility) {
                    String compatLevel = latest.getCompatibilityLevel();
                    if (compatLevel == null) {
                        compatLevel = "BACKWARD";
                    }
                    if (!checkSchemaCompatibility(latest.getSchemaContent(), schema, compatLevel)) {
                        throw new Exception("Schema is not compatible with the current version");
                    }
                }
            }
            
            // 创建新的Schema注册记录
            SchemaRegistry registry = new SchemaRegistry();
            registry.setId(UUID.randomUUID().toString());
            registry.setSubject(subject);
            registry.setVersion(newVersion);
            registry.setSchemaContent(schema.getSchemaContent());
            registry.setSchemaType(schema.getSchemaType() != null ? schema.getSchemaType().name() : null);
            registry.setCompatibilityLevel("BACKWARD");
            registry.setCreatedTime(new Date());
            
            // 转换为数据库实体并保存
            Map<String, Object> entity = convertToEntity(registry);
            databaseAccess.insert(TABLE_NAME, entity);
            
            logger.info("Registered schema: subject={}, version={}", subject, newVersion);
            return registry;
            
        } catch (ISystemDatabaseAccess.DatabaseAccessException e) {
            logger.error("Failed to register schema: subject={}", subject, e);
            throw new Exception("Failed to register schema: " + e.getMessage(), e);
        }
    }
    
    @Override
    public SchemaRegistry getLatestSchema(String subject) throws Exception {
        try {
            String sql = "SELECT * FROM " + TABLE_NAME + 
                        " WHERE SUBJECT = ? ORDER BY VERSION DESC LIMIT 1";
            List<Map<String, Object>> results = databaseAccess.executeQuery(sql, subject);
            if (results.isEmpty()) {
                return null;
            }
            return convertToSchemaRegistry(results.get(0));
        } catch (ISystemDatabaseAccess.DatabaseAccessException e) {
            logger.error("Failed to get latest schema: subject={}", subject, e);
            throw new Exception("Failed to get latest schema: " + e.getMessage(), e);
        }
    }
    
    @Override
    public SchemaRegistry getSchema(String subject, Integer version) throws Exception {
        try {
            if (version == null) {
                return getLatestSchema(subject);
            }
            String sql = "SELECT * FROM " + TABLE_NAME + 
                        " WHERE SUBJECT = ? AND VERSION = ?";
            List<Map<String, Object>> results = databaseAccess.executeQuery(sql, subject, version);
            if (results.isEmpty()) {
                return null;
            }
            return convertToSchemaRegistry(results.get(0));
        } catch (ISystemDatabaseAccess.DatabaseAccessException e) {
            logger.error("Failed to get schema: subject={}, version={}", subject, version, e);
            throw new Exception("Failed to get schema: " + e.getMessage(), e);
        }
    }
    
    @Override
    public List<Integer> listVersions(String subject) throws Exception {
        try {
            String sql = "SELECT VERSION FROM " + TABLE_NAME + 
                        " WHERE SUBJECT = ? ORDER BY VERSION ASC";
            List<Map<String, Object>> results = databaseAccess.executeQuery(sql, subject);
            List<Integer> versions = new ArrayList<>();
            for (Map<String, Object> row : results) {
                Object versionObj = row.get("VERSION");
                if (versionObj instanceof Number) {
                    versions.add(((Number) versionObj).intValue());
                }
            }
            return versions;
        } catch (ISystemDatabaseAccess.DatabaseAccessException e) {
            logger.error("Failed to list versions: subject={}", subject, e);
            throw new Exception("Failed to list versions: " + e.getMessage(), e);
        }
    }
    
    @Override
    public List<SchemaRegistry> listSchemas(String subject) throws Exception {
        try {
            String sql = "SELECT * FROM " + TABLE_NAME + 
                        " WHERE SUBJECT = ? ORDER BY VERSION ASC";
            List<Map<String, Object>> results = databaseAccess.executeQuery(sql, subject);
            List<SchemaRegistry> schemas = new ArrayList<>();
            for (Map<String, Object> row : results) {
                schemas.add(convertToSchemaRegistry(row));
            }
            return schemas;
        } catch (ISystemDatabaseAccess.DatabaseAccessException e) {
            logger.error("Failed to list schemas: subject={}", subject, e);
            throw new Exception("Failed to list schemas: " + e.getMessage(), e);
        }
    }
    
    @Override
    public void deleteSchema(String subject, Integer version) throws Exception {
        try {
            String sql;
            if (version == null) {
                // 删除所有版本
                sql = "DELETE FROM " + TABLE_NAME + " WHERE SUBJECT = ?";
                databaseAccess.executeUpdate(sql, subject);
                logger.info("Deleted all schemas for subject: {}", subject);
            } else {
                // 删除指定版本
                sql = "DELETE FROM " + TABLE_NAME + " WHERE SUBJECT = ? AND VERSION = ?";
                databaseAccess.executeUpdate(sql, subject, version);
                logger.info("Deleted schema: subject={}, version={}", subject, version);
            }
        } catch (ISystemDatabaseAccess.DatabaseAccessException e) {
            logger.error("Failed to delete schema: subject={}, version={}", subject, version, e);
            throw new Exception("Failed to delete schema: " + e.getMessage(), e);
        }
    }
    
    // ========== 辅助方法 ==========
    
    /**
     * 获取所有 subjects
     */
    public List<String> getAllSubjects() throws Exception {
        try {
            String sql = "SELECT DISTINCT SUBJECT FROM " + TABLE_NAME + " ORDER BY SUBJECT";
            List<Map<String, Object>> results = databaseAccess.executeQuery(sql);
            List<String> subjects = new ArrayList<>();
            for (Map<String, Object> row : results) {
                Object subjectObj = row.get("SUBJECT");
                if (subjectObj != null) {
                    subjects.add(subjectObj.toString());
                }
            }
            return subjects;
        } catch (ISystemDatabaseAccess.DatabaseAccessException e) {
            logger.error("Failed to get all subjects", e);
            throw new Exception("Failed to get all subjects: " + e.getMessage(), e);
        }
    }
    
    /**
     * 检查兼容性
     */
    public boolean checkCompatibility(String subject, DataSchema schema) throws Exception {
        SchemaRegistry latest = getLatestSchema(subject);
        if (latest == null) {
            return true;
        }
        String level = latest.getCompatibilityLevel();
        if (level == null) {
            level = "BACKWARD";
        }
        return checkSchemaCompatibility(latest.getSchemaContent(), schema, level);
    }
    
    /**
     * 设置兼容性级别
     */
    public void setCompatibilityLevel(String subject, String level) throws Exception {
        try {
            String sql = "UPDATE " + TABLE_NAME + 
                        " SET COMPATIBILITY_LEVEL = ?, MODIFIED_TIME = ? WHERE SUBJECT = ?";
            databaseAccess.executeUpdate(sql, level, new Timestamp(System.currentTimeMillis()), subject);
            logger.info("Set compatibility level: subject={}, level={}", subject, level);
        } catch (ISystemDatabaseAccess.DatabaseAccessException e) {
            logger.error("Failed to set compatibility level: subject={}", subject, e);
            throw new Exception("Failed to set compatibility level: " + e.getMessage(), e);
        }
    }
    
    /**
     * 获取兼容性级别
     */
    public String getCompatibilityLevel(String subject) throws Exception {
        SchemaRegistry latest = getLatestSchema(subject);
        return latest != null ? latest.getCompatibilityLevel() : "BACKWARD";
    }
    
    // ========== 私有方法 ==========
    
    private Map<String, Object> convertToEntity(SchemaRegistry registry) {
        Map<String, Object> entity = new HashMap<>();
        entity.put("ID", registry.getId());
        entity.put("SUBJECT", registry.getSubject());
        entity.put("VERSION", registry.getVersion());
        entity.put("SCHEMA_CONTENT", registry.getSchemaContent());
        entity.put("SCHEMA_TYPE", registry.getSchemaType());
        entity.put("COMPATIBILITY_LEVEL", registry.getCompatibilityLevel());
        entity.put("CREATED_TIME", registry.getCreatedTime() != null ? 
            new Timestamp(registry.getCreatedTime().getTime()) : new Timestamp(System.currentTimeMillis()));
        entity.put("CREATED_BY", registry.getCreatedBy());
        entity.put("MODIFIED_TIME", new Timestamp(System.currentTimeMillis()));
        return entity;
    }
    
    private SchemaRegistry convertToSchemaRegistry(Map<String, Object> row) {
        SchemaRegistry registry = new SchemaRegistry();
        registry.setId((String) row.get("ID"));
        registry.setSubject((String) row.get("SUBJECT"));
        
        Object versionObj = row.get("VERSION");
        if (versionObj instanceof Number) {
            registry.setVersion(((Number) versionObj).intValue());
        }
        
        registry.setSchemaContent((String) row.get("SCHEMA_CONTENT"));
        registry.setSchemaType((String) row.get("SCHEMA_TYPE"));
        registry.setCompatibilityLevel((String) row.get("COMPATIBILITY_LEVEL"));
        
        Object createdTimeObj = row.get("CREATED_TIME");
        if (createdTimeObj instanceof Timestamp) {
            registry.setCreatedTime(new Date(((Timestamp) createdTimeObj).getTime()));
        }
        
        registry.setCreatedBy((String) row.get("CREATED_BY"));
        
        return registry;
    }
    
    private boolean checkSchemaCompatibility(String oldSchemaContent, DataSchema newSchema, String level) {
        if (oldSchemaContent == null || newSchema == null) {
            return true;
        }
        
        String newSchemaContent = newSchema.getSchemaContent();
        if (newSchemaContent == null) {
            return true;
        }
        
        switch (level.toUpperCase()) {
            case "NONE":
                return true;
            case "BACKWARD":
                // 简化的向后兼容检查：新Schema内容不为空
                return !newSchemaContent.isEmpty();
            case "FORWARD":
                // 简化的向前兼容检查：旧Schema内容不为空
                return !oldSchemaContent.isEmpty();
            case "FULL":
                // 双向兼容
                return !newSchemaContent.isEmpty() && !oldSchemaContent.isEmpty();
            default:
                return true;
        }
    }
}
