package com.chinacreator.gzcm.runtime.core.cache.outbox;

import java.sql.Timestamp;
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

    /**
     * 删除保留期之外的已发布行；返回删除条数。
     *
     * <p><b>只清 {@code PUBLISHED}</b>：PENDING 是待投递，FAILED 是「广播没成功就被忘掉」的
     * 假绿源头，两者必须留在表里供运维重放与观测。Outbox 行本身不携带业务内容（V310 列注释已钉），
     * 因此这里是物理清退而非逻辑删除——逻辑删除只会让这张纯基础设施表无界增长。</p>
     *
     * <p>截止时刻由调用侧算好传入 {@code timestamp} 参数：PG 的 {@code INTERVAL} 字面量乘参数
     * 会让占位符类型不可推断（同 {@code CONCAT} 那族缺陷），禁在此写 {@code ? * INTERVAL ...}。</p>
     *
     * @param cutoff 早于此时刻的 PUBLISHED 行才可删
     * @param limit  单轮上限，避免长事务锁住整表
     */
    public int purgePublished(Timestamp cutoff, int limit) {
        return jdbcTemplate.update("DELETE FROM " + TABLE + " WHERE id IN (SELECT id FROM " + TABLE
                + " WHERE status = 'PUBLISHED' AND published_at < ? ORDER BY published_at ASC LIMIT ?)",
                cutoff, limit);
    }
}
