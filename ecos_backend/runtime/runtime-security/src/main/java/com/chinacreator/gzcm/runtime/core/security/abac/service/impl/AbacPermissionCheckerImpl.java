package com.chinacreator.gzcm.runtime.core.security.abac.service.impl;


import java.util.Comparator;

import java.util.List;

import java.util.Map;

import java.util.Objects;

import com.chinacreator.gzcm.sysman.abac.cache.AbacPolicyCacheService;
import com.chinacreator.gzcm.sysman.abac.model.AbacContext;
import com.chinacreator.gzcm.sysman.abac.model.AbacPolicy;
import com.chinacreator.gzcm.sysman.abac.service.IAbacPermissionChecker;
import com.chinacreator.gzcm.sysman.abac.service.IAbacPolicyService;

/**
 * 极简 ABAC 权限检查实现：
 * - 仅支持在 subject/resource/action/environment map 中按 key 取值，与条件表达式中的简单 equals/!= 比较。
 * - 条件表达式格式示例：subject.department == "IT" && resource.sensitivity != "HIGH"
 * - 后续可替换为 Aviator / SpEL 等表达式引擎。
 */
public class AbacPermissionCheckerImpl implements IAbacPermissionChecker {

    private final IAbacPolicyService policyService;
    private final AbacPolicyCacheService cacheService;

    public AbacPermissionCheckerImpl(IAbacPolicyService policyService, AbacPolicyCacheService cacheService) {
        this.policyService = policyService;
        this.cacheService = cacheService;
    }

    @Override
    public Decision check(AbacContext context) throws PolicyEvaluationException {
        try {
            // 先从缓存获取策略列表
            List<AbacPolicy> policies = cacheService != null ? cacheService.getAllPolicies() : null;
            
            // 如果缓存为空或过期，从服务加载并刷新缓存
            if (policies == null || policies.isEmpty()) {
                policies = policyService.listPolicies();
                if (cacheService != null && policies != null) {
                    cacheService.refreshAll(policies);
                }
            }
            
            if (policies == null || policies.isEmpty()) {
                return Decision.NOT_APPLICABLE;
            }
            
            // 按优先级排序（高优先级先评估）
            policies.sort(Comparator.comparing(p -> p.getPriority() == null ? 0 : p.getPriority(), Comparator.reverseOrder()));

            Decision finalDecision = Decision.NOT_APPLICABLE;
            for (AbacPolicy policy : policies) {
                if (!matches(policy, context)) {
                    continue;
                }
                
                // DENY策略立即返回，不允许覆盖
                if ("DENY".equalsIgnoreCase(policy.getEffect())) {
                    return Decision.DENY;
                }
                
                // ALLOW策略记录，但继续检查是否有更高优先级的DENY
                if ("ALLOW".equalsIgnoreCase(policy.getEffect())) {
                    finalDecision = Decision.PERMIT;
                }
            }
            
            return finalDecision;
        } catch (IAbacPolicyService.AbacException e) {
            throw new PolicyEvaluationException("加载策略失败: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new PolicyEvaluationException("策略评估失败: " + e.getMessage(), e);
        }
    }

    private boolean matches(AbacPolicy policy, AbacContext ctx) {
        return eval(policy.getSubjectCondition(), ctx.getSubject())
            && eval(policy.getResourceCondition(), ctx.getResource())
            && eval(policy.getActionCondition(), ctx.getAction())
            && eval(policy.getEnvironmentCondition(), ctx.getEnvironment());
    }

    /**
     * 增强的条件解析：支持 "key == value" 和 "key != value" 以及 "&&" 和 "||" 连接。
     * namespace.key 形式会忽略 namespace，仅在 map 中用最后一段 key 查找。
     * 支持数值比较：">", ">=", "<", "<="
     * 支持包含判断：contains, startsWith, endsWith
     */
    private boolean eval(String expr, Map<String, Object> attrs) {
        if (expr == null || expr.trim().isEmpty()) {
            return true;
        }
        if (attrs == null) {
            return false;
        }
        
        // 处理OR逻辑（优先级低于AND）
        if (expr.contains("||")) {
            String[] orParts = expr.split("\\|\\|");
            for (String orPart : orParts) {
                if (evalAndExpression(orPart.trim(), attrs)) {
                    return true; // OR逻辑：任一为真即返回真
                }
            }
            return false;
        }
        
        // 处理AND逻辑
        return evalAndExpression(expr, attrs);
    }
    
    /**
     * 评估AND表达式
     */
    private boolean evalAndExpression(String expr, Map<String, Object> attrs) {
        String[] parts = expr.split("&&");
        for (String raw : parts) {
            String s = raw.trim();
            if (s.isEmpty()) continue;
            
            if (!evalSingleCondition(s, attrs)) {
                return false; // AND逻辑：任一为假即返回假
            }
        }
        return true;
    }
    
    /**
     * 评估单个条件
     */
    private boolean evalSingleCondition(String condition, Map<String, Object> attrs) {
        condition = condition.trim();
        if (condition.isEmpty()) {
            return true;
        }
        
        // 支持多种比较操作符
        String[] operators = {"!=", "==", ">=", "<=", ">", "<", "contains", "startsWith", "endsWith"};
        String operator = null;
        int operatorIndex = -1;
        
        for (String op : operators) {
            int index = condition.indexOf(op);
            if (index > 0) {
                operator = op;
                operatorIndex = index;
                break;
            }
        }
        
        if (operator == null) {
            // 没有找到操作符，可能是简单的key存在性检查
            String key = extractKey(condition);
            return attrs.containsKey(key) && attrs.get(key) != null;
        }
        
        String key = extractKey(condition.substring(0, operatorIndex));
        String value = stripQuotes(condition.substring(operatorIndex + operator.length()).trim());
        Object actual = attrs.get(key);
        
        if (actual == null) {
            return false;
        }
        
        String actualStr = Objects.toString(actual, null);
        
        switch (operator) {
            case "==":
                return actualStr.equals(value);
            case "!=":
                return !actualStr.equals(value);
            case ">":
                return compareNumbers(actualStr, value) > 0;
            case ">=":
                return compareNumbers(actualStr, value) >= 0;
            case "<":
                return compareNumbers(actualStr, value) < 0;
            case "<=":
                return compareNumbers(actualStr, value) <= 0;
            case "contains":
                return actualStr.contains(value);
            case "startsWith":
                return actualStr.startsWith(value);
            case "endsWith":
                return actualStr.endsWith(value);
            default:
                return false;
        }
    }
    
    /**
     * 提取key（支持namespace.key格式）
     */
    private String extractKey(String keyExpr) {
        String key = keyExpr.trim();
        // 支持 namespace.key，取最后一段
        if (key.contains(".")) {
            key = key.substring(key.lastIndexOf('.') + 1);
        }
        return key;
    }
    
    /**
     * 数值比较
     */
    private int compareNumbers(String actual, String expected) {
        try {
            double actualNum = Double.parseDouble(actual);
            double expectedNum = Double.parseDouble(expected);
            return Double.compare(actualNum, expectedNum);
        } catch (NumberFormatException e) {
            // 如果不是数字，按字符串比较
            return actual.compareTo(expected);
        }
    }

    private String stripQuotes(String v) {
        if ((v.startsWith("\"") && v.endsWith("\"")) || (v.startsWith("'") && v.endsWith("'"))) {
            return v.substring(1, v.length() - 1);
        }
        return v;
    }
}


