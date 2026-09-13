package com.chinacreator.gzcm.engine.kb.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.exception.BusinessException;
import com.chinacreator.gzcm.engine.kb.KgSyncService;
import com.chinacreator.gzcm.engine.kb.dto.GraphBuildRequest;
import com.chinacreator.gzcm.engine.kb.dto.KnowledgeIngestRequest;
import com.chinacreator.gzcm.engine.kb.dto.KnowledgeIngestResult;
import com.chinacreator.gzcm.engine.kb.model.KnowledgeNode;
import com.chinacreator.gzcm.engine.kb.repository.KnowledgeNodeMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/knowledge")
public class KnowledgeIngestController {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeIngestController.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final KnowledgeNodeMapper nodeMapper;
    private final JdbcTemplate jdbcTemplate;
    private final KgSyncService kgSyncService;

    public KnowledgeIngestController(KnowledgeNodeMapper nodeMapper,
                                     JdbcTemplate jdbcTemplate,
                                     KgSyncService kgSyncService) {
        this.nodeMapper = nodeMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.kgSyncService = kgSyncService;
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
     * POST /api/v1/knowledge/graph/build — 触发 runtime-task 异步全量建图。
     *
     * <p>复用 {@link KgSyncService#triggerFullSync(String)} 已有方法；
     * jobId = {@code build-<timestamp>}（时间戳防并发碰撞）。异步执行，立即返回 jobId。</p>
     */
    @PostMapping("/graph/build")
    public ApiResponse<GraphBuildResponse> build(@RequestBody(required = false) GraphBuildRequest req) {
        try {
            String jobId = "build-" + System.currentTimeMillis();
            kgSyncService.triggerFullSync(jobId);
            log.info("Knowledge graph build triggered: jobId={}", jobId);
            emitAudit("knowledge.graph.build", "jobId=" + jobId);
            return ApiResponse.success(new GraphBuildResponse(jobId, "accepted"));
        } catch (Exception e) {
            log.error("Knowledge graph build failed: {}", e.getMessage(), e);
            return ApiResponse.internalError("图谱构建触发失败: " + e.getMessage());
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
