package com.chinacreator.gzcm.engine.kb;

import com.chinacreator.gzcm.engine.kb.controller.KbProfileController;
import com.chinacreator.gzcm.engine.kb.service.profile.KbProfileService;
import com.chinacreator.gzcm.engine.kb.service.profile.KbProfileStore;
import com.chinacreator.gzcm.runtime.core.task.model.TaskDescription;
import com.chinacreator.gzcm.runtime.core.task.service.ITaskManagementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

/**
 * F04-14 验收：画像生成经 runtime-task KB_PROFILE_GENERATE 派发，任务中心可见。
 *
 * <p>断言：{@link KbProfileController#generate} 不把 LLM/画像计算放线程池本地执行
 * （禁 Executors/CompletableFuture/@Scheduled，铁律 §2.5-3），而是把
 * TaskDescription(taskType=KB_PROFILE_GENERATE, async=true) 交给
 * {@link ITaskManagementService#submitTask}，任务中心显示。</p>
 */
@ExtendWith(MockitoExtension.class)
class KbTaskDelegationTest {

    @Mock private KbProfileService service;
    @Mock private KbProfileStore store;
    @Mock private ITaskManagementService taskManagement;

    private KbProfileController controller;

    @BeforeEach
    void setUp() throws ITaskManagementService.TaskManagementException {
        lenient().when(taskManagement.submitTask(any(TaskDescription.class)))
                .thenReturn("mock-task-id");
        controller = new KbProfileController(service, store, taskManagement);
    }

    @Test
    @DisplayName("F04-14: 画像生成走 submitTask — 任务中心可见，非自建线程")
    void profileGenerationVisibleInTaskCenter() throws ITaskManagementService.TaskManagementException {
        KbProfileController.ProfileGenerateRequest req = new KbProfileController.ProfileGenerateRequest();
        req.setMetricCodes(List.of("sales"));
        req.setWindowFrom("2026-01-01");
        req.setWindowTo("2026-06-30");

        var resp = controller.generate(req).getBody();
        assertNotNull(resp, "generate 必须携带信封（ResponseEntity 不得空 body）");
        assertEquals(0, resp.getCode(), "generate → success code=0");
        Object data = resp.getData();
        assertNotNull(data);
        if (data instanceof java.util.Map<?, ?> m) {
            assertNotNull(m.get("taskId"), "响应 data.taskId 非空（任务中心链接）");
        }

        ArgumentCaptor<TaskDescription> captor = ArgumentCaptor.forClass(TaskDescription.class);
        verify(taskManagement).submitTask(captor.capture());
        TaskDescription submitted = captor.getValue();
        assertEquals("KB_PROFILE_GENERATE", submitted.getTaskType(),
                "画像生成任务类型 = KB_PROFILE_GENERATE（task center 分类键）");
        assertTrue(Boolean.TRUE.equals(submitted.getAsync()),
                "async=true — 异步派发，不占用 Web 线程");
        assertNotNull(submitted.getParameters(), "parameters 携带 metrics/dims/window payload");
        assertNotNull(submitted.getTaskId(), "taskId 非空，任务中心可查");
    }
}
