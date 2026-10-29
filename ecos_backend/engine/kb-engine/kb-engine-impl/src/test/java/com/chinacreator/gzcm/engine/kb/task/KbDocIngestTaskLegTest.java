package com.chinacreator.gzcm.engine.kb.task;

import com.chinacreator.gzcm.engine.kb.dto.KnowledgeDocIngestResultVO;
import com.chinacreator.gzcm.engine.kb.service.KnowledgeDocIngestService;
import com.chinacreator.gzcm.runtime.core.task.callback.ITaskStatusCallback;
import com.chinacreator.gzcm.runtime.core.task.executor.ITaskExecutor.TaskExecutionException;
import com.chinacreator.gzcm.runtime.core.task.model.TaskDescription;
import com.chinacreator.gzcm.runtime.core.task.model.TaskExecutionPlan;
import com.chinacreator.gzcm.runtime.core.task.parser.ITaskParser;
import com.chinacreator.gzcm.runtime.core.task.service.ITaskManagementService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@code KB_DOC_INGEST} 承流三件套门（#146z 附录A 第 11 条异步执行腿）。
 *
 * <p>钉四件事：① Parser 只认 {@code KB_DOC_INGEST}（大小写不敏感），且
 * <b>无 {@code docId} 参数即拒</b>——执行腿按 docId 回读行，缺键的任务只会烧成一个
 * 永远失败的执行（参数键面与派发侧单源对齐）；② Executor 阶段产物走同一份
 * {@code runByDocId} 单源，成功回报 {@code onTaskComplete(true)} + 结果 JSON，
 * 失败回报 {@code onTaskComplete(false)} + {@code onError} 并上抛
 * {@code TaskExecutionException}（禁吞异常把失败报成成功）；③ Registrar 用
 * {@code Parser.TASK_TYPE} 单源注册双件，禁字面量第二副本；④ 任务类型字符串
 * 与 {@code KbProfileController} 等同族派发形态一致（async 腿的可见性由
 * {@code KnowledgeDocumentIngestContractTest} 钉）。</p>
 */
@ExtendWith(MockitoExtension.class)
class KbDocIngestTaskLegTest {

    @Mock private KnowledgeDocIngestService docIngestService;
    @Mock private ITaskStatusCallback callback;
    @Mock private ITaskManagementService taskManagement;

    private static TaskDescription desc(Map<String, Object> params) {
        TaskDescription d = new TaskDescription();
        d.setTaskId("kbdi-t-1");
        d.setTaskType("KB_DOC_INGEST");
        d.setParameters(params);
        return d;
    }

    private static Map<String, Object> params(String docId, String objectKey) {
        Map<String, Object> p = new HashMap<>();
        if (docId != null) {
            p.put("docId", docId);
        }
        if (objectKey != null) {
            p.put("objectKey", objectKey);
        }
        p.put("biz_kind", "KB_DOC_INGEST");
        return p;
    }

    // ── ① Parser ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("Parser 支持词表只含 KB_DOC_INGEST（大小写不敏感，别型即拒）")
    void parserSupportsOnlyOwnType() {
        KbDocIngestTaskParser parser = new KbDocIngestTaskParser();
        assertTrue(parser.supports("KB_DOC_INGEST"));
        assertTrue(parser.supports("kb_doc_ingest"), "equalsIgnoreCase 兼容派发侧大小写");
        assertFalse(parser.supports("KB_IMPORT"), "抽取族不得被本解析器接走");
        assertFalse(parser.supports(null));
        assertEquals("KB_DOC_INGEST", KbDocIngestTaskParser.TASK_TYPE,
                "任务类型单源常量不得漂移");
    }

    @Test
    @DisplayName("Parser 无 docId 参数 ⇒ validate/parse 双拒（缺键任务不进执行腿）")
    void parserRejectsMissingDocId() {
        KbDocIngestTaskParser parser = new KbDocIngestTaskParser();
        assertThrows(ITaskParser.TaskParseException.class,
                () -> parser.validate(desc(params(null, "raw/x/a.pdf"))));
        assertThrows(ITaskParser.TaskParseException.class,
                () -> parser.parse(desc(params("  ", "raw/x/a.pdf"))),
                "空白 docId 同样拒绝，不得当成 null 继续跑");
    }

    @Test
    @DisplayName("Parser 拒别型任务类型 / 缺 taskId")
    void parserRejectsForeignTypeAndBlankTaskId() {
        KbDocIngestTaskParser parser = new KbDocIngestTaskParser();
        TaskDescription foreign = desc(params("d1", "raw/x/a.pdf"));
        foreign.setTaskType("KB_PROFILE_GENERATE");
        assertThrows(ITaskParser.TaskParseException.class, () -> parser.validate(foreign));

        TaskDescription noId = desc(params("d1", "raw/x/a.pdf"));
        noId.setTaskId(null);
        assertThrows(ITaskParser.TaskParseException.class, () -> parser.validate(noId));
    }

    @Test
    @DisplayName("Parser 产出单步计划，config/context 双挂参数（与 Executor 读取面同形）")
    void parserProducesSingleStepPlan()
            throws ITaskParser.TaskParseException {
        KbDocIngestTaskParser parser = new KbDocIngestTaskParser();
        TaskExecutionPlan plan = parser.parse(desc(params("d1", "raw/unstructured/hr/d1/a.pdf")));

        assertEquals("kbdi-t-1", plan.getTaskId());
        List<TaskExecutionPlan.ExecutionStep> steps = plan.getSteps();
        assertEquals(1, steps.size(), "解析流水线是单步任务，禁拆成多步各自编排");
        TaskExecutionPlan.ExecutionStep step = steps.get(0);
        assertEquals("KB_DOC_INGEST", step.getStepType());
        assertEquals("KB_DOC_INGEST", step.getExecutor());
        assertEquals("d1", step.getConfig().get("docId"));
        assertEquals("raw/unstructured/hr/d1/a.pdf", step.getConfig().get("objectKey"));
        assertNotNull(plan.getContext(), "context 兜底：config 读不到时仍可按 plan 取参");
        assertEquals("d1", plan.getContext().get("docId"));
    }

    // ── ② Executor ────────────────────────────────────────────────────────

    private static TaskExecutionPlan plan(Map<String, Object> config) {
        TaskExecutionPlan plan = new TaskExecutionPlan();
        plan.setTaskId("kbdi-t-1");
        TaskExecutionPlan.ExecutionStep step = new TaskExecutionPlan.ExecutionStep();
        step.setStepId("kb-doc-ingest-step-1");
        step.setStepType("KB_DOC_INGEST");
        step.setExecutor("KB_DOC_INGEST");
        step.setConfig(config);
        plan.setSteps(List.of(step));
        return plan;
    }

    @Test
    @DisplayName("Executor 成功腿：跑 runByDocId 单源 + onTaskComplete(true) + 结果 JSON")
    void executorSuccessRunsSingleSourcePipeline() throws Exception {
        KnowledgeDocIngestResultVO result = new KnowledgeDocIngestResultVO();
        result.setDocId("d1");
        result.setParseStatus("done");
        result.setChunkCount(3);
        when(docIngestService.runByDocId("d1")).thenReturn(result);

        String out = new KbDocIngestTaskExecutor(docIngestService)
                .execute(plan(params("d1", "raw/x/a.pdf")), callback);

        verify(docIngestService).runByDocId("d1");
        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(callback).onTaskComplete(eq("kbdi-t-1"), eq(true), json.capture(), eq(null));
        assertTrue(json.getValue().contains("\"d1\""), "结果 JSON 必须含 docId（任务中心可读产物）");
        assertTrue(out.contains("\"d1\""));
        verify(callback, never()).onTaskComplete(anyString(), eq(false), any(), any());
    }

    @Test
    @DisplayName("Executor 失败腿：阶段异常回报 false + onError 并上抛，禁吞成成功")
    void executorFailureIsReportedAsFailure() throws Exception {
        when(docIngestService.runByDocId("d1"))
                .thenThrow(new IllegalStateException("解析器不可用"));

        KbDocIngestTaskExecutor executor = new KbDocIngestTaskExecutor(docIngestService);
        assertThrows(TaskExecutionException.class,
                () -> executor.execute(plan(params("d1", "raw/x/a.pdf")), callback));
        verify(callback).onTaskComplete("kbdi-t-1", false, null, "解析器不可用");
        verify(callback).onError(eq("kbdi-t-1"), anyString(), eq(null));
    }

    @Test
    @DisplayName("Executor 无 docId ⇒ 拒绝执行，不空跑流水线")
    void executorRejectsPlanWithoutDocId() {
        KbDocIngestTaskExecutor executor = new KbDocIngestTaskExecutor(docIngestService);
        assertThrows(TaskExecutionException.class,
                () -> executor.execute(plan(params(null, "raw/x/a.pdf")), callback));
        assertThrows(TaskExecutionException.class, () -> executor.execute(null, callback));
    }

    @Test
    @DisplayName("Executor context 兜底取参（config 缺键时仍按 plan.context 续跑）")
    void executorFallsBackToPlanContext() throws Exception {
        KnowledgeDocIngestResultVO result = new KnowledgeDocIngestResultVO();
        result.setDocId("d9");
        result.setParseStatus("done");
        when(docIngestService.runByDocId("d9")).thenReturn(result);

        TaskExecutionPlan p = plan(new HashMap<>());
        p.setContext(params("d9", "raw/x/d9.pdf"));
        new KbDocIngestTaskExecutor(docIngestService).execute(p, callback);

        verify(docIngestService).runByDocId("d9");
    }

    @Test
    @DisplayName("不支持运行中取消/暂停/恢复 ⇒ 显式上抛，禁静默 no-op 谎报已取消")
    void executorRejectsUnsupportedLifecycleCalls() {
        KbDocIngestTaskExecutor executor = new KbDocIngestTaskExecutor(docIngestService);
        assertThrows(TaskExecutionException.class, () -> executor.cancel("kbdi-t-1"));
        assertThrows(TaskExecutionException.class, () -> executor.pause("kbdi-t-1"));
        assertThrows(TaskExecutionException.class, () -> executor.resume("kbdi-t-1"));
    }

    // ── ③ Registrar ───────────────────────────────────────────────────────

    @Test
    @DisplayName("Registrar 以 Parser.TASK_TYPE 单源注册执行器 + 解析器双件")
    void registrarRegistersBothLegsWithSingleSourceKey() {
        KbDocIngestTaskExecutor executor = new KbDocIngestTaskExecutor(docIngestService);
        KbDocIngestTaskParser parser = new KbDocIngestTaskParser();

        new KbDocIngestTaskRegistrar(taskManagement, executor, parser).register();

        verify(taskManagement).registerExecutor(KbDocIngestTaskParser.TASK_TYPE, executor);
        verify(taskManagement).registerParser(KbDocIngestTaskParser.TASK_TYPE, parser);
    }

    // ── 进度回报的最低可读性（任务中心分态可见） ──────────────────────────

    @Test
    @DisplayName("成功腿先报进度再报完成（任务中心不得只见 RUNNING 无进展）")
    void executorReportsProgressBeforeCompletion() throws Exception {
        KnowledgeDocIngestResultVO result = new KnowledgeDocIngestResultVO();
        result.setDocId("d1");
        result.setParseStatus("done");
        when(docIngestService.runByDocId("d1")).thenReturn(result);

        new KbDocIngestTaskExecutor(docIngestService).execute(plan(params("d1", "raw/x/a.pdf")),
                callback);

        // 两档进度各自钉死：10 = 进入流水线前，90 = 流水线完成后、回报前（禁只留一个笼统 anyInt）
        verify(callback).onProgressUpdate(eq("kbdi-t-1"), eq(10), anyString());
        verify(callback).onProgressUpdate(eq("kbdi-t-1"), eq(90), anyString());
        verify(callback, times(2)).onProgressUpdate(eq("kbdi-t-1"), anyInt(), anyString());
        verify(callback).onTaskComplete(eq("kbdi-t-1"), eq(true), anyString(), eq(null));
    }
}
