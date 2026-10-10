package com.chinacreator.gzcm.engine.cognitive2.service.asset;

import com.chinacreator.gzcm.common.event.EventTypes;
import com.chinacreator.gzcm.common.event.KafkaTopics;
import com.chinacreator.gzcm.engine.cognitive.api.CognitiveErrorCode;
import com.chinacreator.gzcm.engine.cognitive.api.asset.BeliefProbabilityKind;
import com.chinacreator.gzcm.engine.cognitive.api.asset.CognitiveAssetStatus;
import com.chinacreator.gzcm.engine.cognitive.api.objects.CognitiveObjectClass;
import com.chinacreator.gzcm.engine.cognitive2.dao.BeliefDao;
import com.chinacreator.gzcm.engine.cognitive2.dao.HypothesisDao;
import com.chinacreator.gzcm.engine.cognitive2.dao.ModelRegistryDao;
import com.chinacreator.gzcm.engine.cognitive2.service.CognitiveAuditFallback;
import com.chinacreator.gzcm.engine.cognitive2.service.objects.CognitiveObjectCatalogService;
import com.chinacreator.gzcm.engine.cognitive2.service.scenario.CognitiveGuardException;
import com.chinacreator.gzcm.runtime.eventbus.EventBusService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 认知资产生命周期与受控变更服务（补充设计 v1.0 §8 / §13）。
 *
 * <p><b>SUP 对照</b>：SUP-COG-01（catalog = C 层 11 对象 + 载体形态，仅出自
 * {@link CognitiveObjectCatalogService} 单源 + §8.1 A/B/C 三层表挂载属性）、
 * SUP-COG-02/§16.5 红线 4（belief 置信三分拦截落 {@link BeliefLifecycleGateway}，
 * 本服务不直接写 belief 行、不提供 probability 写入路径）、
 * SUP-COG-03（提案只落候选审计事件，不改原资产行/行数；无批准提案时认知资产状态/行数不变）。</p>
 *
 * <p><b>载体（V268 已落盘未实跑）</b>：治理态唯一权威列 =
 * {@code lifecycle_state}（hypothesis/belief/model 三表，词表单源
 * {@link CognitiveAssetStatus}）；存量 legacy {@code status} 列停写不改语义，
 * {@code lifecycle_state} NULL 时读侧按 V268 头注 ② 回落 legacy 展示（禁伪造接管态）。</p>
 *
 * <p><b>候选落点口径（SUP-COG-03 fallback，本 javadoc 即口径）</b>：cognitive 域<b>无专属候选表</b>
 * （data 候选 V272 归 data-engine、ontology 候选 V273 归 ontology-engine，§14.2「各自域
 * 正式候选载体」；ST07 控制域单库 5+1，认知侧禁读他域候选表）⇒ 候选<b>只落一条
 * {@code ecos.audit} 审计事件</b>（payload 带 proposalId + 完整提议内容），主通道不可用经
 * {@link CognitiveAuditFallback} 本地 JSONL 兜底；兜底仍失败 ⇒ fail-closed 抛
 * {@code ECOS-COG-501}（铁律 §2.4#5 三段式，禁「log.warn + continue」假候选）。
 * <b>禁止</b>借本服务新建认知侧候选表/DDL（铁律：0 新表、DDL 只走单源目录）。</p>
 *
 * <p><b>resolve 前缀路由（§13 只读解析，0 业务计算）</b>：
 * {@code bel} → {@code ecos_cognitive_belief} / {@code hyp} → {@code ecos_cognitive_hypothesis} /
 * {@code mod} → {@code ecos_cognitive_model}；前三段全查不到 ⇒ 404（§13.1 不降级为空数据）；
 * 其它前缀 ⇒ 400 fail-loud。belief 行附带置信三元组
 * （probability / confidenceKind / calibrationRef；prob_kind NULL + probability 非 NULL ⇒
 * {@code UNKNOWN_LEGACY} 回落，§8.3 末段禁伪造补齐）。</p>
 *
 * <p><b>状态迁移（§8.4 四要件）</b>：操作者身份 / 原因 / 证据引用 / 审计 traceId 随
 * {@link EventTypes.Cognition#COGNITIVE_ASSET_STATUS_CHANGED} 事件 payload 全量落
 * {@code ecos.cognitive}（Kafka 未启用自动走 eventbus 内存 fallback，发布失败降级 WARN 不阻塞主流程）。
 * SUPERSEDED = 建新版本 + supersedes 指旧版（§8.4「不可原地覆盖」，理由须含 supersededBy= 留痕）；
 * CONTRADICTED 仅置位，原行/反证链保留（§8.4「不等于删除」）。</p>
 */
@Service("ecosCognitiveAssetLifecycleService")
public class CognitiveAssetLifecycleService {

    private static final Logger log = LoggerFactory.getLogger(CognitiveAssetLifecycleService.class);

    private static final String KEY_BELIEF = "BELIEF";
    private static final String KEY_HYPOTHESIS = "HYPOTHESIS";
    private static final String KEY_MODEL = "MODEL";

    private final BeliefDao beliefDao;
    private final HypothesisDao hypothesisDao;
    private final ModelRegistryDao modelRegistryDao;
    private final CognitiveObjectCatalogService catalogService;
    private final CognitiveAuditFallback auditFallback;
    @Autowired(required = false)
    private EventBusService eventBus;

    public CognitiveAssetLifecycleService(BeliefDao beliefDao,
                                          HypothesisDao hypothesisDao,
                                          ModelRegistryDao modelRegistryDao,
                                          CognitiveObjectCatalogService catalogService,
                                          CognitiveAuditFallback auditFallback) {
        this.beliefDao = beliefDao;
        this.hypothesisDao = hypothesisDao;
        this.modelRegistryDao = modelRegistryDao;
        this.catalogService = catalogService;
        this.auditFallback = auditFallback;
    }

    // ── catalog（SUP-COG-01）───────────────────────────────

    /** C 层 11 对象目录 + §8.1 A/B/C 三层挂载属性（只读、零 SQL、枚举单源装配）。 */
    public Map<String, Object> catalog() {
        Map<String, Object> data = new LinkedHashMap<>();
        Map<String, Object> layers = new LinkedHashMap<>();
        layers.put("A", "权威业务事实：所属业务引擎/data-engine 写入，必须有来源与版本（最高权威性）");
        layers.put("B", "治理后的认知资产（Hypothesis/Belief/Decision Basis/Cognitive Model 等）："
                + "仅按 PRD-05 对象目录、载体归属与审批规则持久化，受治理、不自动等于事实");
        layers.put("C", "运行时推理产物：默认不作为业务事实落盘，经 traceId/runId 关联审计事件（临时、可过期）");
        data.put("layers", layers);
        data.put("objects", catalogService.catalog());
        data.put("governedAssetClasses", List.of(KEY_BELIEF, KEY_HYPOTHESIS, KEY_MODEL));
        data.put("lifecycleStates", cognitiveVocabulary());
        return data;
    }

    private static List<String> cognitiveVocabulary() {
        return java.util.Arrays.stream(CognitiveAssetStatus.values())
                .map(Enum::name).toList();
    }

    // ── resolve（§13 只读解析）─────────────────────────────

    /** 按 assetId 前缀（bel/hyp/mod）解析为只读 DTO；查不到 404，非法前缀 400。 */
    public Map<String, Object> resolve(String assetId) {
        AssetRef ref = parseRef(assetId);
        Map<String, Object> row = loadRow(ref);
        if (row == null) {
            throw new CognitiveGuardException(CognitiveErrorCode.E404_SCENARIO,
                    CognitiveErrorCode.MSG_INVALID_PARAM, ref.assetType() + ":" + assetId);
        }
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("assetId", assetId);
        dto.put("assetClass", ref.assetType());
        dto.put("assetType", ref.assetType());
        String lifecycle = clean(row.get("lifecycle_state"));
        dto.put("lifecycleState", lifecycle);
        dto.put("lifecycleSource", lifecycle != null ? "V268" : "legacy-fallback");
        dto.put("legacyStatus", clean(row.get("status")));
        if (KEY_BELIEF.equals(ref.assetType())) {
            setConfidenceTriplet(dto, row);
        }
        dto.put("objectKey", referenceObjectKey(ref.assetType()));
        return dto;
    }

    // ── transition（§8.4 状态机；非法 ⇒ 409 原行不变）─────

    /**
     * 统一状态机校验 + 落 {@code lifecycle_state}（V268 唯一权威列；SQL 端带
     * {@code WHERE id=? AND is_deleted=0}，0 行受影响 ⇒ 409 显式拒绝，禁静默）。
     *
     * <p>SUPERSEDED 附加门禁：reason 必须含 {@code supersededBy=}（新版本引用留痕，§8.4
     * 「ACTIVE 规则/模型版本不可原地覆盖；变更创建新版本并建立 supersedes」）。</p>
     */
    public Map<String, Object> transition(String assetId, String targetRaw, String reason,
                                          String evidenceRef, String operator) {
        AssetRef ref = parseRef(assetId);
        Optional<CognitiveAssetStatus> targetOpt = CognitiveAssetStatus.fromDbValue(targetRaw);
        if (targetOpt.isEmpty()) {
            throw new CognitiveGuardException(CognitiveErrorCode.E400_INVALID_PARAM,
                    CognitiveErrorCode.MSG_INVALID_PARAM, "target");
        }
        if (reason == null || reason.isBlank()) {
            throw new CognitiveGuardException(CognitiveErrorCode.E400_INVALID_PARAM,
                    CognitiveErrorCode.MSG_INVALID_PARAM, "reason");
        }
        if (CognitiveAssetStatus.SUPERSEDED == targetOpt.get()
                && !reason.contains("supersededBy=")) {
            throw new CognitiveGuardException(CognitiveErrorCode.E400_INVALID_PARAM,
                    CognitiveErrorCode.MSG_INVALID_PARAM, "reason:supersededBy-required");
        }
        Map<String, Object> row = loadRow(ref);
        if (row == null) {
            throw new CognitiveGuardException(CognitiveErrorCode.E404_SCENARIO,
                    CognitiveErrorCode.MSG_INVALID_PARAM, assetId);
        }
        CognitiveAssetStatus from = stateOrFallback(row);
        if (!from.canTransitTo(targetOpt.get())) {
            throw new CognitiveGuardException(CognitiveErrorCode.E409_STATE_ILLEGAL,
                    CognitiveErrorCode.MSG_STATE_ILLEGAL, assetId + ":" + from + "->" + targetRaw);
        }
        int updated = applyTransition(ref, assetId, targetOpt.get().name(), operator);
        if (updated <= 0) {
            throw new CognitiveGuardException(CognitiveErrorCode.E409_STATE_ILLEGAL,
                    CognitiveErrorCode.MSG_STATE_ILLEGAL, assetId + ":stale");
        }
        String traceId = UUID.randomUUID().toString();
        String eventRef = publishLifecycleChanged(assetId, ref.assetType(), from, targetOpt.get(),
                reason, evidenceRef, operator, traceId);
        log.info("认知资产状态迁移 {} {}->{} by={} traceId={}", assetId, from, targetOpt.get(),
                operator, traceId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("assetId", assetId);
        result.put("assetClass", ref.assetType());
        result.put("fromState", from.name());
        result.put("toState", targetOpt.get().name());
        result.put("traceId", traceId);
        result.put("eventRef", eventRef);
        return result;
    }

    // ── proposals（SUP-COG-03：候选只落审计事件，禁改原资产行）──

    /**
     * 提交认知资产变更候选（§13「只创建候选/提案」）。
     *
     * <p>候选落点 = 一条 {@code ecos.audit} 审计事件（carrier="ecos.audit"）+ 一条
     * {@link EventTypes.Cognition#COGNITIVE_CANDIDATE_PROPOSED} 通知（{@code ecos.cognitive}）。
     * 原资产行 0 改动（不 UPDATE、不 INSERT、行数不变）；批准升格须走 {@link #transition}。</p>
     */
    public Map<String, Object> propose(String assetId, Map<String, Object> proposal) {
        AssetRef ref = parseRef(assetId);
        if (proposal == null || proposal.isEmpty()) {
            throw new CognitiveGuardException(CognitiveErrorCode.E400_INVALID_PARAM,
                    CognitiveErrorCode.MSG_INVALID_PARAM, "proposal");
        }
        Optional<CognitiveAssetStatus> targetOpt =
                CognitiveAssetStatus.fromDbValue(clean(proposal.get("targetState")));
        if (targetOpt.isEmpty()) {
            throw new CognitiveGuardException(CognitiveErrorCode.E400_INVALID_PARAM,
                    CognitiveErrorCode.MSG_INVALID_PARAM, "targetState");
        }
        Map<String, Object> row = loadRow(ref);
        if (row != null) {
            // 原行存在 ⇒ 与 transition 同一矩阵校验（非法目标态 ⇒ 409，不建候选）
            if (!stateOrFallback(row).canTransitTo(targetOpt.get())) {
                throw new CognitiveGuardException(CognitiveErrorCode.E409_STATE_ILLEGAL,
                        CognitiveErrorCode.MSG_STATE_ILLEGAL, assetId + ":proposed");
            }
        }
        String operator = clean(proposal.get("operator"));
        String reason = clean(proposal.get("reason"));
        if (reason == null) {
            throw new CognitiveGuardException(CognitiveErrorCode.E400_INVALID_PARAM,
                    CognitiveErrorCode.MSG_INVALID_PARAM, "reason");
        }
        Object evidenceRef = proposal.get("evidenceRef");
        String proposalId = "cog_prop_" + UUID.randomUUID().toString().substring(0, 12);
        String traceId = UUID.randomUUID().toString();

        Map<String, Object> candidate = new LinkedHashMap<>();
        candidate.put("proposalId", proposalId);
        candidate.put("assetId", assetId);
        candidate.put("assetClass", ref.assetType());
        candidate.put("targetState", targetOpt.get().name());
        candidate.put("reason", reason);
        candidate.put("evidenceRef", evidenceRef);
        candidate.put("operator", operator);

        Map<String, Object> auditPayload = new LinkedHashMap<>();
        auditPayload.put("action", "cognitive.asset.proposal");
        auditPayload.put("resource", "ecos_cognitive_asset:" + ref.assetType());
        auditPayload.put("result", "PENDING_APPROVAL");
        auditPayload.put("detail", proposalId);
        auditPayload.put("candidate", candidate);
        auditPayload.put("userId", operator);
        auditPayload.put("timestamp", LocalDateTime.now().toString());

        boolean auditOk = publishToKafka(KafkaTopics.AUDIT, auditPayload);
        if (!auditOk) {
            try {
                auditFallback.write(auditPayload);
            } catch (IOException e) {
                throw new CognitiveGuardException(CognitiveErrorCode.E501_SCHEMA_NOT_READY,
                        CognitiveErrorCode.MSG_INTERNAL, "audit-channel-down");
            }
        }
        Map<String, Object> notify = new LinkedHashMap<>();
        notify.put("eventType", EventTypes.Cognition.COGNITIVE_CANDIDATE_PROPOSED);
        notify.put("proposalId", proposalId);
        notify.put("assetId", assetId);
        notify.put("assetClass", ref.assetType());
        notify.put("targetState", targetOpt.get().name());
        notify.put("reason", reason);
        notify.put("evidenceRef", evidenceRef);
        notify.put("operator", operator);
        notify.put("traceId", traceId);
        boolean notified = publishToKafka(KafkaTopics.topicForAggregate("cognition"), notify);

        log.info("认知资产变更候选已登记 proposalId={} assetId={} target={}（原资产行未变更）",
                proposalId, assetId, targetOpt.get());
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("proposalId", proposalId);
        resp.put("assetId", assetId);
        resp.put("status", "PENDING_APPROVAL");
        resp.put("carrier", "ecos.audit");
        resp.put("eventRef", notified
                ? EventTypes.Cognition.COGNITIVE_CANDIDATE_PROPOSED : "audit-fallback");
        resp.put("traceId", traceId);
        return resp;
    }

    // ── internals ──────────────────────────────────────────

    record AssetRef(String assetId, String assetType) {
    }

    private static AssetRef parseRef(String assetId) {
        if (assetId == null || assetId.isBlank()) {
            throw new CognitiveGuardException(CognitiveErrorCode.E400_INVALID_PARAM,
                    CognitiveErrorCode.MSG_INVALID_PARAM, "assetId");
        }
        String id = assetId.trim();
        String prefix = id.length() >= 3 ? id.toLowerCase().substring(0, 3) : id.toLowerCase();
        return switch (prefix) {
            case "bel" -> new AssetRef(id, KEY_BELIEF);
            case "hyp" -> new AssetRef(id, KEY_HYPOTHESIS);
            case "mod" -> new AssetRef(id, KEY_MODEL);
            default -> throw new CognitiveGuardException(CognitiveErrorCode.E400_INVALID_PARAM,
                    CognitiveErrorCode.MSG_INVALID_PARAM, "assetId-prefix");
        };
    }

    private Map<String, Object> loadRow(AssetRef ref) {
        return switch (ref.assetType()) {
            case KEY_BELIEF -> beliefDao.findLifecycleStateById(ref.assetId());
            case KEY_HYPOTHESIS -> hypothesisDao.findLifecycleStateById(ref.assetId());
            default -> modelRegistryDao.findLifecycleStateById(ref.assetId());
        };
    }

    private int applyTransition(AssetRef ref, String assetId, String target, String operator) {
        return switch (ref.assetType()) {
            case KEY_BELIEF -> beliefDao.updateLifecycleState(assetId, target, operator);
            case KEY_HYPOTHESIS -> hypothesisDao.updateLifecycleState(assetId, target, operator);
            default -> modelRegistryDao.updateLifecycleState(assetId, target, operator);
        };
    }

    /** lifecycle_state NULL ⇒ legacy status 回落映射（V268 头注 ② 展示口径，禁伪造）。 */
    private static CognitiveAssetStatus stateOrFallback(Map<String, Object> row) {
        String lifecycle = clean(row.get("lifecycle_state"));
        Optional<CognitiveAssetStatus> direct = CognitiveAssetStatus.fromDbValue(lifecycle);
        if (direct.isPresent()) {
            return direct.get();
        }
        String legacy = clean(row.get("status"));
        String registry = clean(row.get("registry_state"));
        if ("PUBLISHED".equalsIgnoreCase(registry)) {
            return CognitiveAssetStatus.ACTIVE;
        }
        return switch (legacy == null ? "" : legacy.toUpperCase()) {
            case "ACTIVE" -> CognitiveAssetStatus.ACTIVE;
            case "VALID", "EVIDENCED" -> CognitiveAssetStatus.EVIDENCE_CHECKED;
            case "ARCHIVED" -> CognitiveAssetStatus.DEPRECATED;
            case "PROPOSED" -> CognitiveAssetStatus.CANDIDATE;
            case "REFUTED", "INVALIDATED" -> CognitiveAssetStatus.CONTRADICTED;
            default -> CognitiveAssetStatus.DRAFT;
        };
    }

    private static void setConfidenceTriplet(Map<String, Object> dto, Map<String, Object> beliefRow) {
        Object probability = beliefRow.get("probability");
        String rawKind = clean(beliefRow.get("prob_kind"));
        Optional<BeliefProbabilityKind> kindOpt = BeliefProbabilityKind.fromDbValue(rawKind);
        if (kindOpt.isEmpty() && probability != null) {
            kindOpt = Optional.of(BeliefProbabilityKind.UNKNOWN_LEGACY);
        }
        dto.put("confidenceProbability", probability instanceof Number n ? n.doubleValue() : null);
        dto.put("confidenceKind", kindOpt.map(Enum::name).orElse(null));
        dto.put("calibrationRef", clean(beliefRow.get("calibration_ref")));
    }

    private static String referenceObjectKey(String assetType) {
        return switch (assetType) {
            case KEY_BELIEF -> CognitiveObjectClass.BELIEF.objectKey();
            case KEY_HYPOTHESIS -> CognitiveObjectClass.HYPOTHESIS.objectKey();
            default -> CognitiveObjectClass.COGNITIVE_MODEL_INSTANCE.objectKey();
        };
    }

    private String publishLifecycleChanged(String assetId, String assetClass, CognitiveAssetStatus from,
                                           CognitiveAssetStatus to, String reason, Object evidenceRef,
                                           String operator, String traceId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventType", EventTypes.Cognition.COGNITIVE_ASSET_STATUS_CHANGED);
        payload.put("assetId", assetId);
        payload.put("assetClass", assetClass);
        payload.put("fromState", from.name());
        payload.put("toState", to.name());
        payload.put("reason", reason);
        payload.put("evidenceRef", evidenceRef);
        payload.put("operator", operator);
        payload.put("traceId", traceId);
        payload.put("timestamp", LocalDateTime.now().toString());
        return publishToKafka(KafkaTopics.topicForAggregate("cognition"), payload)
                ? EventTypes.Cognition.COGNITIVE_ASSET_STATUS_CHANGED : "audit-fallback";
    }

    private boolean publishToKafka(String topic, Object payload) {
        if (eventBus == null) {
            return false;
        }
        try {
            eventBus.publish(topic, payload);
            return true;
        } catch (Exception e) {
            log.warn("认知资产事件 publish 失败 topic={} err={}", topic, e.getMessage());
            return false;
        }
    }

    private static String clean(Object raw) {
        if (raw == null) {
            return null;
        }
        String v = String.valueOf(raw);
        return v.isBlank() ? null : v.trim();
    }
}
