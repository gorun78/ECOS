package com.chinacreator.gzcm.engine.cognitive.api.asset;

import java.util.Optional;
import java.util.Set;

/**
 * Belief 点概率语义三分（《认知域与智能域补充详细设计 v1.0》§8.3 / §16.5 红线 4）。
 *
 * <p><b>载体</b>：V268 {@code ecos_cognitive.ecos_cognitive_belief.prob_kind}（与 V262
 * {@code probability NUMERIC(6,5)} 同一张表的语义配套列）。DDL 不建 CHECK，词表收口本枚举
 * （R-15 同纪律）。</p>
 *
 * <p><b>红线（§8.3 / §16.5，fail-loud 铁律）</b>：
 * <ul>
 *   <li>{@link #UNCALIBRATED_CONFIDENCE} / {@link #UNCALIBRATED_MODEL_ESTIMATE}（LLM/模型
 *   置信估计）<b>禁止</b>直接写入 {@code probability} 列、禁止入金额计算、禁止入自动审批
 *   （SUP-COG-02 / SUP-MODEL-03）——带该类 kind 的点概率输入一律 400 拒绝，只能走
 *   被否通道（候选提案 / 审计留痕），待治理校准后方可回写；</li>
 *   <li>{@link #STATISTICAL_PROBABILITY} 需记录模型版本/数据集/范围与校准状态；
 *   {@link #CALIBRATED_MODEL_PROBABILITY} 需声明校准方法（{@code calibration_ref} 必填语义，
 *   应用侧校验）；</li>
 *   <li>存量 {@code probability 非 NULL + prob_kind IS NULL} → 应用侧读为
 *   {@link #UNKNOWN_LEGACY} 并纳入治理清单（§8.3 末段：<b>禁伪造补齐</b>，不得假设为统计概率）。</li>
 * </ul>
 * </p>
 */
public enum BeliefProbabilityKind {

    /** 可复算统计模型产生的概率（记录模型版本/数据集/范围/校准状态）。 */
    STATISTICAL_PROBABILITY,

    /** 通过校准门禁的模型概率（声明校准方法与适用域）。 */
    CALIBRATED_MODEL_PROBABILITY,

    /** 模型/LLM 置信估计——禁直入 probability 列，禁入金额计算/自动审批。 */
    UNCALIBRATED_CONFIDENCE,

    /** 未校准模型估计（同 UNCALIBRATED 族：禁直入 probability 列）。 */
    UNCALIBRATED_MODEL_ESTIMATE,

    /** 存量兜底词：probability 有值但来源不可考（只读回落，禁新写入、禁伪造补齐）。 */
    UNKNOWN_LEGACY;

    /** 属 UNCALIBRATED 族（§8.3 红线拦截面）。 */
    public boolean isUncalibrated() {
        return this == UNCALIBRATED_CONFIDENCE || this == UNCALIBRATED_MODEL_ESTIMATE;
    }

    /** 可作已验证概率参与计算/审批的族（§8.3 白名单）。 */
    public boolean isCalibrated() {
        return this == STATISTICAL_PROBABILITY || this == CALIBRATED_MODEL_PROBABILITY;
    }

    /** DB 值解析（大小写不敏感；未登记录面 ⇒ empty，调用方 fail-loud，禁静默默认）。 */
    public static Optional<BeliefProbabilityKind> fromDbValue(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(valueOf(raw.trim().toUpperCase()));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** 词表字面量集合（目录/错误消息面单源）。 */
    public static Set<String> vocabulary() {
        return java.util.Arrays.stream(values()).map(Enum::name)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
    }
}
