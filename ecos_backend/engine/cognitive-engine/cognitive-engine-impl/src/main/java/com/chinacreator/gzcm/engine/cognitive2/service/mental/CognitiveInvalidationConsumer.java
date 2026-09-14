package com.chinacreator.gzcm.engine.cognitive2.service.mental;

import com.chinacreator.gzcm.common.event.KafkaTopics;
import com.chinacreator.gzcm.common.exception.BusinessException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 认知失效事件订阅方（PMO-59 P3b T2 / ADR-9）— topic {@code ecos.cognitive}
 * 消费方组 {@code dccheng-cognitive-group}（gateway spring.kafka.consumer.group-id 既有占位正式启用）。
 *
 * <p><b>职责</b>：假设失效事件（{@code COGNITIVE_HYPOTHESIS_INVALIDATED}）→ 联动作废该假设
 * 关联的未决推演结论：{@code ecos_scenario_run} 中 {@code simulation_result->'assumptionRefs'}
 * 含该假设且 status=SUCCEEDED/SUCCEEDED_DEGRADED 的 run → {@code status='SUPERSEDED'} +
 * {@code ecos_cognitive_run_invalidation}（V130）留痕（eventId+runId 唯一幂等去重）。
 * 消费异常吞掉 WARN 不堵分区（ref: kb-engine EcosOntologyEventConsumer 同仓消费先例）。</p>
 *
 * <p><b>Kafka 路径说明</b>（指令 §实现决策 4）：Kafka 模式下 {@code EventBusService.subscribe}
 * 仅内存注册（P2b 实证），真实消费必须 {@code @KafkaListener}。本类为 cognitive 模块首个消费者
 * （groupId=dccheng-cognitive-group，与 P2 验收记录 §5.2 预留位一致）；factory 由 Spring Kafka
 * AutoConfiguration 默认产物提供（gateway spring.kafka 既配），spring-kafka 依赖 P2b 已落 POM。</p>
 *
 * <p><b>幂等</b>：Kafka 至少一次投递 → 同一 (eventId, runId) 由 uniq_ecos_cog_run_inv_evt_run
 * 兜底不重复留痕；run 状态 UPDATE 命中 0 行（已被其他投递路径改过）时跳过。</p>
 */
@Component
public class CognitiveInvalidationConsumer {

    private static final Logger log = LoggerFactory.getLogger(CognitiveInvalidationConsumer.class);

    /** 消费事件类型白名单（topic 多事件类型共用，本订阅方只吃失效类） */
    private static final String EVENT_TYPE_INVALIDATED = "COGNITIVE_HYPOTHESIS_INVALIDATED";
    /** 可作废的 run 状态白名单（RUNNING/FAILED 不适用"关联作废"语义） */
    private static final List<String> SUPERSEDEABLE_STATUSES = List.of("SUCCEEDED", "SUCCEEDED_DEGRADED");

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final MentalEventPublisher eventPublisher;

    /** 审计通道（铁律 §2.4#5）— 可选装配（required=false + null 兜底） */
    @Autowired(required = false)
    private com.chinacreator.gzcm.runtime.eventbus.EventBusService eventBusService;

    public CognitiveInvalidationConsumer(JdbcTemplate jdbc,
                                         ObjectMapper objectMapper,
                                         MentalEventPublisher eventPublisher) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Kafka Listener 入口 — topic=KafkaTopics.COGNITIVE, groupId=dccheng-cognitive-group。
     * String 入参 + ObjectMapper 解析；eventType 非失效类静默跳过；
     * 异常 catch WARN 不抛（单条坏消息不堵分区；连续失败由 DLQ 接管）。
     */
    @KafkaListener(topics = KafkaTopics.COGNITIVE, groupId = "dccheng-cognitive-group")
    public void onCognitiveEvent(String json) {
        if (json == null || json.isEmpty()) {
            log.warn("CognitiveInvalidationConsumer: kafka payload null/empty, skip");
            return;
        }
        try {
            Map<String, Object> payload = objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {
            });
            if (!EVENT_TYPE_INVALIDATED.equals(String.valueOf(payload.get("eventType")))) {
                return;
            }
            handleInvalidation(payload);
        } catch (BusinessException e) {
            log.warn("CognitiveInvalidationConsumer business rejected (not rethrown): {}", e.getMessage());
        } catch (Exception e) {
            log.warn("CognitiveInvalidationConsumer kafka consume failed (ignored, not blocking partition): err={}",
                e.getMessage(), e);
        }
    }

    /**
     * 假设失效 → 关联作废（单一实现：Kafka 消费链路与自发自收同一方法，事件驱动单点）。
     *
     * @param payload 失效事件体（eventType/eventId/hypothesisId 必填；domain/invalidReason/autoDetected 留痕）
     */
    @Transactional
    public void handleInvalidation(Map<String, Object> payload) {
        String eventId = str(payload.get("eventId"));
        String hypothesisId = str(payload.get("hypothesisId"));
        if (eventId == null || hypothesisId == null) {
            throw new BusinessException(400, "COG-400: 失效事件缺 eventId/hypothesisId 留痕字段");
        }
        String hypothesisCode = str(payload.get("hypothesisCode"));
        String invalidReason = str(payload.get("invalidReason"));
        boolean autoDetected = Boolean.parseBoolean(str(payload.get("autoDetected")));

        // 1. 查引用该假设的未决推演 run（JSONB 包含匹配 assumptionRefs 数组）
        String refsArrayJson = toJsonArray(List.of(hypothesisId));
        List<Map<String, Object>> candidates = jdbc.queryForList(
            "SELECT id, status FROM ecos_scenario_run " +
            "WHERE is_deleted = 0 AND status IN (?, ?) " +
            "AND simulation_result IS NOT NULL " +
            "AND (simulation_result->'assumptionRefs') @> ?::jsonb " +
            "ORDER BY create_time DESC",
            SUPERSEDEABLE_STATUSES.get(0), SUPERSEDEABLE_STATUSES.get(1), refsArrayJson);
        if (candidates.isEmpty()) {
            log.info("CognitiveInvalidationConsumer 无关联 run 可作废: hypothesis={} event={}", hypothesisId, eventId);
            return;
        }

        // 2. 逐 run 打标 SUPERSEDED + 3. impact 表留痕（uniq(event_id,run_id) 幂等兜底）
        int superseded = 0;
        LocalDateTime now = LocalDateTime.now();
        for (Map<String, Object> run : candidates) {
            String runId = String.valueOf(run.get("id"));
            int updated = jdbc.update(
                "UPDATE ecos_scenario_run SET status = 'SUPERSEDED', update_time = ? " +
                "WHERE id = ? AND is_deleted = 0 AND status IN (?, ?)",
                now, runId, SUPERSEDEABLE_STATUSES.get(0), SUPERSEDEABLE_STATUSES.get(1));
            if (updated == 0) {
                log.warn("run 状态更新未命中（并发或已作废，幂等跳过）: runId={}", runId);
                continue;
            }
            String impactId = "cog_inv_" + UUID.randomUUID().toString().substring(0, 12);
            Integer inserted = jdbc.update(
                "INSERT INTO ecos_cognitive_run_invalidation " +
                "(id, event_id, hypothesis_id, run_id, auto_detected, superseded_at, detail, create_by, update_by, is_deleted) " +
                "SELECT ?, ?, ?, ?, ?, ?, ?, 'system', 'system', 0 " +
                "WHERE NOT EXISTS (SELECT 1 FROM ecos_cognitive_run_invalidation " +
                "WHERE event_id = ? AND run_id = ? AND is_deleted = 0)",
                impactId, eventId, hypothesisId, runId, autoDetected, now,
                buildDetail(hypothesisCode, invalidReason), eventId, runId);
            if (inserted != null && inserted > 0) {
                superseded++;
            }
        }
        log.info("CognitiveInvalidationConsumer 作废联动完成: hypothesis={} event={} supersededRuns={}",
            hypothesisId, eventId, superseded);

        // 4. 复盘事件（ecos.cognitive COGNITIVE_RUN_SUPERSEDED，faultContext 含 reviewTag 周一故障复盘预留）
        if (superseded > 0) {
            try {
                eventPublisher.publishRunSuperseded(eventId, hypothesisId, hypothesisCode, superseded, autoDetected);
            } catch (Exception e) {
                log.warn("COGNITIVE_RUN_SUPERSEDED 发布失败 (ignored, 不阻塞): err={}", e.getMessage());
            }
        }
        // 5. 审计（铁律 §2.4#5：关联作废=写操作必发 ecos.audit）
        audit("hypothesis.invalidation-run-superseded", "ecos_scenario_run", "success",
            "eventId=" + eventId + ", hypothesisId=" + hypothesisId + ", supersededRuns=" + superseded);
    }

    /** 写操作审计（Kafka ecos.audit）；未装配/异常降级 WARN 不阻塞。 */
    private void audit(String action, String resource, String result, String detail) {
        if (eventBusService == null) {
            log.warn("失效作废审计无法发送: EventBusService 未装配, action={}", action);
            return;
        }
        try {
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("action", action);
            event.put("resource", resource);
            event.put("result", result);
            event.put("detail", detail);
            event.put("userId", "system");
            event.put("timestamp", LocalDateTime.now().toString());
            eventBusService.publish(KafkaTopics.AUDIT, event);
        } catch (Exception e) {
            log.warn("失效作废审计 (EventBus) failed (ignored): {}", e.getMessage());
        }
    }

    /** 作废旧化摘要（留痕 detail 列）。 */
    private String buildDetail(String hypothesisCode, String invalidReason) {
        StringBuilder sb = new StringBuilder("hypothesisCode=").append(hypothesisCode == null ? "-" : hypothesisCode);
        if (invalidReason != null) {
            String r = invalidReason.length() > 200 ? invalidReason.substring(0, 200) : invalidReason;
            sb.append(", invalidReason=").append(r);
        }
        return sb.toString();
    }

    /** id 列表 → JSON 数组文本（JSONB @> 包含匹配参数）。 */
    private String toJsonArray(List<String> ids) {
        try {
            return objectMapper.writeValueAsString(ids);
        } catch (Exception e) {
            throw new BusinessException(400, "COG-400: assumptionRefs JSON 序列化失败: " + e.getMessage());
        }
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o).trim();
    }
}
