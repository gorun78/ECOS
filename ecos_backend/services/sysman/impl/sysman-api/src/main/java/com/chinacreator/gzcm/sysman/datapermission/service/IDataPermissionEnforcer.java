package com.chinacreator.gzcm.sysman.datapermission.service;

import java.util.List;
import java.util.Map;

/**
 * 数据权限执行器：
 * - 基于当前用户上下文和策略，对 SQL 与结果集应用数据权限。
 */
public interface IDataPermissionEnforcer {

    /**
     * 对查询 SQL 应用行级、列级、动态策略，返回重写后的 SQL。
     *
     * @param resource    资源标识（表/视图/业务编码）
     * @param originalSql 原始 SQL
     * @param context     上下文（用户/租户/时间等变量）
     */
    String rewriteQuerySql(String resource, String originalSql, Map<String, Object> context) throws Exception;

    /**
     * 对结果集应用字段脱敏。
     *
     * @param results 查询结果列表（实体或 DTO 对象）
     */
    void applyMasking(List<?> results);
    
    /**
     * 应用字段级权限：对结果集中的特定字段进行过滤或脱敏
     * 
     * @param results 查询结果列表（实体或 DTO 对象）
     * @param resource 资源标识（表/视图/业务编码）
     * @param context 上下文（用户/租户/时间等变量）
     */
    void applyFieldLevelPermissions(List<?> results, String resource, Map<String, Object> context);
}


