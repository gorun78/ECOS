package com.chinacreator.gzcm.runtime.eventbus.dlq.dao;

import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Map;

/**
 * 详细设计-00 C.5.2 (W13) — DLQ 单表持久化收口（R2 B4c-runtime · 架构铁律 §2.5#1 / IR06）。
 *
 * <p>控制域 {@code ecos_control.ecos_runtime_event_dlq}（DDL=V243）全部 raw-SQL
 * 自 {@code com.chinacreator.gzcm.runtime.eventbus.dlq.DltConsumer} 逐字节下沉，
 * SQL 文本与参数顺序不变；{@code DltConsumer} 退化为消费/告警门面，SQL 只经本 DAO
 * （{@link JdbcTemplateDaoSupport} 的 {@code jdbcTemplate} 通道）执行。</p>
 *
 * <p>仅在有 {@link JdbcTemplate} 的容器（gateway / 各 service）内生效；
 * 无数据源栈则本 Bean 不装配，DLQ 消费侧随之降级。</p>
 *
 * <p>红线 EN01/IR01：全部 slot 参数化。</p>
 */
@Repository
public class EcosRuntimeEventDlqDao extends JdbcTemplateDaoSupport {

    private static final String T = "ecos_control.ecos_runtime_event_dlq";
    private static final String V = "1.0.0";

    /** DLQ 落库：payload + error + firstSeen，status=pending。返回应用侧 UUID。 */
    public String insertDlt(String id, String baseTopic, String dltTopic, String payload, String errorMessage) {
        jdbcTemplate.update(
            "INSERT INTO " + T +
            " (id, topic, dlt_topic, payload, error_message, attempts, first_seen_at, status, version_no, is_deleted, domain)" +
            " VALUES (?, ?, ?, ?, ?, 0, NOW(), 'pending', ?, 0, 'default')",
            id, baseTopic, dltTopic, payload, errorMessage, V);
        return id;
    }

    public long count() {
        Long c = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + T + " WHERE is_deleted = 0", Long.class);
        return c == null ? 0 : c;
    }

    /** 供 monitor/DLQ 重放接口取样；取最早一条 pending。 */
    public Map<String, Object> findPending(int limit) {
        // 供 monitor/DLQ 重放接口取样；取最早一条 pending
        try {
            return jdbcTemplate.queryForMap(
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
        return jdbcTemplate.update("UPDATE " + T +
                " SET status = 'replayed', replayed_at = NOW(), update_time = NOW() WHERE id = ?", id) > 0;
    }
}
