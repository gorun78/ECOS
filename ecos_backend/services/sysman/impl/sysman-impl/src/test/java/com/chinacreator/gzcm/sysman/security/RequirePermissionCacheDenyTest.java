package com.chinacreator.gzcm.sysman.security;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.Set;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import com.chinacreator.gzcm.common.exception.ForbiddenException;
import com.chinacreator.gzcm.sysman.iam.service.IPermissionCheckService;

/**
 * 缓存决策「只作额外 DENY 源」门（§2.9.8③ 执法侧；裁决 A · #147AF/D）。
 *
 * <p><b>方向纪律</b>：缓存命中 DENY ⇒ 403（即使 authorities 放行，deny-overrides）；
 * 缓存<b>从不</b>放行；缓存判定异常 ⇒ 跳过本腿、维持既有裁决（不误拒也不放宽）。
 * 三条腿各一枚用例，其中「authorities 放行仍被 DENY」是 A 方案的核心反证。</p>
 *
 * @author ECOS Sysman #147AF
 */
class RequirePermissionCacheDenyTest {

    interface Guarded {
        @RequirePermission("system:user:query")
        void doWork();
    }

    private static ObjectProvider<IPermissionCheckService> provider(IPermissionCheckService checker) {
        return new ObjectProvider<>() {
            @Override
            public IPermissionCheckService getObject(Object... args) {
                return checker;
            }

            @Override
            public IPermissionCheckService getObject() {
                return checker;
            }

            @Override
            public IPermissionCheckService getIfAvailable() {
                return checker;
            }

            @Override
            public IPermissionCheckService getIfUnique() {
                return checker;
            }
        };
    }

    private static RequirePermissionAspect aspect(IPermissionCheckService checker) throws Exception {
        RequirePermissionAspect aspect = new RequirePermissionAspect();
        ReflectionTestUtils.setField(aspect, "permissionCheckProvider", provider(checker));
        return aspect;
    }

    private static ProceedingJoinPoint joinPoint() throws Throwable {
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        Method method = Guarded.class.getMethod("doWork");
        when(signature.getMethod()).thenReturn(method);
        when(pjp.getSignature()).thenReturn(signature);
        when(pjp.proceed()).thenReturn(null);
        return pjp;
    }

    private static void loginWith(String authority) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("u-1", null,
                        java.util.List.of(new SimpleGrantedAuthority(authority))));
    }

    @AfterEach
    void clearAuth() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("核心反证：authorities 放行，但缓存决策 DENY ⇒ 403（缓存只收紧）")
    void cachedDenyOverridesAuthorityAllow() throws Throwable {
        IPermissionCheckService checker = mock(IPermissionCheckService.class);
        when(checker.getUserDeniedCodes("u-1")).thenReturn(Set.of("system:user:query"));
        RequirePermissionAspect aspect = aspect(checker);
        loginWith("system:user:query");

        ProceedingJoinPoint pjp = joinPoint();
        assertThrows(ForbiddenException.class, () -> aspect.checkPermission(pjp));
        verify(pjp, never()).proceed();
    }

    @Test
    @DisplayName("缓存无该码的 DENY：维持既有 authority 结论（放行）")
    void cachedDenyForOtherCodeDoesNotBlock() throws Throwable {
        IPermissionCheckService checker = mock(IPermissionCheckService.class);
        when(checker.getUserDeniedCodes("u-1")).thenReturn(Set.of("other:code"));
        RequirePermissionAspect aspect = aspect(checker);
        loginWith("system:user:query");

        ProceedingJoinPoint pjp = joinPoint();
        aspect.checkPermission(pjp);
        verify(pjp).proceed();
    }

    @Test
    @DisplayName("缓存判定异常：跳过 DENY 腿，不误拒（缓存不是权威）")
    void cacheFailureDoesNotBlock() throws Throwable {
        IPermissionCheckService checker = mock(IPermissionCheckService.class);
        when(checker.getUserDeniedCodes(any())).thenThrow(new IllegalStateException("cache down"));
        RequirePermissionAspect aspect = aspect(checker);
        loginWith("system:user:query");

        ProceedingJoinPoint pjp = joinPoint();
        aspect.checkPermission(pjp);
        verify(pjp).proceed();
    }
}
