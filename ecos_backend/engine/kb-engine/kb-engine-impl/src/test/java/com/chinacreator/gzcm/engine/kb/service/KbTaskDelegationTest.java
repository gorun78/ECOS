package com.chinacreator.gzcm.engine.kb.service;

import com.chinacreator.gzcm.engine.kb.profile.KbProfileMapper;
import com.chinacreator.gzcm.engine.kb.profile.KbProfileServiceImpl;
import com.chinacreator.gzcm.engine.kb.profile.KbProfileStatsMapper;
import com.chinacreator.gzcm.engine.kb.profile.ProfileGenerateRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import com.chinacreator.gzcm.common.context.TraceContext;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.chinacreator.gzcm.runtime.core.task.model.TaskDescription;
import com.chinacreator.gzcm.runtime.core.task.service.ITaskManagementService;

/**
 * F04-14 验收（mvn -Dtest=KbTaskDelegationTest）— 调度与并发收口 runtime-task（K-38/K-39 纠正）。
 *
 * <p>断言画像生成（{@link KbProfileServiceImpl#generate}，dryRun=false）<b>一律经
 * {@link ITaskManagementService#submitTask}</b> 提交、返回 taskId 且在任务中心可反查，
 * 而非本地 {@code Executors}/{@code CompletableFuture}/自建线程（F04-14 陈述）。
 *
 * <p>纯 Mockito，无 @SpringBootTest / 无 H2。
 *
 * @author ECOS KB Team
 */
class KbTaskDelegationTest {

    private KbProfileMapper profileMapper;
    private KbProfileStatsMapper statsMapper;
    private ITaskManagementService taskService;
    private KbProfileServiceImpl service;

    @BeforeEach
    void setUp() {
        profileMapper = mock(KbProfileMapper.class);
        statsMapper = mock(KbProfileStatsMapper.class);
        taskService = mock(ITaskManagementService.class);
        service = new KbProfileServiceImpl(profileMapper, statsMapper,
                mock(com.chinacreator.gzcm.engine.kb.profile.CuratedFactSource.class), taskService);
        TraceContext.put("trace-test-1");
    }

    @AfterEach
    void tearDown() {
        TraceContext.clear();
    }

    private static ProfileGenerateRequest req() {
        ProfileGenerateRequest r = new ProfileGenerateRequest();
        r.setMetricCodes(Collections.singletonList("M_RR"));
        r.setDims(new HashMap<>());
        r.setWindowFrom("2025-01");
        r.setWindowTo("2026-12");
        r.setDryRun(false);
        return r;
    }

    @Test
    @DisplayName("F04-14: dryRun=false 画像生成提交任务中心（KN_PROFILE_GENERATE）→ 返回 taskId + SUBMITTED")
    void profileGenerationVisibleInTaskCenter() throws Exception {
        when(taskService.submitTask(any(TaskDescription.class))).thenReturn("task-123");

        Map<String, Object> out = service.generate(req());

        assertEquals("SUBMITTED", out.get("status"), "非 dry-run 走任务中心，状态 SUBMITTED");
        assertEquals("task-123", out.get("taskId"), "submitTask 返回的 taskId 透传，任务中心可反查 progress");
        assertNotNull(out.get("traceId"), "traceId 供 UI→任务中心双向定位（C.7）");
        assertEquals("trace-test-1", out.get("traceId"), "响应 traceId = TraceContext.current()（MDC 回写）");

        // 关键点：TaskDescription 必须声明任务类型 = 画像生成（可被任务中心反查/路由到 Executor）
        ArgumentCaptor<TaskDescription> td = ArgumentCaptor.forClass(TaskDescription.class);
        verify(taskService, times(1)).submitTask(td.capture());
        assertEquals(KbProfileServiceImpl.TASK_TYPE_PROFILE, td.getValue().getTaskType(),
                "任务类型 = KB_PROFILE_GENERATE，注册了独立 Executor");
        assertNotNull(td.getValue().getParameters().get("metricCodes"), "参数携带 metricCodes 供 Executor 复用");
    }

    @Test
    @DisplayName("F04-14: dryRun=true 不落任务中心（不占任务中心、不提交），直接同步返回 DRY_RUN_DONE")
    void dryRunDoesNotSubmitToTaskCenter() throws Exception {
        ProfileGenerateRequest r = req();
        r.setDryRun(true);
        // dry-run 走 generateSync(dryRun=true)，不触达 taskService
        // （零样本 → 退化链穷尽 → KB_041 计入 errorCodes，但不抛）

        Map<String, Object> out = service.generate(r);

        assertEquals("DRY_RUN_DONE", out.get("status"));
        assertNull(out.get("taskId"), "dry-run 不占任务中心，taskId 应为 null");
        verify(taskService, never()).submitTask(any(TaskDescription.class));
    }
}
