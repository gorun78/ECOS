package com.chinacreator.gzcm.sysman.controller;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.sysman.service.PortalSearchQueryService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Portal 全局搜索 wire 保真（DI-2 #147DR）。
 *
 * <p>应答 5 键（q/type/total/items/rlsApplied）；命中项对齐前端 {@code SearchHit}
 * 四键（type/id/name/url，内部 score 不出 wire）；标题命中权重 2、描述命中 1；
 * 同 type:id 去重；type 过滤只打对应检索腿。</p>
 */
class PortalSearchWireShapeTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private PortalSearchController controller;

    /** 只覆盖四类检索腿，DAO 不参与（构造传 null 即可）。 */
    private static final class FakeSearchService extends PortalSearchQueryService {
        private int agentCalls;

        private FakeSearchService() {
            super(null);
        }

        @Override
        public List<Map<String, Object>> searchAgents(String keyword) {
            agentCalls++;
            return List.of(row("a1", "Revenue Agent", "数字人"), row("a1", "Revenue Agent", "重复行"));
        }

        @Override
        public List<Map<String, Object>> searchWorkflows(String keyword) {
            return List.of(row("w1", null, "weekly revenue report"));
        }

        @Override
        public List<Map<String, Object>> searchGlossary(String keyword) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("id", "g1");
            r.put("term", "收入");
            r.put("definition", "revenue 口径定义");
            return List.of(r);
        }

        @Override
        public List<Map<String, Object>> searchScenarios(String keyword) {
            return List.of(row("s1", "Revenue Scenario", "渠道分析"));
        }
    }

    @BeforeEach
    void setUp() {
        com.chinacreator.gzcm.sysman.iam.context.UserContext.clear();
        controller = new PortalSearchController(new FakeSearchService());
    }

    @Test
    @DisplayName("全量搜索：5 键信封 + 命中 4 键；按分数倒序、同 type:id 去重")
    void searchKeepsEnvelopeAndHitKeys() throws Exception {
        JsonNode data = dataOf(controller.search("revenue", "all"));

        assertEquals(List.of("q", "type", "total", "items", "rlsApplied"), fieldNames(data));
        assertEquals("revenue", data.get("q").asText());
        assertEquals("all", data.get("type").asText());
        assertEquals(4, data.get("total").asInt(), "重复 agent 行被去重");
        assertFalse(data.get("rlsApplied").asBoolean());

        JsonNode items = data.get("items");
        assertEquals(List.of("type", "id", "name", "url"), fieldNames(items.get(0)));
        assertEquals("agent", items.get(0).get("type").asText(), "标题命中权重最高，排首位");
        assertTrue(items.get(0).get("url").asText().startsWith("/app/"));
        assertFalse(items.get(0).has("score"), "内部 score 不得出 wire");
    }

    @Test
    @DisplayName("type 过滤：仅打对应检索腿（agent 只调 searchAgents）")
    void typeFilterCallsOnlyMatchingLeg() throws Exception {
        FakeSearchService service = new FakeSearchService();
        controller = new PortalSearchController(service);

        JsonNode data = dataOf(controller.search("revenue", "agent"));

        assertEquals(1, data.get("total").asInt());
        assertEquals(1, service.agentCalls);
        assertEquals("agent", data.get("items").get(0).get("type").asText());
    }

    @Test
    @DisplayName("参数校验：空 q / 超长 q / 非法 type 均 400 且不带 data")
    void invalidParamsReturnBadRequest() throws Exception {
        assertEquals(400, controller.search(" ", "all").getCode());
        assertEquals(400, controller.search("x".repeat(101), "all").getCode());
        assertEquals(400, controller.search("revenue", "bogus").getCode());
    }

    // ── 辅助 ─────────────────────────────────────────────

    private static Map<String, Object> row(String id, String name, String description) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", id);
        r.put("name", name);
        r.put("description", description);
        return r;
    }

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
