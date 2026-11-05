package com.chinacreator.gzcm.gateway.controller;

import java.util.ArrayList;
import java.util.List;

import com.chinacreator.gzcm.gateway.controller.SecurityController.AuditLogVO;
import com.chinacreator.gzcm.gateway.controller.SecurityController.DecryptResultVO;
import com.chinacreator.gzcm.gateway.controller.SecurityController.DiagnosticReportVO;
import com.chinacreator.gzcm.gateway.controller.SecurityController.MaskResultVO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * 安全中心端点 wire 保真（DI-2 #147EV）。
 *
 * <p>遮蔽成功 3 键（下划线键名）/ 失败省略 {@code original_length}；解密 4 键；
 * 审计行 5 键；诊断报告 1 键。</p>
 */
class SecurityWireShapeTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    @DisplayName("遮蔽两腿：成功 3 键、失败省略 original_length")
    void maskKeepsConditionalKey() throws Exception {
        JsonNode ok = MAPPER.valueToTree(new MaskResultVO("abc...", "engine", 12));
        assertEquals(List.of("masked_value", "engine", "original_length"), fieldNames(ok));
        assertEquals(12, ok.get("original_length").asInt());

        JsonNode failed = MAPPER.valueToTree(new MaskResultVO("ERROR", "Java-fallback-failed", null));
        assertEquals(List.of("masked_value", "engine"), fieldNames(failed));
        assertFalse(failed.has("original_length"));
    }

    @Test
    @DisplayName("解密 4 键；审计行 5 键；诊断报告 1 键")
    void decryptAuditDiagnosticKeepKeys() throws Exception {
        JsonNode decrypt = MAPPER.valueToTree(new DecryptResultVO(true, "GRANTED",
                List.of("[1]"), "gateway"));
        assertEquals(List.of("success", "verdict", "traces", "engine"), fieldNames(decrypt));

        JsonNode log = MAPPER.valueToTree(new AuditLogVO("seed-0", "2026-11-05T00:00:00Z",
                "LOGIN", "SUCCESS", "detail"));
        assertEquals(List.of("id", "timestamp", "event", "status", "detail"), fieldNames(log));

        JsonNode report = MAPPER.valueToTree(new DiagnosticReportVO("report"));
        assertEquals(List.of("diagnosticReport"), fieldNames(report));
    }

    private static List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }
}
