package com.chinacreator.gzcm.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EnvelopeMapperTest {

    private static final OperationRegistry.Operation OP = new OperationRegistry.Operation(
            "listCognitiveModels", "GET", "/api/v1/cognitive/models", "implemented", "supplementary-cognitive-ai-v1.yaml");

    @Test
    @DisplayName("ApiResponse code=0 → SUCCESS，data 原样透传，traceId 取后端")
    void successEnvelope() {
        var r = new HttpTransport.Result(200,
                "{\"code\":0,\"data\":[{\"modelId\":\"m1\"}],\"traceId\":\"t-1\"}", null, null);
        var out = EnvelopeMapper.map(OP.operationId(), OP, r, "req-t");
        assertEquals(true, out.get("ok"));
        assertEquals("SUCCESS", out.get("status"));
        assertEquals("t-1", out.get("trace_id"));
        assertTrue(out.get("data").toString().contains("m1"));
        assertEquals(null, out.get("error"));
    }

    @Test
    @DisplayName("code≠0 → FAILED，错误码取后端 errorCode")
    void businessFailure() {
        var r = new HttpTransport.Result(200,
                "{\"code\":500,\"errorCode\":\"E_X\",\"message\":\"bad\"}", null, null);
        var out = EnvelopeMapper.map(OP.operationId(), OP, r, "req-t");
        assertEquals(false, out.get("ok"));
        assertEquals("FAILED", out.get("status"));
        assertTrue(out.get("error").toString().contains("E_X"));
    }

    @Test
    @DisplayName("401 → BLOCKED（不是 FAILED/UNAVAILABLE），禁被折成成功")
    void unauthorized() {
        var out = EnvelopeMapper.map(OP.operationId(), OP,
                new HttpTransport.Result(401, "{\"code\":401,\"message\":\"expired\"}", null, null), "t");
        assertEquals("BLOCKED", out.get("status"));
        assertEquals(false, out.get("ok"));
        assertTrue(out.get("error").toString().contains("ECOS-CLI-AUTH"));
    }

    @Test
    @DisplayName("503 → UNAVAILABLE；连接失败同样 UNAVAILABLE（不许报 SUCCESS）")
    void dependencyUnavailable() {
        assertEquals("UNAVAILABLE", EnvelopeMapper.map(OP.operationId(), OP,
                new HttpTransport.Result(503, "", null, null), "t").get("status"));
        assertEquals("UNAVAILABLE", EnvelopeMapper.map(OP.operationId(), OP,
                new HttpTransport.Result(-1, null, null, "ConnectException: refused"), "t").get("status"));
    }

    @Test
    @DisplayName("非 ApiResponse 信封（actuator health）→ SUCCESS + 原样 data")
    void frameworkHealthShape() {
        var out = EnvelopeMapper.map("getGatewayHealth",
                new OperationRegistry.Operation("getGatewayHealth", "GET", "/actuator/health", "framework", "platform-v1.yaml"),
                new HttpTransport.Result(200, "{\"status\":\"UP\"}", null, null), "t");
        assertEquals("SUCCESS", out.get("status"));
        assertEquals("UP", ((java.util.Map<?, ?>) out.get("data")).get("status"));
    }

    @Test
    @DisplayName("输出顶层固定六字段，且不含请求头/凭据")
    void outputShapeAndNoCredentialLeak() {
        var out = EnvelopeMapper.map(OP.operationId(), OP,
                new HttpTransport.Result(200, "{\"code\":0,\"data\":{}}", null, null), "t");
        assertEquals(java.util.List.of("ok", "status", "data", "error", "trace_id", "source_refs"),
                java.util.List.copyOf(out.keySet()));
        assertFalse(out.toString().toLowerCase().contains("authorization"));
        assertFalse(out.toString().toLowerCase().contains("bearer"));
    }

    @Test
    @DisplayName("非法参数 → CliException(USAGE)")
    void usageErrors() {
        var e = assertThrows(CliException.class,
                () -> CliOptions.parse(new String[] {"doctor", "--output", "xml"}, 1));
        assertEquals(ExitCodes.USAGE, e.exitCode());
    }
}
