package com.chinacreator.gzcm.runtime.eventbus.dlq;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 详细设计-00 C.5.2 (W13) — DLQ 单一消费者 / 兜底。
 *
 * <p>关闭 W13：此前 {@code <topic>.DLT} 仅在 {@code EcosOntologyEventConsumer} 注释出现、无消费者、无 lag 告警。
 * 本组件是 DLQ 的<b>统一出口</b>（默认 {@code DefaultErrorHandler} 连续失败投 DLT 后由本消费者收口）：
 * <ol>
 *   <li>落控制域 {@code public.ecos_runtime_event_dlq}（payload + error + firstSeen，C.5.2 DDL=V243）；</li>
 *   <li>lag > {@link #LAG_THRESHOLD}（默认 100）持续 > {@link #LAG_WINDOW}（默认 5min）→ 经
 *       {@link DltAlertSink} 升级告警（不在此内联告警实现，告警分支归 runtime-monitor / 01 册门面）。</li>
 * </ol>
 *
 * <p>红线：只 `<topic>.DLT`；不吞业务 payload 中的敏感字段（payload 已加密/脱敏由 producer 保证）；
 * 重放走 {@code POST /api/v1/monitor/dlq/{id}/replay}（gateway C 章），本组件不直接重放。</p>
 */
@Component
@ConditionalOnBean(JdbcTemplate.class)
public class DltConsumer {

    public static final int LAG_THRESHOLD = 100;
    public static final Duration LAG_WINDOW = Duration.ofMinutes(5);
    private static final String T = "public.ecos_runtime_event_dlq";
    private static final String V = "1.0.0";

    private static final Logger log = LoggerFactory.getLogger(DltConsumer.class);

    private final JdbcTemplate jdbc;
    private final DltAlertSink alertSink;
    // topic -> 首次进入滞后窗口时刻
    private final ConcurrentMap<String, Long> lagWindowOpenedAt = new ConcurrentHashMap<>();

    public DltConsumer(JdbcTemplate jdbc) {
        this(jdbc, (severity, message) ->
                log.warn("[runtime-dlq] ALERT sev={} msg={}", severity, message));
    }

    public DltConsumer(JdbcTemplate jdbc, DltAlertSink alertSink) {
        this.jdbc = jdbc;
        this.alertSink = alertSink == null ? (sev, m) -> log.warn("[runtime-dlq] ALERT [] {}", m) : alertSink;
    }

    /**
     * 消费一条 DLT 消息：持久化到 DLQ 表。
     *
     * @param topic   DLT topic（形如 {@code ecos.audit.DLT}）
     * @param payload 原始业务消息（producer 已序列化）
     * @param error   最后一次失败的异常（可为 null）
     * @return DLQ 记录 id（应用侧 UUID）
     */
    public String consume(String topic, String payload, Throwable error) {
        String id = java.util.UUID.randomUUID().toString();
        String base = baseTopic(topic);
        jdbc.update(
            "INSERT INTO " + T +
            " (id, topic, dlt_topic, payload, error_message, attempts, first_seen_at, status, version_no, is_deleted, domain)" +
            " VALUES (?, ?, ?, ?, ?, 0, NOW(), 'pending', ?, 0, 'default')",
            id, base, topic, payload, truncate(error == null ? null : String.valueOf(error), 1000), V);
        log.info("[runtime-dlq] persisted dlt={} payloadLen={} err={}",
                topic, payload == null ? 0 : payload.length(), error);
        return id;
    }

    /**
     * 暴露 lag 指标 + 触发滞后告警（供 consumer lags 指标喂入）。
     * 单 topic lag > {@link #LAG_THRESHOLD} 持续 > {@link #LAG_WINDOW} 才升级，避免抖动误报。
     */
    public void reportLag(String topic, long lag) {
        if (lag <= LAG_THRESHOLD) {
            lagWindowOpenedAt.remove(topic);
            return;
        }
        long now = System.currentTimeMillis();
        long opened = lagWindowOpenedAt.computeIfAbsent(topic, k -> now);
        if (now - opened >= LAG_WINDOW.toMillis()) {
            lagWindowOpenedAt.put(topic, now); // 重置窗口，避免每 tick 重复告警
            alertSink.alert("warning", "Kafka DLQ lag 超阈值 topic=" + topic + " lag=" + lag
                    + " > " + LAG_THRESHOLD + " 持续 > " + LAG_WINDOW.toMinutes() + "min");
        }
    }

    public long dlqCount() {
        Long c = jdbc.queryForObject("SELECT COUNT(*) FROM " + T + " WHERE is_deleted = 0", Long.class);
        return c == null ? 0 : c;
    }

    public Map<String, Object> findPending(int limit) {
        // 供 monitor/DLQ 重放接口取样；取最早一条 pending
        try {
            return jdbc.queryForMap(
                "SELECT id, topic, dlt_topic, payload, error_message AS errorMessage," +
                " attempts, first_seen_at AS firstSeenAt, status FROM " + T +
                " WHERE is_deleted = 0 AND status = 'pending' ORDER BY first_seen_at ASC LIMIT ?",
                Math.max(1, limit));
        } catch (Exception ex) {
            return null;
        }
    }
    /** 重放标记成功：status → replayed（由 gateway C 章 replay 接口调）。 */
    public boolean markReplayed(String id) {
        return jdbc.update("UPDATE " + T +
                " SET status = 'replayed', replayed_at = NOW(), update_time = NOW() WHERE id = ?", id) > 0;
    }

    static String baseTopic(String dltTopic) {
        if (dltTopic == null) return "";
        return dltTopic.endsWith(".DLT") ? dltTopic.substring(0, dltTopic.length() - 4) : dltTopic;
    }

    private static String truncate(String s, int len) {
        if (s == null) return null;
        return s.length() <= len ? s : s.substring(0, len);
    }

    /** 告警下沉接口 —— 默认 slf4j；deployment 侧可替换为 runtime-monitor 门面（01 册 security/monitor REST）。 */
    @FunctionalInterface
    public interface DltAlertSink {
        void alert(String severity, String message);
    }
}
