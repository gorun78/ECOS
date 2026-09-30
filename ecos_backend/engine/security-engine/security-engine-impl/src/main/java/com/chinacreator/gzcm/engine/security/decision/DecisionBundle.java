package com.chinacreator.gzcm.engine.security.decision;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 详细设计-01 C.1 [3] / D 章 — 三通道唯一裁决返回体（一次 {@code decide} 拿全）。
 *
 * <p>结构（D 章 {@code DecisionBundle}）：</p>
 * <pre>{@code
 * {
 *   rls:     {predicateTemplate, bindings[], combine, denyIfEmpty},
 *   columns: {mode: allow|deny, names: [...]},
 *   masks:   [{column, strategy}],
 *   opa:     {allow, policyRef, obligations[]},
 *   decisionId, traceId
 * }
 * }</pre>
 *
 * <p>结构性意义：把 RLS/CLS/mask/OPA 合并成<b>单入口</b>后，"绕过其中一条"在架构上
 * 不可表达（PRD-07 §1.5-3 "导出旁路 grep = 0" 的结构性实现，而非人工审查）。</p>
 */
public final class DecisionBundle {

    /** 通道语义（C.1 Purpose）。 */
    public enum Purpose { PAGE, EXPORT, AI }

    private final Map<String, Object> rls;
    private final Map<String, Object> columns;
    private final List<Map<String, Object>> masks;
    private final Map<String, Object> opa;
    private final String decisionId;
    private final String traceId;

    public DecisionBundle(Map<String, Object> rls, Map<String, Object> columns,
                          List<Map<String, Object>> masks, Map<String, Object> opa,
                          String decisionId, String traceId) {
        this.rls = rls;
        this.columns = columns;
        this.masks = masks == null ? new ArrayList<>() : masks;
        this.opa = opa;
        this.decisionId = decisionId;
        this.traceId = traceId;
    }

    public Map<String, Object> rls() { return rls; }
    public Map<String, Object> columns() { return columns; }
    public List<Map<String, Object>> masks() { return masks; }
    public Map<String, Object> opa() { return opa; }
    public String decisionId() { return decisionId; }
    public String traceId() { return traceId; }

    /** CLS 是否拒绝全部列（allow 模式且 names 空）。 */
    public boolean columnsDenied() {
        Object mode = columns.get("mode");
        Object names = columns.get("names");
        boolean allowMode = "allow".equals(String.valueOf(mode));
        boolean empty = names instanceof List<?> l && l.isEmpty();
        return allowMode && empty;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("rls", rls);
        m.put("columns", columns);
        m.put("masks", masks);
        m.put("opa", opa);
        m.put("decisionId", decisionId);
        m.put("traceId", traceId);
        return m;
    }
}
