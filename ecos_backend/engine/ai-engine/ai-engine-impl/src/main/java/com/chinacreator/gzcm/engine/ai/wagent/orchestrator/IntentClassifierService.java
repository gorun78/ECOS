package com.chinacreator.gzcm.engine.ai.wagent.orchestrator;

import com.chinacreator.gzcm.engine.ai.wagent.WAgentEnums.GoalIntent;
import com.chinacreator.gzcm.runtime.llm.gateway.ChatMessage;
import com.chinacreator.gzcm.runtime.llm.gateway.ChatRequest;
import com.chinacreator.gzcm.runtime.llm.gateway.ChatResponse;
import com.chinacreator.gzcm.runtime.llm.gateway.LLMGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 意图分类服务（详细设计-10 F10-05，{@link IntentClassifyPort} 的 LLM 通道实现）。
 *
 * <p><b>通道单源</b>：只走 llm-gateway {@link LLMGateway#call(ChatRequest)}
 * （F06-05 出口收一；{@code LLMGatewayService} 已废弃 forRemoval，禁用）。model 缺省由
 * 网关默认路由解析（禁在本域散落模型名字面量）。</p>
 *
 * <p><b>保守降级（F10-05 失败路径明文）</b>：网关未装配、调用异常、响应失败、输出不可解析、
 * intent 越八类词表、confidence 越 [0,1] —— 一律映射 {@link IntentClassifyPort.Verdict#unavailable()}
 * 或低置信不可用形，由上游转 CLARIFY；<b>禁 fail-open 造 Run、禁默认猜测</b>。</p>
 *
 * <p>分类器只产 intent+confidence 元数据（数字不来自 LLM 红线：置信度仅准入门槛判定，
 * 不进任何结论/预算/等级计算）。</p>
 */
@Service
public class IntentClassifierService implements IntentClassifyPort {

    private static final Logger log = LoggerFactory.getLogger(IntentClassifierService.class);

    /** callerId 单源（llm-gateway 计量/ABAC 主体标识，形态沿 {@code llm-gateway:<subsystem>:<profile>}）。 */
    static final String CALLER_ID = "llm-gateway:wagent:intent-classifier";

    /** 输出契约：要求模型严格输出一行 {@code INTENT=<8类>|CONFIDENCE=<0~1>}。 */
    private static final Pattern LINE_PATTERN =
            Pattern.compile("INTENT\\s*=\\s*([A-Za-z_]+)\\s*\\|\\s*CONFIDENCE\\s*=\\s*(\\d+(?:\\.\\d+)?)");

    private static final String SYSTEM_PROMPT =
            "你是业务意图分类器。只允许输出恰好一行，格式：INTENT=<类别>|CONFIDENCE=<0到1的小数>。"
                    + "类别只能取以下 8 个之一：" + String.join("/", GoalIntent.ALL)
                    + "。无法确定时输出最低的把握值。除该行外不得输出任何其它文本。";

    private final ObjectProvider<LLMGateway> llmGatewayProvider;

    public IntentClassifierService(ObjectProvider<LLMGateway> llmGatewayProvider) {
        this.llmGatewayProvider = llmGatewayProvider;
    }

    @Override
    public Verdict classify(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return Verdict.unavailable();
        }
        LLMGateway gateway = llmGatewayProvider.getIfAvailable();
        if (gateway == null) {
            log.warn("[F10-05] llm-gateway 未装配 ⇒ 保守 clarify（禁 fail-open）");
            return Verdict.unavailable();
        }
        ChatRequest request = new ChatRequest();
        request.setCallerId(CALLER_ID);
        request.setTemperature(0.0);
        request.setStream(false);
        request.setMessages(List.of(
                new ChatMessage("system", SYSTEM_PROMPT),
                new ChatMessage("user", rawText)));
        ChatResponse response;
        try {
            response = gateway.call(request);
        } catch (Exception e) {
            log.warn("[F10-05] 意图分类调用异常 ⇒ 保守 clarify: {}", e.getMessage());
            return Verdict.unavailable();
        }
        if (response == null || !response.isSuccess()
                || response.getContent() == null || response.getContent().isBlank()) {
            log.warn("[F10-05] 意图分类响应不可用 ⇒ 保守 clarify");
            return Verdict.unavailable();
        }
        return parse(response.getContent());
    }

    /** 严格解析（越界即不可用，禁四舍五入/宽容猜测）。 */
    static Verdict parse(String content) {
        Matcher matcher = LINE_PATTERN.matcher(content);
        if (!matcher.find()) {
            return Verdict.unavailable();
        }
        String intent = matcher.group(1).trim().toUpperCase(Locale.ROOT);
        if (!GoalIntent.isValid(intent)) {
            return Verdict.unavailable();
        }
        BigDecimal confidence;
        try {
            confidence = new BigDecimal(matcher.group(2).trim());
        } catch (NumberFormatException e) {
            return Verdict.unavailable();
        }
        if (confidence.signum() < 0 || confidence.compareTo(BigDecimal.ONE) > 0) {
            return Verdict.unavailable();
        }
        return Verdict.of(GoalIntent.valueOf(intent).value(), confidence);
    }
}
