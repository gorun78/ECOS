package com.chinacreator.gzcm.runtime.llm.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 详细设计-06 F06-05 要点 4（R-18 默认选项 ①）— ABAC 裁决经 security-engine REST 的行为契约。
 *
 * <p>覆盖两态一致性与 fail-closed（铁律 §2.4#4/#6）：显式 ALLOW 才放行；{@code source=fallback}
 * 不算放行；host 不可达 / base URL 不可得 / 底座 OkHttpClient 缺席 → DENY 且不抛。
 * base URL 供给链的 resolver 分支不在本模块可测范围（runtime-access 非 llm-gateway 编译期依赖；
 * 该分支不可用即为空串 → DENY，其兜底语义由 {@link #blankBaseUrlDeniesWithoutAnyHttpCall} 断言）。</p>
 */
@DisplayName("F06-05：llm-gateway ABAC 裁决走 security REST 且 fail-closed")
class SecurityEngineBridgeAbacTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // ═══════════════ 1. 显式 ALLOW / fallback / 缺失 ═══════════════

    @Test
    @DisplayName("security 返回 data.allow=true → 放行；请求体带 policy=rbac + subject；出站带 X-Request-Id")
    void explicitAllowFromSecurityRestPermits() throws IOException {
        try (Harness fx = Harness.withFakeSecurity("{\"code\":0,\"message\":\"ok\",\"data\":{\"allow\":true}}")) {
            assertTrue(fx.bridge.evaluate("service:llm-gateway", "t1", "llm:call", Map.of("model", "deepseek")));
            assertEquals(SecurityEngineBridge.PATH_POLICY_EVALUATE, fx.captured.path);
            String body = fx.captured.body();
            assertTrue(body.contains("\"policy\":\"rbac\""), body);
            assertTrue(body.contains("\"action\":\"llm:call\""), body);
            assertTrue(body.contains("\"tenantId\":\"t1\""), body);
            assertTrue(fx.captured.headerNames.stream()
                            .anyMatch(h -> "x-request-id".equalsIgnoreCase(h)),
                    "出站必须带 X-Request-Id（F06-06 要点 2）: " + fx.captured.headerNames);
        }
    }

    @Test
    @DisplayName("security 处于 fallback 兜底态（source=fallback）→ 不放行")
    void fallbackSourceIsNotTreatedAsAllow() throws IOException {
        try (Harness fx = Harness.withFakeSecurity(
                "{\"code\":0,\"data\":{\"allow\":true,\"source\":\"fallback\"}}")) {
            assertFalse(fx.bridge.evaluate("c", "t", "llm:call", Map.of()));
        }
    }

    @Test
    @DisplayName("裁决键缺失 → DENY（禁默认放行兜底）")
    void missingVerdictKeyDenies() throws IOException {
        try (Harness fx = Harness.withFakeSecurity("{\"code\":0,\"data\":{\"policyId\":\"p-1\"}}")) {
            assertFalse(fx.bridge.evaluate("c", "t", "llm:call", Map.of()));
        }
    }

    // ═══════════════ 2. fail-closed 形态 ═══════════════

    @Test
    @DisplayName("security host 不可达 → DENY 且不抛（不得把不可用折叠成异常）")
    void unreachableSecurityHostDenies() {
        try (Harness fx = new Harness(new Captured(), null, "http://127.0.0.1:1", true)) {
            assertFalse(fx.bridge.evaluate("c", "t", "llm:call", Map.of()));
        }
    }

    @Test
    @DisplayName("base URL 不可得（覆写空 + resolver 缺席）→ DENY，且零 HTTP 调用")
    void blankBaseUrlDeniesWithoutAnyHttpCall() throws IOException {
        try (Harness fx = Harness.withFakeSecurity("{\"code\":0,\"data\":{\"allow\":true}}")) {
            SecurityEngineBridge noBase = fx.newBridge("");
            assertFalse(noBase.evaluate("c", "t", "llm:call", Map.of()));
            assertEquals(0, fx.captured.hits.get(), "base URL 不可得时不得发起裁决调用");
        }
    }

    @Test
    @DisplayName("底座 OkHttpClient 缺席 → Spring 装配成功（非启动期崩），裁决 DENY")
    void beanConstructsWithoutOkHttpClientAndDenies() {
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
        ctx.register(BridgeOnlyConfig.class, PropertySourcesPlaceholderConfigurer.class);
        ctx.getEnvironment().getPropertySources()
                .addFirst(new MapPropertySource("test", Map.of("service.security.base-url", "http://127.0.0.1:1")));
        ctx.refresh();
        try (ctx) {
            SecurityEngineBridge bridge = ctx.getBean(SecurityEngineBridge.class);
            assertFalse(bridge.evaluate("c", "t", "llm:call", Map.of()));
            assertFalse(bridge.isSecurityEngineAvailable(),
                    "密钥服务/加解密服务皆缺席 → 集成状态须报「不可用」，供调用方区分未配置与断连");
        }
    }

    // ═══════════════ 装配辅助 ═══════════════

    /** 真实/假 security 现场：持有 Spring ctx（提供底座 OkHttpClient + ObjectMapper）与可选 HttpServer。 */
    private static final class Harness implements AutoCloseable {
        final AnnotationConfigApplicationContext ctx;
        final SecurityEngineBridge bridge;
        final Captured captured;
        private final HttpServer server;

        Harness(Captured captured, HttpServer server, String baseUrl, boolean withOkHttpClient) {
            this.captured = captured;
            this.server = server;
            this.ctx = new AnnotationConfigApplicationContext();
            if (withOkHttpClient) {
                this.ctx.register(HttpConfig.class);
            }
            this.ctx.refresh();
            this.bridge = newBridge(baseUrl);
        }

        SecurityEngineBridge newBridge(String baseUrl) {
            return new SecurityEngineBridge(ctx, ctx.getBeanProvider(OkHttpClient.class), baseUrl, MAPPER);
        }

        static Harness withFakeSecurity(String responseBody) throws IOException {
            Captured captured = new Captured();
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", ex -> {
                captured.hits.incrementAndGet();
                captured.path = ex.getRequestURI().getPath();
                captured.headerNames.addAll(ex.getRequestHeaders().keySet());
                captured.bodyBytes = ex.getRequestBody().readAllBytes();
                byte[] out = responseBody.getBytes(StandardCharsets.UTF_8);
                ex.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
                ex.sendResponseHeaders(200, out.length);
                ex.getResponseBody().write(out);
                ex.close();
            });
            server.start();
            return new Harness(captured, server,
                    "http://127.0.0.1:" + server.getAddress().getPort(), true);
        }

        @Override
        public void close() {
            ctx.close();
            if (server != null) {
                server.stop(0);
            }
        }
    }

    private static final class Captured {
        final AtomicInteger hits = new AtomicInteger();
        final List<String> headerNames = new ArrayList<>();
        volatile String path;
        volatile byte[] bodyBytes = new byte[0];

        String body() {
            return new String(bodyBytes, StandardCharsets.UTF_8);
        }
    }

    /** 只装配裁决底座（OkHttpClient + ObjectMapper），不引入 security-engine 类。 */
    @Configuration
    static class HttpConfig {
        @Bean
        OkHttpClient okHttpClient() {
            return new OkHttpClient();
        }

        @Bean
        ObjectMapper objectMapper() {
            return MAPPER;
        }
    }

    /** 生产装配形态：四参构造器由 Spring 解析（OkHttpClient 候选缺席 → ObjectProvider 返回 null）。 */
    @Configuration
    static class BridgeOnlyConfig {
        @Bean
        SecurityEngineBridge securityEngineBridge(
                org.springframework.context.ApplicationContext applicationContext,
                org.springframework.beans.factory.ObjectProvider<OkHttpClient> httpClientProvider,
                @org.springframework.beans.factory.annotation.Value("${service.security.base-url:}")
                        String securityBaseUrl,
                ObjectMapper objectMapper) {
            return new SecurityEngineBridge(applicationContext, httpClientProvider, securityBaseUrl, objectMapper);
        }

        @Bean
        ObjectMapper objectMapper() {
            return MAPPER;
        }
    }
}
