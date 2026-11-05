package com.chinacreator.gzcm.engine.data.controller;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.data.dto.DataEngineConfigRefreshVO;
import com.chinacreator.gzcm.engine.data.dto.DataEngineConfigSegmentsVO;
import com.chinacreator.gzcm.engine.data.dto.DataEngineConfigUpdateVO;
import com.chinacreator.gzcm.sysman.config.service.impl.SysConfigService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * data-engine 配置端点 wire 保真（DI-2 #147DL）。
 *
 * <p>分段视图（{@code segments[].subgroups[].id/configs}）、子组视图短键、更新回执
 * （{@code updated/total}）与刷新回执（{@code refreshed/cache_size}）全部按既有键面与顺序断言；
 * 配置行夹具走属主 {@code SysConfigService#getByGroup} 的 Map 行（契约未改），
 * 转换由 {@code DataEngineConfigRow.fromRows} 承接。</p>
 */
class DataEngineConfigWireShapeTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private SysConfigService sysConfigService;
    private DataEngineConfigController controller;

    @BeforeEach
    void setUp() throws Exception {
        sysConfigService = mock(SysConfigService.class);
        controller = new DataEngineConfigController();
        Field field = DataEngineConfigController.class.getDeclaredField("sysConfigService");
        field.setAccessible(true);
        field.set(controller, sysConfigService);
    }

    @Test
    @DisplayName("settings/groups：四段固定顺序、覆盖子分区、未知键落 global/general、空值折空串")
    void segmentsKeepShapeOrderAndFallback() throws Exception {
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(row("dw.execution.mode", "memory"));
        rows.add(row("dw.lake.enabled", "false"));
        rows.add(row("dw.pipeline.max_steps", "20"));
        rows.add(row("dw.pipeline.log_storage", "db"));
        rows.add(row("dw.copilot.enabled", "false"));
        rows.add(row("dw.unknown.key", "x"));
        rows.add(row("dw.null.value", null));
        rows.add(row(null, "skipped"));
        when(sysConfigService.getByGroup("data-engine")).thenReturn(rows);

        JsonNode data = dataOf(controller.getConfigBySegment());

        assertEquals(List.of("segments"), fieldNames(data));
        JsonNode segments = data.get("segments");
        assertEquals(List.of("near-source", "dw", "semantic", "global"), idsOf(segments));
        assertEquals(List.of("id", "subgroups"), fieldNames(segments.get(0)));

        // semantic 段为占位空段
        assertEquals(0, segments.get(2).get("subgroups").size());

        // 段内子分区按 SUBGROUP_ORDER：execution → pipeline → pipeline-advanced
        JsonNode dwSubgroups = segments.get(1).get("subgroups");
        assertEquals(List.of("execution", "pipeline", "pipeline-advanced"), idsOf(dwSubgroups));
        assertEquals(List.of("id", "configs"), fieldNames(dwSubgroups.get(0)));
        // 精确 key 覆盖：Pipeline 2.0 运行期项落 pipeline-advanced，完整 key 保留
        assertEquals("db", dwSubgroups.get(2).get("configs").get("dw.pipeline.log_storage").asText());
        assertEquals("20", dwSubgroups.get(1).get("configs").get("dw.pipeline.max_steps").asText());

        JsonNode globalSubgroups = segments.get(3).get("subgroups");
        assertEquals(List.of("copilot", "general"), idsOf(globalSubgroups));
        assertEquals("x", globalSubgroups.get(1).get("configs").get("dw.unknown.key").asText());
        assertEquals("", globalSubgroups.get(1).get("configs").get("dw.null.value").asText());
    }

    @Test
    @DisplayName("settings（旧子组视图）：短键去前缀、general 去 dw. 前缀、缺省值补插")
    void flatViewKeepsShortKeysAndBackfillsDefaults() throws Exception {
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(row("dw.execution.mode", "memory"));
        rows.add(row("dw.lake.enabled", "false"));
        rows.add(row("dw.unknown.key", "x"));
        when(sysConfigService.getByGroup("data-engine")).thenReturn(rows);

        JsonNode data = dataOf(controller.getAllConfig());

        assertEquals("memory", data.get("execution").get("mode").asText());
        assertEquals("false", data.get("lake").get("enabled").asText());
        assertEquals("x", data.get("general").get("unknown.key").asText(),
                "general 组去 dw. 前缀后保留短键");
        // 版本迭代新增键自动补插（幂等）：库里缺失的默认键会 upsert
        verify(sysConfigService, atLeastOnce())
                .upsertValue(eq("dw.metadata.history_versions"), anyString(), eq("data-engine"),
                        eq("string"), eq(""));
    }

    @Test
    @DisplayName("settings 更新回执：updated + total；刷新回执：refreshed + cache_size")
    void writeReceiptsKeepKeys() throws Exception {
        List<Map<String, String>> updates = List.of(
                Map.of("config_key", "dw.execution.mode", "config_value", "doris"));
        when(sysConfigService.updateBatch(updates)).thenReturn(1);
        when(sysConfigService.cacheSize()).thenReturn(64);

        DataEngineConfigUpdateVO updateVo = controller.updateConfig(updates).getData();
        assertEquals(1, updateVo.updated());
        assertEquals(1, updateVo.total());

        ApiResponse<DataEngineConfigRefreshVO> refresh = controller.refreshCache();
        JsonNode data = dataOf(refresh);
        assertEquals(List.of("refreshed", "cache_size"), fieldNames(data));
        assertTrue(data.get("refreshed").asBoolean());
        assertEquals(64, data.get("cache_size").asInt());
    }

    // ── 辅助 ─────────────────────────────────────────────

    private static Map<String, Object> row(String key, String value) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("config_key", key);
        row.put("config_value", value);
        return row;
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

    private static List<String> idsOf(JsonNode array) {
        List<String> ids = new ArrayList<>();
        array.forEach(item -> ids.add(item.get("id").asText()));
        return ids;
    }
}
