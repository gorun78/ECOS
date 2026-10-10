package com.chinacreator.gzcm.engine.ai.wagent.orchestrator;

import java.math.BigDecimal;

/**
 * 意图分类端口（详细设计-10 F10-05：{@code WAgentQuestionService.fromUtterance →
 * IntentClassifyPort.classify()}）。
 *
 * <p>端口只产 <b>intent + confidence 元数据</b>（"数字不来自 LLM"红线的形态：分类器不产
 * 任何业务数字/结论，置信度亦只作准入判定，不进入结论面）。八类意图词表与
 * {@code goal_type} 共用 {@link com.chinacreator.gzcm.engine.ai.wagent.WAgentEnums.GoalIntent}
 * 单源，禁第二套词表。</p>
 *
 * <p>失败路径（F10-05 明文）：分类器不可用 ⇒ 返回 {@code classifierAvailable=false} 的
 * 保守判定（上游一律转 CLARIFY，<b>禁 fail-open 造 Run</b>）。</p>
 */
public interface IntentClassifyPort {

    /**
     * 分类判定。
     *
     * @param intent             八意图之一（{@code GoalIntent.value()} 大写形态）；
     *                           分类器不可用/越界时为 {@code null}
     * @param confidence         [0,1] 置信度；不可用时 {@code ZERO}
     * @param classifierAvailable 分类通道是否可用（false = 保守 clarify，禁默认猜测）
     */
    record Verdict(String intent, BigDecimal confidence, boolean classifierAvailable) {

        public static Verdict of(String intent, BigDecimal confidence) {
            return new Verdict(intent, confidence, true);
        }

        /** 保守降级形：不可用 ≠ 低置信，语义上禁建 Run、须澄清。 */
        public static Verdict unavailable() {
            return new Verdict(null, BigDecimal.ZERO, false);
        }

        /** 置信是否达建 Run 阈值（阈值判定在 {@link IntentClassifyPort#CONFIDENCE_MIN} 单源）。 */
        public boolean readyForRun() {
            return classifierAvailable && intent != null
                    && confidence != null && confidence.compareTo(CONFIDENCE_MIN) >= 0;
        }
    }

    /**
     * 建 Run 置信阈值单源（F10-05/REQ-WAG-05 冻结 0.70；{@code < 0.70} 不得建 Run）。
     * 文档未外置该阈值为配置键（V265 仅 readiness/budget 组），故留代码单源常量，
     * R4 终验时评估是否随配置面收口。
     */
    BigDecimal CONFIDENCE_MIN = new BigDecimal("0.70");

    /** 对原文分类（实现禁抛业务异常：通道故障一律映射 {@link Verdict#unavailable()}）。 */
    Verdict classify(String rawText);
}
