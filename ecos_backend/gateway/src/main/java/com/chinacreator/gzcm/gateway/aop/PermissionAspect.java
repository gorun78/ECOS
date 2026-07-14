package com.chinacreator.gzcm.gateway.aop;

import com.chinacreator.gzcm.common.annotation.RequirePermission;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Collection;

/**
 * S6-1.2 PermissionAspect — @RequirePermission 注解的 AOP 切面。
 *
 * <p>
 * 拦截被 @RequirePermission 标记的方法，从 {@link SecurityContextHolder} 获取当前用户权限，
 * 检查用户是否拥有注解中指定的权限码。无权限时抛出 {@link AccessDeniedException}。
 * </p>
 */
@Aspect
@Component
public class PermissionAspect {

    private static final Logger log = LoggerFactory.getLogger(PermissionAspect.class);

    /**
     * 环绕通知：校验当前用户是否拥有 @RequirePermission 指定的权限。
     *
     * @param joinPoint         切入点
     * @param requirePermission 权限注解实例
     * @return 方法执行结果
     * @throws Throwable 方法执行异常或权限不足时抛出 AccessDeniedException
     */
    @Around("@annotation(requirePermission)")
    public Object aroundRequirePermission(ProceedingJoinPoint joinPoint,
                                          RequirePermission requirePermission) throws Throwable {

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();

        String permissionCode = requirePermission.permission();
        String description = requirePermission.value();

        // 获取当前认证信息
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            log.warn("Permission denied: no authenticated user for method {}.{}",
                    method.getDeclaringClass().getSimpleName(), method.getName());
            throw new AccessDeniedException("未登录或认证已过期");
        }

        String username = authentication.getName();

        // 检查用户是否拥有所需权限
        if (!hasPermission(authentication, permissionCode)) {
            log.warn("Permission denied: user '{}' lacks permission '{}' for {}.{} (desc: {})",
                    username, permissionCode,
                    method.getDeclaringClass().getSimpleName(), method.getName(),
                    description.isEmpty() ? "N/A" : description);
            throw new AccessDeniedException(
                    "权限不足，需要权限: " + permissionCode);
        }

        log.debug("Permission granted: user '{}' has permission '{}' for {}.{}",
                username, permissionCode,
                method.getDeclaringClass().getSimpleName(), method.getName());

        return joinPoint.proceed();
    }

    /**
     * 检查认证对象是否拥有指定权限。
     * 支持角色前缀 "ROLE_" 自动匹配。
     *
     * @param authentication 认证信息
     * @param permissionCode 所需权限码
     * @return true 如果拥有权限
     */
    private boolean hasPermission(Authentication authentication, String permissionCode) {
        if (permissionCode == null || permissionCode.isEmpty()) {
            return true;
        }

        Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();
        if (authorities == null || authorities.isEmpty()) {
            return false;
        }

        // 超级管理员 / admin 角色拥有所有权限
        for (GrantedAuthority authority : authorities) {
            String auth = authority.getAuthority();
            if ("ROLE_SUPER_ADMIN".equals(auth) || "admin".equals(auth)) {
                return true;
            }
        }

        for (GrantedAuthority authority : authorities) {
            String auth = authority.getAuthority();
            if (permissionCode.equals(auth)) {
                return true;
            }
            // 也支持 ROLE_ 前缀匹配
            if (auth.startsWith("ROLE_") && permissionCode.equals(auth.substring(5))) {
                return true;
            }
        }

        return false;
    }
}
