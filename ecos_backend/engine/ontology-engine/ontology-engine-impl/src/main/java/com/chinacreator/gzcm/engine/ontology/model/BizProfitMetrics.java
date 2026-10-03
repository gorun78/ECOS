package com.chinacreator.gzcm.engine.ontology.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * biz-profit 领域包 10 项指标的固定登记（F03-02 逐项登记编码/公式/可加性/期间/币种/血缘，
 * F03-04 领域包首发布的指标基线）。本模型只做语义登记（定义），不产金额数值（铁律 C.3 读写边界：
 * 本体域只产定义与版本，不产数值）。
 */
public final class BizProfitMetrics {

    private BizProfitMetrics() {}

    /** 指标定义登记条目。 */
    public static final class Spec {
        public final String code;
        public final String name;
        public final String expression;
        public final String aggregation;   // SUM / AVG / MAX / null
        public final String entityCode;
        public final UnitDimension derivedUnit;
        public final Additivity additivity;
        public final String period;        // MONTH / QUARTER / YEAR
        public final String currency;      // CNY / null
        /** 表达式叶子 → 单位。 */
        public final Map<String, UnitDimension> leafUnits;

        Spec(String code, String name, String expression, String aggregation, String entityCode,
             UnitDimension derivedUnit, Additivity additivity, String period, String currency,
             Map<String, UnitDimension> leafUnits) {
            this.code = code;
            this.name = name;
            this.expression = expression;
            this.aggregation = aggregation;
            this.entityCode = entityCode;
            this.derivedUnit = derivedUnit;
            this.additivity = additivity;
            this.period = period;
            this.currency = currency;
            this.leafUnits = leafUnits;
        }
    }

    private static Map<String, UnitDimension> leaf(String... kv) {
        Map<String, UnitDimension> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(kv[i], UnitDimension.valueOf(kv[i + 1]));
        }
        return m;
    }

    public static final Spec M_ACCEPTANCE_AMT = new Spec(
            "M_ACCEPTANCE_AMT", "验收金额", "SUM(acceptance_amt)", "SUM", "AcceptanceStage",
            UnitDimension.AMOUNT, Additivity.ADDITIVE, "MONTH", "CNY",
            leaf("acceptance_amt", "AMOUNT"));

    public static final Spec M_COLLECTION_AMT = new Spec(
            "M_COLLECTION_AMT", "回款金额", "SUM(collection_amt)", "SUM", "CollectionStage",
            UnitDimension.AMOUNT, Additivity.ADDITIVE, "MONTH", "CNY",
            leaf("collection_amt", "AMOUNT"));

    public static final Spec M_SUBCONTRACT_AMT = new Spec(
            "M_SUBCONTRACT_AMT", "分包金额", "SUM(subcontract_amt)", "SUM", "SubcontractStage",
            UnitDimension.AMOUNT, Additivity.ADDITIVE, "MONTH", "CNY",
            leaf("subcontract_amt", "AMOUNT"));

    public static final Spec M_FC_REVENUE = new Spec(
            "M_FC_REVENUE", "财务收入", "SUM(fc_revenue)", "SUM", "FinancialRevenue",
            UnitDimension.AMOUNT, Additivity.ADDITIVE, "MONTH", "CNY",
            leaf("fc_revenue", "AMOUNT"));

    public static final Spec M_COST_SALARY = new Spec(
            "M_COST_SALARY", "人工成本", "salary_cost + overtime_cost", "+", "SalaryCost",
            UnitDimension.AMOUNT, Additivity.ADDITIVE, "MONTH", "CNY",
            leaf("salary_cost", "AMOUNT", "overtime_cost", "AMOUNT"));

    public static final Spec M_COST_MGMT = new Spec(
            "M_COST_MGMT", "管理成本", "SUM(mgmt_cost)", "SUM", "ManagementCost",
            UnitDimension.AMOUNT, Additivity.ADDITIVE, "MONTH", "CNY",
            leaf("mgmt_cost", "AMOUNT"));

    public static final Spec M_COST_MKT = new Spec(
            "M_COST_MKT", "市场成本", "SUM(mkt_cost)", "SUM", "MarketingCost",
            UnitDimension.AMOUNT, Additivity.ADDITIVE, "MONTH", "CNY",
            leaf("mkt_cost", "AMOUNT"));

    public static final Spec M_FC_PROFIT = new Spec(
            "M_FC_PROFIT", "财务利润", "fc_revenue - cost_total", "-", "FinancialRevenue",
            UnitDimension.AMOUNT, Additivity.ADDITIVE, "MONTH", "CNY",
            leaf("fc_revenue", "AMOUNT", "cost_total", "AMOUNT"));

    public static final Spec M_REALIZATION_RATE = new Spec(
            "M_REALIZATION_RATE", "兑现率", "fc_revenue / target_revenue", "AVG", "FinancialRevenue",
            UnitDimension.RATIO, Additivity.NON, "MONTH", null,
            leaf("fc_revenue", "AMOUNT", "target_revenue", "AMOUNT"));

    public static final Spec M_COLLECTION_CYCLE = new Spec(
            "M_COLLECTION_CYCLE", "回款周期", "(due_month - collect_month)", "-", "CollectionStage",
            UnitDimension.MONTH, Additivity.NON, "MONTH", null,
            leaf("due_month", "MONTH", "collect_month", "MONTH"));

    private static final List<Spec> ALL = List.of(
            M_ACCEPTANCE_AMT, M_COLLECTION_AMT, M_SUBCONTRACT_AMT, M_FC_REVENUE,
            M_COST_SALARY, M_COST_MGMT, M_COST_MKT, M_FC_PROFIT,
            M_REALIZATION_RATE, M_COLLECTION_CYCLE);

    public static List<Spec> all() { return ALL; }

    public static Spec byCode(String code) {
        for (Spec s : ALL) {
            if (s.code.equals(code)) return s;
        }
        return null;
    }
}
