package com.chinacreator.gzcm.sysman.iam.cache.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

import com.chinacreator.gzcm.common.context.TenantContextHolder;
import com.chinacreator.gzcm.runtime.core.cache.CacheKey;
import com.chinacreator.gzcm.runtime.core.cache.CacheSettings;
import com.chinacreator.gzcm.runtime.core.cache.CacheSettingsSource;
import com.chinacreator.gzcm.runtime.core.cache.RuntimeCacheManager;
import com.chinacreator.gzcm.sysman.iam.entity.Permission;

/**
 * Q-4 权限缓存键隔离门（§2.9.8③④⑥；#147AA）。
 *
 * <p>覆盖六条腿：键段完整（含 env/租户/类型/版本）、跨租户不串、无租户上下文不缓存、
 * 无陈旧上限不缓存、关闭态不缓存、撤权跨视图即时失效；每条正向断言都配一枚
 * 「能变红」反证（旧裸 {@code userId} 键形态必须判不合格），防止改动被无声回退。</p>
 *
 * @author ECOS Sysman #147AA
 */
class PermissionCacheIsolationGateTest {

    private static final String ENV = "unit-test";
    private static final String USER = "u-1";
    private static final String TENANT_A = "tenant-a";
    private static final String TENANT_B = "tenant-b";

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    private static ObjectProvider<CacheSettingsSource> provider(CacheSettings settings) {
        CacheSettingsSource src = () -> settings;
        return new ObjectProvider<>() {
            @Override
            public CacheSettingsSource getObject(Object... args) {
                return src;
            }

            @Override
            public CacheSettingsSource getObject() {
                return src;
            }

            @Override
            public CacheSettingsSource getIfAvailable() {
                return src;
            }

            @Override
            public CacheSettingsSource getIfUnique() {
                return src;
            }
        };
    }

    /** 启用态：缺省 TTL 600s、陈旧上限 120s、env 由配置单源给出。 */
    private static RuntimeCacheManager enabled() {
        return new RuntimeCacheManager(provider(
                new CacheSettings(true, 600L, 120L, false, ENV)));
    }

    private static Map<String, Object> decisions(String effect) {
        Map<String, Object> one = new LinkedHashMap<>();
        one.put("effect", effect);
        one.put("priority", 10);
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("perm-1", one);
        return map;
    }

    private static Permission permission(String id) {
        Permission p = new Permission();
        p.setPermissionId(id);
        p.setResource("resource-a");
        p.setAction("read");
        return p;
    }

    /**
     * 隔离性谓词：键必须带 env 段、domain 段、类型段、租户 scope 段与版本段。
     * 旧形态（裸 userId 作键）在此判红——这正是本批要修的缺陷。
     */
    static boolean isolatesByTenantAndVersion(String rawKey, String tenant) {
        CacheKey parsed = CacheKey.parse(rawKey);
        return parsed != null
                && ENV.equals(parsed.env())
                && PermissionCacheServiceImpl.DOMAIN.equals(parsed.domain())
                && tenant.equals(parsed.scope())
                && USER.equals(parsed.objectId())
                && !parsed.version().isBlank();
    }

    @Test
    @DisplayName("键段完整：env/domain/objectType/scope=服务端租户/objectId=用户/version 六段齐备")
    void keysCarryEnvDomainTypeTenantUserAndVersion() {
        RuntimeCacheManager manager = spy(enabled());
        PermissionCacheServiceImpl service = new PermissionCacheServiceImpl(manager);
        TenantContextHolder.setTenantId(TENANT_A);

        service.putUserPermissionDecisions(USER, decisions("ALLOW"), -1);
        ArgumentCaptor<CacheKey> decisionsKey = ArgumentCaptor.forClass(CacheKey.class);
        verify(manager).putString(decisionsKey.capture(), any(), anyLong());
        assertEquals("ecos:" + ENV + ":sysman:permission-decision:" + TENANT_A + ":u-1:v1",
                decisionsKey.getValue().toString());

        service.putUserPermissions(USER, Set.of(permission("perm-1")), -1);
        ArgumentCaptor<CacheKey> permissionsKey = ArgumentCaptor.forClass(CacheKey.class);
        verify(manager, org.mockito.Mockito.atLeastOnce()).putString(permissionsKey.capture(), any(), anyLong());
        assertEquals("ecos:" + ENV + ":sysman:permission-set:" + TENANT_A + ":u-1:v1",
                permissionsKey.getAllValues().get(permissionsKey.getAllValues().size() - 1).toString());

        // 反证：旧裸 userId 键形态在谓词下必须不合格
        assertFalse(isolatesByTenantAndVersion(USER, TENANT_A), "裸 userId 作键＝跨租户可命中，必须判红");
        assertFalse(isolatesByTenantAndVersion("redis:" + ENV + ":x:y:" + USER + ":z:v1", TENANT_A));
        assertTrue(isolatesByTenantAndVersion(decisionsKey.getValue().toString(), TENANT_A));
        assertFalse(isolatesByTenantAndVersion(decisionsKey.getValue().toString(), TENANT_B));
    }

    @Test
    @DisplayName("跨租户不串：tenant-a 写入的值在 tenant-b 读不到")
    void tenantAWriteIsInvisibleToTenantB() {
        PermissionCacheServiceImpl service = new PermissionCacheServiceImpl(enabled());
        TenantContextHolder.setTenantId(TENANT_A);
        service.putUserPermissionDecisions(USER, decisions("DENY"), -1);
        assertEquals("DENY", effects(service.getUserPermissionDecisions(USER)).get("perm-1"));

        TenantContextHolder.setTenantId(TENANT_B);
        assertTrue(service.getUserPermissionDecisions(USER).isEmpty(),
                "越权形态=A 租户命中 B 租户权限缓存：必须回空并由调用方回源");
    }

    @Test
    @DisplayName("无服务端租户上下文＝不缓存（权限先于缓存，不做无隔离域的全局权限键）")
    void missingTenantContextRefusesToCache() {
        RuntimeCacheManager manager = enabled();
        PermissionCacheServiceImpl service = new PermissionCacheServiceImpl(manager);

        service.putUserPermissionDecisions(USER, decisions("ALLOW"), -1);
        assertEquals(0, manager.size(), "无租户上下文不得产生任何键");
        assertTrue(service.getUserPermissionDecisions(USER).isEmpty());

        TenantContextHolder.setTenantId(TENANT_A);
        assertTrue(service.getUserPermissionDecisions(USER).isEmpty(),
                "无租户期写入既未发生，切到有租户后也不该凭空命中");
    }

    @Test
    @DisplayName("未定义最大陈旧时间＝不缓存（§2.9.8⑥ 权限缓存须先定义陈旧上限）")
    void missingStalenessCeilingRefusesToCache() {
        RuntimeCacheManager manager = new RuntimeCacheManager(provider(
                new CacheSettings(true, 600L, 0L, false, ENV)));
        PermissionCacheServiceImpl service = new PermissionCacheServiceImpl(manager);
        TenantContextHolder.setTenantId(TENANT_A);

        service.putUserPermissionDecisions(USER, decisions("ALLOW"), -1);
        assertEquals(0, manager.size(), "陈旧上限缺席时权限缓存必须拒写");
        assertTrue(service.getUserPermissionDecisions(USER).isEmpty());
    }

    @Test
    @DisplayName("关闭态不缓存：读恒空、写恒 no-op，调用方一律回源")
    void disabledSettingsStoreNothing() {
        RuntimeCacheManager manager = new RuntimeCacheManager(provider(CacheSettings.DISABLED));
        PermissionCacheServiceImpl service = new PermissionCacheServiceImpl(manager);
        TenantContextHolder.setTenantId(TENANT_A);

        service.putUserPermissionDecisions(USER, decisions("ALLOW"), -1);
        assertTrue(service.getUserPermissionDecisions(USER).isEmpty());
        assertTrue(service.getUserPermissions(USER).isEmpty());
    }

    @Test
    @DisplayName("撤权生效：evictUser 跨该用户全部租户视图即时删除，且不误伤他人")
    void evictUserClearsEveryTenantViewOnlyForThatUser() {
        PermissionCacheServiceImpl service = new PermissionCacheServiceImpl(enabled());
        TenantContextHolder.setTenantId(TENANT_A);
        service.putUserPermissionDecisions(USER, decisions("ALLOW"), -1);
        service.putUserPermissions(USER, Set.of(permission("perm-1")), -1);
        TenantContextHolder.setTenantId(TENANT_B);
        service.putUserPermissionDecisions(USER, decisions("ALLOW"), -1);
        TenantContextHolder.setTenantId(TENANT_A);
        service.putUserPermissionDecisions("u-2", decisions("ALLOW"), -1);

        service.evictUser(USER);

        TenantContextHolder.setTenantId(TENANT_A);
        assertTrue(service.getUserPermissionDecisions(USER).isEmpty(), "撤权后 A 视图必须回源");
        assertTrue(service.getUserPermissions(USER).isEmpty(), "权限集合同步失效");
        assertFalse(service.getUserPermissionDecisions("u-2").isEmpty(), "不得波及其他用户");
        TenantContextHolder.setTenantId(TENANT_B);
        assertTrue(service.getUserPermissionDecisions(USER).isEmpty(), "撤权后 B 视图必须回源");
    }

    @Test
    @DisplayName("evictAll 推进版本段并清空域；后续写入落在新版本，旧值不可再命中")
    void evictAllBumpsVersionAndClearsDomain() {
        RuntimeCacheManager manager = enabled();
        PermissionCacheServiceImpl service = new PermissionCacheServiceImpl(manager);
        TenantContextHolder.setTenantId(TENANT_A);
        service.putUserPermissionDecisions(USER, decisions("ALLOW"), -1);
        assertEquals(1, manager.size());

        service.evictAll();

        assertEquals(2L, service.currentVersion());
        assertEquals(0, manager.size(), "域级失效须清空既有键");
        service.putUserPermissionDecisions(USER, decisions("DENY"), -1);
        assertEquals("DENY", effects(service.getUserPermissionDecisions(USER)).get("perm-1"));
        assertNotEquals(1L, service.currentVersion());
    }

    @Test
    @DisplayName("权限集合与决策缓存是不同 objectType 段，互不覆盖")
    void permissionSetAndDecisionsAreSeparateObjectTypes() {
        PermissionCacheServiceImpl service = new PermissionCacheServiceImpl(enabled());
        TenantContextHolder.setTenantId(TENANT_A);
        service.putUserPermissions(USER, Set.of(permission("perm-1")), -1);
        service.putUserPermissionDecisions(USER, decisions("DENY"), -1);

        assertEquals(1, service.getUserPermissions(USER).size());
        assertEquals("DENY", effects(service.getUserPermissionDecisions(USER)).get("perm-1"));

        service.evictUser(USER);
        assertTrue(service.getUserPermissions(USER).isEmpty());
        assertTrue(service.getUserPermissionDecisions(USER).isEmpty());
    }

    @Test
    @DisplayName("毫秒 TTL 换算：≤0 交回配置缺省，正值向上取整且至少 1 秒")
    void ttlConversionKeepsPositiveMillisVisible() {
        assertEquals(0L, PermissionCacheServiceImpl.ttlSeconds(-1L));
        assertEquals(0L, PermissionCacheServiceImpl.ttlSeconds(0L));
        assertEquals(1L, PermissionCacheServiceImpl.ttlSeconds(1L));
        assertEquals(1L, PermissionCacheServiceImpl.ttlSeconds(1000L));
        assertEquals(2L, PermissionCacheServiceImpl.ttlSeconds(1001L));
        assertEquals(60L, PermissionCacheServiceImpl.ttlSeconds(60_000L));
    }

    @Test
    @DisplayName("非法 userId 段（含冒号/空白）不产生键，歧义键一律拒绝")
    void ambiguousUserIdsAreRefused() {
        RuntimeCacheManager manager = enabled();
        PermissionCacheServiceImpl service = new PermissionCacheServiceImpl(manager);
        TenantContextHolder.setTenantId("tenant:with:colon");
        service.putUserPermissionDecisions(USER, decisions("ALLOW"), -1);
        assertEquals(0, manager.size());

        TenantContextHolder.setTenantId(TENANT_A);
        service.putUserPermissionDecisions("u:1", decisions("ALLOW"), -1);
        service.putUserPermissionDecisions("  ", decisions("ALLOW"), -1);
        service.putUserPermissions(null, Collections.emptySet(), -1);
        assertEquals(0, manager.size(), "含冒号或空白的段会造成键歧义，必须整键拒绝");
        assertTrue(service.getUserPermissionDecisions("u:1").isEmpty());
    }

    private static Map<String, Object> effects(Map<String, Object> cached) {
        Map<String, Object> out = new LinkedHashMap<>();
        cached.forEach((id, raw) -> {
            if (raw instanceof Map<?, ?> map) {
                out.put(id, map.get("effect"));
            }
        });
        return out;
    }
}
