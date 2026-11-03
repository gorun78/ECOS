package com.chinacreator.gzcm.runtime.core.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import com.chinacreator.gzcm.runtime.core.cache.outbox.CacheInvalidationRelay;

/**
 * 失效广播生产半的门禁（§2.9.8④；#147AN）。
 *
 * <p><b>差距来源</b>：Outbox 表、中继、订阅端在 #147AF 就落地了，生产端却只有
 * {@code PermissionCacheServiceImpl.evictAll} 一条腿直连中继——其余已接缓存的 9 个域的写路径
 * 一律不递料，他节点副本只能等 TTL 收敛。本类钉住「底座确实递料」与「广播处理端不再递料（防环）」，
 * 两条都是能变红的断言：摘掉 {@code notifyDomain} 前两枚即红，把订阅端改回 {@code invalidateDomain}
 * 第二枚即红。</p>
 *
 * @author ECOS Runtime #147AN
 */
@DisplayName("#147AN/CacheInvalidationNotify：域级失效必须递料，且只递一次")
class CacheInvalidationNotifyGateTest {

    /** 记录每次入队的假投递口。 */
    private static final class RecordingNotifier implements CacheInvalidationNotifier {
        final List<String> calls = new ArrayList<>();

        @Override
        public String enqueueDomainInvalidation(String env, String domain, String reason) {
            calls.add(env + "|" + domain + "|" + reason);
            return "row-" + calls.size();
        }
    }

    /** 抛异常的投递口：投递失败不得影响调用方（权威回源面必须仍然可用）。 */
    private static final class ThrowingNotifier implements CacheInvalidationNotifier {
        @Override
        public String enqueueDomainInvalidation(String env, String domain, String reason) {
            throw new IllegalStateException("outbox 落库失败");
        }
    }

    private static <T> ObjectProvider<T> provider(T value) {
        return new ObjectProvider<>() {
            @Override
            public T getObject(Object... args) {
                return value;
            }

            @Override
            public T getObject() {
                return value;
            }

            @Override
            public T getIfAvailable() {
                return value;
            }

            @Override
            public T getIfUnique() {
                return value;
            }
        };
    }

    /**
     * @param enabled  缓存开关（关闭态用于验「不递料」）
     * @param notifier 投递口；传 null 表示未装配（单节点 / 无 Outbox 部署）
     */
    private static RuntimeCacheManager cache(boolean enabled, CacheInvalidationNotifier notifier) {
        CacheSettings settings = new CacheSettings(enabled, 120L, 60L, false, "dev");
        RuntimeCacheManager manager = new RuntimeCacheManager(provider(() -> settings));
        inject(manager, "outboxProvider", provider(notifier));
        return manager;
    }

    private static void inject(RuntimeCacheManager manager, String field, Object value) {
        try {
            Field f = RuntimeCacheManager.class.getDeclaredField(field);
            f.setAccessible(true);
            f.set(manager, value);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("注入字段失败: " + field, e);
        }
    }

    private static CacheKey key(String domain, String objectType, String objectId) {
        return new CacheKey("dev", domain, objectType, "tenant-a", objectId, "1");
    }

    @Test
    @DisplayName("evictObject 递一条域级意图：env/域/对象线索都能在负载里复原")
    void evictObjectEnqueuesDomainScopedIntent() {
        RecordingNotifier notifier = new RecordingNotifier();
        RuntimeCacheManager manager = cache(true, notifier);
        manager.putString(key("kb-search", "knowledge-search", "abc"), "v", 30L);

        assertEquals(1, manager.evictObject("kb-search", "knowledge-search", "abc"));
        assertEquals(List.of("dev|kb-search|object=knowledge-search/abc"), notifier.calls);
    }

    @Test
    @DisplayName("invalidateDomain 递一条；invalidateDomainLocal 一条都不递（订阅端专用入口=防环缝）")
    void invalidateDomainEnqueuesButLocalVariantDoesNot() {
        RecordingNotifier notifier = new RecordingNotifier();
        RuntimeCacheManager manager = cache(true, notifier);
        manager.putString(key("ontology", "ontology-list", "all"), "v", 30L);

        assertEquals(1, manager.invalidateDomain("dev", "ontology"));
        assertEquals(List.of("dev|ontology|domain"), notifier.calls);

        // 广播处理端走 Local 变体：不得再次递料，否则 广播→订阅→入队→发布 成环
        manager.invalidateDomainLocal("dev", "ontology");
        assertEquals(1, notifier.calls.size(), "Local 变体不得递料（防回环的唯一结构缝）");
    }

    @Test
    @DisplayName("缓存关闭时不递料：没有缓存副本就没有需要广播的失效，禁空写 Outbox")
    void disabledCacheEnqueuesNothing() {
        RecordingNotifier notifier = new RecordingNotifier();
        RuntimeCacheManager manager = cache(false, notifier);

        manager.evictObject("kb-search", "knowledge-search", "abc");
        manager.invalidateDomain("dev", "kb-search");
        assertTrue(notifier.calls.isEmpty(), "关闭态必须零投递");
    }

    @Test
    @DisplayName("投递失败只记日志：本节点失效已完成，回源面不得被拖挂")
    void notifierFailureDoesNotBreakEviction() {
        RuntimeCacheManager manager = cache(true, new ThrowingNotifier());
        CacheKey k = key("sysman", "user-profile", "u-admin");
        manager.putString(k, "v", 30L);
        assertEquals(Optional.of("v"), manager.getString(k));

        assertEquals(1, manager.evictObject("sysman", "user-profile", "u-admin"));
        assertEquals(Optional.empty(), manager.getString(k));
    }

    @Test
    @DisplayName("未装配投递口（单节点部署）：底座照常读写与失效，不因缺 bean 失败")
    void absentNotifierIsTolerated() {
        RuntimeCacheManager manager = cache(true, null);
        CacheKey k = key("cognitive-model", "model-list", "all");
        manager.putString(k, "v", 30L);
        assertEquals(Optional.of("v"), manager.getString(k));

        assertEquals(1, manager.evictObject("cognitive-model", "model-list", "all"));
        assertEquals(Optional.empty(), manager.getString(k));
    }

    @Test
    @DisplayName("底座只认接口：中继确实实现它，且字段按接口注入（依赖方向 outbox→cache，无包级环）")
    void relayImplementsTheInterfaceAndFieldIsTypedToIt() throws Exception {
        assertTrue(CacheInvalidationNotifier.class.isAssignableFrom(CacheInvalidationRelay.class),
                "中继必须实现投递接口，否则底座递料无处可去");
        Field f = RuntimeCacheManager.class.getDeclaredField("outboxProvider");
        assertEquals(ObjectProvider.class, f.getType());
        assertTrue(f.getGenericType().getTypeName().contains(CacheInvalidationNotifier.class.getName()),
                "递料口必须按接口注入（禁绑实现类）");
    }

    @Test
    @DisplayName("假面自检：provider 假面对 null 返回 null（防把装配缺席测成有装配）")
    void providerFakeReturnsNullForAbsentBean() {
        assertNull(provider(null).getIfAvailable());
        cache(true, null); // 装配缺席路径本身不得抛
    }
}
