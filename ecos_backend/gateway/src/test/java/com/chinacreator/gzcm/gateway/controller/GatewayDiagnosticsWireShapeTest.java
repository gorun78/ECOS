package com.chinacreator.gzcm.gateway.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code GET /api/v1/system/gateway-diagnostics} wire 键面门（DI-2 #147CU）。
 *
 * <p>旧实现用 {@code LinkedHashMap} 拼装：7 制品条目 4 键（name/port/mode/active）、过滤链 4 键
 * （index/name/<b>ORDER</b>/specIndex）、档位矩阵 2 键（activeProfile/capabilities）与三档能力表、
 * 总响应 6 键（traceId 允许 null 但键位恒在）。本门按集合相等＋键序钉住。</p>
 *
 * <p><b>同时钉住一处实测订正</b>：{@code routeManifest} 旧实现的双重 put 使响应里实际是
 * <b>原始 manifest 文档</b>（{@code $comment/version/entries}），而摘要形态从未出网。</p>
 */
class GatewayDiagnosticsWireShapeTest {

    private static final List<String> TOP_KEYS = List.of(
            "artifacts", "filterChain", "routeManifest", "anonymousEndpoints", "editionMatrix", "traceId");

    private static List<String> keys(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    private JsonNode data() {
        ApiResponse<GatewayDiagnosticsController.DiagnosticsVO> resp =
                new GatewayDiagnosticsController().gatewayDiagnostics();
        return new ObjectMapper().valueToTree(resp.getData());
    }

    @Test
    @DisplayName("总响应 6 键且键序固定；traceId 键位恒在（允许 null）")
    void topLevelShape() {
        JsonNode data = data();
        assertEquals(TOP_KEYS, keys(data));
        assertTrue(data.has("traceId"));
    }

    @Test
    @DisplayName("制品 7 条：首条 4 键（name/port/mode/active），端口与档位不漂")
    void artifactsShape() {
        JsonNode artifacts = data().path("artifacts");
        assertEquals(7, artifacts.size());
        assertEquals(List.of("name", "port", "mode", "active"), keys(artifacts.get(0)));
        assertEquals("gateway", artifacts.get(0).path("name").asText());
        assertEquals(8080, artifacts.get(0).path("port").asInt());
        assertEquals("monolith", artifacts.get(0).path("mode").asText());
        assertEquals("workspace", artifacts.get(6).path("name").asText());
        assertEquals(18090, artifacts.get(6).path("port").asInt());
    }

    @Test
    @DisplayName("过滤链 6 条：4 键，ORDER 保持大写键名")
    void filterChainShape() {
        JsonNode chain = data().path("filterChain");
        assertEquals(6, chain.size());
        assertEquals(List.of("index", "name", "ORDER", "specIndex"), keys(chain.get(0)));
        assertEquals(0, chain.get(0).path("index").asInt());
        assertEquals("VersionPrefixRewriteFilter", chain.get(0).path("name").asText());
    }

    @Test
    @DisplayName("routeManifest 是原始 manifest 文档（$comment/version/entries），非摘要")
    void routeManifestIsRawDocument() {
        JsonNode manifest = data().path("routeManifest");
        assertTrue(manifest.isObject(), "classpath 下 route-manifest.json 应在场");
        assertTrue(manifest.has("entries"), "实际出网的是原始 manifest，entries 必在");
        assertTrue(manifest.has("version"));
        assertTrue(!manifest.has("serviceRoutes"), "旧双重 put 的摘要形态从未出网，出现即行为漂移");
    }

    @Test
    @DisplayName("档位矩阵 2 键，capabilities 三档且键序固定")
    void editionMatrixShape() {
        JsonNode matrix = data().path("editionMatrix");
        assertEquals(List.of("activeProfile", "capabilities"), keys(matrix));
        assertEquals(List.of("standard", "enterprise", "ultimate"), keys(matrix.path("capabilities")));
        assertEquals(5, matrix.path("capabilities").path("standard").size());
        assertEquals(7, matrix.path("capabilities").path("ultimate").size());
    }

    @Test
    @DisplayName("匿名清单来自注册表单源（对象形态，键为路径模式）")
    void anonymousEndpointsShape() {
        JsonNode anon = data().path("anonymousEndpoints");
        assertTrue(anon.isObject());
        assertTrue(anon.size() > 0, "匿名注册表不应为空（空集说明单源被绕过）");
    }
}
