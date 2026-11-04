package com.chinacreator.gzcm.engine.kb.support;

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

import com.chinacreator.gzcm.common.event.KafkaTopics;
import com.chinacreator.gzcm.runtime.eventbus.EventBusService;

/**
 * kb 审计发布器的落库前置门（#147BB）。
 *
 * <p><b>为什么必须有这一门</b>：消费端 {@code AuditKafkaConsumer} 以 <b>{@code id} 作主键</b>判重，
 * 缺 {@code id} 的事件会打印「审计事件缺 id, 丢弃」后直接丢掉。本发布器原先只放 {@code eventId}
 * ⇒ kb 六个调用点（图谱同步／入库／文档 ingest／实例抽取／KG 映射／KG 同步）的审计证据
 * <b>全部没有落库</b>，而单测都只断言「调用了 publisher」，看不到消费端这一层（同 #147AQ 一族）。
 * ontology 侧在 #147M 已修过同一缺陷，kb 未跟上。</p>
 *
 * <p>另钉两条：治理列（{@code git_commit_sha} 等）能随事件带到消费端白名单提取；
 * 基础键（action/resource）不被治理列覆盖——否则调用方传错键就能改写审计归类。</p>
 *
 * @author ECOS KB #147BB
 */
class KbAuditEventPublisherTest {

    /** 捕获式事件总线（Kafka 在线）。 */
    static class CapturingBus implements EventBusService {
        final List<String> topics = new ArrayList<>();
        final List<Object> payloads = new ArrayList<>();

        @Override
        public void publish(String topic, Object payload) {
            topics.add(topic);
            payloads.add(payload);
        }

        @Override
        public void subscribe(String topic, Class<?> payloadType, Consumer<Object> handler) {
            // no-op
        }

        @Override
        public boolean isKafkaAvailable() {
            return true;
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> published(CapturingBus bus) {
        assertEquals(1, bus.payloads.size(), "应恰好发布一条事件");
        assertEquals(KafkaTopics.AUDIT, bus.topics.get(0));
        return (Map<String, Object>) bus.payloads.get(0);
    }

    @Test
    @DisplayName("基础审计也带 id（消费端缺 id 会整条丢弃）")
    void basicAuditCarriesPrimaryId() {
        CapturingBus bus = new CapturingBus();
        new KbAuditEventPublisher(bus).audit("kg_sync_submit", Map.of("k", "v"), "kb-graph-sync");

        Map<String, Object> evt = published(bus);
        Object id = evt.get("id");
        assertNotNull(id, "缺 id ⇒ AuditKafkaConsumer 直接丢弃，审计证据落不了库");
        assertTrue(String.valueOf(id).matches("\\d{13,}"), "id 必须是数字串（落库主键）: " + id);
        assertEquals("kg_sync_submit", evt.get("action"));
        assertEquals("kb-graph-sync", evt.get("resource"));
        assertFalse(evt.containsKey("result"), "未给 result 时不得伪造");
    }

    @Test
    @DisplayName("治理列随事件投递，且不得覆盖 action/resource")
    void governanceColumnsAreCarriedWithoutOverridingBaseKeys() {
        CapturingBus bus = new CapturingBus();
        Map<String, Object> columns = new LinkedHashMap<>();
        columns.put("git_repository", "kb-asset-tree");
        columns.put("git_commit_sha", "c0ffee123");
        columns.put("object_type", "rule_version");
        columns.put("action", "伪造的归类");   // 治理列里的同名键不得生效
        columns.put("certificate", "丢弃我");  // 不在契约白名单：到消费端会被筛掉，但这里不臆造列

        new KbAuditEventPublisher(bus)
                .audit("rule.version.publish", Map.of("ruleId", "r-1"), "kb-rule-version",
                        "SUCCESS", columns);

        Map<String, Object> evt = published(bus);
        assertNotNull(evt.get("id"));
        assertEquals("kb-asset-tree", evt.get("git_repository"));
        assertEquals("c0ffee123", evt.get("git_commit_sha"));
        assertEquals("rule_version", evt.get("object_type"));
        assertEquals("SUCCESS", evt.get("result"));
        assertEquals("rule.version.publish", evt.get("action"), "基础键必须压过治理列的同名键");
        assertEquals("kb-rule-version", evt.get("resource"));
    }
}
