package com.chinacreator.gzcm.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * S6-1.2 RequirePermission 注解 — 标记需要权限校验的方法。
 * 配合 {@code PermissionAspect} 自动拦截并校验当前用户是否拥有指定权限。
 *
 * <p>位于 common-api 模块，可供 gateway 等模块共享引用。</p>
 *
 * <pre>
 *   @RequirePermission("databridge:task:create")
 *   public ApiResponse<?> createTask(...) { ... }
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface RequirePermission {

    /**
     * 权限描述（可选，用于日志记录，默认为空字符串）。
     */
    String value() default "";

    /**
     * 所需权限码（如 "databridge:task:create"）。
     */
    String permission() default "";
}
