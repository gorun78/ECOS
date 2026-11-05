package com.chinacreator.gzcm.gateway.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.gateway.service.RuntimeAlertRepository;
import com.chinacreator.gzcm.gateway.service.RuntimeAlertRepository.AlertRow;
import com.chinacreator.gzcm.gateway.service.RuntimeAlertRepository.DlqRow;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@code /api/v1/monitor/alerts|dlq} 列表与回执 wire 键面门（DI-2 #147CW）。
 *
 * <p>列表行 12 键（V242 显式 SELECT 列）/ DLQ 行 8 键（V243）保持 snake_case 键名与 JDBC 原值
 * 类型；分页包裹 4 键；ack/重放回执 2 键。详情腿是 {@code SELECT *} 动态列，仍为 Map，
 * 不在本门的钉死范围（类注释已登记理由）。</p>
 */
class RuntimeAlertWireShapeTest {

    private static final Timestamp TS = Timestamp.valueOf("2026-11-05 09:30:00");

    private static final List<String> ALERT_KEYS = List.of(
            "id", "rule_code", "severity", "status", "title", "source_module", "trace_id",
            "ack_by", "ack_time", "close_time", "occurred_at", "tenant_id");

    private static final List<String> DLQ_KEYS = List.of(
            "id", "topic", "dlt_topic", "error_message", "attempts", "first_seen_at",
            "replayed_at", "status");

    /** 可控仓储：只覆盖列表/回执腿，详情腿沿用父类（测试不触库）。 */
    private static final class FakeRepo extends RuntimeAlertRepository {
        FakeRepo() {
            super(null);
        }

        @Override
        public List<AlertRow> listAlerts(String severity, String status, int size, int offset) {
            return List.of(new AlertRow("a-1", "R-1", "high", status, "磁盘告警", "runtime",
                    "tr-1", null, null, null, TS, "t1"));
        }

        @Override
        public int countAlerts(String severity, String status) {
            return 7;
        }

        @Override
        public List<DlqRow> listDlq(String status, int size, int offset) {
            return List.of(new DlqRow("d-1", "ecos.audit", "ecos.audit.DLT", "boom", 3,
                    TS, null, status));
        }

        @Override
        public int countDlq(String status) {
            return 2;
        }

        @Override
        public int ackAlert(String id, String by) {
            return 1;
        }

        @Override
        public int replayDlq(String id) {
            return 1;
        }
    }

    private static List<String> keys(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    private static JsonNode data(Object payload) {
        return new ObjectMapper().valueToTree(payload);
    }

    @Test
    @DisplayName("告警列表：分页 4 键 + 行 12 键（snake_case、时间列为 JDBC 原值）")
    void alertListShape() {
        JsonNode data = data(new RuntimeAlertV1Controller(new FakeRepo())
                .list(null, "open", 1, 20).getBody().getData());

        assertEquals(List.of("rows", "total", "page", "size"), keys(data));
        assertEquals(7, data.path("total").asInt());
        assertEquals(ALERT_KEYS, keys(data.path("rows").get(0)));
        assertEquals("high", data.path("rows").get(0).path("severity").asText());
        assertEquals(TS.getTime(), data.path("rows").get(0).path("occurred_at").asLong());
    }

    @Test
    @DisplayName("DLQ 列表：分页 4 键 + 行 8 键（attempts 为整数列）")
    void dlqListShape() {
        JsonNode data = data(new RuntimeAlertV1Controller(new FakeRepo())
                .dlq("pending", 1, 20).getBody().getData());

        assertEquals(List.of("rows", "total", "page", "size"), keys(data));
        assertEquals(2, data.path("total").asInt());
        assertEquals(DLQ_KEYS, keys(data.path("rows").get(0)));
        assertEquals(3, data.path("rows").get(0).path("attempts").asInt());
    }

    @Test
    @DisplayName("ack / 重放回执：各 2 键（id/status）")
    void receiptShapes() {
        RuntimeAlertV1Controller controller = new RuntimeAlertV1Controller(new FakeRepo());

        JsonNode ack = data(controller.ack("a-1").getData());
        assertEquals(List.of("id", "status"), keys(ack));
        assertEquals("acked", ack.path("status").asText());

        JsonNode replay = data(controller.replay("d-1").getData());
        assertEquals(List.of("id", "status"), keys(replay));
        assertEquals("replayed", replay.path("status").asText());
    }
}
