package com.chinacreator.gzcm.cli.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.chinacreator.gzcm.cli.CliCommand;
import com.chinacreator.gzcm.cli.CliCommands;
import com.chinacreator.gzcm.cli.CliOptions;
import com.chinacreator.gzcm.cli.HttpTransport;
import com.chinacreator.gzcm.cli.OperationRegistry;
import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 写命令的「头 ↔ 输出」一致性（计划 §二 幂等键 + 批 C trace 贯通的最小可执行证据）。
 *
 * <p>钉住：{@code Idempotency-Key} 请求头必须与输出 {@code data.idempotencyKey} 同值，
 * {@code X-Trace-Id} 必须与输出 {@code trace_id} 同值。二者若各调一次取号，实时值与回显值会
 * 分叉，重放对账与审计追查同时失效。</p>
 */
class WriteCommandIdempotencyTest {

    private HttpServer server;
    private String baseUrl;
    private final AtomicInteger hits = new AtomicInteger();
    private final AtomicReference<String> seenTrace = new AtomicReference<>();
    private final AtomicReference<String> seenIdem = new AtomicReference<>();

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/wagent/candidates", exchange -> {
            hits.incrementAndGet();
            seenTrace.set(exchange.getRequestHeaders().getFirst("X-Trace-Id"));
            seenIdem.set(exchange.getRequestHeaders().getFirst("Idempotency-Key"));
            byte[] body = "{\"candidateId\":\"c-test\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    @DisplayName("写命令 --dry-run：只回显 plannedCall，桩服务零请求")
    void dryRunSendsNoRequest() {
        CliOptions options = CliOptions.parse(new String[] {
                "knowledge", "extract",
                "--candidate-type", "KNOWLEDGE_ITEM",
                "--payload-json", "{}",
                "--base-url", baseUrl,
                "--token", "test-token",
                "--dry-run", "--no-retry"}, 2);
        CliCommand command = CliCommands.ALL.stream()
                .filter(c -> CliCommands.KNOWLEDGE_EXTRACT.equals(c.name()))
                .findFirst()
                .orElseThrow();
        HttpTransport transport = new HttpTransport(options.baseUrl(), options.traceId(),
                options.timeoutMs(), options.retryPolicy(), true);

        Map<String, Object> out = GenericCommandRunner.run(
                OperationRegistry.load(), options, transport, command);

        assertEquals(0, hits.get(), "--dry-run 不得发出任何写请求");
        assertEquals("SUCCESS", String.valueOf(out.get("status")));
        Object data = out.get("data");
        assertTrue(data instanceof Map<?, ?>, "dry-run 应回显 data.plannedCall");
        String planned = String.valueOf(((Map<?, ?>) data).get("plannedCall"));
        assertTrue(planned.startsWith("POST "), "计划应为 POST：实得 " + planned);
        assertTrue(planned.contains("/candidates"), "计划路径应来自契约：实得 " + planned);
    }

    @Test
    @DisplayName("knowledge extract：Idempotency-Key 与回显同值，X-Trace-Id 与 trace_id 同值")
    void idempotencyAndTraceMatchWhatIsSent() {
        CliOptions options = CliOptions.parse(new String[] {
                "knowledge", "extract",
                "--candidate-type", "KNOWLEDGE_ITEM",
                "--payload-json", "{}",
                "--base-url", baseUrl,
                "--token", "test-token",
                "--no-retry"}, 2);
        CliCommand command = CliCommands.ALL.stream()
                .filter(c -> CliCommands.KNOWLEDGE_EXTRACT.equals(c.name()))
                .findFirst()
                .orElseThrow();
        HttpTransport transport = new HttpTransport(options.baseUrl(), options.traceId(),
                options.timeoutMs(), options.retryPolicy(), true);

        Map<String, Object> out = GenericCommandRunner.run(
                OperationRegistry.load(), options, transport, command);

        assertEquals(1, hits.get(), "写命令应恰好发一次请求");
        assertEquals(options.traceId(), seenTrace.get(), "X-Trace-Id 必须是本次运行的 trace");
        assertEquals(String.valueOf(out.get("trace_id")), seenTrace.get(), "输出 trace_id 必须与线上头一致");

        Object data = out.get("data");
        assertTrue(data instanceof Map<?, ?>, "写命令输出应带 data 对象以回显幂等键");
        assertEquals(seenIdem.get(), ((Map<?, ?>) data).get("idempotencyKey"),
                "回显的 idempotencyKey 必须就是请求头里发出的那个");
        assertTrue(String.valueOf(seenIdem.get()).startsWith("cli-"), "缺省幂等键应为 cli-<uuid>");
    }
}
