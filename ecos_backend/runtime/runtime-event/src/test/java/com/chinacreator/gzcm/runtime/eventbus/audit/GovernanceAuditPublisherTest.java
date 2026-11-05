package com.chinacreator.gzcm.runtime.eventbus.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import com.chinacreator.gzcm.common.event.KafkaTopics;
import com.chinacreator.gzcm.runtime.eventbus.EventBusService;

/**
 * 治理审计统一发布口（#147BV · §2.9.9 G5「其余发布域的 {@code git_commit_sha}」）。
 *
 * <p><b>钉的是什么</b>：ai / cognitive 两个发布域此前零治理审计。本门把事件形状钉成
 * 「{@code id} 必在（消费端缺 id 整条丢弃，#147BB 教训）+ 治理列随行 + 基础键压过治理列同名键」，
 * 并把「通道缺失/发布异常不抛、返回 false + 本地兜底日志」的失败语义固定下来——
 * 归档腿跑在业务已成功之后，把已成功的归档翻成失败只会制造「业务回滚但 Git 已有提交」的错账。</p>
 *
 * <p><b>变异自证</b>：删掉 {@code event.put("id", …)} ⇒ 第一例红（缺主键）；
 * 把基础键挪到 {@code putAll(governanceColumns)} 之前 ⇒ 压键反证例红；
 * 把 publish 失败改回抛出 ⇒ 失败面两例红。</p>
 */
class GovernanceAuditPublisherTest {

    /** 捕获式事件总线（不连 Kafka；isKafkaAvailable 可配）。 */
    private static final class CapturingBus implements EventBusService {
        final List<String> topics = new ArrayList<>();
        final List<Object> payloads = new ArrayList<>();
        boolean kafkaAvailable;
        boolean throwOnPublish;

        @Override
        public void publish(String topic, Object payload) {
            if (throwOnPublish) {
                throw new IllegalStateException("simulated broker failure");
            }
            topics.add(topic);
            payloads.add(payload);
        }

        @Override
        public void subscribe(String topic, Class<?> payloadType, Consumer<Object> handler) {
            // 本门不消费
        }

        @Override
        public boolean isKafkaAvailable() {
            return kafkaAvailable;
        }
    }

    private static ObjectProvider<EventBusService> provider(EventBusService bus) {
        return new ObjectProvider<>() {
            @Override
            public EventBusService getObject() {
                return bus;
            }

            @Override
            public EventBusService getObject(Object... args) {
                return bus;
            }

            @Override
            public EventBusService getIfAvailable() {
                return bus;
            }

            @Override
            public EventBusService getIfUnique() {
                return bus;
            }
        };
    }

    @Test
    @DisplayName("事件形状：id 必在 + 治理列随行 + 基础键压过治理列同名键")
    void publishesGovernanceEventWithStableShape() {
        CapturingBus bus = new CapturingBus();
        GovernanceAuditPublisher publisher = new GovernanceAuditPublisher(provider(bus));

        Map<String, Object> governance = new LinkedHashMap<>();
        governance.put("git_repository", "agent-collab");
        governance.put("git_commit_sha", "deadbeef");
        governance.put("content_hash", "abc123");
        governance.put("object_type", "agent");
        governance.put("action", "CALLER-TRIED-TO-REWRITE"); // 压键反证：须被基础键覆盖
        governance.put("tenantId", "CALLER-TENANT");

        boolean sent = publisher.publish("agent.archive", "ai-agent", "SUCCESS",
                governance, Map.of("agentId", "a-1"));

        assertTrue(sent);
        assertEquals(1, bus.payloads.size());
        assertEquals(KafkaTopics.AUDIT, bus.topics.get(0));

        @SuppressWarnings("unchecked")
        Map<String, Object> event = (Map<String, Object>) bus.payloads.get(0);
        assertNotNull(event.get("id"), "id 必在：消费端以 id 作主键判重，缺则整条丢弃");
        assertTrue(String.valueOf(event.get("id")).matches("\\d+"), "id 须为数字串（与消费端派生口径一致）");
        assertEquals("agent-collab", event.get("git_repository"));
        assertEquals("deadbeef", event.get("git_commit_sha"));
        assertEquals("abc123", event.get("content_hash"));
        assertEquals("agent", event.get("object_type"));
        assertEquals("agent.archive", event.get("action"), "治理列不得改写 action");
        assertEquals("ai-agent", event.get("resource"));
        assertEquals("SUCCESS", event.get("result"));
        assertNotNull(event.get("eventId"));
        assertNotNull(event.get("timestamp"));
        assertEquals(Map.of("agentId", "a-1"), event.get("attr"));
    }

    @Test
    @DisplayName("空治理列/空 attr：事件仍成形，attr 落空对象（禁 null 载荷）")
    void blankGovernanceAndAttrAreTolerated() {
        CapturingBus bus = new CapturingBus();
        GovernanceAuditPublisher publisher = new GovernanceAuditPublisher(provider(bus));

        assertTrue(publisher.publish("model.archive", "cognitive-model", null, null, null));

        @SuppressWarnings("unchecked")
        Map<String, Object> event = (Map<String, Object>) bus.payloads.get(0);
        assertEquals(Map.of(), event.get("attr"));
        assertFalse(event.containsKey("result"), "result 为 null 时应省略键（不写 null 值）");
    }

    @Test
    @DisplayName("通道缺失：返回 false 且不抛（归档后补证据不得翻转业务结果）")
    void missingBusReturnsFalseWithoutThrowing() {
        GovernanceAuditPublisher publisher = new GovernanceAuditPublisher(provider(null));
        assertFalse(publisher.publish("agent.archive", "ai-agent", "SUCCESS", Map.of(), null));
    }

    @Test
    @DisplayName("发布异常：返回 false 且不抛（与裁决类 fail-closed 语义有意区分）")
    void publishFailureReturnsFalseWithoutThrowing() {
        CapturingBus bus = new CapturingBus();
        bus.throwOnPublish = true;
        GovernanceAuditPublisher publisher = new GovernanceAuditPublisher(provider(bus));

        assertFalse(publisher.publish("agent.archive", "ai-agent", "SUCCESS", Map.of(), null));
    }

    @Test
    @DisplayName("内存总线降级：kafka 不可用仍视为已投递（不误报失败）")
    void memoryBusCountsAsSent() {
        CapturingBus bus = new CapturingBus();
        bus.kafkaAvailable = false;
        GovernanceAuditPublisher publisher = new GovernanceAuditPublisher(provider(bus));

        assertTrue(publisher.publish("agent.archive", "ai-agent", "SUCCESS", Map.of(), null));
        assertEquals(1, bus.payloads.size());
    }
}
