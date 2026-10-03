package com.chinacreator.gzcm.engine.kb.repo;

import com.chinacreator.gzcm.runtime.llm.gateway.ChatMessage;
import com.chinacreator.gzcm.runtime.llm.gateway.ChatRequest;
import com.chinacreator.gzcm.runtime.llm.gateway.ChatResponse;
import com.chinacreator.gzcm.runtime.llm.gateway.LLMGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * RAG answer LLM 生成 helper — PMO-50 T3。
 *
 * <p>F04-13 / K-34 / K-36 改造：不再自写 RestTemplate 直连 llm-gateway 的 chat 端点，
 * 一律改经 {@link LLMGateway} Java 接口调用，底座统一承担超时/重试/审计/配额。
 *
 * <p>架构铁律 §2.5#2：kb-engine 不直接 new 外部 LLM provider，不吃透 HTTP 协议。
 *
 * <p>降级策略：失败/超时/LLM 不可用 → 返回 {@code null}，
 * 上层 {@code KnowledgeRetrievalServiceImpl#ragQuery} 改用拼接 answer，
 * 并把 {@code answerGenerated} 标记为 false（保证 RAG API 可用）。
 *
 * <p>SYSTEM_PROMPT 严格限定 RAG 答案边界，禁止幻觉。
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

    /** 一致 callerId（llm-gateway 侧 ABAC / 密钥判定 / 审计主体） */
    private static final String CALLER_ID = "kb-engine:rag-answer";

    private final LLMGateway llmGateway;
    private final String model;

    public AnswerGenerationHelper(LLMGateway llmGateway,
                                  @org.springframework.beans.factory.annotation.Value(
                                          "${ecos.rag.llm-chat-model:" + DEFAULT_MODEL + "}") String model) {
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
            ChatRequest req = new ChatRequest();
            req.setModel(model);
            req.setMessages(buildMessages(question, sources));
            req.setTemperature(0.3);
            req.setMaxTokens(1024);
            req.setStream(Boolean.FALSE);
            req.setCallerId(CALLER_ID);
            ChatResponse resp = llmGateway.call(req);
            if (resp == null || !resp.isSuccess()) {
                String err = resp == null ? "null response" : resp.getErrorMsg();
                log.warn("AnswerGenerationHelper: llm-gateway 返回失败 err={}", err);
                return null;
            }
            String content = resp.getContent();
            if (content == null || content.isBlank()) {
                log.debug("AnswerGenerationHelper: llm 空回复 — fallback");
                return null;
            }
            log.debug("AnswerGenerationHelper: LLM answer generated, len={}", content.length());
            return content;
        } catch (Exception e) {
            log.warn("AnswerGenerationHelper: LLM 调用失败（fallback 拼接 answer）: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 构造 messages（system 段 = 提示词；user 段 = top sources 拼装 + 原始 query）。
     */
    private List<ChatMessage> buildMessages(String question, List<Map<String, Object>> sources) {
        StringBuilder context = new StringBuilder("Sources:\n");
        int n = 1;
        for (Map<String, Object> s : sources) {
            Object c = s.get("content");
            String content = (c == null) ? "" : String.valueOf(c).trim();
            if (content.isEmpty()) continue;
            context.append("[").append(n++).append("] ").append(content).append('\n');
        }
        context.append("\nQuestion: ").append(question).append('\n');
        context.append("Answer:");

        return List.of(new ChatMessage("system", SYSTEM_PROMPT), new ChatMessage("user", context.toString()));
    }
}
