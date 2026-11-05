package com.chinacreator.gzcm.engine.ai.dto;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ObjectQL 规范化与 JSON 文本化（{@link com.chinacreator.gzcm.engine.ai.nlq.SemanticQueryService}
 * 的 DSL 结构，动态形态不冻结）。
 */
public final class ObjectQlText {

    private ObjectQlText() {
    }

    /**
     * 复合 filter（operator+conditions）→ {@code filters} 数组；跨 entity 复合查询取首个子查询。
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> normalize(Map<String, Object> ql) {
        Object filter = ql.get("filter");
        if (filter instanceof Map) {
            Map<String, Object> fMap = (Map<String, Object>) filter;
            if (fMap.containsKey("operator") && fMap.containsKey("conditions")) {
                Map<String, Object> result = new LinkedHashMap<>(ql);
                result.remove("filter");
                result.put("filters", fMap.get("conditions"));
                return result;
            }
        }
        if (ql.containsKey("composite") && ql.containsKey("queries")) {
            Object queries = ql.get("queries");
            if (queries instanceof List && !((List<?>) queries).isEmpty()) {
                return normalize((Map<String, Object>) ((List<?>) queries).get(0));
            }
        }
        return ql;
    }

    /** Map → JSON 字符串（无外部依赖，输出形态与原实现逐字一致）。 */
    public static String toJson(Map<String, Object> map) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) sb.append(", ");
            sb.append("\"").append(entry.getKey()).append("\": ");
            appendValue(sb, entry.getValue());
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static void appendValue(StringBuilder sb, Object value) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof String) {
            sb.append("\"").append(escapeJson((String) value)).append("\"");
        } else if (value instanceof Number) {
            sb.append(value);
        } else if (value instanceof Boolean) {
            sb.append(value);
        } else if (value instanceof Map) {
            sb.append(toJson((Map<String, Object>) value));
        } else if (value instanceof List) {
            sb.append("[");
            boolean first = true;
            for (Object item : (List<?>) value) {
                if (!first) sb.append(", ");
                appendValue(sb, item);
                first = false;
            }
            sb.append("]");
        } else {
            sb.append("\"").append(escapeJson(value.toString())).append("\"");
        }
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
