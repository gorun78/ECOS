package com.chinacreator.gzcm.runtime.llm.abac;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 详细设计-06 F06-22 llm-gateway 侧「security REST 桩」—— 真实 {@link HttpServer} 端口，
 * 响应体直读 {@link FakeVerdict} 三态（ALLOW/DENY/UNSAFE_UNREACHABLE）。
 *
 * <p>与 ai-engine-impl 侧的 {@code MockSecurityEngineStub} 语义等价：桩是<b>边界</b>，
 * 由生产代码经真实 OkHttp 打进来；不 Mockito 化 verdict 枚举（R-18 反假阳性口径）。
 * 该类<b>必须</b>可 {@link #close()}—— 桩端口占用是 teste 收尾漏 reap 的高频故障源。</p>
 */
public final class FakeSecurityHttpServer implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(FakeSecurityHttpServer.class);

    public enum FakeVerdict {
        /** 显式放行（源=engine，非 fallback；对齐 X-26 "fallback≠allow"）。 */
        ALLOW,
        /** 显式拒绝（源=engine）。 */
        DENY,
        /** 桩 service 直接回 429（限流；生产代码须映射 FAIL_CLOSED 语义，非 DENY）。 */
        RATE_LIMITED_429,
        /** 桩 service 回非 JSON 200（不可解析；生产代码须映射 FAIL_CLOSED 而非 DENY）。 */
        UNSAFE_UNPARSEABLE,
    }

    private final HttpServer server;
    private final AtomicReference<FakeVerdict> verdict =
            new AtomicReference<>(FakeVerdict.ALLOW);
    private final AtomicInteger hits = new AtomicInteger();

    public FakeSecurityHttpServer() throws IOException {
        this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public void setVerdict(FakeVerdict v) {
        verdict.set(v);
    }

    public FakeVerdict currentVerdict() {
        return verdict.get();
    }

    /** 打到桩的真实 HTTP 命中数（含 policy-engine/evaluate 路径）。 */
    public int hits() {
        return hits.get();
    }

    private void handle(HttpExchange ex) throws IOException {
        if (!"/api/v1/security/policy-engine/evaluate".equals(ex.getRequestURI().getPath())) {
            ex.sendResponseHeaders(404, -1);
            ex.close();
            return;
        }
        hits.incrementAndGet();
        ex.getRequestBody().readAllBytes();
        FakeVerdict v = verdict.get();
        String body;
        int status;
        switch (v) {
            case ALLOW -> body = "{\"code\":0,\"message\":\"ok\",\"data\":{\"allow\":true,\"source\":\"engine\",\"policyId\":\"p-allow\"}}";
            case DENY -> body = "{\"code\":0,\"message\":\"denied\",\"data\":{\"allow\":false,\"source\":\"engine\",\"policyId\":\"p-deny\"}}";
            case RATE_LIMITED_429 -> body = "";
            case UNSAFE_UNPARSEABLE -> body = "not-a-json ``";
            default -> throw new IllegalStateException("unknown verdict " + v);
        }
        status = (v == FakeVerdict.RATE_LIMITED_429) ? 429 : 200;
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().put("Content-Type",
                java.util.Collections.singletonList("application/json; charset=utf-8"));
        ex.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) {
            try (OutputStream os = ex.getResponseBody()) {
                os.write(bytes);
            }
        }
        ex.close();
    }

    @Override
    public void close() {
        server.stop(0);
        log.debug("[abac-fake] closed");
    }
}
