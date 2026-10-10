package com.chinacreator.gzcm.engine.ai.wagent.orchestrator;

import java.util.Map;

/**
 * 步骤执行接缝（tool/llm/aggregate 的引擎调用唯一消费面）。
 *
 * <p>编排核心只经本接口触达工具面与叙述面：真实实现随 #130（工具目录 + EngineEndpointInvoker）
 * 与 #131（Candidate/发布通道）落地；LLM 调用必须走 llm-gateway 门面（卷 06 单出口纪律）。
 * 缺席时 {@link StepExecutor} 以 {@code E-WA-DEP} 失败登记——<b>禁伪称执行成功</b>。</p>
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

        public static Outcome failure(String errorCode, String errorText) {
            return new Outcome(false, Map.of(), errorCode, errorText, Map.of());
        }
    }

    /** 同步执行单步（等待/子 Run 型步骤不经本面，见 {@link StepExecutor} 分派）。 */
    Outcome invoke(Request request);
}
