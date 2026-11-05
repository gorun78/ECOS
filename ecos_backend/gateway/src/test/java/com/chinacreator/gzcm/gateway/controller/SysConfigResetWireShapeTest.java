package com.chinacreator.gzcm.gateway.controller;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 配置重置回执 wire 保真（DI-2 #147EQ）。
 *
 * <p>四键固定：configKey / resetValue / previousValue / updated；
 * {@code previousValue} 为 null 时仍保留键（与旧 Map 逐字段同形）。</p>
 */
class SysConfigResetWireShapeTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    @DisplayName("四键固定且 previousValue=null 不被省略")
    void resetResultKeepsNullKey() throws Exception {
        JsonNode node = MAPPER.valueToTree(
                new SysConfigController.ResetResultVO("demo.key", "300", null, true));
        assertEquals(List.of("configKey", "resetValue", "previousValue", "updated"),
                fieldNames(node));
        assertTrue(node.get("previousValue").isNull());
        assertTrue(node.get("updated").asBoolean());
    }

    private static List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }
}
