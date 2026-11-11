package com.chinacreator.gzcm.cli.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.chinacreator.gzcm.cli.CliOptions;
import com.chinacreator.gzcm.cli.CliStatus;
import com.chinacreator.gzcm.cli.ExitCodes;
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
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@code doctor --preflight} 门禁（计划 §一 行 14：doctor 扩为 11 步 preflight 入口）。
 *
 * <p>钉住四点：①--dry-run 只打印 {@code POST …/cli/preflight} 且零请求；
 * ②服务端 503 + blocked>0 ⇒ BLOCKED(2)（不得被 HTTP 码映射成 UNAVAILABLE/3）；
 * ③counts 定级优先于 HTTP 码（FAIL 压过 BLOCKED）；④notVerified>0 ⇒ PARTIAL(4)，
 * 全 PASS ⇒ SUCCESS(0)。</p>
 */
class DoctorPreflightTest {

    private HttpServer server;
    private String baseUrl;
    private final AtomicInteger hits = new AtomicInteger();
    private final AtomicReference<String> lastMethod = new AtomicReference<>();
    private final AtomicReference<String> mode = new AtomicReference<>("blocked");

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/cli/preflight", exchange -> {
            hits.incrementAndGet();
            lastMethod.set(exchange.getRequestMethod());
            int status;
            String body;
            switch (mode.get()) {
                case "allpass" -> {
                    status = 200;
                    body = envelope(0, 11, 0, 0, 0);
                }
                case "notverified" -> {
                    status = 200;
                    body = envelope(0, 9, 0, 0, 2);
                }
                case "fail" -> {
                    status = 500;
                    body = errorEnvelope(500, "E-CLI-PREFLIGHT-FAIL", 5, 1, 3, 2);
                }
                default -> {
                    status = 503;
                    body = errorEnvelope(503, "E-CLI-PREFLIGHT-BLOCKED", 4, 0, 7, 0);
                }
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private static String envelope(int code, int pass, int fail, int blocked, int notVerified) {
        return "{\"code\":" + code + ",\"data\":" + report(pass, fail, blocked, notVerified) + "}";
    }

    private static String errorEnvelope(int code, String errorCode, int pass, int fail, int blocked,
                                        int notVerified) {
        return "{\"code\":" + code + ",\"errorCode\":\"" + errorCode + "\",\"message\":\"preflight\","
                + "\"data\":" + report(pass, fail, blocked, notVerified) + "}";
    }

    private static String report(int pass, int fail, int blocked, int notVerified) {
        int total = pass + fail + blocked + notVerified;
        return "{\"stepCount\":" + total + ",\"pass\":" + pass + ",\"fail\":" + fail
                + ",\"blocked\":" + blocked + ",\"notVerified\":" + notVerified
                + ",\"createsRun\":false,\"steps\":[]}";
    }

    private Map<String, Object> run(String[] extra) {
        OperationRegistry registry = OperationRegistry.load();
        List<String> argv = new java.util.ArrayList<>(List.of("doctor", "--preflight"));
        argv.addAll(List.of(extra));
        argv.add("--token");
        argv.add("tok");
        CliOptions options = CliOptions.parse(argv.toArray(new String[0]), 1);
        HttpTransport transport = new HttpTransport(options.baseUrl(), "t", 5_000,
                new RetryPolicy(1), true);
        return DoctorCommand.run(registry, options, transport);
    }

    private static int exit(Map<String, Object> out) {
        return ExitCodes.forStatus(CliStatus.valueOf(String.valueOf(out.get("status"))));
    }

    @Test
    @DisplayName("--preflight --dry-run：打印 POST 预检路径且零请求")
    void dryRunSendsNothing() {
        Map<String, Object> out = run(new String[] {"--dry-run", "--base-url", baseUrl});

        assertEquals("SUCCESS", out.get("status"));
        assertEquals(0, hits.get(), "dry-run 不得发出任何请求");
        Map<?, ?> data = (Map<?, ?>) out.get("data");
        assertEquals("POST " + baseUrl + "/api/v1/cli/preflight", data.get("plannedCall"));
        assertEquals(false, data.get("createsRun"));
        assertEquals("runPreflight",
                ((Map<?, ?>) ((List<?>) out.get("source_refs")).get(0)).get("operationId"));
    }

    @Test
    @DisplayName("服务端 503 + blocked>0 ⇒ BLOCKED(2)，不得按 HTTP 码降成 UNAVAILABLE(3)")
    void blockedDependencyIsBlockedNotUnavailable() {
        mode.set("blocked");
        Map<String, Object> out = run(new String[] {"--base-url", baseUrl});

        assertEquals(1, hits.get());
        assertEquals("POST", lastMethod.get());
        assertEquals("BLOCKED", out.get("status"));
        assertEquals(ExitCodes.BLOCKED, exit(out));
        Map<?, ?> data = (Map<?, ?>) out.get("data");
        assertEquals(7, data.get("blocked"));
        assertEquals(false, data.get("createsRun"));
    }

    @Test
    @DisplayName("fail>0 ⇒ FAILED(1)：失败项压过 BLOCKED，且仍带完整报告")
    void failDominates() {
        mode.set("fail");
        Map<String, Object> out = run(new String[] {"--base-url", baseUrl});

        assertEquals("FAILED", out.get("status"));
        assertEquals(ExitCodes.FAILED, exit(out));
        assertFalse((Boolean) out.get("ok"));
        Map<?, ?> data = (Map<?, ?>) out.get("data");
        assertEquals(1, data.get("fail"));
        assertEquals(11, data.get("stepCount"));
    }

    @Test
    @DisplayName("notVerified>0 ⇒ PARTIAL(4)；全 PASS ⇒ SUCCESS(0)")
    void partialAndSuccess() {
        mode.set("notverified");
        Map<String, Object> partial = run(new String[] {"--base-url", baseUrl});
        assertEquals("PARTIAL", partial.get("status"));
        assertEquals(ExitCodes.PARTIAL, exit(partial));
        assertTrue((Boolean) partial.get("ok"), "PARTIAL 表示已跑完但有不可判定项");

        mode.set("allpass");
        Map<String, Object> ok = run(new String[] {"--base-url", baseUrl});
        assertEquals("SUCCESS", ok.get("status"));
        assertEquals(ExitCodes.SUCCESS, exit(ok));
        assertEquals(11, ((Map<?, ?>) ok.get("data")).get("pass"));
    }

    @Test
    @DisplayName("--preflight 无凭据 ⇒ BLOCKED(2) 且零请求（禁匿名预检）")
    void missingTokenIsBlockedWithoutRequest() {
        OperationRegistry registry = OperationRegistry.load();
        CliOptions options = CliOptions.parse(
                new String[] {"doctor", "--preflight", "--base-url", baseUrl}, 1);
        Map<String, Object> out = DoctorCommand.run(registry, options,
                new HttpTransport(baseUrl, "t", 5_000, new RetryPolicy(1), true));

        assertEquals("BLOCKED", out.get("status"));
        assertEquals(ExitCodes.BLOCKED, exit(out));
        assertEquals(0, hits.get(), "无凭据不得发出预检请求");
        assertEquals("runPreflight",
                ((Map<?, ?>) ((List<?>) out.get("source_refs")).get(0)).get("operationId"));
    }
}
