package com.chinacreator.gzcm.sysman.security;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.exception.DataBridgeException;
import com.chinacreator.gzcm.common.exception.ForbiddenException;
import com.chinacreator.gzcm.common.exception.UnauthorizedException;
import com.chinacreator.gzcm.sysman.abac.model.AbacPolicy;
import com.chinacreator.gzcm.sysman.abac.service.IAbacPolicyService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@code @RequirePermission} 切面拒绝腿的<b>形态门</b>（#142/B4-3e-36，收口差距 D-B4-59 的
 * AOP 腿；与判据侧 {@code SecurityDenialHttpStatusRatchetTest} 成对，缺一即半只眼）。
 *
 * <h3>本门要钉住的缺陷本体</h3>
 * <p>切面旧形态在拒绝时 {@code return ApiResponse.unauthorized(...)}／{@code forbidden(...)}：
 * ①{@code ApiResponse} 没有任何 HTTP 能力 ⇒ 状态行恒 200，「无权限」在传输层与成功同形
 * （§4.7.2 要求分态）；②{@code @Around} 的返回值由调用点按<b>被拦截方法的声明类型</b>消费，
 * 端点一旦折成 {@code ResponseEntity<ApiResponse<T>>}（本仓 D-B4-59 余面正在逐批改），切面
 * 返回的裸 {@code ApiResponse} 当场 ClassCastException ⇒ 拒绝动作把请求变成 500，比假 200 更糟。</p>
 *
 * <h3>为什么"能变红"</h3>
 * <p>前两枚用例直接打真代理（{@link AspectJProxyFactory}）调一枚声明 {@code ResponseEntity} 的
 * 被拦截方法：把切面改回 {@code return ApiResponse.forbidden(...)}，本用例读到的就是
 * ClassCastException 而非法令异常 ⇒ 立即红（不是靠注释声明「已解耦」）。第三～五枚用例断言
 * 拒绝时 {@code proceed()} 零调用（fail-closed 不因为改抛而退化成放行）与携带值同号，
 * 第六／七枚钉住两条容错腿（注解值为空、ABAC ALLOW／RBAC 命中）行为不变。</p>
 */
@DisplayName("RequirePermission 切面拒绝腿＝抛承载异常，不是裸回信封")
class RequirePermissionAspectDenialShapeTest {

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    /** 真被拦截 Bean：一枚声明 {@code ResponseEntity}（折叠后的仓内主流形态），一枚声明裸信封。 */
    public static class GuardBean {
        @RequirePermission("task:WRITE")
        public ResponseEntity<ApiResponse<String>> guardedEntity() {
            return ResponseEntity.ok(ApiResponse.success("PROCEEDED"));
        }

        @RequirePermission("task:WRITE")
        public ApiResponse<String> guardedEnvelope() {
            return ApiResponse.success("PROCEEDED");
        }

        @RequirePermission({})
        public ApiResponse<String> emptyValueMethod() {
            return ApiResponse.success("PROCEEDED");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 一、真代理腿：证明「拒绝」与「被拦截方法返回类型」彻底解耦
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("未登录＋被拦截方法声明 ResponseEntity＝抛 401 承载异常，禁 ClassCastException")
    void unauthorizedOnResponseEntitySignatureIsThrownNotCast() {
        SecurityContextHolder.clearContext();

        GuardBean proxy = newProxy();

        UnauthorizedException ex = assertThrows(UnauthorizedException.class, proxy::guardedEntity,
                "切面若在拒绝时 return ApiResponse ⇒ 调用点按声明类型 cast 出 ClassCastException，"
                        + "本断言即红（这条正是端点折叠批的隐性回归面）");
        assertEquals(401, ex.getHttpStatus(), "携带值必须由 UnauthorizedException 单源自述");
        assertEquals(401, ex.getErrorCode(), "状态行与信封码必须由同一携带值推出");
        assertEquals("未登录或Token已过期", ex.getMessage(),
                "消息文本必须与旧信封逐字一致——API 只增不改，消费方读到的 message 不得变");
    }

    @Test
    @DisplayName("权限不足＋声明 ResponseEntity＝抛 403 承载异常，理由仍回显所需权限码")
    void forbiddenOnResponseEntitySignatureIsThrownNotCast() {
        authenticate("alice", "other:READ");

        GuardBean proxy = newProxy();

        ForbiddenException ex = assertThrows(ForbiddenException.class, proxy::guardedEntity,
                "回退成 return ApiResponse.forbidden(...) 时本用例读到 CCE 而非 403 裁决");
        assertEquals(403, ex.getHttpStatus());
        assertEquals(403, DataBridgeException.resolveHttpStatus(ex.getHttpStatus()),
                "承载值经单源判据后仍须同号，禁被折成 500");
        assertEquals("权限不足，需要: task:WRITE", ex.getMessage(),
                "拒绝理由必须可机读且与旧信封逐字一致");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 二、直接打切面腿：钉住「抛之前 proceed() 零调用」与 ABAC 分态
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("ABAC DENY＝抛 403「访问被安全策略拒绝」，被拦截方法零执行，且不可变策略列表不得静默关掉 ABAC（D-B4-70）")
    void abacDenyThrowsForbiddenAndNeverProceeds() throws Throwable {
        authenticate("bob", "task:WRITE"); // RBAC 命中，仍须被 ABAC DENY 覆盖（策略优先语义不变）

        RequirePermissionAspect aspect = new RequirePermissionAspect();
        // 夹具刻意用 List.of(...)（不可变）喂策略：原地 sort 协作者返回值会抛
        // UnsupportedOperationException 并被宽 catch 吞成「ABAC 未评估」⇒ 本用例读到放行而非 403。
        injectAbac(aspect, denyPolicy("bob"));

        ProceedingJoinPoint pjp = joinPoint("guardedEnvelope");

        ForbiddenException ex = assertThrows(ForbiddenException.class, () -> aspect.checkPermission(pjp));
        assertEquals("访问被安全策略拒绝", ex.getMessage(),
                "策略拒绝与权限不足必须可分态，禁共用一条理由");
        verify(pjp, never()).proceed();
    }

    @Test
    @DisplayName("承载异常必须是非受检型且不自带信封字段（禁「抛一个包着 ApiResponse 的异常」式假修）")
    void carrierExceptionIsUncheckedAndEnvelopeFree() throws Throwable {
        SecurityContextHolder.clearContext();
        RequirePermissionAspect aspect = new RequirePermissionAspect();

        Throwable thrown = assertThrows(Throwable.class,
                () -> aspect.checkPermission(joinPoint("guardedEnvelope")));

        assertInstanceOf(RuntimeException.class, thrown,
                "被拦截方法签名不声明受检异常（API 只增不改），拒绝腿若改受检型即编不过/运行炸");
        assertEquals(0, countEnvelopeFieldsIn(thrown.getClass()),
                "承载异常类不得持有 ApiResponse 字段——状态行只能由 handler 注解承载面写定，"
                        + "禁把裸信封藏在异常里再抛（那是 D-B4-59 的换位版本）");
    }

    @Test
    @DisplayName("被拦截方法声明裸信封时同样抛承载异常（解耦与返回类型无关，两形态同判）")
    void thrownShapeIsIndependentOfDeclaredReturnType() throws Throwable {
        SecurityContextHolder.clearContext();
        RequirePermissionAspect aspect = new RequirePermissionAspect();

        assertThrows(UnauthorizedException.class,
                () -> aspect.checkPermission(joinPoint("guardedEnvelope")));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 三、容错腿行为不变（本批只动拒绝腿，禁顺带改放行语义）
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("权限命中＝照常 proceed()，返回原方法结果")
    void grantedPermissionProceeds() throws Throwable {
        authenticate("carol", "task:WRITE");
        RequirePermissionAspect aspect = new RequirePermissionAspect();
        ProceedingJoinPoint pjp = joinPoint("guardedEnvelope");
        when(pjp.proceed()).thenReturn(ApiResponse.success("PROCEEDED"));

        Object ret = aspect.checkPermission(pjp);

        assertInstanceOf(ApiResponse.class, ret, "放行腿必须原样透传被拦截方法的返回值");
        assertEquals("PROCEEDED", ((ApiResponse<?>) ret).getData());
    }

    @Test
    @DisplayName("注解值为空＝容错放行（既有语义不变，禁本批顺带收紧）")
    void emptyAnnotationValueStillProceeds() throws Throwable {
        authenticate("dave", "task:WRITE");
        RequirePermissionAspect aspect = new RequirePermissionAspect();
        ProceedingJoinPoint pjp = joinPoint("emptyValueMethod");
        when(pjp.proceed()).thenReturn(ApiResponse.success("PROCEEDED"));

        Object ret = aspect.checkPermission(pjp);

        assertInstanceOf(ApiResponse.class, ret,
                "空值容错腿仍走 proceed()，且不得被拒绝腿改动波及");
        assertEquals("PROCEEDED", ((ApiResponse<?>) ret).getData());
        verify(pjp).proceed();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // helpers
    // ─────────────────────────────────────────────────────────────────────────

    /** 走真代理：CGLIB 子类代理 ⇒ 调用点带 checkcast，能如实复现「切面返回值 vs 声明类型」碰撞。 */
    private static GuardBean newProxy() {
        AspectJProxyFactory factory = new AspectJProxyFactory(new GuardBean());
        factory.setProxyTargetClass(true);
        factory.addAspect(new RequirePermissionAspect());
        return (GuardBean) factory.getProxy();
    }

    private static void authenticate(String user, String authority) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                user, "n/a", List.of(new SimpleGrantedAuthority(authority))));
    }

    private static AbacPolicy denyPolicy(String subject) {
        AbacPolicy policy = new AbacPolicy();
        policy.setPolicyId("p-deny-" + subject);
        policy.setPolicyName("deny-for-gate-test");
        policy.setSubjectCondition("*");
        policy.setResourceCondition("*");
        policy.setActionCondition("*");
        policy.setEffect("DENY");
        policy.setPriority(1);
        return policy;
    }

    private static void injectAbac(RequirePermissionAspect aspect, AbacPolicy policy) throws Exception {
        IAbacPolicyService service = mock(IAbacPolicyService.class);
        when(service.listPolicies()).thenReturn(List.of(policy));
        Field f = RequirePermissionAspect.class.getDeclaredField("abacPolicyService");
        f.setAccessible(true);
        f.set(aspect, service);
    }

    /** 构造一枚最小 {@link ProceedingJoinPoint}：签名指回 {@link GuardBean} 的真实方法。 */
    private static ProceedingJoinPoint joinPoint(String methodName) throws Throwable {
        Method method = findMethod(GuardBean.class, methodName);
        MethodSignature signature = mock(MethodSignature.class);
        when(signature.getMethod()).thenReturn(method);
        when(signature.toShortString()).thenReturn("GuardBean." + methodName + "()");
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        when(pjp.getSignature()).thenReturn(signature);
        return pjp;
    }

    private static Method findMethod(Class<?> type, String name) {
        for (Method m : type.getDeclaredMethods()) {
            if (m.getName().equals(name)) return m;
        }
        throw new AssertionError("夹具方法形状变化（找不到 " + name + "）＝本门前提出问题，须显式订正而非静默通过");
    }

    /** 反射扫描类声明字段：承载异常类里不得持有裸信封字段。 */
    private static int countEnvelopeFieldsIn(Class<?> type) {
        int n = 0;
        for (Field f : type.getDeclaredFields()) {
            if (ApiResponse.class.equals(f.getType())) n++;
        }
        return n;
    }
}
