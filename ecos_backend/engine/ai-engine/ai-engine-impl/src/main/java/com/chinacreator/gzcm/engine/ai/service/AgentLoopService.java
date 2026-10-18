package com.chinacreator.gzcm.engine.ai.service;

import com.chinacreator.gzcm.common.error.AiError;
import com.chinacreator.gzcm.common.error.AiErrorClass;
import com.chinacreator.gzcm.common.trace.AgentLoopTrace;
import com.chinacreator.gzcm.engine.ai.GuardrailsService;
import com.chinacreator.gzcm.engine.ai.SkillService;
import com.chinacreator.gzcm.engine.ai.entity.SkillEntity;
import com.chinacreator.gzcm.engine.ai.tool.CaliberStatusSource;
import com.chinacreator.gzcm.engine.ai.tool.CitationPostCheckService;
import com.chinacreator.gzcm.engine.ai.tool.RefusalRulesService;
import com.chinacreator.gzcm.engine.ai.tool.ScenarioToolCatalog;
import com.chinacreator.gzcm.engine.ai.tool.ToolCitation;
import com.chinacreator.gzcm.engine.ai.trace.AiTraceSupport;
import com.chinacreator.gzcm.runtime.llm.gateway.ChatMessage;
import com.chinacreator.gzcm.runtime.llm.gateway.ChatRequest;
import com.chinacreator.gzcm.runtime.llm.gateway.ChatResponse;
import com.chinacreator.gzcm.runtime.llm.gateway.LLMGateway;
import com.chinacreator.gzcm.runtime.llm.session.AgentSession;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Agent 推理循环 — 多轮工具调用循环（think → tool_call → observe → think）。
 * <p>
 * 替代 Hermes Agent Loop 的纯 Java 实现，最多 5 轮推理：
 * </p>
 * <ol>
 *   <li>构建初始 messages（system prompt + 历史消息 + 当前用户消息）</li>
 *   <li>调用 LLM → 解析响应</li>
 *   <li>如果是 tool_call → 执行工具 → 工具结果塞回 messages → 回到步骤 2</li>
 *   <li>如果是 final → 记录并返回 AgentLoopResult</li>
 *   <li>超过 5 轮 → 返回超限错误</li>
 * </ol>
 *
 * <h3>注入点</h3>
 * <ul>
 *   <li>{@link LLMGateway} — 唯一 LLM 出口（F06-05 / 铁律 §2.5-2；旧
 *       {@code LLMGatewayService} 门面在本类已移除，X-4 双注入消除）</li>
 *   <li>{@link ToolExecutorService} — 工具执行器（required=false，T0.2 实现）</li>
 *   <li>{@link ToolRegistry} — 工具注册中心，用于 Schema 校验（required=false）</li>
 * </ul>
 */
@Service
public class AgentLoopService {

    private static final Logger log = LoggerFactory.getLogger(AgentLoopService.class);

    /**
     * Agent Loop 结构化行专用 logger（{@link AgentLoopTrace#LOGGER_NAME}）。
     *
     * <p>与业务日志分通道：logback 侧该 logger 只挂文件 appender，
     * 运行回放（F06-08）读取端因此无需从混合日志里挑行。</p>
     */
    private static final Logger agentLoopLog = LoggerFactory.getLogger(AgentLoopTrace.LOGGER_NAME);

    /** Agent 推理最大轮次 */
    private static final int MAX_TURNS = 5;

    /** 工具调用超时（毫秒） */
    private static final long TOOL_TIMEOUT_MS = 30_000L;

    /** 工具结果最大字符数（超过截断） */
    private static final int TOOL_RESULT_MAX_CHARS = 2000;

    /** F06-07 要点 3：配额超限的可读消息（占位 {used}/{limit}/{traceId}），原文与 reason 只入日志 */
    private static final String QUOTA_MESSAGE_TEMPLATE =
            "本次会话的 token 用量已达上限（已用 {used} / 上限 {limit}），为避免继续消耗已停止本次推理"
                    + "（诊断码 {traceId}）。";

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private LLMGateway llmGateway;

    @Autowired(required = false)
    private ToolExecutorService toolExecutorService;

    @Autowired(required = false)
    private ToolRegistry toolRegistry;

    @Autowired(required = false)
    private SkillService skillService;

    @Autowired(required = false)
    private MemoryExtractor memoryExtractor;

    @Autowired
    private AgentConfigResolver agentConfigResolver;

    /** 护栏服务（F06-04 要点 2：必填 —— 未装配即 FAIL_CLOSED，不再有判空跳过分支） */
    @Autowired
    private GuardrailsService guardrailsService;

    @Autowired(required = false)
    private AgentMetricsCollector metricsCollector;

    /**
     * 会话 token 配额（F06-07 要点 3）。必填语义由本类 {@link #checkSessionQuota} 承担：
     * 未装配 = 无法判定，按"不启用"处理并在首轮 WARN 一次（配额是成本控制，非安全红线）。
     */
    @Autowired(required = false)
    private SessionTokenQuotaService sessionTokenQuota;

    /**
     * 会话上下文压缩（F06-09）。required=false 只服务单测装配；生产缺失时
     * {@link #assessCompression} 记 ERROR 并以 {@code compressSkipped=true} 上报（禁静默）。
     */
    @Autowired(required = false)
    private SessionCompressionService sessionCompression;

    /**
     * F06-13 三条拒绝规则（咽喉前置判定）。required=false 只服务单测装配；
     * 生产缺失时 {@link #refusalPreAdjudication} 记 ERROR 并按规则 1 fail-closed 澄清
     * （无法判定口径 ≠ 可以放行数值路径）。
     */
    @Autowired(required = false)
    private RefusalRulesService refusalRules;

    /** F06-13 规则 1 的口径状态源（不可判 = 未批准）。 */
    @Autowired(required = false)
    private CaliberStatusSource caliberStatus;

    /** F06-12 引用后校验器。required=false 只服务单测装配；缺失时本轮不做引用判定并记 ERROR。 */
    @Autowired(required = false)
    private CitationPostCheckService citationCheck;

    /** 正则：匹配 function_name(arg1=val1, arg2=val2) 模式 */
    private static final Pattern FUNCTION_CALL_PATTERN =
            Pattern.compile("(\\w+)\\s*\\(\\s*([^)]*)\\s*\\)");

    // ─── Public API ────────────────────────────────────────────────────

    /**
     * 执行 Agent 推理循环。
     *
     * @param config      Agent 配置（system prompt / model / temperature / maxTokens）
     * @param userMessage 用户消息
     * @param session     当前 AgentSession（含 systemPrompt）
     * @return AgentLoopResult 推理结果
     */
    public AgentLoopResult run(AgentLoopConfig config, String userMessage, AgentSession session) {
        // F06-09 ⑥：压缩判定在循环内产生，结果统一附着到本轮返回的 AgentLoopResult（done 帧/状态条）
        CompressionReport compression = new CompressionReport();
        AgentLoopResult result = doRun(config, userMessage, session, compression);
        compression.applyTo(result);
        return result;
    }

    private AgentLoopResult doRun(AgentLoopConfig config, String userMessage, AgentSession session,
                                  CompressionReport compression) {
        long startTime = System.currentTimeMillis();
        String sessionId = session != null ? session.getSessionId() : null;
        String agentId = session != null ? session.getProfileName() : "default";
        int totalTokens = 0;
        List<Map<String, Object>> toolCallRecords = new ArrayList<>();
        // F06-12 引用后校验的比对基准（工具返回体原文）+ F06-13 规则 3 的失败分类计数
        List<String> toolResultBodies = new ArrayList<>();
        List<AiErrorClass> toolFailureClasses = new ArrayList<>();

        // F06-06 要点 1：traceId 唯一源 = MDC（入口过滤器写入）；AgentTracer 只登记本地调试视图
        String traceId = AgentTracer.newTrace();

        if (llmGateway == null) {
            step(agentId, traceId, AgentLoopTrace.Op.PLAN, 0, null, null,
                    AgentLoopTrace.UNKNOWN, AgentLoopTrace.UNKNOWN,
                    AgentLoopTrace.UNKNOWN, AgentLoopTrace.Decision.FAIL_CLOSED);
            AgentLoopResult r = AgentLoopResult.error(sessionId, "LLM Gateway 未就绪", 0);
            // F06-14：网关未装配属下游服务故障（502），终止态必须带分类，禁无码失败上浮
            return withClassification(r, AiErrorClass.TOOL_BACKEND_ERROR, traceId);
        }

        // T0b-1: 输入护栏 —— 必经 security-engine（F06-04：判空跳过与 catch→warn 两个 fail-open 分支已消灭）
        if (userMessage != null) {
            long guardStart = System.currentTimeMillis();
            if (guardrailsService == null) {
                log.error("[AgentLoop] GuardrailsService 未装配，输入护栏无法裁决 → FAIL_CLOSED: session={}", sessionId);
                step(agentId, traceId, AgentLoopTrace.Op.PLAN, 0, null, null,
                        AgentLoopTrace.UNKNOWN, AgentLoopTrace.UNKNOWN,
                        System.currentTimeMillis() - guardStart, AgentLoopTrace.Decision.FAIL_CLOSED);
                return guardrailFailure(sessionId, traceId, AiErrorClass.GUARDRAIL_FAIL_CLOSED,
                        "护栏服务未装配（GuardrailsService Bean 缺失），禁止未经裁决的模型调用", null);
            }
            Map<String, Object> inputCheck;
            try {
                inputCheck = guardrailsService.validate(Map.of(
                        "llmOutput", userMessage,
                        "stage", GuardrailsServiceImpl.STAGE_INPUT));
            } catch (Exception e) {
                log.error("[AgentLoop] 输入护栏调用异常 → GUARDRAIL_FAIL_CLOSED: session={} err={}",
                        sessionId, e.getMessage(), e);
                step(agentId, traceId, AgentLoopTrace.Op.PLAN, 0, null, null,
                        AgentLoopTrace.UNKNOWN, AgentLoopTrace.UNKNOWN,
                        System.currentTimeMillis() - guardStart, AgentLoopTrace.Decision.FAIL_CLOSED);
                return guardrailFailure(sessionId, traceId, AiErrorClass.GUARDRAIL_FAIL_CLOSED,
                        "输入护栏调用异常: " + e.getMessage(), e);
            }
            if (!Boolean.TRUE.equals(inputCheck.get("passed"))) {
                log.warn("[AgentLoop] 输入护栏拦截 session={} violations={}", sessionId, inputCheck.get("violations"));
                step(agentId, traceId, AgentLoopTrace.Op.PLAN, 0, null, null,
                        AgentLoopTrace.UNKNOWN, AgentLoopTrace.UNKNOWN,
                        System.currentTimeMillis() - guardStart, AgentLoopTrace.Decision.DENY);
                return guardrailBlocked(sessionId, traceId, inputCheck);
            }
        }

        // T0c-1: 上下文压缩 —— 已改为 token 阈值语义，须在模型解析后判定（见下方 assessCompression）

        // ── 三层配置解析：L1(yaml) → L2(builtin模板) → L3(实例) → 运行时覆盖 ──
        Map<String, Object> requestOverrides = configToOverrides(config);
        AgentLoopConfig resolvedConfig = agentConfigResolver.resolve(agentId, requestOverrides);
        log.info("[AgentLoop] Resolved config for agent '{}': {}", agentId, resolvedConfig);

        // F06-13：三条拒绝规则的前置判定 —— 命中即返回，禁进 LLM 数值路径（非提示词祈使）
        AgentLoopResult refusedResult = refusalPreAdjudication(
                agentId, traceId, sessionId, userMessage, resolvedConfig, startTime);
        if (refusedResult != null) {
            return refusedResult;
        }

        // F06-09：入模前唯一压缩判定点（modelLimit 经 llm-gateway 目录读，禁引擎侧兜底常量）
        assessCompression(compression, sessionId, resolvedConfig.getModel(), traceId);

        // 使用解析后的 maxIterations 和 timeout
        int maxTurns = resolvedConfig.getMaxIterations() != null ? resolvedConfig.getMaxIterations() : MAX_TURNS;

        // 1. 构建初始 messages
        List<Message> messages = buildInitialMessages(resolvedConfig, userMessage, session);

        // F06-06 要点 3：plan 步骤恰一条结构化行（输入护栏通过 → ALLOW；无输入消息 → NA）
        step(agentId, traceId, AgentLoopTrace.Op.PLAN, 0, null, resolvedConfig.getModel(),
                AgentLoopTrace.UNKNOWN, AgentLoopTrace.UNKNOWN,
                System.currentTimeMillis() - startTime,
                userMessage != null ? AgentLoopTrace.Decision.ALLOW : AgentLoopTrace.Decision.NA);

        // 2. think → tool → observe 循环
        for (int turn = 1; turn <= maxTurns; turn++) {
            log.info("[AgentLoop] turn={}/{} session={} trace={}", turn, maxTurns, sessionId, traceId);

            // F06-07 要点 3：每轮前置配额判定 —— 超限即终止，禁继续静默消耗（用量真值来自网关计量点）
            AgentLoopResult quotaBlocked = checkSessionQuota(
                    agentId, traceId, sessionId, resolvedConfig, turn, totalTokens, startTime);
            if (quotaBlocked != null) {
                return quotaBlocked;
            }

            // T0a: 记录 think 阶段开始
            long thinkStart = System.currentTimeMillis();

            // 2a. 调用 LLM
            ChatResponse resp = callLLM(messages, resolvedConfig, sessionId);
            long thinkElapsed = System.currentTimeMillis() - thinkStart;
            emitLlmCallStep(agentId, traceId, resolvedConfig, turn, resp, thinkElapsed);

            if (resp == null || !resp.isSuccess()) {
                // F06-14 要点 2：LLM 出口的有界退避与降备用 provider 已在网关单源结算，
                // 引擎侧禁再自旋（X-47/X-88 的一次性 ad-hoc 重试已废）。失败面只渲染分类模板
                // （含诊断码），原始原因仅入日志（X-49）。
                AiError llmError = ErrorCodeMapper.classify(resp, traceId);
                AiErrorClass llmClass = llmError.errorClass();
                logAtDeclaredLevel(llmClass,
                        "[AgentLoop] LLM call failed at turn={}: class={} code={} gatewayRetries={} raw={}",
                        null, turn, llmClass.name(), llmError.errorCode(),
                        llmError.attemptsMade(), llmError.rawReason());
                AgentLoopResult failure = AgentLoopResult.error(sessionId, llmError.userMessage(),
                        totalTokens);
                failure.setErrorCode(llmError.errorCode());
                failure.setErrorClass(llmClass.name());
                failure.setRetryable(llmError.retryable());
                failure.setTraceId(traceId);
                return failure;
            }

            if (resp.hasUsage()) {
                totalTokens += resp.getTokensInput() + resp.getTokensOutput();
            }

            // 2b. 解析响应 — 判断是 tool_call 还是 final
            String content = resp.getContent();
            List<ToolCall> toolCalls = parseToolCalls(resp);

            if (toolCalls != null && !toolCalls.isEmpty()) {
                // ── 工具调用分支 ─────────────────────────────────
                log.info("[AgentLoop] turn={} detected {} tool call(s): {}",
                        turn, toolCalls.size(),
                        toolCalls.stream().map(ToolCall::getName).toList());

                // 添加 assistant 消息（含 tool_calls）
                messages.add(Message.assistant(toolCalls));

                // 逐个执行工具
                for (ToolCall tc : toolCalls) {
                    // T0a: 记录 act 阶段开始
                    long actStart = System.currentTimeMillis();

                    ToolExecutorService.ToolResult tr = executeTool(tc, resolvedConfig);
                    long actElapsed = System.currentTimeMillis() - actStart;
                    recordToolOutcome(tr, toolResultBodies, toolFailureClasses);

                    // T0a: trace 记录 + F06-06 结构化行（tool_call 一次；裁决态来自护栏咽喉，缺失记 NA）
                    AgentLoopTrace.Decision toolDecision = decisionOf(tr.getDecision());
                    step(agentId, traceId, AgentLoopTrace.Op.TOOL_CALL, turn, tc.getName(),
                            resolvedConfig.getModel(),
                            AgentLoopTrace.UNKNOWN, AgentLoopTrace.UNKNOWN,
                            actElapsed, toolDecision);
                    step(agentId, traceId, AgentLoopTrace.Op.TOOL_RESULT, turn, tc.getName(),
                            resolvedConfig.getModel(),
                            AgentLoopTrace.UNKNOWN, AgentLoopTrace.UNKNOWN,
                            tr.getElapsedMs() > 0 ? tr.getElapsedMs() : AgentLoopTrace.UNKNOWN,
                            toolDecision);
                    // T0a-3: 慢查询告警
                    if (metricsCollector != null) {
                        metricsCollector.alertSlow(agentId, traceId, "tool_call", actElapsed);
                    }

                    // 记录到 toolCallRecords
                    Map<String, Object> record = new LinkedHashMap<>();
                    record.put("turn", turn);
                    record.put("toolName", tc.getName());
                    record.put("toolCallId", tc.getId());
                    record.put("durationMs", actElapsed);
                    record.put("success", tr.isSuccess());
                    record.put("resultSnippet", truncate(tr.getContent(), 200));
                    // B5-2 承流面取用：裁决态在此映射一次（映射单源=decisionOf），
                    // 引用块是上游由工具返回体确定性渲染的产物，承流侧只搬运不重算
                    record.put("decision", toolDecision.name());
                    if (tr.getCitationBlock() != null && !tr.getCitationBlock().isBlank()) {
                        record.put("citationBlock", tr.getCitationBlock());
                    }
                    toolCallRecords.add(record);

                    // 工具结果塞回 messages
                    messages.add(Message.toolResult(tr));

                    log.info("[AgentLoop] turn={} tool={} completed in {}ms success={}",
                            turn, tc.getName(), actElapsed, tr.isSuccess());

                    if (session != null) {
                        session.touch();
                    }
                }

                // continue → 回到循环顶部，LLM 看到工具结果后继续推理
                continue;
            }

            // ── 最终回复分支 ─────────────────────────────────
            log.info("[AgentLoop] turn={} final response ({} chars, {} tokens)",
                    turn,
                    content != null ? content.length() : 0,
                    totalTokens);

            if (session != null) {
                session.touch();
            }

            // F06-13 规则 3：本轮工具全部失败 → 只渲染 F06-14 分类结论，禁编造、禁 F06-01 之外兜底文案
            if (allToolCallsFailed(toolCallRecords)) {
                AiErrorClass dominant = dominantFailureClass(toolFailureClasses);
                log.warn("[AgentLoop] 工具全部失败 → 按分类渲染拒绝答复: class={} session={}", dominant, sessionId);
                step(agentId, traceId, AgentLoopTrace.Op.FINAL, turn, null, resolvedConfig.getModel(),
                        usageTokens(resp, true), usageTokens(resp, false),
                        System.currentTimeMillis() - startTime, AgentLoopTrace.Decision.DENY);
                if (metricsCollector != null) {
                    metricsCollector.record(agentId, "run", false,
                            System.currentTimeMillis() - startTime, 0, 0, traceId);
                }
                return allToolsFailedResult(sessionId, traceId, dominant,
                        totalTokens, toolCallRecords);
            }

            // F06-12：数值引用后校验 —— 确定性代码，先于输出护栏（护栏只脱敏，不判引用）
            String answerForGuardrail = content;
            CitationSnapshot citation = new CitationSnapshot();
            // 校验面 = 有取数的一轮 ∪ 场景受控工具白名单的一轮（PRD §4.3-2 诱导项：模型可以
            // 干脆不调工具直接报数，只按"有工具结果"设闸会留这条缝）
            boolean citationScope = !toolResultBodies.isEmpty()
                    || ScenarioToolCatalog.isScenarioWhitelist(resolvedConfig.getToolWhitelist());
            if (citationCheck == null) {
                if (citationScope) {
                    log.error("[AgentLoop] CitationPostCheckService 未装配：本轮属引用校验面却无法校验，"
                            + "本轮引用状态不上报（禁伪造默认值）session={}", sessionId);
                }
            } else if (citationScope && answerForGuardrail != null) {
                CitationPostCheckService.PostCheckResult pc =
                        citationCheck.check(answerForGuardrail, toolResultBodies);
                while (!pc.passed() && citationCheck.canRegenerate(citation.regenerations)) {
                    citation.regenerations++;
                    log.warn("[AgentLoop] 引用后校验未通过 → 重生成 {}/{}: {} session={}",
                            citation.regenerations, CitationPostCheckService.MAX_REGENERATIONS,
                            pc.diagnostic(), sessionId);
                    long regenStart = System.currentTimeMillis();
                    messages.add(Message.user(CitationPostCheckService.REGENERATION_INSTRUCTION));
                    ChatResponse regen = callLLM(messages, resolvedConfig, sessionId);
                    emitLlmCallStep(agentId, traceId, resolvedConfig, turn, regen,
                            System.currentTimeMillis() - regenStart);
                    if (regen != null && regen.isSuccess()) {
                        if (regen.hasUsage()) {
                            totalTokens += regen.getTokensInput() + regen.getTokensOutput();
                        }
                        if (regen.getContent() != null) {
                            answerForGuardrail = regen.getContent();
                        }
                    }
                    pc = citationCheck.check(answerForGuardrail, toolResultBodies);
                }
                citation.assessed = true;
                citation.complete = pc.passed();
                if (pc.passed()) {
                    citation.block = ToolCitation.firstBlock(answerForGuardrail);
                } else {
                    citation.violation = String.join(",", pc.violations());
                    step(agentId, traceId, AgentLoopTrace.Op.FINAL, turn, null, resolvedConfig.getModel(),
                            usageTokens(resp, true), usageTokens(resp, false),
                            System.currentTimeMillis() - startTime, AgentLoopTrace.Decision.DENY);
                    if (metricsCollector != null) {
                        metricsCollector.record(agentId, "run", false,
                                System.currentTimeMillis() - startTime, 0, 0, traceId);
                    }
                    return citationFallbackResult(sessionId, traceId, citationCheck,
                            citation, totalTokens, toolCallRecords);
                }
            }

            // T0b-2: 输出护栏 —— 必经 security-engine；命中保留整段替换文案（留痕事件已在护栏内部发出）
            String finalContent = answerForGuardrail;
            AgentLoopTrace.Decision outputDecision = AgentLoopTrace.Decision.NA;
            if (guardrailsService == null) {
                log.error("[AgentLoop] GuardrailsService 未装配，模型输出不得未经检查返回 → FAIL_CLOSED: session={}", sessionId);
                step(agentId, traceId, AgentLoopTrace.Op.FINAL, turn, null, resolvedConfig.getModel(),
                        usageTokens(resp, true), usageTokens(resp, false),
                        System.currentTimeMillis() - startTime, AgentLoopTrace.Decision.FAIL_CLOSED);
                return guardrailFailure(sessionId, traceId, AiErrorClass.GUARDRAIL_FAIL_CLOSED,
                        "护栏服务未装配（GuardrailsService Bean 缺失）", null);
            }
            if (finalContent != null) {
                Map<String, Object> outputCheck;
                try {
                    outputCheck = guardrailsService.validate(Map.of(
                            "llmOutput", finalContent,
                            "stage", GuardrailsServiceImpl.STAGE_OUTPUT));
                } catch (Exception e) {
                    log.error("[AgentLoop] 输出护栏调用异常 → GUARDRAIL_FAIL_CLOSED: session={} err={}",
                            sessionId, e.getMessage(), e);
                    step(agentId, traceId, AgentLoopTrace.Op.FINAL, turn, null, resolvedConfig.getModel(),
                            usageTokens(resp, true), usageTokens(resp, false),
                            System.currentTimeMillis() - startTime, AgentLoopTrace.Decision.FAIL_CLOSED);
                    return guardrailFailure(sessionId, traceId, AiErrorClass.GUARDRAIL_FAIL_CLOSED,
                            "输出护栏调用异常: " + e.getMessage(), e);
                }
                if (!Boolean.TRUE.equals(outputCheck.get("passed"))) {
                    if (isGuardrailFailClosed(outputCheck)) {
                        // 不可用 ≠ 命中：不可用整体失败（503），命中才做整段替换（403 语义）
                        step(agentId, traceId, AgentLoopTrace.Op.FINAL, turn, null, resolvedConfig.getModel(),
                                usageTokens(resp, true), usageTokens(resp, false),
                                System.currentTimeMillis() - startTime, AgentLoopTrace.Decision.FAIL_CLOSED);
                        return guardrailBlocked(sessionId, traceId, outputCheck);
                    }
                    log.warn("[AgentLoop] 输出护栏拦截 session={} violations={}",
                            sessionId, outputCheck.get("violations"));
                    finalContent = GuardrailsServiceImpl.FILTERED_PLACEHOLDER;
                    outputDecision = AgentLoopTrace.Decision.DENY;
                } else if (outputCheck.get("content") instanceof String masked && !masked.isEmpty()) {
                    // security 下发的脱敏结果在返回给用户前生效（F06-02 同源纪律）
                    finalContent = masked;
                    outputDecision = AgentLoopTrace.Decision.ALLOW;
                } else {
                    outputDecision = AgentLoopTrace.Decision.ALLOW;
                }
            }

            // F06-06 要点 3：final 步骤行（latency = 整轮累计；token 取本轮 LLM 真值，缺失写 -1）
            step(agentId, traceId, AgentLoopTrace.Op.FINAL, turn, null, resolvedConfig.getModel(),
                    usageTokens(resp, true), usageTokens(resp, false),
                    System.currentTimeMillis() - startTime, outputDecision);

            // T0a-2: 记录 metrics
            if (metricsCollector != null) {
                metricsCollector.record(agentId, "run", true,
                        System.currentTimeMillis() - startTime,
                        resp.getTokensInput(), resp.getTokensOutput(), traceId);
            }

            AgentLoopResult result = AgentLoopResult.success(finalContent, turn, sessionId, totalTokens, toolCallRecords);
            result.setTraceId(traceId);
            result.setAnswerType(RefusalRulesService.TYPE_NORMAL);
            if (citation.assessed) {
                result.applyCitation(citation.complete, citation.block, citation.violation,
                        citation.regenerations);
            }
            return result;
        }

        // 3. 超过最大轮次
        log.warn("[AgentLoop] Max turns ({}) exceeded for session={}", maxTurns, sessionId);
        // T0a-2: 记录 metrics（超限）
        if (metricsCollector != null) {
            metricsCollector.record(agentId, "run", false,
                    System.currentTimeMillis() - startTime, 0, 0, traceId);
        }
        return withClassification(
                setErrMsg(AgentLoopResult.maxTurnsExceeded(sessionId, totalTokens, toolCallRecords), traceId),
                AiErrorClass.LLM_TIMEOUT, traceId);
    }

    /**
     * 为既有终止态补 D.5 分类字段（{@code AgentLoopResultContractTest} 冻结了文本，故只补码不改文案）。
     * D.5 无"轮次耗尽/依赖未就绪"专用类 —— 就近取可重试 5xx，偏差已登记 WP11 偏差台账。
     */
    private static AgentLoopResult withClassification(AgentLoopResult r, AiErrorClass errorClass,
                                                      String traceId) {
        r.setErrorCode(errorClass.errorCode());
        r.setErrorClass(errorClass.name());
        r.setRetryable(errorClass.retryable());
        r.setTraceId(traceId);
        return r;
    }

    // ─── F06-09 上下文压缩 ─────────────────────────────────────────────

    /**
     * 每轮入模前的唯一压缩判定点。
     *
     * <p>压缩不是安全门，但"未判定"必须留痕：服务缺失、会话无作用域、异常抛出三种面
     * 一律以 {@code compressSkipped=true} 上报（F06-09 要点 1 禁静默关闭），且绝不把异常
     * 抛穿推理主链（旧实现的 {@code catch → warn} 吞错形态同时被 X-35 记账）。</p>
     */
    private void assessCompression(CompressionReport compression, String sessionId,
                                   String model, String traceId) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        if (sessionCompression == null) {
            log.error("[AgentLoop] SessionCompressionService 未装配 ⇒ 上下文压缩无法判定，"
                    + "compressSkipped=true: session={} model={} trace={}", sessionId, model, traceId);
            compression.recordSkipped();
            return;
        }
        try {
            compression.record(sessionCompression.compress(sessionId, model, traceId));
        } catch (Exception e) {
            log.error("[AgentLoop] 上下文压缩异常 ⇒ 本轮跳过压缩（不阻断推理）: session={} trace={} err={}",
                    sessionId, traceId, e.getMessage(), e);
            compression.recordSkipped();
        }
    }

    /** 本轮压缩判定快照，run 结束时统一附着到结果。 */
    private static final class CompressionReport {
        private boolean assessed;
        private String mode;
        private int turns;
        private boolean skipped;

        void record(SessionCompressionService.Outcome outcome) {
            if (outcome == null) {
                recordSkipped();
                return;
            }
            this.assessed = true;
            this.mode = outcome.compressionMode();
            this.turns = outcome.compressedTurns();
            this.skipped = outcome.compressSkipped();
        }

        void recordSkipped() {
            this.assessed = true;
            this.mode = null;
            this.turns = 0;
            this.skipped = true;
        }

        void applyTo(AgentLoopResult result) {
            if (result != null && assessed) {
                result.applyCompression(mode, turns, skipped);
            }
        }
    }

    // ─── Message 构建 ──────────────────────────────────────────────────

    /**
     * 构建初始消息列表：system prompt + 历史消息 + 当前用户消息。
     */
    private List<Message> buildInitialMessages(AgentLoopConfig config, String userMessage,
                                                AgentSession session) {
        List<Message> messages = new ArrayList<>();

        // System prompt — 优先从 config，fallback 到 session
        String systemPrompt = null;
        if (config != null && config.getSystemPrompt() != null && !config.getSystemPrompt().isBlank()) {
            systemPrompt = config.getSystemPrompt();
        } else if (session != null && session.getSystemPrompt() != null) {
            systemPrompt = session.getSystemPrompt();
        }

        // T1: 注入Skill列表到System Prompt（运行时加载）
        String agentId = session != null ? session.getProfileName() : null;
        systemPrompt = buildSystemPrompt(systemPrompt, agentId, null);

        // F06-12 双保险第一重：场景受控工具一轮必须带数值引用硬规则（模板单源在 ToolCitation）
        if (ScenarioToolCatalog.isScenarioWhitelist(config == null ? null : config.getToolWhitelist())) {
            systemPrompt = (systemPrompt == null ? "" : systemPrompt) + "\n\n"
                    + CitationPostCheckService.SYSTEM_PROMPT_RULE;
        }

        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(Message.system(systemPrompt));
        }

        // TODO T0.3: 注入记忆上下文（KG 记忆事实）

        // 当前用户消息
        if (userMessage != null && !userMessage.isBlank()) {
            messages.add(Message.user(userMessage));
        }

        log.debug("[AgentLoop] Built {} initial messages", messages.size());
        // Token 预算裁剪 — 使用解析后的 maxContextTokens（L1 yaml 默认 8000）
        int maxCtx = config != null && config.getMaxContextTokens() != null
                ? config.getMaxContextTokens() : 8000;
        messages = trimHistory(messages, maxCtx);
        return messages;
    }

    // ─── System Prompt 构建 (T1: Skill 运行时加载) ─────────────────────

    /**
     * 构建增强版 System Prompt — 将数据库中的已启用 Skill 列表注入 System Prompt。
     * <p>
     * Skill 注入总量不超过 2000 字符；若 {@link SkillService} 不可用（null），
     * 则原样返回 basePrompt。
     * </p>
     *
     * @param basePrompt 原始 System Prompt（取自 config 或 session）
     * @param agentId    Agent 标识（预留，当前未使用）
     * @param userId     用户标识（预留，当前从会话上下文获取）
     * @return 增强后的 System Prompt
     */
    private String buildSystemPrompt(String basePrompt, String agentId, String userId) {
        if (basePrompt == null) {
            basePrompt = "";
        }
        StringBuilder sb = new StringBuilder(basePrompt);

        // 若 SkillService 不可用，跳过 Skill 注入
        if (skillService == null) {
            log.debug("[AgentLoop] SkillService not available, skipping skill injection");
            return sb.toString();
        }

        try {
            List<SkillEntity> skills = skillService.listSkills(null, true);
            if (skills == null || skills.isEmpty()) {
                log.debug("[AgentLoop] No enabled skills found, skipping skill injection");
                return sb.toString();
            }

            sb.append("\n\n## Available Skills\n");

            int charBudget = 2000; // Skill 注入总量上限
            int used = 0;

            for (SkillEntity s : skills) {
                // 截断过长的描述
                String desc = s.getDescription();
                if (desc != null && desc.length() > 120) {
                    desc = desc.substring(0, 117) + "...";
                }

                StringBuilder line = new StringBuilder();
                line.append("- **").append(s.getName()).append("**");
                if (s.getVersion() != null && !s.getVersion().isBlank()) {
                    line.append(" (v").append(s.getVersion()).append(")");
                }
                line.append(": ").append(desc != null ? desc : "无描述").append("\n");

                if (used + line.length() > charBudget) {
                    log.debug("[AgentLoop] Skill injection budget exhausted ({} chars), "
                            + "{} skills injected", used, skills.indexOf(s));
                    break;
                }
                sb.append(line);
                used += line.length();
            }

            log.info("[AgentLoop] Injected {} enabled skills into system prompt ({} chars)",
                    skills.size(), used);
        } catch (Exception e) {
            log.warn("[AgentLoop] Failed to load skills for system prompt: {}", e.getMessage());
        }

        // T3: 注入记忆上下文（MemoryExtractor 规则引擎）
        injectMemorySection(sb, agentId);

        return sb.toString();
    }

    /**
     * 注入记忆段 — 从会话历史中提取用户偏好事实并追加到 System Prompt。
     * <p>
     * 仅当 {@link MemoryExtractor} 可用时执行。事实段位于 "## Available Skills" 之后，
     * 格式为：
     * </p>
     * <pre>
     * ## Memory (from previous conversations)
     * - 用户偏好：使用表格展示数据
     * - 用户习惯：每天早上查看报表
     * </pre>
     *
     * @param sb      System Prompt StringBuilder（原地追加）
     * @param agentId Agent 标识（预留，供后续按 Agent 过滤记忆）
     */
    private void injectMemorySection(StringBuilder sb, String agentId) {
        if (memoryExtractor == null) {
            log.debug("[AgentLoop] MemoryExtractor not available, skipping memory injection");
            return;
        }

        try {
            // 注：当前从空消息列表提取 — T3 阶段仅留下注入点，
            // 后续迭代通过 AgentSessionService.getMessages() 获取会话历史
            List<AgentSessionService.AgentMessage> sessionMessages = null;

            if (sessionMessages == null || sessionMessages.isEmpty()) {
                log.debug("[AgentLoop] No session messages available for memory extraction");
                return;
            }

            List<String> facts = memoryExtractor.extractFacts(sessionMessages);
            if (facts == null || facts.isEmpty()) {
                log.debug("[AgentLoop] No memory facts extracted");
                return;
            }

            sb.append("\n\n## Memory (from previous conversations)\n");
            for (String fact : facts) {
                sb.append("- ").append(fact).append("\n");
            }

            log.info("[AgentLoop] Injected {} memory facts into system prompt", facts.size());
        } catch (Exception e) {
            log.warn("[AgentLoop] Failed to inject memory section: {}", e.getMessage());
        }
    }

    // ─── Token 预算管理 ─────────────────────────────────────────────────

    /**
     * Token 预算裁剪 — system 消息始终保留在最前，其余消息从近到远保留直到超出预算。
     *
     * @param history   完整消息列表
     * @param maxTokens 上下文窗口最大 token 数
     * @return 裁剪后的消息列表
     */
    private List<Message> trimHistory(List<Message> history, int maxTokens) {
        if (history == null || history.isEmpty()) {
            return new ArrayList<>();
        }

        // 1. 分离 system 消息和非 system 消息
        List<Message> systemMsgs = new ArrayList<>();
        List<Message> rest = new ArrayList<>();
        for (Message msg : history) {
            if ("system".equals(msg.getRole())) {
                systemMsgs.add(msg);
            } else {
                rest.add(msg);
            }
        }

        // 2. 计算 system 消息的 token 开销
        int systemTokens = 0;
        for (Message sm : systemMsgs) {
            systemTokens += TokenEstimator.estimate(sm);
        }

        // 3. 预算 = maxTokens - 500（安全余量） - system 消息 token
        int budget = maxTokens - 500 - systemTokens;
        if (budget <= 0) {
            log.warn("[AgentLoop] Token budget exhausted after system messages: "
                    + "systemTokens={}, maxTokens={}", systemTokens, maxTokens);
            return systemMsgs; // system 消息必须保留
        }

        // 4. 从近到远（rest 末尾往前）累加，直到超出预算
        //    选中的消息保持原顺序（rest 末尾的 selectedCount 条）
        int usedTokens = 0;
        int selectedCount = 0;
        for (int i = rest.size() - 1; i >= 0; i--) {
            Message msg = rest.get(i);
            int msgTokens = TokenEstimator.estimate(msg);
            if (usedTokens + msgTokens > budget) {
                log.debug("[AgentLoop] trimHistory: stopping at index={}, "
                        + "usedTokens={}, msgTokens={}, budget={}",
                        i, usedTokens, msgTokens, budget);
                break;
            }
            usedTokens += msgTokens;
            selectedCount++;
        }

        // 构建最终结果：system 消息 + 选中的 rest（末尾 selectedCount 条，保持原顺序）
        List<Message> finalResult = new ArrayList<>(systemMsgs);
        for (int i = rest.size() - selectedCount; i < rest.size(); i++) {
            finalResult.add(rest.get(i));
        }

        int totalTokens = usedTokens + systemTokens;
        log.info("[AgentLoop] trimHistory: {} messages → {} messages, "
                        + "{} tokens / {} budget (system={})",
                history.size(), finalResult.size(), totalTokens, budget, systemTokens);
        return finalResult;
    }

    // ─── LLM 调用 ──────────────────────────────────────────────────────

    /**
     * 调用 LLM — 使用 gateway 的 ChatRequest 模型。
     * <p>
     * 将内部 Message 列表转换为 ChatMessage 列表，构建 ChatRequest 并调用 gateway。
     * {@code sessionId} 必须透传（F06-07）：网关是唯一计量点，会话配额按它取用量，
     * 引擎侧不自记 token。
     * </p>
     */
    private ChatResponse callLLM(List<Message> messages, AgentLoopConfig config, String sessionId) {
        // 转换消息
        List<ChatMessage> chatMessages = new ArrayList<>();
        for (Message m : messages) {
            ChatMessage cm = new ChatMessage();
            cm.setRole(m.getRole());

            // 对于 assistant 消息携带 tool_calls，序列化为 JSON
            if ("assistant".equals(m.getRole()) && m.hasToolCalls()) {
                cm.setContent(serializeToolCallsForLLM(m.getToolCalls()));
            } else if ("tool".equals(m.getRole())) {
                // tool 结果消息 — role=tool, content=结果文本
                cm.setContent(m.getContent());
            } else {
                cm.setContent(m.getContent());
            }
            chatMessages.add(cm);
        }

        // 构建请求 — 使用解析后的配置（L1/L2/L3已合并），安全兜底
        String model = config != null && config.getModel() != null
                ? config.getModel() : "deepseek-chat";
        Double temperature = config != null && config.getTemperature() != null
                ? config.getTemperature() : 0.3;
        Integer maxTokens = config != null && config.getMaxTokens() != null
                ? config.getMaxTokens() : 4096;

        ChatRequest request = new ChatRequest(model, chatMessages, temperature, maxTokens, false);
        // F06-05 要点 3：引擎侧不携带任何密钥（明文或引用）。provider 密钥一律由 llm-gateway
        // 服务端经 security-engine 密钥服务按 llm.gateway.default-api-key-ref 解析（I-4）。
        request.setCallerId("service:ai-engine-agent-loop");
        // F06-07：会话作用域随请求下传 —— 网关按它记 LLM_USAGE 与账行，配额查询才有真值
        request.setSessionId(sessionId);
        try {
            return llmGateway.call(request);
        } catch (Exception e) {
            // F06-15 边界才捕、捕后必分类：网关抛出异常（非失败响应）也必须带类上浮，
            // 禁把 e.getMessage() 原文交给响应面（X-49）
            AiError error = ErrorCodeMapper.classify(e, ErrorCodeMapper.Context.llm("llm-gateway"), null);
            log.error("[AgentLoop] LLM gateway call exception: class={} raw={}",
                    error.errorClass().name(), error.rawReason(), e);
            return ChatResponse.fail(error.errorClass(), error.rawReason());
        }
    }

    /**
     * 将 ToolCall 列表序列化为 LLM 可理解的 JSON（function-calling 风格）。
     */
    private String serializeToolCallsForLLM(List<ToolCall> toolCalls) {
        try {
            List<Map<String, Object>> serialized = new ArrayList<>();
            for (ToolCall tc : toolCalls) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("id", tc.getId());
                entry.put("type", "function");
                Map<String, Object> function = new LinkedHashMap<>();
                function.put("name", tc.getName());
                function.put("arguments", tc.getArguments());
                entry.put("function", function);
                serialized.add(entry);
            }
            return objectMapper.writeValueAsString(serialized);
        } catch (Exception e) {
            log.warn("[AgentLoop] Failed to serialize tool calls", e);
            return "[]";
        }
    }

    // ─── Tool Call 解析 ────────────────────────────────────────────────

    /**
     * 从 LLM 响应中解析工具调用 — 结构化 JSON 解析 + ToolRegistry Schema 校验。
     * <p>
     * 解析策略（按优先级）：
     * </p>
     * <ol>
     *   <li>直接 JSON 解析 tool_calls 字段（OpenAI/DeepSeek 标准格式）</li>
     *   <li>从 content 中提取 {@code ```json ... ```} 代码块</li>
     *   <li>解析 {@code <tool_call>...</tool_call>} 标记格式</li>
     *   <li>正则匹配 {@code function_name(arg1=val1, arg2=val2)} 模式</li>
     *   <li>全部失败 → 视为普通文本回复，返回空列表</li>
     * </ol>
     * <p>
     * 对每个解析出的 ToolCall，通过 {@link ToolRegistry} 进行 Schema 校验：
     * 必填参数检查 + 类型检查 + 默认值填充，校验失败的参数名写入
     * {@link ToolCall#getInvalidParams()}。
     * </p>
     *
     * @param llmResponse LLM 原始响应
     * @return 解析出的工具调用列表，空列表表示非工具调用
     */
    @SuppressWarnings("unchecked")
    private List<ToolCall> parseToolCalls(ChatResponse llmResponse) {
        if (llmResponse == null) {
            return Collections.emptyList();
        }
        String content = llmResponse.getContent();
        if (content == null || content.isBlank()) {
            return Collections.emptyList();
        }

        String trimmed = content.trim();
        List<ToolCall> calls;

        // 1. 尝试直接 JSON 解析 tool_calls 字段（OpenAI 标准格式）
        calls = tryParseJsonToolCalls(trimmed);
        if (calls != null && !calls.isEmpty()) {
            return validateToolCalls(calls);
        }

        // 2. 尝试从 content 中提取 ```json ... ``` 代码块
        calls = tryParseJsonCodeBlock(trimmed);
        if (calls != null && !calls.isEmpty()) {
            return validateToolCalls(calls);
        }

        // 3. 尝试解析 <tool_call>...</tool_call> 标记格式（兜底）
        calls = tryParseToolCallTag(trimmed);
        if (calls != null && !calls.isEmpty()) {
            return validateToolCalls(calls);
        }

        // 4. 正则匹配 function_name(arg1=val1, arg2=val2) 模式（最终兜底）
        calls = tryParseFunctionCall(trimmed);
        if (calls != null && !calls.isEmpty()) {
            return validateToolCalls(calls);
        }

        // 全部失败 → 普通文本回复，不是工具调用
        return Collections.emptyList();
    }

    // ─── 子解析器 ──────────────────────────────────────────────────────

    /**
     * 尝试解析标准 JSON tool_calls 数组格式。
     * <pre>{@code
     * [{"id": "call_xxx", "type": "function", "function": {"name": "query_db", "arguments": {"sql": "..."}}}]
     * }</pre>
     */
    @SuppressWarnings("unchecked")
    private List<ToolCall> tryParseJsonToolCalls(String content) {
        if (!content.startsWith("[")) {
            return null;
        }
        try {
            List<Map<String, Object>> raw = objectMapper.readValue(content,
                    new TypeReference<List<Map<String, Object>>>() {});
            List<ToolCall> calls = new ArrayList<>();
            for (Map<String, Object> entry : raw) {
                String id = (String) entry.getOrDefault("id", "call_" + System.currentTimeMillis());
                Map<String, Object> func = (Map<String, Object>) entry.get("function");
                if (func != null) {
                    String name = (String) func.get("name");
                    Map<String, Object> arguments = parseArguments(func.get("arguments"));
                    if (name != null && !name.isBlank()) {
                        calls.add(new ToolCall(id, name, arguments));
                    }
                }
            }
            return calls.isEmpty() ? null : calls;
        } catch (Exception e) {
            log.debug("[AgentLoop] Content is not JSON tool_calls array: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 尝试从 content 中提取 ```json ... ``` 代码块并解析工具调用。
     */
    @SuppressWarnings("unchecked")
    private List<ToolCall> tryParseJsonCodeBlock(String content) {
        // 查找 ```json ... ``` 或 ``` ... ``` 代码块
        int fenceEnd = 0;
        while (true) {
            int start = content.indexOf("```", fenceEnd);
            if (start < 0) break;
            int lineEnd = content.indexOf('\n', start);
            if (lineEnd < 0) break;
            String langLine = content.substring(start + 3, lineEnd).trim();
            // 只处理 ```json 或 ```（视为默认 JSON）
            if (!langLine.isEmpty() && !langLine.startsWith("json")) {
                fenceEnd = start + 3;
                continue;
            }

            int end = content.indexOf("```", lineEnd + 1);
            if (end < 0) break;

            String jsonStr = content.substring(lineEnd + 1, end).trim();
            fenceEnd = end + 3;

            // 尝试解析为 tool_calls 数组
            if (jsonStr.startsWith("[")) {
                List<ToolCall> calls = tryParseJsonToolCalls(jsonStr);
                if (calls != null && !calls.isEmpty()) {
                    return calls;
                }
            }

            // 尝试解析为单个 function 对象
            try {
                Map<String, Object> obj = objectMapper.readValue(jsonStr,
                        new TypeReference<Map<String, Object>>() {});
                // 可能是 {"name": "...", "arguments": {...}} 单工具调用
                if (obj.containsKey("name") && obj.containsKey("arguments")) {
                    String name = (String) obj.get("name");
                    Map<String, Object> arguments = parseArguments(obj.get("arguments"));
                    if (name != null && !name.isBlank()) {
                        String id = "call_" + System.currentTimeMillis();
                        return Collections.singletonList(new ToolCall(id, name, arguments));
                    }
                }
                // 也可能是 {"function": {"name": "...", "arguments": {...}}} 格式
                Map<String, Object> func = (Map<String, Object>) obj.get("function");
                if (func != null) {
                    String name = (String) func.get("name");
                    Map<String, Object> arguments = parseArguments(func.get("arguments"));
                    if (name != null && !name.isBlank()) {
                        String id = (String) obj.getOrDefault("id", "call_" + System.currentTimeMillis());
                        return Collections.singletonList(new ToolCall(id, name, arguments));
                    }
                }
            } catch (Exception ignored) {
                // Not valid JSON object
            }
        }
        return null;
    }

    /**
     * 尝试解析 {@code <tool_call>...</tool_call>} 标记格式（保留旧版兼容）。
     */
    @SuppressWarnings("unchecked")
    private List<ToolCall> tryParseToolCallTag(String content) {
        try {
            int start = content.indexOf("<tool_call>");
            int end = content.indexOf("</tool_call>");
            if (start >= 0 && end > start) {
                String json = content.substring(start + "<tool_call>".length(), end).trim();
                Map<String, Object> funcMap = objectMapper.readValue(json,
                        new TypeReference<Map<String, Object>>() {});
                String name = (String) funcMap.get("name");
                Map<String, Object> arguments = parseArguments(funcMap.get("arguments"));
                if (name != null && !name.isBlank()) {
                    String id = "call_" + System.currentTimeMillis();
                    return Collections.singletonList(new ToolCall(id, name, arguments));
                }
            }
        } catch (Exception e) {
            log.debug("[AgentLoop] Content is not <tool_call> format: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 尝试正则匹配 {@code function_name(arg1=val1, arg2=val2)} 模式。
     * <p>
     * 支持参数值的引号包裹（单引号或双引号）和未包裹的值。
     * </p>
     */
    @SuppressWarnings("unchecked")
    private List<ToolCall> tryParseFunctionCall(String content) {
        Matcher m = FUNCTION_CALL_PATTERN.matcher(content);
        List<ToolCall> calls = new ArrayList<>();
        while (m.find()) {
            String funcName = m.group(1);
            String argsStr = m.group(2);
            Map<String, Object> arguments = parseKeyValueArgs(argsStr);

            if (funcName != null && !funcName.isBlank()) {
                String id = "call_" + System.currentTimeMillis() + "_" + calls.size();
                calls.add(new ToolCall(id, funcName.trim(), arguments));
            }
        }
        return calls.isEmpty() ? null : calls;
    }

    // ─── Schema 校验 ────────────────────────────────────────────────────

    /**
     * 对解析出的 ToolCall 列表进行 Schema 校验。
     * <ul>
     *   <li>必填参数检查：缺少必填参数 → 加入 invalidParams</li>
     *   <li>类型检查：参数类型不匹配 → 加入 invalidParams</li>
     *   <li>默认值填充：参数缺失但有默认值 → 自动填充</li>
     * </ul>
     * <p>
     * 若 ToolRegistry 未注入或工具未注册，则跳过校验并原样返回。
     * </p>
     */
    private List<ToolCall> validateToolCalls(List<ToolCall> calls) {
        if (calls == null || calls.isEmpty()) {
            return calls;
        }
        if (toolRegistry == null) {
            log.debug("[AgentLoop] ToolRegistry not available, skipping Schema validation");
            return calls;
        }

        for (ToolCall tc : calls) {
            ToolSchema schema = toolRegistry.get(tc.getName());
            if (schema == null) {
                log.debug("[AgentLoop] Tool '{}' not registered, skipping Schema validation", tc.getName());
                continue;
            }

            Map<String, ToolSchema.ParamDef> paramDefs = schema.getParameters();
            if (paramDefs == null || paramDefs.isEmpty()) {
                continue;
            }

            Map<String, Object> args = tc.getArguments();
            if (args == null) {
                args = new LinkedHashMap<>();
                tc.setArguments(args);
            }

            List<String> invalidParams = new ArrayList<>();

            for (Map.Entry<String, ToolSchema.ParamDef> entry : paramDefs.entrySet()) {
                String paramName = entry.getKey();
                ToolSchema.ParamDef def = entry.getValue();
                Object value = args.get(paramName);

                // 必填参数检查
                if (def.isRequired() && isEmptyArg(value)) {
                    invalidParams.add(paramName);
                    log.warn("[AgentLoop] Tool '{}' missing required param: {}", tc.getName(), paramName);
                    continue;
                }

                // 类型检查（仅对非 null 值）
                if (value != null && !typeMatches(def.getType(), value)) {
                    invalidParams.add(paramName);
                    log.warn("[AgentLoop] Tool '{}' param '{}' type mismatch: expected {} got {}",
                            tc.getName(), paramName, def.getType(), value.getClass().getSimpleName());
                    continue;
                }

                // 默认值填充
                if (isEmptyArg(value) && def.getDefaultValue() != null) {
                    args.put(paramName, def.getDefaultValue());
                    log.debug("[AgentLoop] Tool '{}' filled default for param: {} = {}",
                            tc.getName(), paramName, def.getDefaultValue());
                }
            }

            tc.setInvalidParams(invalidParams);
        }

        return calls;
    }

    // ─── 校验辅助方法 ───────────────────────────────────────────────────

    /**
     * 判断参数值是否为空（null 或空白字符串）。
     */
    private static boolean isEmptyArg(Object value) {
        if (value == null) return true;
        if (value instanceof String s) return s.isBlank();
        return false;
    }

    /**
     * 检查参数值的实际类型是否与 Schema 定义的类型兼容。
     */
    private static boolean typeMatches(String expectedType, Object value) {
        if (expectedType == null) return true;
        return switch (expectedType.toLowerCase()) {
            case "string" -> value instanceof String || value instanceof CharSequence;
            case "number", "integer" -> value instanceof Number;
            case "boolean" -> value instanceof Boolean;
            case "array" -> value instanceof List || value.getClass().isArray();
            case "object" -> value instanceof Map;
            default -> true; // 未知类型，放行
        };
    }

    /**
     * 统一解析 arguments（支持 String JSON 和已解析的 Map 两种形态）。
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseArguments(Object argsObj) {
        if (argsObj == null) {
            return new LinkedHashMap<>();
        }
        try {
            if (argsObj instanceof String s) {
                if (s.isBlank()) return new LinkedHashMap<>();
                return objectMapper.readValue(s, new TypeReference<Map<String, Object>>() {});
            }
            if (argsObj instanceof Map) {
                return new LinkedHashMap<>((Map<String, Object>) argsObj);
            }
        } catch (Exception e) {
            log.debug("[AgentLoop] Failed to parse arguments: {}", e.getMessage());
        }
        return new LinkedHashMap<>();
    }

    /**
     * 解析 key=value 格式的参数字符串（用于正则兜底路径）。
     * <p>
     * 值支持：单引号包裹、双引号包裹、无包裹的简单值。
     * </p>
     */
    private Map<String, Object> parseKeyValueArgs(String argsStr) {
        Map<String, Object> args = new LinkedHashMap<>();
        if (argsStr == null || argsStr.isBlank()) {
            return args;
        }

        // 按逗号分割（注意不在引号内分割）
        List<String> pairs = splitArgsRespectingQuotes(argsStr);
        for (String pair : pairs) {
            int eqIdx = pair.indexOf('=');
            if (eqIdx <= 0) continue;

            String key = pair.substring(0, eqIdx).trim();
            String rawVal = pair.substring(eqIdx + 1).trim();

            // 去掉首尾引号
            Object value = unquote(rawVal);
            args.put(key, value);
        }
        return args;
    }

    /**
     * 按逗号分割参数字符串（引号内的逗号不分割）。
     */
    private List<String> splitArgsRespectingQuotes(String argsStr) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        char quote = 0; // 0 = 不在引号内

        for (int i = 0; i < argsStr.length(); i++) {
            char c = argsStr.charAt(i);
            if (quote == 0 && (c == '\'' || c == '"')) {
                quote = c;
                current.append(c);
            } else if (quote != 0 && c == quote) {
                quote = 0;
                current.append(c);
            } else if (quote == 0 && c == ',') {
                result.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0) {
            result.add(current.toString());
        }
        return result;
    }

    /**
     * 去掉字符串首尾的单引号或双引号，尝试数字/布尔类型推断。
     */
    private static Object unquote(String s) {
        if (s == null || s.isEmpty()) return s;
        if ((s.startsWith("\"") && s.endsWith("\""))
                || (s.startsWith("'") && s.endsWith("'"))) {
            return s.substring(1, s.length() - 1);
        }
        // 尝试数字推断
        try {
            if (s.contains(".")) {
                return Double.parseDouble(s);
            }
            return Long.parseLong(s);
        } catch (NumberFormatException ignored) {
            // 不是数字
        }
        // 尝试布尔推断
        if ("true".equalsIgnoreCase(s)) return true;
        if ("false".equalsIgnoreCase(s)) return false;
        return s;
    }

    // ─── 工具执行 ──────────────────────────────────────────────────────

    /**
     * 执行工具调用 — 带超时、截断保护和工具白名单。
     * <p>
     * 未注入 ToolExecutorService 或工具抛异常一律归 {@code TOOL_BACKEND_ERROR} 结构化失败
     * （F06-14 要点 3，禁 mock-success），并按矩阵登记做 ≤1 次固定退避重试。
     * </p>
     */
    private ToolExecutorService.ToolResult executeTool(ToolCall tc, AgentLoopConfig config) {
        // T0b-2: 工具白名单检查
        if (config != null && config.getToolWhitelist() != null && !config.getToolWhitelist().isEmpty()) {
            if (!config.getToolWhitelist().contains(tc.getName())) {
                log.warn("[AgentLoop] Tool '{}' blocked by whitelist", tc.getName());
                ToolExecutorService.ToolResult tr = new ToolExecutorService.ToolResult();
                tr.setSuccess(false);
                tr.setError("工具 '" + tc.getName() + "' 不在Agent允许的工具白名单中");
                tr.setToolCallId(tc.getId());
                tr.setToolName(tc.getName());
                tr.setContent("");
                tr.setElapsedMs(0);
                return tr;
            }
        }

        // F06-11 要点 3（X-46）：场景 Agent 禁通用兜底工具 —— 即使误配进白名单也必须拦截
        if (ScenarioToolCatalog.isScenarioWhitelist(config == null ? null : config.getToolWhitelist())
                && ScenarioToolCatalog.DENIED_FOR_SCENARIO_AGENTS.contains(tc.getName())) {
            log.error("[AgentLoop] 场景 Agent 请求通用兜底工具 → 拒绝: tool={} whitelist={}",
                    tc.getName(), config.getToolWhitelist());
            ToolExecutorService.ToolResult denied = new ToolExecutorService.ToolResult();
            denied.setSuccess(false);
            denied.setToolCallId(tc.getId());
            denied.setToolName(tc.getName());
            denied.setContent("");
            denied.setElapsedMs(0);
            denied.setError("场景 Agent 禁用通用工具 '" + tc.getName() + "'，只能调用已登记的受控工具");
            denied.setErrorCode(AiErrorClass.INVALID_ARGUMENT.errorCode());
            denied.setErrorClass(AiErrorClass.INVALID_ARGUMENT.name());
            return denied;
        }

        if (toolExecutorService == null) {
            // F06-14 要点 3（X-50）：执行器未装配 = 真实失败，归 TOOL_BACKEND_ERROR；
            // 旧的 mock-success 构造（success=false 却在 content 里塞成功字样）已删除。
            log.error("[AgentLoop] ToolExecutorService 未装配，工具无法真实执行: tool={}", tc.getName());
            return toolBackendFailure(tc, "ToolExecutorService 未装配，工具未真实执行", 0L);
        }

        // F06-14 要点 2：TOOL_BACKEND_ERROR ≤1 次固定退避，上限与形态都取自冻结矩阵（不自造参数）
        AiErrorClass toolClass = AiErrorClass.TOOL_BACKEND_ERROR;
        int attempts = 0;
        long toolStartNs = System.nanoTime();
        ToolExecutorService.ToolResult raw = attemptTool(tc);
        while (isToolBackendFailure(raw) && toolClass.canRetry(attempts)) {
            attempts++;
            if (!sleepMillis(toolClass.backoffMillis(attempts - 1))) {
                log.warn("[AgentLoop] 工具重试退避等待被中断，终止重试: tool={} attempts={}",
                        tc.getName(), attempts);
                break;
            }
            log.warn("[AgentLoop] TOOL_BACKEND_ERROR 有界重试: tool={} attempt={}/{}",
                    tc.getName(), attempts, toolClass.maxRetries());
            raw = attemptTool(tc);
        }
        if (raw == null) {
            return toolBackendFailure(tc, "工具执行未返回结果",
                    (System.nanoTime() - toolStartNs) / 1_000_000);
        }

        // 截断过长的结果
        if (raw.getContent() != null && raw.getContent().length() > TOOL_RESULT_MAX_CHARS) {
            raw.setContent(raw.getContent().substring(0, TOOL_RESULT_MAX_CHARS)
                    + "\n\n[结果已截断，原始长度: " + raw.getContent().length() + " 字符]");
        }
        return raw;
    }

    /** 单次工具执行：异常必须分类上浮（F06-15「捕后必分类」），禁 log.warn 后返回语义成功值。 */
    private ToolExecutorService.ToolResult attemptTool(ToolCall tc) {
        long startNs = System.nanoTime();
        try {
            return toolExecutorService.execute(tc.getName(), tc.getArguments());
        } catch (Exception e) {
            AiError error = ErrorCodeMapper.classify(e,
                    ErrorCodeMapper.Context.tool(tc.getName()), AiTraceSupport.currentTraceId());
            logAtDeclaredLevel(error.errorClass(),
                    "[AgentLoop] Tool execution failed: tool={} class={} raw={}",
                    e, tc.getName(), error.errorClass().name(), error.rawReason());
            return toolBackendFailure(tc, error.rawReason(), (System.nanoTime() - startNs) / 1_000_000);
        }
    }

    /**
     * 工具侧失败的唯一构造点（F06-14 要点 3）：结构化 502 + 模板消息 + 诊断码；
     * {@code content} 只放 {@code "ERROR: "} 前缀的可读消息，禁出现任何 success 语义（X-50）。
     */
    private static ToolExecutorService.ToolResult toolBackendFailure(ToolCall tc, String rawReason,
                                                                     long elapsedMs) {
        AiError error = AiError.toolBackend(AiTraceSupport.currentTraceId(), tc.getName(), rawReason);
        ToolExecutorService.ToolResult tr = ToolExecutorService.ToolResult.fail(
                tc.getId(), tc.getName(), error.userMessage(), elapsedMs);
        tr.setErrorCode(error.errorCode());
        tr.setErrorClass(error.errorClass().name());
        tr.setTraceId(error.traceId());
        return tr;
    }

    /** 仅 {@code TOOL_BACKEND_ERROR} 类失败参与重试；护栏 DENY/FAIL_CLOSED 与引用缺失都不重跑工具。 */
    private static boolean isToolBackendFailure(ToolExecutorService.ToolResult tr) {
        return tr != null && !tr.isSuccess()
                && AiErrorClass.TOOL_BACKEND_ERROR.name().equals(tr.getErrorClass());
    }

    /**
     * F06-14 四元组之「日志级别」的消费点：按冻结矩阵登记的级别落日志。
     * 原始原因只进日志通道，绝不参与响应体渲染（X-49）。
     */
    private static void logAtDeclaredLevel(AiErrorClass errorClass, String message,
                                           Throwable cause, Object... args) {
        AiErrorClass.Level level = errorClass == null
                ? AiErrorClass.Level.ERROR : errorClass.logLevel();
        Object[] payload = cause == null ? args : appendCause(args, cause);
        switch (level) {
            case TRACE -> log.trace(message, payload);
            case DEBUG -> log.debug(message, payload);
            case INFO -> log.info(message, payload);
            case WARN -> log.warn(message, payload);
            case ERROR -> log.error(message, payload);
        }
    }

    private static Object[] appendCause(Object[] args, Throwable cause) {
        Object[] out = new Object[args.length + 1];
        System.arraycopy(args, 0, out, 0, args.length);
        out[args.length] = cause;
        return out;
    }

    /** 退避等待；返回 false 表示等待中被中断（已复位中断标记）。 */
    private static boolean sleepMillis(long waitMs) {
        if (waitMs <= 0) return true;
        try {
            Thread.sleep(waitMs);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    // ─── Helpers ───────────────────────────────────────────────────────

    /**
     * F06-06 要点 3：一步恰一条固定格式结构化日志行（INFO 级，行内自携带 traceId），
     * 同时写入 {@link AgentTracer} 本地调试视图。行文本不含任何消息/工具结果原文。
     *
     * <p>行只经 {@link #agentLoopLog}（{@code ECOS_AGENT_LOOP} 专用通道）落文件，
     * 该行即为 F06-08 运行回放的唯一重建源 —— 进程重启后回放仍成立，不依赖内存。</p>
     */
    private void step(String agentId, String traceId, AgentLoopTrace.Op op, int step,
                      String tool, String model, long promptTokens, long completionTokens,
                      long latencyMs, AgentLoopTrace.Decision decision) {
        String line = AgentLoopTrace.line(op, step)
                .agent(agentId)
                .tool(tool)
                .model(model)
                .promptTokens(promptTokens)
                .completionTokens(completionTokens)
                .latencyMs(latencyMs)
                .decision(decision)
                .trace(traceId)
                .render();
        agentLoopLog.info("{}", line);
        AgentTracer.record(traceId, step, op, latencyMs, promptTokens, completionTokens, tool, decision);
    }

    /** llm_call 步骤行 + 慢查询告警（重试路径与首调共用，保证每次真实网关调用恰一行）。 */
    private void emitLlmCallStep(String agentId, String traceId, AgentLoopConfig resolvedConfig,
                                 int turn, ChatResponse resp, long elapsedMs) {
        step(agentId, traceId, AgentLoopTrace.Op.LLM_CALL, turn, null,
                resp != null && resp.getModel() != null ? resp.getModel() : resolvedConfig.getModel(),
                usageTokens(resp, true), usageTokens(resp, false), elapsedMs,
                resp != null && resp.isSuccess()
                        ? AgentLoopTrace.Decision.ALLOW : AgentLoopTrace.Decision.NA);
        if (metricsCollector != null) {
            metricsCollector.alertSlow(agentId, traceId, AgentLoopTrace.Op.LLM_CALL.wire(), elapsedMs);
        }
    }

    /** ChatResponse token 取值：网关未给出真值/估算值 → {@link AgentLoopTrace#UNKNOWN}（禁 0 冒充实测）。 */
    private static long usageTokens(ChatResponse resp, boolean promptSide) {
        if (resp == null || !resp.hasUsage()) {
            return AgentLoopTrace.UNKNOWN;
        }
        return promptSide ? resp.getTokensInput() : resp.getTokensOutput();
    }

    /** 工具裁决字符串 → 三态枚举（缺失/未知一律 NA，不当作放行）。 */
    private static AgentLoopTrace.Decision decisionOf(String raw) {
        if (raw == null) return AgentLoopTrace.Decision.NA;
        return switch (raw.trim().toUpperCase(java.util.Locale.ROOT)) {
            case "ALLOW", "PERMIT" -> AgentLoopTrace.Decision.ALLOW;
            case "DENY", "DENIED", "REJECT", "REJECTED" -> AgentLoopTrace.Decision.DENY;
            case "FAIL_CLOSED" -> AgentLoopTrace.Decision.FAIL_CLOSED;
            default -> AgentLoopTrace.Decision.NA;
        };
    }

    /** 护栏结果是否为不可用语义（503），与明确拒绝（403）严格分态（F06-04/F06-14）。 */
    private static boolean isGuardrailFailClosed(Map<String, Object> check) {
        return check != null && AiErrorClass.GUARDRAIL_FAIL_CLOSED.name().equals(check.get("errorClass"));
    }

    /** 把护栏返回体转成 Loop 错误结果：只带可读消息 + 诊断码，禁带异常原文（X-49）。 */
    private AgentLoopResult guardrailBlocked(String sessionId, String traceId, Map<String, Object> check) {
        AiErrorClass errorClass = isGuardrailFailClosed(check)
                ? AiErrorClass.GUARDRAIL_FAIL_CLOSED : AiErrorClass.GUARDRAIL_DENIED;
        Object rawMessage = check == null ? null : check.get("message");
        String userMessage = rawMessage instanceof String s && !s.isBlank()
                ? s : errorClass.renderMessage(traceId);
        Object rawCode = check == null ? null : check.get("errorCode");
        String errorCode = rawCode instanceof String s && !s.isBlank() ? s : errorClass.errorCode();
        AgentLoopResult r = AgentLoopResult.guardrailError(sessionId, userMessage, errorCode,
                errorClass.name(), traceId,
                check != null && Boolean.TRUE.equals(check.get("retryable")));
        r.setTraceId(traceId);
        return r;
    }

    /** 护栏链自身失败（Bean 缺失 / 调用异常）→ GUARDRAIL_FAIL_CLOSED 并终止 Loop。 */
    private AgentLoopResult guardrailFailure(String sessionId, String traceId, AiErrorClass errorClass,
                                             String rawReason, Exception cause) {
        if (cause != null) {
            log.error("护栏 fail-closed 明细（仅日志）: {} traceId={}", rawReason, traceId, cause);
        } else {
            log.error("护栏 fail-closed 明细（仅日志）: {} traceId={}", rawReason, traceId);
        }
        AiError error = AiError.of(errorClass, traceId, rawReason);
        AgentLoopResult r = AgentLoopResult.guardrailError(sessionId, error.userMessage(),
                error.errorCode(), errorClass.name(), traceId, error.retryable());
        r.setTraceId(traceId);
        return r;
    }

    /**
     * 会话 token 配额前置判定（详细设计-06 F06-07 要点 3）。
     *
     * <p>用量真值来自 llm-gateway 的唯一计量点（{@link LLMGateway#sessionTokenUsage}），
     * 引擎侧不自记账（F06-05 / 铁律 §2.5-2）。超限即终止，<b>禁继续静默消耗</b>。</p>
     *
     * @return 超限时返回终止结果；未超限或不判定时返回 {@code null}（调用方继续本轮）
     */
    private AgentLoopResult checkSessionQuota(String agentId, String traceId, String sessionId,
                                              AgentLoopConfig resolvedConfig, int turn,
                                              int totalTokens, long startTime) {
        if (sessionTokenQuota == null) {
            if (turn == 1) {
                log.warn("[AgentLoop] SessionTokenQuotaService 未装配，本轮无法做会话配额判定（不静默：每次运行记一次）: "
                        + "session={}", sessionId);
            }
            return null;
        }
        SessionTokenQuotaService.QuotaState state = sessionTokenQuota.check(sessionId);
        if (!state.enforced() || !state.exceeded()) {
            return null;
        }
        log.warn("[AgentLoop] F06-07 会话 token 配额超限 → 终止 Loop: session={} used={} limit={} "
                        + "reason={} turn={} trace={}",
                sessionId, state.used(), state.limit(), state.reason(), turn, traceId);
        // F06-06 要点 3：被拒的这一 step 恰一条结构化行；未发生模型调用 → token 记 -1（禁 0 冒充实测）
        step(agentId, traceId, AgentLoopTrace.Op.PLAN, turn, null, resolvedConfig.getModel(),
                AgentLoopTrace.UNKNOWN, AgentLoopTrace.UNKNOWN,
                System.currentTimeMillis() - startTime, AgentLoopTrace.Decision.DENY);
        return sessionQuotaExceeded(sessionId, traceId, state, totalTokens);
    }

    /**
     * 配额终止结果：错误码沿用 D.5 的 {@code CONTEXT_OVERFLOW}（F06-14 前置判定），
     * 消息按配额语义自造 —— 类模板的"已降级为摘要模式"是压缩路径（F06-09）文案，
     * 用在配额拒绝上会让用户误以为仍有结果。
     */
    private AgentLoopResult sessionQuotaExceeded(String sessionId, String traceId,
                                                 SessionTokenQuotaService.QuotaState state,
                                                 int totalTokens) {
        AiError error = AiError.of(AiErrorClass.CONTEXT_OVERFLOW, traceId,
                "session token quota exceeded: used=" + state.used() + " limit=" + state.limit()
                        + " reason=" + state.reason());
        String userMessage = QUOTA_MESSAGE_TEMPLATE
                .replace("{used}", String.valueOf(state.used()))
                .replace("{limit}", String.valueOf(state.limit()))
                .replace("{traceId}", traceId == null || traceId.isBlank() ? "-" : traceId);
        return AgentLoopResult.contextOverflow(sessionId, userMessage, error.errorCode(),
                error.errorClass().name(), traceId, totalTokens);
    }

    private static String truncate(String str, int maxLen) {
        if (str == null) return null;
        return str.length() <= maxLen ? str : str.substring(0, maxLen) + "...";
    }

    /**
     * 将旧的 AgentLoopConfig 转换为请求覆盖 Map，供 AgentConfigResolver 使用。
     * <p>
     * 仅提取非 null 字段，避免覆盖 L1/L2/L3 的已有值。
     * </p>
     */
    private static Map<String, Object> configToOverrides(AgentLoopConfig config) {
        Map<String, Object> overrides = new LinkedHashMap<>();
        if (config == null) return overrides;
        if (config.getModel() != null) overrides.put("model", config.getModel());
        if (config.getTemperature() != null) overrides.put("temperature", config.getTemperature());
        if (config.getMaxTokens() != null) overrides.put("maxTokens", config.getMaxTokens());
        if (config.getMaxContextTokens() != null) overrides.put("maxContextTokens", config.getMaxContextTokens());
        if (config.getSystemPrompt() != null) overrides.put("systemPrompt", config.getSystemPrompt());
        if (config.getDefaultProvider() != null) overrides.put("defaultProvider", config.getDefaultProvider());
        if (config.getMaxIterations() != null) overrides.put("maxIterations", config.getMaxIterations());
        if (config.getAgentTimeoutMs() != null) overrides.put("agentTimeoutMs", config.getAgentTimeoutMs());
        if (config.getCaliberCode() != null) overrides.put("caliberCode", config.getCaliberCode());
        return overrides;
    }

    // ─── T0 辅助方法 ────────────────────────────────────────────────

    /** 为 AgentLoopResult 设置 traceId */
    // ─── F06-12 / F06-13 咽喉判定 ─────────────────────────────────────

    /** 本轮引用后校验的过程量（判定态、结论、块原文、已消耗重生成次数）。 */
    private static final class CitationSnapshot {
        private boolean assessed;
        private boolean complete;
        private String block;
        private String violation;
        private int regenerations;
    }

    /**
     * F06-13 规则 1/2 的前置判定入口（在构建 messages 与任何 LLM 调用之前）。
     *
     * <p>命中即返回，<b>不进</b> LLM 数值路径；澄清（CLARIFY）是合法答复（success=true），
     * 拒绝（REFUSED）按错误类语义置 success=false。判定服务未装配时记 ERROR 并放行，
     * 与压缩/配额同纪律（禁静默，但不因装配缺失伪装成安全拦截）。</p>
     */
    private AgentLoopResult refusalPreAdjudication(String agentId, String traceId, String sessionId,
                                                  String userMessage, AgentLoopConfig resolvedConfig,
                                                  long startTime) {
        if (refusalRules == null) {
            log.error("[AgentLoop] RefusalRulesService 未装配：F06-13 前置判定无法执行"
                    + "（生产必须装配）session={}", sessionId);
            return null;
        }
        if (userMessage == null) {
            return null;
        }
        Optional<RefusalRulesService.Refusal> hit = refusalRules.preAdjudicate(
                userMessage, resolvedConfig.getCaliberCode(), caliberStatus, traceId);
        if (hit.isEmpty()) {
            return null;
        }
        RefusalRulesService.Refusal refusal = hit.get();
        log.warn("[AgentLoop] F06-13 前置判定命中 → 不进 LLM 数值路径: type={} session={} trace={}",
                refusal.answerType(), sessionId, traceId);
        step(agentId, traceId, AgentLoopTrace.Op.PLAN, 0, null, resolvedConfig.getModel(),
                AgentLoopTrace.UNKNOWN, AgentLoopTrace.UNKNOWN,
                System.currentTimeMillis() - startTime, AgentLoopTrace.Decision.DENY);
        boolean clarify = RefusalRulesService.TYPE_CLARIFY.equals(refusal.answerType());
        AgentLoopResult result = AgentLoopResult.refused(sessionId, refusal.answerType(), refusal.message(),
                refusal.toClarification(), refusal.errorCode(), errorCodeName(refusal.errorCode()),
                traceId, clarify);
        return result;
    }

    /** 记录单工具结局：成功体进引用比对基准，失败分类进规则 3 的众数统计。 */
    private static void recordToolOutcome(ToolExecutorService.ToolResult tr,
                                         List<String> toolResultBodies,
                                         List<AiErrorClass> toolFailureClasses) {
        if (tr == null) return;
        if (tr.isSuccess()) {
            if (tr.getContent() != null) {
                toolResultBodies.add(tr.getContent());
            }
        } else {
            AiErrorClass cls = aiErrorClassOf(tr.getErrorClass());
            if (cls != null) {
                toolFailureClasses.add(cls);
            }
        }
    }

    /** F06-13 规则 3 的触发地板：本轮确有工具调用且无一成功（无工具调用不属"工具全部失败"）。 */
    private static boolean allToolCallsFailed(List<Map<String, Object>> toolCallRecords) {
        if (toolCallRecords.isEmpty()) return false;
        for (Map<String, Object> record : toolCallRecords) {
            if (Boolean.TRUE.equals(record.get("success"))) return false;
        }
        return true;
    }

    /** 失败分类众数（按 {@link AiErrorClass#SEVEN_CLASSES} 表序取首个最高，保持确定性）。 */
    private AiErrorClass dominantFailureClass(List<AiErrorClass> classes) {
        return refusalRules == null ? AiErrorClass.TOOL_BACKEND_ERROR
                : refusalRules.dominantClass(classes);
    }

    /** F06-13 规则 3 的结果装配（只渲染 F06-14 分类文案，禁原始异常上浮）。 */
    private AgentLoopResult allToolsFailedResult(String sessionId, String traceId, AiErrorClass dominant,
                                                 int totalTokens,
                                                 List<Map<String, Object>> toolCallRecords) {
        RefusalRulesService.Refusal refusal = refusalRules == null
                ? new RefusalRulesService.Refusal(RefusalRulesService.TYPE_REFUSED, null,
                        RefusalRulesService.TOOL_BACKEND_REQUIRED,
                        AiErrorClass.TOOL_BACKEND_ERROR.errorCode(),
                        AiErrorClass.TOOL_BACKEND_ERROR.renderMessage(traceId))
                : refusalRules.allToolsFailed(dominant, traceId);
        AgentLoopResult r = AgentLoopResult.refused(sessionId, refusal.answerType(), refusal.message(),
                null, refusal.errorCode(),
                dominant == null ? AiErrorClass.TOOL_BACKEND_ERROR.name() : dominant.name(),
                traceId, false);
        r.setTotalTokens(totalTokens);
        r.setToolCalls(toolCallRecords);
        return r;
    }

    /** F06-12 超限固定话术的结果装配（整段替换，禁把无据数值交给用户）。 */
    private static AgentLoopResult citationFallbackResult(String sessionId, String traceId,
                                                          CitationPostCheckService checker,
                                                          CitationSnapshot citation, int totalTokens,
                                                          List<Map<String, Object>> toolCallRecords) {
        AgentLoopResult r = AgentLoopResult.refused(sessionId, RefusalRulesService.TYPE_REFUSED,
                checker.fallbackMessage(traceId), null, checker.fallbackErrorCode(),
                AiErrorClass.CITATION_MISSING.name(), traceId, false);
        r.setTotalTokens(totalTokens);
        r.setToolCalls(toolCallRecords);
        r.applyCitation(false, null, citation.violation, citation.regenerations);
        return r;
    }

    /** 错误码 → 枚举名（不可识别返回 null，禁伪造分类）。 */
    private static String errorCodeName(String errorCode) {
        for (AiErrorClass c : AiErrorClass.values()) {
            if (errorCode != null && errorCode.equals(c.errorCode())) {
                return c.name();
            }
        }
        return null;
    }

    /** 枚举名 → {@link AiErrorClass}（不可识别返回 null）。 */
    private static AiErrorClass aiErrorClassOf(String errorClassName) {
        if (errorClassName == null || errorClassName.isBlank()) return null;
        for (AiErrorClass c : AiErrorClass.values()) {
            if (c.name().equals(errorClassName)) {
                return c;
            }
        }
        return null;
    }

    /** 设置 traceId 到 result */
    private static AgentLoopResult setErrMsg(AgentLoopResult result, String traceId) {
        if (result != null) {
            result.setTraceId(traceId);
        }
        return result;
    }
}
