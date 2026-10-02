package com.chinacreator.gzcm.workspace.exception;

import com.chinacreator.gzcm.common.base.ApiResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.dao.DataAccessException;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * F07-13 域异常 → 真实 HTTP 状态码 映射单测（详细设计-07 验收 `eachDomainExceptionMapsToItsHttpStatus`）。
 *
 * <p>C-5 表核心 9 类领域异常，每条断言 {@code ApiResponse.code(int)} = 该异常在 C-5 表约定的真实状态码，
 * 断言 {@code code != 200}（禁"200-swallow + body.code 伪装"，A3 红线）。另对
 * {@link DataAccessException} 与通用 {@link Exception} 兜底，断言内部原文不入体。</p>
 */
class WorkspaceExceptionHandlerTest {

    private final WorkspaceExceptionHandler advice = new WorkspaceExceptionHandler();

    // (异常示例, 期望 HTTP 状态码, 期望 body.code int 值一致——C-5 表字符串码 → int 由 @ResponseStatus 与
    //  ApiResponse.error(int code, ...) 双同步。)
    static Stream<Arguments> domainExceptionToStatus() {
        return Stream.of(
            Arguments.of(new ActionValidationException("missing"), 400),
            Arguments.of(new SandboxReferenceForbiddenException("sandbox ref"), 400),
            Arguments.of(new ForbiddenOperationException("no perm"), 403),
            Arguments.of(new IslandBindingException("islands", List.of(
                new IslandBindingException.IslandItem("b1", "x", "DATASET", "isolated"))), 409),
            Arguments.of(new IllegalScenarioTransitionException("ILLEGAL_TRANSITION", "illegal"), 409),
            Arguments.of(new OptimisticLockConflictException("conflict"), 409),
            Arguments.of(new ScenarioNotFoundException("nope"), 404),
            Arguments.of(new CognitiveEngineUnavailableException("eng down"), 503),
            Arguments.of(new ExternalServiceUnavailableException("sec", "down"), 503)
        );
    }

    @ParameterizedTest
    @MethodSource("domainExceptionToStatus")
    void eachDomainExceptionMapsToItsHttpStatus(RuntimeException ex, int expectedStatus) {
        @SuppressWarnings("unchecked")
        ApiResponse<?> body = dispatch(ex);
        assertEquals(expectedStatus, body.getCode(),
            "body.code(int) 与 C-5 表状态码须一致（" + ex.getClass().getSimpleName() + "）");
        assertFalse(200 == body.getCode(), "禁 200-swallow 伪装");
        if (ex instanceof IslandBindingException) {
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) body.getData();
            assertEquals(1, ((List<?>) data.get("islands")).size(), "岛屿清单须透传");
        }
    }

    @Test
    void cognitiveUnavailableResponseCarriesFixedMessageNotStack() {
        CognitiveEngineUnavailableException ex =
            new CognitiveEngineUnavailableException("eng down", new RuntimeException("SecretStackTraceText"));
        // body.code 503 且原文不含 cause 消息
        @SuppressWarnings("unchecked")
        ApiResponse<Void> body = advice.handleCognitiveUnavailable(ex);
        assertEquals(503, body.getCode());
        String msg = body.getMessage() == null ? "" : body.getMessage();
        assertFalse(msg.contains("SecretStackTraceText"), "cause 原文不得入体");
    }

    @Test
    void dataAccessExceptionKeeps500AndHidesSqlText() {
        String secret = "SELECT secret_column FROM internal_table WHERE pass='supersecret'";
        org.springframework.dao.InvalidDataAccessApiUsageException ex =
            new org.springframework.dao.InvalidDataAccessApiUsageException(secret);
        @SuppressWarnings("unchecked")
        ApiResponse<Void> body = advice.handleDataAccess(ex);
        assertEquals(500, body.getCode());
        String msg = body.getMessage() == null ? "" : body.getMessage();
        assertFalse(msg.contains("internal_table"), "内部表名不得入体");
        assertFalse(msg.contains("supersecret"), "内部内容不得入体");
    }

    @Test
    void genericExceptionYields500FixedMessage() {
        RuntimeException ex = new RuntimeException("stacktrace-secret-should-be-hidden");
        @SuppressWarnings("unchecked")
        ApiResponse<Void> body = advice.handleAny(ex);
        assertEquals(500, body.getCode());
        String msg = body.getMessage() == null ? "" : body.getMessage();
        assertFalse(msg.contains("stacktrace-secret"), "内部异常原文必须被固定文案替换");
    }

    @SuppressWarnings("rawtypes")
    private ApiResponse dispatch(RuntimeException ex) {
        if (ex instanceof ActionValidationException e) return advice.handleActionValidation(e);
        if (ex instanceof SandboxReferenceForbiddenException e) return advice.handleSandboxReference(e);
        if (ex instanceof ForbiddenOperationException e) return advice.handleForbidden(e);
        if (ex instanceof IslandBindingException e) return advice.handleIslandBinding(e);
        if (ex instanceof IllegalScenarioTransitionException e) return advice.handleIllegalTransition(e);
        if (ex instanceof OptimisticLockConflictException e) return advice.handleLayoutConflict(e);
        if (ex instanceof ScenarioNotFoundException e) return advice.handleScenarioNotFound(e);
        if (ex instanceof CognitiveEngineUnavailableException e) return advice.handleCognitiveUnavailable(e);
        if (ex instanceof ExternalServiceUnavailableException e) return advice.handleExternalUnavailable(e);
        throw new IllegalArgumentException("unmapped: " + ex.getClass());
    }
}
