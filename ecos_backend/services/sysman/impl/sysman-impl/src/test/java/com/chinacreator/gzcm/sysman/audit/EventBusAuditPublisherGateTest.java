package com.chinacreator.gzcm.sysman.audit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * 审计事件投递口的解析门（#147AQ）。
 *
 * <p><b>它证明什么</b>：本模块原先把可选事件总线声明成 {@code ObjectProvider<Object>}——
 * Spring 按<b>声明类型</b>解析候选，于是按 {@code Object} 去找 Bean：多枚 {@code @Primary} 在场即抛
 * {@code NoUniqueBeanDefinitionException}，单枚 {@code @Primary} 在场则把**无关 Bean** 当总线返回
 * （反射调 {@code publish} 必失败）。两种结局都让「超管旁路留痕 / 安全策略审计 / 接口引用审计」
 * 在<b>生产路径上静默丢失</b>，而三处单测都直接 mock provider，永远造不出容器解析失败，
 * 于是长期全绿。本类用<b>真实容器</b>钉住正确写法，并把旧写法的失败机制作为对照断言留在同一上下文里。</p>
 *
 * <p><b>它不证明什么（如实）</b>：测试用的是本模块可见的替身接口，不是 runtime-event 的真实类——
 * 真实类名能否解析由活体腿（gateway 启动后超管旁路不再打「审计发布失败」WARN、审计事件真的投递）
 * 与生产常量钉值共同保证。</p>
 *
 * @author ECOS sysman #147AQ
 */
class EventBusAuditPublisherGateTest {

    /** 替身总线：与 runtime-event 的 EventBusService 同签名（publish(String, Object)）。 */
    interface FixtureBus {
        void publish(String topic, Object payload);
    }

    /** 记录型替身。 */
    static class RecordingBus implements FixtureBus {
        final List<String> topics = new ArrayList<>();
        final List<Object> payloads = new ArrayList<>();

        @Override
        public void publish(String topic, Object payload) {
            topics.add(topic);
            payloads.add(payload);
        }
    }

    /** 投递即抛（模拟 Kafka 不可用）。 */
    static class ExplodingBus implements FixtureBus {
        @Override
        public void publish(String topic, Object payload) {
            throw new IllegalStateException("kafka down");
        }
    }

    /** 与总线无关的诱饵类型（只为制造「多枚 @Primary」）。 */
    static class DecoyPrimaryA {
    }

    /** 同上。 */
    static class DecoyPrimaryB {
    }

    /**
     * 生产装配形态：容器里同时存在多枚 {@code @Primary} 与一枚总线实现。
     * 旧写法在此必然解析失败——这正是缺陷的机制，不是测试人为加难度。
     */
    private AnnotationConfigApplicationContext productionShapedContext(Class<? extends FixtureBus> busImpl) {
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
        ctx.registerBean("decoyPrimaryA", DecoyPrimaryA.class, bd -> bd.setPrimary(true));
        ctx.registerBean("decoyPrimaryB", DecoyPrimaryB.class, bd -> bd.setPrimary(true));
        ctx.registerBean("fixtureBus", FixtureBus.class, () -> newBus(busImpl));
        ctx.refresh();
        return ctx;
    }

    private static FixtureBus newBus(Class<? extends FixtureBus> impl) {
        try {
            return impl.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("构造替身总线失败: " + impl.getName(), e);
        }
    }

    private static Map<String, Object> payload(String eventType) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventType", eventType);
        payload.put("source", "gate-test");
        return payload;
    }

    @Test
    @DisplayName("生产形态（多枚 @Primary）下按接口类名解析得到总线，投递落到 ecos.audit")
    void resolvesBusByInterfaceNameInProductionShape() {
        try (AnnotationConfigApplicationContext ctx = productionShapedContext(RecordingBus.class)) {
            RecordingBus bus = (RecordingBus) ctx.getBean("fixtureBus");
            EventBusAuditPublisher publisher = new EventBusAuditPublisher(ctx, FixtureBus.class.getName());

            Map<String, Object> audit = payload("super_admin_bypass");
            publisher.publish(audit);

            assertEquals(List.of(EventBusAuditPublisher.AUDIT_TOPIC), bus.topics);
            assertEquals(List.of(audit), bus.payloads);
        }
    }

    @Test
    @DisplayName("对照断言：同一上下文下旧写法 ObjectProvider<Object> 必然解析失败（缺陷机制）")
    void objectTypedProviderFailsOnSameContext() {
        try (AnnotationConfigApplicationContext ctx = productionShapedContext(RecordingBus.class)) {
            assertThrows(NoUniqueBeanDefinitionException.class,
                    () -> ctx.getBeanProvider(Object.class).getIfAvailable(),
                    "按 Object 取候选在多枚 @Primary 下必须失败——旧写法「静默丢审计」的根因即此");
        }
    }

    @Test
    @DisplayName("容器里没有总线：不投递、不抛（裁剪档位属预期缺席）")
    void absentBusIsNoOp() {
        try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext()) {
            ctx.registerBean("decoyPrimaryA", DecoyPrimaryA.class, bd -> bd.setPrimary(true));
            ctx.refresh();
            EventBusAuditPublisher publisher = new EventBusAuditPublisher(ctx, FixtureBus.class.getName());

            assertDoesNotThrow(() -> publisher.publish(payload("security_policy_create")));
            assertNull(ctx.getBeanProvider(FixtureBus.class).getIfAvailable(), "上下文本就没有总线 Bean");
        }
    }

    @Test
    @DisplayName("类名不可解析（未装配 runtime-event）：不抛")
    void unresolvableClassNameIsNoOp() {
        try (AnnotationConfigApplicationContext ctx = productionShapedContext(RecordingBus.class)) {
            EventBusAuditPublisher publisher = new EventBusAuditPublisher(ctx, "no.such.EventBusService");

            assertDoesNotThrow(() -> publisher.publish(payload("interface_ref_create")));
            assertTrue(((RecordingBus) ctx.getBean("fixtureBus")).topics.isEmpty(), "找不到总线类型时不得误投给别的 Bean");
        }
    }

    @Test
    @DisplayName("总线投递抛异常：投递口吞掉不发散（审计不阻塞业务路径）")
    void publishFailureIsSwallowed() {
        try (AnnotationConfigApplicationContext ctx = productionShapedContext(ExplodingBus.class)) {
            EventBusAuditPublisher publisher = new EventBusAuditPublisher(ctx, FixtureBus.class.getName());

            assertDoesNotThrow(() -> publisher.publish(payload("super_admin_bypass")),
                    "审计是旁路留痕，投递失败绝不允许冒泡到业务动作");
        }
    }

    @Test
    @DisplayName("容器装配：本组件注册进容器即可实例化（构造器歧义只有容器能照出来）")
    void containerCanInstantiateTheComponent() {
        try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext()) {
            ctx.register(EventBusAuditPublisher.class);
            ctx.refresh();

            assertNotNull(ctx.getBean(EventBusAuditPublisher.class),
                    "有测试构造器在场时必须显式 @Autowired，否则 Spring 找缺省构造器直接失败（活体腿实测过）");
        }
    }

    @Test
    @DisplayName("生产常量钉值：解析目标 = runtime-event 的 EventBusService，topic = ecos.audit")
    void productionConstantsArePinned() {
        assertEquals("com.chinacreator.gzcm.runtime.eventbus.EventBusService",
                EventBusAuditPublisher.DEFAULT_EVENT_BUS_CLASS,
                "解析目标改名后必须同步本门（与 runtime-event 接口 FQN 保持一致）");
        assertEquals("ecos.audit", EventBusAuditPublisher.AUDIT_TOPIC);
    }

    @Test
    @DisplayName("棘轮：本模块 main 源码禁止再出现 ObjectProvider<Object>（注释除外）")
    void noObjectTypedProviderInMainSources() throws IOException {
        Path root = Paths.get("src/main/java");
        assertTrue(Files.isDirectory(root), "surefire 工作目录应为模块根，实际: " + root.toAbsolutePath());

        List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".java"))::iterator) {
                String code = stripComments(Files.readString(file, StandardCharsets.UTF_8));
                if (code.contains("ObjectProvider<Object>")) {
                    offenders.add(file.toString());
                }
            }
        }
        assertEquals(List.of(), offenders,
                "ObjectProvider<Object> 按声明类型 Object 解析候选，必然失败或误取无关 Bean（#147AQ）");
    }

    /** 去掉块注释与整行行注释——只审代码，不审说明文字。 */
    private static String stripComments(String source) {
        String withoutBlock = source.replaceAll("(?s)/\\*.*?\\*/", "");
        StringBuilder sb = new StringBuilder();
        for (String line : withoutBlock.split("\n")) {
            if (!line.trim().startsWith("//")) {
                sb.append(line).append('\n');
            }
        }
        return sb.toString();
    }
}
