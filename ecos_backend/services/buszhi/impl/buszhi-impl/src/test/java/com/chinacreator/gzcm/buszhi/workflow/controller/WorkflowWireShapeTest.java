package com.chinacreator.gzcm.buszhi.workflow.controller;

import com.chinacreator.gzcm.buszhi.workflow.WorkflowInstanceService;
import com.chinacreator.gzcm.buszhi.workflow.WorkflowInstanceService.WorkflowDefinitionRef;
import com.chinacreator.gzcm.buszhi.workflow.WorkflowInstanceService.WorkflowInstanceRow;
import com.chinacreator.gzcm.buszhi.workflow.WorkflowService;
import com.chinacreator.gzcm.buszhi.workflow.WorkflowService.WorkflowExportRow;
import com.chinacreator.gzcm.buszhi.workflow.WorkflowService.WorkflowPreviewRow;
import com.chinacreator.gzcm.buszhi.workflow.WorkflowService.WorkflowRow;
import com.chinacreator.gzcm.buszhi.workflow.WorkflowService.WorkflowValidationRow;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 工作流双宿主 wire 键面门（DI-2 #147CZ）。
 *
 * <p>属主 {@code WorkflowService}/{@code WorkflowInstanceService} 行面改 record 后，
 * 本门按**键集相等 + 键序**钉住控制器外发面：列表 2 键 + 定义行 10 键、导出 11 键
 * （{@code $schema} 首键）、校验 4 键、预览 5 键、实例 10/11 键（{@code definition} 仅启动腿）。</p>
 */
class WorkflowWireShapeTest {

    private static final List<String> WORKFLOW_KEYS = List.of(
            "id", "name", "description", "status", "mode", "nodes", "edges",
            "publishedAt", "createdAt", "updatedAt");

    private static final List<String> INSTANCE_KEYS = List.of(
            "id", "workflowId", "workflowName", "status", "triggerType", "triggeredBy",
            "startedAt", "completedAt", "errorMessage", "createdAt");

    private WorkflowService workflowService;
    private WorkflowInstanceService instanceService;
    private WorkflowController controller;

    @BeforeEach
    void setUp() {
        workflowService = mock(WorkflowService.class);
        instanceService = mock(WorkflowInstanceService.class);
        controller = new WorkflowController(workflowService, instanceService);
    }

    private static WorkflowRow row() {
        return new WorkflowRow("wf201", "审批流", "描述", "draft", "sequential",
                "[{\"id\":\"n1\",\"type\":\"start\"}]", "[]", "2026-11-05T09:20",
                "2026-11-05T09:00", "2026-11-05T09:10");
    }

    private static WorkflowInstanceRow instance(WorkflowDefinitionRef definition) {
        return new WorkflowInstanceRow("pi1001", "wf201", "审批流", "Running", "MANUAL", "system",
                "2026-11-05T09:00", "2026-11-05T09:30", "boom", "2026-11-05T09:00", definition);
    }

    private static List<String> keys(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    private static JsonNode json(Object payload) {
        return new ObjectMapper().valueToTree(payload);
    }

    @Test
    @DisplayName("列表：包裹 2 键（data/total），定义行 10 键")
    void listShape() {
        when(workflowService.listWorkflows(50)).thenReturn(List.of(row()));
        when(workflowService.totalCount()).thenReturn(1L);

        JsonNode data = json(controller.listWorkflows(50).getData());

        assertEquals(List.of("data", "total"), keys(data));
        assertEquals(WORKFLOW_KEYS, keys(data.path("data").get(0)));
        assertEquals(1, data.path("total").asInt());
    }

    @Test
    @DisplayName("详情：定义行 10 键，nodes/edges 保持原样 JSON 文本")
    void getShape() {
        when(workflowService.getWorkflow("wf201")).thenReturn(Optional.of(row()));

        JsonNode data = json(controller.getWorkflow("wf201").getData());

        assertEquals(WORKFLOW_KEYS, keys(data));
        assertEquals("[{\"id\":\"n1\",\"type\":\"start\"}]", data.path("nodes").asText());
    }

    @Test
    @DisplayName("导出：11 键，$schema 为首键且不落 extras 残键")
    void exportShape() {
        when(workflowService.exportWorkflow("wf201")).thenReturn(Optional.of(new WorkflowExportRow(
                "https://ecos.nousresearch.com/schemas/workflow-definition-v1.json",
                "wf201", "审批流", "描述", "1.0.0", "sequential", "draft",
                List.of(Map.of("id", "n1")), List.of(),
                "2026-11-05T09:00", "2026-11-05T09:10")));

        JsonNode data = json(controller.exportWorkflow("wf201").getData());

        assertEquals(List.of("id", "name", "description", "version", "mode", "status",
                "nodes", "edges", "createdAt", "updatedAt", "$schema"), keys(data));
    }

    @Test
    @DisplayName("校验：4 键（valid/errors/warnings/suggestions）")
    void validateShape() {
        Map<String, String> issue = new LinkedHashMap<>();
        issue.put("code", "WF-E1");
        issue.put("nodeId", "n1");
        issue.put("message", "缺 start 节点");
        when(workflowService.validateWorkflow(org.mockito.ArgumentMatchers.anyMap()))
                .thenReturn(new WorkflowValidationRow(false, List.of(issue), List.of(), List.of()));

        
        
        com.chinacreator.gzcm.buszhi.workflow.dto.WorkflowValidationSaveDTO dto =
                new com.chinacreator.gzcm.buszhi.workflow.dto.WorkflowValidationSaveDTO();
        dto.set("nodes", List.of());

        JsonNode data = json(controller.validateWorkflow(dto).getData());

        assertEquals(List.of("valid", "errors", "warnings", "suggestions"), keys(data));
        assertEquals(false, data.path("valid").asBoolean());
        assertEquals("WF-E1", data.path("errors").get(0).path("code").asText());
    }

    @Test
    @DisplayName("预览：5 键（workflowId/name/expandedSteps/estimatedPath/context）")
    void previewShape() {
        when(workflowService.previewWorkflow(org.mockito.ArgumentMatchers.eq("wf201"),
                org.mockito.ArgumentMatchers.anyMap())).thenReturn(Optional.of(
                new WorkflowPreviewRow("wf201", "审批流", List.of(Map.of("step", 1)), "completed",
                        Map.of("k", "v"))));

        JsonNode data = json(controller.previewWorkflow("wf201", Map.of("k", "v")).getData());

        assertEquals(List.of("workflowId", "name", "expandedSteps", "estimatedPath", "context"),
                keys(data));
        assertEquals("completed", data.path("estimatedPath").asText());
    }

    @Test
    @DisplayName("实例：启动腿 11 键（definition 尾键），列表腿 10 键（无 definition）")
    void instanceShapes() {
        when(instanceService.startInstance(org.mockito.ArgumentMatchers.eq("wf201"),
                org.mockito.ArgumentMatchers.anyMap())).thenReturn(Optional.of(
                instance(new WorkflowDefinitionRef("wf201", "审批流", "draft"))));
        when(instanceService.listInstances(50)).thenReturn(List.of(instance(null)));

        com.chinacreator.gzcm.buszhi.workflow.dto.WorkflowInstanceSaveDTO startBody =
                new com.chinacreator.gzcm.buszhi.workflow.dto.WorkflowInstanceSaveDTO();
        startBody.setWorkflowId("wf201");
        JsonNode started = json(controller.startWorkflow("wf201", startBody).getData());
        List<String> expected = new ArrayList<>(INSTANCE_KEYS);
        expected.add("definition");
        assertEquals(expected, keys(started));
        assertEquals(List.of("id", "name", "status"), keys(started.path("definition")));

        JsonNode listed = json(controller.listInstances(50).getData());
        assertEquals(INSTANCE_KEYS, keys(listed.path("data").get(0)));
    }
}
