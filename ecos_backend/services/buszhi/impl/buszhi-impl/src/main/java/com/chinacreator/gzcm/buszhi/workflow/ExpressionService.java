package com.chinacreator.gzcm.buszhi.workflow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 条件表达式评估引擎 — 支持 ${variables.xxx} 和 ${object.xxx} 语法。
 * <p>
 * 基于 Spring Expression Language (SpEL) 实现条件分支判断。
 */
@Component
public class ExpressionService {

    private static final Logger log = LoggerFactory.getLogger(ExpressionService.class);

    /** 变量占位符模式: ${variables.xxx} 或 ${object.xxx} 或 ${context.xxx} */
    private static final Pattern VAR_PATTERN = Pattern.compile("\\$\\{(\\w+(?:\\.\\w+)*)\\}");

    private final ExpressionParser parser = new SpelExpressionParser();

    /**
     * 评估条件表达式（用于条件网关分支判断）。
     *
     * @param expression SpEL 表达式，如 "${variables.riskLevel} == 'HIGH'" 
     * @param variables  运行时变量 Map
     * @return boolean 评估结果
     */
    public boolean evaluateCondition(String expression, Map<String, Object> variables) {
        if (expression == null || expression.isBlank()) return false;

        try {
            // 1. 替换 ${xxx} 占位符为字面值
            String resolved = resolvePlaceholders(expression, variables);

            // 2. SpEL 求值
            StandardEvaluationContext ctx = new StandardEvaluationContext();
            ctx.setVariable("variables", variables.get("variables") != null ? variables.get("variables") : variables);
            ctx.setVariable("object", variables.get("object") != null ? variables.get("object") : variables);
            ctx.setVariable("context", variables.get("context") != null ? variables.get("context") : new HashMap<>());

            // 也把顶层变量暴露
            for (Map.Entry<String, Object> entry : variables.entrySet()) {
                ctx.setVariable(entry.getKey(), entry.getValue());
            }

            Boolean result = parser.parseExpression(resolved).getValue(ctx, Boolean.class);
            log.debug("Expression '{}' → '{}' → {}", expression, resolved, result);
            return result != null && result;
        } catch (Exception e) {
            log.warn("Failed to evaluate expression '{}': {}", expression, e.getMessage());
            return false;
        }
    }

    /**
     * 解析字符串中的 ${xxx} 占位符，替换为实际值。
     */
    public String resolvePlaceholders(String template, Map<String, Object> variables) {
        if (template == null || !template.contains("${")) return template;

        Matcher matcher = VAR_PATTERN.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String path = matcher.group(1);
            Object value = resolvePath(path, variables);
            String replacement = value != null ? toLiteral(value) : "null";
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * 解析嵌套路径 e.g. "variables.riskLevel"
     */
    private Object resolvePath(String path, Map<String, Object> root) {
        if (root == null) return null;
        String[] parts = path.split("\\.");
        Object current = root;
        for (String part : parts) {
            if (current instanceof Map) {
                current = ((Map<?, ?>) current).get(part);
            } else {
                return null;
            }
        }
        return current;
    }

    /** 将值转为 SpEL 字面量 */
    private String toLiteral(Object value) {
        if (value == null) return "null";
        if (value instanceof Number || value instanceof Boolean) return value.toString();
        return "'" + value.toString().replace("'", "\\'") + "'";
    }

    /**
     * 批量解析 Map 中的占位符。
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> resolveMap(Map<String, Object> input, Map<String, Object> context) {
        Map<String, Object> result = new HashMap<>();
        for (Map.Entry<String, Object> entry : input.entrySet()) {
            if (entry.getValue() instanceof String s) {
                result.put(entry.getKey(), resolvePlaceholders(s, context));
            } else if (entry.getValue() instanceof Map) {
                result.put(entry.getKey(), resolveMap((Map<String, Object>) entry.getValue(), context));
            } else {
                result.put(entry.getKey(), entry.getValue());
            }
        }
        return result;
    }
}
