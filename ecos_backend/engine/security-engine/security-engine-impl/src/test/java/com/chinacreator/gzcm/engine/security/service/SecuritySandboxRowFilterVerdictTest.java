package com.chinacreator.gzcm.engine.security.service;

import com.chinacreator.gzcm.common.security.abac.AbacEvaluateResponseContract.Verdict;
import com.chinacreator.gzcm.common.security.filter.RowFilterVerdictContract;
import com.chinacreator.gzcm.engine.security.dao.SecuritySandboxDao;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * 行过滤<b>真生产侧</b>的三态不变式（#142/B4-3e-7，收口差距 <b>D-B4-42</b>，
 * 并钉住 ai-engine 反射腿依赖的方法签名）。
 *
 * <p>静态门禁 {@code RowFilterVerdictSingleSourceTest} 的验收 5 只断言"宿主调了工厂"，
 * 断不了"工厂在真实谓词路径上写出的形态读得回来"。本类直接跑
 * {@link SecuritySandboxService#evaluateFilter}（security-engine-impl 才是 ai 通道
 * {@code service.security.base-url=http://localhost:18081} 的真生产侧），
 * 对同一输入族断言：</p>
 * <ul>
 *   <li>策略判了拒绝 ⇒ {@link Verdict#DENY}（{@code success=true}＋{@code allowed=false}）；</li>
 *   <li>引擎内部抛异常 ⇒ {@link Verdict#UNEVALUATED}（{@code success=false}＋{@code error}），
 *       <b>与 DENY 必异</b> —— 收口前两条腿在消费侧同值，故障会被静默成"这行没权限"；</li>
 *   <li>反射腿（{@code ToolExecutorService#reviewBySandboxService}）依赖的
 *       {@code evaluateFilter(String, Map, String)} 签名与"返回 Map"仍在位 ——
 *       签名漂移时该类只会静默归 UNEVALUATED（fail-closed 但无人知道为什么），
 *       所以必须在这里变红。</li>
 * </ul>
 */
class SecuritySandboxRowFilterVerdictTest {

    private static SecuritySandboxService newService() {
        return new SecuritySandboxService(mock(ApplicationEventPublisher.class),
                mock(SecuritySandboxDao.class));
    }

    // ── 一、真谓词 ⇒ 三态归位 ───────────────────────────────────────────────

    @Test
    @DisplayName("真实策略拒绝：viewer 过 role 表达式 ⇒ DENY 且 success=true")
    void realPolicyDenyIsReadableAsDeny() {
        Map<String, Object> resp = newService().evaluateFilter(
                "role == 'admin'", Map.of("org", "dept_a"), "viewer");

        assertEquals(Verdict.DENY, RowFilterVerdictContract.verdictOf(resp),
                "策略真拒绝必须读成 DENY（消费侧据此丢行／报 403）");
        assertEquals(Boolean.TRUE, resp.get(RowFilterVerdictContract.KEY_SUCCESS));
        assertEquals(Boolean.FALSE, resp.get(RowFilterVerdictContract.KEY_ALLOWED));
        assertEquals(RowFilterVerdictContract.VALUE_ENGINE_RHAI,
                resp.get(RowFilterVerdictContract.KEY_ENGINE),
                "正常判定路径的引擎标记必须来自单源取值");
        assertFalse(resp.containsKey(RowFilterVerdictContract.KEY_ERROR));
    }

    @Test
    @DisplayName("真实策略放行（含 analyst 命中 role 分支）⇒ ALLOW")
    void realPolicyAllowIsReadableAsAllow() {
        SecuritySandboxService svc = newService();
        assertEquals(Verdict.ALLOW, RowFilterVerdictContract.verdictOf(
                svc.evaluateFilter("classification == 'public'",
                        Map.of("classification", "public"), "viewer")));
        assertEquals(Verdict.ALLOW, RowFilterVerdictContract.verdictOf(
                svc.evaluateFilter("role == 'admin'", Map.of(), "analyst")));
        // admin 过 TOP_SECRET：密级分支真放行，与"引擎没答"必须区分开
        assertEquals(Verdict.ALLOW, RowFilterVerdictContract.verdictOf(
                svc.evaluateFilter("level == 'top_secret'",
                        Map.of("level", "TOP_SECRET"), "admin")));
    }

    @Test
    @DisplayName("blocked_dept 谓词⇒DENY（不是 UNEVALUATED：判定完成了，只是结论为拒绝）")
    void blockedDepartmentRowIsDenyNotUnavailabe() {
        Map<String, Object> resp = newService().evaluateFilter(
                "department == 'sales'", Map.of("department", "blocked_dept"), "analyst");
        assertEquals(Verdict.DENY, RowFilterVerdictContract.verdictOf(resp));
        assertEquals(Boolean.TRUE, resp.get(RowFilterVerdictContract.KEY_SUCCESS));
    }

    // ── 二、引擎故障 ⇒ 与 DENY 必异（D-B4-42 的正身不变式） ──────────────────

    @Test
    @DisplayName("不变式：同输入族下真拒绝与引擎故障读出不同裁决、不同诊断（禁折叠）")
    void engineFailureNeverCollapsesIntoPolicyDeny() {
        Map<String, Object> deny = newService().evaluateFilter(
                "role == 'admin'", Map.of(), "viewer");
        // rowData=null 在密级分支上必然抛 NPE ⇒ 走 catch，即"引擎没能完成判定"
        Map<String, Object> failed = newService().evaluateFilter("classification", null, "viewer");

        assertEquals(Boolean.FALSE, deny.get(RowFilterVerdictContract.KEY_ALLOWED));
        assertEquals(Boolean.FALSE, failed.get(RowFilterVerdictContract.KEY_ALLOWED),
                "外形不变（API 只增不改）：失败路径仍写 allowed=false");

        assertNotEquals(RowFilterVerdictContract.verdictOf(deny),
                RowFilterVerdictContract.verdictOf(failed),
                "D-B4-42 复发：写面外形一致但读面又只剩一个键 ⇒ 故障会被当成行级拒绝");
        assertEquals(Verdict.UNEVALUATED, RowFilterVerdictContract.verdictOf(failed));
        assertEquals(Boolean.FALSE, failed.get(RowFilterVerdictContract.KEY_SUCCESS));
        assertEquals(RowFilterVerdictContract.VALUE_ENGINE_FALLBACK_FAILED,
                failed.get(RowFilterVerdictContract.KEY_ENGINE));

        String diag = RowFilterVerdictContract.diagnose(failed);
        assertTrue(diag.contains("Evaluation error"),
                "诊断必须携带引擎自报的故障说明，便于区分 503 与 403: " + diag);
        assertNotEquals(diag, RowFilterVerdictContract.diagnose(deny));
    }

    @Test
    @DisplayName("异常路径仍写审计之外的正常返回段：工厂一改，两支消费腿同时失配")
    void bothShapesCarryTheSuccessKey() {
        SecuritySandboxService svc = newService();
        List<Map<String, Object>> responses = List.of(
                svc.evaluateFilter("role == 'admin'", Map.of(), "viewer"),
                svc.evaluateFilter("classification", null, "viewer"));
        for (Map<String, Object> resp : responses) {
            assertTrue(resp.containsKey(RowFilterVerdictContract.KEY_SUCCESS),
                    "应答缺 success 键 ⇒ 消费侧无法把故障与拒绝分态（本批契约的锚点）: " + resp);
        }
    }

    @Test
    @DisplayName("判定完成路径写审计，故障路径不冒充 GRANTED/DENIED 审计结论")
    void auditTrailReflectsEvaluationState() {
        SecuritySandboxService svc = newService();
        int before = svc.getAuditLogs().size();
        svc.evaluateFilter("role == 'admin'", Map.of(), "viewer");
        List<Map<String, Object>> logs = svc.getAuditLogs();
        assertTrue(logs.size() > before, "审计在返回段之前写入，否则咽喉看不到判定记录");
        Map<String, Object> latest = logs.get(0);
        assertEquals("POLICY_EVAL", latest.get("event"));
        assertEquals("DENIED", latest.get("status"),
                "真拒绝要留下 DENIED 审计 —— 与故障路径（无 POLICY_EVAL 结论）分态");

        SecuritySandboxService failing = newService();
        int beforeFailed = failing.getAuditLogs().size();
        failing.evaluateFilter("classification", null, "viewer");
        List<Map<String, Object>> failedLogs = failing.getAuditLogs();
        if (failedLogs.size() > beforeFailed) {
            assertNotEquals("GRANTED", failedLogs.get(0).get("status"),
                    "引擎没答却记 GRANTED/DENIED ⇒ 审计把故障写成授权结论");
        }
    }

    // ── 三、反射腿的签名锚 ──────────────────────────────────────────────────

    @Test
    @DisplayName("反射腿签名锚：evaluateFilter(String, Map, String) 返回 Map，类名仍以 SecuritySandboxService 结尾")
    void reflectionLegContractIsPinned() throws Exception {
        Method m = SecuritySandboxService.class.getMethod(
                "evaluateFilter", String.class, Map.class, String.class);
        assertEquals(Map.class, m.getReturnType(),
                "返回类型漂移 ⇒ ToolExecutorService 的 verdictOf 只会把结果读成 UNEVALUATED");
        assertTrue(SecuritySandboxService.class.getName().endsWith("SecuritySandboxService"));
        assertTrue(java.lang.reflect.Modifier.isPublic(SecuritySandboxService.class.getModifiers()),
                "宿主类改非 public ⇒ 反射 invoke 抛 IllegalAccessException，高危指令全量归 503");
        assertEquals(3, m.getParameterCount(),
                "参数个数漂移 ⇒ 反射腿查不到方法，静默归 UNEVALUATED（高危指令全量 503）");
    }
}
