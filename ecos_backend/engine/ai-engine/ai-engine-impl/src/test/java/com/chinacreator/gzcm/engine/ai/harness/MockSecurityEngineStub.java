package com.chinacreator.gzcm.engine.ai.harness;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 详细设计-06 F06-22 测试底座 —— security-engine 可切换桩边界（P-1）。
 *
 * <p>覆盖文档 F06-22 要点 1 的三态注入（ALLOW/DENY/FAIL_CLOSED）四类故障注入
 * （TIMEOUT/RATE_LIMITED_429/UNPARSEABLE）。本桩<b>不</b>用 Mockito 排除的目的：
 * 三态在两条断言上都要被"真读"（真的落 HTTP 响应体再走一遍
 * {@code AiSecurityEngineClient.verdictOf} 的语义），Mockito 替身会绕开这段契约 ——
 * 于是这里以真实 JSON 返回体模拟HttpServer-side 裁决， {@link #state()} 只切换
 * <b>受伤模式</b>而非伪造 verdict 枚举，语义与
 * {@code runtime/llm-gateway SecurityEngineBridgeAbacTest.Harness} 同源。</p>
 *
 * <p>通过 {@link #setState(StubState)} 在校验方法内部切换（无静态单例污染：
 * 上下文必须自持本桩单例并在校验方法内复位）。默认 {@link StubState#ALLOW}：
 * 让"两个 Bootstrap 模式都能过健康检"成为<b>契约</b>而非遮蔽 —— 一旦忘记复位，
 * 下一个用例立刻 FAIL_CLOSED 而非假 ALLOW。</p>
 */
public final class MockSecurityEngineStub {

    public enum StubState {
        /** 裁决放行：返回 {@code {"code":0,"data":{"allow":true,"source":"engine"}}}。 */
        ALLOW,
        /** 明确拒绝：返回 {@code {"code":0,"data":{"allow":false,"source":"engine"}}}（与 fail-closed 严格分态）。 */
        DENY,
        /** 底座不可达：连接拒绝 / 超时（模拟 security-host 连不上 → fallback 到 FAIL_CLOSED）。 */
        UNSAFE_UNREACHABLE,
        /** 限流：门禁返回 429 而非 JSON。 */
        UNSAFE_RATE_LIMITED,
        /** 不可解析：返回 200 但 body 非 JSON（模拟 json 结构坏损 → AISECD 丢帧 → FAIL_CLOSED）。 */
        UNSAFE_UNPARSEABLE,
    }

    private final AtomicReference<StubState> state = new AtomicReference<>(StubState.ALLOW);
    private final long evaluateMs = 0;

    public void setState(StubState s) {
        state.set(s);
    }

    public StubState currentState() {
        return state.get();
    }

    /** 一次性复位 —— 供 @BeforeEach 用，避免跨用例泄露。 */
    public void reset() {
        state.set(StubState.ALLOW);
    }

    /** 模拟 security-engine {@code /policy-engine/evaluate} 面向网关的 HTTP 响应体（byte[] 层）。 */
    public StubResponse evaluate(String toolName) {
        StubState s = state.get();
        switch (s) {
            case ALLOW:
                return StubResponse.json(200,
                        "{\"code\":0,\"message\":\"ok\",\"data\":{\"allow\":true,\"source\":\"engine\",\"policyId\":\"p-allow\"}}");
            case DENY:
                return StubResponse.json(200,
                        "{\"code\":0,\"message\":\"denied\",\"data\":{\"allow\":false,\"source\":\"engine\",\"policyId\":\"p-deny\"}}");
            case UNSAFE_UNREACHABLE:
                return StubResponse.raw(0, new byte[0], "connection-refused");
            case UNSAFE_RATE_LIMITED:
                return StubResponse.raw(429, new byte[0], "rate-limited");
            case UNSAFE_UNPARSEABLE:
                return StubResponse.raw(200, "not-a-json ``".getBytes(java.nio.charset.StandardCharsets.UTF_8), "unparseable");
            default:
                throw new IllegalStateException("unknown state " + s);
        }
    }

    public long simulateLatencyMs() {
        return evaluateMs;
    }

    /** 简易响应载体 —— 站内替换用，规避直接依赖 okhttp/mockwebserver（本仓未引）。 */
    public static final class StubResponse {
        public final int status;
        public final byte[] body;
        public final String note;
        public final Map<String, String> headers = new HashMap<>();

        private StubResponse(int status, byte[] body, String note) {
            this.status = status;
            this.body = body;
            this.note = note;
        }

        static StubResponse json(int status, String json) {
            StubResponse r = new StubResponse(status, json.getBytes(java.nio.charset.StandardCharsets.UTF_8), "json");
            r.headers.put("Content-Type", "application/json; charset=utf-8");
            return r;
        }

        static StubResponse raw(int status, byte[] body, String note) {
            return new StubResponse(status, body, note);
        }
    }
}
