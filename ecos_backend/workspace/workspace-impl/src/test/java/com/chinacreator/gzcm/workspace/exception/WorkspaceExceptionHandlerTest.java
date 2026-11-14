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

    /**
     * D50（2026-11-13 验证）：{@code BusinessException} 携带的状态行必须被采纳。
     *
     * <p><b>缺陷形态（活体实测）</b>：{@code POST /api/v1/workspace/scenarios/sc001/cognitive/diagnose}
     * 及同族 forecast/simulate/policy 三条腿全部返回
     * {@code 500 {"errorCode":"INTERNAL"}}，workspace 日志实为
     * {@code BusinessException: no_active_mind_for_scenario=sc001}；
     * 抛出点 {@code ScenarioCognitiveController#buildPayload} 写的是
     * {@code new BusinessException(404, 404, ...)}。本 advice 当时只有
     * {@code WorkspaceDomainException}/{@code ScenarioDomainException} 两条领域腿，
     * {@code BusinessException} 不在其继承链上 ⇒ 落 {@code handleUnexpected} → 500。</p>
     *
     * <p>「场景还没配 Mind」是调用方前置条件缺失，不是服务故障：报 500 会让前端弹系统错误
     * 并触发 5xx 告警，真正的修法（先绑 Mind）永远不会被提示。</p>
     */
    @Test
    @DisplayName("D50：BusinessException 的 404 必须落成 404，不得被兜底成 500")
    void businessExceptionKeepsItsOwnStatusLine() {
        com.chinacreator.gzcm.common.exception.BusinessException ex =
                new com.chinacreator.gzcm.common.exception.BusinessException(
                        404, 404, "no_active_mind_for_scenario=sc001");

        ResponseEntity<ApiResponse<Map<String, Object>>> resp = advice.handleDataBridge(ex);

        assertEquals(404, resp.getStatusCode().value(),
                "场景无激活 Mind 是前置条件缺失 ⇒ 404，不是 500");
        assertEquals("404", resp.getBody().getErrorCode(),
                "body code 必须与状态行同号（携带值 404）");
        assertTrue(resp.getBody().getMessage().contains("no_active_mind_for_scenario"),
                "业务文案必须原样透出——前端要靠它提示「先绑 Mind」");
    }

    @Test
    @DisplayName("D50 腿二：默认 400 的 BusinessException 落 400（不得一律升 500）")
    void businessExceptionDefaultsTo400() {
        var resp = advice.handleDataBridge(
                new com.chinacreator.gzcm.common.exception.BusinessException("invalid_mind_id: abc"));
        assertEquals(400, resp.getStatusCode().value(),
                "单参构造默认 400，是业务校验失败的标准落点");
    }

    @Test
    @DisplayName("D50 腿三：越界状态行由 common 单源回退 500（不在本 advice 另写区间判据）")
    void outOfRangeStatusFallsBackTo500() {
        var resp = advice.handleDataBridge(
                new com.chinacreator.gzcm.common.exception.DataBridgeException(200, 0, "骗人的 200"));
        assertEquals(500, resp.getStatusCode().value(),
                "200 不是合法错误状态 ⇒ 必须回退 500，禁把失败包装成成功");
    }

    @Test
    @DisplayName("D50 腿四：既有 404 腿（NotFoundException）不被父类分支抢走")
    void notFoundStillMapsTo404() {
        var resp = advice.handleNotFound(
                new com.chinacreator.gzcm.common.exception.NotFoundException("scenario 不存在: x"));
        assertEquals(404, resp.getStatusCode().value());
        expectNotFoundCode(resp.getBody());
    }

    private static void expectNotFoundCode(ApiResponse<Map<String, Object>> body) {
        assertEquals("NOT_FOUND", body.getErrorCode(),
                "既有 NotFoundException 腿的 errorCode 不得被 D50 新腿改写");
    }

    /**
     * D50 注册面腿：`DataBridgeException` 分支必须真的挂在 advice 上。
     *
     * <p><b>为什么必须有这条</b>：上面三条行为腿是<b>直接调用</b> {@code advice.handleDataBridge(..)}，
     * 绕过了 Spring 的分支选择——把方法上的 {@code @ExceptionHandler} 注解注掉后它们仍然全绿
     * （已用变异探针实测：注掉注解 → 21 条仍 0 失败）。而活体缺陷恰恰发生在「Spring 选哪个
     * handler」这一步：没有这条注册，异常就落 {@code handleUnexpected} → 500。
     * 因此本腿用反射直接钉住注册关系，与行为腿互补（一条管"选得中"，一条管"选中的行为对"）。</p>
     */
    @Test
    @DisplayName("D50 注册面：advice 上必须有面向 DataBridgeException 的 @ExceptionHandler")
    void dataBridgeHandlerIsActuallyRegistered() throws NoSuchMethodException {
        var method = WorkspaceExceptionHandler.class.getDeclaredMethod(
                "handleDataBridge", com.chinacreator.gzcm.common.exception.DataBridgeException.class);
        var ann = method.getAnnotation(
                org.springframework.web.bind.annotation.ExceptionHandler.class);

        assertNotNull(ann, "缺 @ExceptionHandler ⇒ Spring 永远不会选到该分支，BusinessException 回到 500（D50 复发）");
        assertTrue(java.util.Arrays.asList(ann.value())
                        .contains(com.chinacreator.gzcm.common.exception.DataBridgeException.class),
                "注册类型必须是 DataBridgeException（BusinessException 的父类），实得 "
                        + java.util.Arrays.toString(ann.value()));
    }

    /**
     * D50 优先级腿：`NotFoundException` 自己有一条更具体的腿。
     *
     * <p>Spring 选 handler 时按异常类型的<b>最近祖先</b>匹配，{@code NotFoundException} 的腿
     * 比父类 {@code DataBridgeException} 的腿更具体 ⇒ 仍走前者（errorCode=NOT_FOUND）。
     * 本腿钉住「两条腿都在、且各自注册在正确的类型上」，防止有人合并两条腿时改掉既有契约。</p>
     */
    @Test
    @DisplayName("D50 优先级：NotFoundException 仍有自己的腿（不被父类分支吸收）")
    void notFoundHandlerStaysMoreSpecific() throws NoSuchMethodException {
        var nf = WorkspaceExceptionHandler.class.getDeclaredMethod(
                "handleNotFound", com.chinacreator.gzcm.common.exception.NotFoundException.class);
        var db = WorkspaceExceptionHandler.class.getDeclaredMethod(
                "handleDataBridge", com.chinacreator.gzcm.common.exception.DataBridgeException.class);

        assertNotNull(nf.getAnnotation(org.springframework.web.bind.annotation.ExceptionHandler.class),
                "NotFound 专腿必须保留");
        assertNotNull(db.getAnnotation(org.springframework.web.bind.annotation.ExceptionHandler.class),
                "DataBridge 父类腿必须存在");
        assertTrue(com.chinacreator.gzcm.common.exception.DataBridgeException.class
                        .isAssignableFrom(com.chinacreator.gzcm.common.exception.NotFoundException.class),
                "前提：NotFound 是 DataBridge 子类，否则两腿互不影响、本腿失去意义");
    }
}
