package com.chinacreator.gzcm.engine.ai.wagent.orchestrator;

import java.util.Map;

/**
 * 步骤执行接缝（tool/llm/aggregate 的引擎调用唯一消费面）。
 *
 * <p>编排核心只经本接口触达工具面与叙述面。实现 = {@code WAgentStepInvoker}
 * （tool 通道委派 {@code EngineEndpointInvoker} → 咽喉 {@code ToolExecutorService.execute}；
 * llm 通道只走 llm-gateway 门面，卷 06 单出口纪律）。
 * {@code aggregate}/{@code sub_run} 的计算与派生口径属 #130/#131 未交付范围，
 * 实现面<b>如实失败并点名缺口</b>；接缝整体缺席时 {@link StepExecutor} 以
 * {@code E-WA-DEP} 失败登记——<b>禁伪称执行成功</b>。</p>
 */
public interface StepInvokerPort {

    /** 执行请求（只带受控字段与引用；输入为已解析槽位）。 */
    record Request(String tenantId, String runId, String stepKey, String stepType,
                   String toolName, String toolVersion, Map<String, Object> input,
                   Integer automationLevel) {
    }

    /** 执行结果：success=false 时 errorCode/errorText 必填（写失败行留痕用）。 */
    record Outcome(boolean success, Map<String, Object> output, String errorCode, String errorText,
                   Map<String, Object> metrics) {

        public static Outcome ok(Map<String, Object> output) {
            return new Outcome(true, output, null, null, Map.of());
        }

        /** 成功且带 metrics（§4.2 StepWriter 的 metrics 留痕面：契约校验结论、耗时、调用标识）。 */
        public static Outcome ok(Map<String, Object> output, Map<String, Object> metrics) {
            return new Outcome(true, output, null, null,
                    metrics == null ? Map.of() : metrics);
        }

        public static Outcome failure(String errorCode, String errorText) {
            return new Outcome(false, Map.of(), errorCode, errorText, Map.of());
        }

        /**
         * 失败且携带部分输出与 metrics（输出契约违约时已拿到的返回体必须留证，
         * 禁为了"看起来干净"丢掉证据；errorCode 一律取 §5.3 W 域码字面量）。
         */
        public static Outcome failure(String errorCode, String errorText,
                                      Map<String, Object> output, Map<String, Object> metrics) {
            return new Outcome(false, output == null ? Map.of() : output, errorCode, errorText,
                    metrics == null ? Map.of() : metrics);
        }
    }

    /** 同步执行单步（等待/子 Run 型步骤不经本面，见 {@link StepExecutor} 分派）。 */
    Outcome invoke(Request request);
}
