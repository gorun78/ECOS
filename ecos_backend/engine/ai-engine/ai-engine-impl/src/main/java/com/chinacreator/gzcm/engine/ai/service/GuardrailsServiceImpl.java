package com.chinacreator.gzcm.engine.ai.service;

import com.chinacreator.gzcm.common.error.AiError;
import com.chinacreator.gzcm.common.error.AiErrorClass;
import com.chinacreator.gzcm.common.security.guardrail.GuardrailDecisionContract;
import com.chinacreator.gzcm.engine.ai.GuardrailsService;
import com.chinacreator.gzcm.engine.ai.dao.GuardrailsPolicyDao;
import com.chinacreator.gzcm.engine.ai.guardrail.ToolAdjudicationRequest;
import com.chinacreator.gzcm.engine.ai.guardrail.ToolObligations;
import com.chinacreator.gzcm.engine.ai.security.AiSecurityEngineClient;
import com.chinacreator.gzcm.engine.ai.security.GuardrailAuditPublisher;
import com.chinacreator.gzcm.engine.ai.security.ToolSubjectResolver;
import com.chinacreator.gzcm.engine.ai.trace.AiTraceSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 输入/输出护栏（详细设计-06 F06-04；替代"本地正则冒充护栏"的旧形态）。
 *
 * <p><b>裁决权威只在 security-engine</b>（铁律 §2.4-3/6/7）：两个拦截点（用户输入、模型输出）
 * 一律经 {@code POST /api/v1/security/policy-engine/evaluate}，脱敏义务一律经
 * {@code POST /api/v1/security/masking/apply} 执行 —— 引擎内不存在任何内容合规判定算法。</p>
 *
 * <p>三态与原文旧形态的差异（X-15/X-16 消灭对象）：</p>
 * <ul>
 *   <li><b>粗筛（本类唯一的正则）</b>：只用于两件事 —— ① 把"疑似违规片段"作为提示随载荷上报，
 *       ② 作为 security 下发的 mask 义务在文本中的<b>定位器</b>。
 *       <b>粗筛未命中永不构成放行依据</b>，放行权只在 security 的显式 ALLOW
 *       （验收 {@code GuardrailComplianceArchTest#noLocalRegexUsedAsPassDecision}）；</li>
 *   <li>security 不可用 / 超时 / 响应不可解析 / 裁决字段不可识别 ⇒
 *       {@link AiErrorClass#GUARDRAIL_FAIL_CLOSED}（503），{@code passed=false}，
 *       调用方（{@code AgentLoopService}）必须终止本次 Loop（旧版 {@code catch → log.warn}
 *       后继续是 fail-open，本类起不再有该分支）；</li>
 *   <li>DENY 与 FAIL_CLOSED 严格分态（{@code errorCode} 403/503 与文案不同，F06-14）；</li>
 *   <li>任何一次拦截（BLOCKED / FILTERED / FAIL_CLOSED）都发 {@code GUARDRAIL_FILTER}
 *       审计事件，<b>发布失败即整体 FAIL_CLOSED</b>（否则 §1.5-1 对账不可得，X-20）。</li>
 * </ul>
 *
 * <p>接缝义务（详细设计-06 §八 / WP13）：security-engine 现无"内容合规"专用端点，
 * 本类复用 {@code policy-engine/evaluate}（action {@code ai:guardrail:input} /
 * {@code ai:guardrail:output}）；专用端点上线后只改本类的常量路径，不改
 * {@link GuardrailsService} 接口。</p>
 */
@Service
public class GuardrailsServiceImpl implements GuardrailsService {

    private static final Logger log = LoggerFactory.getLogger(GuardrailsServiceImpl.class);

    private static final String POLICY_NAME = "rbac";
    private static final String GUARDRAIL_TYPE = "CONTENT_GUARDRAIL";
    private static final String ACTION_PREFIX = "ai:guardrail:";

    public static final String STAGE_INPUT = "input";
    public static final String STAGE_OUTPUT = "output";

    private static final String OUTCOME_BLOCKED = "BLOCKED";
    private static final String OUTCOME_FILTERED = "FILTERED";
    private static final String OUTCOME_FAIL_CLOSED = "FAIL_CLOSED";

    private static final AtomicLong ID_SEQ = new AtomicLong(0);

    /**
     * 粗筛片段（<b>不是</b>判定器）：type 字面量与 security-engine 脱敏规则词表
     * （{@code email/phone/idCard/bankCard}）对齐，便于 mask 义务定位。
     */
    private static final List<CoarseSpan> COARSE_SPANS = List.of(
            new CoarseSpan("phone", Pattern.compile("1[3-9]\\d{9}")),
            new CoarseSpan("idCard", Pattern.compile("\\d{17}[\\dXx]")),
            new CoarseSpan("email", Pattern.compile("[\\w.-]+@[\\w.-]+\\.\\w+")),
            new CoarseSpan("bankCard", Pattern.compile("\\b\\d{16,19}\\b")),
            new CoarseSpan("injection", Pattern.compile(
                    "(?i)(\\bdrop\\s+table\\b|\\bdelete\\s+from\\b|\\binsert\\s+into\\b|--)")),
            new CoarseSpan("jailbreak", Pattern.compile(
                    "(?i)(ignore\\s+(all\\s+)?(previous|above)\\s+instructions?"
                    + "|pretend\\s+you\\s+are|you\\s+are\\s+DAN|override\\s+system\\s+prompt"
                    + "|ignore\\s+all\\s+constraints)")));

    /** 命中后 {@code AgentLoopService} 的整段替换文案（F06-04 要点 3 保留现行为）。 */
    public static final String FILTERED_PLACEHOLDER = "[内容已根据安全策略过滤]";

    private final GuardrailsPolicyDao dao;
    private final AiSecurityEngineClient securityClient;
    private final GuardrailAuditPublisher auditPublisher;
    private final ToolSubjectResolver subjectResolver;

    public GuardrailsServiceImpl(GuardrailsPolicyDao dao,
                                 AiSecurityEngineClient securityClient,
                                 GuardrailAuditPublisher auditPublisher,
                                 ToolSubjectResolver subjectResolver) {
        this.dao = dao;
        this.securityClient = securityClient;
        this.auditPublisher = auditPublisher;
        this.subjectResolver = subjectResolver;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> validate(Map<String, Object> req) {
        String stage = normalizeStage(req == null ? null : req.get("stage"));
        String content = req == null ? "" : String.valueOf(req.getOrDefault("llmOutput", ""));
        String sessionId = text(req == null ? null : req.get("sessionId"));
        List<String> contextFacts = req != null && req.get("contextFacts") instanceof List
                ? (List<String>) req.get("contextFacts") : Collections.emptyList();

        List<CoarseHit> coarse = coarseScreen(content);
        ToolAdjudicationRequest.Subject subject = currentSubject();

        if (subject == null || subject.userId() == null || subject.userId().isBlank()) {
            return failClosed(stage, sessionId, subject, content,
                    "无法从服务端 token 上下文解析主体，护栏无法裁决（fail-closed）", null);
        }

        Map<String, Object> data;
        try {
            data = securityClient.evaluateData(evaluateBody(stage, content, coarse, subject, sessionId));
        } catch (Exception e) {
            log.warn("护栏 FAIL_CLOSED: security-engine 不可用/超时/响应不可解析: stage={} err={}",
                    stage, e.getMessage(), e);
            return failClosed(stage, sessionId, subject, content,
                    "security-engine 不可用或裁决响应不可解析（fail-closed，整次请求拒绝）", e);
        }

        String verdict = AiSecurityEngineClient.verdictOf(data);
        if (AiSecurityEngineClient.V_DENY.equals(verdict)) {
            String reason = deniedReasonOf(data);
            return blocked(stage, sessionId, subject, content, reason, policyIdOf(data));
        }
        if (!AiSecurityEngineClient.V_ALLOW.equals(verdict)) {
            return failClosed(stage, sessionId, subject, content,
                    "security-engine 未给出可识别的裁决（缺 decision/allow 字段或自身处于兜底态）", null);
        }

        // ── 显式 ALLOW：先执行 obligations（回模前，F06-02 同源纪律），再做本地仅否决项 ──
        String maskedContent = content;
        List<String> appliedRules = new ArrayList<>();
        ToolObligations obligations = ToolObligations.fromEvaluateData(data);
        if (!obligations.isEmpty()) {
            try {
                MaskOutcome maskOutcome = applyMaskObligations(content, obligations, coarse);
                maskedContent = maskOutcome.content();
                appliedRules.addAll(maskOutcome.appliedRules());
            } catch (Exception e) {
                log.warn("护栏 FAIL_CLOSED: security 下发的脱敏义务无法执行: stage={} err={}",
                        stage, e.getMessage(), e);
                return failClosed(stage, sessionId, subject, content,
                        "脱敏义务无法执行（缺片段定位依据或 security 返回形态不符），整段结果丢弃", e);
            }
        }

        List<String> violations = new ArrayList<>();
        boolean passed = true;
        if (!contextFacts.isEmpty() && !containsAnyFact(maskedContent, contextFacts)) {
            violations.add("HALLUCINATION: 输出不包含任何上下文事实");
            passed = false;
        }
        if (!passed) {
            // 本地仅否决：事实一致性检查只能把 ALLOW 拉回不通过，反向永不放行（§2.4-7）
            emitFilterAudit(subject, sessionId, stage, OUTCOME_BLOCKED,
                    AiErrorClass.GUARDRAIL_DENIED.errorCode(), digest(content), "本地事实一致性否决");
            log.info("护栏本地仅否决: stage={} sessionId={}", stage, sessionId);
        }

        Map<String, Object> result = baseResult(stage, passed ? "ALLOW" : "DENY", passed);
        result.put("violations", violations);
        result.put("content", maskedContent);
        result.put("advisory", Map.of(
                "coarseHits", hitTypes(coarse),
                "appliedMaskRules", appliedRules,
                GuardrailDecisionContract.KEY_OBLIGATIONS, obligations.summary()));
        result.put("policyId", policyIdOf(data));
        result.put("traceId", AiTraceSupport.currentTraceId());
        return result;
    }

    // ─── 载荷构造 ─────────────────────────────────────────────────────

    /** 四段载荷（subject 只来自服务端上下文，F06-02 同源纪律）。 */
    private Map<String, Object> evaluateBody(String stage, String content, List<CoarseHit> coarse,
                                             ToolAdjudicationRequest.Subject subject, String sessionId) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("action", ACTION_PREFIX + stage);
        input.put("type", GUARDRAIL_TYPE);
        input.put("stage", stage);
        input.put("subject", subjectMap(subject));
        // 内容必须交 security 判定（引擎不实现任何合规判定）；同时给摘要便于审计对账
        input.put("content", content == null ? "" : content);
        input.put("contentDigest", digest(content));
        input.put("contentLength", content == null ? 0 : content.length());
        Map<String, Object> resource = new LinkedHashMap<>();
        resource.put("coarseHints", hitTypes(coarse));
        if (sessionId != null) resource.put("sessionId", sessionId);
        input.put("resource", resource);
        Map<String, Object> environment = new LinkedHashMap<>();
        environment.put("traceId", AiTraceSupport.currentTraceId());
        environment.put("channel", ToolAdjudicationRequest.CHANNEL_AGENT);
        environment.put("timestamp", Instant.now().toString());
        input.put("environment", environment);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("policy", POLICY_NAME);
        body.put("input", input);
        return body;
    }

    private Map<String, Object> subjectMap(ToolAdjudicationRequest.Subject subject) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("userId", subject.userId());
        m.put("roles", subject.roles() == null ? List.of() : subject.roles());
        m.put("department", subject.department());
        m.put("tenantId", subject.tenantId());
        return m;
    }

    private ToolAdjudicationRequest.Subject currentSubject() {
        try {
            return subjectResolver == null ? null : subjectResolver.resolve();
        } catch (Exception e) {
            log.warn("护栏主体解析失败，转 FAIL_CLOSED: {}", e.getMessage(), e);
            return null;
        }
    }

    // ─── 结果形态（passed/violations/checkedAt 为既有契约，其余为新增字段） ───────

    private Map<String, Object> baseResult(String stage, String decision, boolean passed) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("passed", passed);
        result.put("decision", decision);
        result.put("stage", stage);
        result.put("blocked", !passed);
        result.put("violations", new ArrayList<String>());
        result.put("checkedAt", Instant.now().toString());
        return result;
    }

    private Map<String, Object> failClosed(String stage, String sessionId,
                                           ToolAdjudicationRequest.Subject subject, String content,
                                           String rawReason, Exception cause) {
        AiError error = AiError.of(AiErrorClass.GUARDRAIL_FAIL_CLOSED,
                AiTraceSupport.currentTraceId(), rawReason);
        Map<String, Object> result = baseResult(stage, AiSecurityEngineClient.V_FAIL_CLOSED, false);
        result.put("violations", List.of("GUARDRAIL_FAIL_CLOSED: 安全服务不可用，本次请求已拒绝"));
        result.put("errorCode", error.errorCode());
        result.put("errorClass", error.errorClass().name());
        result.put("message", error.userMessage());
        result.put("traceId", error.traceId());
        result.put("retryable", error.retryable());
        result.put("content", "");
        emitFilterAudit(subject, sessionId, stage, OUTCOME_FAIL_CLOSED,
                error.errorCode(), digest(content), rawReason);
        log.error("护栏 FAIL_CLOSED（stage={}）: {} traceId={}", stage, rawReason, error.traceId(),
                cause == null ? new IllegalStateException(rawReason) : cause);
        return result;
    }

    private Map<String, Object> blocked(String stage, String sessionId,
                                        ToolAdjudicationRequest.Subject subject, String content,
                                        String reason, String policyId) {
        AiError error = AiError.of(AiErrorClass.GUARDRAIL_DENIED,
                AiTraceSupport.currentTraceId(), reason);
        Map<String, Object> result = baseResult(stage, AiSecurityEngineClient.V_DENY, false);
        result.put("violations", List.of("GUARDRAIL_DENIED: " + (reason == null ? "内容不符合安全策略" : reason)));
        result.put("errorCode", error.errorCode());
        result.put("errorClass", error.errorClass().name());
        result.put("message", error.userMessage());
        result.put("traceId", error.traceId());
        result.put("retryable", error.retryable());
        result.put("policyId", policyId);
        result.put("content", "");
        emitFilterAudit(subject, sessionId, stage, OUTCOME_BLOCKED, error.errorCode(),
                digest(content), reason);
        log.info("护栏 DENY: stage={} reason={} traceId={}", stage, reason, error.traceId());
        return result;
    }

    /**
     * 护栏留痕事件发布（F06-04 要点 3）。
     *
     * <p>发布失败只记 ERROR 并保留已经成立的拒绝/降级结论：拒绝态本身不会因为缺留痕而变回放行，
     * 但会破坏 §1.5-1 对账，必须可告警（{@code AiErrorClass#monitorAlerted}）。</p>
     */
    private void emitFilterAudit(ToolAdjudicationRequest.Subject subject, String sessionId, String stage,
                                 String outcome, String ruleId, String digest, String reason) {
        String userId = subject == null ? null : subject.userId();
        String tenantId = subject == null ? null : subject.tenantId();
        Map<String, Object> event = auditPublisher.buildFilterEvent(
                userId, tenantId, sessionId, stage, outcome, ruleId, digest);
        try {
            auditPublisher.filterAuditOrThrow(event, "guardrail:" + stage);
        } catch (Exception e) {
            log.error("护栏审计事件发布失败（留痕缺失，拒绝结论不翻转但破坏 §1.5-1 对账）: stage={} outcome={} reason={} err={}",
                    stage, outcome, reason, e.getMessage(), e);
        }
    }

    // ─── 粗筛（仅提示 + 定位，永不放行） ────────────────────────────────

    private List<CoarseHit> coarseScreen(String content) {
        if (content == null || content.isEmpty()) return List.of();
        List<CoarseHit> hits = new ArrayList<>();
        for (CoarseSpan span : COARSE_SPANS) {
            Matcher m = span.pattern().matcher(content);
            Set<String> values = new LinkedHashSet<>();
            while (m.find()) {
                String v = m.group();
                if (v != null && !v.isBlank()) values.add(v);
                if (values.size() >= MAX_COARSE_PER_TYPE) break;
            }
            for (String v : values) hits.add(new CoarseHit(span.type(), v));
        }
        return hits;
    }

    private static List<String> hitTypes(List<CoarseHit> hits) {
        Set<String> types = new LinkedHashSet<>();
        for (CoarseHit h : hits) types.add(h.type());
        return List.copyOf(types);
    }

    private static final int MAX_COARSE_PER_TYPE = 200;
    private static final int MAX_MASK_ITEMS = 500;

    /**
     * 执行 security 下发的脱敏义务：引擎侧只做"把片段交给 security、用返回值替换"，
     * 不含任何脱敏算法（验收 {@code ObligationsApplyTest#engineDoesNotSelfImplementMasking}）。
     *
     * <p>义务规则在文本中无可定位片段时视为<b>空义务</b>（无需替换，不是放行判定，因为放行权
     * 已由 security 的显式 ALLOW 成立）；返回形态不符（数量不匹配、缺 index/masked、越界）
     * 一律抛异常 → 整段结果丢弃 + FAIL_CLOSED。</p>
     */
    private MaskOutcome applyMaskObligations(String content, ToolObligations obligations,
                                             List<CoarseHit> coarse) throws Exception {
        List<String> values = new ArrayList<>();
        List<String> rules = new ArrayList<>();
        List<String> applied = new ArrayList<>();
        for (ToolObligations.Mask mask : obligations.mask()) {
            String rule = mask.rule();
            if (rule == null || rule.isBlank()) continue;
            for (CoarseHit hit : coarse) {
                if (!rule.equalsIgnoreCase(hit.type())) continue;
                if (values.size() >= MAX_MASK_ITEMS) break;
                values.add(hit.value());
                rules.add(rule);
            }
            applied.add(rule);
        }
        if (values.isEmpty()) {
            return new MaskOutcome(content, applied);
        }
        List<Map<String, Object>> results = securityClient.applyMasking(values, rules);
        if (results == null || results.size() != values.size()) {
            throw new IllegalStateException("masking/apply 返回条数与提交片段数不一致，无法保证逐条脱敏");
        }
        String out = content;
        for (Map<String, Object> item : results) {
            Object indexObj = item.get("index");
            Object maskedObj = item.get("masked");
            if (!(indexObj instanceof Number) || maskedObj == null) {
                throw new IllegalStateException("masking/apply 返回项缺 index/masked");
            }
            int index = ((Number) indexObj).intValue();
            if (index < 0 || index >= values.size()) {
                throw new IllegalStateException("masking/apply 返回 index 越界: " + index);
            }
            out = out.replace(values.get(index), String.valueOf(maskedObj));
        }
        return new MaskOutcome(out, applied);
    }

    private record CoarseSpan(String type, Pattern pattern) {
    }

    private record CoarseHit(String type, String value) {
    }

    private record MaskOutcome(String content, List<String> appliedRules) {
    }

    // ─── 小工具 ────────────────────────────────────────────────────────

    private static String normalizeStage(Object raw) {
        String s = text(raw);
        if (s == null) return STAGE_OUTPUT;
        String lower = s.trim().toLowerCase(Locale.ROOT);
        return STAGE_INPUT.equals(lower) || "user_input".equals(lower) ? STAGE_INPUT : STAGE_OUTPUT;
    }

    private static String text(Object v) {
        if (v == null) return null;
        String s = String.valueOf(v).trim();
        return s.isEmpty() || "null".equals(s) ? null : s;
    }

    private static boolean containsAnyFact(String content, List<String> facts) {
        for (String fact : facts) {
            if (fact != null && content != null && content.contains(fact)) return true;
        }
        return false;
    }

    private static String policyIdOf(Map<String, Object> data) {
        if (data == null) return null;
        Object id = data.get("policyId");
        if (id == null) id = data.get(GuardrailDecisionContract.KEY_POLICY_REF);
        if (id == null) id = data.get("policy");
        return id == null ? null : String.valueOf(id);
    }

    /** security 明确 DENY 时的可读原因（义务集里的 deniedReason，缺省泛化表述）。 */
    private static String deniedReasonOf(Map<String, Object> data) {
        String reason = GuardrailDecisionContract.readDeniedReason(
                GuardrailDecisionContract.obligationsOf(data));
        return reason != null ? reason : "security-engine 裁决 DENY";
    }

    /** 审计用内容摘要：SHA-256 前 16 位十六进制，禁入原文（F06-03 要点 2）。 */
    private static String digest(String content) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest((content == null ? "" : content).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(16);
            for (int i = 0; i < Math.min(8, hash.length); i++) {
                sb.append(String.format("%02x", hash[i]));
            }
            return sb.toString();
        } catch (Exception e) {
            return "len=" + (content == null ? 0 : content.length());
        }
    }

    // ─── 策略 CRUD（接口不变；DB 不可用只影响管理面，不影响裁决链） ───────────

    @Override
    public List<Map<String, Object>> listPolicies() {
        try {
            return dao.listPolicies();
        } catch (Exception e) {
            log.warn("护栏策略表不可用，管理面返回空列表（裁决链不受影响）: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    @Override
    public Map<String, Object> createPolicy(Map<String, Object> policy) {
        if (policy == null) {
            throw new com.chinacreator.gzcm.common.exception.ValidationException("policy is required");
        }
        String name = String.valueOf(policy.getOrDefault("name", ""));
        if (name.isEmpty() || "null".equals(name)) {
            throw new com.chinacreator.gzcm.common.exception.ValidationException("name is required");
        }

        String id = String.valueOf(ID_SEQ.incrementAndGet());
        long now = Instant.now().toEpochMilli();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id);
        result.put("name", name);
        result.put("type", policy.getOrDefault("type", "block"));
        result.put("severity", policy.getOrDefault("severity", "medium"));
        result.put("isEnabled", policy.getOrDefault("isEnabled", true));
        result.put("parameters", policy.getOrDefault("parameters", Collections.emptyMap()));
        result.put("createdAt", now);
        result.put("updatedAt", now);

        try {
            dao.insertPolicy(id, name,
                    String.valueOf(result.get("type")),
                    String.valueOf(result.get("severity")),
                    Boolean.TRUE.equals(result.get("isEnabled")));
        } catch (Exception e) {
            log.warn("护栏策略落库失败（仅内存返回，管理面需修正）: id={} err={}", id, e.getMessage(), e);
        }

        log.info("Guardrails policy created: id={} name={}", id, name);
        return result;
    }

    @Override
    public void deletePolicy(String id) {
        try {
            dao.deletePolicy(id);
        } catch (Exception e) {
            log.warn("护栏策略删除未影响数据库（表不可用或记录不存在）: id={} err={}", id, e.getMessage(), e);
        }
        log.info("Guardrails policy deleted: id={}", id);
    }
}
