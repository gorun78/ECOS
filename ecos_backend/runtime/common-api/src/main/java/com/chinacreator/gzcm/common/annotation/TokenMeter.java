package com.chinacreator.gzcm.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * P2-13 TokenMeter 注解 — 标记需要自动记录 LLM Token 用量的方法。
 * 配合 {@code TokenMeterAspect} 自动调用 TokenAuditService.recordUsage()。
 *
 * <p>位于 common-api 模块，可供 gateway 和 hermes-engine 共享引用，避免循环依赖。</p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface TokenMeter {

    /**
     * 操作类型（可选，默认为 "llm_call"）。
     */
    String operation() default "llm_call";

    /**
     * 模型名称（可选，默认为 "hermes-agent"）。
     */
    String model() default "";
}
