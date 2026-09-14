package com.chinacreator.gzcm.engine.kb.repo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.LinkedHashMap;

/**
 * 查询文本 → 真实向量嵌入辅助类（PMO-50 T1）。
 *
 * <p>统一走 runtime llm-gateway 网关 POST {@code {baseUrl}/embedding}
 * （铁律 2.5 #2：kb-engine 自身不持有 provider key，所有 LLM/embedding 调用
 * 经 llm-gateway 出口；baseUrl 指向 gateway :8080 反重写表路由，
 * 不直连 18084 内网端口，遵循 ADR-7）。
 *
 * <p>返回 PG vector 文本字面量 {@code [v1,v2,...]}，供
 * {@code KnowledgeEmbeddingMapper#searchByVector} 直接绑定 {@code ::vector} 强转。
 * 调用失败返回 {@code null}，由调用方走 ILIKE 关键词回退。
 */
@Component
public class QueryEmbeddingHelper {

    private static final Logger log = LoggerFactory.getLogger(QueryEmbeddingHelper.class);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public QueryEmbeddingHelper(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * 把查询文本 embed 成 PG vector 字面量字符串。
     *
     * @param queryText 查询原文（trim 后处理）
     * @param modelId   embedding 模型标识（可空，沿 gateway 默认 profile）
     * @param baseUrl   llm-gateway 入口（如 {@code http://localhost:8080/api/v1/llm}）
     * @return {@code [a,b,c...]} 字面量；任何失败返回 {@code null}
     */
    public String embed(String queryText, String modelId, String baseUrl) {
        if (queryText == null || queryText.isBlank() || baseUrl == null || baseUrl.isBlank()) {
            log.debug("QueryEmbedding: skip — query or baseUrl missing");
            return null;
        }
        String url = baseUrl.endsWith("/") ? baseUrl + "embedding" : baseUrl + "/embedding";
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("input", queryText.trim());
            if (modelId != null && !modelId.isBlank()) {
                body.put("model", modelId);
            }

            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                log.warn("QueryEmbedding: llm-gateway returned non-2xx: {}", response.getStatusCode());
                return null;
            }

            JsonNode root = objectMapper.readTree(response.getBody());
            List<Double> values = extractVector(root);
            if (values == null || values.isEmpty()) {
                log.warn("QueryEmbedding: llm-gateway response missing vector field");
                return null;
            }
            return formatVectorLiteral(values);
        } catch (Exception e) {
            log.warn("QueryEmbedding: llm-gateway call failed — fallback to ILIKE. url={}, error={}",
                    url, e.getMessage());
            return null;
        }
    }

    /**
     * 解析多种 provider 响应结构，提取真实向量数组。
     *
     * <ol>
     *   <li>{@code { data: [ { embedding: [...] } ] }}（OpenAI 风格）</li>
     *   <li>{@code { data: [ [...] ] }}（扁平数组）</li>
     *   <li>{@code { data: { embedding: [...] } }}</li>
     *   <li>{@code { response: { embeddings:[ { values: [...] } ] } }}（Gemini 风格）</li>
     *   <li>{@code { response: { embedding: [...] } }}</li>
     *   <li>{@code { embedding: [...] }} / {@code { values: [...] }}</li>
     * </ol>
     */
    private List<Double> extractVector(JsonNode root) {
        if (root == null || root.isNull()) {
            return null;
        }
        JsonNode dataNode = root.path("data");
        if (dataNode.isArray() && dataNode.size() > 0) {
            JsonNode first = dataNode.get(0);
            if (first.isObject() && first.path("embedding").isArray()) {
                return readNumbers(first.path("embedding"));
            }
            if (first.isArray()) {
                return readNumbers(first);
            }
        }
        if (dataNode.isObject() && dataNode.path("embedding").isArray()) {
            return readNumbers(dataNode.path("embedding"));
        }
        JsonNode response = root.path("response");
        if (response.isObject()) {
            JsonNode embeddings = response.path("embeddings");
            if (embeddings.isArray() && embeddings.size() > 0
                    && embeddings.get(0).isObject()
                    && embeddings.get(0).path("values").isArray()) {
                return readNumbers(embeddings.get(0).path("values"));
            }
            if (response.path("embedding").isArray()) {
                return readNumbers(response.path("embedding"));
            }
        }
        if (root.path("embedding").isArray()) {
            return readNumbers(root.path("embedding"));
        }
        if (root.path("values").isArray()) {
            return readNumbers(root.path("values"));
        }
        if (root.isArray()) {
            return readNumbers(root);
        }
        return null;
    }

    /**
     * 数值 JsonNode 数组 → double 列表。
     */
    private List<Double> readNumbers(JsonNode array) {
        List<Double> out = new ArrayList<>();
        if (array == null || !array.isArray()) {
            return out;
        }
        for (int i = 0; i < array.size(); i++) {
            JsonNode item = array.get(i);
            if (item != null && item.isNumber()) {
                out.add(item.asDouble());
            }
        }
        return out;
    }

    /**
     * double 列表 → PG vector 字面量（{@code [a,b,c]}）。
     */
    static String formatVectorLiteral(List<Double> values) {
        StringBuilder sb = new StringBuilder(values.size() * 12 + 2);
        sb.append('[');
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(String.format(Locale.US, "%.6f", values.get(i)));
        }
        sb.append(']');
        return sb.toString();
    }
}
