package com.chinacreator.gzcm.workspace.config;

import java.util.Map;

/**
 * 算法登记请求体（强类型出口，2026-11-21 场景工作台简化 v0.3）。
 *
 * <p>对应 §5.2「算法库只登记执行承载」：参数/输出 schema 保留自由结构，
 * 其余字段全部显式声明。</p>
 */
public record ScenarioAlgorithmRequest(
        String id,
        String name,
        String analysisType,
        String version,
        String providerType,
        String implementationRef,
        Map<String, Object> paramsSchema,
        Map<String, Object> outputSchema,
        String status) {

    public Map<String, Object> toMap() {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        put(m, "id", id);
        put(m, "name", name);
        put(m, "analysisType", analysisType);
        put(m, "version", version);
        put(m, "providerType", providerType);
        put(m, "implementationRef", implementationRef);
        if (paramsSchema != null) m.put("paramsSchema", paramsSchema);
        if (outputSchema != null) m.put("outputSchema", outputSchema);
        put(m, "status", status);
        return m;
    }

    /** 路径 id 优先（PUT 语义）。 */
    public Map<String, Object> withId(String pathId) {
        Map<String, Object> m = toMap();
        m.put("id", pathId);
        return m;
    }

    private static void put(Map<String, Object> m, String k, String v) {
        if (v != null && !v.isBlank()) m.put(k, v);
    }
}
