package com.chinacreator.gzcm.sysman.security;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.sysman.abac.model.AbacPolicy;
import com.chinacreator.gzcm.sysman.abac.service.IAbacPolicyService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * {@link RequirePermission} 注解的 AOP 切面。
 * <p>
 * 拦截标注了 {@code @RequirePermission} 的方法，检查当前 SecurityContext
 * 中是否包含所需权限。实现逻辑：
 * <ol>
 *   <li>从 SecurityContext 获取当前认证信息</li>
 *   <li>若未认证，返回 401</li>
 *   <li>获取用户持有的权限集合（GrantedAuthority）</li>
 *   <li>检查是否包含任一所需权限码</li>
 *   <li>若无权限，返回 403</li>
 * </ol>
 *
 * @author ECOS S6
 */
@Aspect
@Component
public class RequirePermissionAspect {

    private static final Logger log = LoggerFactory.getLogger(RequirePermissionAspect.class);

    /**
     * ABAC策略服务（可选注入，不可用时跳过ABAC评估）
     */
    @Autowired(required = false)
    private IAbacPolicyService abacPolicyService;

    /**
     * 环绕通知：拦截带有 @RequirePermission 注解的方法。
     *
     * @param joinPoint 切入点
     * @return 原方法返回值（若权限检查通过），或 ApiResponse(403)
     * @throws Throwable 如果业务方法本身抛出异常
     */
    @Around("@annotation(com.chinacreator.gzcm.sysman.security.RequirePermission)")
    public Object checkPermission(ProceedingJoinPoint joinPoint) throws Throwable {
        // 1. 检查认证状态
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            log.warn("RequirePermission check failed: not authenticated, method={}",
                joinPoint.getSignature().toShortString());
            return ApiResponse.unauthorized("未登录或Token已过期");
        }

        // 2. 获取注解中的所需权限
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        RequirePermission annotation = signature.getMethod().getAnnotation(RequirePermission.class);
        String[] requiredPermissions = annotation.value();

        if (requiredPermissions == null || requiredPermissions.length == 0) {
            // 未指定权限码，放行（容错）
            log.warn("RequirePermission annotation has empty value on {}",
                joinPoint.getSignature().toShortString());
            return joinPoint.proceed();
        }

        // 3. 获取用户持有的权限码集合
        Collection<? extends GrantedAuthority> authorities = auth.getAuthorities();
        Set<String> userPermissions = authorities.stream()
            .map(GrantedAuthority::getAuthority)
            .collect(Collectors.toSet());

        // 4. 检查是否包含任一所需权限（OR 语义）
        boolean rbacPassed = false;
        for (String required : requiredPermissions) {
            if (userPermissions.contains(required)) {
                log.debug("RBAC check passed: userId={}, required={}, has one of {}",
                    auth.getName(), required, Arrays.toString(requiredPermissions));
                rbacPassed = true;
                break;
            }
        }

        // 5. ABAC策略评估（在RBAC之后，可覆盖RBAC结果）
        String abacResult = evaluateAbacPolicies(
            requiredPermissions, userPermissions, auth.getName());
        if ("DENY".equals(abacResult)) {
            log.warn("ABAC DENY policy matched: userId={}, required={}",
                auth.getName(), Arrays.toString(requiredPermissions));
            return ApiResponse.forbidden("访问被安全策略拒绝");
        }
        if ("ALLOW".equals(abacResult)) {
            log.debug("ABAC ALLOW policy matched: userId={}, required={}",
                auth.getName(), Arrays.toString(requiredPermissions));
            return joinPoint.proceed();
        }

        // 6. 无ABAC匹配，回退到RBAC结果
        if (rbacPassed) {
            return joinPoint.proceed();
        }

        // 7. 权限不足
        log.warn("Permission denied: userId={}, required={}, user has={}",
            auth.getName(), Arrays.toString(requiredPermissions), userPermissions);
        return ApiResponse.forbidden(
            "权限不足，需要: " + String.join(" 或 ", requiredPermissions));
    }

    /**
     * 评估ABAC策略，返回 "ALLOW"（放行）、"DENY"（拒绝）或 null（无匹配）。
     * <p>
     * 策略按 priority 升序排序（数值越小优先级越高），
     * DENY 策略优先于 ALLOW 策略生效。
     *
     * @param requiredPermissions 方法所需的权限码
     * @param userPermissions     当前用户持有的权限码
     * @param userId              当前用户ID
     * @return "ALLOW" / "DENY" / null
     */
    private String evaluateAbacPolicies(String[] requiredPermissions,
                                         Set<String> userPermissions,
                                         String userId) {
        if (abacPolicyService == null) {
            return null; // ABAC服务不可用，跳过
        }
        try {
            List<AbacPolicy> policies = abacPolicyService.listPolicies();
            if (policies == null || policies.isEmpty()) {
                return null;
            }

            // 按 priority 升序排序（null 值排最后）
            policies.sort(Comparator.comparing(
                p -> p.getPriority() != null ? p.getPriority() : Integer.MAX_VALUE));

            // 获取当前HTTP方法作为 action
            String httpMethod = getCurrentHttpMethod();

            for (AbacPolicy policy : policies) {
                if (policy.getEffect() == null) continue;

                boolean resourceMatch = matchesCondition(
                    policy.getResourceCondition(), requiredPermissions);
                boolean subjectMatch = matchesCondition(
                    policy.getSubjectCondition(), userPermissions);
                boolean actionMatch = matchesCondition(
                    policy.getActionCondition(), httpMethod);

                if (resourceMatch && subjectMatch && actionMatch) {
                    log.info("ABAC policy matched: policyId={}, policyName={}, effect={}, " +
                        "userId={}, resource={}, action={}",
                        policy.getPolicyId(), policy.getPolicyName(), policy.getEffect(),
                        userId, Arrays.toString(requiredPermissions), httpMethod);

                    if ("DENY".equalsIgnoreCase(policy.getEffect())) {
                        return "DENY";
                    }
                    if ("ALLOW".equalsIgnoreCase(policy.getEffect())) {
                        return "ALLOW";
                    }
                }
            }
        } catch (Exception e) {
            log.warn("ABAC policy evaluation failed, falling back to RBAC: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 检查条件是否匹配。
     * <ul>
     *   <li>null 或空字符串 → 匹配（通配）</li>
     *   <li>"*" → 匹配（通配）</li>
     *   <li>逗号分隔的多值 → 任一匹配即匹配（OR语义）</li>
     *   <li>单个值 → 与目标值做包含匹配（大小写不敏感）</li>
     * </ul>
     */
    private boolean matchesCondition(String condition, String[] targetValues) {
        if (condition == null || condition.isEmpty() || "*".equals(condition.trim())) {
            return true;
        }
        if (targetValues == null || targetValues.length == 0) {
            return false;
        }
        String[] condParts = condition.split(",");
        for (String condPart : condParts) {
            String trimmed = condPart.trim();
            for (String target : targetValues) {
                if (target != null && target.toLowerCase().contains(trimmed.toLowerCase())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 检查条件是否匹配单个字符串。
     */
    private boolean matchesCondition(String condition, Set<String> targetValues) {
        return matchesCondition(condition,
            targetValues != null ? targetValues.toArray(new String[0]) : null);
    }

    /**
     * 检查条件是否匹配单个字符串。
     */
    private boolean matchesCondition(String condition, String targetValue) {
        if (condition == null || condition.isEmpty() || "*".equals(condition.trim())) {
            return true;
        }
        if (targetValue == null) {
            return false;
        }
        String[] condParts = condition.split(",");
        for (String condPart : condParts) {
            if (targetValue.equalsIgnoreCase(condPart.trim())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 从当前请求上下文获取HTTP方法。
     */
    private String getCurrentHttpMethod() {
        try {
            ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                return attrs.getRequest().getMethod();
            }
        } catch (Exception e) {
            // 非Web上下文，忽略
        }
        return "UNKNOWN";
    }
}
