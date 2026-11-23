package com.chinacreator.gzcm.workspace.config;

import java.util.List;
import java.util.Map;

/**
 * 场景模板视图（强类型出口，2026-11-21 场景工作台简化 v0.3）。
 *
 * <p>定义默认包仍为自由结构，故以单字段 Map 承载；其余字段全显式。</p>
 */
public record ScenarioTemplateVO(
        String id,
        String name,
        String category,
        String description,
        Map<String, Object> definitionDefaults,
        String status,
        String createTime) {

    public static ScenarioTemplateVO of(Map<String, Object> m) {
        return new ScenarioTemplateVO(
                str(m, "id"),
                str(m, "name"),
                str(m, "category"),
                str(m, "description"),
                mapAny(m, "definitionDefaults", "definition_defaults"),
                str(m, "status"),
                strAny(m, "createTime", "create_time"));
    }

    public static List<ScenarioTemplateVO> listOf(List<Map<String, Object>> rows) {
        return rows.stream().map(ScenarioTemplateVO::of).toList();
    }

    /** DAO 直读列名为 snake_case，契约面为 camelCase —— 两形态同取。 */
    private static String strAny(Map<String, Object> m, String camel, String snake) {
        String v = str(m, camel);
        return v != null ? v : str(m, snake);
    }

    /**
     * definition_defaults 是 TEXT 列（DAO 返回 String），契约面要 JSON 对象。
     * 解析失败按 null 下发（宁可缺省不可抛 500 —— 历史脏值不该让整表读崩）。
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapAny(Map<String, Object> m, String camel, String snake) {
        Object v = m.get(camel);
        if (!(v instanceof Map)) v = m.get(snake);
        if (v instanceof Map) return (Map<String, Object>) v;
        if (v instanceof String s && !s.isBlank() && s.trim().startsWith("{")) {
            try {
                Object parsed = new com.fasterxml.jackson.databind.ObjectMapper().readValue(s, Map.class);
                if (parsed instanceof Map) return (Map<String, Object>) parsed;
            } catch (Exception ignored) {
                // 历史脏值：按缺省下发
            }
        }
        return null;
    }

    private static String str(Map<String, Object> m, String k) {
        Object v = m.get(k);
        return v == null ? null : String.valueOf(v);
    }
}
