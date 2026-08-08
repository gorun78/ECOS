package com.chinacreator.gzcm.engine.ontology.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * FunctionValidator — 表达式白名单验证 + 安全拦截。
 *
 * <p>职责：</p>
 * <ul>
 *   <li>正则匹配禁止模式（Runtime、exec、Class.forName、System.、java. 等）</li>
 *   <li>白名单函数库验证——不在白名单内的函数名拒绝</li>
 *   <li>DAG 无环检查</li>
 *   <li>语法基本结构校验（括号匹配）</li>
 * </ul>
 */
@Component
public class FunctionValidator {

    private static final Logger log = LoggerFactory.getLogger(FunctionValidator.class);

    // ── 白名单函数 ──────────────────────────────────
    public static final Set<String> WHITELISTED_FUNCTIONS = Set.of(
        // 聚合函数
        "SUM", "AVG", "COUNT", "MIN", "MAX",
        // 数学函数
        "ABS", "ROUND", "CEIL", "FLOOR",
        // 条件函数
        "COALESCE", "NULLIF",
        // 字符串函数
        "CONCAT", "UPPER", "LOWER", "TRIM", "SUBSTRING", "LENGTH", "REPLACE",
        // CASE 表达式
        "CASE", "WHEN", "THEN", "ELSE", "END",
        // SQL 关键字（上下文敏感的）
        "WHERE", "AND", "OR", "NOT", "IN", "LIKE", "BETWEEN",
        "CAST", "AS", "DISTINCT",
        "GROUP", "BY", "ORDER", "ASC", "DESC", "HAVING"
    );

    // ── 禁止模式 ────────────────────────────────────
    private static final Pattern[] FORBIDDEN_PATTERNS = {
        Pattern.compile("\\bRuntime\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bexec\\s*\\(", Pattern.CASE_INSENSITIVE),
        Pattern.compile("Class\\.forName", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bSystem\\.", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bjava\\.", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bjavax\\.", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bnew\\s+", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bimport\\s+", Pattern.CASE_INSENSITIVE),
        Pattern.compile("ProcessBuilder", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bProcess\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("getRuntime", Pattern.CASE_INSENSITIVE),
        Pattern.compile("getClass", Pattern.CASE_INSENSITIVE),
        Pattern.compile("forName", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bloadClass\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\beval\\s*\\(", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bScriptEngine", Pattern.CASE_INSENSITIVE),
        Pattern.compile("--", Pattern.CASE_INSENSITIVE),   // SQL 注释注入
        Pattern.compile("/\\*", Pattern.CASE_INSENSITIVE),  // SQL 块注释注入
        Pattern.compile(";\\s*(DROP|DELETE|UPDATE|INSERT|ALTER|CREATE|TRUNCATE|GRANT|REVOKE)",
            Pattern.CASE_INSENSITIVE) // SQL 注入
    };

    // ── 函数名提取正则 ──────────────────────────────
    private static final Pattern FUNCTION_NAME_PATTERN =
        Pattern.compile("\\b([A-Z_][A-Z0-9_]*)\\s*\\(", Pattern.CASE_INSENSITIVE);

    // ── SQL 关键字正则（不计入函数名检查） ──────────
    private static final Pattern KEYWORD_PATTERN = Pattern.compile(
        "\\b(FROM|SELECT|WHERE|AND|OR|NOT|IN|LIKE|BETWEEN|AS|DISTINCT|" +
        "GROUP|BY|ORDER|ASC|DESC|HAVING|ON|IS|NULL|TRUE|FALSE)\\b",
        Pattern.CASE_INSENSITIVE);

    /**
     * 验证表达式安全性。
     *
     * @param expression 原始表达式字符串
     * @return {valid: bool, errors: [String]}
     */
    public Map<String, Object> validate(String expression) {
        Map<String, Object> result = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();

        if (expression == null || expression.isBlank()) {
            errors.add("表达式不能为空");
            result.put("valid", false);
            result.put("errors", errors);
            return result;
        }

        String trimmed = expression.trim();

        // 1. 禁止模式检查
        checkForbiddenPatterns(trimmed, errors);

        // 2. 括号匹配检查
        checkBracketMatching(trimmed, errors);

        // 3. 白名单函数检查
        checkWhitelistedFunctions(trimmed, errors);

        // 4. 基本语法检查
        checkBasicSyntax(trimmed, errors);

        boolean valid = errors.isEmpty();
        result.put("valid", valid);
        result.put("errors", errors);

        if (!valid) {
            log.warn("FunctionValidator rejected expression: {} → errors={}", trimmed, errors);
        }
        return result;
    }

    /**
     * 快速安全扫描——返回第一个禁止模式（用于安全拦截提示）。
     */
    public String quickScan(String expression) {
        if (expression == null) return null;
        for (Pattern p : FORBIDDEN_PATTERNS) {
            Matcher m = p.matcher(expression);
            if (m.find()) {
                return "禁止的操作: " + m.group().trim();
            }
        }
        return null;
    }

    // ── private helpers ────────────────────────────────

    private void checkForbiddenPatterns(String expression, List<String> errors) {
        for (Pattern p : FORBIDDEN_PATTERNS) {
            Matcher m = p.matcher(expression);
            if (m.find()) {
                String matched = m.group().trim();
                errors.add("forbidden: " + matched + " (不安全的操作)");
                return; // 一个禁止模式即拒绝，不继续检查
            }
        }
    }

    private void checkBracketMatching(String expression, List<String> errors) {
        Stack<Character> stack = new Stack<>();
        boolean inString = false;
        char stringChar = 0;

        for (int i = 0; i < expression.length(); i++) {
            char c = expression.charAt(i);

            // 字符串状态处理
            if (!inString && (c == '\'' || c == '"')) {
                inString = true;
                stringChar = c;
                continue;
            }
            if (inString && c == stringChar) {
                // 检查转义
                if (i > 0 && expression.charAt(i - 1) == '\\') {
                    continue;
                }
                inString = false;
                continue;
            }
            if (inString) continue;

            if (c == '(') {
                stack.push(c);
            } else if (c == ')') {
                if (stack.isEmpty()) {
                    errors.add("括号不匹配: 多余的 ')' 位置 " + i);
                    return;
                }
                stack.pop();
            }
        }

        if (!stack.isEmpty()) {
            errors.add("括号不匹配: 缺少 " + stack.size() + " 个 ')'");
        }
    }

    private void checkWhitelistedFunctions(String expression, List<String> errors) {
        String upper = expression.toUpperCase();
        Matcher m = FUNCTION_NAME_PATTERN.matcher(upper);

        while (m.find()) {
            String funcName = m.group(1);
            // 跳过 SQL 关键字（如 FROM、SELECT 等，它们后面不可能有括号调用）
            if (KEYWORD_PATTERN.matcher(funcName).matches()) {
                continue;
            }
            // 函数名最大 20 字符（避免误匹配长标识符）
            if (funcName.length() > 20) {
                continue;
            }
            if (!WHITELISTED_FUNCTIONS.contains(funcName)) {
                errors.add("不支持的操作: " + funcName + "() (不在白名单内)");
                return; // 一个不合法即拒绝
            }
        }
    }

    private void checkBasicSyntax(String expression, List<String> errors) {
        // 检查是否有 FROM 子句（Function 表达式需指定目标表）
        // 注意：FROM 可能出现在字符串中，简单匹配即可
        String upper = expression.toUpperCase();
        if (!upper.contains("FROM")) {
            errors.add("表达式缺少 FROM 子句（需指定数据来源表）");
        }
    }

    /**
     * DAG 无环检查——验证依赖链中无循环引用。
     *
     * @param propertyFuncMap 属性ID → 表达式 映射
     * @return {valid: bool, cycle: [String]} 若有环，cycle 为环路径
     */
    public Map<String, Object> checkCyclicDependency(Map<String, String> propertyFuncMap) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (propertyFuncMap == null || propertyFuncMap.isEmpty()) {
            result.put("valid", true);
            return result;
        }

        // 检测是否有循环依赖（DFS 着色法）
        Set<String> allNodes = propertyFuncMap.keySet();
        Map<String, String> color = new LinkedHashMap<>(); // WHITE/GRAY/BLACK
        for (String node : allNodes) color.put(node, "WHITE");

        for (String node : allNodes) {
            if ("WHITE".equals(color.get(node))) {
                List<String> cycle = new ArrayList<>();
                if (dfsDetectCycle(node, propertyFuncMap, color, cycle)) {
                    result.put("valid", false);
                    result.put("cycle", cycle);
                    result.put("message", "circular dependency: " + String.join(" → ", cycle));
                    return result;
                }
            }
        }

        result.put("valid", true);
        return result;
    }

    private boolean dfsDetectCycle(String node, Map<String, String> funcMap,
                                    Map<String, String> color, List<String> cycle) {
        color.put(node, "GRAY");
        cycle.add(node);

        String expression = funcMap.get(node);
        if (expression != null) {
            // 从表达式中提取引用的其他 Function 属性
            for (String otherNode : funcMap.keySet()) {
                if (otherNode.equals(node)) continue;
                // 简单匹配：表达式中是否引用了其他属性名
                if (expression.contains(otherNode)) {
                    String c = color.get(otherNode);
                    if ("GRAY".equals(c)) {
                        cycle.add(otherNode);
                        return true; // 发现环
                    }
                    if ("WHITE".equals(c)) {
                        if (dfsDetectCycle(otherNode, funcMap, color, cycle)) {
                            return true;
                        }
                    }
                }
            }
        }

        color.put(node, "BLACK");
        cycle.remove(cycle.size() - 1);
        return false;
    }
}
