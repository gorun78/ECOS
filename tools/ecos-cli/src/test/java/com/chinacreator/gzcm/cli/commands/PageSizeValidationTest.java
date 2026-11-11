package com.chinacreator.gzcm.cli.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.chinacreator.gzcm.cli.CliCommand;
import com.chinacreator.gzcm.cli.CliCommands;
import com.chinacreator.gzcm.cli.CliException;
import com.chinacreator.gzcm.cli.CliOptions;
import com.chinacreator.gzcm.cli.ExitCodes;
import com.chinacreator.gzcm.cli.HttpTransport;
import com.chinacreator.gzcm.cli.OperationRegistry;
import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 分页参数**前置**校验门禁。
 *
 * <p>缺陷背景（2026-11-11 实测）：{@code --page-size 501} 的执行路径是
 * 「先发请求 → 再在 attachPageMeta 里校验」，于是用法错误被网关连通性掩盖成
 * {@code UNAVAILABLE}（退出码 3）。用户看到的是「网关不可达」，实际是自己传错了参数，
 * 排查方向被误导。分页参数属纯本地约束，必须在发请求前判死。</p>
 */
class PageSizeValidationTest {

    private HttpServer server;
    private String baseUrl;
    private final AtomicInteger hits = new AtomicInteger();

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/ai/models", exchange -> {
            hits.incrementAndGet();
            byte[] bytes = "{\"code\":0,\"data\":{\"items\":[],\"total\":0}}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private void run(String... extra) {
        List<String> argv = new ArrayList<>(List.of("model", "list",
                "--base-url", baseUrl, "--token", "t", "--no-retry"));
        argv.addAll(List.of(extra));
        CliOptions options = CliOptions.parse(argv.toArray(new String[0]), 2);
        CliCommand command = CliCommands.find("model list");
        assertNotNull(command);
        HttpTransport transport = new HttpTransport(options.baseUrl(), options.traceId(),
                options.timeoutMs(), options.retryPolicy(), true);
        GenericCommandRunner.run(OperationRegistry.load(), options, transport, command);
    }

    @Test
    @DisplayName("--page-size 501 ⇒ USAGE(64)，且一个请求都不发（不得伪装成网关不可达）")
    void oversizedPageSizeIsRejectedBeforeAnyRequest() {
        hits.set(0);
        CliException e = assertThrows(CliException.class, () -> run("--page-size", "501"));
        assertEquals(ExitCodes.USAGE, e.exitCode(), "超上限必须是 USAGE，实得: " + e.getMessage());
        assertTrue(e.getMessage().contains("500"), "错误信息应指明上限: " + e.getMessage());
        assertEquals(0, hits.get(), "参数非法时不得发出任何请求");
    }

    @Test
    @DisplayName("--page-size 0 / 负数 ⇒ USAGE(64)，零请求")
    void nonPositivePageSizeIsRejected() {
        for (String bad : List.of("0", "-3")) {
            hits.set(0);
            CliException e = assertThrows(CliException.class, () -> run("--page-size", bad),
                    "取值 " + bad + " 应被拒");
            assertEquals(ExitCodes.USAGE, e.exitCode(), "取值 " + bad + " 应为 USAGE");
            assertEquals(0, hits.get(), "取值 " + bad + " 时不得发请求");
        }
    }

    @Test
    @DisplayName("--page-size 非整数 ⇒ USAGE(64)，零请求")
    void nonNumericPageSizeIsRejected() {
        hits.set(0);
        CliException e = assertThrows(CliException.class, () -> run("--page-size", "abc"));
        assertEquals(ExitCodes.USAGE, e.exitCode());
        assertEquals(0, hits.get());
    }

    @Test
    @DisplayName("合法 --page-size 500 必须放行并真的发请求（确保校验没误杀边界值）")
    void boundaryValueIsAccepted() {
        hits.set(0);
        run("--page-size", "500");
        assertEquals(1, hits.get(), "边界值 500 应正常发一次请求");
    }
}
