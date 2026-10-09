package com.chinacreator.gzcm.runtime.llm.gateway;

import com.chinacreator.gzcm.common.context.TenantContextHolder;
import com.chinacreator.gzcm.common.error.AiErrorClass;
import com.chinacreator.gzcm.common.trace.EcosTraceContext;
import com.chinacreator.gzcm.runtime.llm.config.LLMGatewayProperties;
import com.chinacreator.gzcm.runtime.llm.metrics.LlmUsageMeter;
import com.chinacreator.gzcm.runtime.llm.security.SecurityEngineBridge;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.*;
import okhttp3.sse.EventSource;
import okhttp3.sse.EventSourceListener;
import okhttp3.sse.EventSources;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * LLMGateway 实现 — 支持 OpenAI 兼容 API 的 HTTP 调用
 * <p>
 * 支持 provider: openrouter, deepseek, openai, anthropic
 * 支持 streaming (SSE) 和 non-streaming 两种模式
 * 重试（F06-14 要点 2）：按 {@link AiErrorClass} 逐类有界退避，配额在网关单源结算；
 * 限流类用尽后退备用 provider（只经本网关内部路由，X-22 禁引擎侧绕行）。
 * </p>
 */
@Primary
@Service
public class LLMGatewayImpl implements LLMGateway {

    private static final Logger log = LoggerFactory.getLogger(LLMGatewayImpl.class);

    /** 默认 provider → baseUrl 映射 */
    private static final Map<String, String> DEFAULT_BASE_URLS = new LinkedHashMap<>();
    static {
        DEFAULT_BASE_URLS.put("openrouter", "https://openrouter.ai/api/v1");
        DEFAULT_BASE_URLS.put("deepseek", "https://api.deepseek.com/v1");
        DEFAULT_BASE_URLS.put("openai", "https://api.openai.com/v1");
        DEFAULT_BASE_URLS.put("anthropic", "https://api.anthropic.com/v1");
    }

    /** provider 状态缓存 (provider → status)*/
    private final ConcurrentHashMap<String, Object> providerStatusCache = new ConcurrentHashMap<>();

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OkHttpClient okHttpClient;

    @Autowired(required = false)
    private LLMGatewayProperties llmGatewayProperties;

    /**
     * security-engine 桥（密钥解析 / ABAC 裁决 / 审计，PMO-74 N14 + 铁律 §2.4）。
     * required=false：未装配视为 security 不可用 → 默认 DENY（不放行直连、不回退明文）。
     */
    @Autowired(required = false)
    private SecurityEngineBridge securityEngineBridge;

    /**
     * 用量计量唯一落点（详细设计-06 F06-07 要点 1）：每次外发返回前记恰一条 {@code LLM_USAGE}
     * + 一行 {@code sys_agent_call_log}。required=false 只服务单测装配；生产缺失时
     * {@link #meterUsage} 记 ERROR（禁静默断链，X-39 形态）。
     */
    @Autowired(required = false)
    private LlmUsageMeter llmUsageMeter;

    // ── Provider URL 解析 ──

    /**
     * 获取指定 provider 的 base URL
     * 优先使用 LLMConfig 中显式设置的 baseUrl，否则使用默认映射
     */
    private String resolveBaseUrl(LLMConfig config) {
        if (config.getBaseUrl() != null && !config.getBaseUrl().isEmpty()) {
            return config.getBaseUrl();
        }
        String provider = config.getProvider() != null ? config.getProvider().toLowerCase() : "";
        return DEFAULT_BASE_URLS.getOrDefault(provider,
                "https://api.openai.com/v1");
    }

    // ── 核心调用 ──

    @Override
    public ChatResponse call(ChatRequest request) {
        // 配置项只能收紧、不能放大冻结矩阵（与类上限取小在 callWithPolicy 内结算）
        Integer configuredCap = llmGatewayProperties == null
                ? null : llmGatewayProperties.getGateway().getRetryMaxAttempts();
        return callWithPolicy(request, configuredCap);
    }

    /**
     * F06-14 要点 2：LLM 出口的有界退避重试<b>只在网关内部结算</b>（X-22 禁引擎侧绕行）。
     *
     * <p>逐类规则：{@code LLM_TIMEOUT} 指数退避 ≤3；{@code LLM_RATE_LIMITED} 指数退避 ≤3 后
     * 按配置降备用 provider（同属网关内部路由）；{@code GUARDRAIL_DENIED}/{@code GUARDRAIL_FAIL_CLOSED}
     * 与未分类面一律 0 次（护栏态重试等于绕过面）；{@code CONTEXT_OVERFLOW} 不重试（截断降级属
     * F06-09，引擎侧）；{@code PARSE_FAILURE} 的重生成配额属引擎侧 F06-12，网关不代扣。</p>
     *
     * @param callerCap 调用方附加上限（{@code callWithRetry} 参数），与类上限取小，禁放大冻结矩阵
     */
    private ChatResponse callWithPolicy(ChatRequest request, Integer callerCap) {
        int attempts = 0;
        ChatResponse response = attemptOnce(request);
        AiErrorClass errorClass = response.aiErrorClass();

        while (!response.isSuccess() && gatewayOwnedRetry(errorClass)) {
            int bound = callerCap == null
                    ? errorClass.maxRetries()
                    : Math.min(callerCap, errorClass.maxRetries());
            if (attempts >= bound || !errorClass.canRetry(attempts)) {
                break;
            }
            long waitMs = errorClass.backoffMillis(attempts);
            attempts++;
            log.warn("[llm-gateway] 有界退避重试 #{}/{} class={} waitMs={} model={}",
                    attempts, bound, errorClass.name(), waitMs, request.getModel());
            if (!sleepMillis(waitMs)) {
                response = ChatResponse.fail(AiErrorClass.LLM_TIMEOUT, "调用在退避等待中被中断");
                errorClass = AiErrorClass.LLM_TIMEOUT;
                break;
            }
            response = attemptOnce(request);
            errorClass = response.aiErrorClass();
        }

        if (!response.isSuccess() && attempts > 0) {
            response.setRetriesAttempted(attempts);
        }

        if (!response.isSuccess() && errorClass == AiErrorClass.LLM_RATE_LIMITED) {
            ChatResponse degraded = tryFallbackProvider(request, response);
            if (degraded != null) {
                degraded.setRetriesAttempted(attempts);
                return degraded;
            }
        }
        return response;
    }

    /**
     * 传输面重试是否归本网关结算（F06-14 要点 2 的<b>不叠加</b>约束）。
     *
     * <p>{@code PARSE_FAILURE} 的重生成配额属引擎侧 F06-12（与引用后校验共用 ≤2），网关若再代扣
     * 一次就把配额翻倍；{@code CONTEXT_OVERFLOW} 走截断降级（F06-09 引擎侧）、
     * {@code TOOL_BACKEND_ERROR} 是工具下游面 —— 三者网关一律 0 次重试，直接原样上浮分类。</p>
     */
    private static boolean gatewayOwnedRetry(AiErrorClass errorClass) {
        return errorClass == AiErrorClass.LLM_TIMEOUT || errorClass == AiErrorClass.LLM_RATE_LIMITED;
    }

    /**
     * 备用 provider 降级（F06-14 要点 2）：仅在限流类用尽退避后触发一次，
     * 目标由 {@code llm.gateway.fallback-model} 配置；未配置即不降级并原样返回限流失败
     * （禁在代码里兜底任何备用模型常量）。
     */
    private ChatResponse tryFallbackProvider(ChatRequest request, ChatResponse rateLimited) {
        String fallbackModel = llmGatewayProperties == null
                ? null : llmGatewayProperties.getGateway().getFallbackModel();
        if (fallbackModel == null || fallbackModel.isBlank() || fallbackModel.equals(request.getModel())) {
            log.warn("[llm-gateway] 限流且未配置备用 provider（llm.gateway.fallback-model），按原失败返回: model={}",
                    request.getModel());
            return null;
        }
        ChatRequest copy = request.copyWithModel(fallbackModel);
        ChatResponse degraded = attemptOnce(copy);
        if (degraded.isSuccess()) {
            degraded.setFallbackProviderUsed(true);
            log.warn("[llm-gateway] 限流降级备用 provider 成功（网关内部路由，引擎侧无感）: from={} to={}",
                    request.getModel(), fallbackModel);
            return degraded;
        }
        log.error("[llm-gateway] 限流降级备用 provider 仍失败: from={} to={} class={}",
                request.getModel(), fallbackModel, degraded.getErrorClass());
        return null;
    }

    /** 退避等待；被中断返回 false（调用方必须把它当失败处理，禁静默继续）。 */
    private static boolean sleepMillis(long waitMs) {
        if (waitMs <= 0) {
            return true;
        }
        try {
            TimeUnit.MILLISECONDS.sleep(waitMs);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * 单次 provider 调用（无重试）。护栏态与非护栏失败均带 {@link AiErrorClass} 分类返回。
     */
    private ChatResponse attemptOnce(ChatRequest request) {
        // 获取 config
        String provider = detectProvider(request.getModel());
        Double temperature = request.getTemperature();
        Integer maxTokens = request.getMaxTokens();
        Boolean stream = request.getStream() != null && request.getStream();

        // 构造 LLMConfig (从 request 中提取, 用于 resolveBaseUrl)
        LLMConfig config = new LLMConfig();
        config.setProvider(provider);
        config.setModel(request.getModel());

        String baseUrl = resolveBaseUrl(config);

        // 如果 request 没有显式设置 temperature/maxTokens，使用合理默认值
        if (temperature == null) temperature = 0.7;
        if (maxTokens == null) maxTokens = 4096;

        // 构建请求体
        String requestBodyJson;
        try {
            requestBodyJson = buildRequestBody(request.getModel(), request.getMessages(),
                    temperature, maxTokens, stream);
        } catch (JsonProcessingException e) {
            log.error("Failed to build request body JSON", e);
            return ChatResponse.fail(AiErrorClass.INVALID_ARGUMENT, "请求体序列化失败");
        }

        // ── H10-T1 (PMO-74 N14 / 铁律 §2.4)：密钥一律服务端经 security-engine 密钥服务解析 ──
        // 调用方自报的明文 apiKey 不信任：出现即 warn + 忽略（默认 DENY，不回退明文）。
        String caller = request.getCallerId() != null && !request.getCallerId().isBlank()
                ? request.getCallerId() : "service:llm-gateway";
        @SuppressWarnings("deprecation")
        String clientSuppliedKey = request.getApiKey();
        if (clientSuppliedKey != null && !clientSuppliedKey.isBlank()) {
            log.warn("[llm-gateway] 忽略调用方自报明文 apiKey（PMO-74 N14 VG-7 越权入参，服务端不消费）: caller={}, model={}",
                    caller, request.getModel());
        }

        // ABAC 裁决（security 不可用/未放行 → 默认 DENY）
        if (!abacAllowLlmCall(caller, provider, request.getModel())) {
            return ChatResponse.fail(AiErrorClass.GUARDRAIL_DENIED,
                    "LLM 调用被拒绝：security-engine ABAC 裁决未放行（默认 DENY，不降级放行）");
        }

        String apiKey = resolveProviderApiKey(request.getApiKeyRef(), caller,
                provider + ":" + request.getModel());
        if (apiKey == null) {
            return ChatResponse.fail(AiErrorClass.GUARDRAIL_FAIL_CLOSED,
                    "LLM 调用被拒绝：未取得 security-engine 密钥服务下发的 provider 密钥"
                            + "（默认 DENY — 不放行直连、不回退明文，详见 server 日志）");
        }

        if (stream) {
            long streamStart = System.currentTimeMillis();
            ChatResponse response = callStreaming(baseUrl, apiKey, requestBodyJson);
            meterUsage(request, caller, provider, response, System.currentTimeMillis() - streamStart);
            return response;
        } else {
            long callStart = System.currentTimeMillis();
            ChatResponse response = callNonStreaming(baseUrl, apiKey, requestBodyJson);
            meterUsage(request, caller, provider, response, System.currentTimeMillis() - callStart);
            return response;
        }
    }


    /**
     * 计量点转引（F06-07 要点 1：响应返回前记账，恰一条 {@code LLM_USAGE}）。
     * 计量自身异常绝不影响已发生的模型调用结果，但必须 ERROR 留痕。
     */
    private void meterUsage(ChatRequest request, String caller, String provider,
                            ChatResponse response, long latencyMs) {
        if (llmUsageMeter == null) {
            log.error("[llm-gateway] LlmUsageMeter 未装配 — 本次调用未计量（LLM_USAGE 缺失、"
                    + "会话配额读数会偏小，禁静默）: caller={}, provider={}, model={}",
                    caller, provider, request.getModel());
            return;
        }
        try {
            llmUsageMeter.meterChat(request, caller, provider, response, latencyMs);
        } catch (Exception e) {
            log.error("[llm-gateway] 计量异常（不翻转调用结果，但必须留痕）: caller={}, model={}, err={}",
                    caller, request.getModel(), e.getMessage(), e);
        }
    }

    @Override
    public long sessionTokenUsage(String sessionId) {
        if (llmUsageMeter == null) {
            log.error("[llm-gateway] LlmUsageMeter 未装配 — 会话用量读数按不可得处理: session={}", sessionId);
            return 0L;
        }
        return llmUsageMeter.sessionTokenUsage(sessionId);
    }

    /**
     * 模型上下文上限（详细设计-06 F06-09 要点 1）—— 只读 {@code llm.models.*} 目录，
     * 目录外模型回落 {@code default-context-limit}；两者皆不可用（属性缺失或 {@code <=0}）时
     * 返回 {@link ModelLimit#unavailable}，<b>不</b>在本网关兜底任何常量。
     */
    @Override
    public ModelLimit modelContextLimit(String model) {
        if (llmGatewayProperties == null) {
            return ModelLimit.unavailable(model);
        }
        LLMGatewayProperties.Models catalog = llmGatewayProperties.getModels();
        String key = model == null ? null : model.trim();
        if (key != null && !key.isEmpty()) {
            LLMGatewayProperties.Model entry = catalog.getCatalog().get(key);
            if (entry == null && key.contains("/")) {
                // 目录以 provider 侧短名登记（deepseek-chat），调用方可能带前缀（deepseek/deepseek-chat）
                entry = catalog.getCatalog().get(key.substring(key.lastIndexOf('/') + 1));
            }
            if (entry != null && entry.getContextLimit() > 0) {
                return new ModelLimit(model, entry.getContextLimit(), true, true);
            }
        }
        int fallback = catalog.getDefaultContextLimit();
        if (fallback > 0) {
            return new ModelLimit(model, fallback, false, true);
        }
        return ModelLimit.unavailable(model);
    }

    @Override
    public ChatResponse callWithRetry(ChatRequest request, int maxRetries) {
        return callWithPolicy(request, Math.max(0, maxRetries));
    }

    @Override
    public EmbeddingResponse embed(EmbeddingRequest request) {
        if (request == null) {
            return EmbeddingResponse.fail("request null");
        }
        String model = request.getModel();
        if ((model == null || model.isEmpty()) && llmGatewayProperties != null) {
            model = llmGatewayProperties.getEngine().getEmbeddingModel();
        }
        if (model == null || model.isEmpty()) {
            model = "text-embedding-3-small";
        }
        String provider = request.getProvider();
        if (provider == null || provider.isEmpty()) {
            provider = detectProvider(model);
        }
        LLMConfig cfg = new LLMConfig();
        cfg.setProvider(provider);
        cfg.setModel(model);
        String baseUrl = resolveBaseUrl(cfg);

        String[] inputs;
        if (request.getTexts() != null && request.getTexts().length > 0) {
            inputs = request.getTexts();
        } else if (request.getInput() != null && !request.getInput().isEmpty()) {
            inputs = new String[] { request.getInput() };
        } else {
            return EmbeddingResponse.fail("既无 input 也无 texts");
        }

        String body;
        try {
            body = buildEmbeddingRequestBody(model, inputs);
        } catch (JsonProcessingException e) {
            log.error("Failed to build embedding body JSON: {}", e.getMessage(), e);
            return EmbeddingResponse.fail("serialize fail: " + e.getMessage());
        }
        log.debug("LLM embed -> {} model={} provider={} n={}", baseUrl, model, provider, inputs.length);
        // 密钥与 chat 同闸（PMO-74 N14）：只经 security-engine 密钥服务取，取不到默认 DENY
        String embedCaller = request.getCallerId() != null && !request.getCallerId().isBlank()
                ? request.getCallerId() : "service:llm-gateway";
        if (!abacAllowLlmCall(embedCaller, provider, model)) {
            return EmbeddingResponse.fail("embedding 调用被拒绝：security-engine ABAC 裁决未放行（默认 DENY）");
        }
        String embedApiKey = resolveProviderApiKey(request.getApiKeyRef(), embedCaller,
                provider + ":" + model);
        if (embedApiKey == null) {
            return EmbeddingResponse.fail("embedding 调用被拒绝：未取得 security-engine 密钥（默认 DENY，不放行直连）");
        }
        return callEmbeddingNonStreaming(baseUrl, embedApiKey, body, model);
    }

    @Override
    public boolean isAvailable(LLMConfig config) {
        String baseUrl = resolveBaseUrl(config);
        String modelsUrl = baseUrl + "/models";

        // 健康探测同样只允许使用 security-engine 解析出的密钥（PMO-74 N14）；
        // 取不到密钥 → 视为不可用（默认 DENY，绝不用调用方明文密钥或空 Bearer 试探）。
        @SuppressWarnings("deprecation")
        String legacyPlainKey = config.getApiKey();
        if (legacyPlainKey != null && !legacyPlainKey.isBlank()) {
            log.warn("[llm-gateway] isAvailable: 忽略调用方自报明文 apiKey（PMO-74 N14）: provider={}",
                    config.getProvider());
        }
        String probeKey = resolveProviderApiKey(config.getApiKeyRef(), "service:llm-gateway-probe",
                "probe:" + config.getProvider());
        if (probeKey == null) {
            providerStatusCache.put(config.getProvider() != null ? config.getProvider() : "unknown",
                    Map.of("status", "denied_no_secret", "baseUrl", baseUrl));
            return false;
        }

        Request request = new Request.Builder()
                .url(modelsUrl)
                .header("Authorization", "Bearer " + probeKey)
                .header("Accept", "application/json")
                .header(EcosTraceContext.HEADER_REQUEST_ID, EcosTraceContext.currentTraceId())
                .get()
                .build();

        try (Response response = okHttpClient.newCall(request).execute()) {
            boolean available = response.isSuccessful();
            if (available) {
                providerStatusCache.put(config.getProvider() != null ? config.getProvider() : "unknown",
                        Map.of("status", "available", "baseUrl", baseUrl));
            } else {
                providerStatusCache.put(config.getProvider() != null ? config.getProvider() : "unknown",
                        Map.of("status", "unavailable", "httpCode", response.code(), "baseUrl", baseUrl));
            }
            return available;
        } catch (IOException e) {
            log.warn("Provider [{}] at [{}] is unavailable: {}", config.getProvider(), baseUrl, e.getMessage());
            providerStatusCache.put(config.getProvider() != null ? config.getProvider() : "unknown",
                    Map.of("status", "unreachable", "error", e.getMessage(), "baseUrl", baseUrl));
            return false;
        }
    }

    @Override
    public Map<String, Object> getProviderStatus() {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : DEFAULT_BASE_URLS.entrySet()) {
            Object status = providerStatusCache.getOrDefault(entry.getKey(),
                    Map.of("status", "unknown", "baseUrl", entry.getValue()));
            result.put(entry.getKey(), status);
        }
        return result;
    }

    // ── 私有方法 ──

    /**
     * H10-T1（PMO-74 N14 / 铁律 §2.4#6）— provider 密钥解析统一闸口。
     *
     * <p>解析链：调用方显式 {@code apiKeyRef} → 服务端配置
     * {@code llm.gateway.default-api-key-ref}（两者均为<b>引用</b>，非明文）→
     * security-engine 密钥服务（{@code SecurityEngineBridge} → ISecretService /
     * IDataEncryptionService）。</p>
     *
     * <p>🔴 fail-closed：桥未装配 / security-engine 不可用 / 引用为空 / 解析失败 →
     * 返回 null（调用方必须拒绝外发），并 {@code log.warn} + ecos.audit 留痕；
     * <b>绝不回退到调用方自报明文密钥</b>。</p>
     *
     * @return 明文密钥（仅供当场拼 Authorization 头，不落日志）；null = 拒绝
     */
    private String resolveProviderApiKey(String explicitRef, String caller, String resourceDesc) {
        if (securityEngineBridge == null) {
            log.warn("[llm-gateway] SecurityEngineBridge 未装配 — security-engine 不可用，密钥无法取得，默认 DENY（不回退明文、不放行直连）: caller={}, resource={}",
                    caller, resourceDesc);
            return null;
        }
        String ref = explicitRef;
        if ((ref == null || ref.isBlank()) && llmGatewayProperties != null) {
            ref = llmGatewayProperties.getGateway().getDefaultApiKeyRef();
        }
        if (ref == null || ref.isBlank()) {
            log.warn("[llm-gateway] 未配置 apiKeyRef（请求级与 llm.gateway.default-api-key-ref 均为空）— 默认 DENY，不回退明文: caller={}, resource={}",
                    caller, resourceDesc);
            securityEngineBridge.audit(caller, "LLM_APIKEY_DENY_NO_REF", resourceDesc, "DENIED");
            return null;
        }
        String key = securityEngineBridge.resolveSecret(ref, caller);
        if (key == null) {
            securityEngineBridge.audit(caller, "LLM_APIKEY_DENY_RESOLUTION_FAILED",
                    resourceDesc + " ref=" + SecurityEngineBridge.maskRef(ref), "DENIED");
            return null;
        }
        securityEngineBridge.audit(caller, "LLM_APIKEY_RESOLVED",
                resourceDesc + " ref=" + SecurityEngineBridge.maskRef(ref), "SUCCESS");
        return key;
    }

    /**
     * LLM 调用前 ABAC 裁决（铁律 §2.4#4，F06-05 要点 4：<b>无降级开关</b>）。
     * security 不可用 / 超时 / 明确拒绝 → false（默认 DENY）。
     */
    private boolean abacAllowLlmCall(String caller, String provider, String model) {
        if (securityEngineBridge == null) {
            log.warn("[llm-gateway] SecurityEngineBridge 未装配 — ABAC 裁决不可用，默认 DENY: caller={}", caller);
            return false;
        }
        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("provider", provider);
        attrs.put("model", model == null ? "" : model);
        String tenantId = null;
        try {
            tenantId = TenantContextHolder.getTenantId();
        } catch (Exception ignored) {
            // 无租户上下文 → 由桥侧回退 default
        }
        boolean allow = securityEngineBridge.evaluate(caller, tenantId, "llm:chat", attrs);
        securityEngineBridge.audit(caller, "LLM_CHAT_ABAC", provider + ":" + model, allow ? "ALLOW" : "DENY");
        return allow;
    }

    /**
     * 从 model 名称检测 provider
     */
    private String detectProvider(String model) {
        if (model == null) return "unknown";
        String m = model.toLowerCase();
        if (m.contains("openrouter") || m.contains("/")) {
            // openrouter 格式通常是 provider/model
            String prefix = model.split("/")[0].toLowerCase();
            if (DEFAULT_BASE_URLS.containsKey(prefix)) {
                return prefix;
            }
            return "openrouter";
        }
        if (m.contains("gpt") || m.contains("o1") || m.contains("o3")) return "openai";
        if (m.contains("deepseek")) return "deepseek";
        if (m.contains("claude")) return "anthropic";
        return "openrouter";
    }

    /**
     * 构建 OpenAI 兼容 /chat/completions 请求体 JSON
     */
    private String buildRequestBody(String model, List<ChatMessage> messages,
                                    Double temperature, Integer maxTokens,
                                    Boolean stream) throws JsonProcessingException {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", model);

        ArrayNode messagesArray = root.putArray("messages");
        for (ChatMessage msg : messages) {
            ObjectNode msgNode = messagesArray.addObject();
            msgNode.put("role", msg.getRole());
            msgNode.put("content", msg.getContent());
        }

        if (temperature != null) {
            root.put("temperature", temperature);
        }
        if (maxTokens != null) {
            root.put("max_tokens", maxTokens);
        }
        if (stream != null) {
            root.put("stream", stream);
        }

        return objectMapper.writeValueAsString(root);
    }

    /**
     * 构建 OpenAI 兼容 /embeddings 请求体 JSON（input 单条:string / 多条:array）
     */
    private String buildEmbeddingRequestBody(String model, String[] inputs) throws JsonProcessingException {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", model);
        if (inputs.length == 1) {
            root.put("input", inputs[0]);
        } else {
            ArrayNode arr = root.putArray("input");
            for (String s : inputs) arr.add(s);
        }
        return objectMapper.writeValueAsString(root);
    }

    /**
     * 非流式 chat 调用
     */
    private ChatResponse callNonStreaming(String baseUrl, String apiKey, String requestBodyJson) {
        String url = baseUrl + "/chat/completions";

        RequestBody body = RequestBody.create(requestBodyJson, MediaType.parse("application/json"));
        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header(EcosTraceContext.HEADER_REQUEST_ID, EcosTraceContext.currentTraceId())
                .post(body)
                .build();

        long startTime = System.currentTimeMillis();

        try (Response response = okHttpClient.newCall(request).execute()) {
            long duration = System.currentTimeMillis() - startTime;

            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "";
                log.error("LLM API error: HTTP {} {} | Body: {} | Duration: {}ms",
                        response.code(), response.message(), errorBody, duration);
                return ChatResponse.fail(classifyHttpStatus(response.code()),
                        String.format("HTTP %d: %s", response.code(),
                                errorBody.isEmpty() ? response.message() : errorBody));
            }

            String responseBody = response.body() != null ? response.body().string() : "";
            return parseNonStreamingResponse(responseBody);

        } catch (SocketTimeoutException e) {
            log.error("LLM API timed out after {}ms", System.currentTimeMillis() - startTime, e);
            return ChatResponse.fail(AiErrorClass.LLM_TIMEOUT, "provider 连接/读取超时: " + e.getMessage());
        } catch (IOException e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("LLM API call failed after {}ms: {}", duration, e.getMessage());
            return ChatResponse.fail(AiErrorClass.LLM_TIMEOUT, "IO error: " + e.getMessage());
        }
    }

    /**
     * provider HTTP 状态码 → F06-14 分类（429 限流 / 413 上下文超限 / 5xx 超时族 / 4xx 参数族）。
     *
     * <p>401/403 由 provider 侧密钥问题引起，属配置面，不当作护栏 DENY（护栏语义只归 security-engine），
     * 归入不可重试的 {@code INVALID_ARGUMENT}，文案由调用方按类渲染。</p>
     */
    static AiErrorClass classifyHttpStatus(int status) {
        if (status == 429) {
            return AiErrorClass.LLM_RATE_LIMITED;
        }
        if (status == 413) {
            return AiErrorClass.CONTEXT_OVERFLOW;
        }
        if (status >= 500) {
            return AiErrorClass.LLM_TIMEOUT;
        }
        if (status == 408) {
            return AiErrorClass.LLM_TIMEOUT;
        }
        return AiErrorClass.INVALID_ARGUMENT;
    }

    /**
     * 非流式 embedding 调用 POST {baseUrl}/embeddings
     */
    private EmbeddingResponse callEmbeddingNonStreaming(String baseUrl, String apiKey, String requestBodyJson, String model) {
        Request httpRequest = new Request.Builder()
                .url(baseUrl + "/embeddings")
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .header(EcosTraceContext.HEADER_REQUEST_ID, EcosTraceContext.currentTraceId())
                .post(RequestBody.create(requestBodyJson, MediaType.parse("application/json")))
                .build();
        long start = System.currentTimeMillis();
        try (Response response = okHttpClient.newCall(httpRequest).execute()) {
            long elapsed = System.currentTimeMillis() - start;
            if (!response.isSuccessful()) {
                String errBody = response.body() != null ? response.body().string() : "";
                log.warn("Embedding API HTTP error {} after {}ms: {}", response.code(), elapsed, errBody);
                return EmbeddingResponse.fail(String.format("HTTP %d: %s", response.code(), errBody));
            }
            String responseBody = response.body() != null ? response.body().string() : "";
            EmbeddingResponse resp = parseEmbeddingResponse(responseBody, model);
            log.debug("LLM embed OK after {}ms model={} dims={}", elapsed, model,
                    resp.getData().isEmpty() ? 0 : resp.getData().get(0).length);
            return resp;
        } catch (IOException e) {
            log.warn("Embedding API IO error after call to {}: {}", baseUrl, e.getMessage());
            return EmbeddingResponse.fail("IO error: " + e.getMessage());
        }
    }

    /**
     * 解析 OpenAI 兼容 embeddings 响应（OpenAI/DeepSeek: data[].embedding[]）
     */
    private EmbeddingResponse parseEmbeddingResponse(String responseBody, String model) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            if (root.has("error")) {
                JsonNode err = root.get("error");
                String msg = err.isTextual() ? err.asText()
                        : (err.has("message") ? err.get("message").asText() : err.toString());
                return EmbeddingResponse.fail("LLM error: " + msg);
            }
            List<float[]> vectors = new ArrayList<>();
            if (root.has("data") && root.get("data").isArray()) {
                for (JsonNode item : root.get("data")) {
                    if (item.has("embedding") && item.get("embedding").isArray()) {
                        JsonNode emb = item.get("embedding");
                        float[] v = new float[emb.size()];
                        for (int i = 0; i < emb.size(); i++) {
                            v[i] = (float) emb.get(i).asDouble();
                        }
                        vectors.add(v);
                    }
                }
            }
            long tokensInput = 0, tokensTotal = 0;
            if (root.has("usage")) {
                JsonNode usage = root.get("usage");
                tokensInput = usage.has("prompt_tokens") ? usage.get("prompt_tokens").asLong() : 0;
                tokensTotal = usage.has("total_tokens") ? usage.get("total_tokens").asLong() : tokensInput;
            }
            return EmbeddingResponse.ok(vectors, model, tokensInput, tokensTotal);
        } catch (Exception e) {
            log.error("Failed to parse embedding response: {}", e.getMessage(), e);
            return EmbeddingResponse.fail("parse fail: " + e.getMessage());
        }
    }

    /**
     * 流式调用 — 使用 OkHttp SSE
     */
    private ChatResponse callStreaming(String baseUrl, String apiKey, String requestBodyJson) {
        String url = baseUrl + "/chat/completions";

        RequestBody body = RequestBody.create(requestBodyJson, MediaType.parse("application/json"));
        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .header(EcosTraceContext.HEADER_REQUEST_ID, EcosTraceContext.currentTraceId())
                .post(body)
                .build();

        StringBuilder contentBuilder = new StringBuilder();
        AtomicInteger tokensInput = new AtomicInteger(0);
        AtomicInteger tokensOutput = new AtomicInteger(0);
        AtomicBoolean usageSeen = new AtomicBoolean(false);
        AtomicReference<String> modelUsed = new AtomicReference<>("");
        AtomicBoolean success = new AtomicBoolean(false);
        AtomicReference<String> errorMsg = new AtomicReference<>("");
        CountDownLatch latch = new CountDownLatch(1);

        EventSource.Factory factory = EventSources.createFactory(okHttpClient);
        factory.newEventSource(request, new EventSourceListener() {
            @Override
            public void onEvent(EventSource eventSource, String id, String type, String data) {
                if ("[DONE]".equals(data)) {
                    return;
                }
                try {
                    JsonNode node = objectMapper.readTree(data);
                    if (node.has("choices") && node.get("choices").isArray() && node.get("choices").size() > 0) {
                        JsonNode choice = node.get("choices").get(0);
                        JsonNode delta = choice.get("delta");
                        if (delta != null && delta.has("content") && !delta.get("content").isNull()) {
                            contentBuilder.append(delta.get("content").asText());
                        }
                        // 记录 finish_reason
                        if (choice.has("finish_reason") && !choice.get("finish_reason").isNull()) {
                            String reason = choice.get("finish_reason").asText();
                            if (!"null".equals(reason)) {
                                success.set(true);
                            }
                        }
                    }
                    // 记录 usage 信息（部分 provider 在流式结束时携带）
                    if (node.has("usage") && !node.get("usage").isNull()) {
                        JsonNode usage = node.get("usage");
                        if (usage.has("prompt_tokens")) {
                            tokensInput.set(usage.get("prompt_tokens").asInt());
                        }
                        if (usage.has("completion_tokens")) {
                            tokensOutput.set(usage.get("completion_tokens").asInt());
                        }
                        usageSeen.set(true);
                    }
                    // 记录 model
                    if (node.has("model") && !node.get("model").isNull()) {
                        modelUsed.set(node.get("model").asText());
                    }
                } catch (Exception e) {
                    log.warn("Failed to parse SSE data: {}", data, e);
                }
            }

            @Override
            public void onFailure(EventSource eventSource, Throwable t, Response response) {
                String error = t != null ? t.getMessage() : "Unknown error";
                if (response != null) {
                    error = "HTTP " + response.code() + ": " + error;
                }
                errorMsg.set(error);
                success.set(false);
                latch.countDown();
            }

            @Override
            public void onOpen(EventSource eventSource, Response response) {
                // 连接已建立
            }

            @Override
            public void onClosed(EventSource eventSource) {
                success.compareAndSet(false, true);
                latch.countDown();
            }
        });

        try {
            boolean completed = latch.await(600, TimeUnit.SECONDS); // 最长等待 10 分钟
            if (!completed) {
                return ChatResponse.fail(AiErrorClass.LLM_TIMEOUT, "Streaming request timed out after 600s");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ChatResponse.fail(AiErrorClass.LLM_TIMEOUT, "Streaming interrupted");
        }

        if (success.get()) {
            String modelStr = modelUsed.get();
            if (modelStr.isEmpty()) modelStr = "unknown";
            ChatResponse ok = ChatResponse.ok(contentBuilder.toString(),
                    tokensInput.get(), tokensOutput.get(), modelStr);
            ok.setUsageReported(usageSeen.get());
            return ok;
        } else {
            return ChatResponse.fail(AiErrorClass.PARSE_FAILURE, errorMsg.get());
        }
    }

    /**
     * 解析非流式响应 JSON
     */
    private ChatResponse parseNonStreamingResponse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);

            if (root.has("error") && !root.get("error").isNull()) {
                JsonNode errorNode = root.get("error");
                String errorMsg = errorNode.has("message") ? errorNode.get("message").asText() : responseBody;
                return ChatResponse.fail(AiErrorClass.PARSE_FAILURE, errorMsg);
            }

            String content = "";
            if (root.has("choices") && root.get("choices").isArray() && root.get("choices").size() > 0) {
                JsonNode choice = root.get("choices").get(0);
                JsonNode message = choice.get("message");
                if (message != null && message.has("content") && !message.get("content").isNull()) {
                    content = message.get("content").asText();
                }
            }

            int tokensInput = 0;
            int tokensOutput = 0;
            boolean usageReported = root.has("usage") && !root.get("usage").isNull();
            if (usageReported) {
                JsonNode usage = root.get("usage");
                tokensInput = usage.has("prompt_tokens") ? usage.get("prompt_tokens").asInt() : 0;
                tokensOutput = usage.has("completion_tokens") ? usage.get("completion_tokens").asInt() : 0;
            }

            String model = root.has("model") && !root.get("model").isNull()
                    ? root.get("model").asText() : "unknown";

            ChatResponse ok = ChatResponse.ok(content, tokensInput, tokensOutput, model);
            ok.setUsageReported(usageReported);
            return ok;

        } catch (Exception e) {
            log.error("Failed to parse LLM response: {}", responseBody, e);
            return ChatResponse.fail(AiErrorClass.PARSE_FAILURE, "Failed to parse response: " + e.getMessage());
        }
    }
}
