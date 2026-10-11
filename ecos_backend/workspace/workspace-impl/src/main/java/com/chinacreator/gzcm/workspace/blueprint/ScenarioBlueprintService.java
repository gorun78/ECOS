package com.chinacreator.gzcm.workspace.blueprint;

import com.chinacreator.gzcm.common.event.EventTypes;
import com.chinacreator.gzcm.common.event.KafkaTopics;
import com.chinacreator.gzcm.common.trace.EcosTraceContext;
import com.chinacreator.gzcm.runtime.eventbus.EventBusService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 场景蓝图应用服务（补充设计 v1.0 §6，§6.1 属主 workspace/场景工作台）。
 *
 * <p>唯一写入面：蓝图 CRUD/状态迁移/审批/发布全部经本服务；ai/cognitive 域禁止复制权威副本
 * （§6.1 / §17 捷径禁令 2）。每次状态迁移记录 actor/时间/原因/traceId/版本，并使能
 * {@code ScenarioBlueprintStatusChanged}、发布时使能 {@code ScenarioBlueprintPublished}
 * （Kafka 聚合名 scenario → 默认路由 {@link KafkaTopics#AUDIT}，禁新建 topic；
 * 发布不可变：发布版本锁死，修订 = version+1 新行 + 旧行 DEPRECATED，§6.2/§6.6）。</p>
 *
 * <p>Controller 不直用 JdbcTemplate（三层纪律），数据访问经 {@link ScenarioBlueprintDao}；
 * 审计/事件发布经 runtime-event {@link EventBusService}（禁业务侧自建 KafkaTemplate，
 * 铁律 §2.5）。</p>
 */
@Service
public class ScenarioBlueprintService {

    private static final Logger log = LoggerFactory.getLogger(ScenarioBlueprintService.class);

    private final ScenarioBlueprintDao dao;
    private final ScenarioBlueprintStateMachine stateMachine;
    private final ScenarioBlueprintValidator validator;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<EventBusService> eventBusProvider;

    public ScenarioBlueprintService(ScenarioBlueprintDao dao,
                                    ScenarioBlueprintStateMachine stateMachine,
                                    ScenarioBlueprintValidator validator,
                                    ObjectMapper objectMapper,
                                    ObjectProvider<EventBusService> eventBusProvider) {
        this.dao = dao;
        this.stateMachine = stateMachine;
        this.validator = validator;
        this.objectMapper = objectMapper;
        this.eventBusProvider = eventBusProvider;
    }

    // ═══════════════ 创建草稿 ═══════════════

    /** POST 创建草稿（DRAFT，version+1 新行；并发撞 uk → 409）。 */
    @Transactional(rollbackFor = Exception.class)
    public ScenarioBlueprintVO create(String scenarioId, BlueprintSaveDTO dto, String actor) {
        if (scenarioId == null || scenarioId.isBlank()) {
            throw new BlueprintContentInvalidException("scenarioId", "必填");
        }
        if (dto == null || dto.getGoalStatement() == null || dto.getGoalStatement().isBlank()) {
            throw new BlueprintContentInvalidException("goalStatement", "必填（§6.2 goal.statement）");
        }
        String traceId = EcosTraceContext.currentTraceId() == null ? UUID.randomUUID().toString()
                : EcosTraceContext.currentTraceId();
        Integer maxVersion = dao.maxVersionOfScenario(scenarioId);
        int nextVersion = (maxVersion == null || maxVersion < 1) ? 1 : maxVersion + 1;

        ScenarioBlueprint bp = new ScenarioBlueprint();
        bp.setId(UUID.randomUUID().toString());
        bp.setTenantId(currentTenantId());
        bp.setScenarioId(scenarioId);
        bp.setVersion(nextVersion);
        bp.setStatus(BlueprintStatus.DRAFT.name());
        bp.setGoalStatement(dto.getGoalStatement());
        bp.setGoalSuccessText(jsonOrNull(dto.getSuccessCriteria()));
        bp.setScopeJsonText(jsonOrNull(dto.getScope()));
        bp.setDependenciesText(jsonOrNull(dto.getDependencies(), "[]"));
        bp.setCapabilitiesText(jsonOrNull(dto.getCapabilities(), "[]"));
        bp.setGapsText("[]");
        bp.setRisksText(jsonOrNull(dto.getRisks(), "[]"));
        bp.setTestsText(jsonOrNull(dto.getTests(), "[]"));
        bp.setContentJson(buildContentSnapshot(bp, dto));
        bp.setApprovalRequired(dto.isApprovalRequired() ? 1 : 0);
        bp.setContextDigest(dto.getContextDigest());
        bp.setTraceId(traceId);
        bp.setActor(actor);
        bp.setTransitionReason(dto.getNote() == null ? "draft created" : dto.getNote());
        bp.setDomain("default");
        bp.setVersionNo(String.valueOf(nextVersion));
        bp.setCreateBy(actor);
        bp.setUpdateBy(actor);

        dao.insert(bp);
        publish(EventTypes.Scenario.SCENARIO_BLUEPRINT_STATUS_CHANGED,
                ScenarioBlueprintStateMachine.transitionPayload(
                        bp.getId(), nextVersion, null, BlueprintStatus.DRAFT, actor,
                        "draft created", traceId));
        log.info("蓝图草稿创建 bp={} scenario={} v={} actor={} trace={}",
                bp.getId(), scenarioId, nextVersion, actor, traceId);
        return toVO(bp, BlueprintStatus.DRAFT);
    }

    /** 修订（§6.2 修订语义）：以既有版本内容为模板生成 version+1 的 DRAFT 新行；
     *  旧行不动（RELEASED 谱线由 transition→DEPRECATED 处理）。 */
    @Transactional(rollbackFor = Exception.class)
    public ScenarioBlueprintVO revise(String blueprintId, String actor) {
        ScenarioBlueprint base = requireById(blueprintId);
        String traceId = EcosTraceContext.currentTraceId() == null ? UUID.randomUUID().toString()
                : EcosTraceContext.currentTraceId();
        Integer maxVersion = dao.maxVersionOfScenario(base.getScenarioId());
        int nextVersion = (maxVersion == null || maxVersion < 1) ? 1 : maxVersion + 1;

        // 内容快照继承（content_json/_text 子段逐列继承，发布面冻结体随新谱线重走校验）
        ScenarioBlueprint bp = new ScenarioBlueprint();
        bp.setId(UUID.randomUUID().toString());
        bp.setTenantId(base.getTenantId());
        bp.setScenarioId(base.getScenarioId());
        bp.setVersion(nextVersion);
        bp.setStatus(BlueprintStatus.DRAFT.name());
        bp.setGoalStatement(base.getGoalStatement());
        bp.setGoalSuccessText(base.getGoalSuccessText());
        bp.setScopeJsonText(base.getScopeJsonText());
        bp.setDependenciesText(base.getDependenciesText());
        bp.setCapabilitiesText(base.getCapabilitiesText());
        bp.setGapsText(base.getGapsText());
        bp.setRisksText(base.getRisksText());
        bp.setTestsText(base.getTestsText());
        bp.setContentJson(buildContentSnapshot(bp, null)); // 重新生成快照（view/status/version 刷新）
        bp.setApprovalRequired(base.getApprovalRequired());
        bp.setTraceId(traceId);
        bp.setActor(actor);
        bp.setTransitionReason("revision of v" + base.getVersion());
        bp.setDomain(base.getDomain() == null ? "default" : base.getDomain());
        bp.setVersionNo(String.valueOf(nextVersion));
        bp.setCreateBy(actor);
        bp.setUpdateBy(actor);

        dao.insert(bp);
        publish(EventTypes.Scenario.SCENARIO_BLUEPRINT_STATUS_CHANGED,
                ScenarioBlueprintStateMachine.transitionPayload(
                        bp.getId(), nextVersion, null, BlueprintStatus.DRAFT, actor,
                        "revision of v" + base.getVersion(), traceId));
        log.info("蓝图修订创建 bp={} base={} v={} actor={} trace={}",
                bp.getId(), base.getId(), nextVersion, actor, traceId);
        return toVO(bp, BlueprintStatus.DRAFT);
    }

    // ═══════════════ 读 ═══════════════

    public List<ScenarioBlueprintVO> listVersions(String scenarioId) {
        return dao.listByScenario(scenarioId).stream().map(bp -> toVO(bp, parseStatus(bp))).toList();
    }

    public ScenarioBlueprintVO get(String blueprintId) {
        ScenarioBlueprint bp = requireById(blueprintId);
        return toVO(bp, parseStatus(bp));
    }

    // ═══════════════ 迁移（§6.3 五要素留痕） ═══════════════

    /** POST {id}/transition：状态机断言 + CAS 从态守卫（并发双迁 409）+ 事件/审计。 */
    @Transactional(rollbackFor = Exception.class)
    public ScenarioBlueprintVO transition(String blueprintId, String target, String reason, String actor) {
        ScenarioBlueprint bp = requireById(blueprintId);
        BlueprintStatus from = parseStatus(bp);
        BlueprintStatus to = BlueprintStatus.fromNullable(target)
                .orElseThrow(() -> IllegalBlueprintTransitionException.illegal(str(from), target));

        stateMachine.assertTransition(from, to);
        if (to == BlueprintStatus.RELEASED) {
            // 版本不可变：发布走专用 /release 门槛端点，禁经通用迁移绕过验收（SUP-BP-02）
            throw new IllegalBlueprintTransitionException(
                    "RELEASE_VIA_RELEASE_ENDPOINT_ONLY", "发布须经 POST /{id}/release 走验收门槛（§6.6）");
        }
        String traceId = EcosTraceContext.currentTraceId() == null ? UUID.randomUUID().toString()
                : EcosTraceContext.currentTraceId();
        int rows = dao.updateTransition(bp.getId(), to.name(), actor,
                reason == null ? "" : reason, traceId, from.name());
        if (rows == 0) {
            throw new BlueprintConflictException(bp.getId(), from.name(), to.name());
        }
        publish(EventTypes.Scenario.SCENARIO_BLUEPRINT_STATUS_CHANGED,
                ScenarioBlueprintStateMachine.transitionPayload(bp.getId(), bp.getVersion(), from, to, actor, reason, traceId));
        log.info("蓝图迁移 bp={} v={} {}->{} actor={} reason={} trace={}",
                bp.getId(), bp.getVersion(), from, to, actor, reason, traceId);
        return get(bp.getId());
    }

    /** 进入 READY_FOR_APPROVAL 前置：验收门槛全过（§6.6）。门槛未过 → 409（service 语义，
     *  端点侧按 409 处理；质量门禁失败 422 属发布侧，见 validate/release）。 */
    @Transactional(rollbackFor = Exception.class)
    public ScenarioBlueprintVO enterApprovalGate(String blueprintId, String reason, String actor) {
        ScenarioBlueprint bp = requireById(blueprintId);
        ScenarioBlueprintValidator.ValidationResult result = validator.validateForRelease(toContent(bp));
        if (!result.isPassed()) {
            throw PublishBlockedException.validationFailed(result.getReasons(), result.getDetails());
        }
        return transition(bp.getId(), BlueprintStatus.READY_FOR_APPROVAL.name(), reason, actor);
    }

    // ═══════════════ 验收/发布（§6.6 门槛 → 201） ═══════════════

    /** GET {id}/validate：只读校验，永不抛未过异常（响应 200 + passed=false + 明细）。 */
    public Map<String, Object> validate(String blueprintId) {
        ScenarioBlueprint bp = requireById(blueprintId);
        ScenarioBlueprintValidator.ValidationResult result = validator.validateForRelease(toContent(bp));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("blueprintId", bp.getId());
        data.put("scenarioId", bp.getScenarioId());
        data.put("version", bp.getVersion());
        data.put("status", bp.getStatus());
        data.put("passed", result.isPassed());
        data.put("reasons", result.getReasons());
        data.put("details", result.getDetails());
        return data;
    }

    /** GET {id}/diff：两版本 gap/依赖结构化对比（无 diff 表，§6.2 content diff）。 */
    public Map<String, Object> diff(String blueprintId, Integer fromVersion, Integer toVersion) {
        ScenarioBlueprint base = requireById(blueprintId);
        int fv = fromVersion == null ? Math.max(1, base.getVersion() - 1) : fromVersion;
        int tv = toVersion == null ? base.getVersion() : toVersion;
        ScenarioBlueprint old = requireByScenarioVersion(base.getScenarioId(), fv);
        ScenarioBlueprint now = requireByScenarioVersion(base.getScenarioId(), tv);
        return blueprintDiff(old, now);
    }

    /** 两版本内容 diff（公开供端点/测试复用，§6.2 内容 diff 口径）。 */
    public Map<String, Object> blueprintDiff(ScenarioBlueprint old, ScenarioBlueprint now) {
        Map<String, Object> diff = new LinkedHashMap<>();
        diff.put("fromVersion", old.getVersion());
        diff.put("toVersion", now.getVersion());
        diff.put("statusFrom", old.getStatus());
        diff.put("statusTo", now.getStatus());
        diff.put("dependencies", diffById(old.getDependenciesText(), now.getDependenciesText(), "id"));
        diff.put("capabilities", diffById(old.getCapabilitiesText(), now.getCapabilitiesText(), "capabilityId"));
        diff.put("gaps", diffById(old.getGapsText(), now.getGapsText(), "gapId"));
        diff.put("contentJsonChanged", !Objects.equals(old.getContentJson(), now.getContentJson()));
        return diff;
    }

    /** POST {id}/approve：approval_ref 必填（缺失 409）→ APPROVED。 */
    @Transactional(rollbackFor = Exception.class)
    public ScenarioBlueprintVO approve(String blueprintId, String approvalRef, String actor) {
        ScenarioBlueprint bp = requireById(blueprintId);
        if (approvalRef == null || approvalRef.isBlank()) {
            throw PublishBlockedException.missingApproval("approval_ref 必填（§6.6 审批链齐）");
        }
        BlueprintStatus from = parseStatus(bp);
        stateMachine.assertTransition(from, BlueprintStatus.APPROVED);
        String traceId = EcosTraceContext.currentTraceId() == null ? UUID.randomUUID().toString()
                : EcosTraceContext.currentTraceId();
        int rows = dao.updateTransition(bp.getId(), BlueprintStatus.APPROVED.name(), actor,
                "approved:" + approvalRef, traceId, from.name());
        if (rows == 0) {
            throw new BlueprintConflictException(bp.getId(), from.name(), BlueprintStatus.APPROVED.name());
        }
        dao.updateApprovalRef(bp.getId(), approvalRef, actor);
        publish(EventTypes.Scenario.SCENARIO_BLUEPRINT_STATUS_CHANGED,
                ScenarioBlueprintStateMachine.transitionPayload(bp.getId(), bp.getVersion(), from,
                        BlueprintStatus.APPROVED, actor, "approved:" + approvalRef, traceId));
        log.info("蓝图审批通过 bp={} v={} ref={} actor={} trace={}",
                bp.getId(), bp.getVersion(), approvalRef, actor, traceId);
        return get(bp.getId());
    }

    /** POST {id}/release：验收门槛全过 + 审批链齐 → RELEASED（201，发布不可变 §6.6）。
     *  发事件：ScenarioBlueprintPublished（聚合 scenario → 默认路由 ecos.audit）。 */
    @Transactional(rollbackFor = Exception.class)
    public ScenarioBlueprintVO release(String blueprintId, String actor) {
        ScenarioBlueprint bp = requireById(blueprintId);
        BlueprintStatus from = parseStatus(bp);

        // 门槛 1：四道静态验收（§6.6）— 未过 409（SUP-BP-02）
        ScenarioBlueprintValidator.ValidationResult result = validator.validateForRelease(toContent(bp));
        if (!result.isPassed()) {
            throw PublishBlockedException.validationFailed(result.getReasons(), result.getDetails());
        }
        // 门槛 2：审批链齐（approval_required=1 必带 ref）
        if (bp.getApprovalRequired() == 1 && (bp.getApprovalRef() == null || bp.getApprovalRef().isBlank())) {
            throw PublishBlockedException.missingApproval("审批链不齐：approval_required=1 但 approval_ref 为空（§6.6）");
        }
        // 状态机：仅 APPROVED → RELEASED
        stateMachine.assertTransition(from, BlueprintStatus.RELEASED);

        String digest = sha256Hex(bp.getContentJson() == null ? "" : bp.getContentJson());
        String traceId = EcosTraceContext.currentTraceId() == null ? UUID.randomUUID().toString()
                : EcosTraceContext.currentTraceId();
        int rows = dao.updateRelease(bp.getId(), BlueprintStatus.RELEASED.name(), digest,
                bp.getContextDigest(), actor, from.name());
        if (rows == 0) {
            throw new BlueprintConflictException(bp.getId(), from.name(), BlueprintStatus.RELEASED.name());
        }

        publish(EventTypes.Scenario.SCENARIO_BLUEPRINT_PUBLISHED,
                ScenarioBlueprintStateMachine.transitionPayload(bp.getId(), bp.getVersion(), from,
                        BlueprintStatus.RELEASED, actor, "published", traceId));
        log.info("蓝图发布 bp={} v={} digest={} actor={} trace={}",
                bp.getId(), bp.getVersion(), digest, actor, traceId);
        ScenarioBlueprintVO vo = get(bp.getId());
        vo.setArtifactDigest(digest);
        return vo;
    }

    // ───────────────── helpers ─────────────────

    private ScenarioBlueprint requireById(String blueprintId) {
        return dao.findById(blueprintId).stream().findFirst()
                .orElseThrow(() -> new BlueprintNotFoundException(blueprintId));
    }

    private ScenarioBlueprint requireByScenarioVersion(String scenarioId, int version) {
        return dao.findByScenarioAndVersion(scenarioId, version).stream().findFirst()
                .orElseThrow(() -> new BlueprintVersionNotFoundException(scenarioId, version));
    }

    private BlueprintStatus parseStatus(ScenarioBlueprint bp) {
        return BlueprintStatus.fromNullable(bp.getStatus())
                .orElseThrow(() -> new BlueprintStateException(
                        "UNRECOGNIZED_STATUS", "蓝图状态词表异常: " + bp.getStatus()));
    }

    private ScenarioBlueprintValidator.BlueprintContent toContent(ScenarioBlueprint bp) {
        ScenarioBlueprintValidator.BlueprintContent c = new ScenarioBlueprintValidator.BlueprintContent();
        c.gapsText = bp.getGapsText();
        c.dependenciesText = bp.getDependenciesText();
        c.capabilitiesText = bp.getCapabilitiesText();
        c.testsText = bp.getTestsText();
        c.approvalRequired = bp.getApprovalRequired();
        c.approvalRef = bp.getApprovalRef();
        return c;
    }

    /** 完整 §6.2 逻辑模型快照（发布冻结体；运行回放以本列解析，SUP-BP-03）。 */
    private String buildContentSnapshot(ScenarioBlueprint bp, BlueprintSaveDTO dto) {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("blueprintId", bp.getId());
        content.put("scenarioId", bp.getScenarioId());
        content.put("version", bp.getVersion());
        content.put("status", bp.getStatus());
        Map<String, Object> goal = new LinkedHashMap<>();
        goal.put("statement", bp.getGoalStatement());
        goal.put("successCriteria", parseStringArray(bp.getGoalSuccessText()));
        content.put("goal", goal);
        content.put("scope", parseObject(bp.getScopeJsonText(), Map.of()));
        content.put("dependencies", parseObject(bp.getDependenciesText(), List.of()));
        content.put("capabilities", parseObject(bp.getCapabilitiesText(), List.of()));
        content.put("gaps", parseObject(bp.getGapsText(), List.of()));
        content.put("risks", parseObject(bp.getRisksText(), List.of()));
        content.put("tests", parseObject(bp.getTestsText(), List.of()));
        Map<String, Object> approval = new LinkedHashMap<>();
        approval.put("required", bp.getApprovalRequired() == 1);
        approval.put("policyRef", dto == null ? null : dto.getApprovalPolicyRef());
        content.put("approval", approval);
        Map<String, Object> release = new LinkedHashMap<>();
        release.put("artifactDigest", null);
        release.put("releasedAt", null);
        content.put("release", release);
        return json(content);
    }

    private Map<String, Object> diffById(String oldJson, String nowJson, String idField) {
        List<Map<String, Object>> oldList = parseObject(oldJson, List.of());
        List<Map<String, Object>> nowList = parseObject(nowJson, List.of());
        Map<String, Map<String, Object>> oldIdx = indexById(oldList, idField);
        Map<String, Map<String, Object>> nowIdx = indexById(nowList, idField);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("added", new ArrayList<>(diffKeys(nowIdx.keySet(), oldIdx.keySet())));
        out.put("removed", new ArrayList<>(diffKeys(oldIdx.keySet(), nowIdx.keySet())));
        List<String> changed = new ArrayList<>();
        for (String key : nowIdx.keySet()) {
            Map<String, Object> old = oldIdx.get(key);
            if (old != null && !Objects.equals(old, nowIdx.get(key))) {
                changed.add(key);
            }
        }
        out.put("changed", changed);
        return out;
    }

    private static Set<String> diffKeys(Set<String> a, Set<String> b) {
        Set<String> out = new LinkedHashSet<>(a);
        out.removeAll(b);
        return out;
    }

    private static Map<String, Map<String, Object>> indexById(List<Map<String, Object>> list, String idField) {
        Map<String, Map<String, Object>> idx = new LinkedHashMap<>();
        for (Map<String, Object> item : list) {
            Object id = item.get(idField);
            if (id != null) {
                idx.put(String.valueOf(id), item);
            }
        }
        return idx;
    }

    private ScenarioBlueprintVO toVO(ScenarioBlueprint bp, BlueprintStatus status) {
        ScenarioBlueprintVO vo = new ScenarioBlueprintVO();
        vo.setId(bp.getId());
        vo.setScenarioId(bp.getScenarioId());
        vo.setVersion(bp.getVersion());
        vo.setStatus(bp.getStatus());
        vo.setGoalStatement(bp.getGoalStatement());
        vo.setSuccessCriteria(parseStringArray(bp.getGoalSuccessText()));
        vo.setScope(parseObject(bp.getScopeJsonText(), Map.of()));
        vo.setDependencies(parseObject(bp.getDependenciesText(), List.of()));
        vo.setCapabilities(parseObject(bp.getCapabilitiesText(), List.of()));
        vo.setGaps(parseObject(bp.getGapsText(), List.of()));
        vo.setRisks(parseObject(bp.getRisksText(), List.of()));
        vo.setTests(parseObject(bp.getTestsText(), List.of()));
        vo.setApprovalRequired(bp.getApprovalRequired() == 1);
        vo.setApprovalRef(bp.getApprovalRef());
        vo.setArtifactDigest(bp.getArtifactDigest());
        vo.setContextDigest(bp.getContextDigest());
        vo.setTraceId(bp.getTraceId());
        vo.setActor(bp.getActor());
        vo.setTransitionReason(bp.getTransitionReason());
        vo.setReleasedTime(bp.getReleasedTime());
        vo.setCreateTime(bp.getCreateTime());
        vo.setUpdateTime(bp.getUpdateTime());
        vo.setAllowedTransitions(stateMachine.allowedTransitions(status).stream()
                .map(Enum::name).toList());
        return vo;
    }

    @SuppressWarnings("unchecked")
    private <T> T parseObject(String json, T fallback) {
        if (json == null || json.isBlank()) {
            return fallback;
        }
        try {
            return (T) objectMapper.readValue(json, Object.class);
        } catch (JsonProcessingException e) {
            // 落库 JSON 解析失败不伪装合法：按空投影返回原始结构缺失（前端渲染缺省），并留痕
            log.warn("蓝图结构化列解析失败，按缺省投影处理: {}", e.getMessage());
            return fallback;
        }
    }

    private List<String> parseStringArray(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, List.class);
        } catch (JsonProcessingException e) {
            log.warn("蓝图 successCriteria 解析失败，按空数组处理: {}", e.getMessage());
            return List.of();
        }
    }

    private String jsonOrNull(Object value) {
        return jsonOrNull(value, null);
    }

    private String jsonOrNull(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        return json(value);
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("蓝图字段序列化失败: " + e.getMessage(), e);
        }
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(64);
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 摘要不可用: " + e.getMessage(), e);
        }
    }

    private void publish(String eventType, Map<String, Object> payload) {
        // 聚合名 scenario 无专属 topic → 默认路由 ecos.audit（EventTypes.Scenario 注册口径，
        // 禁新建 topic）；载荷只带 ID/状态/版本/审批引用/traceId，禁带完整依赖正文（§13）。
        Map<String, Object> event = new LinkedHashMap<>(payload);
        event.put("eventId", UUID.randomUUID().toString());
        event.put("timestamp", LocalDateTime.now().toString());
        event.put("module", "workspace");
        event.put("eventType", eventType);
        EventBusService bus = eventBusProvider.getIfAvailable();
        if (bus == null) {
            log.warn("蓝图事件发布降级：EventBusService 未装配，仅记日志: type={} bp={}",
                    eventType, payload.get("blueprintId"));
            return;
        }
        try {
            bus.publish(KafkaTopics.AUDIT, event);
        } catch (Exception e) {
            // 事件发布失败不阻断主流程（与 WorkspaceSecurityEngineClient 审计口径一致）
            log.warn("蓝图事件发布失败（不阻断主流程）: type={} bp={} reason={}",
                    eventType, payload.get("blueprintId"), e.getMessage());
        }
    }

    private String currentTenantId() {
        // 租户上下文：gateway 侧由 X-ECOS-* / JWT 链路经 sysman 建立；
        // 独立 :18090 进程无透传面时缺省 default-tenant（与场景侧口径一致，
        // 多租户隔离在 security-engine RLS 层兜底，本表租户列仅审计维度）。
        try {
            org.springframework.security.core.Authentication auth =
                    org.springframework.security.core.context.SecurityContextHolder.getContext()
                            .getAuthentication();
            if (auth != null && auth.getDetails() instanceof Map<?, ?> details
                    && details.get("tenantId") != null) {
                return String.valueOf(details.get("tenantId"));
            }
        } catch (Exception ignored) {
            // 缺省租户
        }
        return "default-tenant";
    }

    private static String str(BlueprintStatus s) {
        return s == null ? "null" : s.name();
    }
}
