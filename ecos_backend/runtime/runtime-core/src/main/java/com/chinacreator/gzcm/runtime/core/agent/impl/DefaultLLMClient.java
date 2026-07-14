package com.chinacreator.gzcm.runtime.core.agent.impl;

import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import com.chinacreator.gzcm.runtime.core.agent.exception.AgentException;
import com.chinacreator.gzcm.runtime.core.agent.llm.ChatRequest;
import com.chinacreator.gzcm.runtime.core.agent.llm.ChatResponse;
import com.chinacreator.gzcm.runtime.core.agent.llm.LLMClient;
import com.chinacreator.gzcm.runtime.core.agent.llm.LLMConfig;

/**
 * LLM 客户端默认实现
 * 
 * 基于 Java 11+ HttpClient，兼容 OpenAI Chat Completions API 格式。
 * 支持 OpenAI、Azure OpenAI、vLLM、Ollama 等兼容该 API 的服务。
 *
 * @author CDRC Design Team
 */
public class DefaultLLMClient implements LLMClient {

    private final LLMConfig config;
    private final HttpClient httpClient;
    private final AtomicInteger totalCalls = new AtomicInteger(0);
    private final AtomicInteger totalTokens = new AtomicInteger(0);

    public DefaultLLMClient(LLMConfig config) {
        this.config = config;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(config.getTimeoutSeconds() != null ? config.getTimeoutSeconds() : 120))
                .build();
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        try {
            String url = buildUrl();
            String body = buildRequestBody(request);
            HttpRequest httpRequest = buildHttpRequest(url, body);

            HttpResponse<String> response = httpClient.send(httpRequest,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (response.statusCode() != 200) {
                throw new AgentException(null, AgentException.ERR_LLM_CALL_FAILED,
                        "LLM 调用失败: HTTP " + response.statusCode() + " - " + response.body());
            }

            totalCalls.incrementAndGet();
            return parseResponse(response.body());

        } catch (AgentException e) {
            throw e;
        } catch (Exception e) {
            throw new AgentException(null, AgentException.ERR_LLM_CALL_FAILED,
                    "LLM 调用失败: " + e.getMessage(), e);
        }
    }

    @Override
    public ChatResponse chatStream(ChatRequest request, StreamCallback callback) {
        // 简化实现：先用非流式调用，然后逐个 token 回调
        ChatResponse response = chat(request);
        if (callback != null && response.getContent() != null) {
            // 简单按字符拆分模拟流式输出
            for (char c : response.getContent().toCharArray()) {
                callback.onToken(String.valueOf(c));
            }
        }
        return response;
    }

    @Override
    public List<String> getAvailableModels() {
        return Collections.singletonList(config.getModel());
    }

    @Override
    public Map<String, Object> getUsageStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalCalls", totalCalls.get());
        stats.put("totalTokens", totalTokens.get());
        stats.put("model", config.getModel());
        stats.put("provider", config.getProvider().name());
        return stats;
    }

    // ── Private helpers ──────────────────────────

    private String buildUrl() {
        switch (config.getProvider()) {
            case AZURE_OPENAI:
                return String.format("%s/openai/deployments/%s/chat/completions?api-version=%s",
                        config.getBaseUrl(), config.getDeploymentName(),
                        config.getApiVersion() != null ? config.getApiVersion() : "2024-02-15-preview");
            case LOCAL_VLLM:
            case LOCAL_OLLAMA:
                return config.getBaseUrl() + "/v1/chat/completions";
            case OPENAI:
            default:
                return (config.getBaseUrl() != null ? config.getBaseUrl() : "https://api.openai.com")
                        + "/v1/chat/completions";
        }
    }

    private HttpRequest buildHttpRequest(String url, String body) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(config.getTimeoutSeconds() != null ? config.getTimeoutSeconds() : 120));

        // Authorization header
        if (config.getProvider() == LLMConfig.Provider.AZURE_OPENAI) {
            builder.header("api-key", config.getApiKey());
        } else {
            builder.header("Authorization", "Bearer " + config.getApiKey());
        }

        // OpenAI organization
        if (config.getOrganizationId() != null) {
            builder.header("OpenAI-Organization", config.getOrganizationId());
        }

        return builder.POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build();
    }

    private String buildRequestBody(ChatRequest request) {
        StringBuilder json = new StringBuilder();
        json.append("{");

        json.append("\"model\":\"").append(escape(request.getModel() != null ? request.getModel() : config.getModel())).append("\"");

        // Messages
        json.append(",\"messages\":[");
        if (request.getMessages() != null) {
            for (int i = 0; i < request.getMessages().size(); i++) {
                ChatRequest.ChatMessage msg = request.getMessages().get(i);
                if (i > 0) json.append(",");
                json.append("{\"role\":\"").append(escape(msg.getRole())).append("\"");
                if (msg.getContent() != null) {
                    json.append(",\"content\":\"").append(escape(msg.getContent())).append("\"");
                }
                if (msg.getName() != null) {
                    json.append(",\"name\":\"").append(escape(msg.getName())).append("\"");
                }
                if (msg.getToolCallId() != null) {
                    json.append(",\"tool_call_id\":\"").append(escape(msg.getToolCallId())).append("\"");
                }
                if (msg.getToolCalls() != null && !msg.getToolCalls().isEmpty()) {
                    json.append(",\"tool_calls\":[");
                    for (int j = 0; j < msg.getToolCalls().size(); j++) {
                        ChatRequest.ToolCallRequest tc = msg.getToolCalls().get(j);
                        if (j > 0) json.append(",");
                        json.append("{\"id\":\"").append(escape(tc.getId())).append("\"")
                            .append(",\"type\":\"function\"")
                            .append(",\"function\":{\"name\":\"").append(escape(tc.getFunction().getName())).append("\"")
                            .append(",\"arguments\":").append(tc.getFunction().getArguments())
                            .append("}}");
                    }
                    json.append("]");
                }
                json.append("}");
            }
        }
        json.append("]");

        // Tools (function calling)
        if (request.getTools() != null && !request.getTools().isEmpty()) {
            json.append(",\"tools\":").append(toJsonString(request.getTools()));
        }
        if (request.getToolChoice() != null) {
            json.append(",\"tool_choice\":\"").append(escape(request.getToolChoice())).append("\"");
        }

        // Temperature & max_tokens
        if (request.getTemperature() != null) {
            json.append(",\"temperature\":").append(request.getTemperature());
        } else if (config.getTemperature() != null) {
            json.append(",\"temperature\":").append(config.getTemperature());
        }
        if (request.getMaxTokens() != null) {
            json.append(",\"max_tokens\":").append(request.getMaxTokens());
        } else if (config.getMaxTokens() != null) {
            json.append(",\"max_tokens\":").append(config.getMaxTokens());
        }

        json.append("}");
        return json.toString();
    }

    private ChatResponse parseResponse(String body) {
        // 简单的 JSON 解析（生产环境应用 Jackson）
        ChatResponse response = new ChatResponse();

        // 提取 content
        String content = extractJsonString(body, "\"content\":\"");
        response.setContent(content != null ? content : "");

        // 提取 finish_reason
        String finishReason = extractJsonString(body, "\"finish_reason\":\"");
        response.setFinishReason(finishReason != null ? finishReason : "stop");

        // 提取 usage
        int promptTokens = extractJsonInt(body, "\"prompt_tokens\":");
        int completionTokens = extractJsonInt(body, "\"completion_tokens\":");
        int total = extractJsonInt(body, "\"total_tokens\":");
        if (total > 0) {
            response.setUsage(new ChatResponse.TokenUsage(promptTokens, completionTokens, total));
            totalTokens.addAndGet(total);
        }

        // 提取 tool_calls（简化实现）
        if (body.contains("\"tool_calls\"")) {
            List<ChatRequest.ToolCallRequest> toolCalls = new ArrayList<>();
            // 简化解析：提取第一个 tool call
            String tcId = extractJsonString(body, "\"id\":\"");
            String tcName = extractJsonString(body, "\"name\":\"");
            String tcArgs = extractJsonRaw(body, "\"arguments\":");
            if (tcId != null && tcName != null) {
                toolCalls.add(new ChatRequest.ToolCallRequest(tcId, tcName,
                        tcArgs != null ? tcArgs : "{}"));
            }
            response.setToolCalls(toolCalls);
        }

        return response;
    }

    // ── Minimal JSON helpers (avoid Jackson dependency in runtime-api) ──

    private String extractJsonString(String json, String key) {
        int idx = json.indexOf(key);
        if (idx < 0) return null;
        idx += key.length();
        int end = json.indexOf("\"", idx);
        if (end < 0) return null;
        return unescape(json.substring(idx, end));
    }

    private int extractJsonInt(String json, String key) {
        int idx = json.indexOf(key);
        if (idx < 0) return 0;
        idx += key.length();
        int end = idx;
        while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '-')) {
            end++;
        }
        try {
            return Integer.parseInt(json.substring(idx, end));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String extractJsonRaw(String json, String key) {
        int idx = json.indexOf(key);
        if (idx < 0) return null;
        idx += key.length();
        // Simple bracket matching for raw JSON
        if (idx < json.length() && json.charAt(idx) == '{') {
            int depth = 0;
            int end = idx;
            while (end < json.length()) {
                char c = json.charAt(end);
                if (c == '{') depth++;
                else if (c == '}') depth--;
                if (depth == 0) return json.substring(idx, end + 1);
                end++;
            }
        }
        // fallback: extract quoted string
        if (idx < json.length() && json.charAt(idx) == '"') {
            return extractJsonString(json, key.substring(0, key.length() - 1));
        }
        return null;
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private String unescape(String s) {
        if (s == null) return null;
        return s.replace("\\\"", "\"")
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t")
                .replace("\\\\", "\\");
    }

    // 简单 JSON 序列化（用于 tools）
    private String toJsonString(List<Map<String, Object>> tools) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < tools.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(mapToJson(tools.get(i)));
        }
        return sb.append("]").toString();
    }

    @SuppressWarnings("unchecked")
    private String mapToJson(Map<String, Object> map) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> e : map.entrySet()) {
            if (!first) sb.append(",");
            first = false;
            sb.append("\"").append(escape(e.getKey())).append("\":");
            Object v = e.getValue();
            if (v instanceof String) {
                sb.append("\"").append(escape((String) v)).append("\"");
            } else if (v instanceof Number) {
                sb.append(v);
            } else if (v instanceof Boolean) {
                sb.append(v);
            } else if (v instanceof Map) {
                sb.append(mapToJson((Map<String, Object>) v));
            } else if (v instanceof List) {
                sb.append("[");
                List<?> list = (List<?>) v;
                for (int i = 0; i < list.size(); i++) {
                    if (i > 0) sb.append(",");
                    Object item = list.get(i);
                    if (item instanceof Map) {
                        sb.append(mapToJson((Map<String, Object>) item));
                    } else if (item instanceof String) {
                        sb.append("\"").append(escape((String) item)).append("\"");
                    } else {
                        sb.append(item);
                    }
                }
                sb.append("]");
            } else if (v == null) {
                sb.append("null");
            }
        }
        return sb.append("}").toString();
    }
}
