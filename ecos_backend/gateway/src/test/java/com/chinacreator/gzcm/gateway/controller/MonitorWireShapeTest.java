package com.chinacreator.gzcm.gateway.controller;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.gateway.service.MonitorService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 监控端点 wire 保真（DI-2 #147DO）。
 *
 * <p>仪表盘 7 键、系统资源 4 键、时间序列 3 键、进程 5 键、健康 4 键（数据库探测异常时
 * {@code database.error} 才出现）、告警统计 3 键全部按顺序断言；告警行夹具走
 * {@code MonitorService} 的属主 Map 行（动态列不冻结）。</p>
 */
class MonitorWireShapeTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final class FakeMonitorService extends MonitorService {
        private boolean dbUp = true;
        private boolean dbThrows = false;

        private FakeMonitorService() {
            super(null);
        }

        @Override
        public Integer countActiveAlerts() {
            return 4;
        }

        @Override
        public Integer countAlertsToday() {
            return 7;
        }

        @Override
        public List<Map<String, Object>> queryRecentAlerts() {
            Map<String, Object> alert = new LinkedHashMap<>();
            alert.put("id", "alert-1");
            alert.put("severity", "HIGH");
            return List.of(alert);
        }

        @Override
        public Integer countOpenDqIssues() {
            return 2;
        }

        @Override
        public boolean isDbUp() {
            if (dbThrows) {
                throw new IllegalStateException("connection refused");
            }
            return dbUp;
        }

        @Override
        public List<Map<String, Object>> queryAlertsTodayByLevel() {
            return List.of(Map.of("level", "HIGH", "count", 3));
        }

        @Override
        public Integer countTotalAlerts() {
            return 9;
        }
    }

    @Test
    @DisplayName("dashboard：7 键 + system 4 键 + chart 12 点 3 键 + processes 5 键")
    void dashboardKeepsShape() throws Exception {
        MonitorController controller = new MonitorController(new FakeMonitorService());

        JsonNode data = dataOf(controller.dashboard());

        assertEquals(List.of("system", "active_alerts", "alerts_today", "recent_alerts",
                "open_dq_issues", "chartData", "processes"), fieldNames(data));
        assertEquals(List.of("cpu_cores", "cpu_load", "heap_used_mb", "heap_max_mb"),
                fieldNames(data.get("system")));
        assertEquals(4, data.get("active_alerts").asInt());
        assertEquals(7, data.get("alerts_today").asInt());
        assertEquals(2, data.get("open_dq_issues").asInt());
        assertEquals("alert-1", data.get("recent_alerts").get(0).get("id").asText());

        JsonNode chart = data.get("chartData");
        assertEquals(12, chart.size());
        assertEquals(List.of("time", "cpu", "memory"), fieldNames(chart.get(0)));

        JsonNode processes = data.get("processes");
        assertTrue(processes.size() >= 1, "至少一条进程行（fallback 兜底）");
        assertEquals(List.of("name", "type", "uptime", "items", "status"), fieldNames(processes.get(0)));
    }

    @Test
    @DisplayName("health：4 键；探测异常时 database 携带 error（NON_NULL），同一腿下有 error 键")
    void healthKeepsErrorOnlyWhenProbeThrows() throws Exception {
        FakeMonitorService service = new FakeMonitorService();
        MonitorController controller = new MonitorController(service);

        JsonNode up = dataOf(controller.health());
        assertEquals(List.of("database", "uptime_ms", "version", "status"), fieldNames(up));
        assertEquals("UP", up.get("status").asText());
        assertEquals(List.of("status"), fieldNames(up.get("database")));
        assertFalse(up.get("database").has("error"), "正常探测不带 error");

        service.dbUp = false;
        JsonNode down = dataOf(controller.health());
        assertEquals("DEGRADED", down.get("status").asText());
        assertEquals("DOWN", down.get("database").get("status").asText());

        service.dbThrows = true;
        JsonNode degraded = dataOf(controller.health());
        assertEquals(List.of("status", "error"), fieldNames(degraded.get("database")));
        assertEquals("connection refused", degraded.get("database").get("error").asText());
    }

    @Test
    @DisplayName("alerts：today_by_level + total + open（null 归零）")
    void alertStatsKeepsCounts() throws Exception {
        MonitorController controller = new MonitorController(new FakeMonitorService());

        JsonNode data = dataOf(controller.alertStats());

        assertEquals(List.of("today_by_level", "total", "open"), fieldNames(data));
        assertEquals(9, data.get("total").asInt());
        assertEquals(4, data.get("open").asInt());
        assertEquals("HIGH", data.get("today_by_level").get(0).get("level").asText());
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
