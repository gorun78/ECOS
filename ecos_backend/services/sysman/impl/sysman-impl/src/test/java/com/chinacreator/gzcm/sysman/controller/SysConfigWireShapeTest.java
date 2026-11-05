package com.chinacreator.gzcm.sysman.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.sysman.service.SysConfigQueryService;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 系统配置面 wire 键面保真门（#147CA · DoD DI-2 第六批）。
 *
 * <p><b>钉的是什么</b>：本批把行面 Map 全部上收为服务侧 record，Controller 不再持弱类型。
 * 五条端点的键面各不相同，必须逐面钉死：
 * <ul>
 *   <li>根列表 data 元素＝<b>原始列名</b> 12 键（id/config_group/…/edition，含已解析的
 *       config_options 数组）；</li>
 *   <li>改值回执＝snake_case 3 键，请求三别名 value &gt; configValue &gt; config_value；</li>
 *   <li>元数据 9 键，且 {@code consumed_by} <b>只</b>在被消费时出现（属性级 NON_NULL）；</li>
 *   <li>审计汇总 4 键、条目 6 键（consumed_by/suggestion 互斥为 null 但键都在）。</li>
 * </ul></p>
 *
 * <p><b>变异自证</b>：把 {@link SysConfigQueryService.ConfigGroupRow} 的
 * {@code @JsonProperty("config_group")} 去掉 ⇒ 列名键集红；把元数据 {@code consumed_by}
 * 的 NON_NULL 去掉 ⇒ 未消费条目多键红。</p>
 */
class SysConfigWireShapeTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final List<String> ROW_KEYS = List.of("id", "config_group", "config_key",
            "config_value", "config_type", "config_label", "config_label_en", "description",
            "sort_order", "config_options", "impact_scope", "edition");

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return MAPPER.convertValue(value, Map.class);
    }

    private static List<String> keysOf(Object value) {
        return new ArrayList<>(asMap(value).keySet());
    }

    private static ObjectProvider<SysConfigQueryService> provider(SysConfigQueryService service) {
        return new ObjectProvider<>() {
            @Override
            public SysConfigQueryService getObject() {
                return service;
            }

            @Override
            public SysConfigQueryService getObject(Object... args) {
                return service;
            }

            @Override
            public SysConfigQueryService getIfAvailable() {
                return service;
            }

            @Override
            public SysConfigQueryService getIfUnique() {
                return service;
            }
        };
    }

    @Test
    @DisplayName("根列表 data 元素＝原始列名 12 键（键序）；config_options 是数组")
    void listRowsKeepLegacyColumnNames() {
        SysConfigQueryService service = mock(SysConfigQueryService.class);
        when(service.listByGroup(any(), any(), any())).thenReturn(List.of(
                new SysConfigQueryService.ConfigGroupRow("1", "general", "k", "v", "string",
                        "标签", "label", "desc", 3, List.of("a", "b"), "core", "all")));
        SysConfigController controller = new SysConfigController(provider(service));

        ApiResponse<SysConfigController.ConfigListVO> resp = controller.listByGroup("", "");
        assertEquals(List.of("data", "total"), keysOf(resp.getData()));
        Map<String, Object> row = asMap(resp.getData().data().get(0));
        assertEquals(ROW_KEYS, new ArrayList<>(row.keySet()));
        assertEquals(List.of("a", "b"), row.get("config_options"));
    }

    @Test
    @DisplayName("改值回执＝config_key/config_value/updated；请求三别名逐一生效")
    void updateKeepsSnakeCaseAndAliases() throws Exception {
        SysConfigQueryService service = mock(SysConfigQueryService.class);
        when(service.updateConfigValue(anyString(), anyString())).thenReturn(1);
        SysConfigController controller = new SysConfigController(provider(service));

        for (String json : List.of("{\"value\":\"v1\"}", "{\"configValue\":\"v1\"}",
                "{\"config_value\":\"v1\"}")) {
            SysConfigController.ConfigValueRequest request = MAPPER.readValue(
                    json, SysConfigController.ConfigValueRequest.class);
            ApiResponse<SysConfigController.ConfigUpdateResultVO> resp =
                    controller.update("k", request);
            assertEquals(ApiResponse.CODE_SUCCESS, resp.getCode(), json);
            assertEquals(List.of("config_key", "config_value", "updated"), keysOf(resp.getData()));
            assertEquals("v1", resp.getData().configValue());
            assertTrue(resp.getData().updated());
        }
    }

    @Test
    @DisplayName("元数据：9 键；consumed_by 仅被消费时出现（属性级 NON_NULL）")
    void metadataConsumedByIsConditional() {
        SysConfigQueryService service = mock(SysConfigQueryService.class);
        when(service.queryMetadata()).thenReturn(List.of(
                new SysConfigQueryService.ConfigMetaRow("1", "auth", "max_login_attempts", "5",
                        "number", "尝试次数", "attempts", "d", "core", "all"),
                new SysConfigQueryService.ConfigMetaRow("2", "general", "unknown_key", "x",
                        "string", "未知", "unknown", "d", "core", "all")));
        SysConfigController controller = new SysConfigController(provider(service));

        List<SysConfigController.ConfigMetaVO> data = controller.metadata().getData();
        List<String> consumedKeys = keysOf(data.get(0));
        assertEquals(10, consumedKeys.size(), "被消费条目必须带 consumed_by");
        assertEquals("consumed_by", consumedKeys.get(consumedKeys.size() - 1));
        assertEquals(List.of("config_key", "config_group", "config_label", "config_type",
                        "current_value", "definition", "impact", "edition", "is_consumed"),
                keysOf(data.get(1)), "未消费条目不得凭空多出 consumed_by 键");
    }

    @Test
    @DisplayName("审计：汇总 4 键、条目 6 键（consumed_by/suggestion 互斥但键都在）")
    void auditKeepsKeys() {
        SysConfigQueryService service = mock(SysConfigQueryService.class);
        when(service.queryForAudit()).thenReturn(List.of(
                new SysConfigQueryService.ConfigAuditRow("max_login_attempts", "auth", "l", "d"),
                new SysConfigQueryService.ConfigAuditRow("unknown_key", "general", "l", "d")));
        SysConfigController controller = new SysConfigController(provider(service));

        SysConfigController.ConfigAuditResultVO data = controller.audit().getData();
        assertEquals(List.of("total", "consumed", "unconsumed", "items"), keysOf(data));
        assertEquals(2, data.total());
        assertEquals(1, data.consumed());
        assertEquals(1, data.unconsumed());

        List<String> itemKeys = List.of("config_key", "config_group", "config_label",
                "is_consumed", "consumed_by", "suggestion");
        assertEquals(itemKeys, keysOf(data.items().get(0)));
        assertEquals(itemKeys, keysOf(data.items().get(1)));
        assertFalse(data.items().get(1).consumed());
        assertTrue(data.items().get(1).suggestion().contains("未检测到代码消费"));
    }
}
