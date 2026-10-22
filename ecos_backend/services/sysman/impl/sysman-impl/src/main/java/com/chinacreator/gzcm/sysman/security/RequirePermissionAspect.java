package com.chinacreator.gzcm.sysman.security;

import com.chinacreator.gzcm.common.exception.ForbiddenException;
import com.chinacreator.gzcm.common.exception.UnauthorizedException;
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

import java.util.ArrayList;
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
 *   <li>若未认证，抛 {@link UnauthorizedException}（HTTP 401）</li>
 *   <li>获取用户持有的权限集合（GrantedAuthority）</li>
 *   <li>检查是否包含任一所需权限码</li>
 *   <li>若无权限，抛 {@link ForbiddenException}（HTTP 403）</li>
 * </ol>
 *
 * <h3>#142/B4-3e-35/36：三枚拒绝腿由「返回信封」改为「抛异常」的理由</h3>
 * <p>旧形态 {@code return ApiResponse.unauthorized(...)}／{@code forbidden(...)} 有两重毛病，
 * 一枚都足够否决：</p>
 * <ol>
 *   <li><b>状态行恒 200</b>（差距 D-B4-59）——{@code ApiResponse} 只描述 body、没有任何 HTTP
 *       能力，401/403 的拒绝语义只活在信封 {@code code} 里，只看状态行的中间层（网关、重试、
 *       告警、Playwright 用例）把「无权限」读成「成功」；</li>
 *   <li><b>与返回类型强耦合，是定时炸弹</b>——{@code @Around} 的返回值由 Spring 按<b>被拦截方法
 *       的声明类型</b> cast。本切面当前只圈 gateway {@code TaskController}＋sysman
 *       {@code ConfigController}，其被拦截方法现均声明裸 {@code ApiResponse}，故<b>今天不炸</b>；
 *       但同族收口批正在把端点逐个折成 {@code ResponseEntity<ApiResponse<T>>}（本仓已折叠
 *       {@code NavTaxon}/{@code KbProfile}/{@code Decision} 等），一旦这两枚中任一方法被折叠，
 *       切面返回的 {@code ApiResponse} 会在该调用点抛 ClassCastException ⇒ 拒绝动作把请求变成
 *       500，比原来的假 200 更糟；反向「切面改返 {@code ResponseEntity}」也不可行——声明仍为
 *       {@code ApiResponse} 的方法会把整个 {@code ResponseEntity} 当 body 序列化，属假修。</li>
 * </ol>
 * <p><b>改抛</b>是唯一与返回类型解耦的修法：异常沿调用栈上抛，状态行由既有注解承载面写定
 * （gateway {@code GlobalExceptionHandler} 的 {@code @ExceptionHandler(UnauthorizedException)}
 * ＋{@code @ResponseStatus(UNAUTHORIZED)}／{@code ForbiddenException}＋{@code FORBIDDEN}，
 * sysman-boot {@code GlobalExceptionHandler} 同型两枚），与 D-B4-69 已登记的「注解承载态」
 * 口径一致。信封侧顺带补齐机读码（gateway 腿走 {@code SystemErrorCode.ECOS_SYS_401/403}），
 * 消息文本与旧形态逐字相同 ⇒ 消费方读到的 {@code message} 不变。</p>
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
     * @return 原方法返回值（仅放行腿：RBAC 命中、ABAC ALLOW、或注解值为空的容错腿）
     * @throws UnauthorizedException 未认证（HTTP 401 由 handler 注解承载面写定）
     * @throws ForbiddenException 权限不足或 ABAC DENY 命中（HTTP 403 同上）
     * @throws Throwable 如果业务方法本身抛出异常
     */
    @Around("@annotation(com.chinacreator.gzcm.sysman.security.RequirePermission)")
    public Object checkPermission(ProceedingJoinPoint joinPoint) throws Throwable {
        // 1. 检查认证状态
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            log.warn("RequirePermission check failed: not authenticated, method={}",
                joinPoint.getSignature().toShortString());
            throw new UnauthorizedException("未登录或Token已过期");
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
            throw new ForbiddenException("访问被安全策略拒绝");
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
        throw new ForbiddenException(
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
            List<AbacPolicy> fetched = abacPolicyService.listPolicies();
            if (fetched == null || fetched.isEmpty()) {
                return null;
            }

            // 按 priority 升序排序（null 值排最后）。
            // **#142/B4-3e-36（新登记 D-B4-70）**：这里必须先复制一份再排，不得对协作者返回值
            // 原地 {@code sort}——本仓策略装载腿已有 {@code List.copyOf(...)} 与
            // {@code Collectors.toList()} 两种返回值形态，而不可变列表上的 {@code sort} 抛
            // {@code UnsupportedOperationException}，会被本方法外层的宽 catch 折成「ABAC 未评估」
            // ⇒ 所有策略（含 DENY）静默失效、无声回退 RBAC。缺陷方向是 fail-open，比报错更危险。
            List<AbacPolicy> policies = new ArrayList<>(fetched);
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
