package com.chinacreator.gzcm.engine.kb.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.exception.BusinessException;
import com.chinacreator.gzcm.common.exception.ValidationException;
import com.chinacreator.gzcm.engine.kb.KgSyncService;
import com.chinacreator.gzcm.engine.kb.dto.GraphBuildPreviewVO;
import com.chinacreator.gzcm.engine.kb.dto.GraphBuildRequest;
import com.chinacreator.gzcm.engine.kb.dto.KnowledgeDocIngestResultVO;
import com.chinacreator.gzcm.engine.kb.dto.KnowledgeIngestRequest;
import com.chinacreator.gzcm.engine.kb.dto.KnowledgeIngestResult;
import com.chinacreator.gzcm.engine.kb.dto.EntityInstanceExtractionReportVO;
import com.chinacreator.gzcm.engine.kb.model.KnowledgeNode;
import com.chinacreator.gzcm.engine.kb.repository.KnowledgeNodeMapper;
import com.chinacreator.gzcm.engine.kb.service.KbEntityInstanceExtractionService;
import com.chinacreator.gzcm.engine.kb.service.KnowledgeDocIngestService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识接入 / 图谱构建 REST API — 前缀 /api/v1/knowledge
 *
 * <p>PMO-51 T3：为 cognitive {@code EngineCapabilityRegistryImpl.executeIngest/executeKg}
 * 提供 kb-side 落库与异步构建端点。
 * 幂等：同 {@code entityId} 重复 ingest 返 200 且 {@code idempotent=true}。</p>
 *
 * <p>路径池：</p>
 * <ul>
 *   <li>POST /api/v1/knowledge/ingest      — 写入实体（upsert 到 graph_node，幂等）</li>
 *   <li>POST /api/v1/knowledge/graph/build — 触发 runtime-task 异步全量构建，返回 jobId</li>
 *   <li>POST /api/v1/knowledge/graph/build/preview — dry-run 预览（B5-2 D6，同步只统计不落库）</li>
 * </ul>
 *
 * @group EXTRACT
 */
@RestController
@RequestMapping("/api/v1/knowledge")
public class KnowledgeIngestController {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeIngestController.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final KnowledgeNodeMapper nodeMapper;
    private final JdbcTemplate jdbcTemplate;
    private final KgSyncService kgSyncService;

    /** 非结构化文档登记与解析编排（B5-1 / A3 过渡态）。 */
    private final KnowledgeDocIngestService docIngestService;

    /** 契约驱动实例抽取服务（B3-2）— dry-run 预览复用其 dry-run 分支。 */
    private final KbEntityInstanceExtractionService instanceExtractionService;

    public KnowledgeIngestController(KnowledgeNodeMapper nodeMapper,
                                     JdbcTemplate jdbcTemplate,
                                     KgSyncService kgSyncService,
                                     KnowledgeDocIngestService docIngestService,
                                     KbEntityInstanceExtractionService instanceExtractionService) {
        this.nodeMapper = nodeMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.kgSyncService = kgSyncService;
        this.docIngestService = docIngestService;
        this.instanceExtractionService = instanceExtractionService;
    }

    // ── /docs/ingest ─────────────────────────────────────────────────

    /**
     * POST /api/v1/knowledge/docs/ingest — 非结构化文档登记与解析（B5-1，SOP-2 / A3）。
     *
     * <p>原文写近源层并登记 {@code RAW/UNSTRUCTURED/LAKE_OBJECT}；解析文本分块落
     * kb 自有过渡表 {@code kb_doc_chunk}，并登记为 {@code CURATED} 资源；随后复用
     * B4 向量写入服务做嵌入（失败不阻断分块落库）。
     *
     * @param file         上传文件（multipart 表单字段 file）
     * @param source       上游数据源标识（可选，默认 kb-upload）
     * @param docId        文档 ID（可选，默认生成）
     * @param chunkSize    分块大小（可选，默认取配置；∈ {256,512,1024,2048}）
     * @param chunkOverlap 分块重叠（可选，默认取配置）
     * @return 结构化结果（含状态/分块数/向量写入数）
     */
    @PostMapping(value = "/docs/ingest", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<KnowledgeDocIngestResultVO> ingestDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "source", required = false) String source,
            @RequestParam(value = "docId", required = false) String docId,
            @RequestParam(value = "chunkSize", required = false) Integer chunkSize,
            @RequestParam(value = "chunkOverlap", required = false) Integer chunkOverlap) {
        try {
            return ApiResponse.success(docIngestService.ingest(file, source, docId, chunkSize, chunkOverlap));
        } catch (ValidationException e) {
            log.warn("文档登记与解析入参非法: {}", e.getMessage());
            return ApiResponse.badRequest(e.getMessage());
        } catch (Exception e) {
            log.error("文档登记与解析失败: {}", e.getMessage(), e);
            return ApiResponse.internalError("文档登记与解析失败: " + e.getMessage());
        }
    }

    // ── /ingest ──────────────────────────────────────────────────────

    /**
     * POST /api/v1/knowledge/ingest — 写入一条实体到 graph_node（幂等 upsert）。
     *
     * <p>幂等策略：先按 {@code entityId} 查 graph_node.id (id = entityId 形态)
     * 命中 → 走 UPDATE 仅补缺失字段，返回 {@code idempotent=true}；
     * 未命中 → 走 INSERT，返回 {@code idempotent=false}。</p>
     */
    @PostMapping("/ingest")
    public ApiResponse<KnowledgeIngestResult> ingest(@RequestBody KnowledgeIngestRequest req) {
        try {
            if (req == null || req.getEntityId() == null || req.getEntityId().isBlank()) {
                throw new BusinessException("entityId 不能为空");
            }
            String entityId = req.getEntityId().trim();
            LocalDateTime now = LocalDateTime.now();

            // 1) lookup existing
            List<KnowledgeNode> rows = safeListById(entityId);
            if (!rows.isEmpty()) {
                KnowledgeNode existing = rows.get(0);
                // 仅补缺失字段，禁空值覆盖有效值（后端规范 节 6 实体入库）
                boolean patched = applyPatch(existing, req, now);
                if (patched) {
                    // update 现 mapper 无 update 方法 → 走 JdbcTemplate 直接 set
                    persist(existing);
                }
                log.info("Knowledge ingest (idempotent hit): entityId={}", entityId);
                emitAudit("knowledge.ingest", "idempotent=true entityId=" + entityId);
                return ApiResponse.success(new KnowledgeIngestResult(entityId, true));
            }
            // 2) not found → insert
            KnowledgeNode n = new KnowledgeNode();
            n.setId(entityId);
            n.setLabel(req.getLabel() == null || req.getLabel().isBlank()
                    ? entityId : req.getLabel());
            n.setNodeType(req.getType() == null ? "Concept" : req.getType());
            n.setDescription(req.getSourceRef() == null ? "" : req.getSourceRef());
            n.setPropertiesJson(serializeProperties(req));
            n.setCreatedAt(now);
            n.setUpdatedAt(now);
            nodeMapper.insert(n);
            log.info("Knowledge ingest (insert): entityId={}", entityId);
            emitAudit("knowledge.ingest", "idempotent=false entityId=" + entityId);
            return ApiResponse.success(new KnowledgeIngestResult(entityId, false));
        } catch (BusinessException e) {
            log.warn("Knowledge ingest rejected: {}", e.getMessage());
            return ApiResponse.badRequest(e.getMessage());
        } catch (DataAccessException e) {
            log.error("Knowledge ingest failed: {}", e.getMessage(), e);
            return ApiResponse.internalError("知识接入失败: " + e.getMessage());
        }
    }

    // ── /graph/build ─────────────────────────────────────────────────

    /**
     * POST /api/v1/knowledge/graph/build — 触发异步图谱构建（方案 §5.3 修正：支持 FULL/INCREMENTAL 与 dry-run）。
     *
     * <p>jobId = {@code build-<timestamp>}（时间戳防并发碰撞）。异步执行，立即返回 jobId。
     * {@code mode} 缺省 FULL；{@code dryRun=true} 时只统计不落库（预览报告回填 {@code kg_sync_log.report}）。
     */
    @PostMapping("/graph/build")
    public ApiResponse<GraphBuildResponse> build(@RequestBody(required = false) GraphBuildRequest req) {
        try {
            String jobId = "build-" + System.currentTimeMillis();
            String mode = (req == null || req.getMode() == null || req.getMode().isBlank())
                    ? "FULL" : req.getMode().trim().toUpperCase(java.util.Locale.ROOT);
            boolean dryRun = req != null && req.isDryRun();
            kgSyncService.triggerBuildSync(jobId, mode, dryRun);
            log.info("Knowledge graph build triggered: jobId={}, mode={}, dryRun={}", jobId, mode, dryRun);
            emitAudit("knowledge.graph.build", "jobId=" + jobId + " mode=" + mode + " dryRun=" + dryRun);
            return ApiResponse.success(new GraphBuildResponse(jobId, dryRun ? "previewing" : "accepted"));
        } catch (Exception e) {
            log.error("Knowledge graph build failed: {}", e.getMessage(), e);
            return ApiResponse.internalError("图谱构建触发失败: " + e.getMessage());
        }
    }

    // ── /graph/build/preview ─────────────────────────────────────────

    /**
     * POST /api/v1/knowledge/graph/build/preview — 图谱构建 dry-run 预览（方案 §5.3 + 附录 B graph_build Tab）。
     *
     * <p>复用 B3-2 契约驱动实例抽取的 dry-run 分支：真实读取本体映射契约与 DW 层实例行，
     * 按 C1~C4 校验后<b>只统计不落库</b>，同步返回 create/update/skip/edgeCreate 与问题明细。
     * 前端 GraphBuilderTab「dry-run」按钮消费此端点。</p>
     *
     * @param mode 抽取模式（可选，FULL 默认 / INCREMENTAL）
     * @return dry-run 预览报告
     */
    @PostMapping("/graph/build/preview")
    public ApiResponse<GraphBuildPreviewVO> previewBuild(
            @RequestParam(value = "mode", required = false) String mode) {
        String jobId = "preview-" + System.currentTimeMillis();
        try {
            boolean incremental = "INCREMENTAL".equalsIgnoreCase(mode == null ? "" : mode.trim());
            EntityInstanceExtractionReportVO report =
                    instanceExtractionService.extract("ALL", jobId, incremental, true);

            GraphBuildPreviewVO preview = new GraphBuildPreviewVO();
            preview.setCreate(report.getNodeCreated());
            preview.setUpdate(report.getNodeUpdated());
            preview.setSkip(report.getNodeSkipped());
            preview.setEdgeCreate(report.getEdgeCreated());
            preview.setEntityCount(report.getEntityCount());
            preview.setInvalidMappings(report.getInvalidMappings());
            preview.setOntologyId(report.getOntologyId());
            preview.setOntologies(report.getOntologies());
            preview.setDurationMs(report.getDurationMs());
            preview.setIssues(report.getIssues());

            log.info("图谱构建 dry-run 预览完成: jobId={}, mode={}, create={}, update={}, skip={}, edges={}",
                    jobId, incremental ? "INCREMENTAL" : "FULL",
                    preview.getCreate(), preview.getUpdate(), preview.getSkip(), preview.getEdgeCreate());
            emitAudit("knowledge.graph.build.preview",
                    "jobId=" + jobId + " create=" + preview.getCreate() + " update=" + preview.getUpdate()
                            + " skip=" + preview.getSkip());
            return ApiResponse.success(preview);
        } catch (Exception e) {
            log.error("图谱构建 dry-run 预览失败: jobId={}, {}", jobId, e.getMessage(), e);
            return ApiResponse.internalError("预览失败: " + e.getMessage());
        }
    }

    // ── 内部辅助 ─────────────────────────────────────────────────────

    private List<KnowledgeNode> safeListById(String id) {
        try {
            KnowledgeNode found = nodeMapper.findById(id);
            if (found == null) {
                return List.of();
            }
            return List.of(found);
        } catch (Exception e) {
            log.warn("ingest lookup failed (degenerate): {}", e.getMessage());
            return List.of();
        }
    }

    /** 仅补缺失字段；空值不覆盖有效值。返回 true 表示实际变更。 */
    private boolean applyPatch(KnowledgeNode existing, KnowledgeIngestRequest req, LocalDateTime now) {
        boolean changed = false;
        if (req.getLabel() != null && !req.getLabel().isBlank()
                && (existing.getLabel() == null || existing.getLabel().isBlank())) {
            existing.setLabel(req.getLabel());
            changed = true;
        }
        if (req.getType() != null && !req.getType().isBlank()
                && (existing.getNodeType() == null || existing.getNodeType().isBlank())) {
            existing.setNodeType(req.getType());
            changed = true;
        }
        if (req.getSourceRef() != null && !req.getSourceRef().isBlank()
                && (existing.getDescription() == null || existing.getDescription().isBlank())) {
            existing.setDescription(req.getSourceRef());
            changed = true;
        }
        if (req.getProperties() != null && !req.getProperties().isEmpty()) {
            // 简化策略：properties 直接覆盖为 req（防大 JSON diff 复杂度）
            existing.setPropertiesJson(serializeProperties(req));
            changed = true;
        }
        if (changed) {
            existing.setUpdatedAt(now);
        }
        return changed;
    }

    /** 直接 PG UPDATE 节点（nodeMapper 无 update 方法，绕行 JdbcTemplate）。 */
    private void persist(KnowledgeNode n) {
        jdbcTemplate.update(
                "UPDATE ecos_knowledge.graph_node SET label = ?, node_type = ?, description = ?, " +
                "properties = ?, domain = ?, updated_at = ? WHERE id = ?",
                n.getLabel(), n.getNodeType(), n.getDescription(),
                n.getPropertiesJson(), n.getDomain(),
                n.getUpdatedAt(), n.getId());
    }

    private String serializeProperties(KnowledgeIngestRequest req) {
        Map<String, Object> props = new LinkedHashMap<>();
        if (req.getProperties() != null) {
            props.putAll(req.getProperties());
        }
        if (req.getPayload() != null) {
            props.put("payload", req.getPayload());
        }
        try {
            return MAPPER.writeValueAsString(props);
        } catch (JsonProcessingException e) {
            log.warn("serializeProperties fallback to '{}': {}", e.getMessage());
            return "{}";
        }
    }

    /** 审计事件：写操作落 log；正式 Kafka 发件走 PMO-50 EventBus 统一接入后切换。 */
    private void emitAudit(String action, String detail) {
        try {
            log.info("AUDIT topic=ecos.audit action={} detail={}", action, detail);
        } catch (Exception e) {
            log.warn("emitAudit fallback log failed: {}", e.getMessage());
        }
    }

    /** /graph/build 响应体. */
    public static class GraphBuildResponse {
        private final String jobId;
        private final String status;

        public GraphBuildResponse(String jobId, String status) {
            this.jobId = jobId;
            this.status = status;
        }

        public String getJobId() { return jobId; }
        public String getStatus() { return status; }
    }
}
