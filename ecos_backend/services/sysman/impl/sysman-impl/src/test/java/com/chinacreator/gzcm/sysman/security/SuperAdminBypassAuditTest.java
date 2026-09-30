package com.chinacreator.gzcm.sysman.security;

import com.chinacreator.gzcm.sysman.iam.context.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.method.HandlerMethod;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 详细设计-01 F01-08 / W40 — 超管旁路强审计门禁。
 *
 * <p>既有 P0-1 语义：{@code ROLE_SUPER_ADMIN} 在 clearance 等级判定之前放行（跨租户遥测视角）。
 * W40 关闭"无审计旁路"越权面：旁路保留，但<b>每次</b>必须向 {@code ecos.audit} 发留痕事件。
 * 断言：</p>
 * <ul>
 *   <li>超管访问 L3 平面放行（业务能力保留）<b>且</b>发布 super_admin_bypass 审计到 ecos.audit；</li>
 *   <li>旁路不依赖 clearance 级联查询（未走 jdbc）；</li>
 *   <li>审计 payload 含 eventType=super_admin_bypass / result=BYPASSED / source=sysman-clearance-interceptor；</li>
 *   <li>非超管不受影响：不触发超管旁路审计（回归）；</li>
 *   <li>审计发布失败不阻断旁路（发布抛错仍放行，仅 WARN）。</li>
 * </ul>
 *
 * <p>验收：{@code mvn -pl services/sysman/impl/sysman-impl -am test -Dtest='SuperAdminBypassAuditTest'}</p>
 */
class SuperAdminBypassAuditTest {

    /** 事件总线最小接口替身（反射调用的目标方法签名，避免编译期硬依赖 runtime-event）。 */
    interface FakeEventBus {
        void publish(String topic, Object payload);
    }

    /** 仅作 HandlerMethod 载体，无行为 */
    static class DummyHandler {
        public void handle() {
        }
    }

    private static final HandlerMethod HANDLER =
            new HandlerMethod(new DummyHandler(), findHandle());

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
        SecurityContextHolder.clearContext();
    }

    private void setSuperAdmin(String principal) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, "n/a",
                        List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"))));
    }

    private void setRegularUser(String principal) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, "n/a",
                        List.of(new SimpleGrantedAuthority("ROLE_ANALYST"))));
    }

    /** 放行路径用的 jdbc：所有 clearance 查询都成功返回低等级（证明非超管不靠旁路，超管不到这步）。 */
    private JdbcTemplate jdbcLevel(int level) {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of(Map.of("clearance_level", level)));
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class)))
                .thenReturn(null);
        return jdbc;
    }

    private boolean preHandle(ClearanceInterceptor interceptor, String path) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setRequestURI(path);
        MockHttpServletResponse response = new MockHttpServletResponse();
        return interceptor.preHandle(request, response, HANDLER);
    }

    @Test
    @DisplayName("超管访问 L3 平面放行，并按触发 publish 到 ecos.audit（BYPASSED 留痕）")
    void superAdminBypassAdmitsAndAudits() throws Exception {
        FakeEventBus bus = mock(FakeEventBus.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<Object> provider = (ObjectProvider<Object>) mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(bus);
        JdbcTemplate jdbc = jdbcLevel(0);
        ClearanceInterceptor interceptor = new ClearanceInterceptor(jdbc, provider);

        setSuperAdmin("root");
        loginAsL1();

        assertTrue(preHandle(interceptor, "/api/v1/system/users"),
                "超管旁路应仍放行 L3 平面（业务能力保留）");

        verify(bus, times(1)).publish(eq("ecos.audit"), any(Object.class));
        // 旁路发生在 clearance 级联之前，不应触发 clearance 查询
        verify(jdbc, never()).queryForList(anyString(), any(Object[].class));
    }

    @Test
    @DisplayName("审计 payload 关键字段：eventType/result/source 与 path/requiredLevel")
    void auditPayloadHasRequiredFields() throws Exception {
        FakeEventBus bus = mock(FakeEventBus.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<Object> provider = (ObjectProvider<Object>) mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(bus);
        ClearanceInterceptor interceptor = new ClearanceInterceptor(jdbcLevel(0), provider);

        setSuperAdmin("admin1");
        loginAsL1();

        preHandle(interceptor, "/api/v1/system/users");

        org.mockito.ArgumentCaptor<Object> cap = org.mockito.ArgumentCaptor.forClass(Object.class);
        verify(bus).publish(eq("ecos.audit"), cap.capture());
        Map<?, ?> payload = (Map<?, ?>) cap.getValue();
        org.junit.jupiter.api.Assertions.assertEquals("super_admin_bypass", payload.get("eventType"));
        org.junit.jupiter.api.Assertions.assertEquals("BYPASSED", payload.get("result"));
        org.junit.jupiter.api.Assertions.assertEquals("sysman-clearance-interceptor", payload.get("source"));
        org.junit.jupiter.api.Assertions.assertEquals("/api/v1/system/users", payload.get("resource"));
    }

    @Test
    @DisplayName("审计不可用（provider 空）不阻断旁路：仍放行")
    void auditUnavailableDoesNotBlockBypass() throws Exception {
        @SuppressWarnings("unchecked")
        ObjectProvider<Object> provider = (ObjectProvider<Object>) mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        ClearanceInterceptor interceptor = new ClearanceInterceptor(jdbcLevel(0), provider);

        setSuperAdmin("admin2");
        loginAsL1();

        assertTrue(preHandle(interceptor, "/api/v1/system/users"),
                "事件总线不可用时旁路仍应放行（仅降级 WARN），业务不受影响");
    }

    @Test
    @DisplayName("审计发布抛错不阻断旁路：仍放行（fail-open on audit, fail-closed 仅适用于判定异常）")
    void auditPublishFailureDoesNotBlockBypass() throws Exception {
        FakeEventBus bus = mock(FakeEventBus.class);
        doThrow(new RuntimeException("kafka down")).when(bus).publish(anyString(), any());
        @SuppressWarnings("unchecked")
        ObjectProvider<Object> provider = (ObjectProvider<Object>) mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(bus);
        ClearanceInterceptor interceptor = new ClearanceInterceptor(jdbcLevel(0), provider);

        setSuperAdmin("admin3");
        loginAsL1();

        assertTrue(preHandle(interceptor, "/api/v1/system/users"),
                "审计发布失败不得阻断超管旁路");
    }

    @Test
    @DisplayName("非超管：不触发旁路、不发审计，仍按 clearance 判定")
    void regularUserDoesNotTriggerBypassAudit() throws Exception {
        FakeEventBus bus = mock(FakeEventBus.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<Object> provider = (ObjectProvider<Object>) mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(bus);
        // 用户等级 L1，L3 平面 → 应被拒
        ClearanceInterceptor interceptor = new ClearanceInterceptor(jdbcLevel(1), provider);

        setRegularUser("u-regular");
        loginAsL1();

        assertFalse(preHandle(interceptor, "/api/v1/system/users"),
                "非超管 L1 访问 L3 平面应被拒（旁路仅超管）");
        verify(bus, never()).publish(anyString(), any());
    }

    private void loginAsL1() {
        UserContext context = new UserContext();
        context.setUserId("u-test");
        UserContext.setCurrent(context);
    }
}
