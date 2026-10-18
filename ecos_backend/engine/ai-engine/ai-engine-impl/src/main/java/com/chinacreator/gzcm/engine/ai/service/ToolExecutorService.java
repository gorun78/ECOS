package com.chinacreator.gzcm.engine.ai.service;

import com.chinacreator.gzcm.common.error.AiError;
import com.chinacreator.gzcm.common.error.AiErrorClass;
import com.chinacreator.gzcm.engine.ai.config.AiRestClientConfig;
import com.chinacreator.gzcm.engine.ai.dao.ToolDao;
import com.chinacreator.gzcm.engine.ai.guardrail.ToolAdjudicationRequest;
import com.chinacreator.gzcm.engine.ai.guardrail.ToolDecision;
import com.chinacreator.gzcm.engine.ai.guardrail.ToolObligations;
import com.chinacreator.gzcm.engine.ai.security.AgentToolPolicyGate;
import com.chinacreator.gzcm.engine.ai.security.AgentToolSqlWhitelist;
import com.chinacreator.gzcm.engine.ai.security.AiToolColumnSurfaceGate;
import com.chinacreator.gzcm.engine.ai.security.AiToolRateLimitGate;
import com.chinacreator.gzcm.engine.ai.security.ToolObligationApplier;
import com.chinacreator.gzcm.engine.ai.security.ToolSubjectResolver;
import com.chinacreator.gzcm.engine.ai.tool.ScenarioToolCatalog;
import com.chinacreator.gzcm.engine.ai.tool.ScenarioToolDefinition;
import com.chinacreator.gzcm.engine.ai.tool.ScenarioToolRegistry;
import com.chinacreator.gzcm.engine.ai.tool.ToolCitation;
import com.chinacreator.gzcm.engine.ai.trace.AiTraceSupport;
import com.chinacreator.gzcm.common.trace.EcosTraceContext;
import com.chinacreator.gzcm.runtime.access.endpoint.ServiceEndpointResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
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
 * {@code execute() → policyEvaluate() → sandboxReview() → rateLimitEnforce() → dispatch() → obligationsApply()}：</p>
 * <ol>
 *   <li>{@code policyEvaluate}：对<b>所有</b> toolType 与<b>所有</b>来源（注册表 7 工具 +
 *       DB/fallback 定义的 SQL/REST/BUILTIN + 场景工具）无条件执行一次且仅一次 security-engine
 *       ABAC 裁决（废止原 {@code "SQL".equalsIgnoreCase(toolType)} 前置条件，X-11）；</li>
 *   <li>{@code sandboxReview}：R1.3 高危沙盒，作为<b>附加</b>防御，自身异常一律 DENY
 *       而非放行（X-14）；</li>
 *   <li>{@code rateLimitEnforce}：ALLOW 裁决若签发 {@code rateLimit} 义务，必须在
 *       <b>dispatch 之前</b>消费（#142/B4-3d-4 收口差距 D-B4-21）——配额用尽归 DENIED（403），
 *       义务形态不合契约归 FAIL_CLOSED（503），两者都不进 dispatch，也禁折叠成一态；</li>
 *   <li>{@code dispatch}：按 toolType 分发执行，未获 ALLOW 不会到达此处；</li>
 *   <li>{@code obligationsApply}：ALLOW 返回的 {@code mask[]/rowFilter} 必须在结果回 LLM
 *       <b>之前</b>经 security 侧执行落实，执行不可用则整条结果丢弃并 FAIL_CLOSED（F06-02 要点 2）；
 *       同一阶段还承担 <b>CLS 列面裁剪</b>（#142/B4-3d-6 收口差距 D-B4-28）——
 *       列面裁决与义务签发相互独立，故"义务为空"不再等于"本阶段无事可做"；
 *       顺序固定为 rowFilter → mask → 列面裁剪（详设-01 C.2.4）。</li>
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

    /** F02-15：场景层基址的唯一寻址出口（runtime-access，route-manifest 单源）。 */
    private ServiceEndpointResolver endpointResolver;

    /**
     * 场景工具后端的<b>服务端</b>基址（配置注入，禁取 LLM 传入的 URL —— 否则本通道退化成
     * 第二个 {@code invoke_rest}，F06-11 要点 3 的白名单收口即失效）。
     *
     * <p>F02-15 两态寻址单源：本字段只是<b>显式覆盖位</b>（默认空），未覆盖时经
     * {@link #resolveScenarioBaseUrl()} 由 runtime-access {@code ServiceEndpointResolver}
     * 按 route-manifest 解析。本类禁止再写死 {@code http://localhost:<port>} 字面量
     * （data-engine {@code EndpointResolverUsageArchTest} + gateway {@code CrossEngineEndpointArchTest} 双门禁）。</p>
     */
    private String scenarioBaseUrlOverride = "";

    /** route-manifest 里场景层的归属服务名（{@code owner} 字段，workspace 场景层）。 */
    public static final String SCENARIO_OWNER_SERVICE = "workspace";

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
    private AiToolRateLimitGate rateLimitGate;
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

    /**
     * 阶段 2.5 的限流义务消费方（#142/B4-3d-4，D-B4-21）。
     *
     * <p>未装配时<b>只在裁决确实签发了限流义务</b>的路径上 FAIL_CLOSED：
     * 无义务的裁决不受影响（页面形态/旧策略），避免把"策略没签发"变成"全量工具不可用"，
     * 同时禁止"有义务但无人消费 ⇒ 静默丢弃义务"这一 D-B4-21 原形态复现。</p>
     */
    @Autowired(required = false)
    public void setRateLimitGate(AiToolRateLimitGate rateLimitGate) {
        this.rateLimitGate = rateLimitGate;
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

    /**
     * F06-11：场景工具后端基址的<b>显式覆盖位</b>（配置项 {@code service.scenario.base-url}，
     * 默认留空）。留空即由 {@link #resolveScenarioBaseUrl()} 走 route-manifest 两态寻址；
     * F02-15 收口后本项不再有硬编码 localhost 默认值。
     */
    @Autowired(required = false)
    public void setScenarioBaseUrl(
            @org.springframework.beans.factory.annotation.Value("${service.scenario.base-url:}") String scenarioBaseUrl) {
        this.scenarioBaseUrlOverride = normalizeBaseUrl(scenarioBaseUrl);
    }

    /** F02-15：两态寻址单源；未装配 = 场景工具出向 fail-loud（禁回落硬编码地址）。 */
    @Autowired(required = false)
    public void setEndpointResolver(ServiceEndpointResolver endpointResolver) {
        this.endpointResolver = endpointResolver;
    }

    // ── #143/B5-1 W 契约目录接缝（工具目录单源化） ─────────────────────────

    /**
     * W 契约目录进咽喉的<b>中立接缝</b>（详细设计-10 F10-17/F10-19，差距分析 B5 行「双轨」收口）。
     *
     * <p>为什么是"咽喉增源读契约目录"而不是"把契约映射进咽喉声明表"：后者是把
     * {@code ecos_wagent_tool_contract} 的行复制进 {@code ecos_tool_definition}，
     * 两份数据两处写面 ⇒ 双轨更硬（同一工具的两个版本可以各说各话）。增源只加一个读面，
     * 契约行的唯一真值仍在 W 目录。</p>
     *
     * <p>依赖方向：实现落在 {@code ai.wagent.tool} 侧（持 {@code WAgentToolContractDao}），
     * 本类只见声明、不见 DO —— 咽喉不解析契约列，契约侧不复制声明行。</p>
     *
     * <p>命名取 {@code I} 前缀：{@code ArchitectureTest.serviceInterfacesShouldStartWithI}
     * 对 {@code ..service..} 包内<b>所有</b>接口（含嵌套）生效，本接缝的旧名由该门禁抓红后收回。</p>
     */
    public interface IContractToolSource {

        /**
         * 按 {@code name+version} 取<b>可执行</b>契约声明；任何前置不成立（非 active、
         * 非 rest 形态、端点前缀不可归属、方法非法）一律返回空 ⇒ 咽喉维持「工具未找到」
         * （fail-closed，禁"退而求其次"按名取其它版本或按 DB 行拼一个）。
         */
        Optional<ContractToolDeclaration> findActive(String toolName, String toolVersion);
    }

    /**
     * 契约声明里咽喉真正需要的几项（{@code operation} 已折算成裁决词表 {@code read|write}）。
     *
     * <p>{@code endpointPath} 存<b>声明里的相对路径</b>，与场景工具同纪律：绝对化在出向时用
     * 服务端基址拼接，入参只能填 {@code {占位符}}，不可左右 scheme/host。</p>
     */
    public record ContractToolDeclaration(String name, String version, String endpointPath,
                                          String httpMethod, String operation, String inputSchemaJson,
                                          Long timeoutMs) {
    }

    private IContractToolSource contractToolSource;

    /** gateway 基址的<b>显式覆盖位</b>（与 {@code AiRestClientConfig} 同键，默认空走 route-manifest）。 */
    private String contractBaseUrlOverride = "";

    /**
     * W 契约目录源；未装配 = 契约工具对咽喉不可见（通用工具与场景工具路径不受影响）。
     * 实现在 wagent 侧（{@code WAgentContractToolSource}），装配后 F10-19 的 12 工具才可执行。
     */
    @Autowired(required = false)
    public void setContractToolSource(IContractToolSource contractToolSource) {
        this.contractToolSource = contractToolSource;
    }

    /** 契约工具基址覆盖项（运维逃生口；键名与 gateway 探测面单源同一条，禁在此写本机值）。 */
    @Autowired(required = false)
    public void setContractBaseUrl(
            @org.springframework.beans.factory.annotation.Value("${"
                    + AiRestClientConfig.GATEWAY_BASE_URL_PROPERTY + ":}") String contractBaseUrl) {
        this.contractBaseUrlOverride = normalizeBaseUrl(contractBaseUrl);
    }

    /**
     * 契约工具出向基址：与 F10-08 探测面同源（覆盖项 → route-manifest 解析 gateway 承流地址 →
     * fail-loud）。契约声明的 {@code /api/v1/{engine}/...} 前缀族的路由表唯一属主是 gateway，
     * 本通道禁自拼各 service 端口。
     */
    private String resolveContractBaseUrl() {
        if (!contractBaseUrlOverride.isEmpty()) {
            return contractBaseUrlOverride;
        }
        if (endpointResolver == null) {
            throw new IllegalStateException("ServiceEndpointResolver 未装配，契约工具基址无法解析"
                    + "（F02-15 两态寻址单源，禁回落硬编码地址）");
        }
        return endpointResolver
                .resolve(AiRestClientConfig.GATEWAY_OWNER_SERVICE,
                        ServiceEndpointResolver.RouteMode.SERVICE)
                .map(ToolExecutorService::normalizeBaseUrl)
                .filter(base -> !base.isEmpty())
                .orElseThrow(() -> new IllegalStateException("route-manifest 未登记 gateway 承流服务，"
                        + "契约工具基址无法解析（fail-loud）"));
    }

    /**
     * 场景工具出向基址解析链（与 workspace {@code WorkspaceRestClientConfig} 同口径）：
     * ① {@code service.scenario.base-url} 显式覆盖（运维逃生口）→ ② route-manifest
     * 两态寻址（{@code RouteMode.SERVICE}：owner 已切流取 service 端口，未切流自动回落
     * monolith 端口）→ ③ 双双不可得抛异常 fail-loud，<b>禁静默回落 localhost 字面量</b>。
     */
    private String resolveScenarioBaseUrl() {
        if (!scenarioBaseUrlOverride.isEmpty()) {
            return scenarioBaseUrlOverride;
        }
        if (endpointResolver == null) {
            throw new IllegalStateException("ServiceEndpointResolver 未装配，场景工具基址无法解析"
                    + "（F02-15 两态寻址单源，禁回落硬编码地址）");
        }
        return endpointResolver.resolve(SCENARIO_OWNER_SERVICE, ServiceEndpointResolver.RouteMode.SERVICE)
                .map(ToolExecutorService::normalizeBaseUrl)
                .filter(base -> !base.isEmpty())
                .orElseThrow(() -> new IllegalStateException("route-manifest 未登记服务 "
                        + SCENARIO_OWNER_SERVICE + "，场景工具基址无法解析（fail-loud）"));
    }

    /** base-url 归一：去首尾空白与尾斜杠；null/空白视为未设置（返回空串）。 */
    private static String normalizeBaseUrl(String url) {
        if (url == null) return "";
        String trimmed = url.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
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
     * 供 {@link AgentLoopService} 与 W Agent 编排通道（{@code EngineEndpointInvoker}）调用。
     *
     * <p>#143/B5-1：{@code call.getVersion()} 必须一路带到工具定义解析（见
     * {@link #loadToolDefinition(String, String)}）—— W 契约目录按 {@code name+version}
     * 唯一定位且只有 active 版可执行，版本在这一跳丢掉 ⇒ 编排侧校完 active、咽喉仍解析不到。</p>
     */
    public ToolResult execute(ToolCall call) {
        return execute(call.getName(), call.getArguments(), call.getId(), call.getVersion());
    }

    /**
     * 执行工具调用。
     *
     * @param toolName  工具名称/code
     * @param arguments 调用参数
     * @return 工具执行结果
     */
    public ToolResult execute(String toolName, Map<String, Object> arguments) {
        return execute(toolName, arguments, null, null);
    }

    private ToolResult execute(String toolName, Map<String, Object> arguments, String callId,
                               String toolVersion) {
        long startNs = System.nanoTime();
        boolean viaRegistry = toolRegistry != null && toolRegistry.has(toolName);

        // registry 未命中才需要解析工具定义（场景声明 → W 契约 → DB → 内存 fallback）
        ToolDefinitionEntry def = viaRegistry ? null : loadToolDefinition(toolName, toolVersion);
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

        // ── 阶段 2.5: rateLimitEnforce —— ALLOW 签发的限流义务必须在 dispatch 之前消费（D-B4-21） ──
        ToolResult rateBlock = rateLimitEnforce(toolName, decision, callId, startNs);
        if (rateBlock != null) {
            return rateBlock;
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
            return obligationsApply(toolName, raw, decision, adjudication.subject(),
                    adjudication.asset(), startNs);
        } catch (TimeoutException e) {
            long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;
            String timeoutText = e.getMessage() == null || e.getMessage().isBlank()
                    ? "工具执行超时 (" + TIMEOUT_SECONDS + "s)" : e.getMessage();
            return buildClassifiedError(callId, toolName, AiErrorClass.TOOL_BACKEND_ERROR,
                    timeoutText, elapsedMs);
        } catch (ScenarioArgumentsException badArgs) {
            // F06-11 入参地板：缺参属客户端可修正态（INVALID_ARGUMENT），不得伪装成后端故障（502）
            long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;
            log.info("F06-11 场景工具入参地板拒绝: tool={} reason={}", toolName, badArgs.getMessage());
            return buildClassifiedError(callId, toolName, AiErrorClass.INVALID_ARGUMENT,
                    badArgs.getMessage(), elapsedMs);
        } catch (ScenarioEndpointUnresolvedException pendingBackend) {
            // 分册 09 v1.2 §十一 移交项的端点尚未上线 ⇒ 归 STUB_CAPABILITY（501，不重试），
            // 不归 TOOL_BACKEND_ERROR（502 可重试 1 次 + "数据服务异常请稍后重试"）——把一个不存在的能力
            // 报成一次可重试的服务故障，会诱导 Loop 无谓重试并把缺口洗成"临时抖动"（同 X-50 禁伪成功的动机）。
            // 缺口原文（含 HTTP 404 与移交项定位）只入日志，对外用固定模板（X-49 rawReason 禁外泄）。
            long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;
            log.warn("F06-11 场景工具声明端点未命中(属主能力未上线): tool={} reason={}", toolName,
                    pendingBackend.getMessage());
            return buildClassifiedError(callId, toolName, AiErrorClass.STUB_CAPABILITY,
                    pendingBackend.getMessage(), elapsedMs);
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

    /**
     * 裁决中间结果：{@code rejected} 非空即终止（DENY / FAIL_CLOSED），禁继续执行。
     *
     * <p>{@code asset}（#142/B4-3d-6，D-B4-28）是<b>阶段 1 就定形</b>的表资产基数：
     * 列面裁剪要知道"这次结果该按哪张表裁决"，而 SQL 原文在阶段 1 之后就不在作用域里了。
     * 让值对象随裁决结果一路带到阶段 4，结果回传段既不回头重解析 SQL，也不自带第二套
     * 表引用解析器 —— 两处解析对同一 SQL 给出不同表集时，"该裁哪张表"就成了第二个真值。</p>
     */
    private record Adjudication(ToolResult rejected, ToolDecision decision,
                                ToolAdjudicationRequest.Subject subject,
                                AiToolColumnSurfaceGate.Asset asset) {

        static Adjudication reject(ToolResult result) {
            return new Adjudication(result, null, null, AiToolColumnSurfaceGate.Asset.of(List.of()));
        }

        static Adjudication allow(ToolDecision decision, ToolAdjudicationRequest.Subject subject,
                                  AiToolColumnSurfaceGate.Asset asset) {
            return new Adjudication(null, decision, subject, asset);
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
        // 表资产基数与 ABAC 载荷同源（AgentToolPolicyGate 的唯一表引用提取器，D-B4-28）
        AiToolColumnSurfaceGate.Asset asset =
                AiToolColumnSurfaceGate.Asset.of(policyGate.controlledTables(sqlText));
        if (decision.allowed()) {
            return Adjudication.allow(decision, subject, asset);
        }
        AiErrorClass errorClass = AgentToolPolicyGate.errorClassFor(decision);
        if (errorClass == null) errorClass = AiErrorClass.GUARDRAIL_DENIED;
        log.warn("F06-01 工具被裁决闸拒绝: tool={} user={} verdict={} reason={}",
                toolName, subject.userId(), decision.verdict(), decision.reason());
        return Adjudication.reject(buildGuardrailError(callId, toolName, errorClass,
                decision.reason(), startNs, decision));
    }

    /** {@code action.operation} 判定：场景/W 契约工具取<b>声明值</b>，其余按写类名单与 HTTP 方法推断。 */
    private String inferOperation(String toolName, ToolDefinitionEntry def, Map<String, Object> arguments) {
        // F06-11 要点 1：read|write 是声明属性，禁按参数或方法猜测（否则同一次调用的裁决载荷会漂移）
        if (def != null && def.scenario != null) return def.scenario.operation();
        // #143/B5-1：契约工具的 operation 同样取声明值（由 WAgentContractToolSource 按
        // operation_class 折算：READ→read，SUGGEST/WRITE→write，宁严勿宽）
        if (def != null && def.contract != null) return def.contract.operation();
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

    // ── 阶段 2.5: rateLimitEnforce ────────────────────────────────────────

    /**
     * 消费 ALLOW 裁决签发的 {@code rateLimit} 义务（#142/B4-3d-4，差距 D-B4-21 的收口点）。
     *
     * <p>策略侧只在 {@code purpose=="ai"} 且放行时签发该义务，键形如
     * {@code ai.tool.<subjectId>}，键名与前缀的单源在
     * {@link com.chinacreator.gzcm.common.security.guardrail.GuardrailDecisionContract}。
     * 本腿在 dispatch <b>之前</b>：限流是"这次调用能否发起"的判断，放到结果回传段
     * （阶段 4）执行等于先花掉后端预算再拒绝。</p>
     *
     * <p>三态映射（禁折叠，§4.7.2 在执行面的延伸）：</p>
     * <ul>
     *   <li>未签发义务 → {@code null}（本层无阻断，且不计额）；</li>
     *   <li>窗口内配额用尽 → {@code GUARDRAIL_DENIED}（403，不重试；这是策略义务未满足，
     *       不是"护栏没说话"）；</li>
     *   <li>义务形态不合契约（键缺失／键面漂移／主体段为空）或消费方未装配
     *       → {@code GUARDRAIL_FAIL_CLOSED}（503；禁按自造键计额后放行，
     *       也禁把"有义务没人消费"重新退化成静默丢弃＝D-B4-21 的原形态）。</li>
     * </ul>
     *
     * <p>复用 {@code GUARDRAIL_DENIED} 而非新增 429 码的理由见差距文档 v1.0.21 ⑤c
     * （D.5 码表冻结 + {@code LLM_RATE_LIMITED} 自带"降级备用 provider"语义，
     * 用它会把护栏拒绝冒充成 provider 限流）。</p>
     *
     * @return 拒绝执行的 ToolResult；{@code null} 表示本层无阻断
     */
    private ToolResult rateLimitEnforce(String toolName, ToolDecision decision,
                                        String callId, long startNs) {
        ToolObligations obligations = decision == null ? null : decision.obligations();
        if (obligations == null || !obligations.declaresRateLimit()) {
            return null;
        }
        if (rateLimitGate == null) {
            log.warn("F06-02 限流义务消费方未装配，拒绝执行（fail-closed）: tool={} key={}",
                    toolName, obligations.rateLimitKey());
            return buildGuardrailError(callId, toolName, AiErrorClass.GUARDRAIL_FAIL_CLOSED,
                    "限流义务消费方不可用（AI_TOOL_RATE_LIMIT_GATE_UNWIRED），拒绝执行", startNs, decision);
        }
        AiToolRateLimitGate.RateOutcome outcome = rateLimitGate.enforce(obligations);
        switch (outcome.verdict()) {
            case DENIED:
                log.info("F06-02 限流义务拒绝工具调用（策略结论）: tool={} reason={}",
                        toolName, outcome.reason());
                return buildGuardrailError(callId, toolName, AiErrorClass.GUARDRAIL_DENIED,
                        "工具调用限流义务配额已用尽: " + outcome.reason(), startNs, decision);
            case FAIL_CLOSED:
                log.warn("F06-02 限流义务形态不合契约，拒绝执行（fail-closed）: tool={} reason={}",
                        toolName, outcome.reason());
                return buildGuardrailError(callId, toolName, AiErrorClass.GUARDRAIL_FAIL_CLOSED,
                        "限流义务无法消费: " + outcome.reason(), startNs, decision);
            case PASS:
            default:
                return null;
        }
    }

    // ── 阶段 4: obligationsApply ──────────────────────────────────────────

    /**
     * ALLOW 义务在结果回 LLM <b>之前</b>落实；执行不可用整条结果丢弃并 FAIL_CLOSED
     * （禁"先返回再说明"，F06-02 要点 2）。
     *
     * <p>#142/B4-3d-6（D-B4-28）后本阶段承担两条腿：OPA 义务（rowFilter/mask）与
     * <b>CLS 列面裁剪</b>。短路口径因此从"义务为空即返回"改为
     * "义务为空<b>且</b>无待裁列面才返回" —— 否则"策略没签发义务"会把列面腿整条旁路掉，
     * 而列面裁决与义务签发是两件独立的事（{@code /decision} 的 columns 段不依赖 obligations）。</p>
     *
     * <p>分态（§4.5.4「不可用 ≠ 无权限」，禁折叠）：</p>
     * <ul>
     *   <li>{@link ToolObligationApplier.Outcome#denied()} ⇒ {@link AiErrorClass#GUARDRAIL_DENIED}
     *       （403，策略结论，禁重试）；</li>
     *   <li>其余 {@code !applied} ⇒ {@link AiErrorClass#GUARDRAIL_FAIL_CLOSED}（503，没有可用结论）；</li>
     *   <li>执行器未装配且本调用确实需要列面裁决 ⇒ 503，理由取
     *       {@link AiToolColumnSurfaceGate#REASON_GATE_UNWIRED}（"列面腿没接线"这一缺陷形态
     *       必须在审计里可辨认，禁写成一句通用文案）。</li>
     * </ul>
     */
    private ToolResult obligationsApply(String toolName, ToolResult raw, ToolDecision decision,
                                        ToolAdjudicationRequest.Subject subject,
                                        AiToolColumnSurfaceGate.Asset asset, long startNs) {
        ToolObligations obligations = decision == null ? ToolObligations.none() : decision.obligations();
        boolean needsColumnScreen = asset != null && asset.requiresScreening();
        if (obligations.isEmpty() && !needsColumnScreen) {
            return raw;
        }
        if (obligationApplier == null) {
            log.warn("F06-02 obligations 执行器未装配，整条结果丢弃（fail-closed）: tool={} columnScreen={}",
                    toolName, needsColumnScreen);
            String reason = needsColumnScreen
                    ? "security obligations 执行器不可用，结果已丢弃（"
                            + AiToolColumnSurfaceGate.REASON_GATE_UNWIRED + "）"
                    : "security obligations 执行器不可用，结果已丢弃";
            return buildGuardrailError(raw.getToolCallId(), toolName, AiErrorClass.GUARDRAIL_FAIL_CLOSED,
                    reason, startNs, decision);
        }
        ToolObligationApplier.Outcome outcome =
                obligationApplier.apply(toolName, raw.getContent(), obligations, subject, asset);
        if (outcome.denied()) {
            log.info("B4-3d-6 列面策略拒绝工具结果（策略结论，不重试）: tool={} reason={}",
                    toolName, outcome.failReason());
            return buildGuardrailError(raw.getToolCallId(), toolName, AiErrorClass.GUARDRAIL_DENIED,
                    outcome.failReason(), startNs, decision);
        }
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
     * 加载工具定义：<b>场景受控工具声明优先</b>（F06-11），其次 W 契约目录（#143/B5-1），
     * 再次数据库，最后内存 fallback。
     *
     * <p>声明优先的次序是安全相关的：工具的 {@code operation}/{@code timeoutMs}/端点必须取
     * <b>声明值</b>，若同名 DB 行先命中，管理员配的 {@code tool_type} 会改写裁决载荷的
     * {@code action.operation}（写判成读即绕过 OPA 的写权限面）。</p>
     *
     * <p>W 契约这一源只在<b>带版本</b>时参与：契约唯一定位靠 {@code uniq(name, version)}，
     * 无版本即不猜"最新版"（{@code EngineEndpointInvoker} 已裁决的口径，两侧必须同判据）。</p>
     */
    ToolDefinitionEntry loadToolDefinition(String toolName, String toolVersion) {
        ToolDefinitionEntry scenario = loadScenarioDefinition(toolName);
        if (scenario != null) {
            return scenario;
        }
        ToolDefinitionEntry contract = loadContractDefinition(toolName, toolVersion);
        if (contract != null) {
            return contract;
        }
        ToolDefinitionEntry def = loadFromDatabase(toolName);
        if (def != null) {
            return def;
        }
        return fallbackTools.get(toolName);
    }

    /**
     * W 契约声明 → {@link ToolDefinitionEntry}（{@code toolType} 恒为 REST）。
     *
     * <p>接缝自身不判 active／前缀归属（那是 {@link IContractToolSource} 实现的地板，
     * 与 {@code PlanValidator}、{@code EngineEndpointInvoker} 同一词表单源）；
     * 这里只负责"拿到声明后按 REST 通道形态装好"。</p>
     */
    private ToolDefinitionEntry loadContractDefinition(String toolName, String toolVersion) {
        if (contractToolSource == null || toolVersion == null || toolVersion.isBlank()) {
            return null;
        }
        ContractToolDeclaration declared =
                contractToolSource.findActive(toolName, toolVersion).orElse(null);
        if (declared == null) {
            return null;
        }
        ToolDefinitionEntry entry = new ToolDefinitionEntry();
        entry.code = declared.name();
        entry.name = declared.name();
        entry.description = "W 契约工具 " + declared.name() + "@" + declared.version();
        entry.toolType = "REST";
        entry.endpointUrl = declared.endpointPath();
        entry.httpMethod = declared.httpMethod();
        entry.schemaJson = declared.inputSchemaJson();
        entry.contract = declared;
        return entry;
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
        // #143/B5-1：W 契约工具同口径取契约 timeout_ms —— 该列是 V236 INTEGER 列（V270 对同名列
        // 的 ADD COLUMN IF NOT EXISTS 恒为 no-op，DO 里 timeoutMsContract 无列可映射，已登记缺陷）
        long declaredBudgetMs = def.scenario != null ? def.scenario.timeoutMs()
                : def.contract == null || def.contract.timeoutMs() == null
                        ? TIMEOUT_SECONDS * 1000L : def.contract.timeoutMs();
        long budgetSeconds = Math.min(TIMEOUT_SECONDS, Math.max(1, (declaredBudgetMs + 999) / 1000));
        try {
            return future.get(budgetSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            // 预算文案必须报实际预算：契约/声明可低于 30s，固定印 "30s" 会把 1s 的声明缺口说成咽喉超时
            throw new TimeoutException("工具执行超时 (" + budgetSeconds + "s)");
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
        // #143/B5-1：W 契约工具同纪律 —— 端点/方法/参数面全部来自契约声明
        if (def.contract != null) {
            return executeContractRest(def, arguments);
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
        String url = resolveScenarioBaseUrl() + path;
        HttpMethod method = HttpMethod.valueOf(declared.httpMethod().toUpperCase(Locale.ROOT));
        Set<String> declaredArgs = declared.argsSchema().keySet();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(EcosTraceContext.HEADER_REQUEST_ID, AiTraceSupport.currentTraceId());

        HttpEntity<?> entity;
        if (method == HttpMethod.GET || method == HttpMethod.DELETE) {
            String query = buildDeclaredQuery(declaredArgs, declared.backendEndpoint(), arguments);
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
        ResponseEntity<String> response;
        try {
            response = restTemplate.exchange(url, method, entity, String.class);
        } catch (HttpStatusCodeException e) {
            // 404 有两种真身：声明端点尚未实现（分册 09 移交项），或端点在位但资源不存在。
            // 二者都必须以真实错误上浮（X-50 禁 mock 成功），差别只在诊断文本 —— 移交项在此点明缺口原因。
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                String hint = ScenarioToolCatalog.missingBackendHint(declared.toolKey());
                if (hint != null) {
                    throw new ScenarioEndpointUnresolvedException("场景工具声明端点未命中(404): "
                            + method + " " + path + " —— 候选原因: " + hint, e);
                }
            }
            throw e;
        }
        String body = response.getBody();
        return body != null ? body : "";
    }

    /**
     * GET 形态的 query string：只带声明里的参数，且不重复路径已消费的名字。
     *
     * <p>入参必须是<b>占位符替换前的模板路径</b>（{@code backendEndpoint}/{@code endpointPath}）：
     * "已消费"的判据是模板里存在 {@code \{key\}}；传替换后的实际路径会让该判据恒不成立，
     * 于是路径参数被重复编进 query string（同一值出现两处 = 后端可能读到不一致口径）。</p>
     */
    private String buildDeclaredQuery(Set<String> declaredArgs, String templatePath,
                                      Map<String, Object> arguments) {
        if (arguments == null || arguments.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Object> e : arguments.entrySet()) {
            String key = e.getKey();
            if (!declaredArgs.contains(key) || e.getValue() == null) continue;
            if (templatePath.contains("{" + key + "}")) continue;
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

    /**
     * 场景工具声明端点未实现（分册 09 v1.2 §十一 移交项）—— 归 {@code STUB_CAPABILITY}/501
     * （能力尚未上线，不可重试），原文只入日志；绝不降级成成功（X-50）。
     */
    static final class ScenarioEndpointUnresolvedException extends RuntimeException {
        ScenarioEndpointUnresolvedException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    // ── #143/B5-1 W 契约工具执行 ───────────────────────────────────────────

    /**
     * 契约工具分发（契约声明 → 唯一 REST 出向调用），与 {@link #executeScenarioRest} 同三条硬约束：
     * 基址由服务端解析（入参不可左右 scheme/host）、必填地板先于出向、载荷只带声明里的参数名。
     *
     * <p>差别只在声明形态：契约行的参数面是 JSON-Schema（{@code properties}／{@code required}），
     * 不是场景声明的内部参数表，因此这里就地解析 schema 得到参数白名单。</p>
     *
     * <p><b>占位符地板</b>：路径里的 {@code \{x\}} 必须是 {@code properties} 已声明的属性名。
     * V266 现存的 {@code check_metric_caliber} 用 {@code \{code\}} 而 schema 声明
     * {@code metricCode} —— 属目录行数据缺陷，本通道 fail-closed 拒绝出向，禁把字面
     * {@code \{code\}} 打到后端换 404（那会把"目录配错"洗成"能力未上线"，并把缺陷从日志里抹平）。
     * 分类仍落 {@code INVALID_ARGUMENT}（{@code RetryKind.NONE}，不诱导 Loop 重试），
     * 且诊断文本点明是目录侧缺陷而非调用方缺参。</p>
     */
    private String executeContractRest(ToolDefinitionEntry def, Map<String, Object> arguments)
            throws Exception {
        ContractToolDeclaration declared = def.contract;
        Map<String, Object> schema = parseContractSchema(declared);
        Set<String> declaredArgs = contractPropertyNames(schema);

        for (String placeholder : contractPathPlaceholders(declared.endpointPath())) {
            if (!declaredArgs.contains(placeholder)) {
                throw new ScenarioArgumentsException("契约工具路径占位符未在 input_schema.properties 声明"
                        + "（目录行缺陷，需订正 endpoint 或 schema）: " + declared.name() + " {" + placeholder + "}");
            }
        }
        for (String required : contractRequiredNames(schema)) {
            Object value = arguments == null ? null : arguments.get(required);
            if (value == null || String.valueOf(value).isBlank()) {
                throw new ScenarioArgumentsException("契约工具缺少必填参数: " + declared.name() + " " + required);
            }
        }

        String path = resolveUrlParams(declared.endpointPath(), arguments);
        if (path.indexOf('{') >= 0 || path.indexOf('}') >= 0) {
            // 走到这里说明占位符有声明但入参没给值 —— 同样禁止把未填充的字面量出向
            throw new ScenarioArgumentsException("契约工具路径占位符未填满: " + declared.name());
        }

        String url = resolveContractBaseUrl() + path;
        HttpMethod method = HttpMethod.valueOf(declared.httpMethod().toUpperCase(Locale.ROOT));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(EcosTraceContext.HEADER_REQUEST_ID, AiTraceSupport.currentTraceId());

        HttpEntity<?> entity;
        if (method == HttpMethod.GET || method == HttpMethod.DELETE) {
            String query = buildDeclaredQuery(declaredArgs, declared.endpointPath(), arguments);
            entity = new HttpEntity<>(headers);
            if (!query.isEmpty()) url = url + (url.contains("?") ? "&" : "?") + query;
        } else {
            Map<String, Object> payload = new LinkedHashMap<>();
            if (arguments != null) {
                for (Map.Entry<String, Object> e : arguments.entrySet()) {
                    if (declaredArgs.contains(e.getKey())) payload.put(e.getKey(), e.getValue());
                }
            }
            entity = new HttpEntity<>(payload, headers);
        }

        log.info("B5-1 契约工具出向: tool={} version={} method={} path={} operation={}",
                declared.name(), declared.version(), method, path, declared.operation());
        ResponseEntity<String> response;
        try {
            response = restTemplate.exchange(url, method, entity, String.class);
        } catch (HttpStatusCodeException e) {
            // 与场景通道同一分类：404 = 声明端点尚未落地（V266 备注列已声明"设计路径待复核"三行）
            // ⇒ STUB_CAPABILITY(501, 不重试)，不归 502 可重试抖动（X-50 禁把缺口洗成临时故障）
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw new ScenarioEndpointUnresolvedException("契约工具声明端点未命中(404): "
                        + method + " " + path + " —— 契约行 endpoint 属设计路径或后端未上线，需登记缺口", e);
            }
            throw e;
        }
        String body = response.getBody();
        return body != null ? body : "";
    }

    /** 契约 {@code input_schema_json} 解析：空／非对象一律 fail-loud（禁"读不懂就当无参数面"放行）。 */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseContractSchema(ContractToolDeclaration declared) {
        String schemaJson = declared.inputSchemaJson();
        if (schemaJson == null || schemaJson.isBlank()) {
            throw new IllegalStateException("契约工具 input_schema_json 为空，参数面无法解析（fail-closed）: "
                    + declared.name());
        }
        try {
            if (objectMapper.readValue(schemaJson, Object.class) instanceof Map<?, ?> map) {
                return (Map<String, Object>) map;
            }
        } catch (Exception e) {
            throw new IllegalStateException("契约工具 input_schema_json 非法 JSON（fail-closed）: "
                    + declared.name(), e);
        }
        throw new IllegalStateException("契约工具 input_schema_json 不是 JSON 对象（fail-closed）: "
                + declared.name());
    }

    /** {@code properties} 键集 = 出向允许携带的参数名（未声明的入参一律丢弃，与场景通道同纪律）。 */
    private Set<String> contractPropertyNames(Map<String, Object> schema) {
        Set<String> names = new LinkedHashSet<>();
        if (schema.get("properties") instanceof Map<?, ?> props) {
            for (Object key : props.keySet()) {
                if (key instanceof String s && !s.isBlank()) names.add(s);
            }
        }
        return names;
    }

    /** {@code required} 数组（JSON-Schema 形态；缺失即无必填面）。 */
    private List<String> contractRequiredNames(Map<String, Object> schema) {
        List<String> required = new ArrayList<>();
        if (schema.get("required") instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof String s && !s.isBlank()) required.add(s);
            }
        }
        return required;
    }

    /** 路径里的 {@code \{name\}} 清单（不用正则，避免为一条地板规则再引 Matcher）。 */
    private List<String> contractPathPlaceholders(String path) {
        List<String> out = new ArrayList<>();
        if (path == null) return out;
        int from = 0;
        while (true) {
            int open = path.indexOf('{', from);
            if (open < 0) return out;
            int close = path.indexOf('}', open + 1);
            if (close < 0) return out;
            String name = path.substring(open + 1, close).trim();
            if (!name.isEmpty()) out.add(name);
            from = close + 1;
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

        /**
         * #143/B5-1 W 契约工具的声明体（非契约工具恒为 null）。
         *
         * <p>存在即代表：端点/方法/参数面全部取自 {@code ecos_wagent_tool_contract} 行，
         * 执行只能走 {@link #executeContractRest} 的服务端基址通道，{@code operation} 与
         * 超时预算取契约值 —— 与 {@link #scenario} 同纪律，二者互斥（声明解析按场景优先）。</p>
         */
        ContractToolDeclaration contract;
    }
}
