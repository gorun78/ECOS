package com.chinacreator.gzcm.sysman.audit.crypto;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * ECOS Phase 1 P1-2: Cryptographic Audit — 加密审计注解。
 *
 * <p>标记在需要记录到加密审计账本的方法上。由 {@link CryptographicAuditAspect}
 * 拦截，将方法返回结果序列化为 JSON payload，计算 SHA-256 哈希链后存入
 * {@link CryptoAuditService}。
 *
 * <p>使用示例：
 * <pre>{@code
 *   @CryptographicAudit(eventType = "EXPORT", resource = "#id", action = "export_dataset")
 *   public Dataset export(@PathVariable String id) { ... }
 * }</pre>
 *
 * <p>{@code resource} 和 {@code action} 支持 SpEL 表达式，
 * 可引用方法参数名称（需在编译时保留参数名，Java 8+ {@code -parameters}）。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface CryptographicAudit {

    /** 事件类型，必填。如 CREATE, UPDATE, DELETE, EXPORT, ACCESS, CONFIG_CHANGE */
    String eventType();

    /** 目标资源标识，支持 SpEL 表达式。如 {@code #id}、{@code #dto.name} */
    String resource() default "";

    /** 操作动作描述，支持 SpEL 表达式。如 {@code export_dataset} */
    String action() default "";
}
