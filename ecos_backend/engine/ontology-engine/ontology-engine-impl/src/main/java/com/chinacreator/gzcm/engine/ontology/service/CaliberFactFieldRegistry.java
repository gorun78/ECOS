package com.chinacreator.gzcm.engine.ontology.service;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * H-2（PRD-09 v1.2 §1.2 V2/V3）— 业务事实字段登记册：公式 DSL 中允许出现的
 * <b>非指标</b>引用（事实列）与其单位，以及合法限定符（{@code IDENT '[' IDENT ']'}）。
 *
 * <p>事实列语义源 = {@code V167__biz_fact_model.sql}（ecos_dw 五表实测列名）
 * + PRD-02 §数据契约（合同基数/归属比例/实现率…）；限定符源 =
 * {@code stage ∈ ACCEPTANCE/COLLECTION/SUBCONTRACT}、{@code fact_type ∈ PLAN/ACTUAL}
 * （PRD-03 §1.3 指标登记「Σ stage.amount WHERE stage=ACCEPTANCE」形态）。
 * 调用方（向导）可在请求体 {@code factFields} 里追加本口径私有字段。</p>
 *
 * <p>与 {@link MetricUnitDerivationGuard}（发布门禁 V2，保留不动）共存：
 * 本登记册只提供「事实叶子 → 单位」元数据供 DSL V3 量纲推导复用同一套单位体系
 * （AMOUNT / RATE / MONTH / PERSON_COUNT / SCALAR）。</p>
 */
final class CaliberFactFieldRegistry {

    /** 事实列 → 单位（与 MetricUnitDerivationGuard 单位体系同源；SCALAR 为无量纲）。 */
    private static final Map<String, String> FIELD_UNITS = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

    static {
        FIELD_UNITS.put("contract_base", "AMOUNT");       // V167 stage_fact.contract_base
        FIELD_UNITS.put("amount", "AMOUNT");              // V167 stage/cost_fact.amount
        FIELD_UNITS.put("cost_amount", "AMOUNT");         // 成本额别名
        FIELD_UNITS.put("attribution_ratio", "RATE");     // V167 attribution_ratio NUMERIC(5,4)
        FIELD_UNITS.put("realization_rate", "RATE");      // V167 realization_rate NUMERIC(5,4)
        FIELD_UNITS.put("collection_rate", "RATE");       // 回款率
        FIELD_UNITS.put("hourly_rate", "AMOUNT");         // V167 resource_fact.hourly_rate
        FIELD_UNITS.put("fte", "PERSON_COUNT");           // V167 resource_fact.fte
        FIELD_UNITS.put("head_count", "PERSON_COUNT");    // 人数
        FIELD_UNITS.put("work_hours", "HOURS");           // V167 resource_fact.work_hours
        FIELD_UNITS.put("months", "MONTH");               // 月数（回款周期类）
    }

    /** 合法限定符：stage 三类 + fact_type 两类（V167 ck 枚举域）。 */
    private static final Set<String> QUALIFIERS = new LinkedHashSet<>();

    static {
        QUALIFIERS.add("ACCEPTANCE");
        QUALIFIERS.add("COLLECTION");
        QUALIFIERS.add("SUBCONTRACT");
        QUALIFIERS.add("PLAN");
        QUALIFIERS.add("ACTUAL");
    }

    private CaliberFactFieldRegistry() {
    }

    static boolean isKnownField(String name) {
        return FIELD_UNITS.containsKey(name);
    }

    /** 事实列单位；未登记返回 null（V3 宽松放行 + WARN，与既有守卫同哲学）。 */
    static String unitOf(String name) {
        return FIELD_UNITS.get(name);
    }

    static boolean isKnownQualifier(String qualifier) {
        return QUALIFIERS.contains(qualifier.toUpperCase());
    }
}
