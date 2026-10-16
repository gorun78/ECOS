package com.chinacreator.gzcm.engine.ai.guardrail;

import com.chinacreator.gzcm.common.security.decision.DataScopeContract;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 工具裁决请求 —— PRD-06 §1.3 的四段载荷（subject / action / resource / environment）。
 *
 * <p>详细设计-06 F06-02（REQ-AI-01）。契约放在 {@code ai-engine-api}，供 cognitive / workspace
 * 复用同一载荷形态，禁止各引擎自拼 Map（拼错一段即"无裁决记录的工具调用"）。</p>
 *
 * <p><b>单一构造器</b>：四段缺一即编译不过，防止漏段。{@link Subject} 只能由服务端 token
 * 上下文构造（{@code null} 主体在咽喉处即 FAIL_CLOSED），{@link Resource} 只能经
 * {@link #projectResource(Map)} 从白名单字段投影 —— LLM 自报的任意入参不得进入载荷。</p>
 */
public final class ToolAdjudicationRequest {

    /** action.type 固定值（PRD §1.3 逐字） */
    public static final String ACTION_TYPE = "TOOL_CALL";

    /** environment.channel 固定值：Agent 工具调用通道 */
    public static final String CHANNEL_AGENT = "AGENT";

    /** resource 允许投影的工具入参字段（白名单；其余字段一律不入载荷） */
    private static final Set<String> RESOURCE_ARG_WHITELIST = Set.of(
            "scenarioId", "forecastRunId", DataScopeContract.KEY_PROJECT_IDS, "periods",
            "caliberId", "objectId");

    /** 主体段：只来自服务端 token 上下文 */
    public record Subject(String userId, List<String> roles, String department, String tenantId) {
    }

    /** 动作段 */
    public record Action(String type, String tool, String operation) {
    }

    /** 资源段：工具入参白名单字段投影，禁止整段 LLM 参数 */
    public record Resource(String scenarioId, String forecastRunId, String caliberId,
                           String objectId, List<String> projectIds, List<String> periods) {
    }

    /** 环境段 */
    public record Environment(String traceId, String channel, String timestamp) {
    }

    private final Subject subject;
    private final Action action;
    private final Resource resource;
    private final Environment environment;

    /** 唯一构造器（四段齐备） */
    public ToolAdjudicationRequest(Subject subject, Action action, Resource resource,
                                   Environment environment) {
        this.subject = subject;
        this.action = action;
        this.resource = resource;
        this.environment = environment;
    }

    public Subject subject() { return subject; }
    public Action action() { return action; }
    public Resource resource() { return resource; }
    public Environment environment() { return environment; }

    public String tool() { return action == null ? null : action.tool(); }

    /**
     * security-engine {@code policy-engine/evaluate} 的 input 段（四段逐字下发）。
     *
     * <p>只放结构化字段，禁放 SQL / 工具入参原文（防参数注入式越权与审计面扩敏）。</p>
     */
    public Map<String, Object> toEvaluateInput() {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("action", ACTION_TYPE + ":" + (action == null ? "?" : action.tool()));
        input.put("type", ACTION_TYPE);
        input.put("tool", action == null ? null : action.tool());
        input.put("operation", action == null ? null : action.operation());
        input.put("subject", subjectMap());
        input.put("resource", resourceMap());
        input.put("environment", environmentMap());
        return input;
    }

    private Map<String, Object> subjectMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        if (subject == null) return m;
        m.put("userId", subject.userId());
        m.put("roles", subject.roles() == null ? List.of() : subject.roles());
        m.put("department", subject.department());
        m.put("tenantId", subject.tenantId());
        return m;
    }

    /**
     * 资源段写出：{@code dataScope} 的键面<b>委托</b> {@link DataScopeContract} 单源
     * （#142/B4-3d-3，D-B4-20 的第二套字面量消除）。
     *
     * <p><b>钉现状（非认可，差距 D-B4-29）</b>：本段只声明 {@code projectIds}（与策略不读的
     * {@code periods}），<b>从不声明</b> {@code departmentId} —— {@link Resource} 里就没有这个字段，
     * 工具入参白名单里也没有。后果是 §4.7.2 的 AI p1「域内 ALLOW」在生产工具形态下
     * <b>不可达</b>：策略侧 {@code requested_department} 取默认 {@code ""}，与已解析的
     * {@code subject_department} 不等 ⇒ 落 {@code AI_SCOPE_OUT_OF_SUBJECT_DEPARTMENT}，
     * 明明域内却按越权拒。本批刻意<b>不</b>顺手补该字段：补了就得同时给"声明域从哪来"
     * 定单源（场景上下文 or 主体所属域），否则等于把一条未裁决的语义写进生产载荷。
     * 有 {@code ToolPayloadDataScopeNailTest} 的一条特征化用例在此钉住，改动必与裁决同批。</p>
     */
    private Map<String, Object> resourceMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        if (resource == null) return m;
        if (resource.scenarioId() != null) m.put("scenarioId", resource.scenarioId());
        if (resource.forecastRunId() != null) m.put("forecastRunId", resource.forecastRunId());
        if (resource.caliberId() != null) m.put("caliberId", resource.caliberId());
        if (resource.objectId() != null) m.put("objectId", resource.objectId());
        Map<String, Object> scope = new LinkedHashMap<>();
        if (resource.projectIds() != null && !resource.projectIds().isEmpty()) {
            scope.put(DataScopeContract.KEY_PROJECT_IDS, resource.projectIds());
        }
        if (resource.periods() != null && !resource.periods().isEmpty()) {
            scope.put("periods", resource.periods());
        }
        if (!scope.isEmpty()) m.put(DataScopeContract.KEY_DATA_SCOPE, scope);
        return m;
    }

    private Map<String, Object> environmentMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        if (environment == null) return m;
        m.put("traceId", environment.traceId());
        m.put("channel", environment.channel());
        m.put("timestamp", environment.timestamp());
        return m;
    }

    /**
     * 从工具入参投影 resource 段：只取 {@link #RESOURCE_ARG_WHITELIST} 内的字段，
     * 其余（含 {@code sql}/{@code prompt}/{@code command} 等 LLM 直供文本）一律丢弃。
     */
    public static Resource projectResource(Map<String, Object> arguments) {
        Map<String, Object> kept = new LinkedHashMap<>();
        if (arguments != null) {
            for (String key : RESOURCE_ARG_WHITELIST) {
                Object v = arguments.get(key);
                if (v != null) kept.put(key, v);
            }
        }
        return new Resource(
                asText(kept.get("scenarioId")),
                asText(kept.get("forecastRunId")),
                asText(kept.get("caliberId")),
                asText(kept.get("objectId")),
                asTextList(kept.get(DataScopeContract.KEY_PROJECT_IDS)),
                asTextList(kept.get("periods")));
    }

    /** 白名单字段集（供验收断言与越权用例引用） */
    public static Set<String> resourceArgWhitelist() {
        return RESOURCE_ARG_WHITELIST;
    }

    private static String asText(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    private static List<String> asTextList(Object v) {
        if (v == null) return null;
        List<String> out = new ArrayList<>();
        if (v instanceof Collection<?> c) {
            for (Object o : c) {
                if (o != null) out.add(String.valueOf(o));
            }
        } else {
            out.add(String.valueOf(v));
        }
        return out;
    }

    /** 归一化主体角色集合（去重、去空） */
    public static Set<String> normalizedRoles(Subject subject) {
        Set<String> roles = new LinkedHashSet<>();
        if (subject != null && subject.roles() != null) {
            for (String r : subject.roles()) {
                if (r != null && !r.isBlank()) roles.add(r.trim());
            }
        }
        return roles;
    }
}
