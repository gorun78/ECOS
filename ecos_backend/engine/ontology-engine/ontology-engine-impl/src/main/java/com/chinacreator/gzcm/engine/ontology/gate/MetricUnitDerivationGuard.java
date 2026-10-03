package com.chinacreator.gzcm.engine.ontology.gate;

import com.chinacreator.gzcm.engine.ontology.model.Additivity;
import com.chinacreator.gzcm.engine.ontology.model.BizProfitMetrics;
import com.chinacreator.gzcm.engine.ontology.model.UnitDimension;
import com.chinacreator.gzcm.engine.ontology.service.MetricUnitAnalyzer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * V2 单位门禁（F03-02/F03-03 / PRD-03 §1.4-2）：
 * <ul>
 *   <li>单位推导无冲突（{@link MetricUnitAnalyzer}）→ 否则 {@code ECOS-ONTO-021} 并返回推导链；</li>
 *   <li>可加性与聚合声明一致：不可加指标（比率/月，NON）声明 SUM 等可加聚合 →
 *       {@code ECOS-ONTO-022}。</li>
 * </ul>
 */
public class MetricUnitDerivationGuard {

    public static final String CODE_UNIT = "ECOS-ONTO-021";
    public static final String CODE_ADDITIVITY = "ECOS-ONTO-022";

    private static final Logger log = LoggerFactory.getLogger(MetricUnitDerivationGuard.class);

    private final MetricUnitAnalyzer unitAnalyzer;

    public MetricUnitDerivationGuard(MetricUnitAnalyzer unitAnalyzer) {
        this.unitAnalyzer = unitAnalyzer;
    }

    public List<GateViolation> evaluate(GateContext ctx) {
        List<GateViolation> out = new ArrayList<>();
        for (GateContext.MetricRef m : ctx.getMetrics()) {
            // ── V2a 单位推导 ──
            if (m.expression != null && !m.expression.isBlank()) {
                BizProfitMetrics.Spec spec = BizProfitMetrics.byCode(m.code);
                java.util.Map<String, UnitDimension> leafUnits =
                        spec != null ? spec.leafUnits : java.util.Collections.emptyMap();
                MetricUnitAnalyzer.Result r = unitAnalyzer.analyze(m.expression, leafUnits);
                if (r.conflicted) {
                    GateViolation v = new GateViolation(GateViolation.Gate.V2_UNIT, CODE_UNIT,
                            "指标 " + m.code + " 单位推导冲突: " + r.message + "。推导链: " + r.chain);
                    v.addRef(m.code);
                    out.add(v);
                }
            }
            // ── V2b 可加性 vs 聚合声明 ──
            AggregationKind kind = AggregationKind.of(m.aggregation);
            if (kind != null && kind.additive) {
                Additivity add = declaredAdditivity(m.code, m.aggregation);
                if (add == Additivity.NON) {
                    GateViolation v = new GateViolation(GateViolation.Gate.V2_UNIT, CODE_ADDITIVITY,
                            "不可加指标 " + m.code + " 被声明为可加聚合 " + m.aggregation + "（比率/月指标禁参与可加聚合）");
                    v.addRef(m.code);
                    out.add(v);
                }
            }
        }
        return out;
    }

    /**
     * 从 fixed 登记推断可加性（比率 RATIO 与 月 MONTH 派生 → NON，其余 → ADDITIVE）。
     * 未登记的指标按表达式派生单位（比率/月 = NON）。
     */
    private Additivity declaredAdditivity(String code, String aggregation) {
        BizProfitMetrics.Spec spec = BizProfitMetrics.byCode(code);
        if (spec != null) return spec.additivity;
        return null; // 未登记：以 V2a 单位推导为准，不在此误判
    }

    /** 聚合声明是否为一项"可加"聚合（SUM/AVG 视为可加，触发对 NON 的拦截）。 */
    private enum AggregationKind {
        SUM(true), AVG(true), MIN(false), MAX(false), COUNT(false), NONE(false);

        final boolean additive;
        AggregationKind(boolean additive) { this.additive = additive; }

        static AggregationKind of(String s) {
            if (s == null) return NONE;
            for (AggregationKind k : values()) {
                if (k.name().equalsIgnoreCase(s.trim())) return k;
            }
            if (s.regionMatches(true, 0, "sum", 0, 3)) return SUM; // 前缀容忍 "SUMX" 等变体
            return NONE;
        }
    }
}
