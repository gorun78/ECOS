package com.chinacreator.gzcm.runtime.core.cache.outbox;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 缓存失效 Outbox 数据访问（§2.9.8④；#147AF/D）。
 *
 * <p>与业务写同库落行：业务提交后行即存在（不会因进程崩溃丢失）；中继成功置 {@code PUBLISHED}，
 * 超次置 {@code FAILED} 供运维重放。SQL 全参数化，禁字面量拼接。</p>
 *
 * @author ECOS Runtime #147AF
 */
@Repository
public class CacheInvalidationOutboxDao {

    private static final String TABLE = "ecos_control.cache_invalidation_outbox";

    private final JdbcTemplate jdbcTemplate;

    public CacheInvalidationOutboxDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 入队一条失效意图；返回行 id。 */
    public String enqueue(String eventType, String payloadJson) {
        String id = UUID.randomUUID().toString();
        jdbcTemplate.update("INSERT INTO " + TABLE
                        + " (id, event_type, payload_json, status, attempts) VALUES (?, ?, ?, 'PENDING', 0)",
                id, eventType, payloadJson);
        return id;
    }

    /** 取一批待投递行（先入先出）。 */
    public List<Map<String, Object>> pollPending(int limit) {
        return jdbcTemplate.queryForList("SELECT id, event_type, payload_json, attempts FROM " + TABLE
                + " WHERE is_deleted = 0 AND status = 'PENDING' ORDER BY create_time ASC LIMIT ?", limit);
    }

    public int markPublished(String id) {
        return jdbcTemplate.update("UPDATE " + TABLE
                + " SET status = 'PUBLISHED', published_at = CURRENT_TIMESTAMP, attempts = attempts + 1 WHERE id = ?", id);
    }

    public int markFailed(String id, String error, int maxAttempts) {
        return jdbcTemplate.update("UPDATE " + TABLE
                + " SET attempts = attempts + 1, last_error = ?, status = CASE WHEN attempts + 1 >= ? THEN 'FAILED' ELSE 'PENDING' END"
                + " WHERE id = ?", error, maxAttempts, id);
    }

    public int countPending() {
        Integer n = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + TABLE
                + " WHERE is_deleted = 0 AND status = 'PENDING'", Integer.class);
        return n == null ? 0 : n;
    }
}
