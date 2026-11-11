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
    @DisplayName("trace_id 优先级：响应头 > 响应体 traceId > 本地请求 trace（§三.2 审计对齐前提）")
    void traceIdPrecedence() {
        String bodyWithTrace = "{\"code\":0,\"data\":{},\"traceId\":\"body-trace\"}";
        String bodyNoTrace = "{\"code\":0,\"data\":{}}";

        // 头与体都有 ⇒ 取响应头（后端过滤器写入的那个才是审计落库值）
        assertEquals("header-trace", EnvelopeMapper.map(OP.operationId(), OP,
                new HttpTransport.Result(200, bodyWithTrace, "header-trace", null), "cli-req").get("trace_id"));
        // 仅头 ⇒ 取头
        assertEquals("header-only", EnvelopeMapper.map(OP.operationId(), OP,
                new HttpTransport.Result(200, bodyNoTrace, "header-only", null), "cli-req").get("trace_id"));
        // 仅体 ⇒ 取体（后端未回写头时的兜底）
        assertEquals("body-trace", EnvelopeMapper.map(OP.operationId(), OP,
                new HttpTransport.Result(200, bodyWithTrace, null, null), "cli-req").get("trace_id"));
        // 都没有 ⇒ 回退本地请求 trace（保证输出字段永不为空）
        assertEquals("cli-req", EnvelopeMapper.map(OP.operationId(), OP,
                new HttpTransport.Result(200, bodyNoTrace, null, null), "cli-req").get("trace_id"));
        // 头为空串等同缺失，不得把空串当 trace（否则审计对不上）
        assertEquals("cli-req", EnvelopeMapper.map(OP.operationId(), OP,
                new HttpTransport.Result(200, bodyNoTrace, "   ", null), "cli-req").get("trace_id"));
    }

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
    @DisplayName("L3 面 403（越权/过期/自审批）→ FAILED(1)，错误码取后端 errorCode；不得降成 BLOCKED")
    void approvalBearingForbiddenIsFailed() {
        var r = new HttpTransport.Result(403,
                "{\"code\":403,\"errorCode\":\"E-POLICY\",\"message\":\"当前权限不支持该操作\"}", null, null);
        var out = EnvelopeMapper.map(OP.operationId(), OP, r, "t", true);
        assertEquals("FAILED", out.get("status"));
        assertEquals(false, out.get("ok"));
        assertTrue(out.get("error").toString().contains("E-POLICY"));
        assertEquals(ExitCodes.FAILED,
                ExitCodes.forStatus(CliStatus.valueOf(String.valueOf(out.get("status")))));
    }

    @Test
    @DisplayName("L3 面 409（同凭证重放/已消费）→ FAILED(1)，不得当作 SUCCESS 或 UNAVAILABLE")
    void approvalBearingConflictIsFailed() {
        var r = new HttpTransport.Result(409,
                "{\"code\":409,\"errorCode\":\"E-WA-STATE\",\"message\":\"凭证已消费\"}", null, null);
        var out = EnvelopeMapper.map(OP.operationId(), OP, r, "t", true);
        assertEquals("FAILED", out.get("status"));
        assertTrue(out.get("error").toString().contains("E-WA-STATE"));
    }

    @Test
    @DisplayName("只读面 403 仍按设计册 §16.4 归 BLOCKED（L3 专有语义不外溢）")
    void readForbiddenStaysBlocked() {
        var r = new HttpTransport.Result(403,
                "{\"code\":403,\"errorCode\":\"E-POLICY\",\"message\":\"denied\"}", null, null);
        assertEquals("BLOCKED", EnvelopeMapper.map(OP.operationId(), OP, r, "t", false).get("status"));
        assertEquals("BLOCKED", EnvelopeMapper.map(OP.operationId(), OP, r, "t").get("status"));
    }

    @Test
    @DisplayName("非 ApiResponse 信封（actuator health）→ SUCCESS + 原样 data")
    void frameworkHealthShape() {        var out = EnvelopeMapper.map("getGatewayHealth",
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
