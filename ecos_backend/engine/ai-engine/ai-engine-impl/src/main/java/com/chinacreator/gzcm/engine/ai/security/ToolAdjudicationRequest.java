package com.chinacreator.gzcm.engine.ai.security;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * F06-02（PMO-74 W144，分册 06 详细设计-06 §F06-02）— 咽喉 {@code policyEvaluate}
 * 阶段向 security-engine ABAC 端点投递的<b>四段裁决载荷</b>
 * （{@code subject} / {@code action} / {@code resource} / {@code environment}）。
 *
 * <p>本类为裁决闸 {@link AgentToolPolicyGate} 的载荷构造器，落 impl {@code security}
 * 语义包（与闸同域，非跨引擎契约 —— 未落 api 因 api 模块无测试设施且其 AGENTS.md
 * 禁业务逻辑类；如 cognitive/workspace 日后需复用，届时再提升并补契约）。</p>
 *
 * <p>承载四个受控段：</p>
 * <ol>
 *   <li>{@link Subject}：{@code {userId, roles[], department}} — 只能由调用方
 *       从 <b>token 上下文</b>（{@code UserContext}/{@code SecurityContext}）取后传入；
 *       <b>不得</b>接受 LLM/工具入参自报（PMO-74 H9-T1）。</li>
 *   <li>{@link Action}：{@code {type=TOOL_CALL, tool, operation=read|write}}。</li>
 *   <li>{@link Resource}：{@code {scenarioId?, forecastRunId?, dataScope{projectIds[], periods[]}}}
 *       — <b>只对 LLM 入参按白名单投影</b>；未在白名单内的键一律丢弃（防参数注入式越权，
 *       X-20）。</li>
 *   <li>{@link Environment}：{@code {traceId, channel=AGENT, timestamp}} —
 *       {@code channel} 恒 {@code "AGENT"}（不接受调用方覆盖）；
 *       {@code timestamp} 服务端生成（不接受调用方注入）。</li>
 * </ol>
 *
 * <p>单入口：</p>
 * <pre>{@code
 * ToolAdjudicationRequest req = ToolAdjudicationRequest.fromServerSide(
 *     userId, roles, department,   // ← 调用方从 token 上下文取
 *     "query_db", ActionKind.READ,
 *     MDC.get("traceId"),
 *     llmArgs);                    // ← LLM 入参，仅白名单字段投影
 * }</pre>
 *
 * <p><b>不变式</b>（由 {@code ToolAdjudicationPayloadTest} 锁）：</p>
 * <ul>
 *   <li>I-P1：{@code subject.userId} 非 null/blank/anonymous —— 否则 {@code IllegalStateException}；</li>
 *   <li>I-P2：{@code environment.channel == "AGENT"}（<b>任何</b>调用方覆盖尝试无效）；</li>
 *   <li>I-P3：{@code environment.timestamp} 由服务端 {@link Instant} 填，调用方无法注入；</li>
 *   <li>I-P4：{@code llmArgs} 里出现的非白名单键（如
 *       {@code userId}/{@code department}/{@code channel}/{@code role}/{@code tenantId} 等）
 *       <b>不出现在</b> {@link #toWireBody()} 任何字段，防 <i>parameter-injection escape</i>。</li>
 * </ul>
 */
public final class ToolAdjudicationRequest {

    // ── 常量 ─────────────────────────────────────────────────────────────

    /** action.type 唯一取值（工具调用裁决） */
    public static final String ACTION_TYPE_TOOL_CALL = "TOOL_CALL";

    /** environment.channel 唯一取值（本裁判决一定位为 AGENT，不接受调用方覆盖） */
    public static final String CHANNEL_AGENT = "AGENT";

    // ── 操作模态 ─────────────────────────────────────────────────────────

    /** action.operation 取值闭集（一无其它）。未识别 → 归 read（禁默认写越权）。 */
    public enum ActionKind { READ, WRITE }

    /**
     * LLM 入参 → resource 段投影白名单（唯一切片，闭集）。
     * 遵循"少即是多"：非此 5 键的 LLM 参数一律不进载荷。
     */
    public static final Set<String> RESOURCE_WHITELIST;
    static {
        Set<String> s = new LinkedHashSet<>();
        s.add("scenario_id");
        s.add("forecast_run_id");
        s.add("data_scope");
        s.add("project_ids");
        s.add("periods");
        RESOURCE_WHITELIST = Set.copyOf(s);
    }

    // ── 四段（不可变记录） ──────────────────────────────────────────────

    /** subject — 由调用方从 token 上下文取，禁自报 */
    public record Subject(String userId, List<String> roles, String department) {}

    /** action — 工具调用裁决固定 type=TOOL_CALL */
    public record Action(String type, String tool, String operation) {}

    /** resource — LLM 入参白名单投影 */
    public record Resource(String scenarioId, String forecastRunId, DataScope dataScope) {}

    /** resource.dataScope — 项目/周期维度 */
    public record DataScope(List<String> projectIds, List<String> periods) {}

    /** environment — traceId + channel=AGENT + 服务端 timestamp */
    public record Environment(String traceId, String channel, String timestamp) {}

    // ── 字段 ─────────────────────────────────────────────────────────────

    private final Subject subject;
    private final Action action;
    private final Resource resource;
    private final Environment environment;

    private ToolAdjudicationRequest(Subject subject, Action action,
                                    Resource resource, Environment environment) {
        this.subject = subject;
        this.action = action;
        this.resource = resource;
        this.environment = environment;
    }

    // ── 单一入口 ────────────────────────────────────────────────────────

    /**
     * 服务端构造唯一入口。
     *
     * @param userId     主体 —— 调用方从 token 上下文取（UserContext/SecurityContext）
     * @param roles      主体角色（可空 → 空列表）
     * @param department 主体部门（可空 → ""）
     * @param tool       工具名（action.tool）
     * @param op         操作模态（READ/WRITE；null → READ）
     * @param traceId    MDC traceId（可空 → ""，由调用方从 MDC/透传头取，禁 LLM 自报）
     * @param llmArgs    LLM 入参（<b>非信任来源</b>，仅按 {@link #RESOURCE_WHITELIST} 投影）
     * @throws IllegalStateException subject.userId 为 null/blank/"anonymous"（无法归属主体，
     *         呼应 F06-01 咽喉的无 token 主体拒绝；本载荷构造阶段先拒）
     */
    public static ToolAdjudicationRequest fromServerSide(
            String userId,
            List<String> roles,
            String department,
            String tool,
            ActionKind op,
            String traceId,
            Map<String, Object> llmArgs) {

        // I-P1：subject 必须是有效 token 主体（禁匿名/自报）
        if (userId == null || userId.isBlank() || "anonymous".equalsIgnoreCase(userId)
                || "anonymousUser".equals(userId)) {
            throw new IllegalStateException(
                    "[F06-02] ToolAdjudicationRequest: subject.userId 必须来自 token 上下文"
                            + "（非空且非 anonymous），拒绝构造: userId=" + userId);
        }
        if (tool == null || tool.isBlank()) {
            throw new IllegalStateException("[F06-02] action.tool 必填（禁止空工具裁决）");
        }

        Subject subject = new Subject(
                userId,
                List.copyOf(roles != null ? roles : List.of()),
                department != null ? department : "");
        Action action = new Action(ACTION_TYPE_TOOL_CALL, tool,
                (op == null || op == ActionKind.WRITE) ? "write" : "read");
        Resource resource = projectResource(llmArgs);
        Environment environment = new Environment(
                traceId != null ? traceId : "",
                CHANNEL_AGENT,
                Instant.now().toString());

        return new ToolAdjudicationRequest(subject, action, resource, environment);
    }

    /**
     * LLM 入参白名单投影（I-P4）：仅 5 键映射到 resource，其余一律丢弃。
     * 关键：<b>不</b>接受 {@code userId/department/role/tenantId/channel} 等自报键，
     * 即使 LLM 明写也不会进载荷（防参数注入式越权）。
     */
    private static Resource projectResource(Map<String, Object> llmArgs) {
        if (llmArgs == null || llmArgs.isEmpty()) {
            return new Resource(null, null, new DataScope(List.of(), List.of()));
        }
        String scenarioId = strOrNull(llmArgs.get("scenario_id"));
        String forecastRunId = strOrNull(llmArgs.get("forecast_run_id"));

        // dataScope：兼容两种形态 —— {data_scope:{project_ids:[..], periods:[..]}}
        // 或扁平 {project_ids:[..], periods:[..]}
        List<String> projectIds = List.of();
        List<String> periods = List.of();
        Object ds = llmArgs.get("data_scope");
        if (ds instanceof Map<?, ?> m) {
            projectIds = strList(m.get("project_ids"));
            periods = strList(m.get("periods"));
        } else {
            projectIds = strList(llmArgs.get("project_ids"));
            periods = strList(llmArgs.get("periods"));
        }
        DataScope dataScope = new DataScope(projectIds, periods);
        return new Resource(scenarioId, forecastRunId, dataScope);
    }

    private static String strOrNull(Object o) {
        return o instanceof String s && !s.isBlank() ? s : null;
    }

    private static List<String> strList(Object o) {
        if (!(o instanceof List<?> list)) return List.of();
        List<String> out = new ArrayList<>(list.size());
        for (Object v : list) {
            if (v == null) continue;
            String s = String.valueOf(v);
            if (!s.isBlank()) out.add(s);
        }
        return List.copyOf(out);
    }

    // ── Getters ─────────────────────────────────────────────────────────

    public Subject getSubject()       { return subject; }
    public Action getAction()         { return action; }
    public Resource getResource()     { return resource; }
    public Environment getEnvironment() { return environment; }

    // ── toWireBody：送 policy-engine 的 body 结构 ───────────────────────

    /**
     * 生成 policy-engine {@code POST /api/v1/security/policy-engine/evaluate}
     * 请求体的 {@code input} 段（不含 policy/主体 envelope；envelope 由
     * {@link AgentToolPolicyGate} 拼）。
     *
     * <p>键名顺序固定（LinkedHashMap）便于 review；键名与 F06-02 四段命名对齐。
     * I-P2/I-P4：channel 恒 AGENT、非白名单 LLM 键不入此 body。</p>
     */
    public Map<String, Object> toWireBody() {
        Map<String, Object> m = new LinkedHashMap<>();
        Map<String, Object> subj = new LinkedHashMap<>();
        subj.put("userId", subject.userId());
        subj.put("roles", subject.roles());
        subj.put("department", subject.department());
        m.put("subject", subj);

        m.put("action", Map.of(
                "type", action.type(),
                "tool", action.tool(),
                "operation", action.operation()));

        Map<String, Object> res = new LinkedHashMap<>();
        if (resource.scenarioId() != null) res.put("scenarioId", resource.scenarioId());
        if (resource.forecastRunId() != null) res.put("forecastRunId", resource.forecastRunId());
        res.put("dataScope", Map.of(
                "projectIds", resource.dataScope().projectIds(),
                "periods", resource.dataScope().periods()));
        m.put("resource", res);

        m.put("environment", new LinkedHashMap<>(Map.of(
                "traceId", environment.traceId(),
                "channel", environment.channel(),
                "timestamp", environment.timestamp())));
        return m;
    }
}
