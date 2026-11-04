package com.chinacreator.gzcm.sysman.security;

import com.chinacreator.gzcm.sysman.audit.EventBusAuditPublisher;
import com.chinacreator.gzcm.sysman.dao.ClearanceLookupDao;
import com.chinacreator.gzcm.sysman.iam.context.UserContext;
import com.chinacreator.gzcm.sysman.security.clearance.service.impl.SubjectClearanceQueryServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.method.HandlerMethod;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
 *   <li>超管访问 L3 平面放行（业务能力保留）<b>且</b>恰好递交一条 super_admin_bypass 审计；</li>
 *   <li>旁路不依赖 clearance 级联查询（未走 jdbc）；</li>
 *   <li>审计 payload 含 eventType=super_admin_bypass / result=BYPASSED / source=sysman-clearance-interceptor；</li>
 *   <li>非超管不受影响：不触发超管旁路审计（回归）；</li>
 *   <li>投递失败不阻断旁路：该保证由投递口 {@link EventBusAuditPublisher} 内部吞异常承担，
 *       由 {@code EventBusAuditPublisherGateTest} 钉（#147AQ 起拦截器不再自己兜一层）。</li>
 * </ul>
 *
 * <p>#147AQ：本类原先 mock {@code ObjectProvider<Object>} 来"证明"审计送达——那正是缺陷躲过单测的原因：
 * 容器按声明类型 {@code Object} 解析候选必然失败，而 mock 直接返回替身，永远造不出解析失败。
 * 现在替身换成本模块的投递口；解析正确性由投递口自己的门禁（真实 {@code ApplicationContext} +
 * 多枚 {@code @Primary} 诱饵 + 旧写法对照断言）负责。</p>
 *
 * <p>验收：{@code mvn -pl services/sysman/impl/sysman-impl -am test -Dtest='SuperAdminBypassAuditTest'}</p>
 */
class SuperAdminBypassAuditTest {

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

    /** 放行路径用的 DAO：所有 clearance 查询都成功返回低等级（证明非超管不靠旁路，超管不到这步）。 */
    private ClearanceLookupDao daoLevel(int level) {
        ClearanceLookupDao dao = mock(ClearanceLookupDao.class);
        when(dao.queryClearanceRows(any(ClearanceLookupDao.ClearanceRowKey.class), anyString(), any()))
                .thenReturn(List.of(Map.of("clearance_level", level)));
        return dao;
    }

    private boolean preHandle(ClearanceInterceptor interceptor, String path) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setRequestURI(path);
        MockHttpServletResponse response = new MockHttpServletResponse();
        return interceptor.preHandle(request, response, HANDLER);
    }

    /**
     * 拦截器装配替身。
     * <p>存在理由（#142/B4-3e-81）：五档密级链已从拦截器抽到 sysman 属主单源，本用例只测超管旁路
     * 与审计腿，判定链必须走<b>同一份真实实现</b>（只把 DAO 换成替身），禁再造一份内联级联假象。
     * <p>机构门面（#142/B4-3e-82）给的是 Mockito 默认替身——它返 {@code List} 的方法默认给空集，
     * 于是属主判据得出「无主机构」⇒ 机构级档整体跳过。本组用例断言的是旁路与审计腿，
     * 机构档读数与它无关，但装配面必须显式给（缺参即编译不过＝防「未装配折叠腿」再现）。
     */
    private ClearanceInterceptor bypassInterceptor(ClearanceLookupDao dao, EventBusAuditPublisher publisher) {
        return new ClearanceInterceptor(dao, publisher, new SubjectClearanceQueryServiceImpl(dao),
                mock(com.chinacreator.gzcm.sysman.iam.service.IOrganizationService.class));
    }

    @Test
    @DisplayName("超管访问 L3 平面放行，并恰好递交一条 ecos.audit 留痕（BYPASSED）")
    void superAdminBypassAdmitsAndAudits() throws Exception {
        EventBusAuditPublisher publisher = mock(EventBusAuditPublisher.class);
        ClearanceLookupDao dao = daoLevel(0);
        ClearanceInterceptor interceptor = bypassInterceptor(dao, publisher);

        setSuperAdmin("root");
        loginAsL1();

        assertTrue(preHandle(interceptor, "/api/v1/system/users"),
                "超管旁路应仍放行 L3 平面（业务能力保留）");

        verify(publisher, times(1)).publish(any(Map.class));
        // 旁路发生在 clearance 级联之前，不应触发 clearance 查询
        verify(dao, never()).queryClearanceRows(any(ClearanceLookupDao.ClearanceRowKey.class), anyString(), any());
    }

    @Test
    @DisplayName("审计 payload 关键字段：eventType/result/source 与 path/requiredLevel")
    @SuppressWarnings({"unchecked", "rawtypes"})
    void auditPayloadHasRequiredFields() throws Exception {
        EventBusAuditPublisher publisher = mock(EventBusAuditPublisher.class);
        ClearanceInterceptor interceptor = bypassInterceptor(daoLevel(0), publisher);

        setSuperAdmin("admin1");
        loginAsL1();

        preHandle(interceptor, "/api/v1/system/users");

        ArgumentCaptor<Map> cap = ArgumentCaptor.forClass(Map.class);
        verify(publisher).publish(cap.capture());
        Map<?, ?> payload = cap.getValue();
        assertEquals("super_admin_bypass", payload.get("eventType"));
        assertEquals("BYPASSED", payload.get("result"));
        assertEquals("sysman-clearance-interceptor", payload.get("source"));
        assertEquals("/api/v1/system/users", payload.get("resource"));
    }

    @Test
    @DisplayName("无投递口（兼容装配）：不阻断旁路，仍放行")
    void auditUnavailableDoesNotBlockBypass() throws Exception {
        ClearanceLookupDao dao = daoLevel(0);
        ClearanceInterceptor interceptor = new ClearanceInterceptor(dao,
                new SubjectClearanceQueryServiceImpl(dao),
                mock(com.chinacreator.gzcm.sysman.iam.service.IOrganizationService.class));

        setSuperAdmin("admin2");
        loginAsL1();

        assertTrue(preHandle(interceptor, "/api/v1/system/users"),
                "投递口缺席时旁路仍应放行（仅降级日志），业务不受影响");
    }

    @Test
    @DisplayName("非超管：不触发旁路、不发审计，仍按 clearance 判定")
    void regularUserDoesNotTriggerBypassAudit() throws Exception {
        EventBusAuditPublisher publisher = mock(EventBusAuditPublisher.class);
        // 用户等级 L1，L3 平面 → 应被拒
        ClearanceInterceptor interceptor = bypassInterceptor(daoLevel(1), publisher);

        setRegularUser("u-regular");
        loginAsL1();

        assertFalse(preHandle(interceptor, "/api/v1/system/users"),
                "非超管 L1 访问 L3 平面应被拒（旁路仅超管）");
        verify(publisher, never()).publish(any(Map.class));
    }

    private void loginAsL1() {
        UserContext context = new UserContext();
        context.setUserId("u-test");
        UserContext.setCurrent(context);
    }
}
