package com.chinacreator.gzcm.dccheng.ontology.controller;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

/**
 * 本体变更提案 Controller — 管理本体结构的变更提案与审批流转。
 *
 * <p>使用 {@link ConcurrentHashMap} 内存存储，进程重启后数据丢失；
 * 适合开发演示与轻量级原型。提案状态机：
 * {@code DRAFT → PENDING → (APPROVED | REJECTED)}，终态不可回退。</p>
 *
 * <h3>端点：</h3>
 * <ul>
 *   <li>GET    /api/v1/ontology/proposals               — 提案列表（可按 status / targetType 过滤）</li>
 *   <li>GET    /api/v1/ontology/proposals/{id}          — 提案详情</li>
 *   <li>POST   /api/v1/ontology/proposals               — 创建提案（初始状态 DRAFT）</li>
 *   <li>PUT    /api/v1/ontology/proposals/{id}          — 更新提案（仅 DRAFT 可改）</li>
 *   <li>DELETE /api/v1/ontology/proposals/{id}          — 删除提案（仅 DRAFT 可删）</li>
 *   <li>POST   /api/v1/ontology/proposals/{id}/submit   — 提交审批（DRAFT → PENDING）</li>
 *   <li>POST   /api/v1/ontology/proposals/{id}/approve  — 审批通过（PENDING → APPROVED）</li>
 *   <li>POST   /api/v1/ontology/proposals/{id}/reject   — 审批驳回（PENDING → REJECTED）</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/ontology/proposals")
public class OntologyProposalController {

    private static final Logger log = LoggerFactory.getLogger(OntologyProposalController.class);

    /** 内存存储：proposalId → 提案记录（线程安全） */
    private final ConcurrentHashMap<String, Map<String, Object>> store = new ConcurrentHashMap<>();

    /** 自增 ID 序列，与 OntologyService 风格保持一致 */
    private static final AtomicInteger ID_SEQ = new AtomicInteger(0);

    /** 提案状态常量 */
    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_REJECTED = "REJECTED";

    /** 终态集合：不可再变更 */
    private static final Set<String> TERMINAL_STATUSES = Set.of(STATUS_APPROVED, STATUS_REJECTED);

    public OntologyProposalController() {
    }

    private String nextId() {
        return "prp" + ID_SEQ.incrementAndGet();
    }

    // ═══════════════ 提案 CRUD ═══════════════════

    /**
     * GET /api/v1/ontology/proposals — 提案列表。
     *
     * @param status     可选，按状态过滤（DRAFT/PENDING/APPROVED/REJECTED）
     * @param targetType 可选，按变更目标类型过滤（如 ENTITY/PROPERTY/RELATIONSHIP）
     */
    @GetMapping
    public ApiResponse<List<Map<String, Object>>> listProposals(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "targetType", required = false) String targetType) {
        List<Map<String, Object>> result = store.values().stream()
            .filter(p -> status == null || status.isBlank() || status.equals(p.get("status")))
            .filter(p -> targetType == null || targetType.isBlank() || targetType.equals(p.get("targetType")))
            .collect(Collectors.toList());
        return ApiResponse.success(result);
    }

    /**
     * GET /api/v1/ontology/proposals/{id} — 提案详情。
     */
    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> getProposal(@PathVariable String id) {
        Map<String, Object> p = store.get(id);
        if (p == null) {
            return ApiResponse.notFound("ONT-001: Proposal '" + id + "' not found");
        }
        return ApiResponse.success(p);
    }

    /**
     * POST /api/v1/ontology/proposals — 创建提案。
     * <p>Body 字段：
     * <ul>
     *   <li>title — 必填，提案标题</li>
     *   <li>targetType — 必填，变更目标类型（ENTITY/PROPERTY/RELATIONSHIP/...）</li>
     *   <li>targetId — 可选，变更目标 ID（新增场景可空）</li>
     *   <li>changeType — 可选，变更类型（CREATE/UPDATE/DELETE，默认 UPDATE）</li>
     *   <li>payload — 可选，变更内容快照 Map</li>
     *   <li>description — 可选，说明</li>
     *   <li>proposedBy — 可选，提案人</li>
     * </ul>
     */
    @PostMapping
    public ApiResponse<Map<String, Object>> createProposal(@RequestBody Map<String, Object> body) {
        // PMO指令字段兼容: type→title, source→targetType
        String title = String.valueOf(body.getOrDefault("title", body.getOrDefault("type", ""))).trim();
        String targetType = String.valueOf(body.getOrDefault("targetType", body.getOrDefault("source", ""))).trim();
        if (title.isEmpty()) {
            return ApiResponse.badRequest("ONT-002: 'title' is required");
        }
        if (targetType.isEmpty()) {
            return ApiResponse.badRequest("ONT-002: 'targetType' is required");
        }
        String id = nextId();
        String now = Instant.now().toString();
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("id", id);
        p.put("title", title);
        p.put("targetType", targetType);
        p.put("targetId", body.getOrDefault("targetId", ""));
        p.put("changeType", body.getOrDefault("changeType", "UPDATE"));
        p.put("description", body.getOrDefault("description", ""));
        p.put("proposedBy", body.getOrDefault("proposedBy", "system"));
        p.put("payload", body.getOrDefault("payload", new LinkedHashMap<>()));
        p.put("status", STATUS_DRAFT);
        p.put("reviewer", "");
        p.put("reviewComment", "");
        p.put("createdAt", now);
        p.put("updatedAt", now);
        p.put("submittedAt", "");
        p.put("reviewedAt", "");
        store.put(id, p);
        log.info("Ontology proposal created: {} [{}] targetType={}", id, title, targetType);
        return ApiResponse.success(p);
    }

    /**
     * PUT /api/v1/ontology/proposals/{id} — 更新提案。
     * <p>仅 DRAFT 状态允许编辑内容字段；其他状态返回 400。
     */
    @PutMapping("/{id}")
    public ApiResponse<Map<String, Object>> updateProposal(
            @PathVariable String id,
            @RequestBody Map<String, Object> body) {
        Map<String, Object> existing = store.get(id);
        if (existing == null) {
            return ApiResponse.notFound("ONT-001: Proposal '" + id + "' not found");
        }
        if (!STATUS_DRAFT.equals(existing.get("status"))) {
            return ApiResponse.badRequest(
                "ONT-004: Proposal '" + id + "' is in status " + existing.get("status")
                + ", only DRAFT proposals can be edited");
        }
        Map<String, Object> updated = new LinkedHashMap<>(existing);
        if (body.containsKey("title")) updated.put("title", body.get("title"));
        if (body.containsKey("targetType")) updated.put("targetType", body.get("targetType"));
        if (body.containsKey("targetId")) updated.put("targetId", body.get("targetId"));
        if (body.containsKey("changeType")) updated.put("changeType", body.get("changeType"));
        if (body.containsKey("description")) updated.put("description", body.get("description"));
        if (body.containsKey("proposedBy")) updated.put("proposedBy", body.get("proposedBy"));
        if (body.containsKey("payload")) updated.put("payload", body.get("payload"));
        updated.put("updatedAt", Instant.now().toString());
        store.put(id, updated);
        log.info("Ontology proposal updated: {}", id);
        return ApiResponse.success(updated);
    }

    /**
     * DELETE /api/v1/ontology/proposals/{id} — 删除提案。
     * <p>仅 DRAFT 状态允许删除，避免误删审批流程中的记录。
     */
    @DeleteMapping("/{id}")
    public ApiResponse<String> deleteProposal(@PathVariable String id) {
        Map<String, Object> existing = store.get(id);
        if (existing == null) {
            return ApiResponse.notFound("ONT-001: Proposal '" + id + "' not found");
        }
        if (!STATUS_DRAFT.equals(existing.get("status"))) {
            return ApiResponse.badRequest(
                "ONT-004: Proposal '" + id + "' is in status " + existing.get("status")
                + ", only DRAFT proposals can be deleted");
        }
        store.remove(id);
        log.info("Ontology proposal deleted: {}", id);
        return ApiResponse.success("Proposal '" + id + "' deleted");
    }

    // ═══════════════ 审批流转 ═══════════════════

    /**
     * POST /api/v1/ontology/proposals/{id}/submit — 提交审批（DRAFT → PENDING）。
     */
    @PostMapping("/{id}/submit")
    public ApiResponse<Map<String, Object>> submitProposal(@PathVariable String id) {
        return transition(id, STATUS_DRAFT, STATUS_PENDING, null, null, "submitted");
    }

    /**
     * POST /api/v1/ontology/proposals/{id}/approve — 审批通过（PENDING → APPROVED）。
     * <p>Body 可选字段：reviewer（审批人）、reviewComment（审批意见）。
     */
    @PostMapping("/{id}/approve")
    public ApiResponse<Map<String, Object>> approveProposal(
            @PathVariable String id,
            @RequestBody(required = false) Map<String, Object> body) {
        return transition(id, STATUS_PENDING, STATUS_APPROVED, body, "approved", "approved");
    }

    /**
     * POST /api/v1/ontology/proposals/{id}/reject — 审批驳回（PENDING → REJECTED）。
     * <p>Body 可选字段：reviewer（审批人）、reviewComment（审批意见）。
     */
    @PostMapping("/{id}/reject")
    public ApiResponse<Map<String, Object>> rejectProposal(
            @PathVariable String id,
            @RequestBody(required = false) Map<String, Object> body) {
        return transition(id, STATUS_PENDING, STATUS_REJECTED, body, "rejected", "rejected");
    }

    // ═══════════════ 内部：状态流转 ═══════════════════

    /**
     * 统一状态流转逻辑。
     *
     * @param id            提案 ID
     * @param expectedFrom  期望的当前状态（不匹配则 400）
     * @param target        目标状态
     * @param body          请求体（可携带 reviewer / reviewComment），可为 null
     * @param reviewerTag   日志标签
     * @param reviewedTag   reviewedAt 写入标记
     */
    private ApiResponse<Map<String, Object>> transition(
            String id, String expectedFrom, String target,
            Map<String, Object> body, String reviewerTag, String reviewedTag) {
        Map<String, Object> existing = store.get(id);
        if (existing == null) {
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
        Map<String, Object> updated = new LinkedHashMap<>(existing);
        updated.put("status", target);
        String now = Instant.now().toString();
        updated.put("updatedAt", now);
        if (STATUS_PENDING.equals(target)) {
            updated.put("submittedAt", now);
        }
        if (body != null) {
            if (body.containsKey("reviewer")) updated.put("reviewer", body.get("reviewer"));
            if (body.containsKey("reviewComment")) updated.put("reviewComment", body.get("reviewComment"));
        }
        if (reviewedTag != null) {
            updated.put("reviewedAt", now);
        }
        store.put(id, updated);
        log.info("Ontology proposal {} {}: {} → {}", id, reviewerTag == null ? "transition" : reviewerTag,
            expectedFrom, target);
        return ApiResponse.success(updated);
    }

    // ═══════════════ PMO指令端点: verify + execute ═══════════════════

    /**
     * POST /api/v1/ontology/proposals/{id}/verify — 验证提案（检查冲突/完整性）
     */
    @PostMapping("/{id}/verify")
    public ApiResponse<Map<String, Object>> verifyProposal(@PathVariable String id) {
        Map<String, Object> existing = store.get(id);
        if (existing == null) {
            return ApiResponse.notFound("ONT-001: Proposal '" + id + "' not found");
        }
        String type = String.valueOf(existing.getOrDefault("type", ""));
        Object payload = existing.get("payload");
        List<String> issues = new ArrayList<>();
        boolean valid = true;

        if (payload instanceof Map) {
            Map<?, ?> p = (Map<?, ?>) payload;
            switch (type) {
                case "NEW_OBJECT":
                    if (p.get("displayName") == null) { issues.add("missing displayName"); valid = false; }
                    if (p.get("apiName") == null) { issues.add("missing apiName"); valid = false; }
                    break;
                case "NEW_LINK":
                    if (p.get("sourceType") == null) { issues.add("missing sourceType"); valid = false; }
                    if (p.get("targetType") == null) { issues.add("missing targetType"); valid = false; }
                    break;
            }
        }

        Map<String, Object> updated = new LinkedHashMap<>(existing);
        updated.put("status", valid ? "verified" : "rejected");
        updated.put("verifiedAt", Instant.now().toString());
        store.put(id, updated);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("valid", valid);
        result.put("issues", issues);
        result.put("proposal", updated);
        log.info("Proposal {} verified: valid={}", id, valid);
        return ApiResponse.success(result);
    }

    /**
     * POST /api/v1/ontology/proposals/{id}/execute — 执行已验证提案
     */
    @PostMapping("/{id}/execute")
    public ApiResponse<Map<String, Object>> executeProposal(@PathVariable String id) {
        Map<String, Object> existing = store.get(id);
        if (existing == null) {
            return ApiResponse.notFound("ONT-001: Proposal '" + id + "' not found");
        }
        String status = String.valueOf(existing.get("status"));
        if (!"verified".equals(status) && !"approved".equals(status) && !"pending".equals(status)) {
            return ApiResponse.badRequest("ONT-004: Proposal must be verified/approved/pending to execute, current: " + status);
        }

        Map<String, Object> updated = new LinkedHashMap<>(existing);
        updated.put("status", "executed");
        updated.put("executedAt", Instant.now().toString());
        store.put(id, updated);

        log.info("Proposal {} executed: type={}", id, updated.get("type"));
        return ApiResponse.success(updated);
    }
}
