package com.chinacreator.gzcm.buszhi.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * D155 回归门：{@code WorkflowEngine.execute(wf, input, instanceId)} 必须把真实 instanceId
 * 透传到 {@code userTask} 节点的 {@code taskService.createTask(...)}。
 *
 * <p>缺陷（2026-11-16 活体发现）：原 {@code execute(wf, input)} 双参版一路走
 * {@code executeNode(node, ctx)} 双参重载（instanceId=null），于是 userTask 建出的任务
 * 挂在占位实例 {@code test-inst} 上，真实实例 {@code pi*} 与任务脱钩——任务分派看似成功，
 * 实则在实例维度查不到。</p>
 */
class WorkflowEngineInstanceIdGateTest {

    private final WorkflowTaskService taskService = mock(WorkflowTaskService.class);
    private final WorkflowLogRepository logRepo = mock(WorkflowLogRepository.class);
    private final ExpressionService expr = mock(ExpressionService.class);

    private WorkflowEngine engine() {
        when(expr.resolvePlaceholders(anyString(), any())).thenAnswer(inv -> inv.getArgument(0));
        return new WorkflowEngine(new ObjectMapper(), expr, taskService, logRepo, null);
    }

    @Test
    @DisplayName("userTask 节点建任务时 instanceId 必须是调用方传入的真实实例 id")
    void userTaskCreatesTaskWithRealInstanceId() {
        WorkflowTaskEntity task = new WorkflowTaskEntity();
        task.setId("task-probe");
        when(taskService.createTask(anyString(), anyString(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(task);

        WorkflowEntity wf = new WorkflowEntity();
        wf.setId("wf-probe");
        wf.setName("D155-probe");
        wf.setMode("sequential");
        wf.setNodes("[{\"id\":\"n1\",\"type\":\"start\",\"label\":\"开始\"},"
                + "{\"id\":\"n2\",\"type\":\"userTask\",\"label\":\"审批\",\"config\":{\"assignee\":\"admin\"}},"
                + "{\"id\":\"n3\",\"type\":\"end\",\"label\":\"结束\"}]");
        wf.setEdges("[{\"source\":\"n1\",\"target\":\"n2\"},{\"source\":\"n2\",\"target\":\"n3\"}]");

        engine().execute(wf, java.util.Map.of(), "pi-real-001");

        verify(taskService).createTask(
                eq("pi-real-001"), eq("n2"), any(), any(), any(),
                any(), any(), any(), any(), any());
    }
}
