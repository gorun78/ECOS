package com.chinacreator.gzcm.runtime.core.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Runtime 统一缓存门面（§2.9.8④⑤；#147J）。
 *
 * <p><b>定位</b>：受控加速层——权威数据源唯一、可重建、权限先于缓存、fail-closed。
 * 对象通道为进程内 TTL；字符串通道在 {@code ecos.cache.redis_enabled=true} 且
 * {@link RedisStringCache} 可用时走 Redis（跨进程共享），否则退回进程内——
 * 两条路径语义一致，Redis 只替换存储，键规范/{@code CacheSettings} 单一事实源不变。</p>
 *
 * <p><b>五条硬口径</b>：</p>
 * <ol>
 *   <li><b>关闭即不缓存</b>：{@link CacheSettings#enabled()} 为 false 时 get 恒空、put 恒 no-op
 *       （调用方必须回源，绝不把"没有缓存"当"没有数据"）；</li>
 *   <li><b>版本化</b>：键含 {@code v{version}}，写新版本即天然隔离旧值；</li>
 *   <li><b>先提交权威数据、再失效缓存</b>：失效只能由写路径在提交后调用
 *       {@link #invalidateDomain} / {@link #evict} / {@link #evictObject}，缓存层不反向改权威数据；
 *       {@link #invalidateDomainLocal} 是广播处理端的专用入口（执行失效但不宣告失效，防回环）；</li>
 *   <li><b>环境段单源</b>：{@link CacheKey} 的 {@code env} 只能由 {@link #env()} / {@link #key} 从
 *       {@code CacheSettings} 给出，各服务不得自备 env 拼键；</li>
 *   <li><b>陈旧上限由底座钳制</b>：{@code maxStaleSeconds} &gt;0 时是所有写入的 TTL 天花板；
 *       权限类对象还要求该上限为正值才可缓存（{@link #stalenessCeilingSeconds()}）。</li>
 * </ol>
 *
 * @author ECOS Runtime底座 #147J
 */
@Component
public class RuntimeCacheManager {

    private static final Logger log = LoggerFactory.getLogger(RuntimeCacheManager.class);

    private final ObjectProvider<CacheSettingsSource> settingsSource;
    private final LongSupplier clock;
    private final ConcurrentHashMap<String, Entry> store = new ConcurrentHashMap<>();
    /** 字符串通道的进程内兜底（未启用 Redis 或 Redis 不可用时的同语义存储）。 */
    private final ConcurrentHashMap<String, StringEntry> stringStore = new ConcurrentHashMap<>();

    /**
     * Redis 远程通道（可选装配；§2.9.8 远程共享层）。字段注入以避免改动既有两参构造器
     * 的全部测试调用点；缺席时字符串通道退回进程内。
     */
    @Autowired(required = false)
    private ObjectProvider<RedisStringCache> redisProvider;

    /**
     * 失效广播投递口（可选装配；§2.9.8④ 跨节点失效的<b>生产</b>半，实现 = Outbox 中继）。
     *
     * <p>缺席或缓存关闭时不投递——本节点直删共享 Redis 键已覆盖远程面，Outbox 只补两件事：
     * Redis 短暂不可用时其它节点退回的进程内副本要被告知清、以及失效意图的持久重试。
     * 广播粒度是<b>域</b>：粗粒度只会多清不会少清（多清=回源，绝不返回陈旧值），
     * 换来的是不必为每种对象形态另设事件。</p>
     */
    @Autowired(required = false)
    private ObjectProvider<CacheInvalidationNotifier> outboxProvider;

    @Autowired
    public RuntimeCacheManager(ObjectProvider<CacheSettingsSource> settingsSource) {
        this(settingsSource, System::currentTimeMillis);
    }

    /** 测试用时钟注入（避免 sleep 判 TTL）。 */
    RuntimeCacheManager(ObjectProvider<CacheSettingsSource> settingsSource, LongSupplier clock) {
        this.settingsSource = settingsSource;
        this.clock = clock;
    }

    private CacheSettings settings() {
        CacheSettingsSource src = settingsSource != null ? settingsSource.getIfAvailable() : null;
        if (src == null) {
            return CacheSettings.DISABLED;
        }
        CacheSettings s = src.settings();
        return s == null ? CacheSettings.DISABLED : s.normalized();
    }

    public boolean enabled() {
        return settings().enabled();
    }

    /**
     * 缓存键环境段的单源供给面（§2.9.8③/⑤；#147AA）。
     *
     * <p>各服务<b>不得</b>自备 env 拼键——环境段只能来自 Runtime 配置单源，
     * 否则同库多环境会互串缓存。</p>
     */
    public String env() {
        return settings().env();
    }

    /**
     * 由 Runtime 组装缓存键：env 取配置单源，其余五段由调用方给出。
     *
     * <p>{@code scope} 传空白会由 {@link CacheKey} 直接抛出——权限先于缓存，
     * 无隔离域的键禁止存在，调用方须把「拿不到租户」当失败回源而不是当无域缓存。</p>
     */
    public CacheKey key(String domain, String objectType, String scope, String objectId, String version) {
        return new CacheKey(env(), domain, objectType, scope, objectId, version);
    }

    /**
     * 陈旧时间上限（秒）；≤0 表示配置未给出上限。
     *
     * <p>权限类对象（受控加速层里最敏感的一类）在拿到正值上限前必须<b>不缓存</b>
     * （fail-closed），不得用缺省 TTL 兜底。</p>
     */
    public long stalenessCeilingSeconds() {
        return settings().maxStaleSeconds();
    }

    /** 读缓存；过期/关闭/未命中一律空（调用方回源）。 */
    public <T> Optional<T> get(CacheKey key, Class<T> type) {
        if (key == null || type == null || !enabled()) {
            return Optional.empty();
        }
        Entry e = store.get(key.toString());
        if (e == null) {
            return Optional.empty();
        }
        if (e.expiresAt <= clock.getAsLong()) {
            store.remove(key.toString(), e);
            return Optional.empty();
        }
        Object value = e.value;
        return type.isInstance(value) ? Optional.of(type.cast(value)) : Optional.empty();
    }

    /**
     * 写缓存。
     *
     * <p>最终 TTL 受两道约束：{@code maxStaleSeconds} &gt;0 时作为<b>上限钳制</b>（显式 TTL 也压不住它），
     * 钳制后 ≤0 则不缓存。这使 §2.9.8④「权限类对象陈旧时间 ≤ 配置上限」由底座而非各消费方自律保证。</p>
     *
     * @param ttlSeconds &gt;0 用显式 TTL；≤0 用配置缺省 TTL；最终 TTL 为 0 时不缓存
     */
    public void put(CacheKey key, Object value) {
        put(key, value, 0L);
    }

    public void put(CacheKey key, Object value, long ttlSeconds) {
        CacheSettings s = settings();
        if (key == null || value == null || !s.enabled()) {
            return;
        }
        long ttl = ttlSeconds > 0 ? ttlSeconds : s.defaultTtlSeconds();
        if (s.maxStaleSeconds() > 0) {
            ttl = Math.min(ttl, s.maxStaleSeconds());
        }
        if (ttl <= 0) {
            return;
        }
        store.put(key.toString(), new Entry(value, clock.getAsLong() + ttl * 1000L));
    }

    /** 精确失效（写路径提交后调用）。 */
    public void evict(CacheKey key) {
        if (key != null) {
            store.remove(key.toString());
            stringStore.remove(key.toString());
            RedisStringCache redis = redis();
            if (redis != null) {
                redis.evict(key.toString());
            }
        }
    }

    // ── 字符串通道（对象编解码留给消费方；#147AF）────────────────────────

    /**
     * 写字符串缓存；TTL 规则与对象通道一致（显式 TTL → 缺省 TTL → 陈旧上限钳制）。
     *
     * <p>声明 {@code redis_enabled=true} 且 Redis 可用时<b>只写 Redis</b>（避免本地/远程双写分裂）；
     * 否则退回进程内。两条路径失败都只会让调用方后续回源，不影响正确性。</p>
     */
    public void putString(CacheKey key, String value, long ttlSeconds) {
        CacheSettings s = settings();
        if (key == null || value == null || !s.enabled()) {
            return;
        }
        long ttl = ttlSeconds > 0 ? ttlSeconds : s.defaultTtlSeconds();
        if (s.maxStaleSeconds() > 0) {
            ttl = Math.min(ttl, s.maxStaleSeconds());
        }
        if (ttl <= 0) {
            return;
        }
        RedisStringCache redis = redis();
        if (s.redisEnabled() && redis != null && redis.available()) {
            redis.put(key.toString(), value, ttl);
            return;
        }
        stringStore.put(key.toString(), new StringEntry(value, clock.getAsLong() + ttl * 1000L));
    }

    /** 读字符串缓存；关闭/过期/不可用一律空（调用方回源）。 */
    public Optional<String> getString(CacheKey key) {
        if (key == null || !enabled()) {
            return Optional.empty();
        }
        CacheSettings s = settings();
        RedisStringCache redis = redis();
        if (s.redisEnabled() && redis != null && redis.available()) {
            return redis.get(key.toString());
        }
        StringEntry e = stringStore.get(key.toString());
        if (e == null) {
            return Optional.empty();
        }
        if (e.expiresAt() <= clock.getAsLong()) {
            stringStore.remove(key.toString(), e);
            return Optional.empty();
        }
        return Optional.of(e.value());
    }

    /** 观测面：当前是否真正走 Redis（配置开启且通道可用）。 */
    public boolean remoteEnabled() {
        CacheSettings s = settings();
        RedisStringCache redis = redis();
        return s.enabled() && s.redisEnabled() && redis != null && redis.available();
    }

    /** 观测面：进程内字符串通道条目数（不含 Redis）。 */
    public int localStringSize() {
        return stringStore.size();
    }

    private RedisStringCache redis() {
        return redisProvider == null ? null : redisProvider.getIfAvailable();
    }

    /**
     * 按对象标识失效（同 domain + objectType + objectId，跨所有 scope）。
     *
     * <p>用户权限/机构授权变更后，同一用户可能同时存在于多个租户 scope 的键下；
     * 只按当前 scope 精确失效会留下旧租户视图的陈旧权限（#147AA Q-4）。逐段比对
     * 只删命中键，不做通配脚本。</p>
     *
     * @return 被删除的键数
     */
    public int evictObject(String domain, String objectType, String objectId) {
        if (domain == null || objectType == null || objectId == null) {
            return 0;
        }
        int before = store.size();
        store.keySet().removeIf(raw -> {
            CacheKey k = CacheKey.parse(raw);
            return k != null && domain.equals(k.domain())
                    && objectType.equals(k.objectType())
                    && objectId.equals(k.objectId());
        });
        int beforeStrings = stringStore.size();
        stringStore.keySet().removeIf(raw -> {
            CacheKey k = CacheKey.parse(raw);
            return k != null && domain.equals(k.domain())
                    && objectType.equals(k.objectType())
                    && objectId.equals(k.objectId());
        });
        RedisStringCache redis = redis();
        if (redis != null) {
            // 跨全部 scope：ecos:{env}:{domain}:{objectType}:*:{objectId}:v*
            redis.evictPrefix("ecos:" + env() + ":" + domain + ':' + objectType + ":*:" + objectId + ":v");
        }
        notifyDomain(domain, "object=" + objectType + '/' + objectId);
        return (before - store.size()) + (beforeStrings - stringStore.size());
    }

    /**
     * 域级失效（写路径提交后调用）：清本节点 + 共享 Redis 键，并投递跨节点失效意图。
     *
     * <p>广播走 Outbox（{@link CacheInvalidationNotifier}），投递失败不影响本节点已完成的
     * 直删——最坏情形是他节点副本留到 TTL 上限，而不是本节点留脏。</p>
     *
     * @return 被删除的键数（本节点 + Redis，不含他节点尚未收到的广播）
     */
    public int invalidateDomain(String env, String domain) {
        int removed = invalidateDomainLocal(env, domain);
        notifyDomain(domain, "domain");
        return removed;
    }

    /**
     * 只做失效、不投递广播的域级清退——<b>订阅端专用</b>。
     *
     * <p>广播处理若再走 {@link #invalidateDomain} 会再次入队 ⇒ Outbox→发布→订阅→入队 成环。
     * 底座把「执行失效」与「宣告失效」分成两个入口，环就在这一层断开，而不是靠运行时标志位。</p>
     */
    public int invalidateDomainLocal(String env, String domain) {
        CacheKey probe;
        try {
            probe = new CacheKey(env, domain, "any", "any", "any", "0");
        } catch (IllegalArgumentException e) {
            log.warn("缓存域失效入参非法: env={} domain={}", env, domain);
            return 0;
        }
        String prefix = "ecos:" + probe.env() + ':' + probe.domain() + ':';
        int before = store.size();
        store.keySet().removeIf(k -> k.startsWith(prefix));
        int beforeStrings = stringStore.size();
        stringStore.keySet().removeIf(k -> k.startsWith(prefix));
        int removed = (before - store.size()) + (beforeStrings - stringStore.size());
        RedisStringCache redis = redis();
        if (redis != null) {
            removed += redis.evictPrefix(prefix);
        }
        return removed;
    }

    /**
     * 投递域级失效意图（关闭缓存 / 未装配投递口 / 投递异常都只记日志，不影响调用方）。
     *
     * <p>意图落库失败＝这次失效不会广播出去，其它节点副本留到 TTL 收敛，
     * 因此必须 {@code warn} 让人可见，禁静默。</p>
     */
    private void notifyDomain(String domain, String reason) {
        CacheInvalidationNotifier outbox = outbox();
        if (outbox == null || !enabled() || domain == null || domain.isBlank()) {
            return;
        }
        try {
            outbox.enqueueDomainInvalidation(env(), domain, reason);
        } catch (RuntimeException e) {
            log.warn("缓存失效意图投递失败（其它节点副本将留到 TTL 收敛）: domain={} cause={}",
                    domain, e.getMessage());
        }
    }

    private CacheInvalidationNotifier outbox() {
        return outboxProvider == null ? null : outboxProvider.getIfAvailable();
    }

    /** 清空（仅运维/测试；生产由 TTL 自然收敛）。**不**清 Redis（跨节点共享面须显式运维处理）。 */
    public void clear() {
        store.clear();
        stringStore.clear();
    }

    /** 进程内条目数（对象通道 + 字符串通道；不含 Redis，Redis 规模须用 countPrefix 观测）。 */
    public int size() {
        return store.size() + stringStore.size();
    }

    private record Entry(Object value, long expiresAt) {
    }

    private record StringEntry(String value, long expiresAt) {
    }
}
