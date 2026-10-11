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
 * {@link ContextSourceReader} 的 DATA_ASSET 面实现（详设-09 v1.2 §十一 H-5 卷 07 SPI bean）。
 *
 * <p><b>ST08 只读通道</b>：认知域禁直连 {@code ecos_dw}，数据资产元数据一律经 data-engine
 * 既有只读接口 {@code GET /api/v1/datanet/assets/\{assetId\}} 获取；HTTP 底座复用 runtime 的
 * 共享客户端 {@code cognitiveRestTemplate} + {@link ServiceEndpointResolver} 两态寻址
 * （§2.5 底座收口 / X-35，禁自行 new RestTemplate）。</p>
 *
 * <p><b>只接已就绪的一面</b>：{@link #supports(String)} 只对 {@code DATA_ASSET} 返回 true。
 * KNOWLEDGE / ONTOLOGY / CAPABILITY 面无对应只读契约 ⇒ 保持"未接线"，由
 * {@code ContextBuilderService} 按 §5.5 语义处理（必需源 503 {@code E503_DEPENDENCY_MISSING}
 * + {@code data.missing}），<b>禁</b>在此声明支持再返回空集合伪造成功（X-44）。</p>
 *
 * <p><b>SUP-CTX-01 三要素</b>：{@code version} 必须取源侧透出的实际版本 —— 端点未透出版本列时
 * 抛 {@link ContextReadException}，<b>禁</b>回填请求声明的版本（回填等于自证一致，
 * 会让 {@code enforceVersionConsistency} 永久通过）；同理 {@code authoritative=false}、
 * {@code freshness=UNKNOWN}：源侧未声明即不替源侧声明。权限裁剪不在本类做
 * （§5.2：Builder 不得先读全量再自行过滤），由 Builder 的 {@code ContextSecurityTrimmer} 承接。</p>
 */
@Component
public class DataEngineContextSourceReader implements ContextSourceReader {

    private static final Logger log = LoggerFactory.getLogger(DataEngineContextSourceReader.class);

    /** 本实现承接的来源类型（与 {@code ContextSourceRef.sourceType} 字面量同源）。 */
    static final String SOURCE_TYPE_DATA_ASSET = "DATA_ASSET";

    /** data-engine 资产详情只读路径（owner=datanet，route-manifest 单源）。 */
    static final String ASSET_DETAIL_PATH = "/api/v1/datanet/assets/%s";

    private static final String DATA_OWNER_SERVICE = "datanet";
    private static final int CODE_SUCCESS = 0;

    private final RestTemplate restTemplate;
    private final ServiceEndpointResolver endpointResolver;

    public DataEngineContextSourceReader(@Qualifier("cognitiveRestTemplate") RestTemplate restTemplate,
                                         ServiceEndpointResolver endpointResolver) {
        this.restTemplate = restTemplate;
        this.endpointResolver = endpointResolver;
    }

    @Override
    public boolean supports(String sourceType) {
        return SOURCE_TYPE_DATA_ASSET.equals(sourceType);
    }

    @Override
    public SourceReadResult read(ContextBuildRequest request, ContextSourceRef ref) {
        if (ref == null || isBlank(ref.getSourceId())) {
            throw new ContextReadException(SOURCE_TYPE_DATA_ASSET,
                    ref == null ? null : ref.getSourceId(),
                    "DATA_ASSET 源缺 sourceId（禁全量扫描资产目录，§5.4-2）");
        }
        JsonNode data = fetchAssetDetail(ref.getSourceId());
        String version = firstText(data, "version", "assetVersion", "asset_version",
                "dataVersion", "data_version", "schemaVersion", "schema_version");
        if (version == null) {
            throw new ContextReadException(SOURCE_TYPE_DATA_ASSET, ref.getSourceId(),
                    "源侧资产详情未透出 version（SUP-CTX-01 三要素不全，禁以请求声明版本回填）");
        }
        String asOfTime = firstText(data, "lastSyncTime", "last_sync_time",
                "lastTaggedAt", "last_tagged_at");

        SourceReadResult result = new SourceReadResult();
        result.setVersion(version);
        result.setAsOfTime(asOfTime);
        result.setAssetRefs(assetRefs(ref.getSourceId(), version, data, asOfTime));
        // 源侧未声明权威等级与新鲜度策略 ⇒ 不替源侧声明（空/未知态，禁伪造 CURRENT）
        result.setAuthoritative(false);
        result.setDenied(false);
        return result;
    }

    private List<AssetProjection> assetRefs(String assetId, String version, JsonNode data, String asOfTime) {
        List<AssetProjection> refs = new ArrayList<>();
        refs.add(new AssetProjection(assetId, version,
                firstText(data, "categoryStatus", "category_status"), asOfTime));
        for (JsonNode field : data.path("fields")) {
            String fieldKey = firstText(field, "fieldKey", "field_key", "fieldName", "field_name", "columnName");
            if (fieldKey != null) {
                refs.add(new AssetProjection(assetId + "." + fieldKey, version,
                        firstText(field, "sensitivityLevel", "sensitivity_level"), asOfTime));
            }
        }
        return refs;
    }

    /** 拉取资产详情信封的 data 段；任何失败都抛 {@link ContextReadException}（禁空结果伪装"没有数据"）。 */
    private JsonNode fetchAssetDetail(String assetId) {
        String url = baseUrl() + String.format(ASSET_DETAIL_PATH, enc(assetId));
        JsonNode body;
        try {
            ResponseEntity<JsonNode> resp = restTemplate.exchange(url, HttpMethod.GET, null, JsonNode.class);
            body = resp.getBody();
        } catch (HttpStatusCodeException e) {
            log.error("data-engine 资产详情读失败: status={} assetId={}", e.getStatusCode().value(), assetId);
            throw new ContextReadException(SOURCE_TYPE_DATA_ASSET, assetId,
                    "源侧读失败 status=" + e.getStatusCode().value());
        } catch (RestClientException e) {
            log.error("data-engine 资产详情不可达: assetId={} errType={}",
                    assetId, e.getClass().getSimpleName());
            throw new ContextReadException(SOURCE_TYPE_DATA_ASSET, assetId,
                    "data-engine 只读通道不可达（禁回退直连 ecos_dw，ST08）");
        }
        if (body == null) {
            throw new ContextReadException(SOURCE_TYPE_DATA_ASSET, assetId,
                    "源侧返回空响应体（视为不可用，禁按空数据处理）");
        }
        int code = body.path("code").asInt(Integer.MIN_VALUE);
        if (code == 404) {
            throw new ContextReadException(SOURCE_TYPE_DATA_ASSET, assetId,
                    "资产不存在（源侧 404，真空态与读失败分列）");
        }
        if (code != CODE_SUCCESS) {
            throw new ContextReadException(SOURCE_TYPE_DATA_ASSET, assetId,
                    "源侧裁决失败 code=" + code);
        }
        JsonNode data = body.path("data");
        if (data.isMissingNode() || data.isNull()) {
            throw new ContextReadException(SOURCE_TYPE_DATA_ASSET, assetId,
                    "源侧 code=0 但 data 缺失（契约破坏，禁返回空集合）");
        }
        return data;
    }

    private String baseUrl() {
        return endpointResolver
                .resolve(DATA_OWNER_SERVICE, ServiceEndpointResolver.RouteMode.MONOLITH)
                .orElseThrow(() -> new ContextReadException(SOURCE_TYPE_DATA_ASSET, null,
                        "route-manifest 未登记服务 " + DATA_OWNER_SERVICE + "，只读基址无法解析（fail-loud）"));
    }

    /** camelCase / snake_case 双取（data-engine 侧列名未收口，读端容忍）；全缺 ⇒ null。 */
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
