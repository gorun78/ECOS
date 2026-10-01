package com.chinacreator.gzcm.engine.kb.repo;

import com.chinacreator.gzcm.runtime.llm.gateway.EmbeddingRequest;
import com.chinacreator.gzcm.runtime.llm.gateway.EmbeddingResponse;
import com.chinacreator.gzcm.runtime.llm.gateway.LLMGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * RAG 向量 helper — PMO-50 T1 建立，B4 修复解析并扩展批量。
 *
 * <p>F04-13 LLM 收敛：不再直连 {@code /api/v1/llm/*} REST，统一经
 * {@link LLMGateway#embed(EmbeddingRequest)}（llm-gateway 横切底座，铁律 §2.5-2）。
 * 本类只负责把 {@link EmbeddingResponse} 的 {@code List<float[]>} 转成
 * pgvector 字面量（{@code [v1,v2,...]}）供上层落库/检索使用。
 *
 * <p>失败时返回 {@code null} / 空列表，由上层回退关键词检索并 {@code log.warn}
 * （禁止静默降级）。
 *
 * @since PMO-50 T1 / B4, F04-13
 */
@Component
public class QueryEmbeddingHelper {

    private static final Logger log = LoggerFactory.getLogger(QueryEmbeddingHelper.class);

    /** 兜底模型名（model 为空时透传给 llm-gateway，由其按配置兜底） */
    private static final String DEFAULT_MODEL = "text-embedding-3-small";

    private final LLMGateway llmGateway;

    public QueryEmbeddingHelper(LLMGateway llmGateway) {
        this.llmGateway = llmGateway;
    }

    /**
     * 获取单条 query 文本的 embedding 字面量（含括号）。
     *
     * @param query   用户查询文本
     * @param model   embedding 模型名（如 text-embedding-3-small）
     * @param baseUrl llm-gateway base — <b>F04-13 收敛后不再使用</b>，baseUrl 由
     *                llm-gateway 内部按 {@code LLMGatewayProperties} / provider 映射统一解析；
     *                保留仅为兼容既有调用方签名（API 只增不改）。
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
     * 批量获取文本 embedding（B4/T3：一次调用 llm-gateway，避免逐条调用）。
     *
     * @param texts   待向量化文本列表（空/全是空白 → 直接返回空列表）
     * @param model   embedding 模型名
     * @param baseUrl llm-gateway base — <b>F04-13 收敛后不再使用</b>，保留仅为兼容既有签名。
     * @return 与 {@code texts} 等长对齐的向量列表（整体失败返回空列表）
     */
    public List<float[]> embedBatch(List<String> texts, String model, String baseUrl) {
        if (texts == null || texts.isEmpty()) {
            return Collections.emptyList();
        }
        String effectiveModel = (model == null || model.isBlank()) ? DEFAULT_MODEL : model;
        try {
            EmbeddingRequest req = new EmbeddingRequest();
            req.setInput(texts.get(0));
            if (texts.size() > 1) {
                req.setTexts(texts.toArray(new String[0]));
            }
            req.setModel(effectiveModel);
            req.setCallerId("kb-engine:rag");

            EmbeddingResponse resp = llmGateway.embed(req);
            if (resp == null || !resp.isSuccess()) {
                log.warn("QueryEmbeddingHelper: llm-gateway embed 失败 — fallback ILIKE, err={}",
                        resp == null ? "null" : resp.getErrorMsg());
                return Collections.emptyList();
            }
            List<float[]> data = resp.getData();
            if (data == null || data.isEmpty()) {
                log.warn("QueryEmbeddingHelper: llm-gateway 未返回向量 — fallback ILIKE");
                return Collections.emptyList();
            }
            return data;
        } catch (Exception e) {
            log.warn("QueryEmbeddingHelper: embedding 调用失败（上层降级）err={}", e.getMessage());
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
