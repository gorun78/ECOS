package com.chinacreator.gzcm.engine.cognitive2.service.mental;

import com.chinacreator.gzcm.common.event.KafkaTopics;
import com.chinacreator.gzcm.runtime.eventbus.EventBusService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 认知心智层事件发布器（PMO-59 P2b / ADR-9）— topic {@code ecos.cognitive}（KafkaTopics.COGNITIVE）。
 *
 * <p>通道：统一走 {@link EventBusService}（runtime-event 公共底座，铁律 §2.5 禁止自建 KafkaTemplate）。
 * Kafka 未启用时自动降级内存总线（MemoryEventBusFallbackCondition 互斥），本地验证不受阻。</p>
 *
 * <p>可靠性约定（指令 T1 验收口径）：publish 全程 try/catch 吞异常打 WARN，<b>不阻塞主流程</b>
 * （业务落库先于事件发送，事件最终一致）。消费方约定 group.id={@code dccheng-cognitive-group}。</p>
 *
 * <p>{@code faultContext} 结构化字段（用户确认周一故障复盘对接口径）：
 * {@code {phase, hypothesisId/beliefId..., invalidReason, relatedEvidenceIds[], reviewTag}}。</p>
 */
@Component
public class MentalEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(MentalEventPublisher.class);

    private static final String SOURCE = "cognitive-engine";

    /** 故障复盘预留 tag（周一故障复盘流水线过滤键） */
    public static final String REVIEW_TAG = "P2b-mental-layer-review";

    @Autowired(required = false)
    private EventBusService eventBusService;

    /**
     * 假设失效事件。
     *
     * @param hypothesisId   假设主键
     * @param hypothesisCode 业务唯一键
     * @param domain         业务域
     * @param invalidReason  失效原因（触发证据/冲突说明）
     * @param evidenceIds    关联证据 id（触发 + 支撑）
     * @param autoDetected   是否自动检测命中（false=人工标记兜底）
     */
    public void publishHypothesisInvalidated(String hypothesisId, String hypothesisCode, String domain,
                                             String invalidReason, java.util.List<String> evidenceIds,
                                             boolean autoDetected) {
        Map<String, Object> payload = envelope("COGNITIVE_HYPOTHESIS_INVALIDATED");
        payload.put("hypothesisId", hypothesisId);
        payload.put("hypothesisCode", hypothesisCode);
        payload.put("domain", domain);
        payload.put("invalidReason", invalidReason);
        payload.put("evidenceIds", evidenceIds);
        payload.put("autoDetected", autoDetected);
        Map<String, Object> faultContext = new LinkedHashMap<>();
        faultContext.put("phase", "hypothesis-invalidation");
        faultContext.put("hypothesisId", hypothesisId);
        faultContext.put("relatedEvidenceIds", evidenceIds);
        faultContext.put("autoDetected", autoDetected);
        faultContext.put("reviewTag", REVIEW_TAG);
        payload.put("faultContext", faultContext);
        publish(payload);
    }

    /**
     * 不确定性判断版本变更事件（新证据加权更新 / 人工覆写）。
     *
     * @param variableName 变量名
     * @param domain       业务域
     * @param newVersion   更新后版本
     * @param triggeredByEvidenceId 触发证据 id（覆写纯专家判断可空）
     * @param manual       是否人工覆写
     */
    public void publishBeliefUpdated(String variableName, String domain, int newVersion,
                                     String triggeredByEvidenceId, boolean manual) {
        Map<String, Object> payload = envelope("COGNITIVE_BELIEF_UPDATED");
        payload.put("variableName", variableName);
        payload.put("domain", domain);
        payload.put("newVersion", newVersion);
        payload.put("triggeredByEvidenceId", triggeredByEvidenceId);
        payload.put("manualOverride", manual);
        Map<String, Object> faultContext = new LinkedHashMap<>();
        faultContext.put("phase", "belief-update");
        faultContext.put("variableName", variableName);
        faultContext.put("domain", domain);
        faultContext.put("version", newVersion);
        faultContext.put("manualOverride", manual);
        faultContext.put("reviewTag", REVIEW_TAG);
        payload.put("faultContext", faultContext);
        publish(payload);
    }

    /**
     * 新证据登记事件（冲突检测命中时携带 conflict 标记，补算扫描链路输入）。
     */
    public void publishEvidenceRegistered(String evidenceId, String evidenceCode, String fact,
                                          String metric, boolean conflict) {
        Map<String, Object> payload = envelope("COGNITIVE_EVIDENCE_REGISTERED");
        payload.put("evidenceId", evidenceId);
        payload.put("evidenceCode", evidenceCode);
        payload.put("fact", fact);
        payload.put("metric", metric);
        payload.put("conflict", conflict);
        Map<String, Object> faultContext = new LinkedHashMap<>();
        faultContext.put("phase", "evidence-register");
        faultContext.put("evidenceId", evidenceId);
        faultContext.put("conflict", conflict);
        faultContext.put("reviewTag", REVIEW_TAG);
        payload.put("faultContext", faultContext);
        publish(payload);
    }

    /**
     * 推演结论作废事件（PMO-59 P3b 新增）— 假设失效联动作废引用它的未决 run。
     *
     * @param triggeredByEventId 触发作废的失效事件 id（cog_evt_ 前缀，溯源链）
     * @param hypothesisId       失效假设主键
     * @param hypothesisCode     业务唯一键
     * @param supersededRuns     本次联动作废的 run 数
     * @param autoDetected       失效是否自动检测触发
     */
    public void publishRunSuperseded(String triggeredByEventId, String hypothesisId,
                                     String hypothesisCode, int supersededRuns, boolean autoDetected) {
        Map<String, Object> payload = envelope("COGNITIVE_RUN_SUPERSEDED");
        payload.put("triggeredByEventId", triggeredByEventId);
        payload.put("hypothesisId", hypothesisId);
        payload.put("hypothesisCode", hypothesisCode);
        payload.put("supersededRuns", supersededRuns);
        Map<String, Object> faultContext = new LinkedHashMap<>();
        faultContext.put("phase", "run-supersede");
        faultContext.put("triggeredByEventId", triggeredByEventId);
        faultContext.put("hypothesisId", hypothesisId);
        faultContext.put("supersededRuns", supersededRuns);
        faultContext.put("autoDetected", autoDetected);
        faultContext.put("reviewTag", REVIEW_TAG);
        payload.put("faultContext", faultContext);
        publish(payload);
    }

    private Map<String, Object> envelope(String eventType) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventType", eventType);
        payload.put("eventId", MentalEvent.newEventId());
        payload.put("timestamp", MentalEvent.nowIso());
        payload.put("source", SOURCE);
        return payload;
    }

    /** 统一发布入口 — EventBus 主通道，异常吞掉 WARN 不落（不阻塞主流程）。 */
    private void publish(Map<String, Object> payload) {
        if (eventBusService == null) {
            log.warn("心智事件无法发送: EventBusService 未装配, eventType={}", payload.get("eventType"));
            return;
        }
        try {
            eventBusService.publish(KafkaTopics.COGNITIVE, payload);
            log.info("心智事件已发布: eventType={} eventId={}", payload.get("eventType"), payload.get("eventId"));
        } catch (Exception e) {
            log.warn("心智事件发布失败 (ignored, 不阻塞主流程): eventType={} err={}",
                    payload.get("eventType"), e.getMessage());
        }
    }
}
