package com.chinacreator.gzcm.engine.ontology.controller;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.ontology.dto.OntologyProposalPublishVO;
import com.chinacreator.gzcm.engine.ontology.dto.OntologyProposalSaveDTO;
import com.chinacreator.gzcm.engine.ontology.dto.OntologyProposalVO;
import com.chinacreator.gzcm.engine.ontology.dto.OntologyProposalVerifyVO;
import com.chinacreator.gzcm.engine.ontology.service.OntologyProposalService;
import com.chinacreator.gzcm.engine.ontology.service.OntologyService;
import com.chinacreator.gzcm.engine.ontology.service.OntologyVersionService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 本体变更提案 Controller — 管理本体结构的变更提案与审批流转。
 *
 * <p>持久化委托至 {@link OntologyProposalService}（PostgreSQL 表 ecos_ontology_proposals）。
 * 提案状态机：{@code DRAFT → PENDING → (APPROVED | REJECTED) → EXECUTED}，终态不可回退。</p>
 *
 * <h3>端点：</h3>
 * <ul>
 *   <li>GET    /api/v1/ontology/proposals               — 提案列表（可按 status / proposalType 过滤）</li>
 *   <li>GET    /api/v1/ontology/proposals/{id}          — 提案详情</li>
 *   <li>POST   /api/v1/ontology/proposals               — 创建提案（初始状态 DRAFT）</li>
 *   <li>PUT    /api/v1/ontology/proposals/{id}          — 更新提案（仅 DRAFT 可改）</li>
 *   <li>DELETE /api/v1/ontology/proposals/{id}          — 删除提案（仅 DRAFT 可删）</li>
 *   <li>POST   /api/v1/ontology/proposals/{id}/submit   — 提交审批（DRAFT → PENDING）</li>
 *   <li>POST   /api/v1/ontology/proposals/{id}/approve  — 审批通过（PENDING → APPROVED）</li>
 *   <li>POST   /api/v1/ontology/proposals/{id}/reject   — 审批驳回（PENDING → REJECTED）</li>
 *   <li>POST   /api/v1/ontology/proposals/{id}/verify   — 验证提案冲突/完整性</li>
 *   <li>POST   /api/v1/ontology/proposals/{id}/execute  — 执行已验证提案</li>
 *   <li>POST   /api/v1/ontology/proposals/{id}/approve-and-publish — 审批+执行+版本发布</li>
 * </ul>
 *
 * <p>T16-3 (2026-09-13) 改造：入参 Map → 强类型 DTO（{@link OntologyProposalSaveDTO}）；
 * 返回 {@code Map} → {@link OntologyProposalVO} / {@link OntologyProposalVerifyVO} /
 * {@link OntologyProposalPublishVO}。payload JSONB 字段用 {@code Object} 承载
 * （PGobject → 反序列化后对象，动态嵌套数据豁免）。Service 旧 Map 方法保留
 * （C1 兼容，Wave31 C1 边界）。
 */
@RestController("ontologyProposalController")
@RequestMapping("/api/v1/ontology/proposals")
public class OntologyProposalController {

    private static final Logger log = LoggerFactory.getLogger(OntologyProposalController.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 提案状态常量 */
    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_REJECTED = "REJECTED";
    public static final String STATUS_EXECUTED = "EXECUTED";

    /** 终态集合：不可再变更 */
    private static final Set<String> TERMINAL_STATUSES = Set.of(STATUS_APPROVED, STATUS_REJECTED, STATUS_EXECUTED);

    private final OntologyProposalService proposalService;
    private final OntologyVersionService versionService;
    private final OntologyService ontologyService;

    public OntologyProposalController(OntologyProposalService proposalService,
                                       OntologyVersionService versionService,
                                       OntologyService ontologyService) {
        this.proposalService = proposalService;
        this.versionService = versionService;
        this.ontologyService = ontologyService;
    }

    // ═══════════════ 提案 CRUD ═══════════════════

    /**
     * GET /api/v1/ontology/proposals — 提案列表。
     *
     * @param status       可选，按状态过滤（DRAFT/PENDING/APPROVED/REJECTED/EXECUTED）
     * @param proposalType 可选，按提案类型过滤（CREATE_ENTITY/ADD_PROPERTY/MODIFY_PROPERTY/...）
     */
    @GetMapping
    public ApiResponse<List<OntologyProposalVO>> listProposals(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "type", required = false) String proposalType) {
        List<Map<String, Object>> rows = proposalService.listProposals(status, proposalType);
        List<OntologyProposalVO> vos = rows == null ? new ArrayList<>()
                : rows.stream().map(this::toVO).collect(Collectors.toList());
        return ApiResponse.success(vos);
    }

    /**
     * GET /api/v1/ontology/proposals/{id} — 提案详情。
     */
    @GetMapping("/{id}")
    public ApiResponse<OntologyProposalVO> getProposal(@PathVariable String id) {
        Map<String, Object> p = proposalService.findProposalById(id);
        if (p == null) {
            return ApiResponse.notFound("ONT-001: Proposal '" + id + "' not found");
        }
        return ApiResponse.success(toVO(p));
    }

    /**
     * POST /api/v1/ontology/proposals — 创建提案。
     *
     * <p>T16-3：入参 Map → {@link OntologyProposalSaveDTO}（兼容旧字段
     * ptype/source/targetId/domain_code/target_entity/proposedBy/proposal_type/targetType，
     * 通过 {@code @JsonProperty} 在 SaveDTO 上绑定）。
     *
     * <p>兼容旧字段: {@code type}→{@code proposalType}、{@code source}→{@code proposalType}、
     * {@code title}/{@code description} 存入 payload JSONB。
     */
    @PostMapping
    public ApiResponse<OntologyProposalVO> createProposal(
            @RequestParam(value = "proposalType", required = false) String proposalTypeParam,
            @RequestParam(value = "domainCode", required = false) String domainCodeParam,
            @RequestBody(required = false) OntologyProposalSaveDTO body) {

        OntologyProposalSaveDTO dto = body != null ? body : new OntologyProposalSaveDTO();

        // 解析 proposalType（优先级：路径 query > DTO 主字段 > DTO 兼容字段）
        String proposalType = firstNonBlank(
                proposalTypeParam,
                dto.getProposalType(),
                dto.getPtypeAlt(),
                dto.getSourceAlt(),
                dto.getProposalTypeAlt(),
                dto.getTargetTypeAlt());
        if (proposalType == null) {
            proposalType = "";
        }
        proposalType = proposalType.trim();

        // 解析 domainCode（优先级：路径 query > DTO 主字段 > DTO 兼容字段）
        String domainCode = firstNonBlank(domainCodeParam, dto.getDomainCode(), dto.getDomainCodeAlt());
        if (domainCode == null) {
            domainCode = "default";
        }
        domainCode = domainCode.trim();

        if (proposalType.isEmpty()) {
            return ApiResponse.badRequest("ONT-002: 'proposalType' is required");
        }

        String targetEntity = firstNonBlank(
                dto.getTargetEntity(),
                dto.getTargetIdAlt(),
                dto.getTargetEntityAlt());
        if (targetEntity == null) {
            targetEntity = "";
        }
        targetEntity = targetEntity.trim();

        String author = firstNonBlank(dto.getAuthor(), dto.getProposedByAlt());
        if (author == null) {
            author = "system";
        }
        author = author.trim();

        // 构建 payload JSONB：包含 title/description/changeType 等扩展字段
        Map<String, Object> payloadData = new LinkedHashMap<>();
        if (dto.getPayload() != null) {
            payloadData.putAll(dto.getPayload());
        }
        if (dto.getTitle() != null && !payloadData.containsKey("title")) {
            payloadData.put("title", dto.getTitle());
        }
        if (dto.getDescription() != null && !payloadData.containsKey("description")) {
            payloadData.put("description", dto.getDescription());
        }
        if (dto.getChangeType() != null && !payloadData.containsKey("changeType")) {
            payloadData.put("changeType", dto.getChangeType());
        }
        String payloadJson;
        try {
            payloadJson = MAPPER.writeValueAsString(payloadData);
        } catch (JsonProcessingException e) {
            return ApiResponse.badRequest("ONT-003: Failed to serialize payload: " + e.getMessage());
        }

        Object snapshotRaw = dto.getSnapshot();
        String snapshotJson = null;
        if (snapshotRaw != null) {
            try {
                snapshotJson = snapshotRaw instanceof String s ? s : MAPPER.writeValueAsString(snapshotRaw);
            } catch (JsonProcessingException e) {
                snapshotJson = null;
            }
        }

        proposalService.insertProposal(domainCode, proposalType, targetEntity, payloadJson,
                snapshotJson, STATUS_DRAFT, author);

        // 查询刚插入的记录获取自增ID
        Map<String, Object> created = proposalService.findCreatedProposal();

        log.info("Ontology proposal created: {} [{}] proposalType={}", created.get("id"), proposalType, proposalType);
        return ApiResponse.success(toVO(created));
    }

    /**
     * PUT /api/v1/ontology/proposals/{id} — 更新提案。
     *
     * <p>T16-3：请求体 Map → {@link OntologyProposalSaveDTO}。
     * 仅 DRAFT 状态允许编辑内容字段；其他状态返回 400。
     */
    @PutMapping("/{id}")
    public ApiResponse<OntologyProposalVO> updateProposal(
            @PathVariable String id,
            @RequestBody OntologyProposalSaveDTO dto) {
        Map<String, Object> existing = proposalService.findProposalById(id);
        if (existing == null) {
            return ApiResponse.notFound("ONT-001: Proposal '" + id + "' not found");
        }

        String currentStatus = String.valueOf(existing.get("status"));
        if (!STATUS_DRAFT.equals(currentStatus)) {
            return ApiResponse.badRequest(
                    "ONT-004: Proposal '" + id + "' is in status " + currentStatus
                            + ", only DRAFT proposals can be edited");
        }

        // 构建更新（按 DTO 字段非空判定 → 拼 SQL）
        StringBuilder sql = new StringBuilder("UPDATE ecos_ontology_proposals SET updated_at=NOW()");
        List<Object> params = new ArrayList<>();

        if (dto.getDomainCode() != null) {
            sql.append(", domain_code=?");
            params.add(dto.getDomainCode());
        }
        String proposalType = firstNonBlank(dto.getProposalType(), dto.getPtypeAlt(), dto.getSourceAlt(),
                dto.getProposalTypeAlt(), dto.getTargetTypeAlt());
        if (proposalType != null) {
            sql.append(", proposal_type=?");
            params.add(proposalType);
        }
        String targetEntity = firstNonBlank(dto.getTargetEntity(), dto.getTargetIdAlt(), dto.getTargetEntityAlt());
        if (targetEntity != null) {
            sql.append(", target_entity=?");
            params.add(targetEntity);
        }
        String author = firstNonBlank(dto.getAuthor(), dto.getProposedByAlt());
        if (author != null) {
            sql.append(", author=?");
            params.add(author);
        }
        if (dto.getPayload() != null) {
            try {
                sql.append(", payload=?::jsonb");
                Object p = dto.getPayload();
                params.add(p instanceof String s ? s : MAPPER.writeValueAsString(p));
            } catch (JsonProcessingException e) {
                return ApiResponse.badRequest("ONT-003: Failed to serialize payload: " + e.getMessage());
            }
        }
        if (dto.getSnapshot() != null) {
            try {
                sql.append(", snapshot=?::jsonb");
                Object s = dto.getSnapshot();
                params.add(s instanceof String str ? str : MAPPER.writeValueAsString(s));
            } catch (JsonProcessingException e) {
                // ignore snapshot serialization error
            }
        }

        sql.append(" WHERE id=?::bigint");
        params.add(id);

        Map<String, Object> updated = proposalService.updateProposal(id, sql, params);
        log.info("Ontology proposal updated: {}", id);
        return ApiResponse.success(toVO(updated));
    }

    /**
     * DELETE /api/v1/ontology/proposals/{id} — 删除提案。
     * <p>仅 DRAFT 状态允许删除，避免误删审批流程中的记录。
     */
    @DeleteMapping("/{id}")
    public ApiResponse<String> deleteProposal(@PathVariable String id) {
        Map<String, Object> existing = proposalService.findProposalById(id);
        if (existing == null) {
            return ApiResponse.notFound("ONT-001: Proposal '" + id + "' not found");
        }

        String currentStatus = String.valueOf(existing.get("status"));
        if (!STATUS_DRAFT.equals(currentStatus)) {
            return ApiResponse.badRequest(
                    "ONT-004: Proposal '" + id + "' is in status " + currentStatus
                            + ", only DRAFT proposals can be deleted");
        }

        proposalService.deleteProposal(id);
        log.info("Ontology proposal deleted: {}", id);
        return ApiResponse.success("Proposal '" + id + "' deleted");
    }

    // ═══════════════ 审批流转 ═══════════════════

    /**
     * POST /api/v1/ontology/proposals/{id}/submit — 提交审批（DRAFT → PENDING）。
     */
    @PostMapping("/{id}/submit")
    public ApiResponse<OntologyProposalVO> submitProposal(@PathVariable String id) {
        return transition(id, STATUS_DRAFT, STATUS_PENDING, null);
    }

    /**
     * POST /api/v1/ontology/proposals/{id}/approve — 审批通过（PENDING → APPROVED）。
     * <p>Body 可选字段：reviewer（审批人）、reviewComment（审批意见）。
     */
    @PostMapping("/{id}/approve")
    public ApiResponse<OntologyProposalVO> approveProposal(
            @PathVariable String id,
            @RequestBody(required = false) OntologyProposalSaveDTO body) {
        return transition(id, STATUS_PENDING, STATUS_APPROVED, body);
    }

    /**
     * POST /api/v1/ontology/proposals/{id}/reject — 审批驳回（PENDING → REJECTED）。
     * <p>Body 可选字段：reviewer（审批人）、reviewComment（审批意见）。
     */
    @PostMapping("/{id}/reject")
    public ApiResponse<OntologyProposalVO> rejectProposal(
            @PathVariable String id,
            @RequestBody(required = false) OntologyProposalSaveDTO body) {
        return transition(id, STATUS_PENDING, STATUS_REJECTED, body);
    }

    // ═══════════════ 内部：状态流转 ═══════════════════

    /**
     * 统一状态流转逻辑。
     *
     * @param id           提案 ID
     * @param expectedFrom 期望的当前状态（不匹配则 400）
     * @param target       目标状态
     * @param body         请求体（可携带 reviewer/reviewComment），可为 null
     */
    private ApiResponse<OntologyProposalVO> transition(
            String id, String expectedFrom, String target, OntologyProposalSaveDTO body) {
        Map<String, Object> existing;
        try {
            existing = proposalService.queryForMap("SELECT * FROM ecos_ontology_proposals WHERE id=?::bigint", id);
        } catch (EmptyResultDataAccessException e) {
            return ApiResponse.notFound("ONT-001: Proposal '" + id + "' not found");
        }

        String currentStatus = String.valueOf(existing.get("status"));
        if (TERMINAL_STATUSES.contains(currentStatus)) {
            return ApiResponse.badRequest(
                    "ONT-005: Proposal '" + id + "' is already in terminal status " + currentStatus);
        }
        if (!expectedFrom.equals(currentStatus)) {
            return ApiResponse.badRequest(
                    "ONT-004: Proposal '" + id + "' is in status " + currentStatus
                            + ", expected " + expectedFrom + " to transition to " + target);
        }

        StringBuilder sql = new StringBuilder("UPDATE ecos_ontology_proposals SET status=?, updated_at=NOW()");
        List<Object> params = new ArrayList<>();
        params.add(target);

        // 兼容旧 reviewer / reviewerComment 字段
        if (body != null) {
            if (body.getReviewer() != null) {
                sql.append(", reviewer=?");
                params.add(body.getReviewer());
            }
            if (body.getReviewComment() != null) {
                sql.append(", reviewer_comment=?");
                params.add(body.getReviewComment());
            }
        }

        sql.append(" WHERE id=?::bigint");
        params.add(id);

        proposalService.update(sql.toString(), params.toArray());

        Map<String, Object> updated = proposalService.queryForMap(
                "SELECT * FROM ecos_ontology_proposals WHERE id=?::bigint", id);
        log.info("Ontology proposal {} transition: {} → {}", id, expectedFrom, target);
        return ApiResponse.success(toVO(updated));
    }

    // ═══════════════ PMO指令端点: verify + execute ═══════════════════

    /**
     * POST /api/v1/ontology/proposals/{id}/verify — 验证提案（检查冲突/完整性）。
     *
     * <p>T16-3：返回 Map → {@link OntologyProposalVerifyVO}。
     */
    @PostMapping("/{id}/verify")
    public ApiResponse<OntologyProposalVerifyVO> verifyProposal(@PathVariable String id) {
        Map<String, Object> existing;
        try {
            existing = proposalService.queryForMap("SELECT * FROM ecos_ontology_proposals WHERE id=?::bigint", id);
        } catch (EmptyResultDataAccessException e) {
            return ApiResponse.notFound("ONT-001: Proposal '" + id + "' not found");
        }

        String proposalType = String.valueOf(existing.getOrDefault("proposal_type", ""));
        Object payloadObj = existing.get("payload");
        List<String> issues = new ArrayList<>();
        boolean valid = true;

        // 解析 payload JSONB (PGobject → Map)
        Map<?, ?> payload = null;
        if (payloadObj != null) {
            try {
                if (payloadObj instanceof Map<?, ?> m) {
                    payload = m;
                } else {
                    payload = MAPPER.readValue(String.valueOf(payloadObj), Map.class);
                }
            } catch (Exception ignored) {
                // 解析失败 → payload 视为 empty（issues 后面会记录）
            }
        }

        if (payload != null) {
            switch (proposalType.toUpperCase()) {
                case "CREATE_ENTITY":
                case "NEW_OBJECT":
                    if (payload.get("displayName") == null && payload.get("name") == null) {
                        issues.add("missing displayName/name");
                        valid = false;
                    }
                    if (payload.get("apiName") == null && payload.get("code") == null) {
                        issues.add("missing apiName/code");
                        valid = false;
                    }
                    break;
                case "ADD_RELATIONSHIP":
                case "NEW_LINK":
                    if (payload.get("sourceType") == null && payload.get("source_entity") == null) {
                        issues.add("missing sourceType/source_entity");
                        valid = false;
                    }
                    if (payload.get("targetType") == null && payload.get("target_entity") == null) {
                        issues.add("missing targetType/target_entity");
                        valid = false;
                    }
                    break;
                default:
                    // 其他类型不在白名单内，不做 payload 字段校验
                    break;
            }
        } else {
            issues.add("payload is empty or unparseable");
            valid = false;
        }

        // 更新提案状态为 verified / rejected
        String newStatus = valid ? "verified" : STATUS_REJECTED;
        proposalService.update("UPDATE ecos_ontology_proposals SET status=? WHERE id=?::bigint", newStatus, id);

        OntologyProposalVerifyVO vo = new OntologyProposalVerifyVO();
        vo.setValid(valid);
        vo.setIssues(issues);
        vo.setProposal(proposalService.queryForMap("SELECT * FROM ecos_ontology_proposals WHERE id=?::bigint", id));
        log.info("Proposal {} verified: valid={}", id, valid);
        return ApiResponse.success(vo);
    }

    /**
     * POST /api/v1/ontology/proposals/{id}/execute — 执行已验证提案。
     */
    @PostMapping("/{id}/execute")
    public ApiResponse<OntologyProposalVO> executeProposal(@PathVariable String id) {
        Map<String, Object> existing;
        try {
            existing = proposalService.queryForMap("SELECT * FROM ecos_ontology_proposals WHERE id=?::bigint", id);
        } catch (EmptyResultDataAccessException e) {
            return ApiResponse.notFound("ONT-001: Proposal '" + id + "' not found");
        }

        String status = String.valueOf(existing.get("status"));
        if (!"verified".equals(status) && !"approved".equals(status) && !"pending".equals(status)
                && !STATUS_APPROVED.equals(status) && !STATUS_PENDING.equals(status)) {
            return ApiResponse.badRequest(
                    "ONT-004: Proposal must be verified/approved/pending to execute, current: " + status);
        }

        proposalService.update("UPDATE ecos_ontology_proposals SET status=?, updated_at=NOW() WHERE id=?::bigint",
                "executed", id);

        Map<String, Object> updated = proposalService.queryForMap(
                "SELECT * FROM ecos_ontology_proposals WHERE id=?::bigint", id);
        log.info("Proposal {} executed", id);
        return ApiResponse.success(toVO(updated));
    }

    // ═══════════════ T3: 审批+执行+版本发布联动 ═══════════════════

    /**
     * POST /api/v1/ontology/proposals/{id}/approve-and-publish — 一键审批通过、执行payload、创建并发布版本。
     *
     * <p>流程：
     * <ol>
     *   <li>查询提案，检查状态为 PENDING</li>
     *   <li>更新状态为 APPROVED，记录 reviewer/reviewer_comment</li>
     *   <li>调用 OntologyVersionService 创建新版本（基于 domain_code）</li>
     *   <li>执行 payload（根据 proposal_type 创建实体/属性/关系）</li>
     *   <li>发布版本（Draft → Published）</li>
     *   <li>更新提案状态为 EXECUTED，回填 version_id</li>
     *   <li>返回 {@link OntologyProposalPublishVO}</li>
     * </ol>
     */
    @PostMapping("/{id}/approve-and-publish")
    public ApiResponse<OntologyProposalPublishVO> approveAndPublish(
            @PathVariable String id,
            @RequestBody(required = false) OntologyProposalSaveDTO body) {
        // 1. 查询提案
        Map<String, Object> proposal = proposalService.findProposalById(id);
        if (proposal == null) {
            return ApiResponse.notFound("ONT-001: Proposal '" + id + "' not found");
        }

        // 检查状态为 PENDING 或 APPROVED
        String currentStatus = String.valueOf(proposal.get("status"));
        if (!STATUS_PENDING.equals(currentStatus) && !STATUS_APPROVED.equals(currentStatus)) {
            return ApiResponse.badRequest(
                    "ONT-004: Proposal '" + id + "' must be PENDING or APPROVED, current: " + currentStatus);
        }

        // 2. 更新为 APPROVED，记录审批信息
        // T16-3: body 改强类型 SaveDTO；reviewer 取自 dto.reviewer，reviewComment 取自 dto.reviewComment
        String reviewer = body != null && body.getReviewer() != null ? body.getReviewer() : "";
        String reviewerComment = body != null && body.getReviewComment() != null ? body.getReviewComment() : "";

        proposalService.approve(id, STATUS_APPROVED, reviewer, reviewerComment);

        String domainCode = String.valueOf(proposal.getOrDefault("domain_code", "default"));
        String proposalType = String.valueOf(proposal.getOrDefault("proposal_type", ""));

        // 3. 创建版本（使用 domain_code 作为 ontologyId）
        Long versionIdLong = null;
        String versionIdStr = null;
        try {
            Map<String, Object> versionBody = new LinkedHashMap<>();
            versionBody.put("changeLog", "Approve-and-publish from proposal " + id
                    + ": " + proposalType);
            versionBody.put("publisher", reviewer.isEmpty() ? "system" : reviewer);
            // T16-3: 版本 payload 动态嵌套豁免（versionService 历史契约）
            Map<String, Object> version = versionService.createVersion(domainCode, versionBody);
            versionIdStr = String.valueOf(version.get("id"));

            // 4. 执行 payload（根据类型创建实体/属性/关系）
            executePayload(domainCode, proposalType, proposal);

            // 5. 发布版本
            versionService.publishVersion(domainCode, versionIdStr);

            // 获取版本号用于回填
            Object verNo = version.get("versionNo");
            if (verNo != null) {
                try {
                    versionIdLong = Long.valueOf(String.valueOf(verNo));
                } catch (NumberFormatException nfe) {
                    versionIdLong = null;
                }
            }

            // 6. 更新提案为 EXECUTED，回填 version_id
            if (versionIdLong != null) {
                proposalService.markExecutedWithVersion(id, STATUS_EXECUTED, versionIdLong);
            } else {
                proposalService.markExecuted(id, STATUS_EXECUTED);
            }

            log.info("Proposal {} approve-and-publish complete: versionId={}", id, versionIdStr);
        } catch (Exception e) {
            log.error("Proposal {} approve-and-publish failed at step 3-5: {}", id, e.getMessage(), e);
            return ApiResponse.badRequest("ONT-006: Execution failed: " + e.getMessage());
        }

        // 7. 返回结果
        Map<String, Object> finalProposal = proposalService.findProposalById(id);

        OntologyProposalPublishVO vo = new OntologyProposalPublishVO();
        vo.setStatus(STATUS_EXECUTED);
        vo.setVersionId(versionIdStr);
        vo.setProposal(finalProposal);
        return ApiResponse.success(vo);
    }

    /**
     * 根据提案类型执行 payload，创建对应的本体元素。
     */
    private void executePayload(String domainCode, String proposalType, Map<String, Object> proposal) {
        Object payloadObj = proposal.get("payload");
        Map<?, ?> payload = null;
        if (payloadObj != null) {
            try {
                if (payloadObj instanceof Map<?, ?> m) {
                    payload = m;
                } else {
                    payload = MAPPER.readValue(String.valueOf(payloadObj), Map.class);
                }
            } catch (Exception e) {
                log.warn("Cannot parse payload for proposal execution: {}", e.getMessage());
                return;
            }
        }
        if (payload == null) {
            log.info("No payload to execute for proposal type={}", proposalType);
            return;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> payloadMap = (Map<String, Object>) payload;

        switch (proposalType.toUpperCase()) {
            case "CREATE_ENTITY":
                Map<String, Object> entityBody = new LinkedHashMap<>(payloadMap);
                if (!entityBody.containsKey("code")) {
                    entityBody.put("code", entityBody.getOrDefault("apiName", "auto_entity"));
                }
                if (!entityBody.containsKey("name")) {
                    entityBody.put("name", entityBody.getOrDefault("displayName", "Auto Entity"));
                }
                ontologyService.createEntity(domainCode, entityBody);
                log.info("Created entity via proposal: code={}", entityBody.get("code"));
                break;
            case "ADD_PROPERTY":
            case "MODIFY_PROPERTY":
                Map<String, Object> propBody = new LinkedHashMap<>(payloadMap);
                String entityId = String.valueOf(propBody.getOrDefault("entityId",
                        propBody.getOrDefault("entityCode",
                        propBody.getOrDefault("targetEntity", propBody.getOrDefault("entity_code", "")))));
                if (!entityId.isEmpty()) {
                    ontologyService.createProperty(entityId, propBody);
                    log.info("Added/Modified property via proposal: entityId={}", entityId);
                }
                break;
            case "ADD_RELATIONSHIP":
                Map<String, Object> relBody = new LinkedHashMap<>(payloadMap);
                String sourceEntityId = String.valueOf(relBody.getOrDefault("sourceEntityId",
                        relBody.getOrDefault("source_entity", domainCode)));
                ontologyService.createRelationship(sourceEntityId, relBody);
                log.info("Created relationship via proposal");
                break;
            default:
                log.info("Proposal type '{}' execution is no-op (no entity/property/relationship creation)",
                        proposalType);
                break;
        }
    }

    // ═══════════════ T16-3 工具方法 ═══════════════════

    /**
     * 任选第一个非空字符串（用于兼容旧字段映射）。
     */
    private static String firstNonBlank(@SuppressWarnings("unused") String... candidates) {
        if (candidates != null) {
            for (String c : candidates) {
                if (c != null && !c.isBlank()) {
                    return c;
                }
            }
        }
        return null;
    }

    /**
     * Map 行（ecos_ontology_proposals 表 SELECT * 直出）→ VO。
     * 字段名按 DB 下划线 → VO 驼峰。
     */
    private OntologyProposalVO toVO(Map<String, Object> row) {
        OntologyProposalVO vo = new OntologyProposalVO();
        if (row == null) {
            return vo;
        }
        vo.setId(row.get("id"));
        vo.setDomainCode(objToString(row.get("domain_code")));
        vo.setProposalType(objToString(row.get("proposal_type")));
        vo.setTargetEntity(objToString(row.get("target_entity")));
        // payload / snapshot 是 PG JSONB 列，PG JDBC 直出 PGobject → 原始 Object 透传（动态嵌套数据豁免）
        vo.setPayload(row.get("payload"));
        vo.setSnapshot(row.get("snapshot"));
        vo.setStatus(objToString(row.get("status")));
        vo.setAuthor(objToString(row.get("author")));
        vo.setReviewer(objToString(row.get("reviewer")));
        vo.setReviewerComment(objToString(row.get("reviewer_comment")));
        vo.setVersionId(row.get("version_id"));
        vo.setOptimisticLockVersion(row.get("optimistic_lock_version"));
        vo.setCreatedAt(objToString(row.get("created_at")));
        vo.setUpdatedAt(objToString(row.get("updated_at")));
        return vo;
    }

    /** Object → String（null 安全）。 */
    private static String objToString(Object o) {
        return o == null ? null : o.toString();
    }
}
