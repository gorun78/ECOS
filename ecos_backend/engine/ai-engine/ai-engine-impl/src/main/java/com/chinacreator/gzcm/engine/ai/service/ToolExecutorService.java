package com.chinacreator.gzcm.engine.ai.service;

import com.chinacreator.gzcm.common.error.AiError;
import com.chinacreator.gzcm.common.error.AiErrorClass;
import com.chinacreator.gzcm.engine.ai.dao.ToolDao;
import com.chinacreator.gzcm.engine.ai.guardrail.ToolAdjudicationRequest;
import com.chinacreator.gzcm.engine.ai.guardrail.ToolDecision;
import com.chinacreator.gzcm.engine.ai.guardrail.ToolObligations;
import com.chinacreator.gzcm.engine.ai.security.AgentToolPolicyGate;
import com.chinacreator.gzcm.engine.ai.security.AgentToolSqlWhitelist;
import com.chinacreator.gzcm.engine.ai.security.ToolObligationApplier;
import com.chinacreator.gzcm.engine.ai.security.ToolSubjectResolver;
import com.chinacreator.gzcm.engine.ai.tool.ScenarioToolCatalog;
import com.chinacreator.gzcm.engine.ai.tool.ScenarioToolDefinition;
import com.chinacreator.gzcm.engine.ai.tool.ScenarioToolRegistry;
import com.chinacreator.gzcm.engine.ai.tool.ToolCitation;
import com.chinacreator.gzcm.engine.ai.trace.AiTraceSupport;
import com.chinacreator.gzcm.common.trace.EcosTraceContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.Method;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;

/**
 * 工具执行器 —— Agent 工具调用的<b>唯一事实咽喉</b>（详细设计-06 F06-01）。
 *
 * <p>阶段链固定为
 * {@code execute() → policyEvaluate() → sandboxReview() → dispatch() → obligationsApply()}：</p>
 * <ol>
 *   <li>{@code policyEvaluate}：对<b>所有</b> toolType 与<b>所有</b>来源（注册表 7 工具 +
 *       DB/fallback 定义的 SQL/REST/BUILTIN + 场景工具）无条件执行一次且仅一次 security-engine
 *       ABAC 裁决（废止原 {@code "SQL".equalsIgnoreCase(toolType)} 前置条件，X-11）；</li>
 *   <li>{@code sandboxReview}：R1.3 高危沙盒，作为<b>附加</b>防御，自身异常一律 DENY
 *       而非放行（X-14）；</li>
 *   <li>{@code dispatch}：按 toolType 分发执行，未获 ALLOW 不会到达此处；</li>
 *   <li>{@code obligationsApply}：ALLOW 返回的 {@code mask[]/rowFilter} 必须在结果回 LLM
 *       <b>之前</b>经 security 侧执行落实，执行不可用则整条结果丢弃并 FAIL_CLOSED（F06-02 要点 2）。</li>
 * </ol>
 *
 * <p>三态语义（DENY ≠ FAIL_CLOSED，禁折叠）由 {@link AiErrorClass#GUARDRAIL_DENIED} /
 * {@link AiErrorClass#GUARDRAIL_FAIL_CLOSED} 承载，随 {@link ToolResult} 结构化字段上浮到
 * {@code AgentLoopService}（F06-14）。</p>
 */
@Service
public class ToolExecutorService {

    private static final Logger log = LoggerFactory.getLogger(ToolExecutorService.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final int TIMEOUT_SECONDS = 30;
    private static final int MAX_RESULT_LENGTH = 2000;

    /** 写类工具（裁决载荷 {@code action.operation} 判定用，PRD-06 §1.3 read|write） */
    private static final Set<String> WRITE_TOOL_NAMES = Set.of("write_file", "patch");

    private ToolDao toolDao;

    /**
     * F06-17 待收口项（X-24 计 3 处 {@code new RestTemplate()} 之一）：本实例仍是自建 REST 客户端，
     * 收口到 runtime-access 属 WP8 排期。<b>非 final</b> 是 F06-11 集成测试的注入面 ——
     * 场景工具的后端故障必须能替换出口，禁为此在生产路径加 mock 分支。
     */
    private RestTemplate restTemplate;
    private final Map<String, ToolDefinitionEntry> fallbackTools = new ConcurrentHashMap<>();

    // F06-11: 场景受控工具声明（无执行体，dispatch 仍回本咽喉，裁决次数恰一条不变）
    private ScenarioToolRegistry scenarioRegistry;

    /**
     * 场景工具后端的<b>服务端</b>基址（配置注入，禁取 LLM 传入的 URL —— 否则本通道退化成
     * 第二个 {@code invoke_rest}，F06-11 要点 3 的白名单收口即失效）。
     */
    private String scenarioBaseUrl = DEFAULT_SCENARIO_BASE_URL;

    /** 测试/装配用覆盖点（生产由 Spring 经 {@code service.scenario.base-url} 注入）。 */
    static final String DEFAULT_SCENARIO_BASE_URL = "http://localhost:18090";

    // R1.3: SecuritySandboxService 位于 security-engine-impl（编译期不可见），
    // 通过 ApplicationContext + 反射调用；不可用时高危指令按 fail-closed 拒绝
    @Autowired(required = false)
    private ApplicationContext applicationContext;

    /** R1.3: 高危指令正则 —— 附加防御，不构成放行依据，也不再是裁决触发条件（F06-01 要点 4） */
    private static final Pattern HIGH_RISK_PATTERN = Pattern.compile(
            "(?i).*(DELETE|DROP|TRUNCATE|ALTER\\s+TABLE|EXEC|RM\\s|MV\\s|UPDATE\\s+.*WHERE|GRANT|REVOKE).*");

    @Autowired(required = false)
    public void setToolDao(ToolDao toolDao) {
        this.toolDao = toolDao;
    }

    // 使用 @Lazy 打破 AgentDelegationService → AgentLoopService → ToolExecutorService 循环依赖
    @org.springframework.context.annotation.Lazy
    @Autowired(required = false)
    private AgentDelegationService agentDelegationService;

    /** 工具注册中心 — 优先走注册表，未命中时回退到 DB/fallback 逻辑 */
    @org.springframework.context.annotation.Lazy
    @Autowired
    private ToolRegistry toolRegistry;

    // F06-01/F06-02/F06-03: 工具裁决闸（原 AgentToolSqlSecurityGate 升格改名）。
    // 未装配 = 一切工具调用 FAIL_CLOSED（§2.4-6），不存在"闸缺失即放行"的路径。
    // setter 注入与本类 toolDao 既有风格一致（字段注入存量清单归 H11-T3 统一整改）。
    private AgentToolPolicyGate policyGate;
    private ToolSubjectResolver subjectResolver;
    private ToolObligationApplier obligationApplier;
    private AgentToolSqlWhitelist sqlWhitelist;

    @Autowired(required = false)
    public void setPolicyGate(AgentToolPolicyGate policyGate) {
        this.policyGate = policyGate;
    }

    @Autowired(required = false)
    public void setSubjectResolver(ToolSubjectResolver subjectResolver) {
        this.subjectResolver = subjectResolver;
    }

    @Autowired(required = false)
    public void setObligationApplier(ToolObligationApplier obligationApplier) {
        this.obligationApplier = obligationApplier;
    }

    @Autowired(required = false)
    public void setSqlWhitelist(AgentToolSqlWhitelist sqlWhitelist) {
        this.sqlWhitelist = sqlWhitelist;
    }

    /** F06-11：场景受控工具声明源；未装配 = 场景工具不可见（通用工具路径不受影响）。 */
    @Autowired(required = false)
    public void setScenarioRegistry(ScenarioToolRegistry scenarioRegistry) {
        this.scenarioRegistry = scenarioRegistry;
    }

    /** F06-11：场景工具后端基址（配置单源，默认本机 workspace :18090；未实现后端得到真实 502）。 */
    @Autowired(required = false)
    public void setScenarioBaseUrl(
            @org.springframework.beans.factory.annotation.Value(
                    "${service.scenario.base-url:" + DEFAULT_SCENARIO_BASE_URL + "}") String scenarioBaseUrl) {
        if (scenarioBaseUrl != null && !scenarioBaseUrl.isBlank()) {
            this.scenarioBaseUrl = scenarioBaseUrl.endsWith("/")
                    ? scenarioBaseUrl.substring(0, scenarioBaseUrl.length() - 1) : scenarioBaseUrl.trim();
        }
    }

    /** F06-11 集成测试注入面（替换 REST 出口以注入后端故障；生产路径不调用）。 */
    void setRestTemplate(RestTemplate restTemplate) {
        if (restTemplate != null) this.restTemplate = restTemplate;
    }

    public ToolExecutorService() {
        this.restTemplate = new RestTemplate();
        initFallbackTools();
    }

    // ── Public API ────────────────────────────────────────────────────────

    /**
     * 执行工具调用（通过 ToolCall 对象）。
     * 供 {@link AgentLoopService} 调用。
     */
    public ToolResult execute(ToolCall call) {
        return execute(call.getName(), call.getArguments(), call.getId());
    }

    /**
     * 执行工具调用。
     *
     * @param toolName  工具名称/code
     * @param arguments 调用参数
     * @return 工具执行结果
     */
    public ToolResult execute(String toolName, Map<String, Object> arguments) {
        return execute(toolName, arguments, null);
    }

    private ToolResult execute(String toolName, Map<String, Object> arguments, String callId) {
        long startNs = System.nanoTime();
        boolean viaRegistry = toolRegistry != null && toolRegistry.has(toolName);

        // registry 未命中才需要解析工具定义（DB 优先，回退内存 fallback）
        ToolDefinitionEntry def = viaRegistry ? null : loadToolDefinition(toolName);
        if (!viaRegistry && def == null) {
            // 工具不存在 → 不执行，也就没有"未裁决的执行"（§1.5-1 对账口径）
            // D.5：未注册工具属客户端可修正态（400 INVALID_ARGUMENT），禁记 502 后端故障
            return buildClassifiedError(callId, toolName, AiErrorClass.INVALID_ARGUMENT,
                    "工具未找到: " + toolName, (System.nanoTime() - startNs) / 1_000_000);
        }

        // ── 阶段 1: policyEvaluate —— 无条件、一次且仅一次（F06-01 要点 1） ──
        Adjudication adjudication = policyEvaluate(toolName, arguments, def, viaRegistry,
                callId, startNs);
        if (adjudication.rejected != null) {
            return adjudication.rejected;
        }
        ToolDecision decision = adjudication.decision;

        // ── 阶段 2: sandboxReview —— 附加防御，异常即 DENY（X-14） ──
        ToolResult sandboxBlock = sandboxReview(toolName, arguments, callId, startNs);
        if (sandboxBlock != null) {
            return sandboxBlock;
        }

        try {
            // ── 阶段 3: dispatch ──
            ToolResult raw = viaRegistry
                    ? toolRegistry.execute(toolName, arguments)
                    : executeResolved(def, arguments, callId, startNs);
            if (!raw.isSuccess()) {
                return raw;
            }

            // ── 阶段 4: obligationsApply —— 结果回 LLM 之前（F06-02 要点 2） ──
            return obligationsApply(toolName, raw, decision, adjudication.subject(), startNs);
        } catch (TimeoutException e) {
            long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;
            return buildClassifiedError(callId, toolName, AiErrorClass.TOOL_BACKEND_ERROR,
                    "工具执行超时 (" + TIMEOUT_SECONDS + "s)", elapsedMs);
        } catch (ScenarioArgumentsException badArgs) {
            // F06-11 入参地板：缺参属客户端可修正态（INVALID_ARGUMENT），不得伪装成后端故障（502）
            long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;
            log.info("F06-11 场景工具入参地板拒绝: tool={} reason={}", toolName, badArgs.getMessage());
            return buildClassifiedError(callId, toolName, AiErrorClass.INVALID_ARGUMENT,
                    badArgs.getMessage(), elapsedMs);
        } catch (Exception e) {
            // F06-15「捕后必分类」：异常原文只入日志，上浮面必须是结构化分类（TOOL_BACKEND_ERROR）
            long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;
            AiError error = ErrorCodeMapper.classify(e, ErrorCodeMapper.Context.tool(toolName),
                    AiTraceSupport.currentTraceId());
            log.error("Tool execution failed: tool={} class={} raw={} traceId={}",
                    toolName, error.errorClass().name(), error.rawReason(), error.traceId(), e);
            return buildClassifiedError(callId, toolName, error.errorClass(),
                    error.rawReason(), elapsedMs);
        }
    }

    // ── 阶段 1: policyEvaluate ────────────────────────────────────────────

    /** 裁决中间结果：{@code rejected} 非空即终止（DENY / FAIL_CLOSED），禁继续执行 */
    private record Adjudication(ToolResult rejected, ToolDecision decision,
                                ToolAdjudicationRequest.Subject subject) {

        static Adjudication reject(ToolResult result) {
            return new Adjudication(result, null, null);
        }

        static Adjudication allow(ToolDecision decision, ToolAdjudicationRequest.Subject subject) {
            return new Adjudication(null, decision, subject);
        }
    }

    /**
     * 全工具类型裁决（必须在调用方线程执行，以读取 token 上下文）。
     *
     * <p>主体只能来自服务端 token 上下文（{@link ToolSubjectResolver}），禁取入参自报值；
     * SQL 原文只进本地地板校验与摘要，<b>不入裁决载荷</b>（F06-02 要点 1）。</p>
     */
    private Adjudication policyEvaluate(String toolName, Map<String, Object> arguments,
                                        ToolDefinitionEntry def, boolean viaRegistry,
                                        String callId, long startNs) {
        if (policyGate == null) {
            log.warn("F06-01 工具裁决闸（AgentToolPolicyGate）未装配，拒绝执行（fail-closed）: tool={}", toolName);
            return Adjudication.reject(buildGuardrailError(callId, toolName, AiErrorClass.GUARDRAIL_FAIL_CLOSED,
                    "工具裁决闸不可用，拒绝执行（默认 DENY，fail-closed）", startNs));
        }
        if (subjectResolver == null) {
            log.warn("F06-02 主体解析器（ToolSubjectResolver）未装配，拒绝执行（fail-closed）: tool={}", toolName);
            return Adjudication.reject(buildGuardrailError(callId, toolName, AiErrorClass.GUARDRAIL_FAIL_CLOSED,
                    "裁决主体不可解析，拒绝执行（fail-closed）", startNs));
        }

        ToolAdjudicationRequest.Subject subject = subjectResolver.resolve();

        String sqlText = null;
        List<String> boundParams = List.of();
        if (viaRegistry) {
            Object sqlArg = arguments != null ? arguments.get("sql") : null;
            if (sqlArg instanceof String s && !s.isBlank()) sqlText = s;
        } else if (def != null && "SQL".equalsIgnoreCase(def.toolType)) {
            try {
                sqlText = resolveToolSql(def, arguments);
            } catch (IllegalArgumentException | IllegalStateException floor) {
                // schema 生成阶段的白名单拒绝 = 本地地板 DENY，不进入 dispatch
                log.info("F06-01 裁决阶段 SQL 白名单拒绝: tool={} reason={}", toolName, floor.getMessage());
                return Adjudication.reject(buildGuardrailError(callId, toolName,
                        AiErrorClass.GUARDRAIL_DENIED, "SQL 被本地白名单地板拒绝: " + floor.getMessage(),
                        startNs));
            }
            boundParams = extractBoundParamNames(def.schemaJson);
        }

        ToolAdjudicationRequest request = new ToolAdjudicationRequest(
                subject,
                new ToolAdjudicationRequest.Action(ToolAdjudicationRequest.ACTION_TYPE, toolName,
                        inferOperation(toolName, def, arguments)),
                ToolAdjudicationRequest.projectResource(arguments),
                new ToolAdjudicationRequest.Environment(AiTraceSupport.currentTraceId(),
                        ToolAdjudicationRequest.CHANNEL_AGENT, Instant.now().toString()));

        ToolDecision decision = policyGate.review(request, sqlText, boundParams);
        if (decision.allowed()) {
            return Adjudication.allow(decision, subject);
        }
        AiErrorClass errorClass = AgentToolPolicyGate.errorClassFor(decision);
        if (errorClass == null) errorClass = AiErrorClass.GUARDRAIL_DENIED;
        log.warn("F06-01 工具被裁决闸拒绝: tool={} user={} verdict={} reason={}",
                toolName, subject.userId(), decision.verdict(), decision.reason());
        return Adjudication.reject(buildGuardrailError(callId, toolName, errorClass,
                decision.reason(), startNs, decision));
    }

    /** {@code action.operation} 判定：场景工具取<b>声明值</b>，其余按写类名单与 HTTP 方法推断。 */
    private String inferOperation(String toolName, ToolDefinitionEntry def, Map<String, Object> arguments) {
        // F06-11 要点 1：read|write 是声明属性，禁按参数或方法猜测（否则同一次调用的裁决载荷会漂移）
        if (def != null && def.scenario != null) return def.scenario.operation();
        if (toolName != null && WRITE_TOOL_NAMES.contains(toolName)) return "write";
        if (def != null && def.httpMethod != null && !def.httpMethod.isBlank()) {
            String m = def.httpMethod.toUpperCase(Locale.ROOT);
            if (!m.startsWith("GET") && !m.startsWith("HEAD")) return "write";
        }
        Object methodArg = arguments != null ? arguments.get("method") : null;
        if (methodArg instanceof String s && !s.isBlank()) {
            String m = s.toUpperCase(Locale.ROOT);
            if (!m.startsWith("GET") && !m.startsWith("HEAD")) return "write";
        }
        return "read";
    }

    // ── 阶段 4: obligationsApply ──────────────────────────────────────────

    /**
     * ALLOW 义务在结果回 LLM <b>之前</b>落实；执行不可用整条结果丢弃并 FAIL_CLOSED
     * （禁"先返回再说明"，F06-02 要点 2）。
     */
    private ToolResult obligationsApply(String toolName, ToolResult raw, ToolDecision decision,
                                        ToolAdjudicationRequest.Subject subject, long startNs) {
        ToolObligations obligations = decision == null ? ToolObligations.none() : decision.obligations();
        if (obligations.isEmpty()) {
            return raw;
        }
        if (obligationApplier == null) {
            log.warn("F06-02 obligations 执行器未装配，整条结果丢弃（fail-closed）: tool={}", toolName);
            return buildGuardrailError(raw.getToolCallId(), toolName, AiErrorClass.GUARDRAIL_FAIL_CLOSED,
                    "security obligations 执行器不可用，结果已丢弃", startNs, decision);
        }
        ToolObligationApplier.Outcome outcome =
                obligationApplier.apply(toolName, raw.getContent(), obligations, subject);
        if (!outcome.applied()) {
            log.warn("F06-02 obligations 执行失败，整条结果丢弃（fail-closed）: tool={} reason={}",
                    toolName, outcome.failReason());
            return buildGuardrailError(raw.getToolCallId(), toolName, AiErrorClass.GUARDRAIL_FAIL_CLOSED,
                    "结果未按 security 义务脱敏/过滤，已丢弃: " + outcome.failReason(), startNs, decision);
        }
        long elapsedMs = raw.getElapsedMs();
        ToolResult applied = ToolResult.ok(raw.getToolCallId(), toolName,
                truncateSafe(outcome.json(), MAX_RESULT_LENGTH), elapsedMs);
        applied.setTraceId(AiTraceSupport.currentTraceId());
        applied.setDecision(decision.verdict().name());
        applied.setPolicyId(decision.policyId());
        return applied;
    }

    private String truncateSafe(String s, int max) {
        if (s == null) return null;
        return s.length() > max ? s.substring(0, max) + "...[truncated]" : s;
    }

    /** 阶段 3：注册表之外的 DB/fallback/场景声明工具执行（原 executeWithTimeout + 截断 + 成功包装）。 */
    private ToolResult executeResolved(ToolDefinitionEntry def, Map<String, Object> arguments,
                                       String callId, long startNs)
            throws TimeoutException, Exception {
        String result = executeWithTimeout(def, arguments);
        long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;
        if (result != null && result.length() > MAX_RESULT_LENGTH) {
            result = result.substring(0, MAX_RESULT_LENGTH) + "...[truncated]";
        }
        ToolResult ok = buildSuccess(callId, def.code, result, elapsedMs);
        ok.setTraceId(AiTraceSupport.currentTraceId());
        // F06-11 要点 4：场景工具返回体必须自带引用元数据 —— 缺元数据的数值一律丢弃（CITATION_MISSING），
        // 不允许"可用但无引用"的结果进 LLM（那正是 F06-12 后校验要拦的形态，早一阶段拦更省一次幻觉）
        return def.scenario == null ? ok : withCitation(ok, def, result);
    }

    /**
     * 引用元数据装配（F06-11 要点 4 → F06-12 逐字引用的上游保证）。
     *
     * <p>引用块由本类<b>确定性渲染</b>并附在结果尾部，LLM 只需逐字复制；元数据不全即判
     * {@code CITATION_MISSING}（422），禁在此造默认口径或补当前时间。</p>
     */
    private ToolResult withCitation(ToolResult ok, ToolDefinitionEntry def, String body) {
        Map<String, Object> parsed = parseJsonObject(body);
        ToolCitation citation = parsed == null ? null : ToolCitation.fromResult(parsed);
        if (citation == null || !citation.isComplete()) {
            List<String> missing = citation == null
                    ? List.of("forecastRunId", "caliberId", "version", "asOfTime")
                    : citation.missingFields();
            log.warn("F06-11 场景工具返回体缺少引用元数据，结果丢弃: tool={} missing={}",
                    def.code, missing);
            return buildClassifiedError(ok.getToolCallId(), def.code, AiErrorClass.CITATION_MISSING,
                    "工具返回缺少引用元数据: " + String.join(",", missing), ok.getElapsedMs());
        }
        String block = citation.render();
        ok.setCitationBlock(block);
        ok.setContent(ok.getContent() + "\n引用块（结论数值必须逐字附带，禁改写）: " + block);
        return ok;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJsonObject(String body) {
        if (body == null || body.isBlank()) return null;
        try {
            Object parsed = objectMapper.readValue(body, Object.class);
            return parsed instanceof Map ? (Map<String, Object>) parsed : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** 非护栏类结构化失败（F06-14 分类上浮；文案只出模板 + 诊断码，禁原始原因随响应体上浮）。 */
    private ToolResult buildClassifiedError(String callId, String toolName, AiErrorClass errorClass,
                                            String rawReason, long elapsedMs) {
        AiError error = AiError.of(errorClass, AiTraceSupport.currentTraceId(), rawReason);
        log.debug("分类错误明细（仅日志）: tool={} class={} reason={} traceId={}",
                toolName, errorClass.name(), rawReason, error.traceId());
        ToolResult r = ToolResult.fail(callId, toolName, error.userMessage(), elapsedMs);
        r.setErrorCode(error.errorCode());
        r.setErrorClass(errorClass.name());
        r.setTraceId(error.traceId());
        return r;
    }

    // ── 阶段 2: R1.3 高危指令沙盒审查（附加防御层） ──────────────────────

    /**
     * R1.3 沙盒审查。详细设计-06 F06-01 要点 4 重新定位：本层是主裁决（{@code policyEvaluate}）
     * 之后的<b>附加</b>防御，不再是任何工具调用的裁决触发条件 —— 是否强制沙盒只影响本层强度，
     * 不影响"必须被裁决"（否则 DENY 面按用户而变，违反 §1.5-1）。
     *
     * <p>X-14 收口：原 {@code catch → return null}（审查器异常维持放行）改为 DENY/FAIL_CLOSED；
     * {@code sandbox_mandatory} 读取失败（高危命中时）同样 FAIL_CLOSED。</p>
     *
     * @return 拒绝执行的 ToolResult，或 null 表示本层无阻断
     */
    private ToolResult sandboxReview(String toolName, Map<String, Object> arguments,
                                     String callId, long startNs) {
        String userId;
        String instruction;
        try {
            userId = getCurrentUserId();
            if (userId == null) {
                // 阶段 1 已对无主体 FAIL_CLOSED；此处兜底，绝不放行
                return buildGuardrailError(callId, toolName, AiErrorClass.GUARDRAIL_FAIL_CLOSED,
                        "沙盒审查无法取得 token 主体", startNs);
            }
            instruction = buildInstructionText(toolName, arguments);
            if (instruction == null || instruction.isBlank()) {
                return null;
            }
            if (!HIGH_RISK_PATTERN.matcher(instruction).matches()) {
                return null; // 非高危：主裁决已 ALLOW，附加层不额外阻断
            }
        } catch (Exception e) {
            log.warn("F06-01/X-14 沙盒审查器异常，拒绝执行（fail-closed，铁律 §2.4-6）: tool={}",
                    toolName, e);
            return buildGuardrailError(callId, toolName, AiErrorClass.GUARDRAIL_FAIL_CLOSED,
                    "沙盒审查器异常: " + e.getMessage(), startNs);
        }

        try {
            Boolean sandboxMandatory = querySandboxMandatory(userId);
            if (sandboxMandatory == null) {
                log.warn("F06-01/X-14 高危指令且 sandbox_mandatory 不可读，拒绝执行（fail-closed）: "
                        + "tool={} user={}", toolName, userId);
                return buildGuardrailError(callId, toolName, AiErrorClass.GUARDRAIL_FAIL_CLOSED,
                        "sandbox_mandatory 无法判定", startNs);
            }
            if (!sandboxMandatory) {
                return null; // 非强制沙盒用户：主裁决已在阶段 1 无条件完成
            }

            Boolean highRisk = reviewBySandboxService(instruction, userId);
            if (highRisk == null) {
                log.warn("R1.3/H8-T4 SecuritySandboxService 不可用，高危指令默认拒绝（fail-closed）: "
                                + "tool={}, user={}, instruction={}",
                        toolName, userId, truncate(instruction, 200),
                        new IllegalStateException("SecuritySandboxService unavailable (RLS §2.4-6 DENY)"));
                writeSandboxBlockAudit(userId, toolName, instruction);
                return buildGuardrailError(callId, toolName, AiErrorClass.GUARDRAIL_FAIL_CLOSED,
                        "安全裁决服务不可用，高危指令默认拒绝（fail-closed，铁律 §2.4-6）", startNs);
            }

            if (highRisk) {
                log.warn("R1.3 沙盒拦截高危指令: tool={}, user={}, instruction={}",
                        toolName, userId, truncate(instruction, 200));
                writeSandboxBlockAudit(userId, toolName, instruction);
                return buildGuardrailError(callId, toolName, AiErrorClass.GUARDRAIL_DENIED,
                        "高危指令被沙盒拦截（sandbox_block）：指令包含敏感操作，需管理员审批", startNs);
            }

            // 审查通过 → 交阶段 3 dispatch
            return null;
        } catch (Exception e) {
            log.warn("F06-01/X-14 沙盒审查执行异常，拒绝执行（fail-closed）: tool={}", toolName, e);
            return buildGuardrailError(callId, toolName, AiErrorClass.GUARDRAIL_FAIL_CLOSED,
                    "沙盒审查执行异常: " + e.getMessage(), startNs);
        }
    }

    /**
     * R1.3 / H8-T4: 从 token 上下文获取当前用户 ID。
     * <p>F06-02 收口：主体解析统一收口到 {@link ToolSubjectResolver}（sysman UserContext
     * → SecurityContext 两级），禁在本类内各自拼接主体来源，更禁从工具参数/请求体自报值取主体
     * （PMO-74 H9-T1 口径）。</p>
     */
    private String getCurrentUserId() {
        if (subjectResolver == null) return null;
        ToolAdjudicationRequest.Subject subject = subjectResolver.resolve();
        return subject == null ? null : subject.userId();
    }

    /**
     * R1.3: 查询用户的 sandbox_mandatory 配置。
     *
     * @return true=强制沙盒, false=不强制, null=查询失败/无配置
     */
    private Boolean querySandboxMandatory(String userId) {
        try {
            List<Map<String, Object>> rows = toolDao.querySandboxMandatory(userId);
            if (rows.isEmpty() || rows.get(0).get("sandbox_mandatory") == null) {
                return false;
            }
            return ((Boolean) rows.get(0).get("sandbox_mandatory"));
        } catch (Exception e) {
            log.debug("R1.3 查询 sandbox_mandatory 失败 user={}: {}", userId, e.getMessage());
            return null;
        }
    }

    /**
     * R1.3: 拼接待审查的指令文本（工具名 + 参数中的 sql/command/query 字段）。
     */
    private String buildInstructionText(String toolName, Map<String, Object> arguments) {
        StringBuilder sb = new StringBuilder(toolName != null ? toolName : "");
        if (arguments != null) {
            for (String key : new String[]{"sql", "command", "query", "cmd", "statement", "script"}) {
                Object val = arguments.get(key);
                if (val != null) {
                    sb.append(' ').append(val);
                }
            }
            // 若无显式指令字段，拼接全部参数值
            if (sb.length() <= (toolName != null ? toolName.length() : 0)) {
                for (Object val : arguments.values()) {
                    if (val != null) sb.append(' ').append(val);
                }
            }
        }
        return sb.toString();
    }

    /**
     * R1.3: 调用 SecuritySandboxService 审查指令风险。
     * <p>SecuritySandboxService 位于 security-engine-impl（编译期不可见），
     * 通过 ApplicationContext 查找 bean 后反射调用 evaluateFilter，
     * 返回 allowed=false 即视为高风险。
     *
     * @return true=高风险(拒绝), false=低风险(放行), null=服务不可用(降级放行)
     */
    @SuppressWarnings("unchecked")
    private Boolean reviewBySandboxService(String instruction, String userId) {
        if (applicationContext == null) {
            return null;
        }
        try {
            Object sandboxService = null;
            Map<String, ?> beans = applicationContext.getBeansOfType(Object.class);
            for (Map.Entry<String, ?> entry : beans.entrySet()) {
                if (entry.getValue().getClass().getName().endsWith("SecuritySandboxService")) {
                    sandboxService = entry.getValue();
                    break;
                }
            }
            if (sandboxService == null) {
                return null; // 服务不可用 → 降级
            }
            // 调用 evaluateFilter(String expression, Map rowData, String userRole)
            // expression 传入高危指令标记，userRole 传 userId，让沙盒策略评估是否允许
            Method evalMethod = sandboxService.getClass().getMethod(
                    "evaluateFilter", String.class, Map.class, String.class);
            Map<String, Object> rowData = Map.of("instruction", instruction, "classification", "RESTRICTED");
            Object result = evalMethod.invoke(sandboxService, "command contains blocked", rowData, userId);
            if (result instanceof Map<?, ?> m) {
                Object allowed = m.get("allowed");
                if (allowed instanceof Boolean b) {
                    return !b; // allowed=false → 高风险
                }
            }
            return null;
        } catch (Exception e) {
            log.debug("R1.3 SecuritySandboxService 审查降级: {}", e.getMessage());
            return null;
        }
    }

    /**
     * R1.3: 写入沙盒拦截审计日志到 ecos_audit_log。
     */
    private void writeSandboxBlockAudit(String userId, String toolName, String instruction) {
        if (toolDao == null) return;
        try {
            String changesJson = String.format(
                    "{\"userId\":\"%s\",\"toolName\":\"%s\",\"instruction\":\"%s\",\"action\":\"sandbox_block\",\"result\":\"FAILURE\"}",
                    userId != null ? userId : "",
                    toolName != null ? toolName : "",
                    escapeJson(truncate(instruction, 1000)));
            toolDao.insertSandboxAudit(userId, toolName, changesJson);
        } catch (Exception e) {
            log.error("R1.3 沙盒拦截审计日志写入失败: {}", e.getMessage());
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) + "...[truncated]" : s;
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }

    // ── Tool Loading ──────────────────────────────────────────────────────

    /**
     * 加载工具定义：<b>场景受控工具声明优先</b>（F06-11），其次数据库，最后内存 fallback。
     *
     * <p>声明优先的次序是安全相关的：场景工具的 {@code operation}/{@code timeoutMs}/端点必须取
     * <b>声明值</b>，若同名 DB 行先命中，管理员配的 {@code tool_type} 会改写裁决载荷的
     * {@code action.operation}（写判成读即绕过 OPA 的写权限面）。</p>
     */
    ToolDefinitionEntry loadToolDefinition(String toolName) {
        ToolDefinitionEntry scenario = loadScenarioDefinition(toolName);
        if (scenario != null) {
            return scenario;
        }
        ToolDefinitionEntry def = loadFromDatabase(toolName);
        if (def != null) {
            return def;
        }
        return fallbackTools.get(toolName);
    }

    /**
     * F06-11：场景受控工具声明 → {@link ToolDefinitionEntry}（{@code toolType} 恒为 REST）。
     *
     * <p>{@code endpointUrl} 存<b>声明里的相对路径</b>，绝对化在 dispatch 时用服务端基址拼接，
     * 保证"基址不可由入参左右"；{@code schemaJson} 存声明的参数模式 JSON，使既有的
     * schema 侧读面（如审计摘要）无需分叉。</p>
     */
    private ToolDefinitionEntry loadScenarioDefinition(String toolName) {
        if (scenarioRegistry == null) return null;
        ScenarioToolDefinition declared = scenarioRegistry.find(toolName).orElse(null);
        if (declared == null) return null;
        ToolDefinitionEntry entry = new ToolDefinitionEntry();
        entry.code = declared.toolKey();
        entry.name = declared.name();
        entry.description = declared.description();
        entry.toolType = "REST";
        entry.endpointUrl = declared.backendEndpoint();
        entry.httpMethod = declared.httpMethod();
        entry.schemaJson = declared.argsSchemaJson();
        entry.scenario = declared;
        return entry;
    }

    private ToolDefinitionEntry loadFromDatabase(String toolName) {
        if (toolDao == null) {

            log.debug("ToolDao not available, skipping DB load for tool: {}", toolName);
            return null;
        }
        try {
            List<Map<String, Object>> rows = toolDao.loadToolDefinition(toolName);
            if (rows.isEmpty()) {
                return null;
            }
            Map<String, Object> row = rows.get(0);
            ToolDefinitionEntry entry = new ToolDefinitionEntry();
            entry.code = (String) row.get("code");
            entry.name = (String) row.get("name");
            entry.description = (String) row.get("description");
            String toolType = (String) row.get("tool_type");
            entry.toolType = toolType != null ? toolType.toUpperCase() : "API";
            entry.endpointUrl = (String) row.get("endpoint_url");
            entry.httpMethod = (String) row.get("http_method");
            entry.schemaJson = row.get("schema_json");
            return entry;
        } catch (Exception e) {
            log.warn("Failed to load tool '{}' from database: {}", toolName, e.getMessage());
            return null;
        }
    }

    // ── Execution Dispatch ────────────────────────────────────────────────

    private String executeWithTimeout(ToolDefinitionEntry def, Map<String, Object> arguments)
            throws TimeoutException, Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<String> future = executor.submit(() -> dispatch(def, arguments));
        // F06-11：场景工具的执行预算取声明 timeoutMs（上界仍是咽喉 30s），通用工具维持原 30s
        long budgetSeconds = def.scenario == null ? TIMEOUT_SECONDS
                : Math.min(TIMEOUT_SECONDS,
                Math.max(1, (def.scenario.timeoutMs() + 999) / 1000));
        try {
            return future.get(budgetSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("工具执行被中断", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception ex) {
                throw ex;
            }
            throw new RuntimeException(cause);
        } finally {
            executor.shutdownNow();
        }
    }

    private String dispatch(ToolDefinitionEntry def, Map<String, Object> arguments) throws Exception {
        // F06-11：场景受控工具走声明式 REST 通道（端点/方法/载荷全部来自声明，不接受入参指定 URL）
        if (def.scenario != null) {
            return executeScenarioRest(def, arguments);
        }
        String type = def.toolType;
        if (type == null) {
            return executeRest(def, arguments);
        }
        switch (type) {
            case "SQL":     return executeSql(def, arguments);
            case "REST":
            case "API":     return executeRest(def, arguments);
            case "BUILTIN": return executeBuiltin(def.code, arguments);
            default:        return executeRest(def, arguments);
        }
    }

    // ── F06-11 场景受控工具执行 ───────────────────────────────────────────

    /**
     * 场景工具分发（声明 → 唯一 REST 出向调用）。
     *
     * <p>三条硬约束：</p>
     * <ol>
     *   <li><b>URL 只由服务端基址 + 声明路径</b>构成，入参只能填 {@code {占位符}}，
     *       不可注入 scheme/host（否则本通道退化为通用 {@code invoke_rest}）；</li>
     *   <li><b>必填参数地板</b>在出向之前判（缺参即 {@code INVALID_ARGUMENT}，禁把注定失败的载荷打到后端）；</li>
     *   <li><b>写工具载荷收口</b>：{@code status} 强制 DRAFT、身份字段剔除（审批人只从 token 取）。</li>
     * </ol>
     *
     * <p>GET/HEAD 把非路径参数编成 query string；POST 编成 JSON body —— 两种形态都只带声明里的参数名。</p>
     */
    private String executeScenarioRest(ToolDefinitionEntry def, Map<String, Object> arguments)
            throws Exception {
        ScenarioToolDefinition declared = def.scenario;
        List<String> missing = declared.missingRequiredArgs(arguments);
        if (!missing.isEmpty()) {
            throw new ScenarioArgumentsException("场景工具缺少必填参数: " + String.join(",", missing));
        }
        for (String placeholder : declared.pathPlaceholders()) {
            Object value = arguments == null ? null : arguments.get(placeholder);
            if (value == null || String.valueOf(value).isBlank()) {
                throw new ScenarioArgumentsException("场景工具路径占位符缺值: " + placeholder);
            }
        }

        String path = resolveUrlParams(declared.backendEndpoint(), arguments);
        String url = scenarioBaseUrl + path;
        HttpMethod method = HttpMethod.valueOf(declared.httpMethod().toUpperCase(Locale.ROOT));
        Set<String> declaredArgs = declared.argsSchema().keySet();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(EcosTraceContext.HEADER_REQUEST_ID, AiTraceSupport.currentTraceId());

        HttpEntity<?> entity;
        if (method == HttpMethod.GET || method == HttpMethod.DELETE) {
            String query = buildDeclaredQuery(declaredArgs, path, arguments);
            entity = new HttpEntity<>(headers);
            if (!query.isEmpty()) url = url + (url.contains("?") ? "&" : "?") + query;
        } else {
            Map<String, Object> payload = new LinkedHashMap<>();
            if (arguments != null) {
                for (Map.Entry<String, Object> e : arguments.entrySet()) {
                    if (declaredArgs.contains(e.getKey())) payload.put(e.getKey(), e.getValue());
                }
            }
            entity = new HttpEntity<>(ScenarioToolCatalog.enforceWritePayload(declared, payload), headers);
        }

        log.info("F06-11 场景工具出向: tool={} method={} path={} operation={}",
                declared.toolKey(), method, path, declared.operation());
        ResponseEntity<String> response = restTemplate.exchange(url, method, entity, String.class);
        String body = response.getBody();
        return body != null ? body : "";
    }

    /** GET 形态的 query string：只带声明里的参数，且不重复路径已消费的名字。 */
    private String buildDeclaredQuery(Set<String> declaredArgs, String resolvedPath,
                                      Map<String, Object> arguments) {
        if (arguments == null || arguments.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Object> e : arguments.entrySet()) {
            String key = e.getKey();
            if (!declaredArgs.contains(key) || e.getValue() == null) continue;
            if (resolvedPath.contains("{" + key + "}")) continue;
            if (!sb.isEmpty()) sb.append('&');
            sb.append(URLEncoder.encode(key, StandardCharsets.UTF_8))
                    .append('=')
                    .append(URLEncoder.encode(String.valueOf(e.getValue()), StandardCharsets.UTF_8));
        }
        return sb.toString();
    }

    /** 场景工具入参地板失败（映射为 {@code INVALID_ARGUMENT}，与后端故障分类区分）。 */
    static final class ScenarioArgumentsException extends RuntimeException {
        ScenarioArgumentsException(String message) {
            super(message);
        }
    }

    // ── SQL Execution ─────────────────────────────────────────────────────

    private String executeSql(ToolDefinitionEntry def, Map<String, Object> arguments) throws Exception {
        if (toolDao == null) {
            throw new IllegalStateException("ToolDao 不可用，无法执行 SQL 工具");
        }

        // F06-01 防御纵深 —— 裁决闸未装配时拒绝一切 SQL 工具（fail-closed，§2.4-6）。
        // 正常路径下阶段 1 已完成裁决，本处只是"dispatch 被绕过"时的第二道卡。
        if (policyGate == null) {
            throw new IllegalStateException(
                    "[F06-01] 工具裁决闸（AgentToolPolicyGate）未装配，拒绝执行 SQL 工具（fail-closed）");
        }

        Object schemaObj = def.schemaJson;
        String sql = resolveToolSql(def, arguments);
        if (sql == null || sql.isBlank()) {
            throw new IllegalArgumentException("SQL 工具缺少 SQL 语句定义");
        }

        List<Object> paramList = extractParamsFromSchema(schemaObj, arguments);

        log.debug("Executing SQL: {} with params: {}", sql, paramList);
        List<Map<String, Object>> rows = toolDao.queryForList(sql, paramList.toArray());

        return objectMapper.writeValueAsString(Map.of(
            "rows", rows,
            "count", rows.size()
        ));
    }

    // ── H8-T4/F06-01 SQL 地板辅助 ────────────────────────────────────────

    /**
     * 解析工具的最终 SQL 文本（与 executeSql 的解析顺序完全一致）：
     * schema 定义优先，其次 arguments 的 sql 字段。
     */
    private String resolveToolSql(ToolDefinitionEntry def, Map<String, Object> arguments) {
        String sql = extractSqlFromSchema(def.schemaJson, arguments);
        if (sql == null || sql.isBlank()) {
            Object arg = arguments != null ? arguments.get("sql") : null;
            if (arg instanceof String s) sql = s;
        }
        return sql;
    }

    /** schema 声明的绑定参数名（将拼入 WHERE 子句或作为 ? 的列约束），供白名单校验 */
    @SuppressWarnings("unchecked")
    private List<String> extractBoundParamNames(Object schemaObj) {
        List<String> names = new ArrayList<>();
        try {
            Map<String, Object> schemaMap;
            if (schemaObj instanceof String s) {
                schemaMap = objectMapper.readValue(s, Map.class);
            } else if (schemaObj instanceof Map) {
                schemaMap = (Map<String, Object>) schemaObj;
            } else {
                return names;
            }
            List<Map<String, Object>> parameters = (List<Map<String, Object>>) schemaMap.get("parameters");
            if (parameters != null) {
                for (Map<String, Object> param : parameters) {
                    Object name = param.get("name");
                    if (name instanceof String s && !s.isBlank()) names.add(s);
                }
            }
        } catch (Exception ignored) {
            // 解析失败 → 空参数名列表；SQL 文本本身仍会被安全闸全量校验
        }
        return names;
    }

    // ── REST Execution ────────────────────────────────────────────────────

    private String executeRest(ToolDefinitionEntry def, Map<String, Object> arguments) throws Exception {
        String url = def.endpointUrl;
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("REST 工具缺少 endpoint_url");
        }

        // 替换 URL 中的路径参数 {paramName}
        url = resolveUrlParams(url, arguments);

        String httpMethod = def.httpMethod;
        if (httpMethod == null) {
            httpMethod = "POST";
        }
        HttpMethod method = HttpMethod.valueOf(httpMethod.toUpperCase());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        // F06-06 要点 2：REST 工具出站透传 MDC traceId（同源，禁自造 ID；runtime-access 收口后由拦截器接管 — F06-17）
        headers.set(EcosTraceContext.HEADER_REQUEST_ID, AiTraceSupport.currentTraceId());

        HttpEntity<?> entity;
        if (method == HttpMethod.GET || method == HttpMethod.DELETE) {
            entity = new HttpEntity<>(headers);
        } else {
            entity = new HttpEntity<>(arguments, headers);
        }

        log.debug("Executing REST: {} {}", method, url);
        ResponseEntity<String> response = restTemplate.exchange(url, method, entity, String.class);

        String body = response.getBody();
        return body != null ? body : "";
    }

    // ── BUILTIN Execution ─────────────────────────────────────────────────

    private String executeBuiltin(String code, Map<String, Object> arguments) throws Exception {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("BUILTIN 工具缺少 code");
        }

        switch (code) {
            case "getCurrentTime": return executeGetCurrentTime();
            case "calculateRisk":  return executeCalculateRisk(arguments);
            case "delegate_to_agent": return executeDelegate(arguments);
            default:
                throw new IllegalArgumentException("未知内置工具: " + code);
        }
    }

    private String executeGetCurrentTime() throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("timestamp", System.currentTimeMillis());
        result.put("iso", Instant.now().toString());
        result.put("formatted", DateTimeFormatter.ISO_ZONED_DATE_TIME.format(ZonedDateTime.now()));
        return objectMapper.writeValueAsString(result);
    }

    private String executeCalculateRisk(Map<String, Object> arguments) throws Exception {
        double probability = getDoubleParam(arguments, "probability", 0.5);
        double impact = getDoubleParam(arguments, "impact", 0.5);
        String severity = getStringParam(arguments, "severity", "medium");

        double riskScore = probability * impact;
        String riskLevel;
        if (riskScore >= 0.7) {
            riskLevel = "HIGH";
        } else if (riskScore >= 0.4) {
            riskLevel = "MEDIUM";
        } else {
            riskLevel = "LOW";
        }

        // 严重程度加权
        double severityMultiplier;
        switch (severity.toLowerCase()) {
            case "critical": severityMultiplier = 1.5; break;
            case "high":     severityMultiplier = 1.2; break;
            case "medium":   severityMultiplier = 1.0; break;
            case "low":      severityMultiplier = 0.8; break;
            default:         severityMultiplier = 1.0; break;
        }
        double weightedScore = Math.min(1.0, riskScore * severityMultiplier);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("probability", probability);
        result.put("impact", impact);
        result.put("rawRiskScore", Math.round(riskScore * 100.0) / 100.0);
        result.put("severity", severity);
        result.put("severityMultiplier", severityMultiplier);
        result.put("weightedRiskScore", Math.round(weightedScore * 100.0) / 100.0);
        result.put("riskLevel", riskLevel);

        return objectMapper.writeValueAsString(result);
    }

    /**
     * 执行子 Agent 委托 — 委托给 {@link AgentDelegationService}。
     * <p>
     * 这是 delegate_to_agent 内置工具的执行入口。
     * 因为需要返回 ToolResult（而非纯 String），不走 dispatch 路径，
     * 直接在外部 execute 方法中通过 ToolResult 返回。
     * </p>
     */
    private String executeDelegate(Map<String, Object> arguments) throws Exception {
        if (agentDelegationService == null) {
            throw new IllegalStateException("AgentDelegationService 不可用，无法执行子Agent委托");
        }
        // delegate 返回 ToolResult，但 executeBuiltin 要求返回 String
        // 将 ToolResult 序列化为 JSON 返回
        ToolResult delegateResult = agentDelegationService.delegate(arguments);
        Map<String, Object> serialized = new LinkedHashMap<>();
        serialized.put("success", delegateResult.isSuccess());
        serialized.put("content", delegateResult.getContent());
        serialized.put("error", delegateResult.getError());
        serialized.put("elapsedMs", delegateResult.getElapsedMs());
        return objectMapper.writeValueAsString(serialized);
    }

    // ── Fallback Tool Definitions ─────────────────────────────────────────

    private void initFallbackTools() {
        registerFallback("getCurrentTime", "获取当前时间", "BUILTIN", null, null);
        registerFallback("calculateRisk", "风险评估计算", "BUILTIN", null, null);
        registerFallback("knowledge-search", "知识库搜索", "API",
            "/api/v1/ecos/knowledge/search", "GET");
        registerFallback("object-query", "对象查询", "API",
            "/api/v1/ecos/objects/{entityCode}", "GET");
        registerFallback("graph-query", "知识图谱查询", "API",
            "/api/v1/ecos/knowledge/graph", "GET");
        registerFallback("ontology-explore", "本体浏览", "API",
            "/api/ontology", "GET");
        registerFallback("delegate_to_agent", "子Agent动态委托", "BUILTIN", null, null);
    }

    private void registerFallback(String code, String name, String toolType,
                                   String endpointUrl, String httpMethod) {
        ToolDefinitionEntry entry = new ToolDefinitionEntry();
        entry.code = code;
        entry.name = name;
        entry.toolType = toolType;
        entry.endpointUrl = endpointUrl;
        entry.httpMethod = httpMethod;
        fallbackTools.put(code, entry);
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private String resolveUrlParams(String url, Map<String, Object> arguments) {
        if (url.contains("{") && arguments != null) {
            for (Map.Entry<String, Object> e : arguments.entrySet()) {
                url = url.replace("{" + e.getKey() + "}",
                    e.getValue() != null ? e.getValue().toString() : "");
            }
        }
        return url;
    }

    @SuppressWarnings("unchecked")
    private String extractSqlFromSchema(Object schemaObj, Map<String, Object> arguments) {
        if (schemaObj == null) return null;
        try {
            Map<String, Object> schemaMap;
            if (schemaObj instanceof String s) {
                schemaMap = objectMapper.readValue(s, Map.class);
            } else if (schemaObj instanceof Map) {
                schemaMap = (Map<String, Object>) schemaObj;
            } else {
                return null;
            }

            // 直接 sql 字段（管理端定义的 SQL —— 仍会经由 H8-T4 安全闸在调用方线程裁决）
            Object sql = schemaMap.get("sql");
            if (sql instanceof String s && !s.isBlank()) {
                return s;
            }

            // 从参数列表构建占位符 SQL。
            // H8-T4 (PMO-74 L3) 整改：table 与参数名不再裸拼进 SQL ——
            //   ① 表名必须为合法标识符且在 Agent 工具 SQL 白名单中显式授权；
            //   ② SELECT 列 = 白名单授权列清单（仅当管理员显式授权 "*" 时才保留 SELECT *）；
            //   ③ WHERE 参数名必须是合法标识符且属于该表授权列（阻断 IR04 注入面）。
            List<Map<String, Object>> parameters = (List<Map<String, Object>>) schemaMap.get("parameters");
            Object tableObj = schemaMap.get("table");
            if (tableObj instanceof String table && parameters != null) {
                if (sqlWhitelist == null) {
                    throw new IllegalStateException(
                            "[H8-T4] SQL 白名单组件未装配，拒绝按 schema 生成 SQL（fail-closed）");
                }
                Set<String> allowedCols = sqlWhitelist.requireAllowedColumns(table);
                boolean allCols = allowedCols.contains(AgentToolSqlWhitelist.ALL_COLUMNS);
                StringBuilder sb = new StringBuilder("SELECT ");
                if (allCols) {
                    sb.append("*");
                } else {
                    sb.append(String.join(",", allowedCols));
                }
                sb.append(" FROM ").append(table).append(" WHERE 1=1");
                for (Map<String, Object> param : parameters) {
                    String name = (String) param.get("name");
                    if (name == null || name.isBlank()) continue;
                    if (arguments.containsKey(name)) {
                        sqlWhitelist.requireAllowedColumn(table, name);
                        sb.append(" AND ").append(name).append(" = ?");
                    }
                }
                sb.append(" LIMIT 100");
                return sb.toString();
            }
        } catch (IllegalArgumentException | IllegalStateException guarded) {
            // H8-T4 白名单拒绝 —— 原样上抛，不吞进「schema 即 SQL」兜底分支
            throw guarded;
        } catch (Exception e) {
            // schema 本身可能就是 SQL 字符串（同样会过安全闸）
            if (schemaObj instanceof String s) return s;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private List<Object> extractParamsFromSchema(Object schemaObj, Map<String, Object> arguments) {
        List<Object> params = new ArrayList<>();
        try {
            Map<String, Object> schemaMap;
            if (schemaObj instanceof String s) {
                schemaMap = objectMapper.readValue(s, Map.class);
            } else if (schemaObj instanceof Map) {
                schemaMap = (Map<String, Object>) schemaObj;
            } else {
                return params;
            }
            List<Map<String, Object>> parameters = (List<Map<String, Object>>) schemaMap.get("parameters");
            if (parameters != null) {
                for (Map<String, Object> param : parameters) {
                    String name = (String) param.get("name");
                    if (arguments.containsKey(name)) {
                        params.add(arguments.get(name));
                    }
                }
            }
        } catch (Exception ignored) {
            log.warn("从 schema 提取位置参数失败，本次仅传递已识别参数: {}", ignored.getMessage());
        }
        return params;
    }

    private double getDoubleParam(Map<String, Object> args, String key, double defaultValue) {
        if (args == null) return defaultValue;
        Object val = args.get(key);
        if (val instanceof Number n) return n.doubleValue();
        if (val instanceof String s) {
            try { return Double.parseDouble(s); }
            catch (NumberFormatException ignored) {
                log.debug("double 参数解析失败，回退默认值: key={} default={}", key, defaultValue);
            }
        }
        return defaultValue;
    }

    private String getStringParam(Map<String, Object> args, String key, String defaultValue) {
        if (args == null) return defaultValue;
        Object val = args.get(key);
        return val != null ? val.toString() : defaultValue;
    }

    // ── Result Factory ────────────────────────────────────────────────────

    private ToolResult buildSuccess(String callId, String toolName, String content, long elapsedMs) {
        return ToolResult.ok(callId, toolName, content, elapsedMs);
    }

    /**
     * 护栏拒绝类错误（F06-14 分类）：对外文案取 {@link AiError#userMessage()} 模板
     * （含诊断码 traceId），拒绝原因原文只进日志，禁随响应体上浮（X-49）。
     */
    private ToolResult buildGuardrailError(String callId, String toolName, AiErrorClass errorClass,
                                           String rawReason, long startNs) {
        return buildGuardrailError(callId, toolName, errorClass, rawReason, startNs, null);
    }

    private ToolResult buildGuardrailError(String callId, String toolName, AiErrorClass errorClass,
                                           String rawReason, long startNs, ToolDecision decision) {
        long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;
        AiError error = AiError.of(errorClass, AiTraceSupport.currentTraceId(), rawReason);
        log.debug("护栏错误明细（仅日志）: tool={} class={} reason={} traceId={}",
                toolName, errorClass.name(), rawReason, error.traceId());
        ToolResult r = ToolResult.fail(callId, toolName, error.userMessage(), elapsedMs);
        r.setErrorCode(error.errorCode());
        r.setErrorClass(errorClass.name());
        r.setTraceId(error.traceId());
        r.setDecision(decision != null ? decision.verdict().name()
                : (errorClass == AiErrorClass.GUARDRAIL_FAIL_CLOSED ? "FAIL_CLOSED" : "DENY"));
        if (decision != null) r.setPolicyId(decision.policyId());
        return r;
    }

    // ── Inner Types ───────────────────────────────────────────────────────

    /** 工具执行结果 */
    public static class ToolResult {
        private String toolCallId;
        private String toolName;
        private String content;
        private String error;
        private long elapsedMs;
        private boolean success;

        // 详细设计-06 F06-14：护栏/错误分类的结构化上浮面（只增不改既有字段）
        private String errorCode;
        private String errorClass;
        private String traceId;
        private String decision;
        private String policyId;

        /** F06-11/12：场景工具返回体的引用块（由咽喉确定性渲染，禁 LLM 复述改写）。 */
        private String citationBlock;

        public ToolResult() {}

        public static ToolResult ok(String callId, String toolName, String content, long elapsedMs) {
            ToolResult r = new ToolResult();
            r.toolCallId = callId;
            r.toolName = toolName;
            r.content = content;
            r.elapsedMs = elapsedMs;
            r.success = true;
            return r;
        }

        public static ToolResult fail(String callId, String toolName, String error, long elapsedMs) {
            ToolResult r = new ToolResult();
            r.toolCallId = callId;
            r.toolName = toolName;
            r.error = error;
            r.content = "ERROR: " + error;
            r.elapsedMs = elapsedMs;
            r.success = false;
            return r;
        }

        public String getToolCallId() { return toolCallId; }
        public void setToolCallId(String v) { this.toolCallId = v; }
        public String getToolName() { return toolName; }
        public void setToolName(String v) { this.toolName = v; }
        public String getContent() { return content; }
        public void setContent(String v) { this.content = v; }
        public String getError() { return error; }
        public void setError(String v) { this.error = v; }
        public long getElapsedMs() { return elapsedMs; }
        public void setElapsedMs(long v) { this.elapsedMs = v; }
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean v) { this.success = v; }
        public String getErrorCode() { return errorCode; }
        public void setErrorCode(String v) { this.errorCode = v; }
        public String getErrorClass() { return errorClass; }
        public void setErrorClass(String v) { this.errorClass = v; }
        public String getTraceId() { return traceId; }
        public void setTraceId(String v) { this.traceId = v; }
        public String getDecision() { return decision; }
        public void setDecision(String v) { this.decision = v; }
        public String getPolicyId() { return policyId; }
        public void setPolicyId(String v) { this.policyId = v; }
        public String getCitationBlock() { return citationBlock; }
        public void setCitationBlock(String v) { this.citationBlock = v; }

        @Override
        public String toString() {
            return success ? "ToolResult[ok, " + toolName + "]"
                : "ToolResult[fail, " + toolName + ": " + error + "]";
        }
    }

    static class ToolDefinitionEntry {
        String code;
        String name;
        String description;
        String toolType;
        String endpointUrl;
        String httpMethod;
        Object schemaJson;

        /**
         * F06-11 场景受控工具的声明体（非场景工具恒为 null）。
         *
         * <p>存在即代表：{@code operation} 与 {@code timeoutMs} 必须取声明值、执行必须走
         * 服务端基址拼出的 REST 通道、返回体必须自带引用元数据 —— 三者都由本咽喉强制，
         * 不依赖 DB 行或调用方入参。</p>
         */
        ScenarioToolDefinition scenario;
    }
}
