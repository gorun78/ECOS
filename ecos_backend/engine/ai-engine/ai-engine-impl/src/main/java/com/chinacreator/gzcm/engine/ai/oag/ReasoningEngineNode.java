package com.chinacreator.gzcm.engine.ai.oag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Node 6: 推理引擎 — 基于检索到的知识进行 LLM 推理。
 *
 * <p>职责：</p>
 * <ul>
 *   <li>构建 prompt（意图 + 知识 + RLS/CLS 约束）</li>
 *   <li>调用 LLM 进行推理</li>
 *   <li>记录推理元数据（tokens, latency 等）</li>
 * </ul>
 *
 * <p>依赖：LLMGatewayService (llm-gateway)</p>
 */
@Component
public class ReasoningEngineNode implements OagNode {

    private static final Logger log = LoggerFactory.getLogger(ReasoningEngineNode.class);

    @Override
    public OagPipelineContext execute(OagPipelineContext ctx) {
        ctx.setCurrentNode("ReasoningEngine");

        String query = ctx.getRewrittenQuery() != null ? ctx.getRewrittenQuery() : ctx.getUserQuery();
        String intent = ctx.getIntent();
        Map<String, Object> knowledge = ctx.getKnowledgeResult();
        Map<String, Object> context = ctx.getContext();

        // 构建 system prompt
        String systemPrompt = buildSystemPrompt(intent, knowledge, ctx.getClsColumns());

        long start = System.currentTimeMillis();

        // 模拟 LLM 推理（生产环境应注入 LLMGatewayService 调用实际 LLM）
        String llmResponse = simulateLlmResponse(query, intent, knowledge);
        long latency = System.currentTimeMillis() - start;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("systemPrompt", systemPrompt);
        result.put("response", llmResponse);
        result.put("model", context != null ? context.getOrDefault("model", "deepseek-chat") : "deepseek-chat");
        result.put("latencyMs", latency);
        result.put("tokensUsed", estimateTokens(query) + estimateTokens(llmResponse));

        ctx.setReasoningResult(result);
        ctx.getMetadata().put("reasoningModel", result.get("model"));
        ctx.getMetadata().put("reasoningLatencyMs", latency);
        ctx.getMetadata().put("reasoningTokens", result.get("tokensUsed"));

        log.info("[OAG:{}] 推理完成 model={} latency={}ms tokens={}",
                ctx.getTraceId(), result.get("model"), latency, result.get("tokensUsed"));

        return ctx;
    }

    /**
     * 构建 system prompt。
     */
    private String buildSystemPrompt(String intent, Map<String, Object> knowledge, Map<String, Object> clsColumns) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是 ECOS 企业认知操作系统的智能助手。\n\n");

        sb.append("## 当前意图: ").append(intent != null ? intent : "UNKNOWN").append("\n\n");

        if (knowledge != null && knowledge.containsKey("knowledgeChunks")) {
            sb.append("## 相关知识:\n");
            @SuppressWarnings("unchecked")
            var chunks = (java.util.List<Map<String, Object>>) knowledge.get("knowledgeChunks");
            for (Map<String, Object> chunk : chunks) {
                sb.append("- ").append(chunk.get("content")).append("\n");
            }
            sb.append("\n");
        }

        sb.append("## 安全约束:\n");
        sb.append("- 仅返回用户有权访问的数据\n");
        sb.append("- 不输出敏感信息\n");
        if (clsColumns != null && !clsColumns.isEmpty()) {
            sb.append("- 返回列已由 CLS 策略过滤\n");
        }
        sb.append("\n请根据以上上下文回答用户问题。");

        return sb.toString();
    }

    /**
     * 模拟 LLM 响应（生产环境替换为实际 LLM 调用）。
     */
    private String simulateLlmResponse(String query, String intent, Map<String, Object> knowledge) {
        return String.format(
                "根据您的查询「%s」(意图: %s)，系统已完成分析。\n\n" +
                "ECOS 平台基于 DIKW 架构提供数据治理、知识图谱和大模型Agent三大核心能力。" +
                "当前检索到 %d 条相关知识片段，已按安全策略过滤。",
                query, intent != null ? intent : "UNKNOWN",
                knowledge != null && knowledge.containsKey("knowledgeChunks")
                        ? ((java.util.List<?>) knowledge.get("knowledgeChunks")).size() : 0
        );
    }

    private int estimateTokens(String text) {
        if (text == null || text.isEmpty()) return 0;
        return text.length() / 2; // 简化估算：2字符≈1token
    }
}
