package com.chinacreator.gzcm.gateway.controller;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * H10-T2 审计锚点样板单（gateway/SysConfigController）：
 * 真实主体归属 + 经 runtime-event 发 {@code KafkaTopics.AUDIT}。
 *
 * <p>全部用例经可观测差分自证「承重」：主体变了 operator 必须跟着变（排除硬编码）、
 * 事件总线可达时必须收到 ecos.audit（排除假外发）、不可用时必须返回 false 且不抛
 * （排除静默吞异常）。</p>
 */
class SysConfigAuditEgressTest {

    /** 反射查找用不到，测试实例只覆盖 resolveEventBus 接缝。 */
    private static class TestableController extends SysConfigController {
        private final Object stubBus;

        TestableController(Object stubBus) {
            super(null, null);
            this.stubBus = stubBus;
        }

        @Override
        Object resolveEventBus() {
            return stubBus;
        }
    }

    /** 记录最后一次 publish 的假总线，签名须与 EventBusService.publish 一致。 */
    public static class RecordingBus {
        String topic;
        Object payload;
        int calls;

        public void publish(String topic, Object payload) {
            this.topic = topic;
            this.payload = payload;
            this.calls++;
        }
    }

    @BeforeEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticate(String userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null,
                        List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }

    @Test
    void authenticatedSubjectBecomesAuditOperator() {
        authenticate("u-42");
        assertEquals("u-42", SysConfigController.resolveOperator());
    }

    @Test
    void missingAuthenticationYieldsAnonymousNotSystem() {
        assertEquals(SysConfigController.ANONYMOUS_OPERATOR, SysConfigController.resolveOperator());
        assertNotEquals("system", SysConfigController.resolveOperator(),
                "不得把无法归属的写操作伪造成可信主体 system");
    }

    @Test
    void operatorFollowsSubjectInsteadOfBeingHardcoded() {
        authenticate("u-111");
        String first = SysConfigController.resolveOperator();
        SecurityContextHolder.clearContext();
        authenticate("u-222");
        String second = SysConfigController.resolveOperator();
        assertEquals("u-111", first);
        assertEquals("u-222", second);
        assertNotEquals(first, second, "operator 恒等 ⇒ 说明仍是硬编码常量");
    }

    @Test
    void payloadCarriesKeyAndValueChangeAndRealOperator() {
        authenticate("u-77");
        Map<String, Object> payload = SysConfigController.buildAuditPayload(
                SysConfigController.resolveOperator(), "dw.olap.engine", "doris", "clickhouse");
        assertEquals("u-77", payload.get("operator"));
        assertEquals("dw.olap.engine", payload.get("aggregateId"));
        assertEquals("doris", payload.get("oldValue"));
        assertEquals("clickhouse", payload.get("newValue"));
        assertEquals("sys_config_reset", payload.get("eventType"));
        assertNotNull(payload.get("timestamp"));
    }

    @Test
    void nullOldValueIsSerializedAsEmptyString() {
        Map<String, Object> payload = SysConfigController.buildAuditPayload("u-77", "k", null, "v");
        assertEquals("", payload.get("oldValue"));
    }

    @Test
    void egressDeliversPayloadToEcosAuditTopic() {
        RecordingBus bus = new RecordingBus();
        TestableController controller = new TestableController(bus);
        authenticate("u-42");
        Map<String, Object> payload = SysConfigController.buildAuditPayload(
                SysConfigController.resolveOperator(), "feature.toggle", "on", "off");

        assertTrue(controller.publishAuditEvent(payload), "假总线可达却报告未送达 ⇒ 出口未接线");
        assertEquals("ecos.audit", bus.topic);
        assertEquals(1, bus.calls);
        assertSame(payload, bus.payload);
        assertEquals("u-42", ((Map<?, ?>) bus.payload).get("operator"));
    }

    @Test
    void unavailableEventBusReportsFalseWithoutThrowing() {
        TestableController controller = new TestableController(null);
        Map<String, Object> payload = SysConfigController.buildAuditPayload(
                "u-42", "feature.toggle", "on", "off");
        assertFalse(controller.publishAuditEvent(payload), "总线缺失时必须如实返回未送达");
        assertNull(controller.resolveEventBus(), "applicationContext 缺失时不得凭空造出总线");
    }
}
