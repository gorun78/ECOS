package com.chinacreator.gzcm.workspace.exception;

import com.chinacreator.gzcm.common.base.ApiResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.MDC;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 卷07 F07-13（W177 / C159）—— {@link WorkspaceExceptionHandler} 对 §四 C-5 映射表的逐行核对。
 *
 * <p>不启容器：直接调用 advice 方法，断言返回的 {@link ResponseEntity} 状态行
 * 与 body 的 code/errorCode/traceId/details。容器内的真实 dispatcher 语义由
 * {@code WorkspaceTestHarnessSmokeTest#springContextBootsInBothShapes} 承担。</p>
 */
class WorkspaceExceptionHandlerTest {

    private final WorkspaceExceptionHandler advice = new WorkspaceExceptionHandler();

    static Stream<Arguments> c5Rows() {
        return Stream.of(
                Arguments.of("ACTION_FIELDS_MISSING", 400,
                        new ActionValidationException(List.of("metric", "runMode"))),
                Arguments.of("SANDBOX_REF_FORBIDDEN", 400,
                        new SandboxReferenceForbiddenException("run-sb-1")),
                Arguments.of("PERMISSION_DENIED", 403,
                        new ForbiddenOperationException("scenario:s-1")),
                Arguments.of("ISLAND_BINDING", 409,
                        new IslandBindingException(List.of(Map.of("nodeId", "n-1")))),
                Arguments.of("ILLEGAL_TRANSITION", 409,
                        IllegalScenarioTransitionException.illegal("DRAFT", "COMPLETED")),
                Arguments.of("TERMINAL_STATE", 409,
                        IllegalScenarioTransitionException.terminal("COMPLETED")),
                Arguments.of("NO_FORMAL_RUN", 409,
                        IllegalScenarioTransitionException.noFormalRun()),
                Arguments.of("LAYOUT_CONFLICT", 409,
                        new OptimisticLockConflictException("s-1", "7")),
                Arguments.of("SCENARIO_NOT_FOUND", 404,
                        new ScenarioNotFoundException("s-1")),
                Arguments.of("COGNITIVE_UNAVAILABLE", 503,
                        new CognitiveEngineUnavailableException("engine down"))
        );
    }

    @ParameterizedTest(name = "C-5: {0} -> HTTP {1}")
    @MethodSource("c5Rows")
    @DisplayName("C-5 表每一行的领域异常都落到真状态码")
    void eachDomainExceptionMapsToItsHttpStatus(String expectedCode, int expectedStatus, Throwable ex) {
        ResponseEntity<ApiResponse<Map<String, Object>>> resp = advice.handleDomain(ex);

        assertEquals(expectedStatus, resp.getStatusCode().value(),
                ex.getClass().getSimpleName() + " 的状态行必须是真实码（禁 200 装 5xx）");
        assertEquals(HttpStatus.valueOf(expectedStatus), resp.getStatusCode());
        ApiResponse<Map<String, Object>> body = resp.getBody();
        assertNotNull(body, "响应体不能为空");
        assertEquals(expectedStatus, body.getCode(), "body.code 与状态行同源（A3）");
        assertEquals(expectedCode, body.getErrorCode());
        assertNotNull(body.getTraceId(), "traceId 必填（F07-21）");
        assertFalse(body.getData() == null || body.getData().isEmpty(),
                expectedCode + " 的 details 必须随体返回供前端渲染");
    }

    /** scenario 包既有的抛出侧载体（属主 Agent 的文件）必须被同一 advice 映射。 */
    @Test
    void scenarioPackageDomainExceptionsAreMappedToo() {
        ResponseEntity<ApiResponse<Map<String, Object>>> resp = advice.handleDomain(
                new com.chinacreator.gzcm.workspace.scenario.CognitiveEngineUnavailableException("x"));
        assertEquals(503, resp.getStatusCode().value());
        assertEquals("COGNITIVE_UNAVAILABLE", resp.getBody().getErrorCode());
    }

    @ParameterizedTest
    @CsvSource({
            "DataAccessException,jdbc:postgresql://secret-host:5432/sys_man password=hunter2",
            "HttpClientErrorException,401 Unauthorized on GET http://internal-host:18081/api/x",
            "NullPointerException,can not find bean com.chinacreator.gzcm.engine.ontology.XxxImpl"
    })
    @DisplayName("内部异常原文一律不入响应体（X-16 / X-41）")
    void internalExceptionMessageIsNeverInResponseBody(String kind, String secretMessage) {
        Exception ex = switch (kind) {
            case "DataAccessException" -> new DataAccessResourceFailureException(secretMessage);
            case "HttpClientErrorException" -> new ResourceAccessException(secretMessage);
            default -> new NullPointerException(secretMessage);
        };

        ResponseEntity<ApiResponse<Map<String, Object>>> resp = kind.equals("DataAccessException")
                ? advice.handleDataAccess((DataAccessException) ex)
                : advice.handleUnexpected(ex);

        assertEquals(500, resp.getStatusCode().value());
        String body = resp.getBody().getMessage() + " " + resp.getBody().getData();
        assertFalse(body.contains(secretMessage), "异常原文泄漏进响应体: " + body);
        assertFalse(body.contains("hunter2"), "凭据原文泄漏: " + body);
        assertFalse(body.contains("internal-host") || body.contains("secret-host"),
                "下游主机名泄漏: " + body);
        assertEquals("INTERNAL", resp.getBody().getErrorCode());
        assertNotNull(resp.getBody().getTraceId());
    }

    /** 兜底分支的 traceId 必须与入口 MDC 同源（前端凭此号回查日志）。 */
    @Test
    void traceIdComesFromEntryMdcNotSelfGenerated() {
        MDC.put("traceId", "unit-test-trace-1");
        try {
            ResponseEntity<ApiResponse<Map<String, Object>>> resp =
                    advice.handleUnexpected(new IllegalStateException("boom"));
            assertEquals("unit-test-trace-1", resp.getBody().getTraceId());
        } finally {
            MDC.remove("traceId");
        }
    }

    /** 下游 5xx（HttpClientErrorException 族）也走 500 固定文案，不复制下游状态码到 body 之外。 */
    @Test
    void downstreamHttpFailureIsSanitized() {
        HttpServerErrorException ex = HttpServerErrorException.create(
                HttpStatus.SERVICE_UNAVAILABLE, "gateway boom", null, null, null);
        ResponseEntity<ApiResponse<Map<String, Object>>> resp = advice.handleDownstream(ex);
        assertEquals(500, resp.getStatusCode().value());
        assertFalse(resp.getBody().getMessage().contains("boom"));
    }

    /** DuplicateKeyException 属数据层内部异常 → 500 + traceId，禁把 SQLState 入体。 */
    @Test
    void duplicateKeyIsInternalNotSqlDetail() {
        DuplicateKeyException ex = new DuplicateKeyException(
                "Duplicate entry 'X' for key 'uk_scenario' [SQL: SELECT ...secret...]");
        ResponseEntity<ApiResponse<Map<String, Object>>> resp = advice.handleDataAccess(ex);
        assertEquals(500, resp.getStatusCode().value());
        assertFalse(resp.getBody().getMessage().contains("uk_scenario"));
    }
}
