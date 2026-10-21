package com.chinacreator.gzcm.engine.ontology.governance;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import com.chinacreator.gzcm.common.event.EventTypes;
import com.chinacreator.gzcm.common.event.KafkaTopics;
import com.chinacreator.gzcm.common.exception.DataBridgeException;
import com.chinacreator.gzcm.common.security.abac.AbacEvaluateResponseContract;
import com.chinacreator.gzcm.common.security.rls.RlsExecutionContract;
import com.chinacreator.gzcm.engine.ontology.governance.dao.OntologyCandidateDao;
import com.chinacreator.gzcm.engine.ontology.governance.dto.OntologyCandidateCreateDTO;
import com.chinacreator.gzcm.engine.ontology.security.SecurityEngineClient;
import com.chinacreator.gzcm.engine.ontology.service.OntologyProposalService;
import com.chinacreator.gzcm.runtime.eventbus.EventBusService;
import com.chinacreator.gzcm.sysman.iam.context.UserContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * V273 本体 AI 候选 Service  --  《ECOS 认知域与智能域补充详细设计 v1.0》§7.3/7.4/7.5。
 *
 * <h3>候选 != 正式资产(§7.3)</h3>
 * 本服务只创建/推进候选状态;ONTOLOGY_PROPOSAL_CREATE 桥接产出的是 proposals 表的
 * 逻辑引用(proposal_ref,ST09 禁跨表 FK),为候选进入正式本体变更提案流的
 * 唯一出口(SUP-ONTO-02:AI 禁直发,无任何直接 publish 端点)。正式本体
 * 资产(entity/property/relationship 表)一律经既有提案审批链执行,本类不落任何
 * 正式资产写入;DIFF_PREVIEW 快照仅写候选行自身 diff_preview_text 列。
 *
 * <h3>状态机(§7.4,与 data-engine V272 同形态)</h3>
 * GENERATED - EVIDENCE_CHECKED - VALIDATED - PENDING_APPROVAL - APPROVED - APPLIED
 * + 分支 REJECTED / NEEDS_EVIDENCE / CONFLICTED / EXPIRED / ROLLED_BACK。
 * GENERATED/VALIDATED 不得当 APPROVED:状态迁移用 CAS(where status=fromStatus),
 * APPROVED 只允许 PENDING_APPROVAL 迁入且必须携带 approval_ref 且先过
 * security-engine ABAC(ontology.candidate.approve,fail-closed DENY);
 * 生成(create)与审批(approve)是两个方法,两个审计动作(§7.4 末段)。
 *
 * <h3>幂等(§7.5)</h3>
 * 同 idempotencyKey 重试:先按 (tenant_id, idempotency_key) 回读既有候选直接返回;
 * 并发下 DB 唯一索引 uk_v273_onto_idem 兜底,应用层捕获 DuplicateKeyException
 * 后回读同 key 记录(不重复创建)。
 *
 * <h3>写操作裁决(§7.5 / 架构铁律 §2.4)</h3>
 * APPROVE 与 APPLY 先过 security-engine ABAC;裁决 false / security 不可用 =
 * fail-closed ForbiddenException(403)并发布 POLICY_DENIED 事件。
 *
 * <h3>置信度(§7.5 置信度规则,V273 kind 词表单源本类)</h3>
 * MODEL_ESTIMATE / UNCALIBRATED_MODEL_ESTIMATE / STATISTICAL。value 收敛 0..1;
 * LLM 自报(confidenceLlmText 非空或 sourceLlm=true)强制
 * UNCALIBRATED_MODEL_ESTIMATE + calibrated=false;声明 calibrated=true 但
 * 无 calibrationGateRef 强制 calibrated=false。
 *
 * <h3>事件</h3>
 * 生成/校验/审批/拒绝经 runtime-event EventBusService 发布 EventTypes.Agent
 * 族事件(aggregate "agent" 归 KafkaTopics.AGENT);runtime-event 未装配时
 * warn 降级不阻塞主流程。
 */
@Service
public class OntologyCandidateService {

    // ── §7.4 状态词表(与 V273 列注释一致,单源本类)──
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

    // ── V273 候选类型词表(单源本类)──
    public static final String TYPE_ENTITY = "ENTITY";
    public static final String TYPE_PROPERTY = "PROPERTY";
    public static final String TYPE_RELATION = "RELATION";
    public static final String TYPE_CONSTRAINT = "CONSTRAINT";
    private static final List<String> CANDIDATE_TYPES =
            List.of(TYPE_ENTITY, TYPE_PROPERTY, TYPE_RELATION, TYPE_CONSTRAINT);

    // ── V273 置信度 kind 词表(单源本类)──
    public static final String CONF_MODEL_ESTIMATE = "MODEL_ESTIMATE";
    public static final String CONF_UNCALIBRATED_MODEL_ESTIMATE = "UNCALIBRATED_MODEL_ESTIMATE";
    public static final String CONF_STATISTICAL = "STATISTICAL";

    /** §7.5 evidenceRequired:三类结构候选必须携带非空证据。 */
    private static final List<String> EVIDENCE_REQUIRED_TYPES =
            List.of(TYPE_ENTITY, TYPE_PROPERTY, TYPE_RELATION);

    /** §7.4 状态迁移白名单(非法迁移在 Service 层即拒,不触 DB)。 */
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

    /** 命名规范:ASCII 标识符字符集(validate 自检项 a)。 */
    static final Pattern IDENTIFIER_PATTERN = Pattern.compile("[A-Za-z][A-Za-z0-9_]*");

    /** ABAC 动作常量(security-engine policy-engine)。 */
    private static final String ACTION_APPROVE = "ontology.candidate.approve";
    private static final String ACTION_APPLY = "ontology.candidate.apply";

    /** 桥接提案类型(proposals 表 proposal_type 列口径)。 */
    private static final String PROPOSAL_TYPE_ONTOLOGY_CANDIDATE = "ONTOLOGY_CANDIDATE";

    /** 桥接提案初始状态。 */
    private static final String PROPOSAL_STATUS_DRAFT = "DRAFT";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final OntologyCandidateDao dao;
    private final OntologyProposalService proposalService;
    private final ObjectProvider<SecurityEngineClient> securityProvider;
    private final ObjectProvider<EventBusService> eventBusProvider;

    public OntologyCandidateService(OntologyCandidateDao dao,
                                    OntologyProposalService proposalService,
                                    ObjectProvider<SecurityEngineClient> securityProvider,
                                    ObjectProvider<EventBusService> eventBusProvider) {
        this.dao = dao;
        this.proposalService = proposalService;
        this.securityProvider = securityProvider;
        this.eventBusProvider = eventBusProvider;
    }

    // ═══════════════ 候选创建(生成) ═══════════════

    /**
     * 创建本体候选(§7.3 统一候选生成入口;ENTITY/PROPERTY/RELATION/CONSTRAINT)。
     *
     * <p>校验:candidateType 必须在词表内,targetRef 必填;ENTITY/PROPERTY/RELATION
     * 证据强制(§7.5 evidenceRequired,缺失抛 IllegalArgumentException 转 400)。
     * 幂等:同 (tenant, idempotencyKey) 先回读返回既有候选,并发命中唯一索引
     * 回读兜底。创建后审计留痕并发布 CANDIDATE_GENERATED。</p>
     *
     * @param dto 候选入参
     * @return 落库行的 key-value(含候选 id)
     */
    public Map<String, Object> createCandidate(OntologyCandidateCreateDTO dto) {
        String candidateType = upperBlank(dto.getCandidateType());
        if (candidateType == null || !CANDIDATE_TYPES.contains(candidateType)) {
            throw new IllegalArgumentException("candidateType 非法: " + dto.getCandidateType()
                    + "(词表 " + CANDIDATE_TYPES + ")");
        }
        if (isBlank(dto.getTargetRef())) {
            throw new IllegalArgumentException("targetRef 必填(实体 code / 实体.属性 形态)");
        }
        // §7.5 evidenceRequired:三类结构候选必须随选随证
        if (EVIDENCE_REQUIRED_TYPES.contains(candidateType)
                && (dto.getEvidenceRefs() == null || dto.getEvidenceRefs().isEmpty())) {
            throw new IllegalArgumentException(
                    candidateType + " 候选必须携带非空 evidenceRefs(§7.5 evidenceRequired)");
        }

        String tenantId = resolveTenantId(dto.getTenantId());
        String creator = resolveActor(dto.getCreatedBy());
        String traceId = resolveTraceId(dto.getTraceId());

        ResolvedConfidence confidence = resolveConfidence(dto);

        // 幂等第一步:同 (tenant, idempotencyKey) 已有候选 → 直接返回(§7.5 重试不重复)
        if (!isBlank(dto.getIdempotencyKey())) {
            Map<String, Object> existing =
                    dao.findByIdempotencyKey(tenantId, dto.getIdempotencyKey().trim());
            if (existing != null) {
                return existing;
            }
        }

        String id = UUID.randomUUID().toString();
        try {
            dao.insert(id, tenantId, dto.getOrgId(), candidateType,
                    dto.getSubjectType(), dto.getPredicate(), dto.getObjectType(),
                    dto.getTargetRef().trim(),
                    toJson(dto.getProposal(), "{}"),
                    toJson(dto.getEvidenceRefs(), null),
                    confidence.value(), confidence.kind(), toSmallest(confidence.calibrated()),
                    toJson(dto.getConflicts(), null),
                    toJson(dto.getCompatibility(), null),
                    null,
                    null,
                    STATUS_GENERATED,
                    null,
                    dto.getToolContractRef(),
                    null,
                    dto.getSourceRunId(),
                    traceId,
                    isBlank(dto.getIdempotencyKey()) ? null : dto.getIdempotencyKey().trim(),
                    isBlank(dto.getDomain()) ? "default" : dto.getDomain().trim(),
                    isBlank(dto.getVersionNo()) ? "v1" : dto.getVersionNo().trim(),
                    creator);
        } catch (DuplicateKeyException dup) {
            // 幂等第二步(并发兜底):uk_v273_onto_idem 命中 → 回读同 key 记录幂等返回
            if (isBlank(dto.getIdempotencyKey())) {
                throw dup;
            }
            Map<String, Object> existing =
                    dao.findByIdempotencyKey(tenantId, dto.getIdempotencyKey().trim());
            if (existing != null) {
                return existing;
            }
            throw dup;
        }

        Map<String, Object> row = dao.findByCandidateId(id);
        audit("ontology.candidate.create",
                "candidateId=" + id + " type=" + candidateType);
        publishCandidateEvent(EventTypes.Agent.CANDIDATE_GENERATED, id, tenantId,
                traceId, creator, dto.getSourceRunId());
        return row;
    }

    // ═══════════════ 查询 ═══════════════

    /**
     * 候选详情(含 basis:confidence 三元组 + 证据计数 + 冲突标记 + 状态)。
     * 不存在返回 null(Controller 转 404)。
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
        return row;
    }

    /**
     * 详情 basis(证据基线摘要):confidence 规范化三元组 + 证据引用计数 +
     * 冲突标记 + 状态机所在段。只读计算,不落库。
     */
    static Map<String, Object> buildBasis(Map<String, Object> row) {
        Map<String, Object> basis = new LinkedHashMap<>();
        basis.put("confidenceValue", row.get("confidenceValue"));
        basis.put("confidenceKind", row.get("confidenceKind"));
        basis.put("calibrated", row.get("calibrated"));
        basis.put("evidenceRefCount", countJsonArray(asString(row.get("evidenceRefsText"))));
        basis.put("hasConflicts", countJsonArray(asString(row.get("conflictsText"))) > 0);
        basis.put("status", row.get("status"));
        basis.put("proposalRef", row.get("proposalRef"));
        basis.put("approvalRef", row.get("approvalRef"));
        return basis;
    }

    /**
     * 候选列表(tenantId 必填;status / candidateType / batchId 可选过滤)。
     *
     * @param tenantId      租户(必填,空白 → 400)
     * @param status        状态过滤(可空)
     * @param candidateType 候选类型过滤(可空)
     * @param batchId       批次过滤 = source_run_id(一次 W Agent Run 产出的候选批次,可空)
     */
    public List<Map<String, Object>> listCandidates(String tenantId, String status,
                                                    String candidateType, String batchId) {
        if (isBlank(tenantId)) {
            throw new IllegalArgumentException("tenantId 必填(候选列表按租户隔离)");
        }
        return dao.list(tenantId.trim(),
                isBlank(status) ? null : status.trim().toUpperCase(Locale.ROOT),
                isBlank(candidateType) ? null : candidateType.trim().toUpperCase(Locale.ROOT),
                isBlank(batchId) ? null : batchId.trim());
    }
    // ═══════════════ ONTOLOGY_GAP_ANALYZE(只读) ═══════════════

    /**
     * ONTOLOGY_GAP_ANALYZE(§7.3,只读):请求方实体类型 vs 全量在线实体 code 比对。
     *
     * <p>纯只读:不写候选表,不触任何正式资产。输入 JSON 体:
     * {@code targetEntityTypes[]}(必填)+ 可选 {@code relationshipIntents[]}
     * ({subject, predicate, object})。缺失实体 code 进 missingEntities;
     * 关系意图端点缺失进 missingRelationships;谓词与既有关系 code 前缀重叠进
     * overlappingRelationships。DAO 读源 DataAccessException 原样上抛(真 5xx,
     * §13.1 状态码真值化,禁伪空)。
     *
     * @param body 请求体(JSON 对象)
     * @return 差异分析产物(readOnly=true)
     */
    public Map<String, Object> gapAnalyze(Object body) {
        Map<String, Object> req = toObjectMap(body);
        List<String> requestedTypes = asStringList(req.get("targetEntityTypes"));
        if (requestedTypes.isEmpty()) {
            throw new IllegalArgumentException(
                    "targetEntityTypes[] 必填且非空(ONTOLOGY_GAP_ANALYZE 比对基准)");
        }
        List<String> existingCodes = dao.listEntityCodes();
        java.util.Set<String> existingSet = new java.util.HashSet<>();
        for (String c : existingCodes) {
            existingSet.add(c.toUpperCase(Locale.ROOT));
        }

        List<String> missingEntities = new ArrayList<>();
        for (String t : requestedTypes) {
            if (!existingSet.contains(t.trim().toUpperCase(Locale.ROOT))) {
                missingEntities.add(t.trim());
            }
        }

        List<Map<String, Object>> missingRelationships = new ArrayList<>();
        List<Map<String, Object>> overlappingRelationships = new ArrayList<>();
        Object rawIntents = req.get("relationshipIntents");
        List<?> intents = rawIntents instanceof List<?> l ? l : List.of();
        for (Object o : intents) {
            if (!(o instanceof Map<?, ?> m)) {
                continue;
            }
            Map<String, Object> intent = new LinkedHashMap<>();
            String subject = asString(m.get("subject"));
            String predicate = asString(m.get("predicate"));
            String object = asString(m.get("object"));
            intent.put("subject", subject);
            intent.put("predicate", predicate);
            intent.put("object", object);
            boolean subjectOk = !isBlank(subject) && dao.findEntityByCode(subject.trim()) != null;
            boolean objectOk = !isBlank(object) && dao.findEntityByCode(object.trim()) != null;
            if (!subjectOk || !objectOk) {
                List<String> missing = new ArrayList<>();
                if (!subjectOk) {
                    missing.add(nullToEmpty(subject));
                }
                if (!objectOk) {
                    missing.add(nullToEmpty(object));
                }
                intent.put("missingEndpoints", missing);
                missingRelationships.add(intent);
                continue;
            }
            if (!isBlank(predicate)) {
                List<Map<String, Object>> collisions =
                        dao.findRelationshipCodeCollisions(predicate.trim(), 20);
                if (!collisions.isEmpty()) {
                    List<String> collidingCodes = new ArrayList<>();
                    for (Map<String, Object> c : collisions) {
                        if (c.get("code") != null) {
                            collidingCodes.add(String.valueOf(c.get("code")));
                        }
                    }
                    intent.put("collisionCount", collisions.size());
                    intent.put("collidingRelationshipCodes", collidingCodes);
                    overlappingRelationships.add(intent);
                }
            }
        }

        Map<String, Object> inventory = new LinkedHashMap<>();
        inventory.put("entityCount", existingCodes.size());
        inventory.put("types", requestedTypes);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("readOnly", Boolean.TRUE);
        out.put("candidateInventory", inventory);
        out.put("missingEntities", missingEntities);
        out.put("missingRelationships", missingRelationships);
        out.put("overlappingRelationships", overlappingRelationships);
        out.put("note", missingEntities.isEmpty() && missingRelationships.isEmpty()
                ? "请求类型与在线本体无缺口(谓词重叠见 overlappingRelationships)"
                : "存在缺口:缺失实体须先建 ENTITY/RELATION 候选走 §7.4 审批链");
        return out;
    }

    // ═══════════════ ONTOLOGY_CANDIDATE_VALIDATE ═══════════════

    /**
     * ONTOLOGY_CANDIDATE_VALIDATE(§7.3):候选自检,纯自校验 + 只读源探测;
     * 只回写候选行自身 validation_text 并 CAS 推进状态,不触任何正式资产。
     *
     * <p>四段检查:
     * <ol>
     *   <li>命名:subjectType / predicate / objectType 或 targetRef 非空且满足
     *       ASCII 标识符字符集(违规逐条列出);</li>
     *   <li>端点:RELATION 候选 subjectType / objectType 实体须存在;</li>
     *   <li>冲突:predicate 与既有关系 code 前缀重叠;</li>
     *   <li>兼容:compatibility_text 存在时须含 breakingChange 声明(否则 unknown)。</li>
     * </ol>
     *
     * <p>状态推进(仅 GENERATED / EVIDENCE_CHECKED / NEEDS_EVIDENCE 可发起):
     * passed=true → VALIDATED;端点缺失或谓词冲突(RELATION)→ CONFLICTED;
     * 其余问题 → NEEDS_EVIDENCE。CAS 未命中 → IllegalStateException(409 语义)。</p>
     *
     * @param candidateId 候选 ID
     * @return 校验产物(passed / problems / checks / status + 最新候选行)
     */
    public Map<String, Object> validate(String candidateId) {
        Map<String, Object> current = requireCandidate(candidateId);
        String from = String.valueOf(current.get("status"));
        if (!List.of(STATUS_GENERATED, STATUS_EVIDENCE_CHECKED, STATUS_NEEDS_EVIDENCE)
                .contains(from)) {
            throw new IllegalArgumentException(
                    "仅 GENERATED/EVIDENCE_CHECKED/NEEDS_EVIDENCE 可发起自检,当前 " + from);
        }
        String type = asString(current.get("candidateType"));
        String subjectType = asString(current.get("subjectType"));
        String predicate = asString(current.get("predicate"));
        String objectType = asString(current.get("objectType"));
        String targetRef = asString(current.get("targetRef"));

        List<String> problems = new ArrayList<>();
        List<String> namingProblems = new ArrayList<>();

        // (a) 命名
        checkIdentifier(namingProblems, "subjectType", subjectType);
        checkIdentifier(namingProblems, "predicate", predicate);
        checkIdentifier(namingProblems, "objectType", objectType);
        // 兜底:subject/predicate/object 全空时 targetRef 必须合规(属性源常直接写 code)
        if (isBlank(subjectType) && isBlank(predicate) && isBlank(objectType)) {
            checkIdentifier(namingProblems, "targetRef", targetRef);
        }
        problems.addAll(namingProblems);

        // (b) 端点存在性(仅 RELATION;ENTITY/PROPERTY 端点即自身不查)
        boolean endpointsFailed = false;
        if (TYPE_RELATION.equals(type)) {
            if (isBlank(subjectType) || dao.findEntityByCode(subjectType.trim()) == null) {
                endpointsFailed = true;
                problems.add("RELATION 端点缺失: subjectType=" + nullToEmpty(subjectType));
            }
            if (isBlank(objectType) || dao.findEntityByCode(objectType.trim()) == null) {
                endpointsFailed = true;
                problems.add("RELATION 端点缺失: objectType=" + nullToEmpty(objectType));
            }
        }

        // (c) 谓词重叠
        boolean conflicts = false;
        if (TYPE_RELATION.equals(type) && !isBlank(predicate)) {
            List<Map<String, Object>> collisions =
                    dao.findRelationshipCodeCollisions(predicate.trim(), 20);
            if (!collisions.isEmpty()) {
                conflicts = true;
                List<String> codes = new ArrayList<>();
                for (Map<String, Object> c : collisions) {
                    if (c.get("code") != null) {
                        codes.add(String.valueOf(c.get("code")));
                    }
                }
                problems.add("谓词重叠: predicate=" + predicate.trim()
                        + " 与既有关系 code 前缀冲突 " + codes);
            }
        }

        // (d) 兼容声明
        boolean compatibilityUnknown = false;
        String compatibilityText = asString(current.get("compatibilityText"));
        if (!isBlank(compatibilityText)) {
            try {
                Object parsed = MAPPER.readValue(compatibilityText, Object.class);
                if (!(parsed instanceof Map<?, ?> pm) || !pm.containsKey("breakingChange")) {
                    compatibilityUnknown = true;
                    problems.add("兼容声明缺少 breakingChange 字段(compatibility unknown)");
                }
            } catch (JsonProcessingException ignored) {
                compatibilityUnknown = true;
            }
        }

        boolean passed = problems.isEmpty();
        Map<String, Object> checks = new LinkedHashMap<>();
        checks.put("naming", namingProblems.isEmpty());
        checks.put("endpoints", !endpointsFailed);
        checks.put("conflicts", !conflicts);
        checks.put("compatibility", !compatibilityUnknown);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("passed", passed);
        result.put("problems", problems);
        result.put("checks", checks);
        result.put("timestamp", System.currentTimeMillis());

        dao.updateValidationText(candidateId.trim(), toJson(result, "{}"));

        String toStatus;
        if (passed) {
            toStatus = STATUS_VALIDATED;
        } else if (endpointsFailed || (conflicts && TYPE_RELATION.equals(type))) {
            toStatus = STATUS_CONFLICTED;
        } else {
            toStatus = STATUS_NEEDS_EVIDENCE;
        }
        if (!from.equals(toStatus)) {
            validateTransition(from, toStatus);
            int affected = dao.transitionStatus(candidateId.trim(), from, toStatus, null,
                    resolveActor(null));
            if (affected == 0) {
                throw new IllegalStateException("OPTIMISTIC_CONFLICT: 候选 " + candidateId
                        + " 状态已由 " + from + " 漂移,自检推进 " + toStatus + " 失败");
            }
        }

        audit("ontology.candidate.validate",
                "candidateId=" + candidateId + " passed=" + passed + " to=" + toStatus);
        publishCandidateEvent(statusChangedEventType(toStatus), candidateId.trim(),
                asString(current.get("tenantId")), asString(current.get("traceId")),
                resolveActor(null), asString(current.get("sourceRunId")));

        Map<String, Object> out = new LinkedHashMap<>(result);
        out.put("status", toStatus);
        Map<String, Object> row = dao.findByCandidateId(candidateId.trim());
        if (row != null) {
            out.put("candidate", row);
        }
        return out;
    }
    // ═══════════════ ONTOLOGY_DIFF_PREVIEW(只读) ═══════════════

    /**
     * ONTOLOGY_DIFF_PREVIEW(§7.3,只读):候选 proposal vs 当前本体的
     * 结构差异快照(若应用,将有哪些 entity/property/relation 行被 ADD)。
     *
     * <p>纯只读:候选行自身 diff_preview_text 列允许回写(列写不等于资产写),
     * 正式 entity/property/relationship 表永不触碰;返回体 committed=false
     * 恒真(禁应用,SUP-ONTO-01/02)。RELATION 目标逐端点 findEntityByCode
     * 标注存在性;PROPERTY 目标按实体 listPropertiesByEntityId 计数现有属性
     * (影响面规模参照)。
     *
     * @param candidateId 候选 ID
     * @return 差异快照(readOnly=true,committed=false)
     */
    public Map<String, Object> diffPreview(String candidateId) {
        Map<String, Object> current = requireCandidate(candidateId);
        String type = asString(current.get("candidateType"));
        String targetRef = asString(current.get("targetRef"));

        List<Map<String, Object>> addedEntities = new ArrayList<>();
        List<Map<String, Object>> addedProperties = new ArrayList<>();
        List<Map<String, Object>> addedRelations = new ArrayList<>();
        List<String> affectedObjects = new ArrayList<>();

        if (TYPE_ENTITY.equals(type)) {
            if (!isBlank(targetRef)) {
                Map<String, Object> e = new LinkedHashMap<>();
                e.put("code", targetRef.trim());
                e.put("alreadyExists", dao.findEntityByCode(targetRef.trim()) != null);
                addedEntities.add(e);
                affectedObjects.add("entity:" + targetRef.trim());
            }
        } else if (TYPE_PROPERTY.equals(type)) {
            // 宿主实体:proposal 内 hostEntity 优先,缺省取 subjectType
            Map<String, Object> proposal = parseObject(asString(current.get("proposalText")));
            String host = firstNonBlank(
                    proposal == null ? null : asString(proposal.get("hostEntity")),
                    asString(current.get("subjectType")));
            if (isBlank(host)) {
                host = firstEntityCodeOf(targetRef);
            }
            if (!isBlank(host)) {
                String propCode = firstNonBlank(
                        proposal == null ? null : asString(proposal.get("code")), targetRef);
                Map<String, Object> prop = new LinkedHashMap<>();
                prop.put("entity", host.trim());
                prop.put("code", propCode);
                prop.put("currentPropertyCount",
                        dao.listPropertiesByEntityId(host.trim()).size());
                prop.put("endpointExists", dao.findEntityByCode(host.trim()) != null);
                addedProperties.add(prop);
                affectedObjects.add("property:" + host.trim() + "."
                        + nullToEmpty(propCode));
            } else if (!isBlank(targetRef)) {
                affectedObjects.add("property:" + targetRef.trim());
            }
        } else if (TYPE_RELATION.equals(type)) {
            String subjectType = asString(current.get("subjectType"));
            String objectType = asString(current.get("objectType"));
            Map<String, Object> rel = new LinkedHashMap<>();
            rel.put("subject", subjectType);
            rel.put("predicate", asString(current.get("predicate")));
            rel.put("object", objectType);
            rel.put("subjectExists",
                    !isBlank(subjectType) && dao.findEntityByCode(subjectType.trim()) != null);
            rel.put("objectExists",
                    !isBlank(objectType) && dao.findEntityByCode(objectType.trim()) != null);
            addedRelations.add(rel);
            if (!isBlank(subjectType)) {
                affectedObjects.add("relation-endpoint:" + subjectType.trim());
            }
            if (!isBlank(objectType)) {
                affectedObjects.add("relation-endpoint:" + objectType.trim());
            }
        }
        if (TYPE_CONSTRAINT.equals(type) && !isBlank(targetRef)) {
            affectedObjects.add("constraint:" + targetRef.trim());
        }

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("candidateType", type);
        snapshot.put("targetRef", targetRef);
        snapshot.put("addedEntities", addedEntities);
        snapshot.put("addedProperties", addedProperties);
        snapshot.put("addedRelations", addedRelations);
        snapshot.put("affectedObjects", affectedObjects);
        snapshot.put("committed", Boolean.FALSE);
        snapshot.put("timestamp", System.currentTimeMillis());

        dao.updateDiffPreviewText(candidateId.trim(), toJson(snapshot, "{}"));

        Map<String, Object> out = new LinkedHashMap<>(snapshot);
        out.put("readOnly", Boolean.TRUE);
        out.put("candidateId", candidateId.trim());
        return out;
    }

    // ═══════════════ ONTOLOGY_PROPOSAL_CREATE(唯一桥接出口) ═══════════════

    /**
     * ONTOLOGY_PROPOSAL_CREATE(§7.3):VALIDATED 候选 → 正式本体提案流(唯一出口)。
     *
     * <p>CAS 门槛:仅 VALIDATED 态可发起(其余状态拒)。桥接 7-arg
     * {@link OntologyProposalService#insertProposal}:domainCode=候选 domain,
     * proposalType=ONTOLOGY_CANDIDATE,targetEntity=targetRef,
     * payloadJson=proposal_text,snapshotJson=diff_preview_text(缺省 "{}"),
     * status=DRAFT,author=当前 actor。随后回填 proposal_ref(ST09 逻辑引用)
     * 并 CAS 迁 PENDING_APPROVAL,审计留痕。
     *
     * <p><b>SUP-ONTO-02:AI 禁直发</b>  --  本端点是候选进入正式提案流的唯一路径,
     * 候选侧不存在任何直接 publish / apply-to-ontology 端点;候选行自身被
     * APPROVED / APPLIED 仅表示候选被接纳与落地确认(见 {@link #applyCandidate}),
     * 正式资产变更仅由 proposals 审批链驱动。</p>
     *
     * @param candidateId 候选 ID
     * @return {proposalId, candidateId, status}
     */
    public Map<String, Object> submitProposal(String candidateId) {
        Map<String, Object> current = requireCandidate(candidateId);
        String from = String.valueOf(current.get("status"));
        if (!STATUS_VALIDATED.equals(from)) {
            throw new IllegalStateException(
                    "仅 VALIDATED 候选可提交提案(§7.4),当前 " + from
                            + "(须先经 /validate 自检通过)");
        }
        String domain = asString(current.get("domain"));
        String targetRef = asString(current.get("targetRef"));
        String payloadJson = asString(current.get("proposalText"));
        String snapshotJson = asString(current.get("diffPreviewText"));
        if (isBlank(snapshotJson)) {
            snapshotJson = "{}";
        }
        String author = resolveActor(null);

        String proposalId = proposalService.insertProposal(
                isBlank(domain) ? "default" : domain.trim(),
                PROPOSAL_TYPE_ONTOLOGY_CANDIDATE,
                isBlank(targetRef) ? null
                        : targetRef.trim(),
                isBlank(payloadJson) ? "{}" : payloadJson,
                snapshotJson,
                PROPOSAL_STATUS_DRAFT,
                author);

        dao.updateProposalRef(candidateId.trim(), proposalId);
        int affected = dao.transitionStatus(candidateId.trim(), STATUS_VALIDATED,
                STATUS_PENDING_APPROVAL, null, author);
        if (affected == 0) {
            // 提案已建但候选状态漂移:审计留痕后上抛(提案保留供人工处置,不误删)
            audit("ontology.candidate.submit-proposal.conflict",
                    "candidateId=" + candidateId + " proposalId=" + proposalId
                            + " from=" + from);
            throw new IllegalStateException("OPTIMISTIC_CONFLICT: 候选 " + candidateId
                    + " 状态已由 " + from + " 漂移,提交提案失败(提案 " + proposalId
                            + " 已创建,需人工处置)");
        }

        audit("ontology.candidate.submit-proposal",
                "candidateId=" + candidateId + " proposalId=" + proposalId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("candidateId", candidateId.trim());
        out.put("proposalId", proposalId);
        out.put("status", STATUS_PENDING_APPROVAL);
        out.put("candidate", dao.findByCandidateId(candidateId.trim()));
        return out;
    }

    // ═══════════════ 状态推进(§7.4,两步分离) ═══════════════

    /**
     * 审批通过(PENDING_APPROVAL → APPROVED,第二步动作;独立审计)。
     *
     * <p>§7.4 末段:生成(create)与审批(approve)是两个动作,两个权限判断,
     * 两个审计事件。写操作裁决(§7.5 / 铁律 §2.4):先过 security-engine ABAC
     * {@code ontology.candidate.approve};裁决 false / security 不可用 →
     * fail-closed ForbiddenException(403)并发布 POLICY_DENIED 事件。
     * APPROVED 必须挂靠审批记录(approvalRef 必填,应用侧门禁)。</p>
     *
     * @param candidateId 候选 ID
     * @param approvalRef 审批记录引用(必填)
     * @param approver    审批人(缺省取登录态)
     */
    public Map<String, Object> approve(String candidateId, String approvalRef,
                                       String approver) {
        if (isBlank(approvalRef)) {
            throw new IllegalArgumentException("approve 必须携带 approvalRef(审批记录引用必填)");
        }
        Map<String, Object> current = requireCandidate(candidateId);
        String from = String.valueOf(current.get("status"));
        if (!STATUS_PENDING_APPROVAL.equals(from)) {
            throw new IllegalStateException("仅 PENDING_APPROVAL 候选可审批(§7.4),当前 "
                    + from + "(GENERATED/VALIDATED 不得当 APPROVED)");
        }
        // security-engine 裁决咽喉(§7.5 写操作必须过统一裁决;FAILED → DENY)
        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("candidateId", candidateId.trim());
        attrs.put("candidateType", asString(current.get("candidateType")));
        attrs.put("tenantId", asString(current.get("tenantId")));
        attrs.put("domain", asString(current.get("domain")));
        AbacEvaluateResponseContract.Verdict verdict = evaluateSecurityPolicy(ACTION_APPROVE, attrs);
        if (verdict != AbacEvaluateResponseContract.Verdict.ALLOW) {
            // 分态出口（D-B4-44）：DENY 才是策略拒绝（发 POLICY_DENIED 事件 + 403）；
            // UNEVALUATED 是护栏没说话（不可用），不得冒充策略拒绝，也不得发策略拒绝事件。
            if (verdict == AbacEvaluateResponseContract.Verdict.UNEVALUATED) {
                audit("ontology.candidate.approve.unavailable", "candidateId=" + candidateId.trim());
                throw abacUnavailable(ACTION_APPROVE);
            }
            publishCandidateEvent(EventTypes.Agent.POLICY_DENIED, candidateId.trim(),
                    asString(current.get("tenantId")), asString(current.get("traceId")),
                    resolveActor(approver), asString(current.get("sourceRunId")));
            audit("ontology.candidate.approve.denied", "candidateId=" + candidateId.trim());
            throw new com.chinacreator.gzcm.common.exception.ForbiddenException(
                    "ontology.candidate.approve 裁决 DENY(§7.5 / 铁律 §2.4)");
        }
        int affected = dao.transitionStatus(candidateId.trim(), STATUS_PENDING_APPROVAL,
                STATUS_APPROVED, approvalRef.trim(), resolveActor(approver));
        if (affected == 0) {
            throw new IllegalStateException("OPTIMISTIC_CONFLICT: 候选 " + candidateId
                    + " 状态已由 " + from + " 漂移,审批失败");
        }
        audit("ontology.candidate.approve",
                "candidateId=" + candidateId.trim() + " approvalRef=" + approvalRef.trim());
        publishCandidateEvent(EventTypes.Agent.CANDIDATE_APPROVED, candidateId.trim(),
                asString(current.get("tenantId")), asString(current.get("traceId")),
                resolveActor(approver), asString(current.get("sourceRunId")));
        return dao.findByCandidateId(candidateId.trim());
    }

    /**
     * 证据核验:GENERATED → EVIDENCE_CHECKED(第一步动作)。
     */
    public Map<String, Object> markEvidenceChecked(String candidateId) {
        return transition(candidateId, STATUS_EVIDENCE_CHECKED, null,
                "evidence checked by " + resolveActor(null));
    }

    /**
     * 通用状态推进(CAS):reject / need-evidence / conflict / expire 等分支。
     *
     * @param candidateId 候选 ID
     * @param toStatus    目标状态(必须 ∈ ALLOWED_TRANSITIONS[from])
     * @param approvalRef 审批记录引用(迁 APPROVED 必填;其余忽略)
     * @param reason      迁移原因(留痕 javadoc;DB 无 reason 列)
     */
    public Map<String, Object> transition(String candidateId, String toStatus,
                                          String approvalRef, String reason) {
        if (isBlank(candidateId)) {
            throw new com.chinacreator.gzcm.common.exception.NotFoundException("候选(id 为空)");
        }
        Map<String, Object> current = requireCandidate(candidateId);
        String from = String.valueOf(current.get("status"));
        String to = upperBlank(toStatus);
        validateTransition(from, to);
        if (STATUS_APPROVED.equals(to) && isBlank(approvalRef)) {
            throw new IllegalArgumentException("迁 APPROVED 必须携带 approvalRef(生成/验证不得当审批)");
        }
        int affected = dao.transitionStatus(candidateId.trim(), from, to, approvalRef,
                resolveActor(null));
        if (affected == 0) {
            throw new IllegalStateException("OPTIMISTIC_CONFLICT: 候选 " + candidateId
                    + " 状态已由 " + from + " 漂移,迁移 " + to + " 失败");
        }
        audit("ontology.candidate.transition",
                "candidateId=" + candidateId.trim() + " from=" + from + " to=" + to
                        + " reason=" + nullToEmpty(reason));
        publishCandidateEvent(statusChangedEventType(to), candidateId.trim(),
                asString(current.get("tenantId")), asString(current.get("traceId")),
                resolveActor(null), asString(current.get("sourceRunId")));
        return dao.findByCandidateId(candidateId.trim());
    }

    /**
     * 应用已批准的候选(APPROVED → APPLIED,SECOND 步写动作)。
     *
     * <p><b>本方法只标记候选 APPLIED(应用回执)并审计,不执行任何本体变更</b>;
     * 正式本体资产变更严格经 proposals 审批链(submitProposal 桥接的提案
     * approve-and-publish 流程),候选 APPLIED 仅表示"该候选已被接纳并完成
     * 落地确认"。security-engine ABAC {@code ontology.candidate.apply}
     * 前置裁决;fail-closed DENY(§7.5)。</p>
     *
     * @param candidateId 候选 ID
     * @return 最新候选行
     */
    public Map<String, Object> applyCandidate(String candidateId) {
        Map<String, Object> current = requireCandidate(candidateId);
        String from = String.valueOf(current.get("status"));
        if (!STATUS_APPROVED.equals(from)) {
            throw new IllegalStateException("候选须为 APPROVED 方可应用(§7.4),当前 "
                    + from + "(GENERATED/VALIDATED 不得当 APPROVED)");
        }
        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("candidateId", candidateId.trim());
        attrs.put("candidateType", asString(current.get("candidateType")));
        attrs.put("tenantId", asString(current.get("tenantId")));
        attrs.put("domain", asString(current.get("domain")));
        AbacEvaluateResponseContract.Verdict verdict = evaluateSecurityPolicy(ACTION_APPLY, attrs);
        if (verdict != AbacEvaluateResponseContract.Verdict.ALLOW) {
            // 同 approve 腿：DENY→403，UNEVALUATED→503 + ECOS-SEC-501（D-B4-44 分态）
            if (verdict == AbacEvaluateResponseContract.Verdict.UNEVALUATED) {
                audit("ontology.candidate.apply.unavailable", "candidateId=" + candidateId.trim());
                throw abacUnavailable(ACTION_APPLY);
            }
            publishCandidateEvent(EventTypes.Agent.POLICY_DENIED, candidateId.trim(),
                    asString(current.get("tenantId")), asString(current.get("traceId")),
                    resolveActor(null), asString(current.get("sourceRunId")));
            audit("ontology.candidate.apply.denied", "candidateId=" + candidateId.trim());
            throw new com.chinacreator.gzcm.common.exception.ForbiddenException(
                    "ontology.candidate.apply 裁决 DENY(§7.5 / 铁律 §2.4)");
        }
        String approvalRef = asString(current.get("approvalRef"));
        int affected = dao.transitionStatus(candidateId.trim(), STATUS_APPROVED,
                STATUS_APPLIED, approvalRef, resolveActor(null));
        if (affected == 0) {
            throw new IllegalStateException("OPTIMISTIC_CONFLICT: 候选 " + candidateId
                    + " 状态已由 " + from + " 漂移,apply 失败");
        }
        audit("ontology.candidate.apply", "candidateId=" + candidateId.trim()
                + "(回执态;正式变更仅经 proposals 审批链执行)");
        return dao.findByCandidateId(candidateId.trim());
    }
    // ═══════════════ 内部:状态机 / 置信度 / 安全 / 事件 / 主体 ═══════════════

    /** 非法状态迁移拒绝(含 GENERATED/VALIDATED→APPROVED 直跳)。 */
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
                    + "(§7.4 状态机;GENERATED/VALIDATED 不得当 APPROVED)");
        }
    }

    /**
     * 置信度解析(§7.5 置信度规则;V273 kind 词表与 data-engine 同语义内联实现,
     * 跨引擎禁 import data 包故此处复刻)。
     *
     * <p>规则:value 收敛 0..1;LLM 自报(confidenceLlmText 非空或 sourceLlm=true)
     * 强制 UNCALIBRATED_MODEL_ESTIMATE + calibrated=false;STATISTICAL 声明原样保留;
     * 声明 calibrated=true 强制 false,除非同时提供 calibrationGateRef(门禁引用
     * 非空);kind 词外归一(别名 MODEL_ESTIMATE 已在词表内)。</p>
     */
    static ResolvedConfidence resolveConfidence(OntologyCandidateCreateDTO dto) {
        // 1) LLM 自报文本优先(解析百分比)
        if (!isBlank(dto.getConfidenceLlmText())) {
            Double value = extractPercent(dto.getConfidenceLlmText());
            return new ResolvedConfidence(value, CONF_UNCALIBRATED_MODEL_ESTIMATE,
                    value == null ? null : Boolean.FALSE);
        }
        Double raw = dto.getConfidenceValue() == null
                ? null : dto.getConfidenceValue().doubleValue();
        if (raw == null && !Boolean.TRUE.equals(dto.getSourceLlm())) {
            return new ResolvedConfidence(null, null, null);
        }
        double value = raw == null ? 0.0 : clamp01(raw);

        boolean llm = Boolean.TRUE.equals(dto.getSourceLlm());
        String declaredKind = upperBlank(dto.getConfidenceKind());
        boolean gate = !isBlank(dto.getCalibrationGateRef());

        // LLM 自报数值:强制未校准模型估计
        if (llm) {
            return new ResolvedConfidence(value, CONF_UNCALIBRATED_MODEL_ESTIMATE, Boolean.FALSE);
        }
        // 统计概率:原样(统计不是"校准模型",calibrated 不参与)
        if (CONF_STATISTICAL.equals(declaredKind)) {
            return new ResolvedConfidence(value, CONF_STATISTICAL, null);
        }
        // 声明已校准:必须有 calibration 门禁引用才认,否则强制 calibrated=false
        boolean calibrated = Boolean.TRUE.equals(dto.getCalibrated()) && gate;
        if (calibrated) {
            // 门禁通过 + 声明已校准 → MODEL_ESTIMATE(V273 词表,等价 data 侧 CALIBRATED_MODEL)
            return new ResolvedConfidence(value, CONF_MODEL_ESTIMATE, Boolean.TRUE);
        }
        // 其余模型侧数值:声明 calibrated 但无门禁 → 强制 false
        if (declaredKind == null || CONF_MODEL_ESTIMATE.equals(declaredKind)
                || CONF_UNCALIBRATED_MODEL_ESTIMATE.equals(declaredKind)) {
            return new ResolvedConfidence(value, CONF_UNCALIBRATED_MODEL_ESTIMATE,
                    Boolean.TRUE.equals(dto.getCalibrated()) ? Boolean.FALSE : null);
        }
        throw new IllegalArgumentException("confidence.kind 不在白名单: " + dto.getConfidenceKind()
                + "(V273 白名单 MODEL_ESTIMATE / UNCALIBRATED_MODEL_ESTIMATE / STATISTICAL)");
    }

    /** 规范化后的置信度(value + kind + calibrated 标记)。 */
    static final class ResolvedConfidence {
        private final Double value;
        private final String kind;
        private final Boolean calibrated;

        ResolvedConfidence(Double value, String kind, Boolean calibrated) {
            this.value = value;
            this.kind = kind;
            this.calibrated = calibrated;
        }

        Double value() {
            return value;
        }

        String kind() {
            return kind;
        }

        Boolean calibrated() {
            return calibrated;
        }
    }

    /**
     * security-engine ABAC 裁决 — 原样返回契约三态（fail-closed 但<b>不分态即违 §4.7.2</b>）。
     *
     * <p>#142/B4-3e-12（差距 D-B4-44）：收口前本方法返回 {@code boolean}，bean 缺失与
     * 客户端任何失败都折叠成 {@code false}，调用点再统一抛 403 —— 把"护栏没说话"
     * 报成"这人没权限"。现 bean 缺失／客户端异常一律 {@code UNEVALUATED}，
     * 与策略 {@code DENY} 在调用点分面（403 vs 503 + ECOS-SEC-501）。</p>
     */
    private AbacEvaluateResponseContract.Verdict evaluateSecurityPolicy(
            String action, Map<String, Object> attrs) {
        SecurityEngineClient security = securityProvider.getIfAvailable();
        if (security == null) {
            // 护栏未参与裁决（bean 缺失）＝不可用，不是策略拒绝
            return AbacEvaluateResponseContract.Verdict.UNEVALUATED;
        }
        try {
            return security.evaluatePolicy(action, attrs);
        } catch (Exception e) {
            return AbacEvaluateResponseContract.Verdict.UNEVALUATED;
        }
    }

    /**
     * UNEVALUATED 的落码出口（503 + 机读 {@code ECOS-SEC-501}，码面取
     * {@link RlsExecutionContract} 单源常量，禁本类字面量）。
     *
     * <p>本体域没有模块局部 advice，异常经网关 {@code handleDataBridge} 透出；不可用态与
     * 403 策略拒绝的状态串在本方法起就已分开，验收见 {@code OntologyCandidateServiceTest}。
     * <b>#142/B4-3e-15（D-B4-52 收口）</b>后该 handler 已按携带值落状态行，
     * 原「HTTP 面仍 200＝本体域缺局部 advice」的 shortfall 已闭合。</p>
     */
    private static DataBridgeException abacUnavailable(String action) {
        return new DataBridgeException(RlsExecutionContract.SEC_UNAVAILABLE_HTTP,
                RlsExecutionContract.SEC_UNAVAILABLE_HTTP,
                RlsExecutionContract.SEC_UNAVAILABLE_CODE + ": " + action
                        + " 未获安全裁决（security-engine 不可用），按 fail-closed 拒绝，非「用户无权限」"
                        + "(§7.5 / §4.7.2 / 铁律 §2.4)");
    }

    /** 审计留痕(security 客户端可用时非阻塞发送;缺失仅 debug)。 */
    private void audit(String action, Object detail) {
        SecurityEngineClient security = securityProvider.getIfAvailable();
        if (security == null) {
            return;
        }
        try {
            security.audit(action, detail);
        } catch (Exception ignored) {
            // 审计失败不阻塞主流程(与既有 SecurityEngineClient.audit 同纪律)
        }
    }

    /** 候选事件发布(runtime-event 未装配 → warn 降级不阻塞主流程)。 */
    private void publishCandidateEvent(String eventType, String candidateId, String tenantId,
                                       String traceId, String actor, String sourceRunId) {
        EventBusService bus = eventBusProvider.getIfAvailable();
        if (bus == null) {
            return;
        }
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("eventType", eventType);
            payload.put("domain", "ontology");
            payload.put("candidateId", candidateId);
            payload.put("tenantId", tenantId);
            payload.put("actor", actor);
            payload.put("traceId", traceId);
            if (sourceRunId != null && !sourceRunId.isBlank()) {
                payload.put("sourceRunId", sourceRunId);
            }
            bus.publish(KafkaTopics.topicForAggregate("agent"), payload);
        } catch (Exception ignored) {
            // 事件失败不阻塞候选主流程(降级留痕)
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

    private Map<String, Object> requireCandidate(String candidateId) {
        if (isBlank(candidateId)) {
            throw new com.chinacreator.gzcm.common.exception.NotFoundException("候选(id 为空)");
        }
        Map<String, Object> current = dao.findByCandidateId(candidateId.trim());
        if (current == null) {
            throw new com.chinacreator.gzcm.common.exception.NotFoundException("候选 " + candidateId);
        }
        return current;
    }

    private static void checkIdentifier(List<String> problems, String field, String value) {
        if (isBlank(value)) {
            return; // 全空兜底由调用方统一处理;单字段空不构成命名违规
        }
        if (!IDENTIFIER_PATTERN.matcher(value.trim()).matches()) {
            problems.add("命名违规: " + field + "=" + value.trim()
                    + "(须 ASCII 标识符 [A-Za-z][A-Za-z0-9_]*)");
        }
    }

    /** targetRef 实体 code 提取:支持 "Ent001.prop01" → "Ent001" 与纯 code。 */
    private static String firstEntityCodeOf(String targetRef) {
        if (isBlank(targetRef)) {
            return null;
        }
        int dot = targetRef.indexOf('.');
        return dot > 0 ? targetRef.substring(0, dot).trim() : targetRef.trim();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> toObjectMap(Object body) {
        if (body instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        if (isBlank(asString(body))) {
            throw new IllegalArgumentException("请求体必填(JSON 对象)");
        }
        try {
            return MAPPER.readValue(asString(body), Map.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("请求体非法 JSON: " + e.getMessage());
        }
    }

    private static List<String> asStringList(Object o) {
        if (!(o instanceof List<?> l)) {
            return List.of();
        }
        List<String> out = new ArrayList<>(l.size());
        for (Object e : l) {
            if (e != null && !isBlank(String.valueOf(e))) {
                out.add(String.valueOf(e).trim());
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseObject(String json) {
        if (isBlank(json)) {
            return null;
        }
        try {
            Object parsed = MAPPER.readValue(json, Object.class);
            return parsed instanceof Map<?, ?> m ? (Map<String, Object>) m : null;
        } catch (Exception e) {
            return null; // JSON 列解析失败按缺省处理(候选 creation 已保证结构)
        }
    }

    /** 从 LLM 文本抽取 0..1 数值("90%" → 0.9;"confidence: 0.78" → 0.78)。 */
    private static Double extractPercent(String text) {
        String s = text.trim();
        int pct = s.indexOf('%');
        if (pct >= 0) {
            String head = s.substring(0, pct).trim();
            int start = 0;
            while (start < head.length()
                    && !Character.isDigit(head.charAt(start))
                    && head.charAt(start) != '.' && head.charAt(start) != '-') {
                start++;
            }
            try {
                return clamp01(Double.parseDouble(head.substring(start)) / 100.0);
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*%?")
                .matcher(s);
        while (m.find()) {
            try {
                double d = Double.parseDouble(m.group(1));
                if (d <= 1.0) {
                    return clamp01(d);
                }
            } catch (NumberFormatException ignored) {
                // next
            }
        }
        return null;
    }

    private static double clamp01(double v) {
        if (v < 0) {
            return 0.0;
        }
        if (v > 1) {
            return 1.0;
        }
        return v;
    }

    private static String firstNonBlank(String... candidates) {
        for (String c : candidates) {
            if (!isBlank(c)) {
                return c.trim();
            }
        }
        return null;
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
        return b == null ? null : (b ? 1 : 0);
    }

    private static int countJsonArray(String json) {
        if (isBlank(json)) {
            return 0;
        }
        try {
            Object parsed = MAPPER.readValue(json, Object.class);
            if (parsed instanceof List<?> l) {
                return l.size();
            }
        } catch (Exception ignored) {
            // 解析失败按 0
        }
        return 0;
    }

    private static String asString(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s.trim();
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
        return UUID.randomUUID().toString();
    }

    private static String upperBlank(String s) {
        return s == null ? null : s.trim().toUpperCase(Locale.ROOT);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
