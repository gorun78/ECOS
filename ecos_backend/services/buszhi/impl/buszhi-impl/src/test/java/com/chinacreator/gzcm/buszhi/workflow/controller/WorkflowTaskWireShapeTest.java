package com.chinacreator.gzcm.buszhi.workflow.controller;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.chinacreator.gzcm.buszhi.workflow.WorkflowTaskService;
import com.chinacreator.gzcm.buszhi.workflow.dto.WorkflowTaskStatisticsVO;
import com.chinacreator.gzcm.buszhi.workflow.dto.WorkflowTaskVO;
import com.chinacreator.gzcm.common.base.ApiResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 任务中心端点 wire 保真（DI-2 #147DP）。
 *
 * <p>任务行 11 键（id/instanceId/nodeId/taskType/title/assignee/status/priority/dueDate/
 * completedAt/createdAt）在列表/详情/签收/完成/转签/驳回六腿同形，空值键保留；
 * 列表信封 {@code data + total}、统计 4 键按顺序断言。</p>
 */
class WorkflowTaskWireShapeTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final List<String> TASK_KEYS = List.of("id", "instanceId", "nodeId", "taskType",
            "title", "assignee", "status", "priority", "dueDate", "completedAt", "createdAt");

    private WorkflowTaskController controller;

    /** 只覆盖读/流转方法，仓储依赖不参与（构造传 null）。 */
    private static final class FakeTaskService extends WorkflowTaskService {
        private FakeTaskService() {
            super(null, null, new ObjectMapper());
        }

        private static WorkflowTaskVO task() {
            return new WorkflowTaskVO("task-3001", "inst-1", "node-1", "userTask", "审批采购单",
                    null, "New", "NORMAL", null, null, "2026-11-05T10:00");
        }

        @Override
        public List<WorkflowTaskVO> listTasks(String assignee, int limit) {
            return List.of(task());
        }

        @Override
        public Optional<WorkflowTaskVO> getTask(String taskId) {
            return Optional.of(task());
        }

        @Override
        public Optional<WorkflowTaskVO> claimTask(String taskId, String userId) {
            return Optional.of(task());
        }

        @Override
        public Optional<WorkflowTaskVO> completeTask(String taskId, Map<String, Object> body) {
            return Optional.of(task());
        }

        @Override
        public Optional<WorkflowTaskVO> transferTask(String taskId, String newAssignee) {
            return Optional.of(task());
        }

        @Override
        public Optional<WorkflowTaskVO> rejectTask(String taskId, String reason) {
            return Optional.of(task());
        }

        @Override
        public WorkflowTaskStatisticsVO getStatistics(String userId) {
            return new WorkflowTaskStatisticsVO(0, 0, 0, 0);
        }
    }

    @BeforeEach
    void setUp() {
        controller = new WorkflowTaskController(new FakeTaskService());
    }

    @Test
    @DisplayName("列表：{data,total} + 任务 11 键（可空键保留，assignee/dueDate 为 null 也在）")
    void listKeepsTaskAndEnvelopeKeys() throws Exception {
        JsonNode data = dataOf(controller.listTasks(null, 50));

        assertEquals(List.of("data", "total"), fieldNames(data));
        assertEquals(1, data.get("total").asInt());
        JsonNode task = data.get("data").get(0);
        assertEquals(TASK_KEYS, fieldNames(task));
        assertTrue(task.has("assignee") && task.get("assignee").isNull(),
                "空值键必须保留（旧 Map 同形）");
        assertTrue(task.has("dueDate") && task.get("dueDate").isNull());
    }

    @Test
    @DisplayName("详情/签收/完成/转签/驳回：同一记录 11 键")
    void taskMutationLegsKeepKeys() throws Exception {
        assertEquals(TASK_KEYS, fieldNames(dataOf(controller.getTask("task-3001"))));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("userId", "u1");
        assertEquals(TASK_KEYS, fieldNames(dataOf(controller.claimTask("task-3001", body))));
        assertEquals(TASK_KEYS, fieldNames(dataOf(controller.completeTask("task-3001", body))));

        Map<String, Object> transfer = new LinkedHashMap<>();
        transfer.put("targetUserId", "u2");
        assertEquals(TASK_KEYS, fieldNames(dataOf(controller.transferTask("task-3001", transfer))));
        assertEquals(TASK_KEYS, fieldNames(dataOf(controller.rejectTask("task-3001", body))));
    }

    @Test
    @DisplayName("统计：total/pending/inProgress/completed 四键")
    void statisticsKeepsKeys() throws Exception {
        JsonNode data = dataOf(controller.getStatistics("u1"));
        assertEquals(List.of("total", "pending", "inProgress", "completed"), fieldNames(data));
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
