package com.chinacreator.gzcm.aimod;

import java.util.*;
import java.util.regex.Pattern;

/**
 * ObjectQL 查询解析器 — 将 JSON 查询 DSL 转为参数化 SQL。
 *
 * <h3>语法</h3>
 * <pre>{@code
 * {
 *   "entity": "Supplier",               // 表名（必填，仅允许 [a-zA-Z_][a-zA-Z0-9_]*）
 *   "fields": ["id", "name"],            // 字段列表（可选，默认 SELECT *）
 *   "filter": {                          // 单条件过滤（可选）
 *     "field": "creditScore",
 *     "op": ">=",
 *     "value": 80
 *   },
 *   "filters": [                         // 多条件过滤（可选，与 filter 互斥）
 *     {"field": "creditScore", "op": ">=", "value": 80},
 *     {"field": "status", "op": "=", "value": "active", "logic": "OR"}
 *   ],
 *   "sort": "creditScore DESC",          // 排序（可选）
 *   "limit": 10,                         // 限制行数（可选，默认 100）
 *   "offset": 0                          // 偏移量（可选，默认 0）
 * }
 * }</pre>
 *
 * <h3>支持的操作符</h3>
 * {@code =, !=, >, <, >=, <=, LIKE, NOT LIKE, IN, NOT IN, IS NULL, IS NOT NULL}
 *
 * <h3>安全约束</h3>
 * <ul>
 *   <li>entity/field 名正则校验，拒绝特殊字符</li>
 *   <li>值通过 PreparedStatement 参数化，防 SQL 注入</li>
 *   <li>操作符白名单校验</li>
 * </ul>
 */
public final class ObjectQLParser {

    private static final Pattern IDENTIFIER = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*$");
    private static final Set<String> ALLOWED_OPS = Set.of(
        "=", "!=", "<>", ">", "<", ">=", "<=",
        "LIKE", "NOT LIKE", "IN", "NOT IN",
        "IS NULL", "IS NOT NULL"
    );
    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_LIMIT = 1000;

    private ObjectQLParser() {}

    /**
     * 解析 ObjectQL JSON 字符串，生成 {@link ParsedQuery}。
     *
     * @param json 查询 JSON
     * @return 含 SQL 模板 + 参数列表的解析结果
     * @throws IllegalArgumentException JSON 格式错误或校验失败
     */
    @SuppressWarnings("unchecked")
    public static ParsedQuery parse(String json) {
        Map<String, Object> root;
        try {
            root = parseJsonFlat(json);
        } catch (Exception e) {
            throw new IllegalArgumentException("无效的 ObjectQL JSON: " + e.getMessage(), e);
        }

        // 1. entity — 必填
        String entity = string(root, "entity");
        if (entity == null || entity.isBlank()) {
            throw new IllegalArgumentException("entity 为必填字段");
        }
        if (!IDENTIFIER.matcher(entity).matches()) {
            throw new IllegalArgumentException("非法的 entity 名称: " + entity);
        }

        // 2. fields
        List<String> fields = null;
        Object fieldsObj = root.get("fields");
        if (fieldsObj instanceof List) {
            fields = new ArrayList<>();
            for (Object f : (List<?>) fieldsObj) {
                String fn = String.valueOf(f).trim();
                if (!IDENTIFIER.matcher(fn).matches()) {
                    throw new IllegalArgumentException("非法的字段名: " + fn);
                }
                fields.add(fn);
            }
        }

        // 3. where 条件
        List<Object> params = new ArrayList<>();
        String where = buildWhere(root, params);

        // 4. sort
        String sort = string(root, "sort");

        // 5. limit / offset
        int limit = Math.min(
            root.containsKey("limit") ? intVal(root, "limit") : DEFAULT_LIMIT,
            MAX_LIMIT
        );
        int offset = root.containsKey("offset") ? intVal(root, "offset") : 0;

        // 6. 组装 SQL
        StringBuilder sql = new StringBuilder("SELECT ");
        if (fields != null && !fields.isEmpty()) {
            sql.append(String.join(", ", fields));
        } else {
            sql.append("*");
        }
        sql.append(" FROM ").append(entity);
        if (!where.isEmpty()) {
            sql.append(" WHERE ").append(where);
        }
        if (sort != null && !sort.isBlank()) {
            sql.append(" ORDER BY ").append(sort);
        }
        if (limit > 0) {
            sql.append(" LIMIT ").append(limit);
        }
        if (offset > 0) {
            sql.append(" OFFSET ").append(offset);
        }

        return new ParsedQuery(sql.toString(), params);
    }

    @SuppressWarnings("unchecked")
    private static String buildWhere(Map<String, Object> root, List<Object> params) {
        // filters 数组优先
        Object filtersObj = root.get("filters");
        if (filtersObj instanceof List && !((List<?>) filtersObj).isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (Object item : (List<?>) filtersObj) {
                if (!(item instanceof Map)) continue;
                Map<String, Object> cond = (Map<String, Object>) item;
                String logic = string(cond, "logic");
                if (sb.length() > 0) {
                    sb.append(" OR".equalsIgnoreCase(logic) ? " OR " : " AND ");
                }
                sb.append(buildCondition(cond, params));
            }
            return sb.toString();
        }

        // 单 filter
        Object filterObj = root.get("filter");
        if (filterObj instanceof Map && !((Map<?, ?>) filterObj).isEmpty()) {
            return buildCondition((Map<String, Object>) filterObj, params);
        }

        return "";
    }

    @SuppressWarnings("unchecked")
    private static String buildCondition(Map<String, Object> cond, List<Object> params) {
        String field = string(cond, "field");
        String op = string(cond, "op");
        if (field == null || field.isBlank()) {
            throw new IllegalArgumentException("filter 缺少 field");
        }
        if (!IDENTIFIER.matcher(field).matches()) {
            throw new IllegalArgumentException("非法的 filter field: " + field);
        }
        if (op == null) op = "=";
        String opUpper = op.toUpperCase();
        if (!ALLOWED_OPS.contains(opUpper) && !ALLOWED_OPS.contains(op)) {
            throw new IllegalArgumentException("不支持的操作符: " + op);
        }

        // 无值操作符
        if ("IS NULL".equals(opUpper) || "IS NOT NULL".equals(opUpper)) {
            return field + " " + opUpper;
        }

        // IN / NOT IN
        if ("IN".equals(opUpper) || "NOT IN".equals(opUpper)) {
            Object val = cond.get("value");
            if (val instanceof List) {
                List<String> placeholders = new ArrayList<>();
                for (Object v : (List<?>) val) {
                    placeholders.add("?");
                    params.add(v);
                }
                return field + " " + opUpper + " (" + String.join(", ", placeholders) + ")";
            }
            // 单个值也当 IN 处理
            params.add(val);
            return field + " " + opUpper + " (?)";
        }

        // 普通操作符
        Object value = cond.get("value");
        params.add(value);
        return field + " " + op + " ?";
    }

    // ── JSON 解析（无依赖，仅支持一层嵌套） ──

    /**
     * 简单 JSON 对象解析器。仅支持一层嵌套的 {@code {key:value,...}}，
     * 其中 value 可以是 String/Number/Boolean/null/Array/Object。
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseJsonFlat(String json) {
        Map<String, Object> result = new LinkedHashMap<>();
        String trimmed = json.trim();
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
            throw new IllegalArgumentException("JSON 必须以 { 开头 } 结尾");
        }
        String body = trimmed.substring(1, trimmed.length() - 1).trim();
        if (body.isEmpty()) return result;

        List<String> pairs = splitJson(body);
        for (String pair : pairs) {
            int colon = pair.indexOf(':');
            if (colon < 0) continue;
            String key = unquote(pair.substring(0, colon).trim());
            String rawValue = pair.substring(colon + 1).trim();
            result.put(key, parseValue(rawValue));
        }
        return result;
    }

    private static Object parseValue(String raw) {
        if (raw == null || raw.isEmpty()) return null;
        if ("null".equals(raw)) return null;
        if ("true".equals(raw)) return true;
        if ("false".equals(raw)) return false;
        if (raw.startsWith("\"")) return unquote(raw);
        if (raw.startsWith("[")) return parseArray(raw);
        if (raw.startsWith("{")) return parseJsonFlat(raw);
        // number
        try {
            if (raw.contains(".")) return Double.parseDouble(raw);
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            return raw; // fallback: treat as string
        }
    }

    private static List<Object> parseArray(String raw) {
        String inner = raw.substring(1, raw.length() - 1).trim();
        if (inner.isEmpty()) return Collections.emptyList();
        List<Object> result = new ArrayList<>();
        for (String item : splitCsv(inner)) {
            result.add(parseValue(item.trim()));
        }
        return result;
    }

    private static List<String> splitJson(String body) {
        // 按逗号分割，但忽略 {}, [], "" 内部的逗号
        List<String> result = new ArrayList<>();
        int depth = 0;
        boolean inString = false;
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (inString) {
                current.append(c);
                if (c == '"' && (i == 0 || body.charAt(i - 1) != '\\')) {
                    inString = false;
                }
            } else if (c == '"') {
                inString = true;
                current.append(c);
            } else if (c == '{' || c == '[') {
                depth++;
                current.append(c);
            } else if (c == '}' || c == ']') {
                depth--;
                current.append(c);
            } else if (c == ',' && depth == 0) {
                result.add(current.toString());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0) result.add(current.toString());
        return result;
    }

    private static List<String> splitCsv(String s) {
        // 简化版：按逗号分割（不支持嵌套对象的数组）
        List<String> result = new ArrayList<>();
        boolean inString = false;
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (inString) {
                current.append(c);
                if (c == '"' && (i == 0 || s.charAt(i - 1) != '\\')) inString = false;
            } else if (c == '"') {
                inString = true;
                current.append(c);
            } else if (c == ',') {
                result.add(current.toString());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0) result.add(current.toString());
        return result;
    }

    private static String unquote(String s) {
        if (s == null) return null;
        String t = s.trim();
        if (t.length() >= 2 && t.startsWith("\"") && t.endsWith("\"")) {
            return t.substring(1, t.length() - 1)
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\");
        }
        return t;
    }

    private static String string(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v != null ? v.toString() : null;
    }

    private static int intVal(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v instanceof Number) return ((Number) v).intValue();
        try {
            return Integer.parseInt(String.valueOf(v));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // ── 解析结果 ──

    public static class ParsedQuery {
        private final String sql;
        private final List<Object> params;

        ParsedQuery(String sql, List<Object> params) {
            this.sql = sql;
            this.params = Collections.unmodifiableList(params);
        }

        public String getSql() { return sql; }
        public List<Object> getParams() { return params; }
        public Object[] getParamsArray() { return params.toArray(); }

        @Override
        public String toString() {
            return "ParsedQuery{sql='" + sql + "', params=" + params + "}";
        }
    }
}
