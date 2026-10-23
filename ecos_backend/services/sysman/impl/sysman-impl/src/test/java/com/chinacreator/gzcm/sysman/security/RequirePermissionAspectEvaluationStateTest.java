package com.chinacreator.gzcm.sysman.security;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.exception.ForbiddenException;
import com.chinacreator.gzcm.sysman.abac.model.AbacPolicy;
import com.chinacreator.gzcm.sysman.abac.service.IAbacPolicyService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * {@link RequirePermissionAspect#EvaluationVerdict} 四态分态门（#142/B4-3e-57，收口差距
 * <b>D-B4-87</b>）。
 *
 * <p><b>登记口径</b>（v1.0.135 台账第 1075 行）： {@code EvaluateAbacPolicies}（原
 * 第 167–168 行）在 {@code abacPolicyService == null} 时 {@code return null}（注释「ABAC
 * 服务不可用，跳过」），而第 172–173 行「无策略」返回<b>同一枚</b> {@code null}，调用侧
 * 统一读成「无匹配，回退 RBAC」——经核<b>不是 fail-open</b>（RBAC 裁决腿仍生效），但
 * 违 §4.7.2「不可用与无匹配不可分辨」。裁决：修法属<b>分态类</b>而非 503 承载类，
 * ABAC 经 {@code @Autowired(required = false)} 声明为 RBAC 之上的可叠加层，回退是
 * 设计意图，不可改成 503。</p>
 *
 * <p><b>修法</b>：原 {@code String} 返回（"ALLOW"/"DENY"/null）改为本类内层不可变
 * {@link RequirePermissionAspect.EvaluationVerdict} 四值枚举；「服务未装配」和「评估异常」
 * 归 {@code NOT_ASSESSED}，「服务在位真无命中」归 {@code NO_MATCH}，调用侧分别打
 * {@code log.warn}（不可用）和 {@code log.debug}（真无命中）——在日志口径上已可
 * 分辨，回退行为一字不动。</p>
 *
 * <p><b>能变红的机制</b>：反射直接调私有 {@code evaluateAbacPolicies}，四态断言返回
 * 枚举值。把生产代码退回 {@code String} 原形态后，反射 invoke 返回的是 {@code String}
 * 或 {@code null}，{@code assertEquals(EvaluationVerdict.X, result)} 立刻抛
 * {@code java.lang.AssertionError}（类型不兼容）⇒ 本门<b>必红</b>。</p>
 */
@DisplayName("RequirePermissionAspect ABAC 四态分态门：D-B4-87 收口件")
class RequirePermissionAspectEvaluationStateTest {

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    // ─────────────────────────────────────────────────────────────
    // 一、四态反射直接断言（能变红核心）
    // ─────────────────────────────────────────────────────────────

    @Test
    @Timeout(120)
    @DisplayName("ABAC 服务 null ＝ NOT_ASSESSED（依赖缺席，非真无匹配）")
    void serviceAbsenceYieldsNotAssessed() throws Exception {
        RequirePermissionAspect aspect = new RequirePermissionAspect();
        // 不注入任何服务 = 字段默认 null

        Object result = invoke(aspect, "task:WRITE", Set.of("task:WRITE"), "u-1");

        assertEquals(RequirePermissionAspect.EvaluationVerdict.NOT_ASSESSED, result,
                "服务未装配必须归 NOT_ASSESSED，禁与 NO_MATCH 混读（D-B4-87）");
    }

    @Test
    @Timeout(120)
    @DisplayName("ABAC 服务在位但策略列表为空 ＝ NO_MATCH（真·无策略，不可与未装配混读）")
    void emptyPolicyListYieldsNoMatch() throws Exception {
        RequirePermissionAspect aspect = new RequirePermissionAspect();
        IAbacPolicyService svc = mock(IAbacPolicyService.class);
        when(svc.listPolicies()).thenReturn(List.of());
        inject(aspect, svc);

        Object result = invoke(aspect, "task:WRITE", Set.of("task:WRITE"), "u-1");

        assertEquals(RequirePermissionAspect.EvaluationVerdict.NO_MATCH, result,
                "有服务但无策略＝真无匹配（NO_MATCH），不得归 NOT_ASSESSED");
    }

    @Test
    @Timeout(120)
    @DisplayName("ABAC 服务在位、有策略但条件不匹配 ＝ NO_MATCH（评估完成、未命中）")
    void nonMatchingPolicyYieldsNoMatch() throws Exception {
        RequirePermissionAspect aspect = new RequirePermissionAspect();
        AbacPolicy policy = new AbacPolicy();
        policy.setPolicyId("p-1");
        policy.setPolicyName("non-matching");
        // 铁定不匹配的条件：subject 限定 "nonexistent-user" ≠ "u-1"
        policy.setSubjectCondition("nonexistent-user");
        policy.setResourceCondition("*");
        policy.setActionCondition("*");
        policy.setEffect("ALLOW");
        policy.setPriority(1);

        IAbacPolicyService svc = mock(IAbacPolicyService.class);
        when(svc.listPolicies()).thenReturn(List.of(policy));
        inject(aspect, svc);

        Object result = invoke(aspect, "task:WRITE", Set.of("task:WRITE"), "u-1");

        assertEquals(RequirePermissionAspect.EvaluationVerdict.NO_MATCH, result,
                "有策略但全不匹配＝NO_MATCH，不得折成 NOT_ASSESSED");
    }

    @Test
    @Timeout(120)
    @DisplayName("ABAC ALLOW 命中 ＝ ALLOW（策略放行走 proceed）")
    void allowPolicyYieldsAllow() throws Exception {
        RequirePermissionAspect aspect = new RequirePermissionAspect();
        AbacPolicy policy = new AbacPolicy();
        policy.setPolicyId("p-allow");
        policy.setPolicyName("allow-all");
        policy.setSubjectCondition("*");
        policy.setResourceCondition("*");
        policy.setActionCondition("*");
        policy.setEffect("ALLOW");
        policy.setPriority(1);

        IAbacPolicyService svc = mock(IAbacPolicyService.class);
        when(svc.listPolicies()).thenReturn(List.of(policy));
        inject(aspect, svc);

        Object result = invoke(aspect, "task:WRITE", Set.of("task:WRITE"), "u-1");

        assertEquals(RequirePermissionAspect.EvaluationVerdict.ALLOW, result);
    }

    @Test
    @Timeout(120)
    @DisplayName("ABAC DENY 命中 ＝ DENY（策略拒绝走 ForbiddenException）")
    void denyPolicyYieldsDeny() throws Exception {
        RequirePermissionAspect aspect = new RequirePermissionAspect();
        AbacPolicy policy = new AbacPolicy();
        policy.setPolicyId("p-deny");
        policy.setPolicyName("deny-all");
        policy.setSubjectCondition("*");
        policy.setResourceCondition("*");
        policy.setActionCondition("*");
        policy.setEffect("DENY");
        policy.setPriority(1);

        IAbacPolicyService svc = mock(IAbacPolicyService.class);
        when(svc.listPolicies()).thenReturn(List.of(policy));
        inject(aspect, svc);

        Object result = invoke(aspect, "task:WRITE", Set.of("task:WRITE"), "u-1");

        assertEquals(RequirePermissionAspect.EvaluationVerdict.DENY, result);
    }

    @Test
    @Timeout(120)
    @DisplayName("评估中途抛异常 ＝ NOT_ASSESSED（不可与 NO_MATCH 混读）")
    void evaluationExceptionYieldsNotAssessed() throws Exception {
        RequirePermissionAspect aspect = new RequirePermissionAspect();
        IAbacPolicyService svc = mock(IAbacPolicyService.class);
        when(svc.listPolicies()).thenThrow(new RuntimeException("DB down"));
        inject(aspect, svc);

        Object result = invoke(aspect, "task:WRITE", Set.of("task:WRITE"), "u-1");

        assertEquals(RequirePermissionAspect.EvaluationVerdict.NOT_ASSESSED, result,
                "评估中途故障＝不可用（NOT_ASSESSED），不得折成 NO_MATCH（那是谎报）");
    }

    // ─────────────────────────────────────────────────────────────
    // 二、端到端安全语义（不许改过头：回退 RBAC，非 fail-open）
    // ─────────────────────────────────────────────────────────────

    @Test
    @Timeout(120)
    @DisplayName("ABAC 不可用 + RBAC 命中 ＝ 照常放行（回退是设计意图，非 fail-open）")
    void notAssessedPlusRbacPassProceeds() throws Throwable {
        authenticate("bob", "task:WRITE");
        RequirePermissionAspect aspect = new RequirePermissionAspect();
        ProceedingJoinPoint pjp = joinPoint("guardedEnvelope");
        when(pjp.proceed()).thenReturn(ApiResponse.success("PROCEEDED"));

        Object ret = aspect.checkPermission(pjp);

        assertEquals("PROCEEDED", ((ApiResponse<?>) ret).getData(),
                "ABAC 不可用但 RBAC 命中仍须放行，回退行为一字不动");
        verify(pjp).proceed();
    }

    @Test
    @Timeout(120)
    @DisplayName("ABAC 不可用 + RBAC 未命中 ＝ ForbiddenException（回退后仍走 RBAC 裁决，不放行）")
    void notAssessedPlusRbacFailThrowsForbidden() throws Throwable {
        authenticate("eve", "other:READ");
        RequirePermissionAspect aspect = new RequirePermissionAspect();
        ProceedingJoinPoint pjp = joinPoint("guardedEnvelope");

        ForbiddenException ex = assertThrows(ForbiddenException.class,
                () -> aspect.checkPermission(pjp),
                "ABAC 不可用不得变成 fail-open：RBAC 未命中仍须拒绝");
        assertTrue(ex.getMessage().contains("task:WRITE"),
                "拒绝理由须回显所需权限码，与既有形态一致");
        verify(pjp, never()).proceed();
    }

    @Test
    @Timeout(120)
    @DisplayName("ABAC 不可用 + ABAC DENY 策略（若本可评估时命中）不影响：服务 null 时 DENY 策略读不到")
    void serviceNullSkipsAllPolicies() throws Throwable {
        // ABAC 服务为 null ⇒ 打不到策略列表 ⇒ 即使库里本来有 DENY 也读不到
        // 这不是 fail-open（DENY 策略经 service.listPolicies() 取得；service 为 null 则评估根本没发生），
        // 而是 ABAC 可叠加层缺席的既定后果，RBAC 裁决兜底。
        authenticate("mallory", "task:WRITE");
        RequirePermissionAspect aspect = new RequirePermissionAspect();
        ProceedingJoinPoint pjp = joinPoint("guardedEnvelope");
        when(pjp.proceed()).thenReturn(ApiResponse.success("PROCEEDED"));

        Object ret = aspect.checkPermission(pjp);

        assertEquals("PROCEEDED", ((ApiResponse<?>) ret).getData(),
                "服务 null 时不得凭空 DENY（DENY 必须经评估流程产出），RBAC 命中即放行");
    }

    // ─────────────────────────────────────────────────────────────
    // 三、源码面（B4 惯例：折叠形态不得复现）
    // ─────────────────────────────────────────────────────────────

    @Test
    @Timeout(120)
    @DisplayName("源码面：EvaluationVerdict 四值在位、调用侧分支判 NOT_ASSESSED，原「同读 null」折叠字面不得复现")
    void sourceFaceHasDistinctStatesAndNoOldComment() throws IOException {
        String code = Files.readString(repoFile());

        assertTrue(code.contains("enum EvaluationVerdict"), "四态枚举必须在本类内层声明");
        for (String v : new String[]{"ALLOW", "DENY", "NO_MATCH", "NOT_ASSESSED"}) {
            assertTrue(code.contains(v), "枚举必须具名 " + v + "（缺一枚即退回『布尔/字符串折叠』）");
        }
        assertTrue(code.contains("EvaluationVerdict.NOT_ASSESSED"),
                "调用侧必须按 NOT_ASSESSED 具名态分流（回退 RBAC 的不可用腿）");
        assertFalse(code.contains("ABAC服务不可用，跳过"),
                "原折叠腿自证注释不得复现（D-B4-87 原始形态溯源）");
        assertFalse(code.contains("return null; // ABAC"),
                "旧 return null 折叠腿形态不得复现");
        assertFalse(code.contains("\"DENY\".equals(abacResult)"),
                "原 String 三态比较形态不得复现（改枚举后此比较已不存在）");
    }

    // ─────────────────────────────────────────────────────────────
    // helpers
    // ─────────────────────────────────────────────────────────────

    private Object invoke(RequirePermissionAspect aspect, String[] required,
                          Set<String> userPerms, String userId) throws Exception {
        Method m = RequirePermissionAspect.class.getDeclaredMethod(
                "evaluateAbacPolicies", String[].class, Set.class, String.class);
        m.setAccessible(true);
        return m.invoke(aspect, required, userPerms, userId);
    }

    /** 便捷重载：单值权限码。 */
    private Object invoke(RequirePermissionAspect aspect, String required,
                          Set<String> userPerms, String userId) throws Exception {
        return invoke(aspect, new String[]{required}, userPerms, userId);
    }

    private static void inject(RequirePermissionAspect aspect, IAbacPolicyService svc)
            throws Exception {
        Field f = RequirePermissionAspect.class.getDeclaredField("abacPolicyService");
        f.setAccessible(true);
        f.set(aspect, svc);
    }

    /** 最小夹具：与既有 DenialShapeTest 的 GuardBean 同型。 */
    public static class GuardBean {
        @RequirePermission("task:WRITE")
        public ApiResponse<String> guardedEnvelope() {
            return ApiResponse.success("PROCEEDED");
        }
    }

    private static ProceedingJoinPoint joinPoint(String methodName) throws Throwable {
        Method method = null;
        for (Method m : GuardBean.class.getDeclaredMethods()) {
            if (m.getName().equals(methodName)) {
                method = m;
                break;
            }
        }
        if (method == null) {
            throw new AssertionError("joinPoint: GuardBean 缺方法 " + methodName);
        }
        MethodSignature sig = mock(MethodSignature.class);
        when(sig.getMethod()).thenReturn(method);
        when(sig.toShortString()).thenReturn("GuardBean." + methodName + "()");
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        when(pjp.getSignature()).thenReturn(sig);
        return pjp;
    }

    private static void authenticate(String user, String authority) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, "n/a",
                        List.of(new SimpleGrantedAuthority(authority))));
    }

    private static Path repoFile() {
        String relPath = "ecos_backend/services/sysman/impl/sysman-impl/src/main/java/"
                + "com/chinacreator/gzcm/sysman/security/RequirePermissionAspect.java";
        Path cur = Paths.get("").toAbsolutePath();
        for (int i = 0; i < 8 && cur != null; i++) {
            Path candidate = cur.resolve(relPath);
            if (Files.isRegularFile(candidate)) return candidate;
            cur = cur.getParent();
        }
        throw new IllegalStateException("未找到判据源码（禁跳过校验）: " + relPath);
    }
}
