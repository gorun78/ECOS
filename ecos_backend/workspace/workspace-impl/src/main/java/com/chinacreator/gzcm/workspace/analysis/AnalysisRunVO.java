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
        String createTime,
        /** PIPELINE 执行详情（节点轨迹/outputs/executionId）；其它类型为 null（additive，2026-11-23）。 */
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
        Object result) {

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
                any(m, "createTime", "create_time"),
                resultOf(m));
    }

    /**
     * PIPELINE 结果透出（2026-11-23）：服务层把执行详情放在 {@code simulation_result} 槽位
     * （节点轨迹/outputs 的结构化产物）。仅当 {@code analysisType=PIPELINE} 时透出，
     * 其它类型保持 null —— 老客户端不会因此看到新键，属 additive。
     *
     * <p><b>解包 pgjdbc jsonb</b>：JdbcTemplate 读 jsonb 列会下发 {@code {type:"jsonb", value:"<json>"}}
     * 形态的 PGobject 投影（实测），直接透出会给前端一个带 value 字符串的壳。这里解成真对象；
     * 解析失败原样返回（不吞、不伪造空对象）。</p>
     */
    private static Object resultOf(Map<String, Object> m) {
        String type = str(m, "analysisType");
        if (type == null) type = str(m, "analysis_type");
        if (!"PIPELINE".equalsIgnoreCase(type)) return null;
        Object v = m.get("simulation");
        if (v == null) v = m.get("simulation_result");
        return unwrapJsonb(v);
    }

    private static final com.fasterxml.jackson.databind.ObjectMapper JSON =
            new com.fasterxml.jackson.databind.ObjectMapper();

    /**
     * {@code PGobject} / {@code {type:"jsonb", value:"…"}} → 真对象；其它形态原样返回。
     *
     * <p>实测两形态都会出现：单条 {@code get} 经 JdbcTemplate 直接给 {@code org.postgresql.util.PGobject}
     * 实例；列表查询把它投影成 {@code {type,value}} 三键 Map（Jackson 加了 null 键）。
     * 两种都解；解失败回退原值（不吞、不伪造），避免前端拿到"壳"。</p>
     */
    private static Object unwrapJsonb(Object v) {
        if (v == null) return null;
        // 形态一：PGobject（反射取 value，避免 workspace-impl 直接依赖 pgjdbc 类型）
        String pgText = pgTextOf(v);
        if (pgText == null && v instanceof Map<?, ?> mm) {
            Object inner = mm.get("value");
            if (inner instanceof String s) pgText = s;
        }
        if (pgText == null) return v;
        String trimmed = pgText.trim();
        if (!(trimmed.startsWith("{") || trimmed.startsWith("["))) return v;
        try {
            return JSON.readValue(trimmed, Object.class);
        } catch (Exception e) {
            return v;
        }
    }

    /** 若对象是 PGobject（有 getValue() 且类名含 PGobject）则取 value，否则 null。 */
    private static String pgTextOf(Object v) {
        Class<?> c = v.getClass();
        if (!c.getName().contains("PGobject")) return null;
        try {
            Object value = c.getMethod("getValue").invoke(v);
            return value instanceof String s ? s : null;
        } catch (Exception e) {
            return null;
        }
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
