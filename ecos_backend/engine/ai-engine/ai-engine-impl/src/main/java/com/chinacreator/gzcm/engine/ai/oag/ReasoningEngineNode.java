package com.chinacreator.gzcm.engine.ai.oag;

import com.chinacreator.gzcm.common.security.cls.ColumnSurfaceContract;
import com.chinacreator.gzcm.runtime.llm.gateway.ChatMessage;
import com.chinacreator.gzcm.runtime.llm.gateway.ChatRequest;
import com.chinacreator.gzcm.runtime.llm.gateway.ChatResponse;
import com.chinacreator.gzcm.runtime.llm.gateway.LLMGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * <p>调用策略（F06-05 / 铁律 §2.5-2）：唯一出口 = {@link LLMGateway#call(ChatRequest)}，
 * 引擎侧不持有 Provider 抽象、不携带 api-key。</p>
 *
 * <p>若 {@code LLMGateway} Bean 缺失（独立引擎调试场景），fallback 到模拟路径。</p>
 *
 * <p>依赖：{@link LLMGateway}（required=false）</p>
 */
@Component
public class ReasoningEngineNode implements OagNode {

    private static final Logger log = LoggerFactory.getLogger(ReasoningEngineNode.class);

    /** 默认模型（fallback 链为空时使用） */
    private static final String DEFAULT_MODEL = "deepseek-chat";

    /**
     * 列面<b>已判定</b>时给 LLM 的约束行（唯一允许宣称"已过滤"的文案）。
     * 判据来自单源 {@link ColumnSurfaceContract#isEvaluatedFace(Object)}，禁在此另写一套。
     */
    static final String CLS_FILTERED_CLAIM = "- 返回列已由 CLS 策略过滤";

    /**
     * 列面<b>未判定</b>时的如实文案（#142/B4-3d-6 / D-B4-28）。
     *
     * <p>不提"未判定"而只是"少说一句"是不够的：OAG 这条链路当前没有数据腿，
     * 模型若以为上游裁过列，就会把"输出检索到的全部字段"当成安全行为。
     * 这里把事实交给模型，同时给出一条替代约束（不扩列）。</p>
     */
    static final String CLS_NOT_EVALUATED_CLAIM =
            "- 本链路未经列级安全裁决，回答只可复述上文出现过的字段，不得补全或扩列";

    /** LLM 模型 fallback 链（按优先级排列，第一项最先尝试） */
    @Value("${ecos.oag.model-fallback:'deepseek-chat'}")
    private String modelFallbackRaw;

    /** LLM 底层调用网关（required=false，缺失时走模拟路径）— 唯一 LLM 出口（F06-05） */
    @Autowired(required = false)
    private LLMGateway llmGateway;

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

                // 唯一出口：LLMGateway（F06-05）。引擎不携带 api-key，密钥由网关侧经 security-engine 解析。
                request.setCallerId("service:ai-engine-oag");
                // F06-07：会话作用域下传，网关计量行按它归集（OAG 上下文自带 sessionId）
                request.setSessionId(ctx.getSessionId());
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
        // 宣称位（#142/B4-3d-6 / D-B4-28）：旧判据是 !isEmpty()，于是 SecurityCheckerNode
        // 写的空 Map（"没裁决过"）与真裁决结果（"已按策略裁剪"）在这里同义 ——
        // 提示词因此会替一条没发生过的过滤说话。改判据为单源证据面：只有显式声明
        // "已判定"的列面才允许宣称过滤过；未判定/缺失一律如实写明未判定（默认不宣称）。
        if (ColumnSurfaceContract.isEvaluatedFace(clsColumns)) {
            sb.append(CLS_FILTERED_CLAIM).append("\n");
        } else {
            sb.append(CLS_NOT_EVALUATED_CLAIM).append("\n");
        }
        sb.append("\n请根据以上上下文回答用户问题。");

        return sb.toString();
    }

    /**
     * 模拟 LLM 响应（降级兜底路径）。
     *
     * <p>订正（#142/B4-3d-6 / D-B4-28）：原文案无条件写「已按安全策略过滤」，
     * 而这条路径既没有数据腿也没有列面裁决（知识片段是常量桩），
     * 那句话在演示之外从未为真。降级文案可以朴素，但不许替没发生过的安全步骤说话。</p>
     */
    private String simulateLlmResponse(String query, String intent, Map<String, Object> knowledge) {
        return String.format(
                "根据您的查询「%s」(意图: %s)，系统已完成分析。\n\n" +
                "ECOS 平台基于 DIKW 架构提供数据治理、知识图谱和大模型Agent三大核心能力。" +
                "当前检索到 %d 条相关知识片段；本降级路径未做列级安全裁决，"
                + "仅复述上文出现过的字段。",
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
