package com.chinacreator.gzcm.engine.ai.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.error.AiErrorClass;
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

    // ═══════════════════════════════════════════════════════════════
    //  #12 GET /api/v1/agent/sessions/{sessionId}/context
    // ═══════════════════════════════════════════════════════════════

    /**
     * 上下文预算与压缩状态。
     *
     * @param sessionId 会话 ID；空 ⇒ 404（真实不存在，禁伪默认预算）
     */
    @GetMapping("/sessions/{sessionId}/context")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getSessionContext(
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

        List<AgentSessionService.AgentMessage> activeMessages = null;
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

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("sessionId", sessionId);
        data.put("estimatedTokens", estimatedTokens);
        data.put("estimated", usageEstimated);
        data.put("promptTokens", promptTokens);
        data.put("completionTokens", completionTokens);
        data.put("limit", modelLimit > 0 ? modelLimit : null);
        data.put("threshold", thresholdTokens > 0 ? thresholdTokens : null);
        data.put("compressedTurns", compressedTurns);
        data.put("compressionMode", compressionModeRaw);
        data.put("quotaEnforced", quota != null && quota.enforced());
        data.put("quotaRemaining", quota != null && quota.enforced() ? quota.remaining() : null);
        data.put("quotaLimit", quota != null ? quota.limit() : null);
        data.put("quotaUsed", quota != null && quota.enforced() ? quota.used() : null);

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

    /**
     * 显式触发压缩。
     *
     * <p>请求体（可选段）：
     * <ul>
     *   <li>{@code model} — 上下文上限查询的模型名（缺失时回落会话面回落，最差
     *       时以会话面 agentId 字面 作为默认模型名作查询键，<b>非伪造</b>：
     *       该查询键在目录{@code llm.models.*}不存在时 {@code MODEL_LIMIT_UNAVAILABLE}
     *       不判定返回，不静默按"无需压缩"。）</li>
     *   <li>{@code keepRecent} — 保留窗口最近 K 轮（仅 positive 生效）</li>
     * </ul>
     */
    @PostMapping("/sessions/{sessionId}/compress")
    public ResponseEntity<ApiResponse<Map<String, Object>>> compressSession(
            @PathVariable("sessionId") String sessionId,
            @RequestBody(required = false) Map<String, Object> body) {
        AgentSessionService.AgentSession session = sessionService.getSession(sessionId);
        if (session == null) {
            return ResponseEntity.status(404).body(ApiResponse.error(
                    404, AiErrorClass.NOT_FOUND.errorCode(),
                    "会话 " + sessionId + " 不存在（" + AiErrorClass.NOT_FOUND.renderMessage(null) + "）"));
        }
        String model = readString(body, "model");
        if (model == null) {
            // 会话面下的字典回落；未登记时以会话 profileName 兜底作为查询键（禁硬编码模型名）
            model = session.getAgentId() != null && !session.getAgentId().isBlank()
                    ? session.getAgentId() : "default";
        }
        Integer keepRecent = readPositiveInt(body, "keepRecent");
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
        Map<String, Object> data = compressView(sessionId, model, outcome);
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    private static int resolvedLimitAfter(Outcome outcome) {
        int threshold = outcome.thresholdTokens();
        if (threshold <= 0 || outcome.modelLimit() <= 0) {
            return Integer.MAX_VALUE;
        }
        double ratio = (double) threshold / outcome.modelLimit();
        return ratio > 0 && ratio <= 1 ? outcome.thresholdTokens() : outcome.modelLimit();
    }

    private static Map<String, Object> compressView(String sessionId, String model, Outcome outcome) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("sessionId", sessionId);
        data.put("model", model);
        data.put("status", outcome.status().name());
        data.put("compressSkipped", outcome.compressSkipped());
        data.put("mode", outcome.compressionMode());
        data.put("compressedTurns", outcome.compressedTurns());
        data.put("estimatedTokensBefore", outcome.estimatedTokensBefore());
        data.put("estimatedTokensAfter", outcome.estimatedTokensAfter());
        data.put("threshold", outcome.thresholdTokens());
        data.put("batchId", outcome.batchId());
        data.put("reason", outcome.reason());
        // keyRefs 不在此视图承载（摘要原文在 V203 批次台账与消息体，本响应只承载面签名）
        data.put("keptKeyRefs", null);
        data.put("droppedKeyRefs", null);
        return data;
    }

    // ═══════════════════════════════════════════════════════════════
    //  #15 GET /api/v1/agent/usage
    // ═══════════════════════════════════════════════════════════════

    /**
     * token 与成本聚合（period 维度）。
     *
     * @param period   可选：{@code YYYY-MM}（月桶）或 {@code YYYY-MM-DD}（日桶）；
     *                 缺省 = 当期日
     * @param agentId  可选：按会话-agent 过滤（profileName 字段承载，见 F06-05 meter 落库面）
     * @param modelKey 可选：按模型 key 过滤（本表列面 {@code profile_name} 未拆模型子键，
     *                 过滤在应用侧对 profile_name 前缀命中）
     */
    @GetMapping("/usage")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getAgentUsage(
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
        Map<String, Map<String, Object>> byBucket = new LinkedHashMap<>();
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
            Map<String, Object> row = byBucket.computeIfAbsent(bucketKey, k -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("period", k);
                m.put("promptTokens", 0);
                m.put("completionTokens", 0);
                // 本表数据为真实计量（LlmUsageMeter 单源落库），estimated 一律 false
                m.put("estimated", false);
                // 成本比率缺显式价目面（详设 16 编号的 cost），且不属本腿 P1.3 收切片
                // 故不在此伪造：只承载 prompt/completion，接口层面供需两侧以 -1 占位显式表"不可得"
                m.put("costRatio", null);
                return m;
            });
            int p = Math.max(0, c.getTokensInput());
            int comp = Math.max(0, c.getTokensOutput());
            row.put("promptTokens", (Number) row.get("promptTokens") instanceof Integer i ? i + p : p);
            row.put("completionTokens",
                    (Number) row.get("completionTokens") instanceof Integer i ? i + comp : comp);
            totalPrompt += p;
            totalCompletion += comp;
            counted++;
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("subsystem", effSubsystem);
        data.put("period", period == null || period.isBlank() ? "current-day" : period);
        data.put("agentId", agentFilter);
        data.put("modelKey", modelFilter);
        data.put("itemCount", byBucket.size());
        data.put("items", new java.util.ArrayList<>(byBucket.values()));
        data.put("totals", Map.of("promptTokens", totalPrompt, "completionTokens", totalCompletion));
        data.put("windowCallCount", counted);
        data.put("windowLimit", 500);
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    // ═══════════════════════════════════════════════════════════════
    //  #14 GET /api/v1/agent/guardrail-decisions
    // ═══════════════════════════════════════════════════════════════

    /** 裁决台账读取角色词表（详设说明书 line 6058 门禁面 {@code role:AI_GUARDIAN}，
     * 大小写不敏感，含 {@code ROLE_} 前缀形态；与 F10-21 admin 判据同口径）。 */
    private static final Set<String> GUARDRAIL_ROLES = Set.of("ai_guardian", "role_ai_guardian");

    /**
     * 裁决台账查询（对账面，详设说明书 line 6058 {@code listGuardrailDecisions}）。
     *
     * @param traceId  可选：按链路 traceId 精确过滤
     * @param toolKey  可选：按工具 code 精确过滤
     * @param decision 可选：ALLOW / DENY / FAIL_CLOSED 三态字面过滤
     * @param from     可选：ISO-8601 起始（含）
     * @param to       可选：ISO-8601 截止（不含）
     * @param limit    可选：正整数，缺省 100 上限 500（契约单 Ceiling）
     */
    @GetMapping("/guardrail-decisions")
    public ResponseEntity<ApiResponse<Map<String, Object>>> listGuardrailDecisions(
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

        List<Map<String, Object>> items = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("traceId", row.get("trace_id"));
            item.put("sessionId", row.get("session_id"));
            item.put("toolKey", row.get("tool_key"));
            item.put("decision", row.get("decision"));
            item.put("policyRef", row.get("policy_id"));
            Object latency = row.get("latency_ms");
            item.put("latencyMs", latency instanceof Number n ? n.intValue() : null);
            item.put("createTime", formatTimestamp(row.get("create_time")));
            items.add(item);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("itemCount", items.size());
        data.put("items", items);
        data.put("limit", effLimit);
        // 本端点过滤限定 traceId/toolKey/decision/from/to 五键；未命中时由响应面空集如实表达，
        // 禁合成"空即全量"伪聚合。回显面用 LinkedHashMap（Map.of 拒 null，空过滤即 NPE）
        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("traceId", blankToNull(traceId));
        filters.put("toolKey", blankToNull(toolKey));
        filters.put("decision", blankToNull(decision));
        filters.put("from", blankToNull(from));
        filters.put("to", blankToNull(to));
        data.put("filters", filters);
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    private ResponseEntity<ApiResponse<Map<String, Object>>> guardrailDeniedResponse(String message) {
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

    /** 请求体可选字段读取：缺位 ⇒ null（服务层回落字典），非法类型拒（禁 toString 伪造）。 */
    private static String readString(Map<String, Object> body, String key) {
        if (body == null) return null;
        Object v = body.get(key);
        if (v == null) return null;
        if (!(v instanceof String s)) {
            return null;
        }
        return s.isBlank() ? null : s.trim();
    }

    private static Integer readPositiveInt(Map<String, Object> body, String key) {
        if (body == null) return null;
        Object v = body.get(key);
        if (v instanceof Number n) {
            int i = n.intValue();
            return i > 0 ? i : null;
        }
        if (v instanceof String s) {
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
    private ResponseEntity<ApiResponse<Map<String, Object>>> dependencyUnavailableResponse(
            String scope, String service, String reason) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("scope", scope);
        data.put("service", service);
        data.put("reason", reason == null ? "-" : reason);
        return ResponseEntity.status(AiErrorClass.GUARDRAIL_FAIL_CLOSED.httpStatus()).body(ApiResponse.error(
                AiErrorClass.GUARDRAIL_FAIL_CLOSED.httpStatus(),
                "ECOS-AI-503",
                AiErrorClass.GUARDRAIL_FAIL_CLOSED.renderMessage(null, service)));
    }
}
