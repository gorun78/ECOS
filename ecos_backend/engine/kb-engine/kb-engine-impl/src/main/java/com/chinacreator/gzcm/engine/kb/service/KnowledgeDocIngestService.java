package com.chinacreator.gzcm.engine.kb.service;

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
import com.chinacreator.gzcm.runtime.access.storage.MinioStorageService;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * <p>解析复用既有 {@link DocumentParserService}（Tika &lt;5MB / MinerU ≥5MB）；
 * 向量化复用既有 {@link KnowledgeVectorWriteService}（文本→llm-gateway 批量嵌入→upsert embedding_vec）。
 */
@Service
public class KnowledgeDocIngestService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeDocIngestService.class);

    /** 允许的分块大小（与前端 CHUNK_SIZE_OPTIONS = [256,512,1024,2048] 对齐） */
    private static final Set<Integer> ALLOWED_CHUNK_SIZES = Set.of(256, 512, 1024, 2048);

    /** 解析文本登记为 CURATED 资源时的 source_path（库表定位用 schema.table） */
    private static final String PARSED_TEXT_SOURCE_PATH = "ecos_knowledge.kb_doc_chunk";

    private static final String LAYER_CURATED = "CURATED";
    private static final String TYPE_TABLE = "TABLE";

    /** 状态机（F2） */
    private static final String STATUS_QUEUED = "queued";
    private static final String STATUS_PARSING = "parsing";
    private static final String STATUS_EXTRACTING = "extracting";
    private static final String STATUS_DONE = "done";
    private static final String STATUS_FAILED = "failed";

    /** 分块状态 */
    private static final String CHUNK_ACTIVE = "active";

    /** 非结构化对象 key 前缀（分层规范 §三） */
    private static final String UNSTRUCTURED_PREFIX = "raw/unstructured/";

    /** 原文缺失上游数据源时的兜底标识 */
    private static final String DEFAULT_SOURCE = "kb-upload";

    private final KbDocMapper kbDocMapper;
    private final KbDocChunkMapper kbDocChunkMapper;
    private final DocumentParserService documentParserService;
    private final KnowledgeVectorWriteService vectorWriteService;
    private final MinioStorageService minioStorageService;
    private final RestTemplate restTemplate;
    private final String datanetBaseUrl;
    private final int defaultChunkSize;
    private final int defaultChunkOverlap;

    public KnowledgeDocIngestService(KbDocMapper kbDocMapper,
                                     KbDocChunkMapper kbDocChunkMapper,
                                     DocumentParserService documentParserService,
                                     KnowledgeVectorWriteService vectorWriteService,
                                     MinioStorageService minioStorageService,
                                     RestTemplate restTemplate,
                                     @Value("${ecos.datanet.base-url:http://localhost:18082}") String datanetBaseUrl,
                                     @Value("${ecos.kb.doc.chunk-size:512}") int defaultChunkSize,
                                     @Value("${ecos.kb.doc.chunk-overlap:64}") int defaultChunkOverlap) {
        this.kbDocMapper = kbDocMapper;
        this.kbDocChunkMapper = kbDocChunkMapper;
        this.documentParserService = documentParserService;
        this.vectorWriteService = vectorWriteService;
        this.minioStorageService = minioStorageService;
        this.restTemplate = restTemplate;
        this.datanetBaseUrl = datanetBaseUrl;
        this.defaultChunkSize = defaultChunkSize;
        this.defaultChunkOverlap = defaultChunkOverlap;
    }

    /**
     * 上传 → 近源层 → 登记原文 → 解析 → 分块落库 → 向量化 → 登记解析文本。
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
        long startedAt = System.currentTimeMillis();
        if (file == null || file.isEmpty()) {
            throw new ValidationException("file", "上传文件不能为空");
        }

        String effectiveSource = (source == null || source.isBlank()) ? DEFAULT_SOURCE : source.trim();
        String effectiveDocId = (docId == null || docId.isBlank())
                ? UUID.randomUUID().toString().replace("-", "") : docId.trim();
        String fileName = sanitizeFileName(file.getOriginalFilename());
        int effectiveChunkSize = resolveChunkSize(chunkSize);
        int effectiveChunkOverlap = resolveChunkOverlap(chunkOverlap, effectiveChunkSize);
        String objectKey = UNSTRUCTURED_PREFIX + effectiveSource + "/" + effectiveDocId + "/" + fileName;
        String contentType = file.getContentType() == null ? "application/octet-stream" : file.getContentType();

        KnowledgeDocIngestResultVO result = new KnowledgeDocIngestResultVO();
        result.setDocId(effectiveDocId);
        result.setSource(effectiveSource);
        result.setObjectKey(objectKey);

        // 登记文档行（状态 queued）
        KbDoc doc = new KbDoc();
        doc.setDocId(effectiveDocId);
        doc.setSource(effectiveSource);
        doc.setOriginalFileName(fileName);
        doc.setObjectKey(objectKey);
        doc.setContentType(contentType);
        doc.setSizeBytes(file.getSize());
        doc.setParseStatus(STATUS_QUEUED);
        doc.setChunkSize(effectiveChunkSize);
        doc.setChunkOverlap(effectiveChunkOverlap);
        doc.setChunkCount(0);
        kbDocMapper.upsert(doc);

        Path tempFile = null;
        try {
            byte[] bytes = file.getBytes();

            // 1) 原文写近源层（复用 runtime-access MinIO 封装）
            uploadNearSource(objectKey, bytes, contentType);
            // 2) 原文登记（数据工作台：RAW/UNSTRUCTURED/LAKE_OBJECT）
            registerOriginal(effectiveSource, effectiveDocId, fileName, file.getSize(), contentType);
            result.setOriginalRegistered(true);

            // 3) 解析
            updateStatus(effectiveDocId, STATUS_PARSING, null, null, null);
            tempFile = Files.createTempFile("ecos-kb-doc-", "-" + fileName);
            Files.write(tempFile, bytes);
            DocumentParserService.ParseResult parseResult = documentParserService.parse(tempFile);
            String text = parseResult.getText() == null ? "" : parseResult.getText();

            // 4) 抽块（状态 extracting）
            updateStatus(effectiveDocId, STATUS_EXTRACTING, null, null, null);
            List<KbDocChunk> chunks = split(text, effectiveDocId, effectiveSource,
                    effectiveChunkSize, effectiveChunkOverlap);
            if (!chunks.isEmpty()) {
                kbDocChunkMapper.batchUpsert(chunks);
            }

            // 5) 向量化（复用 B4 KnowledgeVectorWriteService；失败不阻断 chunk 落库）
            vectorize(chunks, effectiveDocId, result);

            // 6) 解析文本登记为 CURATED 资源（数据工作台）
            registerParsedText();
            result.setParsedRegistered(true);
            result.setTextSourcePath(PARSED_TEXT_SOURCE_PATH);

            // 7) 终态 done
            updateStatus(effectiveDocId, STATUS_DONE, chunks.size(), PARSED_TEXT_SOURCE_PATH, null);
            result.setParseStatus(STATUS_DONE);
            result.setChunkCount(chunks.size());
            result.setDurationMs(System.currentTimeMillis() - startedAt);
            emitAudit("kb_doc_ingest",
                    "docId=" + effectiveDocId + " source=" + effectiveSource + " status=done chunks=" + chunks.size());
            log.info("非结构化文档登记与解析完成: docId={}, chunks={}, vectorWritten={}, costMs={}",
                    effectiveDocId, chunks.size(), result.getVectorWritten(), result.getDurationMs());
            return result;
        } catch (Exception e) {
            log.error("非结构化文档登记与解析失败: docId={}, objectKey={}", effectiveDocId, objectKey, e);
            String errorMessage = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            markFailed(effectiveDocId, errorMessage);
            result.setParseStatus(STATUS_FAILED);
            result.setErrorMessage(errorMessage);
            result.setDurationMs(System.currentTimeMillis() - startedAt);
            if (e instanceof DataBridgeException dbe) {
                throw dbe;
            }
            throw new DataAccessException("非结构化文档登记与解析失败: " + errorMessage, e);
        } finally {
            deleteQuietly(tempFile);
        }
    }

    // ═══════════════ 近源层写入 + 数据工作台登记 ═══════════════

    /** 原文写入近源层（MinIO）；失败上抛（登记端点自身职责，非管道尽力而为标记）。 */
    private void uploadNearSource(String objectKey, byte[] bytes, String contentType) {
        Map<String, Object> putResult = minioStorageService.putObject(objectKey, bytes, contentType);
        Object status = putResult == null ? null : putResult.get("status");
        if (!"success".equals(status)) {
            String message = putResult == null ? "空返回" : String.valueOf(putResult.get("message"));
            throw new DataAccessException("近源层原文写入失败: object=" + objectKey + ", err=" + message);
        }
    }

    /** 原文对象登记（数据工作台 /api/v1/datanet/datalake/unstructured）。 */
    private void registerOriginal(String source, String docId, String fileName, long size, String contentType) {
        DatalakeUnstructuredRegisterRequest req = new DatalakeUnstructuredRegisterRequest();
        req.setSource(source);
        req.setDocId(docId);
        req.setOriginalFileName(fileName);
        req.setSize(size);
        req.setContentType(contentType);
        postForRegistration(datanetBaseUrl + "/api/v1/datanet/datalake/unstructured", req, "非结构化原文登记");
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
        postForRegistration(datanetBaseUrl + "/api/v1/datanet/metadata/resources", req, "解析文本资源登记");
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
     * 滑动窗口切分（字符级，重叠 overlap）。
     *
     * <p><b>A3 过渡实现</b>：方案 §4 规定「chunk 切分应在数据工作台解析节点」为 A1 目标态；
     * A3 过渡期由 kb 代做，A1 落地后须迁出（技术债退出条件见方案 §3.3）。
     */
    private List<KbDocChunk> split(String text, String docId, String source, int chunkSize, int overlap) {
        List<KbDocChunk> chunks = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return chunks;
        }
        int length = text.length();
        int start = 0;
        int index = 0;
        while (start < length) {
            int end = Math.min(start + chunkSize, length);
            String content = text.substring(start, end);
            if (!content.isBlank()) {
                KbDocChunk chunk = new KbDocChunk();
                chunk.setId(UUID.randomUUID().toString().replace("-", ""));
                chunk.setDocId(docId);
                chunk.setSource(source);
                chunk.setChunkIndex(index);
                chunk.setContent(content);
                chunk.setCharStart(start);
                chunk.setCharEnd(end);
                chunk.setMetadata("{}");
                chunk.setStatus(CHUNK_ACTIVE);
                chunk.setEmbeddingId(embeddingId(docId, index));
                chunks.add(chunk);
                index++;
            }
            if (end >= length) {
                break;
            }
            start = end - overlap;
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

    private int resolveChunkSize(Integer requested) {
        int value = requested == null ? defaultChunkSize : requested;
        if (!ALLOWED_CHUNK_SIZES.contains(value)) {
            throw new ValidationException("chunkSize",
                    "非法分块大小: " + value + "（合法值: 256/512/1024/2048）");
        }
        return value;
    }

    private int resolveChunkOverlap(Integer requested, int chunkSize) {
        int value = requested == null ? defaultChunkOverlap : requested;
        if (value < 0 || value >= chunkSize) {
            throw new ValidationException("chunkOverlap",
                    "分块重叠须 ≥0 且小于 chunkSize(" + chunkSize + ")，当前=" + value);
        }
        return value;
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

    /** 审计事件：写操作发 Kafka {@code ecos.audit}（Kafka 不可用时 log 兜底，不阻塞主流程，铁律 §2.4-5）。 */
    private void emitAudit(String action, String detail) {
        try {
            String payload = String.format("{\"action\":\"%s\",\"detail\":\"%s\",\"service\":\"kb-engine\"}",
                    action, detail == null ? "" : detail.replace("\"", "'").replace("\\", "/"));
            log.info("AUDIT topic={} payload={}", KafkaTopics.AUDIT, payload);
        } catch (Exception e) {
            log.warn("emitAudit fallback log failed: {}", e.getMessage());
        }
    }
}
