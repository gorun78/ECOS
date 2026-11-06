package com.chinacreator.gzcm.engine.cognitive2.service.framework;

import com.chinacreator.gzcm.engine.cognitive.api.CognitiveErrorCode;
import com.chinacreator.gzcm.engine.cognitive.api.framework.CognitiveFrameworkDefinition;
import com.chinacreator.gzcm.engine.cognitive.api.framework.CognitiveFrameworkStatus;
import com.chinacreator.gzcm.engine.cognitive2.dao.CognitiveFrameworkDao;
import com.chinacreator.gzcm.engine.cognitive2.service.scenario.CognitiveGuardException;
import com.chinacreator.gzcm.common.event.KafkaTopics;
import com.chinacreator.gzcm.runtime.eventbus.EventBusService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * 认知框架注册表服务（详细设计-认知引擎 v1.0 §5/§9.1；CE-03）。
 *
 * <p>三条硬约束：</p>
 * <ol>
 *   <li>同 {@code frameworkId + version} 只允许一行（重复注册 409）；</li>
 *   <li>PUBLISHED 版本不可原地修改：本服务不提供内容 UPDATE，修复只能注册新版本；</li>
 *   <li>发布门：必须契约可解析（input/output schema + steps 齐备）且治理段未声明允许直接副作用。</li>
 * </ol>
 *
 * <p>框架定义是过程治理元数据，不是业务事实；不落 K 类知识、不落任何金额/业务结果。</p>
 */
@Service
public class CognitiveFrameworkRegistryService {

    private static final Logger log = LoggerFactory.getLogger(CognitiveFrameworkRegistryService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    /** 受控执行器白名单（§5.3：YAML executor_type 不是任意代码容器）。 */
    static final java.util.Set<String> ALLOWED_EXECUTORS = java.util.Set.of(
            "controlled_reasoning", "deterministic_tool", "llm_assisted_readonly", "human_review");

    private final CognitiveFrameworkDao dao;

    @Autowired(required = false)
    private ObjectProvider<EventBusService> eventBusProvider;

    public CognitiveFrameworkRegistryService(CognitiveFrameworkDao dao) {
        this.dao = dao;
    }

    /** 列表：可按 problemType/domain/status 过滤；非法 status fail-loud 400。 */
    public List<CognitiveFrameworkDefinition> list(String problemType, String domain, String status) {
        String normalized = null;
        if (status != null && !status.isBlank()) {
            CognitiveFrameworkStatus parsed = CognitiveFrameworkStatus.fromValue(status);
            if (parsed == null) {
                throw new CognitiveGuardException(CognitiveErrorCode.E400_INVALID_PARAM,
                        CognitiveErrorCode.MSG_INVALID_PARAM, "status=" + status);
            }
            normalized = parsed.name();
        }
        return dao.list(problemType, domain, normalized);
    }

    /** 可用框架（仅 PUBLISHED）——策略选择的候选池。 */
    public List<CognitiveFrameworkDefinition> listSelectable(String problemType, String domain) {
        return dao.list(problemType, domain, CognitiveFrameworkStatus.PUBLISHED.name());
    }

    public CognitiveFrameworkDefinition get(String frameworkId, String version) {
        return dao.find(frameworkId, version)
                .orElseThrow(() -> new CognitiveGuardException(CognitiveErrorCode.E404_MODEL_ROW,
                        "框架版本不存在: " + frameworkId + "@" + version, frameworkId));
    }

    /** 注册 DRAFT 版本；同版本已存在 ⇒ 409（版本不可变，改内容须新版本）。 */
    public CognitiveFrameworkDefinition register(Map<String, Object> body, String actor) {
        CognitiveFrameworkDefinition d = parse(body);
        validateForRegistration(d);
        if (dao.exists(d.getFrameworkId(), d.getVersion())) {
            throw new CognitiveGuardException(CognitiveErrorCode.E409_STATE_ILLEGAL,
                    "框架版本已存在（版本不可变，请注册新版本）: "
                            + d.getFrameworkId() + "@" + d.getVersion());
        }
        d.setStatus(CognitiveFrameworkStatus.DRAFT.name());
        d.setCreatedBy(actor);
        d.setCreatedAt(Instant.now().toString());
        d.setContentHash(sha256(canonical(d)));
        dao.insert(d, canonical(d));
        audit("FRAMEWORK_REGISTERED", d, actor, null);
        log.info("认知框架注册: {}@{} status=DRAFT", d.getFrameworkId(), d.getVersion());
        return d;
    }

    /** 发布：DRAFT/VALIDATING → PUBLISHED；已发布再发布 ⇒ 409。 */
    public CognitiveFrameworkDefinition publish(String frameworkId, String version, String actor) {
        CognitiveFrameworkDefinition current = get(frameworkId, version);
        CognitiveFrameworkStatus status = CognitiveFrameworkStatus.fromValue(current.getStatus());
        if (status == null) {
            throw new CognitiveGuardException(CognitiveErrorCode.E409_STATE_ILLEGAL,
                    "框架状态不可识别: " + current.getStatus());
        }
        if (status == CognitiveFrameworkStatus.PUBLISHED) {
            throw new CognitiveGuardException(CognitiveErrorCode.E409_MODEL_IMMUTABLE,
                    "框架版本已发布且不可原地修改: " + frameworkId + "@" + version);
        }
        if (status != CognitiveFrameworkStatus.DRAFT && status != CognitiveFrameworkStatus.VALIDATING) {
            throw new CognitiveGuardException(CognitiveErrorCode.E409_STATE_ILLEGAL,
                    "框架状态不允许发布: " + status);
        }
        requirePublishable(current);
        int n = dao.publish(frameworkId, version, actor);
        if (n == 0) {
            throw new CognitiveGuardException(CognitiveErrorCode.E409_STATE_ILLEGAL,
                    "框架发布并发冲突或状态已变化: " + frameworkId + "@" + version);
        }
        audit("FRAMEWORK_PUBLISHED", current, actor, null);
        log.info("认知框架发布: {}@{}", frameworkId, version);
        return get(frameworkId, version);
    }

    /** 治理迁移：DRAFT→VALIDATING；PUBLISHED→DEPRECATED/DISABLED；DEPRECATED→DISABLED。 */
    public CognitiveFrameworkDefinition transition(String frameworkId, String version,
                                                   String targetRaw, String actor) {
        CognitiveFrameworkDefinition current = get(frameworkId, version);
        CognitiveFrameworkStatus from = CognitiveFrameworkStatus.fromValue(current.getStatus());
        CognitiveFrameworkStatus to = CognitiveFrameworkStatus.fromValue(targetRaw);
        if (from == null || to == null) {
            throw new CognitiveGuardException(CognitiveErrorCode.E400_INVALID_PARAM,
                    CognitiveErrorCode.MSG_INVALID_PARAM, "targetStatus=" + targetRaw);
        }
        boolean legal = (from == CognitiveFrameworkStatus.DRAFT && to == CognitiveFrameworkStatus.VALIDATING)
                || (from == CognitiveFrameworkStatus.PUBLISHED
                    && (to == CognitiveFrameworkStatus.DEPRECATED || to == CognitiveFrameworkStatus.DISABLED))
                || (from == CognitiveFrameworkStatus.DEPRECATED && to == CognitiveFrameworkStatus.DISABLED);
        if (!legal || from.terminal()) {
            throw new CognitiveGuardException(CognitiveErrorCode.E409_STATE_ILLEGAL,
                    "非法框架状态迁移: " + from + " -> " + to);
        }
        int n = dao.updateStatus(frameworkId, version, to.name(), actor);
        if (n == 0) {
            throw new CognitiveGuardException(CognitiveErrorCode.E409_STATE_ILLEGAL,
                    "框架状态迁移并发冲突: " + frameworkId + "@" + version);
        }
        audit("FRAMEWORK_STATUS_CHANGED", current, actor, to.name());
        return get(frameworkId, version);
    }

    // ── 辅助 ─────────────────────────────────────────────

    private static void validateForRegistration(CognitiveFrameworkDefinition d) {
        List<String> missing = new ArrayList<>();
        if (isBlank(d.getFrameworkId())) missing.add("frameworkId");
        if (isBlank(d.getVersion())) missing.add("version");
        if (isBlank(d.getName())) missing.add("name");
        if (isBlank(d.getCategory())) missing.add("category");
        if (d.getSteps() == null || d.getSteps().isEmpty()) missing.add("steps");
        if (!missing.isEmpty()) {
            throw new CognitiveGuardException(CognitiveErrorCode.E400_INVALID_PARAM,
                    CognitiveErrorCode.MSG_INVALID_PARAM, String.join(",", missing));
        }
        for (Map<String, Object> step : d.getSteps()) {
            Object executor = step == null ? null : step.get("executorType");
            if (executor != null && !ALLOWED_EXECUTORS.contains(String.valueOf(executor))) {
                throw new CognitiveGuardException(CognitiveErrorCode.E400_INVALID_PARAM,
                        CognitiveErrorCode.MSG_INVALID_PARAM, "executorType=" + executor);
            }
        }
    }

    private static void requirePublishable(CognitiveFrameworkDefinition d) {
        List<String> missing = new ArrayList<>();
        if (isBlank(d.getInputSchemaRef())) missing.add("inputSchemaRef");
        if (isBlank(d.getOutputSchemaRef())) missing.add("outputSchemaRef");
        if (!d.isContractResolvable()) missing.add("contractResolvable");
        Object sideEffects = d.getGovernance() == null ? null : d.getGovernance().get("allowDirectSideEffects");
        if (Boolean.TRUE.equals(sideEffects)) missing.add("allowDirectSideEffects=true(禁止)");
        if (!missing.isEmpty()) {
            throw new CognitiveGuardException(CognitiveErrorCode.E422_GATES_NOT_PASSED,
                    CognitiveErrorCode.MSG_GATES_NOT_PASSED, String.join(",", missing));
        }
    }

    private static CognitiveFrameworkDefinition parse(Map<String, Object> body) {
        if (body == null) {
            throw new CognitiveGuardException(CognitiveErrorCode.E400_INVALID_PARAM,
                    CognitiveErrorCode.MSG_INVALID_PARAM, "body");
        }
        try {
            return MAPPER.convertValue(body, CognitiveFrameworkDefinition.class);
        } catch (Exception e) {
            throw new CognitiveGuardException(CognitiveErrorCode.E400_INVALID_PARAM,
                    CognitiveErrorCode.MSG_INVALID_PARAM, "body");
        }
    }

    /** 规范化 JSON（键排序、去时间/展示字段），保证同一内容稳定哈希。 */
    static String canonical(CognitiveFrameworkDefinition d) {
        Map<String, Object> m = new TreeMap<>();
        m.put("frameworkId", d.getFrameworkId());
        m.put("version", d.getVersion());
        m.put("name", d.getName());
        m.put("category", d.getCategory());
        m.put("purpose", d.getPurpose());
        m.put("problemTypes", sorted(d.getProblemTypes()));
        m.put("domains", sorted(d.getDomains()));
        m.put("preconditions", sorted(d.getPreconditions()));
        m.put("inputSchemaRef", d.getInputSchemaRef());
        m.put("outputSchemaRef", d.getOutputSchemaRef());
        m.put("steps", d.getSteps());
        m.put("methods", d.getMethods() == null ? List.of() : d.getMethods());
        m.put("requiredCapabilities", sorted(d.getRequiredCapabilities()));
        m.put("validationProfileRef", d.getValidationProfileRef());
        m.put("budgetPolicyRef", d.getBudgetPolicyRef());
        m.put("stopConditions", sorted(d.getStopConditions()));
        m.put("governance", d.getGovernance());
        try {
            return MAPPER.writeValueAsString(m);
        } catch (Exception e) {
            throw new IllegalStateException("framework canonical serialize failed", e);
        }
    }

    private static List<String> sorted(List<String> in) {
        if (in == null) {
            return List.of();
        }
        List<String> copy = new ArrayList<>(in);
        copy.sort(String::compareTo);
        return copy;
    }

    static String sha256(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder("sha256:");
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("sha256 unavailable", e);
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private void audit(String action, CognitiveFrameworkDefinition d, String actor, String targetStatus) {
        EventBusService bus = eventBusProvider == null ? null : eventBusProvider.getIfAvailable();
        if (bus == null) {
            log.warn("EventBusService 未装配，框架审计转本地日志: action={}", action);
            return;
        }
        try {
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("eventType", "COGNITIVE_FRAMEWORK_" + action);
            event.put("action", action);
            event.put("frameworkId", d.getFrameworkId());
            event.put("frameworkVersion", d.getVersion());
            event.put("actor", actor);
            event.put("targetStatus", targetStatus);
            event.put("traceId", com.chinacreator.gzcm.common.trace.EcosTraceContext.currentTraceId());
            event.put("occurredAt", Instant.now().toString());
            bus.publish(KafkaTopics.AUDIT, event);
        } catch (Exception e) {
            log.warn("框架审计事件发送失败: action={} err={}", action, e.getMessage());
        }
    }
}
