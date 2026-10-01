package com.chinacreator.gzcm.engine.kb.repo;

import com.chinacreator.gzcm.runtime.llm.gateway.ChatMessage;
import com.chinacreator.gzcm.runtime.llm.gateway.ChatRequest;
import com.chinacreator.gzcm.runtime.llm.gateway.ChatResponse;
import com.chinacreator.gzcm.runtime.llm.gateway.LLMGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * RAG answer LLM 生成 helper — PMO-50 T3。
 *
 * <p>F04-13 收敛：不再经 gateway REST {@code POST {gatewayBase}/api/v1/llm/chat} 直连，
 * 统一经 {@link LLMGateway#call(ChatRequest)}（横切底座，铁律 §2.5-2），
 * baseUrl 由 llm-gateway 内部按 provider 映射统一解析，调用方不再持有 base URL。
 *
 * <p>降级策略：失败/LLM 不可用 → 返回 {@code null}，
 * 上层 {@code KnowledgeRetrievalServiceImpl#ragQuery} 改用拼接 answer，
 * 并把 {@code answerGenerated} 标记为 false（保证 RAG API 可用）。
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

    private final LLMGateway llmGateway;
    private final String model;

    public AnswerGenerationHelper(
            LLMGateway llmGateway,
            @Value("${ecos.rag.llm-chat-model:" + DEFAULT_MODEL + "}") String model) {
        this.llmGateway = llmGateway;
        this.model = (model == null || model.isBlank()) ? DEFAULT_MODEL : model;
        log.info("AnswerGenerationHelper init: model={}", this.model);
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
        try {
            ChatRequest request = buildRequest(question, sources);
            ChatResponse resp = llmGateway.call(request);
            if (resp == null || !resp.isSuccess()) {
                log.warn("AnswerGenerationHelper: LLM reported failure (fallback concatenated answer): {}",
                        resp == null ? "null" : resp.getErrorMsg());
                return null;
            }
            String content = resp.getContent();
            if (content == null || content.isBlank()) {
                log.debug("AnswerGenerationHelper: no content in response — fallback");
                return null;
            }
            log.debug("AnswerGenerationHelper: LLM answer generated, len={}", content.length());
            return content;
        } catch (Exception e) {
            log.warn("AnswerGenerationHelper: LLM call failed (fallback concatenated answer): {}", e.getMessage());
            return null;
        }
    }

    /**
     * 构造 OpenAI 兼容 chat {@link ChatRequest}。
     * 把 top sources 拼到 system 后的 context，user 段放原始 query。
     */
    private ChatRequest buildRequest(String question, List<Map<String, Object>> sources) {
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

        List<ChatMessage> messages = new ArrayList<>(2);
        messages.add(new ChatMessage("system", SYSTEM_PROMPT));
        messages.add(new ChatMessage("user", context.toString()));

        ChatRequest req = new ChatRequest();
        req.setModel(model);
        req.setMessages(messages);
        req.setTemperature(0.3);
        req.setMaxTokens(1024);
        req.setCallerId("kb-engine:rag");
        return req;
    }
}
