package com.chinacreator.gzcm.engine.kb.repo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * RAG 向量 helper — PMO-50 T1 建立，B4 修复解析并扩展批量。
 *
 * <p>通过 llm-gateway REST {@code POST /api/v1/llm/embedding} 拉取真实向量（架构铁律 §2.5-2：
 * 禁止直连 Provider）。该端点为统一返回体 {@code ApiResponse<EmbeddingResponse>}，
 * 故本类需先拆信封 {@code data}，再读 {@code data.data}（{@code List<List<Number>>}）。
 *
 * <p>失败时返回 {@code null} / 空列表，由上层回退关键词检索并 {@code log.warn}
 * （禁止静默降级）。
 *
 * @since PMO-50 T1 / B4
 */
@Component
public class QueryEmbeddingHelper {

    private static final Logger log = LoggerFactory.getLogger(QueryEmbeddingHelper.class);

    private final RestTemplate restTemplate;

    public QueryEmbeddingHelper(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * 获取单条 query 文本的 embedding 字面量（含括号）。
     *
     * @param query   用户查询文本
     * @param model   embedding 模型名（如 text-embedding-3-small）
     * @param baseUrl llm-gateway base（空字符串=不可用，直接返回 null）
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
     * 批量获取文本 embedding（B4/T3：一次 HTTP 调 llm-gateway，避免逐条调用）。
     *
     * @param texts   待向量化文本列表（空/全是空白 → 直接返回空列表）
     * @param model   embedding 模型名
     * @param baseUrl llm-gateway base（空=不可用）
     * @return 与 {@code texts} 等长对齐的向量列表（整体失败返回空列表）
     */
    public List<float[]> embedBatch(List<String> texts, String model, String baseUrl) {
        if (texts == null || texts.isEmpty()) {
            return Collections.emptyList();
        }
        if (baseUrl == null || baseUrl.isBlank()) {
            log.debug("QueryEmbeddingHelper: llm-gateway-base is blank — skip, fallback ILIKE");
            return Collections.emptyList();
        }
        String url = baseUrl.endsWith("/")
                ? baseUrl + "api/v1/llm/embedding"
                : baseUrl + "/api/v1/llm/embedding";
        try {
            Map<String, Object> body = Map.of(
                    "input", texts,
                    "model", model == null || model.isBlank() ? "text-embedding-3-small" : model
            );
            @SuppressWarnings("unchecked")
            Map<String, Object> resp = restTemplate.postForObject(url, body, Map.class);
            if (resp == null) {
                log.warn("QueryEmbeddingHelper: llm-gateway 返回空响应 url={}", url);
                return Collections.emptyList();
            }
            List<float[]> vectors = parseVectors(resp);
            if (vectors.isEmpty()) {
                log.warn("QueryEmbeddingHelper: 未解析到向量（响应结构不匹配或调用失败）url={}, resp={}", url, resp);
            }
            return vectors;
        } catch (Exception e) {
            log.warn("QueryEmbeddingHelper: embedding 调用失败（上层降级）url={}, err={}", url, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 解析响应为向量列表，兼容两种结构：
     * <ol>
     *   <li>统一返回体 {@code ApiResponse<EmbeddingResponse>}：{@code data.data = [[...],[...]]}</li>
     *   <li>裸 OpenAI / DeepSeek 响应：{@code data[0].embedding = [...]}</li>
     * </ol>
     * 另兼容 Gemini {@code candidates[0].content.parts[0].text = "[v1,v2,...]"}。
     */
    @SuppressWarnings("unchecked")
    private List<float[]> parseVectors(Map<String, Object> resp) {
        Object dataNode = resp.get("data");
        // 1) 统一返回体信封：code/success + data 为对象
        if (dataNode instanceof Map<?, ?> envelopeData && resp.containsKey("code")) {
            Integer code = resp.get("code") instanceof Number n ? n.intValue() : null;
            if (code == null || code != 0) {
                log.warn("QueryEmbeddingHelper: llm-gateway 业务失败 code={}, message={}", code, resp.get("message"));
                return Collections.emptyList();
            }
            Object inner = ((Map<String, Object>) envelopeData).get("data");
            if (inner instanceof List<?> rows) {
                return parseVectorRows(rows);
            }
            return Collections.emptyList();
        }
        // 2) 裸 OpenAI / DeepSeek 响应：data 为数组，元素含 embedding
        if (dataNode instanceof List<?> rows) {
            return parseVectorRows(rows);
        }
        // 3) Gemini 兼容
        Object candidates = resp.get("candidates");
        if (candidates instanceof List<?> list && !list.isEmpty()) {
            Object first = list.get(0);
            if (first instanceof Map<?, ?> cand) {
                Object content = ((Map<String, Object>) cand).get("content");
                if (content instanceof Map<?, ?> contentMap) {
                    Object parts = ((Map<String, Object>) contentMap).get("parts");
                    if (parts instanceof List<?> partList && !partList.isEmpty()
                            && partList.get(0) instanceof Map<?, ?> part) {
                        Object text = ((Map<String, Object>) part).get("text");
                        if (text instanceof String s) {
                            float[] vec = parseLiteral(s);
                            return vec == null ? Collections.emptyList() : List.of(vec);
                        }
                    }
                }
            }
        }
        return Collections.emptyList();
    }

    /**
     * 逐行解析向量：每行既可能是 {@code {"embedding":[...]}}（OpenAI），
     * 也可能是裸数组 {@code [...]}（EmbeddingResponse.data 序列化结果）。
     */
    @SuppressWarnings("unchecked")
    private List<float[]> parseVectorRows(List<?> rows) {
        List<float[]> out = new ArrayList<>(rows.size());
        for (Object row : rows) {
            if (row instanceof Map<?, ?> rowMap) {
                Object emb = ((Map<String, Object>) rowMap).get("embedding");
                if (emb instanceof List<?> values) {
                    float[] vec = toVec(values);
                    if (vec != null) {
                        out.add(vec);
                    }
                }
            } else if (row instanceof List<?> values) {
                float[] vec = toVec(values);
                if (vec != null) {
                    out.add(vec);
                }
            }
        }
        return out;
    }

    private float[] toVec(List<?> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
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
        if (literal == null || literal.isBlank()) {
            return null;
        }
        String s = literal.trim();
        if (s.startsWith("[")) {
            s = s.substring(1);
        }
        if (s.endsWith("]")) {
            s = s.substring(0, s.length() - 1);
        }
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
