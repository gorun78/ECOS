package com.chinacreator.gzcm.engine.data.pipeline;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.runtime.core.task.model.TaskDescription;
import com.chinacreator.gzcm.runtime.core.task.model.TaskStatus;
import com.chinacreator.gzcm.runtime.core.task.service.ITaskManagementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PipelineControllerTest — Pipeline 定义 CRUD 3 接口。
 *
 * <p>Wave-5.1 T-06：mock PipelineService / PipelineRepository /
 * ITaskManagementService，不连 PG。
 */
@ExtendWith(MockitoExtension.class)
class PipelineControllerTest {

    @Mock
    private PipelineService pipelineService;
    @Mock
    private PipelineRepository repository;
    @Mock
    private ITaskManagementService taskService;

    private PipelineController controller;

    @BeforeEach
    void setUp() {
        this.controller = new PipelineController(pipelineService, repository, taskService);
    }

    private PipelineDefinition def(String id, String name, String status) {
        PipelineDefinition d = new PipelineDefinition();
        d.setId(id);
        d.setName(name);
        d.setDescription("desc-" + id);
        d.setStatus(status);
        d.setCreatedAt(LocalDateTime.now());
        d.setUpdatedAt(LocalDateTime.now());
        return d;
    }

    @Test
    @DisplayName("POST /api/v1/pipeline/definitions — 创建成功 200 且返回 id/nodes")
    void createDefinitionSuccess() {
        PipelineDefinition created = def("p-1", "etl-pipeline", "DRAFT");
        when(pipelineService.createDefinition(any())).thenReturn(created);
        when(repository.findNodesByDefinitionId("p-1")).thenReturn(List.of());

        ApiResponse<Map<String, Object>> resp =
                controller.createDefinition(Map.of("name", "etl-pipeline"));
        assertTrue(resp.isSuccess());
        assertNotNull(resp.getData());
        assertEquals("p-1", resp.getData().get("id"));
        assertEquals("etl-pipeline", resp.getData().get("name"));
        assertEquals("DRAFT", resp.getData().get("status"));
    }

    @Test
    @DisplayName("GET /api/v1/pipeline/definitions — 列表 200 且含 id")
    void listDefinitionsSuccess() {
        when(pipelineService.listDefinitions())
                .thenReturn(List.of(def("p-1", "a", "ACTIVE")));
        ApiResponse<List<Map<String, Object>>> resp = controller.listDefinitions();
        assertTrue(resp.isSuccess());
        assertEquals(1, resp.getData().size());
        assertEquals("p-1", resp.getData().get(0).get("id"));
    }

    @Test
    @DisplayName("DELETE /api/v1/pipeline/definitions/{id} — 200 且 service 被调用")
    void deleteDefinitionSuccess() {
        ApiResponse<Map<String, Object>> resp = controller.deleteDefinition("p-1");
        assertTrue(resp.isSuccess());
        verify(pipelineService).deleteDefinition("p-1");
    }

    @Test
    @DisplayName("POST /definitions/{id}/execute — mock runtime-task 全链路")
    void executeDefinitionSucceeds() {
        try {
            when(taskService.submitTask(any(TaskDescription.class))).thenReturn("task-1");
            when(taskService.getTaskStatus("task-1")).thenReturn(new TaskStatus());
        } catch (com.chinacreator.gzcm.runtime.core.task.service.ITaskManagementService.TaskManagementException ignore) {
            throw new IllegalStateException("stub checked exception leaked unexpectedly", ignore);
        } catch (Exception e) {
            throw new IllegalStateException("stub setup failed", e);
        }

        ApiResponse<Map<String, Object>> resp = controller.executeDefinition("p-1");
        assertTrue(resp.isSuccess());
        assertEquals("task-1", resp.getData().get("taskId"));
    }

    @Test
    @DisplayName("GET /tasks/{taskId}/status — 200 含 taskId")
    void getTaskStatusSuccess() throws Exception {
        TaskStatus s = new TaskStatus();
        s.setTaskId("t-9");
        when(taskService.getTaskStatus("t-9")).thenReturn(s);
        ApiResponse<Map<String, Object>> resp = controller.getTaskStatus("t-9");
        assertTrue(resp.isSuccess());
        assertEquals("t-9", resp.getData().get("taskId"));
    }

    @Test
    @DisplayName("GET /executions/{id} — 执行记录不存在 404")
    void getExecutionNotFound() {
        when(repository.findExecutionById("e-none")).thenReturn(null);
        ApiResponse<PipelineExecution> resp = controller.getExecution("e-none");
        assertEquals(ApiResponse.CODE_NOT_FOUND, resp.getCode());
        assertNotNull(resp.getMessage());
    }
}
