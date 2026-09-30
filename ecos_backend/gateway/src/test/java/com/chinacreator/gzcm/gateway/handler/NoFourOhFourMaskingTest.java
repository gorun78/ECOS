package com.chinacreator.gzcm.gateway.handler;

import java.lang.reflect.Method;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.data.DataDomainErrorCode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F02-08 规格 1（P0 验收对 NoFourOhFourMaskingTest）— 单元测试（无 Spring 上下文）。
 *
 * <p>断言 {@link GlobalExceptionHandler} 的手语义契约：</p>
 * <ul>
 *   <li>真实路由不存在（{@link NoResourceFoundException} / {@link NoHandlerFoundException}）→ 404；</li>
 *   <li>Spring DAO 家族 SQL 失败（{@link BadSqlGrammarException}）→ 500 + errorCode={@code ECOS-DATA-031} + traceId；</li>
 *   <li>未识别兜底（任意 RuntimeException）→ 500（<b>不再</b> 404 掩蔽）+ errorCode={@code ECOS-SYS-500}。</li>
 * </ul>
 *
 * <p>两个维度分别断言：</p>
 * <ol>
 *   <li>HTTP 状态 = {@code @ResponseStatus} 注解值（决定响应链路状态行）；</li>
 *   <li>{@code ApiResponse.code}（前端判定字段）与 HTTP 状态一致，且 body 不含技术细节。</li>
 * </ol>
 */
class NoFourOhFourMaskingTest {

    private static final String TRACE_ID = "tc-test-0001";

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
        MDC.put("traceId", TRACE_ID);
    }

    @Test
    void handleAny_illegalStateException_returns500Not404() {
        IllegalStateException in = new IllegalStateException("boom");
        ApiResponse<Void> resp = handler.handleAny(in);

        assertNotNull(resp, "handleAny 必须返回 ApiResponse");
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), resp.getCode(),
                "兜底 handler 的 ApiResponse.code 必须 = 500（F02-08 规格 1：不再 404 掩蔽）");
        assertResponseStatus("handleAny", HttpStatus.INTERNAL_SERVER_ERROR);

        // body 不暴露技术栈 / exp 类名
        String msg = resp.getMessage();
        assertNotNull(msg);
        assertTrue(!msg.contains("IllegalStateException") && !msg.contains("boom"),
                "body errMsg 不得含技术细节类名/异常 msg，实际=" + msg);
    }

    @Test
    void handleAny_genericRuntimeException_returns500WithSys500() {
        Exception in = new RuntimeException("simulated-internal");
        ApiResponse<Void> resp = handler.handleAny(in);
        assertEquals(500, resp.getCode(), "任意 RuntimeException 兜底 = 500");
        assertResponseStatus("handleAny", HttpStatus.INTERNAL_SERVER_ERROR);
        assertEquals("ECOS-SYS-500", readErrorCodeViaReflection(resp),
                "兜底 errorCode 必须 = ECOS-SYS-500");
        // msg 不暴露异常类名/原始 msg
        String msg = resp.getMessage();
        assertNotNull(msg);
        assertTrue(!msg.contains("simulated-internal") && !msg.contains("RuntimeException"),
                "body errMsg 不得含技术细节，实际=" + msg);
    }

    @Test
    void handleRouteNotFound_noResourceFound_returns404() {
        NoResourceFoundException in =
                new NoResourceFoundException(HttpMethod.GET, "/doesnotexist");
        ApiResponse<Void> resp = handler.handleRouteNotFound(in);
        assertNotNull(resp);
        assertEquals(404, resp.getCode(), "真实路由不存在 → 404（F02-08 规格 1）");
        assertResponseStatus("handleRouteNotFound", HttpStatus.NOT_FOUND);
        assertNotNull(resp.getTraceId(), "route 404 也应附 traceId（可选但期望有）");
        if (resp.getTraceId() != null) {
            assertEquals(TRACE_ID, resp.getTraceId());
        }
    }

    @Test
    void handleRouteNotFound_noHandlerFound_returns404() {
        NoHandlerFoundException in = new NoHandlerFoundException(
                HttpMethod.GET.name(), "/nohandler", new org.springframework.http.HttpHeaders());
        ApiResponse<Void> resp = handler.handleRouteNotFound(in);
        assertNotNull(resp);
        assertEquals(404, resp.getCode());
        assertResponseStatus("handleRouteNotFound", HttpStatus.NOT_FOUND);
    }

    @Test
    void handleSpringDataAccess_sqlQueryFailureMapsTo500WithData031AndTraceId() {
        // 用具体 DataAccessException 子类（MyBatisSystemException 同层）构造 DB 失败场景。
        // 注：gateway 模块 test classpath 未引入 spring-jdbc，故用 DataAccessException 根类
        // 直接构造（handler 本身即按 DataAccessException 根匹配），等价覆盖
        // BadSqlGrammarException / MyBatisSystemException 的处理路径。
        DataAccessException in = new DataAccessException("Column not found: 'x'") {
            private static final long serialVersionUID = 1L;
        };
        ApiResponse<Void> resp = handler.handleSpringDataAccess(in);
        assertNotNull(resp);
        assertEquals(500, resp.getCode(), "Spring DAO family → 500（F02-08 规格 1：不得再落 404）");
        // ApiResponse 无 getErrorCode() 公开访问器（common-api 本批次归其他 agent，禁改）；
        // 走反射读取 private errorCode 字段，验证 handler 设置了正确的 ECOS-DATA-031。
        String ec = readErrorCodeViaReflection(resp);
        assertEquals(DataDomainErrorCode.ECOS_DATA_031, ec,
                "响应 errorCode 字段必须 = " + DataDomainErrorCode.ECOS_DATA_031);
        assertResponseStatus("handleSpringDataAccess", HttpStatus.INTERNAL_SERVER_ERROR);
        // traceId 从 MDC 读回
        assertNotNull(resp.getTraceId(), "DB/MyBatis 失败响应体必须附 traceId");
        assertEquals(TRACE_ID, resp.getTraceId());
        // msg 不暴露 SQL 细节
        String msg = resp.getMessage();
        assertNotNull(msg);
        assertTrue(!msg.contains("Column not found") && !msg.toLowerCase().contains("column"),
                "body 不得含 SQL/堆栈细节，实际=" + msg);
    }

    /** 反射读取 ApiResponse 的 private errorCode 字段（common-api 未暴露 accessor）。 */
    private static String readErrorCodeViaReflection(ApiResponse<?> resp) {
        try {
            java.lang.reflect.Field f = resp.getClass().getDeclaredField("errorCode");
            f.setAccessible(true);
            Object v = f.get(resp);
            return v == null ? null : v.toString();
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new AssertionError("无法读取 ApiResponse.errorCode 字段", e);
        }
    }

    // ── 辅助：从 handler.method 读 @ResponseStatus 注解值并断言 ─────────────────────

    private void assertResponseStatus(String methodName, HttpStatus expected) {
        try {
            Method[] methods = handler.getClass().getDeclaredMethods();
            boolean found = false;
            for (Method m : methods) {
                if (m.getName().equals(methodName)) {
                    ResponseStatus ann = m.getAnnotation(ResponseStatus.class);
                    assertNotNull(ann, methodName + " 必须声明 @ResponseStatus");
                    assertEquals(expected, ann.value(),
                            methodName + " 的 @ResponseStatus 必须 = " + expected);
                    found = true;
                    break;
                }
            }
            assertTrue(found, "未找到 handler 方法 " + methodName);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}
