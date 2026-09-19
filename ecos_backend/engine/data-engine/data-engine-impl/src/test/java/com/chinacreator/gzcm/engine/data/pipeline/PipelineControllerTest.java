package com.chinacreator.gzcm.engine.data.pipeline;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.exception.DataBridgeException;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PipelineControllerTest — Pipeline 定义 CRUD + 执行历史 强类型契约。
 *
 * <p>mock PipelineService / PipelineRepository / ITaskManagementService /
 * PipelineSecurityService，不连 PG。
 */
@ExtendWith(MockitoExtension.class)
class PipelineControllerTest {

    @Mock
    private PipelineService pipelineService;
    @Mock
    private PipelineRepository repository;
    @Mock
    private ITaskManagementService taskService;
    @Mock
    private PipelineSecurityService securityService;
    @Mock
    private PipelineNodeTypesService nodeTypesService;

    private PipelineController controller;

    @BeforeEach
    void setUp() {
        this.controller = new PipelineController(pipelineService, repository, taskService, securityService, nodeTypesService);
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
        when(pipelineService.createDefinition(any(PipelineSaveDTO.class))).thenReturn(created);
        when(pipelineService.toVO(created, true)).thenReturn(mockVO("p-1", "etl-pipeline", "DRAFT"));

        PipelineSaveDTO dto = new PipelineSaveDTO();
        dto.setName("etl-pipeline");
        ApiResponse<PipelineVO> resp = controller.createDefinition(dto);
        assertTrue(resp.isSuccess());
        assertNotNull(resp.getData());
        assertEquals("p-1", resp.getData().getId());
        assertEquals("etl-pipeline", resp.getData().getName());
        assertEquals("DRAFT", resp.getData().getStatus());
    }

    @Test
    @DisplayName("GET /api/v1/pipeline/definitions — 列表 200 且含 id")
    void listDefinitionsSuccess() {
        PipelineDefinition d = def("p-1", "a", "ACTIVE");
        when(pipelineService.listDefinitions()).thenReturn(List.of(d));
        when(pipelineService.toVO(d, false)).thenReturn(mockVO("p-1", "a", "ACTIVE"));
        ApiResponse<List<PipelineVO>> resp = controller.listDefinitions();
        assertTrue(resp.isSuccess());
        assertEquals(1, resp.getData().size());
        assertEquals("p-1", resp.getData().get(0).getId());
    }

    @Test
    @DisplayName("GET /definitions/{id} — 详情全量 nodes，供前端画布渲染")
    void getDefinitionSuccess() {
        PipelineDefinition d = def("p-1", "a", "ACTIVE");
        when(pipelineService.getDefinition("p-1")).thenReturn(d);
        when(pipelineService.toVO(d, true)).thenReturn(mockVO("p-1", "a", "ACTIVE"));
        ApiResponse<PipelineVO> resp = controller.getDefinition("p-1");
        assertTrue(resp.isSuccess());
        assertEquals("p-1", resp.getData().getId());
    }

    @Test
    @DisplayName("DELETE /definitions/{id} — 200 且 service 被调用")
    void deleteDefinitionSuccess() {
        ApiResponse<Void> resp = controller.deleteDefinition("p-1");
        assertTrue(resp.isSuccess());
        verify(pipelineService).deleteDefinition("p-1");
    }

    @Test
    @DisplayName("PUT /definitions/{id} — 更新成功 200 且 toVO(def, true) 全量 nodes")
    void updateDefinitionSuccess() {
        PipelineDefinition def = def("p-1", "renamed", "DRAFT");
        when(pipelineService.updateDefinition(org.mockito.ArgumentMatchers.eq("p-1"), any(PipelineSaveDTO.class)))
                .thenReturn(def);
        when(pipelineService.toVO(def, true)).thenReturn(mockVO("p-1", "renamed", "DRAFT"));

        PipelineSaveDTO dto = new PipelineSaveDTO();
        dto.setName("renamed");
        ApiResponse<PipelineVO> resp = controller.updateDefinition("p-1", dto);
        assertTrue(resp.isSuccess());
        assertEquals("p-1", resp.getData().getId());
        assertEquals("renamed", resp.getData().getName());
    }

    @Test
    @DisplayName("POST /definitions/{id}/execute — ABAC 放行（void no-op）+ mock runtime-task 全链路")
    void executeDefinitionSucceeds() throws Exception {
        // checkAbacBeforeExecute 为 void，Mock 默认 no-op（放行）
        when(taskService.submitTask(any(TaskDescription.class))).thenReturn("task-1");
        when(taskService.executeTask("task-1")).thenAnswer(inv -> null);
        when(taskService.getTaskStatus("task-1")).thenReturn(new TaskStatus());

        ApiResponse<PipelineTaskStatusVO> resp = controller.executeDefinition("p-1");
        assertTrue(resp.isSuccess());
        assertEquals("task-1", resp.getData().getTaskId());
    }

    @Test
    @DisplayName("GET /tasks/{taskId}/status — 200 含 taskId")
    void getTaskStatusSuccess() throws Exception {
        TaskStatus s = new TaskStatus();
        s.setTaskId("t-9");
        when(taskService.getTaskStatus("t-9")).thenReturn(s);
        ApiResponse<PipelineTaskStatusVO> resp = controller.getTaskStatus("t-9");
        assertTrue(resp.isSuccess());
        assertEquals("t-9", resp.getData().getTaskId());
    }

    @Test
    @DisplayName("GET /executions/{id} — 执行记录不存在 404")
    void getExecutionNotFound() {
        when(repository.findExecutionById("e-none")).thenReturn(null);
        assertThrows(DataBridgeException.class, () -> controller.getExecution("e-none"));
    }

    @Test
    @DisplayName("GET /definitions/{id}/executions — 分页返回 page/total/items")
    void listExecutionsSuccess() {
        when(pipelineService.getDefinition("p-1")).thenReturn(def("p-1", "a", "ACTIVE"));
        PipelineExecutionPageVO page = PipelineExecutionPageVO.empty(1, 20);
        when(pipelineService.listExecutions("p-1", 1, 20)).thenReturn(page);

        ApiResponse<PipelineExecutionPageVO> resp = controller.listExecutions("p-1", 1, 20);
        assertTrue(resp.isSuccess());
        assertEquals(1, resp.getData().getPage());
        assertEquals(0L, resp.getData().getTotal());
    }

    @Test
    @DisplayName("GET /definitions/{id}/executions — 定义 ARCHIVED → NotFoundException('已被删除')")
    void listExecutionsArchivedDefinition404() {
        when(pipelineService.getDefinition("p-arch")).thenReturn(def("p-arch", "a", "ARCHIVED"));

        org.junit.jupiter.api.Assertions.assertThrows(
                com.chinacreator.gzcm.common.exception.NotFoundException.class,
                () -> controller.listExecutions("p-arch", 1, 20));
    }

    @Test
    @DisplayName("NodeTypes 目录 — 强制枚举契约与 VALID_NODE_TYPES 同源（架构铁律 §4.8.2）")
    void nodeTypesCatalogSizeMatchesValidNodeTypes() {
        // 与 PipelineServiceImplTest.nodeTypesCatalogMatchesValidNodeTypes 互补：
        // 这里断言 PUBLIC 目录是 11 类（含 B6-1 新增 SOURCE_MINIO），关键子集包含前端基础 5 类 + PMO-36 新增 4 类
        assertEquals(11, PipelineNodeTypesCatalog.SUPPORTED.size());
        assertTrue(PipelineNodeTypesCatalog.SUPPORTED.containsAll(java.util.Set.of(
                "SOURCE_JDBC", "SOURCE_CSV", "SOURCE_REST", "TRANSFORM_SQL", "OUTPUT_OBJECT")));
    }

    private PipelineVO mockVO(String id, String name, String status) {
        PipelineVO vo = new PipelineVO();
        vo.setId(id);
        vo.setName(name);
        vo.setStatus(status);
        vo.setNodes(List.of());
        return vo;
    }
}
