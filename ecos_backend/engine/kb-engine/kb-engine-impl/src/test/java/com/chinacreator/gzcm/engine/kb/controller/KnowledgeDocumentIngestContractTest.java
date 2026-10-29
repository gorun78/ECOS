package com.chinacreator.gzcm.engine.kb.controller;

import com.chinacreator.gzcm.common.annotation.RequirePermission;
import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.kb.doc.model.KbDoc;
import com.chinacreator.gzcm.engine.kb.dto.KnowledgeDocumentIngestListVO;
import com.chinacreator.gzcm.engine.kb.dto.KnowledgeDocumentIngestRetryVO;
import com.chinacreator.gzcm.engine.kb.dto.KnowledgeDocumentIngestRowVO;
import com.chinacreator.gzcm.engine.kb.dto.KnowledgeDocumentIngestSubmitVO;
import com.chinacreator.gzcm.engine.kb.service.KnowledgeDocIngestService;
import com.chinacreator.gzcm.engine.kb.support.KbErrorCodes;
import com.chinacreator.gzcm.engine.kb.task.KbDocIngestTaskParser;
import com.chinacreator.gzcm.runtime.core.task.model.TaskDescription;
import com.chinacreator.gzcm.runtime.core.task.service.ITaskManagementService;
import com.chinacreator.gzcm.runtime.core.task.service.ITaskManagementService.TaskManagementException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.multipart.MultipartFile;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 附录A 第 11 条契约门（#146z）：{@code /api/v1/knowledge/ingest/documents} 三端点。
 *
 * <p>钉九件事：① 路径字面量逐字等于契约（无 {@code //}、无拼写漂移），且存量
 * {@code /docs/ingest} 面不受影响（API 只增不改）；② multipart 入参形态 + D/B 档权限词表；
 * ③ 空文件／字段校验失败 400 显式承载，且不落近源层、不派任务；
 * ④ 类型白名单 415／大小上限 413 以 {@code ECOS-KB-031} 携带上浮（advice 单源渲染），
 * <b>拒绝即不落行</b>；⑤ MinIO 不可用 500 {@code ECOS-KB-030}，任务派发腿不可达
 * （§7.5.1 状态矩阵「阻断=是／不入草稿」）；⑥ 受理成功 = 202 + {@code {taskId,objectKey}}，
 * 派发形态为 {@code KB_DOC_INGEST} + {@code async=true}（禁 Web 线程同步跑流水线）；
 * ⑦ runtime-task 不可用 = 503 {@code ECOS-KB-021}，禁降级本地线程；
 * ⑧ 列表腿 status 词表闸（非法 400，不做无谓词的假过滤）+ 分页钳制（默认 50／上限 500）
 * + 行投影不合成时间；⑨ retry 腿无行 404、缺幂等键载体 500/030，禁 200 空面。</p>
 *
 * <p>harness 与 {@code KnowledgeExtractRunsContractTest}／{@code KbProfileStatusFidelityTest}
 * 同族：Mockito + 直调 Controller 看 {@code getStatusCode()}／抛出的携带异常——
 * common-api 的静态棘轮门只认源码形态，对「形态对、状态值错」结构性失明。</p>
 */
@ExtendWith(MockitoExtension.class)
class KnowledgeDocumentIngestContractTest {

    private static final String DOC_ID = "doc-1";
    private static final String OBJECT_KEY = "raw/unstructured/hr/doc-1/a.pdf";

    @Mock private KnowledgeDocIngestService docIngestService;
    @Mock private ITaskManagementService taskManagement;

    private KnowledgeDocumentIngestController controller() {
        return new KnowledgeDocumentIngestController(docIngestService, taskManagement);
    }

    private static KnowledgeDocIngestService.IngestUpload upload() {
        return new KnowledgeDocIngestService.IngestUpload(DOC_ID, "hr", OBJECT_KEY, "a.pdf",
                "application/pdf", 1024L, 512, 64);
    }

    private static MockMultipartFile pdf() {
        return new MockMultipartFile("file", "a.pdf", "application/pdf", new byte[]{1, 2, 3});
    }

    private static void assertStatusAndCode(ResponseEntity<? extends ApiResponse<?>> resp,
                                            int expectedStatus,
                                            String expectedMachineCode) {
        assertEquals(expectedStatus, resp.getStatusCode().value(), "状态行必须等于契约期望值");
        ApiResponse<?> body = resp.getBody();
        assertNotNull(body, "ResponseEntity 必须携带信封");
        if (expectedStatus < 400) {
            // 信封 code 同号只适用于失败面；成功面单源语义 = ApiResponse.success 固定 code=0。
            assertEquals(0, body.getCode(), "成功信封 code 必须为 0（ApiResponse.success 单源）");
        } else {
            assertEquals(expectedStatus, body.getCode(), "失败信封 code 必须与状态行同号");
        }
        if (expectedMachineCode != null) {
            assertEquals(expectedMachineCode, body.getErrorCode(), "机读码必须取 KbErrorCodes 单源");
        }
    }

    private static void assertCarried(KbErrorCodes.KbError error, Throwable thrown,
                                      int expectedStatus, String expectedCode) {
        assertEquals(expectedStatus, error.httpStatus(),
                "契约期望状态行必须与 KbErrorCodes 单源同值（否则是本测试造数；状态由服务侧显式构造的腿请走 assertCarriedStatusOverride）");
        assertEquals(expectedCode, error.code(),
                "契约期望机读码必须与 KbErrorCodes 单源同值");
        assertThrownCarries(thrown, expectedStatus, expectedCode);
    }

    /**
     * 状态行由服务侧显式构造、与枚举默认态不同值的携带腿。
     *
     * <p>唯一实例：大小上限复用机读码 {@code ECOS-KB-031} 但状态行为 413（枚举默认 415 留给类型不符）。
     * 本方法只钉「机读码取单源」+「控制器不吞、原样以 DomainException 上浮」；413 本身由
     * {@code KnowledgeDocIngestUploadGateTest#rejectsOversizeWith413} 在真实服务上钉死，
     * 不在这里用测试值冒充服务判定。</p>
     */
    private static void assertCarriedStatusOverride(KbErrorCodes.KbError error, Throwable thrown,
                                                   int expectedStatus, String expectedCode) {
        assertEquals(expectedCode, error.code(),
                "契约期望机读码必须与 KbErrorCodes 单源同值");
        assertTrue(error.httpStatus() != expectedStatus,
                "本辅助方法专用于状态行覆盖枚举默认态的腿；同值腿请走 assertCarried 以享受单源校验");
        assertThrownCarries(thrown, expectedStatus, expectedCode);
    }

    private static void assertThrownCarries(Throwable thrown, int expectedStatus,
                                            String expectedCode) {
        KbErrorCodes.DomainException dex = assertThrows(KbErrorCodes.DomainException.class,
                () -> {
                    throw thrown;
                }, expectedCode + " 腿必须以 DomainException 上浮到 KbErrorAdvice 单源渲染");
        assertEquals(expectedStatus, dex.getHttpStatus(), "携带状态行 = " + expectedStatus);
        assertEquals(expectedCode, dex.getCode(), "携带机读码 = " + expectedCode);
    }

    // ── ① 契约路径字面量 + 存量面零改动 ────────────────────────────────────

    @Test
    @DisplayName("三端点路径逐字等于附录A 第 11 条契约字面量，无 // 漂移")
    void contractPathsAreLiteral() throws Exception {
        RequestMapping root = KnowledgeDocumentIngestController.class
                .getAnnotation(RequestMapping.class);
        assertNotNull(root, "类级 @RequestMapping 必须存在");
        assertEquals("/api/v1/knowledge/ingest", root.value()[0]);

        Method submit = KnowledgeDocumentIngestController.class.getMethod("submitDocument",
                MultipartFile.class, String.class, String.class, Integer.class, Integer.class);
        Method list = KnowledgeDocumentIngestController.class.getMethod("listDocuments",
                String.class, int.class, int.class);
        Method retry = KnowledgeDocumentIngestController.class.getMethod("retryDocument",
                String.class);

        assertEquals("/documents", submit.getAnnotation(
                org.springframework.web.bind.annotation.PostMapping.class).value()[0]);
        assertEquals("/documents", list.getAnnotation(
                org.springframework.web.bind.annotation.GetMapping.class).value()[0]);
        assertEquals("/documents/{id}/retry", retry.getAnnotation(
                org.springframework.web.bind.annotation.PostMapping.class).value()[0]);

        for (Method m : List.of(submit, list, retry)) {
            String path = root.value()[0] + first(m);
            assertFalseContainsDoubleSlash(path);
        }
        assertEquals("/api/v1/knowledge/ingest/documents", root.value()[0] + first(submit));
        assertEquals("/api/v1/knowledge/ingest/documents", root.value()[0] + first(list));
        assertEquals("/api/v1/knowledge/ingest/documents/{id}/retry", root.value()[0] + first(retry));
    }

    @Test
    @DisplayName("存量 /docs/ingest 面未被本批改动（API 只增不改红线）")
    void legacyIngestFaceUntouched() {
        RequestMapping legacy = KnowledgeIngestController.class.getAnnotation(RequestMapping.class);
        assertNotNull(legacy, "存量控制器保留");
        assertTrue(List.of(legacy.value()).contains("/api/v1/knowledge"),
                "存量基路径不变");
        boolean hasDocsIngest = false;
        for (Method m : KnowledgeIngestController.class.getDeclaredMethods()) {
            var post = m.getAnnotation(org.springframework.web.bind.annotation.PostMapping.class);
            if (post != null && List.of(post.value()).contains("/docs/ingest")) {
                hasDocsIngest = true;
            }
        }
        assertTrue(hasDocsIngest, "存量 /docs/ingest 必须仍在（新面是 additive，不替换）");
    }

    private static String first(Method m) {
        org.springframework.web.bind.annotation.PostMapping post =
                m.getAnnotation(org.springframework.web.bind.annotation.PostMapping.class);
        if (post != null) {
            return post.value()[0];
        }
        return m.getAnnotation(org.springframework.web.bind.annotation.GetMapping.class).value()[0];
    }

    private static void assertFalseContainsDoubleSlash(String path) {
        assertTrue(!path.contains("//"), "路径不得出现 //（双斜杠=映射静默失效）：" + path);
    }

    @Test
    @DisplayName("提交/重放 = D 档写权限，列表 = B 档读权限；提交腿声明 multipart")
    void permissionAndConsumesShape() throws Exception {
        Method submit = KnowledgeDocumentIngestController.class.getMethod("submitDocument",
                MultipartFile.class, String.class, String.class, Integer.class, Integer.class);
        Method list = KnowledgeDocumentIngestController.class.getMethod("listDocuments",
                String.class, int.class, int.class);
        Method retry = KnowledgeDocumentIngestController.class.getMethod("retryDocument",
                String.class);

        assertEquals("knowledge:ingest:write",
                submit.getAnnotation(RequirePermission.class).permission());
        assertEquals("knowledge:ingest:read",
                list.getAnnotation(RequirePermission.class).permission());
        assertEquals("knowledge:ingest:write",
                retry.getAnnotation(RequirePermission.class).permission());
        assertTrue(List.of(submit.getAnnotation(org.springframework.web.bind.annotation
                        .PostMapping.class).consumes())
                .contains(MediaType.MULTIPART_FORM_DATA_VALUE),
                "提交腿必须声明 multipart consumes（契约「multipart/form-data」）");
    }

    // ── ②③ 入参闸：空文件 / 字段校验 ⇒ 400 且不落库不派任务 ────────────────

    @Test
    @DisplayName("POST /documents file 缺省 ⇒ 400，服务与任务底座零交互")
    void submitRejectsMissingFile() {
        ResponseEntity<ApiResponse<KnowledgeDocumentIngestSubmitVO>> resp =
                controller().submitDocument(null, null, null, null, null);
        assertStatusAndCode(resp, 400, null);
        assertTrue(resp.getBody().getMessage().contains("file"), "拒绝文案须点名入参");
        verifyNoInteractions(docIngestService, taskManagement);
    }

    @Test
    @DisplayName("POST /documents 空文件 ⇒ 400，服务与任务底座零交互")
    void submitRejectsEmptyFile() {
        MockMultipartFile empty = new MockMultipartFile("file", "a.pdf", "application/pdf",
                new byte[]{});
        ResponseEntity<ApiResponse<KnowledgeDocumentIngestSubmitVO>> resp =
                controller().submitDocument(empty, null, null, null, null);
        assertStatusAndCode(resp, 400, null);
        verifyNoInteractions(docIngestService, taskManagement);
    }

    @Test
    @DisplayName("prepareUpload 抛 ValidationException ⇒ 400，不写 MinIO、不派任务")
    void submitMapsValidationToBadRequest() {
        when(docIngestService.prepareUpload(any(), any(), any(), any(), any()))
                .thenThrow(new com.chinacreator.gzcm.common.exception.ValidationException(
                        "file", "上传文件不能为空"));
        ResponseEntity<ApiResponse<KnowledgeDocumentIngestSubmitVO>> resp =
                controller().submitDocument(pdf(), null, null, null, null);
        assertStatusAndCode(resp, 400, null);
        verify(docIngestService, never()).storeAndRegisterQueued(any(), any());
        verifyNoInteractions(taskManagement);
    }

    // ── ④ 类型/大小闸：415/413 + ECOS-KB-031，拒绝即不落行 ─────────────────

    @Test
    @DisplayName("白名单外类型 ⇒ 415 + ECOS-KB-031 携带上浮，MinIO/落行/派任务腿全不可达")
    void submitRejectsTypeOutsideWhitelist() {
        when(docIngestService.prepareUpload(any(), any(), any(), any(), any()))
                .thenThrow(KbErrorCodes.UPLOAD_INVALID.exception("上传文件类型不在白名单"));
        Throwable thrown = assertThrows(Throwable.class,
                () -> controller().submitDocument(pdf(), null, null, null, null));
        assertCarried(KbErrorCodes.UPLOAD_INVALID, thrown, 415, "ECOS-KB-031");
        verify(docIngestService, never()).storeAndRegisterQueued(any(), any());
        verifyNoInteractions(taskManagement);
    }

    @Test
    @DisplayName("超大小上限 ⇒ 413 + ECOS-KB-031 携带上浮（与 415 同号不同状态，禁折叠）")
    void submitRejectsOversize() {
        when(docIngestService.prepareUpload(any(), any(), any(), any(), any()))
                .thenThrow(new KbErrorCodes.DomainException(413, KbErrorCodes.UPLOAD_INVALID.code(),
                        "上传文件超大小上限"));
        Throwable thrown = assertThrows(Throwable.class,
                () -> controller().submitDocument(pdf(), null, null, null, null));
        // 状态行 413 系服务侧显式构造（枚举 UPLOAD_INVALID 默认 415 留给类型不符），非测试造数：
        // 真实值由 KnowledgeDocIngestUploadGateTest#rejectsOversizeWith413 在服务上钉死。
        assertCarriedStatusOverride(KbErrorCodes.UPLOAD_INVALID, thrown, 413, "ECOS-KB-031");
        verify(docIngestService, never()).storeAndRegisterQueued(any(), any());
        verifyNoInteractions(taskManagement);
    }

    // ── ⑤ MinIO 不可用 ⇒ 500 ECOS-KB-030，不落任务 ─────────────────────────

    @Test
    @DisplayName("近源层写失败 ⇒ 500 ECOS-KB-030，禁派一个注定失败的任务")
    void submitMapsMinioFailureToCarried500() {
        when(docIngestService.prepareUpload(any(), any(), any(), any(), any())).thenReturn(upload());
        when(docIngestService.storeAndRegisterQueued(any(), any()))
                .thenThrow(KbErrorCodes.OBJECT_STORAGE_UNAVAILABLE.exception("MinIO 不可用"));
        Throwable thrown = assertThrows(Throwable.class,
                () -> controller().submitDocument(pdf(), null, null, null, null));
        assertCarried(KbErrorCodes.OBJECT_STORAGE_UNAVAILABLE, thrown, 500, "ECOS-KB-030");
        verifyNoInteractions(taskManagement);
    }

    @Test
    @DisplayName("kb_doc 表故障（DataAccessException）原样上浮，不被折成 400/404")
    void submitPropagatesDataAccessExceptionToAdvice() {
        when(docIngestService.prepareUpload(any(), any(), any(), any(), any())).thenReturn(upload());
        when(docIngestService.storeAndRegisterQueued(any(), any()))
                .thenThrow(new DataAccessResourceFailureException("kb_doc 表缺失"));
        assertThrows(DataAccessResourceFailureException.class,
                () -> controller().submitDocument(pdf(), null, null, null, null));
        verifyNoInteractions(taskManagement);
    }

    // ── ⑥ 受理成功 ⇒ 202 + runtime-task 派发形态 ───────────────────────────

    @Test
    @DisplayName("POST /documents 受理 ⇒ 202 {taskId,objectKey,docId,status=SUBMITTED}")
    void submitReturns202WithTaskIdAndObjectKey() throws TaskManagementException {
        when(docIngestService.prepareUpload(any(), any(), any(), any(), any())).thenReturn(upload());
        when(taskManagement.submitTask(any(TaskDescription.class))).thenReturn("kbdi-t-1");

        ResponseEntity<ApiResponse<KnowledgeDocumentIngestSubmitVO>> resp =
                controller().submitDocument(pdf(), "hr", DOC_ID, 512, 64);

        assertStatusAndCode(resp, 202, null);
        KnowledgeDocumentIngestSubmitVO data = resp.getBody().getData();
        assertNotNull(data);
        assertEquals("kbdi-t-1", data.getTaskId(), "taskId = runtime-task 真实任务 ID");
        assertEquals(OBJECT_KEY, data.getObjectKey(), "objectKey = 近源层对象 key（契约出参键）");
        assertEquals(DOC_ID, data.getDocId());
        assertEquals("SUBMITTED", data.getStatus(), "202 语义固定 SUBMITTED，禁谎报 DONE");
    }

    @Test
    @DisplayName("派发形态 = KB_DOC_INGEST + async=true + docId/objectKey 参数键面")
    void submitDispatchesAsyncRuntimeTask() throws TaskManagementException {
        when(docIngestService.prepareUpload(any(), any(), any(), any(), any())).thenReturn(upload());
        when(taskManagement.submitTask(any(TaskDescription.class))).thenReturn("kbdi-t-1");

        controller().submitDocument(pdf(), "hr", DOC_ID, 512, 64);

        ArgumentCaptor<TaskDescription> captor = ArgumentCaptor.forClass(TaskDescription.class);
        verify(taskManagement).submitTask(captor.capture());
        TaskDescription desc = captor.getValue();
        assertEquals(KbDocIngestTaskParser.TASK_TYPE, desc.getTaskType(),
                "任务类型单源 = KB_DOC_INGEST（与 Parser/Executor/Registrar 同名）");
        assertEquals(Boolean.TRUE, desc.getAsync(),
                "async=true：受理即返回真 202，禁在 Web 线程同步跑解析流水线");
        assertNotNull(desc.getTaskId(), "taskId 非空（任务中心可查）");
        assertTrue(desc.getTaskId().startsWith("kbdi-"), "taskId 前缀标识本族");
        Map<String, Object> params = desc.getParameters();
        assertNotNull(params);
        assertEquals(DOC_ID, params.get("docId"), "执行腿按 docId 回读行（参数键面单源）");
        assertEquals(OBJECT_KEY, params.get("objectKey"), "幂等键随参数下发");
        assertEquals("KB_DOC_INGEST", params.get("biz_kind"));
        assertNotNull(desc.getTaskName(), "taskName 非空（任务中心可读）");
    }

    // ── ⑦ runtime-task 不可用 ⇒ 503 ECOS-KB-021 ────────────────────────────

    @Test
    @DisplayName("submitTask 抛 TaskManagementException ⇒ 503 ECOS-KB-021，禁降级本地线程")
    void submitMapsTaskBaseFailureTo503() throws TaskManagementException {
        when(docIngestService.prepareUpload(any(), any(), any(), any(), any())).thenReturn(upload());
        when(taskManagement.submitTask(any(TaskDescription.class)))
                .thenThrow(new TaskManagementException("task 底座不可用"));

        Throwable thrown = assertThrows(Throwable.class,
                () -> controller().submitDocument(pdf(), null, null, null, null));
        assertCarried(KbErrorCodes.TASK_BASE_UNAVAILABLE, thrown, 503, "ECOS-KB-021");
        assertTrue(thrown.getMessage().contains("禁降级"),
                "文案须点名「禁降级为本地线程」的裁决口径");
        // 受理顺序如实：MinIO+落行在前，派发失败不回滚（登记台账余项）
        verify(docIngestService).storeAndRegisterQueued(any(), any());
    }

    @Test
    @DisplayName("retry 的 runtime-task 不可用同样 503 ECOS-KB-021")
    void retryMapsTaskBaseFailureTo503() throws TaskManagementException {
        when(docIngestService.findDoc(DOC_ID)).thenReturn(doc(OBJECT_KEY, "failed"));
        when(taskManagement.submitTask(any(TaskDescription.class)))
                .thenThrow(new TaskManagementException("task 底座不可用"));

        Throwable thrown = assertThrows(Throwable.class, () -> controller().retryDocument(DOC_ID));
        assertCarried(KbErrorCodes.TASK_BASE_UNAVAILABLE, thrown, 503, "ECOS-KB-021");
    }

    // ── ⑧ 列表腿：词表闸 / 钳制 / 投影 ─────────────────────────────────────

    @Test
    @DisplayName("GET /documents?status=RUNNING ⇒ 400（状态词表外取值不得静默当「全部」）")
    void listRejectsStatusOutsideVocabulary() {
        ResponseEntity<ApiResponse<KnowledgeDocumentIngestListVO>> resp =
                controller().listDocuments("RUNNING", 1, 50);
        assertStatusAndCode(resp, 400, null);
        assertTrue(resp.getBody().getMessage().contains("status"), "拒绝文案须点名 status");
        verify(docIngestService, never()).listDocs(any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("GET /documents?status=Done ⇒ 归一小写后作为真谓词下推并回显")
    void listNormalizesStatusAndPassesPredicate() {
        when(docIngestService.listDocs(eq("done"), anyInt(), anyInt())).thenReturn(List.of());
        when(docIngestService.countDocs("done")).thenReturn(0L);

        ResponseEntity<ApiResponse<KnowledgeDocumentIngestListVO>> resp =
                controller().listDocuments("Done", 1, 50);
        assertStatusAndCode(resp, 200, null);
        assertEquals("done", resp.getBody().getData().getStatus(), "回显归一后的谓词值");
        verify(docIngestService).listDocs("done", 50, 0);
    }

    @Test
    @DisplayName("GET /documents 分页钳制：size>500 归 500，page<1 归 1，size<1 归 1")
    void listClampsPagination() {
        when(docIngestService.listDocs(isNull(), anyInt(), anyInt())).thenReturn(List.of());
        when(docIngestService.countDocs(isNull())).thenReturn(0L);

        controller().listDocuments(null, 0, 9_999);
        verify(docIngestService).listDocs(null, 500, 0);

        controller().listDocuments(null, -3, -1);
        verify(docIngestService).listDocs(null, 1, 0);

        controller().listDocuments(null, 3, 20);
        verify(docIngestService).listDocs(null, 20, 40);
    }

    @Test
    @DisplayName("列表行投影 = kb_doc 在册列，时间列 ISO；无值保持 null（禁合成）")
    void listProjectsRealRowsWithoutSynthesis() {
        KbDoc row = doc(OBJECT_KEY, "done");
        row.setCreatedAt(LocalDateTime.of(2026, 10, 29, 8, 30, 0));
        row.setUpdatedAt(null);
        when(docIngestService.listDocs(isNull(), anyInt(), anyInt())).thenReturn(List.of(row));
        when(docIngestService.countDocs(isNull())).thenReturn(7L);

        ResponseEntity<ApiResponse<KnowledgeDocumentIngestListVO>> resp =
                controller().listDocuments(null, 1, 50);
        KnowledgeDocumentIngestListVO data = resp.getBody().getData();
        assertEquals(7L, data.getTotal(), "total 取 countDocs 真值，禁以本页条数冒充");
        assertEquals(1, data.getItems().size());
        KnowledgeDocumentIngestRowVO item = data.getItems().get(0);
        assertEquals(DOC_ID, item.getDocId());
        assertEquals(OBJECT_KEY, item.getObjectKey());
        assertEquals("done", item.getParseStatus());
        assertEquals("2026-10-29T08:30:00", item.getCreatedAt());
        assertNull(item.getUpdatedAt(), "空时间列保持 null，不填当前时间冒充");
    }

    @Test
    @DisplayName("列表腿表故障 ⇒ DataAccessException 上浮 advice（禁伪 404/空列表）")
    void listPropagatesTableFailure() {
        when(docIngestService.listDocs(isNull(), anyInt(), anyInt()))
                .thenThrow(new DataAccessResourceFailureException("kb_doc 表缺失"));
        assertThrows(DataAccessResourceFailureException.class,
                () -> controller().listDocuments(null, 1, 50));
    }

    // ── ⑨ retry 腿：404 / 缺幂等键 / 202 ───────────────────────────────────

    @Test
    @DisplayName("retry docId 空白 ⇒ 400，不查库不派任务")
    void retryRejectsBlankDocId() {
        ResponseEntity<ApiResponse<KnowledgeDocumentIngestRetryVO>> resp =
                controller().retryDocument("   ");
        assertStatusAndCode(resp, 400, null);
        verifyNoInteractions(taskManagement);
    }

    @Test
    @DisplayName("retry 无行 ⇒ 404 显式承载（禁 200 空面），不派任务")
    void retryReturns404ForUnknownDocId() {
        when(docIngestService.findDoc("missing")).thenReturn(null);
        ResponseEntity<ApiResponse<KnowledgeDocumentIngestRetryVO>> resp =
                controller().retryDocument("missing");
        assertStatusAndCode(resp, 404, null);
        assertTrue(resp.getBody().getMessage().contains("missing"));
        verifyNoInteractions(taskManagement);
    }

    @Test
    @DisplayName("retry 行缺 objectKey 幂等键 ⇒ 500 ECOS-KB-030，不派注定失败的任务")
    void retryRejectsRowWithoutIdempotencyKey() {
        when(docIngestService.findDoc(DOC_ID)).thenReturn(doc(null, "queued"));
        Throwable thrown = assertThrows(Throwable.class, () -> controller().retryDocument(DOC_ID));
        assertCarried(KbErrorCodes.OBJECT_STORAGE_UNAVAILABLE, thrown, 500, "ECOS-KB-030");
        verifyNoInteractions(taskManagement);
    }

    @Test
    @DisplayName("retry 成功 ⇒ 202 {taskId,docId,objectKey}，重派同一 KB_DOC_INGEST")
    void retryReturns202AndRedispatches() throws TaskManagementException {
        when(docIngestService.findDoc(DOC_ID)).thenReturn(doc(OBJECT_KEY, "failed"));
        when(taskManagement.submitTask(any(TaskDescription.class))).thenReturn("kbdi-t-2");

        ResponseEntity<ApiResponse<KnowledgeDocumentIngestRetryVO>> resp =
                controller().retryDocument(DOC_ID);
        assertStatusAndCode(resp, 202, null);
        KnowledgeDocumentIngestRetryVO data = resp.getBody().getData();
        assertEquals("kbdi-t-2", data.getTaskId());
        assertEquals(DOC_ID, data.getDocId());
        assertEquals(OBJECT_KEY, data.getObjectKey(), "重放沿用同一幂等键对象");
        assertEquals("SUBMITTED", data.getStatus());

        ArgumentCaptor<TaskDescription> captor = ArgumentCaptor.forClass(TaskDescription.class);
        verify(taskManagement).submitTask(captor.capture());
        assertEquals(KbDocIngestTaskParser.TASK_TYPE, captor.getValue().getTaskType());
        assertEquals(Boolean.TRUE, captor.getValue().getAsync());
        assertEquals(OBJECT_KEY, captor.getValue().getParameters().get("objectKey"));
    }

    // ── 夹具 ──────────────────────────────────────────────────────────────

    private static KbDoc doc(String objectKey, String parseStatus) {
        KbDoc d = new KbDoc();
        d.setDocId(DOC_ID);
        d.setSource("hr");
        d.setOriginalFileName("a.pdf");
        d.setObjectKey(objectKey);
        d.setContentType("application/pdf");
        d.setSizeBytes(1024L);
        d.setParseStatus(parseStatus);
        d.setChunkSize(512);
        d.setChunkOverlap(64);
        d.setChunkCount(3);
        return d;
    }
}
