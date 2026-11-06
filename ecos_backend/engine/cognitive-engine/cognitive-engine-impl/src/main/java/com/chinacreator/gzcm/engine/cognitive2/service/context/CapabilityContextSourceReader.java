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

/**
 * {@link ContextSourceReader} 的 CAPABILITY 面（详细设计-认知引擎 §4.3）。
 *
 * <p>只读 AI 能力目录 {@code GET /api/v1/ai/capabilities}（aiming），按 sourceId 精确取一项；
 * 版本 = 目录必载的 {@code contractVersion}。目录未命中/无契约版本 ⇒ 失败（禁伪造能力可用）。
 * 规模为冻结能力目录（有界），不构成无界扫描；权限裁剪由目标能力目录与 Builder 承接。</p>
 */
@Component
public class CapabilityContextSourceReader implements ContextSourceReader {

    private static final Logger log = LoggerFactory.getLogger(CapabilityContextSourceReader.class);
    static final String SOURCE_TYPE_CAPABILITY = "CAPABILITY";
    static final String AI_SERVICE = "aiming";
    static final String CAPABILITIES_PATH = "/api/v1/ai/capabilities";
    private static final int CODE_SUCCESS = 0;

    private final RestTemplate restTemplate;
    private final ServiceEndpointResolver endpointResolver;

    public CapabilityContextSourceReader(@Qualifier("cognitiveRestTemplate") RestTemplate restTemplate,
                                         ServiceEndpointResolver endpointResolver) {
        this.restTemplate = restTemplate;
        this.endpointResolver = endpointResolver;
    }

    @Override
    public boolean supports(String sourceType) {
        return SOURCE_TYPE_CAPABILITY.equals(sourceType);
    }

    @Override
    public SourceReadResult read(ContextBuildRequest request, ContextSourceRef ref) {
        if (ref == null || ref.getSourceId() == null || ref.getSourceId().isBlank()) {
            throw new ContextReadException(SOURCE_TYPE_CAPABILITY, null, "sourceId required");
        }
        JsonNode data = fetchCatalog();
        JsonNode match = null;
        for (JsonNode item : data) {
            if (ref.getSourceId().equals(text(item, "capabilityId", "capability_id"))) {
                match = item;
                break;
            }
        }
        if (match == null) {
            throw new ContextReadException(SOURCE_TYPE_CAPABILITY, ref.getSourceId(),
                    "capability not found in catalog (not an empty success)");
        }
        String contractVersion = text(match, "contractVersion", "contract_version");
        if (contractVersion == null) {
            throw new ContextReadException(SOURCE_TYPE_CAPABILITY, ref.getSourceId(),
                    "capability catalog missing contractVersion (frozen three-element contract)");
        }
        SourceReadResult result = new SourceReadResult();
        result.setVersion(contractVersion);
        result.setCapabilities(java.util.List.of(new CapabilityProjection(
                ref.getSourceId(), contractVersion, null, null,
                text(match, "riskLevel", "risk_level"))));
        result.setAuthoritative(false);
        result.setDenied(false);
        return result;
    }

    private JsonNode fetchCatalog() {
        String base = endpointResolver.resolve(AI_SERVICE, ServiceEndpointResolver.RouteMode.MONOLITH)
                .orElseThrow(() -> new ContextReadException(SOURCE_TYPE_CAPABILITY, null,
                        "route-manifest missing service " + AI_SERVICE));
        JsonNode body;
        try {
            ResponseEntity<JsonNode> resp = restTemplate.exchange(base + CAPABILITIES_PATH,
                    HttpMethod.GET, null, JsonNode.class);
            body = resp.getBody();
        } catch (HttpStatusCodeException e) {
            log.error("capability catalog read failed: status={}", e.getStatusCode().value());
            throw new ContextReadException(SOURCE_TYPE_CAPABILITY, null,
                    "capability catalog status=" + e.getStatusCode().value());
        } catch (RestClientException e) {
            log.error("capability catalog unreachable: {}", e.getClass().getSimpleName());
            throw new ContextReadException(SOURCE_TYPE_CAPABILITY, null,
                    AI_SERVICE + " capability catalog unreachable");
        }
        if (body == null) {
            throw new ContextReadException(SOURCE_TYPE_CAPABILITY, null, "empty catalog body");
        }
        int code = body.path("code").asInt(Integer.MIN_VALUE);
        if (code != CODE_SUCCESS) {
            throw new ContextReadException(SOURCE_TYPE_CAPABILITY, null, "catalog code=" + code);
        }
        JsonNode data = body.path("data");
        if (!data.isArray()) {
            throw new ContextReadException(SOURCE_TYPE_CAPABILITY, null,
                    "catalog data is not an array (contract breach)");
        }
        return data;
    }

    private static String text(JsonNode node, String... keys) {
        for (String k : keys) {
            JsonNode v = node.path(k);
            if (!v.isMissingNode() && !v.isNull() && !v.asText().isBlank()) {
                return v.asText();
            }
        }
        return null;
    }
}
