package com.chinacreator.gzcm.buszhi.workflow.controller;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.chinacreator.gzcm.buszhi.workflow.WorkflowApprovalService;
import com.chinacreator.gzcm.buszhi.workflow.dto.WorkflowAddSignResultVO;
import com.chinacreator.gzcm.buszhi.workflow.dto.WorkflowApprovalResultVO;
import com.chinacreator.gzcm.buszhi.workflow.dto.WorkflowTransferResultVO;
import com.chinacreator.gzcm.common.base.ApiResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 审批端点 wire 保真（DI-2 #147DQ）。
 *
 * <p>通过/驳回回执 {@code approvalId + decision}、转签 {@code taskId + decision + newAssignee}、
 * 加签 {@code taskId + status} 三种键面按顺序断言；请求体仍为自由 Map（意见/表单字段）。</p>
 */
class WorkflowApprovalWireShapeTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private WorkflowApprovalController controller;

    /** 只覆盖四个回执构造，仓储依赖不参与（构造传 null）。 */
    private static final class FakeApprovalService extends WorkflowApprovalService {
        private FakeApprovalService() {
            super(null, null, null, new ObjectMapper());
        }

        @Override
        public WorkflowApprovalResultVO approve(String taskId, Map<String, Object> body) {
            return new WorkflowApprovalResultVO("appr-4001", "Approved");
        }

        @Override
        public WorkflowApprovalResultVO reject(String taskId, Map<String, Object> body) {
            return new WorkflowApprovalResultVO("appr-4002", "Rejected");
        }

        @Override
        public WorkflowTransferResultVO transfer(String taskId, Map<String, Object> body) {
            return new WorkflowTransferResultVO(taskId, "Transferred", "u2");
        }

        @Override
        public WorkflowAddSignResultVO addSign(String taskId, Map<String, Object> body) {
            return new WorkflowAddSignResultVO(taskId, "AddSignRequested");
        }
    }

    @BeforeEach
    void setUp() {
        controller = new WorkflowApprovalController(new FakeApprovalService());
    }

    @Test
    @DisplayName("通过/驳回：approvalId + decision 两键")
    void approveAndRejectKeepTwoKeys() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("opinion", "同意");

        JsonNode approved = dataOf(controller.approve("task-3001", body));
        assertEquals(List.of("approvalId", "decision"), fieldNames(approved));
        assertEquals("Approved", approved.get("decision").asText());

        JsonNode rejected = dataOf(controller.reject("task-3001", body));
        assertEquals(List.of("approvalId", "decision"), fieldNames(rejected));
        assertEquals("Rejected", rejected.get("decision").asText());
    }

    @Test
    @DisplayName("转签：taskId + decision + newAssignee 三键")
    void transferKeepsThreeKeys() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("targetUserId", "u2");

        JsonNode data = dataOf(controller.transfer("task-3001", body));

        assertEquals(List.of("taskId", "decision", "newAssignee"), fieldNames(data));
        assertEquals("Transferred", data.get("decision").asText());
        assertEquals("u2", data.get("newAssignee").asText());
    }

    @Test
    @DisplayName("加签：taskId + status（恒 AddSignRequested）")
    void addSignKeepsTwoKeys() throws Exception {
        JsonNode data = dataOf(controller.addSign("task-3001", new LinkedHashMap<>()));

        assertEquals(List.of("taskId", "status"), fieldNames(data));
        assertEquals("AddSignRequested", data.get("status").asText());
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
