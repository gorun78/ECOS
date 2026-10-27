package com.chinacreator.gzcm.engine.ai.service;

import com.chinacreator.gzcm.common.event.EventTypes;
import com.chinacreator.gzcm.common.event.KafkaTopics;
import com.chinacreator.gzcm.common.token.TokenEstimates;
import com.chinacreator.gzcm.engine.ai.dao.AgentSessionDao;
import com.chinacreator.gzcm.engine.ai.dao.CompressionBatchDao;
import com.chinacreator.gzcm.runtime.eventbus.EventBusService;
import com.chinacreator.gzcm.runtime.llm.gateway.ChatMessage;
import com.chinacreator.gzcm.runtime.llm.gateway.ChatRequest;
import com.chinacreator.gzcm.runtime.llm.gateway.ChatResponse;
import com.chinacreator.gzcm.runtime.llm.gateway.LLMGateway;
import com.chinacreator.gzcm.sysman.config.service.impl.SysConfigService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 会话压缩（详细设计-06 F06-09 / REQ-AI-02）—— 把"条数阈值 + 200 字截断拼接 + 物理 DELETE"
 * 换成"<b>token 阈值 + LLM 结构化摘要 + 引用保护 + 逻辑归档</b>"。
 *
 * <p>六条规则逐一对应文档要点：</p>
 * <ol>
 *   <li><b>触发</b>：{@code estimatedContextTokens > modelLimit × threshold}。
 *       {@code modelLimit} 只经 {@link LLMGateway#modelContextLimit} 从 llm-gateway 模型目录取
 *       （禁引擎内硬编码）；读数不可得即<b>不判定</b>并 ERROR 留痕，禁据此推断"无需压缩"。
 *       开关关闭时上报 {@code compressSkipped=true}（禁静默关闭）；</li>
 *   <li><b>窗口</b>：最近 K 轮原文保留，更早轮交 llm-gateway 生成结构化摘要并注入 system 段；</li>
 *   <li><b>摘要结构</b>：{@code {confirmedFacts[], userPreferences[], openQuestions[], keyRefs[{type,id,link}]}}
 *       落 TEXT 列（MC02 禁 jsonb）。{@code keyRefs} 先由<b>确定性正则</b>从被压缩段预提取，
 *       生成后做<b>引用完整性硬校验</b>（被压缩段 runId 集合 ⊆ 摘要 keyRefs 集合）；</li>
 *   <li><b>工具引用完整性</b>：被压缩消息的 {@code tool_call_id} 若仍被保留窗口引用 ⇒ 原文保留；</li>
 *   <li><b>失败降级</b>：摘要调用失败 / 不可解析 / 校验不过 ⇒ 截断最早轮 +
 *       {@code compression_mode=TRUNCATE_FALLBACK} 标记 + {@code log.warn} + 审计事件上报
 *       （X-35 的"只 warn 不上报"即本项消灭对象）；</li>
 *   <li><b>禁物理删除</b>：归档为 {@code UPDATE ... compressed=1, is_deleted=1, compression_batch_id=?}，
 *       历史原件保留（F06-08 回放与 §2.2-2 语义抽检的前提）。R-20① 已批准 ⇒ 仅对新数据生效。</li>
 * </ol>
 *
 * <p><b>迁移未实跑的诚实边界</b>：V202/V203 按 §14.4 只落文件、未对库执行，治理列与批次表在旧库上
 * 可能缺席。本类以 {@code governanceColumnsReady()/tableReady()} 先行探测，缺位面一律
 * {@link Status#SCHEMA_UNAVAILABLE} + ERROR（fail-loud，禁抛穿调用方、也禁伪装已压缩）。</p>
 */
@Service
public class SessionCompressionService {

    private static final Logger log = LoggerFactory.getLogger(SessionCompressionService.class);

    /** 压缩开关键（sysman 配置字典单源，{@code config_group='ai-engine'}，V264 种子建立） */
    public static final String KEY_ENABLED = "ecos.ai.session.compress.enabled";
    /** 触发阈值键（比例，非条数） */
    public static final String KEY_THRESHOLD = "ecos.ai.session.compress.threshold";
    /** 保留窗口键（最近 K 轮） */
    public static final String KEY_KEEP_RECENT = "ecos.ai.session.compress.keep_recent";

    /**
     * 阈值缺省回落值 —— 权威值在字典行（V264 种子 {@code 0.6}），本常量只服务"行缺失"分支，
     * 且命中即 WARN 留痕（与配额面同口径：字典是单源，代码不是）。
     */
    static final double DEFAULT_THRESHOLD_RATIO = 0.6;

    /** 保留窗口缺省值（同上，权威值 = V264 种子 {@code 6}） */
    static final int DEFAULT_KEEP_RECENT_ROUNDS = 6;

    public static final String MODE_LLM_SUMMARY = "LLM_SUMMARY";
    public static final String MODE_TRUNCATE_FALLBACK = "TRUNCATE_FALLBACK";

    /** 摘要调用的调用方标识（进 llm-gateway 审计面，禁自造 provider 通道） */
    private static final String SUMMARIZER_CALLER_ID = "ai-engine:session-compressor";

    /** 引用预提取（F06-09 要点 3）：四类引用的确定性形态，禁交给 LLM"自己找" */
    private static final List<RefPattern> REF_PATTERNS = List.of(
            new RefPattern("FORECAST_RUN", Pattern.compile(
                    "\"?forecastRunId\"?\\s*[:=]\\s*\"?([A-Za-z0-9_-]{6,})\"?")),
            new RefPattern("CALIBER", Pattern.compile(
                    "\"?caliberId\"?\\s*[:=]\\s*\"?([A-Za-z0-9_-]{6,})\"?")),
            new RefPattern("EVIDENCE", Pattern.compile(
                    "\"?evidenceUrl\"?\\s*[:=]\\s*\"?(https?://[^\\s\",}]+)\"?")),
            new RefPattern("TOOL_CALL", Pattern.compile(
                    "\"?(?:tool_call_id|toolCallId)\"?\\s*[:=]\\s*\"?([A-Za-z0-9_:-]{6,})\"?")));

    /** 摘要 prompt 明示"引用类信息禁省略"的硬约束（结构逐字对齐 F06-09 要点 3） */
    private static final String SUMMARY_SYSTEM_PROMPT = """
            你是会话历史压缩器。把给定历史轮次压缩为结构化摘要，只输出一个 JSON 对象，禁止输出其它文字。
            JSON 结构固定为：
            {"confirmedFacts":["已确认事实"],"userPreferences":["用户偏好"],"openQuestions":["未决问题"],\
            "keyRefs":[{"type":"FORECAST_RUN|CALIBER|EVIDENCE|TOOL_CALL","id":"引用ID","link":"可选链接"}]}
            硬约束：引用类信息（forecastRunId / caliberId / evidenceUrl / tool_call_id）一条都不能省略，\
            必须原样出现在 keyRefs 中；禁改写数值，禁新增历史里不存在的事实。
            """;

    private final AgentSessionDao sessionDao;
    private final CompressionBatchDao batchDao;
    private final LLMGateway llmGateway;
    private final ObjectProvider<SysConfigService> sysConfigProvider;
    private final ObjectProvider<EventBusService> eventBusProvider;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SessionCompressionService(AgentSessionDao sessionDao,
                                     CompressionBatchDao batchDao,
                                     LLMGateway llmGateway,
                                     ObjectProvider<SysConfigService> sysConfigProvider,
                                     ObjectProvider<EventBusService> eventBusProvider) {
        this.sessionDao = sessionDao;
        this.batchDao = batchDao;
        this.llmGateway = llmGateway;
        this.sysConfigProvider = sysConfigProvider;
        this.eventBusProvider = eventBusProvider;
    }

    // ═══════════════════════════════════════════════════
    //  主流程
    // ═══════════════════════════════════════════════════

    /**
     * 一次压缩判定与执行（每轮 Loop 前置调用，与配额判定同位）。
     *
     * @param sessionId 会话 ID；空 ⇒ 无作用域，不判定
     * @param model     本轮使用的模型名（用于向 llm-gateway 目录查上下文上限）
     * @param traceId   MDC 同源诊断码，进批次台账与事件
     */
    public Outcome compress(String sessionId, String model, String traceId) {
        return compress(sessionId, model, traceId, null);
    }

    /**
     * 一次压缩判定与执行（P1.3 #13 compress 显式触发时的 {@code keepRecent} 覆盖面）。
     *
     * <p>与 {@link #compress(String, String, String)} 同一判定流；仅在本重载中被显式提供的
     * {@code keepRecentOverride} 才替代配置字典读数，其余规则（阈值、开关、治理能力位、
     * 引用完整性、失败降级）逐条对齐 Loop 内嵌调用。覆盖值 &lt;= 0 视为非法，按配置字典
     * 读数处理（禁按 0 假装「全压缩」）。</p>
     */
    public Outcome compress(String sessionId, String model, String traceId, Integer keepRecentOverride) {
        if (sessionId == null || sessionId.isBlank()) {
            return Outcome.of(Status.NO_SCOPE, "no_session", true);
        }
        Settings settings = settings();
        if (!settings.enabled()) {
            // 要点 1：关闭必须上报 compressSkipped=true，禁静默关闭
            publishQuiet(EventTypes.Session.COMPRESSION_SKIPPED, sessionId, traceId,
                    Map.of("compressSkipped", true, "reason", "switch_off"));
            log.warn("[F06-09] 压缩开关关闭 ⇒ compressSkipped=true（上下文将自然增长至 413 兜底）: "
                    + "session={} key={}", sessionId, KEY_ENABLED);
            return new Outcome(Status.SWITCH_OFF, true, null, 0, 0, 0, 0, 0, 0, null, traceId,
                    "switch_off");
        }
        if (!settings.facadeAvailable()) {
            log.error("[F06-09] sysman 配置门面不可用 ⇒ 阈值不可读，本轮不判定压缩（非放行结论）: "
                    + "session={}", sessionId);
            return Outcome.of(Status.CONFIG_UNAVAILABLE, "config_facade_missing", true);
        }

        LLMGateway.ModelLimit limit = llmGateway.modelContextLimit(model);
        if (limit == null || !limit.available()) {
            log.error("[F06-09] llm-gateway 模型目录未给出上下文上限 ⇒ 不判定压缩（禁引擎侧兜底常量）: "
                    + "session={} model={}", sessionId, model);
            return Outcome.of(Status.MODEL_LIMIT_UNAVAILABLE, "model_limit_unavailable", true);
        }
        int thresholdTokens = (int) Math.floor(limit.contextLimit() * settings.threshold());

        if (!sessionDao.governanceColumnsReady() || !batchDao.tableReady()) {
            log.error("[F06-09] 压缩治理库面未就位（V202/V203 未执行）⇒ 不压缩且不伪装: "
                    + "session={} modelLimit={} threshold={}", sessionId, limit.contextLimit(),
                    thresholdTokens);
            return new Outcome(Status.SCHEMA_UNAVAILABLE, true, null,
                    0, 0, thresholdTokens, 0, 0, 0, null, traceId, "governance_schema_missing")
                    .withModelLimit(limit.contextLimit());
        }

        List<AgentSessionService.AgentMessage> active =
                sessionDao.findActiveMessages(sessionId, AgentMessageThread.MAIN);
        if (active.isEmpty()) {
            return Outcome.of(Status.NO_MESSAGES, "no_active_messages", false);
        }
        int estimatedBefore = estimateSessionTokens(active);
        if (estimatedBefore <= thresholdTokens) {
            return new Outcome(Status.BELOW_THRESHOLD, false, null, estimatedBefore, estimatedBefore,
                    thresholdTokens, 0, 0, 0, null, traceId, "within_budget")
                    .withModelLimit(limit.contextLimit());
        }

        int effectiveKeepRecent = settings.keepRecent();
        if (keepRecentOverride != null && keepRecentOverride > 0) {
            effectiveKeepRecent = keepRecentOverride;
        }
        Plan plan = planRounds(active, effectiveKeepRecent, sessionId);
        if (plan.compressedRange().isEmpty()) {
            // 保留窗口已覆盖全部历史 —— 无更早轮可压缩，必须显式留痕（禁伪装"已压缩"）
            log.warn("[F06-09] token 超阈值但保留窗口覆盖全部历史 ⇒ 无可压缩段: "
                    + "session={} estimated={} threshold={} keepRecent={}",
                    sessionId, estimatedBefore, thresholdTokens, effectiveKeepRecent);
            return new Outcome(Status.NOTHING_TO_COMPRESS, true, null, estimatedBefore, estimatedBefore,
                    thresholdTokens, 0, plan.protectedMessages().size(), plan.compressedTurns(),
                    null, traceId, "keep_window_covers_context").withModelLimit(limit.contextLimit());
        }

        List<KeyRef> preExtracted = preExtractKeyRefs(plan.compressedRange());
        SummaryResult summary = summarize(model, sessionId, plan.compressedRange(), preExtracted);
        if (summary.usable()) {
            return applySummary(sessionId, traceId, plan, preExtracted, summary,
                    estimatedBefore, thresholdTokens, limit.contextLimit());
        }
        return applyTruncateFallback(sessionId, traceId, plan, preExtracted, summary,
                estimatedBefore, thresholdTokens, limit.contextLimit());
    }

    // ═══════════════════════════════════════════════════
    //  配置读取（单源 = sysman 门面）
    // ═══════════════════════════════════════════════════

    /** 配置读数面（P1.3 #12 context 阈值计算共享；口径与 {@link #compress} 同单源字典链）。 */
    public Settings settings() {
        SysConfigService cfg = sysConfigProvider.getIfAvailable();
        if (cfg == null) {
            return new Settings(false, false, DEFAULT_THRESHOLD_RATIO, DEFAULT_KEEP_RECENT_ROUNDS);
        }
        boolean enabled = cfg.getBoolean(KEY_ENABLED, true);
        double threshold = DEFAULT_THRESHOLD_RATIO;
        String rawThreshold = cfg.getString(KEY_THRESHOLD, null);
        if (rawThreshold == null || rawThreshold.isBlank()) {
            log.warn("[F06-09] 阈值配置行缺失 ⇒ 回落代码缺省（字典才是单源，V264 种子需补建）: "
                    + "key={} default={}", KEY_THRESHOLD, DEFAULT_THRESHOLD_RATIO);
        } else {
            try {
                double parsed = Double.parseDouble(rawThreshold.trim());
                if (parsed > 0 && parsed <= 1) {
                    threshold = parsed;
                } else {
                    log.error("[F06-09] 阈值配置越界(0,1] ⇒ 按缺省处理: key={} value={}",
                            KEY_THRESHOLD, rawThreshold);
                }
            } catch (NumberFormatException e) {
                log.error("[F06-09] 阈值配置不可解析 ⇒ 按缺省处理: key={} value={}",
                        KEY_THRESHOLD, rawThreshold);
            }
        }
        int keepRecent = cfg.getInt(KEY_KEEP_RECENT, DEFAULT_KEEP_RECENT_ROUNDS);
        if (keepRecent <= 0) {
            log.error("[F06-09] 保留窗口配置 <=0 ⇒ 按缺省处理: key={} value={}",
                    KEY_KEEP_RECENT, keepRecent);
            keepRecent = DEFAULT_KEEP_RECENT_ROUNDS;
        }
        return new Settings(true, enabled, threshold, keepRecent);
    }

    // ═══════════════════════════════════════════════════
    //  token 估算 / 轮次划分 / 引用保护（要点 1、2、4）
    // ═══════════════════════════════════════════════════

    /**
     * 会话上下文估算 token —— 估算口径单源 {@link TokenEstimates}（与网关估算面同系数）。
     *
     * <p>逐条消息：provider 上报过的实测量优先（{@code tokens>0}），否则估算文本 +
     * role 固定开销。估算值与实测量<b>不混记</b>：本方法返回的是"预算判定输入"，
     * 任何对外展示数值仍须来自网关计量点（X-68）。</p>
     *
     * <p>P1.3 #12 context 公开读数面：真实计量列不可得时的最终兜底（调用方须标
     * {@code estimated=true}，禁当实测上报）。</p>
     */
    public int estimateSessionTokens(List<AgentSessionService.AgentMessage> messages) {
        return (int) Math.min(Integer.MAX_VALUE, estimateMessageTokens(messages));
    }

    /**
     * 轮次划分与引用保护判定（要点 2 + 要点 4）。
     *
     * <p>一轮 = 一条 user 消息及其后续非 user 消息；最近 K 轮整轮保留原文。
     * 被压缩段里凡 {@code tool_call_id} 仍被保留段引用者 ⇒ 原文保留（不压缩、不截断）。</p>
     */
    Plan planRounds(List<AgentSessionService.AgentMessage> active, int keepRecentRounds,
                    String sessionId) {
        List<List<AgentSessionService.AgentMessage>> rounds = new ArrayList<>();
        List<AgentSessionService.AgentMessage> current = new ArrayList<>();
        for (AgentSessionService.AgentMessage m : active) {
            if ("user".equals(m.getRole()) && !current.isEmpty()) {
                rounds.add(current);
                current = new ArrayList<>();
            }
            current.add(m);
        }
        if (!current.isEmpty()) {
            rounds.add(current);
        }
        int keep = Math.min(keepRecentRounds, rounds.size());
        int splitIndex = rounds.size() - keep;
        List<AgentSessionService.AgentMessage> kept = new ArrayList<>();
        for (int i = Math.max(0, splitIndex); i < rounds.size(); i++) {
            kept.addAll(rounds.get(i));
        }
        List<AgentSessionService.AgentMessage> older = new ArrayList<>();
        List<Integer> olderRoundIndex = new ArrayList<>();
        for (int i = 0; i < Math.max(0, splitIndex); i++) {
            for (AgentSessionService.AgentMessage m : rounds.get(i)) {
                older.add(m);
                olderRoundIndex.add(i);
            }
        }

        Set<String> referencedInKept = referencedToolCallIds(kept);
        List<AgentSessionService.AgentMessage> compressible = new ArrayList<>();
        List<AgentSessionService.AgentMessage> protectedOnes = new ArrayList<>();
        Set<Integer> archivedRounds = new LinkedHashSet<>();
        for (int i = 0; i < older.size(); i++) {
            AgentSessionService.AgentMessage m = older.get(i);
            Set<String> declared = declaredToolCallIds(m);
            boolean referenced = false;
            for (String id : declared) {
                if (referencedInKept.contains(id)) {
                    referenced = true;
                    break;
                }
            }
            if (referenced) {
                protectedOnes.add(m);
                continue;
            }
            compressible.add(m);
            if (m.getId() != null && m.getId() > 0) {
                archivedRounds.add(olderRoundIndex.get(i));
            }
        }
        if (!protectedOnes.isEmpty()) {
            log.info("[F06-09] 引用保护生效 ⇒ {} 条工具结果原文保留（不压缩不截断）: session={}",
                    protectedOnes.size(), sessionId);
        }
        List<Long> archivedIds = new ArrayList<>();
        for (AgentSessionService.AgentMessage m : compressible) {
            if (m.getId() != null && m.getId() > 0) {
                archivedIds.add(m.getId());
            }
        }
        Integer fromSeq = archivedIds.isEmpty() ? null : archivedIds.get(0).intValue();
        Integer toSeq = archivedIds.isEmpty() ? null
                : archivedIds.get(archivedIds.size() - 1).intValue();
        return new Plan(compressible, protectedOnes, kept, archivedIds, fromSeq, toSeq,
                idSet(protectedOnes), archivedRounds.size());
    }

    private static Set<String> idSet(List<AgentSessionService.AgentMessage> messages) {
        Set<String> ids = new LinkedHashSet<>();
        for (AgentSessionService.AgentMessage m : messages) {
            ids.addAll(declaredToolCallIds(m));
        }
        return ids;
    }

    /** 本消息声明的 tool_call_id（assistant 行的 tool_calls[].id）。 */
    static Set<String> declaredToolCallIds(AgentSessionService.AgentMessage m) {
        Set<String> ids = new LinkedHashSet<>();
        if (m.getToolCalls() != null) {
            for (Map<String, Object> call : m.getToolCalls()) {
                Object id = call.get("id");
                if (id != null && !String.valueOf(id).isBlank()) {
                    ids.add(String.valueOf(id));
                }
            }
        }
        collectPattern(m.getToolResultsRaw(), REF_PATTERNS.get(3).pattern(), ids);
        return ids;
    }

    /** 保留段引用到的 tool_call_id（含结果行的 tool_call_id 字段形态）。 */
    static Set<String> referencedToolCallIds(List<AgentSessionService.AgentMessage> kept) {
        Set<String> ids = new LinkedHashSet<>();
        for (AgentSessionService.AgentMessage m : kept) {
            ids.addAll(declaredToolCallIds(m));
            collectPattern(m.getContent(), REF_PATTERNS.get(3).pattern(), ids);
        }
        return ids;
    }

    private static void collectPattern(String text, Pattern pattern, Set<String> sink) {
        if (text == null || text.isBlank()) {
            return;
        }
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            sink.add(matcher.group(1));
        }
    }

    private static void appendIfPresent(StringBuilder sink, String value) {
        if (value != null && !value.isBlank()) {
            sink.append(value).append('\n');
        }
    }

    // ═══════════════════════════════════════════════════
    //  引用预提取 + 摘要生成 + 硬校验（要点 3）
    // ═══════════════════════════════════════════════════

    /** 被压缩段的引用预提取（确定性正则，摘要 prompt 只负责"不许丢"，不负责"找出来"）。 */
    public static List<KeyRef> preExtractKeyRefs(List<AgentSessionService.AgentMessage> messages) {
        Map<String, KeyRef> unique = new LinkedHashMap<>();
        for (AgentSessionService.AgentMessage m : messages) {
            StringBuilder text = new StringBuilder();
            appendIfPresent(text, m.getContent());
            appendIfPresent(text, m.getToolResultsRaw());
            if (m.getToolCalls() != null) {
                appendIfPresent(text, String.valueOf(m.getToolCalls()));
            }
            String haystack = text.toString();
            for (RefPattern ref : REF_PATTERNS) {
                Matcher matcher = ref.pattern().matcher(haystack);
                while (matcher.find()) {
                    String value = matcher.group(1);
                    String type = ref.type();
                    String link = "EVIDENCE".equals(type) ? value : null;
                    unique.putIfAbsent(type + "|" + value, new KeyRef(type, value, link));
                }
            }
        }
        return new ArrayList<>(unique.values());
    }

    /**
     * 经 llm-gateway 生成结构化摘要并做引用完整性硬校验（铁律 §2.5-2：摘要必经网关）。
     *
     * <p>三类失败都归入不可用并带机器可读短码：{@code llm_call_failed} /
     * {@code summary_unparsable} / {@code reference_check_failed}。禁在此抛穿调用方。</p>
     */
    SummaryResult summarize(String model, String sessionId,
                            List<AgentSessionService.AgentMessage> range, List<KeyRef> preExtracted) {
        ChatRequest request = new ChatRequest();
        request.setModel(model);
        request.setSessionId(sessionId);
        request.setCallerId(SUMMARIZER_CALLER_ID);
        request.setTemperature(0.0);
        request.setStream(false);
        request.setMessages(List.of(
                new ChatMessage("system", SUMMARY_SYSTEM_PROMPT),
                new ChatMessage("user", renderForSummary(range, preExtracted))));
        ChatResponse response;
        try {
            response = llmGateway.call(request);
        } catch (Exception e) {
            log.warn("[F06-09] 摘要 LLM 调用异常 ⇒ 降级截断: session={} err={}",
                    sessionId, e.getMessage());
            return SummaryResult.failed("llm_call_failed", preExtracted);
        }
        if (response == null || !response.isSuccess()
                || response.getContent() == null || response.getContent().isBlank()) {
            log.warn("[F06-09] 摘要 LLM 未返回可用内容 ⇒ 降级截断: session={}", sessionId);
            return SummaryResult.failed("llm_call_failed", preExtracted);
        }
        JsonNode root = parseJsonObject(response.getContent());
        if (root == null) {
            log.warn("[F06-09] 摘要输出不可解析为 JSON 对象 ⇒ 降级截断: session={}", sessionId);
            return SummaryResult.failed("summary_unparsable", preExtracted);
        }
        List<KeyRef> summaryRefs = readKeyRefs(root);
        Set<String> requiredRunIds = typeIds(preExtracted, "FORECAST_RUN");
        Set<String> givenRunIds = typeIds(summaryRefs, "FORECAST_RUN");
        // 硬校验：被压缩段 runId 集合 ⊆ 摘要 keyRefs 集合（缺一枚即降级，禁"差不多"放行）
        Set<String> dropped = new LinkedHashSet<>(requiredRunIds);
        dropped.removeAll(givenRunIds);
        if (!dropped.isEmpty()) {
            log.warn("[F06-09] 引用完整性硬校验失败 ⇒ 降级截断: session={} dropped={}",
                    sessionId, dropped);
            return new SummaryResult(false, "reference_check_failed", root, summaryRefs, dropped);
        }
        return new SummaryResult(true, null, root, summaryRefs, Set.of());
    }

    private String renderForSummary(List<AgentSessionService.AgentMessage> range,
                                    List<KeyRef> preExtracted) {
        StringBuilder sb = new StringBuilder();
        sb.append("必须原样保留的引用（一条不得省略）：\n");
        for (KeyRef ref : preExtracted) {
            sb.append("- ").append(ref.type()).append(" id=").append(ref.id());
            if (ref.link() != null) {
                sb.append(" link=").append(ref.link());
            }
            sb.append('\n');
        }
        sb.append("\n待压缩历史轮次：\n");
        for (AgentSessionService.AgentMessage m : range) {
            sb.append(m.getRole()).append(": ");
            appendIfPresent(sb, m.getContent());
            if (m.getToolCalls() != null) {
                sb.append(" toolCalls=").append(m.getToolCalls());
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    private JsonNode parseJsonObject(String content) {
        int start = content.indexOf('{');
        int end = content.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(content.substring(start, end + 1));
            return node != null && node.isObject() ? node : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static List<KeyRef> readKeyRefs(JsonNode root) {
        List<KeyRef> refs = new ArrayList<>();
        JsonNode keyRefs = root.path("keyRefs");
        if (keyRefs.isArray()) {
            for (JsonNode node : keyRefs) {
                String type = node.path("type").asText(null);
                String id = node.path("id").asText(null);
                if (type == null || id == null || id.isBlank()) {
                    continue;
                }
                String link = node.path("link").asText(null);
                refs.add(new KeyRef(type, id, link == null || link.isBlank() ? null : link));
            }
        }
        return refs;
    }

    private static Set<String> typeIds(List<KeyRef> refs, String type) {
        Set<String> ids = new LinkedHashSet<>();
        for (KeyRef ref : refs) {
            if (type.equals(ref.type())) {
                ids.add(ref.id());
            }
        }
        return ids;
    }

    /** 摘要结构序列化进 TEXT 列（MC02 禁 jsonb：库里是文本载荷，检索面只走 key_refs 串）。 */
    private String serializeSummary(SummaryResult result) {
        try {
            Map<String, Object> structured = new LinkedHashMap<>();
            structured.put("confirmedFacts", readStringArray(result.root().path("confirmedFacts")));
            structured.put("userPreferences", readStringArray(result.root().path("userPreferences")));
            structured.put("openQuestions", readStringArray(result.root().path("openQuestions")));
            List<Map<String, Object>> refs = new ArrayList<>();
            for (KeyRef ref : result.keyRefs()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("type", ref.type());
                row.put("id", ref.id());
                row.put("link", ref.link());
                refs.add(row);
            }
            structured.put("keyRefs", refs);
            return objectMapper.writeValueAsString(structured);
        } catch (Exception e) {
            log.error("[F06-09] 摘要结构序列化失败 ⇒ 降级截断: err={}", e.getMessage());
            return null;
        }
    }

    private static List<String> readStringArray(JsonNode node) {
        List<String> values = new ArrayList<>();
        if (node != null && node.isArray()) {
            for (JsonNode item : node) {
                String text = item.asText(null);
                if (text != null && !text.isBlank()) {
                    values.add(text);
                }
            }
        }
        return values;
    }

    // ═══════════════════════════════════════════════════
    //  落库：LLM_SUMMARY / TRUNCATE_FALLBACK（要点 5、6）
    // ═══════════════════════════════════════════════════

    private Outcome applySummary(String sessionId, String traceId, Plan plan,
                                 List<KeyRef> preExtracted, SummaryResult summary,
                                 int estimatedBefore, int thresholdTokens, int modelLimit) {
        String content = serializeSummary(summary);
        if (content == null) {
            return applyTruncateFallback(sessionId, traceId, plan, preExtracted,
                    SummaryResult.failed("summary_unparsable", preExtracted),
                    estimatedBefore, thresholdTokens, modelLimit);
        }
        String batchId = newBatchId();
        String keptRefs = joinRefs(summary.keyRefs());
        int summaryTokens = TokenEstimates.estimateText(content);
        int estimatedAfter = estimateKeptTokens(plan, summaryTokens);

        registerBatch(batchId, sessionId, MODE_LLM_SUMMARY, plan, keptRefs, null, null,
                estimatedBefore, estimatedAfter, traceId);
        int archived = sessionDao.archiveMessagesByIds(sessionId, plan.archivedIds(), batchId);
        sessionDao.insertSummaryMessage(sessionId, content, keptRefs, batchId, summaryTokens,
                System.currentTimeMillis());
        markSessionMode(sessionId, MODE_LLM_SUMMARY);

        log.info("[F06-09] 压缩完成(LLM_SUMMARY): session={} batch={} archived={} compressedTurns={} "
                + "estimatedBefore={} estimatedAfter={} threshold={} modelLimit={}",
                sessionId, batchId, archived, plan.compressedTurns(), estimatedBefore, estimatedAfter,
                thresholdTokens, modelLimit);
        return new Outcome(Status.COMPRESSED, false, MODE_LLM_SUMMARY, estimatedBefore, estimatedAfter,
                thresholdTokens, archived, plan.protectedMessages().size(), plan.compressedTurns(),
                batchId, traceId, null).withModelLimit(modelLimit);
    }

    private Outcome applyTruncateFallback(String sessionId, String traceId, Plan plan,
                                          List<KeyRef> preExtracted, SummaryResult summary,
                                          int estimatedBefore, int thresholdTokens, int modelLimit) {
        String batchId = newBatchId();
        String keptRefs = joinRefs(preExtracted);
        // 降级仍保留原件（逻辑归档），只把最早轮次移出上下文；引用串写入批次台账供抽检
        String dropped = String.join(",", summary.droppedIds());
        int estimatedAfter = estimateKeptTokens(plan, 0);
        registerBatch(batchId, sessionId, MODE_TRUNCATE_FALLBACK, plan, keptRefs,
                dropped.isBlank() ? null : dropped, summary.failureReason(),
                estimatedBefore, estimatedAfter, traceId);
        int archived = sessionDao.archiveMessagesByIds(sessionId, plan.archivedIds(), batchId);
        markSessionMode(sessionId, MODE_TRUNCATE_FALLBACK);

        log.warn("[F06-09] 摘要不可用 ⇒ 退化为截断最早轮(TRUNCATE_FALLBACK)并上报事件: "
                + "session={} batch={} archived={} reason={} droppedRefs={}",
                sessionId, batchId, archived, summary.failureReason(), dropped);
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("compressSkipped", false);
        event.put("compressionMode", MODE_TRUNCATE_FALLBACK);
        event.put("failureReason", summary.failureReason());
        event.put("droppedKeyRefs", dropped);
        event.put("archivedCount", archived);
        event.put("compressedTurns", plan.compressedTurns());
        event.put("estimatedTokensBefore", estimatedBefore);
        event.put("estimatedTokensAfter", estimatedAfter);
        publishQuiet(EventTypes.Session.COMPRESSION_FALLBACK, sessionId, traceId, event);

        return new Outcome(Status.TRUNCATE_FALLBACK, false, MODE_TRUNCATE_FALLBACK,
                estimatedBefore, estimatedAfter, thresholdTokens, archived,
                plan.protectedMessages().size(), plan.compressedTurns(), batchId, traceId,
                summary.failureReason()).withModelLimit(modelLimit);
    }

    private void registerBatch(String batchId, String sessionId, String mode, Plan plan,
                               String keptRefs, String droppedRefs, String failureReason,
                               int estimatedBefore, int estimatedAfter, String traceId) {
        try {
            batchDao.insertBatch(batchId, sessionId, mode, plan.rangeFromSeq(), plan.rangeToSeq(),
                    estimatedBefore, estimatedAfter, keptRefs, droppedRefs, failureReason,
                    traceId, SUMMARIZER_CALLER_ID);
        } catch (Exception e) {
            // 台账写失败不阻断归档（归档本身是减量前提），但必须 ERROR 留痕供对账
            log.error("[F06-09] 压缩批次台账写入失败（归档继续，需人工对账）: session={} batch={} err={}",
                    sessionId, batchId, e.getMessage(), e);
        }
    }

    private void markSessionMode(String sessionId, String mode) {
        try {
            sessionDao.updateSessionCompressionMode(sessionId, mode);
            sessionDao.recalcMessageCount(sessionId);
        } catch (Exception e) {
            log.error("[F06-09] 会话压缩模式/计数回写失败: session={} mode={} err={}",
                    sessionId, mode, e.getMessage(), e);
        }
    }

    /**
     * 压缩后的上下文估算 = 摘要行 + 保留窗口原文 + 引用保护原文。
     *
     * <p>与 {@link #estimateSessionTokens} 同一口径（{@link TokenEstimates} 单源），
     * 否则 before/after 不可比，阈值判定也就不可复现。</p>
     */
    private int estimateKeptTokens(Plan plan, int summaryTokens) {
        long total = summaryTokens;
        total += estimateMessageTokens(plan.keptMessages());
        total += estimateMessageTokens(plan.protectedMessages());
        return (int) Math.min(Integer.MAX_VALUE, total);
    }

    private static long estimateMessageTokens(List<AgentSessionService.AgentMessage> messages) {
        long total = 0;
        for (AgentSessionService.AgentMessage m : messages) {
            if (m.getTokens() != null && m.getTokens() > 0) {
                total += m.getTokens();
                continue;
            }
            StringBuilder text = new StringBuilder();
            appendIfPresent(text, m.getContent());
            appendIfPresent(text, m.getToolResultsRaw());
            if (m.getToolCalls() != null) {
                appendIfPresent(text, String.valueOf(m.getToolCalls()));
            }
            total += TokenEstimates.estimateText(text.toString())
                    + TokenEstimates.ROLE_OVERHEAD_TOKENS;
        }
        return total;
    }

    private static String newBatchId() {
        return UUID.randomUUID().toString();
    }

    private static String joinRefs(List<KeyRef> refs) {
        StringBuilder sb = new StringBuilder();
        for (KeyRef ref : refs) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(ref.type()).append('=').append(ref.id());
        }
        return sb.toString();
    }

    /** 事件上报（runtime-monitor/审计消费面）；总线缺失只 ERROR，不阻断压缩。 */
    private void publishQuiet(String eventType, String sessionId, String traceId,
                              Map<String, Object> payload) {
        EventBusService bus = eventBusProvider.getIfAvailable();
        if (bus == null) {
            log.error("[F06-09] 事件总线不可用 ⇒ 压缩治理事件未上报（禁静默断链）: eventType={} "
                    + "session={} trace={}", eventType, sessionId, traceId);
            return;
        }
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("eventType", eventType);
        event.put("sessionId", sessionId);
        event.put("traceId", traceId);
        event.putAll(payload);
        try {
            bus.publish(KafkaTopics.AUDIT, event);
        } catch (Exception e) {
            log.error("[F06-09] 压缩治理事件上报失败: eventType={} session={} err={}",
                    eventType, sessionId, e.getMessage(), e);
        }
    }

    // ═══════════════════════════════════════════════════
    //  值对象
    // ═══════════════════════════════════════════════════

    /** 压缩结果状态。 */
    public enum Status {
        /** 未达 token 阈值，无需压缩 */
        BELOW_THRESHOLD,
        /** 压缩完成（LLM 结构化摘要） */
        COMPRESSED,
        /** 摘要不可用 ⇒ 已退化为截断最早轮并打标 */
        TRUNCATE_FALLBACK,
        /** 开关关闭 ⇒ 未压缩且必须上报 compressSkipped=true */
        SWITCH_OFF,
        /** 配置门面不可用 ⇒ 不判定 */
        CONFIG_UNAVAILABLE,
        /** 模型目录未给上限 ⇒ 不判定 */
        MODEL_LIMIT_UNAVAILABLE,
        /** 治理库面未就位 ⇒ 不判定 */
        SCHEMA_UNAVAILABLE,
        /** 超阈值但保留窗口覆盖全部历史 ⇒ 无可压缩段 */
        NOTHING_TO_COMPRESS,
        /** 无活消息 */
        NO_MESSAGES,
        /** 无会话作用域 */
        NO_SCOPE
    }

    /**
     * 一次压缩判定/执行的结果快照。
     *
     * @param compressSkipped      true = 本轮<b>未</b>做压缩（关闭/不可判定/无可压缩段），
     *                             调用方须原样上报（禁静默关闭，F06-09 要点 1）
     * @param compressionMode      实际生效模式 {@code LLM_SUMMARY|TRUNCATE_FALLBACK}，未压缩为 null
     * @param protectedRefs        因工具引用保护而原文保留的消息条数（要点 4 的可观测量）
     * @param reason               机器可读短码，只入日志/事件/诊断，禁入面向用户文案
     */
    public record Outcome(Status status, boolean compressSkipped, String compressionMode,
                          int estimatedTokensBefore, int estimatedTokensAfter, int thresholdTokens,
                          int archivedCount, int protectedCount, int compressedTurns,
                          String batchId, String traceId, String reason, int modelLimit) {

        static Outcome of(Status status, String reason, boolean compressSkipped) {
            return new Outcome(status, compressSkipped, null, 0, 0, 0, 0, 0, 0, null, null, reason, 0);
        }

        /** 判定各点的规范构造：模型上限由 {@link #withModelLimit} 在读取目录后补记。 */
        Outcome(Status status, boolean compressSkipped, String compressionMode,
                int estimatedTokensBefore, int estimatedTokensAfter, int thresholdTokens,
                int archivedCount, int protectedCount, int compressedTurns,
                String batchId, String traceId, String reason) {
            this(status, compressSkipped, compressionMode, estimatedTokensBefore, estimatedTokensAfter,
                    thresholdTokens, archivedCount, protectedCount, compressedTurns, batchId,
                    traceId, reason, 0);
        }

        /** 只读派生：补记模型目录上限（不改判定流，供 SSE done 帧与日志对账）。 */
        Outcome withModelLimit(int limit) {
            return new Outcome(status, compressSkipped, compressionMode, estimatedTokensBefore,
                    estimatedTokensAfter, thresholdTokens, archivedCount, protectedCount,
                    compressedTurns, batchId, traceId, reason, limit);
        }
    }

    /** 配置读数快照（P1.3 #12 context 阈值读数面共享；字段皆为字典面单源真值）。 */
    public record Settings(boolean facadeAvailable, boolean enabled, double threshold, int keepRecent) {}

    /** 关键引用（摘要结构里的 keyRefs 单元）。 */
    public record KeyRef(String type, String id, String link) {}

    /** 轮次划分结果。{@code compressedTurns} = 实际有原件被归档的轮数（⑥ 状态条口径）。 */
    record Plan(List<AgentSessionService.AgentMessage> compressedRange,
                List<AgentSessionService.AgentMessage> protectedMessages,
                List<AgentSessionService.AgentMessage> keptMessages,
                List<Long> archivedIds,
                Integer rangeFromSeq,
                Integer rangeToSeq,
                Set<String> protectedIds,
                int compressedTurns) {}

    /** 摘要生成结果。 */
    record SummaryResult(boolean usable, String failureReason, JsonNode root,
                         List<KeyRef> keyRefs, Set<String> droppedIds) {

        static SummaryResult failed(String reason, List<KeyRef> preExtracted) {
            Set<String> dropped = typeIdsOf(preExtracted);
            return new SummaryResult(false, reason, null, List.of(), dropped);
        }

        private static Set<String> typeIdsOf(List<KeyRef> refs) {
            Set<String> ids = new LinkedHashSet<>();
            for (KeyRef ref : refs) {
                if ("FORECAST_RUN".equals(ref.type())) {
                    ids.add(ref.id());
                }
            }
            return ids;
        }
    }

    private record RefPattern(String type, Pattern pattern) {}

    /** 会话线程单源（禁散落 {@code "main"} 字面量）。 */
    static final class AgentMessageThread {
        static final String MAIN = "main";

        private AgentMessageThread() {}
    }
}
