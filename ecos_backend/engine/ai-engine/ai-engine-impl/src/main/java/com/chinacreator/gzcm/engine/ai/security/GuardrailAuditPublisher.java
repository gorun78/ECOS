package com.chinacreator.gzcm.engine.ai.security;

import com.chinacreator.gzcm.common.event.EventTypes;
import com.chinacreator.gzcm.common.event.KafkaTopics;
import com.chinacreator.gzcm.engine.ai.dao.GuardrailDecisionLedgerDao;
import com.chinacreator.gzcm.engine.ai.guardrail.ToolAdjudicationRequest;
import com.chinacreator.gzcm.engine.ai.guardrail.ToolDecision;
import com.chinacreator.gzcm.engine.ai.trace.AiTraceSupport;
import com.chinacreator.gzcm.runtime.eventbus.EventBusService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 护栏裁决审计唯一出口（详细设计-06 F06-03；铁律 §2.4-5 / §2.5-7）。
 *
 * <p>每次裁决（ALLOW / DENY / FAIL_CLOSED）都做两件事：</p>
 * <ol>
 *   <li><b>Kafka 事件（红线）</b>：经 runtime-event {@link EventBusService} 单通道发
 *       {@link KafkaTopics#AUDIT}，{@code eventType = GUARDRAIL_EVAL}。
 *       <b>通道未装配或发布抛异常 ⇒ 抛 {@link GuardrailAuditUnavailableException}
 *       使裁决整体失败</b>（不接受"尽力而为"，X-20 的 warn-continue 形态即本项要消灭的对象）。</li>
 *   <li><b>V204 台账（对账面）</b>：同批双写 {@code ecos_ai.ecos_ai_guardrail_decision_ledger}，
 *       供 PRD-06 §1.5-1 的"无裁决记录的工具调用次数 = 0"查询。
 *       台账写失败记 ERROR（含堆栈）但不翻转裁决结果 —— 决策权威与事件留痕均已在第 1 步成立，
 *       台账是索引面；此不对称是有意设计，禁据此推断"审计可以失败"。</li>
 * </ol>
 *
 * <p>载荷只带摘要与 ID：{@code detail} 内无 SQL 原文、无工具入参原文、无结果原文（F06-03 要点 2，
 * 验收 {@code GuardrailAuditEventTest#eventCarriesNoRawPayload}）。</p>
 *
 * <p><b>本类同时是执行腿拒绝态的唯一出口</b>（#142/B4-3e-1，差距 D-B4-33）：裁决闸之后、
 * dispatch 之前的限流腿若把一次已获 ALLOW 的调用拒掉，必须经
 * {@link #auditExecutionLegDenial} 再落一条事件 + 一行台账，否则台账面上这次调用只剩 ALLOW，
 * "义务已放行但执行被拒"不可见。两行靠 {@code detail.stage} 与事件的 stage 段区分，
 * <b>事件类型不新增词面</b>（仍用 {@code GUARDRAIL_EVAL}；义务词面的单源扩面属 D-B4-31，须先裁属主）。
 * 该腿与裁决腿的留痕失败语义<b>不同</b>，见 {@link #auditExecutionLegDenial} 的裁决登记。</p>
 */
@Component
public class GuardrailAuditPublisher {

    private static final Logger log = LoggerFactory.getLogger(GuardrailAuditPublisher.class);

    private static final String MODULE = "ai-engine";

    /**
     * 留痕失败的可告警标记（只入日志，禁随响应体上浮，X-49）。
     *
     * <p>存在的理由：{@link #auditExecutionLegDenial} 按裁决不外抛（拒绝已成立，翻转会把
     * DENIED 洗成 FAIL_CLOSED），"无痕"因此不会体现在任何返回值上 —— 没有这枚可 grep 的串，
     * 执行腿的拒绝态台账缺行将完全不可观测。</p>
     */
    public static final String TRACE_FAILURE_MARKER = "AI_GUARDRAIL_LEG_TRACE_FAILED";

    private final ObjectProvider<EventBusService> eventBusProvider;
    private final GuardrailDecisionLedgerDao ledgerDao;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public GuardrailAuditPublisher(ObjectProvider<EventBusService> eventBusProvider,
                                   GuardrailDecisionLedgerDao ledgerDao) {
        this.eventBusProvider = eventBusProvider;
        this.ledgerDao = ledgerDao;
    }

    /**
     * 咽喉专用：审计发出失败即失败（fail-closed）。
     *
     * @throws GuardrailAuditUnavailableException 事件通道不可用/发布异常
     */
    public void auditOrThrow(ToolAdjudicationRequest request, ToolDecision decision) {
        String toolKey = request == null || request.tool() == null ? "unknown" : request.tool();
        Map<String, Object> event = buildEvent(request, decision, toolKey);
        publishOrThrow(event, toolKey);
        writeLedger(request, decision, toolKey);
    }

    /** 非红线面的留痕（输出护栏替换等）：失败记 WARN，由调用方决定是否降级。 */
    public void auditQuietly(Map<String, Object> event) {
        try {
            EventBusService bus = eventBusProvider.getIfAvailable();
            if (bus == null) {
                log.warn("护栏审计通道未装配（EventBusService 缺失），事件仅记日志: eventType={}",
                        event.get("eventType"));
                return;
            }
            bus.publish(KafkaTopics.AUDIT, event);
        } catch (Exception e) {
            log.warn("护栏审计事件发布失败: eventType={} err={}", event.get("eventType"), e.getMessage(), e);
        }
    }

    /**
     * 输入/输出护栏事件（详细设计-06 F06-04 要点 3）：发布失败即抛出。
     *
     * <p>护栏本身已经处于"拒绝"态时，审计再失败必须一并转 FAIL_CLOSED（§2.4-5 审计 fail-closed），
     * 不允许"拦了但没留痕"。</p>
     */
    public void filterAuditOrThrow(Map<String, Object> event, String subject) {
        publishOrThrow(event, subject);
    }

    /**
     * 输入/输出护栏留痕事件（F06-04 要点 3：命中/整段替换必须同时发审计事件）。
     *
     * @param outcome {@code BLOCKED}（拒绝）/ {@code FILTERED}（脱敏替换）/ {@code FAIL_CLOSED}（不可用）
     * @param digest  内容摘要（SHA-256 前 16 位）；<b>禁</b>传原文（F06-03 要点 2）
     */
    public Map<String, Object> buildFilterEvent(String userId, String tenantId, String sessionId,
                                                String stage, String outcome, String ruleId, String digest) {
        Map<String, Object> event = baseEvent(userId, tenantId, sessionId);
        event.put("eventType", EventTypes.Guardrail.GUARDRAIL_FILTER);
        event.put("action", "guardrail:filter:" + stage);
        event.put("resource", "stage=" + stage + " ruleId=" + (ruleId == null ? "-" : ruleId));
        event.put("result", outcome == null ? "FILTERED" : outcome);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("stage", stage);
        detail.put("ruleId", ruleId);
        detail.put("contentDigest", digest);
        event.put("detail", detail);
        return event;
    }

    // ── 执行腿拒绝态留痕（#142/B4-3e-1，差距 D-B4-33） ──────────────────────

    /**
     * 护栏<b>执行腿</b>（裁决闸之后的限流腿等）拒绝态的留痕入参。
     *
     * <p>{@code decisionWord} 必须是 V204 {@code ck_agdl_decision} 允许的词
     * （由 {@code AiToolRateLimitGate.Verdict#ledgerDecision()} 单点折算，禁在此另写一套映射）；
     * {@code reasonCode} 只允许拒绝态<b>码段</b>，明细原文（可能含限流键／主体标识）禁入本对象。</p>
     */
    public record ExecutionLegDenial(String toolKey,
                                     ToolAdjudicationRequest.Subject subject,
                                     String stage,
                                     String decisionWord,
                                     String reasonCode,
                                     String policyId,
                                     int latencyMs,
                                     List<Map<String, Object>> obligations) {
    }

    /**
     * 执行腿拒绝态的双写出口：一条 {@code GUARDRAIL_EVAL} 事件 + 一行 V204 台账
     * （差距 D-B4-33 的收口点：闸侧只记 ALLOW，"义务已放行但执行被拒"这一态在台账上不可见）。
     *
     * <p><b>与 {@link #auditOrThrow} 的关键差别 —— 本方法不抛</b>（裁决登记，禁按"审计红线"照抄）：
     * {@code auditOrThrow} 在裁决<b>即将放行</b>时留痕，留痕失败可以把整条裁决翻成 FAIL_CLOSED
     * （宁可错杀不可漏放）；本方法在调用方<b>已经</b>拒绝之后才执行，同一件事翻不动 ——
     * DENIED 若因留痕失败被改成 FAIL_CLOSED，等于 §4.7.2「不可用 ≠ 无权限」两态在审计失败时折叠，
     * 而对外分类是响应面唯一的判据。因此这里失败只记 ERROR（含 {@link #TRACE_FAILURE_MARKER}
     * 便于告警与"无痕行数"对账），调用方的拒绝结论与状态码保持不变。
     * 事件失败时台账同批不写：宁缺勿留半条，与闸侧"同批双写"的对账口径一致。</p>
     */
    public void auditExecutionLegDenial(ExecutionLegDenial denial) {
        if (denial == null || denial.decisionWord() == null) {
            // 无拒绝态可留痕（PASS 腿的 decisionWord 按契约为 null）⇒ 静默返回，
            // 禁在此补写 ALLOW 行把闸侧裁决行翻倍（I-1 对账口径＝每次裁决一条）
            return;
        }
        Map<String, Object> event = buildExecutionLegEvent(denial);
        try {
            publishOrThrow(event, denial.toolKey());
        } catch (GuardrailAuditUnavailableException e) {
            log.error("{} 执行腿拒绝态审计事件发出失败（拒绝结论已成立、禁翻转分类故不外抛；台账同批不写 ⇒ 本条无痕需人工对账）: "
                            + "tool={} stage={} decision={} err={}",
                    TRACE_FAILURE_MARKER, denial.toolKey(), denial.stage(), denial.decisionWord(),
                    e.getMessage(), e);
            return;
        }
        writeExecutionLegLedger(denial);
    }

    private Map<String, Object> buildExecutionLegEvent(ExecutionLegDenial denial) {
        String userId = denial.subject() == null ? null : denial.subject().userId();
        String tenantId = denial.subject() == null ? null : denial.subject().tenantId();
        Map<String, Object> event = baseEvent(userId, tenantId, null);
        // stage 进 action 段（不是只进 detail）：同一 traceId 下闸侧与本腿会有两条 GUARDRAIL_EVAL，
        // 只有事件类型词面的消费者按 tool= 聚合时若无法从 action 分辨，就会把一次调用读成两次裁决。
        event.put("eventType", EventTypes.Guardrail.GUARDRAIL_EVAL);
        event.put("action", "guardrail:eval:" + legStage(denial) + ":" + denial.toolKey());
        event.put("resource", "tool=" + denial.toolKey());
        event.put("result", denial.decisionWord());
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("tool", denial.toolKey() == null ? "-" : denial.toolKey());
        detail.put("stage", legStage(denial));
        detail.put("decision", denial.decisionWord());
        detail.put("reasonCode", denial.reasonCode() == null ? "-" : denial.reasonCode());
        detail.put("policyId", denial.policyId() == null ? "-" : denial.policyId());
        detail.put("latencyMs", denial.latencyMs());
        detail.put("obligations", denial.obligations() == null ? List.of() : denial.obligations());
        event.put("detail", detail);
        return event;
    }

    private void writeExecutionLegLedger(ExecutionLegDenial denial) {
        try {
            ledgerDao.insert(UUID.randomUUID().toString(), AiTraceSupport.currentTraceId(), null,
                    denial.toolKey(), denial.decisionWord(), denial.policyId(),
                    toJson(legObligationsCache(denial)),
                    Math.max(0, denial.latencyMs()),
                    subjectSnapshotOf(denial.subject()),
                    denial.subject() == null || denial.subject().userId() == null
                            ? "system" : denial.subject().userId());
        } catch (Exception e) {
            log.error("{} 执行腿拒绝态台账（V204）写入失败，事件留痕已成立但索引面缺一行，"
                            + "会破坏 §1.5-1「义务已放行但执行被拒」的对账: tool={} stage={} decision={} err={}",
                    TRACE_FAILURE_MARKER, denial.toolKey(), denial.stage(), denial.decisionWord(),
                    e.getMessage(), e);
        }
    }

    /**
     * 台账 {@code obligations} 列的缓存形态 —— V204 没有 stage/reason 列，也不为一条腿改表
     * （IR03 只加不删 + 改表属 DDL 批），故本腿的两个关键字段落进<b>已被 DDL 声明为非判定输入</b>
     * 的缓存列（R-18 ②：该列"不作为任何运行期判定输入"，加字段不改变任何判定）。
     */
    private Map<String, Object> legObligationsCache(ExecutionLegDenial denial) {
        Map<String, Object> cache = new LinkedHashMap<>();
        cache.put("stage", legStage(denial));
        cache.put("reasonCode", denial.reasonCode());
        cache.put("obligations", denial.obligations() == null ? List.of() : denial.obligations());
        return cache;
    }

    /** stage 缺失时记 {@code "unknown"} 而非 null：台账缓存列要能读出"哪条腿漏了 stage"。 */
    private static String legStage(ExecutionLegDenial denial) {
        return denial.stage() == null || denial.stage().isBlank() ? "unknown" : denial.stage();
    }

    private Map<String, Object> buildEvent(ToolAdjudicationRequest request, ToolDecision decision,
                                           String toolKey) {
        String userId = request != null && request.subject() != null ? request.subject().userId() : null;
        String tenantId = request != null && request.subject() != null ? request.subject().tenantId() : null;
        Map<String, Object> event = baseEvent(userId, tenantId, null);
        if (request != null && request.environment() != null
                && request.environment().traceId() != null) {
            event.put("traceId", request.environment().traceId());
        }
        event.put("eventType", EventTypes.Guardrail.GUARDRAIL_EVAL);
        event.put("action", "guardrail:eval:" + toolKey);
        event.put("resource", "tool=" + toolKey);
        event.put("result", decision.verdict().name());
        event.put("detail", decision.toAuditDetail(toolKey));
        return event;
    }

    private Map<String, Object> baseEvent(String userId, String tenantId, String sessionId) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("eventId", UUID.randomUUID().toString());
        event.put("timestamp", Instant.now().toString());
        event.put("module", MODULE);
        event.put("userId", userId == null ? "anonymous" : userId);
        event.put("tenantId", tenantId == null ? "default" : tenantId);
        event.put("traceId", AiTraceSupport.currentTraceId());
        if (sessionId != null) event.put("sessionId", sessionId);
        return event;
    }

    private void publishOrThrow(Map<String, Object> event, String toolKey) {
        EventBusService bus = eventBusProvider.getIfAvailable();
        if (bus == null) {
            throw new GuardrailAuditUnavailableException(toolKey,
                    "护栏审计通道未装配（EventBusService 缺失），裁决按 FAIL_CLOSED 处理: tool=" + toolKey,
                    new IllegalStateException("no EventBusService bean"));
        }
        try {
            bus.publish(KafkaTopics.AUDIT, event);
        } catch (Exception e) {
            throw new GuardrailAuditUnavailableException(toolKey,
                    "护栏审计事件发布失败，裁决整体失败（fail-closed）: tool=" + toolKey, e);
        }
    }

    private void writeLedger(ToolAdjudicationRequest request, ToolDecision decision, String toolKey) {
        try {
            String traceId = request != null && request.environment() != null
                    && request.environment().traceId() != null
                    ? request.environment().traceId() : AiTraceSupport.currentTraceId();
            String userId = request != null && request.subject() != null ? request.subject().userId() : null;
            ledgerDao.insert(UUID.randomUUID().toString(), traceId, null, toolKey,
                    decision.verdict().name(), decision.policyId(),
                    toJson(decision.obligationSummary()), (int) Math.min(Integer.MAX_VALUE, decision.latencyMs()),
                    subjectSnapshot(request), userId == null ? "system" : userId);
        } catch (Exception e) {
            log.error("护栏裁决台账（V204）写入失败，事件留痕已成立、不翻转裁决，但会破坏 §1.5-1 对账: tool={} err={}",
                    toolKey, e.getMessage(), e);
        }
    }

    /** 主体快照：仅 userId/roles/department 三要素，禁入参原文与任何凭据（X-23 教训）。 */
    private String subjectSnapshot(ToolAdjudicationRequest request) {
        return subjectSnapshotOf(request == null ? null : request.subject());
    }

    /**
     * 快照形态的<b>唯一构造点</b>：闸侧（手上是 request）与执行腿侧（手上只有 Subject）
     * 必须产出同一份三要素快照 —— 两处各写一遍字段名就是两套快照口径，
     * 而 {@code subject_snapshot} 正是两行之间的连接键。
     */
    private String subjectSnapshotOf(ToolAdjudicationRequest.Subject subject) {
        if (subject == null) return "{}";
        Map<String, Object> snap = new LinkedHashMap<>();
        snap.put("userId", subject.userId());
        snap.put("roles", subject.roles());
        snap.put("department", subject.department());
        return toJson(snap);
    }

    private String toJson(Object value) {
        if (value == null) return null;
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            log.warn("护栏审计快照序列化失败，改记类型名: {}", e.getMessage());
            return "{\"serializationError\":true}";
        }
    }
}
