package com.chinacreator.gzcm.runtime.core.cache.outbox;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.chinacreator.gzcm.runtime.core.cache.CacheInvalidationNotifier;
import com.chinacreator.gzcm.runtime.core.cache.RedisStringCache;
import com.chinacreator.gzcm.runtime.core.cache.RuntimeCacheManager;

/**
 * 缓存失效 Outbox 中继（§2.9.8④；#147AF/D）。
 *
 * <p><b>链路</b>：业务写路径（提交后）→ {@link #enqueueDomainInvalidation} 落 Outbox 行 →
 * 本类轮询把 PENDING 行发布到 Redis 频道 {@value #CHANNEL} → 置 PUBLISHED；
 * 订阅端（{@code CacheInvalidationSubscriberConfig}）收到后按域清本节点缓存。
 * <b>至少一次</b>：发布失败置 PENDING 并在超次后置 FAILED 供重放（禁静默丢弃）。</p>
 *
 * <p><b>为什么 Redis 不可用时不置 PUBLISHED</b>：置了就是假成功——其他节点不会失效，
 * 陈旧窗口无界。此处按失败累加，超次转 FAILED 让人看见。</p>
 *
 * <p><b>第三段=清退</b>：{@link #purge()} 只删超保留期的 PUBLISHED 行；PENDING/FAILED 永不清退，
 * 否则「投递失败」这条线索会被自己的清理抹掉。</p>
 *
 * @author ECOS Runtime #147AF
 */
@Component
public class CacheInvalidationRelay implements CacheInvalidationNotifier {

    private static final Logger log = LoggerFactory.getLogger(CacheInvalidationRelay.class);

    /** 失效广播频道（单源；订阅端同值）。 */
    public static final String CHANNEL = "ecos:cache:invalidate";
    static final int BATCH = 50;
    static final int MAX_ATTEMPTS = 5;
    /** 单轮清退上限：一次删太多会把这张写得很勤的表锁住。 */
    static final int PURGE_BATCH = 500;

    private final CacheInvalidationOutboxDao outboxDao;
    private final ObjectProvider<RedisStringCache> redis;
    private final ObjectProvider<RuntimeCacheManager> cache;
    private final int retentionMinutes;

    public CacheInvalidationRelay(CacheInvalidationOutboxDao outboxDao,
                                  ObjectProvider<RedisStringCache> redis,
                                  ObjectProvider<RuntimeCacheManager> cache,
                                  @Value("${ecos.cache.outbox.retention-minutes:1440}") int retentionMinutes) {
        this.outboxDao = outboxDao;
        this.redis = redis;
        this.cache = cache;
        this.retentionMinutes = retentionMinutes;
    }

    /**
     * 入队一次「域失效」意图（由 {@code RuntimeCacheManager.evictObject / invalidateDomain}
     * 在权威数据提交后的失效动作里调用；#147AN 接通生产半）。
     *
     * @return Outbox 行 id
     */
    @Override
    public String enqueueDomainInvalidation(String env, String domain, String reason) {
        String safeEnv = env == null || env.isBlank() ? "default" : env;
        String safeDomain = domain == null ? "" : domain;
        String payload = "{\"env\":\"" + escape(safeEnv) + "\",\"domain\":\"" + escape(safeDomain)
                + "\",\"reason\":\"" + escape(reason == null ? "" : reason) + "\"}";
        return outboxDao.enqueue("CACHE_INVALIDATE", payload);
    }

    /** 定时中继（默认 3s；关闭缓存时不空转）。 */
    @Scheduled(initialDelayString = "${ecos.cache.outbox.initial-delay-ms:5000}",
            fixedDelayString = "${ecos.cache.outbox.relay-ms:3000}")
    public void relay() {
        try {
            int published = relayOnce();
            if (published > 0) {
                log.debug("Outbox 中继：本轮发布 {} 条缓存失效", published);
            }
        } catch (RuntimeException e) {
            log.warn("Outbox 中继异常（下轮重试）: {}", e.getMessage());
        }
    }

    /** 单轮中继；返回成功发布条数（供单测直调）。 */
    int relayOnce() {
        RuntimeCacheManager manager = cache == null ? null : cache.getIfAvailable();
        if (manager != null && !manager.enabled()) {
            return 0; // 缓存关闭：失效广播无意义，不空转
        }
        RedisStringCache channel = redis == null ? null : redis.getIfAvailable();
        List<Map<String, Object>> pending = outboxDao.pollPending(BATCH);
        int published = 0;
        for (Map<String, Object> row : pending) {
            String id = String.valueOf(row.get("id"));
            String payload = String.valueOf(row.get("payload_json"));
            long receivers = channel == null ? -1L : channel.publish(CHANNEL, payload);
            if (receivers >= 0L) {
                outboxDao.markPublished(id);
                published++;
            } else {
                outboxDao.markFailed(id, "redis publish unavailable", MAX_ATTEMPTS);
            }
        }
        return published;
    }

    /**
     * 保留期清退（默认 10 分钟一轮）。
     *
     * <p><b>为什么必须有这一条</b>：#147AN 把生产端接通后，每一次写路径失效都会落一行；
     * 没有清退就等于把「跨节点失效」的实现代价转成一张无界增长的表。清退只碰 PUBLISHED，
     * PENDING/FAILED 一律留（后者是重放与观测的唯一线索）。</p>
     */
    @Scheduled(initialDelayString = "${ecos.cache.outbox.purge-initial-delay-ms:60000}",
            fixedDelayString = "${ecos.cache.outbox.purge-ms:600000}")
    public void purge() {
        try {
            int removed = purgeOnce();
            if (removed > 0) {
                log.info("Outbox 保留期清退：删除 {} 条 PUBLISHED 行（保留 {} 分钟）",
                        removed, retentionMinutes);
            }
        } catch (RuntimeException e) {
            log.warn("Outbox 保留期清退异常（下轮重试）: {}", e.getMessage());
        }
    }

    /** 单轮清退；返回删除条数（供单测与活体腿直调）。 */
    int purgeOnce() {
        Timestamp cutoff = Timestamp.from(Instant.now().minus(Duration.ofMinutes(retentionMinutes)));
        return outboxDao.purgePublished(cutoff, PURGE_BATCH);
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
