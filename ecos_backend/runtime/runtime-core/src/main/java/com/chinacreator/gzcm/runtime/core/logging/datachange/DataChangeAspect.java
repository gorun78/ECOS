package com.chinacreator.gzcm.runtime.core.logging.datachange;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * 数据变更AOP切面
 * 用于自动拦截数据变更操作并记录日志
 * 
 * 使用说明：
 * 1. 如果使用Spring AOP，可以添加@Aspect注解
 * 2. 配置切点表达式拦截数据库操作方法
 * 3. 在切面中调用DataChangeInterceptor记录变更
 * 
 * @author CDRC Runtime Team
 */
public class DataChangeAspect {
    
    private final DataChangeInterceptor interceptor;
    
    public DataChangeAspect(DataChangeInterceptor interceptor) {
        this.interceptor = interceptor;
    }
    
    /**
     * 拦截插入操作
     * 切点表达式示例：@Pointcut("execution(* com.chinacreator.gzcm.runtime.core.database.ISystemDatabaseAccess.insert(..))")
     */
    public void interceptInsert(String tableName, Object entity) {
        try {
            // 提取记录ID
            String recordId = extractRecordId(entity);
            
            // 提取数据
            Map<String, Object> data = interceptor.extractData(entity);
            
            // 记录变更
            interceptor.interceptInsert(tableName, recordId, data);
        } catch (Exception e) {
            System.err.println("记录插入日志失败: " + e.getMessage());
        }
    }
    
    /**
     * 拦截更新操作
     * 切点表达式示例：@Pointcut("execution(* com.chinacreator.gzcm.runtime.core.database.ISystemDatabaseAccess.update(..))")
     */
    public void interceptUpdate(String tableName, Object entity) {
        try {
            // 提取记录ID
            String recordId = extractRecordId(entity);
            
            // 查询更新前的数据
            Map<String, Object> beforeData = queryBeforeData(tableName, recordId);
            
            // 提取更新后的数据
            Map<String, Object> afterData = interceptor.extractData(entity);
            
            // 记录变更
            interceptor.interceptUpdate(tableName, recordId, beforeData, afterData);
        } catch (Exception e) {
            System.err.println("记录更新日志失败: " + e.getMessage());
        }
    }
    
    /**
     * 拦截删除操作
     * 切点表达式示例：@Pointcut("execution(* com.chinacreator.gzcm.runtime.core.database.ISystemDatabaseAccess.delete(..))")
     */
    public void interceptDelete(String tableName, String primaryKey) {
        try {
            // 查询删除前的数据
            Map<String, Object> data = queryBeforeData(tableName, primaryKey);
            
            // 记录变更
            interceptor.interceptDelete(tableName, primaryKey, data);
        } catch (Exception e) {
            System.err.println("记录删除日志失败: " + e.getMessage());
        }
    }
    
    /**
     * 提取记录ID
     */
    private String extractRecordId(Object entity) {
        if (entity == null) {
            return null;
        }
        
        // 尝试通过反射获取主键字段
        try {
            // 常见的ID字段名
            String[] idFieldNames = {"id", "recordId", "primaryKey", "logId", "taskId"};
            
            for (String fieldName : idFieldNames) {
                try {
                    Method getter = entity.getClass().getMethod("get" + capitalize(fieldName));
                    Object value = getter.invoke(entity);
                    if (value != null) {
                        return value.toString();
                    }
                } catch (Exception e) {
                    // 继续尝试下一个字段
                }
            }
        } catch (Exception e) {
            // 忽略异常
        }
        
        return null;
    }
    
    /**
     * 查询更新/删除前的数据
     */
    private Map<String, Object> queryBeforeData(String tableName, String recordId) {
        if (tableName == null || recordId == null) {
            return new HashMap<>();
        }
        
        try {
            // 通过数据库访问接口查询
            // 这里需要根据实际的实体类型来查询
            // 简化实现：返回空Map，实际应该查询数据库
            return new HashMap<>();
        } catch (Exception e) {
            System.err.println("查询变更前数据失败: " + e.getMessage());
            return new HashMap<>();
        }
    }
    
    /**
     * 首字母大写
     */
    private String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
    
    /**
     * 设置当前上下文（从ThreadLocal或请求上下文获取）
     */
    public void setContext(String userId, String tenantId, String traceId, String auditLogId) {
        interceptor.setContext(userId, tenantId, traceId, auditLogId);
    }
}

