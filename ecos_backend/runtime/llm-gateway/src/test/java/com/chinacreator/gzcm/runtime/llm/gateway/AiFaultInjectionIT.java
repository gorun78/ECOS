package com.chinacreator.gzcm.runtime.llm.gateway;

import com.chinacreator.gzcm.common.error.AiErrorClass;
import com.chinacreator.gzcm.common.security.abac.AbacEvaluateResponseContract;
import com.chinacreator.gzcm.runtime.llm.config.LLMGatewayProperties;
import com.chinacreator.gzcm.runtime.llm.security.SecurityEngineBridge;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F06-14 验收 —— PRD-06 §5.2-3 三类故障注入（超时 / 限流 / 解析失败）必须走文档化路径。
 *
 * <p>注入方式：给网关注入一个短路型 OkHttp 应用拦截器（永不触达网络层，零外部依赖），
 * 由它按场景返回冻结状态码/响应体，从而对 {@code LLMGatewayImpl.callWithPolicy} 的
 * <b>真实退避循环</b>计数。断言的是"路径"而非"文案"：</p>
 * <ol>
 *   <li>{@code LLM_TIMEOUT}（504）→ 指数退避，重试<b>恰好且至多</b> 3 次（总外发 4 次）；</li>
 *   <li>{@code LLM_RATE_LIMITED}（429）→ 退避 ≤3 后，仅在配置了 {@code llm.gateway.fallback-model}
 *       时由<b>网关内部</b>路由备用 provider 一次（X-22：引擎侧无感、不绕行）；未配置则原样失败；</li>
 *   <li>{@code PARSE_FAILURE} → 网关 0 次代扣（重生成配额属引擎侧 F06-12，禁叠加）。</li>
 * </ol>
 *
 * <p>命名为 {@code *IT}：需显式 {@code -Dtest=AiFaultInjectionIT} 才跑（与 DqPhase4E2eIT 同规），
 * 不进 surefire 默认轮。</p>
 */
class AiFaultInjectionIT {

    private static final MediaType JSON = MediaType.parse("application/json");
    private static final String PRIMARY_MODEL = "deepseek-chat";
    private static final String FALLBACK_MODEL = "openai-gpt-4o-mini";

    @Test
    @DisplayName("三类故障注入：超时退避≤3、限流退避后经网关降备用 provider、解析失败网关不代扣")
    void timeoutRateLimitParseFaultTakeDocumentedPaths() throws Exception {
        timeoutFaultTakesBoundedExponentialRetry();
        rateLimitFaultWithoutFallbackConfigStaysBoundedAndFails();
        rateLimitFaultAfterExhaustingBackoffFallsBackInsideGateway();
        parseFailureFaultIsNotRetriedByGateway();
    }

    @Test
    @DisplayName("护栏拒绝态 0 重试：ABAC DENY 不得被退避循环放大成绕过面")
    void guardrailDenyFaultTakesZeroRetry() throws Exception {
        Fixture fixture = new Fixture(Collections.emptyList(), 0, null);
        LLMGatewayImpl gateway = gateway(fixture, "", false);

        ChatResponse response = gateway.call(request(PRIMARY_MODEL));

        assertFalse(response.isSuccess(), "ABAC DENY 必须返回失败");
        assertEquals(AiErrorClass.GUARDRAIL_DENIED, response.aiErrorClass(),
                "未放行必须归 GUARDRAIL_DENIED");
        assertEquals(0, fixture.calls.get(), "护栏拒绝不得外发任何 provider 请求");
    }

    // ── 场景 ─────────────────────────────────────────────────────────

    private void timeoutFaultTakesBoundedExponentialRetry() throws Exception {
        Fixture fixture = new Fixture(List.of(504), 0, null);
        LLMGatewayImpl gateway = gateway(fixture, "", true);

        ChatResponse response = gateway.call(request(PRIMARY_MODEL));

        assertEquals(AiErrorClass.LLM_TIMEOUT, response.aiErrorClass(),
                "504 必须归 LLM_TIMEOUT（D.5）");
        assertFalse(response.isSuccess(), "退避用尽后不得伪装成功（X-50）");
        assertEquals(LLM_TIMEOUT_CALLS, fixture.calls.get(),
                "LLM_TIMEOUT 总外发必须 = 首次 + 3 次重试（有界，禁无限自旋）");
        assertEquals(LLM_TIMEOUT_CALLS - 1, response.getRetriesAttempted(),
                "retriesAttempted 必须与真实退避次数同源上报");
        assertEquals(LLM_TIMEOUT_CALLS, fixture.modelsSeen.size(),
                "同模型重试不得偷换 provider 目标");
        assertTrue(fixture.modelsSeen.stream().allMatch(PRIMARY_MODEL::equals),
                "超时重试必须仍打原模型，实际=" + fixture.modelsSeen);
    }

    private void rateLimitFaultWithoutFallbackConfigStaysBoundedAndFails() throws Exception {
        Fixture fixture = new Fixture(List.of(429), 0, null);
        LLMGatewayImpl gateway = gateway(fixture, "", true);

        ChatResponse response = gateway.call(request(PRIMARY_MODEL));

        assertEquals(AiErrorClass.LLM_RATE_LIMITED, response.aiErrorClass(),
                "429 必须归 LLM_RATE_LIMITED（D.5）");
        assertEquals(LLM_TIMEOUT_CALLS, fixture.calls.get(),
                "限流退避上限同样 ≤3（总外发 4 次），未配置备用模型时禁擅自换 provider");
        assertFalse(response.isFallbackProviderUsed(),
                "未配置 llm.gateway.fallback-model 即不得降级（代码内禁兜底备用模型常量）");
    }

    private void rateLimitFaultAfterExhaustingBackoffFallsBackInsideGateway() throws Exception {
        Fixture fixture = new Fixture(List.of(429), 200, FALLBACK_MODEL);
        LLMGatewayImpl gateway = gateway(fixture, FALLBACK_MODEL, true);

        ChatResponse response = gateway.call(request(PRIMARY_MODEL));

        assertTrue(response.isSuccess(), "配置了备用 provider 且其成功时必须返回成功");
        assertTrue(response.isFallbackProviderUsed(),
                "限流用尽退避后必须经网关内部路由降备用 provider（F06-14 要点 2）");
        assertEquals(LLM_TIMEOUT_CALLS + 1, fixture.calls.get(),
                "主模型 4 次（首 + 3 退避）外发后才有资格降级，降级再加 1 次 ⇒ 总 5 次，禁提前切流");
        assertEquals(FALLBACK_MODEL, fixture.modelsSeen.get(fixture.modelsSeen.size() - 1),
                "降级请求必须携带配置的备用模型");
        assertEquals(LLM_TIMEOUT_CALLS - 1, response.getRetriesAttempted(),
                "降级成功也要如实上报已消耗的退避次数");
    }

    private void parseFailureFaultIsNotRetriedByGateway() throws Exception {
        Fixture fixture = new Fixture(List.of(200), 0, null);
        LLMGatewayImpl gateway = gateway(fixture, "", true);

        ChatResponse response = gateway.call(request(PRIMARY_MODEL));

        assertEquals(AiErrorClass.PARSE_FAILURE, response.aiErrorClass(),
                "结构非法的响应体必须归 PARSE_FAILURE");
        assertFalse(response.isSuccess(), "解析失败不得回落成空内容成功");
        assertEquals(1, fixture.calls.get(),
                "PARSE_FAILURE 的重生成配额属引擎侧 F06-12，网关代扣会叠加配额（禁）");
        assertEquals(0, response.getRetriesAttempted(),
                "网关未重试时 retriesAttempted 必须为 0");
    }

    /** {@code LLM_TIMEOUT} 冻结上限 3 ⇒ 总外发 1+3。 */
    private static final int LLM_TIMEOUT_CALLS = 4;

    // ── 装配 ─────────────────────────────────────────────────────────

    private static LLMGatewayImpl gateway(Fixture fixture, String fallbackModel,
                                          boolean abacAllow) throws Exception {
        LLMGatewayImpl gateway = new LLMGatewayImpl();
        setField(gateway, "objectMapper", new ObjectMapper());
        setField(gateway, "okHttpClient", new OkHttpClient.Builder()
                .addInterceptor(fixture.interceptor()).build());

        LLMGatewayProperties properties = new LLMGatewayProperties();
        properties.getGateway().setFallbackModel(fallbackModel);
        properties.getGateway().setDefaultApiKeyRef("secret:llm/itest-ref");
        setField(gateway, "llmGatewayProperties", properties);

        SecurityEngineBridge bridge = Mockito.mock(SecurityEngineBridge.class);
        // #142/B4-3e-14：桥的裁决出口已改三态（evaluateVerdict）。本夹具只关心"DENY 腿 0 重试"，
        // 故布尔入参在这里显式映射成 DENY（不是 UNEVALUATED —— 那是另一条腿，见 GatewayAbacModeTest）。
        Mockito.when(bridge.evaluateVerdict(Mockito.anyString(), Mockito.nullable(String.class),
                Mockito.anyString(), Mockito.<Map<String, Object>>any()))
                .thenReturn(abacAllow ? AbacEvaluateResponseContract.Verdict.ALLOW
                        : AbacEvaluateResponseContract.Verdict.DENY);
        Mockito.when(bridge.resolveSecret(Mockito.anyString(), Mockito.anyString()))
                .thenReturn("itest-provider-key");
        setField(gateway, "securityEngineBridge", bridge);
        return gateway;
    }

    private static ChatRequest request(String model) {
        ChatRequest request = new ChatRequest();
        request.setModel(model);
        request.setMessages(List.of(new ChatMessage("user", "故障注入探针")));
        request.setTemperature(0.0);
        request.setMaxTokens(16);
        request.setStream(false);
        request.setCallerId("service:itest");
        return request;
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    /** 故障注入拦截器：按脚本返回状态码/响应体，并记录每次外发的模型名。 */
    private static final class Fixture {
        private final List<Integer> statusScript;
        private final int succeedStatus;
        private final String succeedForModel;
        private final AtomicInteger calls = new AtomicInteger();
        private final List<String> modelsSeen = Collections.synchronizedList(new ArrayList<>());
        private final ObjectMapper mapper = new ObjectMapper();

        Fixture(List<Integer> statusScript, int succeedStatus, String succeedForModel) {
            this.statusScript = statusScript;
            this.succeedStatus = succeedStatus;
            this.succeedForModel = succeedForModel;
        }

        Interceptor interceptor() {
            return chain -> {
                Request request = chain.request();
                int attempt = calls.incrementAndGet();
                String requestJson = readBody(request);
                modelsSeen.add(extractModel(requestJson));
                if (succeedForModel != null && succeedForModel.equals(modelsSeen.get(attempt - 1))) {
                    return respond(request, succeedStatus, completionJson());
                }
                int status = statusScript.get(Math.min(attempt - 1, statusScript.size() - 1));
                return respond(request, status, faultBody(status));
            };
        }

        private Response respond(Request request, int status, String body) {
            return new Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(status)
                    .message(status >= 400 ? "injected-fault" : "ok")
                    .body(ResponseBody.create(body, JSON))
                    .build();
        }

        /** 429/504 只回错误信封；200 故意回非 JSON 体（HTML 网关错误页形态），触发 PARSE_FAILURE。 */
        private String faultBody(int status) {
            if (status >= 200 && status < 300) {
                return "<html>gateway returned a non-json body</html>";
            }
            return "{\"error\":{\"message\":\"injected " + status + "\"}}";
        }

        private String completionJson() {
            return "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"ok\"}}],"
                    + "\"usage\":{\"prompt_tokens\":3,\"completion_tokens\":2},\"model\":\""
                    + succeedForModel + "\"}";
        }

        private String extractModel(String requestJson) {
            try {
                return mapper.readTree(requestJson).path("model").asText("?");
            } catch (Exception e) {
                return "?";
            }
        }

        private String readBody(Request request) throws IOException {
            Buffer buffer = new Buffer();
            if (request.body() != null) {
                request.body().writeTo(buffer);
            }
            return buffer.readUtf8();
        }
    }

    @Test
    @DisplayName("状态码分类单源：408/5xx→LLM_TIMEOUT、429→限流、413→上下文超限、401/403 归参数面")
    void httpStatusClassificationIsSingleSourced() {
        assertEquals(AiErrorClass.LLM_TIMEOUT, LLMGatewayImpl.classifyHttpStatus(408));
        assertEquals(AiErrorClass.LLM_TIMEOUT, LLMGatewayImpl.classifyHttpStatus(502));
        assertEquals(AiErrorClass.LLM_TIMEOUT, LLMGatewayImpl.classifyHttpStatus(504));
        assertEquals(AiErrorClass.LLM_RATE_LIMITED, LLMGatewayImpl.classifyHttpStatus(429));
        assertEquals(AiErrorClass.CONTEXT_OVERFLOW, LLMGatewayImpl.classifyHttpStatus(413));
        assertEquals(AiErrorClass.INVALID_ARGUMENT, LLMGatewayImpl.classifyHttpStatus(401));
        assertNotNull(LLMGatewayImpl.classifyHttpStatus(418), "任何状态码都必须落到某一类，禁 null");
    }
}
