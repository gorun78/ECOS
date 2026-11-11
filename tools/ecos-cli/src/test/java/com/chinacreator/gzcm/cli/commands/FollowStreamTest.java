package com.chinacreator.gzcm.cli.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.chinacreator.gzcm.cli.CliCommand;
import com.chinacreator.gzcm.cli.CliCommands;
import com.chinacreator.gzcm.cli.CliOptions;
import com.chinacreator.gzcm.cli.HttpTransport;
import com.chinacreator.gzcm.cli.OperationRegistry;
import com.chinacreator.gzcm.cli.RetryPolicy;
import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@code agent inspect --follow} 门禁（CLI 生产化）：走 streamRunEvents 的真流语义。
 *
 * <p>钉住三态：①流可用 ⇒ SUCCESS + 事件条数；②服务端降级/不可用 ⇒ UNAVAILABLE（禁当空流成功）；
 * ③{@code --dry-run} 只打印 SSE 计划、零请求。</p>
 */
class FollowStreamTest {

    private HttpServer server;
    private String baseUrl;
    private final AtomicInteger hits = new AtomicInteger();

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/wagent/runs/", exchange -> {
            hits.incrementAndGet();
            if (exchange.getRequestURI().getPath().endsWith("/events")) {
                byte[] body = ("event: meta\nid: 1\ndata: {\"runId\":\"R\"}\n\n"
                        + "event: done\nid: 2\ndata: {}\n\n").getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
                exchange.sendResponseHeaders(200, body.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(body);
                }
            } else {
                byte[] body = "{\"code\":200,\"data\":{}}".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, body.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(body);
                }
            }
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private Map<String, Object> runFollow(String[] extra) {
        OperationRegistry registry = OperationRegistry.load();
        CliCommand inspect = CliCommands.find(CliCommands.AGENT_INSPECT);
        List<String> list = new java.util.ArrayList<>(List.of("agent", "inspect", "--run-id", "R"));
        list.addAll(List.of(extra));
        if (!list.contains("--base-url")) {
            list.add("--base-url");
            list.add(baseUrl);
        }
        CliOptions options = CliOptions.parse(list.toArray(new String[0]), 2);
        HttpTransport transport = new HttpTransport(options.baseUrl(), "t", 5_000,
                new RetryPolicy(1), true);
        return GenericCommandRunner.run(registry, options, transport, inspect);
    }

    @Test
    @DisplayName("流可用 ⇒ SUCCESS + 事件条数（不再落到 getRun）")
    void streamsEvents() {
        Map<String, Object> out = runFollow(new String[] {"--follow", "--token", "tok"});
        assertEquals("SUCCESS", out.get("status"));
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) out.get("data");
        assertEquals(2, data.get("count"));
        assertEquals("streamRunEvents",
                ((Map<?, ?>) ((List<?>) out.get("source_refs")).get(0)).get("operationId"));
    }

    @Test
    @DisplayName("服务端无 SSE（返回 JSON）⇒ UNAVAILABLE，hint 指向轮询")
    void degradesToUnavailable() {
        Map<String, Object> out = runFollow(new String[] {"--follow", "--token", "tok",
                "--base-url", baseUrl + "/nowhere"});
        // 走不存在的 context ⇒ 连接/4xx 均视为不可用
        assertEquals("UNAVAILABLE", out.get("status"));
        @SuppressWarnings("unchecked")
        Map<String, Object> error = (Map<String, Object>) out.get("error");
        assertTrue(String.valueOf(error.get("hint")).contains("轮询"));
    }

    @Test
    @DisplayName("--follow --dry-run：只打印 SSE 计划，零请求")
    void dryRunSendsNothing() {
        int before = hits.get();
        Map<String, Object> out = runFollow(new String[] {"--follow", "--dry-run"});
        assertEquals("SUCCESS", out.get("status"));
        assertEquals(before, hits.get(), "dry-run 不得发出任何请求");
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) out.get("data");
        assertTrue(String.valueOf(data.get("plannedCall")).endsWith("/events"));
    }
}
