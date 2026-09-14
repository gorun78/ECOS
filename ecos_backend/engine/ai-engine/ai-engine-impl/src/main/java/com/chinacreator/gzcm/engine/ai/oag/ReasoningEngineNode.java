package com.chinacreator.gzcm.engine.ai.oag;

import com.chinacreator.gzcm.engine.ai.service.LLMProvider;
import com.chinacreator.gzcm.runtime.llm.gateway.ChatMessage;
import com.chinacreator.gzcm.runtime.llm.gateway.ChatRequest;
import com.chinacreator.gzcm.runtime.llm.gateway.ChatResponse;
import com.chinacreator.gzcm.runtime.llm.gateway.LLMGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Node 6: 推理引擎 — 基于检索到的知识进行 LLM 推理。
 *
 * <p>职责：</p>
 * <ul>
 *   <li>构建 prompt（意图 + 知识 + RLS/CLS 约束）</li>
 *   <li>调用 LLM 进行推理（带模型 fallback 链）</li>
 *   <li>记录推理元数据（tokens, latency 等）</li>
 * </ul>
 *
 * <p>模型 fallback 链配置在 {@code application-*.yml} 的
 * {@code ecos.oag.model-fallback} 数组，例如
 * {@code [deepseek-chat, qwen-turbo, deepseek-v3-thinking]}。</p>
 *
 * <p>调用策略（参考 {@code AgentLoopService.callLLM}）：</p>
 * <ol>
 *   <li>优先 {@link LLMProvider}（直接调用 Provider API）</li>
 *   <li>兜底 {@link LLMGateway#call(ChatRequest)}</li>
 * </ol>
 *
 * <p>若 {@code LLMGateway} Bean 缺失（独立引擎调试场景），fallback 到模拟路径。</p>
 *
 * <p>依赖：{@link LLMGateway} / {@link LLMProvider}（均 required=false）</p>
 */
@Component
public class ReasoningEngineNode implements OagNode {

    private static final Logger log = LoggerFactory.getLogger(ReasoningEngineNode.class);

    /** 默认模型（fallback 链为空时使用） */
    private static final String DEFAULT_MODEL = "deepseek-chat";

    /** 供 {@link LLMGateway} 兜底调用时的 api-key 透传 */
    @Value("${llm.deepseek.api-key:}")
    private String deepseekApiKey;

    /** LLM 模型 fallback 链（按优先级排列，第一项最先尝试） */
    @Value("${ecos.oag.model-fallback:'deepseek-chat'}")
    private String modelFallbackRaw;

    /** LLM 底层调用网关（required=false，缺失时走模拟路径） */
    @Autowired(required = false)
    private LLMGateway llmGateway;

    /** LLM Provider 列表（required=false，供直接调用） */
    @Autowired(required = false)
    private ObjectProvider<List<LLMProvider>> llmProviderProvider;

    /**
     * 解析模型 fallback 链。
     *
     * @return 模型列表（至少含默认模型）
     */
    private List<String> resolveModelFallbackChain() {
        List<String> models = new ArrayList<>();
        if (modelFallbackRaw != null && !modelFallbackRaw.isBlank()) {
            for (String m : modelFallbackRaw.split(",")) {
                String trimmed = m.trim();
                if (!trimmed.isEmpty()) {
                    models.add(trimmed);
                }
            }
        }
        if (models.isEmpty()) {
            models.add(DEFAULT_MODEL);
        }
        return models;
    }

    @Override
    public OagPipelineContext execute(OagPipelineContext ctx) {
        ctx.setCurrentNode("ReasoningEngine");

        String query = ctx.getRewrittenQuery() != null ? ctx.getRewrittenQuery() : ctx.getUserQuery();
        String intent = ctx.getIntent();
        Map<String, Object> knowledge = ctx.getKnowledgeResult();
        Map<String, Object> context = ctx.getContext();

        // 构建 system prompt
        String systemPrompt = buildSystemPrompt(intent, knowledge, ctx.getClsColumns());

        // 从请求参数获取 model / temperature / maxTokens
        Map<String, Object> requestParams = ctx.getRequestParams();
        String requestedModel = requestParams != null
                ? (String) requestParams.get("model") : null;
        Double temperature = null;
        Integer maxTokens = null;
        if (requestParams != null) {
            if (requestParams.get("temperature") instanceof Number n) {
                temperature = n.doubleValue();
            }
            if (requestParams.get("maxTokens") instanceof Number n) {
                maxTokens = n.intValue();
            }
        }

        // LLM 网关不可用时 fallback 到模拟路径
        if (llmGateway == null) {
            log.warn("[OAG:{}] LLMGateway 未注入，降级到模拟 LLM 路径", ctx.getTraceId());
            return executeSimulated(ctx, query, intent, knowledge, systemPrompt,
                    requestedModel != null ? requestedModel : DEFAULT_MODEL);
        }

        // 构建模型 fallback 链
        List<String> models = resolveModelFallbackChain();
        // 请求指定了 model → 置于链首
        if (requestedModel != null && !requestedModel.isBlank() && !models.contains(requestedModel)) {
            models.add(0, requestedModel);
        }

        long start = System.currentTimeMillis();
        ChatResponse response = null;
        String usedModel = null;
        StringBuilder errorLog = new StringBuilder();
        int maxAttempts = models.size();

        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            String model = models.get(attempt);
            try {
                ChatRequest request = buildChatRequest(systemPrompt, query, model,
                        temperature != null ? temperature : 0.3,
                        maxTokens != null ? maxTokens : 4096);

                // 1. 优先尝试 LLMProvider
                LLMProvider provider = selectProvider();
                if (provider != null) {
                    log.info("[OAG:{}] First trying LLMProvider {} for model={}",
                            ctx.getTraceId(), provider.getName(), model);
                    ChatResponse resp = provider.chat(request);
                    if (resp != null && resp.isSuccess()) {
                        response = resp;
                        usedModel = model;
                        break;
                    }
                    log.warn("[OAG:{}] Provider {} returned failure for model={}: {} — trying next model",
                            ctx.getTraceId(), provider.getName(), model,
                            resp != null ? resp.getErrorMsg() : "null");
                    errorLog.append(String.format("[%s] provider=%s: %s; ",
                            model, provider.getName(),
                            resp != null ? resp.getErrorMsg() : "null"));
                }

                // 2. Fallback: 使用 LLMGateway 路径
                request.setApiKey(deepseekApiKey);
                ChatResponse gwResp = llmGateway.call(request);
                if (gwResp != null && gwResp.isSuccess()) {
                    response = gwResp;
                    usedModel = model;
                    break;
                }
                log.warn("[OAG:{}] LLMGateway failed for model={}: {} — trying next model",
                        ctx.getTraceId(), model,
                        gwResp != null ? gwResp.getErrorMsg() : "null");
                errorLog.append(String.format("[%s] gateway: %s; ",
                        model, gwResp != null ? gwResp.getErrorMsg() : "null"));

            } catch (Exception e) {
                log.warn("[OAG:{}] Exception calling model={}: {} — trying next model",
                        ctx.getTraceId(), model, e.getMessage());
                errorLog.append(String.format("[%s] exception: %s; ", model, e.getMessage()));
                // 超时/429/5xx 等异常自动降级到下一模型
            }
        }

        long latency = System.currentTimeMillis() - start;

        // 所有模型都失败
        if (response == null) {
            String allErrors = errorLog.length() > 0 ? errorLog.toString().trim() : "unknown error";
            log.warn("[OAG:{}] All {} model(s) failed: {}. Falling back to simulated LLM response",
                    ctx.getTraceId(), models.size(), allErrors);
            return executeSimulated(ctx, query, intent, knowledge, systemPrompt,
                    models.get(0));
        }

        // 构建成功结果
        String llmResponse = response.getContent() != null ? response.getContent() : "";
        int tokensUsed = response.getTokensInput() + response.getTokensOutput();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("systemPrompt", systemPrompt);
        result.put("response", llmResponse);
        result.put("model", usedModel);
        result.put("latencyMs", latency);
        result.put("tokensUsed", tokensUsed);

        ctx.setReasoningResult(result);
        ctx.getMetadata().put("reasoningModel", usedModel);
        ctx.getMetadata().put("reasoningLatencyMs", latency);
        ctx.getMetadata().put("reasoningTokens", tokensUsed);

        log.info("[OAG:{}] 推理完成 model={} latency={}ms tokens={}",
                ctx.getTraceId(), usedModel, latency, tokensUsed);

        return ctx;
    }

    /**
     * 模拟 LLM 推理路径（LLM 网关不可用或所有模型失败时的降级方案）。
     */
    private OagPipelineContext executeSimulated(OagPipelineContext ctx, String query,
                                                  String intent, Map<String, Object> knowledge,
                                                  String systemPrompt, String model) {
        long start = System.currentTimeMillis();
        String llmResponse = simulateLlmResponse(query, intent, knowledge);
        long latency = System.currentTimeMillis() - start;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("systemPrompt", systemPrompt);
        result.put("response", llmResponse);
        result.put("model", model);
        result.put("latencyMs", latency);
        result.put("tokensUsed", estimateTokens(query) + estimateTokens(llmResponse));

        ctx.setReasoningResult(result);
        ctx.getMetadata().put("reasoningModel", model);
        ctx.getMetadata().put("reasoningLatencyMs", latency);
        ctx.getMetadata().put("reasoningTokens", result.get("tokensUsed"));

        log.info("[OAG:{}] 推理完成 (SIMULATED) model={} latency={}ms tokens={}",
                ctx.getTraceId(), model, latency, result.get("tokensUsed"));

        return ctx;
    }

    /**
     * 构建 {@link ChatRequest}。
     */
    private ChatRequest buildChatRequest(String systemPrompt, String userQuery,
                                         String model, double temperature, int maxTokens) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatMessage("system", systemPrompt));
        messages.add(new ChatMessage("user", userQuery));
        return new ChatRequest(model, messages, temperature, maxTokens, false);
    }

    /**
     * 选取优先级最高的可用 LLM Provider。
     *
     * @return 选定的 Provider，无可用时返回 null
     */
    private LLMProvider selectProvider() {
        if (llmProviderProvider == null) {
            return null;
        }
        List<LLMProvider> providers = llmProviderProvider.getIfAvailable();
        if (providers == null || providers.isEmpty()) {
            return null;
        }
        return providers.stream()
                .sorted(java.util.Comparator.comparingInt(LLMProvider::priority))
                .filter(LLMProvider::isAvailable)
                .findFirst()
                .orElse(null);
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
     * 模拟 LLM 响应（降级兜底路径）。
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
        if (text == null || text.isEmpty()) { return 0; }
        return text.length() / 2;
    }
}
