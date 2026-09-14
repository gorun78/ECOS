package com.chinacreator.gzcm.sysman.datapermission.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 字段级脱敏注解。
 *
 * 示例：
 * <pre>
 * &#64;Masking(strategy = "phone")
 * private String mobile;
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD})
public @interface Masking {

    /**
     * 脱敏策略标识：
     * phone / idCard / email / bankCard / custom 等
     */
    String strategy();

    /**
     * 可见前缀字符数（部分策略使用）
     */
    int prefixVisible() default 0;

    /**
     * 可见后缀字符数（部分策略使用）
     */
    int suffixVisible() default 0;
}


