package com.chinacreator.gzcm.engine.ai.service;

import com.chinacreator.gzcm.runtime.access.endpoint.ServiceEndpointResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 知识上下文读取腿（P0-4）：agent-loop 入模前从 kb-engine 取 RAG 检索片段。
 *
 * <p>属主端点 = kb-engine（dccheng 属主面）{@code POST /api/v1/knowledge/rag}，
 * 入参 {@code {query, topK, threshold}}，响应 {@code data.sources[{content,score,source}]}。
 * 基址经 runtime-access {@code ServiceEndpointResolver} 双模寻址（monolith 8080 / service 18086），
 * 解析不到或调用失败一律降级为空上下文并 warn 留痕（检索腿不参与安全裁决，
 * 安全裁决仍由 Guardrails/Security 单源 fail-closed）。</p>
 */
@Component
public class KnowledgeContextClient {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeContextClient.class);

    private static final String KB_OWNER = "dccheng";
    private static final String RAG_PATH = "/api/v1/knowledge/rag";
    private static final int DEFAULT_TOP_K = 5;
    private static final double DEFAULT_THRESHOLD = 0.7;
    private static final int MAX_CONTEXT_CHARS = 2000;

    private final ObjectProvider<ServiceEndpointResolver> endpointProvider;
    private final RestTemplate restTemplate;

    public KnowledgeContextClient(ObjectProvider<ServiceEndpointResolver> endpointProvider) {
        this.endpointProvider = endpointProvider;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(1500);
        factory.setReadTimeout(4000);
        this.restTemplate = new RestTemplate(factory);
    }

    /**
     * 检索并拼装知识上下文；无命中/不可用返回 {@code null}（调用方不得据此编造内容）。
     */
    @SuppressWarnings("unchecked")
    public String retrieve(String query, int topK) {
        if (query == null || query.isBlank()) {
            return null;
        }
        String base = resolveBase();
        if (base == null) {
            return null;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("query", query);
        body.put("topK", topK <= 0 ? DEFAULT_TOP_K : Math.min(topK, 20));
        body.put("threshold", DEFAULT_THRESHOLD);
        try {
            Map<String, Object> envelope = restTemplate.postForObject(base + RAG_PATH, body, Map.class);
            if (envelope == null) {
                return null;
            }
            Object dataObj = envelope.get("data");
            if (!(dataObj instanceof Map<?, ?> data)) {
                return null;
            }
            Object sourcesObj = data.get("sources");
            if (!(sourcesObj instanceof List<?> sources) || sources.isEmpty()) {
                log.debug("[RAG] 无召回片段: query={} base={}", query, base);
                return null;
            }
            StringBuilder sb = new StringBuilder();
            int appended = 0;
            for (Object item : sources) {
                if (!(item instanceof Map<?, ?> source)) {
                    continue;
                }
                Object content = source.get("content");
                if (content == null || String.valueOf(content).isBlank()) {
                    continue;
                }
                if (sb.length() >= MAX_CONTEXT_CHARS) {
                    break;
                }
                String text = String.valueOf(content).trim();
                int room = MAX_CONTEXT_CHARS - sb.length();
                if (text.length() > room) {
                    text = text.substring(0, Math.max(room, 0));
                }
                sb.append("- ").append(text).append('\n');
                appended++;
            }
            if (appended == 0) {
                return null;
            }
            log.info("[RAG] 召回 {} 条知识片段（{} 字符）: query={}", appended, sb.length(), query);
            return sb.toString();
        } catch (RuntimeException e) {
            log.warn("[RAG] 知识检索降级（kb-engine 不可用或响应异常）: query={} err={}",
                    query, e.getMessage());
            return null;
        }
    }

    private String resolveBase() {
        ServiceEndpointResolver resolver = endpointProvider == null ? null : endpointProvider.getIfAvailable();
        if (resolver == null) {
            log.debug("[RAG] ServiceEndpointResolver 未装配，跳过知识检索");
            return null;
        }
        Optional<String> host = resolver.resolve(KB_OWNER, ServiceEndpointResolver.RouteMode.SERVICE);
        if (host.isEmpty()) {
            log.warn("[RAG] kb-engine 基址不可解析（owner={}），跳过知识检索", KB_OWNER);
            return null;
        }
        String base = host.get();
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }
}
