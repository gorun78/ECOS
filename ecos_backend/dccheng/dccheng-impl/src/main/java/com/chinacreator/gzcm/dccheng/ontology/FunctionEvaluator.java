package com.chinacreator.gzcm.dccheng.ontology;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 计算属性求值引擎 — 支持 EXPRESSION / AGGREGATION / LOOKUP 三种Function。
 *
 * <h3>Function 类型</h3>
 * <ul>
 *   <li><b>EXPRESSION</b>: 同实体内运算，表达式引用当前对象属性名</li>
 *   <li><b>AGGREGATION</b>: 跨link聚合子查询</li>
 *   <li><b>LOOKUP</b>: 简单link单行取值</li>
 * </ul>
 *
 * <h3>错误处理</h3>
 * 计算异常返回 null + log error，不抛500。
 */
@Component
public class FunctionEvaluator {

    private static final Logger log = LoggerFactory.getLogger(FunctionEvaluator.class);

    private final JdbcTemplate jdbc;

    public FunctionEvaluator(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ── 白名单函数 ──────────────────────────────────────────────

    private static final Map<String, Function<List<Object>, Object>> EXPRESSION_FUNCTIONS = new LinkedHashMap<>();

    static {
        EXPRESSION_FUNCTIONS.put("CONCAT", args -> {
            StringBuilder sb = new StringBuilder();
            for (Object a : args) sb.append(a != null ? a.toString() : "");
            return sb.toString();
        });
        EXPRESSION_FUNCTIONS.put("UPPER", args -> checkArgCount(args, 1) ? ((String) args.get(0)).toUpperCase() : null);
        EXPRESSION_FUNCTIONS.put("LOWER", args -> checkArgCount(args, 1) ? ((String) args.get(0)).toLowerCase() : null);
        EXPRESSION_FUNCTIONS.put("ROUND", args -> {
            if (args.size() < 1 || args.get(0) == null) return null;
            double val = toDouble(args.get(0));
            int decimals = args.size() >= 2 ? toInt(args.get(1)) : 0;
            double factor = Math.pow(10, decimals);
            return Math.round(val * factor) / factor;
        });
        EXPRESSION_FUNCTIONS.put("ABS", args -> checkArgCount(args, 1) ? Math.abs(toDouble(args.get(0))) : null);
        EXPRESSION_FUNCTIONS.put("COALESCE", args -> {
            for (Object a : args) { if (a != null && !"".equals(String.valueOf(a).trim())) return a; }
            return null;
        });
    }

    private static boolean checkArgCount(List<Object> args, int expected) {
        return args.size() >= expected && args.get(0) != null;
    }

    // ── 正则 ──────────────────────────────────────────────────

    /** 匹配函数调用: FUNC_NAME(arg1, arg2, ...) */
    private static final Pattern FUNC_CALL = Pattern.compile(
        "(" + String.join("|", EXPRESSION_FUNCTIONS.keySet()) + ")\\(([^)]*)\\)",
        Pattern.CASE_INSENSITIVE
    );

    /** AGGREGATION: 聚合函数(Entity.field) WHERE ... */
    private static final Pattern AGG_PATTERN = Pattern.compile(
        "(SUM|COUNT|AVG|MAX|MIN)\\s*\\(\\s*(\\w+)\\.(\\w+)\\s*\\)\\s*(?:WHERE\\s+(.+))?",
        Pattern.CASE_INSENSITIVE
    );

    /** LOOKUP: LOOKUP(Entity.field BY pk = THIS.fk) */
    private static final Pattern LOOKUP_PATTERN = Pattern.compile(
        "LOOKUP\\s*\\(\\s*(\\w+)\\.(\\w+)\\s+BY\\s+(\\w+)\\s*=\\s*THIS\\.(\\w+)\\s*\\)",
        Pattern.CASE_INSENSITIVE
    );

    // ── 公共方法 ──────────────────────────────────────────────

    /**
     * 计算单个 FUNCTION 属性的值。
     *
     * @param prop       属性定义（含 functionType / functionExpression）
     * @param objectData 当前对象的属性值Map (key=属性名, value=值)
     * @return 计算结果，异常时返回 null
     */
    public Object evaluate(OntologyProperty prop, Map<String, Object> objectData) {
        if (prop.getFunctionType() == null || prop.getFunctionType().isBlank()) return null;
        if (prop.getFunctionExpression() == null || prop.getFunctionExpression().isBlank()) return null;

        String type = prop.getFunctionType().trim().toUpperCase();
        try {
            return switch (type) {
                case "EXPRESSION"  -> evaluateExpression(prop.getFunctionExpression(), objectData);
                case "AGGREGATION" -> evaluateAggregation(prop.getFunctionExpression(), objectData);
                case "LOOKUP"      -> evaluateLookup(prop.getFunctionExpression(), objectData);
                default -> { log.warn("Unknown functionType: {}", prop.getFunctionType()); yield null; }
            };
        } catch (Exception e) {
            log.error("Function evaluation failed for property {} [{}]: {}",
                prop.getCode(), prop.getFunctionType(), e.getMessage());
            return null;
        }
    }

    // ── EXPRESSION ────────────────────────────────────────────

    /**
     * 表达式求值。支持:
     * <ul>
     *   <li>属性引用: 直接用属性名，从 objectData 取值</li>
     *   <li>算术运算: + - * / %</li>
     *   <li>白名单函数: CONCAT/UPPER/LOWER/ROUND/ABS/COALESCE</li>
     *   <li>括号分组: ( )</li>
     * </ul>
     */
    Object evaluateExpression(String expr, Map<String, Object> objectData) {
        if (expr == null || expr.isBlank()) return null;

        // Step 1: 预处理 — 替换属性引用为字面值
        String processed = substitutePropertyRefs(expr, objectData);

        // Step 2: 求值
        return evalSimpleExpr(processed, objectData);
    }

    /**
     * 将表达式中的属性名替换为实际值。
     * 属性名是 [a-zA-Z_][a-zA-Z0-9_]* 且不在函数名列表中。
     */
    private String substitutePropertyRefs(String expr, Map<String, Object> objectData) {
        // 先处理函数调用（提取出来，替换为临时占位符）
        Map<String, String> funcPlaceholders = new LinkedHashMap<>();
        String temp = expr;
        Matcher m = FUNC_CALL.matcher(temp);
        int idx = 0;
        while (m.find()) {
            String placeholder = "__FUNC_" + idx + "__";
            funcPlaceholders.put(placeholder, m.group(0));
            temp = temp.substring(0, m.start()) + placeholder + temp.substring(m.end());
            m = FUNC_CALL.matcher(temp);
            idx++;
        }

        // 替换属性引用
        Pattern propPattern = Pattern.compile("\\b([a-zA-Z_][a-zA-Z0-9_]*)\\b");
        Matcher pm = propPattern.matcher(temp);
        StringBuilder sb = new StringBuilder();
        Set<String> functionNames = EXPRESSION_FUNCTIONS.keySet();
        while (pm.find()) {
            String word = pm.group(1);
            // 跳过数字（科学计数法中的E）、关键字、函数名占位符
            if (word.equals("E")) {
                pm.appendReplacement(sb, "E");
            } else if (functionNames.stream().anyMatch(f -> f.equalsIgnoreCase(word))) {
                pm.appendReplacement(sb, word);
            } else if (word.startsWith("__FUNC_")) {
                pm.appendReplacement(sb, word);
            } else if (objectData.containsKey(word)) {
                Object val = objectData.get(word);
                pm.appendReplacement(sb, val == null ? "0" : Matcher.quoteReplacement(formatValue(val)));
            } else {
                pm.appendReplacement(sb, Matcher.quoteReplacement(word));
            }
        }
        pm.appendTail(sb);
        String withValues = sb.toString();

        // 恢复函数调用
        for (Map.Entry<String, String> e : funcPlaceholders.entrySet()) {
            withValues = withValues.replace(e.getKey(), e.getValue());
        }

        return withValues;
    }

    private String formatValue(Object val) {
        if (val instanceof Number) {
            double d = ((Number) val).doubleValue();
            if (d == (long) d) return String.valueOf((long) d);
            return String.valueOf(d);
        }
        if (val instanceof String) {
            // 字符串在算术表达式中尝试转为数字
            String s = (String) val;
            try {
                return String.valueOf(Double.parseDouble(s));
            } catch (NumberFormatException ex) {
                return "0";
            }
        }
        return "0";
    }

    /**
     * 简单的算术表达式求值器（递归下降）。
     * 支持: + - * / %, 括号, 函数调用
     */
    private Object evalSimpleExpr(String expr, Map<String, Object> objectData) {
        // 先处理函数调用
        String processed = expandFunctions(expr);
        // 递归下降求值
        double[] result = new double[]{0};
        int[] pos = new int[]{0};
        result[0] = parseExpression(processed, pos);
        return result[0];
    }

    private String expandFunctions(String expr) {
        StringBuilder sb = new StringBuilder();
        Matcher m = FUNC_CALL.matcher(expr);
        while (m.find()) {
            String funcName = m.group(1).toUpperCase();
            String argsStr = m.group(2);
            List<Object> args = parseFunctionArgs(argsStr);
            Function<List<Object>, Object> fn = EXPRESSION_FUNCTIONS.get(funcName);
            if (fn != null) {
                try {
                    Object result = fn.apply(args);
                    m.appendReplacement(sb, result == null ? "0" : Matcher.quoteReplacement(formatValue(result)));
                } catch (Exception e) {
                    m.appendReplacement(sb, "0");
                }
            } else {
                m.appendReplacement(sb, "0");
            }
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private List<Object> parseFunctionArgs(String argsStr) {
        List<Object> args = new ArrayList<>();
        int depth = 0;
        StringBuilder current = new StringBuilder();
        for (char c : argsStr.toCharArray()) {
            if (c == '(') depth++;
            if (c == ')') depth--;
            if (c == ',' && depth == 0) {
                args.add(parseArgValue(current.toString().trim()));
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        String last = current.toString().trim();
        if (!last.isEmpty()) {
            args.add(parseArgValue(last));
        }
        return args;
    }

    private Object parseArgValue(String s) {
        if (s == null || s.isEmpty()) return null;
        try {
            if (s.contains(".")) return Double.parseDouble(s);
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return s.replaceAll("^\"|\"$", "");
        }
    }

    // ── 递归下降表达式解析器 ─────────────────────────────────

    private double parseExpression(String s, int[] pos) {
        double left = parseTerm(s, pos);
        while (pos[0] < s.length()) {
            char op = s.charAt(pos[0]);
            if (op == '+' || op == '-') {
                pos[0]++;
                double right = parseTerm(s, pos);
                if (op == '+') left += right;
                else left -= right;
            } else {
                break;
            }
        }
        return left;
    }

    private double parseTerm(String s, int[] pos) {
        double left = parseFactor(s, pos);
        while (pos[0] < s.length()) {
            char op = s.charAt(pos[0]);
            if (op == '*' || op == '/' || op == '%') {
                pos[0]++;
                double right = parseFactor(s, pos);
                if (op == '*') left *= right;
                else if (op == '/') left = right != 0 ? left / right : 0;
                else left %= right;
            } else {
                break;
            }
        }
        return left;
    }

    private double parseFactor(String s, int[] pos) {
        skipWhitespace(s, pos);
        if (pos[0] >= s.length()) return 0;

        char c = s.charAt(pos[0]);
        if (c == '(') {
            pos[0]++; // skip '('
            double val = parseExpression(s, pos);
            skipWhitespace(s, pos);
            if (pos[0] < s.length() && s.charAt(pos[0]) == ')') pos[0]++; // skip ')'
            return val;
        }
        if (c == '+' || c == '-') {
            pos[0]++;
            double val = parseFactor(s, pos);
            return c == '-' ? -val : val;
        }

        // number
        int start = pos[0];
        while (pos[0] < s.length() &&
               (Character.isDigit(s.charAt(pos[0])) || s.charAt(pos[0]) == '.')) {
            pos[0]++;
        }
        if (pos[0] > start) {
            return Double.parseDouble(s.substring(start, pos[0]));
        }

        // skip unknown char
        pos[0]++;
        return 0;
    }

    private void skipWhitespace(String s, int[] pos) {
        while (pos[0] < s.length() && Character.isWhitespace(s.charAt(pos[0]))) {
            pos[0]++;
        }
    }

    // ── AGGREGATION ───────────────────────────────────────────

    /**
     * 聚合求值。
     * 表达式格式: "SUM(Entity.field) WHERE Entity.fk = ?"
     * 其中 ? 会被替换为当前对象的对应字段值。
     */
    Object evaluateAggregation(String expr, Map<String, Object> objectData) {
        Matcher m = AGG_PATTERN.matcher(expr.trim());
        if (!m.matches()) {
            log.warn("Invalid AGGREGATION expression: {}", expr);
            return null;
        }

        String func = m.group(1).toUpperCase();
        String entity = m.group(2);
        String field = m.group(3);
        String whereClause = m.group(4);

        StringBuilder sql = new StringBuilder();
        sql.append("SELECT ").append(func).append("(").append(field).append(")");
        sql.append(" FROM ").append(entity);

        if (whereClause != null && !whereClause.isBlank()) {
            // 替换 ? 为当前对象的值
            List<Object> params = new ArrayList<>();
            String resolvedWhere = resolveWhereParams(whereClause, objectData, params);
            sql.append(" WHERE ").append(resolvedWhere);

            try {
                return jdbc.queryForObject(sql.toString(), params.toArray(), Object.class);
            } catch (Exception e) {
                log.error("AGGREGATION query failed: {} — {}", sql, e.getMessage());
                return null;
            }
        }

        try {
            return jdbc.queryForObject(sql.toString(), Object.class);
        } catch (Exception e) {
            log.error("AGGREGATION query failed: {} — {}", sql, e.getMessage());
            return null;
        }
    }

    // ── LOOKUP ────────────────────────────────────────────────

    /**
     * LOOKUP 求值。
     * 表达式格式: "LOOKUP(Entity.field BY pk = THIS.fk)"
     */
    Object evaluateLookup(String expr, Map<String, Object> objectData) {
        Matcher m = LOOKUP_PATTERN.matcher(expr.trim());
        if (!m.matches()) {
            log.warn("Invalid LOOKUP expression: {}", expr);
            return null;
        }

        String entity = m.group(1);
        String targetField = m.group(2);
        String pkColumn = m.group(3);
        String fkField = m.group(4); // THIS.{fkField}

        Object fkValue = objectData.get(fkField);
        if (fkValue == null) {
            log.debug("LOOKUP: fk field '{}' is null for entity {}", fkField, entity);
            return null;
        }

        String sql = "SELECT " + targetField + " FROM " + entity + " WHERE " + pkColumn + " = ? LIMIT 1";
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(sql, fkValue);
            if (rows.isEmpty()) return null;
            return rows.get(0).values().iterator().next();
        } catch (Exception e) {
            log.error("LOOKUP query failed: {} — {}", sql, e.getMessage());
            return null;
        }
    }

    // ── 辅助方法 ──────────────────────────────────────────────

    /**
     * 解析 WHERE 子句中的 ? 占位符，替换为当前对象的字段值。
     * 返回参数化后的 WHERE 子句，同时填充 params 列表。
     */
    private String resolveWhereParams(String whereClause, Map<String, Object> objectData, List<Object> params) {
        // 策略: 将 ? 替换为 ? (保持参数化)，但收集需要绑定的值
        // 简化: 直接将 ? 替换为对象的主键/id值
        StringBuilder sb = new StringBuilder();
        int qIdx = 0;
        for (char c : whereClause.toCharArray()) {
            if (c == '?') {
                qIdx++;
                // 简单策略: 第一个 ? 用 id 字段，后续用顺序匹配
                // MVP: 先从 objectData 按属性查找
                sb.append('?');
            } else {
                sb.append(c);
            }
        }

        // 收集参数: 将 WHERE 中的实体字段引用解析为当前对象的值
        // 例如: "Contract.supplier_id = ?" — 需要找到 supplier_id 对应 objectData 中的字段
        // MVP简化: 直接将所有 ? 绑定到当前对象的属性
        Pattern fieldRef = Pattern.compile("(\\w+)\\.(\\w+)\\s*=\\s*\\?");
        Matcher fm = fieldRef.matcher(whereClause);
        while (fm.find()) {
            String colName = fm.group(2); // the foreign key column name
            // Try to find the matching field in objectData
            Object val = objectData.get(colName);
            if (val == null) {
                // Try common FK mappings: entity_id -> id
                val = findMatchingFK(colName, objectData);
            }
            params.add(val != null ? val : objectData.get("id"));
        }

        // If no params collected, use id as fallback
        if (params.isEmpty()) {
            for (int i = 0; i < qIdx; i++) {
                params.add(objectData.get("id"));
            }
        }

        return sb.toString();
    }

    /**
     * 尝试将 FK 列名映射到 objectData 中的值。
     */
    private Object findMatchingFK(String colName, Map<String, Object> objectData) {
        // 直接匹配
        if (objectData.containsKey(colName)) return objectData.get(colName);
        // 尝试去掉 _id 后缀
        if (colName.endsWith("_id")) {
            String stem = colName.substring(0, colName.length() - 3);
            if (objectData.containsKey(stem)) return objectData.get(stem);
        }
        // 返回 id 作为默认值
        return objectData.get("id");
    }

    private static double toDouble(Object val) {
        if (val instanceof Number) return ((Number) val).doubleValue();
        try { return Double.parseDouble(String.valueOf(val)); }
        catch (NumberFormatException e) { return 0; }
    }

    private static int toInt(Object val) {
        if (val instanceof Number) return ((Number) val).intValue();
        try { return Integer.parseInt(String.valueOf(val)); }
        catch (NumberFormatException e) { return 0; }
    }

    // ── 批量计算 ───────────────────────────────────────────────

    /**
     * 对一批 object 数据计算所有 FUNCTION 属性的值，并将结果注入 objects。
     *
     * @param entityCode  实体代码
     * @param objects     对象列表（会被原地修改，注入计算属性值）
     */
    public void computeAndInject(String entityCode, List<Map<String, Object>> objects) {
        if (objects == null || objects.isEmpty()) return;

        // 查询该 entity 的所有 FUNCTION 属性
        List<OntologyProperty> funcProps = findFunctionProperties(entityCode);
        if (funcProps.isEmpty()) return;

        for (Map<String, Object> obj : objects) {
            for (OntologyProperty prop : funcProps) {
                try {
                    Object computed = evaluate(prop, obj);
                    obj.put(prop.getCode(), computed);
                } catch (Exception e) {
                    log.error("Failed to compute function property {} for entity {}: {}",
                        prop.getCode(), entityCode, e.getMessage());
                }
            }
        }
    }

    /**
     * 查询指定 entity 的所有 FUNCTION 类型属性。
     */
    private List<OntologyProperty> findFunctionProperties(String entityCode) {
        try {
            return jdbc.query(
                "SELECT p.* FROM ecos_ontology_property p " +
                "JOIN ecos_ontology_entity e ON p.entity_id = e.id " +
                "WHERE e.code = ? AND p.function_type IS NOT NULL AND p.function_type <> ''",
                (rs, rn) -> {
                    OntologyProperty p = new OntologyProperty();
                    p.setId(rs.getString("id"));
                    p.setEntityId(rs.getString("entity_id"));
                    p.setCode(rs.getString("code"));
                    p.setName(rs.getString("name"));
                    p.setPropertyType(rs.getString("property_type"));
                    p.setFunctionType(rs.getString("function_type"));
                    p.setFunctionExpression(rs.getString("function_expression"));
                    return p;
                }, entityCode);
        } catch (Exception e) {
            log.warn("Failed to find function properties for entity {}: {}", entityCode, e.getMessage());
            return List.of();
        }
    }
}
