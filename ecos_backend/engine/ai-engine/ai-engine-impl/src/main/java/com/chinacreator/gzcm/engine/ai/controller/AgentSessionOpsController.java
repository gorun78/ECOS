package com.chinacreator.gzcm.engine.ai.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.error.AiErrorClass;
import com.chinacreator.gzcm.common.security.guardrail.GuardrailDecisionContract;
import com.chinacreator.gzcm.engine.ai.dao.AgentSessionDao;
import com.chinacreator.gzcm.engine.ai.dao.GuardrailDecisionLedgerDao;
import com.chinacreator.gzcm.engine.ai.guardrail.ToolAdjudicationRequest;
import com.chinacreator.gzcm.engine.ai.security.ToolSubjectResolver;
import com.chinacreator.gzcm.engine.ai.service.AgentSessionService;
import com.chinacreator.gzcm.engine.ai.service.SessionCompressionService;
import com.chinacreator.gzcm.engine.ai.service.SessionCompressionService.Outcome;
import com.chinacreator.gzcm.engine.ai.service.SessionCompressionService.Status;
import com.chinacreator.gzcm.engine.ai.service.SessionTokenQuotaService;
import com.chinacreator.gzcm.runtime.llm.gateway.LLMGateway;
import com.chinacreator.gzcm.runtime.llm.gateway.LLMGateway.ModelLimit;
import com.chinacreator.gzcm.runtime.llm.metrics.AgentMetrics;
import com.chinacreator.gzcm.runtime.llm.model.AgentCallLog;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 会话运维链端点（P1.3 #12/#13/#15：附录 A 端点缺口 35 条之 ai 会话运维族首切片）。
 *
 * <p><b>#12 {@code GET /sessions/{sessionId}/context}</b> —— 上下文预算与压缩状态读数面：
 * 活消息 token 估算经 {@link SessionCompressionService#estimateSessionTokens}
 * （{@code TokenEstimates} 单源口径，估算值如实标 {@code estimated=true}），
 * 模型上限经 {@link LLMGateway#modelContextLimit}（禁引擎内硬编码），
 * 阈值 = 上限 × sysman 字典面 {@code threshold}；配额经
 * {@link SessionTokenQuotaService#check}（{@code quotaRemaining}，未启用时 false 明示）。
 * 会话不存在 ⇒ 404 真 404（禁合成预算伪数据）。</p>
 *
 * <p><b>#13 {@code POST /sessions/{sessionId}/compress}</b> —— 显式触发压缩（幂等于批次）：
 * 判定/执行单源 {@link SessionCompressionService#compress}（token 阈值 + LLM 结构化摘要
 * + 引用保护 + 逻辑归档）；请求体 {@code model} 缺失时回落会话创建的 agentId 登记表，
 * 仍不可得时以会话面时域字典键兜底（<b>真实调用面不硬编码模型名</b>：目录无该模型和
 * 默认项时 {@code MODEL_LIMIT_UNAVAILABLE} 不判定，禁伪装）。状态映射：
 * 依赖不可得三类（schema/config/model-limit）⇒ 503 机读码；已压缩后上下文仍超模型上限
 * ⇒ 413 {@code ECOS-AI-413}；其余状态（含内嵌压缩 or 降级）⇒ 200 + {@code compressSkipped}
 * 明示（1 开关关闭报 {@code compressSkipped=true}，静默关闭禁）。</p>
 *
 * <p><b>#15 {@code GET /usage}</b> —— token 聚合（period 维度）：行数读面单源
 * {@link AgentMetrics#getRecentCalls}（{@code subsystem="ai-engine"}、limit 上限 500），
 * 按 {@code period} 桶（DEFAULT=当期日）与 {@code agentId}/{@code modelKey} 复用
 * {@code profileName}/{@code modelKey} 字段，Java 侧聚合 prompt/completion 文本，
 * {@code estimated} 一律 {@code false}（本表数据为实测，估算口径禁混写 X-39）。</p>
 *
 * <p>本 Controller 与 {@code AgentChatController}/{@code DiagnosticAgentController}/
 * {@code AgentProviderController} 共用 {@code /api/v1/agent} 前缀（均已在
 * {@code VersionPrefixRewriteFilter} 反向重写面覆盖，{@code ClearanceInterceptor}
 * L1 兜底按 {@code /api/v1/} 全放行）。只增不改：不改既有的相同前缀映射。</p>
 *
 * <p><b>#147DB 载体</b>：四个端点载荷改 record（键名与旧 Map 逐键一致；{@code keptKeyRefs}/
 * {@code droppedKeyRefs}/{@code costRatio} 等「恒 null 但仍出键」的字段用普通可空组件保持）。
 * 仍余 {@code Map} 的是属主 {@link GuardrailDecisionLedgerDao#listFiltered} 的台账行。</p>
 */
@RestController
@RequestMapping("/api/v1/agent")
public class AgentSessionOpsController {

    private final AgentSessionService sessionService;
    private final AgentSessionDao sessionDao;
    private final SessionCompressionService compressionService;
    private final SessionTokenQuotaService tokenQuotaService;
    private final LLMGateway llmGateway;
    private final AgentMetrics agentMetrics;
    private final GuardrailDecisionLedgerDao guardrailLedgerDao;
    private final ToolSubjectResolver toolSubjectResolver;

    public AgentSessionOpsController(AgentSessionService sessionService,
                                     AgentSessionDao sessionDao,
                                     SessionCompressionService compressionService,
                                     SessionTokenQuotaService tokenQuotaService,
                                     LLMGateway llmGateway,
                                     AgentMetrics agentMetrics,
                                     GuardrailDecisionLedgerDao guardrailLedgerDao,
                                     ToolSubjectResolver toolSubjectResolver) {
        this.sessionService = sessionService;
        this.sessionDao = sessionDao;
        this.compressionService = compressionService;
        this.tokenQuotaService = tokenQuotaService;
        this.llmGateway = llmGateway;
        this.agentMetrics = agentMetrics;
        this.guardrailLedgerDao = guardrailLedgerDao;
        this.toolSubjectResolver = toolSubjectResolver;
    }

    /** #12 上下文面（13 键；limit/threshold/配额三数在不可得时保持 null 出键）。 */
    public record SessionContextVO(@JsonProperty("sessionId") String sessionId,
                                   @JsonProperty("estimatedTokens") int estimatedTokens,
                                   @JsonProperty("estimated") boolean estimated,
                                   @JsonProperty("promptTokens") int promptTokens,
                                   @JsonProperty("completionTokens") int completionTokens,
                                   @JsonProperty("limit") Integer limit,
                                   @JsonProperty("threshold") Integer threshold,
                                   @JsonProperty("compressedTurns") int compressedTurns,
                                   @JsonProperty("compressionMode") Object compressionMode,
                                   @JsonProperty("quotaEnforced") boolean quotaEnforced,
                                   @JsonProperty("quotaRemaining") Long quotaRemaining,
                                   @JsonProperty("quotaLimit") Long quotaLimit,
                                   @JsonProperty("quotaUsed") Long quotaUsed) {
    }

    /** #13 压缩回执（13 键；keyRefs 两键恒 null 出键——摘要原文不入本响应）。 */
    public record CompressVO(@JsonProperty("sessionId") String sessionId,
                             @JsonProperty("model") String model,
                             @JsonProperty("status") String status,
                             @JsonProperty("compressSkipped") boolean compressSkipped,
                             @JsonProperty("mode") String mode,
                             @JsonProperty("compressedTurns") int compressedTurns,
                             @JsonProperty("estimatedTokensBefore") int estimatedTokensBefore,
                             @JsonProperty("estimatedTokensAfter") int estimatedTokensAfter,
                             @JsonProperty("threshold") int threshold,
                             @JsonProperty("batchId") String batchId,
                             @JsonProperty("reason") String reason,
                             @JsonProperty("keptKeyRefs") Object keptKeyRefs,
                             @JsonProperty("droppedKeyRefs") Object droppedKeyRefs) {
    }

    /** #15 聚合桶（5 键；costRatio 无价目面故恒 null 出键）。 */
    public record UsageBucketVO(@JsonProperty("period") String period,
                                @JsonProperty("promptTokens") int promptTokens,
                                @JsonProperty("completionTokens") int completionTokens,
                                @JsonProperty("estimated") boolean estimated,
                                @JsonProperty("costRatio") Object costRatio) {
    }

    /** #15 合计（2 键）。 */
    public record UsageTotalsVO(@JsonProperty("promptTokens") long promptTokens,
                                @JsonProperty("completionTokens") long completionTokens) {
    }

    /** #15 响应（9 键）。 */
    public record UsageVO(@JsonProperty("subsystem") String subsystem,
                          @JsonProperty("period") String period,
                          @JsonProperty("agentId") String agentId,
                          @JsonProperty("modelKey") String modelKey,
                          @JsonProperty("itemCount") int itemCount,
                          @JsonProperty("items") List<UsageBucketVO> items,
                          @JsonProperty("totals") UsageTotalsVO totals,
                          @JsonProperty("windowCallCount") int windowCallCount,
                          @JsonProperty("windowLimit") int windowLimit) {
    }

    /** #14 台账条目（7 键；{@code policyRef} 键名取契约常量）。 */
    public record GuardrailDecisionVO(@JsonProperty("traceId") Object traceId,
                                      @JsonProperty("sessionId") Object sessionId,
                                      @JsonProperty("toolKey") Object toolKey,
                                      @JsonProperty("decision") Object decision,
                                      @JsonProperty(GuardrailDecisionContract.KEY_POLICY_REF) Object policyRef,
                                      @JsonProperty("latencyMs") Integer latencyMs,
                                      @JsonProperty("createTime") String createTime) {
    }

    /** #14 过滤回显（5 键，未给的键保持 null 出键）。 */
    public record GuardrailFiltersVO(@JsonProperty("traceId") String traceId,
                                     @JsonProperty("toolKey") String toolKey,
                                     @JsonProperty("decision") String decision,
                                     @JsonProperty("from") String from,
                                     @JsonProperty("to") String to) {
    }

    /** #14 响应（4 键）。 */
    public record GuardrailListVO(@JsonProperty("itemCount") int itemCount,
                                  @JsonProperty("items") List<GuardrailDecisionVO> items,
                                  @JsonProperty("limit") int limit,
                                  @JsonProperty("filters") GuardrailFiltersVO filters) {
    }

    /** 依赖不可得载荷（3 键）。 */
    public record DependencyUnavailableVO(@JsonProperty("scope") String scope,
                                          @JsonProperty("service") String service,
                                          @JsonProperty("reason") String reason) {
    }

    /** #13 请求体（{@code model}/{@code keepRecent} 保持宽松类型，非法形态按"缺位"处理）。 */
    public record CompressRequest(Object model, Object keepRecent) {
    }

    // ═══════════════════════════════════════════════════════════════
    //  #12 GET /api/v1/agent/sessions/{sessionId}/context
    // ═══════════════════════════════════════════════════════════════

    /**
     * 上下文预算与压缩状态。
     *
     * @param sessionId 会话 ID；空 ⇒ 404（真实不存在，禁伪默认预算）
     */
    @GetMapping("/sessions/{sessionId}/context")
    public ResponseEntity<ApiResponse<SessionContextVO>> getSessionContext(
            @PathVariable("sessionId") String sessionId) {
        AgentSessionService.AgentSession session = sessionService.getSession(sessionId);
        if (session == null) {
            return ResponseEntity.status(404).body(ApiResponse.error(
                    404, AiErrorClass.NOT_FOUND.errorCode(),
                    "会话 " + sessionId + " 不存在（" + AiErrorClass.NOT_FOUND.renderMessage(null) + "）"));
        }
        Object[] stats = sessionDao.tokenStats(sessionId);
        // 优先取真实计量读数（V202 治理列，网关计量记账）；未就位回落估算（X-88 严格估算/实测分态）
        Object promptTokensRaw = stats[0];
        Object completionTokensRaw = stats[1];
        Object compressionModeRaw = stats[2];

        // 估算仅作字典/门槛不可得时的最终兜底（否则一律用真实计量，禁估算当实测 X-39）
        int promptTokens = promptTokensRaw instanceof Number n ? n.intValue() : estimateActiveMessages(sessionId);
        int completionTokens = completionTokensRaw instanceof Number n ? n.intValue() : 0;
        boolean usageEstimated = !(promptTokensRaw instanceof Number);
        // estimatedTokens = 上下文体输入+输出（真实计量或估算兜底口径）
        int estimatedTokens = promptTokens + completionTokens;

        int modelLimit = resolveModelLimit(session).contextLimit();
        int thresholdTokens = modelLimit > 0
                ? (int) Math.floor(modelLimit * compressionService.settings().threshold())
                : 0;
        // 已压缩条数读面：V202 治理列面（compressed=1 的行）；治理列未就位 ⇒ 0 + 明示（禁伪造历史）
        int compressedTurns = sessionDao.governanceColumnsReady()
                ? sessionDao.countCompressedMessages(sessionId) : 0;
        SessionTokenQuotaService.QuotaState quota = checkQuota(sessionId);

        SessionContextVO data = new SessionContextVO(sessionId, estimatedTokens, usageEstimated,
                promptTokens, completionTokens,
                modelLimit > 0 ? modelLimit : null,
                thresholdTokens > 0 ? thresholdTokens : null,
                compressedTurns, compressionModeRaw,
                quota != null && quota.enforced(),
                quota != null && quota.enforced() ? quota.remaining() : null,
                quota != null ? quota.limit() : null,
                quota != null && quota.enforced() ? quota.used() : null);

        return ResponseEntity.ok(ApiResponse.success(data));
    }

    /** 读面估算兜底：消息体积估算经 {@link SessionCompressionService#estimateSessionTokens}（TokenEstimates 单源口径）。 */
    private int estimateActiveMessages(String sessionId) {
        List<AgentSessionService.AgentMessage> active = sessionService.getMessages(sessionId);
        if (active == null || active.isEmpty()) {
            return 0;
        }
        return compressionService.estimateSessionTokens(active);
    }

    /** 配额读数面（配额服务异常由 {@code AiErrorAdvice} 统一分类，控制器层禁内吞）。 */
    private SessionTokenQuotaService.QuotaState checkQuota(String sessionId) {
        return tokenQuotaService.check(sessionId);
    }

    // ═══════════════════════════════════════════════════════════════
    //  #13 POST /api/v1/agent/sessions/{sessionId}/compress
    // ═══════════════════════════════════════════════════════════════

    @PostMapping("/sessions/{sessionId}/compress")
    public ResponseEntity<ApiResponse<CompressVO>> compressSession(
            @PathVariable("sessionId") String sessionId,
            @RequestBody(required = false) CompressRequest body) {
        AgentSessionService.AgentSession session = sessionService.getSession(sessionId);
        if (session == null) {
            return ResponseEntity.status(404).body(ApiResponse.error(
                    404, AiErrorClass.NOT_FOUND.errorCode(),
                    "会话 " + sessionId + " 不存在（" + AiErrorClass.NOT_FOUND.renderMessage(null) + "）"));
        }
        String model = readString(body == null ? null : body.model());
        if (model == null) {
            // 会话面下的字典回落；未登记时以会话 profileName 兜底作为查询键（禁硬编码模型名）
            model = session.getAgentId() != null && !session.getAgentId().isBlank()
                    ? session.getAgentId() : "default";
        }
        Integer keepRecent = readPositiveInt(body == null ? null : body.keepRecent());
        String traceId = com.chinacreator.gzcm.engine.ai.trace.AiTraceSupport.currentTraceId();
        Outcome outcome;
        outcome = compressionService.compress(sessionId, model, traceId, keepRecent);

        // 依赖不可得三类 ⇒ 503 机读码（禁 200 伪装已压缩）
        if (outcome.status() == Status.SCHEMA_UNAVAILABLE
                || outcome.status() == Status.CONFIG_UNAVAILABLE
                || outcome.status() == Status.MODEL_LIMIT_UNAVAILABLE) {
            return dependencyUnavailableResponse(sessionId, "session-compression",
                    outcome.reason());
        }
        // 已压缩但上下文仍超模型上限 ⇒ 413（F06-09 降级路径显式终态）
        if (outcome.status() == Status.COMPRESSED
                && outcome.estimatedTokensAfter() > resolvedLimitAfter(outcome)) {
            return ResponseEntity.status(AiErrorClass.CONTEXT_OVERFLOW.httpStatus()).body(ApiResponse.error(
                    AiErrorClass.CONTEXT_OVERFLOW.httpStatus(),
                    AiErrorClass.CONTEXT_OVERFLOW.errorCode(),
                    AiErrorClass.CONTEXT_OVERFLOW.renderMessage(traceId)));
        }
        return ResponseEntity.ok(ApiResponse.success(compressView(sessionId, model, outcome)));
    }

    private static int resolvedLimitAfter(Outcome outcome) {
        int threshold = outcome.thresholdTokens();
        if (threshold <= 0 || outcome.modelLimit() <= 0) {
            return Integer.MAX_VALUE;
        }
        double ratio = (double) threshold / outcome.modelLimit();
        return ratio > 0 && ratio <= 1 ? outcome.thresholdTokens() : outcome.modelLimit();
    }

    private static CompressVO compressView(String sessionId, String model, Outcome outcome) {
        // keyRefs 不在此视图承载（摘要原文在 V203 批次台账与消息体，本响应只承载面签名）
        return new CompressVO(sessionId, model, outcome.status().name(), outcome.compressSkipped(),
                outcome.compressionMode(), outcome.compressedTurns(), outcome.estimatedTokensBefore(),
                outcome.estimatedTokensAfter(), outcome.thresholdTokens(), outcome.batchId(),
                outcome.reason(), null, null);
    }

    // ═══════════════════════════════════════════════════════════════
    //  #15 GET /api/v1/agent/usage
    // ═══════════════════════════════════════════════════════════════

    @GetMapping("/usage")
    public ResponseEntity<ApiResponse<UsageVO>> getAgentUsage(
            @RequestParam(name = "period", required = false) String period,
            @RequestParam(name = "agentId", required = false) String agentId,
            @RequestParam(name = "modelKey", required = false) String modelKey) {
        String effSubsystem = "ai-engine";
        List<AgentCallLog> calls = agentMetrics.getRecentCalls(effSubsystem, 500);

        YearMonth monthFilter = null;
        java.time.LocalDate dayFilter = null;
        if (period != null && !period.isBlank()) {
            String p = period.trim();
            try {
                if (p.length() == 7) {
                    monthFilter = YearMonth.parse(p, DateTimeFormatter.ofPattern("yyyy-MM"));
                } else if (p.length() == 10) {
                    dayFilter = java.time.LocalDate.parse(p, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                } else {
                    return ResponseEntity.status(400).body(ApiResponse.error(
                            400, AiErrorClass.INVALID_ARGUMENT.errorCode(),
                            AiErrorClass.INVALID_ARGUMENT.renderMessage(null)));
                }
            } catch (DateTimeParseException e) {
                return ResponseEntity.status(400).body(ApiResponse.error(
                        400, AiErrorClass.INVALID_ARGUMENT.errorCode(),
                        AiErrorClass.INVALID_ARGUMENT.renderMessage(null)));
            }
        }
        String agentFilter = blankToNull(agentId);
        String modelFilter = blankToNull(modelKey);

        // 聚合键：period_label（月桶或日桶或原始 createdTime 月度）
        Map<String, Bucket> byBucket = new LinkedHashMap<>();
        DateTimeFormatter dayFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        DateTimeFormatter monthFmt = DateTimeFormatter.ofPattern("yyyy-MM");
        long totalPrompt = 0;
        long totalCompletion = 0;
        int counted = 0;
        for (AgentCallLog c : calls) {
            if (agentFilter != null && !agentFilter.equals(c.getProfileName())) {
                continue;
            }
            if (modelFilter != null && (c.getProfileName() == null
                    || !String.valueOf(c.getProfileName()).startsWith(modelFilter))) {
                continue;
            }
            Object created = c.getCreatedTime();
            if (created == null) {
                continue;
            }
            LocalDateTime ldt;
            if (created instanceof Timestamp ts) {
                ldt = ts.toLocalDateTime();
            } else if (created instanceof LocalDateTime l) {
                ldt = l;
            } else {
                continue;
            }
            String dayLabel = ldt.format(dayFmt);
            String monthLabel = ldt.format(monthFmt);
            if (dayFilter != null) {
                if (!dayFilter.equals(ldt.toLocalDate())) {
                    continue;
                }
            } else if (monthFilter != null) {
                if (!monthFilter.equals(YearMonth.from(ldt))) {
                    continue;
                }
            }
            String bucketKey = (dayFilter != null || period != null && period.trim().length() == 10)
                    ? dayLabel : monthLabel;
            Bucket row = byBucket.computeIfAbsent(bucketKey, Bucket::new);
            int p = Math.max(0, c.getTokensInput());
            int comp = Math.max(0, c.getTokensOutput());
            row.promptTokens += p;
            row.completionTokens += comp;
            totalPrompt += p;
            totalCompletion += comp;
            counted++;
        }

        List<UsageBucketVO> items = new ArrayList<>(byBucket.size());
        for (Bucket b : byBucket.values()) {
            // 本表数据为真实计量（LlmUsageMeter 单源落库），estimated 一律 false；
            // 成本比率缺显式价目面，故 costRatio 恒 null（不伪造）
            items.add(new UsageBucketVO(b.period, b.promptTokens, b.completionTokens, false, null));
        }

        UsageVO data = new UsageVO(effSubsystem,
                period == null || period.isBlank() ? "current-day" : period,
                agentFilter, modelFilter, byBucket.size(), items,
                new UsageTotalsVO(totalPrompt, totalCompletion), counted, 500);
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    /** 聚合桶累加器（内部可变载体；仅作聚合，不外发）。 */
    private static final class Bucket {
        final String period;
        int promptTokens;
        int completionTokens;

        Bucket(String period) {
            this.period = period;
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  #14 GET /api/v1/agent/guardrail-decisions
    // ═══════════════════════════════════════════════════════════════

    /** 裁决台账读取角色词表（详设说明书 line 6058 门禁面 {@code role:AI_GUARDIAN}，
     * 大小写不敏感，含 {@code ROLE_} 前缀形态；与 F10-21 admin 判据同口径）。 */
    private static final Set<String> GUARDRAIL_ROLES = Set.of("ai_guardian", "role_ai_guardian");

    @GetMapping("/guardrail-decisions")
    public ResponseEntity<ApiResponse<GuardrailListVO>> listGuardrailDecisions(
            @RequestParam(name = "traceId", required = false) String traceId,
            @RequestParam(name = "toolKey", required = false) String toolKey,
            @RequestParam(name = "decision", required = false) String decision,
            @RequestParam(name = "from", required = false) String from,
            @RequestParam(name = "to", required = false) String to,
            @RequestParam(name = "limit", required = false, defaultValue = "100") Integer limit) {
        // 准入门禁（统一门禁层，禁伪造空台账放行）：主体缺失 / 无 AI_GUARDIAN 角色 ⇒ 403
        if (toolSubjectResolver == null) {
            return guardrailDeniedResponse("裁决台账主体解析器未装配（fail-closed）");
        }
        ToolAdjudicationRequest.Subject subject = toolSubjectResolver.resolve();
        if (subject == null || !hasGuardianRole(subject.roles())) {
            return guardrailDeniedResponse("裁决台账仅限 AI_GUARDIAN 角色读取");
        }
        int effLimit = clampLimit(limit);
        List<Map<String, Object>> rows = guardrailLedgerDao.listFiltered(
                blankToNull(traceId), blankToNull(toolKey), blankToNull(decision),
                blankToNull(from), blankToNull(to), effLimit);

        List<GuardrailDecisionVO> items = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            Object latency = row.get("latency_ms");
            items.add(new GuardrailDecisionVO(row.get("trace_id"), row.get("session_id"),
                    row.get("tool_key"), row.get("decision"), row.get("policy_id"),
                    latency instanceof Number n ? n.intValue() : null,
                    formatTimestamp(row.get("create_time"))));
        }
        // 过滤回显：未命中时由响应面空集如实表达，禁合成"空即全量"伪聚合
        GuardrailFiltersVO filters = new GuardrailFiltersVO(blankToNull(traceId),
                blankToNull(toolKey), blankToNull(decision), blankToNull(from), blankToNull(to));
        return ResponseEntity.ok(ApiResponse.success(
                new GuardrailListVO(items.size(), items, effLimit, filters)));
    }

    private ResponseEntity<ApiResponse<GuardrailListVO>> guardrailDeniedResponse(String message) {
        return ResponseEntity.status(AiErrorClass.GUARDRAIL_DENIED.httpStatus()).body(
                ApiResponse.forbidden(AiErrorClass.GUARDRAIL_DENIED.errorCode(),
                        message == null ? AiErrorClass.GUARDRAIL_DENIED.renderMessage(null) : message));
    }

    private static boolean hasGuardianRole(List<String> roles) {
        if (roles == null) return false;
        for (String r : roles) {
            if (r != null && GUARDRAIL_ROLES.contains(r.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static int clampLimit(Integer limit) {
        if (limit == null || limit <= 0) return 100;
        return Math.min(limit, 500);
    }

    private static String formatTimestamp(Object raw) {
        if (raw instanceof Timestamp ts) {
            return ts.toLocalDateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));
        }
        if (raw instanceof LocalDateTime ldt) {
            return ldt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));
        }
        return raw == null ? null : String.valueOf(raw);
    }

    // ── 私有工具方法 ────────────────────────────────────────────────

    /** 上下文面模型上限的读取面：按会话 profileName 查目录，缺位时按会话 agentId 再查，
     * 均不可得时返回 {@link ModelLimit#unavailable}（<b>禁</b> 引擎内硬编码上限常量）。 */
    private ModelLimit resolveModelLimit(AgentSessionService.AgentSession session) {
        ModelLimit m = null;
        String primary = session.getAgentId();
        if (primary != null && !primary.isBlank()) {
            m = safeModelLimit(primary);
        }
        if (m == null || !m.available()) {
            String secondary = "default";
            if (!secondary.equals(primary)) {
                m = safeModelLimit(secondary);
            }
        }
        return m != null ? m : ModelLimit.unavailable(primary == null ? "-" : primary);
    }

    private ModelLimit safeModelLimit(String key) {
        return llmGateway.modelContextLimit(key);
    }

    /** 可选字段读取：缺位/非法类型 ⇒ null（服务层回落字典），禁 toString 伪造。 */
    private static String readString(Object value) {
        if (value == null) return null;
        if (!(value instanceof String s)) {
            return null;
        }
        return s.isBlank() ? null : s.trim();
    }

    private static Integer readPositiveInt(Object value) {
        if (value == null) return null;
        if (value instanceof Number n) {
            int i = n.intValue();
            return i > 0 ? i : null;
        }
        if (value instanceof String s) {
            try {
                int i = Integer.parseInt(s.trim());
                return i > 0 ? i : null;
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    /** 版本化依赖不可得的显式承载：使用 503 + D.5 分类 stub 码（依赖未就绪，非 200 伪成功）。 */
    private <T> ResponseEntity<ApiResponse<T>> dependencyUnavailableResponse(
            String scope, String service, String reason) {
        DependencyUnavailableVO data = new DependencyUnavailableVO(scope, service,
                reason == null ? "-" : reason);
        return ResponseEntity.status(AiErrorClass.GUARDRAIL_FAIL_CLOSED.httpStatus()).body(ApiResponse.<T>error(
                AiErrorClass.GUARDRAIL_FAIL_CLOSED.httpStatus(),
                AiErrorClass.GUARDRAIL_FAIL_CLOSED.errorCode(),
                AiErrorClass.GUARDRAIL_FAIL_CLOSED.renderMessage(null, service)));
    }
}
