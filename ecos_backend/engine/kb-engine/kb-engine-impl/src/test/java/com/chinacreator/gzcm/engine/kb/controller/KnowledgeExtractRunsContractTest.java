package com.chinacreator.gzcm.engine.kb.controller;

import com.chinacreator.gzcm.common.annotation.RequirePermission;
import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.kb.dto.EntityInstanceExtractionReportVO;
import com.chinacreator.gzcm.engine.kb.dto.KnowledgeExtractRunReportVO;
import com.chinacreator.gzcm.engine.kb.dto.KnowledgeExtractRunRequest;
import com.chinacreator.gzcm.engine.kb.dto.KnowledgeExtractRunSubmitVO;
import com.chinacreator.gzcm.engine.kb.dto.KnowledgeExtractWatermarkListVO;
import com.chinacreator.gzcm.engine.kb.service.KnowledgeExtractRunService;
import com.chinacreator.gzcm.engine.kb.service.StructuredExtractQueryService;
import com.chinacreator.gzcm.engine.kb.support.KbErrorCodes;
import com.chinacreator.gzcm.runtime.core.task.model.TaskStatus;
import com.chinacreator.gzcm.runtime.core.task.service.ITaskManagementService;
import com.chinacreator.gzcm.runtime.core.task.service.ITaskManagementService.TaskManagementException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@code /api/v1/knowledge/extract} runs 族四端点契约门（详设-07 §7.3.2 端点总表第 12/13 行）。
 *
 * <p>钉八件事：① 无载体入参（{@code sourceRef} / {@code fieldMappings}）与非词表
 * {@code sourceType} 一律 400 且<b>不进快照闸</b>；② 无快照 ⇒ {@code DomainException} 以
 * 409 {@code ECOS-KB-011} 上浮，<b>不被提交腿的 {@code RuntimeException} 折成 503</b>
 * （折叠会让「无数据」与「底座坏了」在传输层同形）；③ 任务底座失败 ⇒ 503 {@code ECOS-KB-021}，
 * 状态行与信封同号；④ 提交成功 ⇒ 202 + {@code {taskId,jobId,status:"SUBMITTED"}}；
 * ⑤ 报告回读三态互不折叠（任务状态在/审计兜底/两源皆无 404），报告不可读时
 * {@code reportAvailable=false} 且计数保持 {@code null}（禁把「读不到」渲染成「0 条」）；
 * ⑥ 审计表故障且无法判定存在性 ⇒ 原样上浮 500 {@code ECOS-KB-050}，有任务状态时容错；
 * ⑦ dry-run 三件套弃用头齐备且 {@code watermarkAdvanced} 按 nextWatermark 真值判定；
 * ⑧ 水位线分页钳制与 {@code sourceType} 口径回显（不做无谓词的假过滤）。</p>
 *
 * <p>harness 与 {@code KbProfileStatusFidelityTest} 同族：Mockito + 直调 Controller 看
 * {@code getStatusCode()}——common-api 的静态棘轮门只认源码形态，对「形态对、状态值错」结构性失明。</p>
 */
@ExtendWith(MockitoExtension.class)
class KnowledgeExtractRunsContractTest {

    private static final String SCOPE = "ONT";
    private static final String JOB_ID = "KBK1S-1";

    @Mock private KnowledgeExtractRunService runService;
    @Mock private StructuredExtractQueryService queryService;
    @Mock private ITaskManagementService taskManagement;

    private KnowledgeExtractRunsController controller() {
        return new KnowledgeExtractRunsController(runService, queryService, taskManagement);
    }

    private static KnowledgeExtractRunRequest request() {
        KnowledgeExtractRunRequest req = new KnowledgeExtractRunRequest();
        req.setSourceType("ONTOLOGY_MAPPING");
        req.setOntologySnapshotId(SCOPE);
        return req;
    }

    private static void assertStatusAndCode(ResponseEntity<? extends ApiResponse<?>> resp,
                                            int expectedStatus,
                                            String expectedMachineCode) {
        assertEquals(expectedStatus, resp.getStatusCode().value(), "状态行必须等于契约期望值");
        ApiResponse<?> body = resp.getBody();
        assertNotNull(body, "ResponseEntity 必须携带信封");
        assertEquals(expectedStatus, body.getCode(), "信封 code 必须与状态行同号");
        if (expectedMachineCode != null) {
            assertEquals(expectedMachineCode, body.getErrorCode(), "机读码必须取 KbErrorCodes 单源");
        }
    }

    // ── ① 入参无载体 / 词表外取值 ⇒ 400，且不进快照闸 ─────────────────────

    @Test
    @DisplayName("POST /runs 带 fieldMappings ⇒ 400（映射契约在本体映射台账，禁请求体内联绕过）")
    void submitRejectsFieldMappings() {
        KnowledgeExtractRunRequest req = request();
        req.setFieldMappings(Map.of("source", "id", "target", "code"));
        ResponseEntity<ApiResponse<KnowledgeExtractRunSubmitVO>> resp = controller().submitRun(req);
        assertStatusAndCode(resp, 400, null);
        assertTrue(resp.getBody().getMessage().contains("fieldMappings"), "拒绝文案须点名字段");
        verifyNoInteractions(runService, taskManagement);
    }

    @Test
    @DisplayName("POST /runs 带 sourceRef ⇒ 400（抽取源由映射台账决定）")
    void submitRejectsSourceRef() {
        KnowledgeExtractRunRequest req = request();
        req.setSourceRef("dataset://x");
        ResponseEntity<ApiResponse<KnowledgeExtractRunSubmitVO>> resp = controller().submitRun(req);
        assertStatusAndCode(resp, 400, null);
        assertTrue(resp.getBody().getMessage().contains("sourceRef"), "拒绝文案须点名字段");
        verifyNoInteractions(runService, taskManagement);
    }

    @Test
    @DisplayName("POST /runs 的 sourceType 非词表取值 ⇒ 400（词表当前仅 ONTOLOGY_MAPPING）")
    void submitRejectsUnknownSourceType() {
        KnowledgeExtractRunRequest req = request();
        req.setSourceType("MANUAL_UPLOAD");
        ResponseEntity<ApiResponse<KnowledgeExtractRunSubmitVO>> resp = controller().submitRun(req);
        assertStatusAndCode(resp, 400, null);
        assertTrue(resp.getBody().getMessage().contains("ONTOLOGY_MAPPING"), "拒绝文案须给出允许词表");
        verifyNoInteractions(runService, taskManagement);
    }

    // ── ② 无快照 ⇒ 409 ECOS-KB-011 上浮，禁被提交腿折成 503 ───────────────

    @Test
    @DisplayName("POST /runs 无快照 ⇒ DomainException 以 409/ECOS-KB-011 上浮（不折成 503）")
    void submitWithoutSnapshotCarries409() throws Exception {
        KbErrorCodes.DomainException denied = KbErrorCodes.SNAPSHOT_MISSING.exception("无快照");
        org.mockito.Mockito.doThrow(denied).when(runService).requireSnapshot(SCOPE);

        KbErrorCodes.DomainException thrown = assertThrows(KbErrorCodes.DomainException.class,
                () -> controller().submitRun(request()));
        assertEquals(409, thrown.getHttpStatus(), "状态行 409 由 KbErrorAdvice 落，禁变成 503");
        assertEquals("ECOS-KB-011", thrown.getCode());
        verify(runService, never()).submit(anyString(), anyString(), org.mockito.ArgumentMatchers.anyBoolean(),
                anyString());
    }

    // ── ③④ 任务底座 503 与提交成功 202 ───────────────────────────────────

    @Test
    @DisplayName("POST /runs 提交失败 ⇒ 503 上状态行 + ECOS-KB-021（禁降级本地同步计算）")
    void submitTaskBaseUnavailableIs503() throws Exception {
        when(runService.newJobId()).thenReturn(JOB_ID);
        when(runService.submit(eq(SCOPE), eq(KnowledgeExtractRunService.MODE_INCREMENTAL), eq(false), eq(JOB_ID)))
                .thenThrow(new TaskManagementException("runtime-task 不可达"));
        // 状态行由 KbErrorAdvice 单源渲染：控制器只负责把携带 503 的载体抛出去（同域其余端点同源）
        KbErrorCodes.DomainException carried = assertThrows(KbErrorCodes.DomainException.class,
                () -> controller().submitRun(request()));
        assertEquals(KbErrorCodes.TASK_BASE_UNAVAILABLE.httpStatus(), carried.getHttpStatus(),
                "携带状态必须是 503（否则 advice 落出的状态行与契约不符）");
        assertEquals(KbErrorCodes.TASK_BASE_UNAVAILABLE.code(), carried.getCode(),
                "机读码必须是 ECOS-KB-021 单源");
        assertTrue(carried.getMessage().contains("runtime-task"), "拒绝文案须给出底座名");
        verify(runService).requireSnapshot(SCOPE);   // 快照闸在前，不得因底座故障被跳过
    }

    @Test
    @DisplayName("POST /runs 成功 ⇒ 202 + {taskId,jobId,status=SUBMITTED}")
    void submitSuccessIs202() throws Exception {
        when(runService.newJobId()).thenReturn(JOB_ID);
        when(runService.submit(anyString(), anyString(), org.mockito.ArgumentMatchers.anyBoolean(), anyString()))
                .thenReturn(new KnowledgeExtractRunService.SubmitOutcome("task-1", JOB_ID));
        ResponseEntity<ApiResponse<KnowledgeExtractRunSubmitVO>> resp = controller().submitRun(request());
        assertEquals(202, resp.getStatusCode().value(), "受理态必须是 202（不是 200）");
        assertEquals(0, resp.getBody().getCode(), "成功信封 code=0");
        KnowledgeExtractRunSubmitVO vo = resp.getBody().getData();
        assertEquals("task-1", vo.getTaskId());
        assertEquals(JOB_ID, vo.getJobId(), "jobId 与 taskId 分列，禁互相顶替");
        assertEquals("SUBMITTED", vo.getStatus());
    }

    @Test
    @DisplayName("POST /runs 的 dryRun=true 原样透传给单腿服务（禁静默丢弃）")
    void submitPassesDryRunThrough() throws Exception {
        when(runService.newJobId()).thenReturn(JOB_ID);
        when(runService.submit(eq(SCOPE), eq(KnowledgeExtractRunService.MODE_INCREMENTAL), eq(true), eq(JOB_ID)))
                .thenReturn(new KnowledgeExtractRunService.SubmitOutcome("task-2", JOB_ID));
        KnowledgeExtractRunRequest req = request();
        req.setDryRun(Boolean.TRUE);
        assertEquals(202, controller().submitRun(req).getStatusCode().value());
        verify(runService).submit(SCOPE, KnowledgeExtractRunService.MODE_INCREMENTAL, true, JOB_ID);
    }

    // ── ⑤ 报告回读三态 ───────────────────────────────────────────────────

    @Test
    @DisplayName("GET /runs/{taskId} 两源皆无 ⇒ 404（未知 taskId 不得伪装成空报告）")
    void getRunUnknownTaskIdIs404() {
        ResponseEntity<ApiResponse<KnowledgeExtractRunReportVO>> resp = controller().getRun("task-none");
        assertEquals(404, resp.getStatusCode().value());
        assertEquals(404, resp.getBody().getCode(), "信封 code 与状态行同号");
    }

    @Test
    @DisplayName("GET /runs/{taskId} 空白 taskId ⇒ 400（不入查询）")
    void getRunBlankTaskIdIs400() {
        ResponseEntity<ApiResponse<KnowledgeExtractRunReportVO>> resp = controller().getRun("   ");
        assertStatusAndCode(resp, 400, null);
        verifyNoInteractions(taskManagement, queryService);
    }

    @Test
    @DisplayName("GET /runs/{taskId} 任务状态可读 ⇒ 200 且报告 JSON 投影为契约键")
    void getRunProjectsTaskResult() throws Exception {
        TaskStatus status = new TaskStatus();
        status.setTaskId("task-3");
        status.setStatus(TaskStatus.Status.SUCCEEDED);
        status.setProgress(100);
        status.setStatusMessage("完成抽取");
        status.setResult("{\"jobId\":\"KBK1S-9\",\"nodeCreated\":7,\"nodeUpdated\":5,\"c3Skipped\":true,"
                + "\"nextWatermark\":\"w-9\",\"mode\":\"INCREMENTAL\",\"dryRun\":false,\"durationMs\":88,"
                + "\"issues\":[{\"entityCode\":\"PERSON\",\"code\":\"C2_SKIP\",\"message\":\"m\"}],"
                + "\"ontologies\":[\"ONT@1\"],\"ontologyCount\":1,\"entityCount\":12}");
        when(taskManagement.getTaskStatus("task-3")).thenReturn(status);

        ResponseEntity<ApiResponse<KnowledgeExtractRunReportVO>> resp = controller().getRun("task-3");
        assertEquals(200, resp.getStatusCode().value());
        KnowledgeExtractRunReportVO vo = resp.getBody().getData();
        assertEquals("SUCCEEDED", vo.getStatus());
        assertEquals(Integer.valueOf(100), vo.getProgress());
        assertEquals(Boolean.TRUE, vo.getReportAvailable(), "可解析报告 ⇒ reportAvailable=true");
        assertEquals(Integer.valueOf(7), vo.getCreated(), "契约键 created 取 nodeCreated 单源");
        assertEquals(Integer.valueOf(5), vo.getUpdated(), "契约键 updated 取 nodeUpdated 单源");
        assertEquals(Boolean.TRUE, vo.getC3Skipped());
        assertEquals(Boolean.TRUE, vo.getWatermarkAdvanced(), "nextWatermark 非空 ⇒ 水位推进");
        assertEquals("KBK1S-9", vo.getJobId());
        assertEquals(1, vo.getIssues().size(), "issues 逐条投影");
        assertEquals("PERSON", vo.getIssues().get(0).getEntityCode());
        assertEquals(List.of("ONT@1"), vo.getOntologies());
        assertNull(vo.getVectorDegraded(), "无信号源 ⇒ null，禁写死 false");
        assertEquals(Boolean.FALSE, vo.getVectorDegradedApplicable(), "不适用须显式标注");
    }

    @Test
    @DisplayName("GET /runs/{taskId} 任务状态无 result ⇒ 200 但 reportAvailable=false 且计数为 null")
    void getRunUnreadableResultMarksReportUnavailable() throws Exception {
        TaskStatus status = new TaskStatus();
        status.setStatus(TaskStatus.Status.RUNNING);
        status.setProgress(30);
        when(taskManagement.getTaskStatus("task-4")).thenReturn(status);

        ResponseEntity<ApiResponse<KnowledgeExtractRunReportVO>> resp = controller().getRun("task-4");
        assertEquals(200, resp.getStatusCode().value(), "任务存在即 200，报告未就绪不是错误");
        KnowledgeExtractRunReportVO vo = resp.getBody().getData();
        assertEquals("RUNNING", vo.getStatus());
        assertEquals(Boolean.FALSE, vo.getReportAvailable());
        assertNull(vo.getCreated(), "读不到报告不得渲染成 0 条");
        assertNull(vo.getUpdated());
        assertNull(vo.getWatermarkAdvanced(), "无报告 ⇒ 无水位证据，置 null 而非 false");
    }

    @Test
    @DisplayName("GET /runs/{taskId} 任务存储已清 ⇒ 审计行兜底出状态与 jobId/mode")
    void getRunFallsBackToAuditRow() {
        Map<String, Object> audit = Map.of(
                "job_id", JOB_ID,
                "task_id", "task-5",
                "mode", "INCREMENTAL",
                "status", "SUCCEEDED",
                "duration_ms", 321L,
                "rows_total", 10L,
                "rows_ok", 10L,
                "rows_failed", 0L);
        when(queryService.findLatestAuditByTaskId("task-5")).thenReturn(List.of(audit));

        ResponseEntity<ApiResponse<KnowledgeExtractRunReportVO>> resp = controller().getRun("task-5");
        assertEquals(200, resp.getStatusCode().value());
        KnowledgeExtractRunReportVO vo = resp.getBody().getData();
        assertEquals("SUCCEEDED", vo.getStatus());
        assertEquals(JOB_ID, vo.getJobId());
        assertEquals("INCREMENTAL", vo.getMode());
        assertEquals(Long.valueOf(321L), vo.getDurationMs());
        assertEquals(Boolean.FALSE, vo.getReportAvailable(), "审计行不含报告体 ⇒ 报告不可读");
    }

    // ── ⑥ 审计表故障分态 ─────────────────────────────────────────────────

    @Test
    @DisplayName("GET /runs/{taskId} 审计表故障且无任务状态 ⇒ 原样上浮 500（禁折成 404）")
    void getRunAuditReadFailurePropagates() {
        DataAccessException failure = new DataAccessResourceFailureException("ecos_kb_extract_audit 不可读");
        when(queryService.findLatestAuditByTaskId("task-6")).thenThrow(failure);
        DataAccessException thrown = assertThrows(DataAccessException.class,
                () -> controller().getRun("task-6"));
        assertEquals(failure, thrown, "故障必须原样上浮给 KbErrorAdvice（500 ECOS-KB-050）");
    }

    @Test
    @DisplayName("GET /runs/{taskId} 审计表故障但任务状态已就绪 ⇒ 容错仍 200")
    void getRunAuditReadFailureToleratedWithTaskStatus() throws Exception {
        TaskStatus status = new TaskStatus();
        status.setStatus(TaskStatus.Status.SUCCEEDED);
        when(taskManagement.getTaskStatus("task-7")).thenReturn(status);
        when(queryService.findLatestAuditByTaskId("task-7"))
                .thenThrow(new DataAccessResourceFailureException("boom"));

        ResponseEntity<ApiResponse<KnowledgeExtractRunReportVO>> resp = controller().getRun("task-7");
        assertEquals(200, resp.getStatusCode().value(), "存在性已由任务状态确立 ⇒ 审计反查失败不阻断");
        assertEquals("SUCCEEDED", resp.getBody().getData().getStatus());
    }

    // ── ⑦ dry-run 弃用头与报告 ───────────────────────────────────────────

    @Test
    @DisplayName("POST /dry-run ⇒ 200 + 报告 + Deprecation/Sunset/Link 三件套")
    void dryRunCarriesDeprecationHeaders() {
        when(runService.newJobId()).thenReturn(JOB_ID);
        when(runService.preview(SCOPE, JOB_ID, true)).thenReturn(report(3, 2, ""));
        ResponseEntity<ApiResponse<KnowledgeExtractRunReportVO>> resp = controller().dryRun(request());

        assertEquals(200, resp.getStatusCode().value(), "预演是同步完成态，不是 202");
        HttpHeaders headers = resp.getHeaders();
        assertEquals("true", headers.getFirst("Deprecation"), "§7.3.1 演进三件套之一");
        assertNotNull(headers.getFirst("Sunset"), "§7.3.1 演进三件套之二");
        assertTrue(headers.getFirst(HttpHeaders.LINK).contains("rel=\"successor-version\""),
                "§7.3.1 演进三件套之三，实际=" + headers.getFirst(HttpHeaders.LINK));

        KnowledgeExtractRunReportVO vo = resp.getBody().getData();
        assertEquals(JOB_ID, vo.getJobId());
        assertEquals("SUCCEEDED", vo.getStatus());
        assertEquals(Integer.valueOf(3), vo.getCreated());
        assertEquals(Integer.valueOf(2), vo.getUpdated());
        assertEquals(Boolean.TRUE, vo.getReportAvailable());
        assertEquals(Boolean.FALSE, vo.getWatermarkAdvanced(), "预演不推进水位：nextWatermark 空 ⇒ false");
        assertEquals("PENDING_REVIEW", vo.getIssues().get(0).getCode());
    }

    @Test
    @DisplayName("POST /dry-run 与 /runs 共用同一入参闸与快照闸（禁两条腿各写一套）")
    void dryRunSharesInputAndSnapshotGates() {
        KnowledgeExtractRunRequest bad = request();
        bad.setFieldMappings(Map.of("a", "b"));
        assertStatusAndCode(controller().dryRun(bad), 400, null);

        org.mockito.Mockito.doThrow(KbErrorCodes.SNAPSHOT_MISSING.exception("无快照"))
                .when(runService).requireSnapshot(SCOPE);
        KbErrorCodes.DomainException thrown = assertThrows(KbErrorCodes.DomainException.class,
                () -> controller().dryRun(request()));
        assertEquals(409, thrown.getHttpStatus());
        assertEquals("ECOS-KB-011", thrown.getCode());
        verify(runService, never()).preview(anyString(), anyString(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    // ── ⑧ 水位线 ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /watermarks sourceType 非词表取值 ⇒ 400（不做无谓词的假过滤）")
    void watermarksRejectsUnknownSourceType() {
        ResponseEntity<ApiResponse<KnowledgeExtractWatermarkListVO>> resp =
                controller().listWatermarks("FILE_UPLOAD", 1, 50);
        assertStatusAndCode(resp, 400, null);
        verifyNoInteractions(queryService);
    }

    @Test
    @DisplayName("GET /watermarks ⇒ 200 + 活行投影 + sourceType 口径回显")
    void watermarksListActiveRows() {
        Map<String, Object> row = Map.of(
                "ontology_id", SCOPE,
                "entity_code", "PERSON",
                "resource_id", "rsrc-1",
                "watermark", "w-1",
                "trace_id", "t-1",
                "domain", "hr",
                "version_no", "v1",
                "updated_at", Timestamp.valueOf("2026-10-28 10:00:00"));
        when(queryService.listWatermarkItems(50, 50)).thenReturn(List.of(row));
        when(queryService.countWatermarkItems()).thenReturn(1L);

        ResponseEntity<ApiResponse<KnowledgeExtractWatermarkListVO>> resp =
                controller().listWatermarks(null, 2, 50);
        assertEquals(200, resp.getStatusCode().value());
        KnowledgeExtractWatermarkListVO vo = resp.getBody().getData();
        assertEquals(1L, vo.getTotal());
        assertEquals(2, vo.getPage());
        assertEquals(50, vo.getSize());
        assertEquals("ONTOLOGY_MAPPING", vo.getSourceType(), "V179 无 source_type 列 ⇒ 只做口径回显");
        assertEquals(1, vo.getItems().size());
        assertEquals("PERSON", vo.getItems().get(0).getEntityCode());
        assertEquals("2026-10-28T10:00", vo.getItems().get(0).getUpdatedAt(), "ISO-8601 同口径");
    }

    @Test
    @DisplayName("GET /watermarks 分页钳制：page<1 归 1、size>500 归 500")
    void watermarksClampsPaging() {
        when(queryService.listWatermarkItems(500, 0)).thenReturn(List.of());
        when(queryService.countWatermarkItems()).thenReturn(0L);

        KnowledgeExtractWatermarkListVO vo =
                controller().listWatermarks("ONTOLOGY_MAPPING", 0, 1000).getBody().getData();
        assertEquals(1, vo.getPage());
        assertEquals(500, vo.getSize(), "§7.3.1 分页上限 500");
        verify(queryService).listWatermarkItems(500, 0);
    }

    @Test
    @DisplayName("GET /watermarks 表不可读 ⇒ 原样上浮（500 ECOS-KB-050，禁伪 404 空列表）")
    void watermarksDataAccessFailurePropagates() {
        when(queryService.listWatermarkItems(anyInt(), anyInt()))
                .thenThrow(new DataAccessResourceFailureException("ecos_kb_extract_watermark 缺失"));
        assertThrows(DataAccessException.class, () -> controller().listWatermarks(null, 1, 50));
    }

    // ── 签名锚：路径/方法/返回体/权限码一律承契约 ─────────────────────────

    @Test
    @DisplayName("反射钉：类基址 + 四腿方法与路径 + 返回 ResponseEntity + 权限码")
    void endpointSignaturesArePinned() throws Exception {
        assertEquals("/api/v1/knowledge/extract",
                KnowledgeExtractRunsController.class.getAnnotation(RequestMapping.class).value()[0]);

        Method submit = KnowledgeExtractRunsController.class.getMethod("submitRun",
                KnowledgeExtractRunRequest.class);
        Method getRun = KnowledgeExtractRunsController.class.getMethod("getRun", String.class);
        Method dryRun = KnowledgeExtractRunsController.class.getMethod("dryRun",
                KnowledgeExtractRunRequest.class);
        Method watermarks = KnowledgeExtractRunsController.class.getMethod("listWatermarks",
                String.class, int.class, int.class);

        assertEquals("/runs", submit.getAnnotation(PostMapping.class).value()[0]);
        assertEquals("/runs/{taskId}", getRun.getAnnotation(GetMapping.class).value()[0]);
        assertEquals("/dry-run", dryRun.getAnnotation(PostMapping.class).value()[0]);
        assertEquals("/watermarks", watermarks.getAnnotation(GetMapping.class).value()[0]);

        for (Method m : List.of(submit, getRun, dryRun, watermarks)) {
            assertEquals(ResponseEntity.class, m.getReturnType(),
                    m.getName() + " 返回体退回 ApiResponse＝状态行将恒 200（D-B4-59 复现）");
            assertNotNull(m.getAnnotation(RequirePermission.class), m.getName() + " 缺权限声明");
        }
        assertEquals("knowledge:extract:submit", submit.getAnnotation(RequirePermission.class).permission());
        assertEquals("knowledge:extract:preview", dryRun.getAnnotation(RequirePermission.class).permission());
        assertEquals("knowledge:extract:read", getRun.getAnnotation(RequirePermission.class).permission());
        assertEquals("knowledge:extract:read", watermarks.getAnnotation(RequirePermission.class).permission());

        assertFalse(getRun.isAnnotationPresent(PostMapping.class), "报告读面是 GET 腿");
    }

    private static EntityInstanceExtractionReportVO report(int created, int updated, String nextWatermark) {
        EntityInstanceExtractionReportVO report = new EntityInstanceExtractionReportVO();
        report.setNodeCreated(created);
        report.setNodeUpdated(updated);
        report.setMode("FULL");
        report.setDryRun(true);
        report.setDurationMs(120L);
        report.setC3Skipped(false);
        report.setNextWatermark(nextWatermark);
        report.addOntology(SCOPE + "@1");
        report.addIssue("PERSON", "PENDING_REVIEW", "预演告警");
        return report;
    }
}
