package com.chinacreator.gzcm.sysman.iam.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

import com.chinacreator.gzcm.common.context.TenantContextHolder;
import com.chinacreator.gzcm.runtime.core.cache.CacheSettings;
import com.chinacreator.gzcm.runtime.core.cache.CacheSettingsSource;
import com.chinacreator.gzcm.runtime.core.cache.RuntimeCacheManager;
import com.chinacreator.gzcm.sysman.iam.dao.OrganizationDao;
import com.chinacreator.gzcm.sysman.iam.entity.Organization;
import com.chinacreator.gzcm.sysman.iam.entity.UserOrganization;

/**
 * 机构归属缓存门（14 类矩阵「机构与组织基础信息」；#147AF/D）。
 *
 * <p>四条：① 开缓存时同一用户两次解析只回源一次（这是 ClearanceInterceptor 每请求的热读）；
 * ② 缓存关闭/无租户上下文时不缓存（权限先于缓存）；③ 分配/移除/设主机构后缓存立即失效；
 * ④ 缓存值跨请求可回放且内容一致（JSON 往返）。</p>
 *
 * @author ECOS Sysman #147AF
 */
class OrganizationAffiliationCacheGateTest {

    private static final String USER = "u-1";

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    private static ObjectProvider<RuntimeCacheManager> provider(RuntimeCacheManager manager) {
        return new ObjectProvider<>() {
            @Override
            public RuntimeCacheManager getObject(Object... args) {
                return manager;
            }

            @Override
            public RuntimeCacheManager getObject() {
                return manager;
            }

            @Override
            public RuntimeCacheManager getIfAvailable() {
                return manager;
            }

            @Override
            public RuntimeCacheManager getIfUnique() {
                return manager;
            }
        };
    }

    private static RuntimeCacheManager manager(CacheSettings settings) {
        CacheSettingsSource src = () -> settings;
        return new RuntimeCacheManager(new ObjectProvider<>() {
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
        });
    }

    private static OrganizationServiceImpl service(OrganizationDao dao, RuntimeCacheManager manager) {
        OrganizationServiceImpl svc = new OrganizationServiceImpl(dao);
        ReflectionTestUtils.setField(svc, "affiliationCacheProvider", provider(manager));
        return svc;
    }

    private static UserOrganization row(String orgId, String primary) {
        UserOrganization uo = new UserOrganization();
        uo.setUserId(USER);
        uo.setOrgId(orgId);
        uo.setIsPrimary(primary);
        return uo;
    }

    @Test
    @DisplayName("开缓存：两次解析只回源一次，且内容 JSON 往返一致")
    void cachesAffiliationsAcrossCalls() throws Exception {
        OrganizationDao dao = mock(OrganizationDao.class);
        when(dao.getUserOrganizationAffiliations(USER)).thenReturn(List.of(row("org-1", "1"), row("org-2", "0")));
        OrganizationServiceImpl svc = service(dao, manager(new CacheSettings(true, 600L, 60L, false, "dev")));
        TenantContextHolder.setTenantId("tenant-a");

        List<UserOrganization> first = svc.getUserOrganizationAffiliations(USER);
        List<UserOrganization> second = svc.getUserOrganizationAffiliations(USER);

        assertEquals(2, first.size());
        assertEquals(2, second.size(), "命中缓存后仍应拿到完整两条");
        assertEquals("org-1", second.get(0).getOrgId(), "缓存回放须保序且字段一致");
        assertEquals("1", second.get(0).getIsPrimary());
        verify(dao, times(1)).getUserOrganizationAffiliations(USER);
    }

    @Test
    @DisplayName("无租户上下文：不缓存（权限先于缓存），每次回源")
    void noTenantMeansNoCaching() throws Exception {
        OrganizationDao dao = mock(OrganizationDao.class);
        when(dao.getUserOrganizationAffiliations(USER)).thenReturn(List.of(row("org-1", "1")));
        OrganizationServiceImpl svc = service(dao, manager(new CacheSettings(true, 600L, 60L, false, "dev")));

        svc.getUserOrganizationAffiliations(USER);
        svc.getUserOrganizationAffiliations(USER);

        verify(dao, times(2)).getUserOrganizationAffiliations(USER);
    }

    @Test
    @DisplayName("写面（移除机构关联）后缓存立即失效：下次读取回源")
    void writeInvalidatesCache() throws Exception {
        OrganizationDao dao = mock(OrganizationDao.class);
        when(dao.getUserOrganizationAffiliations(USER)).thenReturn(List.of(row("org-1", "1")));
        OrganizationServiceImpl svc = service(dao, manager(new CacheSettings(true, 600L, 60L, false, "dev")));
        TenantContextHolder.setTenantId("tenant-a");

        svc.getUserOrganizationAffiliations(USER);
        svc.removeUserFromOrg(USER, "org-1");
        svc.getUserOrganizationAffiliations(USER);

        verify(dao, times(2)).getUserOrganizationAffiliations(USER);
        verify(dao).removeUserFromOrg(USER, "org-1");
    }

    @Test
    @DisplayName("分配机构后缓存立即失效（含主机构变更）")
    void assignInvalidatesCache() throws Exception {
        OrganizationDao dao = mock(OrganizationDao.class);
        when(dao.getUserOrganizationAffiliations(USER)).thenReturn(List.of(row("org-1", "1")));
        Organization org = new Organization();
        org.setOrgId("org-2");
        when(dao.getOrganization("org-2")).thenReturn(org);
        OrganizationServiceImpl svc = service(dao, manager(new CacheSettings(true, 600L, 60L, false, "dev")));
        TenantContextHolder.setTenantId("tenant-a");

        svc.getUserOrganizationAffiliations(USER);
        svc.assignUserToOrg(USER, "org-2", true, "tester");
        svc.getUserOrganizationAffiliations(USER);

        verify(dao, times(2)).getUserOrganizationAffiliations(USER);
        verify(dao).assignUserToOrg(any(UserOrganization.class));
        verify(dao, times(1)).setPrimaryOrg(USER, "org-2");
    }
}
