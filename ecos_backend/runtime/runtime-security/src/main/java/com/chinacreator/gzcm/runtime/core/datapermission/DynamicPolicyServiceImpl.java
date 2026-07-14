package com.chinacreator.gzcm.runtime.core.datapermission;

import java.util.List;
import java.util.Map;

import com.chinacreator.gzcm.sysman.datapermission.model.DynamicPolicy;
import com.chinacreator.gzcm.sysman.datapermission.DynamicPolicyService;
import com.chinacreator.gzcm.sysman.datapermission.RowLevelSecurityService;

/**
 * 动态策略服务简单实现：
 * - 与 RowLevelSecurityService 使用相同的占位符解析规则（${key}）；
 * - 将多个策略条件用 AND 连接后返回，供上层注入到 SQL 中。
 */
public class DynamicPolicyServiceImpl implements DynamicPolicyService {

    @Override
    public String buildCondition(List<DynamicPolicy> policies, Map<String, Object> context) {
        if (policies == null || policies.isEmpty()) {
            return null;
        }
        StringBuilder combined = new StringBuilder();
        for (DynamicPolicy p : policies) {
            if (p == null || p.getConditionExpression() == null) {
                continue;
            }
            String expr = resolvePlaceholders(p.getConditionExpression(), context);
            if (expr == null || expr.trim().isEmpty()) {
                continue;
            }
            if (combined.length() > 0) {
                combined.append(" AND ");
            }
            combined.append('(').append(expr).append(')');
        }
        return combined.length() == 0 ? null : combined.toString();
    }

    private String resolvePlaceholders(String expression, Map<String, Object> context) {
        if (expression == null || context == null || context.isEmpty()) {
            return expression;
        }
        StringBuilder sb = new StringBuilder();
        char[] chars = expression.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];
            if (c == '$' && i + 1 < chars.length && chars[i + 1] == '{') {
                int end = expression.indexOf('}', i + 2);
                if (end > i + 2) {
                    String key = expression.substring(i + 2, end).trim();
                    Object val = context.get(key);
                    if (val != null) {
                        sb.append(String.valueOf(val));
                    } else {
                        sb.append(expression, i, end + 1);
                    }
                    i = end;
                    continue;
                }
            }
            sb.append(c);
        }
        return sb.toString();
    }
}


