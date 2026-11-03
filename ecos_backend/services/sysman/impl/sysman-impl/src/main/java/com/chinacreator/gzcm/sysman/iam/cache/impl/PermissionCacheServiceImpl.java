package com.chinacreator.gzcm.sysman.iam.cache.impl;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Service;

import com.chinacreator.gzcm.common.context.TenantContextHolder;
import com.chinacreator.gzcm.runtime.core.cache.CacheKey;
import com.chinacreator.gzcm.runtime.core.cache.RuntimeCacheManager;
import com.chinacreator.gzcm.sysman.iam.cache.PermissionCacheService;
import com.chinacreator.gzcm.sysman.iam.entity.Permission;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 权限缓存：Runtime 统一缓存门面的消费方（§2.9.8③④⑥；Q-4 处置 · #147AA）。
 *
 * <p><b>本类不再自持存储</b>：旧形态是两个裸 {@code ConcurrentHashMap}，键只有 {@code userId}、
 * 入参 {@code ttlMillis} 被丢弃、并宣称「生产环境建议替换为 Redis 实现」——那既是 §2.9.8⑤/DC-3
 * 禁止的「各服务自建缓存」，也是 §2.9.8⑥ 禁止的「无隔离维度的权限缓存」。现一律经
 * {@link RuntimeCacheManager}：键规范、环境段、TTL、陈旧上限钳制、失效都来自单源。</p>
 *
 * <p><b>三条权限类特有硬口径</b>（{@link #key} 一处收口，读/写腿同判）：</p>
 * <ol>
 *   <li><b>必须有服务端租户上下文</b>——{@code scope} 取 {@link TenantContextHolder}
 *       （由 {@code JwtAuthenticationFilter} 从令牌侧写入，客户端不可控），拿不到就
 *       <b>不缓存</b>并回源（权限先于缓存，绝不做无隔离域的全局权限键）；</li>
 *   <li><b>必须已定义最大陈旧时间</b>——{@code maxStaleSeconds ≤ 0} 时拒写拒读，
 *       撤权生效时间无法证明即不启用缓存；</li>
 *   <li><b>撤权即失效</b>——{@link #evictUser} 跨该用户全部租户视图删除，
 *       {@link #evictAll} 另把版本号推进到下一段，使失效后才落地的旧值写入不可被命中
 *       （版本化缓存；剩余竞态窗口由第 2 条的陈旧上限钳制）。</li>
 * </ol>
 *
 * <p>缓存命中不绕鉴权：调用方 {@code PermissionCheckServiceImpl} 只把命中结果当「同一份权威
 * 计算的重放」，DENY 语义与回源一致；缓存不可用/未命中一律回源，绝不把「没有缓存」当「没有权限」。</p>
 *
 * @author ECOS Sysman #147AA
 */
@Service
public class PermissionCacheServiceImpl implements PermissionCacheService {

    /** 控制域段：sysman 权限面。 */
    static final String DOMAIN = "sysman";
    /** 缓存对象类型段（14 类矩阵之「角色·权限·授权关系」的两形态）。 */
    static final String TYPE_PERMISSIONS = "permission-set";
    static final String TYPE_DECISIONS = "permission-decision";

    /** 字符串通道编解码（#147AF）：缓存值落 JSON，进程内与 Redis 两条路径共用同一编码。 */
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Set<Permission>> PERMISSION_SET_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<Map<String, Object>> DECISION_MAP_TYPE = new TypeReference<>() {
    };

    private final RuntimeCacheManager cache;

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(PermissionCacheServiceImpl.class);

    /** 失效广播 Outbox（可选装配；缺席时只清本节点）。 */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private org.springframework.beans.factory.ObjectProvider<
            com.chinacreator.gzcm.runtime.core.cache.outbox.CacheInvalidationRelay> invalidationRelay;

    /** 权限域版本号：权威数据整体变更时推进，旧版本键自然失配。 */
    private final AtomicLong version = new AtomicLong(1L);

    public PermissionCacheServiceImpl(RuntimeCacheManager cache) {
        this.cache = cache;
    }

    @Override
    public void putUserPermissions(String userId, Set<Permission> permissions, long ttlMillis) {
        CacheKey key = key(TYPE_PERMISSIONS, userId);
        if (key == null || permissions == null) {
            return;
        }
        // 字符串通道（#147AF）：对象编解码由本类承担，键/TTL/失效仍由 Runtime 单源决定，
        // 从而同一份实现既能落进程内也能落 Redis（跨节点共享的前提）。
        cache.putString(key, toJson(permissions), ttlSeconds(ttlMillis));
    }

    @Override
    public Set<Permission> getUserPermissions(String userId) {
        CacheKey key = key(TYPE_PERMISSIONS, userId);
        if (key == null) {
            return Collections.emptySet();
        }
        return cache.getString(key)
                .map(json -> fromJson(json, PERMISSION_SET_TYPE))
                .orElseGet(Collections::emptySet);
    }

    @Override
    public void putUserPermissionDecisions(String userId, Map<String, Object> decisions, long ttlMillis) {
        CacheKey key = key(TYPE_DECISIONS, userId);
        if (key == null || decisions == null) {
            return;
        }
        cache.putString(key, toJson(decisions), ttlSeconds(ttlMillis));
    }

    @Override
    public Map<String, Object> getUserPermissionDecisions(String userId) {
        CacheKey key = key(TYPE_DECISIONS, userId);
        if (key == null) {
            return Collections.emptyMap();
        }
        return cache.getString(key)
                .map(json -> fromJson(json, DECISION_MAP_TYPE))
                .orElseGet(Collections::emptyMap);
    }

    private static String toJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            // 序列化失败＝不可缓存；返回 null 由 putString 判空跳过（caller 仍会回源，不产生错值）
            return null;
        }
    }

    private static <T> T fromJson(String json, TypeReference<T> type) {
        try {
            return MAPPER.readValue(json, type);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    @Override
    public void evictUser(String userId) {
        if (userId == null || userId.isBlank()) {
            return;
        }
        cache.evictObject(DOMAIN, TYPE_PERMISSIONS, userId.trim());
        cache.evictObject(DOMAIN, TYPE_DECISIONS, userId.trim());
    }

    @Override
    public void evictAll() {
        version.incrementAndGet();
        cache.invalidateDomain(cache.env(), DOMAIN);
        // §2.9.8④ Outbox：域失效意图与写事务同批落行，由后台中继广播到其他节点（#147AF/D）。
        // 本节点已在上一行直接清除；此处失败不影响本地语义（中继会重试）。
        try {
            var relay = invalidationRelay == null ? null : invalidationRelay.getIfAvailable();
            if (relay != null) {
                relay.enqueueDomainInvalidation(cache.env(), DOMAIN, "permission-evict-all");
            }
        } catch (RuntimeException e) {
            log.warn("缓存失效 Outbox 入队失败（本节点已清，跨节点靠 TTL 兜底）: {}", e.getMessage());
        }
    }

    /** 当前权限域版本段（门禁取证用）。 */
    long currentVersion() {
        return version.get();
    }

    /**
     * 组装权限类缓存键；返回 {@code null} = 按硬口径不缓存（调用方须回源，不是「无数据」）。
     *
     * <p>键的六段由 {@link RuntimeCacheManager#key} 给出：{@code env} 来自配置单源，
     * {@code scope} 来自服务端租户上下文，{@code version} 来自本域版本号。</p>
     */
    private CacheKey key(String objectType, String userId) {
        if (userId == null || userId.isBlank()) {
            return null;
        }
        if (cache.stalenessCeilingSeconds() <= 0L) {
            return null;
        }
        String tenant = TenantContextHolder.getTenantId();
        if (tenant == null || tenant.isBlank()) {
            return null;
        }
        try {
            return cache.key(DOMAIN, objectType, tenant.trim(), userId.trim(),
                    Long.toString(version.get()));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** 毫秒 TTL 换算：≤0 交回配置缺省 TTL（最终仍受陈旧上限钳制）。 */
    static long ttlSeconds(long ttlMillis) {
        if (ttlMillis <= 0L) {
            return 0L;
        }
        return Math.max(1L, (ttlMillis + 999L) / 1000L);
    }
}
