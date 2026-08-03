package com.chinacreator.gzcm.engine.ai.service;

import com.chinacreator.gzcm.runtime.llm.gateway.ChatMessage;
import com.chinacreator.gzcm.runtime.llm.gateway.ChatRequest;
import com.chinacreator.gzcm.runtime.llm.gateway.ChatResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * DeepSeek LLM Provider — 通过 DeepSeek API（OpenAI 兼容）实现 LLMProvider 接口。
 *
 * <h3>配置项（application.yml）</h3>
 * <pre>
 * llm:
 *   deepseek:
 *     api-key: sk-xxx
 *     base-url: https://api.deepseek.com
 *     model: deepseek-chat
 * </pre>
 *
 * <h3>技术细节</h3>
 * <ul>
 *   <li>使用 JDK 11+ {@link java.net.http.HttpClient}，无额外 Maven 依赖</li>
 *   <li>Endpoint: {@code {base-url}/v1/chat/completions}</li>
 *   <li>支持原生 function-calling（tool_calls）</li>
 *   <li>若 api-key 为空，{@link #isAvailable()} 返回 false，自动跳过</li>
 * </ul>
 */
@Component
public class DeepSeekProvider implements LLMProvider {

    private static final Logger log = LoggerFactory.getLogger(DeepSeekProvider.class);

    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public DeepSeekProvider(
            @Value("${llm.deepseek.api-key:}") String apiKey,
            @Value("${llm.deepseek.base-url:https://api.deepseek.com}") String baseUrl,
            @Value("${llm.deepseek.model:deepseek-chat}") String model) {
        this.apiKey = (apiKey != null) ? apiKey.trim() : "";
        this.baseUrl = (baseUrl != null && !baseUrl.isBlank()) ? baseUrl : "https://api.deepseek.com";
        this.model = (model != null && !model.isBlank()) ? model : "deepseek-chat";
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        this.objectMapper = new ObjectMapper();

        if (this.apiKey.isEmpty()) {
            log.warn("[DeepSeekProvider] api-key is empty — provider will be unavailable");
        } else {
            log.info("[DeepSeekProvider] initialized api-key={}... base-url={} model={}",
                    maskKey(this.apiKey), this.baseUrl, this.model);
        }
    }

    // ─── LLMProvider contract ───────────────────────────────────────────

    @Override
    public String getName() {
        return "deepseek";
    }

    @Override
    public boolean supportsFunctionCalling() {
        return true;
    }

    @Override
    public int priority() {
        return 10;
    }

    @Override
    public boolean isAvailable() {
        return !apiKey.isEmpty();
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        if (!isAvailable()) {
            return ChatResponse.fail("DeepSeekProvider: api-key is empty");
        }

        String reqModel = request.getModel() != null && !request.getModel().isBlank()
                ? request.getModel() : this.model;
        Double temperature = request.getTemperature() != null ? request.getTemperature() : 0.7;
        Integer maxTokens = request.getMaxTokens() != null ? request.getMaxTokens() : 4096;
        Boolean stream = request.getStream() != null ? request.getStream() : false;

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", reqModel);
        body.put("temperature", temperature);
        body.put("max_tokens", maxTokens);
        body.put("stream", stream);

        // 转换消息 — 处理 tool_calls 序列化
        List<Map<String, Object>> openAiMessages = convertMessages(request.getMessages());
        body.put("messages", openAiMessages);

        try {
            String jsonBody = objectMapper.writeValueAsString(body);
            log.debug("[DeepSeekProvider] request body length={}", jsonBody.length());

            HttpRequest httpReq = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/v1/chat/completions"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .timeout(Duration.ofSeconds(120))
                    .build();

            HttpResponse<String> httpResp = httpClient.send(httpReq, HttpResponse.BodyHandlers.ofString());

            if (httpResp.statusCode() != 200) {
                String errBody = httpResp.body();
                log.error("[DeepSeekProvider] API returned {}: {}", httpResp.statusCode(),
                        errBody != null ? errBody.substring(0, Math.min(500, errBody.length())) : "(empty)");
                return ChatResponse.fail("DeepSeek API error " + httpResp.statusCode() + ": "
                        + (errBody != null ? errBody.substring(0, Math.min(200, errBody.length())) : ""));
            }

            return parseResponse(httpResp.body(), reqModel);

        } catch (java.net.http.HttpTimeoutException e) {
            log.error("[DeepSeekProvider] request timeout", e);
            return ChatResponse.fail("DeepSeek API timeout: " + e.getMessage());
        } catch (Exception e) {
            log.error("[DeepSeekProvider] request failed", e);
            return ChatResponse.fail("DeepSeek API error: " + e.getMessage());
        }
    }

    // ─── Message conversion ─────────────────────────────────────────────

    /**
     * 将 llm-gateway ChatMessage 转换为 OpenAI/DeepSeek 兼容的消息格式。
     * <p>
     * 特殊处理：若 assistant 消息的 content 是 JSON tool_calls 数组，
     * 则转换为原生 tool_calls 字段（而非 content 文本）。
     * </p>
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> convertMessages(List<ChatMessage> messages) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (messages == null) return result;

        for (ChatMessage cm : messages) {
            Map<String, Object> msg = new LinkedHashMap<>();
            msg.put("role", cm.getRole());

            String content = cm.getContent();
            String role = cm.getRole();

            if ("assistant".equals(role) && content != null && content.trim().startsWith("[")) {
                // 可能是序列化的 tool_calls JSON 数组
                try {
                    List<Map<String, Object>> parsed = objectMapper.readValue(content,
                            new TypeReference<List<Map<String, Object>>>() {});
                    // 检查是否符合 tool_calls 格式: [{id, type:"function", function:{name, arguments}}]
                    if (!parsed.isEmpty() && isToolCallsFormat(parsed)) {
                        msg.put("content", null);
                        msg.put("tool_calls", parsed);
                        result.add(msg);
                        continue;
                    }
                } catch (Exception e) {
                    // 不是有效 JSON → 当作普通文本
                    log.debug("[DeepSeekProvider] assistant content is not tool_calls JSON, treating as text");
                }
            } else if ("tool".equals(role)) {
                // 工具结果消息 — ChatMessage 无 tool_call_id，直接传 content
                msg.put("content", content != null ? content : "");
                result.add(msg);
                continue;
            }

            // 默认：普通文本消息
            msg.put("content", content != null ? content : "");
            result.add(msg);
        }

        return result;
    }

    /**
     * 检查解析后的 JSON 数组是否为 tool_calls 格式。
     */
    private boolean isToolCallsFormat(List<Map<String, Object>> list) {
        for (Map<String, Object> item : list) {
            if (item.containsKey("function") || "function".equals(item.get("type"))) {
                return true;
            }
        }
        return false;
    }

    // ─── Response parsing ───────────────────────────────────────────────

    /**
     * 解析 DeepSeek API 响应为 ChatResponse。
     * <p>
     * 若 message 含 tool_calls，序列化为 JSON 字符串存入 content（与 AgentLoopService.parseToolCalls() 兼容）。
     * </p>
     */
    @SuppressWarnings("unchecked")
    private ChatResponse parseResponse(String body, String resModel) {
        try {
            Map<String, Object> root = objectMapper.readValue(body,
                    new TypeReference<Map<String, Object>>() {});

            List<Map<String, Object>> choices = (List<Map<String, Object>>) root.get("choices");
            if (choices == null || choices.isEmpty()) {
                return ChatResponse.fail("DeepSeek response has no choices");
            }

            Map<String, Object> choice = choices.get(0);
            Map<String, Object> message = (Map<String, Object>) choice.get("message");
            if (message == null) {
                return ChatResponse.fail("DeepSeek response message is null");
            }

            String content = (String) message.get("content");
            List<Map<String, Object>> toolCalls = (List<Map<String, Object>>) message.get("tool_calls");

            // 若有 tool_calls，序列化为 JSON 字符串（与 AgentLoopService 解析兼容）
            String finalContent;
            if (toolCalls != null && !toolCalls.isEmpty()) {
                finalContent = objectMapper.writeValueAsString(toolCalls);
            } else {
                finalContent = (content != null) ? content : "";
            }

            // 提取 token 用量
            Map<String, Object> usage = (Map<String, Object>) root.get("usage");
            int tokensInput = 0;
            int tokensOutput = 0;
            if (usage != null) {
                if (usage.get("prompt_tokens") instanceof Number) {
                    tokensInput = ((Number) usage.get("prompt_tokens")).intValue();
                }
                if (usage.get("completion_tokens") instanceof Number) {
                    tokensOutput = ((Number) usage.get("completion_tokens")).intValue();
                }
            }

            log.debug("[DeepSeekProvider] response: {} chars, {} tool_calls, tokens in={} out={}",
                    finalContent.length(),
                    toolCalls != null ? toolCalls.size() : 0,
                    tokensInput, tokensOutput);

            return ChatResponse.ok(finalContent, tokensInput, tokensOutput, resModel);

        } catch (Exception e) {
            log.error("[DeepSeekProvider] failed to parse response: {}", e.getMessage());
            return ChatResponse.fail("Failed to parse DeepSeek response: " + e.getMessage());
        }
    }

    // ─── Helpers ────────────────────────────────────────────────────────

    private static String maskKey(String key) {
        if (key == null || key.length() <= 8) return "***";
        return key.substring(0, 4) + "..." + key.substring(key.length() - 4);
    }
}
