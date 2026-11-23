package com.chinacreator.gzcm.workspace.definition;

import java.util.List;
import java.util.Map;

/**
 * 场景定义聚合视图（强类型出口，2026-11-21 场景工作台简化 v0.3）。
 *
 * <p>字段面与服务层 {@code get/save/publish} 返回的键一一对应，序列化形状不变
 * （缺省键以 null 出现，前端按可选读）。引入本类是为了让边界不再出现
 * {@code Map<String,Object>} 载荷行（{@code ControllerMapPayloadRatchetTest} 只减不增）。</p>
 */
public record ScenarioDefinitionVO(
        Object scenario,
        Map<String, List<String>> bindings,
        List<?> versions,
        String currentVersion,
        Map<String, Object> upstream,
        Boolean editable,
        Boolean published,
        Boolean pendingApproval,
        String releasedVersion,
        String artifactDigest) {

    @SuppressWarnings("unchecked")
    public static ScenarioDefinitionVO of(Map<String, Object> m) {
        return new ScenarioDefinitionVO(
                m.get("scenario"),
                (Map<String, List<String>>) m.get("bindings"),
                m.get("versions") instanceof List ? (List<?>) m.get("versions") : null,
                str(m, "currentVersion"),
                (Map<String, Object>) m.get("upstream"),
                bool(m, "editable"),
                bool(m, "published"),
                bool(m, "pendingApproval"),
                str(m, "releasedVersion"),
                str(m, "artifactDigest"));
    }

    private static String str(Map<String, Object> m, String k) {
        Object v = m.get(k);
        return v == null ? null : String.valueOf(v);
    }

    private static Boolean bool(Map<String, Object> m, String k) {
        Object v = m.get(k);
        return v instanceof Boolean b ? b : null;
    }
}
