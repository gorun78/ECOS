package com.chinacreator.gzcm.engine.kb.repo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * RAG 查询向量 helper — PMO-50 T1。
 *
 * <p>通过 llm-gateway REST {@code /api/v1/llm/embedding} 拉取 query 真实向量，
 * 替代此前 {@code KnowledgeEmbeddingMapper#searchByVector(String queryText, int topK)}
 * 的"queryText::vector"字符串强转（必失败）bug。
 *
 * <p>支持三种 OpenAI / DeepSeek / Gemini 兼容响应结构（兼容 llm-gateway 三种 provider）。
 * 失败时返回 {@code null}，让上层回退 ILIKE 关键词搜索（PMO 优雅降级）。
 */
@Component
public class QueryEmbeddingHelper {

    private static final Logger log = LoggerFactory.getLogger(QueryEmbeddingHelper.class);

    private final RestTemplate restTemplate;

    public QueryEmbeddingHelper(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * 获取 query 文本的 embedding 字面量（含括号）。
     *
     * @param query   用户查询文本
     * @param model   embedding 模型名（如 text-embedding-3-small）
     * @param baseUrl llm-gateway base（空字符串=不可用，直接返回 null）
     * @return 形如 "[v1,v2,...]" 字面量；失败/不可用时返回 null
     */
    public String embed(String query, String model, String baseUrl) {
        if (query == null || query.isBlank()) {
            return null;
        }
        if (baseUrl == null || baseUrl.isBlank()) {
            log.debug("QueryEmbeddingHelper: llm-gateway-base is blank — skip, fallback ILIKE");
            return null;
        }
        String url = baseUrl.endsWith("/")
                ? baseUrl + "api/v1/llm/embedding"
                : baseUrl + "/api/v1/llm/embedding";
        try {
            Map<String, Object> body = Map.of(
                    "input", query,
                    "model", model == null || model.isBlank() ? "text-embedding-3-small" : model
            );
            @SuppressWarnings("unchecked")
            Map<String, Object> resp = restTemplate.postForObject(url, body, Map.class);
            if (resp == null) {
                return null;
            }
            float[] vec = parseVector(resp);
            if (vec == null || vec.length == 0) {
                log.warn("QueryEmbeddingHelper: empty vector from llm-gateway");
                return null;
            }
            return toLiteral(vec);
        } catch (Exception e) {
            log.warn("QueryEmbeddingHelper: embedding call failed (fallback to ILIKE): {}", e.getMessage());
            return null;
        }
    }

    /**
     * 解析三种响应结构（OpenAI / DeepSeek / Gemini）：
     * - OpenAI: {"data":[{"embedding":[...]}],"model":...,"usage":{...}}
     * - DeepSeek: 同 OpenAI
     * - Gemini: {"candidates":[{"content":{"parts":[{"text":"[v1,v2,...]"}]}}]}
     */
    private float[] parseVector(Map<String, Object> resp) {
        // OpenAI / DeepSeek: data[0].embedding
        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> data = (List<Map<String, Object>>) resp.get("data");
            if (data != null && !data.isEmpty()) {
                @SuppressWarnings("unchecked")
                List<Object> emb = (List<Object>) data.get(0).get("embedding");
                return toVec(emb);
            }
        } catch (Exception ignored) {
            // fallthrough
        }
        // Gemini: candidates[0].content.parts[0].text = "[v1,v2,...]"
        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> candidates = (List<Map<String, Object>>) resp.get("candidates");
            if (candidates != null && !candidates.isEmpty()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
                if (content != null) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
                    if (parts != null && !parts.isEmpty()) {
                        Object text = parts.get(0).get("text");
                        if (text instanceof String s) {
                            return parseLiteral(s);
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            // fallthrough
        }
        return null;
    }

    private float[] toVec(List<Object> values) {
        if (values == null || values.isEmpty()) return null;
        float[] out = new float[values.size()];
        for (int i = 0; i < values.size(); i++) {
            Object v = values.get(i);
            if (v instanceof Number n) {
                out[i] = n.floatValue();
            } else {
                try {
                    out[i] = Float.parseFloat(String.valueOf(v));
                } catch (Exception e) {
                    return null;
                }
            }
        }
        return out;
    }

    private float[] parseLiteral(String literal) {
        if (literal == null || literal.isBlank()) return null;
        String s = literal.trim();
        if (s.startsWith("[")) s = s.substring(1);
        if (s.endsWith("]")) s = s.substring(0, s.length() - 1);
        String[] parts = s.split(",");
        float[] out = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                out[i] = Float.parseFloat(parts[i].trim());
            } catch (Exception e) {
                return null;
            }
        }
        return out;
    }

    /**
     * 形如 [0.1,0.2,0.3]（含括号），适配 MyBatis {@code e.embedding <=> #{v}::vector}。
     */
    private String toLiteral(float[] vec) {
        StringBuilder sb = new StringBuilder(vec.length * 8);
        sb.append('[');
        for (int i = 0; i < vec.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(vec[i]);
        }
        sb.append(']');
        return sb.toString();
    }
}
