package com.chinacreator.gzcm.sysman.abac;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.chinacreator.gzcm.common.context.TenantContextHolder;
import com.chinacreator.gzcm.sysman.abac.model.AbacPolicy;
import com.chinacreator.gzcm.sysman.abac.service.IAbacPolicyService;
import com.chinacreator.gzcm.sysman.iam.service.IPermissionCheckService;

/**
 * ABAC PEP 的 fail-closed 与「无自持缓存」门（§2.9.8⑤⑥⑦ / DC-3；#147AG）。
 *
 * <p>旧形态有两枚自建 Caffeine：策略常驻 + 决策 5 分钟硬编码 TTL、键
 * {@code subject|resource|action} 无租户维度；且策略加载失败时**回退内存旧策略**。
 * 三条后果：① 缓存配置不服 Runtime 单源（DC-3）；② 决策键无租户段（越权形态同 Q-4）；
 * ③ 失败回退是 <b>fail-open</b>——DB 故障期间撤权/新增 DENY 不生效。
 * 本批删除两枚缓存并把加载失败改为抛（由 {@code evaluate} 的 catch 落 DENY），
 * 本门钉住：失败必拒且不惊动 RBAC、策略变更**立即生效**（无陈旧窗口）、作用域过滤仍生效。</p>
 *
 * @author ECOS Sysman #147AG
 */
class AbacPepFailClosedNoCacheTest {

    private static final String USER = "u-1";
    private static final String RES = "res-a";
    private static final String ACTION = "read";

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    private static AbacPolicy denyPolicy(String scopeType, String scopeId) {
        AbacPolicy p = new AbacPolicy();
        p.setPolicyId("pol-" + scopeType + "-deny");
        p.setPolicyName("deny-all-" + scopeType);
        p.setEffect("DENY");
        p.setPriority(100);
        p.setScopeType(scopeType);
        p.setScopeId(scopeId);
        return p;
    }

    private static IPermissionCheckService rbacAllowing(boolean allow) throws Exception {
        IPermissionCheckService rbac = mock(IPermissionCheckService.class);
        when(rbac.checkPermission(anyString(), anyString(), anyString())).thenReturn(allow);
        return rbac;
    }

    @Test
    @DisplayName("策略加载失败＝DENY，且不再拿陈旧策略放行（旧实现 fail-open）")
    void policyLoadFailureIsFailClosedAndSkipsRbac() throws Exception {
        IPermissionCheckService rbac = rbacAllowing(true);
        AbacPepService pep = new AbacPepService(rbac);
        IAbacPolicyService broken = mock(IAbacPolicyService.class);
        when(broken.listPolicies()).thenThrow(new RuntimeException("db down"));
        ReflectionTestUtils.setField(pep, "policyService", broken);

        assertFalse(pep.evaluate(USER, RES, ACTION), "策略面不可用必须 DENY（不可回退旧策略）");
        verify(rbac, never()).checkPermission(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("策略服务未装配＝无显式策略，回退 RBAC（既有语义不变）")
    void missingPolicyServiceFallsBackToRbac() throws Exception {
        IPermissionCheckService rbac = rbacAllowing(true);
        AbacPepService pep = new AbacPepService(rbac);

        assertTrue(pep.evaluate(USER, RES, ACTION));
        verify(rbac, times(1)).checkPermission(USER, RES, ACTION);
    }

    @Test
    @DisplayName("显式 GLOBAL DENY 覆盖 RBAC 放行（DENY-OVERRIDES）")
    void globalDenyOverridesRbacAllow() throws Exception {
        IPermissionCheckService rbac = rbacAllowing(true);
        AbacPepService pep = new AbacPepService(rbac);
        IAbacPolicyService policies = mock(IAbacPolicyService.class);
        when(policies.listPolicies()).thenReturn(List.of(denyPolicy("GLOBAL", null)));
        ReflectionTestUtils.setField(pep, "policyService", policies);

        assertFalse(pep.evaluate(USER, RES, ACTION));
        verify(rbac, never()).checkPermission(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("作用域过滤：TENANT 策略只在其租户生效，他租户回退 RBAC")
    void tenantPolicyAppliesOnlyInItsTenant() throws Exception {
        IPermissionCheckService rbac = rbacAllowing(true);
        AbacPepService pep = new AbacPepService(rbac);
        IAbacPolicyService policies = mock(IAbacPolicyService.class);
        when(policies.listPolicies()).thenReturn(List.of(denyPolicy("TENANT", "tenant-a")));
        ReflectionTestUtils.setField(pep, "policyService", policies);

        TenantContextHolder.setTenantId("tenant-a");
        assertFalse(pep.evaluate(USER, RES, ACTION), "本租户 DENY 必须生效");

        TenantContextHolder.setTenantId("tenant-b");
        assertTrue(pep.evaluate(USER, RES, ACTION), "他租户策略不得越界生效");
    }

    @Test
    @DisplayName("无决策缓存：策略撤回后下一次评估立即改判（旧实现 5 分钟内仍命中旧决策）")
    void noDecisionCacheSoRevocationTakesEffectImmediately() throws Exception {
        IPermissionCheckService rbac = rbacAllowing(true);
        AbacPepService pep = new AbacPepService(rbac);
        IAbacPolicyService policies = mock(IAbacPolicyService.class);
        when(policies.listPolicies())
                .thenReturn(List.of(denyPolicy("GLOBAL", null)))
                .thenReturn(List.of());
        ReflectionTestUtils.setField(pep, "policyService", policies);

        assertFalse(pep.evaluate(USER, RES, ACTION), "首次：DENY 策略生效");
        assertTrue(pep.evaluate(USER, RES, ACTION),
                "策略撤回后必须立即改判；若命中旧决策缓存则本断言变红");
        verify(policies, times(2)).listPolicies();
    }
}
