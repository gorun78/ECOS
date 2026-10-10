package com.chinacreator.gzcm.engine.cognitive2.service.asset;

import com.chinacreator.gzcm.engine.cognitive.api.CognitiveErrorCode;
import com.chinacreator.gzcm.engine.cognitive.api.asset.BeliefProbabilityKind;
import com.chinacreator.gzcm.engine.cognitive2.service.scenario.CognitiveGuardException;

/**
 * Belief 置信红线网关（《补充设计 v1.0》§8.3 / §16.5 红线 4 / SUP-COG-02）。
 *
 * <p><b>唯一裁决入口</b>：任何写 {@code ecos_cognitive_belief.probability}
 * （V262 点概率列）的路径（CognitiveBeliefService 写方法 / 未来 MCP 或候选批准通道）
 * 必须先过 {@link #guard}。裁决规则：</p>
 * <ul>
 *   <li>{@code probability} 非空 + {@code kind} ∈ UNCALIBRATED 族
 *       （UNCALIBRATED_CONFIDENCE / UNCALIBRATED_MODEL_ESTIMATE）⇒
 *       <b>400 拒绝</b>——LLM/模型置信估计禁直入 probability 列、禁入金额计算/自动审批；</li>
 *   <li>{@code kind}/{@code probability} 均为空 ⇒ 合法（V262 口径：NULL = 尚未标定，禁补 0）；</li>
 *   <li>{@code probability} 非空 + {@code kind} 空 ⇒ 400 拒绝（V268 头注 ③ 要求 probability
 *       与 prob_kind 语义配套；存量 UNKNOWN_LEGACY 是<b>读侧回落</b>，禁<b>写</b>入）。</li>
 * </ul>
 *
 * <p>非 Spring 组件：由 {@code CognitiveBeliefService} 直接实例化并持有（单一读者保证
 * 全部 belief 写路径收口于此，无多 Bean 冲突面）。纯函数、零 IO、零 LLM。</p>
 */
public class BeliefLifecycleGateway {

    /**
     * 写前强校验。
     *
     * @param probability 待写点概率（可空 = 未标定，合法）
     * @param kind        概率语义分类（可空；但 probability 非空时必填）
     * @throws CognitiveGuardException 400（ECOS-COG-400P）红线命中
     */
    public void guard(Double probability, BeliefProbabilityKind kind) {
        if (probability == null) {
            return; // NULL = 未标定（V262 既有语义），置空即合法
        }
        if (kind == null) {
            throw new CognitiveGuardException(CognitiveErrorCode.E400_INVALID_PARAM,
                    CognitiveErrorCode.MSG_INVALID_PARAM, "probabilityKind-required");
        }
        if (kind.isUncalibrated()) {
            throw new CognitiveGuardException(CognitiveErrorCode.E400_INVALID_PARAM,
                    CognitiveErrorCode.MSG_INVALID_PARAM,
                    "uncalibrated-confidence-to-probability");
        }
        if (kind == BeliefProbabilityKind.UNKNOWN_LEGACY) {
            throw new CognitiveGuardException(CognitiveErrorCode.E400_INVALID_PARAM,
                    CognitiveErrorCode.MSG_INVALID_PARAM, "unknown-legacy-not-writable");
        }
    }
}
