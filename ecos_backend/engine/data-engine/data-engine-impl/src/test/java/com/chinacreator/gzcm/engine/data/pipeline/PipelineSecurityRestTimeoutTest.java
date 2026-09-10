package com.chinacreator.gzcm.engine.data.pipeline;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PipelineSecurityRestTimeoutTest — P2#4：RestTemplate 显式超时配置验证。
 *
 * <p>两条断言：
 * <ol>
 *   <li>管道里 new 出的 {@link PipelineSecurityService#newRestTemplateWithTimeouts(int, int)}
 *       返回的 RestTemplate，其 {@link ClientHttpRequestFactory} 是
 *       {@link SimpleClientHttpRequestFactory}，且 connect/read 超时严格大于 0。</li>
 *   <li>对不存在的端点（模拟第三方 503 / 不可达）调用
 *       {@link PipelineSecurityService#auditWrite}，方法不应抛 NPE，超时/网络错误被
 *       内部捕获并 log.warn（审计失败不阻塞主流程，铁律 §2.4 第 5 条）。</li>
 * </ol>
 *
 * <p>不依赖 Spring 容器（避免 spring-test 依赖），通过 UncheckedIOException
 * 模拟 503 / 连接失败场景。
 */
class PipelineSecurityRestTimeoutTest {

    // ────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("newRestTemplateWithTimeouts — factory 是 SimpleClientHttpRequestFactory 且超时 > 0")
    void factoryHasExplicitTimeouts() {
        RestTemplate rt = PipelineSecurityService.newRestTemplateWithTimeouts(30_000, 60_000);
        assertNotNull(rt);

        ClientHttpRequestFactory f = rt.getRequestFactory();
        assertNotNull(f, "factory 不应为 null");
        assertTrue(f instanceof SimpleClientHttpRequestFactory,
                "factory 类型应为 SimpleClientHttpRequestFactory，实际：" + f.getClass());
        String desc = f.getClass().getSimpleName();
        assertTrue(desc.contains("SimpleClientHttp") || desc.contains("Buffering"),
                "factory 应为 Simple 系，实际：" + desc);

        // SimpleClientHttpRequestFactory.setConnectTimeout(0) / setReadTimeout(0) 时
        // getRequestFactory 会出现 0 超时缺省，这里显式断言 > 0 防止回落到 "永不超时" 的兜底。
        SimpleClientHttpRequestFactory s = (SimpleClientHttpRequestFactory) f;
        // 改用反射读 SimpleClientHttpRequestFactory#connectTimeout 与 readTimeout
        int connect = readTimeoutField(s, "connectTimeout");
        int read = readTimeoutField(s, "readTimeout");
        assertTrue(connect > 0, "connectTimeout 应 > 0，实际 " + connect);
        assertTrue(read > 0, "readTimeout 应 > 0，实际 " + read);
        assertTrue(connect <= 30_000 && read <= 60_000,
                "超时不应超过 30s/60s 上限");
    }

    @Test
    @DisplayName("newRestTemplateWithTimeouts — 入参 0/负数 被钳到 1ms（防 NPE / 永不超时）")
    void zeroTimeoutClampsToOneMs() {
        RestTemplate rt0 = PipelineSecurityService.newRestTemplateWithTimeouts(0, 0);
        SimpleClientHttpRequestFactory f0 = (SimpleClientHttpRequestFactory) rt0.getRequestFactory();
        assertEquals(1, readTimeoutField(f0, "connectTimeout"));
        assertEquals(1, readTimeoutField(f0, "readTimeout"));

        RestTemplate rtNeg = PipelineSecurityService.newRestTemplateWithTimeouts(-5, -5);
        SimpleClientHttpRequestFactory fNeg = (SimpleClientHttpRequestFactory) rtNeg.getRequestFactory();
        assertEquals(1, readTimeoutField(fNeg, "connectTimeout"));
        assertEquals(1, readTimeoutField(fNeg, "readTimeout"));
    }

    private static int readTimeoutField(SimpleClientHttpRequestFactory f, String field) {
        try {
            java.lang.reflect.Field fld = f.getClass().getDeclaredField(field);
            fld.setAccessible(true);
            return ((Integer) fld.get(f));
        } catch (ReflectiveOperationException e) {
            fail("SimpleClientHttpRequestFactory 字段 " + field + " 读取失败: " + e);
            return -1;
        }
    }

    // ────────────────────────────────────────────────────────────────────
    // 503 不 NPE（Mock 第三方不可达场景）
    // ────────────────────────────────────────────────────────────────────

    private static HttpServer stubServer;
    private static int stubPort;

    @BeforeAll
    static void startStub() throws Exception {
        stubServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        stubServer.createContext("/api/security/audit/log", ex -> {
            byte[] body = "{\"code\":503,\"msg\":\"security engine unavailable\"}".getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(503, body.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(body);
            }
        });
        stubServer.start();
        stubPort = stubServer.getAddress().getPort();
    }

    @AfterAll
    static void stopStub() {
        if (stubServer != null) {
            stubServer.stop(0);
        }
    }

    @Test
    @DisplayName("auditWrite — 第三方 503 不抛 NPE，也不阻塞调用方（铁律 §2.4 第 5 条）")
    void auditWriteDoesNotNpeOn503() throws Exception {
        // 直接构造一个用 stub URL 的服务实例
        PipelineSecurityService svc = new PipelineSecurityService();
        // 反射覆盖 securityBaseUrl 指向 stub（P2#4 把 restTemplate 保留为 final 字段，
        // 不再 mock；这里实际走 HTTP 调用 stub。SimpleClientHttpRequestFactory 30s 连接超时，
        // 503 响应在连接建立后立即返回，不会真超时——但代码路径等价"不可达分支"）。
        java.lang.reflect.Field url = PipelineSecurityService.class.getDeclaredField("securityBaseUrl");
        url.setAccessible(true);
        url.set(svc, "http://127.0.0.1:" + stubPort);

        long start = System.currentTimeMillis();
        // 不抛 NPE 即通过；超时也不应挂 > 10s（我们用 503 快速响应代替，断言 < 10s）
        assertDoesNotThrow(() -> svc.auditWrite("PIPELINE_TEST_503", "p-1", "tester"));
        long elapsed = System.currentTimeMillis() - start;
        // @Async 改造前是同步走 RestTemplate；本测试走同步分支（无 Spring 代理），
        // 503 应快速返回；上限放宽到 10s（一旦回归到 0 超时会看 60s 上限）
        assertTrue(elapsed < 10_000, "503 调用应在 10s 内返回，实际 " + elapsed + "ms");

        // 触发的副作用：audit 失败仅 log.warn，不应抛任何 NPE
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> svc.auditWrite("PIPELINE_NPE_GUARD", "p-2", "u"));
    }
}
