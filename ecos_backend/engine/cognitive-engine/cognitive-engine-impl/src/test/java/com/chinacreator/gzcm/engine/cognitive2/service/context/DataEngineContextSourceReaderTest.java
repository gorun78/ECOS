package com.chinacreator.gzcm.engine.cognitive2.service.context;

import com.chinacreator.gzcm.engine.cognitive.api.context.ContextBuildRequest;
import com.chinacreator.gzcm.engine.cognitive.api.context.ContextSourceRef;
import com.chinacreator.gzcm.runtime.access.endpoint.ServiceEndpointResolver;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * DATA_ASSET 权威源读取实现单测（详设-09 v1.2 §十一 H-5 卷 07 SPI）。
 *
 * <p>守的是 Context Builder 的三条诚实纪律：只接已就绪的来源面（其余面保持未接线，由 Builder
 * 记 {@code data.missing}）、源侧未透出版本即失败（禁回填请求声明版本自证一致）、
 * 读失败必抛异常而非返回空集合（"引擎返回空内容 ≠ 没有数据"）。</p>
 */
class DataEngineContextSourceReaderTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String BASE = "http://datanet-test-base";

    private final RestTemplate restTemplate = mock(RestTemplate.class);
    private final ServiceEndpointResolver resolver = mock(ServiceEndpointResolver.class);
    private final DataEngineContextSourceReader reader =
            new DataEngineContextSourceReader(restTemplate, resolver);

    private void routeRegistered() {
        when(resolver.resolve("datanet", ServiceEndpointResolver.RouteMode.MONOLITH))
                .thenReturn(Optional.of(BASE));
    }

    private void respondWith(String json) {
        routeRegistered();
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), nullable(HttpEntity.class), eq(JsonNode.class)))
                .thenReturn(new ResponseEntity<>(readTree(json), HttpStatus.OK));
    }

    private void failWith(RuntimeException error) {
        routeRegistered();
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), nullable(HttpEntity.class), eq(JsonNode.class)))
                .thenThrow(error);
    }

    private static JsonNode readTree(String json) {
        try {
            return MAPPER.readTree(json);
        } catch (Exception e) {
            throw new IllegalStateException("测试夹具 JSON 不合法: " + e.getMessage(), e);
        }
    }

    private static ContextSourceRef ref(String sourceType, String sourceId, String version) {
        ContextSourceRef r = new ContextSourceRef();
        r.setSourceType(sourceType);
        r.setSourceId(sourceId);
        r.setVersion(version);
        r.setRequired(true);
        return r;
    }

    @Test
    @DisplayName("只承接 DATA_ASSET：其余面无只读契约 ⇒ 保持未接线（禁声明支持再回空集合）")
    void supportsOnlyTheWiredSourceFacet() {
        assertTrue(reader.supports("DATA_ASSET"));
        assertFalse(reader.supports("KNOWLEDGE"));
        assertFalse(reader.supports("ONTOLOGY"));
        assertFalse(reader.supports("COGNITIVE"));
        assertFalse(reader.supports("CAPABILITY"));
        assertFalse(reader.supports(null));
    }

    @Test
    @DisplayName("成功读取：version/asOfTime 取源侧实际值，资产与字段级引用一并带出")
    void readsAssetMetadataFromDataEngine() {
        respondWith("""
                {"code":0,"data":{
                  "assetId":"asset-001","assetVersion":"v7","categoryStatus":"PUBLISHED",
                  "lastSyncTime":"2026-10-01T02:00:00Z",
                  "fields":[{"fieldKey":"revenue","sensitivityLevel":"L2"},
                            {"field_key":"cost_amount","sensitivity_level":"L1"},
                            {"fieldKey":"   ","sensitivityLevel":"L3"}]}}""");

        ContextSourceReader.SourceReadResult result =
                reader.read(new ContextBuildRequest(), ref("DATA_ASSET", "asset-001", "v7"));

        assertEquals("v7", result.getVersion());
        assertEquals("2026-10-01T02:00:00Z", result.getAsOfTime());
        assertEquals("PUBLISHED", result.getAssetRefs().get(0).getStatus());
        assertEquals(3, result.getAssetRefs().size());
        assertEquals("asset-001.cost_amount", result.getAssetRefs().get(2).getRefId());
        // 源侧未声明权威等级/新鲜度策略 ⇒ 不替源侧声明
        assertFalse(result.isAuthoritative());
        assertEquals("UNKNOWN", result.getFreshness());
        assertFalse(result.isDenied());
        assertTrue(result.getFacts().isEmpty());
    }

    @Test
    @DisplayName("源侧未透出 version ⇒ 抛 ContextReadException（禁回填请求声明的版本）")
    void missingSourceVersionFailsInsteadOfEchoingRequestVersion() {
        respondWith("""
                {"code":0,"data":{"assetId":"asset-001","categoryStatus":"PUBLISHED"}}""");

        ContextReadException e = assertThrows(ContextReadException.class,
                () -> reader.read(new ContextBuildRequest(), ref("DATA_ASSET", "asset-001", "v7")));
        assertTrue(e.getMessage().contains("version"), e.getMessage());
        assertEquals("DATA_ASSET", e.sourceType);
        assertEquals("asset-001", e.sourceId);
    }

    @Test
    @DisplayName("缺 sourceId ⇒ 直接失败（禁无界检索全平台资产目录）")
    void blankSourceIdIsRejected() {
        routeRegistered();
        ContextReadException e = assertThrows(ContextReadException.class,
                () -> reader.read(new ContextBuildRequest(), ref("DATA_ASSET", "  ", "v7")));
        assertTrue(e.getMessage().contains("sourceId"), e.getMessage());
        assertThrows(ContextReadException.class, () -> reader.read(new ContextBuildRequest(), null));
    }

    @Test
    @DisplayName("资产不存在（body.code=404）与读失败（不可达 / 非 0 裁决 / 空响应体）都抛异常，禁空结果伪装")
    void readFailuresNeverBecomeEmptyResults() {
        respondWith("""
                {"code":404,"message":"ASSET-010: asset 不存在: asset-404","data":null}""");
        ContextReadException notFound = assertThrows(ContextReadException.class,
                () -> reader.read(new ContextBuildRequest(), ref("DATA_ASSET", "asset-404", "v7")));
        assertTrue(notFound.getMessage().contains("404"), notFound.getMessage());

        respondWith("""
                {"code":0,"message":"ok"}""");
        assertTrue(assertThrows(ContextReadException.class,
                () -> reader.read(new ContextBuildRequest(), ref("DATA_ASSET", "asset-001", "v7")))
                .getMessage().contains("data"));

        failWith(new ResourceAccessException("connect refused"));
        assertTrue(assertThrows(ContextReadException.class,
                () -> reader.read(new ContextBuildRequest(), ref("DATA_ASSET", "asset-001", "v7")))
                .getMessage().contains("ST08"));

        failWith(HttpClientErrorException.create(HttpStatus.FORBIDDEN, "Forbidden",
                HttpHeaders.EMPTY, "{\"code\":403}".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8));
        assertEquals("DATA_ASSET", assertThrows(ContextReadException.class,
                () -> reader.read(new ContextBuildRequest(), ref("DATA_ASSET", "asset-001", "v7"))).sourceType);
    }

    @Test
    @DisplayName("route-manifest 未登记 datanet ⇒ fail-loud（禁硬编码基址兜底，X-35）")
    void unresolvedBaseFailsLoud() {
        when(resolver.resolve(anyString(), any(ServiceEndpointResolver.RouteMode.class)))
                .thenReturn(Optional.empty());
        ContextReadException e = assertThrows(ContextReadException.class,
                () -> reader.read(new ContextBuildRequest(), ref("DATA_ASSET", "asset-001", "v7")));
        assertTrue(e.getMessage().contains("route-manifest"), e.getMessage());
        assertNull(e.sourceId);
    }

    @Test
    @DisplayName("请求 URL = 解析基址 + 登记路径 + 编码后的 assetId（不新增对外 API 面）")
    void requestUrlIsBuiltFromRegisteredPath() {
        respondWith("""
                {"code":0,"data":{"assetId":"a b","version":"v1"}}""");
        ContextSourceReader.SourceReadResult result =
                reader.read(new ContextBuildRequest(), ref("DATA_ASSET", "a b", "v1"));
        assertEquals("v1", result.getVersion());
        org.mockito.ArgumentCaptor<String> url = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(restTemplate)
                .exchange(url.capture(), eq(HttpMethod.GET), nullable(HttpEntity.class), eq(JsonNode.class));
        assertEquals(BASE + "/api/v1/datanet/assets/a+b", url.getValue());
    }
}
