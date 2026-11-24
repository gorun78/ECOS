package com.chinacreator.gzcm.engine.ai.toolcontract;

import java.util.Map;

/**
 * 步骤执行接缝（2026-11-23 去 wagent 化迁入本包；原 {@code wagent.orchestrator.StepInvokerPort}）。
 *
 * <p>只保留<b>工具通道</b>真正需要的最小面：{@link Request} / {@link Outcome} 两个载荷 +
 * {@link #invoke(Request)}。原接口的 llm/aggregate/sub_run 分派语义随 wagent 编排核心一并删除
 * —— 本接口现在的实现方是 {@code EngineEndpointInvoker}（工具执行走咽喉
 * {@code ToolExecutorService.execute}），不再承担 Run 编排。</p>
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

        /** 成功且带 metrics（契约校验结论、耗时、调用标识）。 */
        public static Outcome ok(Map<String, Object> output, Map<String, Object> metrics) {
            return new Outcome(true, output, null, null,
                    metrics == null ? Map.of() : metrics);
        }

        public static Outcome failure(String errorCode, String errorText) {
            return new Outcome(false, Map.of(), errorCode, errorText, Map.of());
        }

        /**
         * 失败且携带部分输出与 metrics（输出契约违约时已拿到的返回体必须留证，
         * 禁为了"看起来干净"丢掉证据）。
         */
        public static Outcome failure(String errorCode, String errorText,
                                      Map<String, Object> output, Map<String, Object> metrics) {
            return new Outcome(false, output == null ? Map.of() : output, errorCode, errorText,
                    metrics == null ? Map.of() : metrics);
        }
    }

    /** 同步执行单步。 */
    Outcome invoke(Request request);
}
