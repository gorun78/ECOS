package com.chinacreator.gzcm.gateway.controller;

import java.util.ArrayList;
import java.util.List;

import com.chinacreator.gzcm.gateway.controller.EngineTaskController.EngineTaskDetailVO;
import com.chinacreator.gzcm.gateway.controller.EngineTaskController.EngineTaskRowVO;
import com.chinacreator.gzcm.gateway.controller.EngineTaskController.EngineTaskSubmitVO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 引擎任务面 wire 保真（DI-2 #147ET）。
 *
 * <p>提交回执 3 键、任务详情 8 键、汇聚行 5 键（取状态失败时 status=UNKNOWN/progress=0
 * 的分支仍出同键面）。</p>
 */
class EngineTaskWireShapeTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    @DisplayName("提交回执 3 键；详情 8 键（progress/时间原样透传）")
    void submitAndDetailKeepKeys() throws Exception {
        JsonNode submit = MAPPER.valueToTree(new EngineTaskSubmitVO("task-1", "data", "SUBMITTED"));
        assertEquals(List.of("taskId", "engine", "status"), fieldNames(submit));

        JsonNode detail = MAPPER.valueToTree(new EngineTaskDetailVO("task-1", "n", "t", "data",
                "RUNNING", 42, null, null));
        assertEquals(List.of("taskId", "taskName", "taskType", "engine", "status", "progress",
                "startedAt", "completedAt"), fieldNames(detail));
        assertEquals(42, detail.get("progress").asInt());
        assertTrue(detail.get("startedAt").isNull());
    }

    @Test
    @DisplayName("汇聚行 5 键；状态取值失败腿同为 UNKNOWN/0")
    void aggregateRowKeepsKeysAndFallback() throws Exception {
        JsonNode ok = MAPPER.valueToTree(new EngineTaskRowVO("task-1", "n", "t", "RUNNING", 7));
        assertEquals(List.of("taskId", "taskName", "taskType", "status", "progress"),
                fieldNames(ok));

        JsonNode fallback = MAPPER.valueToTree(new EngineTaskRowVO("task-2", "n", "t", "UNKNOWN", 0));
        assertEquals("UNKNOWN", fallback.get("status").asText());
        assertEquals(0, fallback.get("progress").asInt());
    }

    private static List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }
}
