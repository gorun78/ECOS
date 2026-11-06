package com.chinacreator.gzcm.engine.cognitive2.service.context;

import com.chinacreator.gzcm.engine.cognitive.api.context.ContextBuildRequest;
import com.chinacreator.gzcm.engine.cognitive.api.context.ContextSourceRef;
import com.chinacreator.gzcm.runtime.access.endpoint.ServiceEndpointResolver;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link ContextSourceReader} 的 K/I 面实现（详细设计-认知引擎 v1.0 §4.3）。
 *
 * <p>来源类型（与前端 {@code CONTEXT_SOURCE_TYPES} 同词表）：</p>
 * <ul>
 *   <li>{@code KNOWLEDGE} → kb-engine 文章详情 {@code GET /api/v1/knowledge/articles/{id}}（dccheng）；</li>
 *   <li>{@code ONTOLOGY}  → ontology-engine 实体详情 {@code GET /api/v1/ecos/ontologies/entities/{id}}（buszhi）。</li>
 * </ul>
 *
 * <p><b>只读 + 不伪造</b>：版本取源侧 actual 值（{@code version} 或 {@code updatedAt/updated_at}）；
 * 源侧未透出版本 ⇒ {@link ContextReadException}，禁回填请求声明版本（SUP-CTX-01）。
 * 读失败/404 抛异常，禁返回空集合伪装“没有数据”；权威等级与新鲜度不替源侧声明。</p>
 */
@Component
public class KnowledgeOntologyContextSourceReader implements ContextSourceReader {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeOntologyContextSourceReader.class);

    static final String SOURCE_TYPE_KNOWLEDGE = "KNOWLEDGE";
    static final String SOURCE_TYPE_ONTOLOGY = "ONTOLOGY";

    static final String KNOWLEDGE_SERVICE = "dccheng";
    static final String ONTOLOGY_SERVICE = "buszhi";
    static final String KNOWLEDGE_PATH = "/api/v1/knowledge/articles/%s";
    static final String ONTOLOGY_PATH = "/api/v1/ecos/ontologies/entities/%s";
    private static final int CODE_SUCCESS = 0;

    private final RestTemplate restTemplate;
    private final ServiceEndpointResolver endpointResolver;

    public KnowledgeOntologyContextSourceReader(@Qualifier("cognitiveRestTemplate") RestTemplate restTemplate,
                                                ServiceEndpointResolver endpointResolver) {
        this.restTemplate = restTemplate;
        this.endpointResolver = endpointResolver;
    }

    @Override
    public boolean supports(String sourceType) {
        return SOURCE_TYPE_KNOWLEDGE.equals(sourceType) || SOURCE_TYPE_ONTOLOGY.equals(sourceType);
    }

    @Override
    public SourceReadResult read(ContextBuildRequest request, ContextSourceRef ref) {
        if (ref == null || isBlank(ref.getSourceId())) {
            throw new ContextReadException(ref == null ? "UNKNOWN" : ref.getSourceType(), null,
                    "sourceId required (no full-catalog scan, §5.4-2)");
        }
        boolean knowledge = SOURCE_TYPE_KNOWLEDGE.equals(ref.getSourceType());
        String service = knowledge ? KNOWLEDGE_SERVICE : ONTOLOGY_SERVICE;
        String path = String.format(knowledge ? KNOWLEDGE_PATH : ONTOLOGY_PATH, enc(ref.getSourceId()));
        JsonNode data = fetch(service, path, ref.getSourceType(), ref.getSourceId());

        String version = firstText(data, "version", "updatedAt", "updated_at", "updateTime", "update_time");
        if (version == null) {
            throw new ContextReadException(ref.getSourceType(), ref.getSourceId(),
                    "source side exposes no version/updatedAt (SUP-CTX-01; request version must not be echoed)");
        }
        String asOfTime = firstText(data, "updatedAt", "updated_at", "updateTime", "update_time", "createdAt", "created_at");
        String status = firstText(data, "status", "state", "lifecycleState", "lifecycle_state");

        SourceReadResult result = new SourceReadResult();
        result.setVersion(version);
        result.setAsOfTime(asOfTime);
        result.setAssetRefs(assetRefs(ref, data, version, status, asOfTime));
        // 源侧未声明权威等级/新鲜度 ⇒ 不替源侧声明
        result.setAuthoritative(false);
        result.setDenied(false);
        return result;
    }

    private List<AssetProjection> assetRefs(ContextSourceRef ref, JsonNode data, String version,
                                            String status, String asOfTime) {
        List<AssetProjection> refs = new ArrayList<>();
        refs.add(new AssetProjection(ref.getSourceId(), version, status, asOfTime));
        String nestedId = firstText(data, "id", "code", "entityId", "entity_id");
        if (nestedId != null && !nestedId.equals(ref.getSourceId())) {
            refs.add(new AssetProjection(nestedId, version, status, asOfTime));
        }
        return refs;
    }

    private JsonNode fetch(String service, String path, String sourceType, String sourceId) {
        String base = endpointResolver.resolve(service, ServiceEndpointResolver.RouteMode.MONOLITH)
                .orElseThrow(() -> new ContextReadException(sourceType, sourceId,
                        "route-manifest missing service " + service + " (read base unresolved)"));
        JsonNode body;
        try {
            ResponseEntity<JsonNode> resp = restTemplate.exchange(base + path, HttpMethod.GET, null, JsonNode.class);
            body = resp.getBody();
        } catch (HttpStatusCodeException e) {
            log.error("{} source read failed: status={} id={}", service, e.getStatusCode().value(), sourceId);
            throw new ContextReadException(sourceType, sourceId, "source status=" + e.getStatusCode().value());
        } catch (RestClientException e) {
            log.error("{} source unreachable: id={} type={}", service, sourceId, e.getClass().getSimpleName());
            throw new ContextReadException(sourceType, sourceId, service + " read channel unreachable");
        }
        if (body == null) {
            throw new ContextReadException(sourceType, sourceId, "empty response body treated as unavailable");
        }
        int code = body.path("code").asInt(Integer.MIN_VALUE);
        if (code == 404) {
            throw new ContextReadException(sourceType, sourceId, "source 404 (not found)");
        }
        if (code != CODE_SUCCESS) {
            throw new ContextReadException(sourceType, sourceId, "source code=" + code);
        }
        JsonNode data = body.path("data");
        if (data.isMissingNode() || data.isNull()) {
            throw new ContextReadException(sourceType, sourceId, "code=0 but data missing (contract breach)");
        }
        return data;
    }

    private static String firstText(JsonNode node, String... candidates) {
        if (node == null) {
            return null;
        }
        for (String k : candidates) {
            JsonNode v = node.path(k);
            if (!v.isMissingNode() && !v.isNull() && !v.asText().isBlank()) {
                return v.asText();
            }
        }
        return null;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String enc(String raw) {
        return java.net.URLEncoder.encode(raw, java.nio.charset.StandardCharsets.UTF_8);
    }
}
