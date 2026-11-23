package com.chinacreator.gzcm.workspace.analysis;

import java.util.List;
import java.util.Map;

/**
 * 分析运行记录视图（强类型出口，2026-11-21 场景工作台简化 v0.3）。
 *
 * <p>字段面 = 锚点表 {@code ecos_scenario_run} 的读列集（DAO 以 snake_case 下发）。
 * 契约面键名为 camelCase，故 {@link #of} 两形态同取，避免只认一侧导致整行 null。</p>
 */
public record AnalysisRunVO(
        String id,
        String scenarioId,
        String runType,
        String analysisType,
        String status,
        String metric,
        String runMode,
        String sourceType,
        String sourceId,
        String sourceRunId,
        String definitionVersion,
        String definitionDigest,
        Boolean degraded,
        String createTime) {

    /** 从服务层 Map 提取（缺键取 null；camel/snake 两形态同取）。 */
    public static AnalysisRunVO of(Map<String, Object> m) {
        String id = str(m, "id");
        if (id == null) id = str(m, "analysisId");
        return new AnalysisRunVO(
                id,
                any(m, "scenarioId", "scenario_id"),
                any(m, "runType", "run_type"),
                any(m, "analysisType", "analysis_type"),
                str(m, "status"),
                str(m, "metric"),
                any(m, "runMode", "run_mode"),
                any(m, "sourceType", "source_type"),
                any(m, "sourceId", "source_id"),
                any(m, "sourceRunId", "source_run_id"),
                any(m, "definitionVersion", "definition_version"),
                any(m, "definitionDigest", "definition_digest"),
                boolAny(m, "degraded"),
                any(m, "createTime", "create_time"));
    }

    public static List<AnalysisRunVO> listOf(List<Map<String, Object>> rows) {
        return rows.stream().map(AnalysisRunVO::of).toList();
    }

    private static String any(Map<String, Object> m, String camel, String snake) {
        String v = str(m, camel);
        return v != null ? v : str(m, snake);
    }

    private static String str(Map<String, Object> m, String k) {
        Object v = m.get(k);
        return v == null ? null : String.valueOf(v);
    }

    private static Boolean boolAny(Map<String, Object> m, String k) {
        Object v = m.get(k);
        if (v instanceof Boolean b) return b;
        if (v instanceof Number n) return n.intValue() != 0;
        if (v instanceof String s) return "true".equalsIgnoreCase(s) || "1".equals(s);
        return null;
    }
}
