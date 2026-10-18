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
     * <p><b>不声明 {@code departmentId} 是授权边界，不是待补的缺口（#142/B4-3d-11 订正
     * D-B4-29 的定性，可达性缺口另号为 D-B4-36）</b>：{@link Resource} 没有该字段、
     * 工具入参白名单也不含它 —— 而 SQL 类工具的执行腿
     * （{@code ToolExecutorService#executeSql} → {@code queryForList}）没有任何 RLS 行级下推，
     * 只有只读地板 + 表白名单 + CLS 列面。于是策略里的「声明域 vs 主体域」比对，
     * 在这条通道上是<b>唯一的行范围闸</b>。若把它放进调用方（= LLM 可控）的白名单，
     * 等于让被裁决者自己申报裁决依据 ⇒ 一行 {@code "departmentId": "<任意域>"} 即可越域。
     * 因此本字段<b>只允许由服务端真值解析产出</b>（前置 D-B4-30 裁决腿并入 {@code /decision}、
     * D-B4-36 声明域单源落地），且落地时须由该批把白名单的来源约束一起写明。</p>
     *
     * <p><b>后果（如实登记，非认可）</b>：§4.7.2 的 AI p1「域内 ALLOW」在生产工具形态下暂不可达；
     * 策略侧自 B4-3d-11 起落的是诚实理由码 {@code AI_DEPARTMENT_NOT_DECLARED}
     * （旧形态误标为横向越权 {@code AI_SCOPE_OUT_OF_SUBJECT_DEPARTMENT}）。
     * 有 {@code ToolPayloadDataScopeNailTest} 两条钉守着：白名单加成员即刻变红，
     * 调用方自报字段必须被丢弃。</p>
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
