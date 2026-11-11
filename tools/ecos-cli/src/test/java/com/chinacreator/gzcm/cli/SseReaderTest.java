package com.chinacreator.gzcm.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * SSE 读取门禁（CLI 生产化批 A 交付但此前无覆盖）：本地 HttpServer 起真流，
 * 钉住 ①三段解析 + 多行 data 拼接；②{@code Last-Event-ID} 透传；③{@code maxEvents} 截断；
 * ④非 2xx 返回 -1（调用方据此判 UNAVAILABLE，禁当空流成功）。
 */
class SseReaderTest {

    private HttpServer server;
    private String baseUrl;
    private final AtomicReference<String> lastEventIdHeader = new AtomicReference<>();

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/events", exchange -> {
            lastEventIdHeader.set(exchange.getRequestHeaders().getFirst("Last-Event-ID"));
            String body = "event: meta\nid: 1\ndata: {\"runId\":\"R1\"}\n\n"
                    + "event: step\nid: 2\ndata: line-a\ndata: line-b\n\n"
                    + "event: done\nid: 3\ndata: {}\n\n";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.createContext("/denied", exchange -> {
            byte[] bytes = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(403, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        // 属主端点降级形态：200 + JSON（能力位 channel=polling 是合法返回，不是流）
        server.createContext("/json200", exchange -> {
            byte[] bytes = "{\"code\":0,\"data\":{\"channel\":\"polling\"}}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        // 真 SSE 但零事件（合法空流：连接成功、确实没有事件）
        server.createContext("/empty-stream", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0);
            exchange.close();
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    @DisplayName("三段解析：event/id/data（多行 data 用换行拼接）")
    void parsesFrames() {
        SseReader reader = new SseReader(baseUrl, "trace-1", 5_000);
        List<SseReader.Event> events = new ArrayList<>();

        int count = reader.stream("/events", "tok", null, 10, events::add);

        assertEquals(3, count);
        assertEquals("meta", events.get(0).event());
        assertEquals("1", events.get(0).id());
        assertTrue(events.get(0).data().contains("R1"));
        assertEquals("step", events.get(1).event());
        assertEquals("line-a\nline-b", events.get(1).data(), "多行 data 必须按 SSE 规范用换行拼接");
        assertEquals("done", events.get(2).event());
        assertNull(lastEventIdHeader.get(), "未传 sinceEventId 时不得带 Last-Event-ID");
    }

    @Test
    @DisplayName("Last-Event-ID 透传（断点续传游标）")
    void forwardsLastEventId() {
        SseReader reader = new SseReader(baseUrl, "trace-2", 5_000);
        reader.stream("/events", "tok", "2", 10, e -> { });
        assertEquals("2", lastEventIdHeader.get());
    }

    @Test
    @DisplayName("maxEvents 截断：读满上限即停，不整流通读")
    void capsAtMaxEvents() {
        SseReader reader = new SseReader(baseUrl, "trace-3", 5_000);
        List<SseReader.Event> events = new ArrayList<>();
        int count = reader.stream("/events", null, null, 2, events::add);
        assertEquals(2, count);
        assertEquals(2, events.size());
    }

    @Test
    @DisplayName("非 2xx ⇒ -1（调用方据此报 UNAVAILABLE，禁当空流成功）")
    void non2xxReturnsMinusOne() {
        SseReader reader = new SseReader(baseUrl, "trace-4", 5_000);
        assertEquals(-1, reader.stream("/denied", "tok", null, 10, e -> { }));
    }

    @Test
    @DisplayName("200 + application/json ⇒ -1（状态码 2xx 不等于「是流」，否则空 events 会伪装成功）")
    void nonSseContentTypeIsNotSuccess() {
        SseReader reader = new SseReader(baseUrl, "trace-5", 5_000);
        List<SseReader.Event> events = new ArrayList<>();
        assertEquals(-1, reader.stream("/json200", "tok", null, 10, events::add),
                "200 但 Content-Type 非 text/event-stream 必须按不可用返回，不能返回 0 当成功");
        assertTrue(events.isEmpty());
    }

    @Test
    @DisplayName("真 SSE 但零事件 ⇒ 0（合法空流不算不可用）")
    void realEmptyStreamReturnsZero() {
        SseReader reader = new SseReader(baseUrl, "trace-6", 5_000);
        assertEquals(0, reader.stream("/empty-stream", "tok", null, 10, e -> { }),
                "已确认为 SSE 且连接正常 ⇒ 0 是真实事件数，不能与「不可用」混淆");
    }
}
