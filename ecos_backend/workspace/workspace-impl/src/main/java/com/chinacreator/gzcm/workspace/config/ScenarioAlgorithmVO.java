package com.chinacreator.gzcm.workspace.config;

import java.util.List;
import java.util.Map;

/**
 * 算法登记视图（强类型出口，2026-11-21 场景工作台简化 v0.3）。
 *
 * <p>params/output schema 保留自由结构（算法自描述），其余全显式。</p>
 */
public record ScenarioAlgorithmVO(
        String id,
        String name,
        String analysisType,
        String version,
        String providerType,
        String implementationRef,
        Map<String, Object> paramsSchema,
        Map<String, Object> outputSchema,
        String status,
        String createTime) {

    public static ScenarioAlgorithmVO of(Map<String, Object> m) {
        return new ScenarioAlgorithmVO(
                str(m, "id"),
                str(m, "name"),
                strAny(m, "analysisType", "analysis_type"),
                str(m, "version"),
                strAny(m, "providerType", "provider_type"),
                strAny(m, "implementationRef", "implementation_ref"),
                mapAny(m, "paramsSchema", "params_schema"),
                mapAny(m, "outputSchema", "output_schema"),
                str(m, "status"),
                strAny(m, "createTime", "create_time"));
    }

    public static List<ScenarioAlgorithmVO> listOf(List<Map<String, Object>> rows) {
        return rows.stream().map(ScenarioAlgorithmVO::of).toList();
    }

    /** DAO 直读列名为 snake_case，契约面为 camelCase —— 两形态同取，禁只认一侧。 */
    private static String strAny(Map<String, Object> m, String camel, String snake) {
        String v = str(m, camel);
        return v != null ? v : str(m, snake);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapAny(Map<String, Object> m, String camel, String snake) {
        Object v = m.get(camel);
        if (!(v instanceof Map)) v = m.get(snake);
        return v instanceof Map ? (Map<String, Object>) v : null;
    }

    private static String str(Map<String, Object> m, String k) {
        Object v = m.get(k);
        return v == null ? null : String.valueOf(v);
    }
}
