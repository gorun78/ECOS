package com.chinacreator.gzcm.sysman.security;

import com.chinacreator.gzcm.sysman.iam.context.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ClearanceInterceptorTest {

    /** 仅作 HandlerMethod 载体，无行为 */
    static class DummyHandler {
        public void handle() {
        }
    }

    private final HandlerMethod handlerMethod = new HandlerMethod(new DummyHandler(), findHandle());

    private static java.lang.reflect.Method findHandle() {
        try {
            return DummyHandler.class.getMethod("handle");
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(e);
        }
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    private void loginAs(String userId) {
        UserContext context = new UserContext();
        context.setUserId(userId);
        UserContext.setCurrent(context);
    }

    private boolean preHandle(JdbcTemplate jdbc, String path) throws Exception {
        ClearanceInterceptor interceptor = new ClearanceInterceptor(jdbc);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setRequestURI(path);
        MockHttpServletResponse response = new MockHttpServletResponse();
        return interceptor.preHandle(request, response, handlerMethod) ? true : assertForbidden(response);
    }

    private boolean assertForbidden(MockHttpServletResponse response) throws Exception {
        assertEquals(403, response.getStatus(), "拦截器判否时必须已写入 403");
        assertTrue(response.getContentAsString().contains("\"code\""));
        return false;
    }

    private JdbcTemplate jdbcThrowingOnEveryQuery() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), any(Object[].class)))
                .thenThrow(new RuntimeException("security profile table unreachable"));
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class)))
                .thenThrow(new RuntimeException("security profile table unreachable"));
        return jdbc;
    }

    private JdbcTemplate jdbcWithUserLevel(int level) {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of(Map.of("clearance_level", level)));
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class)))
                .thenReturn(null);
        return jdbc;
    }

    @Test
    void anonymousCallerIsRejectedOnBusinessPlane() throws Exception {
        assertFalse(preHandle(jdbcWithUserLevel(4), "/api/v1/datanet/datasource"));
    }

    @Test
    void configuredLevelAdmitsL1PlaneButNotL3Plane() throws Exception {
        loginAs("u-test");
        // PATH_RULES: /api/v1/ -> L1，/api/v1/system/ -> L3
        assertTrue(preHandle(jdbcWithUserLevel(1), "/api/v1/datanet/datasource"));
        assertFalse(preHandle(jdbcWithUserLevel(1), "/api/v1/system/users"));
    }

    @Test
    void insufficientConfiguredLevelIsRejectedRegardlessOfPath() throws Exception {
        loginAs("u-test");
        assertFalse(preHandle(jdbcWithUserLevel(1), "/api/v1/system/users"));
        assertTrue(preHandle(jdbcWithUserLevel(3), "/api/v1/system/users"));
    }

    @Test
    void cascadeQueryFailureNeverReachesConfidentialPlane() throws Exception {
        loginAs("u-test");
        // 全级联查询异常 -> 兜底 L1：L3 平面必须仍被拒（fail-closed 方向不得随查询失败而放宽）
        assertFalse(preHandle(jdbcThrowingOnEveryQuery(), "/api/v1/system/users"));
    }

    @Test
    void healthPathStaysExempt() throws Exception {
        assertTrue(preHandle(jdbcThrowingOnEveryQuery(), "/api/health"));
    }
}
