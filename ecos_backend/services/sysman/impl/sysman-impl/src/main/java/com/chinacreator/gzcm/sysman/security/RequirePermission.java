package com.chinacreator.gzcm.sysman.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 方法级权限校验注解。
 * <p>
 * 标注在 Controller 方法上，AOP 切面会在方法执行前检查当前用户
 * 是否持有指定权限码。权限码格式为 {@code resource:action}，
 * 如 {@code "user:READ"}、{@code "task:WRITE"}。
 *
 * <p>使用示例：
 * <pre>{@code
 * @GetMapping
 * @RequirePermission("user:READ")
 * public ApiResponse<?> list() { ... }
 *
 * @PostMapping
 * @RequirePermission({"user:WRITE", "user:DELETE"})
 * public ApiResponse<?> create() { ... }
 * }</pre>
 *
 * @author ECOS S6
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface RequirePermission {

    /**
     * 所需权限码（支持多个，满足其一即可）。
     * <p>
     * 当指定多个权限码时，用户只需持有其中任意一个权限即可通过校验。
     *
     * @return 权限码数组
     */
    String[] value() default {};
}
