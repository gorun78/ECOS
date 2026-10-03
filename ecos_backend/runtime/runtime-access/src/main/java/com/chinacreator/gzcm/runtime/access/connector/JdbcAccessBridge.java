package com.chinacreator.gzcm.runtime.access.connector;

import jakarta.annotation.PostConstruct;

import org.springframework.stereotype.Component;

/**
 * 全仓唯一 {@link JdbcConnector} 实例的持有桥。
 *
 * <p>问题：部分消费方（如 data-engine 的 {@code BaseJdbcAdapter} 及 7 个子类适配器）
 * 由反射 {@code Class#newInstance()} 构造、非 Spring 托管，无法直接构造器注入。
 * 本桥在 Spring 上下文里经构造器注入拿到唯一的 {@link JdbcConnector} Bean 并缓存为静态单例，
 * 供这些非 Bean 对象经 {@link #shared()} 取用——建连一律收敛 runtime-access（铁律 #4），
 * 业务侧不再自行 {@code new} 或被引 {@code java.sql.DriverManager}（ARCH-06）。
 *
 * <p>{@link JdbcConnector} 本身无实例注入态（仅静态 logger / mapper），故非 Spring 上下文
 * （单测直接 {@code new} 适配器）可用其无注入依赖的实例兜底，行为与 Bean 一致。
 */
@Component
public class JdbcAccessBridge {

    private static JdbcConnector holder;

    private final JdbcConnector jdbcConnector;

    public JdbcAccessBridge(JdbcConnector jdbcConnector) {
        this.jdbcConnector = jdbcConnector;
    }

    @PostConstruct
    void bind() {
        JdbcAccessBridge.holder = jdbcConnector;
    }

    /** 取用全仓唯一 {@link JdbcConnector}；Spring 上下文优先返回注入 Bean，否则回落无注入态实例。 */
    public static JdbcConnector shared() {
        if (holder != null) {
            return holder;
        }
        return new JdbcConnector();
    }
}
