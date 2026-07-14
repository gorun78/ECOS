package com.chinacreator.gzcm.runtime.hermes.gateway;

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
 * 重试机制: 指数退避
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
            return ChatResponse.fail("Failed to serialize request: " + e.getMessage());
        }

        // 从 ChatRequest 提取 apiKey
        String apiKey = request.getApiKey() != null ? request.getApiKey() : "";

        if (stream) {
            return callStreaming(baseUrl, apiKey, requestBodyJson);
        } else {
            return callNonStreaming(baseUrl, apiKey, requestBodyJson);
        }
    }

    @Override
    public ChatResponse callWithRetry(ChatRequest request, int maxRetries) {
        int attempt = 0;
        long baseDelay = 1000L; // 初始延迟 1 秒

        while (true) {
            ChatResponse response = call(request);
            if (response.isSuccess()) {
                return response;
            }

            attempt++;
            if (attempt > maxRetries) {
                log.warn("All {} retries exhausted for model [{}]. Last error: {}",
                        maxRetries, request.getModel(), response.getErrorMsg());
                return response;
            }

            // 指数退避: 1s, 2s, 4s, 8s, ...
            long delay = baseDelay * (1L << (attempt - 1));
            log.info("Retry #{}/{} for model [{}] after {}ms. Error: {}",
                    attempt, maxRetries, request.getModel(), delay, response.getErrorMsg());
            try {
                TimeUnit.MILLISECONDS.sleep(delay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return ChatResponse.fail("Retry interrupted: " + e.getMessage());
            }
        }
    }

    @Override
    public boolean isAvailable(LLMConfig config) {
        String baseUrl = resolveBaseUrl(config);
        String modelsUrl = baseUrl + "/models";

        Request request = new Request.Builder()
                .url(modelsUrl)
                .header("Authorization", "Bearer " + (config.getApiKey() != null ? config.getApiKey() : ""))
                .header("Accept", "application/json")
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
     * 构建 OpenAI 兼容的请求体 JSON
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
     * 非流式调用
     */
    private ChatResponse callNonStreaming(String baseUrl, String apiKey, String requestBodyJson) {
        String url = baseUrl + "/chat/completions";

        RequestBody body = RequestBody.create(requestBodyJson, MediaType.parse("application/json"));
        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .post(body)
                .build();

        long startTime = System.currentTimeMillis();

        try (Response response = okHttpClient.newCall(request).execute()) {
            long duration = System.currentTimeMillis() - startTime;

            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "";
                log.error("LLM API error: HTTP {} {} | Body: {} | Duration: {}ms",
                        response.code(), response.message(), errorBody, duration);
                return ChatResponse.fail(
                        String.format("HTTP %d: %s", response.code(),
                                errorBody.isEmpty() ? response.message() : errorBody));
            }

            String responseBody = response.body() != null ? response.body().string() : "";
            return parseNonStreamingResponse(responseBody);

        } catch (IOException e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("LLM API call failed after {}ms: {}", duration, e.getMessage());
            return ChatResponse.fail("IO error: " + e.getMessage());
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
                .post(body)
                .build();

        StringBuilder contentBuilder = new StringBuilder();
        AtomicInteger tokensInput = new AtomicInteger(0);
        AtomicInteger tokensOutput = new AtomicInteger(0);
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
                return ChatResponse.fail("Streaming request timed out after 600s");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ChatResponse.fail("Streaming interrupted: " + e.getMessage());
        }

        if (success.get()) {
            String modelStr = modelUsed.get();
            if (modelStr.isEmpty()) modelStr = "unknown";
            return ChatResponse.ok(contentBuilder.toString(),
                    tokensInput.get(), tokensOutput.get(), modelStr);
        } else {
            return ChatResponse.fail(errorMsg.get());
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
                return ChatResponse.fail(errorMsg);
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
            if (root.has("usage") && !root.get("usage").isNull()) {
                JsonNode usage = root.get("usage");
                tokensInput = usage.has("prompt_tokens") ? usage.get("prompt_tokens").asInt() : 0;
                tokensOutput = usage.has("completion_tokens") ? usage.get("completion_tokens").asInt() : 0;
            }

            String model = root.has("model") && !root.get("model").isNull()
                    ? root.get("model").asText() : "unknown";

            return ChatResponse.ok(content, tokensInput, tokensOutput, model);

        } catch (Exception e) {
            log.error("Failed to parse LLM response: {}", responseBody, e);
            return ChatResponse.fail("Failed to parse response: " + e.getMessage());
        }
    }
}
