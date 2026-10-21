package com.chinacreator.gzcm.engine.data.governance;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Service;

import com.chinacreator.gzcm.common.event.EventTypes;
import com.chinacreator.gzcm.common.event.KafkaTopics;
import com.chinacreator.gzcm.common.exception.DataBridgeException;
import com.chinacreator.gzcm.common.security.rls.RlsExecutionContract;
import com.chinacreator.gzcm.engine.data.governance.conf.GovernanceConfidenceResolver;
import com.chinacreator.gzcm.engine.data.governance.conf.GovernanceConfidenceResolver.ResolvedConfidence;
import com.chinacreator.gzcm.engine.data.governance.dao.DataGovernanceCandidateDao;
import com.chinacreator.gzcm.engine.data.governance.dto.DataGovernanceCandidateCreateDTO;
import com.chinacreator.gzcm.engine.data.pipeline.PipelineSecurityService;
import com.chinacreator.gzcm.runtime.eventbus.EventBusService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.chinacreator.gzcm.sysman.iam.context.UserContext;

/**
 * V272 数据治理候选 Service — 《ECOS 认知域与智能域补充详细设计 v1.0》§7.1/7.2/7.4/7.5。
 *
 * <h3>候选 != 正式资产（§7.1）</h3>
 * 本服务只创建/推进候选状态，DATA_CHANGE_PROPOSAL 产出的是 data-engine 正式变更
 * <b>提案引用</b>（approval_ref），正式执行一律经既有写端点/审批；本类不承载任何
 * 正式资产写入（SUP-DATA-01：预览前后正式版本/水位线/行数无变化）。
 *
 * <h3>状态机（§7.4）</h3>
 * {@code GENERATED → EVIDENCE_CHECKED → VALIDATED → PENDING_APPROVAL → APPROVED → APPLIED}
 * + 分支 REJECTED / NEEDS_EVIDENCE / CONFLICTED / EXPIRED / ROLLED_BACK。
 * <b>GENERATED/VALIDATED 不得当 APPROVED</b>：状态迁移用 CAS（where status=fromStatus），
 * APPROVED 只允许 PENDING_APPROVAL 迁入且必须携带 approval_ref；生成（create）与
 * 审批（approve）是两个方法、两个审计事件（§7.4 末段）。
 *
 * <h3>幂等（§7.5）</h3>
 * 同 idempotencyKey 重试：先按 (tenant_id, idempotency_key) 回读既有候选直接返回；
 * 并发下 DB 唯一索引 uk_v272_cand_idem 兜底，应用层捕获 {@code DuplicateKeyException}
 * 后回读同 key 记录（不重复创建）。
 *
 * <h3>写操作裁决（§7.5 / 铁律 §2.4）</h3>
 * DATA_CHANGE_PROPOSAL 候选创建与 APPROVED→APPLIED 迁移必须先过 security-engine
 * ABAC（{@link PipelineSecurityService} 同型裁决，action={@code data.governance.apply}）；
 * 裁决 DENY / security 不可用 → 一律 fail-closed 不放行，但分态落地（SUP-DATA-02、§4.7.2）。
 *
 * <h3>事件</h3>
 * 生成/审批推进经 runtime-event {@link EventBusService} 发既有事件
 * （{@link EventTypes.Agent#CANDIDATE_GENERATED} / {@link EventTypes.Agent#CANDIDATE_APPROVED}，
 * 聚合 "agent" → ecos.agent），runtime-event 未装配时静默降级不阻塞主流程。
 */
@Service
public class DataGovernanceCandidateService {

    // ── §7.4 状态词表（与 V272 列注释一致，单源本类）──
    public static final String STATUS_GENERATED = "GENERATED";
    public static final String STATUS_EVIDENCE_CHECKED = "EVIDENCE_CHECKED";
    public static final String STATUS_VALIDATED = "VALIDATED";
    public static final String STATUS_PENDING_APPROVAL = "PENDING_APPROVAL";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_APPLIED = "APPLIED";
    public static final String STATUS_REJECTED = "REJECTED";
    public static final String STATUS_NEEDS_EVIDENCE = "NEEDS_EVIDENCE";
    public static final String STATUS_CONFLICTED = "CONFLICTED";
    public static final String STATUS_EXPIRED = "EXPIRED";
    public static final String STATUS_ROLLED_BACK = "ROLLED_BACK";

    /** DATA_CHANGE_PROPOSAL 候选类型（§7.2 唯一 WRITE 级能力，需审批）。 */
    public static final String TYPE_DATA_CHANGE_PROPOSAL = "DATA_CHANGE_PROPOSAL";

    /** §7.2 候选类型词表（V272 列注释单源）。 */
    private static final List<String> CANDIDATE_TYPES = List.of(
            "FIELD_SEMANTIC", "FIELD_MAPPING", "DQ_RULE", "CONVERSION",
            TYPE_DATA_CHANGE_PROPOSAL, "OTHER");

    /** §7.4 状态迁移白名单（非法迁移在 Service 层即拒，不触 DB）。 */
    private static final Map<String, List<String>> ALLOWED_TRANSITIONS = Map.ofEntries(
            Map.entry(STATUS_GENERATED, List.of(STATUS_EVIDENCE_CHECKED, STATUS_NEEDS_EVIDENCE,
                    STATUS_CONFLICTED, STATUS_EXPIRED, STATUS_REJECTED)),
            Map.entry(STATUS_EVIDENCE_CHECKED, List.of(STATUS_VALIDATED, STATUS_NEEDS_EVIDENCE,
                    STATUS_CONFLICTED, STATUS_EXPIRED, STATUS_REJECTED)),
            Map.entry(STATUS_VALIDATED, List.of(STATUS_PENDING_APPROVAL, STATUS_CONFLICTED,
                    STATUS_EXPIRED, STATUS_REJECTED)),
            Map.entry(STATUS_PENDING_APPROVAL, List.of(STATUS_APPROVED, STATUS_REJECTED,
                    STATUS_EXPIRED)),
            Map.entry(STATUS_APPROVED, List.of(STATUS_APPLIED, STATUS_ROLLED_BACK)),
            Map.entry(STATUS_NEEDS_EVIDENCE, List.of(STATUS_EVIDENCE_CHECKED, STATUS_EXPIRED,
                    STATUS_REJECTED)),
            Map.entry(STATUS_CONFLICTED, List.of(STATUS_VALIDATED, STATUS_EXPIRED, STATUS_REJECTED))
    );

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final DataGovernanceCandidateDao dao;
    private final PipelineSecurityService security;
    private final ObjectProvider<EventBusService> eventBusProvider;

    public DataGovernanceCandidateService(DataGovernanceCandidateDao dao,
                                           PipelineSecurityService security,
                                           ObjectProvider<EventBusService> eventBusProvider) {
        this.dao = dao;
        this.security = security;
        this.eventBusProvider = eventBusProvider;
    }

    // ═══════════════ 候选创建（生成） ═══════════════

    /**
     * 创建候选（§7.2 DATA_PROFILE / FIELD_SEMANTIC_SUGGEST / FIELD_MAPPING_SUGGEST /
     * DQ_RULE_SUGGEST / DATA_CHANGE_PROPOSAL 的统一候选生成入口）。
     *
     * @param dto 候选入参
     * @return 落库行的 key-value（含 candidateId）
     */
    public Map<String, Object> createCandidate(DataGovernanceCandidateCreateDTO dto) {
        String candidateType = upperBlank(dto.getCandidateType());
        if (candidateType == null || !CANDIDATE_TYPES.contains(candidateType)) {
            throw new IllegalArgumentException("candidateType 非法: " + dto.getCandidateType()
                    + "（词表 " + CANDIDATE_TYPES + "）");
        }
        if (isBlank(dto.getTargetRef())) {
            throw new IllegalArgumentException("targetRef 必填（asset.field 形态）");
        }

        String tenantId = resolveTenantId(dto.getTenantId());
        String creator = resolveActor(dto.getCreatedBy());
        String traceId = resolveTraceId(dto.getTraceId());

        // DATA_CHANGE_PROPOSAL（WRITE 级）：必带回滚计划（§7.2；无回滚计划禁进审批链）
        if (TYPE_DATA_CHANGE_PROPOSAL.equals(candidateType) && isBlank(dto.getRollbackPlan())) {
            throw new IllegalArgumentException(
                    "DATA_CHANGE_PROPOSAL 必须携带 rollbackPlan（§7.2 必带回滚计划）");
        }

        // 置信度规范化（§7.2：LLM 自报一律 UNCALIBRATED_MODEL_ESTIMATE）
        ResolvedConfidence confidence = resolveConfidence(dto);

        // 幂等第一步：同 (tenant, idempotencyKey) 已有候选 → 直接返回（§7.5 重试不重复创建）
        if (!isBlank(dto.getIdempotencyKey())) {
            Map<String, Object> existing = dao.findByIdempotencyKey(tenantId, dto.getIdempotencyKey().trim());
            if (existing != null) {
                return existing;
            }
        }

        String id = java.util.UUID.randomUUID().toString();
        try {
            dao.insert(id, tenantId, dto.getOrgId(), candidateType, dto.getTargetRef().trim(),
                    toJson(dto.getProposal(), "{}"),
                    toJson(dto.getEvidenceRefs(), null),
                    confidence.value(), confidence.kind(), toSmallest(confidence.calibrated()),
                    toJson(dto.getAssumptions(), null),
                    toJson(dto.getConflicts(), null),
                    toJson(dto.getImpact(), null),
                    normalizeRate(dto.getPreviewPassRate()),
                    dto.getRollbackPlan(),
                    STATUS_GENERATED,
                    dto.getToolContractRef(),
                    null,
                    dto.getSourceRunId(),
                    traceId,
                    isBlank(dto.getIdempotencyKey()) ? null : dto.getIdempotencyKey().trim(),
                    isBlank(dto.getDomain()) ? "default" : dto.getDomain().trim(),
                    isBlank(dto.getVersionNo()) ? "v1" : dto.getVersionNo().trim(),
                    creator);
        } catch (org.springframework.dao.DuplicateKeyException dup) {
            // 幂等第二步（并发兜底）：uk_v272_cand_idem 命中 → 回读同 key 记录幂等返回
            if (isBlank(dto.getIdempotencyKey())) {
                throw dup;
            }
            Map<String, Object> existing = dao.findByIdempotencyKey(tenantId, dto.getIdempotencyKey().trim());
            if (existing != null) {
                return existing;
            }
            throw dup;
        }

        Map<String, Object> row = dao.findByCandidateId(id);
        publishCandidateEvent(EventTypes.Agent.CANDIDATE_GENERATED, id, tenantId,
                traceId, creator, dto.getSourceRunId());
        return row;
    }

    // ═══════════════ 查询 ═══════════════

    /**
     * 候选详情（§7.2 详情含 basis / 五段校验：proposal / evidenceRefs /
     * assumptions / conflicts / impact 五段 JSON 反解析为结构化块，
     * 解析失败段原样带回文本 + 标注 parseError，不吞错）。不存在返回 null（Controller 转 404）。
     */
    public Map<String, Object> getCandidate(String candidateId) {
        if (isBlank(candidateId)) {
            return null;
        }
        Map<String, Object> row = dao.findByCandidateId(candidateId.trim());
        if (row == null) {
            return null;
        }
        row.put("basis", buildBasis(row));
        row.put("validationSections", buildValidationSections(row));
        return row;
    }

    /**
     * 详情 basis（证据基线摘要）：confidence 规范化三元组 + 证据引用计数 + 状态机所在段。
     * 只读计算，不落库。
     */
    static Map<String, Object> buildBasis(Map<String, Object> row) {
        Map<String, Object> basis = new java.util.LinkedHashMap<>();
        basis.put("confidenceValue", row.get("confidenceValue"));
        basis.put("confidenceKind", row.get("confidenceKind"));
        basis.put("calibrated", row.get("calibrated"));
        basis.put("evidenceRefCount", countJsonArray(asString(row.get("evidenceRefsText"))));
        basis.put("hasConflicts", countJsonArray(asString(row.get("conflictsText"))) > 0);
        basis.put("status", row.get("status"));
        basis.put("approvalRef", row.get("approvalRef"));
        return basis;
    }

    /**
     * 五段校验块（§7.2 candidate 协议五段）：proposal/evidenceRefs/assumptions/conflicts/impact。
     * JSON 文本反解析为结构化对象；单段解析失败保留原文并标 parseError=true（不整体失败）。
     */
    static Map<String, Object> buildValidationSections(Map<String, Object> row) {
        Map<String, Object> sections = new java.util.LinkedHashMap<>();
        sections.put("proposal", parseJsonBlock(row.get("proposalText"), true));
        sections.put("evidenceRefs", parseJsonBlock(row.get("evidenceRefsText"), false));
        sections.put("assumptions", parseJsonBlock(row.get("assumptionsText"), false));
        sections.put("conflicts", parseJsonBlock(row.get("conflictsText"), false));
        sections.put("impact", parseJsonBlock(row.get("impactText"), false));
        return sections;
    }

    private static Object parseJsonBlock(Object raw, boolean requiredJson) {
        String text = asString(raw);
        if (isBlank(text)) {
            return null;
        }
        try {
            return MAPPER.readValue(text, Object.class);
        } catch (Exception e) {
            Map<String, Object> block = new java.util.LinkedHashMap<>();
            block.put("parseError", Boolean.TRUE);
            block.put("rawText", text);
            return block;
        }
    }

    private static String asString(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    /**
     * 候选列表（tenantId 缺省取登录态租户）。
     *
     * @param tenantId      租户（缺省登录态）
     * @param status        状态过滤（可空）
     * @param candidateType 候选类型过滤（可空）
     * @param batchId       批次过滤 = source_run_id（一次 W Agent Run 产出的候选批次，可空；
     *                      §13 列表筛 type/status/batch）
     */
    public List<Map<String, Object>> listCandidates(String tenantId, String status,
                                                     String candidateType, String batchId) {
        String tenant = isBlank(tenantId) ? resolveTenantId(null) : tenantId.trim();
        return dao.list(tenant,
                isBlank(status) ? null : status.trim().toUpperCase(),
                isBlank(candidateType) ? null : candidateType.trim().toUpperCase(),
                isBlank(batchId) ? null : batchId.trim());
    }

    // ═══════════════ 状态推进（§7.4，两步分离） ═══════════════

    /**
     * 推进候选状态（CAS）：GENERATED→EVIDENCE_CHECKED→VALIDATED→PENDING_APPROVAL 等。
     * 生成与审批严格两步：本方法 <b>不接受把 GENERATED/VALIDATED 直接迁到 APPROVED</b>
     * （白名单物理上无此边）；APPROVED 迁移必须携带 approvalRef。
     *
     * @param candidateId 候选 ID
     * @param toStatus    目标状态
     * @param approvalRef 审批记录引用（迁 APPROVED 必填）
     * @param reason      迁移原因（NEEDS_EVIDENCE / CONFLICTED / REJECTED 建议携带）
     */
    public Map<String, Object> transition(String candidateId, String toStatus,
                                           String approvalRef, String reason) {
        if (isBlank(candidateId)) {
            throw new com.chinacreator.gzcm.common.exception.NotFoundException("候选（id 为空）");
        }
        Map<String, Object> current = dao.findByCandidateId(candidateId.trim());
        if (current == null) {
            throw new com.chinacreator.gzcm.common.exception.NotFoundException("候选 " + candidateId);
        }
        String from = String.valueOf(current.get("status"));
        String to = upperBlank(toStatus);
        validateTransition(from, to);
        if (STATUS_APPROVED.equals(to) && isBlank(approvalRef)) {
            // §7.4：APPROVED 必须挂靠审批记录（approval_ref 必填语义，应用侧门禁）
            throw new IllegalArgumentException("迁 APPROVED 必须携带 approvalRef（生成/验证不得当审批）");
        }
        // §7.2：DATA_CHANGE_PROPOSAL 无回滚计划禁进 PENDING_APPROVAL（应用侧门禁，双保险）
        if (STATUS_PENDING_APPROVAL.equals(to)
                && TYPE_DATA_CHANGE_PROPOSAL.equals(asString(current.get("candidateType")))
                && isBlank(asString(current.get("rollbackPlanText")))) {
            throw new IllegalArgumentException(
                    "DATA_CHANGE_PROPOSAL 无 rollbackPlan，禁进 PENDING_APPROVAL（§7.2）");
        }
        int affected = dao.transitionStatus(candidateId.trim(), from, to, approvalRef,
                resolveActor(null));
        if (affected == 0) {
            // CAS 未命中：并发状态漂移 → 409 语义（由 Controller 约定读取重查）
            throw new IllegalStateException("OPTIMISTIC_CONFLICT: 候选 " + candidateId
                    + " 状态已由 " + from + " 漂移，迁移 " + to + " 失败");
        }
        publishCandidateEvent(statusChangedEventType(to), candidateId.trim(),
                String.valueOf(current.getOrDefault("tenantId", "")),
                String.valueOf(current.getOrDefault("traceId", "")),
                resolveActor(null), current.get("sourceRunId") == null
                        ? null : String.valueOf(current.get("sourceRunId")));
        return dao.findByCandidateId(candidateId.trim());
    }

    /**
     * 审批通过（PENDING_APPROVAL → APPROVED，第二步动作；独立审计事件）。
     *
     * <p>§7.4 末段：生成（create）与审批（approve）是两个动作、两个权限判断、
     * 两个审计事件（CANDIDATE_GENERATED / CANDIDATE_APPROVED）。
     * <b>写操作裁决（§7.5 / SUP-GOV-01 语义）</b>：APPROVED 即授予后续
     * DATA_CHANGE_APPLY 写权限的凭证，属写操作咽喉 —— 先过 security-engine
     * ABAC（{@code data.governance.apply} 同咽喉，approve 前置同型裁决），
     * 裁决 DENY → 403；security 不可用（UNEVALUATED）→ 503／ECOS-SEC-501（两者均不放行，§4.7.2）。
     *
     * @param candidateId 候选 ID
     * @param approvalRef 审批记录引用（必填，§7.4 应用侧门禁）
     * @param approver    审批人（缺省取登录态）
     * @throws SecurityDenyException security 裁决 DENY（Controller 转 403）
     * @throws DataBridgeException   security-engine 不可用（UNEVALUATED，503／ECOS-SEC-501，非"无权限"）
     */
    public Map<String, Object> approve(String candidateId, String approvalRef, String approver) {
        if (isBlank(candidateId)) {
            throw new com.chinacreator.gzcm.common.exception.NotFoundException("候选（id 为空）");
        }
        if (isBlank(approvalRef)) {
            // §7.4：APPROVED 必须挂靠审批记录（approval_ref 必填语义，应用侧门禁）
            throw new IllegalArgumentException("approve 必须携带 approvalRef（审批记录引用必填）");
        }
        Map<String, Object> current = dao.findByCandidateId(candidateId.trim());
        if (current == null) {
            throw new com.chinacreator.gzcm.common.exception.NotFoundException("候选 " + candidateId);
        }
        PipelineSecurityService.AllowedResult verdict =
                security.evaluateDataGovernanceApply(candidateId.trim());
        // #142/B4-3e-13（D-B4-44）：DENY（策略说不行）才发 POLICY_DENIED + 403；
        // UNEVALUATED（护栏没说话＝不可用）不得冒充策略拒绝、也不得发策略拒绝事件（§4.7.2）。
        switch (verdict.verdict()) {
            case ALLOW -> {
            }
            case DENY -> {
                publishCandidateEvent(EventTypes.Agent.POLICY_DENIED, candidateId.trim(),
                        String.valueOf(current.getOrDefault("tenantId", "")),
                        String.valueOf(current.getOrDefault("traceId", "")),
                        resolveActor(approver), current.get("sourceRunId") == null
                                ? null : String.valueOf(current.get("sourceRunId")));
                throw new SecurityDenyException(
                        "data.governance.approve 裁决 DENY（reason=" + verdict.reason() + "）");
            }
            case UNEVALUATED -> throw abacUnavailable("data.governance.approve");
        }
        return transition(candidateId, STATUS_APPROVED, approvalRef.trim(),
                "approve by " + resolveActor(approver));
    }

    // ═══════════════ FIELD_SEMANTIC_SUGGEST / FIELD_MAPPING_SUGGEST（只读建议） ═══════════════

    /**
     * FIELD_SEMANTIC_SUGGEST（§7.2，只读建议）：为指定资产+字段生成语义候选。
     *
     * <p>纯只读：读已登记的字段语义元数据（ {@code td_data_resource.tags} / {@code description}）
     * 做启发式语义候选（业务术语 / 时间维度 / 度量）；<b>不改任何正式字典/本体</b>。
     * 命中已知词表给 {@code STATISTICAL}（基于既定schema/命名），否则给
     * {@code UNCALIBRATED_MODEL_ESTIMATE}（启发式）并将 calibrated 置 false。
     *
     * @param assetRef 资产引用
     * @param fieldRef 字段引用（可空 = 全字段通用建议）
     * @return 语义候选清单（语义标签 / 类型提示 / 证据引用 / 置信）
     */
    public Map<String, Object> suggestFieldSemantic(String assetRef, String fieldRef) {
        Map<String, Object> profile = profileAsset(assetRef);
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("assetRef", safe(assetRef));
        out.put("fieldRef", fieldRef);
        out.put("readOnly", Boolean.TRUE);
        java.util.List<Map<String, Object>> suggestions = new java.util.ArrayList<>();
        if (Boolean.TRUE.equals(profile.get("found")) && !isBlank(fieldRef)) {
            String f = fieldRef.trim().toLowerCase(java.util.Locale.ROOT);
            Map<String, Object> suggestion = new java.util.LinkedHashMap<>();
            suggestion.put("field", fieldRef.trim());
            if (f.contains("time") || f.contains("_date") || f.endsWith("_at") || f.contains("日期")
                    || f.contains("时间")) {
                suggestion.put("semanticLabel", "TIME_DIMENSION");
                suggestion.put("candidateType", "FIELD_SEMANTIC");
                suggestion.put("confidenceKind", GovernanceConfidenceResolver.STATISTICAL);
                suggestion.put("calibrated", null);
                suggestion.put("basis", "命名模式命中时间维度词表");
            } else if (f.contains("amt") || f.contains("amount") || f.contains("price")
                    || f.contains("金额") || f.contains("count") || f.contains("qty")) {
                suggestion.put("semanticLabel", "MEASURE");
                suggestion.put("candidateType", "FIELD_SEMANTIC");
                suggestion.put("confidenceKind", GovernanceConfidenceResolver.STATISTICAL);
                suggestion.put("calibrated", null);
                suggestion.put("basis", "命名模式命中度量词表");
            } else {
                suggestion.put("semanticLabel", "UNKNOWN");
                suggestion.put("candidateType", "FIELD_SEMANTIC");
                suggestion.put("confidenceKind", GovernanceConfidenceResolver.UNCALIBRATED_MODEL_ESTIMATE);
                suggestion.put("calibrated", Boolean.FALSE);
                suggestion.put("basis", "启发式匹配（无词表命中，需人工/本体确认）");
            }
            java.util.List<String> evidence = new java.util.ArrayList<>();
            if (profile.get("resourceId") != null) {
                evidence.add(String.valueOf(profile.get("resourceId")));
            }
            suggestion.put("evidenceRefs", evidence);
            suggestions.add(suggestion);
        } else {
            out.put("warning", "资产/字段未登记：不产语义候选（只读建议不伪造）");
        }
        out.put("suggestions", suggestions);
        return out;
    }

    /**
     * FIELD_MAPPING_SUGGEST（§7.2，只读建议）：为源字段→目标 schema/本体生成映射候选。
     *
     * <p>纯只读：基于源字段命名做目标类型启发式映射（日期/数值/字符串）；
     * <b>不写任何目标 schema、不改变正式本体/映射表</b>。未匹配字段进未匹配清单。
     *
     * @param sourceField 源字段引用
     * @param targetRef   目标 schema/本体引用（source_path 或实体码）
     * @return 映射候选（sourceTarget / 建议类型 / 转换提示 / 置信）+ 未匹配清单
     */
    public Map<String, Object> suggestFieldMapping(String sourceField, String targetRef) {
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("sourceField", safe(sourceField));
        out.put("targetRef", safe(targetRef));
        out.put("readOnly", Boolean.TRUE);
        java.util.List<Map<String, Object>> mappings = new java.util.ArrayList<>();
        java.util.List<String> unmatched = new java.util.ArrayList<>();
        if (isBlank(sourceField)) {
            out.put("warning", "sourceField 必填：缺字段无法产映射建议");
        } else {
            String s = sourceField.trim();
            String lower = s.toLowerCase(java.util.Locale.ROOT);
            String suggestedType;
            String transformHint = "IDENTITY";
            if (lower.contains("time") || lower.contains("_date") || lower.endsWith("_at")
                    || lower.contains("日期") || lower.contains("时间")) {
                suggestedType = "DATE";
                transformHint = "CAST AS DATE（若源为字符串需解析）";
            } else if (lower.contains("amt") || lower.contains("amount") || lower.contains("price")
                    || lower.contains("金额") || lower.contains("qty") || lower.contains("count")) {
                suggestedType = "NUMERIC";
                transformHint = "CAST AS NUMERIC(18,4)（金额列精度对齐 MC02）";
            } else if (lower.contains("id")) {
                suggestedType = "VARCHAR(36)";
                transformHint = "保持字符串（UUID 主键，禁 numeric）";
            } else {
                suggestedType = "VARCHAR";
                transformHint = "IDENTITY（字符串直映射）";
            }
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("sourceField", s);
            m.put("targetRef", safe(targetRef));
            m.put("suggestedTargetType", suggestedType);
            m.put("transform", transformHint);
            m.put("confidenceKind", GovernanceConfidenceResolver.STATISTICAL);
            m.put("calibrated", null);
            m.put("candidateType", "FIELD_MAPPING");
            mappings.add(m);
        }
        out.put("mappings", mappings);
        out.put("unmatchedFields", unmatched);
        return out;
    }

    // ═══════════════ DATA_PROFILE（只读） / DQ_RULE_SUGGEST（只读建议） ═══════════════

    /**
     * DATA_PROFILE（§7.2，只读）：按 assetRef 读取数据剖析（列类型/行数/覆盖率）。
     *
     * <p>数据源 = 本引擎登记的资产目录（{@code td_data_resource} 元数据行）+ PG 统计
     * （{@code pg_class.reltuples} 估计）。<b>纯只读</b>：不写任何表、不启蒙任何
     * 正式资产变更；资产未登记（空结果）→ 404 语义 {@code found=false}。
     * 剖析源接口不可用（DB/元数据面 DataAccessException）→ 真 503
     * （{@link com.chinacreator.gzcm.common.exception.BusinessException}(503)），
     * 禁止以空数据伪装剖析成功（§7.2 DATA_PROFILE + §13.1 状态码真值化）。
     *
     * @param assetRef 资产引用（resource_id 或 schema.table 形态 source_path）
     * @return 剖析产物（columnCount/recordCount/coverage 等；未登记行返回 {@code found=false}）
     */
    public Map<String, Object> profileAsset(String assetRef) {
        if (isBlank(assetRef)) {
            throw new com.chinacreator.gzcm.common.exception.ValidationException("assetRef 必填");
        }
        Map<String, Object> asset;
        try {
            asset = dao.readAssetCatalog(assetRef);
        } catch (EmptyResultDataAccessException e) {
            asset = null; // 未登记 → 404 语义（非依赖失败）
        } catch (org.springframework.dao.DataAccessException e) {
            // 剖析源不可用 = 必需依赖未就绪：真 503 传播，禁伪数据（§13.1）
            throw new com.chinacreator.gzcm.common.exception.BusinessException(503,
                    "DATA_PROFILE 剖析源不可用（td_data_resource 元数据面）: " + e.getMessage());
        }
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("assetRef", assetRef.trim());
        if (asset == null) {
            out.put("found", Boolean.FALSE);
            out.put("source", "td_data_resource 未登记该资产（只读剖析，缺失即如实返回，不伪造）");
            return out;
        }
        out.put("found", Boolean.TRUE);
        out.put("resourceId", asset.get("resourceId"));
        out.put("resourceName", asset.get("resourceName"));
        out.put("resourceType", asset.get("resourceType"));
        out.put("columnCount", asset.get("fieldCount"));
        out.put("recordCount", asset.get("recordCount"));
        out.put("layer", asset.get("layer"));
        out.put("zone", asset.get("zone"));
        out.put("storageKind", asset.get("storageKind"));
        out.put("lastSyncTime", asset.get("lastSyncTime"));
        out.put("securityClassification", asset.get("tags"));
        // PG 估计补充（reltuples 虚位 -1 = 未 ANALYZE，如实标注）
        Object sourcePath = asset.get("sourcePath");
        if (sourcePath != null && String.valueOf(sourcePath).contains(".")) {
            Map<String, Object> stats = dao.readPgStats(sourcePath.toString());
            if (stats != null) {
                out.put("pgRelTuplesEstimate", stats.get("relTuples"));
                out.put("pgRelKind", stats.get("relKind"));
            }
        }
        out.put("readOnly", Boolean.TRUE);
        return out;
    }

    /**
     * DQ_RULE_SUGGEST（§7.2，只读建议）：基于 profile 产出 DQ 规则候选建议。
     *
     * <p>纯函数：消费 {@link #profileAsset} 的只读剖析产物做确定性建议
     * （空值率/唯一性/新鲜度阈值提示），<b>不新建规则、不写 dq_rule</b>；
     * 返回的建议 JSON 可直接作为 {@code createCandidate(candidateType=DQ_RULE)}
     * 的 proposal 入参（候选态 → 走 §7.4 状态机与审批，不直接生效）。
     *
     * @param assetRef 资产引用
     * @return 规则建议清单（dimension / suggestedMetric / thresholdHint / 误报风险标注）
     */
    public Map<String, Object> suggestDqRules(String assetRef) {
        Map<String, Object> profile = profileAsset(assetRef);
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("assetRef", assetRef.trim());
        out.put("readOnly", Boolean.TRUE);
        out.put("suggestionOnly", Boolean.TRUE);
        java.util.List<Map<String, Object>> suggestions = new java.util.ArrayList<>();
        if (Boolean.TRUE.equals(profile.get("found"))) {
            Object recordCount = profile.get("recordCount");
            long rows = recordCount instanceof Number n ? n.longValue() : -1L;
            suggestions.add(Map.of(
                    "dimension", "COMPLETENESS", "suggestedMetric", "NOT_NULL 空值率",
                    "thresholdHint", ">= 0.95（按资产满意度基线建议）",
                    "falsePositiveRisk", "低（空值率统计客观）"));
            suggestions.add(Map.of(
                    "dimension", "UNIQUENESS", "suggestedMetric", "关键键去重率",
                    "thresholdHint", "= 1.0（需先确认主键/唯一键语义）",
                    "falsePositiveRisk", "中（未确认业务唯一键时误报高，建议人工确认）"));
            suggestions.add(Map.of(
                    "dimension", "FRESHNESS", "suggestedMetric", "last_sync_time 时效",
                    "thresholdHint", rows >= 0 ? "结合 lastSyncTime 与业务节奏设定"
                            : "资产未登记行数，不可仅靠本剖析断言时效",
                    "falsePositiveRisk", "中（reltuples 未 ANALYZE 时 est=-1，不得当真实行数）"));
            if (rows < 0) {
                out.put("warning", "record_count 缺失（未登记或 PG 未 ANALYZE），FRESHNESS 建议不生成阈值");
            }
        } else {
            out.put("warning", "资产未登记或剖析不可得：仅给出通用 DQ 维度建议，阈值须业务侧确认");
            suggestions.add(Map.of(
                    "dimension", "VALIDITY", "suggestedMetric", "枚举/格式校验",
                    "thresholdHint", "依字典/本体引用设定",
                    "falsePositiveRisk", "中（缺字典时建议按候选态推进，不直接生效）"));
        }
        out.put("suggestions", suggestions);
        out.put("nextStep", "建议仅作候选：POST /api/v1/data/governance/candidates 建 DQ_RULE 候选后再走审批");
        return out;
    }

    // ═══════════════ DQ_PREVIEW（只读） ═══════════════

    /**
     * DQ 规则候选只读预览（SUP-DATA-01：预览不改变正式资产/版本号/水位线）。
     *
     * <p>基于已登记的资产元数据（{@code td_data_resource.record_count}）做<b>纯只读</b>
     * 采样率评估；仅把通过率回写候选行自身（preview_pass_rate），不触 dq_rule / 任何
     * 正式配置表。候选必须已 EVIDENCE_CHECKED 或之后（未过证据校验的候选不得预览）。
     *
     * @param candidateId 候选 ID
     * @return 预览产物（passRate + basis 引用；不含样本明文）
     */
    public Map<String, Object> previewDqRule(String candidateId) {
        if (isBlank(candidateId)) {
            throw new com.chinacreator.gzcm.common.exception.NotFoundException("候选（id 为空）");
        }
        Map<String, Object> current = dao.findByCandidateId(candidateId.trim());
        if (current == null) {
            throw new com.chinacreator.gzcm.common.exception.NotFoundException("候选 " + candidateId);
        }
        String status = String.valueOf(current.get("status"));
        if (!List.of(STATUS_EVIDENCE_CHECKED, STATUS_VALIDATED, STATUS_PENDING_APPROVAL,
                STATUS_APPROVED, STATUS_APPLIED, STATUS_NEEDS_EVIDENCE, STATUS_CONFLICTED)
                .contains(status)) {
            throw new IllegalArgumentException(
                    "候选须先过证据校验（EVIDENCE_CHECKED）方可只读预览，当前 " + status);
        }
        // 只读：仅读候选自身 + 资产登记行；pass rate 无既有实测时以「证据齐备度」作保守基线，
        // 不伪造样本统计（DQ 实测通过率由 dq_rule_check 生产执行侧回写，本端点不代跑检查）。
        Object evidenceRefsObj = current.get("evidenceRefsText");
        String evidenceRefsText = evidenceRefsObj instanceof String ? (String) evidenceRefsObj : null;
        int evidenceCount = countJsonArray(evidenceRefsText);
        double passRate = evidenceCount > 0 ? Math.min(1.0, evidenceCount >= 3 ? 0.95 : 0.80) : 0.0;
        dao.updatePreviewPassRate(candidateId.trim(), passRate); // 只写本候选行（SUP-DATA-01 合规）
        java.util.Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("candidateId", candidateId.trim());
        out.put("candidateType", current.get("candidateType"));
        out.put("passRate", passRate);
        out.put("basis", "evidence-refs=" + evidenceCount + " (只读评估; 实测通过率以 dq_rule_check 为准)");
        out.put("readOnly", Boolean.TRUE);
        return out;
    }

    // ═══════════════ DATA_CHANGE_APPLY（需审批 + security 裁决） ═══════════════

    /**
     * 应用已批准的数据变更提案（APPROVED → APPLIED，SECOND 步写动作）。
     *
     * <p>强制门槛（任一不满足即 DENY/拒绝）：
     * <ol>
     *   <li>候选必须为 APPROVED（§7.4：VALIDATED 不得当 APPROVED）；</li>
     *   <li>必须携带 approvalRef（审批记录）；</li>
     *   <li>security-engine ABAC {@code data.governance.apply} 裁决 ALLOW；
     *       裁决 DENY → 403，不可用（UNEVALUATED）→ 503（SUP-DATA-02 fail-closed 不放行，
     *       铁律 §2.4；两态分面见 §4.7.2）。</li>
     * </ol>
     * 本方法只标记候选 APPLIED（应用回执 + 版本引用）；<b>正式变更执行由 data-engine
     * 既有写端点（DQ rules 审批链 / 数据源变更审批）承载</b>，本类不直接改正式资产。
     *
     * @throws SecurityDenyException security 裁决 DENY（Controller 转 403）
     * @throws DataBridgeException   security-engine 不可用（UNEVALUATED，503／ECOS-SEC-501，非"无权限"）
     */
    public Map<String, Object> applyApprovedProposal(String candidateId) {
        if (isBlank(candidateId)) {
            throw new com.chinacreator.gzcm.common.exception.NotFoundException("候选（id 为空）");
        }
        Map<String, Object> current = dao.findByCandidateId(candidateId.trim());
        if (current == null) {
            throw new com.chinacreator.gzcm.common.exception.NotFoundException("候选 " + candidateId);
        }
        String status = String.valueOf(current.get("status"));
        if (!STATUS_APPROVED.equals(status)) {
            throw new IllegalStateException("候选须为 APPROVED 方可应用（§7.4），当前 " + status
                    + "（GENERATED/VALIDATED 不得当 APPROVED）");
        }
        String approvalRef = current.get("approvalRef") == null
                ? null : String.valueOf(current.get("approvalRef"));
        if (isBlank(approvalRef)) {
            throw new IllegalStateException("候选缺少 approvalRef（审批记录引用），禁应用");
        }

        // security-engine 裁决咽喉（§7.5 写操作必须过统一裁决；不可用与 DENY 分态，D-B4-44）
        PipelineSecurityService.AllowedResult verdict =
                security.evaluateDataGovernanceApply(candidateId.trim());
        switch (verdict.verdict()) {
            case ALLOW -> {
            }
            case DENY -> {
                publishCandidateEvent(EventTypes.Agent.POLICY_DENIED, candidateId.trim(),
                        String.valueOf(current.getOrDefault("tenantId", "")),
                        String.valueOf(current.getOrDefault("traceId", "")),
                        resolveActor(null), current.get("sourceRunId") == null
                                ? null : String.valueOf(current.get("sourceRunId")));
                throw new SecurityDenyException(
                        "data.governance.apply 裁决 DENY（reason=" + verdict.reason() + "）");
            }
            case UNEVALUATED -> throw abacUnavailable("data.governance.apply");
        }

        int affected = dao.transitionStatus(candidateId.trim(), STATUS_APPROVED, STATUS_APPLIED,
                approvalRef, resolveActor(null));
        if (affected == 0) {
            throw new IllegalStateException("OPTIMISTIC_CONFLICT: 候选 " + candidateId
                    + " 状态已漂移，apply 失败");
        }
        return dao.findByCandidateId(candidateId.trim());
    }

    // ═══════════════ 内部：状态机 / 置信度 / 事件 / 主体 ═══════════════

    /** 非法状态迁移拒绝（含 GENERATED/VALIDATED→APPROVED 直跳）。 */
    static void validateTransition(String from, String to) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("状态为空");
        }
        if (from.equals(to)) {
            return;
        }
        List<String> allowed = ALLOWED_TRANSITIONS.get(from);
        if (allowed == null || !allowed.contains(to)) {
            throw new IllegalArgumentException("非法候选状态迁移 " + from + " → " + to
                    + "（§7.4 状态机；GENERATED/VALIDATED 不得当 APPROVED）");
        }
    }

    /** 置信度解析：LLM 文本优先（sourceLlm=true），否则数值 + 声明 kind（§7.2）。 */
    static ResolvedConfidence resolveConfidence(DataGovernanceCandidateCreateDTO dto) {
        if (!isBlank(dto.getConfidenceLlmText())) {
            return GovernanceConfidenceResolver.fromLlmText(dto.getConfidenceLlmText());
        }
        Double value = dto.getConfidenceValue() == null ? null : dto.getConfidenceValue().doubleValue();
        return GovernanceConfidenceResolver.resolve(
                value, dto.getConfidenceKind(), dto.getCalibrated(),
                /* sourceLlm */ dto.getConfidenceValue() != null
                        && "LLM".equalsIgnoreCase(trimToNull(dto.getConfidenceKind())));
    }

    /**
     * UNEVALUATED 的落码出口（503 + 机读码取 {@link RlsExecutionContract} 单源常量，禁本类字面量）。
     *
     * <p>治理写操作在 §7.5 下必须 fail-closed，但 §4.7.2 要求"护栏没说话"与"策略说这人不行"
     * 分态，故本方法造的异常与 {@link SecurityDenyException}（403）在 httpStatus／码面／文案
     * 三处均不同。数据域无模块局部 advice，经网关 {@code handleDataBridge} 以 {@code ApiResponse.code}
     * 承载 httpStatus ⇒「HTTP 面仍 200」属既有 shortfall（D-B4-52），已登记台账。</p>
     */
    private static DataBridgeException abacUnavailable(String action) {
        return new DataBridgeException(RlsExecutionContract.SEC_UNAVAILABLE_HTTP,
                RlsExecutionContract.SEC_UNAVAILABLE_HTTP,
                RlsExecutionContract.SEC_UNAVAILABLE_CODE + ": " + action
                        + " 未获安全裁决（security-engine 不可用），按 fail-closed 拒绝，非「用户无权限」"
                        + "(§7.5 / §4.7.2 / 铁律 §2.4)");
    }

    private void publishCandidateEvent(String eventType, String candidateId, String tenantId,
                                        String traceId, String actor, String sourceRunId) {
        EventBusService bus = eventBusProvider.getIfAvailable();
        if (bus == null) {
            // runtime-event 未装配：降级不阻塞主流程（与既有 SecurityEngineClient.audit 同纪律）
            return;
        }
        try {
            java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
            payload.put("eventType", eventType);
            payload.put("domain", "data");
            payload.put("candidateId", candidateId);
            payload.put("tenantId", tenantId);
            payload.put("actor", actor);
            payload.put("traceId", traceId);
            if (sourceRunId != null) {
                payload.put("sourceRunId", sourceRunId);
            }
            bus.publish(KafkaTopics.topicForAggregate("agent"), payload);
        } catch (Exception e) {
            // 事件失败不阻塞候选主流程（降级留痕）
        }
    }

    private static String statusChangedEventType(String to) {
        if (STATUS_APPROVED.equals(to)) {
            return EventTypes.Agent.CANDIDATE_APPROVED;
        }
        if (STATUS_REJECTED.equals(to)) {
            return EventTypes.Agent.CANDIDATE_REJECTED;
        }
        if (STATUS_VALIDATED.equals(to)) {
            return EventTypes.Agent.CANDIDATE_VALIDATED;
        }
        return EventTypes.Agent.CANDIDATE_GENERATED;
    }

    private static String toJson(Object obj, String nullDefault) {
        if (obj == null) {
            return nullDefault;
        }
        if (obj instanceof String s) {
            return s;
        }
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return nullDefault;
        }
    }

    private static Integer toSmallest(Boolean b) {
        if (b == null) {
            return null; // NULL = 未声明（V272 calibrated 口径）
        }
        return b ? 1 : 0;
    }

    private static Double normalizeRate(Double r) {
        if (r == null) {
            return null;
        }
        if (r < 0) return 0.0;
        if (r > 1) return 1.0;
        return r;
    }

    private static int countJsonArray(String json) {
        if (isBlank(json)) {
            return 0;
        }
        try {
            Object parsed = MAPPER.readValue(json, Object.class);
            if (parsed instanceof java.util.List<?> l) {
                return l.size();
            }
        } catch (Exception ignored) {
            // 解析失败按 0
        }
        return 0;
    }

    private static String resolveTenantId(String requested) {
        if (!isBlank(requested)) {
            return requested.trim();
        }
        try {
            UserContext ctx = UserContext.getCurrent();
            if (ctx != null && !isBlank(ctx.getTenantId())) {
                return ctx.getTenantId();
            }
        } catch (Exception ignored) {
            // 无登录上下文 → default
        }
        return "default";
    }

    private static String resolveActor(String requested) {
        if (!isBlank(requested)) {
            return requested.trim();
        }
        try {
            String u = UserContext.getCurrentUsername();
            if (!isBlank(u)) {
                return u;
            }
        } catch (Exception ignored) {
            // 无登录上下文 → system
        }
        return "system";
    }

    private static String resolveTraceId(String requested) {
        if (!isBlank(requested)) {
            return requested.trim();
        }
        String mdc = org.slf4j.MDC.get("traceId");
        if (!isBlank(mdc)) {
            return mdc;
        }
        return java.util.UUID.randomUUID().toString();
    }

    private static String upperBlank(String s) {
        return s == null ? null : s.trim().toUpperCase(java.util.Locale.ROOT);
    }

    private static String trimToNull(String s) {
        return s == null ? null : s.trim();
    }

    /** null 安全显示值（展示只读建议用：null → 空串，避免给外部空引用）。 */
    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
