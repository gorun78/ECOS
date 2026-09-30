package com.chinacreator.gzcm.runtime.eventbus;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link KafkaEventBusServiceImpl} 的 T-C 双投递用例（PMO-74 H2-T7 / H2-T8）。
 *
 * <p>全部用例<b>不需要 broker</b>：{@code KafkaTemplate} 用 mock 替身，投递语义只验
 * 「本地分发是否命中 / 记录头是否写对 / 回环是否跳过」三件事。真正起 Kafka 网络的只有
 * {@code subscribe} 那两条用例，容器在 {@link #tearDown()} 里显式停掉。
 */
@SuppressWarnings("unchecked")
class KafkaEventBusServiceImplTest {

    private static final String TOPIC = "ecos.test.pmo74";
    private static final String ORIGIN_HEADER = "ecos-event-origin";

    private final KafkaTemplate<String, String> template = mock(KafkaTemplate.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private KafkaEventBusServiceImpl bus;

    private KafkaEventBusServiceImpl newBus() {
        Map<String, Object> props = new HashMap<>();
        props.put("bootstrap.servers", "localhost:1");
        return new KafkaEventBusServiceImpl(template, mapper,
                new KafkaBusConsumerConfig.ConsumerBaseProps(props), "");
    }

    @AfterEach
    void tearDown() {
        if (bus != null) {
            bus.shutdownContainers();
        }
    }

    @Test
    @DisplayName("publish：同 JVM 订阅者拿到原始对象（非 JSON 串），且发出的记录带 origin 头")
    void publishDispatchesLocallyAndStampsOriginHeader() {
        bus = newBus();
        CopyOnWriteArrayList<Object> received = new CopyOnWriteArrayList<>();
        bus.subscribe(TOPIC, Map.class, received::add);
        when(template.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.<SendResult<String, String>>completedFuture(null));

        Map<String, Object> payload = Map.of("eventId", "e-1");
        bus.publish(TOPIC, payload);

        assertEquals(1, received.size(), "本地分发只应命中一次");
        assertSame(payload, received.get(0), "本地分发应传原对象，不做序列化往返");

        ArgumentCaptor<ProducerRecord<String, String>> captor =
                ArgumentCaptor.forClass(ProducerRecord.class);
        verify(template).send(captor.capture());
        ProducerRecord<String, String> sent = captor.getValue();
        assertEquals(TOPIC, sent.topic());
        assertTrue(sent.value().contains("e-1"), "发往 Kafka 的应是 JSON 载荷");
        byte[] origin = sent.headers().lastHeader(ORIGIN_HEADER).value();
        assertFalse(new String(origin, StandardCharsets.UTF_8).isEmpty(), "origin 头必须非空才能做回环排除");
    }

    @Test
    @DisplayName("publish：topic 为空 / payload 为 null 一律丢弃，既不本地分发也不发 Kafka")
    void publishDropsEmptyTopicAndNullPayload() {
        bus = newBus();
        AtomicInteger calls = new AtomicInteger();
        bus.subscribe(TOPIC, String.class, p -> calls.incrementAndGet());

        bus.publish(null, "x");
        bus.publish("", "x");
        bus.publish(TOPIC, null);

        assertEquals(0, calls.get());
        verify(template, never()).send(any(ProducerRecord.class));
    }

    @Test
    @DisplayName("publish：序列化失败时本地分发仍然完成（T-C 不退化为全丢的核心保证）")
    void localDeliverySurvivesSerializationFailure() {
        bus = newBus();
        CopyOnWriteArrayList<Object> received = new CopyOnWriteArrayList<>();
        bus.subscribe(TOPIC, Object.class, received::add);

        Object unserializable = new Object();
        bus.publish(TOPIC, unserializable);

        assertEquals(1, received.size(), "broker 侧失败不应带走同 JVM 的投递");
        assertSame(unserializable, received.get(0));
        verify(template, never()).send(any(ProducerRecord.class));
    }

    @Test
    @DisplayName("dispatch：origin 与本实例一致的回环记录被跳过")
    void dispatchSkipsSelfOriginRecord() {
        bus = newBus();
        AtomicInteger calls = new AtomicInteger();
        Consumer<Object> handler = p -> calls.incrementAndGet();

        bus.dispatch(TOPIC, Map.class, handler, record("{\"eventId\":\"e-2\"}", originIdOfBus()));

        assertEquals(0, calls.get(), "自身发出的记录已在 publish 侧分发过");
    }

    @Test
    @DisplayName("dispatch：外部 origin / 无 origin 老消息照常投递，反序列化为目标类型")
    void dispatchDeliversForeignAndLegacyRecords() {
        bus = newBus();
        CopyOnWriteArrayList<Object> received = new CopyOnWriteArrayList<>();
        Consumer<Object> handler = received::add;

        bus.dispatch(TOPIC, Map.class, handler, record("{\"eventId\":\"e-3\"}", "another-jvm"));
        bus.dispatch(TOPIC, Map.class, handler, record("{\"eventId\":\"e-4\"}", null));

        assertEquals(2, received.size());
        assertEquals("e-3", ((Map<?, ?>) received.get(0)).get("eventId"));
        assertEquals("e-4", ((Map<?, ?>) received.get(1)).get("eventId"));
    }

    @Test
    @DisplayName("dispatch：payloadType 为 String 时透传原文；反序列化失败只跳过不抛")
    void dispatchPassesThroughRawStringAndSkipsBadJson() {
        bus = newBus();
        CopyOnWriteArrayList<Object> received = new CopyOnWriteArrayList<>();
        Consumer<Object> handler = received::add;

        bus.dispatch(TOPIC, String.class, handler, record("raw-text", null));
        bus.dispatch(TOPIC, Map.class, handler, record("{not-json", null));

        assertEquals(List.of("raw-text"), received, "坏 JSON 应被跳过，不得抛给消费线程");
    }

    @Test
    @DisplayName("dispatch：业务 handler 抛异常不外溢（消费位点继续推进）")
    void dispatchSwallowsHandlerException() {
        bus = newBus();
        Consumer<Object> boom = p -> {
            throw new IllegalStateException("boom");
        };
        bus.dispatch(TOPIC, Map.class, boom, record("{\"eventId\":\"e-5\"}", null));
    }

    /** 取本实例写进 Kafka 的 origin 值 — 不暴露内部字段，靠 publish 侧的捕获反推 */
    private String originIdOfBus() {
        when(template.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.<SendResult<String, String>>completedFuture(null));
        bus.publish("ecos.test.probe-for-origin", "probe");
        ArgumentCaptor<ProducerRecord<String, String>> captor =
                ArgumentCaptor.forClass(ProducerRecord.class);
        verify(template).send(captor.capture());
        return new String(captor.getValue().headers().lastHeader(ORIGIN_HEADER).value(),
                StandardCharsets.UTF_8);
    }

    private ConsumerRecord<String, String> record(String value, String origin) {
        ConsumerRecord<String, String> r = new ConsumerRecord<>(TOPIC, 0, 0L, null, value);
        if (origin != null) {
            r.headers().add(new RecordHeader(ORIGIN_HEADER, origin.getBytes(StandardCharsets.UTF_8)));
        }
        return r;
    }
}
