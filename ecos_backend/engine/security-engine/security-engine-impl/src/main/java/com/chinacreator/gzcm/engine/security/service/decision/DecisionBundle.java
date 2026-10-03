package com.chinacreator.gzcm.engine.security.service.decision;

import com.chinacreator.gzcm.engine.security.service.predicate.PredicateBinding;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 详细设计-01 C.1 [3] — 三通道唯一裁决产物（D.2 DecisionBundle）。
 *
 * <pre>
 * {
 *   rls:     {predicateTemplate, bindings[], combine:"AND", denyIfEmpty},
 *   columns: {mode: allow|deny, names[]},
 *   masks:   [{column, strategy}],
 *   opa:     {allow, obligations[], policyRef},
 *   decisionId, traceId
 * }
 * </pre>
 *
 * <p>调用方（页面/导出/AI 三通道）MUST 消费全部四段；"绕过"在结构上不可表达
 * （PRD-07 §1.5-3 导出旁路 grep=0 的结构性实现）。</p>
 */
public final class DecisionBundle {

    public final Map<String, Object> rls;
    public final Map<String, Object> columns;
    public final List<Map<String, Object>> masks;
    public final Map<String, Object> opa;
    public final String decisionId;
    public final String traceId;
    public final String purpose;
    /** 漏洞面标记：任一子通道 fail-closed 时为 false */
    public final boolean allowed;

    public DecisionBundle(Map<String, Object> rls, Map<String, Object> columns,
                          List<Map<String, Object>> masks, Map<String, Object> opa,
                          String decisionId, String traceId, String purpose, boolean allowed) {
        this.rls = rls;
        this.columns = columns;
        this.masks = masks;
        this.opa = opa;
        this.decisionId = decisionId;
        this.traceId = traceId;
        this.purpose = purpose;
        this.allowed = allowed;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("allowed", allowed);
        m.put("rls", rls);
        m.put("columns", columns);
        m.put("masks", masks);
        m.put("opa", opa);
        m.put("decisionId", decisionId);
        m.put("traceId", traceId);
        m.put("purpose", purpose);
        return m;
    }

    public static Map<String, Object> rlsSection(String predicateTemplate, List<PredicateBinding> bindings,
                                                 boolean denyIfEmpty, boolean denyAll) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("predicateTemplate", predicateTemplate);
        m.put("bindings", bindings.stream().map(b -> {
            Map<String, Object> bm = new LinkedHashMap<>();
            bm.put("name", b.name());
            bm.put("source", b.source());
            if (b.value() != null) bm.put("value", b.value());
            if (b.items() != null) bm.put("items", b.items());
            return bm;
        }).toList());
        m.put("combine", "AND");
        m.put("denyIfEmpty", denyIfEmpty);
        m.put("denyAll", denyAll);
        return m;
    }

    public static Map<String, Object> mask(String column, String strategy) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("column", column);
        m.put("strategy", strategy);
        return m;
    }

    public static Map<String, Object> opaSection(boolean allow, List<Object> obligations,
                                                 String policyRef, boolean policySkipped) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("allow", allow);
        m.put("obligations", obligations != null ? obligations : new ArrayList<>());
        m.put("policyRef", policyRef);
        m.put("policySkipped", policySkipped);
        return m;
    }
}
