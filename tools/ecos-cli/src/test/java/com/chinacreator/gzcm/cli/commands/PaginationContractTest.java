package com.chinacreator.gzcm.cli.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 分页契约（计划 §二：{@code --all} 才遍历，默认单页并回显 {@code data.page}）。
 *
 * <p>钉住：①默认单页只发 1 次请求且 {@code data.page.all=false}；②{@code --all} 遍历到短页并
 * 合并行；③非列表响应（对象）不被分页逻辑改写。</p>
 */
class PaginationContractTest {

    private HttpServer server;
    private String baseUrl;
    private final AtomicInteger hits = new AtomicInteger();
    private final AtomicReference<String> lastQuery = new AtomicReference<>();
    /** 逐次请求的 query，用于验证「翻页是否保留原始过滤条件」。 */
    private final java.util.List<String> queries = java.util.Collections.synchronizedList(new java.util.ArrayList<>());

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/ai/models", exchange -> {
            int n = hits.incrementAndGet();
            lastQuery.set(exchange.getRequestURI().getQuery());
            queries.add(exchange.getRequestURI().getQuery());
            // 第一页返回 2 行（等于 pageSize ⇒ 未到尾），第二页返回 1 行（短页 ⇒ 停）
            String items = n == 1 ? "[{\"id\":\"m1\"},{\"id\":\"m2\"}]" : "[{\"id\":\"m3\"}]";
            String body = "{\"code\":0,\"data\":{\"items\":" + items + ",\"total\":3,\"size\":2}}";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.createContext("/api/v1/wagent/runs/R1", exchange -> {
            hits.incrementAndGet();
            byte[] bytes = "{\"code\":0,\"data\":{\"runId\":\"R1\",\"state\":\"RUNNING\"}}"
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

    private Map<String, Object> run(String name, String[] extra, int commandWords) {
        String[] base = {"--base-url", baseUrl, "--token", "t", "--no-retry"};
        String[] args = new String[base.length + extra.length];
        System.arraycopy(extra, 0, args, 0, extra.length);
        System.arraycopy(base, 0, args, extra.length, base.length);
        CliOptions options = CliOptions.parse(args, commandWords);
        CliCommand command = CliCommands.ALL.stream()
                .filter(c -> c.name().equals(name)).findFirst().orElseThrow();
        HttpTransport transport = new HttpTransport(options.baseUrl(), options.traceId(),
                options.timeoutMs(), options.retryPolicy(), true);
        return GenericCommandRunner.run(OperationRegistry.load(), options, transport, command);
    }

    @Test
    @DisplayName("默认单页：只发一次请求，data.page.all=false 且带 pageSize")
    void defaultSinglePageEchoesPageMeta() {
        hits.set(0);
        Map<String, Object> out = run("model list", new String[] {"model", "list", "--page-size", "2"}, 2);
        assertEquals(1, hits.get(), "默认不得遍历（--all 才翻页）");
        Object data = out.get("data");
        assertTrue(data instanceof Map<?, ?>, "列表应按 {items,page} 回显");
        Map<?, ?> dataMap = (Map<?, ?>) data;
        assertTrue(dataMap.get("items") instanceof List, "items 必须是行数组");
        assertEquals(2, ((List<?>) dataMap.get("items")).size());
        Map<?, ?> page = (Map<?, ?>) dataMap.get("page");
        assertFalse((Boolean) page.get("all"), "默认单页 all 必须为 false");
        assertEquals(2, page.get("pageSize"));
        assertEquals(3, page.get("total"), "后端 total 应被读取");
    }

    @Test
    @DisplayName("--all：翻到短页即停，合并全部行")
    void allWalksUntilShortPage() {
        hits.set(0);
        Map<String, Object> out = run("model list",
                new String[] {"model", "list", "--all", "--page-size", "2"}, 2);
        assertEquals(2, hits.get(), "第一页满页 ⇒ 应再取一页，第二页短页 ⇒ 停止");
        Map<?, ?> dataMap = (Map<?, ?>) out.get("data");
        assertEquals(3, ((List<?>) dataMap.get("items")).size(), "两页应合并为 3 行");
        Map<?, ?> page = (Map<?, ?>) dataMap.get("page");
        assertTrue((Boolean) page.get("all"));
        assertEquals(2, page.get("pagesFetched"));
        assertTrue(String.valueOf(lastQuery.get()).contains("offset=2"), "第二页应带 offset=2");
    }

    @Test
    @DisplayName("--all 翻页必须保留原始过滤条件（续页丢 filter 会返回未过滤数据）")
    void allPagesKeepOriginalFilters() {
        hits.set(0);
        queries.clear();
        run("model list",
                new String[] {"model", "list", "--all", "--page-size", "2", "--capability", "CHAT"}, 2);
        assertEquals(2, hits.get(), "夹具前提：应翻两页");
        assertEquals(2, queries.size());
        for (int i = 0; i < queries.size(); i++) {
            assertTrue(String.valueOf(queries.get(i)).contains("capability=CHAT"),
                    "第 " + (i + 1) + " 页丢了原始过滤条件 capability=CHAT，实得 query: " + queries.get(i));
        }
    }

    @Test
    @DisplayName("total 三种承载位置都能读到：顶层 / data.total / data.data.total")
    void totalIsReadFromAllKnownShapes() throws Exception {
        String[] bodies = {
            "{\"code\":0,\"data\":[{\"id\":\"a\"}],\"total\":7}",
            "{\"code\":0,\"data\":{\"items\":[{\"id\":\"a\"}],\"total\":8}}",
            "{\"code\":0,\"data\":{\"data\":{\"items\":[{\"id\":\"a\"}],\"total\":9}}}"
        };
        int[] expected = {7, 8, 9};
        for (int i = 0; i < bodies.length; i++) {
            final String body = bodies[i];
            final int idx = i;
            HttpTransport.Result first = new HttpTransport.Result(200, body, null, null);
            com.chinacreator.gzcm.cli.PageWalker.PageResult page =
                    com.chinacreator.gzcm.cli.PageWalker.collectFromFirst(10, 10, false, first,
                            (offset, limit) -> {
                                throw new AssertionError("默认单页不应续取（形态 " + idx + "）");
                            });
            assertEquals(expected[i], page.total(), "第 " + i + " 种形态的 total 未读到");
            assertEquals(1, page.rows().size());
        }
    }

    @Test
    @DisplayName("非列表响应（对象）不被分页逻辑改写")
    void nonListResponseIsUntouched() {
        hits.set(0);
        Map<String, Object> out = run("agent inspect",
                new String[] {"agent", "inspect", "--run-id", "R1"}, 2);
        Map<?, ?> dataMap = (Map<?, ?>) out.get("data");
        assertEquals("R1", dataMap.get("runId"), "对象载荷必须原样保留");
        assertEquals("RUNNING", dataMap.get("state"));
        assertFalse(dataMap.containsKey("page"), "对象响应不得被塞入分页元数据");
    }
}
