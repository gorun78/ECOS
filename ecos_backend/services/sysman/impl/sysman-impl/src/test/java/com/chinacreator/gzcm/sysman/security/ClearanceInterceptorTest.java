package com.chinacreator.gzcm.sysman.security;

import com.chinacreator.gzcm.sysman.dao.ClearanceLookupDao;
import com.chinacreator.gzcm.sysman.iam.context.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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

    private boolean preHandle(ClearanceLookupDao dao, String path) throws Exception {
        // 五档密级链已抽为属主单源（#142/B4-3e-81）：拦截器只保留准入兜底，判据取自同一份实现
        // 机构门面（#142/B4-3e-82）：默认替身返回空关联行集＝「无主机构」⇒ 机构级跳过，
        // 与本用例改前经 findOrgById 拿到空集的结果形态一致（本门不测机构腿，见 ClearanceInterceptorOrgResolutionTest）
        ClearanceInterceptor interceptor = new ClearanceInterceptor(dao,
                new com.chinacreator.gzcm.sysman.security.clearance.service.impl.SubjectClearanceQueryServiceImpl(dao),
                mock(com.chinacreator.gzcm.sysman.iam.service.IOrganizationService.class));
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

    private ClearanceLookupDao daoThrowingOnEveryQuery() {
        ClearanceLookupDao dao = mock(ClearanceLookupDao.class);
        when(dao.queryClearanceRows(any(ClearanceLookupDao.ClearanceRowKey.class), anyString(), any()))
                .thenThrow(new RuntimeException("security profile table unreachable"));
        when(dao.highestRoleClearanceLevel(anyString(), any()))
                .thenThrow(new RuntimeException("security profile table unreachable"));
        when(dao.globalDefaultClearanceLevel(any()))
                .thenThrow(new RuntimeException("security profile table unreachable"));
        return dao;
    }

    private ClearanceLookupDao daoWithUserLevel(int level) {
        ClearanceLookupDao dao = mock(ClearanceLookupDao.class);
        when(dao.queryClearanceRows(any(ClearanceLookupDao.ClearanceRowKey.class), anyString(), any()))
                .thenReturn(List.of(Map.of("clearance_level", level)));
        return dao;
    }

    @Test
    void anonymousCallerIsRejectedOnBusinessPlane() throws Exception {
        assertFalse(preHandle(daoWithUserLevel(4), "/api/v1/datanet/datasource"));
    }

    @Test
    void configuredLevelAdmitsL1PlaneButNotL3Plane() throws Exception {
        loginAs("u-test");
        // PATH_RULES: /api/v1/ -> L1，/api/v1/system/ -> L3
        assertTrue(preHandle(daoWithUserLevel(1), "/api/v1/datanet/datasource"));
        assertFalse(preHandle(daoWithUserLevel(1), "/api/v1/system/users"));
    }

    @Test
    void insufficientConfiguredLevelIsRejectedRegardlessOfPath() throws Exception {
        loginAs("u-test");
        assertFalse(preHandle(daoWithUserLevel(1), "/api/v1/system/users"));
        assertTrue(preHandle(daoWithUserLevel(3), "/api/v1/system/users"));
    }

    @Test
    void cascadeQueryFailureNeverReachesConfidentialPlane() throws Exception {
        loginAs("u-test");
        // 全级联查询异常 -> 兜底 L1：L3 平面必须仍被拒（fail-closed 方向不得随查询失败而放宽）
        assertFalse(preHandle(daoThrowingOnEveryQuery(), "/api/v1/system/users"));
    }

    @Test
    void healthPathStaysExempt() throws Exception {
        assertTrue(preHandle(daoThrowingOnEveryQuery(), "/api/health"));
    }
}
