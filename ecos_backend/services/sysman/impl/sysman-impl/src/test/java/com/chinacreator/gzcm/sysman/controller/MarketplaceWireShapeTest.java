package com.chinacreator.gzcm.sysman.controller;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 能力市场端点 wire 保真（DI-2 #147DN）。
 *
 * <p>资产行 11 键（{@code id/name/nameZh/description/category/status/owner/version/tags/
 * createdAt/updatedAt}）在种子、发布、列表、搜索、仪表盘五腿同形；列表/搜索/仪表盘/
 * 访问申请的信封键面按顺序断言。</p>
 */
class MarketplaceWireShapeTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final List<String> ASSET_KEYS = List.of("id", "name", "nameZh", "description",
            "category", "status", "owner", "version", "tags", "createdAt", "updatedAt");

    private MarketplaceController controller;

    @BeforeEach
    void setUp() {
        controller = new MarketplaceController();
    }

    @Test
    @DisplayName("list：{data,total} + 资产 11 键；按 updatedAt 倒序")
    void listKeepsAssetAndEnvelopeKeys() throws Exception {
        JsonNode data = dataOf(controller.listAssets(null, null));

        assertEquals(List.of("data", "total"), fieldNames(data));
        assertEquals(5, data.get("total").asInt());
        assertEquals(ASSET_KEYS, fieldNames(data.get("data").get(0)));

        JsonNode filtered = dataOf(controller.listAssets("model", "published"));
        assertEquals(1, filtered.get("total").asInt());
        assertEquals("Fraud Detection Model", filtered.get("data").get(0).get("name").asText());
    }

    @Test
    @DisplayName("publish：资产 11 键，缺省值回填（category=data/status=draft/version=1.0.0）")
    void publishKeepsAssetKeysAndDefaults() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "Risk Rule Set");
        body.put("tags", List.of("risk", "rule"));

        JsonNode data = dataOf(controller.publishAsset(body));

        assertEquals(ASSET_KEYS, fieldNames(data));
        assertEquals("data", data.get("category").asText());
        assertEquals("draft", data.get("status").asText());
        assertEquals("1.0.0", data.get("version").asText());
        assertEquals(2, data.get("tags").size());
        assertTrue(data.get("id").asText().matches("[0-9a-f]{32}"), "发布 id 为去横线 UUID");
    }

    @Test
    @DisplayName("search：{data,total,query}（query 键恒出现），命中名称与标签")
    void searchKeepsQueryEcho() throws Exception {
        JsonNode data = dataOf(controller.searchAssets("fraud"));

        assertEquals(List.of("data", "total", "query"), fieldNames(data));
        assertEquals("fraud", data.get("query").asText());
        assertEquals(1, data.get("total").asInt());

        JsonNode blank = dataOf(controller.searchAssets(null));
        assertEquals(5, blank.get("total").asInt());
        assertTrue(blank.has("query") && blank.get("query").isNull(), "未传 q 时 query 键仍在（null）");
    }

    @Test
    @DisplayName("dashboard：5 键 + recentAssets 最多 5 条；request-access 6 键")
    void dashboardAndAccessKeepKeys() throws Exception {
        JsonNode dash = dataOf(controller.dashboard());
        assertEquals(List.of("totalAssets", "byCategory", "byStatus", "recentAssets", "timestamp"),
                fieldNames(dash));
        assertEquals(5, dash.get("totalAssets").asInt());
        assertEquals(5, dash.get("recentAssets").size());
        assertEquals(3, dash.get("byStatus").get("published").asInt());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("assetId", "asset-001");
        body.put("reason", "audit");
        JsonNode access = dataOf(controller.requestAccess(body));
        assertEquals(List.of("requestId", "assetId", "requester", "reason", "status", "createdAt"),
                fieldNames(access));
        assertEquals("pending", access.get("status").asText());
        assertEquals("anonymous", access.get("requester").asText());
    }

    // ── 辅助 ─────────────────────────────────────────────

    private static JsonNode dataOf(ApiResponse<?> resp) throws Exception {
        assertEquals(0, resp.getCode(), "夹具必须走成功信封: " + resp.getMessage());
        return MAPPER.valueToTree(resp).get("data");
    }

    private static List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }
}
