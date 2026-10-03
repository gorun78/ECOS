package com.chinacreator.gzcm.engine.kb.repo;

import com.chinacreator.gzcm.runtime.llm.gateway.EmbeddingRequest;
import com.chinacreator.gzcm.runtime.llm.gateway.EmbeddingResponse;
import com.chinacreator.gzcm.runtime.llm.gateway.LLMGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * RAG 向量 helper — PMO-50 T1 建立，F04-13（K-34/K-35）改造后<b>不再直连 llm-gateway HTTP 端点，
 * 而是经 {@link LLMGateway} 接口调用</b>，收敛超时/重试/审计/配额由底座承担。
 *
 * <p>失败时返回 {@code null} / 空列表，由上层回退关键词检索并 {@code log.warn}
 * （禁止静默降级；调用方 {@code KnowledgeRetrievalServiceImpl} 会置 {@code vectorDegraded=true}）。
 *
 * <p>公开签名保留 {@code (query, model, baseUrl)} 三参形式以便向下兼容既有 mock/调用点，
 * 其中 {@code baseUrl} 现为<b>兼容参数</b>（不再用于拼接 URL，llm-gateway 内部按 provider 解析），
 * 但仍保留判空："baseUrl blank = 上层降级" 语义与 K-34 补偿前一致，避免调用侧静默变更。
 */
@Component
public class QueryEmbeddingHelper {

    private static final Logger log = LoggerFactory.getLogger(QueryEmbeddingHelper.class);

    /** 该 callerId 用于 llm-gateway 侧 ABAC 裁决 / 密钥引用解析 / 审计主体。 */
    private static final String CALLER_ID = "kb-engine:rag-query";

    private final LLMGateway llmGateway;

    public QueryEmbeddingHelper(LLMGateway llmGateway) {
        this.llmGateway = llmGateway;
    }

    /**
     * 获取单条 query 文本的 embedding 字面量（含括号）。
     *
     * @param query   用户查询文本
     * @param model   embedding 模型名（如 text-embedding-3-small）
     * @param baseUrl <b>兼容参数</b>：blank 时直接返回 null（保留 K-34 兼容语义 = "llm-gateway 未配置 → 降级"）；
     *                不再用于 URL 拼接（收敛到 {@link LLMGateway}）
     * @return 形如 {@code [v1,v2,...]} 字面量；失败/不可用时返回 null
     */
    public String embed(String query, String model, String baseUrl) {
        List<float[]> vectors = embedBatch(Collections.singletonList(query), model, baseUrl);
        if (vectors.isEmpty()) {
            return null;
        }
        return toLiteral(vectors.get(0));
    }

    /**
     * 批量获取文本 embedding — 一次调用 {@link LLMGateway#embed(EmbeddingRequest)}。
     *
     * @param texts   待向量化文本列表（空/全是空白 → 直接返回空列表）
     * @param model   embedding 模型名
     * @param baseUrl <b>兼容参数</b>（同上；blank = 降级）
     * @return 与 {@code texts} 等长对齐的向量列表（整体失败返回空列表）
     */
    public List<float[]> embedBatch(List<String> texts, String model, String baseUrl) {
        if (texts == null || texts.isEmpty()) {
            return Collections.emptyList();
        }
        if (baseUrl == null || baseUrl.isBlank()) {
            log.debug("QueryEmbeddingHelper: llm-gateway base 未配置 — skip，上层降级 ILIKE");
            return Collections.emptyList();
        }
        try {
            EmbeddingRequest req = new EmbeddingRequest();
            req.setTexts(texts.toArray(new String[0]));
            req.setModel(model);
            req.setCallerId(CALLER_ID);
            EmbeddingResponse resp = llmGateway.embed(req);
            if (resp == null || !resp.isSuccess()) {
                String err = resp == null ? "null response" : resp.getErrorMsg();
                log.warn("QueryEmbeddingHelper: llm-gateway 返回失败 err={}", err);
                return Collections.emptyList();
            }
            List<float[]> data = resp.getData();
            if (data == null || data.isEmpty()) {
                log.warn("QueryEmbeddingHelper: llm-gateway 返回空向量 model={}", model);
                return Collections.emptyList();
            }
            if (data.size() != texts.size()) {
                log.warn("QueryEmbeddingHelper: 向量数量 {} 与输入 {} 不匹配 — 上层降级",
                        data.size(), texts.size());
                // 数量不对齐属真失败（会导致向量-文本错位），按失败处理
                return Collections.emptyList();
            }
            return data;
        } catch (Exception e) {
            log.warn("QueryEmbeddingHelper: embedding 调用异常（上层降级） err={}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 向量 → pgvector 字面量（形如 {@code [0.1,0.2,0.3]}，含括号），
     * 适配 MyBatis {@code CAST(#{v} AS vector)} / {@code embedding_vec <=> #{v}::vector}。
     */
    public static String toLiteral(float[] vec) {
        if (vec == null || vec.length == 0) {
            return null;
        }
        StringBuilder sb = new StringBuilder(vec.length * 8);
        sb.append('[');
        for (int i = 0; i < vec.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(vec[i]);
        }
        sb.append(']');
        return sb.toString();
    }
}
