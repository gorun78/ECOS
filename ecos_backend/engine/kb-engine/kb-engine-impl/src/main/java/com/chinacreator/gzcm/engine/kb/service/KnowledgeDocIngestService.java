package com.chinacreator.gzcm.engine.kb.service;

import com.chinacreator.gzcm.common.data.DataDomainErrorCode;
import com.chinacreator.gzcm.common.event.KafkaTopics;
import com.chinacreator.gzcm.common.exception.DataAccessException;
import com.chinacreator.gzcm.common.exception.DataBridgeException;
import com.chinacreator.gzcm.common.exception.ValidationException;
import com.chinacreator.gzcm.engine.kb.doc.mapper.KbDocChunkMapper;
import com.chinacreator.gzcm.engine.kb.doc.mapper.KbDocMapper;
import com.chinacreator.gzcm.engine.kb.doc.model.KbDoc;
import com.chinacreator.gzcm.engine.kb.doc.model.KbDocChunk;
import com.chinacreator.gzcm.engine.kb.dto.DataResourceRegisterRequest;
import com.chinacreator.gzcm.engine.kb.dto.DatalakeUnstructuredRegisterRequest;
import com.chinacreator.gzcm.engine.kb.dto.KnowledgeDocIngestResultVO;
import com.chinacreator.gzcm.engine.kb.dto.KnowledgeVectorWriteItem;
import com.chinacreator.gzcm.engine.kb.dto.KnowledgeVectorWriteResultVO;
import com.chinacreator.gzcm.engine.kb.support.KbAuditEventPublisher;
import com.chinacreator.gzcm.engine.kb.support.KbErrorCodes;
import com.chinacreator.gzcm.runtime.access.document.DocumentChunk;
import com.chinacreator.gzcm.runtime.access.document.DocumentChunkSplitter;
import com.chinacreator.gzcm.runtime.access.document.DocumentParseResult;
import com.chinacreator.gzcm.runtime.access.document.DocumentParseService;
import com.chinacreator.gzcm.runtime.access.endpoint.ServiceEndpointResolver;
import com.chinacreator.gzcm.runtime.access.storage.MinioStorageService;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 非结构化文档登记与解析编排（B5-1，SOP-2 / ADR-2 A3 过渡态）。
 *
 * <p><b>A3 边界（严守，勿越界）</b>：
 * <ul>
 *   <li>原文二进制写入近源层 {@code raw/unstructured/{source}/{docId}/{fileName}}（复用
 *       runtime-access {@link MinioStorageService}，禁 new Driver —— 铁律 §2.5-1）</li>
 *   <li>原文对象经数据工作台 REST {@code POST /api/v1/datanet/datalake/unstructured} 登记为
 *       {@code RAW/UNSTRUCTURED/LAKE_OBJECT}</li>
 *   <li>解析文本落 kb 自有过渡表 {@code ecos_knowledge.kb_doc_chunk}（**不是** DW 层表）</li>
 *   <li>解析文本经数据工作台 REST {@code POST /api/v1/datanet/metadata/resources} 登记为 {@code CURATED}</li>
 *   <li><b>禁止</b> kb 直写 DW 层 CURATED 表（A2 永久否决，铁律 §3.3 跨引擎只读不互写）</li>
 * </ul>
 *
 * <p><b>A1 技术债退出条件</b>：SOURCE_MINIO + 文档解析节点落地后 1 个批次内，本服务的
 * 切分/落库职责迁出至数据工作台解析节点，{@code kb_doc_chunk} 迁入 DW 层（见方案 §3.3）。
 *
 * <p>解析复用 runtime-access 公共能力 {@link DocumentParseService}（Tika &lt;5MB / MinerU ≥5MB）；
 * 切分复用 {@link DocumentChunkSplitter}（B6-2 上移，kb 不再自建）；
 * 向量化复用既有 {@link KnowledgeVectorWriteService}（文本→llm-gateway 批量嵌入→upsert embedding_vec）。
 */
@Service
public class KnowledgeDocIngestService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeDocIngestService.class);

    /** 解析文本登记为 CURATED 资源时的 source_path（库表定位用 schema.table） */
    private static final String PARSED_TEXT_SOURCE_PATH = "ecos_knowledge.kb_doc_chunk";

    private static final String LAYER_CURATED = "CURATED";
    private static final String TYPE_TABLE = "TABLE";

    /**
     * 状态机（F2）。
     *
     * <p>#146z：由 private 放宽为 public —— 契约面 {@code GET /ingest/documents} 的 {@code status}
     * 词表闸与 {@code KB_DOC_INGEST} 执行腿需引用同一组取值（单源，禁第二处字面量定义）。</p>
     */
    public static final String STATUS_QUEUED = "queued";
    public static final String STATUS_PARSING = "parsing";
    public static final String STATUS_EXTRACTING = "extracting";
    public static final String STATUS_DONE = "done";
    public static final String STATUS_FAILED = "failed";

    /** 接入记录状态闭集（列表端点 {@code status} 过滤词表；逐字承 F2 状态机取值）。 */
    public static final Set<String> ALL_STATUSES = Set.of(
            STATUS_QUEUED, STATUS_PARSING, STATUS_EXTRACTING, STATUS_DONE, STATUS_FAILED);

    /** 分块状态 */
    private static final String CHUNK_ACTIVE = "active";

    /** 非结构化对象 key 前缀（分层规范 §三） */
    private static final String UNSTRUCTURED_PREFIX = "raw/unstructured/";

    /** 原文缺失上游数据源时的兜底标识 */
    private static final String DEFAULT_SOURCE = "kb-upload";

    private final KbDocMapper kbDocMapper;
    private final KbDocChunkMapper kbDocChunkMapper;
    /** 文档解析公共能力（runtime-access，B6-2 上移；kb 复用不再自建） */
    private final DocumentParseService documentParserService;
    /** 文档分块公共能力（runtime-access，B6-2 上移；与 data-engine 解析节点共用同一实现） */
    private final DocumentChunkSplitter documentChunkSplitter;
    private final KnowledgeVectorWriteService vectorWriteService;
    private final MinioStorageService minioStorageService;
    private final RestTemplate restTemplate;
    /** data-engine 跨引擎取数 base URL 覆写（可覆写显式配置，默认空 = 走 resolver；F02-15 ①）。 */
    private final String datanetBaseUrlOverride;
    private final int defaultChunkSize;
    private final int defaultChunkOverlap;
    /**
     * 上传类型白名单（#146z 契约 §7.5.1-1「类型白名单」；详设未给闭集 ⇒ 配置化实现假设，
     * 默认 = runtime-access {@link DocumentParseService} 解析能力面相应的常用办公/文本类型，
     * 台账登记待裁决）。逗号分隔 MIME 前缀，比对走 {@link #normalizeContentType} 归一口径。
     */
    private final Set<String> allowedTypePrefixes;
    /** 上传大小上限字节数（#146z 契约「大小上限」；默认 50MB = 实现假设，台账登记待裁决）。 */
    private final long maxSizeBytes;

    /** 跨引擎 base URL 唯一寻址出口（optional；缺席即 fail-loud ECOS-DATA-041，不回落硬编码端口）。 */
    @Autowired(required = false)
    private ServiceEndpointResolver endpointResolver;

    public KnowledgeDocIngestService(KbDocMapper kbDocMapper,
                                     KbDocChunkMapper kbDocChunkMapper,
                                     DocumentParseService documentParserService,
                                     DocumentChunkSplitter documentChunkSplitter,
                                     KnowledgeVectorWriteService vectorWriteService,
                                     MinioStorageService minioStorageService,
                                     RestTemplate restTemplate,
                                     @Value("${ecos.route.datanet-base-url:}") String datanetBaseUrlOverride,
                                     @Value("${ecos.kb.doc.chunk-size:512}") int defaultChunkSize,
                                     @Value("${ecos.kb.doc.chunk-overlap:64}") int defaultChunkOverlap,
                                     @Value("${ecos.kb.doc.allowed-content-types:application/pdf,"
                                             + "application/msword,"
                                             + "application/vnd.openxmlformats-officedocument"
                                             + ".wordprocessingml.document,"
                                             + "application/vnd.ms-excel,"
                                             + "application/vnd.openxmlformats-officedocument"
                                             + ".spreadsheetml.sheet,"
                                             + "application/vnd.ms-powerpoint,"
                                             + "application/vnd.openxmlformats-officedocument"
                                             + ".presentationml.presentation,"
                                             + "text/plain,text/markdown,text/html,image/png,"
                                             + "image/jpeg,image/tiff}") String allowedContentTypes,
                                     @Value("${ecos.kb.doc.max-size-mb:50}") long maxSizeMb) {
        this.kbDocMapper = kbDocMapper;
        this.kbDocChunkMapper = kbDocChunkMapper;
        this.documentParserService = documentParserService;
        this.documentChunkSplitter = documentChunkSplitter;
        this.vectorWriteService = vectorWriteService;
        this.minioStorageService = minioStorageService;
        this.restTemplate = restTemplate;
        this.datanetBaseUrlOverride = datanetBaseUrlOverride;
        this.defaultChunkSize = defaultChunkSize;
        this.defaultChunkOverlap = defaultChunkOverlap;
        this.allowedTypePrefixes = parseAllowedTypes(allowedContentTypes);
        this.maxSizeBytes = maxSizeMb * 1024L * 1024L;
    }

    /**
     * data-engine base URL：覆写非空 → 用户值；否则经 ServiceEndpointResolver 两态寻址
     * （route-manifest owner=datanet）；均不可得 → fail-loud 抛 ECOS-DATA-041（F02-15 ③：
     * 登记失败必须中止 ingest，禁继续 with-empty）。
     */
    private String datanetBaseUrl() {
        if (datanetBaseUrlOverride != null && !datanetBaseUrlOverride.isBlank()) {
            return datanetBaseUrlOverride.trim();
        }
        if (endpointResolver == null) {
            throw crossEngineUnavailable("datanet 服务寻址失败（ServiceEndpointResolver bean 不可用）");
        }
        return endpointResolver.resolve("datanet", ServiceEndpointResolver.RouteMode.MONOLITH)
                .orElseThrow(() -> crossEngineUnavailable(
                        "datanet 服务寻址失败（route-manifest 缺 datanet 条目）"));
    }

    /** 跨引擎依赖不可用 → fail-loud 503 + ECOS-DATA-041（ingest 编排层 catch 中按 DataBridgeException
     *  原样上抛，不再包装为 500 DataAccessException）。 */
    private static DataBridgeException crossEngineUnavailable(String reason) {
        // #146l/B4-3e-93：机读码已切 common-api 属主常量单源（原文案逐字不变）
        return new DataBridgeException(503, 503, DataDomainErrorCode.ECOS_DATA_041 + ": " + reason);
    }

    /**
     * 上传 → 近源层 → 登记原文 → 解析 → 分块落库 → 向量化 → 登记解析文本。
     *
     * <p><b>#146z 单源关系</b>：本方法（存量 {@code POST /docs/ingest} 同步全链语义，逐字保持）
     * 与契约异步腿（{@code storeAndRegisterQueued} + {@link #runByDocId}）共用
     * {@link #prepareUpload} / {@link #uploadNearSource} / {@link #runStages} 三条腿，
     * 阶段编排只此一份（铁律 §2.5-6 禁两处实现漂移）。</p>
     *
     * @param file         上传文件（必填）
     * @param source       上游数据源标识（可空，默认 kb-upload）
     * @param docId        文档 ID（可空，默认生成 UUID）
     * @param chunkSize    分块大小（可空，取配置默认；取值须 ∈ {256,512,1024,2048}）
     * @param chunkOverlap 分块重叠（可空，取配置默认；须 ≥0 且 &lt; chunkSize）
     * @return 结构化结果
     */
    public KnowledgeDocIngestResultVO ingest(MultipartFile file, String source, String docId,
                                             Integer chunkSize, Integer chunkOverlap) {
        IngestUpload upload = prepareUpload(file, source, docId, chunkSize, chunkOverlap);
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new DataAccessException("读取上传文件失败: " + e.getMessage(), e);
        }

        KnowledgeDocIngestResultVO result = new KnowledgeDocIngestResultVO();
        result.setDocId(upload.docId());
        result.setSource(upload.source());
        result.setObjectKey(upload.objectKey());

        // 登记文档行（状态 queued）
        kbDocMapper.upsert(toDocEntity(upload, STATUS_QUEUED));

        try {
            // 1) 原文写近源层（复用 runtime-access MinIO 封装）
            uploadNearSource(upload.objectKey(), bytes, upload.contentType());
            // 2..7) 登记原文 → 解析 → 分块落库 → 向量化 → 登记解析文本 → 终态 done
            StagesOutcome outcome = runStages(upload, bytes, result);
            result.setDurationMs(outcome.durationMs());
            emitAudit("kb_doc_ingest", "docId=" + upload.docId() + " source=" + upload.source()
                    + " status=done chunks=" + outcome.chunkCount());
            log.info("非结构化文档登记与解析完成: docId={}, chunks={}, vectorWritten={}, costMs={}",
                    upload.docId(), outcome.chunkCount(), result.getVectorWritten(), result.getDurationMs());
            return result;
        } catch (Exception e) {
            log.error("非结构化文档登记与解析失败: docId={}, objectKey={}",
                    upload.docId(), upload.objectKey(), e);
            String errorMessage = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            markFailed(upload.docId(), errorMessage);
            result.setParseStatus(STATUS_FAILED);
            result.setErrorMessage(errorMessage);
            if (e instanceof DataBridgeException dbe) {
                throw dbe;
            }
            throw new DataAccessException("非结构化文档登记与解析失败: " + errorMessage, e);
        }
    }

    // ═══════════════ #146z 契约面三条腿（校验／近源层写／阶段流水线） ═══════════════

    /**
     * 上传载体校验并物化（契约 §7.5.1-1「接入校类型与大小」）。
     *
     * <p>不访问 MinIO/网络：空文件与分块参数非法走存量 {@code ValidationException}
     * （{@code /docs/ingest} 旧控制器映射 400，保持逐字不变）；类型/大小走
     * {@link KbErrorCodes#UPLOAD_INVALID}（415/413，机读码 ECOS-KB-031，由
     * {@code KbErrorAdvice} 单源渲染状态行）。</p>
     */
    public IngestUpload prepareUpload(MultipartFile file, String source, String docId,
                                      Integer chunkSize, Integer chunkOverlap) {
        if (file == null || file.isEmpty()) {
            throw new ValidationException("file", "上传文件不能为空");
        }
        String rawContentType = file.getContentType() == null
                ? "application/octet-stream" : file.getContentType();
        String normalized = normalizeContentType(rawContentType);
        if (!matchesType(normalized, allowedTypePrefixes)) {
            throw KbErrorCodes.UPLOAD_INVALID.exception(
                    "上传文件类型不在白名单: contentType=" + rawContentType
                            + "（白名单来自 ecos.kb.doc.allowed-content-types）");
        }
        if (file.getSize() > maxSizeBytes) {
            throw new KbErrorCodes.DomainException(413, KbErrorCodes.UPLOAD_INVALID.code(),
                    "上传文件超大小上限: size=" + file.getSize() + " bytes > max=" + maxSizeBytes
                            + " bytes（上限来自 ecos.kb.doc.max-size-mb）");
        }
        String effectiveSource = (source == null || source.isBlank()) ? DEFAULT_SOURCE : source.trim();
        String effectiveDocId = (docId == null || docId.isBlank())
                ? UUID.randomUUID().toString().replace("-", "") : docId.trim();
        String fileName = sanitizeFileName(file.getOriginalFilename());
        int effectiveChunkSize = resolveChunkSize(chunkSize);
        int effectiveChunkOverlap = resolveChunkOverlap(chunkOverlap, effectiveChunkSize);
        String objectKey = UNSTRUCTURED_PREFIX + effectiveSource + "/" + effectiveDocId + "/" + fileName;
        return new IngestUpload(effectiveDocId, effectiveSource, objectKey, fileName,
                rawContentType, file.getSize(), effectiveChunkSize, effectiveChunkOverlap);
    }

    /**
     * 契约异步腿第一段：原文写近源层 + 登记 {@code queued} 行（不跑解析流水线）。
     *
     * <p>顺序即契约状态矩阵行序：MinIO 不可用 ⇒ 阻断不落 kb_doc 行（矩阵「不入草稿」口径，
     * 以 500 ECOS-KB-030 上抛）；写成功后 upsert queued 行，供 runtime-task 执行腿按 docId 续跑。</p>
     */
    public KnowledgeDocIngestResultVO storeAndRegisterQueued(IngestUpload upload, byte[] bytes) {
        uploadNearSource(upload.objectKey(), bytes, upload.contentType());
        kbDocMapper.upsert(toDocEntity(upload, STATUS_QUEUED));
        KnowledgeDocIngestResultVO result = new KnowledgeDocIngestResultVO();
        result.setDocId(upload.docId());
        result.setSource(upload.source());
        result.setObjectKey(upload.objectKey());
        result.setParseStatus(STATUS_QUEUED);
        return result;
    }

    /**
     * 按 kb_doc 行重跑阶段流水线（{@code KB_DOC_INGEST} 执行腿 / {@code retry} 幂等重放共用）。
     *
     * <p>幂等键 = objectKey：原文对象 key 不变，重放读同一对象；分块 ID 为
     * {@code docId#chunkIndex} 派生 UUID（确定性），batchUpsert 天然幂等。</p>
     *
     * @throws KbErrorCodes.DomainException 500 ECOS-KB-030（对象缺失、读失败、缺幂等键载体）
     * @throws IOException                  阶段流水线临时文件读写失败（由执行腿收口为任务失败）
     * @throws DataAccessException          流水线阶段失败（runStages 内已落 failed 状态）
     */
    public KnowledgeDocIngestResultVO runByDocId(String docId) throws IOException {
        KbDoc doc = kbDocMapper.findById(docId);
        if (doc == null) {
            // 执行腿内部缺失（受理/重放时控制器已 404 闸过）⇒ 任务失败承载，不冒充 HTTP 面
            throw new IllegalStateException("未找到接入记录: docId=" + docId);
        }
        if (doc.getObjectKey() == null || doc.getObjectKey().isBlank()) {
            throw KbErrorCodes.OBJECT_STORAGE_UNAVAILABLE.exception(
                    "接入记录缺幂等键 objectKey 载体，无法重放: docId=" + docId);
        }
        IngestUpload upload = new IngestUpload(doc.getDocId(),
                doc.getSource() == null ? DEFAULT_SOURCE : doc.getSource(),
                doc.getObjectKey(),
                doc.getOriginalFileName() == null ? "unnamed" : doc.getOriginalFileName(),
                doc.getContentType() == null ? "application/octet-stream" : doc.getContentType(),
                doc.getSizeBytes() == null ? 0L : doc.getSizeBytes(),
                doc.getChunkSize() == null ? defaultChunkSize : doc.getChunkSize(),
                doc.getChunkOverlap() == null ? defaultChunkOverlap : doc.getChunkOverlap());
        byte[] bytes = loadNearSource(doc.getObjectKey());

        KnowledgeDocIngestResultVO result = new KnowledgeDocIngestResultVO();
        result.setDocId(upload.docId());
        result.setSource(upload.source());
        result.setObjectKey(upload.objectKey());
        StagesOutcome outcome = runStages(upload, bytes, result);
        result.setDurationMs(outcome.durationMs());
        emitAudit("kb_doc_ingest", "docId=" + upload.docId() + " source=" + upload.source()
                + " status=done chunks=" + outcome.chunkCount());
        return result;
    }

    /** 生成文档业务 ID（契约异步腿在受理前取号，与 {@link #prepareUpload} 的缺省形态同构）。 */
    public String newDocId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /** 上传载体（三条腿之间传递的不可变参数组）。 */
    public record IngestUpload(String docId, String source, String objectKey, String fileName,
                               String contentType, long sizeBytes, int chunkSize, int chunkOverlap) {
    }

    /** 阶段流水线产物（分块数 + 耗时）。 */
    public record StagesOutcome(int chunkCount, long durationMs) {
    }

    /**
     * 阶段流水线 2..7：登记原文 → 解析 → 分块落库 → 向量化 → 登记解析文本 → 终态 done。
     *
     * <p>失败上抛由调用方落 failed：同步入口在本类 catch，异步腿在 {@code KbDocIngestTaskExecutor}。
     * {@code IOException}（临时文件创建／写入）原样上抛——存量语义：同步入口由外层
     * {@code catch (Exception)} 收口落 failed + {@code DataAccessException} 包装，本搬迁不改判据。</p>
     */
    private StagesOutcome runStages(IngestUpload upload, byte[] bytes, KnowledgeDocIngestResultVO result)
            throws IOException {
        long startedAt = System.currentTimeMillis();
        Path tempFile = null;
        try {
            // 2) 原文登记（数据工作台：RAW/UNSTRUCTURED/LAKE_OBJECT）
            registerOriginal(upload.source(), upload.docId(), upload.fileName(),
                    upload.sizeBytes(), upload.contentType());
            result.setOriginalRegistered(true);

            // 3) 解析
            updateStatus(upload.docId(), STATUS_PARSING, null, null, null);
            tempFile = Files.createTempFile("ecos-kb-doc-", "-" + upload.fileName());
            Files.write(tempFile, bytes);
            DocumentParseResult parseResult = documentParserService.parse(tempFile);
            String text = parseResult.text() == null ? "" : parseResult.text();

            // 4) 抽块（状态 extracting）
            updateStatus(upload.docId(), STATUS_EXTRACTING, null, null, null);
            List<KbDocChunk> chunks = toKbChunks(
                    documentChunkSplitter.split(text, upload.chunkSize(), upload.chunkOverlap()),
                    upload.docId(), upload.source());
            if (!chunks.isEmpty()) {
                kbDocChunkMapper.batchUpsert(chunks);
            }

            // 5) 向量化（复用 B4 KnowledgeVectorWriteService；失败不阻断 chunk 落库）
            vectorize(chunks, upload.docId(), result);

            // 6) 解析文本登记为 CURATED 资源（数据工作台）
            registerParsedText();
            result.setParsedRegistered(true);
            result.setTextSourcePath(PARSED_TEXT_SOURCE_PATH);

            // 7) 终态 done
            updateStatus(upload.docId(), STATUS_DONE, chunks.size(), PARSED_TEXT_SOURCE_PATH, null);
            result.setParseStatus(STATUS_DONE);
            result.setChunkCount(chunks.size());
            return new StagesOutcome(chunks.size(), System.currentTimeMillis() - startedAt);
        } finally {
            deleteQuietly(tempFile);
        }
    }

    /** kb_doc 行实体（queued 初始态；字段与存量 ingest 前置登记逐一同构）。 */
    private static KbDoc toDocEntity(IngestUpload upload, String status) {
        KbDoc doc = new KbDoc();
        doc.setDocId(upload.docId());
        doc.setSource(upload.source());
        doc.setOriginalFileName(upload.fileName());
        doc.setObjectKey(upload.objectKey());
        doc.setContentType(upload.contentType());
        doc.setSizeBytes(upload.sizeBytes());
        doc.setParseStatus(status);
        doc.setChunkSize(upload.chunkSize());
        doc.setChunkOverlap(upload.chunkOverlap());
        doc.setChunkCount(0);
        return doc;
    }

    // ═══════════════ 近源层写入 + 数据工作台登记 ═══════════════

    /** 原文写入近源层（MinIO）；失败上抛（登记端点自身职责，非管道尽力而为标记）。
     *
     * <p>#146z：失败载体由 {@code DataAccessException}（advice 落 500 ECOS-KB-050）改为本域
     * 500 {@link KbErrorCodes#OBJECT_STORAGE_UNAVAILABLE}（ECOS-KB-030）——契约状态矩阵
     * 「MinIO 不可用 → ECOS-KB-030/500 阻断」点名该码；存量 {@code /docs/ingest} 旧控制器的
     * 宽 catch 按 {@code Exception} 收口，信封文案逐字保持（其内部 code 语义差异登记台账余项）。
     * 本异常仍属 {@code DataBridgeException} 家族，{@link #ingest} 的 catch 原样上抛不再包装。</p>
     */
    private void uploadNearSource(String objectKey, byte[] bytes, String contentType) {
        Map<String, Object> putResult;
        try {
            putResult = minioStorageService.putObject(objectKey, bytes, contentType);
        } catch (RuntimeException e) {
            throw KbErrorCodes.OBJECT_STORAGE_UNAVAILABLE.exception(
                    "近源层原文写入失败（MinIO 不可用）: object=" + objectKey + ", err=" + e.getMessage());
        }
        Object status = putResult == null ? null : putResult.get("status");
        if (!"success".equals(status)) {
            String message = putResult == null ? "空返回" : String.valueOf(putResult.get("message"));
            throw KbErrorCodes.OBJECT_STORAGE_UNAVAILABLE.exception(
                    "近源层原文写入失败: object=" + objectKey + ", err=" + message);
        }
    }

    /**
     * 从近源层读回原文（#146z 异步执行腿 / retry 重放；与 {@link #uploadNearSource} 同一
     * runtime-access 封装，禁另立 MinIO 客户端 —— 铁律 §2.5-1）。
     *
     * <p>读失败（对象缺失、网络故障）→ 500 ECOS-KB-030，禁折成成功或空文本。</p>
     */
    private byte[] loadNearSource(String objectKey) {
        byte[] bytes;
        try {
            bytes = minioStorageService.getObject(objectKey);
        } catch (RuntimeException e) {
            throw KbErrorCodes.OBJECT_STORAGE_UNAVAILABLE.exception(
                    "近源层原文读取失败（MinIO 不可用或对象缺失）: object=" + objectKey
                            + ", err=" + e.getMessage());
        }
        if (bytes == null || bytes.length == 0) {
            throw KbErrorCodes.OBJECT_STORAGE_UNAVAILABLE.exception(
                    "近源层原文为空: object=" + objectKey);
        }
        return bytes;
    }

    /** 原文对象登记（数据工作台 /api/v1/datanet/datalake/unstructured）。 */
    private void registerOriginal(String source, String docId, String fileName, long size, String contentType) {
        DatalakeUnstructuredRegisterRequest req = new DatalakeUnstructuredRegisterRequest();
        req.setSource(source);
        req.setDocId(docId);
        req.setOriginalFileName(fileName);
        req.setSize(size);
        req.setContentType(contentType);
        postForRegistration(datanetBaseUrl() + "/api/v1/datanet/datalake/unstructured", req, "非结构化原文登记");
    }

    /** 解析文本登记为 CURATED 资源（数据工作台 /api/v1/datanet/metadata/resources）。 */
    private void registerParsedText() {
        DataResourceRegisterRequest req = new DataResourceRegisterRequest();
        req.setResourceName("非结构化解析文本（kb_doc_chunk）");
        req.setResourceType(TYPE_TABLE);
        req.setLayer(LAYER_CURATED);
        req.setZone(null);
        req.setSourcePath(PARSED_TEXT_SOURCE_PATH);
        req.setDescription("A3 过渡态：kb-engine 文档解析文本分块表，A1 落地后迁入 DW 层");
        postForRegistration(datanetBaseUrl() + "/api/v1/datanet/metadata/resources", req, "解析文本资源登记");
    }

    /** 跨引擎 REST 登记（统一返回体 code=0 为成功；非 0 或异常一律上抛）。 */
    private void postForRegistration(String url, Object body, String action) {
        try {
            JsonNode resp = restTemplate.postForObject(url, body, JsonNode.class);
            if (resp == null || resp.path("code").asInt(-1) != 0) {
                String message = resp == null ? "空响应" : resp.path("message").asText("");
                throw new DataAccessException(action + "失败: " + message + " (url=" + url + ")");
            }
            log.info("{}成功: url={}", action, url);
        } catch (DataBridgeException e) {
            throw e;
        } catch (Exception e) {
            throw new DataAccessException(action + "调用失败: " + e.getMessage() + " (url=" + url + ")", e);
        }
    }

    // ═══════════════ 分块 / 向量化 ═══════════════

    /**
     * 把 runtime-access 公共切分结果映射为 kb 自有实体（A3 过渡表 {@code kb_doc_chunk}）。
     *
     * <p>切分逻辑唯一实现在 {@link DocumentChunkSplitter}（B6-2 上移），本方法只做字段映射，
     * 不再自带切分算法（避免两处实现漂移，铁律 §2.5-6）。
     */
    private List<KbDocChunk> toKbChunks(List<DocumentChunk> source, String docId, String sourceTag) {
        List<KbDocChunk> chunks = new ArrayList<>(source.size());
        for (DocumentChunk chunk : source) {
            KbDocChunk entity = new KbDocChunk();
            entity.setId(UUID.randomUUID().toString().replace("-", ""));
            entity.setDocId(docId);
            entity.setSource(sourceTag);
            entity.setChunkIndex(chunk.chunkIndex());
            entity.setContent(chunk.content());
            entity.setCharStart(chunk.charStart());
            entity.setCharEnd(chunk.charEnd());
            entity.setMetadata("{}");
            entity.setStatus(CHUNK_ACTIVE);
            entity.setEmbeddingId(embeddingId(docId, chunk.chunkIndex()));
            chunks.add(entity);
        }
        return chunks;
    }

    /** 与 KnowledgeVectorWriteService 确定性主键同构（docId#chunkIndex → UUID），便于回填 embedding_id。 */
    private String embeddingId(String docId, int chunkIndex) {
        return UUID.nameUUIDFromBytes((docId + "#" + chunkIndex).getBytes(StandardCharsets.UTF_8)).toString();
    }

    /** 复用 B4 向量写入服务；失败不阻断主流程（chunk 已落库），仅 error 日志 + 结果记 skipped。 */
    private void vectorize(List<KbDocChunk> chunks, String docId, KnowledgeDocIngestResultVO result) {
        if (chunks.isEmpty()) {
            return;
        }
        List<KnowledgeVectorWriteItem> items = new ArrayList<>(chunks.size());
        for (KbDocChunk chunk : chunks) {
            KnowledgeVectorWriteItem item = new KnowledgeVectorWriteItem();
            item.setId(chunk.getEmbeddingId());
            item.setArticleId(docId);
            item.setChunkIndex(chunk.getChunkIndex());
            item.setText(chunk.getContent());
            item.setTokenCount(0);
            items.add(item);
        }
        try {
            KnowledgeVectorWriteResultVO vr = vectorWriteService.upsertVectors(items);
            result.setVectorWritten(vr.getWritten());
            result.setVectorSkipped(vr.getSkipped());
        } catch (Exception e) {
            log.error("向量化失败（chunk 已落库，向量待补）: docId={}, chunks={}", docId, chunks.size(), e);
            result.setVectorSkipped(items.size());
        }
    }

    // ═══════════════ 状态机 / 校验 / 工具 ═══════════════

    private void updateStatus(String docId, String status, Integer chunkCount,
                              String textSourcePath, String errorMessage) {
        kbDocMapper.updateStatus(docId, status, chunkCount, textSourcePath, errorMessage);
    }

    /** 落 failed 状态；此处失败仅告警，不覆盖原始异常。 */
    private void markFailed(String docId, String errorMessage) {
        try {
            updateStatus(docId, STATUS_FAILED, null, null, truncate(errorMessage, 2000));
        } catch (Exception e) {
            log.warn("落 failed 状态失败（忽略）: docId={}, err={}", docId, e.getMessage());
        }
    }

    /** 解析生效分块大小（委托 runtime-access 公共校验，配置默认值来自 ecos.kb.doc.chunk-size）。 */
    private int resolveChunkSize(Integer requested) {
        return documentChunkSplitter.resolveChunkSize(requested, defaultChunkSize);
    }

    /** 解析生效分块重叠（委托 runtime-access 公共校验，配置默认值来自 ecos.kb.doc.chunk-overlap）。 */
    private int resolveChunkOverlap(Integer requested, int chunkSize) {
        return documentChunkSplitter.resolveChunkOverlap(requested, chunkSize, defaultChunkOverlap);
    }

    /** 白名单配置解析（逗号分隔；空项忽略，大小写不敏感）。 */
    private static Set<String> parseAllowedTypes(String csv) {
        Set<String> out = new java.util.HashSet<>();
        if (csv != null) {
            for (String part : csv.split(",")) {
                String v = normalizeContentType(part);
                if (!v.isEmpty()) {
                    out.add(v);
                }
            }
        }
        return Set.copyOf(out);
    }

    /** MIME 归一口径：小写 + 去参（{@code ;charset=...}）+ 去空白（比对与白名单装载共用，单源）。 */
    private static String normalizeContentType(String raw) {
        if (raw == null) {
            return "";
        }
        int semicolon = raw.indexOf(';');
        String base = semicolon >= 0 ? raw.substring(0, semicolon) : raw;
        return base.trim().toLowerCase(Locale.ROOT);
    }

    /** 类型白名单判定：归一后与白名单全等或按 {@code prefix/*} 通配匹配。 */
    private static boolean matchesType(String normalizedContentType, Set<String> allowedPrefixes) {
        for (String allowed : allowedPrefixes) {
            if (allowed.endsWith("/*")) {
                if (normalizedContentType.startsWith(allowed.substring(0, allowed.length() - 1))) {
                    return true;
                }
            } else if (allowed.equals(normalizedContentType)) {
                return true;
            }
        }
        return false;
    }

    /** 契约读面用：按 docId 取 kb_doc 行（retry 的存在性闸与幂等键回显；无行返回 null，不合成）。 */
    public KbDoc findDoc(String docId) {
        return kbDocMapper.findById(docId);
    }

    /** 契约读面用：接入记录分页列表（status 空 = 全部；谓词与计数同源，见 {@code KbDocMapper}）。 */
    public List<KbDoc> listDocs(String status, int limit, int offset) {
        return kbDocMapper.listByStatus(status, limit, offset);
    }

    /** 契约读面用：接入记录总数（与 {@link #listDocs} 同谓词口径）。 */
    public long countDocs(String status) {
        return kbDocMapper.countByStatus(status);
    }

    /** 文件名去路径并限长，避免对象 key 越出 {docId}/ 目录。 */
    private String sanitizeFileName(String original) {
        String name = (original == null || original.isBlank()) ? "unnamed" : original.trim().replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        if (name.isBlank()) {
            name = "unnamed";
        }
        return truncate(name, 512);
    }

    private void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("临时文件清理失败: {} ({})", path, e.getMessage());
        }
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    /** F04-15 真实审计发布器（可空；缺席时本地结构化 log 兜底，语义同 KnowledgeNavSecurityEngineClient 先例）。 */
    @Autowired(required = false)
    private KbAuditEventPublisher kbAuditEventPublisher;

    /**
     * 审计事件（F04-15 收口，2026-10-12 复盘订正）：经 {@link KbAuditEventPublisher} 走 EventBus
     * 发 Kafka {@code ecos.audit}（复刻 {@code KnowledgeNavSecurityEngineClient#audit} 先例）；
     * 发布器未装配 → 本地 log 兜底；发布失败（ECOS-KB-022）→ 按先例调用方口径降级 log.error，
     * 不阻塞主流程、绝不静默吞审计。
     */
    private void emitAudit(String action, String detail) {
        if (kbAuditEventPublisher == null) {
            log.warn("[AUDIT-LOCAL-FALLBACK] topic={} action={} detail={}", KafkaTopics.AUDIT, action, detail);
            return;
        }
        try {
            kbAuditEventPublisher.audit(action, detail, "kb-doc-ingest");
        } catch (KbErrorCodes.DomainException ex) {
            log.error("审计发布失败（写已落库，审计证据见本地兜底 log）: action={} {}",
                    action, ex.getMessage());
        }
    }
}
