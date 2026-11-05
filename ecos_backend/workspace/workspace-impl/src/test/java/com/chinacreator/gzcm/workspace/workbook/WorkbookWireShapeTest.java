package com.chinacreator.gzcm.workspace.workbook;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.chinacreator.gzcm.workspace.dto.WorkbookEnvStatusVO;
import com.chinacreator.gzcm.workspace.dto.WorkbookExecutionRecordVO;
import com.chinacreator.gzcm.workspace.dto.WorkbookHealthVO;
import com.chinacreator.gzcm.workspace.dto.WorkbookSessionVO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Workbook 端点 wire 保真（DI-2 #147EE）。
 *
 * <p>历史记录 6 键（result 为引擎原样结构）；会话摘要 2 键；环境状态 available[/message]；
 * 健康检查 sql/python/r 三段。</p>
 */
class WorkbookWireShapeTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    @DisplayName("历史记录：6 键且 result 原样透传；会话摘要 2 键")
    void recordAndSessionKeepKeys() throws Exception {
        Map<String, Object> engineResult = new LinkedHashMap<>();
        engineResult.put("columns", List.of("a"));
        engineResult.put("rows", List.of(Map.of("a", 1)));

        JsonNode record = MAPPER.valueToTree(new WorkbookExecutionRecordVO(
                "aaaa1111", "sql", "select 1", "sess-1", "2026-11-05T10:00", engineResult));

        assertEquals(List.of("id", "language", "code", "sessionId", "timestamp", "result"),
                fieldNames(record));
        assertEquals(1, record.get("result").get("rows").get(0).get("a").asInt());

        JsonNode session = MAPPER.valueToTree(new WorkbookSessionVO("sess-1", 3));
        assertEquals(List.of("sessionId", "executionCount"), fieldNames(session));
        assertEquals(3, session.get("executionCount").asInt());
    }

    @Test
    @DisplayName("健康检查：sql/python/r 三段；环境状态 message 仅异常时出现")
    void healthKeepsConditionalKeys() throws Exception {
        JsonNode healthy = MAPPER.valueToTree(new WorkbookHealthVO(
                new WorkbookEnvStatusVO(true, null),
                new WorkbookEnvStatusVO(true, null),
                Map.of("available", false)));

        assertEquals(List.of("sql", "python", "r"), fieldNames(healthy));
        assertEquals(List.of("available"), fieldNames(healthy.get("sql")));
        assertFalse(healthy.get("python").has("message"));
        assertTrue(!healthy.get("r").get("available").asBoolean());

        JsonNode withMessage = MAPPER.valueToTree(new WorkbookEnvStatusVO(false, "python3 not found"));
        assertEquals(List.of("available", "message"), fieldNames(withMessage));
    }

    private static List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }
}
