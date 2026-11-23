package com.chinacreator.gzcm.workspace.config;

import java.util.Map;

/**
 * 场景模板请求体（强类型出口，2026-11-21 场景工作台简化 v0.3）。
 *
 * <p>边界强类型化（撞 {@code ControllerMapPayloadRatchetTest} 的弱类型面在减不在增）；
 * 定义默认包仍是自由结构（模板要能装未来字段），故只把它收成单字段 Map。</p>
 */
public record ScenarioTemplateRequest(
        String id,
        String name,
        String category,
        String description,
        Map<String, Object> definitionDefaults,
        String status) {

    public Map<String, Object> toMap() {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        put(m, "id", id);
        put(m, "name", name);
        put(m, "category", category);
        put(m, "description", description);
        if (definitionDefaults != null) m.put("definitionDefaults", definitionDefaults);
        put(m, "status", status);
        return m;
    }

    /** 路径 id 优先（PUT 语义：以 URL 为准，体里的 id 被忽略）。 */
    public Map<String, Object> withId(String pathId) {
        Map<String, Object> m = toMap();
        m.put("id", pathId);
        return m;
    }

    private static void put(Map<String, Object> m, String k, String v) {
        if (v != null && !v.isBlank()) m.put(k, v);
    }
}
