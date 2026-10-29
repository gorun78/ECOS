package com.chinacreator.gzcm.engine.kb.controller;

import com.chinacreator.gzcm.common.annotation.RequirePermission;
import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.exception.ValidationException;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 文档接入契约面 Controller — 《ECOS详细设计说明书-从零实施版-2026-10-12》第 7 篇
 * §7.3.2 端点总表第 4866/4867 行（= 台账《从零实施版-实现差距分析》附录A 缺失清单第 11 条：
 * 契约路径 {@code /api/v1/knowledge/ingest/documents} 与既存 {@code /docs/ingest} 偏差，
 * 本类按契约字面路径 additive 补齐，存量路径零改动）。
 *
 * <p>三端点（路径/入参/出参逐字承契约）：</p>
 * <ul>
 *   <li><b>{@code POST /api/v1/knowledge/ingest/documents}</b>（档 D，multipart）→ 202
 *       {@code {taskId,objectKey}}：类型/大小闸（ECOS-KB-031）→ MinIO RAW 写（失败 500
 *       ECOS-KB-030，<b>不落 kb_doc 行</b>，承 §7.5.5 降级矩阵「MinIO … 阻断=是」）→ queued 登记 →
 *       runtime-task {@code KB_DOC_INGEST}（async=true 真异步，失败同步 503 ECOS-KB-021，
 *       禁自建线程池兜底）；</li>
 *   <li><b>{@code GET /api/v1/knowledge/ingest/documents}</b>（档 B）→
 *       {@code {items,total,page,size}}（{@code ecos_knowledge.kb_doc} 真实行，status 真谓词）；</li>
 *   <li><b>{@code POST /api/v1/knowledge/ingest/documents/{id}/retry}</b>（档 D）→ 202
 *       {@code {taskId}}：无行 404（禁 200 空面），有行按幂等键 objectKey 重派同一任务类型。</li>
 * </ul>
 *
 * <p><b>与存量 {@code POST /docs/ingest} 的单源关系</b>：三条腿（校验 {@code prepareUpload}、
 * 近源层写 {@code storeAndRegisterQueued}、阶段流水线 {@code runByDocId}→{@code runStages}）
 * 全部复用 {@link KnowledgeDocIngestService} 的同一份实现——本类只是把「同步全链一口气跑完」
 * 拆成「受理腿当场落原文+queued、执行腿后台跑阶段」的契约形态，解析/分块/向量化/登记语义
 * 与存量逐字一致（铁律 §2.5-6 禁两处编排漂移）。差异只在 HTTP 面：存量恒 200 信封
 * （其控制器宽 catch 收 500），本类按契约出 202/404/500/503 状态行。</p>
 *
 * <p><b>状态行保真（D-B4-59 同族纪律）</b>：400 入参／404 未知 docId 经 {@code ResponseEntity}
 * 显式承载；415/413（ECOS-KB-031）、500（ECOS-KB-030）、503（ECOS-KB-021）改抛
 * {@link KbErrorCodes} 携带的 {@code DomainException}，由 {@code KbErrorAdvice} 单源渲染，
 * 禁本类自造第二套落法；500 表缺失由 {@code DataAccessException} 上浮到同一 advice
 * （ECOS-KB-050；契约行误差列写 030/021，读面表故障落 050 的差异如实登记台账余项）。</p>
 *
 * <p><b>如实声明（禁假值）</b>：</p>
 * <ul>
 *   <li>类型白名单/大小上限详设未给闭集 ⇒ 配置化实现假设
 *       （{@code ecos.kb.doc.allowed-content-types} / {@code ecos.kb.doc.max-size-mb}，
 *       默认值登记台账待裁决，不硬编码进代码常量面）；</li>
 *   <li>对象 key 沿用既存 {@code raw/unstructured/{source}/{docId}/{fileName}} 形态（数据形态
 *       变更超出别名补齐范围，与契约 §7.5.1 步骤 1 字面
 *       {@code raw/unstructured/{domain}/{yyyy}/{mm}/{hash}} 的差异（{@code {domain}}↔
 *       {@code {source}}、{@code {yyyy}/{mm}/{hash}}↔{@code {docId}/{fileName}}）登记台账余项）；</li>
 *   <li>{@code list} 腿无异步语义 ⇒ 契约列的 {@code 021}（任务底座）在本腿不可达，
 *       不凑一条假路径；</li>
 *   <li><b>权限档位与词表两处差异（本批不擅改属主面，如实登记）</b>：§7.3.1 的档位词表写作
 *       {@code kb:*:read}/{@code kb:*:write}，而 kb-engine 既存 24 枚 {@code @RequirePermission}
 *       全用 {@code knowledge:*} 词表（本类三枚与之同族：写腿 {@code knowledge:ingest:write}、
 *       读腿 {@code knowledge:ingest:read}），改词表属全局权限面变更，超出别名补齐范围；
 *       且契约行 4867 把 {@code GET} 记为<b>档 B</b>（仅 JWT+安全裁决），本类读腿按既存同族惯例
 *       挂了 {@code read} 权限（实际严到<b>档 C</b>）——比契约更严而非更松，但确是分档差异，
 *       不据此宣布第 11 条收口。</li>
 * </ul>
 *
 * @group INGEST
 */
@RestController
@RequestMapping("/api/v1/knowledge/ingest")
public class KnowledgeDocumentIngestController {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeDocumentIngestController.class);

    /** 分页默认值（§7.3.1：默认 50 上限 500）。 */
    private static final int DEFAULT_PAGE_SIZE = 50;

    /** 分页上限（§7.3.1）。 */
    private static final int MAX_PAGE_SIZE = 500;

    /** 时间列回显格式（ISO-8601 本地时间，无时区假值）。 */
    private static final DateTimeFormatter TS_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final KnowledgeDocIngestService docIngestService;

    private final ITaskManagementService taskManagement;

    public KnowledgeDocumentIngestController(KnowledgeDocIngestService docIngestService,
                                             ITaskManagementService taskManagement) {
        this.docIngestService = docIngestService;
        this.taskManagement = taskManagement;
    }

    // ─────────────────────────────────────────────────────────────────────
    // POST /documents — 文档接入提交（202）
    // ─────────────────────────────────────────────────────────────────────

    /**
     * 受理一次文档接入（档 D）：原文落近源层 + kb_doc queued 行后派 runtime-task，
     * 解析流水线（登记原文→解析→分块→向量化→登记 CURATED）由执行腿
     * {@code KbDocIngestTaskExecutor} 异步续跑。
     *
     * <p>准入顺序即契约状态矩阵行序：① 类型/大小闸（415/413 ECOS-KB-031）→
     * ② MinIO RAW 写（500 ECOS-KB-030，失败不落行）→ ③ queued 登记 →
     * ④ 任务派发（503 ECOS-KB-021；派发失败行已 queued，矩阵「FAILED 不入草稿」以
     * audit log + 行状态可观测，不悄悄回滚已写对象——原文对象与台账行的一致性口径
     * 登记台账余项）。</p>
     */
    @PostMapping(value = "/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequirePermission(value = "提交文档接入", permission = "knowledge:ingest:write")
    public ResponseEntity<ApiResponse<KnowledgeDocumentIngestSubmitVO>> submitDocument(
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "source", required = false) String source,
            @RequestParam(value = "docId", required = false) String docId,
            @RequestParam(value = "chunkSize", required = false) Integer chunkSize,
            @RequestParam(value = "chunkOverlap", required = false) Integer chunkOverlap) {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.<KnowledgeDocumentIngestSubmitVO>badRequest("file 不能为空（multipart 字段 file）"));
        }
        KnowledgeDocIngestService.IngestUpload upload;
        try {
            upload = docIngestService.prepareUpload(file, source, docId, chunkSize, chunkOverlap);
        } catch (ValidationException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.<KnowledgeDocumentIngestSubmitVO>badRequest(e.getMessage()));
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.<KnowledgeDocumentIngestSubmitVO>badRequest("读取上传文件失败: " + e.getMessage()));
        }

        // ②③) MinIO 写 + queued 登记（DomainException 030 原样上浮 advice；不落行）
        docIngestService.storeAndRegisterQueued(upload, bytes);

        // ④) runtime-task 派发（async=true：受理即返回，禁 Web 线程同步跑流水线；
        //     KB_PROFILE_GENERATE 先例同型。失败 503 ECOS-KB-021，禁降级本地线程）
        String taskId;
        try {
            taskId = dispatchIngestTask(upload.docId(), upload.objectKey());
        } catch (TaskManagementException | RuntimeException e) {
            log.error("submitDocumentIngest 派发失败 docId={}: {}", upload.docId(), e.getMessage(), e);
            throw KbErrorCodes.TASK_BASE_UNAVAILABLE.exception(
                    "runtime-task 不可用，禁降级为本地线程同步计算: " + e.getMessage());
        }

        KnowledgeDocumentIngestSubmitVO vo = new KnowledgeDocumentIngestSubmitVO();
        vo.setTaskId(taskId);
        vo.setObjectKey(upload.objectKey());
        vo.setDocId(upload.docId());
        vo.setStatus("SUBMITTED");
        log.info("submitDocumentIngest 已受理 taskId={} docId={} objectKey={} size={}",
                taskId, upload.docId(), upload.objectKey(), upload.sizeBytes());
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success(vo).withTraceIdFromMdc());
    }

    // ─────────────────────────────────────────────────────────────────────
    // GET /documents — 接入记录列表（分页）
    // ─────────────────────────────────────────────────────────────────────

    /**
     * 接入记录列表（档 B）：{@code ecos_knowledge.kb_doc} 真实行，{@code status} 为真谓词
     * （词表 = F2 状态机闭集，非法取值 400 显式拒绝，不静默当「全部」），
     * {@code updated_at} 倒序分页（默认 50 上限 500）。
     *
     * <p>表缺失/不可访问 → {@code DataAccessException} 上浮 {@code KbErrorAdvice} 落
     * 500 ECOS-KB-050（契约行误差列写 030/021；advice 单源优先，差异登记台账余项），
     * 禁伪 404/空列表。</p>
     */
    @GetMapping("/documents")
    @RequirePermission(value = "读取文档接入记录", permission = "knowledge:ingest:read")
    public ResponseEntity<ApiResponse<KnowledgeDocumentIngestListVO>> listDocuments(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "" + DEFAULT_PAGE_SIZE) int size) {
        String normalizedStatus;
        try {
            normalizedStatus = normalizeStatus(status);
        } catch (ValidationException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.<KnowledgeDocumentIngestListVO>badRequest(e.getMessage()));
        }
        int safePage = Math.max(1, page);
        int safeSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        int offset = (safePage - 1) * safeSize;

        List<KbDoc> rows = docIngestService.listDocs(normalizedStatus, safeSize, offset);
        long total = docIngestService.countDocs(normalizedStatus);

        List<KnowledgeDocumentIngestRowVO> items = new ArrayList<>(rows.size());
        for (KbDoc row : rows) {
            items.add(toRowVO(row));
        }

        KnowledgeDocumentIngestListVO vo = new KnowledgeDocumentIngestListVO();
        vo.setItems(items);
        vo.setTotal(total);
        vo.setPage(safePage);
        vo.setSize(safeSize);
        vo.setStatus(normalizedStatus);
        return ResponseEntity.ok(ApiResponse.success(vo).withTraceIdFromMdc());
    }

    // ─────────────────────────────────────────────────────────────────────
    // POST /documents/{id}/retry — 幂等重放（202）
    // ─────────────────────────────────────────────────────────────────────

    /**
     * 重放一次接入处理（档 D）：按 {@code docId} 回读 kb_doc 行取幂等键 objectKey，
     * 重派同一 {@code KB_DOC_INGEST} 任务（执行腿重读同一原文对象，阶段产物按
     * {@code docId#chunkIndex} 确定性主键 upsert，天然幂等）。
     *
     * <p>无行 ⇒ 404 显式承载（禁 200 空面）；行缺 objectKey 载体 ⇒ 500 ECOS-KB-030
     * （服务腿抛承载，不假装可重放）；任务底座不可用 ⇒ 503 ECOS-KB-021。</p>
     */
    @PostMapping("/documents/{id}/retry")
    @RequirePermission(value = "重放文档接入", permission = "knowledge:ingest:write")
    public ResponseEntity<ApiResponse<KnowledgeDocumentIngestRetryVO>> retryDocument(
            @PathVariable("id") String docId) {
        if (docId == null || docId.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.<KnowledgeDocumentIngestRetryVO>badRequest("docId 不能为空"));
        }
        KbDoc doc = docIngestService.findDoc(docId);
        if (doc == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.<KnowledgeDocumentIngestRetryVO>notFound(
                            "未找到接入记录: docId=" + docId));
        }
        // 缺幂等键载体 → 服务腿 500 ECOS-KB-030（此处提前探明，禁派一个注定失败的任务）
        if (doc.getObjectKey() == null || doc.getObjectKey().isBlank()) {
            throw KbErrorCodes.OBJECT_STORAGE_UNAVAILABLE.exception(
                    "接入记录缺幂等键 objectKey 载体，无法重放: docId=" + docId);
        }

        String taskId;
        try {
            taskId = dispatchIngestTask(doc.getDocId(), doc.getObjectKey());
        } catch (TaskManagementException | RuntimeException e) {
            log.error("retryDocumentIngest 派发失败 docId={}: {}", docId, e.getMessage(), e);
            throw KbErrorCodes.TASK_BASE_UNAVAILABLE.exception(
                    "runtime-task 不可用，禁降级为本地线程同步计算: " + e.getMessage());
        }

        KnowledgeDocumentIngestRetryVO vo = new KnowledgeDocumentIngestRetryVO();
        vo.setTaskId(taskId);
        vo.setDocId(doc.getDocId());
        vo.setObjectKey(doc.getObjectKey());
        vo.setStatus("SUBMITTED");
        log.info("retryDocumentIngest 已受理 taskId={} docId={} objectKey={} (前态={})",
                taskId, doc.getDocId(), doc.getObjectKey(), doc.getParseStatus());
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success(vo).withTraceIdFromMdc());
    }

    // ─────────────────────────────────────────────────────────────────────
    // 内部：任务派发 / 参数归一 / 行投影
    // ─────────────────────────────────────────────────────────────────────

    /**
     * 派发 {@code KB_DOC_INGEST}（async=true，真 202 语义；参数键面 {@code docId/objectKey/biz_kind}
     * 与 Parser/Executor 读取面单源对齐）。
     */
    private String dispatchIngestTask(String docId, String objectKey) throws TaskManagementException {
        TaskDescription desc = new TaskDescription();
        desc.setTaskId("kbdi-" + UUID.randomUUID());
        desc.setTaskName("kb_doc_ingest:" + docId);
        desc.setTaskType(KbDocIngestTaskParser.TASK_TYPE);
        desc.setDescription("文档接入解析流水线 docId=" + docId);
        Map<String, Object> params = new HashMap<>();
        params.put("docId", docId);
        params.put("objectKey", objectKey);
        params.put("biz_kind", "KB_DOC_INGEST");
        desc.setParameters(params);
        desc.setAsync(true);
        desc.setCreatedBy("knowledge-document-ingest-controller");
        return taskManagement.submitTask(desc);
    }

    /** 状态词表闸：空 = 全部；大小写不敏感命中 F2 闭集并归一为小写；其他取值 {@code ValidationException}。 */
    private static String normalizeStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim().toLowerCase(Locale.ROOT);
        if (KnowledgeDocIngestService.ALL_STATUSES.contains(value)) {
            return value;
        }
        throw new ValidationException("status", "status 非法: " + raw
                + "（允许 " + String.join("/", KnowledgeDocIngestService.ALL_STATUSES.stream()
                        .sorted().toList()) + "）");
    }

    /** kb_doc 行 → 出参行（在册列逐字段投影；时间列 ISO-8601，无值保持 null 不合成）。 */
    private static KnowledgeDocumentIngestRowVO toRowVO(KbDoc row) {
        KnowledgeDocumentIngestRowVO vo = new KnowledgeDocumentIngestRowVO();
        vo.setDocId(row.getDocId());
        vo.setSource(row.getSource());
        vo.setOriginalFileName(row.getOriginalFileName());
        vo.setObjectKey(row.getObjectKey());
        vo.setContentType(row.getContentType());
        vo.setSizeBytes(row.getSizeBytes());
        vo.setParseStatus(row.getParseStatus());
        vo.setChunkSize(row.getChunkSize());
        vo.setChunkOverlap(row.getChunkOverlap());
        vo.setChunkCount(row.getChunkCount());
        vo.setTextSourcePath(row.getTextSourcePath());
        vo.setErrorMessage(row.getErrorMessage());
        vo.setCreatedAt(row.getCreatedAt() == null ? null : row.getCreatedAt().format(TS_FORMAT));
        vo.setUpdatedAt(row.getUpdatedAt() == null ? null : row.getUpdatedAt().format(TS_FORMAT));
        return vo;
    }
}
