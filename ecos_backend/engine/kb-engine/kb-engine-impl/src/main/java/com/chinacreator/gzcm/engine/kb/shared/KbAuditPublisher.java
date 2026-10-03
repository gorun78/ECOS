package com.chinacreator.gzcm.engine.kb.shared;

import com.chinacreator.gzcm.common.event.KafkaTopics;
import com.chinacreator.gzcm.engine.kb.shared.KbErrorCode.KbErrorCodeException;
import com.chinacreator.gzcm.runtime.eventbus.EventBusService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * KB 域审计发布 — F04-15 收口 runtime-event（K-44/K-45 纠正）。
 *
 * <p>降级策略（F04-15 陈述）：
 * <ol>
 *   <li>{@link EventBusService} 可用 → 首选经它 publish 到 {@link KafkaTopics#AUDIT}（Kafka 或内存 fallback）；</li>
 *   <li>EventBus 不可用/publish 失败 → 本地 JDBC 兜底必写 {@code ecos_knowledge.kb_extract_audit}（V142 本域台账表）；</li>
 *   <li>兜底亦失败 → 抛 {@link KbErrorCodeException}（HTTP 503 / KB_022）：
 *       "默认 DENY、纠正 warn-后-继续 = 静默丢审计"（K-44/K-45）。</li>
 * </ol>
 *
 * <p>本类<b>不</b>使用 {@code CompletableFuture.runAsync}（K-38 反例）、也<b>不</b>
 * 反射 KafkaTemplate.send（K-45 反例）——同步、可断言、可拒绝。
 */
@Component
public class KbAuditPublisher {

    private static final Logger log = LoggerFactory.getLogger(KbAuditPublisher.class);

    private final JdbcTemplate jdbc;
    private final EventBusService eventBus;

    public KbAuditPublisher(JdbcTemplate jdbc, ObjectProvider<EventBusService> eventBusProvider) {
        this.jdbc = jdbc;
        this.eventBus = eventBusProvider == null ? null : eventBusProvider.getIfAvailable();
    }

    /**
     * 同步发布一次审计事件；首选 EventBus，兜底 JDBC，双通路都失败即抛（fail-closed）。
     *
     * @param action     业务动作（如 {@code extraction.approve}）
     * @param entityType 客体类别（如 {@code extraction}）
     * @param entityId   客体 id
     * @param extra      附加属性（可空；扁平合并进 event）
     */
    public void emit(String action, String entityType, String entityId, Map<String, Object> extra) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("eventId", UUID.randomUUID().toString());
        event.put("kt", "knowledge");
        event.put("timestamp", System.currentTimeMillis());
        event.put("entityType", entityType);
        event.put("entityId", entityId);
        event.put("action", action);
        event.put("actor", currentActor());
        if (extra != null) {
            event.putAll(extra);
        }

        // 1) 首选 EventBus（Kafka / 内存 fallback）
        if (eventBus != null) {
            try {
                eventBus.publish(KafkaTopics.AUDIT, event);
                log.debug("KbAuditPublisher: EventBus publish OK action={} entityId={}", action, entityId);
                return;
            } catch (Exception e) {
                log.warn("KbAuditPublisher: EventBus publish 失败，转 JDBC 兜底 (action={}, entityId={}): {}",
                        action, entityId, e.getMessage());
            }
        } else {
            log.warn("KbAuditPublisher: EventBusService 不可用，转 JDBC 兜底 (action={}, entityId={})",
                    action, entityId);
        }

        // 2) 本地 JDBC 兜底必写
        try {
            jdbc.update(
                    "INSERT INTO ecos_knowledge.kb_extract_audit (job_id, tier, mode, status, error_message) "
                            + "VALUES (?, 'kb', ?, 'AUDIT', ?)",
                    String.valueOf(entityId), String.valueOf(action),
                    eventBus == null ? "bus_unavailable" : "bus_publish_failed");
            return;
        } catch (Exception jdbcErr) {
            // 3) 双通路全失败 → 拒绝操作（默认 DENY）
            throw KbErrorCode.ex(KbErrorCode.KB_022,
                    "审计底座全部不可用（EventBus + JDBC 双通路均失败），操作拒绝（默认 DENY）：action="
                            + action + " err=" + jdbcErr.getMessage());
        }
    }

    private static String currentActor() {
        try {
            org.springframework.security.core.Authentication auth =
                    org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated()) {
                Object principal = auth.getPrincipal();
                if (principal instanceof String s) {
                    return s;
                }
                return auth.getName();
            }
        } catch (Exception ignored) {
            // actor 解析失败降级 anonymous，不阻断
        }
        return "anonymous";
    }
}
