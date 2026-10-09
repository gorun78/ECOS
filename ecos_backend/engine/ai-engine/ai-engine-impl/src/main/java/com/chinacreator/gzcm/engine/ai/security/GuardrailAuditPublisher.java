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
 */
@Component
public class GuardrailAuditPublisher {

    private static final Logger log = LoggerFactory.getLogger(GuardrailAuditPublisher.class);

    private static final String MODULE = "ai-engine";

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
        if (request == null || request.subject() == null) return "{}";
        Map<String, Object> snap = new LinkedHashMap<>();
        snap.put("userId", request.subject().userId());
        snap.put("roles", request.subject().roles());
        snap.put("department", request.subject().department());
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
