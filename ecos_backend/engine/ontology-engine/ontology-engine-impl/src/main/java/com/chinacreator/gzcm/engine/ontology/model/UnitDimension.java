package com.chinacreator.gzcm.engine.ontology.model;

/**
 * 指标表达式四则树叶子单位维度（F03-02 V2 门禁单位推导，替代 PRD"单位可推导且不冲突"）。
 *
 * <p>推导规则（文档 F03-02 白名单，其他组合 → 400 {@code ECOS-ONTO-021} 并返回推导链）：
 * <ul>
 *   <li>金额±金额 = 金额</li>
 *   <li>月±月 = 月</li>
 *   <li>金额/金额 = 比率</li>
 *   <li>金额/人数 = 金额（人均）</li>
 *   <li>常数字面量为 {@link #NONE}（对运算透明：X op NONE = X）</li>
 * </ul>
 */
public enum UnitDimension {
    AMOUNT("金额"),
    MONTH("月"),
    RATIO("比率"),
    MANDAY("人天"),
    HEADCOUNT("人数"),
    NONE("常量/无单位");

    private final String zh;

    UnitDimension(String zh) { this.zh = zh; }

    public String zh() { return zh; }
}
