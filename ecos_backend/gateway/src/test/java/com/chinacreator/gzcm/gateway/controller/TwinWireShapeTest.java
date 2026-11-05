package com.chinacreator.gzcm.gateway.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.chinacreator.gzcm.gateway.controller.TwinController.TwinAggregateVO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 数字孪生聚合视图 wire 保真（DI-2 #147EW）。
 *
 * <p>5 键固定：mqtt_up / total_devices / online_devices / offline_devices / devices；
 * 计数为原始 long/int（旧 Map 同为数值），设备行为服务原样结构。</p>
 */
class TwinWireShapeTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    @DisplayName("聚合视图 5 键 + 计数类型不变")
    void aggregateKeepsKeys() throws Exception {
        JsonNode node = MAPPER.valueToTree(new TwinAggregateVO(true, 3, 2L, 1L,
                List.of(Map.of("id", "dev-1", "status", "online"))));

        assertEquals(List.of("mqtt_up", "total_devices", "online_devices", "offline_devices",
                "devices"), fieldNames(node));
        assertEquals(3, node.get("total_devices").asInt());
        assertEquals(2, node.get("online_devices").asInt());
        assertEquals(1, node.get("offline_devices").asInt());
        assertEquals("dev-1", node.get("devices").get(0).get("id").asText());
    }

    private static List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }
}
