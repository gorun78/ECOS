package com.chinacreator.gzcm.buszhi.workflow.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.base.MachineErrorCode;
import com.chinacreator.gzcm.common.error.WorkflowErrorCode;
import com.chinacreator.gzcm.common.exception.DataBridgeException;
import com.chinacreator.gzcm.common.exception.NotFoundException;
import com.chinacreator.gzcm.buszhi.workflow.WorkflowApprovalEntity;
import com.chinacreator.gzcm.buszhi.workflow.WorkflowApprovalRepository;
import com.chinacreator.gzcm.buszhi.workflow.WorkflowApprovalService;
import com.chinacreator.gzcm.buszhi.workflow.WorkflowInstanceEntity;
import com.chinacreator.gzcm.buszhi.workflow.WorkflowInstanceRepository;
import com.chinacreator.gzcm.buszhi.workflow.WorkflowTaskEntity;
import com.chinacreator.gzcm.buszhi.workflow.WorkflowTaskRepository;

/**
 * W85／附录A 第 10 条契约面守卫（#146y）——
 * {@code POST /api/v1/ontology/workflow-instances/{id}/approvals}。
 *
 * <p>覆盖四类必然性：①契约字面（类级前缀 × 方法级路径拼接，禁双前缀／禁漂移）；
 * ②「服务端解析审批人」＝客户端自报身份一律丢弃；③「审批链断裂绝不折叠成空列表」
 * ＝数据访问故障与读回空行都落 500 携 {@code ECOS-WF-020}（机读码必须在串首，
 * 否则 {@link MachineErrorCode#fromMessage} 取不到＝信封 errorCode 回落数字串）；
 * ④缺实例／无可审批任务／未指派审批人 = 404 fail-loud，禁 200 空面。</p>
 *
 * <p>纯单元档：不拉 Spring 上下文、不连库（真栈可达性属 live 批，本类不冒充）。</p>
 */
@DisplayName("附录A 第 10 条：实例级审批契约面（路径字面／服务端解析审批人／WF-020 状态行）")
class WorkflowInstanceApprovalContractTest {

    private final WorkflowInstanceRepository instanceRepo = mock(WorkflowInstanceRepository.class);
    private final WorkflowTaskRepository taskRepo = mock(WorkflowTaskRepository.class);
    private final WorkflowApprovalRepository approvalRepo = mock(WorkflowApprovalRepository.class);
    private final WorkflowApprovalService approvalService = mock(WorkflowApprovalService.class);

    private final WorkflowInstanceApprovalController controller =
            new WorkflowInstanceApprovalController(instanceRepo, taskRepo, approvalRepo, approvalService);

    // ── ① 契约字面 ────────────────────────────────────

    @Test
    @DisplayName("类级前缀 × 方法级模板拼接逐字等于契约路径（禁双前缀／禁漂移）")
    void contractPathLiteralIsExact() {
        RequestMapping classLevel = WorkflowInstanceApprovalController.class
                .getAnnotation(RequestMapping.class);
        assertTrue(classLevel != null && classLevel.value().length == 1,
                "类级必须恰有一枚 @RequestMapping 前缀（多前缀会让本面在多个根上同时承流，"
                        + "契约对账失去唯一锚点）");

        PostMapping methodLevel = java.util.Arrays.stream(
                        WorkflowInstanceApprovalController.class.getDeclaredMethods())
                .filter(m -> m.isAnnotationPresent(PostMapping.class))
                .map(m -> m.getAnnotation(PostMapping.class))
                .findFirst()
                .orElseThrow(() -> new AssertionError("本类必须至少有一条 @PostMapping 腿"));

        String prefix = first(classLevel.value());
        String template = first(methodLevel.value());
        String joined = prefix + template;

        assertFalse(prefix.endsWith("/"), "类级前缀不得以斜杠结尾（拼接口径＝前缀 + 以斜杠起始的方法级模板）");
        assertTrue(template.startsWith("/") && !template.startsWith("//"),
                "方法级必须是斜杠起始的相对模板，绝对路径会拼成双前缀");
        assertEquals("/api/v1/ontology/workflow-instances/{instanceId}/approvals", joined,
                "契约路径字面漂移（详设 line 4295 = /api/v1/ontology/workflow-instances/{id}/approvals；"
                        + "槽位名 instanceId 与文档 id 同一含义，其余字面须逐字一致）");
        assertFalse(joined.contains("//"), "拼接出双斜杠＝方法级写了绝对路径，运行时必然 404");
        assertTrue(joined.startsWith("/api/v1/ontology/"), "本面属 ontology 契约前缀，须由该前缀承流");
    }

    // ── ② 服务端解析审批人 ────────────────────────────

    @Test
    @DisplayName("客户端自报 userId/approver 被丢弃，属主服务收到的是净化后的体")
    void clientSuppliedActorIsDropped() {
        givenInstance("inst-1");
        givenTasks(task("task-1", "Assigned", "zhangsan"));
        when(approvalService.approve(eq("task-1"), any())).thenReturn(Map.of("approvalId", "appr-1"));
        when(approvalRepo.findById("appr-1")).thenReturn(Optional.of(row("appr-1", "task-1", "zhangsan")));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("userId", "hacker");
        body.put("approver", "hacker");
        body.put("opinion", "同意");

        controller.approveWorkflowInstance("inst-1", body);

        ArgumentCaptor<Map<String, Object>> sent = ArgumentCaptor.forClass(Map.class);
        verify(approvalService).approve(eq("task-1"), sent.capture());
        assertFalse(sent.getValue().containsKey("userId"),
                "契约列「服务端解析审批人」＝客户端不得自报主语；userId 透传等于让调用方指定审批人");
        assertFalse(sent.getValue().containsKey("approver"), "同上，approver 亦属自报键");
        assertEquals("同意", sent.getValue().get("opinion"), "非身份键必须原样搬运，禁顺手过滤业务字段");
    }

    @Test
    @DisplayName("任务未指派审批人 → 404 fail-loud，禁伪造 system 主语写审批行")
    void unassignedTaskRefusesToInventAnActor() {
        givenInstance("inst-1");
        givenTasks(task("task-1", "Assigned", null));

        NotFoundException ex = assertThrows(NotFoundException.class,
                () -> controller.approveWorkflowInstance("inst-1", Map.of("opinion", "ok")));
        assertTrue(ex.getMessage().contains("未指派审批人"),
                "缺失主语必须显式说明，禁静默回落（WorkflowActorContext.setActor 全仓零调用点，"
                        + "本面无可信服务端主语可用）：" + ex.getMessage());
        verify(approvalService, never()).approve(anyString(), any());
    }

    // ── ③ 审批链断裂 → 500 携 ECOS-WF-020 ─────────────

    @Test
    @DisplayName("数据访问故障落 500 且机读码位于消息串首（信封 errorCode 才取得到）")
    void dataAccessFailureCarriesWf020AtMessageStart() {
        when(instanceRepo.findById("inst-1")).thenThrow(
                new DataAccessResourceFailureException("relation ecos_workflow_instance does not exist"));

        DataBridgeException ex = assertThrows(DataBridgeException.class,
                () -> controller.approveWorkflowInstance("inst-1", Map.of()));
        assertEquals(500, ex.getHttpStatus(), "§6.3.3 钉 ECOS-WF-020 = 500，禁折 400/200");
        assertEquals("ECOS-WF-020", MachineErrorCode.fromMessage(ex.getMessage()).orElse(null),
                "机读码不在串首时 MachineErrorCode 取不到 ⇒ 网关信封 errorCode 回落数字串，"
                        + "契约错误码面即失效。实际消息=" + ex.getMessage());
        assertTrue(ex.getMessage().startsWith(WorkflowErrorCode.ECOS_WF_020), "消息须以属主常量开头");
    }

    @Test
    @DisplayName("写入成功但读回空行同样报 WF-020，禁返回 200 空审批行冒充成功")
    void missingReadBackRowIsNotSilentSuccess() {
        givenInstance("inst-1");
        givenTasks(task("task-1", "New", "lisi"));
        when(approvalService.approve(eq("task-1"), any())).thenReturn(Map.of("approvalId", "appr-9"));
        when(approvalRepo.findById("appr-9")).thenReturn(Optional.empty());

        DataBridgeException ex = assertThrows(DataBridgeException.class,
                () -> controller.approveWorkflowInstance("inst-1", Map.of()));
        assertEquals(500, ex.getHttpStatus());
        assertEquals("ECOS-WF-020", MachineErrorCode.fromMessage(ex.getMessage()).orElse(null));
    }

    // ── ④ 404 语义与任务解析 ──────────────────────────

    @Test
    @DisplayName("实例不存在 → WF-009 404（沿用既存词汇，不新造码值）")
    void absentInstanceFailsLoud() {
        when(instanceRepo.findById("inst-x")).thenReturn(Optional.empty());

        NotFoundException ex = assertThrows(NotFoundException.class,
                () -> controller.approveWorkflowInstance("inst-x", Map.of()));
        assertTrue(ex.getMessage().contains("WF-009"), "须复用既存词汇，实得=" + ex.getMessage());
        assertEquals(404, ex.getHttpStatus(), "404 状态行由异常携带值决定（D-B4-56 同号口径）");
    }

    @Test
    @DisplayName("实例存在但全链无可审批任务 → 404，禁 200 空审批行")
    void noApprovableTaskIsNotAnEmptySuccess() {
        givenInstance("inst-1");
        givenTasks(task("task-1", "Completed", "zhangsan"), task("task-2", "Rejected", "zhangsan"));

        NotFoundException ex = assertThrows(NotFoundException.class,
                () -> controller.approveWorkflowInstance("inst-1", Map.of()));
        assertTrue(ex.getMessage().contains("无可审批任务"), ex.getMessage());
        verify(approvalService, never()).approve(anyString(), any());
    }

    @Test
    @DisplayName("多枚待审批任务时取最早一枚（属主读面按 created_at 升序，本面不重排）")
    void earliestApprovableTaskIsConsumed() {
        givenInstance("inst-1");
        givenTasks(task("task-1", "Completed", "zhangsan"),
                task("task-2", "Assigned", "lisi"),
                task("task-3", "New", "wangwu"));
        when(approvalService.approve(eq("task-2"), any())).thenReturn(Map.of("approvalId", "appr-2"));
        when(approvalRepo.findById("appr-2")).thenReturn(Optional.of(row("appr-2", "task-2", "lisi")));

        controller.approveWorkflowInstance("inst-1", Map.of());
        verify(approvalService).approve(eq("task-2"), any());
    }

    @Test
    @DisplayName("成功腿返回读回的整行审批行（字段面与实体列同名）")
    void successReturnsTheApprovalRow() {
        givenInstance("inst-1");
        givenTasks(task("task-1", "New", "lisi"));
        when(approvalService.approve(eq("task-1"), any())).thenReturn(Map.of("approvalId", "appr-7"));
        when(approvalRepo.findById("appr-7")).thenReturn(Optional.of(row("appr-7", "task-1", "lisi")));

        ApiResponse<Map<String, Object>> resp =
                controller.approveWorkflowInstance("inst-1", Map.of("opinion", "同意"));

        assertEquals(0, resp.getCode(), "成功腿 code=0，禁把审批动作写成业务失败信封");
        Map<String, Object> data = resp.getData();
        assertEquals("appr-7", data.get("id"));
        assertEquals("task-1", data.get("taskId"));
        assertEquals("inst-1", data.get("instanceId"));
        assertEquals("lisi", data.get("approver"));
        assertEquals("Approved", data.get("decision"));
        assertEquals(List.of("id", "taskId", "instanceId", "approver", "decision", "opinion",
                        "formData", "createdAt"), List.copyOf(data.keySet()),
                "审批行字段面须与实体列同名且顺序稳定（前端渲染与契约对账共用）");
    }

    // ── 夹具 ──────────────────────────────────────────

    private void givenInstance(String id) {
        WorkflowInstanceEntity instance = new WorkflowInstanceEntity();
        instance.setId(id);
        when(instanceRepo.findById(id)).thenReturn(Optional.of(instance));
    }

    private void givenTasks(WorkflowTaskEntity... tasks) {
        when(taskRepo.findByInstanceId(anyString())).thenReturn(List.of(tasks));
    }

    private static WorkflowTaskEntity task(String id, String status, String assignee) {
        WorkflowTaskEntity t = new WorkflowTaskEntity();
        t.setId(id);
        t.setInstanceId("inst-1");
        t.setNodeId("node-1");
        t.setStatus(status);
        t.setAssignee(assignee);
        return t;
    }

    private static WorkflowApprovalEntity row(String id, String taskId, String approver) {
        WorkflowApprovalEntity e = new WorkflowApprovalEntity();
        e.setId(id);
        e.setTaskId(taskId);
        e.setInstanceId("inst-1");
        e.setApprover(approver);
        e.setDecision("Approved");
        e.setOpinion("同意");
        e.setFormData(null);
        e.setCreatedAt(LocalDateTime.of(2026, 10, 29, 10, 0));
        return e;
    }

    private static String first(String[] values) {
        assertEquals(1, values.length, "注解应恰有一枚路径值，实得=" + List.of(values));
        return values[0];
    }
}
