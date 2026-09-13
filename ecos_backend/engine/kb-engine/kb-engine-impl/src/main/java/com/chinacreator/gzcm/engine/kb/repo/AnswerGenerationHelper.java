package com.chinacreator.gzcm.engine.kb.repo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * RAG answer LLM 生成 helper — PMO-50 T3。
 *
 * <p>通过 gateway REST {@code POST {gatewayBase}/api/v1/llm/chat} 让 LLM
 * 基于检索到的 top sources 生成最终 answer，替代原 top source 标题+snippet 拼接。
 *
 * <p>架构铁律 §2.5#2：kb-engine 不直接 new 外部 LLM provider，
 * 一律通过 gateway 的 llm-gateway 统一收敛（避免各自 new Driver / 各自调 Provider API）。
 *
 * <p>降级策略：失败/超时/LLM 不可用 → 返回 {@code null}，
 * 上层 {@code KnowledgeRetrievalServiceImpl#ragQuery} 改用拼接 answer，
 * 并把 {@code answerGenerated} 标记为 false（保证 RAG API 可用）。
 *
 * <p>请求体格式（OpenAI 兼容 chat）：
 * <pre>
 * {
 *   "model": "deepseek/deepseek-chat",
 *   "messages": [{"role":"system","content":"..."},{"role":"user","content":"..."}]
 * }
 * </pre>
 * 响应体（ApiResponse 包裹）：
 * <pre>
 * { "code":0, "data": { "content":"...","success":true, "model":"...", "tokensInput":.., "tokensOutput":.. } }
 * </pre>
 *
 * <p>配置 key：{@code ecos.rag.llm-gateway-base}（默认 {@code http://localhost:8080}），
 * 复用 {@link QueryEmbeddingHelper} 同一 base，避免重复配置。
 */
@Component
public class AnswerGenerationHelper {

    private static final Logger log = LoggerFactory.getLogger(AnswerGenerationHelper.class);

    /** 兜底 LLM 模型（与 LLMGatewayProperties.Engine.defaultModel 对齐） */
    private static final String DEFAULT_MODEL = "deepseek/deepseek-chat";

    /** 系统提示 — 严格限定 RAG 答案边界，禁止幻觉 */
    private static final String SYSTEM_PROMPT =
            "You are a RAG assistant. Answer the user question strictly based on the provided context. "
            + "Do not invent facts outside the context. If the context is insufficient, say so briefly. "
            + "Be concise (<=120 words).";

    private final RestTemplate restTemplate;
    private final String llmGatewayBase;
    private final String model;

    public AnswerGenerationHelper(RestTemplate restTemplate,
                                  @Value("${ecos.rag.llm-gateway-base:http://localhost:8080}") String llmGatewayBase,
                                  @Value("${ecos.rag.llm-chat-model:" + DEFAULT_MODEL + "}") String model) {
        this.restTemplate = restTemplate;
        this.llmGatewayBase = llmGatewayBase == null ? null : llmGatewayBase.trim();
        this.model = (model == null || model.isBlank()) ? DEFAULT_MODEL : model;
        log.info("AnswerGenerationHelper init: base={}, model={}", llmGatewayBase, this.model);
    }

    /**
     * 调 LLM 生成 answer。
     *
     * @param question 用户原始问题
     * @param sources  检索结果（含 content 字段），空 list 直接返回 null（无依据不生成）
     * @return LLM 生成的 answer 文本；失败/不可用时返回 null（上层降级到拼接 answer）
     */
    public String generate(String question, List<Map<String, Object>> sources) {
        if (question == null || question.isBlank() || sources == null || sources.isEmpty()) {
            log.debug("AnswerGenerationHelper: skip — no question or no sources");
            return null;
        }
        if (llmGatewayBase == null || llmGatewayBase.isBlank()) {
            log.debug("AnswerGenerationHelper: llm-gateway-base blank — fallback concatenated answer");
            return null;
        }
        try {
            String url = buildUrl();
            Map<String, Object> body = buildRequest(question, sources);
            @SuppressWarnings("unchecked")
            Map<String, Object> resp = restTemplate.postForObject(url, body, Map.class);
            return extractContent(resp);
        } catch (Exception e) {
            log.warn("AnswerGenerationHelper: LLM call failed (fallback concatenated answer): {}", e.getMessage());
            return null;
        }
    }

    /**
     * 构造 {@code POST /api/v1/llm/chat} 的 OpenAI 兼容请求体。
     * 把 top sources 拼到 system 后续 context，user 段放原始 query。
     */
    private Map<String, Object> buildRequest(String question, List<Map<String, Object>> sources) {
        StringBuilder context = new StringBuilder("Sources:\n");
        int n = 1;
        for (Map<String, Object> s : sources) {
            Object c = s.get("content");
            String content = (c == null) ? "" : String.valueOf(c).trim();
            if (content.isEmpty()) continue;
            context.append("[").append(n++).append("] ").append(content).append("\n");
        }
        context.append("\nQuestion: ").append(question).append('\n');
        context.append("Answer:");

        List<Map<String, String>> messages = new ArrayList<>(2);
        messages.add(new LinkedHashMap<>(Map.of("role", "system", "content", SYSTEM_PROMPT)));
        messages.add(new LinkedHashMap<>(Map.of("role", "user", "content", context.toString())));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        body.put("temperature", 0.3);
        body.put("max_tokens", 1024);
        return body;
    }

    /**
     * 从 ApiResponse 包裹的响应里抽 {@code data.content}。
     * 兼容 {code,success,message,data:{content..}} 与裸 {content...} 两种返回。
     */
    @SuppressWarnings("unchecked")
    private String extractContent(Map<String, Object> resp) {
        if (resp == null) return null;

        Object dataObj = resp.get("data");
        if (dataObj instanceof Map<?, ?> dataMap) {
            Object content = ((Map<String, Object>) dataMap).get("content");
            if (content instanceof String s && !s.isBlank()) {
                log.debug("AnswerGenerationHelper: LLM answer generated, len={}", s.length());
                return s;
            }
            Object success = ((Map<String, Object>) dataMap).get("success");
            if (success instanceof Boolean b && !b) {
                Object errMsg = ((Map<String, Object>) dataMap).get("errorMsg");
                log.warn("AnswerGenerationHelper: LLM reported failure: {}", errMsg);
                return null;
            }
        }
        // 裸 content 兜底（防御：controller 给分场景直接返 ChatResponse）
        Object content = resp.get("content");
        if (content instanceof String s && !s.isBlank()) {
            return s;
        }
        log.debug("AnswerGenerationHelper: no content in response — fallback");
        return null;
    }

    private String buildUrl() {
        String b = llmGatewayBase;
        return b.endsWith("/") ? b + "api/v1/llm/chat" : b + "/api/v1/llm/chat";
    }
}
