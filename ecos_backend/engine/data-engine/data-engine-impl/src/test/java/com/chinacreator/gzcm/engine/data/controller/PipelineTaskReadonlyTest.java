package com.chinacreator.gzcm.engine.data.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.data.PipelineTaskService;

/**
 * F02-03（详细设计-02，R-2 a 已批准）业务管道单模型收敛验收：
 * YAML 任务模型 6 个写端点全部 409 {@code ECOS-DATA-011} 拒绝，
 * 6 个读端点保留只读且响应体打 {@code deprecated=true}。
 *
 * <p>本测试只判契约（不触库 / 不触 Spring 上下文）：</p>
 * <ul>
 *   <li>写端点 createTask/updateTask/deleteTask/triggerRun/cancelRun → HTTP 409</li>
 *   <li>写端点拒绝路径<b>不下沉</b>服务调用（never 写库）</li>
 *   <li>读端点 listTasks → 成功 + deprecated=true 标识</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class PipelineTaskReadonlyTest {

    @Mock
    private PipelineTaskService taskService;

    private PipelineTaskController controller;

    @BeforeEach
    void setUp() {
        this.controller = new PipelineTaskController(taskService);
    }

    // ─────────────────────────── 写端点：统一 409 ECOS-DATA-011 ──────────

    @Test
    @DisplayName("POST /tasks 创建 — 只读归档 409，拒绝正文指向 JSON DAG 模型，不下沉服务写")
    void createTask_rejects409() {
        ResponseEntity<ApiResponse<Void>> resp = controller.createTask(Map.of("name", "x"));

        assertEquals(HttpStatus.CONFLICT, resp.getStatusCode());
        assertNotNull(resp.getBody());
        assertEquals(409, resp.getBody().getCode());
        assertTrue(resp.getBody().getMessage().contains("pipeline/definitions"),
                "应引导到 /api/v1/pipeline/definitions; 实际 " + resp.getBody().getMessage());
        verify(taskService, never()).createTask(any());
    }

    @Test
    @DisplayName("PUT /tasks/{id} 更新 — 只读归档 409")
    void updateTask_rejects409() {
        ResponseEntity<ApiResponse<Void>> resp = controller.updateTask("t-1", Map.of("k", "v"));

        assertEquals(HttpStatus.CONFLICT, resp.getStatusCode());
        assertEquals(409, resp.getBody().getCode());
        verify(taskService, never()).updateTask(anyString(), any());
    }

    @Test
    @DisplayName("DELETE /tasks/{id} 删除 — 只读归档 409")
    void deleteTask_rejects409() {
        ResponseEntity<ApiResponse<Void>> resp = controller.deleteTask("t-1");

        assertEquals(HttpStatus.CONFLICT, resp.getStatusCode());
        assertEquals(409, resp.getBody().getCode());
        verify(taskService, never()).deleteTask(anyString());
    }

    @Test
    @DisplayName("POST /tasks/{id}/run 触发 — 只读归档 409")
    void triggerRun_rejects409() {
        ResponseEntity<ApiResponse<Void>> resp = controller.triggerRun("t-1", "manual");

        assertEquals(HttpStatus.CONFLICT, resp.getStatusCode());
        assertEquals(409, resp.getBody().getCode());
        verify(taskService, never()).triggerRun(anyString(), anyString());
    }

    @Test
    @DisplayName("POST /tasks/{id}/cancel 取消 — 只读归档 409")
    void cancelRun_rejects409() {
        ResponseEntity<ApiResponse<Void>> resp = controller.cancelRun("t-1", "run-1");

        assertEquals(HttpStatus.CONFLICT, resp.getStatusCode());
        assertEquals(409, resp.getBody().getCode());
        verify(taskService, never()).cancelRun(anyString());
    }

    // ─────────────────────────── 读端点：只读保留 + deprecated ──────────

    @Test
    @DisplayName("GET /tasks 列表 — 成功且响应体带 deprecated=true")
    void listTasks_marksDeprecated() {
        when(taskService.listTasks(eq(1), eq(20))).thenReturn(new LinkedHashMap<>(Map.of("total", 0)));

        ApiResponse<Map<String, Object>> resp = controller.listTasks(1, 20);

        assertTrue(resp.isSuccess(), "code=0");
        assertNotNull(resp.getData());
        assertEquals(Boolean.TRUE, resp.getData().get("deprecated"), "只读端点应打 deprecated=true");
    }

    @Test
    @DisplayName("markDeprecated — null 安全：null 入参返回 null，非 null 增 additive key 不改原 map")
    void markDeprecated_isNullSafeAndNonMutating() {
        assertNull(PipelineTaskController.markDeprecated(null));

        Map<String, Object> original = new LinkedHashMap<>();
        original.put("total", 3);
        Map<String, Object> marked = PipelineTaskController.markDeprecated(original);

        assertEquals(Boolean.TRUE, marked.get("deprecated"));
        assertFalse(original.containsKey("deprecated"), "不得改动传入的原 map");
    }

    @Test
    @DisplayName("读端点异常走 500（不吞错），执行历史只读保留")
    void getRuns_serviceErrorReturnsInternalError() {
        when(taskService.getRuns(anyString())).thenThrow(new RuntimeException("boom"));

        ApiResponse<List<Map<String, Object>>> resp = controller.getRuns("t-1");

        assertEquals(ApiResponse.CODE_INTERNAL_ERROR, resp.getCode());
    }
}
