package com.chinacreator.gzcm.engine.cognitive.api.outcome;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 认知诊断项（《认知域与智能域补充详细设计 v1.0》§9.2 步骤 5 / §9.3 {@code diagnoses[]}）。
 *
 * <p>诊断 = 把偏差关联回<b>当时使用</b>的认知资产版本（Hypothesis / Belief /
 * Decision Basis / Cognitive Model），形成解释与待验证假设。纪律：</p>
 * <ul>
 *   <li>只引用资产 ID + 版本，<b>不复制资产正文</b>（§8.1 B 层受治理，A 层权威在业务引擎）；</li>
 *   <li>{@code verdict} 只能是"待验证"级别判断（SUPPORTED/REFUTED/INCONCLUSIVE），
 *       诊断本身<b>不</b>改写生产 Belief（§9.4：单次偏差禁自动升格）；</li>
 *   <li>缺资产引用时 {@code assetRefs} 留空并置 {@code explanationBasis=false}，
 *       禁编造解释（附录 B 禁捷径）。</li>
 * </ul>
 */
@Data
public class OutcomeDiagnosis {

    /** 诊断结论分级：假设被支持。 */
    public static final String VERDICT_SUPPORTED = "SUPPORTED";
    /** 诊断结论分级：假设被反驳。 */
    public static final String VERDICT_REFUTED = "REFUTED";
    /** 诊断结论分级：证据不足以判定（默认；禁把 INCONCLUSIVE 当 SUPPORTED 用）。 */
    public static final String VERDICT_INCONCLUSIVE = "INCONCLUSIVE";

    /** 关联的指标引用（诊断针对哪个 metricRef 的偏差）。 */
    private String metricRef;

    /** 诊断结论（VERDICT_* 三选一）。 */
    private String verdict = VERDICT_INCONCLUSIVE;

    /** 解释文本（人读；禁把 LLM 自由文本当权威事实）。 */
    private String explanation;

    /** 当时使用的认知资产引用（id@version 形态；空 = 无可关联资产）。 */
    private List<String> assetRefs = new ArrayList<>();

    /** 待验证假设（§9.2 步骤 5 产出；须走提案流，禁直接落 Belief）。 */
    private List<String> pendingHypotheses = new ArrayList<>();

    /** 是否有资产引用支撑解释（false ⇒ explanation 只能是"无依据"级别描述）。 */
    private boolean explanationBasis;

    /**
     * 契约校验：返回问题列表（空 = 合法）。
     * verdict 必须是三分词表之一（禁自造第四态）。
     */
    public List<String> validate() {
        List<String> problems = new ArrayList<>();
        if (!List.of(VERDICT_SUPPORTED, VERDICT_REFUTED, VERDICT_INCONCLUSIVE).contains(verdict)) {
            problems.add("verdict not in {SUPPORTED, REFUTED, INCONCLUSIVE}: " + verdict);
        }
        if (VERDICT_SUPPORTED.equals(verdict) && assetRefs.isEmpty()) {
            problems.add("assetRefs (verdict=SUPPORTED 需资产引用支撑，禁无依据断言)");
        }
        return problems;
    }
}
