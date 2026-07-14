package com.chinacreator.gzcm.runtime.core.datapermission;

import java.util.List;
import java.util.Map;

import com.chinacreator.gzcm.sysman.datapermission.RowLevelSecurityService.DataPermissionException;
import com.chinacreator.gzcm.sysman.datapermission.model.RowLevelPolicy;
import com.chinacreator.gzcm.sysman.datapermission.RowLevelSecurityService;

/**
 * 行级数据权限的简单实现：
 * - 使用占位符 ${key} 从上下文中取值并替换到策略条件中；
 * - 将多个策略条件通过 AND 连接后，拼接到原始 SQL 的 WHERE 子句中；
 * - 对 ORDER BY / GROUP BY / LIMIT 做简单分割，尽量保持原 SQL 结构。
 *
 * 注意：这是一个轻量级 SQL 重写实现，适合作为 3.3.1 策略下推引擎的前置能力，
 * 后续可替换为更加完善的 SQL Parser。
 */
public class RowLevelSecurityServiceImpl implements RowLevelSecurityService {

    @Override
    public String applyPolicies(String originalSql,
                                List<RowLevelPolicy> policies,
                                Map<String, Object> context) throws DataPermissionException {
        if (originalSql == null) {
            throw new DataPermissionException("原始SQL不能为空");
        }
        if (policies == null || policies.isEmpty()) {
            return originalSql;
        }
        StringBuilder combined = new StringBuilder();
        for (RowLevelPolicy policy : policies) {
            if (policy == null || policy.getConditionExpression() == null) {
                continue;
            }
            String expr = resolvePlaceholders(policy.getConditionExpression(), context);
            if (expr == null || expr.trim().isEmpty()) {
                continue;
            }
            if (combined.length() > 0) {
                combined.append(" AND ");
            }
            combined.append('(').append(expr).append(')');
        }
        if (combined.length() == 0) {
            return originalSql;
        }
        return injectWhere(originalSql, combined.toString());
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
                        // 简单按字符串直接替换，调用方需自行保证转义
                        sb.append(String.valueOf(val));
                    } else {
                        // 未找到变量时保留原样，避免破坏表达式
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

    private String injectWhere(String sql, String condition) {
        String lower = sql.toLowerCase();
        int orderIdx = indexOfKeyword(lower, " order by ");
        int groupIdx = indexOfKeyword(lower, " group by ");
        int limitIdx = indexOfKeyword(lower, " limit ");
        int splitIdx = minPositive(orderIdx, groupIdx, limitIdx);

        String mainPart;
        String tailPart;
        if (splitIdx > 0) {
            mainPart = sql.substring(0, splitIdx);
            tailPart = sql.substring(splitIdx);
        } else {
            mainPart = sql;
            tailPart = "";
        }

        String mainLower = mainPart.toLowerCase();
        int whereIdx = indexOfKeyword(mainLower, " where ");
        if (whereIdx < 0) {
            // 无 WHERE，直接追加
            StringBuilder sb = new StringBuilder();
            sb.append(mainPart).append(" WHERE ").append(condition).append(tailPart);
            return sb.toString();
        } else {
            // 已有 WHERE，在末尾追加 AND 条件
            StringBuilder sb = new StringBuilder();
            sb.append(mainPart).append(" AND ").append(condition).append(tailPart);
            return sb.toString();
        }
    }

    private int indexOfKeyword(String lowerSql, String keyword) {
        return lowerSql.indexOf(keyword);
    }

    private int minPositive(int... idxs) {
        int min = -1;
        for (int idx : idxs) {
            if (idx >= 0 && (min < 0 || idx < min)) {
                min = idx;
            }
        }
        return min;
    }
}


