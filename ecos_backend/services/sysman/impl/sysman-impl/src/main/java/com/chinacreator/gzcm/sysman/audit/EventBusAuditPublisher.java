package com.chinacreator.gzcm.sysman.audit;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

/**
 * 审计事件的可选投递口（详细设计-01 F01-08 / §2.9.4 审计留痕）。
 *
 * <p><b>为什么按类名解析而不是 {@code ObjectProvider&lt;Object&gt;}</b>：sysman-impl 编译期不依赖
 * runtime-event（避免把 Kafka 拖进本模块），但 Spring 的 {@code ObjectProvider<T>} 按<b>声明类型</b>
 * 解析候选——声明成 {@code ObjectProvider<Object>} 时容器按 {@code Object} 找候选，命中全部 Bean：
 * 多枚 {@code @Primary} 在场即抛 {@code NoUniqueBeanDefinitionException}；单枚 {@code @Primary}
 * 在场则返回一个<b>无关 Bean</b>（反射调 {@code publish} 必失败）。两种结局都让审计投递静默失效，
 * 而既有单测直接 mock provider，永远造不出容器解析失败（#147AQ 实测：每次超管旁路都打
 * 「No qualifying bean of type 'java.lang.Object' … more than one 'primary' bean」）。
 * 故改为把真实接口的<b>类名</b>交给容器按类型取候选；类缺席（裁剪档位/未装配）即视为无总线。</p>
 *
 * <p>本类保证<b>绝不向业务路径抛异常</b>：总线缺席、类名解析失败、投递异常一律只记日志。
 * 审计是旁路留痕，不能反过来阻断被审计的业务动作。</p>
 *
 * @author ECOS sysman #147AQ
 */
@Component
public class EventBusAuditPublisher {

    /** runtime-event 的事件总线接口（按类名解析，避免编译期硬依赖）。 */
    static final String DEFAULT_EVENT_BUS_CLASS = "com.chinacreator.gzcm.runtime.eventbus.EventBusService";

    /** 审计事件 topic（与既有三处投递同一 topic）。 */
    static final String AUDIT_TOPIC = "ecos.audit";

    private static final Logger log = LoggerFactory.getLogger(EventBusAuditPublisher.class);

    private final ApplicationContext applicationContext;
    private final String eventBusClassName;

    /**
     * 容器装配用构造器。<b>必须显式 {@code @Autowired}</b>：本类有一个包级测试构造器，
     * 不标注时 Spring 会回去找缺省构造器并直接失败（#147AQ 活体腿实测：启动即
     * {@code No default constructor found}）——单测手写 new 永远看不到这一层。
     */
    @Autowired
    public EventBusAuditPublisher(ApplicationContext applicationContext) {
        this(applicationContext, DEFAULT_EVENT_BUS_CLASS);
    }

    /** 测试用：把解析目标换成本模块可见的替身接口；生产装配恒走单参构造器。 */
    EventBusAuditPublisher(ApplicationContext applicationContext, String eventBusClassName) {
        this.applicationContext = applicationContext;
        this.eventBusClassName = eventBusClassName;
    }

    /**
     * 投递一条审计事件到 {@code ecos.audit}。
     *
     * @param payload 审计负载（须含 {@code eventType}）
     */
    public void publish(Map<String, Object> payload) {
        try {
            Object eventBus = resolveEventBus();
            if (eventBus == null) {
                log.debug("[audit] EventBusService 不可用，跳过审计事件: eventType={}", payload.get("eventType"));
                return;
            }
            eventBus.getClass().getMethod("publish", String.class, Object.class)
                    .invoke(eventBus, AUDIT_TOPIC, payload);
        } catch (Exception e) {
            log.warn("[audit] 审计事件投递失败（不阻断业务）: eventType={}, {}",
                    payload.get("eventType"), e.getMessage());
        }
    }

    private Object resolveEventBus() {
        Class<?> busType;
        try {
            busType = Class.forName(eventBusClassName);
        } catch (ClassNotFoundException e) {
            return null;
        }
        return applicationContext.getBeanProvider(busType).getIfAvailable();
    }
}
