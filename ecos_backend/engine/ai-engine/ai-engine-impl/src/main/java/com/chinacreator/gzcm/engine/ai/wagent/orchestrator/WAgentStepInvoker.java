package com.chinacreator.gzcm.engine.ai.wagent.orchestrator;

import com.chinacreator.gzcm.engine.ai.wagent.WAgentEnums.StepType;
import com.chinacreator.gzcm.engine.ai.wagent.WAgentErrorCodes.WAgentErrorCode;
import com.chinacreator.gzcm.engine.ai.wagent.tool.EngineEndpointInvoker;
import com.chinacreator.gzcm.runtime.llm.gateway.ChatMessage;
import com.chinacreator.gzcm.runtime.llm.gateway.ChatRequest;
import com.chinacreator.gzcm.runtime.llm.gateway.ChatResponse;
import com.chinacreator.gzcm.runtime.llm.gateway.LLMGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link StepInvokerPort} 的装配实现（详细设计-10 F10-14/§4.2 {@code invoke(...)} 分派位；
 * 差距分析 P0-5「W Agent 执行回路未通」的执行面收口）。
 *
 * <p><b>分派表（StepType 六类单源）</b></p>
 * <ul>
 *   <li>{@code tool} → {@link EngineEndpointInvoker}（契约纵深防御 + 咽喉调用 + 输出契约校验）；</li>
 *   <li>{@code llm} → llm-gateway 门面 {@link LLMGateway#call(ChatRequest)}（F06-05 出口收一，
 *       引擎侧禁自持 Provider/明文密钥；先例 {@code IntentClassifierService}）；</li>
 *   <li>{@code human_gate}/{@code wait_task} → 不经本接缝（{@link StepExecutor} 在分派前处理，
 *       误达即 E-VALIDATION 留痕）；</li>
 *   <li>{@code aggregate}/{@code sub_run} → <b>如实失败</b>：聚合计算口径与子 Run 管线派生
 *       在详设-10 §4.2 属 NarrativeCompose/ClaimVerifier 与 #130/#131 范围，未随本批交付。
 *       禁在本接缝自造聚合口径或伪称成功。</li>
 * </ul>
 *
 * <p><b>未实装能力的出口码裁决（登记）</b>：aggregate/sub_run 落
 * {@link WAgentErrorCode#E_NOT_FOUND} 而非 {@code E-WA-DEP}——后者模板承诺"依赖服务暂不可用，
 * 请稍后重试"，会把"能力尚未实现"洗成"临时抖动"并诱导无谓重试。依据 = 咽喉对
 * {@code STUB_CAPABILITY}（501 不归 502）的同类论证（{@code ToolExecutorService} 阶段 3 注释，
 * X-50 禁伪成功同族）。§5.3 冻结表内没有"未实现"专码，<b>禁新造码</b>，取表内最近义 +
 * 精确缺口文案入 error_text/metrics 供对账。</p>
 *
 * <p><b>LLM 通道只产叙述，不产事实</b>：本类不校验数字与引用（F10-24 ClaimVerifier 属 #131），
 * 下游禁把 LLM 文本当数值来源；{@code temperature}/{@code max_tokens} 只接受入参里的合法标量，
 * 缺省走低温确定性档。</p>
 */
@Component
public class WAgentStepInvoker implements StepInvokerPort {

    private static final Logger log = LoggerFactory.getLogger(WAgentStepInvoker.class);

    /** callerId 单源（llm-gateway 计量/ABAC 主体标识，形态沿 {@code llm-gateway:<subsystem>:<profile>}）。 */
    static final String CALLER_ID = "llm-gateway:wagent:step-invoker";

    /** llm 步骤入参键（详设未定 llm 步的槽位形态——本接缝自订最小契约并登记）。 */
    static final String KEY_PROMPT = "prompt";
    static final String KEY_SYSTEM_PROMPT = "system_prompt";
    static final String KEY_TEMPERATURE = "temperature";
    static final String KEY_MAX_TOKENS = "max_tokens";

    /** llm 步骤输出键（原样透传网关读数，缺用量禁冒充实测）。 */
    static final String OUT_CONTENT = "content";
    static final String OUT_MODEL = "model";
    static final String OUT_TOKENS_INPUT = "tokens_input";
    static final String OUT_TOKENS_OUTPUT = "tokens_output";
    static final String OUT_USAGE_REPORTED = "usage_reported";
    static final String OUT_USAGE_ESTIMATED = "usage_estimated";

    static final String METRIC_TOKEN_USAGE = "token_usage";
    static final String METRIC_CALLER_ID = "caller_id";
    static final String METRIC_GAP = "capability_gap";

    private final EngineEndpointInvoker endpointInvoker;
    private final ObjectProvider<LLMGateway> llmGatewayProvider;

    public WAgentStepInvoker(EngineEndpointInvoker endpointInvoker,
                             ObjectProvider<LLMGateway> llmGatewayProvider) {
        this.endpointInvoker = endpointInvoker;
        this.llmGatewayProvider = llmGatewayProvider;
    }

    @Override
    public Outcome invoke(Request request) {
        if (request == null || request.stepType() == null || request.stepType().isBlank()) {
            return Outcome.failure(WAgentErrorCode.E_VALIDATION.wireCode(), "步骤执行请求缺 step_type");
        }
        String type = request.stepType();
        if (StepType.TOOL.value().equals(type)) {
            return endpointInvoker.invoke(request);
        }
        if (StepType.LLM.value().equals(type)) {
            return narrate(request);
        }
        if (StepType.AGGREGATE.value().equals(type)) {
            return gap(request, "aggregate 计算口径未随本批交付：§4.2 的聚合属 "
                    + "NarrativeCompose/ClaimVerifier（#131），本接缝禁自造聚合口径");
        }
        if (StepType.SUB_RUN.value().equals(type)) {
            return gap(request, "sub_run 子 Run 管线派生未随本批交付（#130/#131），"
                    + "深度闸与消耗回计在 StepExecutor 侧已实装");
        }
        // human_gate / wait_task 由 StepExecutor 前置分派，到此即调用面越界
        return Outcome.failure(WAgentErrorCode.E_VALIDATION.wireCode(),
                "step_type 不经步骤执行接缝（StepType 单源）: " + type);
    }

    // ── llm 通道 ────────────────────────────────────────────────────────

    private Outcome narrate(Request request) {
        Map<String, Object> input = request.input() == null ? Map.of() : request.input();
        String prompt = text(input.get(KEY_PROMPT));
        if (prompt == null) {
            // §5.3 E-VALIDATION 口径：文案指出具体字段
            return Outcome.failure(WAgentErrorCode.E_VALIDATION.wireCode(),
                    "llm 步骤缺少必需入参: " + KEY_PROMPT + "（step=" + request.stepKey() + "）");
        }
        LLMGateway gateway = llmGatewayProvider.getIfAvailable();
        if (gateway == null) {
            log.warn("[F10-14] llm-gateway 未装配 ⇒ 叙述步骤失败（禁 fail-open 造结论）: run={} step={}",
                    request.runId(), request.stepKey());
            return Outcome.failure(WAgentErrorCode.E_WA_DEP.wireCode(),
                    WAgentErrorCode.E_WA_DEP.renderMessage(null, "llm-gateway"));
        }

        ChatRequest chat = new ChatRequest();
        // model 留空 = 网关默认路由（禁在引擎侧散落模型名字面量/上限常量）
        chat.setCallerId(CALLER_ID);
        chat.setSessionId(request.runId());
        chat.setStream(false);
        chat.setTemperature(temperatureOf(input.get(KEY_TEMPERATURE)));
        Integer maxTokens = intOrNull(input.get(KEY_MAX_TOKENS));
        if (maxTokens != null && maxTokens > 0) {
            chat.setMaxTokens(maxTokens);
        }
        String systemPrompt = text(input.get(KEY_SYSTEM_PROMPT));
        chat.setMessages(systemPrompt == null
                ? List.of(new ChatMessage("user", prompt))
                : List.of(new ChatMessage("system", systemPrompt), new ChatMessage("user", prompt)));

        ChatResponse response;
        try {
            response = gateway.call(chat);
        } catch (Exception e) {
            log.error("[F10-14] llm-gateway 调用异常: run={} step={} raw={}",
                    request.runId(), request.stepKey(), e.getMessage(), e);
            return Outcome.failure(WAgentErrorCode.E_WA_DEP.wireCode(),
                    WAgentErrorCode.E_WA_DEP.renderMessage(null, "llm-gateway"));
        }
        if (response == null) {
            log.warn("[F10-14] llm-gateway 空响应: run={} step={}", request.runId(), request.stepKey());
            return Outcome.failure(WAgentErrorCode.E_WA_DEP.wireCode(),
                    WAgentErrorCode.E_WA_DEP.renderMessage(null, "llm-gateway"));
        }
        if (!response.isSuccess()) {
            WAgentErrorCode code = EngineEndpointInvoker.mapToWAgentCode(response.getErrorClass());
            log.warn("[F10-14] llm-gateway 失败: run={} step={} wCode={} aiClass={} retries={}",
                    request.runId(), request.stepKey(), code.wireCode(), response.getErrorClass(),
                    response.getRetriesAttempted());
            Map<String, Object> metrics = new LinkedHashMap<>();
            metrics.put(METRIC_CALLER_ID, CALLER_ID);
            metrics.put("llm_error_class",
                    response.getErrorClass() == null ? "UNCLASSIFIED" : response.getErrorClass());
            metrics.put("llm_retries_attempted", response.getRetriesAttempted());
            // 网关内部原因摘要只入日志（X-49），对外用 W 域模板
            return new Outcome(false, Map.of(), code.wireCode(),
                    code.renderMessage(null) + " | step=" + request.stepKey(), metrics);
        }
        String content = response.getContent();
        if (content == null || content.isBlank()) {
            log.warn("[F10-14] llm-gateway 成功但正文为空: run={} step={}",
                    request.runId(), request.stepKey());
            return Outcome.failure(WAgentErrorCode.E_INTERNAL.wireCode(),
                    "叙述步骤返回空正文（无法聚合，禁伪称成功）");
        }

        Map<String, Object> output = new LinkedHashMap<>();
        output.put(OUT_CONTENT, content);
        if (response.getModel() != null && !response.getModel().isBlank()) {
            output.put(OUT_MODEL, response.getModel());
        }
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put(METRIC_CALLER_ID, CALLER_ID);
        if (response.hasUsage()) {
            output.put(OUT_TOKENS_INPUT, response.getTokensInput());
            output.put(OUT_TOKENS_OUTPUT, response.getTokensOutput());
            output.put(OUT_USAGE_REPORTED, response.isUsageReported());
            output.put(OUT_USAGE_ESTIMATED, response.isUsageEstimated());
            metrics.put(METRIC_TOKEN_USAGE,
                    response.isUsageReported() ? "reported" : "estimated");
        } else {
            // 缺用量 ≠ 0（禁把默认值冒充实测，与 ChatResponse.MISSING 语义同源）
            metrics.put(METRIC_TOKEN_USAGE, "missing");
        }
        if (response.getLatencyMs() != ChatResponse.MISSING) {
            metrics.put("duration_ms", response.getLatencyMs());
        }
        // LLM 调用计数单源在编排器（executeLoop 的 plusLlmCalls），本接缝禁另记账位
        return Outcome.ok(output, metrics);
    }

    private Outcome gap(Request request, String gapText) {
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put(METRIC_GAP, request.stepType());
        log.warn("[F10-14] 能力缺口未实装: run={} step={} type={} gap={}",
                request.runId(), request.stepKey(), request.stepType(), gapText);
        return new Outcome(false, Map.of(), WAgentErrorCode.E_NOT_FOUND.wireCode(), gapText, metrics);
    }

    // ── 入参小件（只接受合法标量，越界即回缺省档，禁宽容猜测）──────────

    private static String text(Object value) {
        if (value instanceof String s && !s.isBlank()) {
            return s;
        }
        return null;
    }

    /** 温度：缺省 0.0（确定性档）；越 [0,1] 或非数值也回 0.0，禁把脏值喂网关。 */
    static Double temperatureOf(Object value) {
        if (value instanceof Number n) {
            double d = n.doubleValue();
            if (d >= 0.0 && d <= 1.0) {
                return d;
            }
        }
        return 0.0;
    }

    private static Integer intOrNull(Object value) {
        return value instanceof Number n ? n.intValue() : null;
    }
}
