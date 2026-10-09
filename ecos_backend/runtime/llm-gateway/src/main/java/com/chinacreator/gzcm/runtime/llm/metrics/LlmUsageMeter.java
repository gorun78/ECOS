package com.chinacreator.gzcm.runtime.llm.metrics;

import com.chinacreator.gzcm.common.context.TenantContextHolder;
import com.chinacreator.gzcm.common.event.EventTypes;
import com.chinacreator.gzcm.common.token.TokenEstimates;
import com.chinacreator.gzcm.common.trace.EcosTraceContext;
import com.chinacreator.gzcm.runtime.llm.gateway.ChatMessage;
import com.chinacreator.gzcm.runtime.llm.gateway.ChatRequest;
import com.chinacreator.gzcm.runtime.llm.gateway.ChatResponse;
import com.chinacreator.gzcm.runtime.llm.model.AgentCallLog;
import com.chinacreator.gzcm.runtime.llm.repository.AgentCallLogRepository;
import com.chinacreator.gzcm.runtime.llm.security.SecurityEngineBridge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * LLM 用量唯一计量点（详细设计-06 F06-07 要点 1/2；铁律 §2.5-2 出口收一）。
 *
 * <p>由 {@code LLMGatewayImpl} 在响应返回前调用一次，做三件事：</p>
 * <ol>
 *   <li><b>补齐用量语义</b>：provider 在 usage 段上报 → 直接采用（{@code estimated=false}）；
 *       未上报 → 按 {@link TokenEstimates} 估算并回写响应，同时标 {@code estimated=true}。
 *       <b>禁把估算值当实测值上报</b>（X-39/X-88）。</li>
 *   <li><b>{@code LLM_USAGE} 事件</b>：经 {@link SecurityEngineBridge} 的 ecos.audit 通道发出
 *       （llm-gateway 不依赖 runtime-event，故复用桥已有的反射单通道，铁律 §2.5-7 禁另立 KafkaTemplate）。
 *       载荷只含模型/令牌计数/耗时等数值与标识，<b>不含 prompt 与补全原文</b>。</li>
 *   <li><b>{@code sys_agent_call_log} 内部查询视图</b>：落一行带 {@code session_id} 的调用记录，
 *       作为会话配额读数（{@link #sessionTokenUsage}）与运维查询面。</li>
 * </ol>
 *
 * <p><b>失败可见性</b>：事件未送达或落库异常一律 ERROR 留痕（消灭 X-39 的"接口存在、注册被删"
 * 静默断链形态），但不翻转已发生的模型调用结果 —— 调用已产生费用，翻转会误导重试。</p>
 */
@Component
public class LlmUsageMeter {

    private static final Logger log = LoggerFactory.getLogger(LlmUsageMeter.class);

    private static final String MODULE = "llm-gateway";

    /** {@code session_id NOT NULL} 的"无会话"占位桶（真实会话配额查询永不传此值） */
    static final String NO_SESSION = "";

    private final AgentCallLogRepository repository;
    private final ObjectProvider<SecurityEngineBridge> securityBridgeProvider;

    public LlmUsageMeter(AgentCallLogRepository repository,
                         ObjectProvider<SecurityEngineBridge> securityBridgeProvider) {
        this.repository = repository;
        this.securityBridgeProvider = securityBridgeProvider;
    }

    /**
     * 一次 chat 调用的计量（恰一条 {@code LLM_USAGE}）。
     *
     * @param request   本次请求（取 sessionId / messages 估算面 / callerId）
     * @param callerId  已归一的调用方主体（{@code llm-gateway:<subsystem>:<profile>} 或引擎自报主体）
     * @param provider  provider 标识（openai / deepseek / anthropic / openrouter ...）
     * @param response  即将返回给调用方的响应（本方法回写 latency 与 usage 标记/估算值）
     * @param latencyMs 网关侧本次调用耗时
     */
    public void meterChat(ChatRequest request, String callerId, String provider,
                          ChatResponse response, long latencyMs) {
        if (response == null) {
            return;
        }
        long elapsed = Math.max(0, latencyMs);
        response.setLatencyMs(elapsed);
        boolean estimated = applyEstimate(request, response);
        publish(buildUsageEvent(request, callerId, provider, response, elapsed, estimated), callerId, provider);
        persist(request, callerId, provider, response, elapsed);
    }

    /**
     * provider 上报优先，否则按 tokenizer 估算并回写。
     *
     * @return true = 本次用量为估算态（必须随事件上报 {@code estimated=true}）
     */
    private boolean applyEstimate(ChatRequest request, ChatResponse response) {
        if (!response.isSuccess() || response.isUsageReported()) {
            return false;
        }
        response.setTokensInput(estimatePromptTokens(request));
        response.setTokensOutput(TokenEstimates.estimateText(response.getContent()));
        response.setUsageEstimated(true);
        return true;
    }

    private int estimatePromptTokens(ChatRequest request) {
        List<ChatMessage> messages = request == null ? null : request.getMessages();
        if (messages == null) {
            return 0;
        }
        int total = 0;
        for (ChatMessage msg : messages) {
            if (msg != null) {
                total += TokenEstimates.estimateMessage(msg.getContent());
            }
        }
        return total;
    }

    /** 事件载荷：数值 + 标识，无 prompt/补全原文（F06-03 要点 2 同纪律）。 */
    private Map<String, Object> buildUsageEvent(ChatRequest request, String callerId, String provider,
                                                ChatResponse response, long latencyMs, boolean estimated) {
        String model = resolveModel(request, response);
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("eventId", UUID.randomUUID().toString());
        event.put("timestamp", Instant.now().toString());
        event.put("module", MODULE);
        event.put("userId", callerId);
        event.put("tenantId", tenantId());
        event.put("traceId", EcosTraceContext.currentTraceId());
        event.put("sessionId", sessionIdOf(request));
        event.put("eventType", EventTypes.Llm.LLM_USAGE);
        event.put("action", "llm:usage:" + provider);
        event.put("resource", "model=" + model);
        event.put("result", response.isSuccess() ? "SUCCESS" : "FAILED");

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("callerId", callerId);
        detail.put("subsystem", subsystemOf(callerId));
        detail.put("profileName", profileOf(callerId, model));
        detail.put("provider", provider);
        detail.put("model", model);
        detail.put("promptTokens", response.getTokensInput());
        detail.put("completionTokens", response.getTokensOutput());
        detail.put("totalTokens", (long) response.getTokensInput() + response.getTokensOutput());
        detail.put("estimated", estimated);
        detail.put("usageSource", usageSource(response, estimated));
        detail.put("latencyMs", latencyMs);
        event.put("detail", detail);
        return event;
    }

    private static String usageSource(ChatResponse response, boolean estimated) {
        if (estimated) {
            return "ESTIMATED";
        }
        if (response.isUsageReported()) {
            return "REPORTED";
        }
        return "NONE";
    }

    private void publish(Map<String, Object> event, String callerId, String provider) {
        SecurityEngineBridge bridge = securityBridgeProvider.getIfAvailable();
        if (bridge == null) {
            log.error("[llm-gateway] LLM_USAGE 无法上报：SecurityEngineBridge 未装配，"
                    + "ecos.audit 通道缺失（计量断链，禁静默）: caller={}, provider={}", callerId, provider);
            return;
        }
        if (!bridge.publishStructuredAudit(event)) {
            log.error("[llm-gateway] LLM_USAGE 上报失败：EventBusService/KafkaTemplate 均不可用（禁静默）: caller={}, provider={}",
                    callerId, provider);
        }
    }

    private void persist(ChatRequest request, String callerId, String provider,
                         ChatResponse response, long latencyMs) {
        String model = resolveModel(request, response);
        String subsystem = requireNonBlank(subsystemOf(callerId), MODULE);
        AgentCallLog row = AgentCallLog.builder()
                .id(UUID.randomUUID().toString().replace("-", ""))
                .subsystem(truncate(subsystem, 64))
                .profileName(truncate(profileOf(callerId, model), 128))
                .sessionId(truncate(sessionIdOf(request), 64))
                // user_message 列留空：prompt 原文属敏感面，本表无脱敏通道（F06-03 要点 2 同纪律）
                .tokensInput(Math.max(0, response.getTokensInput()))
                .tokensOutput(Math.max(0, response.getTokensOutput()))
                .durationMs((int) Math.min(Integer.MAX_VALUE, latencyMs))
                .status(response.isSuccess() ? "success" : "failed")
                .createdTime(LocalDateTime.now())
                .build();
        try {
            repository.insert(row);
        } catch (Exception e) {
            log.error("[llm-gateway] sys_agent_call_log 落库失败（会话配额读数会偏小，禁静默）: "
                            + "session={}, provider={}, model={}, err={}",
                    row.getSessionId(), provider, model, e.getMessage(), e);
        }
    }

    /**
     * 会话累计 token（配额判定的权威读数，F06-07 要点 3）。
     *
     * @return 无会话标识或无记录时 0
     */
    public long sessionTokenUsage(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return 0L;
        }
        try {
            return repository.sumTokensBySession(sessionId);
        } catch (Exception e) {
            log.error("[llm-gateway] 会话 token 读数失败（按 0 处理会放过超限，禁静默）: session={}, err={}",
                    sessionId, e.getMessage(), e);
            return 0L;
        }
    }

    // ── 归一与兜底 ─────────────────────────────────────────────────────────

    private static String resolveModel(ChatRequest request, ChatResponse response) {
        String model = response.getModel();
        if (model == null || model.isBlank()) {
            model = request == null ? null : request.getModel();
        }
        return model == null || model.isBlank() ? "unknown" : model;
    }

    /** {@code llm-gateway:<subsystem>:<profile>} → subsystem；其余形态原样作 subsystem。 */
    static String subsystemOf(String callerId) {
        String[] parts = split(callerId);
        return parts.length >= 2 ? parts[1] : callerId;
    }

    /** {@code llm-gateway:<subsystem>:<profile>} → profile；否则回落 model。 */
    static String profileOf(String callerId, String model) {
        String[] parts = split(callerId);
        if (parts.length >= 3 && !parts[2].isBlank()) {
            return parts[2];
        }
        return requireNonBlank(model, "default");
    }

    private static String[] split(String callerId) {
        return callerId == null ? new String[0] : callerId.split(":");
    }

    private static String sessionIdOf(ChatRequest request) {
        String sid = request == null ? null : request.getSessionId();
        return sid == null || sid.isBlank() ? NO_SESSION : sid;
    }

    private static String tenantId() {
        try {
            String t = TenantContextHolder.getTenantId();
            return t == null || t.isBlank() ? "default" : t;
        } catch (Exception ignored) {
            return "default";
        }
    }

    private static String requireNonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String truncate(String value, int maxLen) {
        if (value == null || value.length() <= maxLen) {
            return value;
        }
        return value.substring(0, maxLen);
    }
}
