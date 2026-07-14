package com.chinacreator.gzcm.runtime.core.datapermission.service.impl;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.chinacreator.gzcm.sysman.datapermission.ColumnLevelSecurityService;
import com.chinacreator.gzcm.sysman.datapermission.DataMaskingService;
import com.chinacreator.gzcm.sysman.datapermission.DynamicPolicyService;
import com.chinacreator.gzcm.sysman.datapermission.RowLevelSecurityService;
import com.chinacreator.gzcm.sysman.datapermission.RowLevelSecurityService.DataPermissionException;
import com.chinacreator.gzcm.sysman.datapermission.entity.DataPermissionPolicy;
import com.chinacreator.gzcm.sysman.datapermission.model.ColumnLevelPolicy;
import com.chinacreator.gzcm.sysman.datapermission.model.DynamicPolicy;
import com.chinacreator.gzcm.sysman.datapermission.model.RowLevelPolicy;
import com.chinacreator.gzcm.sysman.datapermission.service.IDataPermissionEnforcer;
import com.chinacreator.gzcm.sysman.datapermission.service.IDataPermissionPolicyService;

/**
 * 数据权限执行器实现：基于统一策略表和四种子服务，完成：
 * - SQL 重写（行级 + 列级 + 动态策略）
 * - 结果集脱敏
 */
public class DataPermissionEnforcerImpl implements IDataPermissionEnforcer {

    private final IDataPermissionPolicyService policyService;
    private final RowLevelSecurityService rowLevelSecurityService;
    private final ColumnLevelSecurityService columnLevelSecurityService;
    private final DynamicPolicyService dynamicPolicyService;
    private final DataMaskingService dataMaskingService;

    public DataPermissionEnforcerImpl(IDataPermissionPolicyService policyService,
                                      RowLevelSecurityService rowLevelSecurityService,
                                      ColumnLevelSecurityService columnLevelSecurityService,
                                      DynamicPolicyService dynamicPolicyService,
                                      DataMaskingService dataMaskingService) {
        this.policyService = policyService;
        this.rowLevelSecurityService = rowLevelSecurityService;
        this.columnLevelSecurityService = columnLevelSecurityService;
        this.dynamicPolicyService = dynamicPolicyService;
        this.dataMaskingService = dataMaskingService;
    }

    @Override
    public String rewriteQuerySql(String resource, String originalSql, Map<String, Object> context) throws Exception {
        if (resource == null || originalSql == null) {
            return originalSql;
        }
        Map<String, Object> cond = new HashMap<String, Object>();
        cond.put("resource", resource);
        List<DataPermissionPolicy> all = policyService.listPolicies(cond);
        if (all == null || all.isEmpty()) {
            return originalSql;
        }

        List<RowLevelPolicy> rowPolicies = new ArrayList<RowLevelPolicy>();
        List<ColumnLevelPolicy> colPolicies = new ArrayList<ColumnLevelPolicy>();
        List<DynamicPolicy> dynPolicies = new ArrayList<DynamicPolicy>();
        // 字段级策略：用于结果集后处理
        List<DataPermissionPolicy> fieldPolicies = new ArrayList<DataPermissionPolicy>();

        for (DataPermissionPolicy p : all) {
            if (p == null || p.getPolicyType() == null) {
                continue;
            }
            String type = p.getPolicyType();
            if ("ROW".equalsIgnoreCase(type)) {
                RowLevelPolicy rp = new RowLevelPolicy();
                rp.setPolicyId(p.getPolicyId());
                rp.setPolicyName(p.getPolicyName());
                rp.setResource(p.getResource());
                rp.setConditionExpression(p.getPolicyCondition());
                rowPolicies.add(rp);
            } else if ("COLUMN".equalsIgnoreCase(type)) {
                // 简化：policyCondition 以逗号分隔列名
                ColumnLevelPolicy cp = new ColumnLevelPolicy();
                cp.setPolicyId(p.getPolicyId());
                cp.setPolicyName(p.getPolicyName());
                cp.setResource(p.getResource());
                if (p.getPolicyCondition() != null) {
                    String[] cols = p.getPolicyCondition().split(",");
                    List<String> list = new ArrayList<String>();
                    for (String c : cols) {
                        if (c != null && c.trim().length() > 0) {
                            list.add(c.trim());
                        }
                    }
                    cp.setAllowedColumns(list);
                }
                colPolicies.add(cp);
            } else if ("FIELD".equalsIgnoreCase(type)) {
                // 字段级策略：在结果集返回后应用
                fieldPolicies.add(p);
            } else if ("DYNAMIC".equalsIgnoreCase(type)) {
                DynamicPolicy dp = new DynamicPolicy();
                dp.setPolicyId(p.getPolicyId());
                dp.setPolicyName(p.getPolicyName());
                dp.setResource(p.getResource());
                dp.setConditionExpression(p.getPolicyCondition());
                dynPolicies.add(dp);
            }
        }

        String sql = originalSql;
        // 1) 行级策略
        if (!rowPolicies.isEmpty()) {
            sql = rowLevelSecurityService.applyPolicies(sql, rowPolicies, context);
        }
        // 2) 动态策略（构建表达式并注入 WHERE）
        if (!dynPolicies.isEmpty()) {
            String dynExpr = dynamicPolicyService.buildCondition(dynPolicies, context);
            if (dynExpr != null && dynExpr.trim().length() > 0) {
                List<RowLevelPolicy> tmp = new ArrayList<RowLevelPolicy>();
                RowLevelPolicy rp = new RowLevelPolicy();
                rp.setConditionExpression(dynExpr);
                tmp.add(rp);
                sql = rowLevelSecurityService.applyPolicies(sql, tmp, context);
            }
        }
        // 3) 列级策略
        if (!colPolicies.isEmpty()) {
            sql = columnLevelSecurityService.applyPolicies(sql, colPolicies);
        }
        return sql;
    }

    @Override
    public void applyMasking(List<?> results) {
        if (results == null || results.isEmpty() || dataMaskingService == null) {
            return;
        }
        for (Object obj : results) {
            dataMaskingService.maskObject(obj);
        }
    }
    
    /**
     * 应用字段级权限：对结果集中的特定字段进行过滤或脱敏
     * @param results 查询结果集
     * @param resource 资源标识
     * @param context 上下文信息（包含用户信息等）
     */
    public void applyFieldLevelPermissions(List<?> results, String resource, Map<String, Object> context) {
        if (results == null || results.isEmpty() || resource == null) {
            return;
        }
        
        try {
            // 获取字段级策略
            Map<String, Object> cond = new HashMap<String, Object>();
            cond.put("resource", resource);
            cond.put("policyType", "FIELD");
            List<DataPermissionPolicy> fieldPolicies = policyService.listPolicies(cond);
            
            if (fieldPolicies == null || fieldPolicies.isEmpty()) {
                return;
            }
            
            // 解析字段级策略：policyCondition格式为JSON，例如：
            // {"fields": ["phone", "email"], "action": "MASK", "maskType": "PARTIAL"}
            // {"fields": ["salary"], "action": "HIDE"}
            for (DataPermissionPolicy policy : fieldPolicies) {
                applyFieldPolicy(results, policy, context);
            }
        } catch (Exception e) {
            // 记录日志但不中断处理
            System.err.println("应用字段级权限失败: " + e.getMessage());
        }
    }
    
    /**
     * 应用单个字段级策略
     */
    private void applyFieldPolicy(List<?> results, DataPermissionPolicy policy, Map<String, Object> context) {
        if (policy.getPolicyCondition() == null || policy.getPolicyCondition().trim().isEmpty()) {
            return;
        }
        
        try {
            // 简化实现：假设policyCondition为JSON格式
            // 实际可以使用Jackson等JSON库解析
            String condition = policy.getPolicyCondition();
            
            // 解析字段列表和操作类型
            // 简化处理：假设格式为 "field1,field2:MASK" 或 "field1,field2:HIDE"
            String[] parts = condition.split(":");
            if (parts.length < 2) {
                return;
            }
            
            String[] fields = parts[0].split(",");
            String action = parts[1].trim();
            
            for (Object obj : results) {
                if (obj == null) {
                    continue;
                }
                
                // 使用反射处理字段
                applyFieldAction(obj, fields, action);
            }
        } catch (Exception e) {
            System.err.println("应用字段策略失败: " + e.getMessage());
        }
    }
    
    /**
     * 对对象应用字段操作
     */
    private void applyFieldAction(Object obj, String[] fields, String action) {
        if (obj == null || fields == null || action == null) {
            return;
        }
        
        try {
            Class<?> clazz = obj.getClass();
            
            for (String fieldName : fields) {
                fieldName = fieldName.trim();
                if (fieldName.isEmpty()) {
                    continue;
                }
                
                // 获取字段（支持驼峰命名）
                java.lang.reflect.Field field = null;
                try {
                    field = clazz.getDeclaredField(fieldName);
                } catch (NoSuchFieldException e) {
                    // 尝试驼峰命名转换
                    String camelCase = toCamelCase(fieldName);
                    try {
                        field = clazz.getDeclaredField(camelCase);
                    } catch (NoSuchFieldException ex) {
                        continue; // 字段不存在，跳过
                    }
                }
                
                if (field == null) {
                    continue;
                }
                
                field.setAccessible(true);
                
                if ("HIDE".equalsIgnoreCase(action)) {
                    // 隐藏字段：设置为null
                    field.set(obj, null);
                } else if ("MASK".equalsIgnoreCase(action)) {
                    // 脱敏字段：使用脱敏服务
                    Object value = field.get(obj);
                    if (value != null && dataMaskingService != null) {
                        // 对字段值进行脱敏 - 使用默认的前缀和后缀保留位数（3, 4）
                        String maskedValue = dataMaskingService.maskValue("custom", value.toString(), 3, 4);
                        field.set(obj, maskedValue);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("应用字段操作失败: " + e.getMessage());
        }
    }
    
    /**
     * 转换为驼峰命名
     */
    private String toCamelCase(String fieldName) {
        if (fieldName == null || fieldName.isEmpty()) {
            return fieldName;
        }
        // 简单实现：假设已经是驼峰命名或下划线命名
        if (fieldName.contains("_")) {
            String[] parts = fieldName.split("_");
            StringBuilder sb = new StringBuilder(parts[0].toLowerCase());
            for (int i = 1; i < parts.length; i++) {
                if (parts[i].length() > 0) {
                    sb.append(parts[i].substring(0, 1).toUpperCase());
                    if (parts[i].length() > 1) {
                        sb.append(parts[i].substring(1).toLowerCase());
                    }
                }
            }
            return sb.toString();
        }
        return fieldName;
    }
}


